package com.mistaboom.essence_ascendance.network;

import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import com.mistaboom.essence_ascendance.mapping.ItemEssenceMappingRegistry;
import com.mistaboom.essence_ascendance.mapping.ItemEssenceMappingResult;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import dev.architectury.networking.NetworkManager;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/*
 * Synchronizes the authoritative resolved item -> Attribute Essence display
 * table to clients.
 *
 * This deliberately sends resolved ITEM IDs rather than raw mapping rules:
 * - server config stays authoritative;
 * - tag membership is resolved on the server;
 * - config removals/replacements/priority are already settled;
 * - the client needs no access to the server's config folder.
 */
public final class ItemEssenceTooltipSyncService {

    private static final int RETRY_INTERVAL_TICKS = 20;
    private static final int ENTRIES_PER_CHUNK = 192;

    private static final Map<ServerPlayer, Long> LAST_SENT_GENERATION =
            new WeakHashMap<>();

    private static volatile Snapshot cachedSnapshot =
            null;

    private static boolean initialized =
            false;

    private ItemEssenceTooltipSyncService() {
    }

    public static void init() {
        if (initialized) {
            return;
        }

        if (Platform.getEnvironment()
                == Env.SERVER) {

            NetworkManager.registerS2CPayloadType(
                    ItemEssenceTooltipPayload.TYPE,
                    ItemEssenceTooltipPayload.CODEC
            );
        }

        PlayerEvent.PLAYER_JOIN.register(
                ItemEssenceTooltipSyncService::forceSync
        );

        /*
         * If payload negotiation was not ready at the exact join callback,
         * retry cheaply until the current mapping generation is delivered.
         */
        TickEvent.PLAYER_POST.register(
                player -> {
                    if (player instanceof ServerPlayer serverPlayer
                            && serverPlayer.tickCount
                            % RETRY_INTERVAL_TICKS
                            == 0) {

                        syncIfChanged(
                                serverPlayer
                        );
                    }
                }
        );

        PlayerEvent.PLAYER_QUIT.register(
                ItemEssenceTooltipSyncService::forget
        );

        initialized =
                true;
    }

    public static void forceSync(
            ServerPlayer player
    ) {
        send(
                player,
                true
        );
    }

    public static void syncIfChanged(
            ServerPlayer player
    ) {
        send(
                player,
                false
        );
    }

    public static void syncAll(
            MinecraftServer server
    ) {
        Snapshot snapshot =
                snapshot();

        for (ServerPlayer player :
                server.getPlayerList()
                        .getPlayers()) {

            sendSnapshot(
                    player,
                    snapshot,
                    true
            );
        }
    }

    public static void forget(
            ServerPlayer player
    ) {
        LAST_SENT_GENERATION.remove(
                player
        );
    }

    private static void send(
            ServerPlayer player,
            boolean force
    ) {
        Snapshot snapshot =
                snapshot();

        sendSnapshot(
                player,
                snapshot,
                force
        );
    }

    private static void sendSnapshot(
            ServerPlayer player,
            Snapshot snapshot,
            boolean force
    ) {
        if (!NetworkManager.canPlayerReceive(
                player,
                ItemEssenceTooltipPayload.TYPE
        )) {
            return;
        }

        Long previousGeneration =
                LAST_SENT_GENERATION.get(
                        player
                );

        if (!force
                && previousGeneration != null
                && previousGeneration
                == snapshot.generation()) {
            return;
        }

        for (ItemEssenceTooltipPayload chunk :
                snapshot.chunks()) {

            NetworkManager.sendToPlayer(
                    player,
                    chunk
            );
        }

        LAST_SENT_GENERATION.put(
                player,
                snapshot.generation()
        );
    }

