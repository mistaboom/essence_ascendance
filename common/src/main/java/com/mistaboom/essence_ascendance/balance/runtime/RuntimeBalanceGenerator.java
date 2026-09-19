package com.mistaboom.essence_ascendance.balance.runtime;

import com.google.gson.*;
import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.balance.BalanceProfileDefinition;
import com.mistaboom.essence_ascendance.balance.config.*;
import com.mistaboom.essence_ascendance.balance.engine.*;
import com.mistaboom.essence_ascendance.balance.economy.EconomyProfile;
import com.mistaboom.essence_ascendance.config.*;
import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleStructureStats;
import com.mistaboom.essence_ascendance.equipment.*;
import com.mistaboom.essence_ascendance.progression.*;
import com.mistaboom.essence_ascendance.pylon.EssencePylonContribution;
import com.mistaboom.essence_ascendance.skill.balance.SkillBalanceGenerator;
import com.mistaboom.essence_ascendance.stat.*;
import com.mistaboom.essence_ascendance.tier.*;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/** One generation pass supplies every existing runtime balance access seam. */
public final class RuntimeBalanceGenerator {
    private RuntimeBalanceGenerator() {}
    private static final Gson JSON = new GsonBuilder().disableHtmlEscaping().create();
    public static RuntimeBalanceDefinition bootstrap() {
        return RuntimeReferencePolicy.withBootstrapReferences(() ->
                generate(RuntimeReferencePolicy.bootstrapEvidence(), BalanceSettings.defaults(), null));
    }
    public static RuntimeBalanceDefinition generate(PackEvidence evidence, BalanceSettings settings, BalanceOverrides overrides) {
        return generate(evidence, null, settings, overrides);
    }
    public static RuntimeBalanceDefinition generate(PackEvidence evidence, EconomyProfile economy, BalanceSettings settings, BalanceOverrides overrides) {
        var allTiers = AscendanceTierRegistry.values().stream().sorted(Comparator.comparingInt(AscendanceTierDefinition::order)).toList();
        var tiers = allTiers.stream().filter(AscendanceTierDefinition::grantsPower).toList();
        if (tiers.isEmpty()) throw new IllegalArgumentException("At least one powered Ascendance tier must be registered");
        Map<String,Double> categoryFactors=new TreeMap<>();
        for(var essence:com.mistaboom.essence_ascendance.essence.EssenceRegistry.values())
            categoryFactors.put(essence.id().toString(),categoryFactor(evidence,economy,essence.id().toString()));
        Map<ProgressionBand,Double> bandSupply=new EnumMap<>(ProgressionBand.class);
        for(var band:ProgressionBand.values())bandSupply.put(band,medianSupply(evidence,economy,band));
        Map<ResourceLocation,Long> caps = new LinkedHashMap<>();
        Map<ResourceLocation,Double> fractions = new LinkedHashMap<>();
        Map<ResourceLocation,EquipmentBaselineConfig.TierBaseline> equipment = new LinkedHashMap<>();
        Map<String,InfuserBalanceSettings.GradeSettings> grades = new TreeMap<>();
        Map<String,InfuserBalanceSettings.FocusUpgradeSettings> focuses = new TreeMap<>();
        Map<String,InfuserBalanceSettings.EquipmentUpgradeSettings> upgrades = new TreeMap<>();
        Map<String,EssencePylonContribution> pylons = new TreeMap<>();
        Map<String,Double> encounterBudgets = new TreeMap<>();
        double participation = settings.partialBuildViability();
        // Shares allocate added power. Ordinary equipment is a complete alternative
        // to pack gear, so it must not pay for hypothetical Nexus/skill ownership.
        double equipmentScale = 1;
        // Each non-equipment system is independently viable. Semantic allocation
        // shares are not a tax on actual rank-one effect parameters.
        double bonusScale = 1;
        double skillScale = 1;
        double lastArmor=0,lastToughness=0,lastDamage=0,lastRate=0,lastMining=0,lastDurability=0;
        double lastRangedRate=0,lastRangedDamage=0,lastMagicRate=0,lastMagicDamage=0;
        var policy=settings.generation();
        double entryEnemyDamage=enemyReference(evidence,ProgressionBand.ENTRY,CapabilityAxis.BURST_DAMAGE,false,RuntimeReferencePolicy.playerHit());
        long lastCap=0;
        double fractionWeight=0;
        double[] weights = new double[tiers.size()];
        for (int i=0;i<weights.length;i++) { weights[i]=bandPower(settings,ProgressionBand.at(i)); fractionWeight+=weights[i]; }
        double cumulativeFraction=0;
        for (var tier : allTiers) if (!tier.grantsPower()) {
            caps.put(tier.id(), 0L);
            fractions.put(tier.id(), 0.0);
        }
        for (int i=0;i<tiers.size();i++) {
            var tier=tiers.get(i); var band=ProgressionBand.at(i);
            double power=settings.overallPower()*bandPower(settings,band);
            double median=bandSupply.get(band);
            // Expected resource effort, not a fixed grand-total currency target.
            long cap=positiveLong(median * policy.entryResourceEffort() * Math.pow(policy.effortGrowth(),i) * settings.progressionLength()*settings.costPressure());
            cap=Math.max(lastCap+1,cap); lastCap=cap; caps.put(tier.id(),cap);
            cumulativeFraction+=weights[i]; fractions.put(tier.id(),i==tiers.size()-1 ? 1.0 : cumulativeFraction/fractionWeight);
            double rate=Math.max(lastRate, RuntimeReferencePolicy.required(evidence,band,CapabilityAxis.ATTACK_RATE));
            double equipmentDps=RuntimeReferencePolicy.required(evidence,band,CapabilityAxis.SUSTAINED_DAMAGE);
            double routineHealth=enemyReference(evidence,band,CapabilityAxis.EFFECTIVE_HEALTH,false,RuntimeReferencePolicy.playerHealth());
            double bossHealth=enemyReference(evidence,band,CapabilityAxis.EFFECTIVE_HEALTH,true,routineHealth);
            // Armor reduction consumes damage per hit. Default entity attributes expose
            // that value; attack cadence and sustained DPS are separate provider evidence.
            double enemyDamage=enemyReference(evidence,band,CapabilityAxis.BURST_DAMAGE,false,entryEnemyDamage);
            double bossBurst=enemyReference(evidence,band,CapabilityAxis.BURST_DAMAGE,true,enemyDamage);
            // Explicit configurable encounter windows are policy assumptions, not equipment stat tables.
            // Geometric blending keeps a single enemy axis from replacing the equipment frontier.
            double encounterDps=Math.max(routineHealth/policy.routineEncounterSeconds(),bossHealth/policy.bossEncounterSeconds());
            double offensePressure=Math.pow(Math.clamp(encounterDps/equipmentDps,1,RuntimeReferencePolicy.MAX_OFFENSE_PRESSURE_RATIO),RuntimeReferencePolicy.ENCOUNTER_OFFENSE_ELASTICITY);
            double bossWindowRatio=policy.bossEncounterSeconds()/policy.routineEncounterSeconds();
            double defensePressure=Math.pow(Math.clamp(Math.max(enemyDamage/entryEnemyDamage,bossBurst/(entryEnemyDamage*bossWindowRatio)),1,RuntimeReferencePolicy.MAX_DEFENSE_PRESSURE_RATIO),RuntimeReferencePolicy.ENCOUNTER_DEFENSE_ELASTICITY);
            encounterBudgets.put("routine_health_"+band.name().toLowerCase(Locale.ROOT),routineHealth);
            encounterBudgets.put("boss_health_"+band.name().toLowerCase(Locale.ROOT),bossHealth);
            encounterBudgets.put("enemy_damage_"+band.name().toLowerCase(Locale.ROOT),enemyDamage);
            encounterBudgets.put("offense_encounter_factor_"+band.name().toLowerCase(Locale.ROOT),offensePressure);
            encounterBudgets.put("defense_encounter_factor_"+band.name().toLowerCase(Locale.ROOT),defensePressure);
            double damage=Math.max(lastDamage, equipmentDps/rate*power*equipmentScale*offensePressure);
            double referenceArmor=RuntimeReferencePolicy.observed(evidence,band,CapabilityAxis.ARMOR,0);
            double referenceToughness=RuntimeReferencePolicy.observed(evidence,band,CapabilityAxis.TOUGHNESS,0);
            double armor=Math.max(lastArmor,referenceArmor*power*equipmentScale*defensePressure);
            double toughness=Math.max(lastToughness,referenceToughness*power*equipmentScale*defensePressure);
            // Armor points are nonlinear: a small increase near the vanilla cap
            // can consume all survival headroom. Budget only that added mitigation.
            armor=Math.max(lastArmor,boundedArmor(referenceArmor,
                    referenceToughness,armor,toughness,enemyDamage,
                    1+power*settings.equipmentShare()));
            double mining=Math.max(lastMining,Math.max(1,RuntimeReferencePolicy.required(evidence,band,CapabilityAxis.MINING_SPEED)*power*equipmentScale));
            double durability=Math.max(lastDurability,Math.max(1,RuntimeReferencePolicy.required(evidence,band,CapabilityAxis.DURABILITY)*Math.sqrt(power)));
            int harvest=(int)Math.min(32,RuntimeReferencePolicy.observed(evidence,band,CapabilityAxis.HARVEST_LEVEL,0));
            double rangedRate=Math.max(lastRangedRate,RuntimeReferencePolicy.rangedRate(evidence,band,rate,settings.outlierPolicy().name()));
            double rangedDamage=Math.max(lastRangedDamage,equipmentDps/rangedRate*power*equipmentScale*offensePressure);
            double magicRate=Math.max(lastMagicRate,rangedRate/RuntimeReferencePolicy.CASTER_HIT_RATIO);
            double magicDamage=Math.max(lastMagicDamage,equipmentDps/magicRate*power*equipmentScale*offensePressure);
            equipment.put(tier.id(),new EquipmentBaselineConfig.TierBaseline(armor,toughness,damage,rate,
                    rangedDamage,rangedRate,magicDamage,magicRate,mining,harvest,(int)Math.min(Integer.MAX_VALUE/64,durability)));
            lastArmor=armor;lastToughness=toughness;lastDamage=damage;lastRate=rate;lastMining=mining;lastDurability=durability;
            lastRangedRate=rangedRate;lastRangedDamage=rangedDamage;lastMagicRate=magicRate;lastMagicDamage=magicDamage;
            long density=multipleOfNine(cap*(2.0+i*.4));
            int efficiency=(int)Math.round(10_000*(.58+i*.08-settings.conversionLossPressure()*.45));
            efficiency=Math.clamp(efficiency,100,9500);
            grades.put(tier.id().getPath(),new InfuserBalanceSettings.GradeSettings(density,efficiency,positiveLong(density/(8.0-i))));
            long focusCost=positiveLong(cap*(12.0+i*1.5));
            focuses.put(tier.id().getPath(),new InfuserBalanceSettings.FocusUpgradeSettings(Math.max(1,focusCost/20),focusCost));
            upgrades.put(tier.id().getPath(),new InfuserBalanceSettings.EquipmentUpgradeSettings(positiveLong(cap*(2.5+i*.5)),Math.max(1,(i+1)/2)));
            pylons.put(tier.id().getPath(),new EssencePylonContribution(positiveLong(cap*12),1+i*.6,positiveLong(cap/4.0),.20+i*.25,i+1));
        }
        var dormant=equipment.get(tiers.getFirst().id());
        for (var tier : allTiers) if (!tier.grantsPower())
            equipment.put(tier.id(),new EquipmentBaselineConfig.TierBaseline(
                    dormant.fullSetArmor()*RuntimeReferencePolicy.LATENT_CAPABILITY_RATIO,0,dormant.meleeDamage()*RuntimeReferencePolicy.LATENT_CAPABILITY_RATIO,dormant.meleeAttackSpeed(),
                    dormant.rangedDamage()*RuntimeReferencePolicy.LATENT_CAPABILITY_RATIO,dormant.rangedAttackSpeed(),dormant.magicDamage()*RuntimeReferencePolicy.LATENT_CAPABILITY_RATIO,dormant.magicCastSpeed(),
                    Math.max(1,dormant.miningSpeed()*RuntimeReferencePolicy.LATENT_CAPABILITY_RATIO),dormant.harvestLevel(),Math.max(1,(int)(dormant.durability()*RuntimeReferencePolicy.LATENT_DURABILITY_RATIO))));
        // Preserve physical stat curves. A single multiplier per axis anchors
        // Transcendent to attainable pack equipment before final unit rounding;
        // discrete harvest access advances separately with each equipment infusion.
        RuntimeEquipmentNormalization.apply(equipment,evidence,settings,encounterBudgets);
        double apex=settings.overallPower()*settings.apexPower();
        Map<ResourceLocation,Double> bonuses = new LinkedHashMap<>();
        Map<ResourceLocation,Map<ResourceLocation,Long>> statCaps = new LinkedHashMap<>();
        for (var stat: EssenceStatRegistry.values()) {
            double seed=StatScalingDefaults.get(stat.id()).orElseThrow(() -> new IllegalStateException("Register balance semantics for new stat " + stat.id()));
            double value=seed*bonusScale*Math.sqrt(apex);
            // Probability/resistance units have semantic caps; high pack armor must not create immunity.
            if (stat.id().getPath().endsWith("resistance") || stat.id().getPath().endsWith("efficiency")) value=Math.min(90,value);
            // Armor-adjusted EHP is not raw health. Reusing it here rewards the
            // same armor twice and makes the later defense guard crush resistance.
            if (stat.id().getPath().equals("max_health")) value*=Math.sqrt(Math.max(1,
                    enemyReference(evidence,ProgressionBand.APEX,CapabilityAxis.BURST_DAMAGE,false,entryEnemyDamage)/entryEnemyDamage));
            if (stat.id().getPath().equals("mining_speed") && settings.miningPolicy()==BalanceSettings.MiningPolicy.MATCH_PACK)
                value*=Math.sqrt(Math.max(1,evidence.reference(ProgressionBand.APEX,CapabilityAxis.AREA_MINING,1)));
            if (stat.id().getPath().equals("mining_speed") && settings.miningPolicy()==BalanceSettings.MiningPolicy.RESTRICT) value*=.5;
            if (stat.id().getPath().equals("flight_speed")) {
                if(settings.flightPolicy()==BalanceSettings.FlightPolicy.RESTRICT) value=0;
                // FLIGHT is a binary access axis, never a speed measurement or maximum-effect multiplier.
            }
            bonuses.put(stat.id(),Math.min(1_000_000,value));
        }
        double exponent=.72+.20*settings.compositionSafeguard();
        var tracks=BonusTrackGenerator.resolve(evidence,settings,caps,fractions,bonuses,categoryFactors,exponent);
        tracks.forEach((id,track)-> {
            bonuses.put(id,track.maximumEffect());
            Map<ResourceLocation,Long> costs=new LinkedHashMap<>();
            track.checkpoints().forEach(point->costs.put(point.tierId(),point.cumulativeCap()));
            statCaps.put(id,costs);
        });
        var profile=new BalanceProfileDefinition(ResourceLocation.fromNamespaceAndPath(EssenceAscendance.MOD_ID,"generated"),"Generated Pack Balance",caps,statCaps,fractions,exponent,tracks);
        long entry=caps.get(tiers.getFirst().id());
        pylons.put("empty",new EssencePylonContribution(positiveLong(entry*6.0),.5,positiveLong(entry/8.0),.1,0));
        var infuser=new InfuserBalanceSettings(8,Math.clamp((int)(5000-settings.conversionLossPressure()*2500),100,9000),
                positiveLong(entry/3.0),grades,focuses,upgrades,InfuserBalanceSettings.defaultEquipmentEssenceWeights(),
                new InfuserBalanceSettings.RepairSettings(positiveLong(bandSupply.get(ProgressionBand.MID)/20),1),
                economy==null?com.mistaboom.essence_ascendance.balance.economy.EconomyProcessingPolicy.defaults().conversionEfficiencyBasisPoints():economy.processingPolicy().conversionEfficiencyBasisPoints(),
                economy==null?com.mistaboom.essence_ascendance.balance.economy.EconomyProcessingPolicy.defaults().carrierExtractionEfficiencyBasisPoints():economy.processingPolicy().carrierExtractionEfficiencyBasisPoints());
        var milestones=new LinkedHashMap<ResourceLocation,MilestoneDefinition>();
        MilestoneRegistry.values().forEach(m->milestones.put(m.id(),m));
        var advancements=RuntimeAscensionPolicy.generate(profile);
        var shield=shield(equipment,settings);
        // Rank-one effects and additional rank growth have separate calibration.
        double resolvedSkillScale=skillScale*Math.sqrt(apex);
        var effects=effects(resolvedSkillScale, evidence, settings);
        var config=new EssenceServerConfig(1,6,8,infuser,shield,effects,LatentOreWorldgenSettings.defaults(),profile,milestones,advancements,bonuses,new EquipmentBaselineConfig(equipment));
        var crucible=new EssenceCrucibleStructureStats(1,positiveLong(entry*48.0),8,positiveLong(entry/2.0),20,1,6,1,0);
        Map<String,Double> composition=new TreeMap<>(encounterBudgets);
        composition.put("equipment_quantization",1.0);
        composition.put("equipment_share",settings.equipmentShare()); composition.put("nexus_share",settings.nexusShare());composition.put("skill_share",settings.skillShare());
        composition.put("partial_viability",participation); composition.put("composition_safeguard",settings.compositionSafeguard());
        composition.put("equipment_standalone_factor",equipmentScale);composition.put("nexus_standalone_factor",bonusScale);composition.put("skills_standalone_factor",skillScale);
        composition.put("rank_safe_skill_scale",resolvedSkillScale);
        composition.put("nexus_apex_target",BuildPowerTargets.multiplier(settings,ProgressionBand.APEX,BuildComposition.Participation.BONUS_FOCUSED));
        composition.put("skills_apex_target",BuildPowerTargets.multiplier(settings,ProgressionBand.APEX,BuildComposition.Participation.SKILL_FOCUSED));
        composition.put("combined_apex_target",BuildPowerTargets.multiplier(settings,ProgressionBand.APEX,BuildComposition.Participation.FULLY_COMBINED));
        composition.put("conditional_burst_apex_target",BuildPowerTargets.burstMultiplier(settings,ProgressionBand.APEX,BuildComposition.Participation.FULLY_COMBINED,true));
        var curves=new TreeMap<>(SkillBalanceGenerator.generate(caps,1));
        for(var skill:com.mistaboom.essence_ascendance.skill.SkillRegistry.values()) {
            var curve=curves.get(skill.id().toString());
            double categoryFactor=categoryFactors.get(skill.essenceId().toString());
            var axes=com.mistaboom.essence_ascendance.skill.balance.SkillBalanceSemantics.require(skill.id()).weights();
            double capabilityCost=1;
            if(axes.containsKey(CapabilityAxis.FLIGHT)) {
                if(settings.flightPolicy()==BalanceSettings.FlightPolicy.RESTRICT)capabilityCost*=2;
                else if(settings.flightPolicy()==BalanceSettings.FlightPolicy.MATCH_PACK)
                    capabilityCost/=Math.sqrt(1+Math.max(0,evidence.reference(ProgressionBand.EARLY,CapabilityAxis.FLIGHT,0)));
            }
            if(axes.containsKey(CapabilityAxis.AREA_MINING)||axes.containsKey(CapabilityAxis.VEIN_MINING)) {
                if(settings.miningPolicy()==BalanceSettings.MiningPolicy.RESTRICT)capabilityCost*=2;
                else if(settings.miningPolicy()==BalanceSettings.MiningPolicy.MATCH_PACK)
                    capabilityCost/=Math.sqrt(Math.max(1,Math.max(evidence.reference(ProgressionBand.MID,CapabilityAxis.AREA_MINING,1),evidence.reference(ProgressionBand.MID,CapabilityAxis.VEIN_MINING,1))));
            }
            final double resolvedCostFactor=Math.max(.1,categoryFactor*capabilityCost);
            curves.put(skill.id().toString(),new com.mistaboom.essence_ascendance.skill.balance.SkillBalanceRuntime.ResolvedSkill(curve.maximumRank(),
                    curve.ranks().stream().map(r->new com.mistaboom.essence_ascendance.skill.balance.SkillBalanceRuntime.ResolvedRank(r.rank(),positiveLong(r.cost()*resolvedCostFactor),r.powerMultiplier())).toList()));
        }
        var attunement=AttunementGenerator.generate(evidence,economy,settings,profile);
        var runtime=RuntimeValueQuantization.apply(new RuntimeBalanceDefinition(config,crucible,pylons,curves,composition,attunement));
        var requestedRuntime=runtime;
        RuntimeBuildScenarios.Plan compositionPlan=economy==null?null:RuntimeBuildScenarios.plan(runtime,true,false);
        RuntimeBuildScenarios.Plan firstRankPlan=economy==null?null:RuntimeBuildScenarios.plan(runtime,false,false);
        double compositionScale=1;
        if(compositionPlan!=null) {
            for(var channel:BuildComposition.Channel.values()) {
                double channelScale=calibration(runtime,evidence,settings,firstRankPlan,channel,false);
                runtime=adjusted(runtime,channelScale,channel,false,settings);
                compositionScale=Math.min(compositionScale,channelScale);
                JsonObject measured=runtime.toJson();
                measured.getAsJsonObject("composition").addProperty(channel.name().toLowerCase(Locale.ROOT)+"_calibration",channelScale);
                measured.getAsJsonObject("composition").addProperty("nexus_"+channel.name().toLowerCase(Locale.ROOT)+"_factor",bonusScale*channelScale);
                runtime=RuntimeBalanceDefinition.fromJson(measured);
            }
            // A low-base-damage weapon's flat skill proc can be the first-rank
            // bottleneck. Recover unused Nexus headroom independently instead
            // of letting that proc permanently tax every passive damage stat.
            runtime=restoreNexusOffense(runtime,requestedRuntime,evidence,settings,firstRankPlan);
            // Earlier-tier Nexus constraints must not permanently tax late
            // postures. Recover each real magnitude under the same first-rank
            // survival limits, then freeze it before future-rank calibration.
            runtime=restorePostureRankOne(runtime,requestedRuntime,evidence,settings,firstRankPlan);
            // Freeze first-rank values. Developed builds may trim only added
            // rank power, never the base Nexus or first-rank skill effects.
            var requestedRanks=runtime.skillCurves();
            double growth=1;
            for(var channel:BuildComposition.Channel.values()) {
                double channelGrowth=calibration(runtime,evidence,settings,compositionPlan,channel,true);
                runtime=adjusted(runtime,channelGrowth,channel,true,settings);
                growth=Math.min(growth,channelGrowth);
                var measured=runtime.toJson();
                measured.getAsJsonObject("composition").addProperty("rank_growth_"+channel.name().toLowerCase(Locale.ROOT)+"_calibration",channelGrowth);
                runtime=RuntimeBalanceDefinition.fromJson(measured);
            }
            runtime=restoreRankGrowth(runtime,requestedRanks,evidence,settings,compositionPlan);
            JsonObject ranked=runtime.toJson();ranked.getAsJsonObject("composition").addProperty("rank_growth_calibration",growth);
            runtime=RuntimeBalanceDefinition.fromJson(ranked);
            RuntimeBuildScenarios.analyze(runtime,evidence,settings,compositionPlan).requireSafe();
        }
        runtime=resolveFinalTracks(runtime,evidence,settings,categoryFactors);
        if (compositionPlan != null) {
            firstRankPlan = RuntimeBuildScenarios.plan(runtime, false);
            compositionPlan = RuntimeBuildScenarios.plan(runtime, true);
            runtime = calibrateVitality(runtime, evidence, settings, firstRankPlan, false);
            runtime = reserveVitalityHeadroom(runtime, evidence, settings, firstRankPlan, compositionPlan);
            runtime = calibrateVitality(runtime, evidence, settings, compositionPlan, true);
        }
        if(overrides!=null&&!overrides.exactValues().isEmpty()) {
            JsonObject json=runtime.toJson();
            overrides.exactValues().forEach((path,value)->{if(path.startsWith("/runtime/")) applyExact(json,path.substring("/runtime/".length()),value);});
            BonusTrackGenerator.synchronizeExactOverrides(json,overrides.exactValues());
            RuntimeValueQuantization.requireExactValuesOnGrid(json);
            runtime=RuntimeBalanceDefinition.fromJson(json);
            if (overrides.exactValues().keySet().stream().anyMatch(path -> path.startsWith("/runtime/statMaxBonuses/")
                    || (path.startsWith("/runtime/balanceProfile/bonusTracks/") && path.endsWith("/maximumEffect")))) {
                // Exact native effects change intrinsic utility. Resolve prices from that final effect, then
                // reapply any separately requested exact cost overrides so those remain authoritative.
                runtime=resolveFinalTracks(runtime,evidence,settings,categoryFactors);
                JsonObject resolved=runtime.toJson();
                overrides.exactValues().forEach((path,value)->{if(path.startsWith("/runtime/")) applyExact(resolved,path.substring("/runtime/".length()),value);});
                BonusTrackGenerator.synchronizeExactOverrides(resolved,overrides.exactValues());
                RuntimeValueQuantization.requireExactValuesOnGrid(resolved);
                runtime=RuntimeBalanceDefinition.fromJson(resolved);
            }
        }
        if(compositionPlan!=null) {
            RuntimeBuildScenarios.analyze(runtime,evidence,settings,firstRankPlan).requireSafe();
            var finalAnalysis=RuntimeBuildScenarios.analyze(runtime,evidence,settings,compositionPlan);
            finalAnalysis.requireSafe();
            runtime=runtime.withAnalysis(new RuntimeBuildScenarios.Analysis(compositionScale,finalAnalysis.cases(),finalAnalysis.assumptions()));
        }
        return runtime.withContentIdentity();
    }

