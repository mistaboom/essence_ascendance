package com.mistaboom.essence_ascendance.command;

import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import com.mistaboom.essence_ascendance.equipment.EquipmentActivationType;
import com.mistaboom.essence_ascendance.equipment.EquipmentProfileDefinition;
import com.mistaboom.essence_ascendance.equipment.EquipmentProfileRegistry;
import com.mistaboom.essence_ascendance.equipment.EquipmentStatResolver;
import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
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
import com.mistaboom.essence_ascendance.text.EssenceText;
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
        EssenceCommandUtil.send(source, EssenceCommandUtil.title(EssenceText.command("help.title")));
        EssenceCommandUtil.send(source, EssenceCommandUtil.section(EssenceText.command("help.player")));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence", EssenceText.command("help.overview")));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence balance [essence]", EssenceText.command("help.balance")));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence stats [category]", EssenceText.command("help.stats")));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence stat <stat>", EssenceText.command("help.stat")));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence invest <stat> <amount>", EssenceText.command("help.invest")));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence progress", EssenceText.command("help.progress")));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence ascend", EssenceText.command("help.ascend")));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence milestones [milestone]", EssenceText.command("help.milestones")));

        if (source.hasPermission(EssenceCommandUtil.ADMIN_PERMISSION)) {
            EssenceCommandUtil.send(source, EssenceCommandUtil.section(EssenceText.command("help.development")));
            EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence admin", EssenceText.command("help.admin")));
            EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence debug", EssenceText.command("help.debug")));
            EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence test", EssenceText.command("help.test")));
        }

        EssenceCommandUtil.send(source, EssenceCommandUtil.muted(EssenceText.command("help.tab_completion")));
        return 1;
    }

    private static int showStatus(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PlayerEssenceData data = playerData(source, player);

        long totalAvailable = 0L;

        for (EssenceDefinition essence : EssenceRegistry.values()) {
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

        EssenceCommandUtil.send(source, EssenceCommandUtil.title(EssenceText.command("status.title")));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(EssenceText.command("label.player"), player.getGameProfile().getName()));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                EssenceText.command("label.tier"),
                EssenceText.ascendanceTier(data.getTier()).withStyle(ChatFormatting.AQUA)
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(EssenceText.command("label.available_essence"), EssenceText.command("value.total", EssenceCommandUtil.format(totalAvailable))));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                EssenceText.command("label.investment"),
                EssenceText.command("value.investment", EssenceCommandUtil.format(effective), EssenceCommandUtil.format(capacity), EssenceCommandUtil.format(stored))
        ));

        AscendanceEvaluationResult evaluation = AscendanceEngine.evaluate(player);
        switch (evaluation.status()) {
            case AVAILABLE -> EssenceCommandUtil.send(
                    source,
                    EssenceCommandUtil.line(
                            EssenceText.command("label.next_ascendance"),
                            EssenceText.ascendanceTier(evaluation.nextTier())
                                    .append(Component.literal(" - "))
                                    .withStyle(ChatFormatting.WHITE)
                                    .append(evaluation.progress().readyToAscend()
                                            ? EssenceCommandUtil.good(EssenceText.command("state.ready"))
                                            : EssenceCommandUtil.warn(EssenceText.command("state.not_ready")))
                    )
            );
            case MAX_TIER -> EssenceCommandUtil.send(
                    source,
                    EssenceCommandUtil.line(EssenceText.command("label.next_ascendance"), EssenceCommandUtil.good(EssenceText.command("state.max_tier")))
            );
            case CONFIGURATION_ERROR -> EssenceCommandUtil.send(
                    source,
                    EssenceCommandUtil.line(EssenceText.command("label.next_ascendance"), EssenceCommandUtil.bad(EssenceText.command("state.configuration_error")))
            );
        }

        EssenceCommandUtil.send(source, EssenceCommandUtil.muted(EssenceText.command("status.details")));
        return 1;
    }

    private static int showAllBalances(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PlayerEssenceData data = playerData(source, player);

        EssenceCommandUtil.send(source, EssenceCommandUtil.title(EssenceText.command("balance.title")));
        EssenceCommandUtil.send(
                source,
                EssenceCommandUtil.section(EssenceText.term("essence"))
        );
        for (EssenceDefinition essence : EssenceRegistry.values()) {
            EssenceCommandUtil.send(
                    source,
                    EssenceCommandUtil.line(EssenceText.essence(essence), EssenceCommandUtil.format(data.getAvailable(essence)))
            );
        }
        return 1;
    }

    private static int showBalance(CommandSourceStack source, String essenceName) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        EssenceDefinition essence = EssenceCommandUtil.resolveEssence(essenceName);
        long amount = playerData(source, player).getAvailable(essence);

        EssenceCommandUtil.send(source, EssenceCommandUtil.title(EssenceText.essence(essence)));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(EssenceText.command("label.available"), EssenceCommandUtil.format(amount)));
        return 1;
    }

    private static int showAllStats(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        EssenceCommandUtil.send(source, EssenceCommandUtil.title(EssenceText.command("stats.title")));
        for (StatCategory category : StatCategory.values()) {
            showStats(source, player, category, true);
        }
        EssenceCommandUtil.send(source, EssenceCommandUtil.muted(EssenceText.command("stats.details_hint")));
        return 1;
    }

    private static int showStats(CommandSourceStack source, StatCategory category) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        EssenceCommandUtil.send(source, EssenceCommandUtil.title(EssenceText.command("stats.category_title", EssenceText.category(category))));
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
                    EssenceText.category(category).withStyle(EssenceCommandUtil.categoryColor(category), ChatFormatting.BOLD)
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
                            EssenceCommandUtil.formatBonusComponent(stat, scaling.scaledBonus())
                                    .copy().withStyle(scaling.scaledBonus() > 0.0 ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY)
                    );

            EssenceCommandUtil.send(source, EssenceCommandUtil.line(EssenceText.stat(stat), value));
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
                EssenceText.stat(stat).withStyle(EssenceCommandUtil.categoryColor(stat.category()), ChatFormatting.BOLD)
        );
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(EssenceText.command("label.id"), EssenceCommandUtil.muted(stat.id().toString())));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(EssenceText.command("label.category"), EssenceText.category(stat.category())));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(EssenceText.command("label.unit"), EssenceText.command("unit." + stat.unit().name().toLowerCase(java.util.Locale.ROOT))));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(EssenceText.command("label.essence"), EssenceText.essence(stat.essenceType())));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                EssenceText.command("label.available_named", EssenceText.essence(stat.essenceType())),
                EssenceCommandUtil.format(data.getAvailable(stat.essenceType()))
        ));

        EssenceCommandUtil.send(source, EssenceCommandUtil.section(EssenceText.command("section.progression")));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                EssenceText.command("label.investment"),
                EssenceText.command(
                        "value.investment",
                        EssenceCommandUtil.format(limit.effectiveInvestment()),
                        EssenceCommandUtil.format(limit.investmentCap()),
                        EssenceCommandUtil.format(limit.storedInvestment())
                )
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(EssenceText.command("label.investment_state"), EssenceText.command("investment_state." + limit.state().name().toLowerCase(java.util.Locale.ROOT))));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(EssenceText.command("label.progress"), EssenceCommandUtil.formatProgress(scaling.progression())));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(EssenceText.command("label.current_bonus"), EssenceCommandUtil.formatBonusComponent(stat, scaling.scaledBonus())));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(EssenceText.command("label.current_tier_ceiling"), EssenceCommandUtil.formatBonusComponent(stat, scaling.currentTierMaximumBonus())));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(EssenceText.command("label.transcendent_maximum"), EssenceCommandUtil.formatBonusComponent(stat, scaling.transcendentMaximumBonus())));

        EssenceCommandUtil.send(source, EssenceCommandUtil.section(EssenceText.command("section.equipment_applicability")));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(EssenceText.command("label.current_active_strength"), EssenceCommandUtil.formatStrength(activeStrength)));

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
                                EssenceText.command(
                                        "value.equipment_activation",
                                        EssenceCommandUtil.formatStrength(strength),
                                        EssenceText.command("activation." + activation.name().toLowerCase(java.util.Locale.ROOT))
                                )
                        )
                );
            }
        }

        if (!found) {
            EssenceCommandUtil.send(source, EssenceCommandUtil.warn(EssenceText.command("stat.no_equipment_profile")));
        }
        return 1;
    }

    private static int invest(CommandSourceStack source, String statName, long amount) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        StatDefinition stat = EssenceCommandUtil.resolveStat(statName);
        StatInvestmentResult result = StatProgressionService.invest(player, stat, amount);

        if (result.success()) {
            EssenceCommandUtil.send(source, EssenceCommandUtil.title(EssenceText.command("invest.complete")));
            EssenceCommandUtil.send(source, EssenceCommandUtil.line(EssenceText.command("label.stat"), EssenceText.stat(stat)));
            EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                    EssenceText.command("label.invested"),
                    EssenceText.command("value.amount_essence", EssenceCommandUtil.format(amount), EssenceText.essence(stat.essenceType()))
            ));
            EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                    EssenceText.command("label.total"),
                    EssenceCommandUtil.format(result.investedAfter()) + " / "
                            + EssenceCommandUtil.format(result.investmentCap())
            ));
            EssenceCommandUtil.send(source, EssenceCommandUtil.line(EssenceText.command("label.remaining_essence"), EssenceCommandUtil.format(result.availableAfter())));
            return 1;
        }

        switch (result.status()) {
            case INVALID_AMOUNT -> EssenceCommandUtil.fail(source, EssenceText.command("error.investment_positive"));
            case INSUFFICIENT_ESSENCE -> EssenceCommandUtil.fail(
                    source,
                    EssenceText.command(
                            "error.insufficient_essence",
                            EssenceText.essence(stat.essenceType()),
                            EssenceCommandUtil.format(amount),
                            EssenceCommandUtil.format(result.availableBefore())
                    )
            );
            case AT_CAP -> EssenceCommandUtil.fail(
                    source,
                    EssenceText.command(
                            "error.at_cap",
                            EssenceText.stat(stat),
                            EssenceCommandUtil.format(result.investmentCap())
                    )
            );
            case OVER_CAP -> EssenceCommandUtil.fail(
                    source,
                    EssenceText.command(
                            "error.over_cap",
                            EssenceText.stat(stat),
                            EssenceCommandUtil.format(result.investedBefore()),
                            EssenceCommandUtil.format(result.investmentCap())
                    )
            );
            case WOULD_EXCEED_CAP -> EssenceCommandUtil.fail(
                    source,
                    EssenceText.command(
                            "error.would_exceed_cap",
                            EssenceText.stat(stat),
                            EssenceCommandUtil.format(result.remainingCapacityBefore())
                    )
            );
            case NUMERIC_OVERFLOW -> EssenceCommandUtil.fail(source, EssenceText.command("error.investment_overflow"));
            case CONFIGURATION_ERROR -> EssenceCommandUtil.fail(source, EssenceText.command("error.investment_config"));
            case TRANSACTION_FAILED -> EssenceCommandUtil.fail(source, EssenceText.command("error.investment_transaction"));
            case SUCCESS -> throw new IllegalStateException("Successful investment reached failure handling");
        }
        return 0;
    }

    private static int showAscendanceProgress(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        AscendanceEvaluationResult evaluation = AscendanceEngine.evaluate(player);

        if (evaluation.status() == AscendanceEvaluationResult.Status.MAX_TIER) {
            EssenceCommandUtil.send(source, EssenceCommandUtil.title(EssenceText.command("progress.title")));
            EssenceCommandUtil.send(source, EssenceCommandUtil.line(EssenceText.command("label.current_tier"), EssenceText.ascendanceTier(evaluation.currentTier())));
            EssenceCommandUtil.send(source, EssenceCommandUtil.line(EssenceText.command("label.next_tier"), EssenceCommandUtil.good(EssenceText.command("state.max_tier"))));
            return 1;
        }

        if (evaluation.status() == AscendanceEvaluationResult.Status.CONFIGURATION_ERROR) {
            EssenceCommandUtil.fail(source, EssenceText.command("error.progress_config"));
            return 0;
        }

        AscendanceProgressSnapshot progress = evaluation.progress();
        String worldState = !progress.worldProgress().resolvable()
                ? "unresolved"
                : progress.worldProgress().complete() ? "complete" : "incomplete";

        EssenceCommandUtil.send(source, EssenceCommandUtil.title(EssenceText.command("progress.title")));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                EssenceText.command("label.transition"),
                EssenceText.command("value.transition", EssenceText.ascendanceTier(evaluation.currentTier()), EssenceText.ascendanceTier(evaluation.nextTier()))
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                EssenceText.command("label.investment_depth"),
                EssenceCommandUtil.format(progress.effectiveInvestment()) + " / "
                        + EssenceCommandUtil.format(progress.requiredInvestment())
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                EssenceText.command("label.developed_stats"),
                progress.developedStats() + " / " + progress.requiredDevelopedStats()
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                EssenceText.command("label.represented_categories"),
                progress.representedCategories() + " / " + progress.requiredRepresentedCategories()
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                EssenceText.command("label.world_progression"),
                "complete".equals(worldState)
                        ? EssenceCommandUtil.good(EssenceText.command("state.complete"))
                        : "unresolved".equals(worldState)
                        ? EssenceCommandUtil.bad(EssenceText.command("state.unresolved"))
                        : EssenceCommandUtil.warn(EssenceText.command("state.incomplete"))
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                EssenceText.command("label.ready_to_ascend"),
                progress.readyToAscend() ? EssenceCommandUtil.good(EssenceText.command("state.yes")) : EssenceCommandUtil.warn(EssenceText.command("state.no"))
        ));
        return 1;
    }

    private static int ascend(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        AscendanceAttemptResult result = AscendanceEngine.ascend(player);

        return switch (result.status()) {
            case SUCCESS -> {
                EssenceCommandUtil.send(source, EssenceCommandUtil.title(EssenceText.command("ascend.complete")));
                EssenceCommandUtil.send(
                        source,
                        EssenceCommandUtil.line(
                                EssenceText.command("label.tier"),
                                EssenceText.command(
                                        "value.transition",
                                        EssenceText.ascendanceTier(result.evaluation().currentTier()),
                                        EssenceText.ascendanceTier(result.evaluation().nextTier())
                                )
                        )
                );
                yield 1;
            }
            case NOT_READY -> {
                EssenceCommandUtil.fail(source, EssenceText.command("error.ascend_not_ready"));
                yield 0;
            }
            case MAX_TIER -> {
                EssenceCommandUtil.fail(source, EssenceText.command("error.ascend_max_tier"));
                yield 0;
            }
            case CONFIGURATION_ERROR -> {
                EssenceCommandUtil.fail(source, EssenceText.command("error.ascend_config"));
                yield 0;
            }
        };
    }

    private static int showMilestones(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        EssenceCommandUtil.send(source, EssenceCommandUtil.title(EssenceText.command("milestones.title")));

        for (MilestoneDefinition milestone : com.mistaboom.essence_ascendance.config.EssenceConfigManager.get().milestones().values()) {
            MilestoneProgress progress = evaluateMilestone(player, milestone);
            Component state = !progress.resolvable()
                    ? EssenceCommandUtil.bad(EssenceText.command("state.unresolved"))
                    : progress.complete()
                    ? EssenceCommandUtil.good(EssenceText.command("state.complete"))
                    : EssenceCommandUtil.warn(EssenceText.command("state.incomplete"));

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
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(EssenceText.command("label.id"), EssenceCommandUtil.muted(milestone.id().toString())));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(EssenceText.command("label.provider"), milestone.providerId().toString()));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(EssenceText.command("label.target"), milestone.target()));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                EssenceText.command("label.state"),
                !progress.resolvable()
                        ? EssenceCommandUtil.bad(EssenceText.command("state.unresolved"))
                        : progress.complete()
                        ? EssenceCommandUtil.good(EssenceText.command("state.complete"))
                        : EssenceCommandUtil.warn(EssenceText.command("state.incomplete"))
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
