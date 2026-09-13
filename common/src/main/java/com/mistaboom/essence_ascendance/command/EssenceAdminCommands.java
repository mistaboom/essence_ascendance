package com.mistaboom.essence_ascendance.command;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.balance.BalanceProfileDefinition;
import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.lifecycle.PlayerRuntimeLifecycleService;
import com.mistaboom.essence_ascendance.mapping.ItemEssenceMappingDefinition;
import com.mistaboom.essence_ascendance.mapping.ItemEssenceMappingManager;
import com.mistaboom.essence_ascendance.mapping.ItemEssenceMappingRegistry;
import com.mistaboom.essence_ascendance.mapping.ItemEssenceMappings;
import com.mistaboom.essence_ascendance.network.ItemEssenceTooltipSyncService;
import com.mistaboom.essence_ascendance.config.EssenceServerConfig;
import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import com.mistaboom.essence_ascendance.equipment.EquipmentTier;
import com.mistaboom.essence_ascendance.equipment.EquipmentTierData;
import com.mistaboom.essence_ascendance.equipment.SoulboundEquipmentData;
import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import com.mistaboom.essence_ascendance.essence.EssenceRegistry;
import com.mistaboom.essence_ascendance.infuser.EquipmentInfusionData;
import com.mistaboom.essence_ascendance.infuser.FocusInfusionData;
import com.mistaboom.essence_ascendance.pylon.EssenceFocusData;
import com.mistaboom.essence_ascendance.pylon.EssenceFocusTier;
import com.mistaboom.essence_ascendance.progression.HarvestProgressionSafety;
import com.mistaboom.essence_ascendance.progression.MilestoneDefinition;
import com.mistaboom.essence_ascendance.progression.MilestoneProviders;
import com.mistaboom.essence_ascendance.progression.PermanentMilestoneService;
import com.mistaboom.essence_ascendance.progression.StatInvestmentLimit;
import com.mistaboom.essence_ascendance.progression.TierInvestmentPolicy;
import com.mistaboom.essence_ascendance.stat.EssenceStatRegistry;
import com.mistaboom.essence_ascendance.stat.StatDefinition;
import com.mistaboom.essence_ascendance.skill.SkillDefinition;
import com.mistaboom.essence_ascendance.skill.SkillRegistry;
import com.mistaboom.essence_ascendance.skill.SkillStateEvaluator;
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
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

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
                        Commands.literal("essences")
                                .executes(context -> showEssencesHelp(context.getSource()))
                                .then(
                                        Commands.literal("clear")
                                                .executes(context -> clearAllEssences(context.getSource()))
                                )
                )
                .then(
                        Commands.literal("crucible")
                                .executes(context -> showCrucibleHelp(context.getSource()))
                                .then(
                                        Commands.literal("clear")
                                                .executes(context -> clearAllCrucibleEssences(context.getSource()))
                                                .then(
                                                        Commands.argument("essence", StringArgumentType.word())
                                                                .suggests(EssenceCommandUtil::suggestEssences)
                                                                .executes(context -> clearCrucibleEssence(
                                                                        context.getSource(),
                                                                        StringArgumentType.getString(context, "essence")
                                                                ))
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
                                .executes(context -> showPlayerTierHelp(context.getSource()))
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
                        Commands.literal("itemtier")
                                .executes(context -> showItemTierHelp(context.getSource()))
                                .then(
                                        Commands.literal("set")
                                                .then(
                                                        Commands.argument("tier", StringArgumentType.word())
                                                                .suggests(EssenceCommandUtil::suggestItemTiers)
                                                                .executes(context -> setHeldItemTier(
                                                                        context.getSource(),
                                                                        StringArgumentType.getString(context, "tier")
                                                                ))
                                                )
                                )
                )
                .then(
                        Commands.literal("milestone")
                                .executes(context -> showMilestoneHelp(context.getSource()))
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
                                .then(
                                        Commands.literal("grant")
                                                .then(
                                                        Commands.argument("milestone", StringArgumentType.string())
                                                                .suggests(EssenceCommandUtil::suggestDevelopmentMilestones)
                                                                .executes(context -> setMilestone(
                                                                        context.getSource(),
                                                                        StringArgumentType.getString(context, "milestone"),
                                                                        true
                                                                ))
                                                )
                                )
                                .then(
                                        Commands.literal("revoke")
                                                .then(
                                                        Commands.argument("milestone", StringArgumentType.string())
                                                                .suggests(EssenceCommandUtil::suggestDevelopmentMilestones)
                                                                .executes(context -> setMilestone(
                                                                        context.getSource(),
                                                                        StringArgumentType.getString(context, "milestone"),
                                                                        false
                                                                ))
                                                )
                                )
                )
                .then(
                        Commands.literal("skills")
                                .executes(context -> showSkillsHelp(context.getSource()))
                                .then(Commands.literal("grant_all")
                                        .executes(context -> grantAllSkills(context.getSource())))
                                .then(Commands.literal("clear")
                                        .executes(context -> clearAllSkills(context.getSource())))
                                .then(Commands.literal("activate")
                                        .then(Commands.argument("skill", StringArgumentType.string())
                                                .suggests(EssenceCommandUtil::suggestSkills)
                                                .executes(context -> activateSkill(context.getSource(),
                                                        StringArgumentType.getString(context, "skill")))))
                )
                .then(
                        Commands.literal("attunement")
                                .executes(context -> showAttunementHelp(context.getSource()))
                                .then(
                                        Commands.literal("grant")
                                                .then(
                                                        Commands.argument("attunement", StringArgumentType.string())
                                                                .suggests(EssenceCommandUtil::suggestAttunements)
                                                                .executes(context -> setAttunement(
                                                                        context.getSource(),
                                                                        StringArgumentType.getString(context, "attunement"),
                                                                        true
                                                                ))
                                                )
                                )
                                .then(
                                        Commands.literal("revoke")
                                                .then(
                                                        Commands.argument("attunement", StringArgumentType.string())
                                                                .suggests(EssenceCommandUtil::suggestAttunements)
                                                                .executes(context -> setAttunement(
                                                                        context.getSource(),
                                                                        StringArgumentType.getString(context, "attunement"),
                                                                        false
                                                                ))
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
                .then(Commands.literal("config")
                        .executes(context -> showConfig(context.getSource())))
                .then(
                        Commands.literal("reset")
                                .executes(context -> resetAll(context.getSource()))
                );
    }

    static int showHelp(CommandSourceStack source) {
        EssenceCommandUtil.send(source, EssenceCommandUtil.title("Essence Admin Commands"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence admin essence give <essence> <amount>", "add available Essence"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence admin essence set <essence> <amount>", "set an available Essence balance"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence admin essences clear", "clear all available Essence balances"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence admin crucible clear", "clear stored Crucible Essence"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence admin crucible clear <essence>", "clear one stored Crucible Essence"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence admin stat set <stat> <amount>", "set stored investment directly"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence admin stat max <stat>", "set one stat exactly to its current tier cap"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence admin stat clear <stat>", "clear one stat investment"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence admin stats max", "max every stat to the current tier caps"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence admin stats clear", "clear investments without clearing balances"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence admin skills grant_all", "grant every current catalog skill at zero cost"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence admin skills activate <skill>", "select the skill's required combat/loadout branch"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence admin skills clear", "remove all skill receipts and selections"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence admin tier set <tier>", "force your PLAYER Ascendance tier"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence admin itemtier set <tier>", "set the held Ascendance equipment/Essence Focus tier"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence admin milestone set <milestone> <true|false>", "set an internal progression milestone"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence admin milestone grant|revoke <milestone_id>", "change a configured INTERNAL or catalog skill milestone"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence admin attunement grant|revoke <attunement_id>", "change a known Player Attunement ID for development"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence admin mappings", "show generated dissolution mapping status"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence admin mappings reload", "load saved procedural defaults and explicit overrides; calculate only when the cache is absent"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence admin mappings list", "list the active mapping IDs/selectors"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence admin config", "show the loaded server configuration"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence admin reset", "clear all balances and investments"));
        return 1;
    }

    private static int showEssenceHelp(CommandSourceStack source) {
        EssenceCommandUtil.send(source, EssenceCommandUtil.section("Admin - Essence balances"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence admin essence give <essence> <amount>", "add to a balance"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence admin essence set <essence> <amount>", "replace a balance"));
        return 1;
    }

    private static int showEssencesHelp(CommandSourceStack source) {
        EssenceCommandUtil.send(source, EssenceCommandUtil.section("Admin - All Essence balances"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence admin essences clear", "clear all available Essence balances while preserving investments and Crucible storage"));
        return 1;
    }

    private static int showCrucibleHelp(CommandSourceStack source) {
        EssenceCommandUtil.send(source, EssenceCommandUtil.section("Admin - Crucible reservoir"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command(
                "/essence admin crucible clear",
                "clear all Essence from your shared Crucible reservoir"
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command(
                "/essence admin crucible clear <essence>",
                "clear one Essence from your shared Crucible reservoir"
        ));
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

    private static int showPlayerTierHelp(CommandSourceStack source) {
        EssenceCommandUtil.send(source, EssenceCommandUtil.section("Admin - Player Ascendance Tier"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command(
                "/essence admin tier set <dormant|awakened|resonant|ascendant|transcendent>",
                "force YOUR player progression tier; this does not change held item tiers"
        ));
        return 1;
    }

    private static int showItemTierHelp(CommandSourceStack source) {
        EssenceCommandUtil.send(source, EssenceCommandUtil.section("Admin - Held Item Tier"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command(
                "/essence admin itemtier set <latent|dormant|awakened|resonant|ascendant|transcendent>",
                "set the completed tier of held Ascendance equipment or an Essence Focus"
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.muted(
                "Changing an item tier clears partial infusion progress on that item."
        ));
        return 1;
    }

    private static int showSkillsHelp(CommandSourceStack source) {
        EssenceCommandUtil.send(source, EssenceCommandUtil.section("Admin - Skill testing"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence admin skills grant_all", "grant the current 90-skill catalog for free"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence admin skills activate <skill>", "apply every required selectable branch for an owned skill"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence admin skills clear", "clear all receipts and loadout selections"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.muted("Live tier, milestone, stat, discovery, and attunement requirements still decide effectiveness."));
        return 1;
    }

    private static int showMilestoneHelp(CommandSourceStack source) {
        EssenceCommandUtil.send(source, EssenceCommandUtil.section("Admin - Internal progression milestones"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command(
                "/essence admin milestone grant <milestone_id>",
                "grant a configured INTERNAL or catalog skill milestone"
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command(
                "/essence admin milestone revoke <milestone_id>",
                "revoke a configured INTERNAL or catalog skill milestone"
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.muted(
                "The existing 'set <milestone_id> <true|false>' form remains available."
        ));
        return 1;
    }

    private static int showAttunementHelp(CommandSourceStack source) {
        EssenceCommandUtil.send(source, EssenceCommandUtil.section("Admin - Player Attunements"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command(
                "/essence admin attunement grant <attunement_id>",
                "grant a known stable Attunement ID to yourself"
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command(
                "/essence admin attunement revoke <attunement_id>",
                "revoke a known stable Attunement ID from yourself"
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.muted(
                "Development hook: unknown IDs are rejected to prevent persistent typo entries."
        ));
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

    private static int clearAllEssences(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        EssenceSavedData savedData = EssenceSavedData.get(source.getServer());
        int count = 0;

        for (EssenceDefinition essence : EssenceRegistry.values()) {
            savedData.setEssence(player.getUUID(), essence, 0L);
            count++;
        }

        EssenceCommandUtil.send(source, EssenceCommandUtil.good(
                "Cleared available balances for " + count
                        + " Essence types. Stat investments and Crucible storage were preserved."
        ));
        return 1;
    }

    private static int clearAllCrucibleEssences(
            CommandSourceStack source
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        EssenceSavedData savedData = EssenceSavedData.get(source.getServer());
        int essenceTypes = 0;
        int clearedTypes = 0;

        for (EssenceDefinition essence : EssenceRegistry.values()) {
            essenceTypes++;
            long removed = savedData.removeCrucibleStored(
                    player.getUUID(),
                    essence,
                    Long.MAX_VALUE
            );
            boolean clearedCarry = com.mistaboom.essence_ascendance.balance.economy.FractionalLedgerSavedData
                    .get(source.getServer()).clear(player.getUUID(), "dissolution/" + essence.id());
            if (removed > 0L || clearedCarry) {
                clearedTypes++;
            }
        }

        EssenceCommandUtil.send(source, EssenceCommandUtil.good(
                "Cleared stored Crucible Essence from " + clearedTypes
                        + " of " + essenceTypes
                        + " Essence types."
        ));
        return 1;
    }

    private static int clearCrucibleEssence(
            CommandSourceStack source,
            String essenceName
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        EssenceDefinition essence = EssenceCommandUtil.resolveEssence(essenceName);
        long removed = EssenceSavedData
                .get(source.getServer())
                .removeCrucibleStored(
                        player.getUUID(),
                        essence,
                        Long.MAX_VALUE
                );

        com.mistaboom.essence_ascendance.balance.economy.FractionalLedgerSavedData
                .get(source.getServer()).clear(player.getUUID(), "dissolution/" + essence.id());

        EssenceCommandUtil.send(source, EssenceCommandUtil.good(
                "Cleared " + EssenceCommandUtil.format(removed) + " "
                        + essence.displayName()
                        + " from your shared Crucible reservoir."
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
        PlayerRuntimeLifecycleService.refreshProgressionState(player);

        EssenceCommandUtil.send(source, EssenceCommandUtil.good(
                "Player Ascendance tier set to " + tier.displayName() + "."
        ));
        return 1;
    }

    private static int setHeldItemTier(
            CommandSourceStack source,
            String tierName
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ItemStack held = player.getMainHandItem();
        EquipmentTier requested = EssenceCommandUtil.resolveItemTier(tierName);

        if (held == null || held.isEmpty()) {
            EssenceCommandUtil.fail(source, "Hold Ascendance equipment or an Essence Focus in your main hand.");
            return 0;
        }

        if (EquipmentTierData.isAscendanceEquipment(held)) {
            EquipmentTierData.setTier(held, requested);
            EquipmentInfusionData.clear(held);

            /*
             * Admin tier mutation is a testing shortcut around the real Infuser
             * progression. A non-Latent tier therefore represents an artifact
             * that must already have accepted Essence at some point. Bind an
             * otherwise-unbound test item to the command player, but never
             * overwrite an existing soulbinding. Demoting to Latent also leaves
             * an established binding intact because soulbinding is permanent.
             */
            if (requested != EquipmentTier.LATENT
                    && !SoulboundEquipmentData.isSoulbound(held)) {
                SoulboundEquipmentData.bind(
                        held,
                        player.getUUID(),
                        player.getGameProfile().getName()
                );
            }

            PlayerRuntimeLifecycleService.refreshProgressionState(player);
            EssenceCommandUtil.send(source, EssenceCommandUtil.good(
                    held.getHoverName().getString() + " item tier set to " + requested.displayName() + "."
            ));
            return 1;
        }

        if (EssenceFocusData.isFocusItem(held)) {
            FocusInfusionData.clear(held);
            if (requested == EquipmentTier.LATENT) {
                EssenceFocusData.setLatent(held);
            } else {
                EssenceFocusTier focusTier = focusTierFor(requested);
                EssenceFocusData.setTier(held, focusTier);
            }
            EssenceCommandUtil.send(source, EssenceCommandUtil.good(
                    "Essence Focus item tier set to " + requested.displayName() + "."
            ));
            return 1;
        }

        EssenceCommandUtil.fail(
                source,
                "Held item has no Ascendance item tier. Hold Ascendance equipment or an Essence Focus."
        );
        return 0;
    }

    private static EssenceFocusTier focusTierFor(EquipmentTier tier) {
        for (EssenceFocusTier focusTier : EssenceFocusTier.values()) {
            if (focusTier.serializedName().equals(tier.serializedName())) {
                return focusTier;
            }
        }
        throw new IllegalArgumentException("No Essence Focus tier for " + tier.serializedName());
    }

    private static int setMilestone(
            CommandSourceStack source,
            String milestoneName,
            boolean complete
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ResourceLocation requestedId = EssenceCommandUtil.resolveResourceId(milestoneName);
        MilestoneDefinition milestone = EssenceConfigManager.get()
                .getMilestone(requestedId)
                .orElse(null);
        boolean skillMilestone = EssenceCommandUtil
                .knownSkillMilestoneIds()
                .contains(requestedId);
        ResourceLocation storedId;
        String label;

        if (skillMilestone) {
            /*
             * This is an explicit development override of the permanent
             * definition ID. Normal gameplay still resolves the configured
             * provider and target through MilestoneService.
             */
            storedId = requestedId;
            label = milestone == null
                    ? requestedId.toString()
                    : milestone.displayName() + " [" + milestone.id() + "]";
        } else if (milestone != null) {
            if (!milestone.providerId().equals(MilestoneProviders.INTERNAL)) {
                EssenceCommandUtil.fail(
                        source,
                        "Only INTERNAL milestones can be changed directly. "
                                + milestone.id() + " uses " + milestone.providerId() + "."
                );
                return 0;
            }

            storedId = ResourceLocation.tryParse(milestone.target());
            if (storedId == null) {
                EssenceCommandUtil.fail(source, "Milestone has invalid internal target: " + milestone.target());
                return 0;
            }
            label = milestone.displayName() + " [" + milestone.id() + "]";
        } else {
            EssenceCommandUtil.fail(
                    source,
                    "Unknown INTERNAL or skill milestone ID: " + requestedId
            );
            return 0;
        }

        EssenceSavedData savedData = EssenceSavedData.get(player.server);
        boolean changed = complete
                ? savedData.completeMilestone(player.getUUID(), storedId)
                : savedData.revokeMilestone(player.getUUID(), storedId);

        PlayerRuntimeLifecycleService.refreshProgressionState(player);

        boolean effectiveComplete = skillMilestone
                ? PermanentMilestoneService.resolveAll(
                        player,
                        java.util.List.of(requestedId)
                ).get(0).complete()
                : savedData.hasCompletedMilestone(player.getUUID(), storedId);

        if (effectiveComplete != complete) {
            EssenceCommandUtil.fail(
                    source,
                    "Milestone " + label
                            + " remains COMPLETE because its configured provider is already complete. "
                            + "Revoke or reset the provider's progress before clearing its permanent capture."
            );
            return 0;
        }

        Component state = effectiveComplete
                ? EssenceCommandUtil.good("COMPLETE")
                : EssenceCommandUtil.warn("INCOMPLETE");
        EssenceCommandUtil.send(
                source,
                Component.literal("Milestone ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(label).withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(" is now ").withStyle(ChatFormatting.GRAY))
                        .append(state)
                        .append(Component.literal(changed ? "." : " (state was already set).").withStyle(ChatFormatting.GRAY))
        );
        return 1;
    }

    private static int grantAllSkills(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        int granted = EssenceSavedData.get(source.getServer()).grantAllSkillsForAdmin(player.getUUID(), SkillRegistry.values());
        PlayerRuntimeLifecycleService.refreshProgressionState(player);
        EssenceCommandUtil.send(source, EssenceCommandUtil.good("Granted " + granted + " new current-catalog skill receipts at zero cost."));
        return 1;
    }

    private static int clearAllSkills(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        int removed = EssenceSavedData.get(source.getServer()).clearAllSkillsForAdmin(player.getUUID());
        PlayerRuntimeLifecycleService.refreshProgressionState(player);
        EssenceCommandUtil.send(source, EssenceCommandUtil.good("Cleared " + removed + " skill receipts and all loadout selections."));
        return 1;
    }

    private static int activateSkill(CommandSourceStack source, String skillName) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ResourceLocation id = EssenceCommandUtil.resolveResourceId(skillName);
        SkillDefinition skill = SkillRegistry.get(id).orElse(null);
        if (skill == null) {
            EssenceCommandUtil.fail(source, "Unknown current catalog skill: " + id);
            return 0;
        }
        EssenceSavedData savedData = EssenceSavedData.get(source.getServer());
        PlayerEssenceData data = savedData.getPlayerData(player.getUUID());
        if (!data.ownsSkill(id)) {
            EssenceCommandUtil.fail(source, "You do not own " + id + ". Use /essence admin skills grant_all first.");
            return 0;
        }
        var plan = SkillStateEvaluator.activationPlan(id, new LinkedHashSet<>(data.getOwnedSkills().keySet()),
                new LinkedHashMap<>(data.getLoadoutSelections())).orElse(null);
        if (plan == null) {
            EssenceCommandUtil.fail(source, "No valid activation plan exists for " + id + ". Check its owned prerequisites.");
            return 0;
        }
        for (ResourceLocation suppression : plan.automaticSuppressionsToClear()) savedData.clearLoadoutSelection(player.getUUID(), suppression);
        for (var assignment : plan.selectableAssignments().entrySet())
            savedData.setLoadoutSelection(player.getUUID(), assignment.getKey(), assignment.getValue());
        PlayerRuntimeLifecycleService.refreshProgressionState(player);
        EssenceCommandUtil.send(source, EssenceCommandUtil.good("Activated the loadout branch required by " + id + "."));
        return 1;
    }

    private static int setAttunement(
            CommandSourceStack source,
            String attunementName,
            boolean granted
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ResourceLocation attunementId = EssenceCommandUtil.resolveResourceId(attunementName);
        if (!EssenceCommandUtil.knownAttunementIds().contains(attunementId)) {
            EssenceCommandUtil.fail(source, "Unknown Player Attunement ID: " + attunementId);
            return 0;
        }
        EssenceSavedData savedData = EssenceSavedData.get(player.server);
        boolean changed = granted
                ? savedData.grantAttunement(player.getUUID(), attunementId)
                : savedData.revokeAttunement(player.getUUID(), attunementId);

        /*
         * Attunements may immediately change skill eligibility/effectiveness.
         * Push the complete authoritative snapshot even when the requested
         * state was already present so the development client is reconciled.
         */
        PlayerRuntimeLifecycleService.refreshProgressionState(player);

        Component state = granted
                ? EssenceCommandUtil.good("GRANTED")
                : EssenceCommandUtil.warn("REVOKED");
        EssenceCommandUtil.send(
                source,
                Component.literal("Player Attunement ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(attunementId.toString()).withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(" is now ").withStyle(ChatFormatting.GRAY))
                        .append(state)
                        .append(Component.literal(changed ? "." : " (state was already set).").withStyle(ChatFormatting.GRAY))
        );
        return 1;
    }

    private static int resetAll(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        EssenceSavedData.get(source.getServer()).clearAll(player.getUUID());
        com.mistaboom.essence_ascendance.balance.economy.FractionalLedgerSavedData.get(source.getServer())
                .clear(player.getUUID());
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
                        "Item → Essence Mappings"
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

        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Generated cache", ItemEssenceMappingManager.generatedCachePath().toAbsolutePath().toString()));
        EssenceCommandUtil.send(source, EssenceCommandUtil.muted(
                "Saved baseline is reused; /essence debug balance rebuild recalculates and replaces it."));

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
                        "Generated defaults",
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

            ItemEssenceTooltipSyncService.syncAll(
                    source.getServer()
            );

            EssenceCommandUtil.send(
                    source,
                    EssenceCommandUtil.good(
                            "Installed item → Essence mapping generation " + report.generation()
                                    + "; tooltip synchronization was requested."
                    )
            );

        } else {
            EssenceCommandUtil.send(
                    source,
                    EssenceCommandUtil.bad(
                            "Item mapping reload was rejected; generation " + report.generation()
                                    + " is unchanged. No replacement was installed."
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
                outputs = definition.outputs().isEmpty()
                        ? "BLOCK"
                        : "NO VISIBLE OUTPUTS";
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
                "Crucible pylons",
                "radius " + EssenceCommandUtil.formatDecimal(config.pylonRadius())
                        + ", max active " + config.maxActivePylons()
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

    private static PlayerEssenceData playerData(CommandSourceStack source, ServerPlayer player) {
        return EssenceSavedData.get(source.getServer()).getPlayerData(player.getUUID());
    }
}