    /** Only provisional posture growth changes: newly implemented healing must remain useful at every tier. */
    private static RuntimeBalanceDefinition reserveVitalityHeadroom(RuntimeBalanceDefinition runtime, PackEvidence evidence,
            BalanceSettings settings, RuntimeBuildScenarios.Plan firstRankPlan, RuntimeBuildScenarios.Plan plan) {
        var requested = runtime.skillCurves();
        // Future offensive growth increases accepted weapon healing. Fit the
        // requested recovery stress curve against that dependency while postures
        // keep their unchanged rank-one values, preserving honest recovery growth.
        runtime = adjusted(runtime, 0, BuildComposition.Channel.HEALING, true, settings);
        // Rank-one values are purchased now. Do not recalibrate them against
        // the five-rank stress projection; future offensive growth gets its
        // own analytic curve below.
        runtime = calibrateVitality(runtime, evidence, settings, firstRankPlan, false);
        runtime = new RuntimeBalanceDefinition(runtime.config(), runtime.crucible(), runtime.pylons(), requested, runtime.composition(), runtime.attunement());
        var noPostureGrowth = adjusted(runtime, 0, BuildComposition.Channel.HEALING, true, settings);
        // Start the analytic Vitality rank pass from the purchased value for
        // both mutually exclusive recovery branches. Otherwise the first
        // branch would be tested while the other branch still carries its
        // requested future curve and would incorrectly fail at factor zero.
        noPostureGrowth = scaleVitality(noPostureGrowth, com.mistaboom.essence_ascendance.skill.SkillIds.RISING_RECOVERY, 0, true);
        noPostureGrowth = scaleVitality(noPostureGrowth, com.mistaboom.essence_ascendance.skill.SkillIds.LIFE_STEAL, 0, true);
        noPostureGrowth = calibrateVitality(noPostureGrowth, evidence, settings, plan, true);
        var reserved = new TreeMap<>(requested);
        for (var id : List.of(com.mistaboom.essence_ascendance.skill.SkillIds.RISING_RECOVERY,
                com.mistaboom.essence_ascendance.skill.SkillIds.LIFE_STEAL)) {
            var curve = noPostureGrowth.skillCurves().get(id.toString());
            reserved.put(id.toString(), new com.mistaboom.essence_ascendance.skill.balance.SkillBalanceRuntime.ResolvedSkill(curve.maximumRank(),
                    curve.ranks().stream().map(rank -> new com.mistaboom.essence_ascendance.skill.balance.SkillBalanceRuntime.ResolvedRank(
                            rank.rank(), rank.cost(), 1 + (rank.powerMultiplier() - 1) * .5)).toList()));
        }
        runtime = new RuntimeBalanceDefinition(runtime.config(), runtime.crucible(), runtime.pylons(), reserved, runtime.composition(), runtime.attunement());
        // Share remaining room with half the actually attainable extra recovery
        // growth. These are analytic ranks; all current ranks remain one.
        double factor = calibration(runtime, evidence, settings, plan, BuildComposition.Channel.HEALING, true);
        runtime = adjusted(runtime, factor, BuildComposition.Channel.HEALING, true, settings);
        runtime = restoreRankGrowth(runtime, requested, evidence, settings, plan);
        var curves = new TreeMap<>(runtime.skillCurves());
        requested.forEach((id, curve) -> { if (RuntimeBuildScenarios.vitalitySkill(ResourceLocation.parse(id))) curves.put(id, curve); });
        var composition = new TreeMap<>(runtime.composition());
        composition.put("vitality_projected_posture_growth_retention", factor);
        return new RuntimeBalanceDefinition(runtime.config(), runtime.crucible(), runtime.pylons(), curves, composition, runtime.attunement());
    }

