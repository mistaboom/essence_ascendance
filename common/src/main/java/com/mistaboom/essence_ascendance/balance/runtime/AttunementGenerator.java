package com.mistaboom.essence_ascendance.balance.runtime;

import com.mistaboom.essence_ascendance.attunement.AttunementActivityRegistry;
import com.mistaboom.essence_ascendance.attunement.AttunementProfile;
import com.mistaboom.essence_ascendance.balance.BalanceProfileDefinition;
import com.mistaboom.essence_ascendance.balance.config.BalanceSettings;
import com.mistaboom.essence_ascendance.balance.economy.EconomyProfile;
import com.mistaboom.essence_ascendance.balance.engine.*;
import com.mistaboom.essence_ascendance.essence.EssenceRegistry;
import com.mistaboom.essence_ascendance.progression.BonusDevelopment;
import com.mistaboom.essence_ascendance.tier.AscendanceTierDefinition;
import com.mistaboom.essence_ascendance.tier.AscendanceTierRegistry;
import net.minecraft.SharedConstants;
import net.minecraft.world.food.FoodConstants;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import com.mistaboom.essence_ascendance.attunement.AttunementActivity;

import java.util.*;

/** One stage of the existing runtime generator; all handler calibration is saved in the profile. */
public final class AttunementGenerator {
    private static final double CONTRIBUTION_UNIT = 1024;
    private AttunementGenerator() { }

    /** Pre-world/source-constructor fixture only. Persisted profiles must contain the complete generated section. */
    public static AttunementProfile bootstrap(BalanceProfileDefinition profile) {
        return generate(RuntimeReferencePolicy.bootstrapEvidence(), null, BalanceSettings.defaults(), profile);
    }

