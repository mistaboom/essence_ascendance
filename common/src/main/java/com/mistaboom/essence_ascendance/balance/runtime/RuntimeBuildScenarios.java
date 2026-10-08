package com.mistaboom.essence_ascendance.balance.runtime;

import com.mistaboom.essence_ascendance.balance.config.BalanceSettings;
import com.mistaboom.essence_ascendance.balance.engine.*;
import com.mistaboom.essence_ascendance.balance.engine.BuildComposition.*;
import com.mistaboom.essence_ascendance.config.SkillEffectBalanceSettings;
import com.mistaboom.essence_ascendance.equipment.*;
import com.mistaboom.essence_ascendance.skill.*;
import com.mistaboom.essence_ascendance.skill.balance.*;
import com.mistaboom.essence_ascendance.stat.*;
import com.mistaboom.essence_ascendance.tier.*;
import com.mistaboom.essence_ascendance.progression.StatScalingService;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Generation-only numeric checks using candidate rank curves and matching weapon inputs. */
public final class RuntimeBuildScenarios {
    private RuntimeBuildScenarios() { }
    public record Case(String tier, String skillSelection, Evaluation evaluation,
                       Map<Participation, Limits> participationLimits,
                       Map<Participation, DefensivePressure> defensivePressure) {
        public Case {
            participationLimits = Collections.unmodifiableMap(new EnumMap<>(participationLimits));
            defensivePressure = Collections.unmodifiableMap(new EnumMap<>(defensivePressure));
            if (participationLimits.size() != Participation.values().length || defensivePressure.size() != Participation.values().length)
                throw new IllegalArgumentException("Every combat case requires all participation limits and defense pressure");
        }
        public Limits limitFor(Participation participation) {
            return Objects.requireNonNull(participationLimits.get(participation), "Missing participation ceiling");
        }
    }
    /** Conditional capability bounds retain their real units; status immunity is never infinite EHP or invented DPS. */
    public record DefensivePressure(double peakAvoidance, double peakDamageReduction,
                                    boolean frontalKnockbackImmunity, double peakHarmfulStatusPrevention,
                                    double maximumMirrorTransfersPerSecond, int mirrorMaximumDurationTicks,
                                    int mirrorMaximumAmplifier) {
        public DefensivePressure {
            for (double value : new double[]{peakAvoidance, peakDamageReduction, peakHarmfulStatusPrevention, maximumMirrorTransfersPerSecond})
                if (!Double.isFinite(value) || value < 0 || value > 1) throw new IllegalArgumentException("Invalid defense capability bound");
            if (mirrorMaximumDurationTicks < 0 || mirrorMaximumDurationTicks > 72_000 || mirrorMaximumAmplifier < 0 || mirrorMaximumAmplifier > 10)
                throw new IllegalArgumentException("Invalid status copy bound");
        }
    }
    public record Analysis(double attenuation, List<Case> cases, List<String> assumptions) {
        public Analysis { cases = List.copyOf(cases); assumptions = List.copyOf(assumptions); }
        public void requireSafe() {
            for (var row : cases) {
                try { row.evaluation().requireSafe(); }
                catch (IllegalArgumentException failure) {
                    throw new IllegalArgumentException(failure.getMessage() + "; "
                            + String.join("; ", row.evaluation().assumptions()), failure);
                }
            }
        }
        public boolean safeFor(Channel channel) {
            return cases.stream().flatMap(row -> row.evaluation().violations().stream())
                    .noneMatch(v -> channel == null || channel.includes(v.metric()));
        }
        public String firstViolation(Channel channel) {
            for (var row : cases) {
                var relevant = row.evaluation().violations().stream()
                        .filter(v -> channel == null || channel.includes(v.metric())).toList();
                if (!relevant.isEmpty()) return row.evaluation().id() + " " + relevant;
            }
            return "no " + channel + " violation";
        }
    }
    public record Plan(Map<ResourceLocation, List<SkillLoadoutProjection.Scenario>> full,
                       Map<ResourceLocation, List<SkillLoadoutProjection.Scenario>> moderate, boolean developed,
                       EquipmentBaselineConfig equipmentLimits) { }
    public static Plan plan() { return plan(true); }
    public static Plan plan(RuntimeBalanceDefinition runtime, boolean developed) {
        return plan(runtime, developed, true);
    }
    static Plan plan(RuntimeBalanceDefinition runtime, boolean developed, boolean includeVitality) {
        return SkillBalanceRuntime.withCurves(runtime.skillCurves(), () -> planResolved(runtime, developed, includeVitality));
    }
    private static Plan planResolved(RuntimeBalanceDefinition runtime, boolean developed, boolean includeVitality) {
        var plan=plan(developed, includeVitality, runtime.composition().getOrDefault("meaningful_progression", 0.0) == 1
                ? runtime.skillCurves() : Map.of());
        return new Plan(plan.full(),plan.moderate(),developed,runtime.config().equipmentBaselineConfig());
    }
    public static Plan plan(boolean developed) {
        return plan(developed, true);
    }
    private static Plan plan(boolean developed, boolean includeVitality) {
        return plan(developed, includeVitality, Map.of());
    }
    private static Plan plan(boolean developed, boolean includeVitality,
            Map<String, com.mistaboom.essence_ascendance.skill.balance.SkillBalanceRuntime.ResolvedSkill> curves) {
        Map<ResourceLocation, List<SkillLoadoutProjection.Scenario>> full = new LinkedHashMap<>(), moderate = new LinkedHashMap<>();
        // Numeric combat validation never consumes gathering/fishing/unequipped
        // scenarios. Keep those in the general projection, not every calibration plan.
        var combatEquipment = SkillLoadoutProjection.representativeEquipment().stream()
                .filter(e -> Set.of("melee_shield", "ranged", "caster").contains(e.id())).toList();
        int apexOrder = AscendanceTierRegistry.powerTiers().stream().mapToInt(AscendanceTierDefinition::order).max().orElseThrow();
        for (var tier : AscendanceTierRegistry.powerTiers()) {
            Map<ResourceLocation, Integer> fullRanks = new LinkedHashMap<>(), moderateRanks = new LinkedHashMap<>();
            for (var skill : SkillRegistry.values()) {
                if (!includeVitality && vitalitySkill(skill.id())) continue;
                int projectedRank = developed && tier.order() == apexOrder ? skill.rankPolicy().projectionRanks()
                        : developed ? Math.min(skill.rankPolicy().projectionRanks(), Math.max(1,
                        tier.order() - AscendanceTierRegistry.get(skill.requiredTierId()).orElseThrow().order() + 1)) : 1;
                if (curves.containsKey(skill.id().toString())) projectedRank = Math.min(projectedRank, curves.get(skill.id().toString()).maximumRank());
                fullRanks.put(skill.id(), projectedRank);
                moderateRanks.put(skill.id(), skill.prerequisites().isEmpty() ? 1 : 0);
            }
            full.put(tier.id(), SkillLoadoutProjection.project(SkillRegistry.values(), tier.id(), fullRanks,
                    (id, rank) -> SkillRegistry.require(id).rankPolicy().curve().power(rank), Map.of(), false, combatEquipment).scenarios());
            moderate.put(tier.id(), SkillLoadoutProjection.project(SkillRegistry.values(), tier.id(), moderateRanks,
                    (id, rank) -> 1, Map.of(), false, combatEquipment).scenarios());
        }
        return new Plan(Collections.unmodifiableMap(full), Collections.unmodifiableMap(moderate), developed, null);
    }