    /** New healing consumes remaining headroom without changing any existing runtime value. */
    private static RuntimeBalanceDefinition calibrateVitality(RuntimeBalanceDefinition runtime, PackEvidence evidence,
            BalanceSettings settings, RuntimeBuildScenarios.Plan plan, boolean ranks) {
        for (var id : List.of(com.mistaboom.essence_ascendance.skill.SkillIds.RISING_RECOVERY,
                com.mistaboom.essence_ascendance.skill.SkillIds.LIFE_STEAL)) {
            // Validate one recovery branch at a time; mutual exclusion makes the magnitudes independent.
            var only = vitalityPlan(plan, id);
            double low = 0, high = 1;
            if (RuntimeBuildScenarios.analyze(runtime, evidence, settings, only).safeFor(BuildComposition.Channel.HEALING)) low = 1;
            else {
                RuntimeBuildScenarios.analyze(scaleVitality(runtime, id, 0, ranks), evidence, settings, only)
                        .requireSafe();
                for (int pass = 0; pass < 20; pass++) {
                    double middle = (low + high) / 2;
                    if (RuntimeBuildScenarios.analyze(scaleVitality(runtime, id, middle, ranks), evidence, settings, only)
                            .safeFor(BuildComposition.Channel.HEALING)) low = middle;
                    else high = middle;
                }
            }
            runtime = scaleVitality(runtime, id, low, ranks);
            var json = runtime.toJson();
            String key = "vitality_" + id.getPath() + (ranks ? "_rank_growth" : "_rank_one");
            json.getAsJsonObject("composition").addProperty(key, low * runtime.composition().getOrDefault(key, 1.0));
            runtime = RuntimeBalanceDefinition.fromJson(json);
        }
        RuntimeBuildScenarios.analyze(runtime, evidence, settings, plan).requireSafe();
        return runtime;
    }
    private static RuntimeBuildScenarios.Plan vitalityPlan(RuntimeBuildScenarios.Plan plan, ResourceLocation branch) {
        var full = new LinkedHashMap<ResourceLocation, List<com.mistaboom.essence_ascendance.skill.balance.SkillLoadoutProjection.Scenario>>();
        plan.full().forEach((tier, rows) -> full.put(tier, rows.stream().filter(row ->
                row.contributingRanks().keySet().stream().noneMatch(id ->
                        (id.equals(com.mistaboom.essence_ascendance.skill.SkillIds.RISING_RECOVERY)
                                || id.equals(com.mistaboom.essence_ascendance.skill.SkillIds.LIFE_STEAL)) && !id.equals(branch))).toList()));
        return new RuntimeBuildScenarios.Plan(full, plan.moderate(), plan.developed(), plan.equipmentLimits());
    }
    private static RuntimeBalanceDefinition scaleVitality(RuntimeBalanceDefinition source, ResourceLocation id, double factor, boolean ranks) {
        var json = source.toJson();
        if (ranks) {
            for (var element : json.getAsJsonObject("skillCurves").getAsJsonObject(id.toString()).getAsJsonArray("ranks")) {
                var rank = element.getAsJsonObject();
                rank.addProperty("powerMultiplier", 1 + (rank.get("powerMultiplier").getAsDouble() - 1) * factor);
            }
        } else {
            var vitality = json.getAsJsonObject("effects").getAsJsonObject("vitality");
            var value = vitality.getAsJsonObject(id.equals(com.mistaboom.essence_ascendance.skill.SkillIds.RISING_RECOVERY) ? "risingRecovery" : "lifeSteal");
            for (String key : id.equals(com.mistaboom.essence_ascendance.skill.SkillIds.RISING_RECOVERY)
                    ? List.of("maxSpeedBonus") : List.of("baseHealingFraction", "perHitHealingFraction"))
                value.addProperty(key, value.get(key).getAsDouble() * factor);
        }
        return RuntimeBalanceDefinition.fromJson(json);
    }