    public static AttunementProfile generate(PackEvidence evidence, EconomyProfile economy,
                                             BalanceSettings settings, BalanceProfileDefinition budget) {
        var tiers = AscendanceTierRegistry.values().stream().sorted(Comparator.comparingInt(AscendanceTierDefinition::order)).toList();
        var powerTiers = tiers.stream().filter(AscendanceTierDefinition::grantsPower).toList();
        if (tiers.size() < 2 || powerTiers.isEmpty())
            throw new IllegalArgumentException("Category Attunement needs an onboarding tier and at least one powered tier");
        var categories = EssenceRegistry.values().stream().sorted(Comparator.comparing(value -> value.id().toString())).toList();
        var activities = AttunementActivityRegistry.values();
        var policy = settings.attunement();
        var references = new TreeMap<String, Double>();
        var assumptions = new ArrayList<String>();
        var cropEligibility = new EvidenceSink();
        evidence.facts().stream().filter(fact -> fact.subject() == EvidenceFact.Subject.BLOCK
                && fact.property().equals(com.mistaboom.essence_ascendance.equipment.PlayerAttributedBlockHarvestService.CROP_ELIGIBILITY_PROPERTY))
                .forEach(cropEligibility::add);
        references.put("player_health", RuntimeReferencePolicy.playerHealth());
        // Native resource mechanics anchor sustain; locomotion throughput remains an explicit assumption.
        references.put("food_capacity", (double) FoodConstants.MAX_FOOD);
        references.put("exhaustion_per_food", (double) FoodConstants.EXHAUSTION_DROP);
        references.put("native_slow_healing_health_per_second", SharedConstants.TICKS_PER_SECOND / (double) FoodConstants.HEALTH_TICK_COUNT);
        references.put("native_sprint_exhaustion_per_block", Double.parseDouble(Float.toString(FoodConstants.EXHAUSTION_SPRINT)));
        double window = settings.generation().survivalWindowSeconds();
        double encounter = settings.generation().routineEncounterSeconds();
        double nativeSpeed = Attributes.MOVEMENT_SPEED.value().getDefaultValue();
        // Player overrides the attribute's entity default; use its native supplier when available.
        if (!RuntimeReferencePolicy.usingBootstrapReferences()) nativeSpeed = net.minecraft.world.entity.player.Player.createAttributes().build().getValue(Attributes.MOVEMENT_SPEED);
        double ground = nativeSpeed * SharedConstants.TICKS_PER_SECOND / (1 - Blocks.STONE.getFriction());
        double flightRatio = Math.sqrt(settings.generation().bossEncounterSeconds() / encounter);
        references.put("run_blocks_per_second", ground);
        references.put("swim_blocks_per_second", nativeSpeed * SharedConstants.TICKS_PER_SECOND);
        references.put("fly_blocks_per_second", ground * flightRatio);
        references.put("run_accessibility_pressure", Math.max(1, Math.sqrt(window / encounter)));
        references.put("flight_distance_effort_ratio", 1 / flightRatio);
        references.put("health_recovery_per_window", Math.min(references.get("player_health"),
                references.get("native_slow_healing_health_per_second") * settings.generation().survivalWindowSeconds()));
        references.put("hunger_consumption_per_window", Math.min(references.get("food_capacity"),
                references.get("run_blocks_per_second") * references.get("native_sprint_exhaustion_per_block")
                        * settings.generation().survivalWindowSeconds() / references.get("exhaustion_per_food")));
        references.put("movement_region_blocks", 32.0);
        references.put("movement_max_blocks_per_tick", 16.0);
        references.put("movement_max_event_blocks", 16.0);
        references.put("exertion_recent_ticks", 100.0);
        references.put("movement_intent_timeout_ticks", 10.0);
        references.put("enemy_threat_cap", 2.0);
        references.put("entry_effort_window_ratio", encounter / window);
        assumptions.add("Food capacity, exhaustion conversion and slow natural-healing cadence come from Minecraft 1.21.1 FoodConstants. Saturation uses the same food-point unit. Native slow healing restores one health per 80 ticks (0.25 health/second); saturated recovery, food and skill effects may legitimately improve actual outcomes.");
        assumptions.add("Native exhaustion converts at 4 exhaustion per consumed food/saturation point. Vitality expenditure is bounded by observed eligible-action exhaustion and confirmed food changes; recent activity alone supplies no budget.");
        assumptions.add("Exertion uses observed native exhaustion consumption per survival window. Replenishment uses the native food capacity, because eating batches many seconds of food expenditure into one action. Healing uses the greater of slow native recovery and the chapter's incoming damage per survival window, bounded by native health capacity. Fast saturated healing does not redefine the baseline.");
        assumptions.add("Movement throughput is a conservative proxy from native player movement attribute, ticks/second and ordinary stone friction, not a prescribed player speed. Swim uses the native per-tick movement attribute; running accessibility and flight opportunity ratios use survival/routine and boss/routine encounter windows. Missing scripted travel behavior requires provider evidence.");
        assumptions.add("Movement attribution uses 32-block regions and rejects coordinate discontinuities above 16 blocks/tick. Legitimate faster modded movement needs an explicit adapter; vehicle, spectator and idle displacement are ineligible.");
        assumptions.add("Hunger consumption can follow native exhaustion conversion; eligible exertion attribution is retained for 100 ticks. This bounded integrity assumption does not award idle hunger or create activity credit.");
        assumptions.add("Movement intent expires after 10 server ticks. An intent heartbeat only suppresses idle displacement; it provides no distance, activity, eligibility or credited outcome, which the server observes independently.");
        assumptions.add("Exploration discoveries reset each chapter. Exploration is optional and finite; repeated running/swimming remain complete routes at the nonzero floor.");
        assumptions.add("Target effort reuses entry resource effort, current-to-next budget growth and the generated investment exponent. Latent onboarding uses the configured onboarding fraction; the first powered chapter uses the early effort fraction and eases procedurally to full effort at the final chapter. Contribution units are a fixed 1024 accounting scale, not Essence or a required event count.");
        assumptions.add("Eligible root actions have no per-action progress ceiling. Large legitimate outcomes retain their full calibrated value up to the remaining seal progress; repeated sources instead diminish through recent-history efficiency with a nonzero configured floor.");
        assumptions.add("Targets convert existing resource effort to survival windows using routine/survival duration. Each category mixes methods freely. Scarce routes receive sqrt(base routes / effective accessible routes) compensation. Workstation opportunity is its reachable stage plus min(1, current material reference * sqrt(current/entry budget) / station acquisition value); innate routes contribute one. Thus an expensive technically craftable station is not counted like free XP. Unknown access contributes zero. These pack-derived estimates are never player gates; missing quest/world evidence remains a reporting boundary.");
        assumptions.add("Investment gain = 1 + maximumAcceleration * sqrt(min(1, normalized Bonus development + historical paid category skill receipts / category reference)). Bonus development is realized generated marginal power divided by available generated power at the current tier; raw Bonus prices are excluded. The neutral category reference is the default tier budget times registered category stat count, independent of individual track prices. Historical owned-skill receipts retain their actual paid amounts and receipt category. Wallet and equipment value are excluded. Respecs affect future gain only; normalized earned progress is retained.");
        assumptions.add("Incoming damage uses routine enemy burst times survival/routine encounter windows. Enemy health and damage use the existing robust percentile; absent routine enemy observations use ordinary player health/hit and are reported by chapter.");
        assumptions.add("Confirmed enemy outcomes retain actual health units and receive bounded live attack/armor threat weighting: sqrt((1+attack/reference_damage)*(1+armor/reference_armor)/2), capped at 2. Absent positive armor observations use the vanilla 20-point protection reference; this weights activity and does not modify combat.");
        assumptions.add("XP uses observed routine enemy experience rewards where available. Missing XP observations use sqrt(entry resource effort) as an explicitly dimensionless opportunity proxy, independent of player health; reports mark that fallback. Material and harvest references use positive reachable external generated values.");
        assumptions.add("Source pressure advances in generated reference units, not packets, strikes, healing ticks or XP orb count. Exponential fading retains bounded per-category source exposure. Efficiency is integrated over each outcome, so splitting a single-source action cannot evade repetition. Variety uses weighted source diversity, allowing two meaningful sources to help without needing dozens of recipes.");
        assumptions.add("Reachability validates registered base routes and saved generated source yields. Crop calibration uses saved block eligibility from the shared mature-crop gameplay selector, including conditional player-action harvests; biological FARMING sources do not establish crop eligibility. Known eligible crop/fishing families whose observed reachable outputs are all suppressed fail generation. Missing family or saved crop-eligibility evidence is an explicit conservative fallback. Opaque runtime or stack-specific mapping overrides may further suppress outputs and require adapter evidence/live acceptance; this report does not prove their behavior.");
        var methods = new TreeMap<String, AttunementProfile.Method>();
        for (var activity : activities)
            methods.put(activity.id(), new AttunementProfile.Method(activity.id(), activity.categoryId(), activity.units(),
                    activity.calibrationFamily(), activity.labelKey(), activity.descriptionKey(), activity.baseGameAccessible()));
        var chapters = new TreeMap<String, AttunementProfile.Chapter>();
        long entryBudget = budget.getDefaultInvestmentCap(powerTiers.getFirst());
        int poweredTransitionCount = Math.max(1, powerTiers.size() - 1);
        for (int index = 0; index + 1 < tiers.size(); index++) {
            var from = tiers.get(index); var to = tiers.get(index + 1);
            int poweredIndex = powerTiers.indexOf(from);
            var band = ProgressionBand.at(Math.max(0, poweredIndex));
            String suffix = from.id().getPath();
            double currentBudget = from.grantsPower() ? budget.getDefaultInvestmentCap(from) : entryBudget;
            double nextBudget = to.grantsPower() ? budget.getDefaultInvestmentCap(to) : entryBudget;
            double growth = nextBudget / currentBudget;
            double effortFraction;
            if (!from.grantsPower()) {
                effortFraction = policy.onboardingEffortFraction();
            } else {
                double position = poweredTransitionCount <= 1 ? 1 : poweredIndex / (double) (poweredTransitionCount - 1);
                effortFraction = policy.earlyEffortFraction()
                        + (1 - policy.earlyEffortFraction()) * Math.clamp(position, 0, 1);
            }
            double effort = settings.generation().entryResourceEffort()
                    * Math.pow(currentBudget / entryBudget, budget.investmentExponent() / 2)
                    * Math.sqrt(growth) * settings.progressionLength() * effortFraction / policy.pace()
                    * references.get("entry_effort_window_ratio");
            long target = boundedLong(CONTRIBUTION_UNIT * effort);
            double routineHealth = enemy(evidence, band, CapabilityAxis.EFFECTIVE_HEALTH, RuntimeReferencePolicy.playerHealth(), settings);
            double incoming = enemy(evidence, band, CapabilityAxis.BURST_DAMAGE, RuntimeReferencePolicy.playerHit(), settings);
            double enemyArmor = enemy(evidence, band, CapabilityAxis.ARMOR, 20, settings);
            double xp = enemy(evidence, band, CapabilityAxis.EXPERIENCE, Math.sqrt(settings.generation().entryResourceEffort()), settings);
            double recovery = Math.min(references.get("player_health"), Math.max(references.get("health_recovery_per_window"), incoming * window / encounter));
            double harvestValue = value(evidence, economy, band, true);
            double cropValue = sourceValue(evidence, economy, band, AcquisitionSource.Kind.FARMING, cropEligibility, harvestValue, assumptions, suffix);
            double fishValue = sourceValue(evidence, economy, band, AcquisitionSource.Kind.FISHING, cropEligibility, harvestValue, assumptions, suffix);
            double materialValue = value(evidence, economy, band, false);
            double durability = Math.max(1, RuntimeReferencePolicy.observed(evidence, band, CapabilityAxis.DURABILITY, 1));
            references.put("routine_health_" + suffix, routineHealth);
            references.put("enemy_damage_" + suffix, incoming);
            references.put("enemy_armor_" + suffix, enemyArmor);
            references.put("experience_per_reference_operation_" + suffix, xp);
            references.put("health_recovery_" + suffix, recovery);
            references.put("harvest_value_" + suffix, harvestValue);
            references.put("crop_value_" + suffix, cropValue);
            references.put("fish_value_" + suffix, fishValue);
            references.put("material_value_" + suffix, materialValue);
            references.put("durability_" + suffix, durability);
            references.put("budget_growth_" + suffix, growth);
            references.put("effort_" + suffix, effort);
            references.put("effort_fraction_" + suffix, effortFraction);
            var resolvedCategories = new TreeMap<String, AttunementProfile.Category>();
            for (var category : categories) {
                long investment = BonusDevelopment.reference(budget, from, category.id().toString());
                references.put("bonus_development_reference_" + category.id().getPath() + "_" + suffix, (double) investment);
                long baseCount = activities.stream().filter(a -> a.categoryId().equals(category.id().toString()) && a.baseGameAccessible()).count();
                long accessible = activities.stream().filter(a -> a.categoryId().equals(category.id().toString()) && accessible(a, evidence, band)).count();
                double effective = activities.stream().filter(a -> a.categoryId().equals(category.id().toString()))
                        .mapToDouble(a -> opportunity(a, evidence, band, materialValue * Math.sqrt(currentBudget / entryBudget))).sum();
                references.put("accessible_methods_" + category.id().getPath() + "_" + suffix, (double) Math.max(1, accessible));
                references.put("effective_methods_" + category.id().getPath() + "_" + suffix, Math.max(1, effective));
                references.put("accessibility_gain_" + category.id().getPath() + "_" + suffix, Math.sqrt(baseCount / Math.max(1, effective)));
                resolvedCategories.put(category.id().toString(), new AttunementProfile.Category(category.id().toString(), target, investment));
            }
            var rates = new TreeMap<String, AttunementProfile.Rate>();
            for (var activity : activities) {
                double reference = switch (activity.calibrationFamily()) {
                    case "damage", "deaths" -> routineHealth;
                    case "incoming_damage" -> incoming * settings.generation().survivalWindowSeconds() / settings.generation().routineEncounterSeconds();
                    case "health" -> recovery;
                    case "hunger" -> references.get("hunger_consumption_per_window");
                    case "food_restoration" -> references.get("food_capacity");
                    case "run" -> references.get("run_blocks_per_second") * settings.generation().survivalWindowSeconds() * references.get("run_accessibility_pressure");
                    case "swim" -> references.get("swim_blocks_per_second") * settings.generation().survivalWindowSeconds();
                    case "fly" -> references.get("fly_blocks_per_second") * settings.generation().survivalWindowSeconds() * references.get("flight_distance_effort_ratio");
                    case "exploration" -> 1 / Math.sqrt(Math.max(1, window / encounter));
                    case "gathering_value" -> harvestValue;
                    case "crop_value" -> cropValue;
                    case "fish_value" -> fishValue;
                    case "experience" -> xp;
                    case "material_value" -> materialValue;
                    case "durability" -> Math.sqrt(durability);
                    default -> throw new IllegalArgumentException("Register calibration evidence for Attunement family " + activity.calibrationFamily());
                };
                String source = "family=" + activity.calibrationFamily() + "; band=" + band + "; current_budget=" + (long) currentBudget
                        + "; next_budget=" + (long) nextBudget + "; access_item=" + activity.accessItem()
                        + "; stage_accessible=" + accessible(activity, evidence, band)
                        + "; reference from saved runtime/attunement/references and declared generation assumptions";
                String categoryPath = activity.categoryId().substring(activity.categoryId().indexOf(':') + 1);
                double accessGain = references.get("accessibility_gain_" + categoryPath + "_" + suffix);
                rates.put(activity.id(), new AttunementProfile.Rate(activity.id(), activity.categoryId(), CONTRIBUTION_UNIT * accessGain / reference,
                        reference, activity.units(), source, accessible(activity, evidence, band)));
            }
            int breadth = AttunementProfile.requiredCategories(categories.size(), index + 1, tiers.size() - 1, policy.breadthExponent());
            String chapterId = "essence_ascendance:" + from.id().getPath() + "_to_" + to.id().getPath();
            chapters.put(from.id().toString(), new AttunementProfile.Chapter(chapterId, from.id().toString(), to.id().toString(), breadth, resolvedCategories, rates));
            long observations = evidence.enemies().stream().filter(enemy -> enemy.included() && enemy.stage().ordinal() <= band.ordinal()
                    && (enemy.encounter() == EnemyReference.Encounter.ROUTINE || enemy.encounter() == EnemyReference.Encounter.ELITE)).count();
            if (observations == 0) assumptions.add(chapterId + ": acquisition evidence puts all routine enemies after this band; use the earliest observed routine band for Attunement only. Enemy equipment-stage labels are not spawn locks. If no enemies exist in evidence, native player attributes remain the fallback.");
            if (evidence.enemies().stream().noneMatch(e -> e.included() && e.axes().getOrDefault(CapabilityAxis.EXPERIENCE, 0.0) > 0))
                assumptions.add(chapterId + ": XP reward evidence absent; entry-effort opportunity proxy used. A live rebuild collects native routine enemy XP rewards.");
            if (economy == null) assumptions.add(chapterId + ": pre-world profile has no installed economy; positive source opportunity values or unit fallback calibrate harvest until a real generated profile is installed.");
        }
        return new AttunementProfile(new AttunementProfile.Policy(policy.maximumAcceleration(), policy.repetitionFloor(),
                policy.varietyStrength(), policy.historyWindow()), chapters, methods, references, assumptions);
    }