    public static Analysis analyze(RuntimeBalanceDefinition runtime, PackEvidence evidence, BalanceSettings settings,
                                   Plan plan) {
        return analyze(runtime, evidence, settings, plan, null, true);
    }

    /** Search probes need only a decision; detailed reports are built only when a caller consumes them. */
    public static boolean isSafe(RuntimeBalanceDefinition runtime, PackEvidence evidence, BalanceSettings settings,
                                 Plan plan, Channel channel) {
        return analyze(runtime, evidence, settings, plan, channel, false) != null;
    }

    private static final Analysis SAFE_PROBE = new Analysis(1, List.of(), List.of());

    // A probe returns null at the first relevant violation, or SAFE_PROBE after checking every case.
    private static Analysis analyze(RuntimeBalanceDefinition runtime, PackEvidence evidence, BalanceSettings settings,
                                    Plan plan, Channel channel, boolean detailed) {
        com.mistaboom.essence_ascendance.balance.generated.BalancePerformance.increment(detailed ? "combat_scenario_analyses" : "combat_scenario_probes");
        List<Case> result = new ArrayList<>();
        int checkedCases = 0;
        boolean parity = runtime.composition().getOrDefault("equipment_apex_parity", 0.0) == 1.0;
        int bandIndex = 0;
        for (var tier : AscendanceTierRegistry.powerTiers().stream().sorted(Comparator.comparingInt(AscendanceTierDefinition::order)).toList()) {
            ProgressionBand band = ProgressionBand.at(bandIndex++);
            double rate = RuntimeReferencePolicy.required(evidence, band, CapabilityAxis.ATTACK_RATE);
            double dps = RuntimeReferencePolicy.required(evidence, band, CapabilityAxis.SUSTAINED_DAMAGE);
            double armor = RuntimeReferencePolicy.observed(evidence, band, CapabilityAxis.ARMOR, 0);
            double toughness = RuntimeReferencePolicy.observed(evidence, band, CapabilityAxis.TOUGHNESS, 0);
            double health = RuntimeReferencePolicy.playerHealth();
            double incoming = runtime.composition().getOrDefault("enemy_damage_" + band.name().toLowerCase(Locale.ROOT), RuntimeReferencePolicy.playerHit());
            if (parity) {
                var pairedArmor = RuntimeReferencePolicy.armor(evidence, band, settings.outlierPolicy().name(), incoming);
                armor = pairedArmor.armor(); toughness = pairedArmor.toughness();
            }
            double window = settings.generation().survivalWindowSeconds();
            var baseline = runtime.config().equipmentBaselineConfig().baselineFor(tier);
            var equipmentLimit=plan.equipmentLimits()==null?null:plan.equipmentLimits().baselineFor(tier);
            Map<String, RuntimeReferencePolicy.Weapon> references = new HashMap<>();
            for (String family : List.of("melee_shield", "ranged", "caster")) references.put(family, parity
                    ? RuntimeReferencePolicy.weapon(evidence, band, family, settings.outlierPolicy().name())
                    : new RuntimeReferencePolicy.Weapon(dps / rate, rate, Math.max(dps / rate,
                            evidence.reference(band, CapabilityAxis.BURST_DAMAGE, dps / rate)), false));
            for (String family : List.of("melee_shield", "ranged", "caster")) {
                String key = "competitive_" + band + "_" + family;
                if (runtime.composition().containsKey(key + "_damage")) {
                    double hit = runtime.composition().get(key + "_damage");
                    references.put(family, new RuntimeReferencePolicy.Weapon(hit, runtime.composition().get(key + "_rate"), hit, true));
                }
            }
            Set<String> duplicateSelections = new HashSet<>();
            Map<String, Modifier> fullNexus = new HashMap<>(), moderateNexus = new HashMap<>();
            double fullHealing = 1 + bonus(runtime, tier, EssenceStats.HEALING_EFFECTIVENESS, false) / 100;
            double moderateHealing = 1 + bonus(runtime, tier, EssenceStats.HEALING_EFFECTIVENESS, true) / 100;
            String referenceAssumption = detailed ? "Reference incoming hit=" + incoming + " HP; fully useful healing window=" + window + " seconds." : "";
            for (var scenario : plan.full().get(tier.id())) {
                String family = scenario.equipmentContext();
                if (!references.containsKey(family)) continue;
                if (!duplicateSelections.add(family + new TreeMap<>(scenario.contributingRanks()))) continue;
                var reference = references.get(family);
                var external = new Equipment(reference.damage(), reference.rate(), armor, toughness,
                        runtime.composition().getOrDefault("competitive_" + band + "_health", health), 0);
                var externalMetrics = BuildComposition.compose(external, Modifier.none(), Modifier.none(), incoming, window);
                double survivalEnvelope = runtime.composition().getOrDefault("competitive_" + band + "_effective_health", externalMetrics.effectiveHealth());
                if (survivalEnvelope > externalMetrics.effectiveHealth()) externalMetrics = new Metrics(externalMetrics.sustainedDamage(),
                        externalMetrics.burstDamage(), externalMetrics.areaDamage(), survivalEnvelope,
                        Math.max(survivalEnvelope, externalMetrics.sustainedHealth()), externalMetrics.healingPerSecond());
                double damage = family.equals("ranged") ? baseline.rangedDamage() : family.equals("caster") ? baseline.magicDamage() : baseline.meleeDamage();
                double attackRate = family.equals("ranged") ? baseline.rangedAttackSpeed() : family.equals("caster") ? baseline.magicCastSpeed() : baseline.meleeAttackSpeed();
                var damageProperty = family.equals("ranged") ? EquipmentBaselineProperty.RANGED_DAMAGE : family.equals("caster") ? EquipmentBaselineProperty.MAGIC_DAMAGE : EquipmentBaselineProperty.MELEE_DAMAGE;
                var speedProperty = family.equals("ranged") ? EquipmentBaselineProperty.RANGED_ATTACK_SPEED : family.equals("caster") ? EquipmentBaselineProperty.MAGIC_CAST_SPEED : EquipmentBaselineProperty.MELEE_ATTACK_SPEED;
                var smaller = plan.moderate().get(tier.id()).stream().filter(s -> s.equipmentContext().equals(family)
                        && s.objective().equals(scenario.objective())).findFirst().orElse(null);
                Map<ResourceLocation, Integer> fullRanks = scenario.contributingRanks();
                Map<ResourceLocation, Integer> moderateRanks = smaller == null ? Map.of() : smaller.contributingRanks();
                var fullEffects = rankedEffects(runtime, fullRanks);
                var moderateEffects = rankedEffects(runtime, moderateRanks);
                var nexusFull = fullNexus.computeIfAbsent(family, key -> nexus(runtime, tier, key, false));
                var nexusModerate = moderateNexus.computeIfAbsent(family, key -> nexus(runtime, tier, key, true));
                // These depend on this candidate/selection, never on archetype or current HP.
                Set<ResourceLocation> specialized = Set.copyOf(fullRanks.keySet().stream()
                        .filter(skillId -> SkillBalanceSemantics.require(skillId).weights().keySet().stream().anyMatch(axis -> switch (axis) {
                            case SUSTAINED_DAMAGE, BURST_DAMAGE, AREA_DAMAGE, ATTACK_RATE, ARMOR_PENETRATION,
                                    DAMAGE_OVER_TIME, DELIVERY_RELIABILITY, SHIELD_INTERACTION -> true;
                            default -> false;
                        })).toList());
                Map<Participation, Set<ResourceLocation>> activeSkills = new EnumMap<>(Participation.class);
                Map<Participation, DefensivePressure> selectionDefenses = new EnumMap<>(Participation.class);
                for (var participation : Participation.values()) {
                    Set<ResourceLocation> selection = switch (participation) {
                        case EQUIPMENT_FOCUSED, BONUS_FOCUSED -> Set.of();
                        case MIXED -> moderateRanks.keySet();
                        case CATEGORY_SPECIALIZED -> specialized;
                        default -> fullRanks.keySet();
                    };
                    activeSkills.put(participation, selection);
                    if (detailed) selectionDefenses.put(participation, defensivePressure(
                            participation == Participation.MIXED ? moderateEffects : fullEffects, selection));
                }
                double[] healthStates = fullRanks.containsKey(SkillIds.DESPERATION) || fullRanks.containsKey(SkillIds.RISING_RECOVERY)
                        ? new double[]{1, .200001, .000001} : new double[]{1};
                String rankDescription = detailed ? "Candidate effective ranks=" + new TreeMap<>(fullRanks) : "";
                Map<Double, List<String>> assumptions = new HashMap<>();
                if (detailed) for (double healthFraction : healthStates) assumptions.put(healthFraction, List.of(referenceAssumption,
                        rankDescription + "; Desperation current-health fraction=" + healthFraction + ".",
                        "Each participation uses its own weapon damage/cadence and ceiling. Ordinary armor is applied once. Low-health EHP uses current, not maximum, health.",
                        "Posture defense assumes a fully built eligible state: intentional movement for dodge, stationary facing of a hostile threat for Bulwark, or repeated identical eligible damage for Adaptive. Status bounds require a harmful application; Mirror additionally requires a valid hostile source and ready cooldown."));
                for (var archetype : EquipmentProfileRegistry.values()) {
                    if (archetype.baselineMultiplier(damageProperty) <= 0 || archetype.baselineMultiplier(speedProperty) <= 0) continue;
                    var ascendance = new Equipment(Math.max(.01, equipmentValue(runtime, damage, archetype, damageProperty)),
                            equipmentValue(runtime, attackRate, archetype, speedProperty), baseline.fullSetArmor(), baseline.fullSetToughness(), health, 0);
                    var originalEquipment=equipmentLimit==null?external:new Equipment(
                            Math.max(.01,equipmentValue(runtime,equipmentLimit.value(damageProperty),archetype,damageProperty)),
                            equipmentValue(runtime,equipmentLimit.value(speedProperty),archetype,speedProperty),
                            equipmentLimit.fullSetArmor(),equipmentLimit.fullSetToughness(),health,0);
                    // Check the ordinary-health boundary and the near-zero worst case.
                    for (double healthFraction : healthStates) {
                        checkedCases++;
                        String id = detailed ? tier.id() + "/" + archetype.id() + "/" + scenario.id() + "/health_" + healthFraction : "";
                        Map<Participation, Metrics> metrics = detailed ? new EnumMap<>(Participation.class) : null;
                        Map<Participation, Limits> limits = detailed ? new EnumMap<>(Participation.class) : null;
                        Map<Participation, DefensivePressure> defenses = detailed ? new EnumMap<>(Participation.class) : null;
                        for (var participation : Participation.values()) {
                            Equipment item = participation == Participation.BONUS_FOCUSED || participation == Participation.SKILL_FOCUSED ? external : ascendance;
                            Modifier nexus = switch (participation) {
                                case EQUIPMENT_FOCUSED, SKILL_FOCUSED -> Modifier.none();
                                case BROAD_GENERALIST -> nexusModerate;
                                case CATEGORY_SPECIALIZED -> nexusFull.offenseOnly();
                                default -> nexusFull;
                            };
                            Set<ResourceLocation> active = activeSkills.get(participation);
                            var effects = participation == Participation.MIXED ? moderateEffects : fullEffects;
                            if (detailed) defenses.put(participation, selectionDefenses.get(participation));
                            double actualHealth = active.contains(SkillIds.DESPERATION) || active.contains(SkillIds.RISING_RECOVERY) ? healthFraction : 1;
                            double healingEffectiveness = switch (participation) {
                                case EQUIPMENT_FOCUSED, SKILL_FOCUSED, CATEGORY_SPECIALIZED -> 1;
                                default -> participation == Participation.BROAD_GENERALIST ? moderateHealing : fullHealing;
                            };
                            var measured = combat(effects, active, family, item, nexus, incoming, window, actualHealth, healingEffectiveness);
                            double target = plan.developed() ? BuildPowerTargets.multiplier(settings, band, participation)
                                    : BuildPowerTargets.rankOneMultiplier(settings, band, participation);
                            // The former rank-one sustain reserve was already filled before
                            // recovery existed. Spend that provisional future allowance on
                            // real recovery, then fit future posture/recovery growth jointly.
                            // The approved final tier ceilings and every current value stay fixed.
                            double healingTarget = active.contains(SkillIds.RISING_RECOVERY) || active.contains(SkillIds.LIFE_STEAL)
                                    ? BuildPowerTargets.multiplier(settings, band, participation) : target;
                            if (plan.developed() && active.contains(SkillIds.RISING_RECOVERY)) {
                                var vitality = SkillBalanceSemantics.require(SkillIds.RISING_RECOVERY);
                                healingTarget += vitality.expectedAvailability()
                                        * vitality.weights().getOrDefault(CapabilityAxis.REGENERATION, 0.0);
                            } else if (plan.developed() && active.contains(SkillIds.LIFE_STEAL)) {
                                var vitality = SkillBalanceSemantics.require(SkillIds.LIFE_STEAL);
                                healingTarget += vitality.expectedAvailability()
                                        * vitality.weights().getOrDefault(CapabilityAxis.HEALING, 0.0);
                            }
                            double burst = plan.developed() ? BuildPowerTargets.burstMultiplier(settings, band, participation,
                                    active.contains(SkillIds.DESPERATION) && actualHealth <= .2) : target;
                            // Tool archetype identity/rounding are not purchased added power.
                            // Frozen before exact overrides: a raised weapon stat
                            // cannot authorize its own higher equipment ceiling.
                            var base = BuildComposition.compose(item==external?external:originalEquipment, Modifier.none(), Modifier.none(), incoming, window);
                            var ceiling = new Limits(withNativeAllowance(reference.dps(), base.sustainedDamage(), target),
                                    withNativeAllowance(reference.burst(), base.burstDamage(), burst), reference.dps() * Math.max(0, target - 1) * 2,
                                    withNativeAllowance(externalMetrics.effectiveHealth(), base.effectiveHealth(), target),
                                    withNativeAllowance(externalMetrics.effectiveHealth(), base.sustainedHealth(), healingTarget * 1.35),
                                    external.health() * Math.max(0, healingTarget - 1) / window);
                            if (detailed) {
                                metrics.put(participation, measured);
                                limits.put(participation, ceiling);
                            } else for (var metric : Metric.values()) {
                                if (channel != null && !channel.includes(metric)) continue;
                                double actual = measured.value(metric), limit = ceiling.value(metric);
                                if (actual > limit + 1e-9 * Math.max(1, limit)) return null;
                            }
                        }
                        if (!detailed) continue;
                        List<Violation> violations = new ArrayList<>();
                        metrics.forEach((participation, value) -> {
                            for (var metric : Metric.values()) {
                                double actual = value.value(metric), limit = limits.get(participation).value(metric);
                                if (actual > limit + 1e-9 * Math.max(1, limit)) violations.add(new Violation(participation, metric, actual, limit));
                            }
                        });
                        var evaluation = new Evaluation(id, metrics, violations, assumptions.get(healthFraction));
                        result.add(new Case(tier.id().toString(), archetype.id() + "/" + scenario.id() + "/health_" + healthFraction,
                                evaluation, Collections.unmodifiableMap(limits), Collections.unmodifiableMap(defenses)));
                    }
                }
            }
        }
        if (checkedCases == 0) throw new IllegalStateException("No registered equipment families were available for numeric build validation");
        if (!detailed) return SAFE_PROBE;
        return new Analysis(1, result, List.of(
                "Only implemented skills and evaluator-approved dependency/choice/replacement selections contribute. Full builds use a provisional future five-rank stress projection, not currently purchasable ranks or a catalog design decision.",
                "Fully developed apex scenarios use every eligible skill's entire projectionRanks curve, including skills first available at that tier. Earlier tiers retain staged development estimates. This corrects the former apex underprojection of late-tier skills without creating player-facing rank gates.",
                "Default final-output ceilings at Transcendent: equipment 1x pack parity; external equipment plus Nexus 2x; external equipment plus skills 2x; combined builds 3x. These are ceilings, not guaranteed multipliers for every legal selection. Gameplay still compounds damage and attack speed; the generator checks the resulting output.",
                "Combined progression ceilings are 1.5x / 1.7x / 2x / 2.5x / 3x. Existing friendly power controls scale added headroom, not the 1x equipment foundation.",
                "Only Desperation builds at <=20% current health may use the 4x Transcendent combined BURST ceiling. A near-zero-health worst case and the ordinary-health boundary are both checked; sustained damage stays capped at 3x. Removing defense investments alone grants no burst exception.",
                "Full Nexus reaches each generated tier cap; moderate Nexus uses each real curve at half investment. Moderate skills use eligible prerequisite-free roots at rank one.",
                "Offense, defense and healing calibrate separately. An explicit first-purchase budget reserves later-tier room for possible future ranks. Rank one is frozen before projected-rank calibration; unrelated implemented effect consumers then recover unused headroom individually. Planned skills never tax current effects.",
                "Late posture first-rank magnitudes recover independently after the shared initial calibration, up to their requested settings and the same complete first-rank survival limits. An earlier-tier Nexus-only bottleneck cannot unnecessarily suppress a posture unavailable at that tier. Recovery changes neither equipment nor offensive tuning.",
                "Primary hits multiply their actual skill damage modifiers, then add flat Static Charge, then apply an armed Riposte and fully charged Stored Force. Static Charge is averaged once over its configured sprint charge cycle for sustained damage, and counted once at full charge for burst.",
                "Kindling uses maintained burning; Combustion and Shatter are secondary-target on-kill damage, not invented extra damage against their already-dead primary target. Area bounds assume one elemental completion per buildup cycle and bounded distinct victims; they are estimates, not measured combat logs.",
                 "Nexus passive regeneration is an existing health-regeneration bonus. Rising Recovery multiplies that bonus and eligible native food regeneration by the same missing-health curve; Healing Effectiveness remains separate.",
                "Armor penetration retains an explicit conservative armor-pressure allowance. Reflection, guard amplification, Crowd Reprisal, control, interception, flight and gathering retain separate semantic budgets; no prevented-hit or Attunement activity is invented.",
                "Evasive expected avoidance and Bulwark/Adaptive damage reduction use actual generated values at the contributing rank, each under its strongest eligible posture condition. The three exclusive postures never stack. Meter build/drain and type-change exposure reduce real availability; peak bounds deliberately do not assume free continuous uptime.",
                "Status Mirror and Pure State share an independent exclusive choice. harmful-status prevention is a capability fraction, and Mirror transfer capacity is applications/second bounded by its generated cooldown. Missing pack harmful-application rates and source acceptance evidence prevent converting these into damage, EHP, or guaranteed status uptime.",
                "Pure State is binary at every diagnostic rank. Its provisional catalog curve supplies no numeric consumer and no invented rank benefit; the later catalog-wide rank design must decide whether it should have ranks. Already-active harmful effects are not cleansed.",
                "Damage-routing reservoirs and Adrenaline participate in the normal first-rank and projected-rank composition guard. Recovery is subsequently allocated from remaining sustain headroom; recovery and provisional posture growth share the developed survival ceiling. A profile rebuild may recalibrate real consumers under the same friendly controls.",
                "Implemented recovery may consume the prior provisional first-purchase sustain reserve up to the unchanged final tier ceiling; offense and effective-health reserves are untouched. Future recovery and posture growth are then calibrated together instead of flattening a newly implemented skill to zero.",
                 "Rising Recovery multiplies the existing Nexus passive health-regeneration bonus and adds the same missing-health multiplier to eligible native food regeneration; the projection uses the fastest saturated cadence (one health per ten ticks) as its conservative upper bound. Native food eligibility/exhaustion remains authoritative; Healing Effectiveness does not multiply either regeneration source.",
                "Life Steal uses direct primary weapon hit damage and cadence, including native primary Static Charge and direct counter damage, with same-target chain progress derived from the native timeout and accepted-hit rate. Native Ricochet/Piercing continuation hits contribute bounded base-fraction healing because each distinct victim resets the chain; primary chain plus continuation is a conservative capacity envelope, not a promise of simultaneous maintained chains. Separate elemental/payload/returned damage does not heal. Healing Effectiveness applies to Life Steal; actual healing is bounded by useful missing HP or, with Pain Purge, recoverable delayed damage.",
                "Hunger Ward protection is bounded by BOTH its damage-share survival budget and one full native food+saturation reservoir. Damage Ceiling adds at most its finite Trauma capacity divided by its cost, only when the reference hit crosses the cap; ignoring the reduced maximum health is a conservative upper bound. Staggered Pain adds no permanent EHP. Pain Purge mirrors actual generated healing plus conditional native saturated-food regeneration into debt, at its calibrated ratio; no potion or food throughput is invented. Metabolic Conversion remains a conditional resource conversion. Adrenaline contributes its real peak attack-speed multiplier in all weapon families. Feast Reflex duration and Inner Sustenance food/saturation restoration retain native resource/time units in vitalityPolicy. Food inventory, consumption side effects and phantom/sleep immunity are conditional capabilities, not invented health or damage. Out-of-combat hunger recovery never counts as in-combat healing.",
                "Pack parity preserves existing equipment curves and attainable weapon/armor pairings. Physical quantization and tool archetype baselines are not nerfed to make a bonus budget fit. External-gear projections assume required equipment access; gameplay still enforces eligibility, ownership and worn-slot coverage."));
    }

