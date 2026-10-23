package com.bettercontent.bettercaveencounters.worldtest;

import com.bettercontent.bettercaveencounters.DepthDirectorMod;
import com.bettercontent.bettercaveencounters.GeologicalWorldGameTests;
import net.minecraftforge.event.RegisterGameTestsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/** Nonshipping subscriber inside the owning mod, not a second mod/module descriptor. */
@Mod.EventBusSubscriber(modid = DepthDirectorMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class DepthDirectorWorldTestMod {
    // Logical GameTest/template namespace only; deliberately NOT a loaded mod ID.
    public static final String MOD_ID = "better_cave_encounters_world_tests";

    private DepthDirectorWorldTestMod() {}

    @SubscribeEvent public static void registerGameTests(RegisterGameTestsEvent event) {
        Module owningModule = DepthDirectorMod.class.getModule();
        if (!owningModule.isNamed() || !DepthDirectorMod.MOD_ID.equals(owningModule.getName())
                || DepthDirectorWorldTestMod.class.getModule() != owningModule
                || DepthDirectorWorldGameTests.class.getModule() != owningModule
                || GeologicalWorldGameTests.class.getModule() != owningModule) {
            throw new IllegalStateException("World fixture must execute in the same owning FML module");
        }
        com.bettercontent.bettercaveencounters.CatalogueRootAccess.verifyOwningModule(owningModule);
        var descriptorMods = ModList.get().getModFileById(DepthDirectorMod.MOD_ID).getMods().stream()
                .map(info -> info.getModId()).toList();
        if (!descriptorMods.equals(java.util.List.of(DepthDirectorMod.MOD_ID))
                || ModList.get().isLoaded(MOD_ID)) {
            throw new IllegalStateException("World fixture must use only the owning production descriptor");
        }
        com.mojang.logging.LogUtils.getLogger().info(
                "World fixture owning module verified: owningMod={} module={} descriptorMods={} geology={}",
                DepthDirectorMod.MOD_ID, owningModule.getName(), descriptorMods,
                Boolean.getBoolean("bc.geologyQualification"));
        if (Boolean.getBoolean("bc.geologyQualification"))
            event.register(GeologicalWorldGameTests.class);
        else event.register(DepthDirectorWorldGameTests.class);
    }
}
