package com.mistaboom.essence_ascendance.balance.engine;

import net.minecraft.core.Holder;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.*;

/** Factual native effect projection shared with the capability reader. Economic normalization is explicit;
 * its scores never feed back into skill power or native capability magnitudes. */
public final class EnchantmentSemantics {
    private EnchantmentSemantics() { }
    // Record equality/hashCode can traverse opaque custom effect implementations. Identity
    // keys avoid invoking those callbacks; a bound also releases old registry generations.
    private static final int CACHE_DEFINITIONS = 4096;
    private static final Map<Enchantment, Map<Integer, Facts>> CACHE = new IdentityHashMap<>();
    public record Facts(Map<String, Double> routes, double strength, List<String> evidence) {
        public Facts { routes = Map.copyOf(routes); evidence = List.copyOf(evidence); }
        public static Facts unknown(String reason) { return new Facts(Map.of(), 0, List.of(reason)); }
    }

    public static Facts analyze(Holder<Enchantment> holder, int requestedLevel) {
        if (holder.is(EnchantmentTags.CURSE)) return Facts.unknown("Curse: no positive effect value");
        Enchantment enchantment = holder.value();
        int level = Math.max(1, Math.min(255, requestedLevel));
        synchronized (CACHE) {
            if (!CACHE.containsKey(enchantment) && CACHE.size() >= CACHE_DEFINITIONS) CACHE.clear();
            Map<Integer, Facts> levels = CACHE.computeIfAbsent(enchantment, ignored -> new HashMap<>());
            Facts previous = null;
            for (int rank = 1; rank <= level; rank++) {
                Facts facts = levels.get(rank);
                if (facts == null) {
                    facts = read(enchantment, rank);
                    // Positive-start definitions retain the anvil score bound, including a
                    // later zero/rebound. A zero-first definition may acquire a real positive
                    // effect later; the stack resolver funds that onset from one shared carrier
                    // allowance rather than inventing positive level-I strength.
                    if (previous != null && (previous.strength() > 0 || levels.get(1).strength() > 0)
                            && facts.strength() > 2 * previous.strength())
                        facts = new Facts(facts.routes(), 2 * previous.strength(), facts.evidence());
                    levels.put(rank, facts);
                }
                previous = facts;
            }
            return previous;
        }
    }

    private static Facts read(Enchantment enchantment, int level) {
        try {
            var sink = new CapabilitySink();
            var measurements = NativeCapabilityReader.enchantmentMeasurements("stack", enchantment, level, true, sink);
            Map<CapabilityAxis, Double> axes = new EnumMap<>(CapabilityAxis.class);
            List<String> evidence = new ArrayList<>();
            for (var measurement : measurements) {
                if (measurement.magnitude() == null || measurement.magnitude() <= 0) continue;
                double strength = normalizedStrength(measurement);
                if (!(strength > 0) || !Double.isFinite(strength)) continue;
                // Conditional peaks are neither universal uptime nor additive duplicate power.
                if (measurement.applicability().contains("conditional=")) strength *= .5;
                axes.merge(measurement.axis(), strength, Math::max);
                evidence.add(measurement.axis() + "=" + measurement.magnitude() + " " + measurement.unit());
            }
            Map<String, Double> routes = new TreeMap<>();
            axes.forEach((axis, strength) -> routes.merge(category(axis), strength, Double::sum));
            if (!routes.isEmpty()) {
                // Native effort is secondary to observed effects. This retains level distinctions
                // when the magnitude is fixed but an opaque condition changes with level (Thorns,
                // for example), without claiming its unknown proc chance as sustained power.
                double effort = .1 * Math.log1p(Math.max(0, enchantment.getMinCost(level) - enchantment.getMinCost(1)));
                if (effort > 0) evidence.add("Secondary native level effort=" + effort + "; no proc-rate inference");
                return new Facts(routes, axes.values().stream().mapToDouble(Double::doubleValue).sum() + effort, evidence);
            }
            // A definition with opaque behavior cannot borrow power from its equipment tag.
            // Empty-effect definitions (native loot-predicate enchantments, for example) have
            // only supported-equipment evidence; retain a small, explicitly uncertain score.
            if (!enchantment.effects().isEmpty() && !NativeEnchantmentEffects.onlyExperienceSuppression(enchantment, level))
                return Facts.unknown("Unsupported effect definition; no magnitude inferred");
            Set<String> supported = new TreeSet<>();
            for (var item : enchantment.getSupportedItems()) {
                Item value = item.value();
                //? if >=26.1.2
                /*ItemStack stack = new ItemStack(value);*/
                //? if >=26.1.2
                /*if (stack.has(net.minecraft.core.component.DataComponents.TOOL) && !stack.is(net.minecraft.tags.ItemTags.SWORDS) || value instanceof FishingRodItem || value instanceof ShearsItem) supported.add("gathering");*/
                //? if >=26.1.2
                /*else if (stack.has(net.minecraft.core.component.DataComponents.BLOCKS_ATTACKS) || stack.is(net.minecraft.tags.ItemTags.HEAD_ARMOR) || stack.is(net.minecraft.tags.ItemTags.CHEST_ARMOR) || stack.is(net.minecraft.tags.ItemTags.LEG_ARMOR) || stack.is(net.minecraft.tags.ItemTags.FOOT_ARMOR) || stack.getOrDefault(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS, net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY).modifiers().stream().anyMatch(modifier -> modifier.attribute().equals(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR) || modifier.attribute().equals(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS))) supported.add("defense");*/
                //? if >=26.1.2
                /*else if (value instanceof ProjectileWeaponItem || stack.is(net.minecraft.tags.ItemTags.SWORDS) || stack.has(net.minecraft.core.component.DataComponents.WEAPON) && !stack.has(net.minecraft.core.component.DataComponents.TOOL) || value instanceof TridentItem || value instanceof MaceItem) supported.add("offense");*/
                //? if >=26.1.2
                /*else if (stack.has(net.minecraft.core.component.DataComponents.GLIDER)) supported.add("mobility");*/
                //? if <26.1.2
                if (value instanceof DiggerItem || value instanceof FishingRodItem || value instanceof ShearsItem) supported.add("gathering");
                //? if <26.1.2
                else if (value instanceof ArmorItem || value instanceof ShieldItem) supported.add("defense");
                //? if <26.1.2
                else if (value instanceof ProjectileWeaponItem || value instanceof SwordItem || value instanceof TridentItem || value instanceof MaceItem) supported.add("offense");
                //? if <26.1.2
                else if (value instanceof ElytraItem) supported.add("mobility");
            }
            if (supported.isEmpty()) return Facts.unknown("No supported positive effect or equipment evidence");
            supported.forEach(category -> routes.put(category, 1.0 / supported.size()));
            return new Facts(routes, .25 * level * (1 + Math.log1p(Math.max(0, enchantment.getAnvilCost())) / 2),
                    List.of("Supported equipment and native anvil effort only; effect and throughput remain unknown"));
        } catch (RuntimeException | LinkageError failure) {
            return Facts.unknown("Effect reading failed: " + failure.getClass().getSimpleName());
        }
    }

