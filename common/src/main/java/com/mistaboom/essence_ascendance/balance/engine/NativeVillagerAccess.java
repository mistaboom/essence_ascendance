package com.mistaboom.essence_ascendance.balance.engine;

import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import java.util.*;

/** Conditional exploration of native surface jigsaws. This narrow contract proves a
 * positive-weight root -> matching internal villager piece, including its fitting box.
 * It never infers villagers from structure/template names, a registry, or a loot table.
 * Actual discovery, successful terrain placement and a surviving available NPC are explicit scenario conditions. */
public final class NativeVillagerAccess {
    public static final String PROVIDER = "native_villager_access";
    private NativeVillagerAccess() { }
    public record Opportunity(String structure, String startPool, String rootTemplate, String childPool,
                              String childTemplate, String villagerType, String connection, ConfigurationAccess.Proof access) { }
    record Template(CompoundTag nbt, StructureTemplate nativeTemplate) { }

    public static boolean adultUntraded(CompoundTag entity) {
        if (!entity.getString("id").equals("minecraft:villager") || !entity.contains("Age", Tag.TAG_INT) || entity.getInt("Age") != 0
                || !entity.contains("Xp", Tag.TAG_INT) || entity.getInt("Xp") != 0 || entity.contains("Offers")
                || entity.getBoolean("NoAI") || entity.getBoolean("NoGravity") || entity.getBoolean("Invulnerable")
                || entity.getShort("DeathTime") != 0 || !entity.contains("Health", Tag.TAG_ANY_NUMERIC)
                || !Float.isFinite(entity.getFloat("Health")) || entity.getFloat("Health") <= 0) return false;
        var data = entity.getCompound("VillagerData");
        var type = ResourceLocation.tryParse(data.getString("type"));
        return data.getString("profession").equals("minecraft:none") && data.contains("level", Tag.TAG_INT) && data.getInt("level") == 1
                && type != null && BuiltInRegistries.VILLAGER_TYPE.containsKey(type);
    }

