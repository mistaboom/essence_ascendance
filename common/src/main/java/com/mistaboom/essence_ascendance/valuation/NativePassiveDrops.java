package com.mistaboom.essence_ascendance.valuation;

import com.google.gson.*;
import com.mistaboom.essence_ascendance.balance.engine.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import java.util.*;

/** Conditional ordinary passive hunting, with actual biome membership, native
 * spawn predicate, a natural or paid habitat, and exact bare-handed loot scope.
 * No entity is spawned, no callback is applied, and no encounter rate is inferred. */
public final class NativePassiveDrops {
    private NativePassiveDrops() { }
    private static final String PROVIDER = "native_passive_hunting";
    public static Map<String, ResourceEvidence> enrich(PackEvidenceContext context, Map<String, ResourceEvidence> original, EvidenceSink sink) {
        var staged = sink.staged();
        var attempt = OptionalIntegration.attempt(PROVIDER, "native habitat and unenchanted drops", () -> {
            try { return collect(context, original, staged); }
            catch (ReflectiveOperationException failure) { throw new IllegalStateException("Native passive acquisition contract changed", failure); }
        });
        if (!attempt.succeeded()) { sink.warn(PROVIDER + " READ_FAILED: " + attempt.failure()); return original; }
        sink.merge(staged); return attempt.value().orElseThrow();
    }
    private static Map<String, ResourceEvidence> collect(PackEvidenceContext context, Map<String, ResourceEvidence> original, EvidenceSink sink) throws ReflectiveOperationException {
        var inputs = context.inputs(); var level = context.server().overworld();
        if (!context.server().isSpawningAnimals() || !level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING) || !level.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)
                || !level.dimensionType().hasSkyLight() || !Blocks.GRASS_BLOCK.defaultBlockState().is(BlockTags.ANIMALS_SPAWNABLE_ON)) return original;
        var access = ConfiguredRecipeAccess.nativeCrafting(context, original);
        ConfigurationAccess.Proof habitat = null;
        // This witness describes terrain, not a silk-touch grass-block item.
        var natural = ProceduralNaturalBlockIndex.startingWorld(inputs);
        if (natural.contains(ResourceLocation.parse("minecraft:grass_block"))) {
            var source = new AcquisitionSource("minecraft:grass_block", AcquisitionSource.Kind.WORLD_GENERATION,
                    ProgressionBand.ENTRY, 1, false, false, 0, .76, List.of(),
                    "Actual starting Overworld grass: " + natural.signals(ResourceLocation.parse("minecraft:grass_block")));
            habitat = new ConfigurationAccess.Proof(new CompetitiveCapabilities.Placement(ProgressionBand.ENTRY, true,
                    source.confidence(), List.of(source)), List.of("Loaded starting-world natural grass terrain; conditional suitable open daylight patch"), List.of());
        }
        if (habitat == null && "3.12".equals(inputs.installedVersion("exdeorum"))) {
            for (var item : inputs.items()) if (item.getClass().getName().equals("thedarkcolour.exdeorum.item.GrassSpreaderItem")) {
                var supplier = (java.util.function.Supplier<?>)field(item, "grassState");
                if (!supplier.getClass().getNestHost().getName().equals("thedarkcolour.exdeorum.registry.EItems")) continue;
                if (supplier.get() != Blocks.GRASS_BLOCK.defaultBlockState()) continue;
                @SuppressWarnings("unchecked") var tag = (net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block>)field(item, "spreadableStates");
                if (!Blocks.DIRT.defaultBlockState().is(tag)) continue;
                var proof = access.requireFinite(List.of(NativeConsumables.request("minecraft:dirt", 64),
                        NativeConsumables.request(BuiltInRegistries.ITEM.getKey(item).toString(), 64))).access();
                if (proof.placement().reachable()) { habitat = proof; break; }
            }
        }
        if (habitat == null) { sink.warn(PROVIDER + ": native grass habitat or joint 64-block spreader setup unproven"); return original; }
        var result = new TreeMap<>(original); var diagnostics = new TreeMap<String, String>();
        // These native registrations all delegate to Animal.checkAnimalSpawnRules.
        for (var entity : List.of(EntityType.COW, EntityType.PIG, EntityType.SHEEP, EntityType.CHICKEN)) {
            String id = BuiltInRegistries.ENTITY_TYPE.getKey(entity).toString();
            String spawnStatus = nativeSpawn(entity, inputs.installedVersion("neoforge"));
            if (!spawnStatus.isEmpty()) { diagnostics.put(id, spawnStatus); continue; }
            var biomes = level.getChunkSource().getGenerator().getBiomeSource().possibleBiomes().stream()
                    .filter(b -> b.value().getMobSettings().getMobs(MobCategory.CREATURE).unwrap().stream()
                            .anyMatch(s -> s.type == entity && s.getWeight().asInt() > 0))
                    .map(b -> b.unwrapKey().orElseThrow().location().toString()).sorted().toList();
            if (biomes.isEmpty()) { diagnostics.put(id, "No positive loaded creature spawn in an actual Overworld biome"); continue; }
            var table = ResourceLocation.parse("minecraft:entities/" + ResourceLocation.parse(id).getPath());
            inputs.retainLootSemantics(table);
            var lootContext = new ProceduralBlockLoot.Context("minecraft:air", Map.of(), Set.of(), "minecraft:air", Set.of(), Map.of())
                    .withToolFacts(NativeToolFacts.capture(inputs, ItemStack.EMPTY).withoutAccessories(inputs::installedVersion));
            var damageTags = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE)
                    .getHolderOrThrow(net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK).tags().map(t -> t.location().toString()).collect(java.util.stream.Collectors.toSet());
            var audit = new BlockLootModifierAudit(inputs.runtimeLootModifiers(), value -> project(value, id, damageTags));
            var cache = new HashMap<String, Map<String, Object>>();
            java.util.function.Function<String, Map<String, Object>> read = key -> cache.computeIfAbsent(key, k -> {
                var definition = inputs.definition("loot_evidence", ResourceLocation.parse(k));
                return definition == null ? Map.of() : ProceduralBlockLoot.object(ProceduralBlockHarvest.plain(project(definition, id, damageTags)));
            });
            var drops = ProceduralBlockLoot.estimate(read.apply(table.toString()), lootContext, read,
                    key -> inputs.itemTag(ResourceLocation.parse(key)), "");
            var modifiers = audit.applicable(table.toString(), lootContext, drops.keySet());
            for (var entry : drops.entrySet()) {
                var drop = entry.getValue(); var resource = result.get(entry.getKey());
                var blockers = modifiers.stream().filter(m -> BlockLootModifierAudit.couldChangeOutput(m, entry.getKey())).map(RuntimeLootAudit.Modifier::implementation).toList();
                if (resource == null || !resource.external() || drop.unresolved() != 0 || !(drop.chance() > 0) || !(drop.expectedCount() > 0) || !blockers.isEmpty()) {
                    diagnostics.put(id + "/" + entry.getKey(), "Loot unresolved=" + drop.unresolved() + "; modifiers=" + blockers); continue;
                }
                if (List.of("renewable", "attainable").stream().anyMatch(property -> {
                    var fact = sink.get(EvidenceFact.Subject.ITEM, entry.getKey(), property);
                    return fact != null && fact.origin() == EvidenceFact.Origin.OVERRIDE && !fact.value().flag();
                })) continue;
                var stage = habitat.placement().stage(); var override = sink.get(EvidenceFact.Subject.ITEM, entry.getKey(), "stage");
                if (override != null && override.origin() == EvidenceFact.Origin.OVERRIDE) stage = resource.stage();
                String sourceId = PROVIDER + ":" + id;
                var conditions = List.of("Actual loaded creature biomes=" + biomes + "; native ON_GROUND Animal spawn predicate and animals_spawnable_on grass",
                        "Suitable clear daylight habitat with brightness above 8; native spawn distance, collision, mob caps and successful encounter remain conditional; no encounter rate",
                        "Ordinary direct bare-handed player kill, no equipment, worn accessories, enchantments or fire; effective loot and preserving modifiers; positive chance=" + drop.chance(),
                        "Natural grass terrain or 64 dirt plus 64 loaded Ex Deorum spreaders paid jointly; habitat remains reusable, no grass item or animal stock credited");
                var off = new SourceAvailability.Timer(SourceAvailability.Applicability.OFF, 0, List.of());
                var availability = new SourceAvailability(sourceId, SourceAvailability.Category.CONDITIONAL_RENEWABLE, SourceAvailability.Scope.SHARED,
                        List.of(), List.of("minecraft:overworld"), conditions, off, off, true, drop.chance(), drop.expectedCount(), List.of());
                var source = new AcquisitionSource(sourceId, AcquisitionSource.Kind.PLAYER_ACTION, stage, drop.expectedCount(), true, false, 0,
                        Math.min(.9, habitat.placement().confidence()), habitat.selected(), String.join("; ", conditions), availability);
                var sources = new ArrayList<>(resource.sources()); sources.add(source);
                result.put(entry.getKey(), new ResourceEvidence(entry.getKey(), resource.reachable() && resource.stage().ordinal() < stage.ordinal() ? resource.stage() : stage,
                        Availability.RENEWABLE_MANUAL, Automation.PLAYER_GATED, true, true, resource.economicValue(), Math.max(resource.confidence(), source.confidence()), sources, resource.warnings()));
                sink.add(new EvidenceFact(EvidenceFact.Subject.SOURCE, sourceId + "/" + entry.getKey(), "conditional_hunting", EvidenceFact.Value.text(source.reason()),
                        PROVIDER, EvidenceFact.Origin.OBSERVED, source.confidence(), 0, stage, source.dependencies(), source.reason()));
            }
        }
        sink.add(new EvidenceFact(EvidenceFact.Subject.PROVIDER, PROVIDER, "unproven_drops", EvidenceFact.Value.text(new Gson().toJson(diagnostics)),
                PROVIDER, EvidenceFact.Origin.OBSERVED, 1, 0, ProgressionBand.ENTRY, List.of(), "Unsupported native drops remain visible"));
        return result;
    }
    private static String nativeSpawn(EntityType<?> type, String neo) throws ReflectiveOperationException {
        if (SpawnPlacements.getPlacementType(type) != SpawnPlacementTypes.ON_GROUND) return "Native placement is not ON_GROUND";
        var data = SpawnPlacements.class.getDeclaredField("DATA_BY_TYPE"); data.setAccessible(true);
        Object row = ((Map<?, ?>)data.get(null)).get(type); if (row == null) return "Missing native placement record";
        Object predicate = field(row, "predicate");
        if (neo == null) return predicate.getClass().getNestHost() == SpawnPlacements.class ? "" : "Unknown vanilla predicate host " + predicate.getClass().getNestHost().getName();
        if (!Set.of("21.1.248", "21.1.250", "21.1.251").contains(neo)) return "Unknown loader version " + neo;
        if (!NativeToolFacts.lambdaOwner(predicate).equals("net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent$MergedSpawnPredicate")) return "Unknown native predicate owner " + NativeToolFacts.lambdaOwner(predicate);
        for (var captured : predicate.getClass().getDeclaredFields()) {
            captured.setAccessible(true); Object merge = captured.get(predicate);
            if (merge == null || !merge.getClass().getName().equals("net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent$MergedSpawnPredicate")) continue;
            if (field(merge, "replacementPredicate") == null && ((List<?>)field(merge, "andPredicates")).isEmpty()
                    && field(merge, "originalPredicate").getClass().getNestHost() == SpawnPlacements.class) return "";
            return "Native merged spawn original=" + field(merge, "originalPredicate").getClass().getNestHost().getName()
                    + "; replacement=" + (field(merge, "replacementPredicate") == null ? "none" : field(merge, "replacementPredicate").getClass().getNestHost().getName())
                    + "; required AND hosts=" + ((List<?>)field(merge, "andPredicates")).stream().map(p -> p.getClass().getNestHost().getName()).sorted().toList();
        }
        return "Native predicate has no audited captured merge";
    }
    private static Object field(Object object, String name) throws ReflectiveOperationException {
        var field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }
    /** Facts of this exact native scenario. Other entity state stays unknown. */
    static JsonElement conditions(JsonElement value, String entity) { return project(value, entity); }
    static JsonElement project(JsonElement value, String entity) { return project(value, entity, null); }
    static JsonElement project(JsonElement value, String entity, Set<String> damageTags) {
        if (value == null || value.isJsonNull() || value.isJsonPrimitive()) return value;
        if (value.isJsonArray()) {
            var out = new JsonArray();
            for (var v : value.getAsJsonArray()) {
                // Native zero-enchantment function returns before count/cap. Keep
                // failed encodings and custom names opaque.
                if (v.isJsonObject() && "minecraft:enchanted_count_increase".equals(text(v.getAsJsonObject(), "function"))
                        && GenerationLootEvidence.unresolvedUnenchantedEntityFunctions(v) == 0) continue;
                out.add(project(v, entity, damageTags));
            }
            return out;
        }
        var c = value.getAsJsonObject(); String type = text(c, "condition");
        if (type.equals("minecraft:block_state_property") || type.equals("minecraft:match_tool")) return constant(false); // absent entity-loot parameters
        if (type.equals("minecraft:killed_by_player")) return constant(true);
        if (type.equals("minecraft:damage_source_properties") && damageTags != null && c.has("predicate")) {
            var p = c.getAsJsonObject("predicate");
            if (p.keySet().equals(Set.of("tags")) && p.get("tags").isJsonArray()) {
                for (var valueTag : p.getAsJsonArray("tags")) {
                    if (!valueTag.isJsonObject()) return c;
                    var tag = valueTag.getAsJsonObject();
                    if (!tag.keySet().equals(Set.of("id", "expected"))) return c;
                    if (damageTags.contains(text(tag, "id")) != tag.get("expected").getAsBoolean()) return constant(false);
                }
                return constant(true);
            }
        }
        if (type.equals("minecraft:entity_properties") && c.has("predicate")) {
            var p = c.getAsJsonObject("predicate"); String who = text(c, "entity");
            String actual = who.equals("this") ? entity : Set.of("attacker", "direct_attacker", "attacking_player").contains(who) ? "minecraft:player" : "";
            if (!actual.isEmpty()) {
                if (p.has("type") && p.get("type").isJsonPrimitive() && !p.get("type").getAsString().startsWith("#")
                        && !actual.equals(p.get("type").getAsString())) return constant(false);
                if (p.has("flags") && p.getAsJsonObject("flags").has("is_on_fire") && p.getAsJsonObject("flags").get("is_on_fire").getAsBoolean()) return constant(false);
                if (actual.equals("minecraft:player") && p.has("equipment")) {
                    for (var slot : p.getAsJsonObject("equipment").entrySet()) if (slot.getValue().isJsonObject()) {
                        var required = slot.getValue().getAsJsonObject();
                        // Explicit non-air item requirement cannot match empty equipment.
                        if (required.has("items") && required.get("items").isJsonPrimitive()
                                && !required.get("items").getAsString().equals("minecraft:air") && !required.get("items").getAsString().startsWith("#")) return constant(false);
                    }
                }
            }
        }
        var out = new JsonObject(); c.entrySet().forEach(e -> out.add(e.getKey(), project(e.getValue(), entity, damageTags))); return out;
    }
    private static String text(JsonObject object, String key) { return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : ""; }
    private static JsonObject constant(boolean value) { var c = new JsonObject(); c.addProperty("condition", "minecraft:random_chance"); c.addProperty("chance", value ? 1 : 0); return c; }
}
