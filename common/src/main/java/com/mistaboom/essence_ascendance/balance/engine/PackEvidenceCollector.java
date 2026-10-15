package com.mistaboom.essence_ascendance.balance.engine;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.balance.config.BalanceOverrides;
import com.mistaboom.essence_ascendance.balance.config.BalanceSettings;
import com.mistaboom.essence_ascendance.balance.generated.BalancePerformance;
import com.mistaboom.essence_ascendance.valuation.ProceduralValuationEngine;
import com.mistaboom.essence_ascendance.valuation.GeneratedYieldEligibility;
import com.mistaboom.essence_ascendance.valuation.ProceduralValuationResult;
import com.mistaboom.essence_ascendance.valuation.ValuationEvidenceSnapshot;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import static com.mistaboom.essence_ascendance.balance.engine.EvidenceFact.Subject.ITEM;
import static com.mistaboom.essence_ascendance.balance.engine.EvidenceFact.Subject.ENEMY;

/** Bounded item/recipe estimates plus measured registered equipment and default mob attributes.
 * Catalog strength bands are calibration groups, never acquisition or progression proofs. */
public final class PackEvidenceCollector {
    private PackEvidenceCollector() { }

    public static PackEvidence collect(MinecraftServer server, BalanceSettings settings, BalanceOverrides overrides,
                                       com.mistaboom.essence_ascendance.valuation.GenerationDataSnapshot inputs,
                                       GenerationProviders runs) {
        inputs.requireCurrent(server);
        BalancePerformance.increment("evidence_collection_runs");
        BalancePerformance.flag("environment_analysis_rescanned", true);
        ProceduralValuationEngine.prepareGeneration(overrides, inputs);
        com.mistaboom.essence_ascendance.balance.economy.EconomyGenerator.prepareProduction(inputs, overrides, runs);
        List<ProceduralValuationResult> valuations;
        try (var phase = BalancePerformance.phase("procedural_valuation")) {
            valuations = ProceduralValuationEngine.evaluateAll(server);
        }
        inputs.recordRoutingDiagnostics(valuations);
        var snapshot = ValuationEvidenceSnapshot.collect(server, valuations);
        var context = new PackEvidenceContext(server, settings, overrides, valuations, snapshot, inputs);
        var sink = new EvidenceSink();
        applyOverrides(context, sink);
        Map<String, ResourceEvidence> resources = resolveResources(valuations, snapshot, sink);
        Map<String, EquipmentReference> measured = new TreeMap<>();
        for (var value : valuations) {
            String id = value.itemId().toString();
            var attempt = OptionalIntegration.attempt("equipment_components", id, () ->
                    java.util.Optional.ofNullable(measureEquipment(new ItemStack(BuiltInRegistries.ITEM.get(value.itemId())), ProgressionBand.ENTRY, false)));
            if (!attempt.succeeded()) sink.warn("Default equipment measurement excluded for " + id + ": " + attempt.failure());
            else attempt.value().orElseThrow().ifPresent(reference -> measured.put(id, reference));
        }
        List<EquipmentReference> equipment = new ArrayList<>(resolveEquipment(measured, resources, sink, settings));
        equipment.add(emptyHandMiningReference());
        var enemies = collectEnemies(sink);
        var frontiers = RobustFrontiers.build(equipment, settings.outlierPolicy().name());
        inputs.limitations().forEach(sink::warn);
        sink.warn("Catalog equipment strength bands and default mob attributes are calibration estimates. Acquisition, quest gates, loot, automation, attack cadence and farm rates are not evaluated.");
        BalancePerformance.count("evidence_resources", resources.size());
        BalancePerformance.count("evidence_equipment", equipment.size());
        BalancePerformance.count("evidence_enemies", enemies.size());
        return new PackEvidence(resources, equipment, enemies, frontiers, sink.facts(), sink.warnings(), snapshot.summary());
    }

