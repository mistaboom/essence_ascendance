package com.mistaboom.essence_ascendance.valuation;

import com.google.gson.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import java.util.*;
import java.util.function.Function;

/** Definition-only bounds on base block drops; never invokes a loot modifier, predicate or player API. */
final class BlockLootModifierAudit {
    enum Mode { UNKNOWN, APPEND_ONLY, EMPTY_DEFINITIONS, NONEMPTY_TOOL, OUTPUT_ITEMS, BLOCK_ORES, TABLE_LIST, FIRST_PATTERN, TOOL_CONDITION, FIRST_OUTPUT_CONVERSION }
    record Rule(Mode mode, Set<String> targets) {
        static final Rule UNKNOWN = new Rule(Mode.UNKNOWN, Set.of());
        Rule { targets = Collections.unmodifiableSortedSet(new TreeSet<>(targets)); }
    }
    private record Contract(String mod, String version, Mode mode) { }
    private static final Map<String, Contract> CONTRACTS = contracts();
    private static Map<String, Contract> contracts() {
        Map<String, Contract> out = new HashMap<>();
        add(out, "aether", "1.5.10", Mode.APPEND_ONLY, "com.aetherteam.aether.loot.modifiers.", "DoubleDropsModifier", "PigDropsModifier");
        add(out, "aether", "1.5.10", Mode.OUTPUT_ITEMS, "com.aetherteam.aether.loot.modifiers.", "GlovesLootModifier");
        add(out, "apotheosis", "8.8.0", Mode.APPEND_ONLY, "dev.shadowsoffire.apotheosis.loot.modifiers.", "AffixLootModifier", "GemLootModifier");
        add(out, "apotheosis", "8.8.0", Mode.NONEMPTY_TOOL, "dev.shadowsoffire.apotheosis.loot.modifiers.", "AffixHookLootModifier");
        add(out, "apotheosis", "8.8.0", Mode.FIRST_PATTERN, "dev.shadowsoffire.apotheosis.loot.modifiers.", "AffixConvertLootModifier");
        add(out, "apothic_enchanting", "1.6.2", Mode.APPEND_ONLY, "dev.shadowsoffire.apothic_enchanting.objects.", "WardenLootModifier");
        add(out, "forbidden_arcanus", "2.6.1", Mode.APPEND_ONLY, "com.stal111.forbidden_arcanus.common.loot.", "BlacksmithGavelLootModifier", "MagicalFarmlandLootModifier");
        add(out, "irons_jewelry", "1.21.1-2.0.2", Mode.APPEND_ONLY, "io.redspace.ironsjewelry.loot.", "InjectJewelryLootModifier");
        add(out, "immersiveengineering", "12.4.2-194", Mode.APPEND_ONLY, "blusunrize.immersiveengineering.common.util.loot.", "AddDropModifier");
        add(out, "neovitae", "1.1.26", Mode.NONEMPTY_TOOL, "com.breakinblocks.neovitae.common.loot.GlobalLootModifiers$", "SmeltingModifier", "VoidingModifier");
        add(out, "relics", "0.12.8", Mode.APPEND_ONLY, "it.hurts.sskirillss.relics.level.", "RelicLootModifier");
        add(out, "relics", "0.12.8", Mode.BLOCK_ORES, "it.hurts.sskirillss.relics.level.", "GreedLootModifier");
        add(out, "repurposed_structures", "7.5.22+1.21.1-neoforge", Mode.APPEND_ONLY,
                "com.telepathicgrunt.repurposedstructures.misc.neoforge.lootmanager.", "StructureModdedLootImporterApplier");
        add(out, "rootsclassic", "1.21.1-1.5.10", Mode.APPEND_ONLY, "elucent.rootsclassic.lootmodifiers.", "DropModifier$BlockDropModifier");
        add(out, "silentgear", "4.2.1.1", Mode.NONEMPTY_TOOL, "net.silentchaos512.gear.loot.modifier.", "BonusDropsTraitLootModifier");
        add(out, "silentgear", "4.2.1.1", Mode.TOOL_CONDITION, "net.silentchaos512.gear.loot.modifier.", "MagmaticTraitLootModifier");
        add(out, "forbidden_arcanus", "2.6.1", Mode.TOOL_CONDITION, "com.stal111.forbidden_arcanus.common.loot.", "FieryLootModifier");
        add(out, "twilightforest", "4.8.3345", Mode.FIRST_OUTPUT_CONVERSION, "twilightforest.loot.modifiers.", "GiantToolGroupingModifier");
        add(out, "supplementaries", "1.21.1-3.9.9", Mode.OUTPUT_ITEMS, "net.mehvahdjukaar.supplementaries.platform.", "ReplaceRopeByConfigModifier");
        add(out, "sushigocrafting", "0.6.6", Mode.OUTPUT_ITEMS, "com.buuz135.sushigocrafting.loot.", "ItemAmountLootModifier");
        add(out, "the_bumblezone", "7.16.1+1.21.1-neoforge", Mode.APPEND_ONLY, "com.telepathicgrunt.the_bumblezone.loot.neoforge.", "BeeStingerLootApplier");
        add(out, "wstweaks", "10.1.1", Mode.APPEND_ONLY, "dev.shadowsoffire.wstweaks.", "WSTLootModifier");
        add(out, "cyclopscore", "1.30.0", Mode.TABLE_LIST, "org.cyclops.cyclopscore.loot.modifier.", "LootModifierInjectItem");
        add(out, "evilcraft", "1.2.96", Mode.TABLE_LIST, "org.cyclops.evilcraft.loot.modifier.", "LootModifierInjectBroom", "LootModifierInjectBoxOfEternalClosure");
        add(out, "incontrol", "1.21-10.3.0", Mode.EMPTY_DEFINITIONS, "mcjty.incontrol.events.", "InControlLootModifier");
        return Map.copyOf(out);
    }
    private static void add(Map<String, Contract> out, String mod, String version, Mode mode, String prefix, String... classes) {
        for (String type : classes) out.put(prefix + type, new Contract(mod, version, mode));
    }
    static Mode auditedMode(String implementation, Function<String, String> versions) {
        Contract contract = CONTRACTS.get(implementation);
        return contract != null && contract.version().equals(versions.apply(contract.mod())) ? contract.mode() : Mode.UNKNOWN;
    }
    static Rule captureRule(String implementation, GenerationDataSnapshot inputs) {
        Mode mode = auditedMode(implementation, inputs::installedVersion);
        Set<String> targets = new TreeSet<>();
        if (mode == Mode.EMPTY_DEFINITIONS) {
            // This is the loaded rule-definition list, not filtered world/player state. Never evaluate a rule.
            try {
                var field = Class.forName("mcjty.incontrol.rules.RulesManager").getDeclaredField("lootRules");
                field.setAccessible(true);
                if (!((Collection<?>) field.get(null)).isEmpty()) return Rule.UNKNOWN;
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Cannot inspect loaded loot rule definitions", failure); }
        } else if (mode == Mode.FIRST_OUTPUT_CONVERSION) {
            // Loaded conversion definitions only; never read the player's mining attachment.
            try {
                var conversions = (Map<?, ?>) Class.forName(implementation).getField("CONVERSIONS").get(null);
                for (var block : conversions.keySet()) targets.add(BuiltInRegistries.ITEM.getKey(((net.minecraft.world.level.block.Block) block).asItem()).toString());
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Cannot inspect loaded giant-block conversions", failure); }
        } else if (mode == Mode.BLOCK_ORES) {
            BuiltInRegistries.BLOCK.getTag(TagKey.create(Registries.BLOCK, ResourceLocation.parse("c:ores")))
                    .ifPresent(tag -> tag.forEach(holder -> targets.add(holder.unwrapKey().orElseThrow().location().toString())));
        } else if (mode == Mode.OUTPUT_ITEMS) {
            if (implementation.endsWith("ReplaceRopeByConfigModifier")) {
                targets.addAll(inputs.itemTags().getOrDefault("supplementaries:ropes", List.of()));
            } else {
                String base = implementation.endsWith("GlovesLootModifier") ? "net.minecraft.world.item.ArmorItem"
                        : "com.buuz135.sushigocrafting.item.AmountItem";
                for (var item : inputs.items()) for (Class<?> type = item.getClass(); type != null; type = type.getSuperclass())
                    if (type.getName().equals(base)) { targets.add(BuiltInRegistries.ITEM.getKey(item).toString()); break; }
            }
        }
        return new Rule(mode, targets);
    }

    private interface ConditionPlan { Object forTable(String table); }
    private record SharedConditions(Object value) implements ConditionPlan {
        @Override public Object forTable(String table) { return value; }
    }
    private record Prepared(RuntimeLootAudit.Modifier modifier, ConditionPlan conditions) { }
    private record Candidate(RuntimeLootAudit.Modifier modifier, Object conditions) { }
    private final List<Prepared> modifiers;
    private String cachedTable;
    private List<Candidate> cachedCandidates = List.of();
    BlockLootModifierAudit(List<RuntimeLootAudit.Modifier> modifiers) {
        // Broad predicates can contain large entity/component definitions. Normalize each
        // once, rather than retaining a copy for every block table in the pack.
        this.modifiers = modifiers.stream()
                .filter(m -> m.blockRule().mode() != Mode.APPEND_ONLY && m.blockRule().mode() != Mode.EMPTY_DEFINITIONS)
                .map(m -> new Prepared(m, conditionPlan(ProceduralBlockHarvest.plain(m.conditionsEnforced()
                        ? blockConditions(m.definition().get("conditions"), m) : JsonNull.INSTANCE))))
                .toList();
    }

    private List<Candidate> candidates(String table) {
        // Harvest discovery visits all states/tools of one block consecutively. Retaining
        // older table lists provides no benefit and keeps their bound conditions alive.
        if (!table.equals(cachedTable)) {
            cachedCandidates = modifiers.stream().filter(p -> p.modifier().mayAffect(table))
                    .filter(p -> tableRuleMayApply(p.modifier(), table))
                    .map(p -> new Candidate(p.modifier(), p.conditions().forTable(table))).toList();
            cachedTable = table;
        }
        return cachedCandidates;
    }

    /** Only table-id terms and their ancestors need a per-table value; all other data is shared. */
    private static ConditionPlan conditionPlan(Object raw) {
        if (raw instanceof List<?> values) {
            List<ConditionPlan> children = values.stream().map(BlockLootModifierAudit::conditionPlan).toList();
            if (children.stream().allMatch(SharedConditions.class::isInstance)) return new SharedConditions(raw);
            return table -> children.stream().map(child -> child.forTable(table)).toList();
        }
        if (!(raw instanceof Map<?, ?>)) return new SharedConditions(raw);
        Map<String, Object> value = ProceduralBlockLoot.object(raw);
        if ("neoforge:loot_table_id".equals(value.get("condition")) && value.get("loot_table_id") instanceof String expected)
            return table -> table.equals(expected) ? TRUE_CONDITION : FALSE_CONDITION;
        ConditionPlan terms = conditionPlan(value.get("terms")), term = conditionPlan(value.get("term"));
        if (terms instanceof SharedConditions && term instanceof SharedConditions) return new SharedConditions(raw);
        return table -> {
            Map<String, Object> bound = new LinkedHashMap<>(value);
            if (!(terms instanceof SharedConditions)) bound.put("terms", terms.forTable(table));
            if (!(term instanceof SharedConditions)) bound.put("term", term.forTable(table));
            return bound;
        };
    }
    private static final Map<String, Object> TRUE_CONDITION = Map.of("condition", "minecraft:random_chance", "chance", 1.0);
    private static final Map<String, Object> FALSE_CONDITION = Map.of("condition", "minecraft:random_chance", "chance", 0.0);

    /** Query once per concrete harvest, retaining only callbacks that could change an existing output. */
    List<RuntimeLootAudit.Modifier> applicable(String table, ProceduralBlockLoot.Context context, Set<String> outputs) {
        List<RuntimeLootAudit.Modifier> result = new ArrayList<>();
        for (var candidate : candidates(table)) {
            var rule = candidate.modifier().blockRule();
            if (rule.mode() == Mode.NONEMPTY_TOOL && context.toolId().equals("minecraft:air")) continue;
            if (rule.mode() == Mode.BLOCK_ORES && ResourceLocation.parse(table).getPath().startsWith("blocks/")
                    && !rule.targets().contains(context.blockId())) continue;
            if (ProceduralBlockLoot.conditionsMayApply(candidate.conditions(), context)) result.add(candidate.modifier());
        }
        // The audited append contracts retain the original prefix. A first-stack whole-list
        // replacement cannot erase any base output when all possible base outputs miss its
        // conversion set and no other applicable callback can change that prefix.
        boolean otherChanges = result.stream().filter(m -> m.blockRule().mode() != Mode.FIRST_OUTPUT_CONVERSION)
                .anyMatch(m -> outputs.stream().anyMatch(id -> couldChangeOutput(m, id)));
        if (!otherChanges) result.removeIf(m -> m.blockRule().mode() == Mode.FIRST_OUTPUT_CONVERSION
                && Collections.disjoint(m.blockRule().targets(), outputs));
        return List.copyOf(result);
    }
    static boolean couldChangeOutput(RuntimeLootAudit.Modifier modifier, String item) {
        return modifier.blockRule().mode() != Mode.OUTPUT_ITEMS || modifier.blockRule().targets().contains(item);
    }
    private static boolean tableRuleMayApply(RuntimeLootAudit.Modifier m, String table) {
        if (m.blockRule().mode() == Mode.TABLE_LIST && m.definition().has("loot_tables"))
            return m.definition().getAsJsonArray("loot_tables").asList().stream().anyMatch(v -> table.equals(v.getAsString()));
        if (m.blockRule().mode() == Mode.FIRST_PATTERN && m.definition().has("entries")) {
            var id = ResourceLocation.parse(table);
            for (var value : m.definition().getAsJsonArray("entries")) {
                var entry = value.getAsJsonObject(); var pattern = entry.getAsJsonObject("pattern");
                if ((!pattern.has("domain") || pattern.get("domain").getAsString().equals(id.getNamespace()))
                        && id.getPath().matches(pattern.get("path_regex").getAsString()))
                    return !entry.has("chance") || entry.get("chance").getAsDouble() > 0;
            }
            return false;
        }
        return true;
    }
    private static JsonElement blockConditions(JsonElement value, RuntimeLootAudit.Modifier modifier) {
        if (value == null) return JsonNull.INSTANCE;
        if (value.isJsonArray()) { var out = new JsonArray(); value.getAsJsonArray().forEach(v -> out.add(blockConditions(v, modifier))); return out; }
        if (!value.isJsonObject()) return value;
        var c = value.getAsJsonObject(); String type = c.has("condition") ? c.get("condition").getAsString() : "";
        if (modifier.blockRule().mode() == Mode.TOOL_CONDITION) {
            boolean trait = modifier.implementation().endsWith("MagmaticTraitLootModifier") && type.equals("silentgear:has_trait");
            var predicate = type.equals("minecraft:match_tool") ? c.getAsJsonObject("predicate") : null;
            boolean component = modifier.implementation().endsWith("FieryLootModifier") && predicate != null
                    && predicate.has("predicates") && predicate.getAsJsonObject("predicates").has("forbidden_arcanus:modifier");
            if (trait || component) {
                // A necessary condition, not a sufficient one: retain the opaque original term.
                var both = new JsonObject(); both.addProperty("condition", "minecraft:all_of");
                var terms = new JsonArray(); terms.add(c.deepCopy());
                terms.add(JsonParser.parseString("{\"condition\":\"minecraft:match_tool\",\"predicate\":{\"count\":{\"min\":1}}}"));
                both.add("terms", terms); return both;
            }
        }
        // A player block-break context has no damage source or last-damage-player parameter.
        if (type.equals("minecraft:damage_source_properties") || type.equals("minecraft:killed_by_player")) return constant(false);
        if (type.equals("minecraft:entity_properties") && c.has("entity")) {
            String entity = c.get("entity").getAsString();
            if (!entity.equals("this")) return constant(false);
            var predicate = c.getAsJsonObject("predicate");
            if (predicate != null && predicate.has("type") && predicate.get("type").isJsonPrimitive()) {
                String expected = predicate.get("type").getAsString();
                if (!expected.startsWith("#") && !expected.equals("minecraft:player")) return constant(false);
            }
        }
        var copy = c.deepCopy();
        if (copy.has("terms")) copy.add("terms", blockConditions(copy.get("terms"), modifier));
        if (copy.has("term")) copy.add("term", blockConditions(copy.get("term"), modifier));
        return copy;
    }
    private static JsonObject constant(boolean value) { var c = new JsonObject(); c.addProperty("condition", "minecraft:random_chance"); c.addProperty("chance", value ? 1 : 0); return c; }
}
