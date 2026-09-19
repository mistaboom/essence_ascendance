package com.mistaboom.essence_ascendance.skill.effect;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.config.SkillEffectBalanceSettings;
import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import com.mistaboom.essence_ascendance.data.SkillPurchase;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import com.mistaboom.essence_ascendance.skill.SkillActivationPolicy;
import com.mistaboom.essence_ascendance.skill.SkillCostBand;
import com.mistaboom.essence_ascendance.skill.SkillGroups;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.SkillRegistry;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;

/**
 * Read-only checks for /essence test skills effects. No player, world, runtime
 * context, damage event, attribute mutation or progression transaction is created.
 * These checks cover registration, catalog metadata, pure arithmetic and the
 * declared persistent-data schema; they do not simulate combat or prove every
 * serialization/lifecycle path safe.
 */
final class SkillEffectDiagnostics {
    private SkillEffectDiagnostics() { }

    static List<String> validate() {
        List<String> failures = new ArrayList<>();
        SkillEffectHudDiagnostics.validate(failures);
        failures.addAll(com.mistaboom.essence_ascendance.projectile.ProjectileDiagnostics.validate());
        registrations(failures);
        catalog(failures);
        configuration(failures);
        arithmetic(failures);
        timedStacks(failures);
        absorptionOwnership(failures);
        elementalFoundation(failures);
        hudVisibility(failures);
        persistentDataSchema(failures);
        return List.copyOf(failures);
    }

    private static void absorptionOwnership(List<String> failures) {
        var pool = new AbsorptionPoolLedger<String>();
        pool.configure("first", 8); pool.configure("second", 4);
        check(failures, pool.grant("first", 20) == 8 && pool.grant("second", 2) == 2,
                "Owned absorption must cap grants independently.");
        check(failures, pool.external(16) == 6, "Owned absorption must not claim unrelated native hearts.");
        var partial = pool.consume(3);
        check(failures, partial.depleted().isEmpty() && pool.amount("first") == 5,
                "Partial absorption damage must not emit a break.");
        var broken = pool.consume(6);
        check(failures, broken.depleted().equals(List.of("first")) && pool.amount("second") == 1,
                "Only a drained positive source emits a break, in deterministic source order.");
        check(failures, pool.consume(0).depleted().isEmpty(), "Empty/zero hits must not repeat a ward break.");
        pool.configure("second", .5);
        check(failures, pool.amount("second") == .5 && pool.remove("first") == 0,
                "Capacity trims and cleanup may remove only their source's points.");
        pool.fit(.25);
        check(failures, pool.total() == .25 && pool.external(2.25) == 2,
                "External native edits must reconcile ownership without inventing absorption.");
        pool.clear();
        check(failures, pool.empty() && pool.total() == 0, "Transient absorption cleanup must clear all ownership.");
    }

