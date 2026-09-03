package com.mistaboom.essence_ascendance.command;

import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import com.mistaboom.essence_ascendance.equipment.EquipmentActivationType;
import com.mistaboom.essence_ascendance.equipment.EquipmentProfileDefinition;
import com.mistaboom.essence_ascendance.equipment.EquipmentProfileRegistry;
import com.mistaboom.essence_ascendance.equipment.EquipmentStatResolver;
import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import com.mistaboom.essence_ascendance.essence.EssenceFamily;
import com.mistaboom.essence_ascendance.essence.EssenceRegistry;
import com.mistaboom.essence_ascendance.progression.AscendanceAttemptResult;
import com.mistaboom.essence_ascendance.progression.AscendanceEngine;
import com.mistaboom.essence_ascendance.progression.AscendanceEvaluationResult;
import com.mistaboom.essence_ascendance.progression.AscendanceProgressSnapshot;
import com.mistaboom.essence_ascendance.progression.MilestoneDefinition;
import com.mistaboom.essence_ascendance.progression.MilestoneProgress;
import com.mistaboom.essence_ascendance.progression.MilestoneRequirement;
import com.mistaboom.essence_ascendance.progression.MilestoneService;
import com.mistaboom.essence_ascendance.progression.StatInvestmentLimit;
import com.mistaboom.essence_ascendance.progression.StatInvestmentResult;
import com.mistaboom.essence_ascendance.progression.StatProgressionService;
import com.mistaboom.essence_ascendance.progression.StatScalingResult;
import com.mistaboom.essence_ascendance.progression.StatScalingService;
import com.mistaboom.essence_ascendance.progression.TierInvestmentPolicy;
import com.mistaboom.essence_ascendance.stat.EssenceStatRegistry;
import com.mistaboom.essence_ascendance.stat.StatCategory;
import com.mistaboom.essence_ascendance.stat.StatDefinition;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

public final class EssenceCommands {

