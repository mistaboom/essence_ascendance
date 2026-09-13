package com.mistaboom.essence_ascendance.mapping;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.balance.generated.GeneratedBalanceService;
import com.mistaboom.essence_ascendance.valuation.ProceduralValuationEngine;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.TickEvent;
import dev.architectury.platform.Platform;
import net.minecraft.server.MinecraftServer;
import java.nio.file.Path;
import java.util.List;

/** Lifecycle bridge; generated balance is the sole mapping authority. */
public final class ItemEssenceMappingManager {
    private static boolean initialized;
    private static MinecraftServer activeServer;
    private static Object observedResources, observedRecipes;
    private ItemEssenceMappingManager() { }

    public static synchronized void init() {
        if (initialized) return;
        initialized = true;
        LifecycleEvent.SERVER_STARTED.register(server -> {
            activeServer = server;
            observedResources = server.getResourceManager();
            observedRecipes = server.getRecipeManager();
            if (!reload().successful()) throw new IllegalStateException(
                    "Essence Ascendance generated balance could not load: " + ItemEssenceMappingRegistry.lastReload().errors());
        });
        LifecycleEvent.SERVER_STOPPED.register(server -> {
            if (activeServer != server) return;
            activeServer = null; observedResources = null; observedRecipes = null;
            ItemEssenceMappingRegistry.clear();
            ProceduralValuationEngine.clear();
            GeneratedBalanceService.clear();
        });
        TickEvent.SERVER_POST.register(server -> {
            if (activeServer != server) return;
            if (observedResources != server.getResourceManager() || observedRecipes != server.getRecipeManager()) {
                observedResources = server.getResourceManager();
                observedRecipes = server.getRecipeManager();
                ProceduralValuationEngine.clear();
                GeneratedBalanceService.markResourcesChanged();
                EssenceAscendance.LOGGER.warn("Server resources reloaded. Existing generated balance remains active; use /essence debug balance rebuild to analyze changed recipes, tags or loot.");
            }
        });
    }
    public static Path configDirectory() {
        return Platform.getConfigFolder().resolve(EssenceAscendance.MOD_ID);
    }
    public static Path generatedCachePath() { return configDirectory().resolve("generated_balance.json"); }
    public static synchronized ItemEssenceMappingRegistry.ReloadReport reload() { return load(false); }
    public static synchronized ItemEssenceMappingRegistry.ReloadReport rebuild() { return load(true); }
    private static ItemEssenceMappingRegistry.ReloadReport load(boolean rebuild) {
        try {
            if (activeServer == null) throw new IllegalStateException("A running server is required");
            if (!activeServer.isSameThread()) throw new IllegalStateException("Balance changes must run on the server thread");
            GeneratedBalanceService.load(activeServer, rebuild);
        } catch (Exception error) {
            ItemEssenceMappingRegistry.rejectReload(new ItemEssenceMappingRegistry.LoadSummary(0, 0, 0, 0, 0, List.of()),
                    List.of(error.getMessage() == null ? error.getClass().getName() : error.getMessage()));
            EssenceAscendance.LOGGER.error("Balance load/rebuild rejected; previous valid profile retained", error);
        }
        return ItemEssenceMappingRegistry.lastReload();
    }
}
