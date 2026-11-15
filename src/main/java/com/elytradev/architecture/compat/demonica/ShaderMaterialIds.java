package com.elytradev.architecture.compat.demonica;

import com.elytradev.architecture.common.ArchitectureLog;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gives a shape the shader block id of the material it is made of.
 *
 * <p>A shader pack cannot see a shape's material: every shape is the same block
 * with only a light value in its state. Demonica hands the chunk builder a
 * per-block context carrying a block id, and the pack's Photonics voxel pass
 * reads that id to decide which blocks are light sources. We set the context to
 * the <em>material</em> state, so a lamp shape reaches the shader as a GregTech
 * lamp and the pack's existing entries apply.</p>
 *
 * <p>Lamps are recognised by name and by the bloom property GregTech gives
 * them, so no GregTech classes are referenced. Which of a lamp's sixteen states
 * the pack actually maps is not something we can know - the pack lists four of
 * them - so the state is found by asking the shader provider for an id and
 * keeping the first bloom state that has one. That costs a handful of lookups
 * once per lamp block, then a map hit.</p>
 *
 * <p>Translucent materials are handled too, per state rather than per block: the
 * pack tints light that crosses a glass id, so a glass shape must reach the shader
 * as glass with the material's own metadata, which is where a stained glass colour
 * lives.</p>
 *
 * <pre>-Darchitecturecraft.shaderIds=false           (off entirely)
 * -Darchitecturecraft.shaderIds.mode=state      (use the vanilla state id instead)</pre>
 */
public final class ShaderMaterialIds {

    private static boolean enabled() {
        return com.elytradev.architecture.common.ArchitectureConfig.shaderMaterialIds();
    }
    private static final boolean USE_PACK_IDS =
            !"state".equalsIgnoreCase(System.getProperty("architecturecraft.shaderIds.mode", "pack"));

    private static final String HOLDER = "com.demonica.celeritas.api.shader.ShaderProviderHolder";
    private static final String CONTEXT = "com.demonica.celeritas.api.shader.vertex.VanillaQuadContext";
    private static final String EXTENSION = "com.demonica.celeritas.api.shader.vertex.BufferBuilderExtension";
    private static final String PROVIDER = "com.demonica.celeritas.api.shader.ShaderProvider";

    private static boolean resolved;
    private static Method isActive;
    private static Method getProvider;
    private static Method getBlockStateId;
    private static Method setActiveContext;
    private static Constructor<?> newContext;
    private static Method withBlockStateId;
    private static boolean failedOnce;

    /**
     * Chosen state and id per material block. Concurrent because chunk rebuilds
     * run on several worker threads; a plain HashMap can corrupt under that.
     */
    private static final Map<Block, Chosen> chosen = new ConcurrentHashMap<Block, Chosen>();
    /**
     * Per-state ids for translucent materials. Glass carries its colour in the
     * metadata, so a per-block cache would tint every shape with whichever
     * colour happened to be resolved first.
     */
    private static final Map<Integer, Chosen> translucent = new ConcurrentHashMap<Integer, Chosen>();
    /** Marks a block that is not one of ours, since a map cannot hold null. */
    private static final Chosen NONE = new Chosen(null, -1);

    private ShaderMaterialIds() {
    }

    /** Forgets the per-material shader ids, so a config change is re-evaluated. */
    public static void clearCache() {
        chosen.clear();
        translucent.clear();
    }

    /**
     * Sets the shader block id for the material about to be drawn.
     *
     * <p>{@code lightLevel} is the shape's own light, written into the vertex
     * attribute a pack reads as block emission. It cannot come from the id: a
     * glowing translucent shape has to keep the material's glass id for
     * tinting, and a pack that only lights ids it maps would see no emitter at
     * all. The attribute is the second route, and it is also the only one a
     * pack honours for a glass id.</p>
     */
    public static void stamp(BufferBuilder buffer, BlockPos pos, IBlockState material, int lightLevel) {
        if (!enabled() || material == null || !isActive())
            return;
        Chosen choice = choiceFor(material);
        if (choice == null || choice == NONE)
            return;
        try {
            Object context = newContext.newInstance(
                    pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15, choice.id, (short) 0, (byte) lightLevel);
            withBlockStateId.invoke(context, choice.id);
            setActiveContext.invoke(buffer, context);
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (!failedOnce) {
                failedOnce = true;
                ArchitectureLog.error("Shader material ids: could not set the block context, shapes keep their own id", e);
            }
        }
    }