    private static RuntimeBalanceDefinition resolveFinalTracks(RuntimeBalanceDefinition runtime,PackEvidence evidence,
            BalanceSettings settings,Map<String,Double> categoryFactors) {
        var profile=runtime.config().balanceProfile();
        var tracks=BonusTrackGenerator.resolve(evidence,settings,profile.defaultTierCaps(),profile.tierFractions(),
                runtime.config().statMaxBonuses(),categoryFactors,profile.investmentExponent());
        JsonObject json=runtime.toJson();
        json.getAsJsonObject("balanceProfile").add("bonusTracks",BonusTrackGenerator.toJson(tracks));
        for(var entry:tracks.entrySet()) {
            JsonObject caps=new JsonObject();
            entry.getValue().checkpoints().forEach(point->caps.addProperty(point.tierId().toString(),point.cumulativeCap()));
            json.getAsJsonObject("balanceProfile").getAsJsonObject("statOverrides").add(entry.getKey().toString(),caps);
            json.getAsJsonObject("statMaxBonuses").addProperty(entry.getKey().toString(),entry.getValue().maximumEffect());
        }
        return RuntimeBalanceDefinition.fromJson(json);
    }

    private static RuntimeBalanceDefinition restorePostureRankOne(RuntimeBalanceDefinition current,
            RuntimeBalanceDefinition requested,PackEvidence evidence,BalanceSettings settings,RuntimeBuildScenarios.Plan plan) {
        for(String[] path:new String[][]{{"evasive","maximumDodgeChance"},{"bulwark","maximumResistance"},
                {"adaptive","resistancePerStack"}}) {
            var full=interpolatePostureRankOne(current,requested,path,1);
            double recovery=1;
            if(!RuntimeBuildScenarios.analyze(full,evidence,settings,plan).safeFor(null)) {
                double low=0,high=1;
                for(int pass=0;pass<20;pass++) {
                    double middle=(low+high)/2;
                    var candidate=interpolatePostureRankOne(current,requested,path,middle);
                    if(RuntimeBuildScenarios.analyze(candidate,evidence,settings,plan).safeFor(null))low=middle;else high=middle;
                }
                recovery=low;
            }
            current=interpolatePostureRankOne(current,requested,path,recovery);
            var json=current.toJson();
            json.getAsJsonObject("composition").addProperty("posture_"+path[0]+"_rank_one_recovery",recovery);
            current=RuntimeBalanceDefinition.fromJson(json);
        }
        return current;
    }
    private static RuntimeBalanceDefinition interpolatePostureRankOne(RuntimeBalanceDefinition current,
            RuntimeBalanceDefinition requested,String[] path,double fraction) {
        var json=current.toJson();
        var effect=json.getAsJsonObject("effects").getAsJsonObject("posture").getAsJsonObject(path[0]);
        double initial=effect.get(path[1]).getAsDouble();
        double target=requested.toJson().getAsJsonObject("effects").getAsJsonObject("posture").getAsJsonObject(path[0]).get(path[1]).getAsDouble();
        effect.addProperty(path[1],initial+(target-initial)*fraction);
        return RuntimeBalanceDefinition.fromJson(json);
    }

