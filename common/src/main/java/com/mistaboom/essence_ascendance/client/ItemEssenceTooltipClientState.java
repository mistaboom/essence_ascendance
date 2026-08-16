package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import com.mistaboom.essence_ascendance.essence.EssenceRegistry;
import com.mistaboom.essence_ascendance.network.ItemEssenceTooltipPayload;
import dev.architectury.event.events.client.ClientPlayerEvent;
import dev.architectury.networking.NetworkManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.Map;

/*
 * Client-only cache for the globally visible item -> Attribute Essence tooltip.
 *
 * Presentation goal: ProjectE-style information density rather than another
 * large tooltip section.
 *
 * Example:
 *   Essence: 20 Offense
 *
 * Multiple outputs remain compact:
 *   Essence: 8 Offense • 3 Utility
 *
 * Extremely broad mappings can use a second continuation line after three
 * outputs instead of making the tooltip excessively wide.
 */
public final class ItemEssenceTooltipClientState {

    private static final int OUTPUTS_PER_LINE = 3;

    private static volatile Map<ResourceLocation, List<DisplayOutput>>
            OUTPUTS_BY_ITEM = Map.of();

    private static long pendingGeneration =
            -1L;

    private static int pendingChunkCount =
            0;

    private static final Map<Integer, List<ItemEssenceTooltipPayload.Entry>>
            PENDING_CHUNKS = new HashMap<>();

    private static boolean initialized =
            false;

    private ItemEssenceTooltipClientState() {
    }

    public static void init() {
        if (initialized) {
            return;
        }

        NetworkManager.registerReceiver(
                NetworkManager.Side.S2C,
                ItemEssenceTooltipPayload.TYPE,
                ItemEssenceTooltipPayload.CODEC,
                (payload, context) ->
                        context.queue(
                                () -> accept(
                                        payload
                                )
                        )
        );

        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register(
                player ->
                        clear()
        );

        initialized =
                true;

        EssenceAscendance.LOGGER.info(
                "Registered item Attribute Essence tooltip synchronization and display"
        );
    }

    private static void accept(
            ItemEssenceTooltipPayload payload
    ) {
        /*
         * Chunk zero is the start of every server snapshot. Reset even when the
         * numeric generation matches our current cache, because a player can
         * disconnect from one server and join a different server whose local
         * mapping generation happens to use the same number.
         */
        if (payload.chunkIndex()
                == 0
                || pendingGeneration
                != payload.mappingGeneration()
                || pendingChunkCount
                != payload.chunkCount()) {

            pendingGeneration =
                    payload.mappingGeneration();

            pendingChunkCount =
                    payload.chunkCount();

            PENDING_CHUNKS.clear();
        }

        PENDING_CHUNKS.put(
                payload.chunkIndex(),
                payload.entries()
        );

        if (PENDING_CHUNKS.size()
                != pendingChunkCount) {
            return;
        }

        Map<ResourceLocation, List<DisplayOutput>> rebuilt =
                new LinkedHashMap<>();

        for (int chunkIndex = 0;
             chunkIndex < pendingChunkCount;
             chunkIndex++) {

            List<ItemEssenceTooltipPayload.Entry> entries =
                    PENDING_CHUNKS.get(
                            chunkIndex
                    );

            if (entries == null) {
                return;
            }

            for (ItemEssenceTooltipPayload.Entry entry :
                    entries) {

                ResourceLocation itemId =
                        ResourceLocation.tryParse(
                                entry.itemId()
                        );

                if (itemId == null) {
                    continue;
                }

                List<DisplayOutput> outputs =
                        new ArrayList<>();

                for (ItemEssenceTooltipPayload.Output output :
                        entry.outputs()) {

                    ResourceLocation essenceId =
                            ResourceLocation.tryParse(
                                    output.essenceId()
                            );

                    if (essenceId == null
                            || output.amount()
                            <= 0L) {
                        continue;
                    }

                    outputs.add(
                            new DisplayOutput(
                                    essenceId,
                                    output.amount()
                            )
                    );
                }

                if (!outputs.isEmpty()) {
                    rebuilt.put(
                            itemId,
                            List.copyOf(
                                    outputs
                            )
                    );
                }
            }
        }

        OUTPUTS_BY_ITEM =
                Map.copyOf(
                        rebuilt
                );

        PENDING_CHUNKS.clear();
        pendingGeneration =
                -1L;
        pendingChunkCount =
                0;

        /*
         * If JEI has already indexed ingredients, make its $ tooltip-search
         * index pick up this new authoritative server mapping snapshot.
         */
        JeiTooltipSearchRefreshBridge.requestRefresh();
    }

