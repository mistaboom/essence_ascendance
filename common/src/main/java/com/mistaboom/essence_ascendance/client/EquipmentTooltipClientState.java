package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.network.EquipmentTooltipPayload;
import com.mistaboom.essence_ascendance.equipment.EquipmentTierData;
import com.mistaboom.essence_ascendance.equipment.SoulboundEquipmentData;
import dev.architectury.networking.NetworkManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.Map;

/*
 * Client-only presentation cache and tooltip organizer.
 *
 * The server supplies player-aware Ascendance values. This class:
 * 1. keeps the normal vanilla tooltip,
 * 2. extracts vanilla enchantment lines,
 * 3. appends a readable Ascendance block,
 * 4. restores the enchantments under an explicit Enchantments heading.
 */
public final class EquipmentTooltipClientState {

    private static volatile Map<String, List<EquipmentTooltipPayload.Line>>
            LINES_BY_ITEM = Map.of();

    private static boolean initialized = false;

    private EquipmentTooltipClientState() {
    }

    public static void init() {
        if (initialized) {
            return;
        }

        NetworkManager.registerReceiver(
                NetworkManager.Side.S2C,
                EquipmentTooltipPayload.TYPE,
                EquipmentTooltipPayload.CODEC,
                (payload, context) ->
                        context.queue(
                                () -> accept(payload)
                        )
        );

        initialized = true;

        EssenceAscendance.LOGGER.info(
                "Registered Ascendance equipment tooltip receiver and organizer"
        );
    }

    /*
     * Kept for compatibility with the first tooltip patch, whose Ascendance item
     * classes call this from appendHoverText(). The actual presentation now runs
     * once from the ItemStack#getTooltipLines return pipeline after the complete
     * vanilla tooltip exists.
     */
    public static void append(
            ItemStack stack,
            List<Component> tooltip
    ) {
        // Intentionally empty.
    }

    public static void applyToGeneratedTooltip(
            ItemStack stack,
            List<Component> tooltip,
            Item.TooltipContext tooltipContext,
            TooltipFlag tooltipFlag
    ) {
        List<EquipmentTooltipPayload.Line> lines =
                LINES_BY_ITEM.get(lookupKey(stack));

        if (lines == null || lines.isEmpty()) {
            return;
        }

        List<Component> enchantmentLines =
                extractVanillaEnchantments(
                        stack,
                        tooltip,
                        tooltipContext,
                        tooltipFlag
                );

        removeVanillaAttributeModifiers(
                tooltip
        );
        trimTrailingEmptyComponents(
                tooltip
        );

        /*
         * Keep the tier directly beneath the vanilla item name. The breathing
         * room belongs after the tier, before the first section heading.
         */
        appendGroup(
                tooltip,
                lines,
                EquipmentTooltipPayload.Group.IDENTITY
        );

        SoulboundEquipmentData.read(stack).ifPresent(binding ->
                tooltip.add(
                        Component.literal(" Soulbound: " + binding.displayOwner())
                                .withStyle(ChatFormatting.DARK_PURPLE)
                )
        );

        tooltip.add(Component.empty());

        tooltip.add(
                section(
                        "Current Stats",
                        ChatFormatting.GREEN
                )
        );

        appendGroupOrNone(
                tooltip,
                lines,
                EquipmentTooltipPayload.Group.STATS,
                "No current equipment stats."
        );

        tooltip.add(Component.empty());
        tooltip.add(
                section(
                        "Enchantments",
                        ChatFormatting.BLUE
                )
        );

        if (enchantmentLines.isEmpty()) {
            tooltip.add(
                    Component.literal("  None")
                            .withStyle(ChatFormatting.DARK_GRAY)
            );
        } else {
            for (Component enchantmentLine : enchantmentLines) {
                tooltip.add(
                        Component.literal("  ")
                                .append(enchantmentLine)
                );
            }
        }

        tooltip.add(Component.empty());
        tooltip.add(
                section(
                        "Essence Abilities",
                        ChatFormatting.AQUA
                )
        );

        appendGroupOrNone(
                tooltip,
                lines,
                EquipmentTooltipPayload.Group.ESSENCE,
                "No active Essence bonuses."
        );
    }

    private static List<Component> extractVanillaEnchantments(
            ItemStack stack,
            List<Component> tooltip,
            Item.TooltipContext tooltipContext,
            TooltipFlag tooltipFlag
    ) {
        ItemEnchantments enchantments =
                stack.get(DataComponents.ENCHANTMENTS);

        if (enchantments == null || enchantments.isEmpty()) {
            return List.of();
        }

        List<Component> generated =
                new ArrayList<>();

        enchantments.addToTooltip(
                tooltipContext,
                generated::add,
                tooltipFlag
        );

        for (Component line : generated) {
            removeFirstMatching(
                    tooltip,
                    line
            );
        }

        return generated;
    }

    /*
     * Ascendance replaces vanilla's generic equipment-attribute readout with its
     * own player-aware Current Stats section.
     *
     * Do NOT match English strings such as "When in Main Hand". These are
     * translatable components. Matching their translation keys keeps the cleanup
     * correct for every client language and removes both:
     *
     *   item.modifiers.*       -> "When on Head", "When in Main Hand", etc.
     *   attribute.modifier.*   -> "+3 Armor", "4 Attack Speed", etc.
     *
     * Enchantment names are extracted separately and restored under the explicit
     * Enchantments section, so enchantments such as Respiration/Aqua Affinity
     * remain visible without their redundant attribute-effect readout.
     */
    private static void removeVanillaAttributeModifiers(
            List<Component> tooltip
    ) {
        tooltip.removeIf(
                EquipmentTooltipClientState::isVanillaAttributeTooltipLine
        );
    }