    private static RuntimeBalanceDefinition restoreRankGrowth(RuntimeBalanceDefinition current,
            Map<String,com.mistaboom.essence_ascendance.skill.balance.SkillBalanceRuntime.ResolvedSkill> requested,
            PackEvidence evidence,BalanceSettings settings,RuntimeBuildScenarios.Plan plan) {
        // Recover each consumer independently after finding a safe common floor.
        // An area-continuation limit must not flatten control duration, guard
        // mobility, or an unrelated stance's entire progression curve.
        for(var entry:new TreeMap<>(requested).entrySet()) {
            String id=entry.getKey();
            if (RuntimeBuildScenarios.vitalitySkill(ResourceLocation.parse(id))) continue;
            if(current.skillCurves().get(id).equals(entry.getValue()))continue;
            var full=interpolateRankGrowth(current,id,entry.getValue(),1);
            if(RuntimeBuildScenarios.analyze(full,evidence,settings,plan).safeFor(null)) {
                current=full;continue;
            }
            double low=0,high=1;
            for(int pass=0;pass<14;pass++) {
                double middle=(low+high)/2;
                var candidate=interpolateRankGrowth(current,id,entry.getValue(),middle);
                if(RuntimeBuildScenarios.analyze(candidate,evidence,settings,plan).safeFor(null))low=middle;else high=middle;
            }
            current=interpolateRankGrowth(current,id,entry.getValue(),low);
        }
        return current;
    }
    private static RuntimeBalanceDefinition interpolateRankGrowth(RuntimeBalanceDefinition current,String id,
            com.mistaboom.essence_ascendance.skill.balance.SkillBalanceRuntime.ResolvedSkill requested,double fraction) {
        var curves=new TreeMap<>(current.skillCurves());var initial=curves.get(id);
        var ranks=new ArrayList<com.mistaboom.essence_ascendance.skill.balance.SkillBalanceRuntime.ResolvedRank>();
        for(int i=0;i<initial.ranks().size();i++) {
            var rank=initial.ranks().get(i);
            ranks.add(new com.mistaboom.essence_ascendance.skill.balance.SkillBalanceRuntime.ResolvedRank(rank.rank(),rank.cost(),
                    rank.powerMultiplier()+(requested.ranks().get(i).powerMultiplier()-rank.powerMultiplier())*fraction));
        }
        curves.put(id,new com.mistaboom.essence_ascendance.skill.balance.SkillBalanceRuntime.ResolvedSkill(initial.maximumRank(),ranks));
        return new RuntimeBalanceDefinition(current.config(),current.crucible(),current.pylons(),curves,current.composition(),current.attunement());
    }

