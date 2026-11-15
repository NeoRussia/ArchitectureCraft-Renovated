package com.elytradev.architecture.client.render;

import com.elytradev.architecture.common.helpers.Utils;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.IBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Finds the second texture layer a material block draws.
 *
 * <p>Many blocks are modelled as two layers: a base texture plus an emissive
 * overlay, the latter switched on by a per-position flag. GregTech and SUSY use
 * this for their active decorative blocks - the base bricks and a bloom layer
 * over them. A shape asks for one sprite per face, so it renders the base and
 * loses the bloom entirely, which is why a wedge cut from an active block looks
 * like plain bricks while the block itself glows.</p>
 *
 * <p>This asks the material's own baked model for its quads, with the extended
 * state so the active flag is set the way that block expects, and returns the
 * first sprite that differs from the base one. Cached per block state: the
 * answer is a property of the state, not of the position, and chunk rebuilds
 * run on several threads at once.</p>
 */
public final class MaterialOverlay {

    private static boolean enabled() {
        return com.elytradev.architecture.common.ArchitectureConfig.overlays();
    }

    /** Per block state id; a NONE marker means "this material has no overlay". */
    private static final Map<Integer, Object> CACHE = new ConcurrentHashMap<Integer, Object>();
    private static final Object NONE = new Object();

    private MaterialOverlay() {
    }

    /** Forgets every per-material answer, so a config change is re-evaluated. */
    public static void clearCache() {
        CACHE.clear();
    }

    /**
     * A material's second layer, and whether it is an emission or just the
     * texture for the active state. GregTech uses the same two-layer model for
     * both: {@code ..._emissive} and {@code ..._bloom} glow, while a plain
     * {@code ..._active} is a replacement texture that must keep normal light.
     */
    public static final class Layer {
        public final TextureAtlasSprite sprite;
        public final boolean emissive;

        Layer(TextureAtlasSprite sprite, boolean emissive) {
            this.sprite = sprite;
            this.emissive = emissive;
        }
    }

    public static Layer layerFor(IBlockAccess world, BlockPos pos, IBlockState material) {
        TextureAtlasSprite sprite = overlayFor(world, pos, material);
        if (sprite == null)
            return null;
        return new Layer(sprite, emissiveName(sprite));
    }

    private static boolean emissiveName(TextureAtlasSprite sprite) {
        String name = sprite == null ? null : sprite.getIconName();
        if (name == null)
            return false;
        return name.endsWith("_emissive") || name.endsWith("_bloom") || name.contains("_emissive_");
    }

    /** The overlay sprite for a material, or null when it draws only one layer. */
    public static TextureAtlasSprite overlayFor(IBlockAccess world, BlockPos pos, IBlockState material) {
        if (!enabled() || material == null)
            return null;
        int stateId = net.minecraft.block.Block.getStateId(material);
        Object cached = CACHE.get(stateId);
        if (cached != null)
            return cached == NONE ? null : (TextureAtlasSprite) cached;
        TextureAtlasSprite found;
        try {
            found = find(world, pos, material);
        } catch (RuntimeException e) {
            // An overlay is decoration: never let a model misbehave and take the
            // shape's own geometry with it. Remember the failure as "no overlay".
            found = null;
        }
        if (found == null)
            found = byNaming(Utils.getSpriteForBlockState(material));
        CACHE.put(stateId, found == null ? NONE : found);
        return found;
    }

    /**
     * Finds the second layer by texture name, which is how GregTech and SUSY
     * name it: {@code sintering_bricks} has {@code sintering_bricks_bloom} next
     * to it. This is needed because the active variant is chosen through an
     * unlisted property at bake time, so the baked model reached through
     * {@code getModelForState} is the single-layer one.
     */
    private static TextureAtlasSprite byNaming(TextureAtlasSprite base) {
        if (base == null)
            return null;
        String icon = base.getIconName();
        if (icon == null || icon.isEmpty() || "missingno".equals(icon))
            return null;
        TextureMap map = Minecraft.getMinecraft().getTextureMapBlocks();
        for (String suffix : SUFFIXES) {
            TextureAtlasSprite sprite = map.getAtlasSprite(icon + suffix);
            if (sprite != null && (icon + suffix).equals(sprite.getIconName()))
                return sprite;
        }
        return null;
    }

