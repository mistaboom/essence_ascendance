package com.mistaboom.essence_ascendance.command;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.attunement.AttunementGameplay;
import com.mistaboom.essence_ascendance.text.EssenceText;
import com.mistaboom.essence_ascendance.team.MembershipAuthority;
import com.mistaboom.essence_ascendance.team.MembershipRuntime;
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

import static com.mistaboom.essence_ascendance.command.EssenceMultiplayerCommandOutput.*;

final class EssenceAdminCommands {

    private EssenceAdminCommands() {
    }

    private static final String ADMIN = "/essence admin ";
    private static final String PLAYER = ADMIN + "player ";

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return group("admin", EssenceAdminCommands::showHelp)
                .requires(source -> source.hasPermission(EssenceCommandUtil.ADMIN_PERMISSION))
                .then(playerCommands())
                .then(group("item", EssenceAdminCommands::showItemTierHelp)
                        .then(group("tier", EssenceAdminCommands::showItemTierHelp)
                                .then(group("set", EssenceAdminCommands::showItemTierHelp)
                                        .then(target(Commands.argument("tier", StringArgumentType.string())
                                                        .suggests(EssenceCommandUtil::suggestItemTiers),
                                                c -> setHeldItemTier(c.getSource(), word(c, "tier")))))))
                .then(EssenceBalanceCommands.admin())
                .then(membershipCommands())
                .then(group("mappings", EssenceAdminCommands::showMappingsHelp)
                        .then(Commands.literal("reload").executes(c -> reloadMappings(c.getSource()))))
                .then(Commands.literal("config").executes(c -> showConfig(c.getSource())));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> membershipCommands() {
        var root = group("membership", EssenceAdminCommands::showMembershipHelp);
        root.then(Commands.literal("status").executes(c -> showMembershipStatus(c.getSource())));
        root.then(Commands.literal("prepare").then(Commands.argument("provider", StringArgumentType.word())
                .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(java.util.List.of("native", "ftb_teams", "openpac"), b))
                .executes(c -> membership(c.getSource(), () -> MembershipRuntime.prepare(
                        c.getSource().getServer(), StringArgumentType.getString(c, "provider"))))));
        root.then(Commands.literal("activate").then(Commands.argument("token", net.minecraft.commands.arguments.UuidArgument.uuid())
                .executes(c -> membership(c.getSource(), () -> MembershipRuntime.activate(
                        c.getSource().getServer(), net.minecraft.commands.arguments.UuidArgument.getUuid(c, "token"))))));
        root.then(Commands.literal("cancel").executes(c -> membership(c.getSource(), () ->
                MembershipRuntime.cancel(c.getSource().getServer()))));
        root.then(Commands.literal("reconcile").executes(c -> membership(c.getSource(), () ->
                MembershipRuntime.reconcile(c.getSource().getServer()))));
        return root;
    }
    private static int membership(CommandSourceStack source, java.util.function.Supplier<String> action) {
        EssenceCommandUtil.send(source, EssenceCommandUtil.title(text("membership.title")));
        try {
            EssenceCommandUtil.send(source, EssenceCommandUtil.good(action.get()));
            EssenceCommandUtil.send(source, EssenceCommandUtil.command(ADMIN + "membership status", text("membership.inspect_help")));
            return 1;
        }
        catch (IllegalArgumentException | IllegalStateException | ArithmeticException failure) {
            EssenceCommandUtil.fail(source, failure.getMessage()); return 0;
        }
    }

    private static int showMembershipHelp(CommandSourceStack source) {
        EssenceCommandUtil.send(source, EssenceCommandUtil.title(text("membership.title")));
        for (String action : new String[]{"status", "prepare <provider>", "activate <token>", "cancel", "reconcile"}) {
            EssenceCommandUtil.send(source, EssenceCommandUtil.command(ADMIN + "membership " + action,
                    text("membership.help." + action.split(" ")[0])));
        }
        return 1;
    }

    private static int showMembershipStatus(CommandSourceStack source) {
        showMembershipStatus(source, MembershipRuntime.diagnosticSnapshot(source.getServer()));
        return 1;
    }

    static void showMembershipStatus(CommandSourceStack source, MembershipRuntime.Diagnostics status) {
        EssenceCommandUtil.send(source, EssenceCommandUtil.title(text("membership.title")));
        line(source, "authority", status.established().displayName());
        line(source, "status", readiness(status.readiness()));
        line(source, "details", status.readiness().detail());
        if (status.notice() != null && !status.notice().isBlank())
            EssenceCommandUtil.send(source, EssenceCommandUtil.warn(status.notice()));
        EssenceCommandUtil.send(source, EssenceCommandUtil.section(text("membership.providers")));
        for (MembershipAuthority authority : MembershipAuthority.values()) {
            var provider = status.providers().get(authority);
            if (provider == null) continue;
            Component value = readiness(provider).copy().append(Component.literal(" (" + provider.version() + ")"))
                    .withStyle(style -> style.withHoverEvent(new net.minecraft.network.chat.HoverEvent(
                            net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT, Component.literal(provider.detail()))));
            EssenceCommandUtil.send(source, EssenceCommandUtil.line(authority.displayName(), value));
            if (provider.status() != com.mistaboom.essence_ascendance.balance.engine.ProviderReadiness.Status.AVAILABLE
                    && provider.status() != com.mistaboom.essence_ascendance.balance.engine.ProviderReadiness.Status.ABSENT)
                EssenceCommandUtil.send(source, EssenceCommandUtil.line(text("membership.provider_details", authority.displayName()),
                        EssenceCommandUtil.warn(provider.detail())));
        }
        if (!status.confirmedDeletions().isEmpty())
            line(source, "membership.deleted", EssenceCommandUtil.format(status.confirmedDeletions().size()));
        if (status.target() == null) return;
        EssenceCommandUtil.send(source, EssenceCommandUtil.section(text("membership.activation")));
        line(source, "membership.target", status.target().displayName());
        line(source, "membership.remaining", text("membership.seconds", (status.activationTicksRemaining() + 19) / 20));
        if (status.activationToken() == null) {
            EssenceCommandUtil.send(source, EssenceCommandUtil.warn(text("membership.awaiting")));
            EssenceCommandUtil.send(source, EssenceCommandUtil.command(ADMIN + "membership status", text("membership.inspect_help")));
        } else {
            line(source, "membership.teams", EssenceCommandUtil.format(status.affectedTeams()));
            line(source, "membership.players", EssenceCommandUtil.format(status.affectedPlayers()));
            EssenceCommandUtil.send(source, EssenceCommandUtil.warn(text("membership.stored_changes")));
            EssenceCommandUtil.send(source, EssenceCommandUtil.muted(text("membership.personal_unchanged")));
            EssenceCommandUtil.send(source, EssenceCommandUtil.warn(text("membership.binding_changes")));
            EssenceCommandUtil.send(source, EssenceCommandUtil.command(ADMIN + "membership activate " + status.activationToken(),
                    text("membership.activate_help")));
        }
        EssenceCommandUtil.send(source, EssenceCommandUtil.command(ADMIN + "membership cancel", text("membership.cancel_help")));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> playerCommands() {
        return group("player", EssenceAdminCommands::showPlayerHelp)
                .then(target(Commands.literal("reset"), c -> resetAll(c.getSource())))
                .then(group("tier", EssenceAdminCommands::showPlayerTierHelp)
                        .then(group("set", EssenceAdminCommands::showPlayerTierHelp)
                                .then(target(Commands.argument("tier", StringArgumentType.string()).suggests(EssenceCommandUtil::suggestTiers),
                                        c -> setTier(c.getSource(), word(c, "tier"))))))
                .then(AttunementCommands.admin())
                .then(essenceCommands())
                .then(bonusCommands())
                .then(skillCommands())
                .then(milestoneCommands())
                .then(group("crucible", EssenceAdminCommands::showCrucibleHelp)
                        .then(group("clear", EssenceAdminCommands::showCrucibleHelp)
                                .then(target(Commands.literal("all"), c -> clearAllCrucibleEssences(c.getSource())))
                                .then(target(Commands.argument("essence", StringArgumentType.string()).suggests(EssenceCommandUtil::suggestEssences),
                                        c -> clearCrucibleEssence(c.getSource(), word(c, "essence"))))))
                .then(group("guide", EssenceAdminCommands::showGuideHelp)
                        .then(target(Commands.literal("give"), c -> giveGuide(c.getSource()))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> essenceCommands() {
        return group("essence", EssenceAdminCommands::showEssenceHelp)
                .then(group("give", EssenceAdminCommands::showEssenceHelp)
                        .then(Commands.argument("essence", StringArgumentType.string()).suggests(EssenceCommandUtil::suggestEssences)
                                .then(target(Commands.argument("amount", LongArgumentType.longArg(1)),
                                        c -> giveEssence(c.getSource(), word(c, "essence"), LongArgumentType.getLong(c, "amount"))))))
                .then(group("set", EssenceAdminCommands::showEssenceHelp)
                        .then(Commands.argument("essence", StringArgumentType.string()).suggests(EssenceCommandUtil::suggestEssences)
                                .then(target(Commands.argument("amount", LongArgumentType.longArg(0)),
                                        c -> setEssence(c.getSource(), word(c, "essence"), LongArgumentType.getLong(c, "amount"))))))
                .then(group("clear", EssenceAdminCommands::showEssenceHelp)
                        .then(target(Commands.literal("all"), c -> clearAllEssences(c.getSource())))
                        .then(target(Commands.argument("essence", StringArgumentType.string()).suggests(EssenceCommandUtil::suggestEssences),
                                c -> setEssence(c.getSource(), word(c, "essence"), 0L))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> bonusCommands() {
        return group("bonuses", EssenceAdminCommands::showBonusHelp)
                .then(group("set", EssenceAdminCommands::showBonusHelp)
                        .then(Commands.argument("bonus", StringArgumentType.string()).suggests(EssenceCommandUtil::suggestStats)
                                .then(target(Commands.argument("amount", LongArgumentType.longArg(0)),
                                        c -> setStat(c.getSource(), word(c, "bonus"), LongArgumentType.getLong(c, "amount"))))))
                .then(group("max", EssenceAdminCommands::showBonusHelp)
                        .then(target(Commands.literal("all"), c -> maxAllStats(c.getSource())))
                        .then(target(Commands.argument("bonus", StringArgumentType.string()).suggests(EssenceCommandUtil::suggestStats),
                                c -> maxStat(c.getSource(), word(c, "bonus")))))
                .then(group("clear", EssenceAdminCommands::showBonusHelp)
                        .then(target(Commands.literal("all"), c -> clearAllStats(c.getSource())))
                        .then(target(Commands.argument("bonus", StringArgumentType.string()).suggests(EssenceCommandUtil::suggestStats),
                                c -> setStat(c.getSource(), word(c, "bonus"), 0L))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> skillCommands() {
        return group("skills", EssenceAdminCommands::showSkillsHelp)
                .then(group("grant", EssenceAdminCommands::showSkillsHelp)
                        .then(target(Commands.literal("all"), c -> grantAllSkills(c.getSource())))
                        .then(target(Commands.argument("skill", StringArgumentType.string()).suggests(EssenceCommandUtil::suggestSkills),
                                c -> grantSkill(c.getSource(), word(c, "skill")))))
                .then(group("clear", EssenceAdminCommands::showSkillsHelp)
                        .then(target(Commands.literal("all"), c -> clearAllSkills(c.getSource()))))
                .then(group("activate", EssenceAdminCommands::showSkillsHelp)
                        .then(target(Commands.argument("skill", StringArgumentType.string()).suggests(EssenceCommandUtil::suggestSkills),
                                c -> activateSkill(c.getSource(), word(c, "skill")))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> milestoneCommands() {
        var root = group("milestones", EssenceAdminCommands::showMilestoneHelp);
        for (String verb : java.util.List.of("grant", "revoke")) root.then(group(verb, EssenceAdminCommands::showMilestoneHelp)
                .then(target(Commands.argument("milestone", StringArgumentType.string()).suggests(EssenceCommandUtil::suggestDevelopmentMilestones),
                        c -> setMilestone(c.getSource(), word(c, "milestone"), verb.equals("grant")))));
        return root;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> group(String name,
            java.util.function.ToIntFunction<CommandSourceStack> help) {
        return Commands.literal(name).executes(c -> help.applyAsInt(c.getSource()))
                .then(Commands.literal("help").executes(c -> help.applyAsInt(c.getSource())));
    }

    private static <T extends com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, T>> T target(
            T node, com.mojang.brigadier.Command<CommandSourceStack> action) {
        return EssenceCommandUtil.withPlayerTarget(node, action);
    }

    private static String word(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, String name) {
        return StringArgumentType.getString(context, name);
    }

    static int showHelp(CommandSourceStack source) {
        return menu(source, "title", new String[][] {
                {ADMIN + "player", "player"}, {ADMIN + "item", "item"},
                {ADMIN + "balance", "balance"}, {ADMIN + "mappings", "mappings"}, {ADMIN + "config", "config"}});
    }

    private static int showPlayerHelp(CommandSourceStack source) {
        int result = menu(source, "player_title", new String[][] {
                {PLAYER + "tier", "tier"}, {PLAYER + "attunement", "attunement"},
                {PLAYER + "essence", "essence"}, {PLAYER + "bonuses", "stat"},
                {PLAYER + "skills", "skills"}, {PLAYER + "milestones", "milestone"},
                {PLAYER + "crucible", "crucible"}, {PLAYER + "guide", "guide"}, {PLAYER + "reset [player]", "reset"}});
        EssenceCommandUtil.send(source, EssenceCommandUtil.muted(EssenceText.command("admin.help.target")));
        return result;
    }

    private static int showEssenceHelp(CommandSourceStack source) {
        return menu(source, "essence_title", new String[][] {
                {PLAYER + "essence give <essence> <amount> [player]", "essence_give"},
                {PLAYER + "essence set <essence> <amount> [player]", "essence_set"},
                {PLAYER + "essence clear <essence|all> [player]", "essence_clear"}});
    }

    private static int showCrucibleHelp(CommandSourceStack source) {
        return menu(source, "crucible_title", new String[][] {
                {PLAYER + "crucible clear <essence|all> [player]", "crucible"}});
    }

    private static int showBonusHelp(CommandSourceStack source) {
        return menu(source, "bonuses_title", new String[][] {
                {PLAYER + "bonuses set <bonus> <amount> [player]", "bonus_set"},
                {PLAYER + "bonuses max <bonus|all> [player]", "bonus_max"},
                {PLAYER + "bonuses clear <bonus|all> [player]", "bonus_clear"}});
    }

    private static int showPlayerTierHelp(CommandSourceStack source) {
        return menu(source, "tier_title", new String[][] {
                {PLAYER + "tier set <tier> [player]", "tier_set"}});
    }

    private static int showItemTierHelp(CommandSourceStack source) {
        int result = menu(source, "item_title", new String[][] {
                {ADMIN + "item tier set <tier> [player]", "item_set"}});
        EssenceCommandUtil.send(source, EssenceCommandUtil.muted(EssenceText.command("admin.help.item_scope")));
        return result;
    }

    private static int showSkillsHelp(CommandSourceStack source) {
        int result = menu(source, "skills_title", new String[][] {
                {PLAYER + "skills grant <skill|all> [player]", "skills_grant"},
                {PLAYER + "skills activate <skill> [player]", "skills_activate"},
                {PLAYER + "skills clear all [player]", "skills_clear"}});
        EssenceCommandUtil.send(source, EssenceCommandUtil.muted(EssenceText.command("admin.help.skills_scope")));
        return result;
    }

    private static int showMilestoneHelp(CommandSourceStack source) {
        return menu(source, "milestones_title", new String[][] {
                {PLAYER + "milestones grant <milestone> [player]", "milestone_grant"},
                {PLAYER + "milestones revoke <milestone> [player]", "milestone_revoke"}});
    }

    private static int showGuideHelp(CommandSourceStack source) {
        return menu(source, "guide_title", new String[][] {{PLAYER + "guide give [player]", "guide_give"}});
    }

    private static int showMappingsHelp(CommandSourceStack source) {
        return menu(source, "mappings_title", new String[][] {
                {"/essence debug mappings status", "mappings_status"}, {"/essence debug mappings list", "mappings_list"},
                {ADMIN + "mappings reload", "mappings_reload"}});
    }

    private static int menu(CommandSourceStack source, String title, String[][] rows) {
        EssenceCommandUtil.send(source, EssenceCommandUtil.title(EssenceText.command("admin.help." + title)));
        for (String[] row : rows) EssenceCommandUtil.send(source,
                EssenceCommandUtil.command(row[0], EssenceText.command("admin.help." + row[1])));
        return 1;
    }

    private static int giveGuide(CommandSourceStack source) throws CommandSyntaxException {
        var result = com.mistaboom.essence_ascendance.progression.DormantGuidebookService.giveForAdmin(source.getPlayerOrException());
        if (result == com.mistaboom.essence_ascendance.progression.DormantGuidebookService.DeliveryResult.FAILED) {
            EssenceCommandUtil.fail(source, EssenceText.command("admin.guide.failed"));
            return 0;
        }
        EssenceCommandUtil.send(source, EssenceCommandUtil.good(EssenceText.command(
                result == com.mistaboom.essence_ascendance.progression.DormantGuidebookService.DeliveryResult.DROPPED
                        ? "admin.guide.dropped" : "admin.guide.given")));
        return 1;
    }

    private static int grantSkill(CommandSourceStack source, String name) throws CommandSyntaxException {
        var id = EssenceCommandUtil.resolveResourceId(name);
        var skill = SkillRegistry.get(id).orElse(null);
        if (skill == null) { EssenceCommandUtil.fail(source, EssenceText.command("error.unknown_skill", id)); return 0; }
        var player = source.getPlayerOrException();
        int count = EssenceSavedData.get(source.getServer()).grantAllSkillsForAdmin(player.getUUID(), java.util.List.of(skill));
        EssenceCommandUtil.send(source, EssenceCommandUtil.good(EssenceText.command("admin.skills.granted", count, id)));
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
                        + " Essence types. Bonus investments and Crucible storage were preserved."
        ));
        return 1;
    }

    private static int clearAllCrucibleEssences(
            CommandSourceStack source
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        EssenceSavedData savedData = EssenceSavedData.get(source.getServer());
        var types = com.mistaboom.essence_ascendance.essence.EssenceTypes.ORDERED;
        var view = savedData.reservoirs().read(player.getUUID());
        int essenceTypes = types.size();
        long clearedTypes = types.stream().filter(e -> view.units(e.id()).signum() > 0).count();
        savedData.clearReservoir(player.getUUID(), types.stream().map(EssenceDefinition::id).toList());

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
        var saved = EssenceSavedData.get(source.getServer());
        var removed = saved.reservoirs().read(player.getUUID()).whole(essence.id());
        saved.clearReservoir(player.getUUID(), java.util.List.of(essence.id()));

        EssenceCommandUtil.send(source, EssenceCommandUtil.good(
                "Cleared " + removed.toString() + " "
                        + essence.displayName()
                        + " from the shared Crucible reservoir."
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
                "Maxed " + count + " Bonuses to their current tier caps."
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
                "Cleared investments for " + count + " Bonuses. Available Essence balances were preserved."
        ));
        return 1;
    }

    private static int setTier(CommandSourceStack source, String tierName) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        AscendanceTierDefinition tier = EssenceCommandUtil.resolveTier(tierName);
        EssenceSavedData.get(source.getServer()).setTier(player.getUUID(), tier);
        AttunementGameplay.forget(player);
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
            EssenceCommandUtil.fail(source, "This player does not own " + id + ". Use /essence admin player skills grant all first.");
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



    private static int resetAll(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        EssenceSavedData saved = EssenceSavedData.get(source.getServer());
        saved.resetProgressionForAdmin(player.getUUID());
        com.mistaboom.essence_ascendance.balance.economy.FractionalLedgerSavedData.get(source.getServer())
                .clear(player.getUUID());
        AttunementGameplay.forget(player);
        PlayerRuntimeLifecycleService.refreshProgressionState(player);
        EssenceCommandUtil.send(source, EssenceCommandUtil.title(EssenceText.command("admin.reset.title")));
        EssenceCommandUtil.send(source, EssenceCommandUtil.good(EssenceText.command("admin.reset.done")));
        EssenceCommandUtil.send(source, EssenceCommandUtil.muted(EssenceText.command("admin.reset.scope")));
        return 1;
    }


    static int showMappings(
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
                "Saved baseline is reused; /essence admin balance rebuild recalculates and replaces it."));

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


    static int listMappings(
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
