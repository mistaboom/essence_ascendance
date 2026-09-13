package com.mistaboom.essence_ascendance.command;

import com.mistaboom.essence_ascendance.attunement.*;
import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.essence.EssenceRegistry;
import com.mistaboom.essence_ascendance.lifecycle.PlayerRuntimeLifecycleService;
import com.mistaboom.essence_ascendance.progression.AscendanceEngine;
import com.mistaboom.essence_ascendance.text.EssenceText;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** Player summaries, explicit diagnostics and permission-gated testing share one presentation. */
final class AttunementCommands {
    private static final int HISTORY_PAGE_SIZE = 4;
    private AttunementCommands() { }

    static LiteralArgumentBuilder<CommandSourceStack> player() {
        return Commands.literal("attunement").executes(c -> show(c.getSource(), false))
                .then(Commands.literal("help").executes(c -> playerHelp(c.getSource())))
                .then(Commands.argument("category", StringArgumentType.string()).suggests(EssenceCommandUtil::suggestEssences)
                        .executes(c -> showCategory(c.getSource(), c.getSource().getPlayerOrException(), category(c), false)));
    }

    static LiteralArgumentBuilder<CommandSourceStack> debug() {
        return Commands.literal("attunement").executes(c -> debugHelp(c.getSource()))
                .then(Commands.literal("help").executes(c -> debugHelp(c.getSource())))
                .then(viewTarget(Commands.literal("summary"),
                        c -> overview(c.getSource(), EssenceCommandUtil.targetPlayer(c))))
                .then(Commands.literal("show").executes(c -> debugHelp(c.getSource()))
                        .then(viewTarget(Commands.argument("category", StringArgumentType.string()).suggests(EssenceCommandUtil::suggestEssences),
                                c -> showCategory(c.getSource(), EssenceCommandUtil.targetPlayer(c), category(c), true))))
                .then(Commands.literal("history").executes(c -> debugHelp(c.getSource()))
                        .then(Commands.argument("category", StringArgumentType.string()).suggests(EssenceCommandUtil::suggestEssences)
                                .executes(c -> history(c.getSource(), c.getSource().getPlayerOrException(), category(c), 1))
                                .then(viewTarget(Commands.argument("page", IntegerArgumentType.integer(1)),
                                        c -> history(c.getSource(), EssenceCommandUtil.targetPlayer(c), category(c), IntegerArgumentType.getInteger(c, "page"))))))
                .then(Commands.literal("activities").executes(c -> activities(c.getSource(), null))
                        .then(Commands.argument("category", StringArgumentType.string()).suggests(EssenceCommandUtil::suggestEssences)
                                .executes(c -> activities(c.getSource(), category(c)))))
                .then(Commands.literal("reachability").executes(c -> reachability(c.getSource(), null))
                        .then(Commands.argument("tier", StringArgumentType.string()).suggests(EssenceCommandUtil::suggestTiers)
                                .executes(c -> reachability(c.getSource(), EssenceCommandUtil.resolveTier(StringArgumentType.getString(c, "tier")).id().toString()))));
    }

