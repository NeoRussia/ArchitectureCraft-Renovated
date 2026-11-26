package com.elytradev.architecture.compat.pulsar;

import com.elytradev.architecture.common.ArchitectureLog;
import com.elytradev.architecture.common.tile.TileShape;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.lang.reflect.Method;

/**
 * Answers Pulsar's positional light questions for a shape: what its material
 * emits, and what it lets through.
 *
 * <p>Called from the methods the coremod adds to {@code BlockShape}, on the
 * world thread, so the tile entity can be read. The material's own RGB values
 * come from Pulsar's registries through {@code RgbStateProperties}, which
 * already honour the user's palette files; nothing is duplicated here. All of it
 * is reflection because Pulsar is optional.</p>
 */
public final class PulsarShapeLight {

    private static final int CLEAR = 0xFFF;
    private static final int WHITE = 0xFFF;

    private static boolean resolved;
    private static boolean broken;
    private static Method of;
    private static Method color;
    private static Method filter;

    private static boolean refreshResolved;
    private static Method lightManager;
    private static Method rgbContextualLight;
    private static Method requestSample;

    private PulsarShapeLight() {
    }

    /**
     * Packed 0xRGB the shape emits, from its base and secondary materials.
     *
     * <p>A positional source is treated as an absolute emitter, so this has to
     * answer black for a shape that does not glow: otherwise every shape would
     * light the room in its material's hue. The shape's own {@code LIGHT} state
     * is the scalar emission, and the material hue is scaled to it so a dim
     * emitter stays dim.</p>
     */
    public static int emission(IBlockAccess world, BlockPos pos, IBlockState state) {
        int level = state == null ? 0 : state.getLightValue();
        if (level <= 0)
            return 0;
        int value = -1;
        int[] materials = materials(world, pos);
        for (int material : materials) {
            if (material < 0)
                continue;
            int rgb = colourOf(material);
            value = value < 0 ? rgb : merge(value, rgb);
        }
        if (value < 0) {
            // No shape at this position. Pulsar also samples the held item this
            // way, and a shape stack carries only its light level (its metadata),
            // not the material, so emit that level neutrally.
            return scaled(WHITE, level);
        }
        return scaled(value, level);
    }

    /** Scales a packed 0xRGB hue whose peak is 15 down to the shape's level. */
    private static int scaled(int rgb, int level) {
        if (level >= 15)
            return rgb;
        int red = (rgb >> 8 & 15) * level / 15;
        int green = (rgb >> 4 & 15) * level / 15;
        int blue = (rgb & 15) * level / 15;
        return (red << 8) | (green << 4) | blue;
    }

    /**
     * Packed 0xRGB transmittance (15 transmits, 0 absorbs), the inverse of the
     * materials' absorption. The stronger absorption of the two materials wins,
     * so a window's glazing filters even though its frame would not.
     */
    public static int transmittance(IBlockAccess world, BlockPos pos, IBlockState state) {
        int absorption = 0;
        int[] materials = materials(world, pos);
        for (int material : materials) {
            if (material < 0)
                continue;
            absorption = merge(absorption, filterOf(material));
        }
        return CLEAR ^ absorption;
    }

    /**
     * States of the shape's two materials at this position, or -1 for a slot
     * that is empty. Only the material identity matters here, so the values are
     * Pulsar's packed property words.
     */
    private static int[] materials(IBlockAccess world, BlockPos pos) {
        if (!resolve())
            return new int[]{-1, -1};
        TileShape tile = TileShape.get(world, pos);
        if (tile == null)
            return new int[]{-1, -1};
        try {
            return new int[]{
                    propertyWord(tile.getBaseBlockState()),
                    propertyWord(tile.getSecondaryBlockState())};
        } catch (RuntimeException e) {
            return new int[]{-1, -1};
        }
    }

    private static int propertyWord(IBlockState state) {
        if (state == null || state.getBlock() == Blocks.AIR)
            return -1;
        try {
            return (Integer) of.invoke(null, state);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            fail(e);
            return -1;
        }
    }

    private static int colourOf(int properties) {
        try {
            return (Integer) color.invoke(null, properties);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            fail(e);
            return 0xFFF;
        }
    }

    private static int filterOf(int properties) {
        try {
            return (Integer) filter.invoke(null, properties);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            fail(e);
            return 0;
        }
    }

    /** Per-channel maximum of two packed 0xRGB values. */
    private static int merge(int a, int b) {
        return (Math.max(a >> 8 & 15, b >> 8 & 15) << 8)
                | (Math.max(a >> 4 & 15, b >> 4 & 15) << 4)
                | Math.max(a & 15, b & 15);
    }

    /**
     * Asks Pulsar to re-sample this cell's RGB values. A shape's material can
     * change without the block state changing (chiselling, cladding), and
     * Pulsar otherwise only samples on a block or tile-entity change, so the
     * light would keep the old colour until something else touched the cell.
     */
    public static void refresh(World world, BlockPos pos) {
        if (world == null || pos == null || world.isRemote)
            return;
        if (!resolveRefresh())
            return;
        try {
            Object manager = lightManager.invoke(world);
            if (manager == null)
                return;
            Object contextual = rgbContextualLight.invoke(manager);
            if (contextual != null)
                requestSample.invoke(contextual, pos.getX(), pos.getY(), pos.getZ());
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            // The colour renews on the next block update; not worth a log line.
        }
    }

    private static synchronized boolean resolveRefresh() {
        if (refreshResolved)
            return lightManager != null;
        refreshResolved = true;
        try {
            Class<?> pulsarWorld = Class.forName("com.sumirelabs.pulsar.world.PulsarWorld");
            lightManager = pulsarWorld.getMethod("pulsar$getLightManager");
            Class<?> manager = Class.forName("com.sumirelabs.pulsar.light.WorldLightManager");
            rgbContextualLight = manager.getMethod("rgbContextualLight");
            Class<?> contextual = Class.forName("com.sumirelabs.pulsar.rgb.RgbContextualLightManager");
            requestSample = contextual.getMethod("request", int.class, int.class, int.class);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            lightManager = null;
            return false;
        }
    }

    private static boolean resolve() {
        if (resolved)
            return !broken;
        resolved = true;
        try {
            Class<?> properties = Class.forName("com.sumirelabs.pulsar.api.RgbStateProperties");
            of = properties.getMethod("of", IBlockState.class);
            color = properties.getMethod("color", int.class);
            filter = properties.getMethod("filter", int.class);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            broken = true;
            return false;
        }
    }

    private static void fail(Throwable e) {
        if (broken)
            return;
        broken = true;
        ArchitectureLog.error("ArchitectureCraft: Pulsar shape light query failed; "
                + "shapes emit white and transmit all RGB light. {}", e.toString());
    }
}
