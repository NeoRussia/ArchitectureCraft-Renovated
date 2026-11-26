package com.elytradev.architecture.core;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;

import java.util.Map;

/**
 * Loads {@link PulsarShapeTransformer} early enough to change the shape block's
 * class before it is defined.
 *
 * <p>Pulsar only asks a <em>block class</em> for RGB emission and transmittance
 * ({@code instanceof PositionalColoredLightSource} / {@code ...Translucency}),
 * and a shape's material lives in its tile entity, so the block has to answer
 * per position. Implementing those interfaces needs Pulsar's API on the compile
 * classpath, which the mod deliberately does not have. Adding them to the class
 * at load time needs nothing but ASM, which Forge already provides, so this is
 * the dependency-free route.</p>
 */
@IFMLLoadingPlugin.Name("ArchitectureCraftPulsarShape")
@IFMLLoadingPlugin.MCVersion("1.12.2")
public final class PulsarShapeCoremod implements IFMLLoadingPlugin {

    @Override
    public String[] getASMTransformerClass() {
        return new String[]{PulsarShapeTransformer.class.getName()};
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