    private static RuntimeBalanceDefinition restoreNexusOffense(RuntimeBalanceDefinition current,RuntimeBalanceDefinition requested,
            PackEvidence evidence,BalanceSettings settings,RuntimeBuildScenarios.Plan plan) {
        double low=0,high=1;
        var full=interpolateNexusOffense(current,requested,1);
        if(RuntimeBuildScenarios.analyze(full,evidence,settings,plan).safeFor(BuildComposition.Channel.OFFENSE))return full;
        for(int pass=0;pass<20;pass++) {
            double middle=(low+high)/2;
            var candidate=interpolateNexusOffense(current,requested,middle);
            if(RuntimeBuildScenarios.analyze(candidate,evidence,settings,plan).safeFor(BuildComposition.Channel.OFFENSE))low=middle;else high=middle;
        }
        return interpolateNexusOffense(current,requested,low);
    }
    private static RuntimeBalanceDefinition interpolateNexusOffense(RuntimeBalanceDefinition current,RuntimeBalanceDefinition requested,double fraction) {
        var json=current.toJson();var bonuses=json.getAsJsonObject("statMaxBonuses");
        for(var stat:List.of(EssenceStats.MELEE_DAMAGE,EssenceStats.MELEE_ATTACK_SPEED,EssenceStats.RANGED_DAMAGE,
                EssenceStats.RANGED_ATTACK_SPEED,EssenceStats.MAGIC_DAMAGE,EssenceStats.MAGIC_CAST_SPEED)) {
            double initial=current.config().statMaxBonus(stat),target=requested.config().statMaxBonus(stat);
            bonuses.addProperty(stat.id().toString(),initial+(target-initial)*fraction);
        }
        RuntimeValueQuantization.apply(json);
        double factor=bonuses.get(EssenceStats.MELEE_DAMAGE.id().toString()).getAsDouble()
                /Math.max(1,requested.config().statMaxBonus(EssenceStats.MELEE_DAMAGE));
        json.getAsJsonObject("composition").addProperty("nexus_offense_factor",factor);
        return RuntimeBalanceDefinition.fromJson(json);
    }

