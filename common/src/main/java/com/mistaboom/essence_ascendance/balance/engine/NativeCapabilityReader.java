package com.mistaboom.essence_ascendance.balance.engine;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.item.enchantment.effects.*;
import java.util.*;
import static com.mistaboom.essence_ascendance.balance.engine.CapabilityEvidence.*;

/** Reads native definitions, never invokes enchantment callbacks, random effects, players or gameplay actions. */
public final class NativeCapabilityReader {
    private NativeCapabilityReader() { }
    public static void collect(PackEvidenceContext context, Map<String, ResourceEvidence> resources, CapabilitySink sink) {
        NativeItemActions.collect(context, resources, sink);
        NativeConsumables.collect(context, resources, sink);
        for (var item : context.inputs().items()) {
            sink.analyzed();
            String id = BuiltInRegistries.ITEM.getKey(item).toString();
            if (id.startsWith("essence_ascendance:")) continue;
            record(id, "native_components", "read effective item " + item.getClass().getName(), staged -> {
                ItemStack stack = new ItemStack(item);
                var placement = CompetitiveCapabilities.placement(resources, id);
                readStack(stack, id, "effective_default", placement, staged);
                candidateName(id, staged);
            }, sink);
        }
        record("configured_recipe_outputs", "configured_stacks", "read configured stack section", staged ->
                ConfiguredStackCapabilities.collect(context.inputs().production(), context.server().registryAccess(), resources,
                        context.inputs().configurationConstrainedItems(), staged), sink);
        var registry = OptionalIntegration.attempt("native_enchantments", "read effective enchantment registry",
                () -> context.server().registryAccess().registryOrThrow(Registries.ENCHANTMENT).holders().toList());
        if (!registry.succeeded()) {
            sink.candidate("enchantment_registry", "native_enchantments", "UNKNOWN: effective enchantment registry unavailable; " + registry.failure());
            return;
        }
        for (var holder : registry.value().orElseThrow()) {
            sink.analyzed();
            String id = holder.key().location().toString();
            record(id, "native_enchantments", "read effective enchantment and definition", staged -> {
                Enchantment enchantment = holder.value();
                // Definition maximum is potential, not proof that this level can be acquired or applied.
                readEnchantmentDefinition(id, id, enchantment, enchantment.getMaxLevel(),
                        new CompetitiveCapabilities.Placement(ProgressionBand.APEX, false, .9, List.of()), false, staged);
                var ops = RegistryOps.create(JsonOps.INSTANCE, context.server().registryAccess());
                var encoded = Enchantment.DIRECT_CODEC.encodeStart(ops, enchantment).getOrThrow();
                if (!encoded.isJsonObject()) throw new IllegalStateException("Effective enchantment codec returned a non-object definition");
                staged.definition("native_enchantments", id, encoded);
                JsonObject effects = encoded.getAsJsonObject().getAsJsonObject("effects");
                if (effects != null) for (var effect : effects.entrySet()) {
                    if (!SUPPORTED.contains(effect.getKey())) staged.candidate(id, "native_enchantments",
                            "Unsupported effect component " + effect.getKey() + "; definition=" + bounded(effect.getValue()));
                }
            }, sink);
        }
    }
    public static void stack(ItemStack stack, String id, String configuration, CompetitiveCapabilities.Placement placement, CapabilitySink sink) {
        record(id, "native_components", "read configured stack " + configuration,
                staged -> readStack(stack, id, configuration, placement, staged), sink);
    }
    private static void readStack(ItemStack stack, String id, String configuration, CompetitiveCapabilities.Placement placement, CapabilitySink sink) {
            if (!configuration.equals("effective_default")) NativeConsumables.readStack(stack, id, configuration, placement, sink);
            stackAttributes(stack, id, configuration, placement, sink);
            if (stack.has(DataComponents.UNBREAKABLE) && stack.getOrDefault(DataComponents.MAX_DAMAGE, 0) > 0) {
                String slot = stack.getItem() instanceof net.minecraft.world.item.ArmorItem armor ? armor.getEquipmentSlot().getName()
                        : stack.getItem() instanceof net.minecraft.world.item.ElytraItem ? "chest"
                        : stack.getItem() instanceof net.minecraft.world.item.ShieldItem ? "offhand" : "mainhand";
                var m = CompetitiveCapabilities.measurement(CapabilityAxis.INDESTRUCTIBILITY, 1.0, "presence",
                        "equipment slot=" + slot + "; source-bound native wear pool",
                        new Scope("source_equipment:" + id, "single", null, 1.0), Operation.manual(), "native_components", Origin.NATIVE,
                        List.of("Preserves this configured item's native wear pool; does not repair or protect other equipped items"));
                sink.add(new Functional(new CapabilityEvidence(id, placement.stage(), Map.of(), placement.reachable(), Math.min(.95, placement.confidence()),
                        "Effective stack UNBREAKABLE component with positive MAX_DAMAGE"), configuration + "/unbreakable", List.of(m), placement.acquisition(), placement.reachable()));
            }
            if (!configuration.equals("effective_default") && stack.has(DataComponents.MAX_DAMAGE)) {
                var m = CompetitiveCapabilities.measurement(CapabilityAxis.DURABILITY, stack.get(DataComponents.MAX_DAMAGE).doubleValue(), "uses",
                        "configured equipped item", CompetitiveCapabilities.equipmentScope(CapabilityAxis.DURABILITY), Operation.manual(),
                        "native_components", Origin.NATIVE, List.of("Other wear/repair mechanisms not composed"));
                sink.add(new Functional(new CapabilityEvidence(id, placement.stage(), Map.of(), placement.reachable(), placement.confidence(),
                        "Static configured MAX_DAMAGE component"), configuration + "/durability", List.of(m), placement.acquisition(), placement.reachable()));
            }
            var enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
            for (var enchantment : enchantments.entrySet()) {
                var key = enchantment.getKey().unwrapKey();
                readEnchantment(id, configuration + "/" + key.map(k -> k.location().toString()).orElse("unregistered"), enchantment.getKey().value(),
                        enchantment.getIntValue(), placement, true, sink);
            }
    }
    private static final Set<String> SUPPORTED = Set.of("minecraft:attributes", "minecraft:damage", "minecraft:damage_protection",
            "minecraft:item_damage", "minecraft:repair_with_xp", "minecraft:fishing_time_reduction", "minecraft:fishing_luck_bonus");
    private static String bounded(JsonElement data) { String value = data.toString(); return value.length() > 1000 ? value.substring(0, 1000) + "…" : value; }
    public static void readEnchantment(String source, String enchantmentId, Enchantment enchantment, int level,
                                       CompetitiveCapabilities.Placement placement, boolean effectiveStack, CapabilitySink sink) {
        record(enchantmentId, "native_enchantments", "read enchantment for " + source + " level=" + level,
                staged -> readEnchantmentDefinition(source, enchantmentId, enchantment, level, placement, effectiveStack, staged), sink);
    }
    private static void readEnchantmentDefinition(String source, String enchantmentId, Enchantment enchantment, int level,
                                       CompetitiveCapabilities.Placement placement, boolean effectiveStack, CapabilitySink sink) {
        List<Measurement> measurements = new ArrayList<>();
        List<String> commonUnknown = effectiveStack ? List.of() : List.of("Definition maximum; attainable level/application/setup requires typed acquisition proof");
        for (EnchantmentAttributeEffect effect : enchantment.getEffects(EnchantmentEffectComponents.ATTRIBUTES)) {
            String attribute = effect.attribute().unwrapKey().map(k -> k.location().toString()).orElse("unknown");
            CapabilityAxis axis = attributeAxis(attribute);
            Double amount = levelValue(effect.amount(), level);
            if (axis == null || amount == null || amount < 0) {
                sink.candidate(enchantmentId, "native_enchantments", "Unsupported attribute/negative transformation " + attribute); continue;
            }
            String unit = effect.operation() == AttributeModifier.Operation.ADD_VALUE ? "attribute_addition" : "attribute_multiplier_addition";
            measurements.add(CompetitiveCapabilities.measurement(axis, amount, unit,
                    "attribute=" + attribute + "; operation=" + effect.operation() + "; slots=" + enchantment.definition().slots(),
                    Scope.self(), Operation.manual(), "native_enchantments", Origin.NATIVE, commonUnknown));
        }
        valueEffects(enchantment, EnchantmentEffectComponents.DAMAGE, CapabilityAxis.BURST_DAMAGE, "health_points", level, measurements, commonUnknown, sink, enchantmentId);
        valueEffects(enchantment, EnchantmentEffectComponents.DAMAGE_PROTECTION, CapabilityAxis.DAMAGE_REDUCTION, "enchantment_protection_points", level, measurements, commonUnknown, sink, enchantmentId);
        valueEffects(enchantment, EnchantmentEffectComponents.ITEM_DAMAGE, CapabilityAxis.DURABILITY_REDUCTION, "wear_transform", level, measurements, commonUnknown, sink, enchantmentId);
        valueEffects(enchantment, EnchantmentEffectComponents.REPAIR_WITH_XP, CapabilityAxis.REPAIR, "durability_per_xp", level, measurements, commonUnknown, sink, enchantmentId);
        valueEffects(enchantment, EnchantmentEffectComponents.FISHING_TIME_REDUCTION, CapabilityAxis.FISHING_TIME_REDUCTION, "native_wait_reduction", level, measurements, commonUnknown, sink, enchantmentId);
        valueEffects(enchantment, EnchantmentEffectComponents.FISHING_LUCK_BONUS, CapabilityAxis.FISHING_PRODUCTIVITY, "native_fishing_luck", level, measurements, commonUnknown, sink, enchantmentId);
        if (!measurements.isEmpty()) sink.add(new Functional(new CapabilityEvidence(source, placement.stage(), Map.of(),
                placement.reachable(), placement.reachable() ? Math.min(.9, placement.confidence()) : .9, "Effective enchantment " + enchantmentId + " level=" + level),
                "enchantment:" + enchantmentId + ":" + level, measurements, placement.acquisition(), effectiveStack && placement.reachable()));
        if (!effectiveStack) sink.candidate(enchantmentId, "native_enchantments", "Maximum level " + level + " retained as potential; acquisition/application unproven");
    }
    private static void valueEffects(Enchantment enchantment,
            net.minecraft.core.component.DataComponentType<List<ConditionalEffect<EnchantmentValueEffect>>> component,
            CapabilityAxis axis, String unit, int level, List<Measurement> output, List<String> commonUnknown,
            CapabilitySink sink, String id) {
        for (var conditional : enchantment.getEffects(component)) {
            CapabilityAxis measuredAxis = axis;
            var effect = conditional.effect(); Double amount = null; String transform = "unknown";
            if (effect instanceof AddValue add) { amount = levelValue(add.value(), level); transform = "add"; }
            else if (effect instanceof MultiplyValue multiply) { amount = levelValue(multiply.factor(), level); transform = "multiply"; }
            else if (effect instanceof SetValue set) { amount = levelValue(set.value(), level); transform = "set"; }
            else if (effect instanceof RemoveBinomial binomial && axis == CapabilityAxis.DURABILITY_REDUCTION) {
                amount = levelValue(binomial.chance(), level); transform = "binomial_probability";
                if (amount != null && amount > 1) amount = null;
            }
            if (axis == CapabilityAxis.DURABILITY_REDUCTION && transform.equals("multiply") && amount != null) {
                amount = amount <= 1 ? 1 - amount : null; transform = "fraction_reduced";
            }
            if (axis == CapabilityAxis.DURABILITY_REDUCTION && transform.equals("add") && amount != null) {
                if (amount >= 0) continue; // Increased wear is not a preservation competitor.
                amount = -amount; transform = "wear_subtraction";
            }
            if (axis == CapabilityAxis.DURABILITY_REDUCTION && transform.equals("set") && amount != null) {
                if (amount != 0) { sink.candidate(id, "native_enchantments", "Fixed wear amount needs baseline/event semantics; not a reduction ratio"); continue; }
                measuredAxis = CapabilityAxis.INDESTRUCTIBILITY; amount = 1.0; transform = "zero_native_wear";
            }
            if (amount == null || amount < 0) { sink.candidate(id, "native_enchantments", "Unsupported value effect " + effect.getClass().getName()); continue; }
            List<String> unknown = new ArrayList<>(commonUnknown);
            String applies = "effect=" + BuiltInRegistries.ENCHANTMENT_EFFECT_COMPONENT_TYPE.getKey(component) + "; transform=" + transform;
            if (conditional.requirements().isPresent()) {
                // Conditions are not evaluated against invented targets. Conditional peaks are separate from unconditional power.
                var condition = net.minecraft.world.level.storage.loot.predicates.LootItemCondition.DIRECT_CODEC
                        .encodeStart(JsonOps.INSTANCE, conditional.requirements().get()).result();
                applies += "; conditional=" + condition.map(Object::toString).orElse("unencoded:" + conditional.requirements().get().getClass().getName());
                unknown.add("Conditional peak; predicate match frequency/uptime unmeasured");
                if (condition.isEmpty()) { sink.candidate(id, "native_enchantments", "Conditional effect predicate cannot encode; numeric frontier excluded"); amount = null; }
            }
            output.add(CompetitiveCapabilities.measurement(measuredAxis, amount, measuredAxis == CapabilityAxis.INDESTRUCTIBILITY ? "presence" : unit + ":" + transform, applies,
                    CompetitiveCapabilities.equipmentScope(measuredAxis), Operation.manual(), "native_enchantments", Origin.NATIVE, unknown));
        }
    }
    public static void stackAttributes(ItemStack stack, String id, CompetitiveCapabilities.Placement placement, CapabilitySink sink) {
        record(id, "native_attributes", "read effective stack attributes",
                staged -> stackAttributes(stack, id, "native_attribute_contributions", placement, staged), sink);
    }
    /** Every native record is detached until its getters/codecs complete. A broken custom component
     * excludes its own facts without discarding unrelated items or enchantments. */
    private static void record(String id, String provider, String operation, java.util.function.Consumer<CapabilitySink> reader, CapabilitySink sink) {
        var attempt = OptionalIntegration.attempt(provider + ":" + id, operation, () -> {
            CapabilitySink staged = new CapabilitySink();
            reader.accept(staged);
            return staged;
        });
        if (attempt.succeeded()) sink.merge(attempt.value().orElseThrow());
        else sink.candidate(id, provider, "UNKNOWN: " + operation + " failed; affected capability evidence excluded; " + attempt.failure());
    }
    private static void stackAttributes(ItemStack stack, String id, String configuration, CompetitiveCapabilities.Placement placement, CapabilitySink sink) {
        var attrs = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY);
        if (attrs.modifiers().isEmpty()) attrs = stack.getItem().getDefaultAttributeModifiers();
        record Key(CapabilityAxis axis, String attribute, AttributeModifier.Operation operation, String slot) { }
        Map<Key, Double> values = new LinkedHashMap<>();
        for (var entry : attrs.modifiers()) {
            String attribute = entry.attribute().unwrapKey().map(k -> k.location().toString()).orElse("unregistered");
            CapabilityAxis axis = attributeAxis(attribute);
            if (axis == null) { sink.candidate(id, "native_attributes", "Attribute semantics unsupported: " + attribute); continue; }
            for (var slot : net.minecraft.world.entity.EquipmentSlot.values()) {
                if (slot == net.minecraft.world.entity.EquipmentSlot.BODY || !entry.slot().test(slot)) continue;
                var key = new Key(axis, attribute, entry.modifier().operation(), slot.getName());
                double value = entry.modifier().amount();
                if (!Double.isFinite(value)) { sink.candidate(id, "native_attributes", "Nonfinite effective attribute excluded"); continue; }
                if (entry.modifier().operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                    values.merge(key, 1 + value, (a, b) -> a * b);
                else values.merge(key, value, Double::sum);
            }
        }
        List<Measurement> measurements = new ArrayList<>();
        values.forEach((key, value) -> {
            boolean total = key.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
            if (!Double.isFinite(value) || value <= (total ? 1 : 0)) return;
            measurements.add(CompetitiveCapabilities.measurement(key.axis(), value,
                    total ? "attribute_total_factor" : key.operation() == AttributeModifier.Operation.ADD_VALUE ? "attribute_addition" : "attribute_multiplier_addition",
                    "attribute=" + key.attribute() + "; operation=" + key.operation() + "; slot=" + key.slot(), Scope.self(), Operation.manual(),
                    "native_attributes", Origin.NATIVE, List.of("Native modifier contribution; base stat, other gear and custom event modifiers not composed")));
        });
        if (!measurements.isEmpty()) sink.add(new Functional(new CapabilityEvidence(id, placement.stage(), Map.of(), placement.reachable(),
                Math.min(.9, placement.confidence()), "Effective slot-filtered native attribute operations"), configuration, measurements,
                placement.acquisition(), placement.reachable()));
    }
    /** Never execute a custom level implementation. */
    private static Double levelValue(LevelBasedValue value, int level) {
        if (nativeLevel(value, 0)) {
            double result = value.calculate(level); return Double.isFinite(result) ? result : null;
        }
        return null;
    }
    private static boolean nativeLevel(LevelBasedValue value, int depth) {
        if (depth > 32) return false;
        if (value instanceof LevelBasedValue.Constant || value instanceof LevelBasedValue.Linear || value instanceof LevelBasedValue.LevelsSquared) return true;
        if (value instanceof LevelBasedValue.Fraction f) return nativeLevel(f.numerator(), depth + 1) && nativeLevel(f.denominator(), depth + 1);
        if (value instanceof LevelBasedValue.Clamped c) return nativeLevel(c.value(), depth + 1);
        if (value instanceof LevelBasedValue.Lookup l) return nativeLevel(l.fallback(), depth + 1);
        return false;
    }
    public static CapabilityAxis attributeAxis(String id) {
        return switch (id) {
            case "minecraft:player.mining_efficiency", "minecraft:player.block_break_speed" -> CapabilityAxis.MINING_SPEED;
            case "minecraft:generic.attack_damage" -> CapabilityAxis.MELEE_DAMAGE;
            case "minecraft:generic.attack_speed" -> CapabilityAxis.ATTACK_RATE;
            case "minecraft:generic.armor" -> CapabilityAxis.ARMOR;
            case "minecraft:generic.armor_toughness" -> CapabilityAxis.TOUGHNESS;
            case "minecraft:generic.max_health" -> CapabilityAxis.MAX_HEALTH;
            case "minecraft:generic.knockback_resistance" -> CapabilityAxis.KNOCKBACK_RESISTANCE;
            case "minecraft:generic.movement_speed" -> CapabilityAxis.GROUND_SPEED;
            case "minecraft:generic.step_height" -> CapabilityAxis.STEP_HEIGHT;
            case "minecraft:generic.jump_strength" -> CapabilityAxis.JUMP;
            case "minecraft:player.block_interaction_range", "minecraft:player.entity_interaction_range" -> CapabilityAxis.REACH;
            default -> null;
        };
    }
    public static void candidateName(String id, CapabilitySink sink) {
        String path = id.substring(id.indexOf(':') + 1);
        Set<String> tokens = new HashSet<>(List.of(path.split("[_/.-]+")));
        if (tokens.stream().anyMatch(Set.of("quarry", "fisher", "fishing", "accelerator", "growth", "twerk", "indestructible", "unbreakable", "affix", "socket", "module", "jetpack", "spell")::contains))
            sink.candidate(id, "nomenclature", "Potential high-impact function/configuration; names establish no magnitude, rate or access");
    }
}
