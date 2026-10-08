package com.mistaboom.essence_ascendance.valuation;

import com.google.gson.JsonParser;
import java.util.*;

public final class ExDeorumManualAccessTest {
    private static int checks;
    public static void main(String[] args) {
        var chance = ExDeorumManualAccess.numberYield(JsonParser.parseString("{\"type\":\"minecraft:binomial\",\"n\":7,\"p\":0.6}"));
        check(Math.abs(chance.expected() - 4.2) < 1e-12 && Math.abs(chance.chance() - (1 - Math.pow(.4, 7))) < 1e-12,
                "Multiple trials retain actual expected count and non-guaranteed occurrence");
        var uniform = ExDeorumManualAccess.numberYield(JsonParser.parseString("{\"type\":\"minecraft:uniform\",\"min\":0,\"max\":3}"));
        check(uniform.chance() == .75 && uniform.expected() == 1.5, "Inclusive integer uniform rolls preserve zero outcomes");
        for (String value : List.of("0", "-1", "257", "{\"type\":\"fixture:random\",\"value\":1}",
                "{\"type\":\"minecraft:binomial\",\"n\":1,\"p\":0}", "{\"type\":\"minecraft:binomial\",\"n\":1,\"p\":2}",
                "{\"type\":\"minecraft:uniform\",\"min\":2,\"max\":1}", "{\"type\":\"minecraft:uniform\",\"min\":-1,\"max\":3}"))
            check(ExDeorumManualAccess.numberYield(JsonParser.parseString(value)) == null, "Unsupported/invalid counts stay unproven: " + value);
        for (String value : List.of("{\"item\":\"minecraft:dirt\"}", "[{\"tag\":\"minecraft:planks\"},{\"item\":\"minecraft:stick\"}]"))
            check(ExDeorumManualAccess.simpleDefinition(JsonParser.parseString(value)), "Loaded plain alternatives can be inspected");
        for (String value : List.of("[]", "{\"item\":\"minecraft:dirt\",\"chance\":1}", "{\"type\":\"fixture:predicate\",\"item\":\"minecraft:dirt\"}"))
            check(!ExDeorumManualAccess.simpleDefinition(JsonParser.parseString(value)), "Custom or additional predicates cannot disappear");
        check(BlockLootModifierAudit.auditedMode("thedarkcolour.exdeorum.loot.CrookLootModifier", m -> "3.12") == BlockLootModifierAudit.Mode.APPEND_ONLY,
                "Audited crook callback preserves base prefix");
        check(BlockLootModifierAudit.auditedMode("thedarkcolour.exdeorum.loot.CrookLootModifier", m -> "3.13") == BlockLootModifierAudit.Mode.UNKNOWN,
                "Unknown crook release is not admitted");
        var context = new ProceduralBlockLoot.Context("minecraft:oak_leaves", Map.of(), Set.of(), "fixture:crook", Set.of("exdeorum:crooks"), Map.of());
        check(!ProceduralBlockLoot.conditionsProven(List.of(Map.of("condition", "fixture:callback")), context), "Unknown runtime condition cannot authorize an added reward");
        check(!ProceduralBlockLoot.conditionsProven(List.of(Map.of("condition", "minecraft:random_chance", "chance", .5)), context), "Partial modifier gate is not promoted to certainty");
        check(ProceduralBlockLoot.conditionsProven(List.of(Map.of("condition", "minecraft:random_chance", "chance", 1)), context), "Proven effective gate retained");
        var componentGate = List.of(Map.of("condition", "minecraft:match_tool", "predicate", Map.of("components", Map.of("fixture:smelt", Map.of()))));
        check(ProceduralBlockLoot.conditionsMayApply(componentGate, context), "An unspecified tool component map remains unknown");
        var plain = context.withToolFacts(new NativeToolFacts(true, Set.of(), Set.of(), Set.of(), Set.of()));
        check(!ProceduralBlockLoot.conditionsMayApply(componentGate, plain), "A captured absent required component disproves the gate");
        var modified = context.withToolFacts(new NativeToolFacts(true, Set.of("fixture:smelt"), Set.of(), Set.of(), Set.of()));
        check(ProceduralBlockLoot.conditionsMayApply(componentGate, modified), "A present custom component remains guarded");
        var modifier = new RuntimeLootAudit.Modifier("fixture:callback", JsonParser.parseString("{\"conditions\":[]}").getAsJsonObject(), "unknown");
        var audit = new BlockLootModifierAudit(List.of(modifier));
        var inactive = context.withToolFacts(new NativeToolFacts(true, Set.of(), Set.of("fixture:callback"), Set.of(), Set.of()));
        check(audit.applicable("minecraft:blocks/oak_leaves", inactive, Set.of("fixture:reward")).isEmpty(), "Captured negative callback prerequisite excludes only that callback");
        check(!audit.applicable("minecraft:blocks/oak_leaves", context, Set.of("fixture:reward")).isEmpty(), "Cached exact tool facts cannot leak into an unspecified tool");
        passiveContext();
        for (String version : List.of("21.1.248", "21.1.250", "21.1.251"))
            check(BlockLootModifierAudit.auditedMode("net.neoforged.neoforge.common.loot.AddTableLootModifier", m -> version)
                    == BlockLootModifierAudit.Mode.APPEND_ONLY, "Raw native table injection preserves existing drops");
        check(BlockLootModifierAudit.auditedMode("net.neoforged.neoforge.common.loot.AddTableLootModifier", m -> "21.1.252")
                == BlockLootModifierAudit.Mode.UNKNOWN, "Unaudited table injector stays unknown");
        check(BlockLootModifierAudit.auditedMode("com.klikli_dev.occultism.loot.AddItemModifier", m -> "1.224.4")
                == BlockLootModifierAudit.Mode.APPEND_ONLY, "Installed item append preserves existing drops");
        check(BlockLootModifierAudit.auditedMode("com.klikli_dev.occultism.loot.AddItemModifier", m -> "1.224.2")
                == BlockLootModifierAudit.Mode.APPEND_ONLY, "Identical installed Occultism callback bytecode preserves drops");
        for (String version : List.of("1.21.1-3.0.16", "1.21.1-3.0.22", "1.21.1-3.6.8"))
            check(BlockLootModifierAudit.auditedMode("net.mehvahdjukaar.moonlight.core.misc.platform.ModLootModifiers$AddItemModifier", m -> version)
                    == BlockLootModifierAudit.Mode.APPEND_ONLY, "Identical installed Moonlight callback bytecode preserves drops");
        check(BlockLootModifierAudit.auditedMode("vectorwing.farmersdelight.common.loot.modifier.AddItemModifier", m -> "1.2.11")
                == BlockLootModifierAudit.Mode.APPEND_ONLY, "Audited older Farmer's Delight callback splits only newly added stacks");
        System.out.println("ExDeorumManualAccessTest: " + checks + " checks PASS; synthetic contracts, not native acceptance");
    }
    private static void passiveContext() {
        check(NativeToolFacts.lambdaOwner(NestedOwner.predicate()).equals(NestedOwner.class.getName()), "Audited nested lambda owner must not collapse to its outer nest host");
        var context = new ProceduralBlockLoot.Context("minecraft:air", Map.of(), Set.of(), "minecraft:air", Set.of(), Map.of());
        for (String condition : List.of("{\"condition\":\"minecraft:block_state_property\",\"block\":\"minecraft:air\"}",
                "{\"condition\":\"minecraft:match_tool\",\"predicate\":{}}",
                "{\"condition\":\"minecraft:entity_properties\",\"entity\":\"this\",\"predicate\":{\"type\":\"minecraft:pig\"}}",
                "{\"condition\":\"minecraft:entity_properties\",\"entity\":\"this\",\"predicate\":{\"flags\":{\"is_on_fire\":true}}}",
                "{\"condition\":\"minecraft:damage_source_properties\",\"predicate\":{\"tags\":[{\"id\":\"fixture:laser\",\"expected\":true}]}}")) {
            var projected = NativePassiveDrops.project(JsonParser.parseString("[" + condition + "]"), "minecraft:cow", Set.of("minecraft:is_player_attack"));
            check(!ProceduralBlockLoot.conditionsMayApply(ProceduralBlockHarvest.plain(projected), context), "Absent native entity parameter/state must disprove modifier: " + condition);
        }
        var unknown = JsonParser.parseString("[{\"condition\":\"fixture:callback\"}]");
        check(ProceduralBlockLoot.conditionsMayApply(ProceduralBlockHarvest.plain(NativePassiveDrops.project(unknown, "minecraft:cow")), context), "Unknown native hunting callback retained");
        var killed = JsonParser.parseString("{\"conditions\":[{\"condition\":\"minecraft:killed_by_player\"}]}").getAsJsonObject();
        var modifier = new RuntimeLootAudit.Modifier("fixture:replacement", killed, "unknown");
        check(new BlockLootModifierAudit(List.of(modifier)).applicable("minecraft:blocks/stone", context, Set.of("minecraft:leather")).isEmpty(), "Block scenario has no player-kill parameter");
        check(!new BlockLootModifierAudit(List.of(modifier), v -> NativePassiveDrops.conditions(v, "minecraft:cow"))
                .applicable("minecraft:entities/cow", context, Set.of("minecraft:leather")).isEmpty(), "Hunting cannot inherit block scenario's false kill gate");
        try (var stream = ExDeorumManualAccessTest.class.getResourceAsStream("/data/minecraft/loot_table/entities/cow.json")) {
            var nativeTable = JsonParser.parseReader(new java.io.InputStreamReader(Objects.requireNonNull(stream)));
            var drops = ProceduralBlockLoot.estimate(ProceduralBlockLoot.object(ProceduralBlockHarvest.plain(NativePassiveDrops.project(nativeTable, "minecraft:cow"))),
                    context, ignored -> Map.of(), ignored -> List.of(), "");
            var leather = drops.get("minecraft:leather");
            check(leather.unresolved() == 0 && Math.abs(leather.chance() - 2.0 / 3) < 1e-12 && leather.expectedCount() == 1,
                    "Native cow distribution preserves zero-count outcomes and no Looting gain");
        } catch (java.io.IOException failure) { throw new AssertionError(failure); }
    }
    private static final class NestedOwner {
        static java.util.function.Predicate<String> predicate() { return String::isEmpty; }
    }
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
}