    /** Native identity is already allowed independently of purchases. Do not spend the
     * external added-power budget a second time on that protected equipment floor.
     * Only the frozen pre-override baseline may supply nativeBase. */
    static double withNativeAllowance(double external, double nativeBase, double multiplier) {
        return Math.max(external, nativeBase) + external * Math.max(0, multiplier - 1);
    }

    static SkillEffectBalanceSettings rankedEffects(RuntimeBalanceDefinition runtime, Map<ResourceLocation, Integer> ranks) {
        if (runtime.composition().getOrDefault("meaningful_progression", 0.0) == 1)
            return SkillRankEffectScaling.applyResolved(runtime.config().skillEffects(), ranks, runtime.skillCurves());
        return SkillRankEffectScaling.apply(runtime.config().skillEffects(), ranks, (id, rank) -> {
            var curve = runtime.skillCurves().get(id.toString());
            return curve.ranks().get(rank - 1).powerMultiplier() / curve.ranks().getFirst().powerMultiplier();
        });
    }
    static double equipmentValue(RuntimeBalanceDefinition runtime, double base, EquipmentProfileDefinition profile, EquipmentBaselineProperty property) {
        return runtime.composition().getOrDefault("equipment_quantization", 0.0) == 1.0
                ? EquipmentBaselineService.resolvedValue(base, profile, property) : base * profile.baselineMultiplier(property);
    }
    private static Modifier nexus(RuntimeBalanceDefinition runtime, AscendanceTierDefinition tier, String family, boolean moderate) {
        var damage = family.equals("ranged") ? EssenceStats.RANGED_DAMAGE : family.equals("caster") ? EssenceStats.MAGIC_DAMAGE : EssenceStats.MELEE_DAMAGE;
        var speed = family.equals("ranged") ? EssenceStats.RANGED_ATTACK_SPEED : family.equals("caster") ? EssenceStats.MAGIC_CAST_SPEED : EssenceStats.MELEE_ATTACK_SPEED;
        double resistance = Math.max(bonus(runtime, tier, EssenceStats.MELEE_RESISTANCE, moderate),
                Math.max(bonus(runtime, tier, EssenceStats.RANGED_RESISTANCE, moderate), bonus(runtime, tier, EssenceStats.MAGIC_RESISTANCE, moderate))) / 100;
        return new Modifier(0, 1 + bonus(runtime, tier, damage, moderate) / 100, 1 + bonus(runtime, tier, speed, moderate) / 100,
                0, 0, bonus(runtime, tier, EssenceStats.MAX_HEALTH, moderate) * 2, 0, 0, resistance, 0,
                bonus(runtime, tier, EssenceStats.HEALTH_REGENERATION, moderate) * 2, 0);
    }
    private static double bonus(RuntimeBalanceDefinition runtime, AscendanceTierDefinition tier, StatDefinition stat, boolean moderate) {
        var profile = runtime.config().balanceProfile(); long cap = profile.getInvestmentCap(tier, stat);
        return runtime.config().statMaxBonus(stat) * StatScalingService.realizedProgressionForInvestment(stat, moderate ? cap / 2 : cap, tier, profile);
    }

