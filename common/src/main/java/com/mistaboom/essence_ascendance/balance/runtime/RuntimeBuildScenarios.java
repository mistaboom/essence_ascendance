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
        public void requireSafe() { for (var row : cases) row.evaluation().requireSafe(); }
        public boolean safeFor(Channel channel) {
            return cases.stream().flatMap(row -> row.evaluation().violations().stream())
                    .noneMatch(v -> channel == null || channel.includes(v.metric()));
        }
    }
    public record Plan(Map<ResourceLocation, List<SkillLoadoutProjection.Scenario>> full,
                       Map<ResourceLocation, List<SkillLoadoutProjection.Scenario>> moderate, boolean developed,
                       EquipmentBaselineConfig equipmentLimits) { }
    public static Plan plan() { return plan(true); }
    public static Plan plan(RuntimeBalanceDefinition runtime, boolean developed) {
        var plan=plan(developed);
        return new Plan(plan.full(),plan.moderate(),developed,runtime.config().equipmentBaselineConfig());
    }
    public static Plan plan(boolean developed) {
        Map<ResourceLocation, List<SkillLoadoutProjection.Scenario>> full = new LinkedHashMap<>(), moderate = new LinkedHashMap<>();
        int apexOrder = AscendanceTierRegistry.powerTiers().stream().mapToInt(AscendanceTierDefinition::order).max().orElseThrow();
        for (var tier : AscendanceTierRegistry.powerTiers()) {
            Map<ResourceLocation, Integer> fullRanks = new LinkedHashMap<>(), moderateRanks = new LinkedHashMap<>();
            for (var skill : SkillRegistry.values()) {
                int projectedRank = developed && tier.order() == apexOrder ? skill.rankPolicy().projectionRanks()
                        : developed ? Math.min(skill.rankPolicy().projectionRanks(), Math.max(1,
                        tier.order() - AscendanceTierRegistry.get(skill.requiredTierId()).orElseThrow().order() + 1)) : 1;
                fullRanks.put(skill.id(), projectedRank);
                moderateRanks.put(skill.id(), skill.prerequisites().isEmpty() ? 1 : 0);
            }
            full.put(tier.id(), SkillLoadoutProjection.project(SkillRegistry.values(), tier.id(), fullRanks,
                    (id, rank) -> SkillRegistry.require(id).rankPolicy().curve().power(rank), Map.of(), false).scenarios());
            moderate.put(tier.id(), SkillLoadoutProjection.project(SkillRegistry.values(), tier.id(), moderateRanks,
                    (id, rank) -> 1, Map.of(), false).scenarios());
        }
        return new Plan(Collections.unmodifiableMap(full), Collections.unmodifiableMap(moderate), developed, null);
    }

    public static Analysis analyze(RuntimeBalanceDefinition runtime, PackEvidence evidence, BalanceSettings settings,
                                   Plan plan) {
        List<Case> result = new ArrayList<>();
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
            Set<String> duplicateSelections = new HashSet<>();
            for (var scenario : plan.full().get(tier.id())) {
                String family = scenario.equipmentContext();
                if (!references.containsKey(family)) continue;
                if (!duplicateSelections.add(family + new TreeMap<>(scenario.contributingRanks()))) continue;
                var reference = references.get(family);
                var external = new Equipment(reference.damage(), reference.rate(), armor, toughness, health, 0);
                var externalMetrics = BuildComposition.compose(external, Modifier.none(), Modifier.none(), incoming, window);
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
                var nexusFull = nexus(runtime, tier, family, false);
                var nexusModerate = nexus(runtime, tier, family, true);
                for (var archetype : EquipmentProfileRegistry.values()) {
                    if (archetype.baselineMultiplier(damageProperty) <= 0 || archetype.baselineMultiplier(speedProperty) <= 0) continue;
                    var ascendance = new Equipment(Math.max(.01, equipmentValue(runtime, damage, archetype, damageProperty)),
                            equipmentValue(runtime, attackRate, archetype, speedProperty), baseline.fullSetArmor(), baseline.fullSetToughness(), health, 0);
                    var originalEquipment=equipmentLimit==null?external:new Equipment(
                            Math.max(.01,equipmentValue(runtime,equipmentLimit.value(damageProperty),archetype,damageProperty)),
                            equipmentValue(runtime,equipmentLimit.value(speedProperty),archetype,speedProperty),
                            equipmentLimit.fullSetArmor(),equipmentLimit.fullSetToughness(),health,0);
                    // Check the ordinary-health boundary and the near-zero worst case.
                    double[] healthStates = fullRanks.containsKey(SkillIds.DESPERATION)
                            ? new double[]{1, .200001, .000001} : new double[]{1};
                    for (double healthFraction : healthStates) {
                        String id = tier.id() + "/" + archetype.id() + "/" + scenario.id() + "/health_" + healthFraction;
                        Map<Participation, Metrics> metrics = new EnumMap<>(Participation.class);
                        Map<Participation, Limits> limits = new EnumMap<>(Participation.class);
                        Map<Participation, DefensivePressure> defenses = new EnumMap<>(Participation.class);
                        for (var participation : Participation.values()) {
                            Equipment item = participation == Participation.BONUS_FOCUSED || participation == Participation.SKILL_FOCUSED ? external : ascendance;
                            Modifier nexus = switch (participation) {
                                case EQUIPMENT_FOCUSED, SKILL_FOCUSED -> Modifier.none();
                                case BROAD_GENERALIST -> nexusModerate;
                                case CATEGORY_SPECIALIZED -> nexusFull.offenseOnly();
                                default -> nexusFull;
                            };
                            Set<ResourceLocation> active = switch (participation) {
                                case EQUIPMENT_FOCUSED, BONUS_FOCUSED -> Set.of();
                                case MIXED -> moderateRanks.keySet();
                                default -> fullRanks.keySet();
                            };
                            if (participation == Participation.CATEGORY_SPECIALIZED) active = Set.copyOf(active.stream()
                                    .filter(skillId -> SkillBalanceSemantics.require(skillId).weights().keySet().stream().anyMatch(axis -> switch (axis) {
                                        case SUSTAINED_DAMAGE, BURST_DAMAGE, AREA_DAMAGE, ATTACK_RATE, ARMOR_PENETRATION,
                                                DAMAGE_OVER_TIME, DELIVERY_RELIABILITY, SHIELD_INTERACTION -> true;
                                        default -> false;
                                    })).toList());
                            var effects = participation == Participation.MIXED ? moderateEffects : fullEffects;
                            defenses.put(participation, defensivePressure(effects, active));
                            double actualHealth = active.contains(SkillIds.DESPERATION) ? healthFraction : 1;
                            metrics.put(participation, combat(effects, active, family, item, nexus, incoming, window, actualHealth));
                            double target = plan.developed() ? BuildPowerTargets.multiplier(settings, band, participation)
                                    : BuildPowerTargets.rankOneMultiplier(settings, band, participation);
                            double burst = plan.developed() ? BuildPowerTargets.burstMultiplier(settings, band, participation, actualHealth <= .2) : target;
                            // Tool archetype identity/rounding are not purchased added power.
                            // Frozen before exact overrides: a raised weapon stat
                            // cannot authorize its own higher equipment ceiling.
                            var base = BuildComposition.compose(item==external?external:originalEquipment, Modifier.none(), Modifier.none(), incoming, window);
                            limits.put(participation, new Limits(Math.max(reference.dps() * target, base.sustainedDamage()),
                                    Math.max(reference.burst() * burst, base.burstDamage()), reference.dps() * Math.max(0, target - 1) * 2,
                                    Math.max(externalMetrics.effectiveHealth() * target, base.effectiveHealth()),
                                    Math.max(externalMetrics.effectiveHealth() * target * 1.35, base.sustainedHealth()), health * Math.max(0, target - 1) / window));
                        }
                        List<Violation> violations = new ArrayList<>();
                        metrics.forEach((participation, value) -> {
                            for (var metric : Metric.values()) {
                                double actual = value.value(metric), limit = limits.get(participation).value(metric);
                                if (actual > limit + 1e-9 * Math.max(1, limit)) violations.add(new Violation(participation, metric, actual, limit));
                            }
                        });
                        var evaluation = new Evaluation(id, metrics, violations, List.of(
                                "Reference incoming hit=" + incoming + " HP; fully useful healing window=" + window + " seconds.",
                                "Candidate effective ranks=" + new TreeMap<>(fullRanks) + "; Desperation current-health fraction=" + healthFraction + ".",
                                "Each participation uses its own weapon damage/cadence and ceiling. Ordinary armor is applied once. Low-health EHP uses current, not maximum, health.",
                                "Posture defense assumes a fully built eligible state: intentional movement for dodge, stationary facing of a hostile threat for Bulwark, or repeated identical eligible damage for Adaptive. Status bounds require a harmful application; Mirror additionally requires a valid hostile source and ready cooldown."));
                        result.add(new Case(tier.id().toString(), archetype.id() + "/" + scenario.id() + "/health_" + healthFraction,
                                evaluation, Collections.unmodifiableMap(limits), Collections.unmodifiableMap(defenses)));
                    }
                }
            }
        }
        if (result.isEmpty()) throw new IllegalStateException("No registered equipment families were available for numeric build validation");
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
                "Nexus passive regeneration is not multiplied by Healing Effectiveness in gameplay. It is modeled separately; externally sourced healing amplification retains a semantic budget because evidence does not provide a healing event rate.",
                "Armor penetration retains an explicit conservative armor-pressure allowance. Reflection, guard amplification, Crowd Reprisal, control, interception, flight and gathering retain separate semantic budgets; no prevented-hit or Attunement activity is invented.",
                "Evasive expected avoidance and Bulwark/Adaptive damage reduction use actual generated values at the contributing rank, each under its strongest eligible posture condition. The three exclusive postures never stack. Meter build/drain and type-change exposure reduce real availability; peak bounds deliberately do not assume free continuous uptime.",
                "Status Mirror and Pure State share an independent exclusive choice. harmful-status prevention is a capability fraction, and Mirror transfer capacity is applications/second bounded by its generated cooldown. Missing pack harmful-application rates and source acceptance evidence prevent converting these into damage, EHP, or guaranteed status uptime.",
                "Pure State is binary at every diagnostic rank. Its provisional catalog curve supplies no numeric consumer and no invented rank benefit; the later catalog-wide rank design must decide whether it should have ranks. Already-active harmful effects are not cleansed.",
                "Pack parity preserves existing equipment curves and attainable weapon/armor pairings. Physical quantization and tool archetype baselines are not nerfed to make a bonus budget fit. External-gear projections assume required equipment access; gameplay still enforces eligibility, ownership and worn-slot coverage."));
    }

    static SkillEffectBalanceSettings rankedEffects(RuntimeBalanceDefinition runtime, Map<ResourceLocation, Integer> ranks) {
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
        return runtime.config().statMaxBonus(stat) * StatScalingService.progressionForInvestment(stat, moderate ? cap / 2 : cap, tier, profile);
    }

    /** Same primary-hit order as SkillEffectRuntime/GuardCounterattackService; secondary damage stays separate. */
    static Metrics combat(SkillEffectBalanceSettings s, Set<ResourceLocation> active, String family, Equipment item,
                          Modifier nexus, double incoming, double window, double healthFraction) {
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
        return new Metrics(sustained, burst, area, currentEhp,
                currentEhp + defense.sustainedHealth() - defense.effectiveHealth(), defense.healingPerSecond());
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
