package com.elytradev.architecture.client;

import com.elytradev.architecture.client.render.MaterialOverlay;
import com.elytradev.architecture.common.ArchitectureConfig;
import com.elytradev.architecture.common.ArchitectureMod;
import com.elytradev.architecture.compat.demonica.ShaderMaterialIds;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Applies a config change without a restart.
 *
 * <p>Both switches are read at the point of use, but their per-material caches
 * and the chunk meshes already built still hold the old answer. Clearing the
 * caches and asking for a chunk reload makes a toggle in Mod Options take effect
 * within a second instead of at the next rebuild.</p>
 */
public final class ClientConfigEvents {

    @SubscribeEvent
    public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (!ArchitectureMod.MOD_ID.equals(event.getModID()))
            return;
        ArchitectureConfig.save();
        MaterialOverlay.clearCache();
        ShaderMaterialIds.clearCache();
        Minecraft.getMinecraft().renderGlobal.loadRenderers();
    }

}