    private static void registrations(List<String> failures) {
        Set<ResourceLocation> expected = Set.of(SkillIds.FRENZY, SkillIds.ARMOR_CRACK,
                SkillIds.DESPERATION, SkillIds.DEATH_RUSH, SkillIds.KINDLING, SkillIds.COMBUSTION,
                SkillIds.FROSTBITE, SkillIds.SHATTER, SkillIds.STATIC_CHARGE, SkillIds.CHAIN_STRIKE,
                SkillIds.HOMING_PROJECTILE, SkillIds.RICOCHET, SkillIds.PIERCING_PROJECTILE,
                SkillIds.EXPLOSIVE_PAYLOAD, SkillIds.ROOTING_PAYLOAD, SkillIds.PROJECTILE_DRAG_FIELD,
                SkillIds.INTERCEPTOR, SkillIds.TRAJECTORY_THEFT, SkillIds.GUARDED_ADVANCE, SkillIds.SHIELD_RAM,
                SkillIds.REFLEXIVE_WARD, SkillIds.STORED_FORCE, SkillIds.GUARD_AMPLIFIER, SkillIds.CROWD_REPRISAL,
                SkillIds.RIPOSTE, SkillIds.EVASIVE_CURRENT, SkillIds.BULWARK_STANCE,
                SkillIds.ADAPTIVE_GUARD, SkillIds.STATUS_MIRROR, SkillIds.PURE_STATE,
                SkillIds.RISING_RECOVERY, SkillIds.LIFE_STEAL, SkillIds.FEAST_REFLEX, SkillIds.INNER_SUSTENANCE,
                SkillIds.SOUL_WARD, SkillIds.DEEP_WARD, SkillIds.SHATTERING_WARD,
                SkillIds.TOOL_INSTINCT, SkillIds.MINING_MOMENTUM, SkillIds.NATURES_BOON, SkillIds.TORCHBEARER);
        List<SkillEffectHandler> actual = List.copyOf(SkillEffectRegistry.handlers());
        Set<ResourceLocation> seen = new HashSet<>();
        check(failures, SkillEffectRegistry.implementedIds().containsAll(expected),
                "A completed baseline gameplay effect is missing from the registry.");
        check(failures, actual.size() == SkillEffectRegistry.implementedIds().size(),
                "Handler collection and implemented ID set disagree.");
        for (SkillEffectHandler handler : actual) {
            check(failures, seen.add(handler.id()), "Duplicate registered handler: " + handler.id());
            check(failures, SkillRegistry.get(handler.id()).isPresent(),
                    "Registered handler is absent from the catalog: " + handler.id());
            check(failures, SkillEffectRegistry.get(handler.id()) == handler,
                    "Handler lookup does not return its registered instance: " + handler.id());
        }
        check(failures, seen.equals(SkillEffectRegistry.implementedIds()),
                "Actual handlers disagree with the implemented ID set.");
        accepts(failures, "Actual registry must pass its registration validator",
                () -> SkillEffectRegistry.validated(actual));
        if (!actual.isEmpty()) {
            SkillEffectHandler first = actual.getFirst();
            rejects(failures, "Registration must reject duplicate IDs",
                    () -> SkillEffectRegistry.validated(List.of(first, first)));
        }
        ResourceLocation unknown = ResourceLocation.fromNamespaceAndPath(
                EssenceAscendance.MOD_ID, "diagnostic/unregistered_skill_effect");
        check(failures, SkillRegistry.get(unknown).isEmpty(), "Diagnostic unknown ID became a catalog skill.");
        SkillEffectHandler unknownHandler = () -> unknown;
        rejects(failures, "Registration must reject unknown catalog IDs",
                () -> SkillEffectRegistry.validated(List.of(unknownHandler)));
    }

