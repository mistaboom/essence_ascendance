package com.mistaboom.essence_ascendance.balance.runtime;

import com.mistaboom.essence_ascendance.balance.engine.PackEvidence;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.EmptyBlockGetter;
import com.mistaboom.essence_ascendance.config.ShieldBalanceSettings;
import com.mistaboom.essence_ascendance.equipment.EquipmentShieldService;
import com.mistaboom.essence_ascendance.stat.StatDefinition;

import java.util.*;

/** Native measurements used only while generating resolved Bonuses; never reads a player or world. */
public final class NativeBonusMechanics {
    // Vanilla FishingHook catchingFish: 100..600 initial ticks before native Lure subtraction.
    public static final int FISHING_MINIMUM_WAIT_TICKS = 100;
    public static final double FISHING_MAXIMUM_SPEED_PERCENT = (FISHING_MINIMUM_WAIT_TICKS - 1) * 100.0;
    private NativeBonusMechanics() { }
    // Group nearby surfaces into one useful progression checkpoint, using the half-block building rhythm.
    // Each endpoint is an observed surface in the group, never the rounded bin edge; purchases remain continuous.
    private static final double SURFACE_GROUP = .5;

    public record StepResolution(double base, double maximum, List<Double> thresholds,
                                 double confidence, String source, List<String> evidence) {
        public StepResolution { thresholds = List.copyOf(thresholds); evidence = List.copyOf(evidence); }
    }

    public static double baseline(Holder<Attribute> attribute) {
        if (RuntimeReferencePolicy.usingBootstrapReferences()) return attribute.value().getDefaultValue();
        var player = Player.createAttributes().build();
        return player.hasAttribute(attribute) ? player.getValue(attribute) : attribute.value().getDefaultValue();
    }

    /** Shared empty-world collision measurement. Context-dependent blocks may throw and require caller policy. */
    public static double collisionTop(net.minecraft.world.level.block.state.BlockState state) {
        var shape = state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        if (shape.isEmpty()) return 0;
        double top = shape.max(Direction.Axis.Y);
        if (!Double.isFinite(top) || top < 0) throw new IllegalArgumentException("Invalid native collision height");
        return top;
    }

    /** Percentage points for an ADD_VALUE modifier whose gameplay conversion divides by 100. */
    public static double additivePercentHeadroom(Holder<Attribute> attribute) {
        return additiveHeadroom(attribute) * 100;
    }

    public static double additiveHeadroom(Holder<Attribute> attribute) {
        return Math.max(0, attribute.value().sanitizeValue(Double.MAX_VALUE) - baseline(attribute));
    }

    /** Percentage points for ADD_MULTIPLIED_BASE, at the native unmodified player baseline. */
    public static double multipliedBasePercentHeadroom(Holder<Attribute> attribute) {
        double base = baseline(attribute);
        return base <= 0 ? 0 : Math.max(0, attribute.value().sanitizeValue(Double.MAX_VALUE) / base - 1) * 100;
    }

    /** The supported vanilla disable hook supplies 100 ticks; longer external disables need factual evidence. */
    public static final int NATIVE_SHIELD_DISABLE_TICKS = 100;
    public static double guardReadinessMaximum(int minimumDisableTicks) {
        double raise = 100.0 * (EquipmentShieldService.VANILLA_RAISE_DELAY_TICKS
                - EquipmentShieldService.MINIMUM_RAISE_DELAY_TICKS) / EquipmentShieldService.VANILLA_RAISE_DELAY_TICKS;
        double recovery = 100.0 * Math.max(0, NATIVE_SHIELD_DISABLE_TICKS - minimumDisableTicks) / NATIVE_SHIELD_DISABLE_TICKS;
        return Math.max(raise, recovery);
    }

    public record Limit(double maximum, String evidence) { }

    /** Mechanical ceilings only. Pricing/power policy and unknown external capabilities do not supply headroom. */
    public static Optional<Limit> limit(StatDefinition stat) {
        return limit(stat, ShieldBalanceSettings.defaults());
    }

    public static Optional<Limit> limit(StatDefinition stat, ShieldBalanceSettings shield) {
        return switch (stat.id().getPath()) {
            case "guarded_movement" -> Optional.of(new Limit(100,
                    "ShieldMath.movementMultiplier removes at most all native blocking slowdown; percentage clamps at 100"));
            case "guard_readiness" -> Optional.of(new Limit(guardReadinessMaximum(shield.minimumDisableTicks()),
                    "Native shield raise 5 -> 1 ticks and supported vanilla disable 100 -> "
                            + shield.minimumDisableTicks()
                            + " ticks; no longer external disable duration is inferred"));
            case "swim_speed" -> Optional.of(new Limit(additivePercentHeadroom(Attributes.WATER_MOVEMENT_EFFICIENCY),
                    "Native water-movement attribute ADD_VALUE headroom, converted to percentage points"));
            case "sneak_speed" -> Optional.of(new Limit(multipliedBasePercentHeadroom(Attributes.SNEAKING_SPEED),
                    "Native sneaking-speed ADD_MULTIPLIED_BASE headroom at unmodified player baseline; other equipment modifiers may consume headroom"));
            case "attack_knockback" -> Optional.of(new Limit(additivePercentHeadroom(Attributes.ATTACK_KNOCKBACK),
                    "Native attack-knockback ADD_VALUE headroom; one percentage point adds 0.01 attribute units"));
            case "knockback_resistance" -> Optional.of(new Limit(additivePercentHeadroom(Attributes.KNOCKBACK_RESISTANCE),
                    "Native knockback-resistance ADD_VALUE headroom; one percentage point adds 0.01 attribute units"));
            case "max_health" -> Optional.of(new Limit(additiveHeadroom(Attributes.MAX_HEALTH) / 2,
                    "Native maximum-health ADD_VALUE headroom; one saved heart adds two native HP"));
            case "reach" -> Optional.of(new Limit(Math.min(additiveHeadroom(Attributes.BLOCK_INTERACTION_RANGE),
                    additiveHeadroom(Attributes.ENTITY_INTERACTION_RANGE)),
                    "Shared reach adds blocks to both native interaction ranges; use the lesser headroom so neither benefit saturates early"));
            case "luck" -> Optional.of(new Limit(additiveHeadroom(Attributes.LUCK),
                    "Native Luck ADD_VALUE headroom; loot must separately support quality weighting"));
            case "movement_speed" -> Optional.of(new Limit(multipliedBasePercentHeadroom(Attributes.MOVEMENT_SPEED),
                    "Native movement-speed ADD_MULTIPLIED_BASE headroom; terrain and external modifiers remain contextual"));
            case "mining_speed" -> Optional.of(new Limit(multipliedBasePercentHeadroom(Attributes.BLOCK_BREAK_SPEED),
                    "Native block-break-speed ADD_MULTIPLIED_BASE headroom; tool, hardness and block completion remain contextual"));
            case "jump_height" -> Optional.of(new Limit(multipliedBasePercentHeadroom(Attributes.JUMP_STRENGTH),
                    "Native jump-strength ADD_MULTIPLIED_BASE headroom; this is impulse percentage, not linear ballistic height"));
            default -> Optional.empty();
        };
    }