    /** Shared payout boundary; internal estimates remain available without claiming survival access. */
    static Map<String, ResourceEvidence> resolveResources(List<ProceduralValuationResult> valuations,
            ValuationEvidenceSnapshot snapshot, EvidenceSink sink) {
        Map<String, ResourceEvidence> resources = new TreeMap<>();
        for (var value : valuations) {
            String id = value.itemId().toString();
            boolean external = !value.itemId().getNamespace().equals(EssenceAscendance.MOD_ID);
            boolean denied = excluded(sink, ITEM, id);
            var eligibility = GeneratedYieldEligibility.decide(new ItemStack(BuiltInRegistries.ITEM.get(value.itemId())),
                    value, false, denied);
            // EA's own materials retain their recipe estimate. Economy generation never auto-pays endogenous items.
            double amount = denied || external && !eligibility.eligible() ? 0 : value.totalValue();
            var stage = enumeration(ProgressionBand.class, sink.text(ITEM, id, "stage", "ENTRY"));
            List<String> warnings = List.of(external ? "catalog_estimate; acquisition and supply unexamined"
                            : "endogenous_excluded_from_external_baselines",
                    "generated_yield_eligibility=" + eligibility.status() + "; " + eligibility.reason());
            resources.put(id, new ResourceEvidence(id, stage, Availability.UNKNOWN, Automation.UNKNOWN,
                    false, external, amount, value.confidence(), snapshot.sources().getOrDefault(id, List.of()), warnings));
        }
        return resources;
    }

    static List<EquipmentReference> resolveEquipment(Map<String, EquipmentReference> measured,
            Map<String, ResourceEvidence> resources, EvidenceSink sink, BalanceSettings settings) {
        List<EquipmentReference> rows = new ArrayList<>();
        for (var ref : measured.values()) {
            var resource = resources.get(ref.itemId());
            if (resource == null) continue;
            String slot = sink.text(ITEM, ref.itemId(), "slot", ref.slot());
            Map<CapabilityAxis, Double> axes = resolvedAxes(sink, ITEM, ref.itemId(), ref.axes());
            if (axes.containsKey(CapabilityAxis.BURST_DAMAGE) && axes.containsKey(CapabilityAxis.ATTACK_RATE)
                    && sink.get(ITEM, ref.itemId(), "axis.SUSTAINED_DAMAGE") == null)
                axes.put(CapabilityAxis.SUSTAINED_DAMAGE, finiteProduct(axes.get(CapabilityAxis.BURST_DAMAGE), axes.get(CapabilityAxis.ATTACK_RATE), ref.itemId()));
            boolean included = resource.external() && resource.economicValue() > 0 && !axes.isEmpty()
                    && BalanceOverrides.EQUIPMENT_SLOTS.contains(slot) && !slot.equals("body")
                    && !excluded(sink, ITEM, ref.itemId()) && sink.flag(ITEM, ref.itemId(), "include_reference", true);
            rows.add(new EquipmentReference(ref.itemId(), slot, ProgressionBand.ENTRY, axes, ref.capabilities(),
                    false, included, ref.confidence(), ref.reason()));
        }
        var banded = catalogBands(rows);
        List<EquipmentReference> result = new ArrayList<>();
        for (var ref : banded) {
            var band = enumeration(ProgressionBand.class, sink.text(ITEM, ref.itemId(), "stage", ref.stage().name()));
            var row = new EquipmentReference(ref.itemId(), ref.slot(), band, ref.axes(), ref.capabilities(),
                    false, ref.included(), ref.confidence(), ref.reason());
            result.add(row);
            measuredAxisFacts(sink, ITEM, row.itemId(), row.axes(), "equipment_components", row.confidence(), band,
                    "Default equipment attributes; relative catalog strength band, not acquisition progression");
        }
        return List.copyOf(result);
    }