    private static void catalog(List<String> failures) {
        accepts(failures, "Existing catalog validation", SkillRegistry::validate);
        definition(failures, SkillIds.FRENZY, AscendanceTiers.DORMANT.id(), SkillCostBand.FOUNDATION,
                List.of(), SkillGroups.OFFENSE_COMBAT_STANCE, SkillActivationPolicy.SELECTABLE);
        definition(failures, SkillIds.ARMOR_CRACK, AscendanceTiers.RESONANT.id(), SkillCostBand.ADVANCED,
                List.of(SkillIds.FRENZY), null, SkillActivationPolicy.AUTOMATIC);
        definition(failures, SkillIds.DESPERATION, AscendanceTiers.DORMANT.id(), SkillCostBand.FOUNDATION,
                List.of(), SkillGroups.OFFENSE_COMBAT_STANCE, SkillActivationPolicy.SELECTABLE);
        definition(failures, SkillIds.DEATH_RUSH, AscendanceTiers.ASCENDANT.id(), SkillCostBand.KEYSTONE,
                List.of(SkillIds.DESPERATION), null, SkillActivationPolicy.AUTOMATIC);
        definition(failures, SkillIds.KINDLING, AscendanceTiers.DORMANT.id(), SkillCostBand.FOUNDATION,
                List.of(), SkillGroups.OFFENSE_ELEMENTAL_IMBUEMENT, SkillActivationPolicy.SELECTABLE);
        definition(failures, SkillIds.COMBUSTION, AscendanceTiers.AWAKENED.id(), SkillCostBand.ADVANCED,
                List.of(SkillIds.KINDLING), null, SkillActivationPolicy.AUTOMATIC);
        definition(failures, SkillIds.FROSTBITE, AscendanceTiers.DORMANT.id(), SkillCostBand.FOUNDATION,
                List.of(), SkillGroups.OFFENSE_ELEMENTAL_IMBUEMENT, SkillActivationPolicy.SELECTABLE);
        definition(failures, SkillIds.SHATTER, AscendanceTiers.AWAKENED.id(), SkillCostBand.ADVANCED,
                List.of(SkillIds.FROSTBITE), null, SkillActivationPolicy.AUTOMATIC);
        definition(failures, SkillIds.STATIC_CHARGE, AscendanceTiers.DORMANT.id(), SkillCostBand.FOUNDATION,
                List.of(), SkillGroups.OFFENSE_ELEMENTAL_IMBUEMENT, SkillActivationPolicy.SELECTABLE);
        definition(failures, SkillIds.CHAIN_STRIKE, AscendanceTiers.AWAKENED.id(), SkillCostBand.ADVANCED,
                List.of(SkillIds.STATIC_CHARGE), null, SkillActivationPolicy.AUTOMATIC);
        var group = SkillRegistry.choiceGroup(SkillGroups.OFFENSE_COMBAT_STANCE);
        check(failures, group.isPresent(), "Combat stance choice group is missing.");
        group.ifPresent(value -> check(failures,
                value.memberIds().size() == 2
                        && Set.copyOf(value.memberIds()).equals(Set.of(SkillIds.FRENZY, SkillIds.DESPERATION)),
                "Combat stance choice group must contain exactly Frenzy and Desperation."));
        var elemental = SkillRegistry.choiceGroup(SkillGroups.OFFENSE_ELEMENTAL_IMBUEMENT);
        check(failures, elemental.isPresent(), "Elemental Imbuement choice group is missing.");
        elemental.ifPresent(value -> check(failures,
                value.memberIds().size() == 3 && Set.copyOf(value.memberIds()).equals(
                        Set.of(SkillIds.KINDLING, SkillIds.FROSTBITE, SkillIds.STATIC_CHARGE)),
                "Elemental Imbuement choice group must contain exactly Kindling, Frostbite, and Static Charge."));
    }

    private static void definition(List<String> failures, ResourceLocation id, ResourceLocation tier,
                                   SkillCostBand cost, List<ResourceLocation> prerequisites,
                                   ResourceLocation group, SkillActivationPolicy policy) {
        var found = SkillRegistry.get(id);
        check(failures, found.isPresent(), "Missing scoped catalog entry: " + id);
        found.ifPresent(skill -> {
            check(failures, skill.essenceId().equals(EssenceTypes.OFFENSE.id()), id + " changed Essence.");
            check(failures, skill.requiredTierId().equals(tier), id + " changed required tier.");
            check(failures, skill.costBand() == cost, id + " changed cost band.");
            check(failures, skill.prerequisites().equals(prerequisites), id + " changed prerequisites.");
            check(failures, Objects.equals(skill.choiceGroup(), group), id + " changed choice group.");
            check(failures, skill.activationPolicy() == policy, id + " changed activation policy.");
            check(failures, skill.replacementTarget() == null, id + " gained an unexpected replacement target.");
            check(failures, skill.requirements().isEmpty(), id + " gained unexpected unlock requirements.");
        });
    }

