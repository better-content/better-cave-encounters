package com.bettercontent.bettercaveencounters;

import com.bettercontent.bettercaveencounters.diagnostics.PlaytestLog;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/** Native lifecycle adapter; retains no level, player or entity references. */
@Mod.EventBusSubscriber(modid = "better_cave_encounters")
public final class PlaytestDiagnostics {
    public static final PlaytestLog LOG = new PlaytestLog("better_cave_encounters");
    private PlaytestDiagnostics() {}

    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) LOG.summary("server");
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { LOG.reset("server"); }

    @Mod.EventBusSubscriber(modid = "better_cave_encounters", value = net.minecraftforge.api.distmarker.Dist.CLIENT)
    public static final class ClientLifecycle {
        @SubscribeEvent public static void disconnect(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
            LOG.reset("client");
        }
    }

    @Mod.EventBusSubscriber(modid = "better_cave_encounters", bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Startup {
        @SubscribeEvent public static void setup(FMLCommonSetupEvent event) {
            String version = net.minecraftforge.fml.ModList.get().getModContainerById("better_cave_encounters")
                    .map(container -> container.getModInfo().getVersion().toString()).orElse("UNKNOWN");
            var modFile = net.minecraftforge.fml.ModList.get().getModFileById("better_cave_encounters");
            String source = modFile == null ? "UNKNOWN" : LOG.sourceIdentity(modFile.getFile().findResource("META-INF", "better-content-source.properties"));
            LOG.status("common", version, source, () -> PlaytestLog.fields(
                    "enabled", DirectorConfig.ENABLED.get(), "enabled_source", "common_config",
                    "global_mob_cap", DirectorConfig.GLOBAL_DIRECTOR_CAP.get(), "spawns_per_tick", DirectorConfig.MAX_SPAWNS_PER_TICK.get(),
                    "spawns_per_second", DirectorConfig.MAX_SPAWNS_PER_SECOND.get(),
                    "integration_compat", net.minecraftforge.fml.ModList.get().isLoaded("better_compat_fixes"),
                    "integration_door", net.minecraftforge.fml.ModList.get().isLoaded("better_deaths_door")));
        }
    }
}