    /** Rank distinct measured strengths within compatible slots. Duplicate catalog entries have no vote. */
    static List<EquipmentReference> catalogBands(List<EquipmentReference> rows) {
        Map<String, List<Double>> populations = new TreeMap<>();
        for (var row : rows) if (row.included()) populations.computeIfAbsent(group(row), ignored -> new ArrayList<>()).add(strength(row));
        populations.replaceAll((key, values) -> values.stream().distinct().sorted().toList());
        List<EquipmentReference> result = new ArrayList<>();
        for (var row : rows) {
            var values = populations.getOrDefault(group(row), List.of());
            int index = java.util.Collections.binarySearch(values, strength(row));
            int band = values.size() <= 1 || index < 0 ? 0 : (int) Math.round(index * 4.0 / (values.size() - 1));
            result.add(new EquipmentReference(row.itemId(), row.slot(), ProgressionBand.at(band), row.axes(), row.capabilities(),
                    false, row.included(), row.confidence(), row.reason()));
        }
        return List.copyOf(result);
    }
    private static String group(EquipmentReference row) {
        return row.axes().containsKey(CapabilityAxis.MINING_SPEED) ? "mining_tools" : row.slot();
    }
    private static double strength(EquipmentReference row) {
        var axes = row.axes();
        if (axes.containsKey(CapabilityAxis.MINING_SPEED)) return axes.get(CapabilityAxis.MINING_SPEED);
        if (axes.containsKey(CapabilityAxis.ARMOR)) return axes.get(CapabilityAxis.ARMOR) + axes.getOrDefault(CapabilityAxis.TOUGHNESS, 0.0);
        return axes.getOrDefault(CapabilityAxis.SUSTAINED_DAMAGE, axes.getOrDefault(CapabilityAxis.DURABILITY, 0.0));
    }

    static List<EnemyReference> collectEnemies(EvidenceSink sink) {
        List<EnemyReference> result = new ArrayList<>();
        for (var type : BuiltInRegistries.ENTITY_TYPE.stream().sorted(Comparator.comparing(t -> BuiltInRegistries.ENTITY_TYPE.getKey(t).toString())).toList()) {
            String id = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
            if (id.startsWith(EssenceAscendance.MOD_ID + ":") || !DefaultAttributes.hasSupplier(type)) continue;
            if (type.getCategory() != MobCategory.MONSTER && type != EntityType.ENDER_DRAGON && type != EntityType.WITHER) continue;
            var attempt = OptionalIntegration.attempt("entity_attributes", id, () -> {
                @SuppressWarnings("unchecked") EntityType<? extends LivingEntity> living = (EntityType<? extends LivingEntity>) type;
                var attributes = DefaultAttributes.getSupplier(living);
                Map<CapabilityAxis, Double> axes = new EnumMap<>(CapabilityAxis.class);
                axes.put(CapabilityAxis.EFFECTIVE_HEALTH, base(attributes, Attributes.MAX_HEALTH, 20));
                axes.put(CapabilityAxis.ARMOR, base(attributes, Attributes.ARMOR, 0));
                axes.put(CapabilityAxis.TOUGHNESS, base(attributes, Attributes.ARMOR_TOUGHNESS, 0));
                axes.put(CapabilityAxis.BURST_DAMAGE, base(attributes, Attributes.ATTACK_DAMAGE, 0));
                axes.put(CapabilityAxis.GROUND_SPEED, base(attributes, Attributes.MOVEMENT_SPEED, .25));
                axes = resolvedAxes(sink, ENEMY, id, axes);
                double health = axes.get(CapabilityAxis.EFFECTIVE_HEALTH);
                boolean boss = type == EntityType.ENDER_DRAGON || type == EntityType.WITHER;
                var encounter = boss ? EnemyReference.Encounter.BOSS : health >= 150 ? EnemyReference.Encounter.APEX
                        : health >= 50 ? EnemyReference.Encounter.ELITE : EnemyReference.Encounter.ROUTINE;
                var band = enumeration(ProgressionBand.class, sink.text(ENEMY, id, "stage", boss || health >= 150 ? "APEX" : health >= 50 ? "LATE" : "ENTRY"));
                boolean included = health > 0 && !excluded(sink, ENEMY, id) && sink.flag(ENEMY, id, "include_reference", true);
                measuredAxisFacts(sink, ENEMY, id, axes, "entity_attributes", .7, band,
                        "Registered default attributes only; entity never created or spawned; no cadence or XP claim");
                return new EnemyReference(id, encounter, band, axes, included, .7,
                        List.of("Encounter access/frequency, attack cadence, equipment, phases and regeneration are unexamined"),
                        "Default hostile attributes; health strength grouping, not acquisition progression");
            });
            if (attempt.succeeded()) attempt.value().ifPresent(result::add);
            else sink.warn("Default enemy attributes excluded for " + id + ": " + attempt.failure());
        }
        return List.copyOf(result);
    }
    /** Empty hands have native destroy speed even when this environment proves no early tool recipe. */
    static EquipmentReference emptyHandMiningReference() {
        double speed = ItemStack.EMPTY.getDestroySpeed(Blocks.DIRT.defaultBlockState());
        if (!Double.isFinite(speed) || speed <= 0) throw new IllegalStateException("Invalid native empty-hand destroy speed");
        return new EquipmentReference("minecraft:air", "mainhand_tool", ProgressionBand.ENTRY,
                Map.of(CapabilityAxis.MINING_SPEED, speed), List.of("innate_empty_hand; hand-breakable targets only"),
                true, true, 1, "Loaded native empty-stack destroy speed on a hand-breakable block; no tool acquisition, harvest level, durability, throughput or scripted interaction access claimed");
    }