    private static void configuration(List<String> failures) {
        accepts(failures, "Default skill-effect settings", () -> SkillEffectBalanceSettings.defaults().validate());
        accepts(failures, "Active server skill-effect settings", () -> EssenceConfigManager.skillEffects().validate());

        integerBounds(failures, "Frenzy cap", 0, 100,
                value -> new SkillEffectBalanceSettings.Frenzy(value, 60, 4, 4));
        integerBounds(failures, "Frenzy timeout", 1, 72_000,
                value -> new SkillEffectBalanceSettings.Frenzy(5, value, 4, 4));
        decimalBounds(failures, "Frenzy damage", 100,
                value -> new SkillEffectBalanceSettings.Frenzy(5, 60, value, 4));
        decimalBounds(failures, "Frenzy attack speed", 100,
                value -> new SkillEffectBalanceSettings.Frenzy(5, 60, 4, value));

        integerBounds(failures, "Armor Crack cap", 0, 100,
                value -> new SkillEffectBalanceSettings.ArmorCrack(value, 60, 2, 0.5));
        integerBounds(failures, "Armor Crack timeout", 1, 72_000,
                value -> new SkillEffectBalanceSettings.ArmorCrack(5, value, 2, 0.5));
        decimalBounds(failures, "Armor Crack armor reduction", 1_024,
                value -> new SkillEffectBalanceSettings.ArmorCrack(5, 60, value, 0.5));
        decimalBounds(failures, "Armor Crack toughness reduction", 1_024,
                value -> new SkillEffectBalanceSettings.ArmorCrack(5, 60, 2, value));
        decimalBounds(failures, "Desperation maximum damage", 1_000,
                value -> new SkillEffectBalanceSettings.Desperation(value));

        decimalBounds(failures, "Death Rush kill threshold", 1,
                value -> new SkillEffectBalanceSettings.DeathRush(value, 0, 5, 160, 5, 5, 5));
        decimalBounds(failures, "Death Rush near-death threshold", 0.5,
                value -> new SkillEffectBalanceSettings.DeathRush(0.5, value, 5, 160, 5, 5, 5));
        integerBounds(failures, "Death Rush cap", 0, 100,
                value -> new SkillEffectBalanceSettings.DeathRush(0.5, 0.2, value, 160, 5, 5, 5));
        integerBounds(failures, "Death Rush duration", 1, 72_000,
                value -> new SkillEffectBalanceSettings.DeathRush(0.5, 0.2, 5, value, 5, 5, 5));
        decimalBounds(failures, "Death Rush attack speed", 100,
                value -> new SkillEffectBalanceSettings.DeathRush(0.5, 0.2, 5, 160, value, 5, 5));
        decimalBounds(failures, "Death Rush bow draw speed", 100,
                value -> new SkillEffectBalanceSettings.DeathRush(0.5, 0.2, 5, 160, 5, value, 5));
        decimalBounds(failures, "Death Rush cast speed", 100,
                value -> new SkillEffectBalanceSettings.DeathRush(0.5, 0.2, 5, 160, 5, 5, value));

        rejects(failures, "Kindling Heat cap validation",
                () -> new SkillEffectBalanceSettings.Kindling(101, 1, 100, 100, 20, 10));
        rejects(failures, "Kindling duration validation",
                () -> new SkillEffectBalanceSettings.Kindling(5, 1, 0, 100, 20, 10));
        rejects(failures, "Kindling burn scaling validation",
                () -> new SkillEffectBalanceSettings.Kindling(5, 1, 100, 100, Double.NaN, 10));
        rejects(failures, "Combustion radius validation",
                () -> new SkillEffectBalanceSettings.Combustion(3, 65, 6, 2, 18, 0.75, 80));
        rejects(failures, "Combustion falloff validation",
                () -> new SkillEffectBalanceSettings.Combustion(3, 4, 6, 2, 18, 1.01, 80));
        rejects(failures, "Frostbite slow validation",
                () -> new SkillEffectBalanceSettings.Frostbite(5, 1, 100, 1, 0.4, 80, 0.95));
        rejects(failures, "Shatter target-budget validation",
                () -> new SkillEffectBalanceSettings.Shatter(3, 5, 101));
        rejects(failures, "Static Charge cap validation",
                () -> new SkillEffectBalanceSettings.StaticCharge(0, 0.25, 4, 0.5, 0.35, 0.6, 40, 0.5, 4));
        rejects(failures, "Chain Strike jump validation",
                () -> new SkillEffectBalanceSettings.ChainStrike(101, 6, 0.75));
        accepts(failures, "Guard profile validation", () -> EssenceConfigManager.skillEffects().guard().validate());
        rejects(failures, "Guarded Advance finite movement validation",
                () -> new com.mistaboom.essence_ascendance.config.GuardBalanceSettings.Mobility(Double.NaN, 1));
        rejects(failures, "Shield Ram repeat guard cannot be shorter than stagger",
                () -> new com.mistaboom.essence_ascendance.config.GuardBalanceSettings.Ram(.2, 2, 10, .3, .6, 3, 1));
        rejects(failures, "Perfect guard timing remains short",
                () -> new com.mistaboom.essence_ascendance.config.GuardBalanceSettings.PerfectGuard(11));
        rejects(failures, "Guard Amplifier growth cannot exceed its maximum",
                () -> new com.mistaboom.essence_ascendance.config.GuardBalanceSettings.Amplifier(2, 2, 120));
        rejects(failures, "Riposte reach remains bounded",
                () -> new com.mistaboom.essence_ascendance.config.GuardBalanceSettings.Riposte(100, 3, .35, 1));
    }