    /** Read-only views share target syntax without mutation refresh or source impersonation. */
    private static <T extends com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, T>> T viewTarget(
            T node, com.mojang.brigadier.Command<CommandSourceStack> view) {
        return node.executes(view).then(Commands.argument("target", EntityArgument.player()).executes(view));
    }
    static LiteralArgumentBuilder<CommandSourceStack> admin() {
        return Commands.literal("attunement")
                .requires(s -> s.hasPermission(EssenceCommandUtil.ADMIN_PERMISSION))
                .executes(c -> adminHelp(c.getSource()))
                .then(Commands.literal("help").executes(c -> adminHelp(c.getSource())))
                .then(adminAction("fill", 100)).then(adminAction("reset", 0))
                .then(Commands.literal("set").executes(c -> adminHelp(c.getSource()))
                        .then(Commands.argument("category", StringArgumentType.string()).suggests((c,b) -> suggestCategories(b))
                                .then(Commands.argument("percent", IntegerArgumentType.integer(0,100))
                                        .executes(c -> mutate(c, StringArgumentType.getString(c,"category"), IntegerArgumentType.getInteger(c,"percent")))
                                        .then(Commands.argument("target", EntityArgument.player())
                                                .executes(c -> mutate(c, StringArgumentType.getString(c,"category"), IntegerArgumentType.getInteger(c,"percent")))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> adminAction(String name, int percent) {
        return Commands.literal(name).executes(c -> adminHelp(c.getSource()))
                .then(Commands.argument("category", StringArgumentType.string()).suggests((c,b) -> suggestCategories(b))
                        .executes(c -> mutate(c, StringArgumentType.getString(c,"category"), percent))
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(c -> mutate(c, StringArgumentType.getString(c,"category"), percent))));
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestCategories(
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        if ("all".startsWith(builder.getRemainingLowerCase())) builder.suggest("all");
        return EssenceCommandUtil.suggestRegistryIds(builder, EssenceRegistry.values().stream().map(e -> e.id()).toList());
    }

    private static String category(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        return EssenceCommandUtil.resolveEssence(StringArgumentType.getString(c, "category")).id().toString();
    }

    private static int mutate(CommandContext<CommandSourceStack> c, String selected, int percent) throws CommandSyntaxException {
        var source = c.getSource();
        if (!ready(source)) return 0;
        var target = EssenceCommandUtil.targetPlayer(c);
        var saved = EssenceSavedData.get(source.getServer());
        var data = saved.getPlayerData(target.getUUID());
        var profile = EssenceConfigManager.serverRuntime().attunement();
        var chapter = profile.chapter(data.getTierId().toString());
        if (chapter == null) {
            EssenceCommandUtil.fail(source, text("no_chapter"));
            return 0;
        }
        String id = selected.equalsIgnoreCase("all") ? null : EssenceCommandUtil.resolveEssence(selected).id().toString();
        var changed = AttunementAdminService.setPercent(data, profile, id, percent);
        saved.setDirty();
        AttunementGameplay.forget(target);
        boolean automatic = AttunementService.shouldPromoteAutomatically(data, chapter);
        boolean promoted = automatic && AscendanceEngine.ascend(target).success();
        if (!promoted) PlayerRuntimeLifecycleService.refreshProgressionState(target);
        EssenceCommandUtil.send(source, EssenceCommandUtil.title(text("admin_title")));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(EssenceText.command("label.player"), target.getDisplayName()));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(text("chapter"),
                EssenceText.ascendanceTier(EssenceCommandUtil.resolveTier(chapter.fromTierId())).append(" → ")
                        .append(EssenceText.ascendanceTier(EssenceCommandUtil.resolveTier(chapter.toTierId())))));
        for (String value : changed) EssenceCommandUtil.send(source, EssenceCommandUtil.line(name(value), EssenceCommandUtil.good(percent + "%")));
        EssenceCommandUtil.send(source, EssenceCommandUtil.muted(text("override_note")));
        if (promoted) EssenceCommandUtil.send(source, EssenceCommandUtil.good(text("automatic", EssenceText.ascendanceTier(data.getTier()))));
        else if (source.getEntity() == target)
            EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence ascend", text("ascend_hint")));
        else EssenceCommandUtil.send(source, EssenceCommandUtil.muted(text("target_ascend_hint", target.getDisplayName())));
        return changed.size();
    }

    static int show(CommandSourceStack source, boolean debug) throws CommandSyntaxException {
        return debug ? debugHelp(source) : overview(source, source.getPlayerOrException());
    }

    private static int overview(CommandSourceStack source, ServerPlayer player) {
        if (!ready(source)) return 0;
        var data = EssenceSavedData.get(player.server).getPlayerData(player.getUUID());
        var snapshot = AttunementService.snapshot(data);
        EssenceCommandUtil.send(source, EssenceCommandUtil.title(text("title")));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(EssenceText.command("label.player"), player.getDisplayName()));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(EssenceText.command("label.tier"), EssenceText.ascendanceTier(data.getTier())));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(text("seals"), snapshot.completedCategories() + " / " + (snapshot.maximumTier() ? snapshot.categories().size() : snapshot.requiredCategories())));
        for (var row : snapshot.categories()) {
            String category = EssenceCommandUtil.commandId(ResourceLocation.parse(row.categoryId()));
            String command = source.getEntity() == player ? "/essence attunement " + category
                    : "/essence debug player attunement show " + category + " " + player.getGameProfile().getName();
            EssenceCommandUtil.send(source, EssenceCommandUtil.suggest(EssenceCommandUtil.line(name(row.categoryId()),
                    row.completed() ? EssenceCommandUtil.good(row.percent() + "%") : EssenceCommandUtil.value(row.percent() + "%")), command));
        }
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(text("state"), state(snapshot)));
        EssenceCommandUtil.send(source, EssenceCommandUtil.muted(text("select_category")));
        return 1;
    }

    private static int showCategory(CommandSourceStack source, ServerPlayer player, String id, boolean debug) {
        if (!ready(source)) return 0;
        var snapshot = AttunementService.snapshot(EssenceSavedData.get(player.server).getPlayerData(player.getUUID()));
        var row = snapshot.categories().stream().filter(r -> r.categoryId().equals(id)).findFirst().orElse(null);
        if (row == null) { EssenceCommandUtil.fail(source, text("no_chapter")); return 0; }
        EssenceCommandUtil.send(source, EssenceCommandUtil.title(text("category_title", name(id))));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(EssenceText.command("label.player"), player.getDisplayName()));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(text("progress"), row.percent() + "%"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(text("investment"), text("multiplier", number(row.investmentMultiplier()))));
        if (debug) EssenceCommandUtil.send(source, EssenceCommandUtil.line(text("target"), EssenceCommandUtil.format(row.target())));
        EssenceCommandUtil.send(source, EssenceCommandUtil.section(text("methods")));
        for (var method : row.methods()) EssenceCommandUtil.send(source, EssenceCommandUtil.line(Component.translatable(method.labelKey()), method.percent() + "%"));
        if (!debug) {
            EssenceCommandUtil.send(source, EssenceCommandUtil.section(text("recent")));
            if (row.recent().isEmpty()) EssenceCommandUtil.send(source, EssenceCommandUtil.muted(text("no_recent")));
            for (var result : row.recent().stream().limit(3).toList()) summaryAction(source, result);
        }
        if (source.hasPermission(EssenceCommandUtil.ADMIN_PERMISSION))
            EssenceCommandUtil.send(source, EssenceCommandUtil.command(historyCommand(source, player, id, 1), text("history_help")));
        return 1;
    }

    private static int history(CommandSourceStack source, ServerPlayer player, String id, int page) {
        if (!ready(source)) return 0;
        var actions = EssenceSavedData.get(source.getServer()).getPlayerData(player.getUUID()).attunement().recent(id);
        int pages = Math.max(1, (actions.size() + HISTORY_PAGE_SIZE - 1) / HISTORY_PAGE_SIZE);
        if (page > pages) { EssenceCommandUtil.fail(source, text("invalid_page", pages)); return 0; }
        EssenceCommandUtil.send(source, EssenceCommandUtil.title(text("history_title", name(id), page, pages)));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(EssenceText.command("label.player"), player.getDisplayName()));
        if (actions.isEmpty()) EssenceCommandUtil.send(source, EssenceCommandUtil.muted(text("no_recent")));
        for (var action : actions.subList((page - 1) * HISTORY_PAGE_SIZE, Math.min(actions.size(), page * HISTORY_PAGE_SIZE))) {
            summaryAction(source, action);
            EssenceCommandUtil.send(source, EssenceCommandUtil.line(text("action"), action.actionId()));
            EssenceCommandUtil.send(source, EssenceCommandUtil.line(text("source"), action.sourceSignature()));
            EssenceCommandUtil.send(source, EssenceCommandUtil.line(text("base"), number(action.baseValue())));
            EssenceCommandUtil.send(source, EssenceCommandUtil.line(text("multipliers"), number(action.investmentMultiplier()) + " / " + number(action.repetitionMultiplier()) + " / " + number(action.varietyMultiplier())));
            EssenceCommandUtil.send(source, EssenceCommandUtil.line(text("gain"), EssenceCommandUtil.format(action.finalContribution())));
        }
        if (page < pages) EssenceCommandUtil.send(source, EssenceCommandUtil.command(
                historyCommand(source, player, id, page + 1), text("next_page")));
        return 1;
    }

    private static String historyCommand(CommandSourceStack source, ServerPlayer player, String category, int page) {
        String target = source.getEntity() == player ? "" : " " + player.getGameProfile().getName();
        return "/essence debug player attunement history " + EssenceCommandUtil.commandId(ResourceLocation.parse(category)) + " " + page + target;
    }

    private static void summaryAction(CommandSourceStack source, AttunementContribution action) {
        var method = AttunementActivityRegistry.get(action.activityId());
        Component label = method == null ? Component.literal(action.activityId()) : Component.translatable(method.labelKey());
        long percent = action.finalContribution() * 100 / AttunementLedger.SCALE;
        Component outcome = action.credited() ? EssenceCommandUtil.good(text("action_percent", percent == 0 ? "<1" : Long.toString(percent)))
                : EssenceCommandUtil.warn(Component.translatableWithFallback("attunement.essence_ascendance.reason." + action.rejectionReason(), text("not_credited").getString()));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(label, outcome));
    }

    private static int activities(CommandSourceStack source, String category) {
        EssenceCommandUtil.send(source, EssenceCommandUtil.title(text("activities")));
        if (category == null) {
            for (var essence : EssenceRegistry.values()) EssenceCommandUtil.send(source, EssenceCommandUtil.command(
                    "/essence debug player attunement activities " + EssenceCommandUtil.commandId(essence.id()), EssenceText.essence(essence)));
        } else for (var method : AttunementActivityRegistry.values().stream().filter(a -> a.categoryId().equals(category)).toList()) {
            EssenceCommandUtil.send(source, EssenceCommandUtil.section(Component.translatable(method.labelKey())));
            EssenceCommandUtil.send(source, EssenceCommandUtil.line(text("registration"), method.id() + " / " + method.calibrationFamily() + " / " + method.units()));
        }
        return 1;
    }

    private static int reachability(CommandSourceStack source, String tier) {
        if (!ready(source)) return 0;
        var profile = EssenceConfigManager.serverRuntime().attunement();
        EssenceCommandUtil.send(source, EssenceCommandUtil.title(text("reachability")));
        if (tier == null) {
            for (var chapter : profile.chapters().values()) EssenceCommandUtil.send(source, EssenceCommandUtil.command(
                    "/essence debug player attunement reachability " + EssenceCommandUtil.commandId(ResourceLocation.parse(chapter.fromTierId())),
                    text("chapter_breadth", chapter.requiredCategories(), chapter.categories().size())));
            return 1;
        }
        var chapter = profile.chapter(tier);
        if (chapter == null) { EssenceCommandUtil.fail(source, text("no_chapter")); return 0; }
        for (var category : chapter.categories().values()) {
            long base = profile.methods().values().stream().filter(m -> m.categoryId().equals(category.categoryId()) && m.baseGameAccessible()).count();
            long accessible = chapter.activities().values().stream().filter(r -> r.categoryId().equals(category.categoryId()) && r.stageAccessible()).count();
            EssenceCommandUtil.send(source, EssenceCommandUtil.line(name(category.categoryId()), text("accessibility", accessible, base)));
        }
        EssenceCommandUtil.send(source, EssenceCommandUtil.muted(text("reachability_boundary")));
        return 1;
    }

    private static boolean ready(CommandSourceStack source) {
        if (EssenceConfigManager.authoritativeReady()) return true;
        EssenceCommandUtil.fail(source, text("profile_unavailable"));
        return false;
    }
    private static Component state(AttunementSnapshot snapshot) {
        return snapshot.maximumTier() ? EssenceCommandUtil.good(text("maximum")) : snapshot.ready()
                ? EssenceCommandUtil.good(text("ready")) : EssenceCommandUtil.warn(text("progressing"));
    }
    private static Component name(String id) {
        return EssenceRegistry.get(ResourceLocation.parse(id)).map(EssenceText::essence).orElseGet(() -> Component.literal(id));
    }
    private static int playerHelp(CommandSourceStack source) {
        EssenceCommandUtil.send(source, EssenceCommandUtil.title(text("title")));
        help(source, "/essence attunement", "help");
        help(source, "/essence attunement <category>", "detail_help");
        help(source, "/essence ascend", "ascend_hint");
        return 1;
    }
    static int adminHelp(CommandSourceStack source) {
        EssenceCommandUtil.send(source, EssenceCommandUtil.title(text("admin_title")));
        help(source, "/essence admin player attunement set <category|all> <0..100> [player]", "set_help");
        help(source, "/essence admin player attunement fill <category|all> [player]", "fill_help");
        help(source, "/essence admin player attunement reset <category|all> [player]", "reset_help");
        EssenceCommandUtil.send(source, EssenceCommandUtil.muted(text("admin_scope")));
        return 1;
    }
    private static int debugHelp(CommandSourceStack source) {
        EssenceCommandUtil.send(source, EssenceCommandUtil.title(text("debug_title")));
        help(source, "/essence debug player attunement summary [player]", "help");
        help(source, "/essence debug player attunement show <category> [player]", "detail_help");
        help(source, "/essence debug player attunement history <category> [page] [player]", "history_help");
        help(source, "/essence debug player attunement activities [category]", "activities_help");
        help(source, "/essence debug player attunement reachability [tier]", "reachability_help");
        return 1;
    }
    private static void help(CommandSourceStack source, String command, String key) {
        EssenceCommandUtil.send(source, EssenceCommandUtil.command(command, text(key)));
    }
    private static Component text(String key, Object... args) { return EssenceText.command("attunement." + key, args); }
    private static String number(double value) { return String.format(Locale.ROOT, "%.2f", value); }
}
