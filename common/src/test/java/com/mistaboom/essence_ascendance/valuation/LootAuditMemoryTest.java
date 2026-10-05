package com.mistaboom.essence_ascendance.valuation;

import com.google.gson.*;
import java.lang.management.ManagementFactory;
import java.util.*;

/**
 * One generation-scoped audit across many distinct tables, in a 128 MiB test JVM.
 * No game bootstrap/server/mod callbacks or GC requests. The previous table cache
 * exhausts this heap because it retains a full opaque predicate tree per table.
 */
public final class LootAuditMemoryTest {
    private static final int TABLES = 30_000;
    private static final int BROAD_MODIFIERS = 16;
    private static long checks;

    public static void main(String[] args) {
        var hand = context("minecraft:air", "3");
        var tool = context("minecraft:iron_pickaxe", "3");
        var young = context("minecraft:air", "0");
        Set<String> outputs = Set.of("minecraft:sweet_berries");
        List<RuntimeLootAudit.Modifier> modifiers = new ArrayList<>();
        for (int i = 0; i < BROAD_MODIFIERS; i++) {
            var condition = new JsonObject();
            condition.addProperty("condition", "fixture:opaque_" + i);
            var data = new JsonArray();
            for (int j = 0; j < 128; j++) {
                var node = new JsonObject(); node.addProperty("id", "fixture:predicate_" + j);
                var range = new JsonObject(); range.addProperty("min", j); range.addProperty("max", j + 1);
                node.add("range", range); data.add(node);
            }
            condition.add("definition", data);
            var conditions = new JsonArray(); conditions.add(condition);
            var definition = new JsonObject(); definition.add("conditions", conditions);
            modifiers.add(new RuntimeLootAudit.Modifier("fixture.Broad" + i, definition, "opaque: retain uncertainty"));
        }
        var tableAndTool = modifier("""
                {"conditions":[{"condition":"minecraft:any_of","terms":[
                  {"condition":"neoforge:loot_table_id","loot_table_id":"fixture:blocks/0"},
                  {"condition":"minecraft:all_of","terms":[
                    {"condition":"minecraft:inverted","term":{"condition":"neoforge:loot_table_id","loot_table_id":"fixture:blocks/1"}},
                    {"condition":"minecraft:match_tool","predicate":{"items":"minecraft:iron_pickaxe"}}
                  ]}
                ]}]}
                """);
        var tableAndState = modifier("""
                {"conditions":[{"condition":"minecraft:all_of","terms":[
                  {"condition":"neoforge:loot_table_id","loot_table_id":"fixture:blocks/1"},
                  {"condition":"minecraft:block_state_property","block":"minecraft:sweet_berry_bush","properties":{"age":"3"}}
                ]}]}
                """);
        modifiers.add(tableAndTool); modifiers.add(tableAndState);
        String before = new Gson().toJson(modifiers);
        var audit = new BlockLootModifierAudit(modifiers);
        long start = System.nanoTime();
        for (int i = 0; i < TABLES; i++) {
            String table = "fixture:blocks/" + i;
            var a = audit.applicable(table, hand, outputs);
            check(a.size() == BROAD_MODIFIERS + (i == 0 || i == 1 ? 1 : 0), "bare-hand scope " + i);
            check(a.contains(tableAndTool) == (i == 0), "OR/inversion binds table " + i);
            check(a.contains(tableAndState) == (i == 1), "state condition binds table " + i);
            var b = audit.applicable(table, tool, outputs);
            check(b.size() == BROAD_MODIFIERS + 1, "same table still evaluates changed tool " + i);
            if (i == 1) check(audit.applicable(table, young, outputs).size() == BROAD_MODIFIERS, "same table still evaluates changed state");
        }
        check(audit.applicable("fixture:blocks/0", hand, outputs).contains(tableAndTool), "evicted table can be revisited");
        check(!audit.applicable("fixture:blocks/1", hand, outputs).contains(tableAndTool), "alternating tables never reuse stale binding");
        check(before.equals(new Gson().toJson(modifiers)), "shared definition trees are never mutated");
        var giant = new RuntimeLootAudit.Modifier("fixture.Giant", new JsonObject(), "whole-list conversion",
                new BlockLootModifierAudit.Rule(BlockLootModifierAudit.Mode.FIRST_OUTPUT_CONVERSION, Set.of("minecraft:cobblestone")));
        var changingOutputs = new BlockLootModifierAudit(List.of(giant));
        check(changingOutputs.applicable("fixture:blocks/same", hand, outputs).isEmpty(), "non-target prefix preserved");
        check(!changingOutputs.applicable("fixture:blocks/same", hand, Set.of("minecraft:cobblestone")).isEmpty(), "same table output set remains dynamic");
        check(changingOutputs.applicable("fixture:blocks/same", hand, outputs).isEmpty(), "results never contaminate cached candidates");
        System.out.println("LootAuditMemoryTest: " + checks + " checks PASS; " + TABLES
                + " tables, " + modifiers.size() + " modifiers, 60000+ contexts; maxHeapMiB="
                + Runtime.getRuntime().maxMemory() / 1048576 + "; elapsedMs=" + (System.nanoTime() - start) / 1_000_000);
        for (var pool : ManagementFactory.getMemoryPoolMXBeans())
            if (pool.getType() == java.lang.management.MemoryType.HEAP)
                System.out.println("Heap pool peak " + pool.getName() + "=" + pool.getPeakUsage().getUsed() / 1048576 + " MiB");
        for (var gc : ManagementFactory.getGarbageCollectorMXBeans())
            System.out.println("Test JVM GC " + gc.getName() + ": " + gc.getCollectionCount() + " collections / " + gc.getCollectionTime() + " ms");
    }
    private static RuntimeLootAudit.Modifier modifier(String json) {
        return new RuntimeLootAudit.Modifier("fixture.Scoped", JsonParser.parseString(json).getAsJsonObject(), "unknown output");
    }
    private static ProceduralBlockLoot.Context context(String tool, String age) {
        return new ProceduralBlockLoot.Context("minecraft:sweet_berry_bush", Map.of("age", age),
                Set.of("age"), tool, Set.of(), Map.of());
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