    /** Same primary-hit order as SkillEffectRuntime/GuardCounterattackService; secondary damage stays separate. */
    static Metrics combat(SkillEffectBalanceSettings s, Set<ResourceLocation> active, String family, Equipment item,
                          Modifier nexus, double incoming, double window, double healthFraction) {
        return combat(s, active, family, item, nexus, incoming, window, healthFraction, 1);
    }
    static Metrics combat(SkillEffectBalanceSettings s, Set<ResourceLocation> active, String family, Equipment item,
                          Modifier nexus, double incoming, double window, double healthFraction, double healingEffectiveness) {
        double damage = 1, speed = 1, flatBurst = 0, flatDps = 0, area = 0;
        if (active.contains(SkillIds.FRENZY)) {
            damage *= 1 + s.frenzy().maxStacks() * s.frenzy().damageBonusPercentPerStack() / 100;
            speed *= 1 + s.frenzy().maxStacks() * s.frenzy().attackSpeedBonusPercentPerStack() / 100;
        }
        if (active.contains(SkillIds.ARMOR_CRACK)) damage *= 1 + Math.min(.8, s.armorCrack().maxStacks() * s.armorCrack().armorReductionPerStack() / 25);
        if (active.contains(SkillIds.DESPERATION)) damage *= 1 + (1 - healthFraction) * s.desperation().maxDamageBonusPercent() / 100;
        if (active.contains(SkillIds.DEATH_RUSH)) speed *= 1 + s.deathRush().maxStacks() * (family.equals("ranged")
                ? s.deathRush().bowDrawSpeedBonusPercentPerStack() : family.equals("caster")
                ? s.deathRush().castSpeedBonusPercentPerStack() : s.deathRush().attackSpeedBonusPercentPerStack()) / 100;
        if (active.contains(SkillIds.KINDLING)) damage *= 1 + s.kindling().burningDamageAmplificationPercent() / 100;
        if (active.contains(SkillIds.ADRENALINE)) speed *= 1 + s.vitality().damage().adrenaline().attackSpeedBonus();
        double hit = item.hitDamage() * nexus.damageMultiplier() * damage;
        double rate = item.attacksPerSecond() * nexus.attackRateMultiplier() * speed;
        if (active.contains(SkillIds.STATIC_CHARGE)) {
            flatBurst = s.staticCharge().lightningDamage();
            double chargeSeconds = s.staticCharge().maximumCharge() / Math.max(.001, 20 * s.staticCharge().sprintPerTick());
            flatDps = flatBurst * Math.min(rate, 1 / Math.max(.05, chargeSeconds));
        }
        double sustained = hit * rate + flatDps;
        if (active.contains(SkillIds.KINDLING)) sustained += hit * s.kindling().burningDamagePercentPerSecond() / 100;
        double burst = hit + flatBurst;
        if (family.equals("melee_shield")) burst += guardCounterBurst(s, active, burst);
        if (active.contains(SkillIds.COMBUSTION)) area += s.combustion().damage() * s.combustion().targetsPerBurst()
                * rate / Math.max(1, Math.ceil(s.kindling().maxHeat() / (double) Math.max(1, s.kindling().heatPerHit())));
        if (active.contains(SkillIds.SHATTER)) area += s.shatter().shardDamage() * s.shatter().maximumTargets()
                * rate / Math.max(1, Math.ceil(s.frostbite().maxChill() / (double) Math.max(1, s.frostbite().chillPerHit())));
        if (active.contains(SkillIds.CHAIN_STRIKE)) for (int i = 1; i <= s.chainStrike().maximumJumps(); i++)
            area += flatDps * Math.pow(s.chainStrike().damageFalloff(), i);
        if (active.contains(SkillIds.RICOCHET)) for (int i = 1; i <= s.projectiles().ricochets(); i++)
            area += hit * rate * Math.pow(s.projectiles().ricochetDamageMultiplier(), i);
        if (active.contains(SkillIds.PIERCING_PROJECTILE)) for (int i = 1; i <= s.projectiles().penetrations(); i++)
            area += hit * rate * Math.pow(s.projectiles().piercingDamageMultiplier(), i);
        area += hit * rate * projectilePayloadArea(s, active);
        var pressure = defensivePressure(s, active);
        var posture = new Modifier(0, 1, 1, 0, 0, 0, 0, 0,
                pressure.peakDamageReduction(), pressure.peakAvoidance(), 0, 0);
        var defense = BuildComposition.compose(item, nexus, posture, incoming, window);
        double currentEhp = defense.effectiveHealth() * healthFraction;
        double additionalHealing = (vitalityHealing(s, active, hit + flatDps / Math.max(.000001, rate), rate, family, healthFraction,
                nexus.healingPerSecond())
                + directContinuationHealing(s, active, hit, rate, family))
                * (active.contains(SkillIds.LIFE_STEAL) ? healingEffectiveness : 1);
        double maximumHealth = item.health() + nexus.bonusHealth();
        double taken = maximumHealth / defense.effectiveHealth();
        // Native absorption is spent BEFORE the post-absorption damage styles. Count one full earned
        // reservoir, not an invented kill/refill rate, and never amplify it with Damage Ceiling.
        double wardProtection = active.contains(SkillIds.SOUL_WARD)
                ? com.mistaboom.essence_ascendance.skill.effect.VitalityWardEffects.capacity(maximumHealth,
                        s.vitality().wards(), active.contains(SkillIds.DEEP_WARD)) / taken : 0;
        if (active.contains(SkillIds.SOUL_WARD) && active.contains(SkillIds.SHATTERING_WARD)
                && !active.contains(SkillIds.DEEP_WARD)) {
            // Peak conditional regeneration is a conservative bound; no assumed break frequency or
            // knockback-as-EHP credit. Accepted native healing also participates in Pain Purge below.
            additionalHealing += maximumHealth * s.vitality().wards().shatteringWard().healingFractionPerSecond();
        }
        double resourceProtection = 0;
        if (active.contains(SkillIds.HUNGER_WARD)) {
            var ward = s.vitality().damage().hungerWard();
            resourceProtection = com.mistaboom.essence_ascendance.vitality.DamageRoutingMath.wardProtection(
                    maximumHealth * healthFraction, net.minecraft.world.food.FoodConstants.MAX_FOOD
                            * 2.0 * ward.healthPerFoodPoint(), ward.damageShare());
        }
        double percentageSurvival = 1;
        if (active.contains(SkillIds.DAMAGE_CEILING)) {
            double fraction = s.vitality().damage().damageCeiling().damageTakenFraction();
            percentageSurvival = 1 / fraction;
            taken *= fraction;
            currentEhp *= percentageSurvival;
            // Prevention is proportional on every hit, not a single-hit cap or a finite HP bank.
            // Sustained healing below is an optimistic bound: lost capacity can only constrain it.
            // We do not credit capacity restoration as healing or assume mid-combat Trauma clears.
        }
        currentEhp += resourceProtection / taken + wardProtection;
        if (active.contains(SkillIds.PAIN_PURGE) && active.contains(SkillIds.STAGGERED_PAIN)) {
            // The native saturated-food rate is conditional, not invented food/potion throughput.
            // Count fully useful HP plus debt recovery conservatively; queue cancellation is not EHP.
            double nativeFoodHealing = 20.0 / net.minecraft.world.food.FoodConstants.HEALTH_TICK_COUNT_SATURATED;
            additionalHealing += (Math.max(0, defense.healingPerSecond() + additionalHealing) + nativeFoodHealing)
                    * s.vitality().damage().painPurge().queuePerHealing();
        }
        return new Metrics(sustained, burst, area, currentEhp,
                currentEhp + (defense.sustainedHealth() - defense.effectiveHealth()) * percentageSurvival
                        + additionalHealing * window / taken,
                defense.healingPerSecond() + additionalHealing);
    }

