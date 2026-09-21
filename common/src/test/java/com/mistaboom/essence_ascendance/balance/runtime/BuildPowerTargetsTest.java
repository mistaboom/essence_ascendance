package com.mistaboom.essence_ascendance.balance.runtime;

import com.mistaboom.essence_ascendance.balance.config.BalanceSettings;
import com.mistaboom.essence_ascendance.balance.engine.BuildComposition.*;
import com.mistaboom.essence_ascendance.balance.engine.ProgressionBand;
import com.mistaboom.essence_ascendance.config.SkillEffectBalanceSettings;
import com.mistaboom.essence_ascendance.skill.*;
import com.mistaboom.essence_ascendance.skill.balance.SkillBalanceRuntime;
import com.mistaboom.essence_ascendance.stat.EssenceStats;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import java.util.*;

/** Runs with RuntimeBalanceTest's real registry bootstrap and generated candidate. */
final class BuildPowerTargetsTest {
    static void run(RuntimeBalanceDefinition runtime) {
        var settings=BalanceSettings.defaults();
        near(BuildPowerTargets.multiplier(settings,ProgressionBand.APEX,Participation.EQUIPMENT_FOCUSED),1,"Equipment parity target");
        near(BuildPowerTargets.multiplier(settings,ProgressionBand.APEX,Participation.BONUS_FOCUSED),2,"Nexus standalone target");
        near(BuildPowerTargets.multiplier(settings,ProgressionBand.APEX,Participation.SKILL_FOCUSED),2,"Skill standalone target");
        near(BuildPowerTargets.multiplier(settings,ProgressionBand.APEX,Participation.FULLY_COMBINED),3,"Combined target");
        var stronger=BalanceSettings.parse("[power]\noverall = 1.5\n","test");
        near(BuildPowerTargets.multiplier(stronger,ProgressionBand.APEX,Participation.FULLY_COMBINED),4,"Friendly power scales added headroom");
        near(BuildPowerTargets.multiplier(stronger,ProgressionBand.APEX,Participation.EQUIPMENT_FOCUSED),1,"Friendly power changed equipment parity");
        near(BuildPowerTargets.burstMultiplier(settings,ProgressionBand.APEX,Participation.CATEGORY_SPECIALIZED,false),3,"Omitted defense grants no burst permission");
        near(BuildPowerTargets.burstMultiplier(settings,ProgressionBand.APEX,Participation.CATEGORY_SPECIALIZED,true),3,"Offense-only rows retain ordinary ceiling even at low health");
        near(BuildPowerTargets.burstMultiplier(settings,ProgressionBand.APEX,Participation.FULLY_COMBINED,true),4,"Actual low-health combined burst ceiling");
        near(BuildPowerTargets.burstMultiplier(settings,ProgressionBand.APEX,Participation.SKILL_FOCUSED,true),2,"Standalone skill ceiling is still 2x");
        double[] combined={1.5,1.7,2,2.5,3};
        for(var band:ProgressionBand.values()) {
            near(BuildPowerTargets.multiplier(settings,band,Participation.FULLY_COMBINED),combined[band.ordinal()],"Tier ceiling");
            if(band!=ProgressionBand.ENTRY)check(BuildPowerTargets.rankOneMultiplier(settings,band,Participation.FULLY_COMBINED)
                    <combined[band.ordinal()],"First-purchase allocation leaves no room for actual ranks");
        }

        var effects=SkillEffectBalanceSettings.defaults();
        var weapon=new Equipment(10,2,0,0,20,0);
        var tool=new Equipment(1,2,0,0,20,0);
        var active=Set.of(SkillIds.FRENZY,SkillIds.STATIC_CHARGE,SkillIds.STORED_FORCE,SkillIds.RIPOSTE);
        var result=RuntimeBuildScenarios.combat(effects,active,"melee_shield",weapon,Modifier.none(),5,10,1);
        double frenzyDamage=1+effects.frenzy().maxStacks()*effects.frenzy().damageBonusPercentPerStack()/100;
        double frenzySpeed=1+effects.frenzy().maxStacks()*effects.frenzy().attackSpeedBonusPercentPerStack()/100;
        double flat=effects.staticCharge().lightningDamage();
        near(result.sustainedDamage(),10*frenzyDamage*2*frenzySpeed+flat/5,"Charge counted once; flat damage does not inherit a skill multiplier");
        near(result.burstDamage(),(10*frenzyDamage+flat)*(1+effects.guard().riposte().damageScale())
                +effects.guard().storedForce().capacity()*effects.guard().storedForce().damageScale(),"Primary/flat/riposte/stored-force order");
        var weak=RuntimeBuildScenarios.combat(effects,Set.of(SkillIds.STATIC_CHARGE),"melee_shield",tool,Modifier.none(),5,10,1);
        var strong=RuntimeBuildScenarios.combat(effects,Set.of(SkillIds.STATIC_CHARGE),"melee_shield",weapon,Modifier.none(),5,10,1);
        near(strong.burstDamage()-weak.burstDamage(),9,"Low-damage tool ratios leaked into external weapon burst");
        var area=RuntimeBuildScenarios.combat(effects,Set.of(SkillIds.COMBUSTION,SkillIds.SHATTER),"melee_shield",weapon,Modifier.none(),5,10,1);
        near(area.burstDamage(),10,"On-kill area damage was assigned to its dead primary target");
        near(area.sustainedDamage(),20,"On-kill area damage inflated single-target DPS");
        check(area.areaDamage()>0,"Secondary area effects disappeared");
        var desperate=RuntimeBuildScenarios.combat(effects,Set.of(SkillIds.DESPERATION),"melee_shield",weapon,Modifier.none(),5,10,.2);
        near(desperate.effectiveHealth(),4,"Glass cannon kept full-health survival");
        near(desperate.burstDamage(),10*(1+.8*effects.desperation().maxDamageBonusPercent()/100),"Desperation uses actual missing-health fraction");

        var full=RuntimeBuildScenarios.plan(runtime, true);
        for (var scenario : full.full().get(AscendanceTiers.TRANSCENDENT.id()))
            scenario.contributingRanks().forEach((id, rank) -> check(rank <= runtime.skillCurves().get(id.toString()).maximumRank(),
                    "Published projection exceeds a meaningful generated maximum"));
        var frenzy=SkillRegistry.require(SkillIds.FRENZY);
        var curve=runtime.skillCurves().get(SkillIds.FRENZY.toString());
        check(curve.maximumRank()==curve.ranks().size(), "Filler projection ranks leaked into the published profile");
        check(curve.ranks().getLast().cost()>=curve.ranks().getFirst().cost(),"Generated rank costs decreased");
        var installed=SkillBalanceRuntime.snapshot();
        try {
            SkillBalanceRuntime.clear();
            var ranked=RuntimeBuildScenarios.rankedEffects(runtime,Map.of(SkillIds.FRENZY,curve.maximumRank()));
            near(ranked.frenzy().damageBonusPercentPerStack(),curve.ranks().getLast().parameters().get("frenzy/damageBonusPercentPerStack"),
                    "Published native rank values depend on an installed profile");
            check(RuntimeBuildScenarios.rankedEffects(runtime,Map.of(SkillIds.FRENZY,1)).equals(runtime.config().skillEffects()),"Rank-one mirror differs from generated state");
            SkillBalanceRuntime.install(runtime.skillCurves());
            check(frenzy.maximumRank()==curve.maximumRank(),"Purchase authority did not adopt generated maximum");
        } finally {
            if(installed.isEmpty())SkillBalanceRuntime.clear();else SkillBalanceRuntime.install(installed);
        }

        for(var one:runtime.generationAnalysis().cases()) {
            check(one.participationLimits().size()==Participation.values().length,"Missing per-participation report limits");
            if(!one.tier().equals(AscendanceTiers.TRANSCENDENT.id().toString()))continue;
            check(one.evaluation().scenarios().get(Participation.BONUS_FOCUSED).healingPerSecond() >= 0,
                    "Nominal allocation report contains negative regeneration");
            check(one.limitFor(Participation.SKILL_FOCUSED).sustainedDamage()<one.limitFor(Participation.FULLY_COMBINED).sustainedDamage(),"Standalone builds inherited combined ceiling");
        }
        System.out.println("BuildPowerTargetsTest: final-output ceilings, nominal allocation budgets, flat/area accounting, real low-health costs and generated rank authority PASS");
    }
    private static void near(double actual,double expected,String message) { check(Math.abs(actual-expected)<1e-8,message+": "+actual+" != "+expected); }
    private static void check(boolean condition,String message) { if(!condition)throw new AssertionError(message); }
}