    /** Uses the same stack attribute fallback and slot filtering as equipped gameplay items. */
    static EquipmentReference measureEquipment(ItemStack stack, ProgressionBand stage, boolean reachable) {
        Item item = stack.getItem();
        ItemAttributeModifiers attributes = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        if (attributes.modifiers().isEmpty()) attributes = item.getDefaultAttributeModifiers();
        Tool tool = stack.get(DataComponents.TOOL);
        boolean known = item instanceof ArmorItem || item instanceof TieredItem || item instanceof BowItem || item instanceof CrossbowItem
                || item instanceof TridentItem || item instanceof MaceItem || item instanceof ShieldItem || item instanceof ElytraItem;
        if (!known && attributes.modifiers().isEmpty() && tool == null) return null;
        String id = BuiltInRegistries.ITEM.getKey(item).toString();
        List<String> capabilities = new ArrayList<>(List.of(EquipmentReference.ESTIMATED_CATALOG_REFERENCE));
        Map<CapabilityAxis, Double> axes = new EnumMap<>(CapabilityAxis.class);
        String slot = "mainhand_melee"; EquipmentSlot equipmentSlot = EquipmentSlot.MAINHAND;
        if (item instanceof ArmorItem armor) {
            equipmentSlot = armor.getEquipmentSlot(); slot = equipmentSlot.getName();
            axes.put(CapabilityAxis.ARMOR, attribute(stack, Attributes.ARMOR, equipmentSlot, 0));
            axes.put(CapabilityAxis.TOUGHNESS, attribute(stack, Attributes.ARMOR_TOUGHNESS, equipmentSlot, 0));
        } else if (item instanceof ElytraItem) {
            slot = "chest"; axes.put(CapabilityAxis.GLIDING, 1.0); capabilities.add("gliding");
        } else if (item instanceof ShieldItem) {
            slot = "offhand"; axes.put(CapabilityAxis.BLOCKING, 1.0); capabilities.add("directional_blocking");
        } else {
            double damage = attribute(stack, Attributes.ATTACK_DAMAGE, equipmentSlot, 1);
            double rate = attribute(stack, Attributes.ATTACK_SPEED, equipmentSlot, 4);
            if (item instanceof BowItem || item instanceof CrossbowItem) {
                slot = item instanceof BowItem ? "mainhand_bow" : "mainhand_crossbow";
                damage = item instanceof BowItem ? 6 : 9; rate = item instanceof BowItem ? 1 : .8;
                axes.put(CapabilityAxis.RANGE, 32.0); capabilities.add("projectile_ammunition");
                capabilities.add("native_projectile_policy_estimate; custom ammunition and callbacks unmeasured");
            }
            axes.put(CapabilityAxis.BURST_DAMAGE, damage); axes.put(CapabilityAxis.ATTACK_RATE, Math.max(.05, rate));
            axes.put(CapabilityAxis.SUSTAINED_DAMAGE, damage * Math.max(.05, rate));
        }
        if (tool != null) {
            double mining = Math.max(tool.defaultMiningSpeed(), stack.getDestroySpeed(Blocks.STONE.defaultBlockState()));
            mining = Math.max(mining, stack.getDestroySpeed(Blocks.OAK_LOG.defaultBlockState()));
            mining = Math.max(mining, stack.getDestroySpeed(Blocks.DIRT.defaultBlockState()));
            axes.put(CapabilityAxis.MINING_SPEED, mining);
            int harvest = stack.isCorrectToolForDrops(Blocks.OBSIDIAN.defaultBlockState()) ? 3
                    : stack.isCorrectToolForDrops(Blocks.DIAMOND_ORE.defaultBlockState()) ? 2
                    : stack.isCorrectToolForDrops(Blocks.IRON_ORE.defaultBlockState()) ? 1 : 0;
            axes.put(CapabilityAxis.HARVEST_LEVEL, (double) harvest);
            capabilities.add("tool_component");
        }
        axes.put(CapabilityAxis.DURABILITY, (double) stack.getMaxDamage());
        if (item.getEnchantmentValue() > 0) capabilities.add("enchantable:" + item.getEnchantmentValue());
        axes.put(CapabilityAxis.REACH, attribute(stack, Attributes.BLOCK_INTERACTION_RANGE, equipmentSlot, 4.5));
        double confidence = known ? .85 : .6;
        if (!BuiltInRegistries.ITEM.getKey(item).getNamespace().equals("minecraft")) {
            confidence = Math.min(confidence, .58); capabilities.add("unknown_dynamic_behavior");
        }
        for (var axis : axes.entrySet()) if (!Double.isFinite(axis.getValue()) || axis.getValue() < 0)
            throw new IllegalArgumentException("Invalid equipment axis " + axis.getKey() + " for " + id);
        return new EquipmentReference(id, slot, stage, axes, capabilities, reachable, false, confidence, "Measured default stack catalog reference; acquisition and recurring supply are unexamined");
    }