    private static void arithmetic(List<String> failures) {
        check(failures, SkillEffectMath.stacks(-1, 5) == 0, "Negative stacks must clamp to zero.");
        check(failures, SkillEffectMath.stacks(Integer.MAX_VALUE, 5) == 5, "Stacks must clamp to cap.");
        check(failures, SkillEffectMath.stacks(5, 0) == 0, "A zero cap must disable stacks.");
        near(failures, SkillEffectMath.stackMultiplier(0, 5, 4), 1, "Zero-stack multiplier");
        near(failures, SkillEffectMath.stackMultiplier(100, 5, 4), 1.2, "Capped-stack multiplier");
        near(failures, SkillEffectMath.stackMultiplier(5, 0, 4), 1, "Disabled-stack multiplier");
        near(failures, SkillEffectMath.stackMultiplier(5, 5, Double.NaN), 1, "Nonfinite stack tuning");

        for (int health = 0; health <= 20; health++) {
            near(failures, SkillEffectMath.desperationMultiplier(health, 20, 30),
                    1.0 + (1.0 - health / 20.0) * 0.3, "Linear health curve at " + health + "/20");
        }
        near(failures, SkillEffectMath.desperationMultiplier(40, 20, 30), 1, "Overheal clamp");
        near(failures, SkillEffectMath.desperationMultiplier(-1, 20, 30), 1.3, "Negative-health clamp");
        near(failures, SkillEffectMath.desperationMultiplier(1, 0, 30), 1, "Invalid maximum health");
        near(failures, SkillEffectMath.desperationMultiplier(Double.NaN, 20, 30), 1, "Invalid health");
        near(failures, SkillEffectMath.desperationMultiplier(1, Double.POSITIVE_INFINITY, 30),
                1, "Nonfinite maximum health");

        check(failures, !SkillEffectMath.rushEligible(0.5, 0.5), "Rush threshold must be strictly below half health.");
        check(failures, SkillEffectMath.rushEligible(Math.nextDown(0.5), 0.5), "Rush must accept just-below-threshold health.");
        check(failures, !SkillEffectMath.rushEligible(Double.NaN, 0.5)
                        && !SkillEffectMath.rushEligible(-0.1, 0.5), "Invalid health must not grant Rush.");
        check(failures, !SkillEffectMath.rushEligible(0, 0), "Zero Rush threshold must disable grants.");
        check(failures, SkillEffectMath.rushRefreshes(0.2, 0.5, 0.2), "Near-death refresh includes its boundary.");
        check(failures, !SkillEffectMath.rushRefreshes(Math.nextUp(0.2), 0.5, 0.2),
                "Health above near-death must not refresh Rush.");
        check(failures, !SkillEffectMath.rushRefreshes(0.5, 0.5, 0.5),
                "Near-death refresh must still require kill eligibility.");

        List<Long> first = SkillEffectMath.grantRush(List.of(), 100, 2, 100, 0.4, 0.5, 0.2);
        check(failures, first.equals(List.of(200L)), "First Rush stack must receive its own expiry.");
        List<Long> second = SkillEffectMath.grantRush(first, 110, 2, 100, 0.4, 0.5, 0.2);
        check(failures, second.equals(List.of(200L, 210L)), "Ordinary Rush grants must preserve independent expiries.");
        check(failures, first.equals(List.of(200L)), "Rush grant must not mutate its input list.");
        check(failures, SkillEffectMath.grantRush(second, 120, 2, 100, 0.4, 0.5, 0.2).equals(second),
                "An ordinary kill at cap must not refresh Rush.");
        check(failures, SkillEffectMath.grantRush(second, 130, 2, 100, 0.2, 0.5, 0.2)
                        .equals(List.of(230L, 230L)), "Near-death kill at cap must refresh all active stacks.");
        check(failures, SkillEffectMath.grantRush(first, 110, 2, 100, 0.2, 0.5, 0.2)
                        .equals(List.of(210L, 210L)), "Near-death kill below cap must add and refresh stacks.");
        check(failures, SkillEffectMath.grantRush(second, 200, 2, 100, 0.4, 0.5, 0.2)
                        .equals(List.of(210L, 300L)), "Each Rush stack must expire independently at its exact deadline.");
        check(failures, SkillEffectMath.grantRush(second, 210, 2, 100, 0.5, 0.5, 0.2).isEmpty(),
                "An ineligible kill must prune expired stacks without granting one.");
        check(failures, SkillEffectMath.grantRush(second, 120, 0, 100, 0.1, 0.5, 0.2).isEmpty(),
                "Zero Rush cap must retain no stacks, including near-death refresh.");
        check(failures, SkillEffectMath.grantRush(second, 120, 1, 100, 0.4, 0.5, 0.2)
                        .equals(List.of(200L)), "Reduced Rush cap must truncate active stacks.");
        check(failures, SkillEffectMath.expiresAt(100, 0) == 101
                        && SkillEffectMath.expiresAt(100, Integer.MAX_VALUE) == 72_100
                        && SkillEffectMath.expiresAt(Long.MAX_VALUE - 1, 160) == Long.MAX_VALUE,
                "Expiry arithmetic must bound durations and saturate overflow.");
        check(failures, SkillEffectMath.remaining(200, 199) == 1
                        && SkillEffectMath.remaining(200, 200) == 0
                        && SkillEffectMath.remaining(200, 201) == 0,
                "Remaining duration must reach zero at expiry and stay nonnegative.");
    }

