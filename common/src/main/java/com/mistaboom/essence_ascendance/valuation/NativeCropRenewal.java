package com.mistaboom.essence_ascendance.valuation;

import com.mistaboom.essence_ascendance.balance.engine.*;
import com.mistaboom.essence_ascendance.balance.generated.BalanceDocument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** Native planted-crop renewal after independent seed/soil/tool acquisition.
 * Uses effective mature hand drops; reserve one seed before publishing a surplus.
 * Random growth/seed survival are conditional, not guaranteed inventories or rates. */
public final class NativeCropRenewal {
    private NativeCropRenewal() { }
    public static Map<String, StartingBlockDrops.ExpectedDrop> surplus(String seed, Map<String, StartingBlockDrops.ExpectedDrop> harvest,
            Map<String, StartingBlockDrops.ExpectedDrop> retained) {
        var replant = harvest.get(seed);
        if (replant == null || replant.expectedCount() < 1 || replant.chance() <= 0
                || replant.chance() < 1 && replant.expectedCount() <= 1) return Map.of();
        var result = new TreeMap<String, StartingBlockDrops.ExpectedDrop>();
        retained.forEach((id, drop) -> {
            double remaining = id.equals(seed) ? Math.min(drop.expectedCount(), replant.expectedCount() - 1) : drop.expectedCount();
            if (remaining > 0 && drop.chance() > 0) result.put(id, new StartingBlockDrops.ExpectedDrop(drop.chance(), remaining));
        });
        return Collections.unmodifiableMap(result);
    }
    public static Map<String, ResourceEvidence> enrich(PackEvidenceContext context, Map<String, ResourceEvidence> original, EvidenceSink sink) {
        var result = new TreeMap<>(original); var resolver = ConfiguredRecipeAccess.nativeCrafting(context, original);
        for (var item : context.inputs().items()) {
            if (!(item instanceof BlockItem planted)) continue;
            var block = planted.getBlock(); var type = block.getClass();
            boolean wart = type == NetherWartBlock.class;
            if (!wart && type != CropBlock.class && type != CarrotBlock.class && type != PotatoBlock.class && type != BeetrootBlock.class) continue;
            String seed = BuiltInRegistries.ITEM.getKey(item).toString();
            var attempt = OptionalIntegration.attempt("native_crop_renewal", seed, () -> {
                ConfigurationAccess.Proof proof = null;
                var hoes = wart ? List.of("") : context.inputs().items().stream()
                        .filter(i -> i.getClass() == HoeItem.class && BuiltInRegistries.ITEM.getKey(i).getNamespace().equals("minecraft"))
                        .map(i -> BuiltInRegistries.ITEM.getKey(i).toString()).sorted().toList();
                for (String soil : wart ? List.of("minecraft:soul_sand") : List.of("minecraft:dirt", "minecraft:grass_block")) {
                    for (String hoe : hoes) {
                        var bill = new ArrayList<com.google.gson.JsonObject>();
                        bill.add(NativeConsumables.request(seed, 1)); bill.add(NativeConsumables.request(soil, 1));
                        if (!hoe.isEmpty()) bill.add(NativeConsumables.request(hoe, 1));
                        var candidate = resolver.requireExploration(bill).access();
                        if (proof == null || candidate.placement().reachable() && (!proof.placement().reachable()
                                || candidate.placement().stage().ordinal() < proof.placement().stage().ordinal())) proof = candidate;
                        if (proof.placement().reachable() && proof.placement().stage() == ProgressionBand.ENTRY) break;
                    }
                    if (proof != null && proof.placement().reachable() && proof.placement().stage() == ProgressionBand.ENTRY) break;
                }
                if (proof == null) return new Cycle(new ConfigurationAccess.Proof(new CompetitiveCapabilities.Placement(
                        ProgressionBand.APEX, false, 0, List.of()), List.of(), List.of("No audited native cultivation tool")), Map.of(), Map.of());
                BlockState mature = wart ? block.defaultBlockState().setValue(NetherWartBlock.AGE, 3)
                        : ((CropBlock)block).getStateForAge(((CropBlock)block).getMaxAge());
                var harvest = StartingBlockDrops.supportedHandDrops(context.inputs(), mature);
                return new Cycle(proof, harvest, proof.placement().reachable()
                        ? surplus(seed, harvest, StartingBlockDrops.supportedHandDrops(context.inputs(), mature, seed)) : Map.of());
            });
            if (!attempt.succeeded()) { sink.warn("Native crop READ_FAILED " + seed + ": " + attempt.failure()); continue; }
            var cycle = attempt.value().orElseThrow();
            if (cycle.surplus().isEmpty()) { sink.warn("Native crop renewal unproven " + seed + ": setup or effective mature/replant loot unsupported"); continue; }
            String source = "native_crop_cycle:" + seed;
            var conditions = List.of("Independent seed/soil" + (wart ? "" : "/hoe") + " acquisition; prepare native valid planting space",
                    "Native random ticks and required light; harvest mature by hand and reserve one seed",
                    "Conditional on successful bootstrap and seed recovery; expected surplus is not a guaranteed finite stock; no growth rate inferred");
            sink.add(new EvidenceFact(EvidenceFact.Subject.SOURCE, source, "conditional_renewal", EvidenceFact.Value.text(BalanceDocument.GSON.toJson(cycle)),
                    "native_crop_renewal", EvidenceFact.Origin.OBSERVED, cycle.proof().placement().confidence(), 0,
                    cycle.proof().placement().stage(), cycle.proof().selected(), String.join("; ", conditions)));
            for (var output : cycle.surplus().entrySet()) {
                var resource = result.get(output.getKey()); if (resource == null || !resource.external()) continue;
                var renewable = sink.get(EvidenceFact.Subject.ITEM, resource.itemId(), "renewable");
                var attainable = sink.get(EvidenceFact.Subject.ITEM, resource.itemId(), "attainable");
                if (renewable != null && renewable.origin() == EvidenceFact.Origin.OVERRIDE && !renewable.value().flag()
                        || attainable != null && attainable.origin() == EvidenceFact.Origin.OVERRIDE && !attainable.value().flag()) continue;
                var stageOverride = sink.get(EvidenceFact.Subject.ITEM, resource.itemId(), "stage");
                var stage = stageOverride != null && stageOverride.origin() == EvidenceFact.Origin.OVERRIDE ? resource.stage() : cycle.proof().placement().stage();
                var timer = new SourceAvailability.Timer(SourceAvailability.Applicability.OFF, 0, List.of());
                var availability = new SourceAvailability(source, SourceAvailability.Category.CONDITIONAL_RENEWABLE,
                        SourceAvailability.Scope.SHARED, List.of(), List.of(), conditions, timer, timer, true,
                        output.getValue().chance(), output.getValue().expectedCount(), List.of());
                var sources = new ArrayList<>(resource.sources());
                sources.add(new AcquisitionSource(source, AcquisitionSource.Kind.FARMING, stage, output.getValue().expectedCount(), true, false, 0,
                        cycle.proof().placement().confidence(), cycle.proof().selected(), String.join("; ", conditions), availability));
                result.put(resource.itemId(), new ResourceEvidence(resource.itemId(), resource.reachable() && resource.stage().ordinal() < stage.ordinal() ? resource.stage() : stage,
                        Availability.RENEWABLE_MANUAL, resource.automation(), true, true, resource.economicValue(),
                        resource.reachable() ? resource.confidence() : cycle.proof().placement().confidence(), sources, resource.warnings()));
            }
        }
        return result;
    }
    private record Cycle(ConfigurationAccess.Proof proof, Map<String, StartingBlockDrops.ExpectedDrop> matureHarvest,
                         Map<String, StartingBlockDrops.ExpectedDrop> surplus) { }
}