    private static boolean isVanillaAttributeTooltipLine(
            Component component
    ) {
        /*
         * Some vanilla attribute rows are direct TranslatableComponents while
         * others are wrapped in an empty/root MutableComponent with the
         * translatable part stored as a sibling. Inspect the full component
         * tree so both forms are removed.
         */
        if (component.getContents()
                instanceof TranslatableContents translatable) {

            String key =
                    translatable.getKey();

            if (key.startsWith("item.modifiers.")
                    || key.startsWith("attribute.modifier.")) {
                return true;
            }
        }

        for (Component sibling :
                component.getSiblings()) {

            if (isVanillaAttributeTooltipLine(
                    sibling
            )) {
                return true;
            }
        }

        return false;
    }

    private static void trimTrailingEmptyComponents(
            List<Component> tooltip
    ) {
        while (!tooltip.isEmpty()) {
            Component last =
                    tooltip.get(
                            tooltip.size() - 1
                    );

            if (!last.getString().isEmpty()) {
                return;
            }

            tooltip.remove(
                    tooltip.size() - 1
            );
        }
    }

    private static void removeFirstMatching(
            List<Component> tooltip,
            Component target
    ) {
        for (int i = 0; i < tooltip.size(); i++) {
            Component existing = tooltip.get(i);

            if (existing.equals(target)
                    || existing.getString().equals(target.getString())) {
                tooltip.remove(i);
                return;
            }
        }
    }

    private static void appendGroup(
            List<Component> tooltip,
            List<EquipmentTooltipPayload.Line> lines,
            EquipmentTooltipPayload.Group group
    ) {
        for (EquipmentTooltipPayload.Line line : lines) {
            if (line.group() == group) {
                tooltip.add(component(line));
            }
        }
    }

    private static void appendGroupOrNone(
            List<Component> tooltip,
            List<EquipmentTooltipPayload.Line> lines,
            EquipmentTooltipPayload.Group group,
            String emptyText
    ) {
        boolean added = false;

        for (EquipmentTooltipPayload.Line line : lines) {
            if (line.group() != group) {
                continue;
            }

            tooltip.add(component(line));
            added = true;
        }

        if (!added) {
            tooltip.add(
                    Component.literal("  " + emptyText)
                            .withStyle(ChatFormatting.DARK_GRAY)
            );
        }
    }

    private static Component component(
            EquipmentTooltipPayload.Line line
    ) {
        String indent = line.tone() == EquipmentTooltipPayload.Tone.TIER
                ? " "
                : "  ";
        Component base =
                Component.literal(indent + line.text());

        return switch (line.tone()) {
            case TIER ->
                    base.copy()
                            .withStyle(
                                    ChatFormatting.LIGHT_PURPLE,
                                    ChatFormatting.BOLD
                            );

            case PRIMARY ->
                    base.copy()
                            .withStyle(ChatFormatting.WHITE);

            case ABILITY ->
                    base.copy()
                            .withStyle(ChatFormatting.AQUA);

            case SET_BONUS ->
                    base.copy()
                            .withStyle(ChatFormatting.GOLD);

            case MUTED ->
                    base.copy()
                            .withStyle(ChatFormatting.GRAY);
        };
    }

    private static Component section(
            String title,
            ChatFormatting color
    ) {
        return Component.literal(title)
                .withStyle(
                        color,
                        ChatFormatting.BOLD
                );
    }


    /*
     * Returns the searchable WORDS represented by the visible player-aware
     * equipment tooltip for this stack.
     *
     * JEI's tooltip search indexes words, not whole lines, so this deliberately
     * tokenizes Component#getString() using whitespace just like JEI does.
     */
    public static Set<String> getSearchTerms(
            ItemStack stack
    ) {
        if (stack == null
                || stack.isEmpty()) {
            return Set.of();
        }

        List<EquipmentTooltipPayload.Line> lines =
                LINES_BY_ITEM.get(lookupKey(stack));

        if (lines == null
                || lines.isEmpty()) {
            return Set.of();
        }

        Set<String> result =
                new LinkedHashSet<>();

        /*
         * LINES_BY_ITEM stores the server-synchronized semantic Line records,
         * not rendered Components. Search the exact display text carried by
         * each record; the normal tooltip renderer styles that same text later.
         */
        for (EquipmentTooltipPayload.Line line :
                lines) {

            addSearchWords(
                    result,
                    line.text()
            );
        }

        return Set.copyOf(
                result
        );
    }


    private static String lookupKey(ItemStack stack) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return itemId + "#" + EquipmentTierData.tier(stack).serializedName();
    }


    private static void addSearchWords(
            Set<String> result,
            String text
    ) {
        if (text == null
                || text.isBlank()) {
            return;
        }

        for (String word :
                text.toLowerCase(
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


    private static void accept(
            EquipmentTooltipPayload payload
    ) {
        Map<String, List<EquipmentTooltipPayload.Line>> next =
                new LinkedHashMap<>();

        for (EquipmentTooltipPayload.Entry entry : payload.entries()) {
            if (entry.itemId() != null && !entry.itemId().isBlank()) {
                next.put(entry.itemId(), entry.lines());
            }
        }

        LINES_BY_ITEM =
                Map.copyOf(
                        next
                );

        /*
         * JEI's $ tooltip-search index is built from ItemStack#getTooltipLines.
         * If its runtime already exists, rebuild the index now that this
         * player-aware tooltip snapshot has changed. If JEI has not initialized
         * yet, its normal initial index build will see this state later.
         */
        JeiTooltipSearchRefreshBridge.requestRefresh();
    }
}