    /** Native positive random-spread placement, without exclusion/custom placement gates. */
    public static boolean placement(JsonObject placement) {
        return text(placement, "type").equals("minecraft:random_spread") && !placement.has("exclusion_zone")
                && number(placement, "frequency", 1) > 0 && number(placement, "frequency", 1) <= 1
                && number(placement, "spacing", 0) > number(placement, "separation", -1) && number(placement, "separation", -1) >= 0;
    }
    public static List<Opportunity> collect(PackEvidenceContext context, CapabilitySink sink) {
        var inputs = context.inputs(); var found = new TreeMap<String, Opportunity>(); var templates = new HashMap<String, Template>();
        if (!inputs.structuresEnabled()) return List.of();
        var attempt = OptionalIntegration.attempt(PROVIDER, "native surface jigsaw NPCs", () -> {
            var eligible = new TreeSet<String>();
            for (var setId : inputs.definitionIds("worldgen/structure_set")) {
                if (!inputs.structureSetEligible(setId)) continue;
                var set = inputs.definition("worldgen/structure_set", setId);
                if (!set.has("placement") || !placement(set.getAsJsonObject("placement"))) continue;
                for (var entry : set.getAsJsonArray("structures")) if (number(entry.getAsJsonObject(), "weight", 0) > 0)
                    eligible.add(text(entry.getAsJsonObject(), "structure"));
            }
            for (String id : eligible) {
                var structureId = ResourceLocation.parse(id);
                if (!inputs.structureDimensions(structureId).contains("minecraft:overworld")) continue;
                var read = OptionalIntegration.attempt(PROVIDER, id, () -> {
                    var result = new ArrayList<Opportunity>(); var structure = inputs.definition("worldgen/structure", structureId);
                    if (structure == null || !text(structure, "type").equals("minecraft:jigsaw") || structure.has("start_jigsaw_name")
                            || number(structure, "size", 0) < 1 || !text(structure, "project_start_to_heightmap").equals("WORLD_SURFACE_WG")
                            || !structure.has("start_height") || number(structure.getAsJsonObject("start_height"), "absolute", -1) != 0) return result;
                    String start = text(structure, "start_pool");
                    for (var rootEntry : elements(inputs.definition("worldgen/template_pool", ResourceLocation.parse(start)))) {
                        if (!nativeElement(rootEntry) || !blockOnlyProcessors(context, rootEntry, false)) continue;
                        String rootId = text(rootEntry, "location"); var root = template(context, templates, rootId);
                        if (root == null) continue;
                        var rootBox = root.nativeTemplate().getBoundingBox(new StructurePlaceSettings(), BlockPos.ZERO);
                        if (Math.max(rootBox.getXSpan(), rootBox.getZSpan()) > number(structure, "max_distance_from_center", 0)) continue;
                        for (var link : root.nativeTemplate().filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), Blocks.JIGSAW)) {
                            if (link.nbt() == null || !rootBox.isInside(link.pos().relative(JigsawBlock.getFrontFacing(link.state())))) continue;
                            String childPool = link.nbt().getString("pool"); var poolId = ResourceLocation.tryParse(childPool); if (poolId == null) continue;
                            for (var childEntry : elements(inputs.definition("worldgen/template_pool", poolId))) {
                                if (!nativeElement(childEntry) || !blockOnlyProcessors(context, childEntry, true)) continue;
                                String childId = text(childEntry, "location"); var child = template(context, templates, childId);
                                if (child == null) continue;
                                var entities = child.nbt().getList("entities", Tag.TAG_COMPOUND);
                                if (entities.size() != 1 || !insideEntity(child.nbt(), entities.getCompound(0))
                                        || !adultUntraded(entities.getCompound(0).getCompound("nbt"))) continue;
                                var entity = entities.getCompound(0).getCompound("nbt"); String type = entity.getCompound("VillagerData").getString("type");
                                for (var rotation : Rotation.values()) {
                                    var settings = new StructurePlaceSettings().setRotation(rotation);
                                    var connectors = child.nativeTemplate().filterBlocks(BlockPos.ZERO, settings, Blocks.JIGSAW);
                                    if (connectors.size() != 1) continue;
                                    var join = connectors.getFirst(); if (!JigsawBlock.canAttach(link, join)) continue;
                                    var offset = link.pos().relative(JigsawBlock.getFrontFacing(link.state())).subtract(join.pos());
                                    var box = child.nativeTemplate().getBoundingBox(settings, offset);
                                    if (!rootBox.isInside(new BlockPos(box.minX(), box.minY(), box.minZ()))
                                            || !rootBox.isInside(new BlockPos(box.maxX(), box.maxY(), box.maxZ()))) continue;
                                    String condition = "Conditional exploration: find a successfully generated accessible overworld surface structure with this positive-weight internal NPC piece; NPC survives, remains untraded and can bind the supplied jobsite during work hours. No guaranteed occurrence, travel rate, population, restock or breeding assumed.";
                                    var source = new AcquisitionSource(id + "/" + childId, AcquisitionSource.Kind.WORLD_GENERATION, ProgressionBand.ENTRY,
                                            1, false, false, 0, .9, List.of(start, childPool), condition);
                                    var proof = new ConfigurationAccess.Proof(new CompetitiveCapabilities.Placement(ProgressionBand.ENTRY, true, .9, List.of(source)),
                                            List.of(id, rootId, childId, condition), List.of());
                                    result.add(new Opportunity(id, start, rootId, childPool, childId, type, link.pos() + " -> " + join.pos() + " " + rotation, proof));
                                    break;
                                }
                            }
                        }
                    }
                    return result;
                });
                read.value().ifPresentOrElse(rows -> rows.forEach(row -> found.putIfAbsent(row.villagerType(), row)),
                        () -> sink.candidate(id, PROVIDER, read.failure(), Set.of(), CapabilitySink.Reason.READ_FAILED));
            }
            return List.copyOf(found.values());
        });
        var result = attempt.value().orElse(List.of());
        if (!attempt.succeeded()) sink.candidate(PROVIDER, PROVIDER, attempt.failure(), Set.of(), CapabilitySink.Reason.READ_FAILED);
        result.forEach(row -> sink.definition(PROVIDER, row.villagerType(), com.mistaboom.essence_ascendance.balance.generated.BalanceDocument.GSON.toJsonTree(row)));
        return result;
    }
    static List<JsonObject> elements(JsonObject pool) {
        if (pool == null || !pool.has("elements")) return List.of(); var result = new ArrayList<JsonObject>();
        for (var value : pool.getAsJsonArray("elements")) { var row = value.getAsJsonObject();
            if (number(row, "weight", 0) > 0 && row.has("element")) result.add(row.getAsJsonObject("element")); }
        return result;
    }
    static boolean insideEntity(CompoundTag template, CompoundTag entity) {
        var size = template.getList("size", Tag.TAG_INT); var block = entity.getList("blockPos", Tag.TAG_INT); var pos = entity.getList("pos", Tag.TAG_DOUBLE);
        if (size.size() != 3 || block.size() != 3 || pos.size() != 3) return false;
        for (int axis = 0; axis < 3; axis++) if (!Double.isFinite(pos.getDouble(axis)) || pos.getDouble(axis) < 0 || pos.getDouble(axis) >= size.getInt(axis)
                || block.getInt(axis) < 0 || block.getInt(axis) >= size.getInt(axis) || block.getInt(axis) != (int)Math.floor(pos.getDouble(axis))) return false;
        return true;
    }
    static boolean nativeElement(JsonObject element) {
        return Set.of("minecraft:single_pool_element", "minecraft:legacy_single_pool_element").contains(text(element, "element_type"))
                && text(element, "projection").equals("rigid") && ResourceLocation.tryParse(text(element, "location")) != null;
    }
    private static boolean blockOnlyProcessors(PackEvidenceContext context, JsonObject element, boolean emptyOnly) {
        if (!element.has("processors")) return false;
        var value = element.get("processors"); JsonObject processors;
        if (value.isJsonArray()) { processors = new JsonObject(); processors.add("processors", value); }
        else if (value.isJsonObject()) processors = value.getAsJsonObject();
        else processors = context.inputs().definition("worldgen/processor_list", ResourceLocation.parse(value.getAsString()));
        if (processors == null || !processors.has("processors")) return false;
        for (var entry : processors.getAsJsonArray("processors")) {
            if (emptyOnly) return false;
            // Audited vanilla rule processors transform blocks only, never entities/coordinates/jigsaw selection.
            // Custom processor types remain unknown even under native-looking pool names.
            if (!text(entry.getAsJsonObject(), "processor_type").equals("minecraft:rule")) return false;
        }
        return true;
    }
    static Template template(PackEvidenceContext context, Map<String, Template> cache, String id) {
        if (cache.containsKey(id)) return cache.get(id);
        var location = ResourceLocation.parse(id).withPath(path -> "structure/" + path + ".nbt");
        var resource = context.server().getResourceManager().getResource(location); if (resource.isEmpty()) { cache.put(id, null); return null; }
        try (var stream = resource.orElseThrow().open()) {
            var tag = NbtIo.readCompressed(stream, NbtAccounter.create(8L * 1024 * 1024));
            tag = net.minecraft.util.datafix.DataFixTypes.STRUCTURE.updateToCurrentVersion(context.server().getFixerUpper(), tag, NbtUtils.getDataVersion(tag, 500));
            // One exact palette avoids silently choosing a helpful variant from an unresolved palette combination.
            if (!tag.contains("palette", Tag.TAG_LIST) || tag.contains("palettes")) { cache.put(id, null); return null; }
            var size = tag.getList("size", Tag.TAG_INT);
            if (size.size() != 3 || size.getInt(0) < 1 || size.getInt(1) < 1 || size.getInt(2) < 1
                    || size.getInt(0) > 128 || size.getInt(1) > 128 || size.getInt(2) > 128) return null;
            var nativeTemplate = new StructureTemplate(); nativeTemplate.load(BuiltInRegistries.BLOCK.asLookup(), tag);
            var result = new Template(tag, nativeTemplate); cache.put(id, result); return result;
        } catch (java.io.IOException failure) { throw new IllegalStateException("Cannot read effective NPC template " + id, failure); }
    }
    private static String text(JsonObject object, String key) { return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : ""; }
    private static double number(JsonObject object, String key, double fallback) { return object.has(key) ? object.get(key).getAsDouble() : fallback; }
}
