package com.elytradev.architecture.common;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import java.io.File;

/**
 * The client rendering switches, in a config file this mod opens itself.
 *
 * <p>Deliberately not the annotation-driven config: under this loader the
 * {@code @Config} class was never registered, so the Mod Options GUI edited
 * nothing and the file kept its defaults. Owning the Configuration means the
 * GUI and the code read the same object.</p>
 *
 * <p>The Property instances are kept rather than looked up again: asking
 * {@code Configuration.get} without a comment clears the comment, which is what
 * made the GUI say "No tooltip defined", and it also kept a lookup on the shape
 * render path.</p>
 */
public final class ArchitectureConfig {

    private static final String CATEGORY = Configuration.CATEGORY_GENERAL;
    public static final String OVERLAYS = "Overlay layer";
    public static final String SHADER_MATERIAL_IDS = "Shader material ids";

    private static Configuration config;
    private static Property overlays;
    private static Property shaderMaterialIds;

    private ArchitectureConfig() {
    }

    public static void load(File file) {
        config = new Configuration(file);
        try {
            config.load();
            overlays = config.get(CATEGORY, OVERLAYS, true,
                    "Draw the second layer of materials that have one: the emissive glow of active"
                            + " casings, lamps and sintering bricks, or the active texture of a running"
                            + " machine. Costs extra geometry for those materials only.");
            shaderMaterialIds = config.get(CATEGORY, SHADER_MATERIAL_IDS, true,
                    "Tell a shader pack which block a shape is made of, so its per-block rules"
                            + " (emissive, coloured light, material class) apply to the shape."
                            + " No effect without a shader pack.");
        } catch (RuntimeException e) {
            ArchitectureLog.error("Could not read the configuration, using defaults", e);
        } finally {
            if (config.hasChanged())
                config.save();
        }
    }

    public static Configuration getConfig() {
        return config;
    }

    /**
     * Writes the file after a GUI edit. Forge's config screen only posts
     * ConfigChangedEvent; saving is the mod's job, which is why the values
     * survived the session but not a restart.
     */
    public static void save() {
        if (config != null && config.hasChanged())
            config.save();
    }

    public static Property getOverlays() {
        return overlays;
    }

    public static Property getShaderMaterialIds() {
        return shaderMaterialIds;
    }

    private static boolean value(Property property, String override) {
        if (override != null)
            return !"false".equalsIgnoreCase(override);
        return property == null || property.getBoolean();
    }

    public static boolean overlays() {
        return value(overlays, System.getProperty("architecturecraft.overlays"));
    }

    public static boolean shaderMaterialIds() {
        return value(shaderMaterialIds, System.getProperty("architecturecraft.shaderIds"));
    }

}