    private static void timedStacks(List<String> failures) {
        TimedStackState chain = TimedStackState.shared();
        chain.grant(100, 2, 60, false);
        check(failures, chain.count() == 1 && chain.nextExpiry() == 160,
                "Shared stacks must gain a bounded first deadline.");
        chain.grant(110, 2, 60, false);
        check(failures, chain.expiries().equals(List.of(170L, 170L)),
                "Shared stacks must refresh every deadline on a grant.");
        chain.grant(120, 2, 60, false);
        check(failures, chain.expiries().equals(List.of(180L, 180L)),
                "Shared grants at cap must refresh the chain without adding a stack.");
        chain.reconcile(179, 1);
        check(failures, chain.count() == 1 && chain.nextExpiry() == 180,
                "Reduced stack cap must retain a valid current deadline.");
        chain.reconcile(180, 1);
        check(failures, chain.count() == 0 && chain.nextExpiry() == 0,
                "Shared stacks must expire at their exact deadline.");
        chain.grant(200, 0, 60, true);
        check(failures, chain.count() == 0, "A disabled stack cap must not grant or refresh state.");

        TimedStackState independent = TimedStackState.independent();
        independent.grant(100, 2, 60, false);
        List<Long> firstSnapshot = independent.expiries();
        independent.grant(110, 2, 60, false);
        check(failures, independent.expiries().equals(List.of(160L, 170L)),
                "Independent stacks must preserve previous deadlines.");
        check(failures, firstSnapshot.equals(List.of(160L)),
                "Stack snapshots must not change when their owner is updated.");
        independent.grant(120, 2, 60, false);
        check(failures, independent.expiries().equals(List.of(160L, 170L)),
                "Independent grants at cap must leave deadlines untouched.");
        independent.grant(130, 2, 60, true);
        check(failures, independent.expiries().equals(List.of(190L, 190L)),
                "Explicit refresh-all must work at the independent stack cap.");
        independent.clear();
        check(failures, independent.count() == 0 && independent.nextExpiry() == 0,
                "Lifecycle cleanup must clear all stack timers.");
        independent.grant(200, 2, 60, false);
        independent.grant(210, 2, 60, false);
        independent.reconcile(260, 2);
        check(failures, independent.expiries().equals(List.of(270L)),
                "Independent reconciliation must remove only expired stacks.");
        independent.reconcile(260, -1);
        check(failures, independent.count() == 0, "Invalid negative stack caps must fail closed.");
        TimedStackState bounded = TimedStackState.independent(java.util.Collections.nCopies(1_001, 300L));
        bounded.reconcile(200, Integer.MAX_VALUE);
        check(failures, bounded.count() == 1_000, "Reusable timers must bound excessive stack caps.");
        TimedStackState changedDuration = TimedStackState.independent();
        changedDuration.grant(100, 2, 100, false);
        changedDuration.grant(110, 2, 10, false);
        check(failures, changedDuration.expiries().equals(List.of(120L, 200L))
                        && changedDuration.nextExpiry() == 120L,
                "Shorter future grants must sort timers without refreshing existing independent stacks.");
    }

