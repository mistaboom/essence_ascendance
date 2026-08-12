package com.mistaboom.essence_ascendance.command;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.config.EssenceServerConfig;
import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import com.mistaboom.essence_ascendance.equipment.ArmorStatWeights;
import com.mistaboom.essence_ascendance.equipment.EquipmentActivationType;
import com.mistaboom.essence_ascendance.equipment.EquipmentBaselineProperty;
import com.mistaboom.essence_ascendance.equipment.EquipmentBaselineResult;
import com.mistaboom.essence_ascendance.equipment.EquipmentBaselineService;
import com.mistaboom.essence_ascendance.equipment.EquipmentProfileDefinition;
import com.mistaboom.essence_ascendance.equipment.EquipmentProfileRegistry;
import com.mistaboom.essence_ascendance.equipment.EquipmentStatProfile;
import com.mistaboom.essence_ascendance.equipment.EquipmentStatProviderRegistry;
import com.mistaboom.essence_ascendance.equipment.EquipmentStatResolver;
import com.mistaboom.essence_ascendance.equipment.EquipmentStatState;
import com.mistaboom.essence_ascendance.progression.AscendanceEngine;
import com.mistaboom.essence_ascendance.progression.AscendanceEvaluationResult;
import com.mistaboom.essence_ascendance.progression.CategoryDevelopment;
import com.mistaboom.essence_ascendance.progression.MilestoneDefinition;
import com.mistaboom.essence_ascendance.progression.MilestoneProgress;
import com.mistaboom.essence_ascendance.progression.MilestoneProviders;
import com.mistaboom.essence_ascendance.progression.MilestoneRequirement;
import com.mistaboom.essence_ascendance.progression.MilestoneService;
import com.mistaboom.essence_ascendance.progression.StatInvestmentLimit;
import com.mistaboom.essence_ascendance.progression.StatScalingResult;
import com.mistaboom.essence_ascendance.progression.StatScalingService;
import com.mistaboom.essence_ascendance.progression.TierInvestmentPolicy;
import com.mistaboom.essence_ascendance.stat.EssenceStatRegistry;
import com.mistaboom.essence_ascendance.stat.StatCategory;
import com.mistaboom.essence_ascendance.stat.StatDefinition;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/*
 * Development diagnostics for the post-conduit equipment architecture.
 *
 * The commands intentionally describe the same four layers used by gameplay:
 *
 * player stat scaling -> tier/archetype baseline -> item applicability -> context.
 */
public final class EssenceDebugCommands {

    private static final DynamicCommandExceptionType UNKNOWN_STAT =
            new DynamicCommandExceptionType(
                    value -> Component.literal("Unknown stat: " + value)
            );

    private static final DynamicCommandExceptionType UNKNOWN_CATEGORY =
            new DynamicCommandExceptionType(
                    value -> Component.literal("Unknown stat category: " + value)
            );

    private static final DynamicCommandExceptionType UNKNOWN_MILESTONE =
            new DynamicCommandExceptionType(
                    value -> Component.literal("Unknown milestone: " + value)
            );