    static boolean vitalitySkill(ResourceLocation id) {
        return id.equals(SkillIds.RISING_RECOVERY) || id.equals(SkillIds.LIFE_STEAL)
                || id.equals(SkillIds.FEAST_REFLEX) || id.equals(SkillIds.INNER_SUSTENANCE);
    }

    static double vitalityHealing(SkillEffectBalanceSettings settings, Set<ResourceLocation> active,
                                  double directHit, double attacksPerSecond, String family, double healthFraction) {
        return vitalityHealing(settings, active, directHit, attacksPerSecond, family, healthFraction, 0);
    }

    static double vitalityHealing(SkillEffectBalanceSettings settings, Set<ResourceLocation> active,
                                  double directHit, double attacksPerSecond, String family, double healthFraction,
                                  double nexusPassiveHealingPerSecond) {
        if (active.contains(SkillIds.RISING_RECOVERY) && active.contains(SkillIds.LIFE_STEAL)
                || active.contains(SkillIds.FEAST_REFLEX) && active.contains(SkillIds.INNER_SUSTENANCE))
            throw new IllegalArgumentException("Vitality projection requires an effective exclusive selection");
        var s = settings.vitality();
        if (active.contains(SkillIds.RISING_RECOVERY)) {
            double missingCurve = Math.pow(Math.clamp(1 - healthFraction, 0, 1), s.risingRecovery().recoveryCurveExponent());
            double acceleratedFraction = s.risingRecovery().maxSpeedBonus() * missingCurve;
            double nativeFoodHealing = 20.0 / net.minecraft.world.food.FoodConstants.HEALTH_TICK_COUNT_SATURATED;
            return (nativeFoodHealing + Math.max(0, nexusPassiveHealingPerSecond)) * acceleratedFraction;
        }
        if (!active.contains(SkillIds.LIFE_STEAL)) return 0;
        double directCounter = family.equals("melee_shield") ? guardCounterBurst(settings, active, directHit) : 0;
        // Runtime expires at elapsed >= timeout, including exact equality. The
        // balance projection uses the number of accepted hits that fit inside
        // that native timeout instead of assuming the maximum chain is always
        // maintained.
        double hitsPerWindow = Math.max(1.0, attacksPerSecond * s.lifeSteal().chainTimeoutTicks() / 20.0);
        double chainProgress = Math.clamp((hitsPerWindow - 1.0) / Math.max(1, s.lifeSteal().maxChainHits() - 1), 0.0, 1.0);
        double fraction = s.lifeSteal().baseHealingFraction()
                + (s.lifeSteal().maxChainHits() - 1) * s.lifeSteal().perHitHealingFraction() * chainProgress;
        return (directHit + directCounter) * attacksPerSecond * fraction;
    }