    /** Reference units, not prices: one HP, protection point, effective extra lifetime,
     * durability/XP, or two native mining-efficiency points. */
    public static double normalizedStrength(CapabilityEvidence.Measurement measurement) {
        double amount = measurement.magnitude();
        String unit = measurement.unit();
        if (measurement.axis() == CapabilityAxis.DURABILITY_REDUCTION) {
            if (unit.contains("binomial_probability") || unit.contains("fraction_reduced"))
                return amount < 1 ? amount / (1 - amount) : 64;
            return unit.contains("wear_subtraction") ? amount : 0;
        }
        if (unit.contains(":multiply") && measurement.axis() != CapabilityAxis.REPAIR)
            amount = Math.max(0, amount - 1);
        if (unit.equals("attribute_multiplier_addition")) return amount / .1;
        if (unit.startsWith("equipment_drop_probability")) return amount / .01;
        if (unit.startsWith("charge_seconds_reduced")) return amount / .25;
        if (unit.equals("ignite_seconds")) return amount / 4;
        return switch (measurement.axis()) {
            case MINING_SPEED -> amount / 2;
            case GROUND_SPEED -> amount / .1;
            case INDESTRUCTIBILITY -> 8;
            default -> amount;
        };
    }

    public static String category(CapabilityAxis axis) {
        return switch (axis) {
            case SUSTAINED_DAMAGE, BURST_DAMAGE, AREA_DAMAGE, ATTACK_RATE, RANGE, DELIVERY_RELIABILITY,
                    ARMOR_PENETRATION, SHIELD_INTERACTION, CROWD_CONTROL, DAMAGE_OVER_TIME,
                    MELEE_DAMAGE, RANGED_DAMAGE, MAGIC_DAMAGE, PROJECTILE_SPEED, CRITICAL_DAMAGE -> "offense";
            case EFFECTIVE_HEALTH, ARMOR, TOUGHNESS, DAMAGE_REDUCTION, AVOIDANCE, BLOCKING, REFLECTION,
                    KNOCKBACK_RESISTANCE, DAMAGE_IMMUNITY, SHIELD_CAPACITY -> "defense";
            case RECOVERY, HEALING, REGENERATION, STATUS_RESISTANCE, MAX_HEALTH -> "vitality";
            case GROUND_SPEED, JUMP, VERTICAL_MOVEMENT, FALL_CONTROL, GLIDING, FLIGHT,
                    ABILITIES_FLYING_SPEED, TELEPORTATION, STEP_HEIGHT -> "mobility";
            case MINING_SPEED, AREA_MINING, VEIN_MINING, HARVEST_LEVEL, CROP_YIELD, DROP_YIELD,
                    HARVEST_SPEED, FISHING_PRODUCTIVITY, FISHING_TIME_REDUCTION -> "gathering";
            default -> "utility";
        };
    }
}
