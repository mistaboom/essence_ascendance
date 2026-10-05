package com.mistaboom.essence_ascendance.command;

import com.mistaboom.essence_ascendance.attunement.AttunementActivityRegistry;
import com.mistaboom.essence_ascendance.attunement.AttunementAdminService;
import com.mistaboom.essence_ascendance.attunement.AttunementEvent;
import com.mistaboom.essence_ascendance.attunement.AttunementLedger;
import com.mistaboom.essence_ascendance.attunement.AttunementProfile;
import com.mistaboom.essence_ascendance.attunement.AttunementService;
import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import com.mistaboom.essence_ascendance.essence.EssenceRegistry;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import com.mistaboom.essence_ascendance.progression.MilestoneProviders;
import com.mistaboom.essence_ascendance.progression.Milestones;
import com.mistaboom.essence_ascendance.skill.Skills;
import com.mistaboom.essence_ascendance.stat.EssenceStats;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Real Brigadier parsing and NBT mutation checks; does not construct or modify a world. */
public final class EssenceCommandTest {
    private static final String OFFENSE = "essence_ascendance:offense";
    private static final String MOBILITY = "essence_ascendance:mobility";
    private static final String UTILITY = "essence_ascendance:utility";
    private static final AttunementProfile.Policy POLICY = new AttunementProfile.Policy(1, .15, .25, 64);
    private static int assertions;