    private static Snapshot snapshot() {
        long generation =
                ItemEssenceMappingRegistry.generation();

        Snapshot existing =
                cachedSnapshot;

        if (existing != null
                && existing.generation()
                == generation) {
            return existing;
        }

        synchronized (ItemEssenceTooltipSyncService.class) {
            existing =
                    cachedSnapshot;

            if (existing != null
                    && existing.generation()
                    == generation) {
                return existing;
            }

            Snapshot rebuilt =
                    buildSnapshot(
                            generation
                    );

            cachedSnapshot =
                    rebuilt;

            return rebuilt;
        }
    }

    private static Snapshot buildSnapshot(
            long generation
    ) {
        List<Item> items =
                BuiltInRegistries.ITEM
                        .stream()
                        .sorted(
                                Comparator.comparing(
                                        item ->
                                                BuiltInRegistries.ITEM
                                                        .getKey(
                                                                item
                                                        )
                                                        .toString()
                                )
                        )
                        .toList();

        List<ItemEssenceTooltipPayload.Entry> entries =
                new ArrayList<>();

        for (Item item :
                items) {

            ItemStack stack =
                    new ItemStack(
                            item
                    );

            ItemEssenceMappingResult result =
                    ItemEssenceMappingRegistry.resolve(
                            stack
                    );

            /*
             * A winning empty-output mapping means "blocked/no Essence".
             * It should therefore have no tooltip line.
             */
            if (!result.mapped()
                    || result.outputs()
                            .isEmpty()) {
                continue;
            }

            List<ItemEssenceTooltipPayload.Output> outputs =
                    result.outputs()
                            .entrySet()
                            .stream()
                            .sorted(
                                    Comparator
                                            .<Map.Entry<EssenceDefinition, Long>>comparingInt(
                                                    entry ->
                                                            essenceOrder(
                                                                    entry.getKey()
                                                            )
                                            )
                                            .thenComparing(
                                                    entry ->
                                                            entry.getKey()
                                                                    .id()
                                                                    .toString()
                                            )
                            )
                            .map(
                                    entry ->
                                            new ItemEssenceTooltipPayload.Output(
                                                    entry.getKey()
                                                            .id()
                                                            .toString(),
                                                    entry.getValue()
                                            )
                            )
                            .toList();

            entries.add(
                    new ItemEssenceTooltipPayload.Entry(
                            BuiltInRegistries.ITEM
                                    .getKey(
                                            item
                                    )
                                    .toString(),
                            outputs
                    )
            );
        }

        int chunkCount =
                Math.max(
                        1,
                        (
                                entries.size()
                                        + ENTRIES_PER_CHUNK
                                        - 1
                        )
                                / ENTRIES_PER_CHUNK
                );

        List<ItemEssenceTooltipPayload> chunks =
                new ArrayList<>(
                        chunkCount
                );

        if (entries.isEmpty()) {
            chunks.add(
                    new ItemEssenceTooltipPayload(
                            generation,
                            0,
                            1,
                            List.of()
                    )
            );

        } else {
            for (int chunkIndex = 0;
                 chunkIndex < chunkCount;
                 chunkIndex++) {

                int from =
                        chunkIndex
                                * ENTRIES_PER_CHUNK;

                int to =
                        Math.min(
                                entries.size(),
                                from
                                        + ENTRIES_PER_CHUNK
                        );

                chunks.add(
                        new ItemEssenceTooltipPayload(
                                generation,
                                chunkIndex,
                                chunkCount,
                                entries.subList(
                                        from,
                                        to
                                )
                        )
                );
            }
        }

        return new Snapshot(
                generation,
                List.copyOf(
                        chunks
                )
        );
    }

    private static int essenceOrder(
            EssenceDefinition essence
    ) {
        return switch (essence.id()
                .getPath()) {

            case "offense" -> 0;
            case "defense" -> 1;
            case "vitality" -> 2;
            case "mobility" -> 3;
            case "gathering" -> 4;
            case "utility" -> 5;
            default -> 100;
        };
    }

    private record Snapshot(
            long generation,
            List<ItemEssenceTooltipPayload> chunks
    ) {
        private Snapshot {
            chunks =
                    List.copyOf(
                            chunks
                    );
        }
    }
}
