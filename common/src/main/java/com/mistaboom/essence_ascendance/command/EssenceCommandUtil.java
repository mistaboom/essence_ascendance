package com.mistaboom.essence_ascendance.command;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import com.mistaboom.essence_ascendance.essence.EssenceFamily;
import com.mistaboom.essence_ascendance.essence.EssenceRegistry;
import com.mistaboom.essence_ascendance.equipment.EquipmentTier;
import com.mistaboom.essence_ascendance.progression.MilestoneDefinition;
import com.mistaboom.essence_ascendance.stat.EssenceStatRegistry;
import com.mistaboom.essence_ascendance.stat.StatCategory;
import com.mistaboom.essence_ascendance.stat.StatDefinition;
import com.mistaboom.essence_ascendance.tier.AscendanceTierDefinition;
import com.mistaboom.essence_ascendance.tier.AscendanceTierRegistry;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

final class EssenceCommandUtil {

    static final int ADMIN_PERMISSION = 2;

    private static final DynamicCommandExceptionType UNKNOWN_ESSENCE =
            new DynamicCommandExceptionType(
                    value -> Component.literal("Unknown Essence type: " + value)
                            .withStyle(ChatFormatting.RED)
            );

    private static final DynamicCommandExceptionType UNKNOWN_STAT =
            new DynamicCommandExceptionType(
                    value -> Component.literal("Unknown stat: " + value)
                            .withStyle(ChatFormatting.RED)
            );

    private static final DynamicCommandExceptionType UNKNOWN_TIER =
            new DynamicCommandExceptionType(
                    value -> Component.literal("Unknown Ascendance tier: " + value)
                            .withStyle(ChatFormatting.RED)
            );

    private static final DynamicCommandExceptionType UNKNOWN_ITEM_TIER =
            new DynamicCommandExceptionType(
                    value -> Component.literal("Unknown item tier: " + value)
                            .withStyle(ChatFormatting.RED)
            );

    private static final DynamicCommandExceptionType UNKNOWN_CATEGORY =
            new DynamicCommandExceptionType(
                    value -> Component.literal("Unknown stat category: " + value)
                            .withStyle(ChatFormatting.RED)
            );

    private static final DynamicCommandExceptionType UNKNOWN_MILESTONE =
            new DynamicCommandExceptionType(
                    value -> Component.literal("Unknown milestone: " + value)
                            .withStyle(ChatFormatting.RED)
            );

    private EssenceCommandUtil() {
    }

    static void send(CommandSourceStack source, Component component) {
        source.sendSuccess(() -> component, false);
    }

    static void fail(CommandSourceStack source, String message) {
        source.sendFailure(Component.literal(message).withStyle(ChatFormatting.RED));
    }

    static MutableComponent title(String text) {
        return Component.literal(text)
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
    }

    static MutableComponent section(String text) {
        return Component.literal(text)
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
    }

    static MutableComponent muted(String text) {
        return Component.literal(text).withStyle(ChatFormatting.DARK_GRAY);
    }

    static MutableComponent value(String text) {
        return Component.literal(text).withStyle(ChatFormatting.WHITE);
    }

    static MutableComponent good(String text) {
        return Component.literal(text).withStyle(ChatFormatting.GREEN);
    }

    static MutableComponent warn(String text) {
        return Component.literal(text).withStyle(ChatFormatting.YELLOW);
    }

    static MutableComponent bad(String text) {
        return Component.literal(text).withStyle(ChatFormatting.RED);
    }

    static MutableComponent line(String label, String value) {
        return line(label, Component.literal(value).withStyle(ChatFormatting.WHITE));
    }

    static MutableComponent line(String label, Component value) {
        return Component.literal("  ")
                .append(Component.literal(label + ": ").withStyle(ChatFormatting.GRAY))
                .append(value);
    }