    private static final class Chosen {
        final IBlockState state;
        final int id;

        Chosen(IBlockState state, int id) {
            this.state = state;
            this.id = id;
        }
    }

    /** Resolves (once per state) which state and id this material should present. */
    private static Chosen choiceFor(IBlockState material) {
        Block block = material.getBlock();

        // Translucent materials: glass, stained glass, and anything else drawn in
        // the translucent layer. Their colour lives in the metadata, hence the
        // per-state cache below.
        // A shape's quads are baked from its material, but the chunk builder only
        // knows the shape block. Handing it the material state's id is what lets the
        // pack's floodfill see glass where a glass shape is drawn, and therefore
        // tint light that passes through it instead of stopping at it.
        if (block.getRenderLayer() == BlockRenderLayer.TRANSLUCENT) {
            int stateId = Block.getStateId(material);
            Chosen cachedTranslucent = translucent.get(stateId);
            if (cachedTranslucent != null)
                return cachedTranslucent;
            int meta = block.getMetaFromState(material);
            int id = USE_PACK_IDS ? packId(block, meta) : Block.getStateId(material);
            Chosen result = id > 0 ? new Chosen(material, id) : NONE;
            translucent.putIfAbsent(stateId, result);
            return translucent.get(stateId);
        }

        Chosen cached = chosen.get(block);
        if (cached != null)
            return cached;
        Chosen result = resolve(block);
        if (result == null)
            result = NONE;
        chosen.putIfAbsent(block, result);
        return chosen.get(block);
    }

    /** Asks the shader provider for the pack id of one material state; 0 when unmapped. */
    private static int packId(Block block, int meta) {
        Object provider;
        try {
            provider = getProvider == null ? null : getProvider.invoke(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return 0;
        }
        if (provider == null)
            return 0;
        try {
            Object id = getBlockStateId.invoke(provider, block, meta);
            return id instanceof Integer ? (Integer) id : 0;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return 0;
        }
    }

    private static Chosen resolve(Block block) {
        ResourceLocation name = block.getRegistryName();
        if (name == null || !name.getPath().contains("lamp"))
            return null;
        if (!USE_PACK_IDS) {
            IBlockState state = withBloom(block.getDefaultState());
            return state == null ? null : new Chosen(state, Block.getStateId(state));
        }
        Object provider;
        try {
            provider = getProvider == null ? null : getProvider.invoke(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
        if (provider == null)
            return null;

        for (IBlockState candidate : block.getBlockState().getValidStates()) {
            if (!isBloomOn(candidate))
                continue;
            try {
                int meta = block.getMetaFromState(candidate);
                Object id = getBlockStateId.invoke(provider, block, meta);
                if (id instanceof Integer && (Integer) id > 0) {
                    Chosen choice = new Chosen(candidate, (Integer) id);
                    return choice;
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                return null;
            }
        }
        return null;
    }

    private static IBlockState withBloom(IBlockState state) {
        IProperty<?> bloom = state.getBlock().getBlockState().getProperty("bloom");
        if (!(bloom instanceof PropertyBool))
            return null;
        return state.withProperty((PropertyBool) bloom, Boolean.TRUE);
    }

    private static boolean isBloomOn(IBlockState state) {
        IProperty<?> bloom = state.getBlock().getBlockState().getProperty("bloom");
        return bloom instanceof PropertyBool && Boolean.TRUE.equals(state.getValue((PropertyBool) bloom));
    }

    /** True when a shader pack is running; false when Demonica is absent. */
    public static boolean isActive() {
        if (!resolved)
            resolve();
        if (isActive == null)
            return false;
        try {
            return Boolean.TRUE.equals(isActive.invoke(null));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    private static synchronized void resolve() {
        if (resolved)
            return;
        resolved = true;
        try {
            Class<?> holder = Class.forName(HOLDER);
            Class<?> context = Class.forName(CONTEXT);
            Class<?> extension = Class.forName(EXTENSION);
            isActive = holder.getMethod("isActive");
            getProvider = holder.getMethod("getProvider");
            setActiveContext = extension.getMethod("demonica$setActiveQuadContext", context);
            newContext = context.getConstructor(int.class, int.class, int.class, int.class, short.class, byte.class);
            withBlockStateId = context.getMethod("withBlockStateId", int.class);
            getBlockStateId = Class.forName(PROVIDER).getMethod("getBlockStateId", Block.class, int.class);
        } catch (ReflectiveOperationException | RuntimeException e) {
            isActive = null;
        }
    }

}
