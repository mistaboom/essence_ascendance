package com.mistaboom.essence_ascendance.client.ore;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.client.ClientPacketDispatch;
import com.mistaboom.essence_ascendance.network.LatentOreCatalogPayload;
import com.mistaboom.essence_ascendance.ore.LatentOreHost;
import dev.architectury.networking.NetworkManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.resources.ResourceLocation;
import java.util.Set;

/** One bounded catalog update at connection setup; encounters and placements never reload resources. */
public final class LatentOreClientCatalog {
    private static boolean initialized;
    private static boolean reloadInFlight;
    private static long catalogRevision;
    private static ClientPacketListener catalogConnection;
    private static Set<ResourceLocation> catalogHosts = Set.of();
    private static Runnable ingredientRefreshListener;
    private LatentOreClientCatalog() {}
    public static void init() {
        if (initialized) return;
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, LatentOreCatalogPayload.TYPE, LatentOreCatalogPayload.CODEC,
                (payload, context) -> ClientPacketDispatch.queue(context, () -> {
                    ClientPacketListener connection = Minecraft.getInstance().getConnection();
                    if (catalogConnection != connection || !catalogHosts.equals(payload.hosts())) catalogRevision++;
                    catalogConnection = connection;
                    catalogHosts = Set.copyOf(payload.hosts());
                    LatentOreHost.setCreativeHosts(payload.hosts());
                    com.mistaboom.essence_ascendance.mixin.LatentOreCreativeTabCacheAccess.essence$invalidate(null);
                    refreshIngredients();
                    LatentOreSprites.setHosts(payload.hosts());
                    ensurePrepared();
                }));
        initialized = true;
    }

    /** The optional recipe viewer attaches only while its runtime is available. No JEI dependency here. */
    public static void setIngredientRefreshListener(Runnable listener) {
        ingredientRefreshListener = listener;
    }

    private static void refreshIngredients() {
        if (ingredientRefreshListener != null) ingredientRefreshListener.run();
    }

    /** Coalesce repeated snapshots; a newer connection/catalog gets at most one follow-up reload. */
    private static void ensurePrepared() {
        Minecraft minecraft = Minecraft.getInstance();
        if (reloadInFlight || catalogConnection == null || catalogConnection != minecraft.getConnection()
                || !catalogConnection.getConnection().isConnected() || !LatentOreSprites.needsPreparation()) return;
        reloadInFlight = true;
        long revision = catalogRevision;
        EssenceAscendance.LOGGER.info("Preparing Latent Ore atlas for {} server-selected hosts", catalogHosts.size());
        minecraft.reloadResourcePacks().whenComplete((ignored, failure) -> minecraft.execute(() -> {
            reloadInFlight = false;
            if (failure != null) EssenceAscendance.LOGGER.warn("Latent Ore catalog resource reload failed", failure);
            if (catalogRevision != revision) {
                ensurePrepared();
            }
            if (failure == null && !reloadInFlight && !LatentOreSprites.needsPreparation()
                    && catalogConnection != null && minecraft.getConnection() == catalogConnection
                    && catalogConnection.getConnection().isConnected() && minecraft.level != null) {
                minecraft.levelRenderer.allChanged();
            }
        }));
    }

    public static void clear() {
        catalogRevision++;
        catalogConnection = null;
        catalogHosts = Set.of();
        LatentOreHost.setCreativeHosts(Set.of());
        com.mistaboom.essence_ascendance.mixin.LatentOreCreativeTabCacheAccess.essence$invalidate(null);
        refreshIngredients();
        LatentOreSprites.setHosts(Set.of());
        LatentOreModels.invalidate();
        LatentOreParticles.clear();
    }
}