    public static void main(String[] args) throws Exception {
        Thread.currentThread().setUncaughtExceptionHandler((thread, failure) -> failure.printStackTrace(
                new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.err))));
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        EssenceTypes.init();
        AscendanceTiers.init();
        EssenceStats.init();
        MilestoneProviders.init();
        Milestones.init();
        Skills.init();
        commandTrees();
        presentation();
        operatorEdits();
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out)).println(
                "EssenceCommandTest: " + assertions + " command/presentation/operator assertions PASS");
    }

    private static void commandTrees() throws Exception {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        EssenceCommands.register(dispatcher);
        var player = source(0);
        var operator = source(EssenceCommandUtil.ADMIN_PERMISSION);
        for (String path : List.of("", "help", "status", "balance", "balance offense", "balance \"essence_ascendance:offense\"", "bonuses", "bonuses list",
                "bonuses list all", "bonuses list offense", "bonuses show melee_damage", "bonuses invest melee_damage 1",
                "attunement", "attunement help", "attunement offense", "ascend", "milestones")) {
            valid(dispatcher, "essence" + (path.isBlank() ? "" : " " + path), player);
        }
        for (String path : List.of(
                "admin", "admin help", "admin player", "admin item", "admin item tier set latent", "admin item tier set latent TestPlayer",
                "admin balance", "admin balance rebuild", "admin balance export", "admin mappings", "admin mappings reload", "admin config",
                "admin player attunement", "admin player attunement help", "admin player attunement set", "admin player attunement fill",
                "admin player attunement fill offense", "admin player attunement fill all TestPlayer",
                "admin player attunement reset", "admin player attunement reset mobility", "admin player attunement reset all TestPlayer",
                "admin player attunement set offense 0", "admin player attunement set offense 100", "admin player attunement set all 37 TestPlayer",
                "debug", "debug help", "debug mappings", "debug mappings status", "debug mappings list",
                "debug player", "debug player summary", "debug balance", "debug balance bonus melee_damage",
                "debug item", "debug item inspect",
                "debug item mapping", "debug item baselines", "debug machine", "debug machine crucible", "debug machine pylon", "debug machine infuser",
                "debug player attunement", "debug player attunement summary", "debug player attunement summary TestPlayer",
                "debug player attunement show", "debug player attunement show offense", "debug player attunement show offense TestPlayer",
                "debug player attunement history offense", "debug player attunement history offense 2",
                "debug player attunement history offense 1 TestPlayer", "debug player attunement history offense 2 TestPlayer",
                "debug player attunement activities", "debug player attunement activities utility",
                "debug player attunement reachability", "debug player attunement reachability latent",
                "debug player skills", "debug player skills summary", "debug player skills owned", "debug player skills loadout",
                "debug player skills show test_skill", "debug player milestones list", "debug player milestones show test_milestone",
                "debug player gameplay", "debug player gameplay offense", "debug player gameplay shield", "debug player gameplay projectiles",
                "debug player gameplay posture", "debug player gameplay status",
                "debug player milestones", "debug player equipment",
                "test", "test skills", "test skills effects", "test skills milestones")) {
            valid(dispatcher, "essence " + path, operator);
            invalid(dispatcher, "essence " + path, player);
            invalid(dispatcher, "essence " + path, source(EssenceCommandUtil.ADMIN_PERMISSION - 1));
        }
        for (String mutation : List.of("reset", "tier set latent", "tier set dormant", "essence give offense 1", "essence set offense 0",
                "essence clear all", "essence clear offense", "bonuses set melee_damage 0", "bonuses max all", "bonuses clear all",
                "essence set \"essence_ascendance:offense\" 1", "bonuses set \"essence_ascendance:melee_damage\" 1",
                "tier set \"essence_ascendance:latent\"", "attunement set \"essence_ascendance:offense\" 25",
                "skills grant all", "skills grant test_skill", "skills clear all", "skills activate test_skill",
                "milestones grant test_milestone", "milestones revoke test_milestone", "crucible clear all", "guide give")) {
            String path = "essence admin player " + mutation;
            valid(dispatcher, path, operator);
            valid(dispatcher, path + " TestPlayer", operator);
            valid(dispatcher, path + " @s", operator);
            invalid(dispatcher, path, player);
            invalid(dispatcher, path + " TestPlayer", player);
        }
        for (String inspection : List.of("summary", "equipment", "skills summary", "skills owned", "skills loadout", "skills show test_skill",
                "milestones list", "milestones show test_milestone", "gameplay offense", "gameplay defense", "gameplay vitality",
                "gameplay mobility", "gameplay gathering", "gameplay utility", "gameplay shield", "gameplay projectiles", "gameplay posture", "gameplay status")) {
            String path = "essence debug player " + inspection;
            valid(dispatcher, path + " TestPlayer", operator);
            valid(dispatcher, path + " @s", operator);
            invalid(dispatcher, path + " TestPlayer", player);
        }
        for (String alias : List.of("stats", "stats all", "stat", "stat melee_damage", "invest melee_damage 1", "progress",
                "admin reset", "admin tier set latent", "admin essence clear", "admin essences clear", "admin stats max", "admin stat max all",
                "admin stats clear", "admin skills grant all", "admin skills grant_all", "admin attunement fill offense", "admin milestone set test true",
                "admin itemtier set latent", "debug summary", "debug attunement", "debug skills", "debug gameplay", "debug equipment",
                "debug skill test_skill", "debug offense", "debug defense", "debug vitality",
                "debug mobility", "debug gathering", "debug utility", "debug shield", "debug projectiles",
                "debug crucible", "debug pylon", "debug infuser", "debug balance rebuild", "debug balance export", "debug mappings reload",
                "debug player skills test_skill", "debug player milestones test_milestone", "debug player attunement offense",
                "debug player attunement player TestPlayer", "debug balance resource", "debug balance equipment", "debug balance progression",
                "debug balance source", "debug balance stat melee_damage", "admin player skills grant_all",
                "admin player milestones set test_milestone true", "admin mappings status", "admin mappings list",
                "help admin", "help debug", "help test",
                "test skill-effects", "test skill-milestones")) {
            invalid(dispatcher, "essence " + alias, operator);
        }
        for (String invalid : List.of("admin player attunement set offense -1", "admin player attunement set offense 101",
                "admin player attunement set offense 10.5", "admin player attunement set offense",
                "admin player attunement fill all @a", "admin player essence give offense 0", "admin player essence set offense -1",
                "bonuses invest melee_damage 0", "bonuses invest melee_damage -1", "bonuses show", "bonuses invest melee_damage",
                "debug player attunement history offense TestPlayer",
                "debug player attunement history offense 0", "debug player attunement history offense -1",
                "debug player attunement history offense 1.5", "debug player attunement history offense 0 TestPlayer",
                "debug player attunement history offense -1 TestPlayer", "debug player attunement history offense 1.5 TestPlayer")) {
            invalid(dispatcher, "essence " + invalid, operator);
        }
        for (String prefix : List.of("essence admin player attunement set ", "essence admin player attunement fill ", "essence admin player attunement reset ")) {
            var suggestions = dispatcher.getCompletionSuggestions(dispatcher.parse(prefix, operator)).get().getList();
            check(suggestions.stream().anyMatch(entry -> entry.getText().equals("all")), "Missing all suggestion: " + prefix);
            for (var essence : EssenceRegistry.values()) {
                check(suggestions.stream().anyMatch(entry -> entry.getText().equals(essence.id().getPath())),
                        "Missing category suggestion: " + prefix + essence.id());
            }
        }
        literalChildren(dispatcher, List.of("essence", "admin"), Set.of("player", "item", "balance", "mappings", "config"));
        literalChildren(dispatcher, List.of("essence", "admin", "player"),
                Set.of("tier", "attunement", "essence", "bonuses", "skills", "milestones", "crucible", "guide", "reset"));
        literalChildren(dispatcher, List.of("essence", "debug", "player"),
                Set.of("summary", "attunement", "skills", "milestones", "equipment", "gameplay"));
        literalChildren(dispatcher, List.of("essence", "debug"), Set.of("player", "item", "machine", "balance", "mappings"));
        literalChildren(dispatcher, List.of("essence", "debug", "item"), Set.of("inspect", "mapping", "baselines"));
        literalChildren(dispatcher, List.of("essence", "debug", "machine"), Set.of("crucible", "pylon", "infuser"));
        literalChildren(dispatcher, List.of("essence", "debug", "balance"), Set.of("summary", "validate", "item", "bonus", "skill", "cost"));
        literalChildren(dispatcher, List.of("essence", "test"), Set.of("luck", "activation", "skills"));
        literalChildren(dispatcher, List.of("essence", "bonuses"), Set.of("list", "show", "invest"));
        literalChildren(dispatcher, List.of("essence", "admin", "balance"), Set.of("rebuild", "export"));
        literalChildren(dispatcher, List.of("essence", "admin", "mappings"), Set.of("reload"));
        literalChildren(dispatcher, List.of("essence", "debug", "mappings"), Set.of("status", "list"));
        namespaceSuggestions();
        // Raw Brigadier completion does not filter requirements; Minecraft filters the sent tree.
        var allowedUsage = dispatcher.getSmartUsage(dispatcher.getRoot().getChild("essence"), player);
        check(allowedUsage.keySet().stream().noneMatch(node -> Set.of("admin", "debug", "test").contains(node.getName())),
                "Privileged branches leaked into non-operator usable commands");
        for (String help : List.of("essence help", "essence admin", "essence admin player", "essence admin item", "essence admin balance",
                "essence debug", "essence debug player", "essence debug item", "essence debug machine", "essence debug player gameplay",
                "essence debug player skills", "essence debug player milestones", "essence debug balance", "essence debug mappings", "essence bonuses",
                "essence debug player attunement", "essence admin player attunement", "essence admin player attunement set", "essence admin player attunement fill",
                "essence admin player attunement reset", "essence attunement help", "essence test", "essence test skills")) {
            var recorded = new RecordingSource();
            check(dispatcher.execute(help, source(EssenceCommandUtil.ADMIN_PERMISSION, recorded)) == 1,
                    "Help should work without player/world context: " + help);
            check(!recorded.messages.isEmpty(), "Help emitted no readable output: " + help);
            for (Component message : recorded.messages) verifyHelpLinks(dispatcher, message, operator);
        }
    }

    private static void namespaceSuggestions() throws Exception {
        var ids = List.of(ResourceLocation.parse("essence_ascendance:offense"),
                ResourceLocation.parse("pack_expansion:offense"), ResourceLocation.parse("pack_expansion:spatial/mobility"));
        var empty = EssenceCommandUtil.suggestRegistryIds(new SuggestionsBuilder("", 0), ids).get().getList();
        var texts = empty.stream().map(com.mojang.brigadier.suggestion.Suggestion::getText).collect(Collectors.toSet());
        check(texts.contains("offense") && texts.contains("\"pack_expansion:offense\"")
                        && texts.contains("\"pack_expansion:spatial/mobility\""),
                "Registry suggestions omitted qualified mod IDs or confused a shared path");
        var qualified = EssenceCommandUtil.suggestRegistryIds(new SuggestionsBuilder("\"pack_expansion:", 0), ids).get().getList();
        check(qualified.size() == 2, "Quoted namespace prefix did not retain every matching mod ID");
        for (var suggestion : qualified) {
            var reader = new StringReader(suggestion.getText());
            String id = StringArgumentType.string().parse(reader);
            check(!reader.canRead() && ids.contains(ResourceLocation.parse(id)),
                    "Qualified suggestion cannot round-trip through its actual command argument parser");
        }
    }

    private static void literalChildren(CommandDispatcher<CommandSourceStack> dispatcher, List<String> path, Set<String> expected) {
        com.mojang.brigadier.tree.CommandNode<CommandSourceStack> node = dispatcher.getRoot();
        for (String part : path) {
            node = node.getChild(part);
            check(node != null, "Missing command branch " + String.join(" ", path));
        }
        var actual = node.getChildren().stream().filter(child -> child instanceof com.mojang.brigadier.tree.LiteralCommandNode<?>)
                .map(com.mojang.brigadier.tree.CommandNode::getName).filter(name -> !name.equals("help")).collect(Collectors.toSet());
        check(actual.equals(expected), "Unexpected aliases or missing domain branches at " + String.join(" ", path) + ": " + actual);
    }

    private static void verifyHelpLinks(CommandDispatcher<CommandSourceStack> dispatcher, Component component, CommandSourceStack source) {
        ClickEvent click = component.getStyle().getClickEvent();
        if (click != null) {
            check(click.getAction() == ClickEvent.Action.SUGGEST_COMMAND, "Help must never execute a mutation immediately");
            String input = click.getValue().strip();
            check(input.startsWith("/essence"), "Help suggests a command outside the Essence domain: " + input);
            check(!input.contains("<") && !input.contains("[") && !input.contains("|"), "Help pasted argument notation: " + input);
            var parsed = dispatcher.parse(input.substring(1), source);
            check(!parsed.getReader().canRead() && !parsed.getContext().getNodes().isEmpty(),
                    "Help suggests an unregistered or retired command prefix: " + input);
        }
        component.getSiblings().forEach(child -> verifyHelpLinks(dispatcher, child, source));
    }

    private static CommandSourceStack source(int permission) {
        return source(permission, CommandSource.NULL);
    }

    private static CommandSourceStack source(int permission, CommandSource output) {
        return new CommandSourceStack(output, Vec3.ZERO, Vec2.ZERO, null, permission,
                "CommandInvariant", Component.literal("CommandInvariant"), null, null);
    }

    private static final class RecordingSource implements CommandSource {
        private final List<Component> messages = new ArrayList<>();
        @Override public void sendSystemMessage(Component message) { messages.add(message.copy()); }
        @Override public boolean acceptsSuccess() { return true; }
        @Override public boolean acceptsFailure() { return true; }
        @Override public boolean shouldInformAdmins() { return false; }
    }

    private static boolean complete(CommandDispatcher<CommandSourceStack> dispatcher, String input, CommandSourceStack source) {
        var parsed = dispatcher.parse(input, source);
        return !parsed.getReader().canRead() && parsed.getContext().getCommand() != null;
    }

    private static void valid(CommandDispatcher<CommandSourceStack> dispatcher, String input, CommandSourceStack source) {
        check(complete(dispatcher, input, source), "Missing or incomplete command: " + input);
    }

    private static void invalid(CommandDispatcher<CommandSourceStack> dispatcher, String input, CommandSourceStack source) {
        check(!complete(dispatcher, input, source), "Unauthorized or invalid command parsed: " + input);
    }

    private static void presentation() {
        Component colored = Component.literal("Offense").withStyle(ChatFormatting.RED);
        Component line = EssenceCommandUtil.line(colored, "50%");
        check(line.getSiblings().getFirst().getStyle().getColor().equals(TextColor.fromLegacyFormat(ChatFormatting.RED)),
                "Shared label styling erased the category color");
        check(EssenceCommandUtil.line(Component.literal("Progress"), "50%").getSiblings().getFirst().getStyle().getColor()
                        .equals(TextColor.fromLegacyFormat(ChatFormatting.GRAY)), "Unstyled label did not receive standard gray");
        check(colored.getString().equals("Offense"), "Rendering mutated the caller's label");
        Component remoteRow = EssenceCommandUtil.suggest(colored, "/essence debug player attunement show offense TestPlayer");
        check(remoteRow.getStyle().getClickEvent().getValue().equals("/essence debug player attunement show offense TestPlayer"),
                "Concrete diagnostic row dropped the inspected player from its follow-up");
        check(remoteRow.getStyle().getClickEvent().getAction() == ClickEvent.Action.SUGGEST_COMMAND,
                "Clickable diagnostic row executed its command immediately");
        check(remoteRow.getStyle().getColor().equals(TextColor.fromLegacyFormat(ChatFormatting.RED))
                        && remoteRow.getString().equals("Offense") && colored.getStyle().getClickEvent() == null,
                "Clickable diagnostic row erased category color/text or mutated its input component");
        for (var sample : Map.of(
                "/essence attunement [category]", "/essence attunement ",
                "/essence admin player attunement set <category|all> <percent>", "/essence admin player attunement set ",
                "/essence debug player attunement show offense TestPlayer", "/essence debug player attunement show offense TestPlayer",
                "/essence debug player skills show test_skill TestPlayer", "/essence debug player skills show test_skill TestPlayer",
                "/essence debug player summary", "/essence debug player summary").entrySet()) {
            check(EssenceCommandUtil.commandSuggestion(sample.getKey()).equals(sample.getValue()), "Help syntax leaked into suggested command");
            var style = EssenceCommandUtil.command(sample.getKey(), "Details").getSiblings().getFirst().getStyle();
            check(style.getClickEvent().getAction() == ClickEvent.Action.SUGGEST_COMMAND, "Help click must suggest, not execute");
            check(style.getClickEvent().getValue().equals(sample.getValue()), "Unexpected help click payload");
            check(style.getHoverEvent() != null, "Command is missing its click hint");
        }
        check(EssenceCommandUtil.command("README_REPORTS.txt", "File guide").getSiblings().getFirst().getStyle().getClickEvent() == null,
                "File label incorrectly became a command action");
        check(EssenceCommandUtil.saturatingAdd(Long.MAX_VALUE, 1) == Long.MAX_VALUE, "Extreme admin investment crashed the summary");
    }

    private static void operatorEdits() {
        AttunementProfile profile = profile();
        var onboarding = profile.chapter(AscendanceTiers.LATENT.id().toString());
        PlayerEssenceData latent = new PlayerEssenceData();
        AttunementAdminService.setPercent(latent, profile, OFFENSE, 99);
        check(!AttunementService.shouldPromoteAutomatically(latent, onboarding), "Incomplete onboarding triggered automatic promotion");
        AttunementAdminService.setPercent(latent, profile, OFFENSE, 100);
        check(AttunementService.shouldPromoteAutomatically(latent, onboarding), "Filled onboarding seal did not qualify for automatic promotion");
        check(latent.getTierId().equals(AscendanceTiers.LATENT.id()), "Pure operator service bypassed synchronized server promotion");
        latent.setTier(AscendanceTiers.DORMANT);
        check(latent.attunement().progress(OFFENSE) == 0, "Onboarding override leaked into the next chapter");
        var chapter = profile.chapter(AscendanceTiers.DORMANT.id().toString());
        PlayerEssenceData data = new PlayerEssenceData();
        data.setTier(AscendanceTiers.DORMANT);
        data.attunement().chapter(chapter.id());
        contribute(data, chapter, "shared", "deal_damage");
        contribute(data, chapter, "shared", "run");
        contribute(data, chapter, "xp", "gain_experience");
        data.attunement().discover("test:biome");
        data.attunement().setRejectedHealingMicros(5_000_000L);
        CompoundTag untouchedMobility = categoryState(data.attunement(), MOBILITY);
        CompoundTag untouchedUtility = categoryState(data.attunement(), UTILITY);
        long playerRevision = data.nexusRevision();
        long ledgerRevision = data.attunement().revision();
        var selected = AttunementAdminService.setPercent(data, profile, OFFENSE, 37);
        check(selected.equals(List.of(OFFENSE)), "Single-seal edit selected more categories");
        check(data.attunement().progress(OFFENSE) == 370_000_000L, "Requested percentage was not exact");
        check(data.attunement().methodProgress("deal_damage") == 0 && data.attunement().recent(OFFENSE).isEmpty(),
                "Operator percentage fabricated or retained action attribution");
        check(!data.attunement().save().getCompound("history").contains(OFFENSE), "Operator edit retained repetition pressure");
        check(categoryState(data.attunement(), MOBILITY).equals(untouchedMobility), "Offense edit changed Mobility");
        check(categoryState(data.attunement(), UTILITY).equals(untouchedUtility), "Offense edit changed Utility");
        check(!data.attunement().discover("test:biome"), "Unrelated category reset exploration discoveries");
        check(data.attunement().rejectedHealingMicros() == 5_000_000L, "Offense edit cleared unrelated recovery debt");
        check(data.nexusRevision() > playerRevision && data.attunement().revision() > ledgerRevision,
                "Admin edit did not invalidate Nexus and Attunement snapshots");
        check(contribute(data, chapter, "shared", "deal_damage").credited(), "Edited seal retained duplicate-action rejection");
        check(!contribute(data, chapter, "shared", "run").credited(), "Unedited seal lost duplicate-action protection");

        AttunementAdminService.setPercent(data, profile, OFFENSE, 0);
        check(data.attunement().progress(OFFENSE) == 0 && data.attunement().recent(OFFENSE).isEmpty(), "Single reset retained progress/history");
        AttunementAdminService.setPercent(data, profile, "essence_ascendance:vitality", 0);
        check(data.attunement().rejectedHealingMicros() == 0, "Vitality reset retained invisible recovery debt");
        var all = AttunementAdminService.setPercent(data, profile, null, 100);
        check(all.size() == EssenceRegistry.values().size(), "Bulk edit did not select every seal");
        for (var category : chapter.categories().keySet()) check(data.attunement().progress(category) == AttunementLedger.SCALE, "Bulk fill skipped a seal");
        check(data.getTierId().equals(AscendanceTiers.DORMANT.id()), "Pure edit bypassed server promotion policy");
        var loaded = PlayerEssenceData.load(data.save());
        for (var category : chapter.categories().keySet()) check(loaded.attunement().progress(category) == AttunementLedger.SCALE, "Operator fill did not persist");
        data.attunement().setRejectedHealingMicros(5_000_000L);
        AttunementAdminService.setPercent(data, profile, null, 0);
        check(data.attunement().rejectedHealingMicros() == 0, "Bulk reset retained invisible recovery debt");
        check(data.attunement().recent().isEmpty(), "Bulk reset retained action history");
        for (var category : chapter.categories().keySet()) check(data.attunement().progress(category) == 0, "Bulk reset skipped a seal");
        check(data.attunement().discover("test:biome"), "Mobility reset left exploration discoveries completed");
        contribute(data, chapter, "resume", "gain_experience");
        rejectWithoutMutation(data, () -> AttunementAdminService.setPercent(data, profile, OFFENSE, -1));
        rejectWithoutMutation(data, () -> AttunementAdminService.setPercent(data, profile, OFFENSE, 101));
        rejectWithoutMutation(data, () -> AttunementAdminService.setPercent(data, profile, "test:missing", 50));
        data.attunement().chapter("test:old_chapter");
        rejectWithoutMutation(data, () -> AttunementAdminService.setPercent(data, profile, "test:missing", 50));
        data.setTier(AscendanceTiers.TRANSCENDENT);
        rejectWithoutMutation(data, () -> AttunementAdminService.setPercent(data, profile, null, 100));
    }

    private static com.mistaboom.essence_ascendance.attunement.AttunementContribution contribute(
            PlayerEssenceData data, AttunementProfile.Chapter chapter, String root, String method) {
        return data.attunement().contribute(root, AttunementEvent.Outcome.eligible(method, "test:source", 10), chapter, POLICY, 0);
    }

    /** Capture all category-local persistent fields so isolation checks include pressure and duplicate roots. */
    private static CompoundTag categoryState(AttunementLedger ledger, String category) {
        CompoundTag saved = ledger.save();
        CompoundTag result = new CompoundTag();
        result.putLong("progress", ledger.progress(category));
        result.put("recent", saved.getCompound("recent").getList(category, net.minecraft.nbt.Tag.TAG_COMPOUND).copy());
        result.put("history", saved.getCompound("history").getList(category, net.minecraft.nbt.Tag.TAG_COMPOUND).copy());
        CompoundTag methods = new CompoundTag();
        AttunementActivityRegistry.values().stream().filter(activity -> activity.categoryId().equals(category))
                .forEach(activity -> methods.putLong(activity.id(), ledger.methodProgress(activity.id())));
        result.put("methods", methods);
        var roots = new net.minecraft.nbt.ListTag();
        for (var root : saved.getList("roots", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            if (((CompoundTag) root).getString("id").endsWith("|" + category)) roots.add(root.copy());
        }
        result.put("roots", roots);
        return result;
    }

    private static void rejectWithoutMutation(PlayerEssenceData data, Runnable edit) {
        CompoundTag before = data.save();
        boolean rejected = false;
        try { edit.run(); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "Invalid operator request succeeded");
        check(data.save().equals(before), "Invalid request partially modified player state");
    }

    private static AttunementProfile profile() {
        var categories = new TreeMap<String, AttunementProfile.Category>();
        var rates = new TreeMap<String, AttunementProfile.Rate>();
        var methods = new TreeMap<String, AttunementProfile.Method>();
        EssenceRegistry.values().forEach(essence -> categories.put(essence.id().toString(),
                new AttunementProfile.Category(essence.id().toString(), 10_000, 100)));
        for (var activity : AttunementActivityRegistry.values()) {
            rates.put(activity.id(), new AttunementProfile.Rate(activity.id(), activity.categoryId(), 1, 10, activity.units(), "test evidence"));
            methods.put(activity.id(), new AttunementProfile.Method(activity.id(), activity.categoryId(), activity.units(),
                    activity.calibrationFamily(), activity.labelKey(), activity.descriptionKey(), activity.baseGameAccessible()));
        }
        String from = AscendanceTiers.DORMANT.id().toString();
        var chapter = new AttunementProfile.Chapter("test:dormant_to_awakened", from, AscendanceTiers.AWAKENED.id().toString(), 2, categories, rates);
        String latent = AscendanceTiers.LATENT.id().toString();
        var onboarding = new AttunementProfile.Chapter("test:latent_to_dormant", latent, from, 1, categories, rates);
        return new AttunementProfile(POLICY, Map.of(latent, onboarding, from, chapter), methods, Map.of(), List.of("Operator command fixture"));
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
