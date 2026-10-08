package com.mistaboom.essence_ascendance.valuation;

import com.google.gson.*;
import com.mistaboom.essence_ascendance.balance.engine.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import java.util.*;
import static com.mistaboom.essence_ascendance.balance.engine.SourceAvailability.*;

/** Definition-only fixtures on the real mapped item registry; no server/player/container creation. */
public final class LootAcquisitionTest {
    private static int checks;
    private static final String TABLE = "fixture:chests/early";
    public static void main(String[] args) {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        cropReservation();
        biomeAndVariableRolls();
        check(LootrProvider.readiness(null, false).status() == ProviderReadiness.Status.ABSENT, "Optional absent does not link Lootr");
        check(LootrProvider.readiness("1.21.1-1.11.38.126", false).status() == ProviderReadiness.Status.NOT_READY, "Installed is not ready");
        check(!LootrProvider.readiness("1.21.1-1.11.38.126", false).collectable(),
                "Unready installed Lootr is excluded without rejecting unrelated generation");
        check(!LootrProvider.readiness("older", true).collectable(), "Unaudited version not accepted");
        check(LootrProvider.readiness("1.21.1-1.11.38.125", true).collectable(), "Additional audited Lootr API supported");
        check(!LootrProvider.readiness("1.21.1-1.11.38.125", false).collectable(), "Additional version still requires ready service");
        check(!LootrProvider.blacklistUsesProblematicProcessors("1.21.1-1.11.38.125"), "Older audited blacklist has no extension registry");
        check(LootrProvider.blacklistUsesProblematicProcessors("1.21.1-1.11.38.126"), "Newer audited blacklist retains callback safety guard");
        check(!LootrProvider.teamLootSettingAvailable("1.21.1-1.11.38.125"), "Older audited per-player API has no team-loot getter");
        check(LootrProvider.teamLootSettingAvailable("1.21.1-1.11.38.126"), "Newer audited team-loot setting must be captured");
        try { LootrProvider.blacklistUsesProblematicProcessors("1.21.1-1.11.38.127"); throw new AssertionError("Unknown callback boundary accepted"); }
        catch (IllegalArgumentException expected) { checks++; }
        var off = rule(false, Set.of(), Set.of(), Set.of());
        var finite = policy(off, off, Set.of(), false);
        var personal = describe(finite, true, 0);
        check(personal.category() == Category.FINITE_PERSONALIZED && personal.scope() == Scope.PLAYER, "Finite per-player loot");
        check(!personal.provenRenewable() && personal.refresh().applicability() == Applicability.OFF, "Personalization is not renewable");
        check(describe(policy(off, off, Set.of(), true), true, 0).scope() == Scope.TEAM, "Team scope retained without enumerating teams");
        check(describe(LootrPolicy.ABSENT, true, 0).category() == Category.FINITE_SHARED, "Absent shared fallback");
        var unavailable = describe(new LootrPolicy(true, false, false, Set.of(), Set.of(), Set.of(), off, off,
                List.of(), List.of("Installed Lootr compatibility was unavailable")), true, 0);
        check(unavailable.category() == Category.UNKNOWN && !unavailable.provenRenewable()
                        && !unavailable.uncertainty().isEmpty(),
                "Failed installed integration stays unknown rather than ordinary absent/shared loot");
        check(!unavailable.accessProven(), "Failed installed Lootr policy cannot certify acquisition access");
        var excluded = describe(policy(off, off, Set.of(TABLE), false), true, 0);
        check(excluded.category() == Category.FINITE_SHARED && excluded.scope() == Scope.SHARED, "Conversion exclusion shared fallback");
        check(excluded.underlyingSource().equals(personal.underlyingSource()), "Personal/shared same underlying opportunity");
        var refresh = describe(policy(rule(true, Set.of(), Set.of(), Set.of()), off, Set.of(), false), true, 0);
        check(refresh.category() == Category.REFRESHABLE && refresh.provenRenewable(), "Explicit refresh source");
        check(refresh.refresh().ticks() == 24000, "Tick interval retained without a throughput conversion");
        var conditional = describe(policy(rule(false, Set.of(), Set.of("minecraft:overworld"), Set.of()), off, Set.of(), false), true, 0);
        check(conditional.category() == Category.CONDITIONAL_RENEWABLE && !conditional.provenRenewable(), "Spatial refresh stays conditional");
        var decay = describe(policy(rule(true, Set.of(), Set.of(), Set.of()), rule(true, Set.of(), Set.of(), Set.of()), Set.of(), false), true, 0);
        check(decay.category() == Category.ACCESS_LIMITED && !decay.provenRenewable(), "Decay limits competing refresh");
        var unproven = describe(finite, false, 0);
        check(unproven.scope() == Scope.CONDITIONAL_PERSONALIZATION && !unproven.uncertainty().isEmpty(), "Unknown eligible container retained");
        check(!describe(finite, true, 1).accessProven(), "Unsupported loot never proves access");
        var converted = ValuationEvidenceSnapshot.containerSource(source(personal));
        check(converted.id().equals(TABLE) && converted.kind() == AcquisitionSource.Kind.LOOT, "Shared consumer keeps underlying identity");
        check(!converted.renewable() && !converted.rateKnown() && converted.unitsPerSecond() == 0, "Finite personal source has no passive rate");
        var refreshedSource = ValuationEvidenceSnapshot.containerSource(source(refresh));
        check(refreshedSource.renewable() && !refreshedSource.rateKnown(), "Refresh never establishes throughput");
        var gson = new Gson();
        check(gson.fromJson(gson.toJson(converted), AcquisitionSource.class).equals(converted), "Structured availability roundtrip");
        check(converted.stage() == ProgressionBand.ENTRY, "Early structure opportunity independent of item's crafting tier");
        check(describe(finite, true, 0).equals(personal), "Deterministic population-independent settings projection");
        var orderedRules = rule(false, Set.of("fixture:z", "fixture:a"), Set.of("fixture:z", "fixture:a"), Set.of());
        check(new ArrayList<>(orderedRules.tables()).equals(List.of("fixture:a", "fixture:z"))
                        && new ArrayList<>(policy(orderedRules, off, Set.of("fixture:z", "fixture:a"), false).blockedTables()).equals(List.of("fixture:a", "fixture:z")),
                "Policy set serialization has stable order across JVMs");
        graphs();
        unenchantedMobDrops();
        blockHarvestAudit();
        blockHarvestDominance();
        if (args.length > 0) installedDefinitionFixture(args[0]);
        System.out.println("LootAcquisitionTest: " + checks + " checks PASS; definition-only fixture, no multiplayer/native pack acceptance");
    }
    private static void unenchantedMobDrops() {
        try (var stream = LootAcquisitionTest.class.getResourceAsStream("/data/minecraft/loot_table/entities/blaze.json")) {
            var table = JsonParser.parseReader(new java.io.InputStreamReader(java.util.Objects.requireNonNull(stream), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            LootSemanticsAudit.auditTables(Map.of(id("minecraft:entities/blaze"), table));
            var functions = table.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries").get(0).getAsJsonObject().get("functions");
            check(GenerationLootEvidence.unresolvedFunctions(functions) > 0, "Container context does not silently borrow a player-kill assumption");
            check(GenerationLootEvidence.unresolvedUnenchantedEntityFunctions(functions) == 0,
                    "Packaged native blaze loot admits its zero-enchantment count identity without claiming Looting yields");
            var failed = functions.deepCopy().getAsJsonArray(); failed.get(1).getAsJsonObject().addProperty(GenerationLootEvidence.UNRESOLVED, true);
            check(GenerationLootEvidence.unresolvedUnenchantedEntityFunctions(failed) > 0, "Failed native function encoding remains unknown");
            var custom = functions.deepCopy().getAsJsonArray(); custom.get(1).getAsJsonObject().addProperty("function", "test:enchanted_count_increase");
            check(GenerationLootEvidence.unresolvedUnenchantedEntityFunctions(custom) > 0, "A similarly named custom function cannot borrow native identity");
        } catch (java.io.IOException failure) { throw new AssertionError(failure); }
        var unresolved = new ProceduralValuationIndex.DropSource(id("test:mob"), .5, 1, 2, 20, 2, 0, false, 1, 1, true, List.of());
        var projected = ValuationEvidenceSnapshot.mobSource(unresolved, ProgressionBand.EARLY, 1, true);
        check(!projected.availability().accessProven() && !projected.availability().uncertainty().isEmpty(),
                "Compact mob rows retain unresolved predicates even if another item source is reliable");
        var fishing = ValuationEvidenceSnapshot.fishingSource(new ProceduralValuationIndex.FishingLootSource(id("test:fishing"), .2, 1,
                1, ProceduralValuationResult.ProgressionBand.OVERWORLD, 1, List.of("conditional target")));
        check(!fishing.availability().accessProven() && !fishing.availability().uncertainty().isEmpty()
                        && fishing.dependencies().contains("minecraft:fishing_rod"),
                "Compact fishing rows retain uncertainty and actual setup instead of becoming free supply");
    }
    private static void graphs() {
        Map<ResourceLocation, JsonObject> tables = new TreeMap<>();
        tables.put(id("fixture:leaf"), json("{\"pools\":[{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"minecraft:diamond\",\"functions\":[{\"function\":\"minecraft:set_count\",\"count\":2}]}]}]}"));
        tables.put(id(TABLE), json("{\"pools\":[{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:loot_table\",\"value\":\"fixture:leaf\"}]}]}"));
        LootSemanticsAudit.auditTables(tables);
        var memo = new HashMap<ResourceLocation, Map<net.minecraft.world.item.Item, ProceduralValuationIndex.ContainerEstimate>>();
        long start = System.nanoTime();
        var result = ProceduralValuationIndex.estimateContainerTable(id(TABLE), tables, memo, new HashSet<>());
        check(result.get(Items.DIAMOND).expectedCount() == 2 && result.get(Items.DIAMOND).complexConditionCount() == 0, "Nested effective table count supported");
        check(memo.size() == 2, "Nested tables memoized once for every consumer");
        check(ProceduralValuationIndex.estimateContainerTable(id(TABLE), tables, memo, new HashSet<>()) == result, "Root reused");
        check(ProceduralValuationIndex.estimateContainerTable(id("fixture:leaf"), tables, memo, new HashSet<>()) == memo.get(id("fixture:leaf")), "Nested reused");
        var duplicate = tables.get(id(TABLE)).deepCopy();
        var entries = duplicate.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries");
        entries.add(entries.get(0).deepCopy()); tables.put(id(TABLE), duplicate); memo.clear();
        var deduplicated = ProceduralValuationIndex.estimateContainerTable(id(TABLE), tables, memo, new HashSet<>());
        check(deduplicated.size() == 1 && deduplicated.get(Items.DIAMOND).expectedCount() == 2, "Repeated nested alternatives merge into one item/source event, not independent opportunities");
        var secondMemo = new HashMap<ResourceLocation, Map<net.minecraft.world.item.Item, ProceduralValuationIndex.ContainerEstimate>>();
        check(ProceduralValuationIndex.estimateContainerTable(id(TABLE), tables, secondMemo, new HashSet<>()).equals(deduplicated), "Deterministic normalized loot generation");
        for (String condition : List.of("{\"condition\":\"fixture:random_chance\",\"chance\":1}",
                "{\"condition\":\"minecraft:random_chance\",\"chance\":{\"type\":\"fixture:dynamic_number\"}}")) {
            var conditionalTable = json("{\"pools\":[{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"minecraft:diamond\"}]}]}");
            conditionalTable.getAsJsonArray("pools").get(0).getAsJsonObject().add("conditions", JsonParser.parseString("[" + condition + "]"));
            var conditioned = ProceduralValuationIndex.estimateContainerTable(id("fixture:conditional"), Map.of(id("fixture:conditional"), conditionalTable), new HashMap<>(), new HashSet<>());
            check(conditioned.get(Items.DIAMOND).complexConditionCount() > 0, "Custom chance predicate/number provider remains unresolved");
        }
        var scoped = new RuntimeLootAudit.Modifier("fixture.Modifier", json("{\"conditions\":[{\"condition\":\"neoforge:loot_table_id\",\"loot_table_id\":\"fixture:leaf\"}]}"), "unsupported callback");
        check(scoped.mayAffect("fixture:leaf") && !scoped.mayAffect(TABLE), "Read-only AND table-id modifier scope");
        check(new RuntimeLootAudit.Modifier("opaque", new JsonObject(), "unknown").mayAffect(TABLE), "Opaque modifier cannot be excluded");
        RuntimeLootAudit.mark(tables, List.of(scoped), List.of());
        memo.clear();
        check(ProceduralValuationIndex.estimateContainerTable(id(TABLE), tables, memo, new HashSet<>()).get(Items.DIAMOND).complexConditionCount() > 0,
                "Unsupported runtime modifier propagates through nesting");
        tables.get(id("fixture:leaf")).getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries").get(0).getAsJsonObject()
                .add("functions", JsonParser.parseString("[{\"function\":\"fixture:transform_item\"}]"));
        LootSemanticsAudit.auditTables(tables); memo.clear();
        check(ProceduralValuationIndex.estimateContainerTable(id(TABLE), tables, memo, new HashSet<>()).get(Items.DIAMOND).complexConditionCount() > 0,
                "Encoded custom function not silently ordinary vanilla loot");
        Map<ResourceLocation, JsonObject> cycle = new TreeMap<>();
        cycle.put(id("fixture:a"), json("{\"pools\":[{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"minecraft:diamond\"},{\"type\":\"minecraft:loot_table\",\"value\":\"fixture:b\"}]}]}"));
        cycle.put(id("fixture:b"), json("{\"pools\":[{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:loot_table\",\"value\":\"fixture:a\"}]}]}"));
        LootSemanticsAudit.auditTables(cycle); memo.clear();
        check(ProceduralValuationIndex.estimateContainerTable(id("fixture:a"), cycle, memo, new HashSet<>()).get(Items.DIAMOND).complexConditionCount() > 0, "Cycle guarded and explicitly uncertain");
        check(!LootSemanticsAudit.facts(tables.get(id(TABLE))).isEmpty(), "Nested and conditional facts retained for later capability availability");
        for (String function : List.of("enchant_randomly", "enchant_with_levels", "set_enchantments")) {
            var gear = json("{\"pools\":[{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"minecraft:fishing_rod\",\"functions\":[{\"function\":\"minecraft:" + function + "\"}]}]}]}");
            var gearTables = Map.of(id("fixture:rod"), gear);
            LootSemanticsAudit.auditTables(gearTables);
            check(ProceduralValuationIndex.estimateContainerTable(id("fixture:rod"), gearTables, new HashMap<>(), new HashSet<>())
                    .get(Items.FISHING_ROD).complexConditionCount() == 0,
                    "Native " + function + " keeps a non-book item's identity for item-only ingredients/actions");
            for (String owner : List.of("entry", "pool", "root", "reference")) {
                var leaf = json("{\"pools\":[{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"minecraft:book\"}]}]}");
                var parent = json("{\"pools\":[{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:loot_table\",\"value\":\"fixture:book_leaf\"}]}]}");
                var pool = leaf.getAsJsonArray("pools").get(0).getAsJsonObject();
                var target = switch (owner) {
                    case "entry" -> pool.getAsJsonArray("entries").get(0).getAsJsonObject();
                    case "pool" -> pool;
                    case "root" -> leaf;
                    default -> parent.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries").get(0).getAsJsonObject();
                };
                target.add("functions", JsonParser.parseString("[{\"function\":\"minecraft:" + function + "\"}]"));
                var transformed = Map.of(id("fixture:book_leaf"), leaf, id("fixture:book_parent"), parent);
                LootSemanticsAudit.auditTables(transformed);
                var projected = ProceduralValuationIndex.estimateContainerTable(id("fixture:book_parent"), transformed, new HashMap<>(), new HashSet<>());
                check(projected.get(Items.BOOK).complexConditionCount() > 0,
                        function + " at " + owner + " cannot prove an ordinary crafting book through a nested table");
            }
        }
        System.out.println("Synthetic normalized graph projection/audit: " + ((System.nanoTime() - start) / 1e6) + " ms; not native generation timing");
    }
    private static void blockHarvestAudit() {
        String table = "minecraft:blocks/sweet_berry_bush";
        var hand = new ProceduralBlockLoot.Context("minecraft:sweet_berry_bush", Map.of("age", "3"),
                Set.of("age"), "minecraft:air", Set.of(), Map.of());
        var tool = new ProceduralBlockLoot.Context(hand.blockId(), hand.properties(), hand.supportedProperties(),
                "minecraft:iron_pickaxe", Set.of(), Map.of());
        Set<String> berries = Set.of("minecraft:sweet_berries");
        // The mapped game's actual table exercises conditional set_count, bonus and explosion functions.
        JsonObject berryTable;
        try (var stream = LootAcquisitionTest.class.getResourceAsStream("/data/minecraft/loot_table/blocks/sweet_berry_bush.json")) {
            berryTable = JsonParser.parseReader(new java.io.InputStreamReader(Objects.requireNonNull(stream))).getAsJsonObject();
        } catch (java.io.IOException failure) { throw new AssertionError(failure); }
        var tables = new TreeMap<ResourceLocation, JsonObject>(); tables.put(id(table), berryTable);
        LootSemanticsAudit.auditTables(tables);
        var drop = blockDrops(berryTable, hand).get("minecraft:sweet_berries");
        check(drop != null && drop.unresolved() == 0 && drop.expectedCount() == 2.5, "Native mature berry base count survives narrower container audit");
        check(GenerationLootEvidence.unresolvedFunctions(berryTable) > 0, "Container limitations remain visible");
        var unknown = modifier("fixture.Unknown", "{}", BlockLootModifierAudit.Mode.UNKNOWN, Set.of());
        RuntimeLootAudit.mark(tables, List.of(unknown), List.of());
        check(blockDrops(berryTable, hand).get("minecraft:sweet_berries").unresolved() == 0, "Runtime marker is evaluated separately for concrete block context");
        check(affected(unknown, table, hand, berries), "Unknown callback still blocks base proof");
        var custom = berryTable.deepCopy();
        custom.getAsJsonArray("functions").add(json("{\"function\":\"fixture:replace_drop\"}"));
        check(blockDrops(custom, hand).get("minecraft:sweet_berries").unresolved() > 0, "Real unsupported block function stays unresolved");
        RuntimeLootAudit.mark(tables, List.of(), List.of("fixture.Extension"));
        check(blockDrops(berryTable, hand).get("minecraft:sweet_berries").unresolved() > 0, "Unsupported extension marker is not discarded");

        String scoped = """
                {"conditions":[{"condition":"minecraft:any_of","terms":[
                  {"condition":"neoforge:loot_table_id","loot_table_id":"fixture:chests/a"},
                  {"condition":"neoforge:loot_table_id","loot_table_id":"fixture:chests/b"}]}]}
                """;
        var scopedModifier = modifier("fixture.Scoped", scoped, BlockLootModifierAudit.Mode.UNKNOWN, Set.of());
        check(!scopedModifier.mayAffect(table) && scopedModifier.mayAffect("fixture:chests/a"), "Nested OR table scopes preserve applicable tables");
        var inverted = modifier("fixture.Scoped", """
                {"conditions":[{"condition":"minecraft:inverted","term":{"condition":"neoforge:loot_table_id","loot_table_id":"minecraft:blocks/sweet_berry_bush"}}]}
                """, BlockLootModifierAudit.Mode.UNKNOWN, Set.of());
        check(!inverted.mayAffect(table) && inverted.mayAffect(TABLE), "Inverted table scope");
        var bypass = new RuntimeLootAudit.Modifier("fixture.Bypass", scopedModifier.definition(), "custom apply",
                BlockLootModifierAudit.Rule.UNKNOWN, false);
        check(bypass.mayAffect(table) && affected(bypass, table, hand, berries), "Overridden apply cannot borrow base class condition contract");
        check(!affected(modifier("fixture.Entity", """
                {"conditions":[{"condition":"minecraft:entity_properties","entity":"this","predicate":{"type":"minecraft:zombie"}}]}
                """, BlockLootModifierAudit.Mode.UNKNOWN, Set.of()), table, hand, berries), "Zombie-only modifier cannot affect player block harvesting");
        check(!affected(modifier("fixture.Damage", """
                {"conditions":[{"condition":"minecraft:damage_source_properties","predicate":{}}]}
                """, BlockLootModifierAudit.Mode.UNKNOWN, Set.of()), table, hand, berries), "Block context lacks damage source");
        check(!affected(modifier("fixture.Block", """
                {"conditions":[{"condition":"minecraft:block_state_property","block":"minecraft:short_grass"}]}
                """, BlockLootModifierAudit.Mode.UNKNOWN, Set.of()), table, hand, berries), "Grass replacement does not taint berry harvest");
        check(affected(modifier("fixture.State", """
                {"conditions":[{"condition":"minecraft:block_state_property","block":"minecraft:sweet_berry_bush","properties":{"charged":"true"}}]}
                """, BlockLootModifierAudit.Mode.UNKNOWN, Set.of()), table, hand, berries), "Unknown state remains possible");

        check(!affected(modifier("fixture.Append", "{}", BlockLootModifierAudit.Mode.APPEND_ONLY, Set.of()), table, hand, berries), "Audited append preserves base count without crediting extra rewards");
        check(!affected(modifier("fixture.Tool", "{}", BlockLootModifierAudit.Mode.NONEMPTY_TOOL, Set.of()), table, hand, berries), "Audited internal empty-tool guard");
        check(affected(modifier("fixture.Tool", "{}", BlockLootModifierAudit.Mode.NONEMPTY_TOOL, Set.of()), table, tool, berries), "Nonempty tool remains uncertain");
        check(affected(modifier("fixture.Match", """
                {"conditions":[{"condition":"minecraft:match_tool","predicate":{}}]}
                """, BlockLootModifierAudit.Mode.UNKNOWN, Set.of()), table, hand, berries), "Empty item predicate can match empty stack");
        check(!affected(modifier("fixture.Components", """
                {"conditions":[{"condition":"minecraft:match_tool","predicate":{"components":{"fixture:component":{}}}}]}
                """, BlockLootModifierAudit.Mode.UNKNOWN, Set.of()), table, hand, berries), "Empty stack has no required component");
        String magmatic = "net.silentchaos512.gear.loot.modifier.MagmaticTraitLootModifier";
        var trait = modifier(magmatic, """
                {"conditions":[{"condition":"silentgear:has_trait","trait":"silentgear:magmatic","level":{}}]}
                """, BlockLootModifierAudit.Mode.TOOL_CONDITION, Set.of());
        check(!affected(trait, table, hand, berries) && affected(trait, table, tool, berries), "Audited trait predicate requires gear but does not prove nonempty tool meets trait");
        check(affected(modifier(magmatic, "{}", BlockLootModifierAudit.Mode.TOOL_CONDITION, Set.of()), table, hand, berries), "Removing configured trait gate cannot preserve a destructive callback");
        check(affected(modifier(magmatic, """
                {"conditions":[{"condition":"minecraft:inverted","term":{"condition":"silentgear:has_trait","trait":"silentgear:magmatic"}}]}
                """, BlockLootModifierAudit.Mode.TOOL_CONDITION, Set.of()), table, hand, berries), "Negated trait gate preserves boolean semantics");
        var fiery = modifier("com.stal111.forbidden_arcanus.common.loot.FieryLootModifier", """
                {"conditions":[{"condition":"minecraft:match_tool","predicate":{"predicates":{"forbidden_arcanus:modifier":"forbidden_arcanus:fiery"}}}]}
                """, BlockLootModifierAudit.Mode.TOOL_CONDITION, Set.of());
        check(!affected(fiery, table, hand, berries) && affected(fiery, table, tool, berries), "Audited single-component predicate requires a component-bearing tool");
        var ore = modifier("fixture.Greed", "{}", BlockLootModifierAudit.Mode.BLOCK_ORES, Set.of("minecraft:iron_ore"));
        check(!affected(ore, table, hand, berries), "Ore-only destructive callback excludes non-ore block");
        check(affected(ore, "fixture:chests/other", hand, berries), "Ore guard is not assumed outside blocks path");
        var armor = modifier("fixture.Armor", "{}", BlockLootModifierAudit.Mode.OUTPUT_ITEMS, Set.of("minecraft:iron_chestplate"));
        check(!BlockLootModifierAudit.couldChangeOutput(armor, "minecraft:sweet_berries")
                && BlockLootModifierAudit.couldChangeOutput(armor, "minecraft:iron_chestplate"), "Output replacement only preserves non-target items");
        var pattern = modifier("fixture.Pattern", """
                {"entries":[{"chance":0,"pattern":{"path_regex":".*blocks.*"}},{"chance":1,"pattern":{"path_regex":".*"}}]}
                """, BlockLootModifierAudit.Mode.FIRST_PATTERN, Set.of());
        check(!affected(pattern, table, hand, berries) && affected(pattern, TABLE, hand, berries), "First matching zero-chance rule suppresses callback only for matched table");
        var giant = modifier("fixture.Giant", "{}", BlockLootModifierAudit.Mode.FIRST_OUTPUT_CONVERSION, Set.of("minecraft:cobblestone"));
        check(!affected(giant, table, hand, berries), "First-stack converter cannot erase retained non-conversion prefix");
        check(affected(giant, table, hand, Set.of("minecraft:cobblestone", "minecraft:sweet_berries")), "Potential converted first stack taints the entire output list");
        check(new BlockLootModifierAudit(List.of(unknown, giant)).applicable(table, hand, berries).contains(giant), "Unknown preceding transform prevents first-stack proof");
        check(!affected(modifier("fixture.EmptyRules", "{}", BlockLootModifierAudit.Mode.EMPTY_DEFINITIONS, Set.of()), table, hand, berries), "Audited empty loaded rule list");
        String append = "com.aetherteam.aether.loot.modifiers.DoubleDropsModifier";
        check(BlockLootModifierAudit.auditedMode(append, id -> "1.5.10") == BlockLootModifierAudit.Mode.APPEND_ONLY, "Exact implementation/version contract");
        check(BlockLootModifierAudit.auditedMode(append, id -> "1.5.11") == BlockLootModifierAudit.Mode.UNKNOWN
                && BlockLootModifierAudit.auditedMode("fixture.SameName", id -> "1.5.10") == BlockLootModifierAudit.Mode.UNKNOWN, "Version drift or unrelated implementation stays unknown");
        String rope = "net.mehvahdjukaar.supplementaries.platform.ReplaceRopeByConfigModifier";
        for (String version : List.of("1.21.1-3.6.7", "1.21.1-3.9.9"))
            check(BlockLootModifierAudit.auditedMode(rope, id -> id.equals("supplementaries") ? version : null)
                    == BlockLootModifierAudit.Mode.OUTPUT_ITEMS, "Every bytecode-audited release shares the same bounded rope-output contract: " + version);
        check(BlockLootModifierAudit.auditedMode(rope, id -> "1.21.1-3.6.8") == BlockLootModifierAudit.Mode.UNKNOWN,
                "A neighboring unexamined rope release remains unknown");
        check(BlockLootModifierAudit.auditedMode(rope, id -> null) == BlockLootModifierAudit.Mode.UNKNOWN,
                "A missing dependency never becomes an audited rope contract");
        check(BlockLootModifierAudit.auditedMode("fixture.ReplaceRopeByConfigModifier", id -> "1.21.1-3.6.7")
                        == BlockLootModifierAudit.Mode.UNKNOWN, "Matching simple class names do not establish native output scope");
        var ropeOnly = modifier(rope, "{}", BlockLootModifierAudit.Mode.OUTPUT_ITEMS, Set.of("fixture:rope"));
        var logHand = new ProceduralBlockLoot.Context("minecraft:oak_log", Map.of(), Set.of(), "minecraft:air", Set.of(), Map.of());
        check(affected(ropeOnly, "minecraft:blocks/oak_log", logHand, Set.of("minecraft:oak_log"))
                        && !outputAffected(ropeOnly, "minecraft:blocks/oak_log", logHand, Set.of("minecraft:oak_log")),
                "Audited rope replacement does not suppress a native log harvest or finite starting-log proof");
        check(outputAffected(ropeOnly, "fixture:blocks/rope", hand, Set.of("fixture:rope")),
                "Actual loaded rope-tag outputs retain replacement uncertainty");
    }
    private static void blockHarvestDominance() {
        var known = new ProceduralBlockHarvest.HarvestDrop(Items.SWEET_BERRIES, .25, 2.5, 0, null, false, List.of());
        check(!ProceduralBlockHarvest.knownHandDominates(false, null, Items.SWEET_BERRIES, .25, 2.5),
                "No hand output cannot erase a tool-only output");
        check(ProceduralBlockHarvest.knownHandDominates(false, known, Items.SWEET_BERRIES, .25, 2.5),
                "Proven identical hand yield removes an unnecessary tool prerequisite");
        check(!ProceduralBlockHarvest.knownHandDominates(false, known, Items.APPLE, .25, 2.5),
                "A different output remains a distinct tool route");
        check(!ProceduralBlockHarvest.knownHandDominates(false, known, Items.SWEET_BERRIES, .5, 2.5),
                "A different occurrence chance remains a distinct tool route");
        check(!ProceduralBlockHarvest.knownHandDominates(false, known, Items.SWEET_BERRIES, .25, 3),
                "A different conditional count remains a distinct tool route");
        check(!ProceduralBlockHarvest.knownHandDominates(false, new ProceduralBlockHarvest.HarvestDrop(
                        Items.SWEET_BERRIES, .25, 2.5, 1, null, false, List.of()), Items.SWEET_BERRIES, .25, 2.5),
                "Equal estimated yields do not turn an uncertain hand route into proof");
        check(!ProceduralBlockHarvest.knownHandDominates(false, new ProceduralBlockHarvest.HarvestDrop(
                        Items.SWEET_BERRIES, .25, 2.5, 0, Items.IRON_PICKAXE, false, List.of()), Items.SWEET_BERRIES, .25, 2.5),
                "A route requiring another tool cannot stand in for empty hand");
        check(!ProceduralBlockHarvest.knownHandDominates(false, new ProceduralBlockHarvest.HarvestDrop(
                        Items.SWEET_BERRIES, .25, 2.5, 0, null, true, List.of()), Items.SWEET_BERRIES, .25, 2.5),
                "A Silk prerequisite cannot stand in for empty hand");
        boolean oreNeedsTool = Blocks.DIAMOND_ORE.defaultBlockState().requiresCorrectToolForDrops();
        var oreEstimate = new ProceduralBlockHarvest.HarvestDrop(Items.DIAMOND, 1, 1, 0, null, false, List.of());
        check(oreNeedsTool && !ProceduralBlockHarvest.knownHandDominates(oreNeedsTool, oreEstimate, Items.DIAMOND, 1, 1),
                "Native required-tool block state prevents pruning despite an ungated table-only hand estimate");
        boolean cropNeedsTool = Blocks.WHEAT.defaultBlockState().requiresCorrectToolForDrops();
        var cropEstimate = new ProceduralBlockHarvest.HarvestDrop(Items.WHEAT, 1, 1, 0, null, false, List.of());
        check(!cropNeedsTool && ProceduralBlockHarvest.knownHandDominates(cropNeedsTool, cropEstimate, Items.WHEAT, 1, 1),
                "Native hand-harvestable crop permits pruning an identical tool route");

        var hand = new ProceduralBlockLoot.Context("fixture:crop", Map.of(), Set.of(), "minecraft:air", Set.of(), Map.of());
        var tool = new ProceduralBlockLoot.Context(hand.blockId(), Map.of(), Set.of(), "minecraft:iron_pickaxe", Set.of(), Map.of());
        var table = json("""
                {"pools":[{"rolls":1,"entries":[{"type":"minecraft:item","name":"minecraft:sweet_berries",
                  "functions":[{"function":"minecraft:set_count","count":2},
                    {"function":"minecraft:set_count","count":4,"conditions":[
                      {"condition":"minecraft:match_tool","predicate":{"items":"minecraft:iron_pickaxe"}}]}]}]}]}
                """);
        var handDrop = blockDrops(table, hand).get("minecraft:sweet_berries");
        var toolDrop = blockDrops(table, tool).get("minecraft:sweet_berries");
        var observedHand = new ProceduralBlockHarvest.HarvestDrop(Items.SWEET_BERRIES, handDrop.chance(),
                handDrop.expectedCount() / handDrop.chance(), handDrop.unresolved(), null, false, handDrop.signals());
        check(handDrop.expectedCount() == 2 && toolDrop.expectedCount() == 4
                        && !ProceduralBlockHarvest.knownHandDominates(false, observedHand, Items.SWEET_BERRIES,
                        toolDrop.chance(), toolDrop.expectedCount() / toolDrop.chance()),
                "A modeled tool-conditioned yield increase survives hand-route pruning");

        var uncertainTable = json("""
                {"pools":[{"rolls":1,"conditions":[{"condition":"fixture:opaque_gate"}],
                  "entries":[{"type":"minecraft:item","name":"minecraft:sweet_berries"}]}]}
                """);
        var uncertainHand = blockDrops(uncertainTable, hand).get("minecraft:sweet_berries");
        var uncertainTool = blockDrops(uncertainTable, tool).get("minecraft:sweet_berries");
        var uncertainRoute = new ProceduralBlockHarvest.HarvestDrop(Items.SWEET_BERRIES, uncertainHand.chance(),
                uncertainHand.expectedCount() / uncertainHand.chance(), uncertainHand.unresolved(), null, false, uncertainHand.signals());
        check(uncertainHand.unresolved() > 0 && !ProceduralBlockHarvest.knownHandDominates(false, uncertainRoute,
                        Items.SWEET_BERRIES, uncertainTool.chance(), uncertainTool.expectedCount() / uncertainTool.chance()),
                "Matching opaque hand and tool estimates keep the tool route; uncertainty is not equivalence");
    }
    private static void installedDefinitionFixture(String path) {
        JsonObject fixture;
        try (var reader = java.nio.file.Files.newBufferedReader(java.nio.file.Path.of(path))) {
            fixture = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (java.io.IOException failure) { throw new AssertionError(failure); }
        var versions = fixture.getAsJsonObject("versions");
        List<RuntimeLootAudit.Modifier> modifiers = new ArrayList<>();
        for (var row : fixture.getAsJsonArray("modifiers")) {
            var data = row.getAsJsonObject(); String implementation = data.get("implementation").getAsString();
            var mode = BlockLootModifierAudit.auditedMode(implementation, mod -> versions.has(mod) ? versions.get(mod).getAsString() : null);
            Set<String> targets = new TreeSet<>();
            data.getAsJsonArray("targets").forEach(id -> targets.add(id.getAsString()));
            modifiers.add(new RuntimeLootAudit.Modifier(implementation, data.getAsJsonObject("definition"),
                    "offline jar-resource fixture; not effective native state", new BlockLootModifierAudit.Rule(mode, targets)));
        }
        var hand = new ProceduralBlockLoot.Context("minecraft:sweet_berry_bush", Map.of("age", "3"),
                Set.of("age"), "minecraft:air", Set.of(), Map.of());
        var unresolved = new BlockLootModifierAudit(modifiers).applicable("minecraft:blocks/sweet_berry_bush", hand, Set.of("minecraft:sweet_berries"))
                .stream().filter(m -> BlockLootModifierAudit.couldChangeOutput(m, "minecraft:sweet_berries")).toList();
        check(unresolved.isEmpty(), "Installed-definition berry fixture unresolved: " + unresolved);
        System.out.println("Jar-resource modifier fixture: " + modifiers.size() + " definitions; mature berry base drop preserved. Native loaded definitions/tags/rules still require rebuild.");
    }
    private static void cropReservation() {
        var hand = new ProceduralBlockLoot.Context("fixture:crop", Map.of(), Set.of(), "minecraft:air", Set.of(), Map.of());
        var table = JsonParser.parseString("""
                {"pools":[{"rolls":1,"entries":[{"type":"minecraft:item","name":"fixture:seed",
                "functions":[{"function":"minecraft:set_count","count":{"type":"minecraft:uniform","min":0,"max":3}}]}]}]}
                """).getAsJsonObject();
        var reserved = ProceduralBlockLoot.estimate(ProceduralBlockLoot.object(ProceduralBlockHarvest.plain(table)), hand,
                ignored -> Map.of(), ignored -> List.of(), "fixture:seed").get("fixture:seed");
        check(reserved.chance() == .5 && reserved.expectedCount() == .75,
                "Seed reservation must transform the native count distribution, not reuse gross harvest probability");
        var raw = Map.of("fixture:seed", new StartingBlockDrops.ExpectedDrop(.75, 1.5));
        var kept = Map.of("fixture:seed", new StartingBlockDrops.ExpectedDrop(reserved.chance(), reserved.expectedCount()));
        check(NativeCropRenewal.surplus("fixture:seed", raw, kept).get("fixture:seed").expectedCount() == .5,
                "Conditional seed surplus still accounts for failed replant outcomes");
        check(NativeCropRenewal.surplus("fixture:seed", Map.of(), kept).isEmpty(), "Harvest without replant evidence cannot renew");
        check(NativeCropRenewal.surplus("fixture:seed", Map.of("fixture:seed", new StartingBlockDrops.ExpectedDrop(1, 1)), kept).isEmpty(),
                "Exactly one replant seed creates no consumable surplus");
    }
    private static Map<String, ProceduralBlockLoot.Drop> blockDrops(JsonObject table, ProceduralBlockLoot.Context context) {
        return ProceduralBlockLoot.estimate(ProceduralBlockLoot.object(ProceduralBlockHarvest.plain(table)), context, ignored -> Map.of(), ignored -> List.of());
    }
    private static void biomeAndVariableRolls() {
        var ability = json("{\"condition\":\"neoforge:can_item_perform_ability\",\"ability\":\"fixture:action\"}");
        check(StartingBlockDrops.handAbilityConditions(ability, "21.1.250", id -> false).getAsJsonObject().get("chance").getAsInt() == 0,
                "A native empty-hand ability denial must survive conditional leaf projection");
        check(StartingBlockDrops.handAbilityConditions(ability, "21.1.250", id -> true).getAsJsonObject().get("chance").getAsInt() == 1,
                "Loaded positive abilities cannot be silently forced false");
        check(StartingBlockDrops.handAbilityConditions(ability, "unknown", id -> { throw new AssertionError("Unknown callback executed"); }).equals(ability),
                "Unknown native ability API must remain unmodeled");
        check(NativeTreeRenewal.singleSeedDrop(new StartingBlockDrops.ExpectedDrop(1 - .95, .05)),
                "Equivalent native Bernoulli seed chance and mean cannot fail due to floating subtraction");
        check(!NativeTreeRenewal.singleSeedDrop(new StartingBlockDrops.ExpectedDrop(.05, .1)),
                "Multiple seeds per event cannot pass the one-seed renewal contract");
        var table = json("""
                {"pools":[{"rolls":{"type":"minecraft:uniform","min":1,"max":3},
                "conditions":[{"condition":"minecraft:location_check","predicate":{"biomes":"#fixture:ocean"}}],
                "entries":[{"type":"minecraft:item","name":"minecraft:obsidian","weight":1,
                "functions":[{"function":"minecraft:set_count","count":{"type":"minecraft:uniform","min":4,"max":8}}]},
                {"type":"minecraft:empty","weight":1}]}]}
                """);
        var known = new ProceduralBlockLoot.Context("fixture:crate", Map.of(), Set.of(), "minecraft:air", Set.of(), Map.of(),
                "minecraft:deep_ocean", Set.of("fixture:ocean"));
        var drop = blockDrops(table, known).get("minecraft:obsidian");
        check(drop.unresolved() == 0 && Math.abs(drop.chance() - (.5 + .75 + .875) / 3) < 1e-12
                && Math.abs(drop.expectedCount() - 6) < 1e-12,
                "Biome-conditioned uniform rolls require the distribution's exact occurrence and expected quantity");
        var dry = new ProceduralBlockLoot.Context("fixture:crate", Map.of(), Set.of(), "minecraft:air", Set.of(), Map.of(),
                "minecraft:plains", Set.of());
        check(blockDrops(table, dry).isEmpty(), "A nonmatching loaded biome cannot provide ocean loot");
        var unknown = new ProceduralBlockLoot.Context("fixture:crate", Map.of(), Set.of(), "minecraft:air", Set.of(), Map.of());
        check(blockDrops(table, unknown).get("minecraft:obsidian").unresolved() > 0, "Registry samples cannot fabricate a biome");
        var poolCondition = table.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("conditions");
        var lootJs = json("{\"condition\":\"lootjs:match_biome\",\"biomes\":\"#fixture:ocean\"}");
        poolCondition.set(0, lootJs);
        var normalized = StartingBlockDrops.biomeConditions(table, "1.21.1-3.7.0").getAsJsonObject();
        check(blockDrops(normalized, known).get("minecraft:obsidian").equals(drop), "Audited loaded biome HolderSet changed its native probability/count");
        check(blockDrops(StartingBlockDrops.biomeConditions(table, "unknown").getAsJsonObject(), known).get("minecraft:obsidian").unresolved() > 0,
                "Unaudited modded biome behavior must remain unknown");
        lootJs.addProperty("extra", true);
        check(blockDrops(StartingBlockDrops.biomeConditions(table, "1.21.1-3.7.0").getAsJsonObject(), known).get("minecraft:obsidian").unresolved() > 0,
                "Unexpected biome predicate fields cannot disappear");
        poolCondition.set(0, normalized.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("conditions").get(0));
        table.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("conditions").get(0).getAsJsonObject().addProperty("offsetY", 1);
        check(blockDrops(table, known).get("minecraft:obsidian").unresolved() > 0, "Neighbor biome predicates need their own spatial witness");
    }
    private static RuntimeLootAudit.Modifier modifier(String implementation, String definition, BlockLootModifierAudit.Mode mode, Set<String> targets) {
        return new RuntimeLootAudit.Modifier(implementation, json(definition), "fixture unresolved effect", new BlockLootModifierAudit.Rule(mode, targets));
    }
    private static boolean affected(RuntimeLootAudit.Modifier modifier, String table, ProceduralBlockLoot.Context context, Set<String> outputs) {
        return !new BlockLootModifierAudit(List.of(modifier)).applicable(table, context, outputs).isEmpty();
    }
    /** Match native harvest projection: an applicable callback taints only outputs in its audited scope. */
    private static boolean outputAffected(RuntimeLootAudit.Modifier modifier, String table, ProceduralBlockLoot.Context context, Set<String> outputs) {
        return new BlockLootModifierAudit(List.of(modifier)).applicable(table, context, outputs).stream()
                .anyMatch(applicable -> outputs.stream().anyMatch(output -> BlockLootModifierAudit.couldChangeOutput(applicable, output)));
    }
    private static LootrPolicy.Rule rule(boolean all, Set<String> tables, Set<String> dimensions, Set<String> structures) {
        return new LootrPolicy.Rule(all, tables, Set.of(), dimensions, structures, 24000, true, true);
    }
    private static LootrPolicy policy(LootrPolicy.Rule refresh, LootrPolicy.Rule decay, Set<String> blocked, boolean team) {
        return new LootrPolicy(true, false, team, blocked, Set.of(), Set.of(), refresh, decay, List.of(), List.of());
    }
    private static SourceAvailability describe(LootrPolicy policy, boolean conversion, int unresolved) {
        return policy.describe(TABLE, "fixture:early_structure", List.of("minecraft:overworld"), conversion, true, .25, 2, unresolved);
    }
    private static ProceduralValuationIndex.ContainerLootSource source(SourceAvailability evidence) {
        return new ProceduralValuationIndex.ContainerLootSource(id(TABLE), .25, 2, 0, "test", ProceduralValuationResult.ProgressionBand.OVERWORLD,
                1, true, id("fixture:early_structure"), 1, List.of(), false, evidence);
    }
    private static ResourceLocation id(String value) { return ResourceLocation.parse(value); }
    private static JsonObject json(String value) { return JsonParser.parseString(value).getAsJsonObject(); }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); checks++; }
}
