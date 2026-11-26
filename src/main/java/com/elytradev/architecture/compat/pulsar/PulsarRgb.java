package com.elytradev.architecture.compat.pulsar;

import com.elytradev.architecture.common.ArchitectureLog;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.VertexFormat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Gives shapes Pulsar's RGB light.
 *
 * <p>Pulsar does not put RGB light in the lightmap. Its client hook wraps
 * {@code BlockRendererDispatcher.renderBlock} and, after the block has been
 * drawn, rewrites each new vertex's colour with the RGB light at that vertex's
 * position. ArchitectureCraft replaces that method with its own dispatcher, so
 * shape vertices never pass through the hook and stay uncoloured.</p>
 *
 * <p>This class does the same rewrite for the vertices the shape renderer has
 * just written: for each vertex, Pulsar's own {@code tintAbgr} at the vertex
 * position, then its ambient-floor guard, which leaves a full-bright vertex
 * (a glowing face or the emissive overlay) untouched. Pulsar's classes are
 * reached by reflection so the mod still builds and runs without it.</p>
 */
public final class PulsarRgb {

    private static boolean resolved;
    private static boolean present;
    private static boolean broken;
    private static boolean failedOnce;

    private static Method active;
    private static Method skyActive;
    private static Method shaderActive;
    private static Method tintAbgr;
    private static Method preserve;
    private static Method suppressVertices;
    private static Method restoreVertices;
    private static Method drawState;
    private static Method stateActive;
    private static Method mark;
    private static Method generation;
    private static Method xOffset;
    private static Method yOffset;
    private static Method zOffset;
    private static Field vanillaRendered;

    private PulsarRgb() {
    }

    /** True when Pulsar is running and tinting vertices itself (not via a shader). */
    public static boolean available() {
        if (broken || !resolve())
            return false;
        try {
            if (Boolean.TRUE.equals(shaderActive.invoke(null)))
                return false;
            return Boolean.TRUE.equals(active.invoke(null)) || Boolean.TRUE.equals(skyActive.invoke(null));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            fail(e);
            return false;
        }
    }

    /** Suspends Pulsar's own vertex handling for the vertices about to be written. */
    public static int suppress() {
        if (!resolve())
            return 0;
        try {
            return (Integer) suppressVertices.invoke(null);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            fail(e);
            return 0;
        }
    }

    public static void restore(int token) {
        if (token == 0 || !resolve())
            return;
        try {
            restoreVertices.invoke(null, token);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            fail(e);
        }
    }

    /**
     * Re-tints the vertices written since {@code start}, the way Pulsar's
     * block-renderer hook does for a normal block.
     */
    public static void tint(BufferBuilder buffer, int start) {
        if (buffer == null || !resolve() || broken)
            return;
        try {
            VertexFormat format = buffer.getVertexFormat();
            int end = buffer.getVertexCount();
            if (!format.hasColor() || end <= start)
                return;
            ByteBuffer bytes = buffer.getByteBuffer();
            int stride = format.getSize();
            int colorOffset = format.getColorOffset();
            int uv = format.hasUvOffset(1) ? format.getUvOffsetById(1) : -1;
            double ox = (Double) xOffset.invoke(buffer);
            double oy = (Double) yOffset.invoke(buffer);
            double oz = (Double) zOffset.invoke(buffer);
            boolean bigEndian = ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN;
            for (int i = start; i < end; i++) {
                int at = i * stride;
                int colour = bytes.getInt(at + colorOffset);
                if (bigEndian)
                    colour = Integer.reverseBytes(colour);
                int material = colour;
                colour = (Integer) tintAbgr.invoke(null, colour,
                        (double) bytes.getFloat(at) - ox,
                        (double) bytes.getFloat(at + 4) - oy,
                        (double) bytes.getFloat(at + 8) - oz);
                if (uv >= 0) {
                    // Same arguments as the block hook: the lightmap pair in levels.
                    colour = (Integer) preserve.invoke(null, material, colour,
                            Short.toUnsignedInt(bytes.getShort(at + uv)) / 16f,
                            Short.toUnsignedInt(bytes.getShort(at + uv + 2)) / 16f);
                }
                bytes.putInt(at + colorOffset, bigEndian ? Integer.reverseBytes(colour) : colour);
            }
            Object state = drawState.invoke(null);
            if (Boolean.TRUE.equals(stateActive.invoke(state))) {
                long tag = (Long) generation.invoke(buffer);
                mark.invoke(null, buffer, start, tag);
            }
            if (vanillaRendered != null)
                vanillaRendered.setBoolean(null, true);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            fail(e);
        }
    }

    private static boolean resolve() {
        if (resolved)
            return present;
        resolved = true;
        try {
            Class<?> client = Class.forName("com.sumirelabs.pulsar.rgb.RgbClient");
            Class<?> ambient = Class.forName("com.sumirelabs.pulsar.rgb.RgbAmbientFloor");
            Class<?> context = Class.forName("com.sumirelabs.pulsar.rgb.RgbDrawContext");
            Class<?> state = Class.forName("com.sumirelabs.pulsar.rgb.RgbDrawState");
            Class<?> runtime = Class.forName("com.sumirelabs.pulsar.rgb.RgbShaderRuntime");
            Class<?> vertexTint = Class.forName("com.sumirelabs.pulsar.rgb.RgbVertexTint");
            Class<?> tags = Class.forName("com.sumirelabs.pulsar.rgb.RgbDynamicVertices");
            Class<?> offsets = Class.forName("com.sumirelabs.pulsar.rgb.RgbBuffer");
            active = client.getMethod("active");
            skyActive = client.getMethod("skyActive");
            tintAbgr = client.getMethod("tintAbgr", int.class, double.class, double.class, double.class);
            vanillaRendered = client.getField("vanillaRendered");
            preserve = ambient.getMethod("preserve", int.class, int.class, float.class, float.class);
            shaderActive = runtime.getMethod("active");
            suppressVertices = context.getMethod("suppressVertices");
            restoreVertices = context.getMethod("restoreVertices", int.class);
            drawState = context.getMethod("state");
            stateActive = state.getMethod("active");
            mark = vertexTint.getMethod("mark", BufferBuilder.class, int.class, long.class);
            generation = tags.getMethod("pulsar$rgbGeneration");
            xOffset = offsets.getMethod("pulsar$xOffset");
            yOffset = offsets.getMethod("pulsar$yOffset");
            zOffset = offsets.getMethod("pulsar$zOffset");
            present = true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            present = false;
        }
        return present;
    }

    private static void fail(Throwable e) {
        broken = true;
        if (failedOnce)
            return;
        failedOnce = true;
        ArchitectureLog.error("ArchitectureCraft: Pulsar RGB tint for shapes failed; "
                + "shapes keep their scalar light. {}", e.toString());
    }
}