    public static StepResolution step(PackEvidence evidence, double requestedMaximum) {
        double base = baseline(Attributes.STEP_HEIGHT);
        double maximum = Math.max(0, Math.min(requestedMaximum,
                Attributes.STEP_HEIGHT.value().sanitizeValue(base + requestedMaximum) - base));
        Map<Double, List<String>> surfaces = new TreeMap<>();
        double confidence = 1;
        List<String> observations = new ArrayList<>();
        int inspected = 0, skipped = 0;
        if (!RuntimeReferencePolicy.usingBootstrapReferences()) {
            for (var block : BuiltInRegistries.BLOCK) {
                String itemId = BuiltInRegistries.ITEM.getKey(block.asItem()).toString();
                var resource = evidence.resources().get(itemId);
                if (resource == null || !resource.reachable()) continue;
                try {
                    double height = collisionTop(block.defaultBlockState());
                    inspected++;
                    if (height == 0) continue;
                    if (!Double.isFinite(height) || height <= base || height > base + maximum + 1e-7) continue;
                    surfaces.computeIfAbsent(height, unused -> new ArrayList<>()).add(itemId);
                    confidence = Math.min(confidence, resource.confidence());
                } catch (RuntimeException unavailableContext) {
                    // Opaque neighbor/world-dependent block behavior is not guessed from a name.
                    skipped++;
                    observations.add("Unmeasured collision shape " + BuiltInRegistries.BLOCK.getKey(block)
                            + ": requires context unavailable to deterministic empty-world sampling");
                }
            }
        }
        List<Double> thresholds = meaningfulThresholds(base, maximum, surfaces.keySet());
        String source = "reachable_block_collision_shapes+native_attribute";
        if (thresholds.isEmpty()) {
            List<Double> fallback = new ArrayList<>();
            for (double surface = Math.ceil((base + 1e-7) / SURFACE_GROUP) * SURFACE_GROUP;
                 surface <= base + maximum + 1e-7; surface += SURFACE_GROUP) fallback.add(surface);
            thresholds = meaningfulThresholds(base, maximum, fallback);
            source = "explicit_native_block_grid_fallback";
            confidence = .55;
            observations.add("No reachable measurable block surfaces in supplied evidence; half/full block grid is a generic fallback, not pack collision evidence");
        }
        observations.add("Native step attribute base=" + base + "; requested addition=" + requestedMaximum
                + "; attribute-bounded addition=" + maximum + "; inspected default shapes=" + inspected + "; unmeasured=" + skipped);
        surfaces.forEach((height, ids) -> observations.add("Observed collision top=" + height + "; reachable block items="
                + String.join(",", ids.stream().sorted().distinct().toList())));
        observations.add("Close surfaces are grouped in half-block intervals; each progression endpoint is the tallest observed boundary in its group. These are valuation/checkpoint references, not purchase snaps. Default-state empty-world samples do not claim exhaustive conditional/modded traversal support");
        observations.add("Traversal thresholds assume full equipment applicability and the native attribute baseline. Partial armor coverage or another step modifier may change the currently applied traversal height; equipment state never restructures this generated catalog");
        return new StepResolution(base, thresholds.isEmpty() ? 0 : thresholds.getLast(), thresholds,
                Math.clamp(confidence, 0, .95), source, observations);
    }

    static List<Double> meaningfulThresholds(double base, double maximum, Collection<Double> surfaces) {
        if (!Double.isFinite(base) || !Double.isFinite(maximum) || base < 0 || maximum < 0)
            throw new IllegalArgumentException("Invalid native step bounds");
        Map<Long, Double> groups = new TreeMap<>();
        for (double surface : surfaces) {
            if (!Double.isFinite(surface) || surface <= base + 1e-7 || surface > base + maximum + 1e-7) continue;
            long group = (long) Math.ceil((surface - 1e-7) / SURFACE_GROUP);
            groups.merge(group, surface, Math::max);
        }
        // Keep the exact native subtraction; rounding downward could miss the collision boundary.
        return groups.values().stream().map(surface -> surface - base).toList();
    }
}
