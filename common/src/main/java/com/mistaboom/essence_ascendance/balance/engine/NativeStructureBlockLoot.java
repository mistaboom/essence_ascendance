package com.mistaboom.essence_ascendance.balance.engine;

import com.google.gson.*;
import com.mistaboom.essence_ascendance.balance.generated.BalanceDocument;
import com.mistaboom.essence_ascendance.valuation.StartingBlockDrops;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.*;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import java.util.*;

/** Conditional exploration of a loaded native jigsaw's contained one-block loot piece.
 * Positive weights, native attachment, fitting box, final state, hand harvest and effective
 * biome-conditioned loot all have independent witnesses. No registry/name-based access,
 * guaranteed stock, renewal, successful terrain placement or encounter rate is inferred. */
public final class NativeStructureBlockLoot {
    private NativeStructureBlockLoot() { }
    public static final String PROVIDER = "native_structure_block_loot";

    public static Map<String, ResourceEvidence> enrich(PackEvidenceContext context, Map<String, ResourceEvidence> original, EvidenceSink sink) {
        if (!context.inputs().structuresEnabled()) return original;
        var staged = sink.staged();
        var attempt = OptionalIntegration.attempt(PROVIDER, "loaded structure definitions", () -> collect(context, original, staged));
        if (!attempt.succeeded()) {
            sink.warn(PROVIDER + " READ_FAILED: " + attempt.failure());
            return original;
        }
        sink.merge(staged);
        return attempt.value().orElseThrow();
    }
    private static Map<String, ResourceEvidence> collect(PackEvidenceContext context, Map<String, ResourceEvidence> original, EvidenceSink sink) {
        var result = new TreeMap<>(original);
        var templates = new HashMap<String, NativeVillagerAccess.Template>();
        var eligible = new TreeSet<String>();
        var inputs = context.inputs();
        for (var setId : inputs.definitionIds("worldgen/structure_set")) {
            if (!inputs.structureSetEligible(setId)) continue;
            var set = inputs.definition("worldgen/structure_set", setId);
            if (!set.has("placement") || !NativeVillagerAccess.placement(set.getAsJsonObject("placement"))) continue;
            for (var value : set.getAsJsonArray("structures")) {
                var row = value.getAsJsonObject();
                if (row.get("weight").getAsInt() > 0) eligible.add(text(row, "structure"));
            }
        }
        var seen = new HashSet<String>();
        var dropCache = new HashMap<String, Map<String, StartingBlockDrops.ExpectedDrop>>();
        var possible = context.server().overworld().getChunkSource().getGenerator().getBiomeSource().possibleBiomes();
        for (String id : eligible) {
            var stagedResources = new TreeMap<String, ResourceEvidence>();
            var stagedFacts = sink.staged();
            var stagedSeen = new HashSet<>(seen);
            var attempt = OptionalIntegration.attempt(PROVIDER, id, () -> {
                var structureId = ResourceLocation.parse(id);
                if (!inputs.structureDimensions(structureId).contains("minecraft:overworld")) return 0;
                var structure = inputs.definition("worldgen/structure", structureId);
                if (structure == null || !text(structure, "type").equals("minecraft:jigsaw")
                        || structure.has("start_jigsaw_name") || !structure.has("size") || structure.get("size").getAsInt() < 1) return 0;
                var nativeStructure = context.server().registryAccess().registryOrThrow(Registries.STRUCTURE).get(structureId);
                if (nativeStructure == null || nativeStructure.getClass() != net.minecraft.world.level.levelgen.structure.structures.JigsawStructure.class) return 0;
                var biomes = nativeStructure.biomes().stream().filter(possible::contains)
                        .sorted(Comparator.comparing(b -> b.unwrapKey().orElseThrow().location())).toList();
                String start = text(structure, "start_pool");
                int count = 0;
                for (var element : NativeVillagerAccess.elements(inputs.definition("worldgen/template_pool", ResourceLocation.parse(start)))) {
                    if (!NativeVillagerAccess.nativeElement(element) || !preservesRoot(context, element)) continue;
                    String rootId = text(element, "location");
                    var root = NativeVillagerAccess.template(context, templates, rootId); if (root == null) continue;
                    var rootBox = root.nativeTemplate().getBoundingBox(new StructurePlaceSettings(), BlockPos.ZERO);
                    if (!structure.has("max_distance_from_center") || Math.max(rootBox.getXSpan(), rootBox.getZSpan())
                            > structure.get("max_distance_from_center").getAsInt()) continue;
                    for (var link : root.nativeTemplate().filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), Blocks.JIGSAW)) {
                        if (link.nbt() == null) continue;
                        String poolId = link.nbt().getString("pool"); var poolKey = ResourceLocation.tryParse(poolId); if (poolKey == null) continue;
                        for (var childElement : NativeVillagerAccess.elements(inputs.definition("worldgen/template_pool", poolKey))) {
                            if (!NativeVillagerAccess.nativeElement(childElement) || !emptyProcessors(context, childElement)) continue;
                            String childId = text(childElement, "location");
                            var child = NativeVillagerAccess.template(context, templates, childId);
                            if (child == null || !child.nativeTemplate().getSize().equals(new net.minecraft.core.Vec3i(1, 1, 1))
                                    || !child.nbt().getList("entities", Tag.TAG_COMPOUND).isEmpty()) continue;
                            for (var rotation : Rotation.values()) {
                                var settings = new StructurePlaceSettings().setRotation(rotation);
                                var connectors = child.nativeTemplate().filterBlocks(BlockPos.ZERO, settings, Blocks.JIGSAW);
                                if (connectors.size() != 1) continue;
                                var join = connectors.getFirst();
                                if (join.nbt() == null || !JigsawBlock.canAttach(link, join)) continue;
                                var position = link.pos().relative(JigsawBlock.getFrontFacing(link.state())).subtract(join.pos());
                                if (!rootBox.isInside(position)) continue;
                                BlockStateParser.BlockResult parsed;
                                try { parsed = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), join.nbt().getString("final_state"), false); }
                                catch (com.mojang.brigadier.exceptions.CommandSyntaxException invalid) { throw new IllegalArgumentException("Unsupported jigsaw final state", invalid); }
                                if (parsed.nbt() != null || parsed.blockState().isAir()) continue;
                                var state = parsed.blockState();
                                for (var biome : biomes) {
                                    String biomeId = biome.unwrapKey().orElseThrow().location().toString();
                                    String key = state + "/" + biomeId; if (stagedSeen.contains(key)) continue;
                                    var tags = biome.tags().map(t -> t.location().toString()).collect(java.util.stream.Collectors.toSet());
                                    var drops = dropCache.computeIfAbsent(key, ignored -> {
                                        var reasons = new ArrayList<String>();
                                        var found = StartingBlockDrops.supportedHandDrops(inputs, state, "", biomeId, tags, reasons::add);
                                        if (found.isEmpty()) sink.warn(PROVIDER + " harvest unproven " + key + ": "
                                                + String.join("; ", reasons.stream().distinct().limit(8).toList()));
                                        return found;
                                    });
                                    if (drops.isEmpty()) continue;
                                    stagedSeen.add(key);
                                    String sourceId = PROVIDER + ":" + id + "/" + childId + "/" + biomeId;
                                    var conditions = List.of("native_structure_block_binding", "Native positive-weight start pool " + start + "; root " + rootId
                                            + "; child pool " + poolId + "; template " + childId + "; matching connector " + position + " " + rotation,
                                            "Actual final state " + state + "; native hand harvest, loaded hardness and effective biome " + biomeId,
                                            "Conditional exploration: find a successfully generated accessible unlooted piece whose harvest origin and predicate query position are in this biome (positive coordinates suffice for the audited truncating query); survive the terrain and harvest it. Enough distinct successful encounters assumed; no guaranteed stock, renewal, placement probability or travel/harvest rate.");
                                    var timer = new SourceAvailability.Timer(SourceAvailability.Applicability.OFF, 0, List.of());
                                    for (var output : drops.entrySet()) {
                                        var resource = stagedResources.getOrDefault(output.getKey(), result.get(output.getKey()));
                                        if (resource == null || !resource.external()) continue;
                                        var attainable = sink.get(EvidenceFact.Subject.ITEM, output.getKey(), "attainable");
                                        if (attainable != null && attainable.origin() == EvidenceFact.Origin.OVERRIDE && !attainable.value().flag()) continue;
                                        var override = sink.get(EvidenceFact.Subject.ITEM, output.getKey(), "stage");
                                        var stage = override != null && override.origin() == EvidenceFact.Origin.OVERRIDE ? resource.stage() : ProgressionBand.ENTRY;
                                        var drop = output.getValue();
                                        var availability = new SourceAvailability(sourceId, SourceAvailability.Category.FINITE_SHARED, SourceAvailability.Scope.SHARED,
                                                List.of(id), List.of("minecraft:overworld"), conditions, timer, timer, true, drop.chance(), drop.expectedCount(), List.of());
                                        var sources = new ArrayList<>(resource.sources());
                                        sources.add(new AcquisitionSource(sourceId, AcquisitionSource.Kind.LOOT, stage, drop.expectedCount(), false, false, 0, .9,
                                                List.of(id, rootId, poolId, childId, biomeId), String.join("; ", conditions), availability));
                                        stagedResources.put(output.getKey(), new ResourceEvidence(resource.itemId(), resource.reachable() && resource.stage().ordinal() < stage.ordinal() ? resource.stage() : stage,
                                                resource.reachable() ? resource.availability() : Availability.FINITE, resource.automation(), true, true,
                                                resource.economicValue(), Math.max(.9, resource.confidence()), sources, resource.warnings()));
                                    }
                                    stagedFacts.add(new EvidenceFact(EvidenceFact.Subject.SOURCE, sourceId, "conditional_exploration", EvidenceFact.Value.text(BalanceDocument.GSON.toJson(drops)),
                                            PROVIDER, EvidenceFact.Origin.OBSERVED, .9, 0, ProgressionBand.ENTRY, List.of(id, rootId, poolId, childId, biomeId), String.join("; ", conditions)));
                                    count++;
                                }
                                break;
                            }
                        }
                    }
                }
                return count;
            });
            if (!attempt.succeeded()) sink.warn(PROVIDER + " READ_FAILED " + id + ": " + attempt.failure());
            else if (attempt.value().orElseThrow() > 0) { result.putAll(stagedResources); sink.merge(stagedFacts); seen.addAll(stagedSeen); }
        }
        return Collections.unmodifiableMap(result);
    }

    private static JsonArray processors(PackEvidenceContext context, JsonObject element) {
        if (!element.has("processors")) return null;
        var value = element.get("processors");
        if (value.isJsonArray()) return value.getAsJsonArray();
        var definition = value.isJsonObject() ? value.getAsJsonObject()
                : context.inputs().definition("worldgen/processor_list", ResourceLocation.parse(value.getAsString()));
        return definition == null || !definition.has("processors") ? null : definition.getAsJsonArray("processors");
    }
    private static boolean emptyProcessors(PackEvidenceContext context, JsonObject element) {
        var processors = processors(context, element); return processors != null && processors.isEmpty();
    }
    private static boolean preservesRoot(PackEvidenceContext context, JsonObject element) {
        var processors = processors(context, element); if (processors == null) return false;
        for (var processor : processors) {
            // This audited processor returns the same block info/coordinates/NBT;
            // its early water-clearing write cannot change jigsaw selection.
            if (!text(processor.getAsJsonObject(), "processor_type").equals("ftbpc:waterlogging_fix_processor")
                    || !"21.1.22".equals(context.inputs().installedVersion("ftbpc"))) return false;
        }
        return true;
    }
    private static String text(JsonObject o, String k) { return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : ""; }
}
