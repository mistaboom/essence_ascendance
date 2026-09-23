package com.mistaboom.essence_ascendance.movement;

import com.google.gson.JsonParser;
import com.mistaboom.essence_ascendance.config.SkillEffectBalanceSettings;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import com.mistaboom.essence_ascendance.progression.MilestoneProviders;
import com.mistaboom.essence_ascendance.progression.Milestones;
import com.mistaboom.essence_ascendance.skill.*;
import com.mistaboom.essence_ascendance.skill.balance.SkillBalanceSemantics;
import com.mistaboom.essence_ascendance.skill.balance.SkillRankEffectScaling;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRegistry;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudHandler;
import com.mistaboom.essence_ascendance.skill.tooltip.SkillTooltipRegistry;
import com.mistaboom.essence_ascendance.stat.EssenceStats;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import net.minecraft.resources.ResourceLocation;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Uses actual committed evaluation, catalog, generated semantics and localization. Not a live-world test. */
public final class TraversalBatchContractTest {
    private static int checks;
    private static final List<ResourceLocation> IDS = List.of(SkillIds.TERRAIN_FREEDOM, SkillIds.AQUATIC_BODY,
            SkillIds.WATER_WALKING, SkillIds.LAVABORN);
    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        EssenceTypes.init(); AscendanceTiers.init(); EssenceStats.init(); MilestoneProviders.init(); Milestones.init(); Skills.init();
        check(TraversalCapabilities.profiles().stream().map(TraversalCapabilities.Profile::skill).toList().equals(IDS), "Batch is exactly the reviewed terrain/fluid branch");
        check(SkillRegistry.size() == 90, "Catalog and other branches remain intact");
        var defaults = SkillEffectBalanceSettings.defaults();
        try (var input = Objects.requireNonNull(TraversalBatchContractTest.class.getResourceAsStream("/assets/essence_ascendance/lang/en_us.json"));
             var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            var language = JsonParser.parseReader(reader).getAsJsonObject();
            for (var id : IDS) {
                var skill = SkillRegistry.require(id);
                check(SkillEffectRegistry.isImplemented(id), "Runtime implementation registered");
                check(skill.essenceId().equals(EssenceTypes.MOBILITY.id()), "Mobility category preserved");
                check(skill.rankPolicy().maximumRank() == 0, "Catalog delegates purchasable rank count to the balance engine");
                check(!SkillRankEffectScaling.supports(id), "No invented numeric rank consumer");
                check(SkillBalanceSemantics.require(id).contributions().stream().allMatch(c -> c.form() == SkillBalanceSemantics.Form.CAPABILITY), "Restorative capabilities cannot become unconditional magnitudes");
                for (double scale : new double[]{.1, 1, 4}) check(SkillRankEffectScaling.generatedPressureFactor(defaults, id, scale) == 1, "Offense generation never amplifies binary access");
                check(!SkillTooltipRegistry.lines(id).isEmpty(), "Resolved tooltip is present");
                for (var line : SkillTooltipRegistry.lines(id)) check(language.has(line.key()) && line.values().isEmpty(), "Localized passive semantics, no fabricated tuning values");
            }
        }
        check(SkillBalanceSemantics.require(SkillIds.AQUATIC_BODY).contributions().stream()
                .noneMatch(c -> c.axis() == com.mistaboom.essence_ascendance.balance.engine.CapabilityAxis.GROUND_SPEED),
                "Aquatic Body does not reserve or project a swimming-speed contribution");
        check(SkillEffectRegistry.handlers().stream().filter(h -> IDS.contains(h.id())).allMatch(h -> h instanceof SkillEffectHudHandler), "Traversal exposes conditional activity cards");
        var all = new LinkedHashMap<ResourceLocation, Integer>(); IDS.forEach(id -> all.put(id, 1));
        var full = evaluate(all);
        check(IDS.stream().allMatch(id -> full.get(id).effective()), "Committed full branch becomes effective");
        check(IDS.stream().noneMatch(id -> evaluate(Map.of()).get(id).effective()), "Unowned abilities are ineffective");
        var noRoot = new LinkedHashMap<>(all); noRoot.remove(SkillIds.TERRAIN_FREEDOM);
        check(IDS.stream().noneMatch(id -> evaluate(noRoot).get(id).effective()), "Losing Terrain Freedom disables all descendants");
        var noAqua = new LinkedHashMap<>(all); noAqua.remove(SkillIds.AQUATIC_BODY);
        var withoutAqua = evaluate(noAqua);
        check(withoutAqua.get(SkillIds.TERRAIN_FREEDOM).effective() && !withoutAqua.get(SkillIds.WATER_WALKING).effective()
                && !withoutAqua.get(SkillIds.LAVABORN).effective(), "Both aquatic descendants require the active parent");
        jumpTransitions();
        flightResources();
        movementOutcomes();
        mobilityChoices();
        com.mistaboom.essence_ascendance.client.SingleSidedFluidVertexConsumerTest.main(args);
        System.out.println("TraversalBatchContractTest: " + checks + " checks passed");
    }
    private static Map<ResourceLocation, SkillEvaluationResult> evaluate(Map<ResourceLocation, Integer> ranks) {
        return SkillStateEvaluator.evaluateAll(SkillEvaluationContext.committed(AscendanceTiers.TRANSCENDENT.id(), ranks,
                Map.of(), Set.of(), Set.of(), Map.of()));
    }
    private static MovementAbilityInput keys(int flags) {
        return new MovementAbilityInput(MovementAbilityInput.ENABLED | flags);
    }
    private static MovementAbilityState.Decision jump(MovementAbilityState state, long tick, int keys,
                                                       MovementAbilityState.Mode mode, boolean supported) {
        return state.input(tick, keys(keys), mode, true, supported, 20, 10);
    }
    private static void jumpTransitions() {
        var state = new MovementAbilityState();
        var charged = MovementAbilityState.Mode.CHARGED;
        var air = MovementAbilityState.Mode.AIR;
        int press = MovementAbilityInput.JUMP;
        int extra = press | MovementAbilityInput.AIR_JUMP;
        check(jump(state, 0, press, charged, true).action() == MovementAbilityState.Action.NONE,
                "Activation with Jump held cannot manufacture a charged press");
        jump(state, 1, 0, charged, true);
        jump(state, 2, press, charged, true);
        for (int packet = 0; packet < 50; packet++) jump(state, 2, press, charged, true);
        check(state.charging() && state.charge(2, 20) == 0, "Packet count never earns ground charge");
        var release = jump(state, 12, 0, charged, true);
        check(release.action() == MovementAbilityState.Action.CHARGED_RELEASE && release.charge() == .5,
                "Early release uses elapsed server ticks, with a proportional launch");
        check(jump(state, 12, 0, charged, true).action() == MovementAbilityState.Action.NONE,
                "Repeated release cannot launch twice");
        state.launched(12, true);
        state.observe(13, true, true, 10);
        check(!state.grounded(), "Stale launch support cannot immediately restore ground state");
        state.observe(14, true, false, 10);
        check(jump(state, 15, press, charged, false).action() == MovementAbilityState.Action.NATIVE_JUMP,
                "Charged branch preserves a separate native airborne Elytra press");
        state.clear(); jump(state, 20, 0, charged, true); jump(state, 21, press, charged, true);
        check(jump(state, 22, 0, charged, false).action() == MovementAbilityState.Action.NONE && !state.charging(),
                "Walking off support cancels rather than releasing charge");
        state.clear(); jump(state, 30, 0, charged, true); jump(state, 31, press, charged, true);
        check(jump(state, 42, 0, charged, true).action() == MovementAbilityState.Action.NONE,
                "Input timeout cancels rather than fabricating a charged release");
        jump(state, 43, press, charged, true);
        state.input(44, MovementAbilityInput.DISABLED, charged, true, true, 20, 10);
        check(!state.charging() && jump(state, 45, 0, charged, true).action() == MovementAbilityState.Action.NONE,
                "GUI suspension cancels ground charge");
        state.clear(); jump(state, 50, 0, air, true);
        check(jump(state, 51, press, air, true).action() == MovementAbilityState.Action.NATIVE_JUMP,
                "Initial ground jump stays native");
        check(jump(state, 52, press, air, false).action() == MovementAbilityState.Action.NONE && state.airAvailable(),
                "Holding takeoff cannot consume the air credit");
        jump(state, 53, 0, air, false);
        check(jump(state, 54, extra, air, false).action() == MovementAbilityState.Action.AIR_JUMP && !state.airAvailable(),
                "A fresh requested air edge spends exactly one landing-earned credit");
        check(jump(state, 54, extra, air, false).action() == MovementAbilityState.Action.NONE,
                "Duplicated air packet never repeats the launch");
        jump(state, 55, 0, air, false);
        check(jump(state, 56, extra, air, false).action() == MovementAbilityState.Action.NATIVE_JUMP && !state.airAvailable(),
                "Spent air press may deploy native Elytra but earns no extra jump");
        state.observe(57, true, true, 10);
        check(state.airAvailable() && jump(state, 57, extra, air, true).action() == MovementAbilityState.Action.NONE,
                "Real landing restores credit without turning a held key into an automatic hop");
        state.observe(58, false, false, 10);
        jump(state, 59, 0, air, false);
        check(jump(state, 60, extra, air, false).action() != MovementAbilityState.Action.AIR_JUMP && !state.airAvailable(),
                "Mode loss and reactivation in midair do not restore a credit");
        state.clear(); jump(state, 70, 0, air, true);
        check(jump(state, 71, press, air, false).action() == MovementAbilityState.Action.NATIVE_JUMP && state.airAvailable(),
                "Delayed ground input arriving after takeoff cannot be promoted into an air request");
    }
    private static void flightResources() {
        var state = new FlightAbilityState();
        state.updateStamina(0, 60, 40, false, false);
        for (int tick = 1; tick <= 60; tick++) {
            state.updateStamina(tick, 60, 40, true, false);
            double once = state.stamina();
            state.updateStamina(tick, 60, 40, true, false);
            check(state.stamina() == once, "Repeated tick cannot double-drain stamina");
        }
        check(state.stamina() < 1e-12, "Finite thrust exhausts at configured endurance");
        state.updateStamina(80, 60, 40, false, false);
        check(state.stamina() < 1e-12, "Airborne coasting cannot recharge Fatigue stamina");
        state.updateStamina(100, 60, 40, false, true);
        check(Math.abs(state.stamina() - .5) < 1e-12, "Ground recharge uses elapsed time");
        state.updateStamina(120, 60, 40, false, true);
        check(state.stamina() == 1, "Ground recharge clamps at full capacity");
        var boost = new FlightAbilityState();
        boost.updateBoost(0, 100);
        check(boost.spendBoost() && !boost.spendBoost(), "Boost can spend only one full charge");
        boost.updateBoost(99, 100);
        check(!boost.spendBoost(), "Boost cannot fire one tick before full recharge");
        boost.updateBoost(100, 100);
        check(boost.spendBoost(), "Boost fires at the exact full-recharge boundary");
        boost.suspendBoost(); boost.updateBoost(150, 100);
        check(boost.boostCharge() == .5, "Disabled Boost keeps elapsed recharge without an instant refill");
        state.updateStamina(140, 60, 40, true, false);
        double reserved = state.stamina();
        state.suspendThrust(); state.updateStamina(1140, 60, 40, false, true);
        check(state.stamina() == reserved, "Inactive elapsed time cannot be booked as grounded stamina recharge");
        state.updateStamina(1141, 60, 40, false, true);
        check(Math.abs(state.stamina() - reserved - .025) < 1e-12, "Resumed grounded time still recharges normally");
        state = new FlightAbilityState();
        state.input(200, keys(MovementAbilityInput.JUMP | MovementAbilityInput.FORWARD), 20);
        check(!state.jumpHeldFreshFor(201, 2) && state.jumpHeldFreshFor(202, 2), "Flight hold requires its deliberate hold interval");
        check(state.consumeJumpPressTick(202, 20) == 200 && state.consumeJumpPressTick(202, 20) == Long.MIN_VALUE,
                "Boost consumes a fresh press once, retaining its original time");
        check(!state.jumpHeldFreshFor(207, 2) && state.forwardInput() == 0, "Stale thrust stops and clears steering");
        state.input(208, keys(MovementAbilityInput.JUMP), 20);
        check(state.consumeJumpPressTick(208, 20) == Long.MIN_VALUE, "A stale held key cannot invent a boost edge");
        state.input(209, keys(0), 20); state.input(210, keys(MovementAbilityInput.JUMP), 20);
        check(state.consumeJumpPressTick(210, 20) == 210, "A real release rearms boost input");
        state.startWings(210); state.stopWings();
        check(!state.wingsActive() && state.wingsStartedTick() == Long.MIN_VALUE, "Stopping Wings clears glide ownership and start time");
    }
    private static void movementOutcomes() {
        var falling = new MovementImpulseMath.Velocity(.3, -1, .4);
        var down = new MovementImpulseMath.Velocity(0, -1, 0);
        var braked = MovementImpulseMath.vectorJump(falling, down, .42, 1, .4);
        check(Math.abs(braked.y() + .6) < 1e-12 && Math.abs(MovementImpulseMath.fallDistance(10, -1, braked.y()) - 3.6) < 1e-5,
                "Downward Vector input brakes descent and reduces its fall burden proportionally");
        check(MovementImpulseMath.fallDistance(10, -1, .1) == 0 && MovementImpulseMath.fallDistance(10, -1, -1.2) == 10,
                "Only arrested or reduced descent reduces accumulated fall distance");
        var thrust = MovementImpulseMath.jetpackThrust(falling, .08, .42, 1.55, 1, 1, .05);
        check(thrust.y() < 0 && thrust.y() > falling.y(), "Thrust accelerates from existing descent without erasing a fall");
        check(Math.abs(Math.hypot(thrust.x() - .3, thrust.z() - .4) - .05) < 1e-12,
                "Diagonal jetpack steering uses one resolved Flight Speed acceleration");
        var faster = MovementImpulseMath.jetpackThrust(falling, .08, .42, 1.55, 1, 1, .1);
        check(Math.abs(Math.hypot(faster.x() - .3, faster.z() - .4) - .1) < 1e-12 && faster.y() == thrust.y(),
                "Flight Speed improves lateral travel without secretly scaling vertical thrust");
        var burst = MovementImpulseMath.vectorBoost(new MovementImpulseMath.Velocity(.4, -.1, .2),
                new MovementImpulseMath.Velocity(0, 0, 1), 0);
        check(burst.x() == .4 && burst.y() == -.1 && Math.abs(burst.z() - 1.7) < 1e-12,
                "Zero-bonus Vector Boost retains perpendicular momentum and reaches native firework cruise");
        var fast = new MovementImpulseMath.Velocity(0, 0, 2);
        check(MovementImpulseMath.vectorBoost(fast, new MovementImpulseMath.Velocity(0, 0, 1), 0).equals(fast),
                "Boost never slows already faster forward motion");
        var glide = MovementImpulseMath.essenceWingsGlide(falling, .25);
        check(glide.y() == falling.y() && glide.horizontalSpeed() > falling.horizontalSpeed(),
                "Wings restores horizontal drag only, preserving native vertical physics");
        check(Math.abs(MovementImpulseMath.impactDamage(10, .2, .5) - 4) < 1e-6,
                "Impact reduction composes once with a native fall multiplier");
        var momentum = new com.mistaboom.essence_ascendance.skill.effect.ContinuousMomentumState();
        var build = com.mistaboom.essence_ascendance.skill.effect.ContinuousMomentumState.Action.BUILD;
        var hold = com.mistaboom.essence_ascendance.skill.effect.ContinuousMomentumState.Action.HOLD;
        var drain = com.mistaboom.essence_ascendance.skill.effect.ContinuousMomentumState.Action.DRAIN;
        for (int tick = 0; tick < 10; tick++) momentum.advance(tick, build, 20, 10, false);
        check(Math.abs(momentum.amount() - .5) < 1e-12, "Grounded movement builds its own meter");
        momentum.advance(10, hold, 20, 10, false);
        check(Math.abs(momentum.amount() - .5) < 1e-12, "Sprint jumps carry without building momentum");
        momentum.fill(); momentum.advance(11, drain, 20, 10, true);
        check(momentum.amount() == 1, "Rush retains the same reservoir without another speed stack");
        momentum.advance(13, hold, 20, 10, true);
        check(momentum.amount() == 0, "A discontinuity clears momentum even during Rush retention");
    }
    private static void mobilityChoices() {
        var ranks = new LinkedHashMap<ResourceLocation, Integer>();
        SkillRegistry.values().stream().filter(s -> s.essenceId().equals(EssenceTypes.MOBILITY.id())).forEach(s -> ranks.put(s.id(), 1));
        check(ranks.size() == 15, "Audit covers exactly fifteen Mobility skills");
        for (var jump : List.of(SkillIds.CHARGED_JUMP, SkillIds.DOUBLE_JUMP, SkillIds.VECTOR_JUMP)) {
            for (var flight : List.of(SkillIds.ESSENCE_WINGS, SkillIds.UNTETHERED_FLIGHT)) {
                var selections = Map.of(SkillGroups.MOBILITY_JUMP_STYLE, jump, SkillGroups.MOBILITY_FLIGHT_REPLACEMENT, flight);
                var result = SkillStateEvaluator.evaluateAll(SkillEvaluationContext.committed(AscendanceTiers.TRANSCENDENT.id(),
                        ranks, selections, SkillRegistry.referencedPermanentMilestoneIds(), Set.of(), Map.of()));
                for (var sibling : List.of(SkillIds.CHARGED_JUMP, SkillIds.DOUBLE_JUMP, SkillIds.VECTOR_JUMP))
                    check(result.get(sibling).effective() == sibling.equals(jump), "Only the selected jump branch operates: " + jump);
                check(result.get(flight).effective() && !result.get(SkillIds.FATIGUE_FLIGHT).effective(),
                        "Selected flight replacement suppresses owned Fatigue behavior");
                check(result.get(SkillIds.VECTOR_BOOST).effective() == flight.equals(SkillIds.ESSENCE_WINGS),
                        "Boost requires effective Wings independently of the selected jump style: " + jump);
            }
        }
        check(SkillRegistry.require(SkillIds.VECTOR_BOOST).prerequisites().equals(List.of(SkillIds.ESSENCE_WINGS)),
                "The catalog driving purchase gates and procedural tree edges declares Wings as Boost's only parent");
        var vanillaRanks = new LinkedHashMap<>(ranks);
        for (var jump : List.of(SkillIds.CHARGED_JUMP, SkillIds.DOUBLE_JUMP, SkillIds.VECTOR_JUMP)) vanillaRanks.remove(jump);
        var vanillaContext = SkillEvaluationContext.committed(AscendanceTiers.TRANSCENDENT.id(), vanillaRanks,
                Map.of(SkillGroups.MOBILITY_FLIGHT_REPLACEMENT, SkillIds.ESSENCE_WINGS),
                SkillRegistry.referencedPermanentMilestoneIds(), Set.of(), Map.of());
        check(SkillStateEvaluator.evaluateAll(vanillaContext).get(SkillIds.VECTOR_BOOST).effective(),
                "Wings and Boost operate with ordinary jumps and no jump skill owned");
        vanillaRanks.remove(SkillIds.VECTOR_BOOST);
        var purchaseContext = SkillEvaluationContext.committed(AscendanceTiers.TRANSCENDENT.id(), vanillaRanks,
                Map.of(SkillGroups.MOBILITY_FLIGHT_REPLACEMENT, SkillIds.ESSENCE_WINGS),
                SkillRegistry.referencedPermanentMilestoneIds(), Set.of(), Map.of());
        check(SkillStateEvaluator.evaluatePurchaseEligibility(SkillRegistry.require(SkillIds.VECTOR_BOOST), purchaseContext)
                        .prerequisitesSatisfied(),
                "Boost purchase prerequisites are satisfied without owning any jump skill");
        var none = SkillStateEvaluator.evaluateAll(SkillEvaluationContext.committed(AscendanceTiers.TRANSCENDENT.id(),
                ranks, Map.of(), SkillRegistry.referencedPermanentMilestoneIds(), Set.of(), Map.of()));
        check(none.get(SkillIds.FATIGUE_FLIGHT).effective() && !none.get(SkillIds.VECTOR_JUMP).effective()
                && !none.get(SkillIds.DOUBLE_JUMP).effective() && !none.get(SkillIds.CHARGED_JUMP).effective(),
                "Cleared jump choice selects none; clearing flight replacement restores eligible Fatigue");
    }
    private static void check(boolean pass, String message) { checks++; if (!pass) throw new AssertionError(message); }
}
