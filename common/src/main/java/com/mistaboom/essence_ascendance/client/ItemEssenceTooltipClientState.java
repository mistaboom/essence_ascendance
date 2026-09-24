package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.balance.economy.FractionalAmountService;
import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import com.mistaboom.essence_ascendance.essence.EssenceRegistry;
import com.mistaboom.essence_ascendance.text.EssenceText;
import com.mistaboom.essence_ascendance.visual.AscendancePalette;
import com.mistaboom.essence_ascendance.network.ItemEssenceTooltipPayload;
import dev.architectury.networking.NetworkManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
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
import java.util.Objects;
import java.util.Set;
import java.util.Map;

/*
 * Client-only cache for the globally visible item -> Essence tooltip.
 *
 * Presentation goal: ProjectE-style information density rather than another
 * large tooltip section.
 *
 * Example:
 *   Essence: 20 Off
 *
 * Multiple outputs remain compact:
 *   Essence: 8 Off • 3 Util
 *
 * Lines wrap by rendered width, keeping each amount/name pair together. Full
 * Essence names remain available to JEI's search index when names are abbreviated.
 */
public final class ItemEssenceTooltipClientState {

    private static volatile YieldSnapshot YIELD_SNAPSHOT = YieldSnapshot.unavailable();

    private static long installedGeneration = -1L;

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
                        ClientPacketDispatch.queue(context,
                                () -> accept(
                                        payload
                                )
                        )
        );

        initialized =
                true;

        EssenceAscendance.LOGGER.info(
                "Registered item Essence tooltip synchronization and display"
        );
    }

    static void accept(
            ItemEssenceTooltipPayload payload
    ) {
        accept(payload, JeiTooltipSearchRefreshBridge::requestRefresh);
    }

    static void acceptForTesting(ItemEssenceTooltipPayload payload) {
        accept(payload, () -> { });
    }

    private static void accept(ItemEssenceTooltipPayload payload, Runnable refreshSearch) {
        // Only chunk zero may start a snapshot. A late older chunk must neither
        // replace an installed table nor destroy a newer in-progress assembly.
        long generation = payload.mappingGeneration();
        if (generation < installedGeneration || generation < pendingGeneration) {
            return;
        }
        if (payload.chunkIndex() == 0) {
            pendingGeneration = generation;
            pendingChunkCount = payload.chunkCount();
            PENDING_CHUNKS.clear();
        } else if (generation != pendingGeneration || payload.chunkCount() != pendingChunkCount) {
            return;
        }

        PENDING_CHUNKS.putIfAbsent(payload.chunkIndex(), payload.entries());

        if (PENDING_CHUNKS.size()
                != pendingChunkCount) {
            return;
        }

        Map<ResourceLocation, List<Yield>> rebuilt =
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
                    rejectPending();
                    return;
                }

                List<Yield> outputs =
                        new ArrayList<>();

                for (ItemEssenceTooltipPayload.Output output :
                        entry.outputs()) {

                    ResourceLocation essenceId =
                            ResourceLocation.tryParse(
                                    output.essenceId()
                            );

                    if (essenceId == null
                            || output.microUnits()
                            <= 0L) {
                        rejectPending();
                        return;
                    }

                    outputs.add(
                            new Yield(
                                    essenceId,
                                    output.microUnits()
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

        long completedGeneration = pendingGeneration;
        Map<ResourceLocation, List<Yield>> completed = Map.copyOf(rebuilt);
        List<YieldRow> rows = completed.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new YieldRow(entry.getKey(), entry.getValue()))
                .toList();
        // Publish every completed projection together. Readers can never observe
        // tooltip data from one generation and browser rows from another.
        YIELD_SNAPSHOT = new YieldSnapshot(completedGeneration, true, completed, rows);
        installedGeneration = completedGeneration;
        PENDING_CHUNKS.clear();
        pendingGeneration =
                -1L;
        pendingChunkCount =
                0;

        /*
         * If JEI has already indexed ingredients, make its $ tooltip-search
         * index pick up this new authoritative server mapping snapshot.
         */
        refreshSearch.run();
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

        List<Yield> outputs =
                YIELD_SNAPSHOT.outputsByItem().get(
                        itemId
                );

        if (outputs == null
                || outputs.isEmpty()) {
            return;
        }

        appendOutputs(outputs, tooltip);
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
    public static void appendDirectEssenceTooltip(
            ResourceLocation essenceId,
            double amount,
            List<Component> tooltip
    ) {
        if (essenceId == null || amount <= 0L || tooltip == null) {
            return;
        }
        appendDirectEssenceTooltipMicros(essenceId, FractionalAmountService.units(amount), tooltip);
    }

    public static void appendDirectEssenceTooltipMicros(ResourceLocation essenceId, long microUnits,
                                                       List<Component> tooltip) {
        if (essenceId == null || microUnits <= 0 || tooltip == null) return;
        appendOutputs(List.of(new Yield(essenceId, microUnits)), tooltip);
    }

    private static void appendOutputs(List<Yield> outputs, List<Component> tooltip) {
        Minecraft client = Minecraft.getInstance();
        Font font = client.font;
        int existingWidth = tooltip.stream().mapToInt(font::width).max().orElse(0);
        int maximumWidth = TooltipLayout.compactWidth(existingWidth, client.getWindow().getGuiScaledWidth());
        Component prefix = EssenceText.tooltip("essence_prefix").withStyle(ChatFormatting.DARK_GRAY);
        Component continuation = Component.literal("  ").withStyle(ChatFormatting.DARK_GRAY);
        Component separator = Component.literal(" \u2022 ").withStyle(ChatFormatting.DARK_GRAY);
        List<Component> tokens = outputs.stream().map(output -> (Component) EssenceText.tooltip(
                        "essence_amount", EssenceYieldFormat.formatMicros(output.microUnits()),
                        compactNameComponent(output.essenceId())).withStyle(style -> style.withColor(colorFor(output.essenceId()))))
                .toList();
        List<List<Component>> rows = TooltipLayout.wrapTokens(tokens, font::width,
                font.width(prefix), font.width(continuation), font.width(separator), maximumWidth);
        for (int index = 0; index < rows.size(); index++) {
            MutableComponent line = (index == 0 ? prefix : continuation).copy();
            List<Component> row = rows.get(index);
            for (int tokenIndex = 0; tokenIndex < row.size(); tokenIndex++) {
                if (tokenIndex > 0) line.append(separator.copy());
                line.append(row.get(tokenIndex));
            }
            tooltip.add(line);
        }
    }

    public static Set<String> getDirectSearchTerms(
            ResourceLocation essenceId,
            double amount
    ) {
        if (essenceId == null || amount <= 0L) {
            return Set.of();
        }
        return getDirectSearchTermsMicros(essenceId, FractionalAmountService.units(amount));
    }

    public static Set<String> getDirectSearchTermsMicros(ResourceLocation essenceId, long microUnits) {
        if (essenceId == null || microUnits <= 0) return Set.of();
        Set<String> result = new LinkedHashSet<>();
        result.add("essence");
        result.add(EssenceYieldFormat.formatMicros(microUnits));
        addNameSearchTerms(result, essenceId);
        return Set.copyOf(result);
    }


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

        List<Yield> outputs =
                YIELD_SNAPSHOT.outputsByItem().get(
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

        for (Yield output :
                outputs) {

            result.add(
                    EssenceYieldFormat.formatMicros(
                            output.microUnits()
                    )
            );

            addNameSearchTerms(result, output.essenceId());
        }

        return Set.copyOf(
                result
        );
    }


    private static Component shortNameComponent(
            ResourceLocation essenceId
    ) {
        EssenceDefinition definition = EssenceRegistry.get(essenceId).orElse(null);
        if (definition != null) {
            return EssenceText.essenceShort(definition);
        }
        return Component.literal(titleCase(essenceId.getPath()));
    }

    static Component compactNameComponent(ResourceLocation essenceId) {
        String abbreviation = essenceId.getNamespace().equals(EssenceAscendance.MOD_ID)
                ? switch (essenceId.getPath()) {
                    case "offense" -> "Off";
                    case "defense" -> "Def";
                    case "vitality" -> "Vit";
                    case "mobility" -> "Mob";
                    case "gathering" -> "Gat";
                    case "utility" -> "Uti";
                    default -> null;
                } : null;
        if (abbreviation == null) return shortNameComponent(essenceId);
        return Component.translatableWithFallback(
                "tooltip.essence_ascendance.essence_abbreviation." + essenceId.getPath(), abbreviation);
    }

    private static void addNameSearchTerms(Set<String> result, ResourceLocation essenceId) {
        for (Component name : List.of(compactNameComponent(essenceId), shortNameComponent(essenceId))) {
            for (String word : name.getString().toLowerCase(Locale.ROOT).trim().split("\\s+")) {
                if (!word.isBlank()) result.add(word);
            }
        }
    }

    static TextColor colorFor(
            ResourceLocation essenceId
    ) {
        return TextColor.fromRgb(AscendancePalette.categoryRgb(essenceId));
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

    private static void rejectPending() {
        EssenceAscendance.LOGGER.warn(
                "Rejected incomplete/invalid item tooltip snapshot for generation {}; retaining installed generation {}",
                pendingGeneration, installedGeneration);
        PENDING_CHUNKS.clear();
        pendingGeneration = -1L;
        pendingChunkCount = 0;
    }

    static void clear() {
        clear(true);
    }

    static void clearForTesting() { clear(false); }

    private static void clear(boolean refreshSearch) {
        installedGeneration = -1L;
        YIELD_SNAPSHOT = YieldSnapshot.unavailable();

        PENDING_CHUNKS.clear();

        pendingGeneration =
                -1L;

        pendingChunkCount =
                0;
        if (refreshSearch) JeiTooltipSearchRefreshBridge.requestRefresh();
    }

    /** Atomic, connection-scoped read model shared by tooltips and read-only browsers. */
    public record YieldSnapshot(long generation, boolean ready,
                                Map<ResourceLocation, List<Yield>> outputsByItem,
                                List<YieldRow> rows) {
        public YieldSnapshot {
            Map<ResourceLocation, List<Yield>> copied = new LinkedHashMap<>();
            outputsByItem.forEach((item, yields) -> copied.put(
                    Objects.requireNonNull(item), List.copyOf(yields)));
            outputsByItem = Map.copyOf(copied);
            rows = List.copyOf(rows);
        }

        private static YieldSnapshot unavailable() {
            return new YieldSnapshot(-1L, false, Map.of(), List.of());
        }
    }

    public record YieldRow(ResourceLocation itemId, List<Yield> outputs) {
        public YieldRow { Objects.requireNonNull(itemId); outputs = List.copyOf(outputs); }
        public long microUnits(ResourceLocation essenceId) {
            return outputs.stream().filter(output -> output.essenceId().equals(essenceId))
                    .mapToLong(Yield::microUnits).findFirst().orElse(0L);
        }
    }

    public record Yield(ResourceLocation essenceId, long microUnits) {
        public Yield {
            Objects.requireNonNull(essenceId);
            if (microUnits <= 0L) throw new IllegalArgumentException("Yield must be positive");
        }
    }

    public static YieldSnapshot yieldSnapshot() { return YIELD_SNAPSHOT; }
}
