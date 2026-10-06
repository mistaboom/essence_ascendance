package com.mistaboom.essence_ascendance.balance.capability;

import com.google.gson.JsonObject;
import com.mistaboom.essence_ascendance.balance.engine.*;
import com.mistaboom.essence_ascendance.valuation.GenerationDataSnapshot;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import java.util.*;
import java.util.function.Supplier;
import static com.mistaboom.essence_ascendance.balance.engine.CapabilityEvidence.*;

/** Audited native extra ticker calls. No target tick, player action or stored-time mutation is executed. */
public final class TimeBottleCapabilityProvider implements PackEvidenceProvider {
    private static final String SOURCE = "tiab:time_in_a_bottle", TARGET = "minecraft:furnace";
    private static final String API = "org.mangorage.tiab.common.api.ICommonTimeInABottleAPI";
    private static final String CONFIG = "org.mangorage.tiab.common.api.ITiabConfig";
    public String id() { return "tiab_capabilities"; }
    public Set<CapabilityAxis> capabilityAxes() { return Set.of(CapabilityAxis.BLOCK_ENTITY_ACCELERATION); }
    public List<String> dependencyModIds() { return List.of("tiab"); }
    public boolean requiredForGeneration() { return false; }
    public ProviderReadiness readiness(GenerationDataSnapshot inputs) {
        return InstalledCapabilityProviders.version(inputs.installedVersion("tiab"), "6.5.4", inputs.installedVersion("neoforge") != null,
                "Audited extra ticker calls and effective stored-time budget; productive work, random-tick growth and sustained throughput remain conditional");
    }
    public void collect(PackEvidenceContext context, EvidenceSink sink) { }
    public void collectCapabilities(PackEvidenceContext context, Map<String, ResourceEvidence> resources, CapabilitySink sink) {
        sink.analyzed();
        try {
            Class<?> apiType = Class.forName(API), configType = Class.forName(CONFIG);
            Object api = ((Supplier<?>)apiType.getField("COMMON_API").get(null)).get();
            Object config = apiType.getMethod("getConfig").invoke(api);
            int storage = number(configType, config, "MAX_STORED_TIME"), ticks = number(configType, config, "TICKS_CONST"),
                    duration = number(configType, config, "EACH_USE_DURATION"), exponent = number(configType, config, "MAX_RATE_MULTI");
            Budget budget = budget(storage, ticks, duration, exponent);
            @SuppressWarnings("unchecked") TagKey<Block> excluded = (TagKey<Block>)apiType.getMethod("getTagKey").invoke(api);
            if (excluded == null) throw new IllegalStateException("Effective accelerator exclusion tag missing");
            JsonObject definition = new JsonObject();
            definition.addProperty("max_stored_ticks", storage); definition.addProperty("ticks_constant", ticks);
            definition.addProperty("each_use_duration", duration); definition.addProperty("max_rate_multi", exponent);
            definition.addProperty("fundable_peak_extra_calls", budget.extraCalls()); definition.addProperty("stored_ticks_for_ladder", budget.ladderTicks());
            definition.addProperty("excluded_block_tag", excluded.location().toString());
            sink.definition(id(), SOURCE, definition);
            if (Blocks.FURNACE.defaultBlockState().is(excluded)) {
                sink.candidate(SOURCE, id(), "Effective exclusion tag blocks the bounded furnace host; no alternate target access certified",
                        capabilityAxes(), CapabilitySink.Reason.ACCESS_UNPROVEN);
                return;
            }
            var proof = new ConfigurationAccess.Resolver(resources, context.inputs().configurationConstrainedItems())
                    .require(List.of(List.of(SOURCE), List.of(TARGET)));
            sink.add(project(budget, proof));
            if (!proof.placement().reachable()) sink.candidate(SOURCE, id(), "Bottle/eligible furnace acquisition remains unproven",
                    capabilityAxes(), CapabilitySink.Reason.ACCESS_UNPROVEN);
            sink.candidate(SOURCE, id(), "Randomly ticking blocks use a separate 1/1365 test per extra iteration; no crop growth multiplier, material yield or machine throughput certified",
                    Set.of(CapabilityAxis.CROP_ACCELERATION, CapabilityAxis.TREE_ACCELERATION, CapabilityAxis.BLOCK_ENTITY_ACCELERATION),
                    CapabilitySink.Reason.UNKNOWN_BEHAVIOR);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Audited time-bottle API changed", error);
        }
    }
    private static int number(Class<?> type, Object target, String method) throws ReflectiveOperationException {
        return ((Number)type.getMethod(method).invoke(target)).intValue();
    }
    /** Configured native use ladder, conservatively requiring its entire budget before activation.
     * The native cap compares timeRate against 2^(exponent-1), despite the config's 2^exponent comment. */
    public static Budget budget(int storageTicks, int ticksConstant, int duration, int exponent) {
        if (storageTicks <= 0 || ticksConstant <= 0 || duration <= 0 || exponent < 1 || exponent > 31)
            throw new IllegalArgumentException("Invalid/overflowing time-bottle configuration");
        long durationTicks = (long)ticksConstant * duration;
        if (durationTicks > Integer.MAX_VALUE || durationTicks > storageTicks)
            throw new IllegalArgumentException("Stored-time capacity cannot fund a native use");
        int peak = 1;
        for (int i = 1; i < exponent; i++) {
            int next = peak * 2;
            long ladder = (long)next * durationTicks;
            // Native energyCost multiplies ints; never promote an overflowing/free/negative cost.
            if (ladder > storageTicks || (long)(next / 2) * durationTicks > Integer.MAX_VALUE) break;
            peak = next;
        }
        return new Budget(peak, (int)durationTicks, (long)peak * durationTicks, storageTicks);
    }
    public record Budget(int extraCalls, int durationTicks, long ladderTicks, int capacityTicks) {
        public Budget {
            if (extraCalls < 1 || Integer.bitCount(extraCalls) != 1 || durationTicks < 1
                    || ladderTicks != (long)extraCalls * durationTicks || capacityTicks < ladderTicks)
                throw new IllegalArgumentException("Invalid native activation budget");
        }
    }
    /** Shared axis facts keep a bounded host, charge budget and unknown productive uptime distinct from output rates. */
    public static Functional project(Budget budget, ConfigurationAccess.Proof proof) {
        var unknown = new ArrayList<>(proof.unknown());
        unknown.add("Conditional peak; actual stored charge, loaded uptime, useful host work, fuel/input access and sustainable duty cycle unmeasured");
        var operation = new Operation(Automation.BOUNDED, Activity.PLAYER_ACTIVE, Renewal.RENEWABLE,
                null, budget.durationTicks() / 20.0, null, null,
                List.of("Native activation ladder consumes " + budget.ladderTicks() + " stored ticks; carry the bottle to replenish nominal game ticks",
                        "Host fuel and recipe inputs; extra ticker calls do not bypass their native consumption"),
                List.of("Independently acquire bottle and " + TARGET, "Charge bottle and activate/double one eligible loaded host"));
        var measurement = CompetitiveCapabilities.measurement(CapabilityAxis.BLOCK_ENTITY_ACCELERATION, (double)budget.extraCalls(),
                "extra_tick_calls_per_server_tick", "conditional=stored-time burst; eligible furnace ticker; effective exclusion tag checked",
                new Scope("configured_furnace_block_entity", "single", null, 1.0), operation, "tiab_capabilities", Origin.TYPED_ADAPTER, unknown);
        return new Functional(new CapabilityEvidence(SOURCE, proof.placement().stage(), Map.of(), proof.placement().reachable(),
                Math.min(.9, proof.placement().confidence()), "Audited native extra-call cap and bounded stored-time activation ladder; capacity=" + budget.capacityTicks()),
                "effective_config/furnace", List.of(measurement), proof.placement().acquisition(), proof.placement().reachable());
    }
}