    /** Native nonredirected continuation is direct weapon damage; payload/elemental area is not. */
    static double directContinuationHealing(SkillEffectBalanceSettings settings, Set<ResourceLocation> active,
                                            double ordinaryHit, double attacksPerSecond, String family) {
        if (!active.contains(SkillIds.LIFE_STEAL) || family.equals("melee_shield")) return 0;
        var p = settings.projectiles();
        boolean ricochet = active.contains(SkillIds.RICOCHET), piercing = active.contains(SkillIds.PIERCING_PROJECTILE);
        if (ricochet && piercing) throw new IllegalArgumentException("A projectile cannot select both continuation paths");
        int contacts = Math.min(Math.max(0, p.maximumImpacts() - 1), ricochet ? p.ricochets() : piercing ? p.penetrations() : 0);
        double retention = ricochet ? p.ricochetDamageMultiplier() : p.piercingDamageMultiplier();
        double directExtra = 0;
        for (int contact = 1; contact <= contacts; contact++) directExtra += ordinaryHit * Math.pow(retention, contact);
        return directExtra * attacksPerSecond * settings.vitality().lifeSteal().baseHealingFraction();
    }

    public static DefensivePressure defensivePressure(SkillEffectBalanceSettings s, Set<ResourceLocation> active) {
        long postures = List.of(SkillIds.EVASIVE_CURRENT, SkillIds.BULWARK_STANCE, SkillIds.ADAPTIVE_GUARD)
                .stream().filter(active::contains).count();
        if (postures > 1 || active.contains(SkillIds.STATUS_MIRROR) && active.contains(SkillIds.PURE_STATE))
            throw new IllegalArgumentException("Numeric defense requires an effective exclusive selection");
        double avoidance = active.contains(SkillIds.EVASIVE_CURRENT) ? s.posture().evasive().maximumDodgeChance() : 0;
        double reduction = active.contains(SkillIds.BULWARK_STANCE) ? s.posture().bulwark().maximumResistance() : 0;
        if (active.contains(SkillIds.ADAPTIVE_GUARD)) reduction = s.posture().adaptive().resistancePerStack()
                * (s.posture().adaptive().maximumStacks() - s.posture().adaptive().minimumHits() + 1);
        boolean mirror = active.contains(SkillIds.STATUS_MIRROR);
        return new DefensivePressure(avoidance, reduction, reduction > 0 && active.contains(SkillIds.BULWARK_STANCE)
                && s.posture().bulwark().knockbackThreshold() <= 1, mirror || active.contains(SkillIds.PURE_STATE) ? 1 : 0,
                mirror ? 20.0 / s.status().mirrorCooldownTicks() : 0,
                mirror ? s.status().mirrorMaximumDurationTicks() : 0, mirror ? s.status().mirrorMaximumAmplifier() : 0);
    }