    private static final String[] SUFFIXES = {"_bloom", "_active_emissive", "_emissive", "_active"};

    private static TextureAtlasSprite find(IBlockAccess world, BlockPos pos, IBlockState material) {
        TextureAtlasSprite base = Utils.getSpriteForBlockState(material);
        if (base == null)
            return null;
        IBakedModel model = Minecraft.getMinecraft().getBlockRendererDispatcher().getModelForState(material);
        if (model == null)
            return null;
        // Active decorative blocks keep their flag per world position (a GT map)
        // or by redstone power at that position, not in the block state. Asking
        // the world at the shape's position therefore answers "inactive" and the
        // model resolves to its single layer. Ask for the active variant instead,
        // through the listed property the blockstate JSON keys on.
        for (IBlockState candidate : candidates(material, world, pos)) {
            TextureAtlasSprite overlay = overlayIn(model, candidate, base);
            if (overlay != null)
                return overlay;
        }
        return null;
    }

    private static List<IBlockState> candidates(IBlockState material, IBlockAccess world, BlockPos pos) {
        List<IBlockState> list = new ArrayList<IBlockState>(2);
        // The one that works for GregTech's active variants: the extended state
        // with the unlisted ACTIVE property forced on, which is what the baked
        // model reads when it picks between its single and two-layer models.
        IBlockState activeExtended = withActiveUnlisted(material, world, pos);
        if (activeExtended != null)
            list.add(activeExtended);
        IBlockState activeListed = withActive(material);
        if (activeListed != null && !list.contains(activeListed))
            list.add(activeListed);
        try {
            IBlockState extended = material.getBlock().getExtendedState(material, world, pos);
            if (extended != null && !list.contains(extended))
                list.add(extended);
        } catch (RuntimeException e) {
            // A block that cannot answer for this position is simply skipped.
        }
        return list;
    }

    /**
     * The extended state with the unlisted ACTIVE property set to true. Found by
     * reflection so that no dependency on GregTech is taken; blocks without such
     * a property simply have no overlay through this route.
     */
    private static IBlockState withActiveUnlisted(IBlockState material, IBlockAccess world, BlockPos pos) {
        IUnlistedProperty<Boolean> active = activeProperty(material.getBlock());
        if (active == null)
            return null;
        try {
            IBlockState extended = material.getBlock().getExtendedState(material, world, pos);
            if (extended instanceof IExtendedBlockState)
                return ((IExtendedBlockState) extended).withProperty(active, Boolean.TRUE);
        } catch (RuntimeException e) {
            return null;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static IUnlistedProperty<Boolean> activeProperty(Block block) {
        for (Class<?> type = block.getClass(); type != null && type != Block.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) || !"ACTIVE".equals(field.getName()))
                    continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(null);
                    if (value instanceof IUnlistedProperty)
                        return (IUnlistedProperty<Boolean>) value;
                } catch (ReflectiveOperationException | RuntimeException e) {
                    return null;
                }
            }
        }
        return null;
    }

    /** The state with a listed "active" boolean turned on, or null when there is none. */
    private static IBlockState withActive(IBlockState material) {
        IProperty<?> active = material.getBlock().getBlockState().getProperty("active");
        if (active instanceof PropertyBool)
            return material.withProperty((PropertyBool) active, Boolean.TRUE);
        return null;
    }

    private static TextureAtlasSprite overlayIn(IBakedModel model, IBlockState state, TextureAtlasSprite base) {
        for (EnumFacing face : EnumFacing.values()) {
            List<BakedQuad> quads;
            try {
                quads = model.getQuads(state, face, 0L);
            } catch (RuntimeException e) {
                continue;
            }
            // A second layer is two quads on the same face. A block that merely
            // uses different textures per face - a log, a machine front - has one
            // quad per face, and must not be mistaken for an overlay.
            if (quads == null || quads.size() < 2)
                continue;
            TextureAtlasSprite below = quads.get(0).getSprite();
            for (BakedQuad quad : quads) {
                TextureAtlasSprite sprite = quad.getSprite();
                if (sprite != null && sprite != below && !sprite.getIconName().equals(below.getIconName()))
                    return sprite;
            }
        }
        return null;
    }

}