    private static void elementalFoundation(List<String> failures) {
        check(failures, Set.of(AttackCategory.values()).equals(
                        Set.of(AttackCategory.MELEE, AttackCategory.RANGED, AttackCategory.CASTER)),
                "Primary attack categories must cover melee, ranged, and Caster exactly.");
        check(failures, Set.of(SourceOwnedBuildupState.ExpiryPolicy.values()).equals(Set.of(
                        SourceOwnedBuildupState.ExpiryPolicy.SHARED_WINDOW,
                        SourceOwnedBuildupState.ExpiryPolicy.INDEPENDENT_STACKS)),
                "Target buildup must retain explicit shared and independent expiry policies.");

        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        PropagationBudget root = new PropagationBudget(2, 2);
        root.seed(first);
        check(failures, !root.tryVisit(first, 0), "A seeded propagation target must not be revisited.");
        check(failures, root.tryVisit(second, 1), "A valid unvisited propagation target must be accepted.");
        check(failures, !root.tryVisit(UUID.randomUUID(), 3), "Propagation must reject generations past its cap.");
        check(failures, root.tryVisit(UUID.randomUUID(), 2), "Propagation must accept its exact generation boundary.");
        check(failures, !root.tryVisit(UUID.randomUUID(), 2) && root.affectedTargets() == 2,
                "Propagation must stop at its per-root target budget.");

        var defaults = SkillEffectBalanceSettings.defaults();
        near(failures, defaults.kindling().burningDamageAmplificationPercent(), 10.0,
                "Default burning amplification");
        near(failures, defaults.kindling().burningDamagePercentPerSecond(), 20.0,
                "Default attack-scaled burning damage");
        near(failures, defaults.frostbite().slowPerStack() * defaults.frostbite().maxChill(), 0.40,
                "Default progressive Chill cap");
        near(failures, defaults.staticCharge().maximumCharge(), 100.0, "Default Static Charge cap");
        near(failures, defaults.staticCharge().sprintPerTick(), 1.0,
                "Default five-second sprint charge rate");
        near(failures, defaults.staticCharge().lightningDamage(), 4.0, "Default lightning damage");
        check(failures, defaults.combustion().rootTargetBudget()
                        >= defaults.combustion().targetsPerBurst(),
                "Combustion root budget must allow at least one complete burst.");
        check(failures, defaults.chainStrike().maximumJumps() == 3,
                "Default Chain Strike traversal must remain bounded to three jumps.");
    }

    private static void hudVisibility(List<String> failures) {
        EffectHudVisibility<String> visibility = new EffectHudVisibility<>(60L);
        visibility.replace(Map.of("effect", false), 100L);
        check(failures, !visibility.visible("effect", 100L),
                "Initially default HUD entries must remain hidden.");
        visibility.replace(Map.of("effect", true), 110L);
        check(failures, visibility.visible("effect", 110L)
                        && visibility.visible("effect", 10_000L),
                "Active HUD entries must stay visible without periodic packets.");
        visibility.replace(Map.of("effect", false), 10_000L);
        check(failures, visibility.visible("effect", 10_000L)
                        && visibility.visible("effect", 10_059L),
                "Default grace must start at the active-to-default transition.");
        visibility.replace(Map.of("effect", false), 10_005L);
        check(failures, !visibility.visible("effect", 10_060L),
                "Repeated default packets must not extend the exact 60-tick grace.");
        visibility.replace(Map.of("effect", true), 10_030L);
        visibility.replace(Map.of(), 10_031L);
        check(failures, !visibility.visible("effect", 10_031L),
                "Removed or disabled HUD entries must disappear immediately.");
        visibility.replace(Map.of("effect", false), 10_032L);
        check(failures, !visibility.visible("effect", 10_032L),
                "Re-added inactive entries must not resurrect an old grace period.");
        visibility.replace(Map.of("effect", true), 10_033L);
        visibility.clear();
        check(failures, !visibility.visible("effect", 10_033L),
                "Client lifecycle cleanup must clear HUD visibility.");
        rejects(failures, "Negative HUD grace duration", () -> new EffectHudVisibility<>(-1L));
    }