    private EssenceDebugCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("debug")
                .requires(source -> source.hasPermission(2))
                .executes(context -> showSummary(context.getSource()))
                .then(
                        Commands.literal("summary")
                                .executes(context -> showSummary(context.getSource()))
                )
                .then(
                        Commands.literal("stat")
                                .then(
                                        Commands.argument("stat", StringArgumentType.word())
                                                .suggests(EssenceDebugCommands::suggestStats)
                                                .executes(context -> showStat(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "stat")
                                                ))
                                )
                )
                .then(
                        Commands.literal("category")
                                .then(
                                        Commands.argument("category", StringArgumentType.word())
                                                .suggests(EssenceDebugCommands::suggestCategories)
                                                .executes(context -> showCategory(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "category")
                                                ))
                                )
                )
                .then(
                        Commands.literal("config")
                                .executes(context -> showConfig(context.getSource()))
                )
                .then(
                        Commands.literal("reloadconfig")
                                .executes(context -> reloadConfig(context.getSource()))
                )
                .then(
                        Commands.literal("milestones")
                                .executes(context -> showMilestones(context.getSource()))
                )
                .then(
                        Commands.literal("milestone")
                                .then(
                                        Commands.argument("milestone", StringArgumentType.string())
                                                .suggests(EssenceDebugCommands::suggestMilestones)
                                                .executes(context -> showMilestone(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "milestone")
                                                ))
                                )
                )
                .then(
                        Commands.literal("setmilestone")
                                .then(
                                        Commands.argument("milestone", StringArgumentType.string())
                                                .suggests(EssenceDebugCommands::suggestMilestones)
                                                .then(
                                                        Commands.argument("complete", BoolArgumentType.bool())
                                                                .executes(context -> setMilestone(
                                                                        context.getSource(),
                                                                        StringArgumentType.getString(context, "milestone"),
                                                                        BoolArgumentType.getBool(context, "complete")
                                                                ))
                                                )
                                )
                )
                .then(
                        Commands.literal("equipment")
                                .executes(context -> showEquipment(context.getSource()))
                )
                .then(
                        Commands.literal("equipmentstats")
                                .executes(context -> showEquipmentStats(context.getSource()))
                )
                .then(
                        Commands.literal("statprofile")
                                .then(
                                        Commands.argument("stat", StringArgumentType.word())
                                                .suggests(EssenceDebugCommands::suggestStats)
                                                .executes(context -> showStatProfile(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "stat")
                                                ))
                                )
                )
                .then(
                        Commands.literal("baseline")
                                .executes(context -> showBaselines(context.getSource()))
                );
    }

    private static int showSummary(
            CommandSourceStack source
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PlayerEssenceData playerData = EssenceSavedData
                .get(source.getServer())
                .getPlayerData(player.getUUID());

        long totalStored = 0L;
        long totalEffective = 0L;
        long totalCapacity = 0L;

        for (StatDefinition stat : EssenceStatRegistry.values()) {
            StatInvestmentLimit limit = TierInvestmentPolicy.evaluate(playerData, stat);
            totalStored = Math.addExact(totalStored, limit.storedInvestment());
            totalEffective = Math.addExact(totalEffective, limit.effectiveInvestment());
            totalCapacity = Math.addExact(totalCapacity, limit.investmentCap());
        }

        EssenceServerConfig config = EssenceConfigManager.get();

        final long finalTotalStored = totalStored;
        final long finalTotalEffective = totalEffective;
        final long finalTotalCapacity = totalCapacity;

        source.sendSuccess(
                () -> Component.literal("Essence Ascendance debug summary:"),
                false
        );
        source.sendSuccess(
                () -> Component.literal(
                        "  Player: " + player.getGameProfile().getName()
                                + " | Tier: " + playerData.getTier().displayName()
                ),
                false
        );
        source.sendSuccess(
                () -> Component.literal(
                        "  Investment: " + format(finalTotalEffective)
                                + " effective / " + format(finalTotalCapacity)
                                + " capacity (" + format(finalTotalStored) + " stored)"
                ),
                false
        );
        source.sendSuccess(
                () -> Component.literal(
                        "  Registries: "
                                + EssenceStatRegistry.size() + " stats, "
                                + EquipmentProfileRegistry.size() + " equipment profiles, "
                                + EquipmentStatProviderRegistry.providers().size() + " equipment providers"
                ),
                false
        );
        source.sendSuccess(
                () -> Component.literal(
                        "  Balance profile: " + config.balanceProfile().displayName()
                                + " [" + config.balanceProfile().id() + "]"
                ),
                false
        );

        AscendanceEvaluationResult evaluation = AscendanceEngine.evaluate(player);
        switch (evaluation.status()) {
            case AVAILABLE -> source.sendSuccess(
                    () -> Component.literal(
                            "  Next tier: " + evaluation.nextTier().displayName()
                                    + " | Ready: "
                                    + (evaluation.progress().readyToAscend() ? "YES" : "NO")
                    ),
                    false
            );
            case MAX_TIER -> source.sendSuccess(
                    () -> Component.literal("  Next tier: MAX TIER"),
                    false
            );
            case CONFIGURATION_ERROR -> source.sendSuccess(
                    () -> Component.literal("  Next tier: CONFIGURATION ERROR"),
                    false
            );
        }

        return 1;
    }

    private static int showStat(
            CommandSourceStack source,
            String statName
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        StatDefinition stat = resolveStat(statName);
        PlayerEssenceData playerData = EssenceSavedData
                .get(source.getServer())
                .getPlayerData(player.getUUID());

        StatInvestmentLimit limit = TierInvestmentPolicy.evaluate(playerData, stat);
        StatScalingResult scaling = StatScalingService.evaluate(playerData, stat);

        source.sendSuccess(
                () -> Component.literal("Stat Debug: " + stat.displayName()),
                false
        );
        source.sendSuccess(
                () -> Component.literal("  ID: " + stat.id()),
                false
        );
        source.sendSuccess(
                () -> Component.literal(
                        "  Category: " + stat.category().name().toLowerCase(Locale.ROOT)
                                + " | Essence: " + stat.essenceType().displayName()
                                + " | Unit: " + stat.unit().name().toLowerCase(Locale.ROOT)
                ),
                false
        );
        source.sendSuccess(
                () -> Component.literal(
                        "  Investment: " + format(limit.effectiveInvestment())
                                + " / " + format(limit.investmentCap())
                                + " effective; " + format(limit.storedInvestment())
                                + " stored; state " + limit.state()
                ),
                false
        );
        source.sendSuccess(
                () -> Component.literal(
                        "  Progression: " + formatPercent(scaling.progression())
                ),
                false
        );
        source.sendSuccess(
                () -> Component.literal(
                        "  Current bonus: " + formatBonus(stat, scaling.scaledBonus())
                                + " | Tier ceiling: "
                                + formatBonus(stat, scaling.currentTierMaximumBonus())
                                + " | Transcendent max: "
                                + formatBonus(stat, scaling.transcendentMaximumBonus())
                ),
                false
        );

        return showStatProfile(source, statName);
    }

    private static int showCategory(
            CommandSourceStack source,
            String categoryName
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        StatCategory category = resolveCategory(categoryName);
        CategoryDevelopment development = StatScalingService.evaluateCategory(player, category);

        int count = 0;
        for (StatDefinition stat : EssenceStatRegistry.values()) {
            if (stat.category() == category) {
                count++;
            }
        }

        int finalCount = count;
        source.sendSuccess(
                () -> Component.literal(
                        "Category Debug: " + category.name().toLowerCase(Locale.ROOT)
                ),
                false
        );
        source.sendSuccess(
                () -> Component.literal(
                        "  " + finalCount + " registered stats | "
                                + format(development.effectiveInvestment()) + " / "
                                + format(development.currentCapacity()) + " effective | "
                                + formatPercent(development.development()) + " developed"
                ),
                false
        );

        return 1;
    }

    private static int showConfig(CommandSourceStack source) {
        EssenceServerConfig config = EssenceConfigManager.get();

        source.sendSuccess(
                () -> Component.literal("Essence Ascendance configuration:"),
                false
        );
        source.sendSuccess(
                () -> Component.literal(
                        "  Path: " + EssenceConfigManager.getConfigPath().toAbsolutePath()
                ),
                false
        );
        source.sendSuccess(
                () -> Component.literal(
                        "  Config version: " + config.configVersion()
                                + " | Balance profile: " + config.balanceProfile().displayName()
                                + " [" + config.balanceProfile().id() + "]"
                ),
                false
        );
        source.sendSuccess(
                () -> Component.literal(
                        "  Definitions: " + config.statMaxBonuses().size() + " stat bonuses, "
                                + config.equipmentBaselineConfig().tierBaselines().size()
                                + " equipment tier baselines, "
                                + config.milestones().size() + " milestones, "
                                + config.advancements().size() + " Ascendance transitions"
                ),
                false
        );

        return 1;
    }

    private static int reloadConfig(CommandSourceStack source) {
        EssenceConfigManager.reload();
        source.sendSuccess(
                () -> Component.literal("Reloaded Essence Ascendance configuration."),
                false
        );
        return showConfig(source);
    }

    private static int showMilestones(
            CommandSourceStack source
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        EssenceServerConfig config = EssenceConfigManager.get();

        source.sendSuccess(
                () -> Component.literal("Configured milestones:"),
                false
        );

        for (MilestoneDefinition milestone : config.milestones().values()) {
            MilestoneProgress progress = evaluateMilestone(player, milestone);
            source.sendSuccess(
                    () -> Component.literal(
                            "  " + milestone.displayName()
                                    + " [" + milestone.id() + "]: "
                                    + milestoneState(progress)
                    ),
                    false
            );
        }

        return 1;
    }

    private static int showMilestone(
            CommandSourceStack source,
            String milestoneName
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        MilestoneDefinition milestone = resolveMilestone(milestoneName);
        MilestoneProgress progress = evaluateMilestone(player, milestone);

        source.sendSuccess(
                () -> Component.literal("Milestone Debug: " + milestone.displayName()),
                false
        );
        source.sendSuccess(
                () -> Component.literal(
                        "  ID: " + milestone.id()
                                + " | Provider: " + milestone.providerId()
                                + " | Target: " + milestone.target()
                ),
                false
        );
        source.sendSuccess(
                () -> Component.literal("  State: " + milestoneState(progress)),
                false
        );

        return 1;
    }

    private static int setMilestone(
            CommandSourceStack source,
            String milestoneName,
            boolean complete
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        MilestoneDefinition milestone = resolveMilestone(milestoneName);

        if (!milestone.providerId().equals(MilestoneProviders.INTERNAL)) {
            source.sendFailure(
                    Component.literal(
                            "Only INTERNAL milestones can be changed directly. "
                                    + milestone.id() + " uses " + milestone.providerId()
                    )
            );
            return 0;
        }

        ResourceLocation target = ResourceLocation.tryParse(milestone.target());
        if (target == null) {
            source.sendFailure(
                    Component.literal("Milestone has invalid internal target: " + milestone.target())
            );
            return 0;
        }

        EssenceSavedData savedData = EssenceSavedData.get(player.server);
        boolean changed = complete
                ? savedData.completeInternalMilestone(player.getUUID(), target)
                : savedData.revokeInternalMilestone(player.getUUID(), target);

        source.sendSuccess(
                () -> Component.literal(
                        "Milestone " + milestone.displayName() + " is now "
                                + (complete ? "COMPLETE" : "INCOMPLETE")
                                + (changed ? "." : " (state was already set).")
                ),
                false
        );
        return 1;
    }

    private static int showEquipment(
            CommandSourceStack source
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();

        source.sendSuccess(
                () -> Component.literal("Active equipment profiles:"),
                false
        );

        showItemProfile(source, "Main hand", player.getItemInHand(InteractionHand.MAIN_HAND));
        showItemProfile(source, "Off hand", player.getItemInHand(InteractionHand.OFF_HAND));

        for (EquipmentSlot slot : EquipmentStatResolver.armorSlots()) {
            showItemProfile(
                    source,
                    slot.getName(),
                    player.getItemBySlot(slot)
            );
        }

        return 1;
    }

    private static void showItemProfile(
            CommandSourceStack source,
            String label,
            ItemStack stack
    ) {
        if (stack.isEmpty()) {
            source.sendSuccess(
                    () -> Component.literal("  " + label + ": EMPTY"),
                    false
            );
            return;
        }

        EquipmentStatProfile profile = EquipmentStatResolver.inspectItem(stack);
        StringBuilder details = new StringBuilder();

        for (EquipmentActivationType activation : profile.activations()) {
            if (details.length() > 0) {
                details.append(" | ");
            }
            details.append(activation.name()).append(": ");
            appendStrengths(details, profile.strengths(activation));
        }

        if (details.length() == 0) {
            details.append("NO ESSENCE STAT PROFILE");
        }

        String itemName = stack.getHoverName().getString();
        String finalDetails = details.toString();
        source.sendSuccess(
                () -> Component.literal(
                        "  " + label + ": " + itemName + " -> " + finalDetails
                ),
                false
        );
    }

    private static int showEquipmentStats(
            CommandSourceStack source
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        EquipmentStatState state = EquipmentStatResolver.evaluate(player);

        source.sendSuccess(
                () -> Component.literal(
                        "Resolved active stats (worn armor + main hand; same-stat contexts merge by MAX):"
                ),
                false
        );

        if (state.values().isEmpty()) {
            source.sendSuccess(
                    () -> Component.literal("  NONE"),
                    false
            );
            return 1;
        }

        for (Map.Entry<ResourceLocation, Double> entry : state.values().entrySet()) {
            source.sendSuccess(
                    () -> Component.literal(
                            "  " + entry.getKey() + ": " + formatStrength(entry.getValue())
                    ),
                    false
            );
        }

        return 1;
    }

    private static int showStatProfile(
            CommandSourceStack source,
            String statName
    ) throws CommandSyntaxException {
        StatDefinition stat = resolveStat(statName);

        source.sendSuccess(
                () -> Component.literal(
                        "Built-in equipment applicability for " + stat.displayName() + ":"
                ),
                false
        );

        boolean found = false;
        for (EquipmentProfileDefinition profile : EquipmentProfileRegistry.values()) {
            for (EquipmentActivationType activation : profile.activationTypes()) {
                double strength = profile.statStrength(activation, stat);
                if (strength <= 0.0) {
                    continue;
                }

                found = true;
                source.sendSuccess(
                        () -> Component.literal(
                                "  " + profile.displayName()
                                        + " [" + profile.id() + "]: "
                                        + formatStrength(strength)
                                        + " when " + activation
                        ),
                        false
                );
            }
        }

        if (!found) {
            source.sendSuccess(
                    () -> Component.literal("  No built-in profile uses this stat."),
                    false
            );
        }

        return 1;
    }

    private static int showBaselines(
            CommandSourceStack source
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();

        source.sendSuccess(
                () -> Component.literal(
                        "Tier/archetype equipment baselines for "
                                + EssenceSavedData.get(player.server)
                                .getPlayerData(player.getUUID())
                                .getTier()
                                .displayName()
                                + ":"
                ),
                false
        );

        for (EquipmentProfileDefinition profile : EquipmentProfileRegistry.values()) {
            EquipmentBaselineResult baseline = EquipmentBaselineService.evaluate(
                    player,
                    profile.id()
            );

            StringBuilder values = new StringBuilder();
            for (EquipmentBaselineProperty property : EquipmentBaselineProperty.values()) {
                if (profile.baselineMultiplier(property) <= 0.0) {
                    continue;
                }
                if (values.length() > 0) {
                    values.append(", ");
                }
                values.append(property.name().toLowerCase(Locale.ROOT))
                        .append('=')
                        .append(formatDecimal(baseline.value(property)));
            }

            source.sendSuccess(
                    () -> Component.literal(
                            "  " + profile.displayName() + " [" + profile.id() + "]: " + values
                    ),
                    false
            );
        }

        source.sendSuccess(
                () -> Component.literal(
                        "  Tier harvest capability level: "
                                + EssenceConfigManager.get()
                                .equipmentBaselineConfig()
                                .baselineFor(
                                        EssenceSavedData.get(player.server)
                                                .getPlayerData(player.getUUID())
                                                .getTier()
                                )
                                .harvestLevel()
                ),
                false
        );
        source.sendSuccess(
                () -> Component.literal(
                        "  Armor slot weights: HEAD " + formatStrength(ArmorStatWeights.weightFor(EquipmentSlot.HEAD))
                                + ", CHEST " + formatStrength(ArmorStatWeights.weightFor(EquipmentSlot.CHEST))
                                + ", LEGS " + formatStrength(ArmorStatWeights.weightFor(EquipmentSlot.LEGS))
                                + ", FEET " + formatStrength(ArmorStatWeights.weightFor(EquipmentSlot.FEET))
                ),
                false
        );

        return 1;
    }

    private static MilestoneProgress evaluateMilestone(
            ServerPlayer player,
            MilestoneDefinition milestone
    ) {
        return MilestoneService.evaluate(
                player,
                MilestoneRequirement.milestone(milestone.id())
        );
    }

    private static StatDefinition resolveStat(
            String input
    ) throws CommandSyntaxException {
        ResourceLocation id = parseId(input);
        if (id == null) {
            throw UNKNOWN_STAT.create(input);
        }
        return EssenceStatRegistry
                .get(id)
                .orElseThrow(() -> UNKNOWN_STAT.create(input));
    }

    private static StatCategory resolveCategory(
            String input
    ) throws CommandSyntaxException {
        try {
            return StatCategory.valueOf(input.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw UNKNOWN_CATEGORY.create(input);
        }
    }

    private static MilestoneDefinition resolveMilestone(
            String input
    ) throws CommandSyntaxException {
        ResourceLocation id = parseId(input);
        if (id == null) {
            throw UNKNOWN_MILESTONE.create(input);
        }
        return EssenceConfigManager
                .get()
                .getMilestone(id)
                .orElseThrow(() -> UNKNOWN_MILESTONE.create(input));
    }

    private static ResourceLocation parseId(String input) {
        String fullId = input.contains(":")
                ? input
                : EssenceAscendance.MOD_ID + ":" + input;
        return ResourceLocation.tryParse(fullId);
    }

    private static CompletableFuture<Suggestions> suggestStats(
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

    private static CompletableFuture<Suggestions> suggestCategories(
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

    private static CompletableFuture<Suggestions> suggestMilestones(
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

    private static void appendStrengths(
            StringBuilder builder,
            Map<ResourceLocation, Double> strengths
    ) {
        if (strengths.isEmpty()) {
            builder.append("NONE");
            return;
        }

        boolean first = true;
        for (Map.Entry<ResourceLocation, Double> entry : strengths.entrySet()) {
            if (!first) {
                builder.append(", ");
            }
            first = false;
            builder.append(entry.getKey().getPath())
                    .append(' ')
                    .append(formatStrength(entry.getValue()));
        }
    }

    private static String milestoneState(MilestoneProgress progress) {
        if (!progress.resolvable()) {
            return "UNRESOLVED";
        }
        return progress.complete() ? "COMPLETE" : "INCOMPLETE";
    }

    private static String formatStrength(double value) {
        return String.format(Locale.ROOT, "%.2fx", value);
    }

    private static String formatPercent(double value) {
        return String.format(Locale.ROOT, "%.2f%%", value * 100.0);
    }

    private static String formatBonus(
            StatDefinition stat,
            double value
    ) {
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

    private static String format(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    private static String formatDecimal(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
