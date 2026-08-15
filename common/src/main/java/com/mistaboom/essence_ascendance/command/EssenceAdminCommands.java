package com.mistaboom.essence_ascendance.command;

import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.lifecycle.PlayerRuntimeLifecycleService;
import com.mistaboom.essence_ascendance.mapping.ItemEssenceMappingDefinition;
import com.mistaboom.essence_ascendance.mapping.ItemEssenceMappingRegistry;
import com.mistaboom.essence_ascendance.mapping.ItemEssenceMappings;
import com.mistaboom.essence_ascendance.config.EssenceServerConfig;
import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import com.mistaboom.essence_ascendance.progression.HarvestProgressionSafety;
import com.mistaboom.essence_ascendance.progression.MilestoneDefinition;
import com.mistaboom.essence_ascendance.progression.MilestoneProviders;
import com.mistaboom.essence_ascendance.progression.StatInvestmentLimit;
import com.mistaboom.essence_ascendance.progression.TierInvestmentPolicy;
import com.mistaboom.essence_ascendance.stat.EssenceStatRegistry;
import com.mistaboom.essence_ascendance.stat.StatDefinition;
import com.mistaboom.essence_ascendance.tier.AscendanceTierDefinition;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

final class EssenceAdminCommands {

    private EssenceAdminCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("admin")
                .requires(source -> source.hasPermission(EssenceCommandUtil.ADMIN_PERMISSION))
                .executes(context -> showHelp(context.getSource()))
                .then(
                        Commands.literal("help")
                                .executes(context -> showHelp(context.getSource()))
                )
                .then(
                        Commands.literal("essence")
                                .executes(context -> showEssenceHelp(context.getSource()))
                                .then(
                                        Commands.literal("give")
                                                .then(
                                                        Commands.argument("essence", StringArgumentType.word())
                                                                .suggests(EssenceCommandUtil::suggestEssences)
                                                                .then(
                                                                        Commands.argument("amount", LongArgumentType.longArg(1))
                                                                                .executes(context -> giveEssence(
                                                                                        context.getSource(),
                                                                                        StringArgumentType.getString(context, "essence"),
                                                                                        LongArgumentType.getLong(context, "amount")
                                                                                ))
                                                                )
                                                )
                                )
                                .then(
                                        Commands.literal("set")
                                                .then(
                                                        Commands.argument("essence", StringArgumentType.word())
                                                                .suggests(EssenceCommandUtil::suggestEssences)
                                                                .then(
                                                                        Commands.argument("amount", LongArgumentType.longArg(0))
                                                                                .executes(context -> setEssence(
                                                                                        context.getSource(),
                                                                                        StringArgumentType.getString(context, "essence"),
                                                                                        LongArgumentType.getLong(context, "amount")
                                                                                ))
                                                                )
                                                )
                                )
                )
                .then(
                        Commands.literal("stat")
                                .executes(context -> showStatHelp(context.getSource()))
                                .then(
                                        Commands.literal("set")
                                                .then(
                                                        Commands.argument("stat", StringArgumentType.word())
                                                                .suggests(EssenceCommandUtil::suggestStats)
                                                                .then(
                                                                        Commands.argument("amount", LongArgumentType.longArg(0))
                                                                                .executes(context -> setStat(
                                                                                        context.getSource(),
                                                                                        StringArgumentType.getString(context, "stat"),
                                                                                        LongArgumentType.getLong(context, "amount")
                                                                                ))
                                                                )
                                                )
                                )
                                .then(
                                        Commands.literal("max")
                                                .then(
                                                        Commands.argument("stat", StringArgumentType.word())
                                                                .suggests(EssenceCommandUtil::suggestStats)
                                                                .executes(context -> maxStat(
                                                                        context.getSource(),
                                                                        StringArgumentType.getString(context, "stat")
                                                                ))
                                                )
                                )
                                .then(
                                        Commands.literal("clear")
                                                .then(
                                                        Commands.argument("stat", StringArgumentType.word())
                                                                .suggests(EssenceCommandUtil::suggestStats)
                                                                .executes(context -> setStat(
                                                                        context.getSource(),
                                                                        StringArgumentType.getString(context, "stat"),
                                                                        0L
                                                                ))
                                                )
                                )
                )
                .then(
                        Commands.literal("stats")
                                .executes(context -> showStatsHelp(context.getSource()))
                                .then(
                                        Commands.literal("max")
                                                .executes(context -> maxAllStats(context.getSource()))
                                )
                                .then(
                                        Commands.literal("clear")
                                                .executes(context -> clearAllStats(context.getSource()))
                                )
                )
                .then(
                        Commands.literal("tier")
                                .then(
                                        Commands.literal("set")
                                                .then(
                                                        Commands.argument("tier", StringArgumentType.word())
                                                                .suggests(EssenceCommandUtil::suggestTiers)
                                                                .executes(context -> setTier(
                                                                        context.getSource(),
                                                                        StringArgumentType.getString(context, "tier")
                                                                ))
                                                )
                                )
                )
                .then(
                        Commands.literal("milestone")
                                .then(
                                        Commands.literal("set")
                                                .then(
                                                        Commands.argument("milestone", StringArgumentType.string())
                                                                .suggests(EssenceCommandUtil::suggestMilestones)
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
                )
                .then(
                        Commands.literal("mappings")
                                .executes(context -> showMappings(context.getSource()))
                                .then(
                                        Commands.literal("reload")
                                                .executes(context -> reloadMappings(context.getSource()))
                                )
                                .then(
                                        Commands.literal("list")
                                                .executes(context -> listMappings(context.getSource()))
                                )
                )
                .then(
                        Commands.literal("config")
                                .executes(context -> showConfig(context.getSource()))
                                .then(
                                        Commands.literal("reload")
                                                .executes(context -> reloadConfig(context.getSource()))
                                )
                )
                .then(
                        Commands.literal("reset")
                                .executes(context -> resetAll(context.getSource()))
                );
    }

    static int showHelp(CommandSourceStack source) {
        EssenceCommandUtil.send(source, EssenceCommandUtil.title("Essence Admin Commands"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence admin essence give <essence> <amount>", "add available Essence"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence admin essence set <essence> <amount>", "set an available Essence balance"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence admin stat set <stat> <amount>", "set stored investment directly"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence admin stat max <stat>", "set one stat exactly to its current tier cap"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence admin stat clear <stat>", "clear one stat investment"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence admin stats max", "max every stat to the current tier caps"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence admin stats clear", "clear investments without clearing balances"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence admin tier set <tier>", "force the current tier"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence admin milestone set <milestone> <true|false>", "set an INTERNAL milestone"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence admin mappings", "show item mapping status and config path"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence admin mappings reload", "reload item mappings from global config"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence admin mappings list", "list the active mapping IDs/selectors"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence admin config", "show the loaded server configuration"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence admin config reload", "reload configuration and show the result"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence admin reset", "clear all balances and investments"));
        return 1;
    }

    private static int showEssenceHelp(CommandSourceStack source) {
        EssenceCommandUtil.send(source, EssenceCommandUtil.section("Admin - Essence balances"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence admin essence give <essence> <amount>", "add to a balance"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence admin essence set <essence> <amount>", "replace a balance"));
        return 1;
    }

    private static int showStatHelp(CommandSourceStack source) {
        EssenceCommandUtil.send(source, EssenceCommandUtil.section("Admin - One stat"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence admin stat set <stat> <amount>", "set stored investment directly"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence admin stat max <stat>", "set to the exact current cap"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence admin stat clear <stat>", "set stored investment to zero"));
        return 1;
    }

    private static int showStatsHelp(CommandSourceStack source) {
        EssenceCommandUtil.send(source, EssenceCommandUtil.section("Admin - All stats"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence admin stats max", "set every stat to its current cap"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence admin stats clear", "clear every stat while preserving Essence balances"));
        return 1;
    }

    private static int giveEssence(CommandSourceStack source, String essenceName, long amount) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        EssenceDefinition essence = EssenceCommandUtil.resolveEssence(essenceName);
        long updated = EssenceSavedData
                .get(source.getServer())
                .addEssence(player.getUUID(), essence, amount);

        EssenceCommandUtil.send(source, EssenceCommandUtil.good(
                "Added " + EssenceCommandUtil.format(amount) + " " + essence.displayName()
                        + ". New balance: " + EssenceCommandUtil.format(updated)
        ));
        return 1;
    }

    private static int setEssence(CommandSourceStack source, String essenceName, long amount) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        EssenceDefinition essence = EssenceCommandUtil.resolveEssence(essenceName);
        EssenceSavedData.get(source.getServer()).setEssence(player.getUUID(), essence, amount);

        EssenceCommandUtil.send(source, EssenceCommandUtil.good(
                "Set " + essence.displayName() + " to " + EssenceCommandUtil.format(amount) + "."
        ));
        return 1;
    }

    private static int setStat(CommandSourceStack source, String statName, long amount) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        StatDefinition stat = EssenceCommandUtil.resolveStat(statName);
        EssenceSavedData.get(source.getServer()).setInvested(player.getUUID(), stat, amount);

        EssenceCommandUtil.send(source, EssenceCommandUtil.good(
                "Set " + stat.displayName() + " stored investment to " + EssenceCommandUtil.format(amount) + "."
        ));
        return 1;
    }

    private static int maxStat(CommandSourceStack source, String statName) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        StatDefinition stat = EssenceCommandUtil.resolveStat(statName);
        PlayerEssenceData data = playerData(source, player);
        StatInvestmentLimit limit = TierInvestmentPolicy.evaluate(data, stat);
        EssenceSavedData.get(source.getServer()).setInvested(
                player.getUUID(),
                stat,
                limit.investmentCap()
        );

        EssenceCommandUtil.send(source, EssenceCommandUtil.good(
                "Maxed " + stat.displayName() + " at the current tier cap of "
                        + EssenceCommandUtil.format(limit.investmentCap()) + "."
        ));
        return 1;
    }

    private static int maxAllStats(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        EssenceSavedData savedData = EssenceSavedData.get(source.getServer());
        PlayerEssenceData data = savedData.getPlayerData(player.getUUID());
        int count = 0;

        for (StatDefinition stat : EssenceStatRegistry.values()) {
            StatInvestmentLimit limit = TierInvestmentPolicy.evaluate(data, stat);
            savedData.setInvested(player.getUUID(), stat, limit.investmentCap());
            count++;
        }

        EssenceCommandUtil.send(source, EssenceCommandUtil.good(
                "Maxed " + count + " stats to their current tier caps."
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.muted("This does not consume available Essence; it is an admin/testing operation."));
        return 1;
    }

    private static int clearAllStats(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        EssenceSavedData savedData = EssenceSavedData.get(source.getServer());
        int count = 0;

        for (StatDefinition stat : EssenceStatRegistry.values()) {
            savedData.setInvested(player.getUUID(), stat, 0L);
            count++;
        }

        EssenceCommandUtil.send(source, EssenceCommandUtil.good(
                "Cleared investments for " + count + " stats. Available Essence balances were preserved."
        ));
        return 1;
    }

    private static int setTier(CommandSourceStack source, String tierName) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        AscendanceTierDefinition tier = EssenceCommandUtil.resolveTier(tierName);
        EssenceSavedData.get(source.getServer()).setTier(player.getUUID(), tier);

        EssenceCommandUtil.send(source, EssenceCommandUtil.good(
                "Ascendance tier set to " + tier.displayName() + "."
        ));
        return 1;
    }

    private static int setMilestone(
            CommandSourceStack source,
            String milestoneName,
            boolean complete
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        MilestoneDefinition milestone = EssenceCommandUtil.resolveMilestone(milestoneName);

        if (!milestone.providerId().equals(MilestoneProviders.INTERNAL)) {
            EssenceCommandUtil.fail(
                    source,
                    "Only INTERNAL milestones can be changed directly. "
                            + milestone.id() + " uses " + milestone.providerId() + "."
            );
            return 0;
        }

        ResourceLocation target = ResourceLocation.tryParse(milestone.target());
        if (target == null) {
            EssenceCommandUtil.fail(source, "Milestone has invalid internal target: " + milestone.target());
            return 0;
        }

        EssenceSavedData savedData = EssenceSavedData.get(player.server);
        boolean changed = complete
                ? savedData.completeInternalMilestone(player.getUUID(), target)
                : savedData.revokeInternalMilestone(player.getUUID(), target);

        Component state = complete
                ? EssenceCommandUtil.good("COMPLETE")
                : EssenceCommandUtil.warn("INCOMPLETE");
        EssenceCommandUtil.send(
                source,
                Component.literal("Milestone ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(milestone.displayName()).withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(" is now ").withStyle(ChatFormatting.GRAY))
                        .append(state)
                        .append(Component.literal(changed ? "." : " (state was already set).").withStyle(ChatFormatting.GRAY))
        );
        return 1;
    }

    private static int resetAll(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        EssenceSavedData.get(source.getServer()).clearAll(player.getUUID());
        EssenceCommandUtil.send(source, EssenceCommandUtil.good("Reset all Essence balances and stat investments."));
        return 1;
    }


    private static int showMappings(
            CommandSourceStack source
    ) {
        ItemEssenceMappingRegistry.ReloadReport report =
                ItemEssenceMappingRegistry.lastReload();

        EssenceCommandUtil.send(
                source,
                EssenceCommandUtil.title(
                        "Item → Attribute Essence Mappings"
                )
        );

        EssenceCommandUtil.send(
                source,
                EssenceCommandUtil.line(
                        "Config path",
                        EssenceCommandUtil.muted(
                                ItemEssenceMappings
                                        .configDirectory()
                                        .toAbsolutePath()
                                        .toString()
                        )
                )
        );

        EssenceCommandUtil.send(
                source,
                EssenceCommandUtil.line(
                        "Last load",
                        report.successful()
                                ? EssenceCommandUtil.good(
                                        "SUCCESS"
                                )
                                : EssenceCommandUtil.bad(
                                        "REJECTED / NOT LOADED"
                                )
                )
        );

        EssenceCommandUtil.send(
                source,
                EssenceCommandUtil.line(
                        "Generation",
                        Long.toString(
                                report.generation()
                        )
                )
        );

        EssenceCommandUtil.send(
                source,
                EssenceCommandUtil.line(
                        "Bundled defaults",
                        Integer.toString(
                                report.bundledDefaultCount()
                        )
                )
        );

        EssenceCommandUtil.send(
                source,
                EssenceCommandUtil.line(
                        "Default changes",
                        report.removedDefaultCount()
                                + " removed, "
                                + report.replacedDefaultCount()
                                + " replaced"
                )
        );

        EssenceCommandUtil.send(
                source,
                EssenceCommandUtil.line(
                        "Config mappings",
                        report.configMappingCount()
                                + " mapping(s) from "
                                + report.configFileCount()
                                + " file(s)"
                )
        );

        EssenceCommandUtil.send(
                source,
                EssenceCommandUtil.line(
                        "Active",
                        report.activeMappingCount()
                                + " mappings ("
                                + report.explicitItemRuleCount()
                                + " item, "
                                + report.tagRuleCount()
                                + " tag)"
                )
        );

        if (!report.warnings()
                .isEmpty()) {

            EssenceCommandUtil.send(
                    source,
                    EssenceCommandUtil.section(
                            "Warnings"
                    )
            );

            for (String warning :
                    report.warnings()) {

                EssenceCommandUtil.send(
                        source,
                        EssenceCommandUtil.warn(
                                "  "
                                        + warning
                        )
                );
            }
        }

        if (!report.errors()
                .isEmpty()) {

            EssenceCommandUtil.send(
                    source,
                    EssenceCommandUtil.section(
                            "Errors"
                    )
            );

            for (String error :
                    report.errors()) {

                EssenceCommandUtil.send(
                        source,
                        EssenceCommandUtil.bad(
                                "  "
                                        + error
                        )
                );
            }
        }

        return report.successful()
                ? 1
                : 0;
    }


    private static int reloadMappings(
            CommandSourceStack source
    ) {
        long before =
                ItemEssenceMappingRegistry.generation();

        ItemEssenceMappingRegistry.ReloadReport report =
                ItemEssenceMappings.reload();

        if (report.successful()
                && report.generation()
                > before) {

            EssenceCommandUtil.send(
                    source,
                    EssenceCommandUtil.good(
                            "Reloaded item → Attribute Essence mappings."
                    )
            );

        } else {
            EssenceCommandUtil.send(
                    source,
                    EssenceCommandUtil.bad(
                            "Item mapping reload was rejected; the previous known-good generation remains active."
                    )
            );
        }

        return showMappings(
                source
        );
    }


    private static int listMappings(
            CommandSourceStack source
    ) {
        EssenceCommandUtil.send(
                source,
                EssenceCommandUtil.title(
                        "Active Item → Essence Mapping Rules"
                )
        );

        if (ItemEssenceMappingRegistry.definitions()
                .isEmpty()) {

            EssenceCommandUtil.send(
                    source,
                    EssenceCommandUtil.muted(
                            "No active mappings."
                    )
            );

            return 1;
        }

        for (ItemEssenceMappingDefinition definition :
                ItemEssenceMappingRegistry.definitions()) {

            String outputs =
                    definition.outputs()
                            .entrySet()
                            .stream()
                            .sorted(
                                    java.util.Comparator.comparing(
                                            entry ->
                                                    entry.getKey()
                                                            .id()
                                                            .toString()
                                    )
                            )
                            .map(
                                    entry ->
                                            entry.getKey()
                                                    .id()
                                                    .getPath()
                                                    + "="
                                                    + EssenceCommandUtil.format(
                                                            entry.getValue()
                                                    )
                            )
                            .collect(
                                    java.util.stream.Collectors.joining(
                                            ", "
                                    )
                            );

            if (outputs.isBlank()) {
                outputs =
                        "BLOCK";
            }

            EssenceCommandUtil.send(
                    source,
                    EssenceCommandUtil.muted(
                            "  "
                                    + definition.id()
                                    + " | p="
                                    + definition.priority()
                                    + " | "
                                    + definition.selectorDisplay()
                                    + " | "
                                    + outputs
                    )
            );
        }

        return 1;
    }


    private static int showConfig(CommandSourceStack source) {
        EssenceServerConfig config = EssenceConfigManager.get();
        EssenceCommandUtil.send(source, EssenceCommandUtil.title("Essence Ascendance Configuration"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line("Path", EssenceCommandUtil.muted(
                EssenceConfigManager.getConfigPath().toAbsolutePath().toString()
        )));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line("Config version", Integer.toString(config.configVersion())));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Balance profile",
                config.balanceProfile().displayName() + " [" + config.balanceProfile().id() + "]"
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Definitions",
                config.statMaxBonuses().size() + " stat bonuses, "
                        + config.equipmentBaselineConfig().tierBaselines().size() + " equipment tier baselines, "
                        + config.milestones().size() + " milestones, "
                        + config.advancements().size() + " Ascendance transitions"
        ));

        var issues = HarvestProgressionSafety.evaluate(config);
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Harvest progression safety",
                issues.isEmpty()
                        ? EssenceCommandUtil.good("PASS")
                        : EssenceCommandUtil.warn("WARNING (" + issues.size() + ")")
        ));

        for (HarvestProgressionSafety.Issue issue : issues) {
            EssenceCommandUtil.send(
                    source,
                    EssenceCommandUtil.warn(
                            "    " + issue.fromTierId() + " -> " + issue.toTierId()
                                    + ": configured level " + issue.configuredHarvestLevel()
                                    + ", milestone requires at least " + issue.requiredHarvestLevel()
                    )
            );
        }
        return 1;
    }

    private static int reloadConfig(CommandSourceStack source) {
        EssenceConfigManager.reload();

        /*
         * Recalculate every connected player's gameplay modifiers and client
         * snapshots immediately against the newly loaded server configuration.
         */
        PlayerRuntimeLifecycleService.refreshAll(
                source.getServer()
        );

        EssenceCommandUtil.send(source, EssenceCommandUtil.good("Reloaded Essence Ascendance configuration."));
        return showConfig(source);
    }

    private static PlayerEssenceData playerData(CommandSourceStack source, ServerPlayer player) {
        return EssenceSavedData.get(source.getServer()).getPlayerData(player.getUUID());
    }
}