    private static double enemy(PackEvidence evidence, ProgressionBand band, CapabilityAxis axis, double fallback, BalanceSettings settings) {
        int firstRoutine = evidence.enemies().stream().filter(e -> e.included() && e.encounter() == EnemyReference.Encounter.ROUTINE)
                .mapToInt(e -> e.stage().ordinal()).min().orElse(band.ordinal());
        int accessibleBand = Math.max(firstRoutine, band.ordinal());
        var values = evidence.enemies().stream().filter(enemy -> enemy.included() && enemy.stage().ordinal() <= accessibleBand
                && enemy.encounter() == EnemyReference.Encounter.ROUTINE)
                .map(enemy -> enemy.axes().getOrDefault(axis, 0.0)).filter(value -> value > 0).toList();
        return values.isEmpty() ? fallback : Math.max(.01, RobustFrontiers.percentile(values, .75, settings.outlierPolicy().name()));
    }
    private static boolean accessible(AttunementActivity activity, PackEvidence evidence, ProgressionBand band) {
        if (!activity.baseGameAccessible()) return false;
        if (activity.accessItem().isEmpty()) return true;
        var resource = evidence.resources().get(activity.accessItem());
        return resource != null && resource.reachable() && resource.stage().ordinal() <= band.ordinal();
    }
    private static double opportunity(AttunementActivity activity, PackEvidence evidence, ProgressionBand band, double materialBudget) {
        if (!accessible(activity, evidence, band)) return 0;
        if (activity.accessItem().isEmpty()) return 1;
        double stationCost = evidence.resources().get(activity.accessItem()).economicValue();
        return stationCost > 0 ? Math.min(1, materialBudget / stationCost) : 1;
    }
    private static double value(PackEvidence evidence, EconomyProfile economy, ProgressionBand band, boolean harvest) {
        var values = evidence.resources().values().stream().filter(resource -> resource.reachable() && resource.external() && resource.stage().ordinal() <= band.ordinal())
                .mapToDouble(resource -> {
                    var resolved = economy == null ? null : economy.resources().get(resource.itemId());
                    return resolved == null ? resource.economicValue() : harvest ? resolved.dissolutionYield().amount() : resolved.economicValue().amount();
                }).filter(number -> Double.isFinite(number) && number > 0).boxed().toList();
        return values.isEmpty() ? 1 : Math.max(.01, RobustFrontiers.percentile(values, .5));
    }
    private static double sourceValue(PackEvidence evidence, EconomyProfile economy, ProgressionBand band, AcquisitionSource.Kind kind,
                                      EvidenceSink cropEligibility, double fallback, List<String> assumptions, String chapter) {
        var sources = evidence.resources().values().stream().filter(resource -> resource.reachable() && resource.external()
                        && resource.stage().ordinal() <= band.ordinal() && resource.sources().stream().anyMatch(source ->
                        sourceAccessible(source, evidence, band) && (kind == AcquisitionSource.Kind.FARMING
                                ? (source.kind() == AcquisitionSource.Kind.FARMING || source.kind() == AcquisitionSource.Kind.PLAYER_ACTION)
                                    && cropEligibility.flag(EvidenceFact.Subject.BLOCK, source.id(),
                                        com.mistaboom.essence_ascendance.equipment.PlayerAttributedBlockHarvestService.CROP_ELIGIBILITY_PROPERTY, false)
                                : source.kind() == kind)))
                .toList();
        if (economy != null && !sources.isEmpty() && sources.stream().allMatch(resource -> {
            var resolved = economy.resources().get(resource.itemId());
            return resolved != null && resolved.dissolutionYield().microUnits() == 0;
        })) throw new IllegalArgumentException("Attunement " + chapter + ": every observed reachable " + kind
                + " source has zero generated yield. Correct source/yield policy or supply eligible source evidence, then explicitly rebuild; base routes must remain reachable.");
        var values = sources.stream()
                .mapToDouble(resource -> {
                    var resolved = economy == null ? null : economy.resources().get(resource.itemId());
                    return resolved == null ? resource.economicValue() : resolved.dissolutionYield().amount();
                }).filter(value -> Double.isFinite(value) && value > 0).boxed().toList();
        if (values.isEmpty()) {
            assumptions.add(chapter + ": no positive " + kind + " source valuation observed"
                    + (kind == AcquisitionSource.Kind.FARMING ? " with saved mature-crop eligibility" : "")
                    + "; its rate uses the current reachable harvest median. Adapter eligibility still requires a real positive installed value; inspect pack sources when this family supplies no outputs.");
            return fallback;
        }
        return Math.max(.01, RobustFrontiers.percentile(values, .5));
    }
    /** Owning a possible output does not establish access to its farming/fishing route. */
    static boolean sourceAccessible(AcquisitionSource source, PackEvidence evidence, ProgressionBand band) {
        if (source.stage().ordinal() > band.ordinal() || source.expectedOutput() <= 0 || source.confidence() < .5
                || source.availability() != null && !source.availability().accessProven()) return false;
        for (String dependency : source.dependencies()) {
            var resource = evidence.resources().get(dependency);
            if (resource == null || !resource.reachable() || resource.stage().ordinal() > band.ordinal()) return false;
        }
        return true;
    }
    private static long boundedLong(double value) {
        if (!Double.isFinite(value) || value > 1_000_000_000_000.0) throw new IllegalArgumentException("Generated Attunement target/reference exceeds safe bounds");
        return Math.max(1, (long) Math.ceil(value));
    }
}