    private static double calibration(RuntimeBalanceDefinition source,PackEvidence evidence,BalanceSettings settings,
            RuntimeBuildScenarios.Plan plan,BuildComposition.Channel channel,boolean rankGrowth) {
        if(RuntimeBuildScenarios.analyze(source,evidence,settings,plan).safeFor(channel))return 1;
        var zero=RuntimeBuildScenarios.analyze(adjusted(source,0,channel,rankGrowth,settings),evidence,settings,plan);
        if(!zero.safeFor(channel)) {
            zero.requireSafe();
            throw new IllegalArgumentException("Cannot calibrate "+channel+" without changing base equipment");
        }
        double low=0,high=1;
        for(int pass=0;pass<20;pass++) {
            double middle=(low+high)/2;
            var candidate=adjusted(source,middle,channel,rankGrowth,settings);
            if(RuntimeBuildScenarios.analyze(candidate,evidence,settings,plan).safeFor(channel))low=middle;else high=middle;
        }
        if(low<.02)throw new IllegalArgumentException("Requested "+channel+" targets leave less than 2% of "
                +(rankGrowth?"additional rank growth":"rank-one added power")+"; revise external evidence or friendly power controls; "
                +RuntimeBuildScenarios.analyze(adjusted(source,.02,channel,rankGrowth,settings),evidence,settings,plan).cases().stream()
                    .filter(c->!c.evaluation().safe()).findFirst().map(c->c.evaluation().id()+" "+c.evaluation().violations()).orElse("unknown constraint"));
        return low;
    }
    private static RuntimeBalanceDefinition adjusted(RuntimeBalanceDefinition source,double factor,
            BuildComposition.Channel channel,boolean rankGrowth,BalanceSettings settings) {
        if(factor==1)return source;
        JsonObject json=source.toJson();
        if(rankGrowth) {
            for(var entry:json.getAsJsonObject("skillCurves").entrySet()) {
                if(!com.mistaboom.essence_ascendance.skill.balance.SkillRankEffectScaling.supports(ResourceLocation.parse(entry.getKey())))continue;
                if(RuntimeBuildScenarios.vitalitySkill(ResourceLocation.parse(entry.getKey())))continue;
                if(entry.getKey().equals(com.mistaboom.essence_ascendance.skill.SkillIds.STATUS_MIRROR.toString()))continue;
                var skillId = ResourceLocation.parse(entry.getKey());
                boolean posture=isPosture(skillId)
                        || skillId.equals(com.mistaboom.essence_ascendance.skill.SkillIds.HUNGER_WARD)
                        || skillId.equals(com.mistaboom.essence_ascendance.skill.SkillIds.DAMAGE_CEILING)
                        || skillId.equals(com.mistaboom.essence_ascendance.skill.SkillIds.PAIN_PURGE)
                        || skillId.equals(com.mistaboom.essence_ascendance.skill.SkillIds.SOUL_WARD)
                        || skillId.equals(com.mistaboom.essence_ascendance.skill.SkillIds.DEEP_WARD);
                boolean healing = skillId.equals(com.mistaboom.essence_ascendance.skill.SkillIds.SHATTERING_WARD);
                if(channel==BuildComposition.Channel.OFFENSE && (posture || healing)
                        || channel==BuildComposition.Channel.DEFENSE && !posture
                        || channel==BuildComposition.Channel.HEALING && !posture && !healing)continue;
                for(var element:entry.getValue().getAsJsonObject().getAsJsonArray("ranks")) {
                    var rank=element.getAsJsonObject();
                    rank.addProperty("powerMultiplier",1+(rank.get("powerMultiplier").getAsDouble()-1)*factor);
                }
            }
        } else attenuateCombat(json,factor,channel,settings);
        RuntimeValueQuantization.apply(json);
        return RuntimeBalanceDefinition.fromJson(json);
    }
    private static void attenuateCombat(JsonObject json,double factor,BuildComposition.Channel channel,BalanceSettings settings) {
        var affected=switch(channel) {
            case OFFENSE -> List.of(EssenceStats.MELEE_DAMAGE,EssenceStats.MELEE_ATTACK_SPEED,EssenceStats.RANGED_DAMAGE,
                    EssenceStats.RANGED_ATTACK_SPEED,EssenceStats.MAGIC_DAMAGE,EssenceStats.MAGIC_CAST_SPEED);
            case DEFENSE -> List.of(EssenceStats.MELEE_RESISTANCE,EssenceStats.RANGED_RESISTANCE,EssenceStats.MAGIC_RESISTANCE,EssenceStats.MAX_HEALTH);
            case HEALING -> List.of(EssenceStats.HEALTH_REGENERATION);
        };
        for(var stat:affected) {
            JsonObject bonuses=json.getAsJsonObject("statMaxBonuses");String key=stat.id().toString();
            bonuses.addProperty(key,bonuses.get(key).getAsDouble()*factor);
        }
        if(channel==BuildComposition.Channel.OFFENSE) {
            scaleEffectFields(json.getAsJsonObject("effects"),factor);
            var adrenaline = json.getAsJsonObject("effects").getAsJsonObject("vitality").getAsJsonObject("damage").getAsJsonObject("adrenaline");
            adrenaline.addProperty("attackSpeedBonus", adrenaline.get("attackSpeedBonus").getAsDouble() * factor);
            JsonObject projectiles=json.getAsJsonObject("effects").getAsJsonObject("projectiles");
            for(String key:List.of("ricochetDamageMultiplier","piercingDamageMultiplier"))
                projectiles.addProperty(key,projectiles.get(key).getAsDouble()*factor);
            JsonObject composition=json.getAsJsonObject("composition");
            for(String key:List.of("rank_safe_skill_scale","skills_standalone_factor"))
                composition.addProperty(key,composition.get(key).getAsDouble()*factor);
        } else if(channel==BuildComposition.Channel.DEFENSE) {
            scalePostureFields(json.getAsJsonObject("effects").getAsJsonObject("posture"),factor);
            var routing = json.getAsJsonObject("effects").getAsJsonObject("vitality").getAsJsonObject("damage");
            var ward = routing.getAsJsonObject("hungerWard");
            ward.addProperty("damageShare", com.mistaboom.essence_ascendance.vitality.DamageRoutingMath.scaleShare(
                    ward.get("damageShare").getAsDouble(), factor));
            var ceiling = routing.getAsJsonObject("damageCeiling");
            ceiling.addProperty("damageTakenFraction", com.mistaboom.essence_ascendance.vitality.DamageRoutingMath.scaleTakenFraction(
                    ceiling.get("damageTakenFraction").getAsDouble(), factor));
            var wards = json.getAsJsonObject("effects").getAsJsonObject("vitality").getAsJsonObject("wards");
            var soul = wards.getAsJsonObject("soulWard");
            soul.addProperty("capacityHealthFraction", soul.get("capacityHealthFraction").getAsDouble() * factor);
            soul.addProperty("victimHealthFraction", soul.get("victimHealthFraction").getAsDouble() * factor);
            var deep = wards.getAsJsonObject("deepWard");
            deep.addProperty("capacityBonusFraction", deep.get("capacityBonusFraction").getAsDouble() * factor);
            // The same serialized percentage also determines the max-HP cost. Never calibrate a
            // second ratio independently or revive the old maximum-health hit threshold.
        } else if (channel == BuildComposition.Channel.HEALING) {
            var purge = json.getAsJsonObject("effects").getAsJsonObject("vitality")
                    .getAsJsonObject("damage").getAsJsonObject("painPurge");
            purge.addProperty("queuePerHealing", purge.get("queuePerHealing").getAsDouble() * factor);
            var shattering = json.getAsJsonObject("effects").getAsJsonObject("vitality")
                    .getAsJsonObject("wards").getAsJsonObject("shatteringWard");
            shattering.addProperty("healingFractionPerSecond", shattering.get("healingFractionPerSecond").getAsDouble() * factor);
        }
    }
    private static boolean isPosture(ResourceLocation id) {
        return id.equals(com.mistaboom.essence_ascendance.skill.SkillIds.EVASIVE_CURRENT)
                || id.equals(com.mistaboom.essence_ascendance.skill.SkillIds.BULWARK_STANCE)
                || id.equals(com.mistaboom.essence_ascendance.skill.SkillIds.ADAPTIVE_GUARD);
    }
    private static void scalePostureFields(JsonObject posture,double factor) {
        for(String[] path:new String[][]{{"evasive","maximumDodgeChance"},{"bulwark","maximumResistance"},
                {"adaptive","resistancePerStack"}}) {
            var effect=posture.getAsJsonObject(path[0]);
            effect.addProperty(path[1],effect.get(path[1]).getAsDouble()*factor);
        }
    }
    private static double enemyReference(PackEvidence evidence,ProgressionBand band,CapabilityAxis axis,boolean bosses,double fallback) {
        double[] values=evidence.enemies().stream().filter(e->e.included()&&e.stage().ordinal()<=band.ordinal()
                &&(bosses?(e.encounter()==EnemyReference.Encounter.BOSS||e.encounter()==EnemyReference.Encounter.APEX):(e.encounter()==EnemyReference.Encounter.ROUTINE||e.encounter()==EnemyReference.Encounter.ELITE)))
                .mapToDouble(e->e.axes().getOrDefault(axis,0.0)).filter(v->Double.isFinite(v)&&v>0).sorted().toArray();
        return values.length==0?fallback:values[(int)Math.floor((values.length-1)*.75)];
    }
    private static double boundedArmor(double referenceArmor,double referenceToughness,double armor,double toughness,double incoming,double headroom) {
        double minimumTaken=BuildComposition.armorDamageFraction(incoming,referenceArmor,referenceToughness)/headroom;
        if(BuildComposition.armorDamageFraction(incoming,armor,toughness)>=minimumTaken)return armor;
        double low=0,high=armor;
        for(int i=0;i<32;i++) {
            double middle=(low+high)/2;
            if(BuildComposition.armorDamageFraction(incoming,middle,toughness)>=minimumTaken)low=middle;else high=middle;
        }
        return low;
    }
    private static double bandPower(BalanceSettings s,ProgressionBand band) {
        return switch(band) {case ENTRY,EARLY -> s.earlyPower();case MID->s.midPower();case LATE->s.latePower();case APEX->s.apexPower();};
    }
    private static double medianSupply(PackEvidence evidence,EconomyProfile economy,ProgressionBand band) {
        if(economy==null)return medianEconomic(evidence,band);
        double[] values=economy.resources().entrySet().stream().filter(e->{var r=evidence.resources().get(e.getKey());return r!=null&&r.reachable()&&r.external()&&r.stage().ordinal()<=band.ordinal()&&e.getValue().dissolutionYield().amount()>0;})
                .mapToDouble(e->e.getValue().dissolutionYield().amount()).sorted().toArray();
        return values.length==0?1:Math.max(.01,values[values.length/2]);
    }
    private static double categoryFactor(PackEvidence evidence,EconomyProfile economy,String essenceId) {
        if(economy==null)return 1;
        double[] category=economy.resources().entrySet().stream().filter(e->{var r=evidence.resources().get(e.getKey());return r!=null&&r.reachable()&&r.external()&&e.getValue().routedYields().getOrDefault(essenceId,0.0)>0;})
                .mapToDouble(e->e.getValue().routedYields().get(essenceId)).sorted().toArray();
        if(category.length==0)return .5;
        return Math.clamp(category[category.length/2]/medianSupply(evidence,economy,ProgressionBand.APEX),.1,4);
    }
    private static void applyExact(JsonObject json,String pointer,Object value) {
        if(pointer.equals("composition")||pointer.startsWith("composition/")||pointer.equals("balanceProfile/id"))
            throw new IllegalArgumentException("Exact override /runtime/"+pointer+" targets derived diagnostics/identity; edit a real runtime value or friendly budget instead");
        String[] keys=pointer.split("/",-1);JsonElement parent=json;
        for(int i=0;i<keys.length-1;i++) parent=child(parent,keys[i],pointer);
        String leaf=keys[keys.length-1].replace("~1","/").replace("~0","~");
        child(parent,keys[keys.length-1],pointer);
        if(parent.isJsonObject())parent.getAsJsonObject().add(leaf,JSON.toJsonTree(value));
        else parent.getAsJsonArray().set(Integer.parseInt(leaf),JSON.toJsonTree(value));
    }
    private static JsonElement child(JsonElement parent,String encoded,String pointer) {
        String key=encoded.replace("~1","/").replace("~0","~");
        if(parent.isJsonObject()&&parent.getAsJsonObject().has(key))return parent.getAsJsonObject().get(key);
        if(parent.isJsonArray()&&key.matches("0|[1-9][0-9]*")) {
            try {int index=Integer.parseInt(key);if(index<parent.getAsJsonArray().size())return parent.getAsJsonArray().get(index);}
            catch(NumberFormatException ignored) { }
        }
        throw new IllegalArgumentException("Unknown exact runtime value /runtime/"+pointer);
    }
    private static double medianEconomic(PackEvidence evidence,ProgressionBand band) {
        double[] values=evidence.resources().values().stream().filter(r->r.reachable()&&r.external()&&r.stage().ordinal()<=band.ordinal()&&r.economicValue()>0)
                .mapToDouble(ResourceEvidence::economicValue).sorted().toArray();
        if(values.length==0)return 1; // Unitless bootstrap/minimum effort, never a fabricated tier economy.
        return Math.max(1,values[values.length/2]);
    }
    private static long positiveLong(double value) {
        if(!Double.isFinite(value)||value>Long.MAX_VALUE/100_000_000.0)throw new IllegalArgumentException("Generated economy amount exceeds safe accounting range");
        return Math.max(1,Math.round(value));
    }
    private static long multipleOfNine(double value) { long raw=positiveLong(value); return Math.max(9,raw-raw%9); }
    private static ShieldBalanceSettings shield(Map<ResourceLocation,EquipmentBaselineConfig.TierBaseline> equipment,BalanceSettings settings) {
        var durability=new EnumMap<EquipmentTier,Integer>(EquipmentTier.class);
        var reflection=new EnumMap<EquipmentTier,Double>(EquipmentTier.class);var amplification=new EnumMap<EquipmentTier,Double>(EquipmentTier.class);
        double baseReflection=100*RuntimeReferencePolicy.SHIELD_INNATE_REFLECTION_RATIO*Math.sqrt(settings.overallPower());
        for(var tier:EquipmentTier.values()) {
            durability.put(tier,tier==EquipmentTier.LATENT?equipment.get(ResourceLocation.fromNamespaceAndPath(EssenceAscendance.MOD_ID,"latent")).durability():equipment.get(tier.ascendanceTier().id()).durability());
            double progress=tier.ordinal()/(double)(EquipmentTier.values().length-1);
            reflection.put(tier,baseReflection*RuntimeReferencePolicy.SHIELD_TIER_REFLECTION_GROWTH_RATIO*progress);
            amplification.put(tier,1+RuntimeReferencePolicy.SHIELD_BLOCK_HEADROOM_RATIO*progress/(1+settings.compositionSafeguard()));
        }
        return new ShieldBalanceSettings(baseReflection,ShieldBalanceSettings.defaults().minimumDisableTicks(),durability,reflection,amplification);
    }
    private static SkillEffectBalanceSettings effects(double scale, PackEvidence evidence, BalanceSettings settings) {
        JsonObject tree=JSON.toJsonTree(SkillEffectBalanceSettings.defaults()).getAsJsonObject();
        scaleEffectFields(tree,scale);
        scalePostureFields(tree.getAsJsonObject("posture"),Math.min(1,scale));
        tree.add("vitality", JSON.toJsonTree(VitalityBalanceGenerator.generate(evidence, settings)));
        tree.add("mobility", JSON.toJsonTree(MobilityBalanceGenerator.generate(settings)));
        tree.add("gathering", JSON.toJsonTree(GatheringBalanceGenerator.generate(settings)));
        return JSON.fromJson(tree,SkillEffectBalanceSettings.class);
    }
    private static void scaleEffectFields(JsonObject tree,double scale) {
        // Registered mechanical parameter paths preserve trigger/cadence/range identities while scaling actual power.
        for(String path:List.of("frenzy.damageBonusPercentPerStack","frenzy.attackSpeedBonusPercentPerStack",
                "armorCrack.armorReductionPerStack","armorCrack.toughnessReductionPerStack","desperation.maxDamageBonusPercent",
                "deathRush.attackSpeedBonusPercentPerStack","deathRush.bowDrawSpeedBonusPercentPerStack","deathRush.castSpeedBonusPercentPerStack",
                "kindling.burningDamagePercentPerSecond","kindling.burningDamageAmplificationPercent","combustion.damage","shatter.shardDamage","staticCharge.lightningDamage")) {
            String[] bits=path.split("\\.");JsonObject parent=tree.getAsJsonObject(bits[0]);parent.addProperty(bits[1],parent.get(bits[1]).getAsDouble()*scale);
        }
        // Secondary payload damage uses the same generated power and composition guard.
        // Control timing and targeting remain identity/avoidance policy, never invented DPS.
        JsonObject payload = tree.getAsJsonObject("projectiles").getAsJsonObject("payload");
        payload.addProperty("explosiveDamageScale", payload.get("explosiveDamageScale").getAsDouble() * scale);
        JsonObject guard = tree.getAsJsonObject("guard");
        for (String section : List.of("storedForce", "reprisal", "riposte")) {
            JsonObject effect = guard.getAsJsonObject(section);
            effect.addProperty("damageScale", Math.min(section.equals("reprisal") ? 1 : 4,
                    effect.get("damageScale").getAsDouble() * scale));
        }
        JsonObject ward = guard.getAsJsonObject("ward");
        ward.addProperty("preventedReflectionScale", Math.min(1, ward.get("preventedReflectionScale").getAsDouble() * scale));
        JsonObject amplifier = guard.getAsJsonObject("amplifier");
        double maximum = Math.min(4, 1 + (amplifier.get("maximumMultiplier").getAsDouble() - 1) * scale);
        amplifier.addProperty("perBlockGrowth", Math.min(maximum - 1, amplifier.get("perBlockGrowth").getAsDouble() * scale));
        amplifier.addProperty("maximumMultiplier", maximum);
    }

}