    private static double attribute(ItemStack stack, Holder<Attribute> attribute, EquipmentSlot slot, double base) {
        double[] operations = {0, 0, 1};
        stack.forEachModifier(slot, (holder, modifier) -> {
            if (!holder.equals(attribute)) return;
            double amount = modifier.amount();
            switch (modifier.operation()) {
                case ADD_VALUE -> operations[0] += amount;
                case ADD_MULTIPLIED_BASE -> operations[1] += amount;
                case ADD_MULTIPLIED_TOTAL -> operations[2] *= 1 + amount;
            }
        });
        double value = (base + operations[0]) * (1 + operations[1]) * operations[2];
        if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("Invalid equipment attribute result: " + value);
        return value;
    }

    /** Validate every native axis before its record is merged into the shared generation evidence. */
    private static void measuredAxisFacts(EvidenceSink staged, EvidenceFact.Subject subject, String id,
                                          Map<CapabilityAxis, Double> axes, String provider, double confidence,
                                          ProgressionBand stage, String reason) {
        for (var entry : axes.entrySet()) {
            if (!Double.isFinite(entry.getValue()) || entry.getValue() < 0)
                throw new IllegalArgumentException("Invalid measured " + entry.getKey() + " for " + id + ": " + entry.getValue());
            fact(staged, subject, id, "axis." + entry.getKey().name(), EvidenceFact.Value.number(entry.getValue()),
                    provider, confidence, stage, reason);
        }
    }

    private static double base(AttributeSupplier supplier, Holder<Attribute> attribute, double fallback) {
        return supplier.hasAttribute(attribute) ? supplier.getBaseValue(attribute) : fallback;
    }

    private static void fact(EvidenceSink sink, EvidenceFact.Subject subject, String id, String property,
                             EvidenceFact.Value value, String provider, double confidence, ProgressionBand stage, String reason) {
        EvidenceFact.Origin origin = provider.equals("equipment_components") || provider.equals("entity_attributes")
                ? EvidenceFact.Origin.OBSERVED : EvidenceFact.Origin.INFERRED;
        sink.add(new EvidenceFact(subject, id, property, value, provider, origin,
                confidence, 0, stage, List.of(), reason));
    }

