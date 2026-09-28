package com.mistaboom.essence_ascendance.mapping;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.balance.generated.GeneratedBalanceService;
import com.mistaboom.essence_ascendance.valuation.ProceduralValuationEngine;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.TickEvent;
import dev.architectury.platform.Platform;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
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
        /*
         * Both loaders publish the Overworld after inserting it into the server's
         * level map, before MinecraftServer.setInitialSpawn can request chunks.
         * Recipes, tags, registries and an unspawned-entity context are available.
         * Later dimensions reuse the one installed profile; SERVER_STARTED is too
         * late because initial spawn search has already generated terrain by then.
         */
        LifecycleEvent.SERVER_LEVEL_LOAD.register(level -> {
            if (level.dimension() != Level.OVERWORLD) return;
            MinecraftServer server = level.getServer();
            if (activeServer == server) return;
            if (activeServer != null) throw new IllegalStateException("A previous server's balance lifecycle was not stopped");
            activeServer = server;
            observedResources = server.getResourceManager();
            observedRecipes = server.getRecipeManager();
            if (!load(false, "overworld_server_level_load").successful()) throw new IllegalStateException(
                    "Essence Ascendance generated balance could not load before initial chunk generation: "
                            + ItemEssenceMappingRegistry.lastReload().errors()
                            + ". Inspect the preceding balance error in latest.log. Saved profile: " + generatedCachePath()
                            + "; retain the profile and human TOML inputs for diagnosis.");
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
                com.mistaboom.essence_ascendance.worldgen.PrimarySubstrateDiscovery.clear();
                GeneratedBalanceService.markResourcesChanged("server_resource_or_recipe_manager_replaced");
                EssenceAscendance.LOGGER.warn("Server resources reloaded. Existing generated balance remains active; use /essence admin balance rebuild to analyze changed recipes, tags or loot.");
            }
        });
    }
    public static Path configDirectory() {
        return Platform.getConfigFolder().resolve(EssenceAscendance.MOD_ID);
    }
    public static Path generatedCachePath() { return GeneratedBalanceService.profilePath(); }
    public static synchronized ItemEssenceMappingRegistry.ReloadReport reload() { return load(false, "explicit_profile_reload"); }
    public static synchronized ItemEssenceMappingRegistry.ReloadReport rebuild() { return load(true, "explicit_balance_rebuild"); }
    private static ItemEssenceMappingRegistry.ReloadReport load(boolean rebuild, String reason) {
        try {
            if (activeServer == null) throw new IllegalStateException("A running server is required");
            if (!activeServer.isSameThread()) throw new IllegalStateException("Balance changes must run on the server thread");
            GeneratedBalanceService.load(activeServer, rebuild, reason);
        } catch (Exception error) {
            ItemEssenceMappingRegistry.rejectReload(new ItemEssenceMappingRegistry.LoadSummary(0, 0, 0, 0, 0, List.of()),
                    List.of(error.getMessage() == null ? error.getClass().getName() : error.getMessage()));
            EssenceAscendance.LOGGER.error("Balance load/rebuild rejected; previous valid profile retained", error);
        }
        return ItemEssenceMappingRegistry.lastReload();
    }
}
