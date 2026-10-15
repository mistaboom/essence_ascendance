package com.mistaboom.essence_ascendance.valuation;

import com.mistaboom.essence_ascendance.balance.engine.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** Factual 1.21.1 retained-parent spreading/harvest contract. No price or rate is
 * supplied. Loaded placement, effective loot, starter/setup stock and replacement
 * tools remain separate requirements; an item-only trader row proves none of them. */
public final class NativeVineRenewal {
    private static final String PROVIDER = "native_vine_renewal";
    private static final String VINE = "minecraft:vine";
    private NativeVineRenewal() { }

    public static boolean growthEnabled(BlockState state, boolean spread, int randomTicks) {
        return state.getBlock().getClass() == VineBlock.class && spread && randomTicks > 0;
    }
    public static boolean acceptsSupport(BlockState state) {
        return !state.hasBlockEntity() && MultifaceBlock.canAttachTo(EmptyBlockGetter.INSTANCE,
                Direction.NORTH, BlockPos.ZERO, state);
    }
    public static Map<String, ResourceEvidence> enrich(PackEvidenceContext context,
            Map<String, ResourceEvidence> original, EvidenceSink sink) {
        if (context.server() == null) return original;
        var staged = sink.staged();
        var attempt = OptionalIntegration.attempt(PROVIDER, "retained native vine parent and paid shears", () -> collect(context, original, staged));
        if (!attempt.succeeded()) { sink.warn(PROVIDER + " READ_FAILED: " + attempt.failure()); return original; }
        sink.merge(staged); return attempt.value().orElseThrow();
    }
    private static Map<String, ResourceEvidence> collect(PackEvidenceContext context,
            Map<String, ResourceEvidence> original, EvidenceSink sink) {
        var resource = original.get(VINE); if (resource == null || !resource.external()) return original;
        var rules = context.server().overworld().getGameRules();
        if (!growthEnabled(Blocks.VINE.defaultBlockState(), rules.getBoolean(GameRules.RULE_DO_VINES_SPREAD),
                rules.getInt(GameRules.RULE_RANDOMTICKING)) || !rules.getBoolean(GameRules.RULE_DOBLOCKDROPS)) return original;
        for (String property : List.of("renewable", "attainable")) {
            var fact = sink.get(EvidenceFact.Subject.ITEM, VINE, property);
            if (fact != null && fact.origin() == EvidenceFact.Origin.OVERRIDE && !fact.value().flag()) return original;
        }
        var inputs = context.inputs(); var access = ConfiguredRecipeAccess.nativeCrafting(context, original);
        var replacement = access.requireStack(NativeConsumables.request("minecraft:shears", 1));
        if (!replacement.placement().reachable()) {
            sink.warn(PROVIDER + ": independently renewable replacement shears unproven: " + replacement.unknown()); return original;
        }
        var natural = ProceduralNaturalBlockIndex.startingWorld(inputs);
        boolean naturalStarter = natural.contains(ResourceLocation.parse(VINE));
        if (!naturalStarter && !access.requireExploration(List.of(NativeConsumables.request(VINE, 1))).access().placement().reachable()) {
            sink.warn(PROVIDER + ": neither starting-world vines nor an independently acquired starter is proved"); return original;
        }
        var supports = inputs.items().stream().filter(item -> item instanceof BlockItem)
                .map(item -> (BlockItem) item)
                .filter(item -> BuiltInRegistries.BLOCK.getKey(item.getBlock()).getNamespace().equals("minecraft"))
                .filter(item -> item.getBlock().getClass().getPackageName().equals("net.minecraft.world.level.block"))
                .filter(item -> acceptsSupport(item.getBlock().defaultBlockState()))
                .map(item -> BuiltInRegistries.ITEM.getKey(item).toString()).filter(original::containsKey)
                .sorted(Comparator.<String, ProgressionBand>comparing(id -> original.get(id).stage()).thenComparing(id -> id)).toList();
        ConfigurationAccess.Proof setup = null;
        for (String support : supports) {
            var bill = new ArrayList<com.google.gson.JsonObject>();
            bill.add(NativeConsumables.request(support, 1));
            bill.add(NativeConsumables.request("minecraft:shears", 1));
            if (!naturalStarter) bill.add(NativeConsumables.request(VINE, 1));
            var candidate = access.requireExploration(bill).access();
            if (candidate.placement().reachable()) { setup = candidate; break; }
        }
        if (setup == null) { sink.warn(PROVIDER + ": joint seed/support/shears setup unproven"); return original; }
        var state = Blocks.VINE.defaultBlockState().setValue(VineBlock.NORTH, true);
        var exclusions = new ArrayList<String>();
        var drops = StartingBlockDrops.supportedToolDrops(inputs, state, Items.SHEARS.getDefaultInstance(),
                "", "", Set.of(), exclusions::add);
        var harvest = drops.get(VINE);
        if (harvest == null || harvest.chance() <= 0 || harvest.expectedCount() <= 0) {
            sink.warn(PROVIDER + ": effective unenchanted shears harvest unproven: " + exclusions); return original;
        }
        var proof = ConfiguredRecipeAccess.combine(List.of(setup, replacement), "joint vine setup plus renewable replacement tool", .85);
        var conditions = List.of(
                naturalStarter ? "Conditional successful exploration/harvest of starting Overworld vines: " + natural.signals(ResourceLocation.parse(VINE))
                        : "Independent finite/exploration starter included in joint seed/support/shears bill",
                "Place one horizontally attached parent on the proved support above minimum build height; retain the parent and harvest only newly grown lower vines",
                "Native VineBlock.randomTick; doVinesSpread=true, randomTickSpeed>0 and doTileDrops=true; loaded clear growth space required",
                "Fresh unenchanted shears; native ShearsItem.mineBlock costs one durability per block; independent renewable replacement shears proved conservatively without assuming repairs",
                "Expected loot per successfully grown lower block; no growth, search, harvesting, replacement-tool or farm rate measured");
        String source = "native_vine_cycle:" + VINE;
        var off = new SourceAvailability.Timer(SourceAvailability.Applicability.OFF, 0, List.of());
        var availability = new SourceAvailability(source, SourceAvailability.Category.CONDITIONAL_RENEWABLE,
                SourceAvailability.Scope.SHARED, List.of(), List.of("minecraft:overworld"), conditions, off, off, true,
                harvest.chance(), harvest.expectedCount(), List.of());
        var stageFact = sink.get(EvidenceFact.Subject.ITEM, VINE, "stage");
        var stage = stageFact != null && stageFact.origin() == EvidenceFact.Origin.OVERRIDE ? resource.stage() : proof.placement().stage();
        var sources = new ArrayList<>(resource.sources());
        sources.add(new AcquisitionSource(source, AcquisitionSource.Kind.FARMING, stage, harvest.expectedCount(), true, false, 0,
                proof.placement().confidence(), proof.selected(), String.join("; ", conditions), availability));
        sink.add(new EvidenceFact(EvidenceFact.Subject.SOURCE, source, "conditional_renewal", EvidenceFact.Value.flag(true),
                PROVIDER, EvidenceFact.Origin.OBSERVED, proof.placement().confidence(), 0, stage, proof.selected(), String.join("; ", conditions)));
        var result = new TreeMap<>(original);
        result.put(VINE, new ResourceEvidence(VINE, resource.reachable() && resource.stage().ordinal() < stage.ordinal() ? resource.stage() : stage,
                Availability.RENEWABLE_MANUAL, Automation.PLAYER_GATED, true, true, resource.economicValue(),
                Math.max(resource.confidence(), proof.placement().confidence()), sources, resource.warnings()));
        return result;
    }
}