    private static void applyOverrides(PackEvidenceContext context, EvidenceSink sink) {
        for (var override : context.overrides().facts()) {
            if (override.kind() == BalanceOverrides.SubjectKind.RECIPE
                    || override.kind() == BalanceOverrides.SubjectKind.RECIPE_FAMILY) continue;
            List<String> targets = new ArrayList<>(); EvidenceFact.Subject subject = ITEM;
            if (override.kind() == BalanceOverrides.SubjectKind.ENEMY) {
                subject = ENEMY;
                BuiltInRegistries.ENTITY_TYPE.keySet().stream().map(Object::toString).filter(id -> matches(id, override.selector())).sorted().forEach(targets::add);
            } else {
                for (Item item : BuiltInRegistries.ITEM) {
                    String id = BuiltInRegistries.ITEM.getKey(item).toString(); boolean match;
                    if (override.kind() == BalanceOverrides.SubjectKind.ITEM_TAG) {
                        ResourceLocation tag = ResourceLocation.tryParse(override.selector().replaceFirst("^#", ""));
                        match = tag != null && new ItemStack(item).is(TagKey.create(Registries.ITEM, tag));
                    } else if (override.kind() == BalanceOverrides.SubjectKind.BLOCK_TAG) {
                        ResourceLocation tag = ResourceLocation.tryParse(override.selector().replaceFirst("^#", ""));
                        match = item instanceof BlockItem block && tag != null && block.getBlock().defaultBlockState().is(TagKey.create(Registries.BLOCK, tag));
                    } else if (override.kind() == BalanceOverrides.SubjectKind.BLOCK) {
                        match = item instanceof BlockItem block && matches(BuiltInRegistries.BLOCK.getKey(block.getBlock()).toString(), override.selector());

                    } else match = matches(id, override.selector());
                    if (match) targets.add(id);
                }
            }
            if (targets.isEmpty()) sink.warn("Override " + override.id() + " at " + override.source() + ":" + override.line()
                    + " matched no registered subjects: " + override.selector());
            for (String target : targets.stream().sorted().toList()) {
                for (var entry : override.values().entrySet()) {
                    EvidenceFact.Value value;
                    if (entry.getValue() instanceof Number number) value = EvidenceFact.Value.number(number.doubleValue());
                    else if (entry.getValue() instanceof Boolean flag) value = EvidenceFact.Value.flag(flag);
                    else if (entry.getValue() instanceof List<?> list) value = EvidenceFact.Value.text(String.join(",", list.stream().map(Object::toString).toList()));
                    else value = EvidenceFact.Value.text(entry.getValue().toString());
                    sink.add(new EvidenceFact(subject, target, canonicalProperty(entry.getKey()), value, "override:" + override.id(),
                            EvidenceFact.Origin.OVERRIDE, override.number("confidence").orElse(1.0), override.priority(),
                            null, List.of(), override.text("reason").orElse(override.source() + ":" + override.line())));
                }
            }
        }
    }

    private static String canonicalProperty(String key) {
        return switch (key) {
            case "health" -> "axis.EFFECTIVE_HEALTH"; case "armor" -> "axis.ARMOR"; case "toughness" -> "axis.TOUGHNESS";
            case "damage" -> "axis.BURST_DAMAGE"; case "attack_speed" -> "axis.ATTACK_RATE";
            case "mining_speed" -> "axis.MINING_SPEED"; case "harvest_level" -> "axis.HARVEST_LEVEL";
            case "durability" -> "axis.DURABILITY";
            default -> key;
        };
    }

    private static double finiteProduct(double left, double right, String id) {
        double value = left * right;
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Equipment damage-rate product overflow for " + id);
        return value;
    }

    private static Map<CapabilityAxis, Double> resolvedAxes(EvidenceSink sink, EvidenceFact.Subject subject, String id, Map<CapabilityAxis, Double> base) {
        Map<CapabilityAxis, Double> axes = new EnumMap<>(CapabilityAxis.class); axes.putAll(base);
        for (CapabilityAxis axis : CapabilityAxis.values()) {
            var fact = sink.get(subject, id, "axis." + axis);
            if (fact == null) continue;
            if (fact.value().type() == EvidenceFact.ValueType.TEXT || fact.value().number() < 0)
                throw new IllegalArgumentException("Invalid nonnegative numeric/flag axis " + axis + " for " + id + " from " + fact.provider());
            axes.put(axis, fact.value().type() == EvidenceFact.ValueType.FLAG ? fact.value().flag() ? 1.0 : 0.0 : fact.value().number());
        }
        return axes;
    }
    private static boolean excluded(EvidenceSink sink, EvidenceFact.Subject subject, String id) {
        return List.of("disabled", "creative_only", "administrative", "joke").stream().anyMatch(key -> sink.flag(subject, id, key, false));
    }
    private static boolean matches(String id, String selector) {
        return id.matches(java.util.regex.Pattern.quote(selector).replace("*", "\\E.*\\Q"));
    }
    private static <E extends Enum<E>> E enumeration(Class<E> type, String text) { return Enum.valueOf(type, text.toUpperCase(Locale.ROOT)); }
}
