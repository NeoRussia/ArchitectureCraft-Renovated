package com.elytradev.architecture.client;

import com.elytradev.architecture.common.ArchitectureConfig;
import com.elytradev.architecture.common.ArchitectureLog;
import com.elytradev.architecture.common.ArchitectureMod;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.fml.client.config.GuiConfig;
import net.minecraftforge.fml.client.config.IConfigElement;

import java.util.ArrayList;
import java.util.List;

/**
 * The config screen behind the Mod Options button, editing the same
 * Configuration the render code reads.
 *
 * <p>The entries are the two properties themselves rather than the contents of
 * a category: asking the category for its children produced an empty list, so
 * the screen opened with nothing in it.</p>
 */
public class ArchitectureConfigGui extends GuiConfig {

    public ArchitectureConfigGui(GuiScreen parent) {
        super(parent, elements(), ArchitectureMod.MOD_ID, false, false,
                "ArchitectureCraft: Renovated");
    }

    private static List<IConfigElement> elements() {
        List<IConfigElement> list = new ArrayList<IConfigElement>();
        if (ArchitectureConfig.getConfig() == null) {
            ArchitectureLog.error("The config screen was opened before the configuration was loaded.");
            return list;
        }
        // The stored properties, so their comments survive and the GUI edits the
        // same instance the render code reads.
        if (ArchitectureConfig.getOverlays() != null)
            list.add(new ConfigElement(ArchitectureConfig.getOverlays()));
        if (ArchitectureConfig.getShaderMaterialIds() != null)
            list.add(new ConfigElement(ArchitectureConfig.getShaderMaterialIds()));
        return list;
    }

}