    public static void appendToGeneratedTooltip(
            ItemStack stack,
            List<Component> tooltip,
            Item.TooltipContext tooltipContext,
            TooltipFlag tooltipFlag
    ) {
        if (stack == null
                || stack.isEmpty()) {
            return;
        }

        ResourceLocation itemId =
                BuiltInRegistries.ITEM
                        .getKey(
                                stack.getItem()
                        );

        List<DisplayOutput> outputs =
                OUTPUTS_BY_ITEM.get(
                        itemId
                );

        if (outputs == null
                || outputs.isEmpty()) {
            return;
        }

        for (int from = 0;
             from < outputs.size();
             from += OUTPUTS_PER_LINE) {

            int to =
                    Math.min(
                            outputs.size(),
                            from
                                    + OUTPUTS_PER_LINE
                    );

            MutableComponent line =
                    Component.literal(
                                    from == 0
                                            ? "Essence: "
                                            : "         "
                            )
                            .withStyle(
                                    ChatFormatting.DARK_GRAY
                            );

            for (int index = from;
                 index < to;
                 index++) {

                if (index > from) {
                    line.append(
                            Component.literal(
                                            " \u2022 "
                                    )
                                    .withStyle(
                                            ChatFormatting.DARK_GRAY
                                    )
                    );
                }

                DisplayOutput output =
                        outputs.get(
                                index
                        );

                line.append(
                        Component.literal(
                                        formatAmount(
                                                output.amount()
                                        )
                                                + " "
                                                + shortName(
                                                        output.essenceId()
                                                )
                                )
                                .withStyle(
                                        colorFor(
                                                output.essenceId()
                                        )
                                )
                );
            }

            tooltip.add(
                    line
            );
        }
    }


    /*
     * Search words corresponding to the visible compact acquisition tooltip.
     *
     * Example visible line:
     *   Essence: 180 Offense • 15 Utility
     *
     * Search words:
     *   essence:, 180, offense, •, 15, utility
     *
     * JEI's own tokenizer similarly indexes whitespace-separated words.
     */
    public static Set<String> getSearchTerms(
            ItemStack stack
    ) {
        if (stack == null
                || stack.isEmpty()) {
            return Set.of();
        }

        ResourceLocation itemId =
                BuiltInRegistries.ITEM
                        .getKey(
                                stack.getItem()
                        );

        List<DisplayOutput> outputs =
                OUTPUTS_BY_ITEM.get(
                        itemId
                );

        if (outputs == null
                || outputs.isEmpty()) {
            return Set.of();
        }

        Set<String> result =
                new LinkedHashSet<>();

        result.add(
                "essence"
        );

        for (DisplayOutput output :
                outputs) {

            result.add(
                    Long.toString(
                            output.amount()
                    )
            );

            String name =
                    shortName(
                            output.essenceId()
                    );

            for (String word :
                    name.toLowerCase(
                                    Locale.ROOT
                            )
                            .trim()
                            .split(
                                    "\\s+"
                            )) {

                if (!word.isBlank()) {
                    result.add(
                            word
                    );
                }
            }
        }

        return Set.copyOf(
                result
        );
    }


    private static String shortName(
            ResourceLocation essenceId
    ) {
        String displayName =
                EssenceRegistry
                        .get(
                                essenceId
                        )
                        .map(
                                EssenceDefinition::displayName
                        )
                        .orElseGet(
                                () ->
                                        titleCase(
                                                essenceId.getPath()
                                        )
                        );

        String suffix =
                " Essence";

        if (displayName.endsWith(
                suffix
        )) {
            return displayName.substring(
                    0,
                    displayName.length()
                            - suffix.length()
            );
        }

        return displayName;
    }

    private static ChatFormatting colorFor(
            ResourceLocation essenceId
    ) {
        return switch (essenceId.getPath()) {
            case "offense" -> ChatFormatting.RED;
            case "defense" -> ChatFormatting.BLUE;
            case "vitality" -> ChatFormatting.DARK_RED;
            case "mobility" -> ChatFormatting.AQUA;
            case "gathering" -> ChatFormatting.GREEN;
            case "utility" -> ChatFormatting.GOLD;
            default -> ChatFormatting.GRAY;
        };
    }

    private static String formatAmount(
            long amount
    ) {
        return String.format(
                java.util.Locale.ROOT,
                "%,d",
                amount
        );
    }

    private static String titleCase(
            String path
    ) {
        String[] words =
                path.split(
                        "_"
                );

        StringBuilder result =
                new StringBuilder();

        for (String word :
                words) {

            if (word.isEmpty()) {
                continue;
            }

            if (!result.isEmpty()) {
                result.append(
                        ' '
                );
            }

            result.append(
                    Character.toUpperCase(
                            word.charAt(
                                    0
                            )
                    )
            );

            if (word.length()
                    > 1) {
                result.append(
                        word.substring(
                                1
                        )
                );
            }
        }

        return result.toString();
    }

    private static void clear() {
        OUTPUTS_BY_ITEM =
                Map.of();

        PENDING_CHUNKS.clear();

        pendingGeneration =
                -1L;

        pendingChunkCount =
                0;
    }

    private record DisplayOutput(
            ResourceLocation essenceId,
            long amount
    ) {
    }
}