    private EssenceCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("essence")
                        .executes(context -> showStatus(context.getSource()))
                        .then(
                                Commands.literal("help")
                                        .executes(context -> showHelp(context.getSource()))
                                        .then(
                                                Commands.literal("admin")
                                                        .requires(source -> source.hasPermission(EssenceCommandUtil.ADMIN_PERMISSION))
                                                        .executes(context -> EssenceAdminCommands.showHelp(context.getSource()))
                                        )
                                        .then(
                                                Commands.literal("debug")
                                                        .requires(source -> source.hasPermission(EssenceCommandUtil.ADMIN_PERMISSION))
                                                        .executes(context -> EssenceDebugCommands.showHelp(context.getSource()))
                                        )
                                        .then(
                                                Commands.literal("test")
                                                        .requires(source -> source.hasPermission(EssenceCommandUtil.ADMIN_PERMISSION))
                                                        .executes(context -> EssenceTestCommands.showHelp(context.getSource()))
                                        )
                        )
                        .then(
                                Commands.literal("status")
                                        .executes(context -> showStatus(context.getSource()))
                        )
                        .then(
                                Commands.literal("balance")
                                        .executes(context -> showAllBalances(context.getSource()))
                                        .then(
                                                Commands.argument("essence", StringArgumentType.word())
                                                        .suggests(EssenceCommandUtil::suggestEssences)
                                                        .executes(context -> showBalance(
                                                                context.getSource(),
                                                                StringArgumentType.getString(context, "essence")
                                                        ))
                                        )
                        )
                        .then(
                                Commands.literal("stats")
                                        .executes(context -> showAllStats(context.getSource()))
                                        .then(
                                                Commands.argument("category", StringArgumentType.word())
                                                        .suggests(EssenceCommandUtil::suggestCategories)
                                                        .executes(context -> showStats(
                                                                context.getSource(),
                                                                EssenceCommandUtil.resolveCategory(
                                                                        StringArgumentType.getString(context, "category")
                                                                )
                                                        ))
                                        )
                        )
                        .then(
                                Commands.literal("stat")
                                        .then(
                                                Commands.argument("stat", StringArgumentType.word())
                                                        .suggests(EssenceCommandUtil::suggestStats)
                                                        .executes(context -> showStat(
                                                                context.getSource(),
                                                                StringArgumentType.getString(context, "stat")
                                                        ))
                                        )
                        )
                        .then(
                                Commands.literal("invest")
                                        .then(
                                                Commands.argument("stat", StringArgumentType.word())
                                                        .suggests(EssenceCommandUtil::suggestStats)
                                                        .then(
                                                                Commands.argument("amount", LongArgumentType.longArg(1))
                                                                        .executes(context -> invest(
                                                                                context.getSource(),
                                                                                StringArgumentType.getString(context, "stat"),
                                                                                LongArgumentType.getLong(context, "amount")
                                                                        ))
                                                        )
                                        )
                        )
                        .then(
                                Commands.literal("progress")
                                        .executes(context -> showAscendanceProgress(context.getSource()))
                        )
                        .then(
                                Commands.literal("ascend")
                                        .executes(context -> ascend(context.getSource()))
                        )
                        .then(
                                Commands.literal("milestones")
                                        .executes(context -> showMilestones(context.getSource()))
                                        .then(
                                                Commands.argument("milestone", StringArgumentType.string())
                                                        .suggests(EssenceCommandUtil::suggestMilestones)
                                                        .executes(context -> showMilestone(
                                                                context.getSource(),
                                                                StringArgumentType.getString(context, "milestone")
                                                        ))
                                        )
                        )
                        .then(EssenceAdminCommands.build())
                        .then(EssenceDebugCommands.build())
                        .then(EssenceTestCommands.build())
        );
    }

    private static int showHelp(CommandSourceStack source) {
        EssenceCommandUtil.send(source, EssenceCommandUtil.title("Essence Ascendance Commands"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.section("Player"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence", "show your current overview"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence balance [essence]", "show available Essence balances"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence stats [category]", "list stats, grouped by their real stat categories"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence stat <stat>", "one complete stat view: investment, scaling, and equipment applicability"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence invest <stat> <amount>", "invest available Essence into a stat"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence progress", "show requirements for the next Ascendance tier"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence ascend", "Ascend when all requirements are met"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence milestones [milestone]", "show milestone progress"));

        if (source.hasPermission(EssenceCommandUtil.ADMIN_PERMISSION)) {
            EssenceCommandUtil.send(source, EssenceCommandUtil.section("Development / Administration"));
            EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence admin", "player/item/configuration mutations"));
            EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence debug", "read-only diagnostics organized by gameplay category"));
            EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence test", "deterministic validation helpers"));
        }

        EssenceCommandUtil.send(source, EssenceCommandUtil.muted("Use tab completion after any branch to discover its subcommands."));
        return 1;
    }

    private static int showStatus(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PlayerEssenceData data = playerData(source, player);

        long totalAvailable = 0L;

        for (EssenceDefinition essence : EssenceRegistry.values()) {
            if (!EssenceCommandUtil.isEssenceVisible(essence)) {
                continue;
            }
            totalAvailable = safeAdd(totalAvailable, data.getAvailable(essence));
        }

        long stored = 0L;
        long effective = 0L;
        long capacity = 0L;
        for (StatDefinition stat : EssenceStatRegistry.values()) {
            StatInvestmentLimit limit = TierInvestmentPolicy.evaluate(data, stat);
            stored = safeAdd(stored, limit.storedInvestment());
            effective = safeAdd(effective, limit.effectiveInvestment());
            capacity = safeAdd(capacity, limit.investmentCap());
        }

        EssenceCommandUtil.send(source, EssenceCommandUtil.title("Essence Ascendance Status"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line("Player", player.getGameProfile().getName()));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Tier",
                Component.literal(data.getTier().displayName()).withStyle(ChatFormatting.AQUA)
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line("Available Essence", EssenceCommandUtil.format(totalAvailable) + " total"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Investment",
                EssenceCommandUtil.format(effective) + " / " + EssenceCommandUtil.format(capacity)
                        + " effective (" + EssenceCommandUtil.format(stored) + " stored)"
        ));

        AscendanceEvaluationResult evaluation = AscendanceEngine.evaluate(player);
        switch (evaluation.status()) {
            case AVAILABLE -> EssenceCommandUtil.send(
                    source,
                    EssenceCommandUtil.line(
                            "Next Ascendance",
                            Component.literal(evaluation.nextTier().displayName() + " - ")
                                    .withStyle(ChatFormatting.WHITE)
                                    .append(evaluation.progress().readyToAscend()
                                            ? EssenceCommandUtil.good("READY")
                                            : EssenceCommandUtil.warn("NOT READY"))
                    )
            );
            case MAX_TIER -> EssenceCommandUtil.send(
                    source,
                    EssenceCommandUtil.line("Next Ascendance", EssenceCommandUtil.good("MAX TIER"))
            );
            case CONFIGURATION_ERROR -> EssenceCommandUtil.send(
                    source,
                    EssenceCommandUtil.line("Next Ascendance", EssenceCommandUtil.bad("CONFIGURATION ERROR"))
            );
        }

        EssenceCommandUtil.send(source, EssenceCommandUtil.muted("Details: /essence balance | /essence stats | /essence progress"));
        return 1;
    }

    private static int showAllBalances(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PlayerEssenceData data = playerData(source, player);

        EssenceCommandUtil.send(source, EssenceCommandUtil.title("Essence Balances"));
        boolean skillEssencesEnabled =
                EssenceConfigManager.get().skillEssencesEnabled();

        for (EssenceFamily family : EssenceFamily.values()) {
            if (family == EssenceFamily.SKILL
                    && !skillEssencesEnabled) {
                continue;
            }

            EssenceCommandUtil.send(
                    source,
                    EssenceCommandUtil.section(family == EssenceFamily.ATTRIBUTE ? "Attribute Essence" : "Skill Essence")
            );
            for (EssenceDefinition essence : EssenceRegistry.values()) {
                if (essence.family() != family
                        || !EssenceCommandUtil.isEssenceVisible(essence)) {
                    continue;
                }
                EssenceCommandUtil.send(
                        source,
                        EssenceCommandUtil.line(essence.displayName(), EssenceCommandUtil.format(data.getAvailable(essence)))
                );
            }
        }
        return 1;
    }

    private static int showBalance(CommandSourceStack source, String essenceName) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        EssenceDefinition essence = EssenceCommandUtil.resolveEssence(essenceName);
        long amount = playerData(source, player).getAvailable(essence);

        EssenceCommandUtil.send(source, EssenceCommandUtil.title(essence.displayName()));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line("Available", EssenceCommandUtil.format(amount)));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Family",
                essence.family() == EssenceFamily.ATTRIBUTE ? "Attribute" : "Skill"
        ));
        return 1;
    }

    private static int showAllStats(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        EssenceCommandUtil.send(source, EssenceCommandUtil.title("Essence Stats"));
        for (StatCategory category : StatCategory.values()) {
            showStats(source, player, category, true);
        }
        EssenceCommandUtil.send(source, EssenceCommandUtil.muted("Use /essence stat <stat> for the full calculation and equipment applicability."));
        return 1;
    }

    private static int showStats(CommandSourceStack source, StatCategory category) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        EssenceCommandUtil.send(source, EssenceCommandUtil.title(EssenceCommandUtil.categoryName(category) + " Stats"));
        showStats(source, player, category, false);
        return 1;
    }

    private static void showStats(
            CommandSourceStack source,
            ServerPlayer player,
            StatCategory category,
            boolean includeHeading
    ) {
        PlayerEssenceData data = EssenceSavedData
                .get(source.getServer())
                .getPlayerData(player.getUUID());

        if (includeHeading) {
            EssenceCommandUtil.send(
                    source,
                    Component.literal(EssenceCommandUtil.categoryName(category))
                            .withStyle(EssenceCommandUtil.categoryColor(category), ChatFormatting.BOLD)
            );
        }

        for (StatDefinition stat : EssenceStatRegistry.values()) {
            if (stat.category() != category) {
                continue;
            }

            StatInvestmentLimit limit = TierInvestmentPolicy.evaluate(data, stat);
            StatScalingResult scaling = StatScalingService.evaluate(data, stat);

            MutableComponent value = Component.literal(
                    EssenceCommandUtil.format(limit.effectiveInvestment())
                            + " / " + EssenceCommandUtil.format(limit.investmentCap())
                            + "  |  "
            ).withStyle(ChatFormatting.GRAY)
                    .append(
                            Component.literal(EssenceCommandUtil.formatBonus(stat, scaling.scaledBonus()))
                                    .withStyle(scaling.scaledBonus() > 0.0 ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY)
                    );

            EssenceCommandUtil.send(source, EssenceCommandUtil.line(stat.displayName(), value));
        }
    }

    private static int showStat(CommandSourceStack source, String statName) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        StatDefinition stat = EssenceCommandUtil.resolveStat(statName);
        PlayerEssenceData data = playerData(source, player);
        StatInvestmentLimit limit = TierInvestmentPolicy.evaluate(data, stat);
        StatScalingResult scaling = StatScalingService.evaluate(data, stat);
        double activeStrength = EquipmentStatResolver.evaluate(player).strength(stat);

        EssenceCommandUtil.send(
                source,
                Component.literal(stat.displayName())
                        .withStyle(EssenceCommandUtil.categoryColor(stat.category()), ChatFormatting.BOLD)
        );
        EssenceCommandUtil.send(source, EssenceCommandUtil.line("ID", EssenceCommandUtil.muted(stat.id().toString())));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line("Category", EssenceCommandUtil.categoryName(stat.category())));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line("Unit", stat.unit().name().toLowerCase(java.util.Locale.ROOT)));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line("Essence", stat.essenceType().displayName()));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Available " + stat.essenceType().displayName(),
                EssenceCommandUtil.format(data.getAvailable(stat.essenceType()))
        ));

        EssenceCommandUtil.send(source, EssenceCommandUtil.section("Progression"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Investment",
                EssenceCommandUtil.format(limit.effectiveInvestment()) + " / "
                        + EssenceCommandUtil.format(limit.investmentCap())
                        + " effective (" + EssenceCommandUtil.format(limit.storedInvestment()) + " stored)"
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line("Investment state", limit.state().toString()));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line("Progress", EssenceCommandUtil.formatProgress(scaling.progression())));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line("Current bonus", EssenceCommandUtil.formatBonus(stat, scaling.scaledBonus())));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line("Current tier ceiling", EssenceCommandUtil.formatBonus(stat, scaling.currentTierMaximumBonus())));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line("Transcendent maximum", EssenceCommandUtil.formatBonus(stat, scaling.transcendentMaximumBonus())));

        EssenceCommandUtil.send(source, EssenceCommandUtil.section("Equipment Applicability"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line("Current active strength", EssenceCommandUtil.formatStrength(activeStrength)));

        boolean found = false;
        for (EquipmentProfileDefinition profile : EquipmentProfileRegistry.values()) {
            for (EquipmentActivationType activation : profile.activationTypes()) {
                double strength = profile.statStrength(activation, stat);
                if (strength <= 0.0) {
                    continue;
                }
                found = true;
                EssenceCommandUtil.send(
                        source,
                        EssenceCommandUtil.line(
                                profile.displayName(),
                                EssenceCommandUtil.formatStrength(strength)
                                        + " when " + activation.name().toLowerCase()
                        )
                );
            }
        }

        if (!found) {
            EssenceCommandUtil.send(source, EssenceCommandUtil.warn("  No built-in equipment profile currently activates this stat."));
        }
        return 1;
    }

    private static int invest(CommandSourceStack source, String statName, long amount) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        StatDefinition stat = EssenceCommandUtil.resolveStat(statName);
        StatInvestmentResult result = StatProgressionService.invest(player, stat, amount);

        if (result.success()) {
            EssenceCommandUtil.send(source, EssenceCommandUtil.title("Investment Complete"));
            EssenceCommandUtil.send(source, EssenceCommandUtil.line("Stat", stat.displayName()));
            EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                    "Invested",
                    EssenceCommandUtil.format(amount) + " " + stat.essenceType().displayName()
            ));
            EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                    "Total",
                    EssenceCommandUtil.format(result.investedAfter()) + " / "
                            + EssenceCommandUtil.format(result.investmentCap())
            ));
            EssenceCommandUtil.send(source, EssenceCommandUtil.line("Remaining Essence", EssenceCommandUtil.format(result.availableAfter())));
            return 1;
        }

        switch (result.status()) {
            case INVALID_AMOUNT -> EssenceCommandUtil.fail(source, "Investment amount must be greater than zero.");
            case INSUFFICIENT_ESSENCE -> EssenceCommandUtil.fail(
                    source,
                    "Not enough " + stat.essenceType().displayName()
                            + ". Required: " + EssenceCommandUtil.format(amount)
                            + ", available: " + EssenceCommandUtil.format(result.availableBefore())
            );
            case AT_CAP -> EssenceCommandUtil.fail(
                    source,
                    stat.displayName() + " is already at its current cap of "
                            + EssenceCommandUtil.format(result.investmentCap()) + "."
            );
            case OVER_CAP -> EssenceCommandUtil.fail(
                    source,
                    stat.displayName() + " is over its current cap. Stored: "
                            + EssenceCommandUtil.format(result.investedBefore())
                            + ", cap: " + EssenceCommandUtil.format(result.investmentCap())
                            + ". Existing investment is preserved."
            );
            case WOULD_EXCEED_CAP -> EssenceCommandUtil.fail(
                    source,
                    "Investment would exceed the cap for " + stat.displayName()
                            + ". Maximum additional investment: "
                            + EssenceCommandUtil.format(result.remainingCapacityBefore()) + "."
            );
            case NUMERIC_OVERFLOW -> EssenceCommandUtil.fail(source, "Investment would exceed the supported numeric range.");
            case CONFIGURATION_ERROR -> EssenceCommandUtil.fail(source, "Unable to determine the current investment cap. Check the server configuration and logs.");
            case TRANSACTION_FAILED -> EssenceCommandUtil.fail(source, "Unable to complete the Essence investment transaction.");
            case SUCCESS -> throw new IllegalStateException("Successful investment reached failure handling");
        }
        return 0;
    }

    private static int showAscendanceProgress(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        AscendanceEvaluationResult evaluation = AscendanceEngine.evaluate(player);

        if (evaluation.status() == AscendanceEvaluationResult.Status.MAX_TIER) {
            EssenceCommandUtil.send(source, EssenceCommandUtil.title("Ascendance Progress"));
            EssenceCommandUtil.send(source, EssenceCommandUtil.line("Current tier", evaluation.currentTier().displayName()));
            EssenceCommandUtil.send(source, EssenceCommandUtil.line("Next tier", EssenceCommandUtil.good("MAX TIER")));
            return 1;
        }

        if (evaluation.status() == AscendanceEvaluationResult.Status.CONFIGURATION_ERROR) {
            EssenceCommandUtil.fail(source, "Ascendance progress cannot be evaluated because the progression configuration is invalid. Check the server log.");
            return 0;
        }

        AscendanceProgressSnapshot progress = evaluation.progress();
        String worldState = !progress.worldProgress().resolvable()
                ? "UNRESOLVED"
                : progress.worldProgress().complete() ? "COMPLETE" : "INCOMPLETE";

        EssenceCommandUtil.send(source, EssenceCommandUtil.title("Ascendance Progress"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Transition",
                evaluation.currentTier().displayName() + " -> " + evaluation.nextTier().displayName()
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Investment depth",
                EssenceCommandUtil.format(progress.effectiveInvestment()) + " / "
                        + EssenceCommandUtil.format(progress.requiredInvestment())
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Developed stats",
                progress.developedStats() + " / " + progress.requiredDevelopedStats()
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Represented categories",
                progress.representedCategories() + " / " + progress.requiredRepresentedCategories()
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "World progression",
                "COMPLETE".equals(worldState)
                        ? EssenceCommandUtil.good(worldState)
                        : "UNRESOLVED".equals(worldState)
                        ? EssenceCommandUtil.bad(worldState)
                        : EssenceCommandUtil.warn(worldState)
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Ready to Ascend",
                progress.readyToAscend() ? EssenceCommandUtil.good("YES") : EssenceCommandUtil.warn("NO")
        ));
        return 1;
    }

    private static int ascend(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        AscendanceAttemptResult result = AscendanceEngine.ascend(player);

        return switch (result.status()) {
            case SUCCESS -> {
                EssenceCommandUtil.send(source, EssenceCommandUtil.title("Ascendance Complete"));
                EssenceCommandUtil.send(
                        source,
                        EssenceCommandUtil.line(
                                "Tier",
                                result.evaluation().currentTier().displayName()
                                        + " -> " + result.evaluation().nextTier().displayName()
                        )
                );
                yield 1;
            }
            case NOT_READY -> {
                EssenceCommandUtil.fail(source, "You do not yet meet the requirements to Ascend. Use /essence progress.");
                yield 0;
            }
            case MAX_TIER -> {
                EssenceCommandUtil.fail(source, "You are already at the highest Ascendance tier.");
                yield 0;
            }
            case CONFIGURATION_ERROR -> {
                EssenceCommandUtil.fail(source, "Ascendance cannot be completed because the progression configuration is invalid. Check the server log.");
                yield 0;
            }
        };
    }

    private static int showMilestones(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        EssenceCommandUtil.send(source, EssenceCommandUtil.title("Milestones"));

        for (MilestoneDefinition milestone : com.mistaboom.essence_ascendance.config.EssenceConfigManager.get().milestones().values()) {
            MilestoneProgress progress = evaluateMilestone(player, milestone);
            Component state = !progress.resolvable()
                    ? EssenceCommandUtil.bad("UNRESOLVED")
                    : progress.complete()
                    ? EssenceCommandUtil.good("COMPLETE")
                    : EssenceCommandUtil.warn("INCOMPLETE");

            EssenceCommandUtil.send(
                    source,
                    Component.literal("  ")
                            .append(Component.literal(milestone.displayName()).withStyle(ChatFormatting.WHITE))
                            .append(Component.literal(" [" + milestone.id().getPath() + "] ").withStyle(ChatFormatting.DARK_GRAY))
                            .append(state)
            );
        }
        return 1;
    }

    private static int showMilestone(CommandSourceStack source, String milestoneName) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        MilestoneDefinition milestone = EssenceCommandUtil.resolveMilestone(milestoneName);
        MilestoneProgress progress = evaluateMilestone(player, milestone);

        EssenceCommandUtil.send(source, EssenceCommandUtil.title(milestone.displayName()));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line("ID", EssenceCommandUtil.muted(milestone.id().toString())));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line("Provider", milestone.providerId().toString()));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line("Target", milestone.target()));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "State",
                !progress.resolvable()
                        ? EssenceCommandUtil.bad("UNRESOLVED")
                        : progress.complete()
                        ? EssenceCommandUtil.good("COMPLETE")
                        : EssenceCommandUtil.warn("INCOMPLETE")
        ));
        return 1;
    }

    private static MilestoneProgress evaluateMilestone(ServerPlayer player, MilestoneDefinition milestone) {
        return MilestoneService.evaluate(player, MilestoneRequirement.milestone(milestone.id()));
    }

    private static PlayerEssenceData playerData(CommandSourceStack source, ServerPlayer player) {
        return EssenceSavedData
                .get(source.getServer())
                .getPlayerData(player.getUUID());
    }

    private static long safeAdd(long left, long right) {
        if (right > 0 && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        if (right < 0 && left < Long.MIN_VALUE - right) {
            return Long.MIN_VALUE;
        }
        return left + right;
    }
}