    static double guardCounterBurst(SkillEffectBalanceSettings s, Set<ResourceLocation> active, double hit) {
        double bonus = active.contains(SkillIds.RIPOSTE) ? hit * s.guard().riposte().damageScale() : 0;
        if (active.contains(SkillIds.STORED_FORCE)) bonus += s.guard().storedForce().capacity() * s.guard().storedForce().damageScale();
        return bonus;
    }
    static double projectilePayloadArea(SkillEffectBalanceSettings s, Set<ResourceLocation> active) {
        double area = 0;
        if (active.contains(SkillIds.EXPLOSIVE_PAYLOAD)) {
            int contacts = 1; double retention = 1;
            if (active.contains(SkillIds.RICOCHET)) { contacts += s.projectiles().ricochets(); retention = s.projectiles().ricochetDamageMultiplier(); }
            if (active.contains(SkillIds.PIERCING_PROJECTILE)) { contacts += s.projectiles().penetrations(); retention = s.projectiles().piercingDamageMultiplier(); }
            contacts = Math.min(contacts, s.projectiles().payloadTriggerBudget());
            double contactDamage = 1;
            for (int contact = 0; contact < contacts; contact++) {
                area += contactDamage * s.projectiles().explosiveDamageScale() * s.combustion().targetsPerBurst();
                contactDamage *= retention;
            }
        }
        return area;
    }
}
