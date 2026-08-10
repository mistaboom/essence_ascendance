package com.mistaboom.essence_ascendance.command;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.config.EssenceServerConfig;
import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
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
import com.mistaboom.essence_ascendance.tier.AscendanceTierDefinition;
import com.mistaboom.essence_ascendance.tier.AscendanceTierRegistry;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class EssenceDebugCommands {

    private static final DynamicCommandExceptionType UNKNOWN_STAT =
            new DynamicCommandExceptionType(
                    value -> Component.literal(
                            "Unknown stat: " + value
                    )
            );

    private static final DynamicCommandExceptionType UNKNOWN_CATEGORY =
            new DynamicCommandExceptionType(
                    value -> Component.literal(
                            "Unknown stat category: " + value
                    )
            );

    private static final DynamicCommandExceptionType UNKNOWN_MILESTONE =
            new DynamicCommandExceptionType(
                    value -> Component.literal(
                            "Unknown milestone: " + value
                    )
            );


    private EssenceDebugCommands() {
    }


    /*
     * ============================================================
     * COMMAND TREE
     * ============================================================
     */

    public static LiteralArgumentBuilder<CommandSourceStack> build() {

        return Commands.literal("debug")
                .requires(source ->
                        source.hasPermission(2)
                )

                /*
                 * /essence debug
                 * /essence debug summary
                 */
                .executes(context ->
                        showSummary(
                                context.getSource()
                        )
                )

                .then(
                        Commands.literal("summary")
                                .executes(context ->
                                        showSummary(
                                                context.getSource()
                                        )
                                )
                )

                /*
                 * /essence debug stat <stat>
                 */
                .then(
                        Commands.literal("stat")
                                .then(
                                        Commands.argument(
                                                        "stat",
                                                        StringArgumentType.word()
                                                )
                                                .suggests(
                                                        EssenceDebugCommands::suggestStats
                                                )
                                                .executes(context ->
                                                        showStat(
                                                                context.getSource(),
                                                                StringArgumentType.getString(
                                                                        context,
                                                                        "stat"
                                                                )
                                                        )
                                                )
                                )
                )

                /*
                 * /essence debug category <category>
                 */
                .then(
                        Commands.literal("category")
                                .then(
                                        Commands.argument(
                                                        "category",
                                                        StringArgumentType.word()
                                                )
                                                .suggests(
                                                        EssenceDebugCommands::suggestCategories
                                                )
                                                .executes(context ->
                                                        showCategory(
                                                                context.getSource(),
                                                                StringArgumentType.getString(
                                                                        context,
                                                                        "category"
                                                                )
                                                        )
                                                )
                                )
                )

                /*
                 * /essence debug config
                 */
                .then(
                        Commands.literal("config")
                                .executes(context ->
                                        showConfig(
                                                context.getSource()
                                        )
                                )
                )

                /*
                 * /essence debug reloadconfig
                 */
                .then(
                        Commands.literal("reloadconfig")
                                .executes(context ->
                                        reloadConfig(
                                                context.getSource()
                                        )
                                )
                )

                /*
                 * /essence debug milestones
                 */
                .then(
                        Commands.literal("milestones")
                                .executes(context ->
                                        showMilestones(
                                                context.getSource()
                                        )
                                )
                )

                /*
                 * /essence debug milestone <milestone>
                 */
                .then(
                        Commands.literal("milestone")
                                .then(
                                        Commands.argument(
                                                        "milestone",
                                                        StringArgumentType.string()
                                                )
                                                .suggests(
                                                        EssenceDebugCommands::suggestMilestones
                                                )
                                                .executes(context ->
                                                        showMilestone(
                                                                context.getSource(),
                                                                StringArgumentType.getString(
                                                                        context,
                                                                        "milestone"
                                                                )
                                                        )
                                                )
                                )
                )

                /*
                 * /essence debug setmilestone <milestone> <true|false>
                 *
                 * Only INTERNAL milestones may be changed directly.
                 */
                .then(
                        Commands.literal("setmilestone")
                                .then(
                                        Commands.argument(
                                                        "milestone",
                                                        StringArgumentType.string()
                                                )
                                                .suggests(
                                                        EssenceDebugCommands::suggestMilestones
                                                )
                                                .then(
                                                        Commands.argument(
                                                                        "complete",
                                                                        BoolArgumentType.bool()
                                                                )
                                                                .executes(context ->
                                                                        setMilestone(
                                                                                context.getSource(),
                                                                                StringArgumentType.getString(
                                                                                        context,
                                                                                        "milestone"
                                                                                ),
                                                                                BoolArgumentType.getBool(
                                                                                        context,
                                                                                        "complete"
                                                                                )
                                                                        )
                                                                )
                                                )
                                )
                );
    }


    /*
     * ============================================================
     * SUMMARY
     * ============================================================
     */

    private static int showSummary(
            CommandSourceStack source
    ) throws CommandSyntaxException {

        ServerPlayer player =
                source.getPlayerOrException();

        EssenceSavedData savedData =
                EssenceSavedData.get(
                        source.getServer()
                );

        PlayerEssenceData playerData =
                savedData.getPlayerData(
                        player.getUUID()
                );


        long totalStored =
                0L;

        long totalEffective =
                0L;

        long totalCapacity =
                0L;


        for (StatDefinition stat :
                EssenceStatRegistry.values()) {

            StatInvestmentLimit limit =
                    TierInvestmentPolicy.evaluate(
                            playerData,
                            stat
                    );


            totalStored =
                    Math.addExact(
                            totalStored,
                            limit.storedInvestment()
                    );


            totalEffective =
                    Math.addExact(
                            totalEffective,
                            limit.effectiveInvestment()
                    );


            totalCapacity =
                    Math.addExact(
                            totalCapacity,
                            limit.investmentCap()
                    );
        }


        final long finalTotalStored =
                totalStored;

        final long finalTotalEffective =
                totalEffective;

        final long finalTotalCapacity =
                totalCapacity;


        EssenceServerConfig config =
                EssenceConfigManager.get();


        source.sendSuccess(
                () -> Component.literal(
                        "Essence Ascendance debug summary:"
                ),
                false
        );


        source.sendSuccess(
                () -> Component.literal(
                        "  Player: "
                                + player.getGameProfile().getName()
                ),
                false
        );


        source.sendSuccess(
                () -> Component.literal(
                        "  Tier: "
                                + playerData.getTier().displayName()
                                + " ["
                                + playerData.getTier().id()
                                + "]"
                ),
                false
        );


        source.sendSuccess(
                () -> Component.literal(
                        "  Balance profile: "
                                + config.balanceProfile().displayName()
                                + " ["
                                + config.balanceProfile().id()
                                + "]"
                ),
                false
        );


        source.sendSuccess(
                () -> Component.literal(
                        "  Stored investment: "
                                + format(
                                finalTotalStored
                        )
                ),
                false
        );


        source.sendSuccess(
                () -> Component.literal(
                        "  Effective investment: "
                                + format(
                                finalTotalEffective
                        )
                                + " / "
                                + format(
                                finalTotalCapacity
                        )
                ),
                false
        );


        source.sendSuccess(
                () -> Component.literal(
                        "  Completed internal milestones: "
                                + playerData
                                .getCompletedMilestones()
                                .size()
                ),
                false
        );


        AscendanceEvaluationResult evaluation =
                AscendanceEngine.evaluate(
                        player
                );


        switch (evaluation.status()) {

            case AVAILABLE ->

                    source.sendSuccess(
                            () -> Component.literal(
                                    "  Next tier: "
                                            + evaluation.nextTier().displayName()
                                            + " | Ready: "
                                            + (
                                            evaluation
                                                    .progress()
                                                    .readyToAscend()
                                                    ? "YES"
                                                    : "NO"
                                    )
                            ),
                            false
                    );


            case MAX_TIER ->

                    source.sendSuccess(
                            () -> Component.literal(
                                    "  Next tier: MAX TIER"
                            ),
                            false
                    );


            case CONFIGURATION_ERROR ->

                    source.sendSuccess(
                            () -> Component.literal(
                                    "  Next tier: CONFIGURATION ERROR"
                            ),
                            false
                    );
        }


        return 1;
    }


    /*
     * ============================================================
     * STAT DEBUG
     * ============================================================
     */

    private static int showStat(
            CommandSourceStack source,
            String statName
    ) throws CommandSyntaxException {

        ServerPlayer player =
                source.getPlayerOrException();

        StatDefinition stat =
                resolveStat(
                        statName
                );


        PlayerEssenceData playerData =
                EssenceSavedData
                        .get(source.getServer())
                        .getPlayerData(
                                player.getUUID()
                        );


        StatInvestmentLimit limit =
                TierInvestmentPolicy.evaluate(
                        playerData,
                        stat
                );


        StatScalingResult scaling =
                StatScalingService.evaluate(
                        playerData,
                        stat
                );


        source.sendSuccess(
                () -> Component.literal(
                        "Stat Debug: "
                                + stat.displayName()
                ),
                false
        );


        source.sendSuccess(
                () -> Component.literal(
                        "  ID: "
                                + stat.id()
                ),
                false
        );


        source.sendSuccess(
                () -> Component.literal(
                        "  Category: "
                                + stat.category()
                                .name()
                                .toLowerCase(
                                        Locale.ROOT
                                )
                ),
                false
        );


        source.sendSuccess(
                () -> Component.literal(
                        "  Essence: "
                                + stat.essenceType().displayName()
                                + " ["
                                + stat.essenceType().id()
                                + "]"
                ),
                false
        );


        source.sendSuccess(
                () -> Component.literal(
                        "  Unit: "
                                + stat.unit()
                                .name()
                                .toLowerCase(
                                        Locale.ROOT
                                )
                ),
                false
        );


        source.sendSuccess(
                () -> Component.literal(
                        "  Tier: "
                                + scaling.tier().displayName()
                ),
                false
        );


        source.sendSuccess(
                () -> Component.literal(
                        "  Stored: "
                                + format(
                                limit.storedInvestment()
                        )
                ),
                false
        );


        source.sendSuccess(
                () -> Component.literal(
                        "  Effective: "
                                + format(
                                limit.effectiveInvestment()
                        )
                                + " / "
                                + format(
                                limit.investmentCap()
                        )
                ),
                false
        );


        source.sendSuccess(
                () -> Component.literal(
                        "  Remaining capacity: "
                                + format(
                                limit.remainingCapacity()
                        )
                ),
                false
        );


        source.sendSuccess(
                () -> Component.literal(
                        "  Investment state: "
                                + limit.state()
                ),
                false
        );


        source.sendSuccess(
                () -> Component.literal(
                        "  Scaling progress: "
                                + formatPercent(
                                scaling.progression()
                        )
                ),
                false
        );


        source.sendSuccess(
                () -> Component.literal(
                        "  Current bonus: "
                                + formatBonus(
                                stat,
                                scaling.scaledBonus()
                        )
                ),
                false
        );


        source.sendSuccess(
                () -> Component.literal(
                        "  Tier bonus ceiling: "
                                + formatBonus(
                                stat,
                                scaling.currentTierMaximumBonus()
                        )
                ),
                false
        );


        source.sendSuccess(
                () -> Component.literal(
                        "  Transcendent maximum: "
                                + formatBonus(
                                stat,
                                scaling.transcendentMaximumBonus()
                        )
                ),
                false
        );


        return 1;
    }


    /*
     * ============================================================
     * CATEGORY DEBUG
     * ============================================================
     */

    private static int showCategory(
            CommandSourceStack source,
            String categoryName
    ) throws CommandSyntaxException {

        ServerPlayer player =
                source.getPlayerOrException();

        StatCategory category =
                resolveCategory(
                        categoryName
                );


        CategoryDevelopment development =
                StatScalingService.evaluateCategory(
                        player,
                        category
                );


        int statCount =
                0;


        for (StatDefinition stat :
                EssenceStatRegistry.values()) {

            if (stat.category()
                    == category) {

                statCount++;
            }
        }


        source.sendSuccess(
                () -> Component.literal(
                        "Category Debug: "
                                + category
                                .name()
                                .toLowerCase(
                                        Locale.ROOT
                                )
                ),
                false
        );


        int finalStatCount =
                statCount;


        source.sendSuccess(
                () -> Component.literal(
                        "  Registered stats: "
                                + finalStatCount
                ),
                false
        );


        source.sendSuccess(
                () -> Component.literal(
                        "  Stored investment: "
                                + format(
                                development.storedInvestment()
                        )
                ),
                false
        );


        source.sendSuccess(
                () -> Component.literal(
                        "  Effective investment: "
                                + format(
                                development.effectiveInvestment()
                        )
                                + " / "
                                + format(
                                development.currentCapacity()
                        )
                ),
                false
        );


        source.sendSuccess(
                () -> Component.literal(
                        "  Development: "
                                + formatPercent(
                                development.development()
                        )
                ),
                false
        );


        return 1;
    }


    /*
     * ============================================================
     * CONFIG DEBUG
     * ============================================================
     */

    private static int showConfig(
            CommandSourceStack source
    ) {

        EssenceServerConfig config =
                EssenceConfigManager.get();


        source.sendSuccess(
                () -> Component.literal(
                        "Essence Ascendance configuration:"
                ),
                false
        );


        source.sendSuccess(
                () -> Component.literal(
                        "  Path: "
                                + EssenceConfigManager
                                .getConfigPath()
                                .toAbsolutePath()
                ),
                false
        );


        source.sendSuccess(
                () -> Component.literal(
                        "  Config version: "
                                + config.configVersion()
                ),
                false
        );


        source.sendSuccess(
                () -> Component.literal(
                        "  Balance profile: "
                                + config.balanceProfile().displayName()
                                + " ["
                                + config.balanceProfile().id()
                                + "]"
                ),
                false
        );


        source.sendSuccess(
                () -> Component.literal(
                        "  Stat scaling definitions: "
                                + config
                                .statMaxBonuses()
                                .size()
                ),
                false
        );


        source.sendSuccess(
                () -> Component.literal(
                        "  Milestone definitions: "
                                + config
                                .milestones()
                                .size()
                ),
                false
        );


        source.sendSuccess(
                () -> Component.literal(
                        "  Advancement definitions: "
                                + config
                                .advancements()
                                .size()
                ),
                false
        );


        source.sendSuccess(
                () -> Component.literal(
                        "  Default per-stat tier caps:"
                ),
                false
        );


        for (AscendanceTierDefinition tier :
                orderedTiers()) {

            long cap =
                    config
                            .balanceProfile()
                            .getDefaultInvestmentCap(
                                    tier
                            );


            source.sendSuccess(
                    () -> Component.literal(
                            "    "
                                    + tier.displayName()
                                    + ": "
                                    + format(cap)
                    ),
                    false
            );
        }


        return 1;
    }


    private static int reloadConfig(
            CommandSourceStack source
    ) {

        EssenceConfigManager.reload();


        source.sendSuccess(
                () -> Component.literal(
                        "Reloaded Essence Ascendance configuration."
                ),
                false
        );


        return showConfig(
                source
        );
    }


    /*
     * ============================================================
     * MILESTONE DEBUG
     * ============================================================
     */

    private static int showMilestones(
            CommandSourceStack source
    ) throws CommandSyntaxException {

        ServerPlayer player =
                source.getPlayerOrException();


        List<MilestoneDefinition> milestones =
                new ArrayList<>(
                        EssenceConfigManager
                                .get()
                                .milestones()
                                .values()
                );


        milestones.sort(
                Comparator.comparing(
                        milestone ->
                                milestone
                                        .id()
                                        .toString()
                )
        );


        source.sendSuccess(
                () -> Component.literal(
                        "Configured Ascendance milestones:"
                ),
                false
        );


        for (MilestoneDefinition milestone :
                milestones) {

            MilestoneProgress progress =
                    evaluateMilestone(
                            player,
                            milestone
                    );


            String state =
                    milestoneState(
                            progress
                    );


            source.sendSuccess(
                    () -> Component.literal(
                            "  "
                                    + milestone.displayName()
                                    + " ["
                                    + milestone.id()
                                    + "] - "
                                    + state
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

        ServerPlayer player =
                source.getPlayerOrException();

        MilestoneDefinition milestone =
                resolveMilestone(
                        milestoneName
                );


        MilestoneProgress progress =
                evaluateMilestone(
                        player,
                        milestone
                );


        source.sendSuccess(
                () -> Component.literal(
                        "Milestone Debug: "
                                + milestone.displayName()
                ),
                false
        );


        source.sendSuccess(
                () -> Component.literal(
                        "  ID: "
                                + milestone.id()
                ),
                false
        );


        source.sendSuccess(
                () -> Component.literal(
                        "  Provider: "
                                + milestone.providerId()
                ),
                false
        );


        source.sendSuccess(
                () -> Component.literal(
                        "  Target: "
                                + milestone.target()
                ),
                false
        );


        source.sendSuccess(
                () -> Component.literal(
                        "  Resolvable: "
                                + (
                                progress.resolvable()
                                        ? "YES"
                                        : "NO"
                        )
                ),
                false
        );


        source.sendSuccess(
                () -> Component.literal(
                        "  Complete: "
                                + (
                                progress.complete()
                                        ? "YES"
                                        : "NO"
                        )
                ),
                false
        );


        return 1;
    }


    private static int setMilestone(
            CommandSourceStack source,
            String milestoneName,
            boolean complete
    ) throws CommandSyntaxException {

        ServerPlayer player =
                source.getPlayerOrException();

        MilestoneDefinition milestone =
                resolveMilestone(
                        milestoneName
                );


        /*
         * Advancement-backed milestones must remain authoritative
         * to Minecraft's advancement system.
         */

        if (!milestone
                .providerId()
                .equals(
                        MilestoneProviders.INTERNAL
                )) {

            source.sendFailure(
                    Component.literal(
                            "Milestone "
                                    + milestone.id()
                                    + " is not an internal milestone and cannot be directly changed."
                    )
            );


            return 0;
        }


        ResourceLocation target =
                ResourceLocation.tryParse(
                        milestone.target()
                );


        if (target == null) {

            source.sendFailure(
                    Component.literal(
                            "Milestone "
                                    + milestone.id()
                                    + " has an invalid internal target: "
                                    + milestone.target()
                    )
            );


            return 0;
        }


        EssenceSavedData savedData =
                EssenceSavedData.get(
                        source.getServer()
                );


        boolean changed;


        if (complete) {

            changed =
                    savedData.completeInternalMilestone(
                            player.getUUID(),
                            target
                    );

        } else {

            changed =
                    savedData.revokeInternalMilestone(
                            player.getUUID(),
                            target
                    );
        }


        String desiredState =
                complete
                        ? "COMPLETE"
                        : "INCOMPLETE";


        source.sendSuccess(
                () -> Component.literal(
                        "Milestone "
                                + milestone.displayName()
                                + " is now "
                                + desiredState
                                + (
                                changed
                                        ? "."
                                        : " (state was already set)."
                        )
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
                MilestoneRequirement.milestone(
                        milestone.id()
                )
        );
    }


    /*
     * ============================================================
     * LOOKUP
     * ============================================================
     */

    private static StatDefinition resolveStat(
            String input
    ) throws CommandSyntaxException {

        ResourceLocation id =
                parseId(
                        input
                );


        if (id == null) {
            throw UNKNOWN_STAT.create(
                    input
            );
        }


        return EssenceStatRegistry
                .get(id)
                .orElseThrow(
                        () -> UNKNOWN_STAT.create(
                                input
                        )
                );
    }


    private static StatCategory resolveCategory(
            String input
    ) throws CommandSyntaxException {

        try {

            return StatCategory.valueOf(
                    input.toUpperCase(
                            Locale.ROOT
                    )
            );


        } catch (IllegalArgumentException exception) {

            throw UNKNOWN_CATEGORY.create(
                    input
            );
        }
    }


    private static MilestoneDefinition resolveMilestone(
            String input
    ) throws CommandSyntaxException {

        ResourceLocation id =
                parseId(
                        input
                );


        if (id == null) {
            throw UNKNOWN_MILESTONE.create(
                    input
            );
        }


        return EssenceConfigManager
                .get()
                .getMilestone(
                        id
                )
                .orElseThrow(
                        () -> UNKNOWN_MILESTONE.create(
                                input
                        )
                );
    }


    private static ResourceLocation parseId(
            String input
    ) {

        String fullId =
                input.contains(":")
                        ? input
                        : EssenceAscendance.MOD_ID
                        + ":"
                        + input;


        return ResourceLocation.tryParse(
                fullId
        );
    }


    /*
     * ============================================================
     * SUGGESTIONS
     * ============================================================
     */

    private static CompletableFuture<Suggestions> suggestStats(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder
    ) {

        String remaining =
                builder.getRemainingLowerCase();


        for (StatDefinition stat :
                EssenceStatRegistry.values()) {

            String name =
                    stat.id().getPath();


            if (name.startsWith(
                    remaining
            )) {

                builder.suggest(
                        name
                );
            }
        }


        return builder.buildFuture();
    }


    private static CompletableFuture<Suggestions> suggestCategories(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder
    ) {

        String remaining =
                builder.getRemainingLowerCase();


        for (StatCategory category :
                StatCategory.values()) {

            String name =
                    category
                            .name()
                            .toLowerCase(
                                    Locale.ROOT
                            );


            if (name.startsWith(
                    remaining
            )) {

                builder.suggest(
                        name
                );
            }
        }


        return builder.buildFuture();
    }


    private static CompletableFuture<Suggestions> suggestMilestones(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder
    ) {

        String remaining =
                builder.getRemainingLowerCase();


        for (MilestoneDefinition milestone :
                EssenceConfigManager
                        .get()
                        .milestones()
                        .values()) {

            ResourceLocation id =
                    milestone.id();


            String suggestion;


            if (id.getNamespace()
                    .equals(
                            EssenceAscendance.MOD_ID
                    )) {

                suggestion =
                        id.getPath();

            } else {

                /*
                 * StringArgumentType.string() allows a quoted
                 * namespaced ID when needed.
                 */

                suggestion =
                        "\""
                                + id
                                + "\"";
            }


            if (suggestion
                    .toLowerCase(
                            Locale.ROOT
                    )
                    .startsWith(
                            remaining
                    )) {

                builder.suggest(
                        suggestion
                );
            }
        }


        return builder.buildFuture();
    }


    /*
     * ============================================================
     * FORMATTING
     * ============================================================
     */

    private static List<AscendanceTierDefinition> orderedTiers() {

        List<AscendanceTierDefinition> tiers =
                new ArrayList<>(
                        AscendanceTierRegistry.values()
                );


        tiers.sort(
                Comparator.comparingInt(
                        AscendanceTierDefinition::order
                )
        );


        return List.copyOf(
                tiers
        );
    }


    private static String milestoneState(
            MilestoneProgress progress
    ) {

        if (!progress.resolvable()) {
            return "UNRESOLVED";
        }


        return progress.complete()
                ? "COMPLETE"
                : "INCOMPLETE";
    }


    private static String formatPercent(
            double value
    ) {

        return String.format(
                "%.2f%%",
                value * 100.0
        );
    }


    private static String formatBonus(
            StatDefinition stat,
            double value
    ) {

        return switch (stat.unit()) {

            case PERCENT ->
                    String.format(
                            "%.2f%%",
                            value
                    );

            case HEARTS ->
                    String.format(
                            "%.2f hearts",
                            value
                    );

            case HEARTS_PER_SECOND ->
                    String.format(
                            "%.3f hearts/sec",
                            value
                    );

            case BLOCKS ->
                    String.format(
                            "%.2f blocks",
                            value
                    );

            case SECONDS ->
                    String.format(
                            "%.2f seconds",
                            value
                    );

            case LEVELS ->
                    String.format(
                            "%.2f levels",
                            value
                    );

            case FLAT ->
                    String.format(
                            "%.3f",
                            value
                    );
        };
    }


    private static String format(
            long value
    ) {

        return String.format(
                "%,d",
                value
        );
    }
}