    static MutableComponent command(String command, String description) {
        return Component.literal("  ")
                .append(Component.literal(command).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" - " + description).withStyle(ChatFormatting.GRAY));
    }

    static MutableComponent status(boolean good, String yes, String no) {
        return good ? good(yes) : bad(no);
    }

    static ChatFormatting categoryColor(StatCategory category) {
        return switch (category) {
            case OFFENSE -> ChatFormatting.RED;
            case DEFENSE -> ChatFormatting.BLUE;
            case VITALITY -> ChatFormatting.GREEN;
            case MOBILITY -> ChatFormatting.AQUA;
            case GATHERING -> ChatFormatting.GOLD;
            case UTILITY -> ChatFormatting.LIGHT_PURPLE;
        };
    }

    static String categoryName(StatCategory category) {
        String lower = category.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    static String format(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    static String formatDecimal(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    static String formatSigned(double value) {
        return String.format(Locale.ROOT, "%+.2f", value);
    }

    static String formatStrength(double value) {
        return String.format(Locale.ROOT, "%.2fx", value);
    }

    static String formatProgress(double fraction) {
        return String.format(Locale.ROOT, "%.2f%%", fraction * 100.0);
    }

    static String formatBonus(StatDefinition stat, double value) {
        return switch (stat.unit()) {
            case PERCENT -> String.format(Locale.ROOT, "%.2f%%", value);
            case HEARTS -> String.format(Locale.ROOT, "%.2f hearts", value);
            case HEARTS_PER_SECOND -> String.format(Locale.ROOT, "%.3f hearts/sec", value);
            case BLOCKS -> String.format(Locale.ROOT, "%.2f blocks", value);
            case SECONDS -> String.format(Locale.ROOT, "%.2f seconds", value);
            case LEVELS -> String.format(Locale.ROOT, "%.2f levels", value);
            case FLAT -> String.format(Locale.ROOT, "%.3f", value);
        };
    }

    static boolean isEssenceVisible(
            EssenceDefinition essence
    ) {
        return essence.family() != EssenceFamily.SKILL
                || EssenceConfigManager.get().skillEssencesEnabled();
    }

    static EssenceDefinition resolveEssence(String input) throws CommandSyntaxException {
        ResourceLocation id = parseId(input);
        if (id == null) {
            throw UNKNOWN_ESSENCE.create(input);
        }
        EssenceDefinition essence = EssenceRegistry.get(id)
                .orElseThrow(() -> UNKNOWN_ESSENCE.create(input));

        if (!isEssenceVisible(essence)) {
            throw UNKNOWN_ESSENCE.create(input);
        }

        return essence;
    }

    static StatDefinition resolveStat(String input) throws CommandSyntaxException {
        ResourceLocation id = parseId(input);
        if (id == null) {
            throw UNKNOWN_STAT.create(input);
        }
        return EssenceStatRegistry.get(id)
                .orElseThrow(() -> UNKNOWN_STAT.create(input));
    }

    static AscendanceTierDefinition resolveTier(String input) throws CommandSyntaxException {
        ResourceLocation id = parseId(input);
        if (id == null) {
            throw UNKNOWN_TIER.create(input);
        }
        return AscendanceTierRegistry.get(id)
                .orElseThrow(() -> UNKNOWN_TIER.create(input));
    }

    static EquipmentTier resolveItemTier(String input) throws CommandSyntaxException {
        if (input != null) {
            String normalized = input.toLowerCase(Locale.ROOT);
            for (EquipmentTier tier : EquipmentTier.values()) {
                if (tier.serializedName().equals(normalized)) {
                    return tier;
                }
            }
        }
        throw UNKNOWN_ITEM_TIER.create(input);
    }

    static StatCategory resolveCategory(String input) throws CommandSyntaxException {
        try {
            return StatCategory.valueOf(input.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw UNKNOWN_CATEGORY.create(input);
        }
    }

    static MilestoneDefinition resolveMilestone(String input) throws CommandSyntaxException {
        ResourceLocation id = parseId(input);
        if (id == null) {
            throw UNKNOWN_MILESTONE.create(input);
        }
        return EssenceConfigManager.get()
                .getMilestone(id)
                .orElseThrow(() -> UNKNOWN_MILESTONE.create(input));
    }

    static ResourceLocation parseId(String input) {
        String fullId = input.contains(":")
                ? input
                : EssenceAscendance.MOD_ID + ":" + input;
        return ResourceLocation.tryParse(fullId);
    }

    static CompletableFuture<Suggestions> suggestEssences(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder
    ) {
        String remaining = builder.getRemainingLowerCase();
        for (EssenceDefinition essence : EssenceRegistry.values()) {
            if (!isEssenceVisible(essence)) {
                continue;
            }

            String name = essence.id().getPath();
            if (name.startsWith(remaining)) {
                builder.suggest(name);
            }
        }
        return builder.buildFuture();
    }

    static CompletableFuture<Suggestions> suggestStats(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder
    ) {
        String remaining = builder.getRemainingLowerCase();
        for (StatDefinition stat : EssenceStatRegistry.values()) {
            String name = stat.id().getPath();
            if (name.startsWith(remaining)) {
                builder.suggest(name);
            }
        }
        return builder.buildFuture();
    }

    static CompletableFuture<Suggestions> suggestTiers(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder
    ) {
        String remaining = builder.getRemainingLowerCase();
        for (AscendanceTierDefinition tier : AscendanceTierRegistry.values()) {
            String name = tier.id().getPath();
            if (name.startsWith(remaining)) {
                builder.suggest(name);
            }
        }
        return builder.buildFuture();
    }

    static CompletableFuture<Suggestions> suggestItemTiers(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder
    ) {
        String remaining = builder.getRemainingLowerCase();
        for (EquipmentTier tier : EquipmentTier.values()) {
            String name = tier.serializedName();
            if (name.startsWith(remaining)) {
                builder.suggest(name);
            }
        }
        return builder.buildFuture();
    }

    static CompletableFuture<Suggestions> suggestCategories(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder
    ) {
        String remaining = builder.getRemainingLowerCase();
        for (StatCategory category : StatCategory.values()) {
            String name = category.name().toLowerCase(Locale.ROOT);
            if (name.startsWith(remaining)) {
                builder.suggest(name);
            }
        }
        return builder.buildFuture();
    }

    static CompletableFuture<Suggestions> suggestMilestones(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder
    ) {
        String remaining = builder.getRemainingLowerCase();
        for (MilestoneDefinition milestone : EssenceConfigManager.get().milestones().values()) {
            ResourceLocation id = milestone.id();
            String suggestion = id.getNamespace().equals(EssenceAscendance.MOD_ID)
                    ? id.getPath()
                    : "\"" + id + "\"";
            if (suggestion.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                builder.suggest(suggestion);
            }
        }
        return builder.buildFuture();
    }
}
