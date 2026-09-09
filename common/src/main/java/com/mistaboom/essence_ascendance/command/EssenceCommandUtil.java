package com.mistaboom.essence_ascendance.command;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import com.mistaboom.essence_ascendance.essence.EssenceRegistry;
import com.mistaboom.essence_ascendance.equipment.EquipmentTier;
import com.mistaboom.essence_ascendance.progression.MilestoneDefinition;
import com.mistaboom.essence_ascendance.stat.EssenceStatRegistry;
import com.mistaboom.essence_ascendance.stat.StatCategory;
import com.mistaboom.essence_ascendance.stat.StatDefinition;
import com.mistaboom.essence_ascendance.tier.AscendanceTierDefinition;
import com.mistaboom.essence_ascendance.tier.AscendanceTierRegistry;
import com.mistaboom.essence_ascendance.text.EssenceText;
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
                    value -> EssenceText.command("error.unknown_essence", value)
                            .withStyle(ChatFormatting.RED)
            );

    private static final DynamicCommandExceptionType UNKNOWN_STAT =
            new DynamicCommandExceptionType(
                    value -> EssenceText.command("error.unknown_stat", value)
                            .withStyle(ChatFormatting.RED)
            );

    private static final DynamicCommandExceptionType UNKNOWN_TIER =
            new DynamicCommandExceptionType(
                    value -> EssenceText.command("error.unknown_tier", value)
                            .withStyle(ChatFormatting.RED)
            );

    private static final DynamicCommandExceptionType UNKNOWN_ITEM_TIER =
            new DynamicCommandExceptionType(
                    value -> EssenceText.command("error.unknown_item_tier", value)
                            .withStyle(ChatFormatting.RED)
            );

    private static final DynamicCommandExceptionType UNKNOWN_CATEGORY =
            new DynamicCommandExceptionType(
                    value -> EssenceText.command("error.unknown_category", value)
                            .withStyle(ChatFormatting.RED)
            );

    private static final DynamicCommandExceptionType UNKNOWN_MILESTONE =
            new DynamicCommandExceptionType(
                    value -> EssenceText.command("error.unknown_milestone", value)
                            .withStyle(ChatFormatting.RED)
            );

    private EssenceCommandUtil() {
    }

    static void send(CommandSourceStack source, Component component) {
        source.sendSuccess(() -> component, false);
    }

    static void fail(CommandSourceStack source, String message) {
        fail(source, Component.literal(message));
    }

    static void fail(CommandSourceStack source, Component message) {
        source.sendFailure(message.copy().withStyle(ChatFormatting.RED));
    }

    static MutableComponent title(String text) {
        return title(Component.literal(text));
    }

    static MutableComponent title(Component text) {
        return text.copy().withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
    }

    static MutableComponent section(String text) {
        return section(Component.literal(text));
    }

    static MutableComponent section(Component text) {
        return text.copy().withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
    }

    static MutableComponent muted(String text) {
        return muted(Component.literal(text));
    }

    static MutableComponent muted(Component text) {
        return text.copy().withStyle(ChatFormatting.DARK_GRAY);
    }

    static MutableComponent value(String text) {
        return value(Component.literal(text));
    }

    static MutableComponent value(Component text) {
        return text.copy().withStyle(ChatFormatting.WHITE);
    }

    static MutableComponent good(String text) {
        return good(Component.literal(text));
    }

    static MutableComponent good(Component text) {
        return text.copy().withStyle(ChatFormatting.GREEN);
    }

    static MutableComponent warn(String text) {
        return warn(Component.literal(text));
    }

    static MutableComponent warn(Component text) {
        return text.copy().withStyle(ChatFormatting.YELLOW);
    }

    static MutableComponent bad(String text) {
        return bad(Component.literal(text));
    }

    static MutableComponent bad(Component text) {
        return text.copy().withStyle(ChatFormatting.RED);
    }

    static MutableComponent line(String label, String value) {
        return line(Component.literal(label), Component.literal(value).withStyle(ChatFormatting.WHITE));
    }

    static MutableComponent line(Component label, String value) {
        return line(label, Component.literal(value).withStyle(ChatFormatting.WHITE));
    }

    static MutableComponent line(String label, Component value) {
        return line(Component.literal(label), value);
    }

    static MutableComponent line(Component label, Component value) {
        return Component.literal("  ")
                .append(label.copy().withStyle(ChatFormatting.GRAY))
                .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                .append(value);
    }

    static MutableComponent command(String command, String description) {
        return command(command, Component.literal(description));
    }

    static MutableComponent command(String command, Component description) {
        return Component.literal("  ")
                .append(Component.literal(command).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" - ").withStyle(ChatFormatting.GRAY))
                .append(description.copy().withStyle(ChatFormatting.GRAY));
    }

    static MutableComponent status(boolean good, Component yes, Component no) {
        return good ? good(yes) : bad(no);
    }

    static MutableComponent status(boolean good, String yes, String no) {
        return status(good, Component.literal(yes), Component.literal(no));
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
        return EssenceText.category(category).getString();
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

    static Component formatBonusComponent(StatDefinition stat, double value) {
        String number = switch (stat.unit()) {
            case PERCENT, HEARTS, BLOCKS, SECONDS, LEVELS ->
                    String.format(Locale.ROOT, "%.2f", value);
            case HEARTS_PER_SECOND, FLAT ->
                    String.format(Locale.ROOT, "%.3f", value);
        };
        String unit = stat.unit().name().toLowerCase(Locale.ROOT);
        return EssenceText.command("bonus." + unit, number);
    }

    static EssenceDefinition resolveEssence(String input) throws CommandSyntaxException {
        ResourceLocation id = parseId(input);
        if (id == null) {
            throw UNKNOWN_ESSENCE.create(input);
        }
        EssenceDefinition essence = EssenceRegistry.get(id)
                .orElseThrow(() -> UNKNOWN_ESSENCE.create(input));
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
