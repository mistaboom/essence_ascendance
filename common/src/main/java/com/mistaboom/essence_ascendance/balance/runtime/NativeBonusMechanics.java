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

import java.util.*;

/** Native measurements used only while generating resolved Bonuses; never reads a player or world. */
public final class NativeBonusMechanics {
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
        return Math.max(0, attribute.value().sanitizeValue(Double.MAX_VALUE) - baseline(attribute)) * 100;
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