    private static void persistentDataSchema(List<String> failures) {
        // An exact declared-field schema catches accidental runtime fields (also
        // generic Map values) without reading/modifying an actual player's data.
        // It is intentionally a schema guard, not a proof of every NBT write path.
        String id = ResourceLocation.class.getTypeName();
        String balances = "java.util.Map<" + id + ", java.lang.Long>";
        String ids = "java.util.Set<" + id + ">";
        Map<String, String> expected = new HashMap<>(Map.of(
                "availableEssence", balances,
                "investedEssence", balances,
                "crucibleReservoir", balances,
                "completedMilestones", ids,
                "ownedSkills", "java.util.Map<" + id + ", " + SkillPurchase.class.getTypeName() + ">",
                "loadoutSelections", "java.util.Map<" + id + ", " + id + ">",
                "attunement", "com.mistaboom.essence_ascendance.attunement.AttunementLedger",
                "currentTierId", id,
                "nexusRevision", "long"));
        expected.put("vitalityDamage", "com.mistaboom.essence_ascendance.vitality.VitalityDamageLedger");
        expected.put("projectileLife", "java.util.UUID");
        expected.put("dormantGuidebookReceived", "boolean");
        expected.put("fractionalResourceCostCarry", "java.util.Map<" + id + ", java.lang.Double>");
        Map<String, String> actual = new HashMap<>();
        for (var field : PlayerEssenceData.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) {
                actual.put(field.getName(), field.getGenericType().getTypeName());
            }
        }
        check(failures, PlayerEssenceData.class.getSuperclass() == Object.class,
                "Persistent player data gained an unchecked parent class.");
        check(failures, actual.equals(expected),
                "Persistent player data fields/types changed; review for runtime stack, target, timer or cooldown state: " + actual);
        var receipt = SkillPurchase.class.getRecordComponents();
        check(failures, receipt != null && receipt.length == 2
                        && receipt[0].getName().equals("essenceId") && receipt[0].getType() == ResourceLocation.class
                        && receipt[1].getName().equals("paidCosts") && receipt[1].getType() == List.class,
                "Persistent purchase receipt schema changed; review for transient combat state.");
    }

    private static void integerBounds(List<String> failures, String label, int minimum, int maximum,
                                      IntConsumer constructor) {
        accepts(failures, label + " minimum", () -> constructor.accept(minimum));
        accepts(failures, label + " maximum", () -> constructor.accept(maximum));
        rejects(failures, label + " below minimum", () -> constructor.accept(minimum - 1));
        rejects(failures, label + " above maximum", () -> constructor.accept(maximum + 1));
    }

    private static void decimalBounds(List<String> failures, String label, double maximum,
                                      DoubleConsumer constructor) {
        accepts(failures, label + " zero", () -> constructor.accept(0));
        accepts(failures, label + " maximum", () -> constructor.accept(maximum));
        for (double invalid : new double[]{-1, Math.nextUp(maximum), Double.NaN,
                Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}) {
            rejects(failures, label + " rejects " + invalid, () -> constructor.accept(invalid));
        }
    }

    private static void accepts(List<String> failures, String label, Runnable operation) {
        try {
            operation.run();
        } catch (RuntimeException exception) {
            failures.add(label + " failed: " + exception.getMessage());
        }
    }

    private static void rejects(List<String> failures, String label, Runnable operation) {
        try {
            operation.run();
            failures.add(label + " did not throw IllegalArgumentException.");
        } catch (IllegalArgumentException expected) {
            // Rejection is the invariant under test.
        } catch (RuntimeException exception) {
            failures.add(label + " threw " + exception.getClass().getSimpleName() + " instead.");
        }
    }

    private static void near(List<String> failures, double actual, double expected, String label) {
        check(failures, Double.isFinite(actual) && Math.abs(actual - expected) <= 1.0E-9,
                label + " expected " + expected + " but got " + actual + ".");
    }

    private static void check(List<String> failures, boolean valid, String message) {
        if (!valid) failures.add(message);
    }
}
