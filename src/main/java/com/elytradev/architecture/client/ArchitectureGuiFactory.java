package com.elytradev.architecture.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.client.IModGuiFactory;

import java.util.Set;

/**
 * Gives the mod a Config button in Mod Options, pointing at our own screen.
 *
 * <p>Registered through {@code @Mod(guiFactory = ...)}, which is what the GUI
 * actually consults; relying on the annotation-driven config left the button
 * with nothing to edit.</p>
 */
public class ArchitectureGuiFactory implements IModGuiFactory {

    @Override
    public void initialize(Minecraft minecraft) {
    }

    @Override
    public boolean hasConfigGui() {
        return true;
    }

    @Override
    public GuiScreen createConfigGui(GuiScreen parentScreen) {
        return new ArchitectureConfigGui(parentScreen);
    }

    @Override
    public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() {
        return null;
    }

}
