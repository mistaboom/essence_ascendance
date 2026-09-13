package com.mistaboom.essence_ascendance.balance.runtime;

import com.mistaboom.essence_ascendance.balance.config.BalanceSettings;
import com.mistaboom.essence_ascendance.balance.engine.*;
import com.mistaboom.essence_ascendance.config.SkillEffectBalanceSettings;
import com.mistaboom.essence_ascendance.equipment.EquipmentProfileRegistry;
import com.mistaboom.essence_ascendance.equipment.EquipmentBaselineProperty;
import com.mistaboom.essence_ascendance.equipment.EquipmentBaselineService;
import com.mistaboom.essence_ascendance.equipment.EquipmentProfileDefinition;
import com.mistaboom.essence_ascendance.skill.*;
import com.mistaboom.essence_ascendance.skill.balance.*;
import com.mistaboom.essence_ascendance.stat.*;
import com.mistaboom.essence_ascendance.tier.*;
import com.mistaboom.essence_ascendance.progression.StatScalingService;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Generation-only numeric check of actual resolved values and legal current skill selections. */
public final class RuntimeBuildScenarios {
    private RuntimeBuildScenarios() {}
    public record Case(String tier, String skillSelection, BuildComposition.Limits limits,
                       BuildComposition.Evaluation evaluation) {}
    public record Analysis(double attenuation, List<Case> cases, List<String> assumptions) {
        public Analysis { cases=List.copyOf(cases); assumptions=List.copyOf(assumptions); }
        public void requireSafe() { for(var row:cases)row.evaluation().requireSafe(); }
    }
    public record Plan(Map<ResourceLocation,List<SkillLoadoutProjection.Scenario>> full,
                       Map<ResourceLocation,List<SkillLoadoutProjection.Scenario>> moderate) {}
    public static Plan plan() {
        Map<ResourceLocation,Integer> fullRanks=new LinkedHashMap<>(),moderateRanks=new LinkedHashMap<>();
        for(var skill:SkillRegistry.values()) {
            fullRanks.put(skill.id(),1);
            moderateRanks.put(skill.id(),skill.prerequisites().isEmpty()?1:0);
        }
        Map<ResourceLocation,List<SkillLoadoutProjection.Scenario>> full=new LinkedHashMap<>(),moderate=new LinkedHashMap<>();
        for(var tier:AscendanceTierRegistry.powerTiers()) {
            full.put(tier.id(),SkillLoadoutProjection.project(SkillRegistry.values(),tier.id(),fullRanks,(id,rank)->1,Map.of(),false).scenarios());
            moderate.put(tier.id(),SkillLoadoutProjection.project(SkillRegistry.values(),tier.id(),moderateRanks,(id,rank)->1,Map.of(),false).scenarios());
        }
        return new Plan(Collections.unmodifiableMap(full),Collections.unmodifiableMap(moderate));
    }
    public static Analysis analyze(RuntimeBalanceDefinition runtime,PackEvidence evidence,BalanceSettings settings,Plan plan,boolean guard) {
        return analyze(runtime,evidence,settings,plan,guard,null);
    }
    public static Analysis analyze(RuntimeBalanceDefinition runtime,PackEvidence evidence,BalanceSettings settings,Plan plan,boolean guard,BuildComposition.Channel channel) {
        List<Case> result=new ArrayList<>(); double attenuation=1;
        boolean parity=runtime.composition().getOrDefault("equipment_apex_parity",0.0)==1.0;
        int bandIndex=0;
        for(var tier:AscendanceTierRegistry.powerTiers().stream().sorted(Comparator.comparingInt(AscendanceTierDefinition::order)).toList()) {
            ProgressionBand band=ProgressionBand.at(bandIndex++);
            double relative=settings.overallPower()*switch(band){case ENTRY,EARLY->settings.earlyPower();case MID->settings.midPower();case LATE->settings.latePower();case APEX->settings.apexPower();};
            double rate=RuntimeReferencePolicy.required(evidence,band,CapabilityAxis.ATTACK_RATE);
            double dps=RuntimeReferencePolicy.required(evidence,band,CapabilityAxis.SUSTAINED_DAMAGE);
            double armor=RuntimeReferencePolicy.observed(evidence,band,CapabilityAxis.ARMOR,0);
            double toughness=RuntimeReferencePolicy.observed(evidence,band,CapabilityAxis.TOUGHNESS,0);
            // The equipment frontier's EFFECTIVE_HEALTH already includes armor mitigation.
            // This model accepts raw health and applies the observed armor exactly once.
            double health=RuntimeReferencePolicy.playerHealth();
            double incoming=runtime.composition().getOrDefault("enemy_damage_"+band.name().toLowerCase(Locale.ROOT),RuntimeReferencePolicy.playerHit());
            if(parity) {
                var pairedArmor=RuntimeReferencePolicy.armor(evidence,band,settings.outlierPolicy().name(),incoming);
                armor=pairedArmor.armor();toughness=pairedArmor.toughness();
            }
            double survivalWindow=settings.generation().survivalWindowSeconds();
            // Relative intent allocates incremental headroom above an ordinary external loadout.
            double headroom=1+relative;
            var baseline=runtime.config().equipmentBaselineConfig().baselineFor(tier);
            Map<String,RuntimeReferencePolicy.Weapon> familyReferences=new HashMap<>();
            for(String family:List.of("melee_shield","ranged","caster"))familyReferences.put(family,parity
                    ?RuntimeReferencePolicy.weapon(evidence,band,family,settings.outlierPolicy().name())
                    :new RuntimeReferencePolicy.Weapon(dps/rate,rate,Math.max(dps/rate,evidence.reference(band,CapabilityAxis.BURST_DAMAGE,dps/rate)),false));
            Set<String> duplicateSelections=new HashSet<>();
            for(var scenario:plan.full().get(tier.id())) {
                String family=scenario.equipmentContext();
                if(!Set.of("melee_shield","ranged","caster").contains(family))continue;
                String signature=family+new TreeSet<>(scenario.contributingRanks().keySet()).toString();
                if(!duplicateSelections.add(signature))continue;
                var reference=familyReferences.get(family);
                var external=new BuildComposition.Equipment(reference.damage(),reference.rate(),armor,toughness,health,0);
                var externalMetrics=BuildComposition.compose(external,BuildComposition.Modifier.none(),BuildComposition.Modifier.none(),incoming,survivalWindow);
                var limits=new BuildComposition.Limits(reference.dps()*headroom,reference.burst()*headroom,
                        reference.dps()*relative*2,externalMetrics.effectiveHealth()*headroom,
                        externalMetrics.effectiveHealth()*headroom*1.35,health*relative/survivalWindow);
                double damage=family.equals("ranged")?baseline.rangedDamage():family.equals("caster")?baseline.magicDamage():baseline.meleeDamage();
                double attackRate=family.equals("ranged")?baseline.rangedAttackSpeed():family.equals("caster")?baseline.magicCastSpeed():baseline.meleeAttackSpeed();
                EquipmentBaselineProperty damageProperty=family.equals("ranged")?EquipmentBaselineProperty.RANGED_DAMAGE:family.equals("caster")?EquipmentBaselineProperty.MAGIC_DAMAGE:EquipmentBaselineProperty.MELEE_DAMAGE;
                EquipmentBaselineProperty speedProperty=family.equals("ranged")?EquipmentBaselineProperty.RANGED_ATTACK_SPEED:family.equals("caster")?EquipmentBaselineProperty.MAGIC_CAST_SPEED:EquipmentBaselineProperty.MELEE_ATTACK_SPEED;
                for(var archetype:EquipmentProfileRegistry.values()) {
                if(archetype.baselineMultiplier(damageProperty)<=0||archetype.baselineMultiplier(speedProperty)<=0)continue;
                double archetypeDamage=equipmentValue(runtime,damage,archetype,damageProperty);
                double archetypeRate=equipmentValue(runtime,attackRate,archetype,speedProperty);
                var ascendance=new BuildComposition.Equipment(Math.max(.01,archetypeDamage),archetypeRate,baseline.fullSetArmor(),baseline.fullSetToughness(),health,0);
                var smaller=plan.moderate().get(tier.id()).stream().filter(s->s.equipmentContext().equals(family)&&s.objective().equals(scenario.objective())).findFirst().orElse(null);
                var inputs=new BuildComposition.Inputs(tier.id()+"/"+archetype.id()+"/"+scenario.id(),external,ascendance,
                        nexus(runtime,tier,family,false),nexus(runtime,tier,family,true),
                        skills(runtime.config().skillEffects(),scenario.contributingRanks().keySet(),family,Math.min(external.hitDamage(),archetypeDamage),Math.min(reference.rate(),archetypeRate)),
                        skills(runtime.config().skillEffects(),smaller==null?Set.of():smaller.contributingRanks().keySet(),family,Math.min(external.hitDamage(),archetypeDamage),Math.min(reference.rate(),archetypeRate)),incoming,survivalWindow);
                var evaluated=BuildComposition.evaluate(inputs,limits);
                if(guard)attenuation=Math.min(attenuation,(channel==null?BuildComposition.guard(inputs,limits):BuildComposition.guard(inputs,limits,channel)).attenuation());
                result.add(new Case(tier.id().toString(),archetype.id()+"/"+scenario.id(),limits,evaluated));
                }
            }
        }
        if(result.isEmpty())throw new IllegalStateException("No registered equipment families were available for numeric build validation");
        return new Analysis(attenuation,result,List.of(
                "Only implemented skills and evaluator-approved dependency/choice/replacement selections contribute numeric combat effects; planned skills remain separate projections.",
                "Full Nexus means each stat reaches its generated tier cap. Broad investment uses each actual curve at half that cap. Moderate skills own eligible prerequisite-free roots and use legal choices.",
                "Skill numbers are conservative trigger estimates from generated handler parameters: maximum maintained stacks, one elemental completion per configured buildup cycle, and a five-second movement charge cycle; not measured combat logs.",
                "The ordinary external equipment frontier receives incremental headroom of overall × band policy; caps are external × (1 + requested headroom), with observed burst evidence separate from sustained DPS divided by attack rate.",
                "Offense, defense and healing are calibrated independently. Survival or recovery limits cannot reduce weapon damage, attack speed or offensive skill effects. Only actual currently purchasable ranks enter live numeric calibration.",
                "External equipment starts from the engine's raw player health; observed armor and toughness are applied once using the band's incoming enemy hit. Armor-adjusted frontier effective health is not treated as raw health.",
                "Parity profiles normalize each original physical axis curve once at Transcendent, then round to its gameplay unit. Armor compares physically wearable armor/toughness pairs; weapon families retain their own winning damage/cadence pairing. When a caster family is absent, the observed ranged DPS supplies the existing faster, lighter caster ratio; absent ranged evidence falls back explicitly to melee.",
                "Armor penetration uses an explicit conservative armor-pressure allowance. Homing reliability, roots, drag fields, interception, theft, shields, immunity, flight and gathering capabilities retain separate semantic budgets; no speculative prevented damage or Attunement activity is modeled.",
                "Equipment-focused and stat-focused scenarios are balance projections with required equipment access; actual applicability, ownership, live requirements and worn-slot coverage remain enforced by gameplay."));
    }
    static double equipmentValue(RuntimeBalanceDefinition runtime,double base,EquipmentProfileDefinition profile,EquipmentBaselineProperty property) {
        return runtime.composition().getOrDefault("equipment_quantization",0.0)==1.0
                ?EquipmentBaselineService.resolvedValue(base,profile,property):base*profile.baselineMultiplier(property);
    }
    private static BuildComposition.Modifier nexus(RuntimeBalanceDefinition runtime,AscendanceTierDefinition tier,String family,boolean moderate) {
        StatDefinition damage=family.equals("ranged")?EssenceStats.RANGED_DAMAGE:family.equals("caster")?EssenceStats.MAGIC_DAMAGE:EssenceStats.MELEE_DAMAGE;
        StatDefinition speed=family.equals("ranged")?EssenceStats.RANGED_ATTACK_SPEED:family.equals("caster")?EssenceStats.MAGIC_CAST_SPEED:EssenceStats.MELEE_ATTACK_SPEED;
        double resistance=Math.max(bonus(runtime,tier,EssenceStats.MELEE_RESISTANCE,moderate),Math.max(bonus(runtime,tier,EssenceStats.RANGED_RESISTANCE,moderate),bonus(runtime,tier,EssenceStats.MAGIC_RESISTANCE,moderate)))/100;
        double healing=bonus(runtime,tier,EssenceStats.HEALTH_REGENERATION,moderate)*2*(1+bonus(runtime,tier,EssenceStats.HEALING_EFFECTIVENESS,moderate)/100);
        return new BuildComposition.Modifier(0,1+bonus(runtime,tier,damage,moderate)/100,1+bonus(runtime,tier,speed,moderate)/100,
                0,0,bonus(runtime,tier,EssenceStats.MAX_HEALTH,moderate)*2,0,0,resistance,0,healing,0);
    }
    private static double bonus(RuntimeBalanceDefinition runtime,AscendanceTierDefinition tier,StatDefinition stat,boolean moderate) {
        var profile=runtime.config().balanceProfile();long cap=profile.getInvestmentCap(tier,stat);
        return runtime.config().statMaxBonus(stat)*StatScalingService.progressionForInvestment(stat,moderate?cap/2:cap,tier,profile);
    }
    private static BuildComposition.Modifier skills(SkillEffectBalanceSettings s,Set<ResourceLocation> active,String family,double hit,double rate) {
        double damage=1,speed=1,flat=0,burst=0,area=0;
        hit=Math.max(.1,hit);rate=Math.max(.2,rate);
        if(active.contains(SkillIds.FRENZY)) {damage+=s.frenzy().maxStacks()*s.frenzy().damageBonusPercentPerStack()/100;speed+=s.frenzy().maxStacks()*s.frenzy().attackSpeedBonusPercentPerStack()/100;}
        if(active.contains(SkillIds.ARMOR_CRACK))damage*=1+Math.min(.8,s.armorCrack().maxStacks()*s.armorCrack().armorReductionPerStack()/25);
        if(active.contains(SkillIds.DESPERATION))damage+=s.desperation().maxDamageBonusPercent()/100;
        if(active.contains(SkillIds.DEATH_RUSH))speed+=s.deathRush().maxStacks()*(family.equals("ranged")?s.deathRush().bowDrawSpeedBonusPercentPerStack():family.equals("caster")?s.deathRush().castSpeedBonusPercentPerStack():s.deathRush().attackSpeedBonusPercentPerStack())/100;
        if(active.contains(SkillIds.KINDLING))damage*=1+s.kindling().burningDamageAmplificationPercent()/100+s.kindling().burningDamagePercentPerSecond()/100/rate;
        if(active.contains(SkillIds.COMBUSTION)) {double proc=s.combustion().damage()/Math.max(1,s.kindling().maxHeat());flat+=proc;burst+=s.combustion().damage()/hit;area+=proc*Math.max(0,s.combustion().targetsPerBurst()-1)/hit;}
        if(active.contains(SkillIds.SHATTER)) {double proc=s.shatter().shardDamage()/Math.max(1,s.frostbite().maxChill());flat+=proc;burst+=s.shatter().shardDamage()/hit;area+=proc*Math.max(0,s.shatter().maximumTargets()-1)/hit;}
        if(active.contains(SkillIds.STATIC_CHARGE)) {flat+=s.staticCharge().lightningDamage()/5/rate;burst+=s.staticCharge().lightningDamage()/hit;}
        if(active.contains(SkillIds.CHAIN_STRIKE)) {double retained=s.chainStrike().damageFalloff();for(int i=0;i<s.chainStrike().maximumJumps();i++){area+=s.staticCharge().lightningDamage()/5/rate/hit*retained;retained*=s.chainStrike().damageFalloff();}}
        if(active.contains(SkillIds.RICOCHET)) {double retained=s.projectiles().ricochetDamageMultiplier();for(int i=0;i<s.projectiles().ricochets();i++){area+=retained;retained*=s.projectiles().ricochetDamageMultiplier();}}
        if(active.contains(SkillIds.PIERCING_PROJECTILE)) {double retained=s.projectiles().piercingDamageMultiplier();for(int i=0;i<s.projectiles().penetrations();i++){area+=retained;retained*=s.projectiles().piercingDamageMultiplier();}}
        area += projectilePayloadArea(s, active);
        return new BuildComposition.Modifier(flat,damage,speed,burst,area,0,0,0,0,0,0,0);
    }
    static double projectilePayloadArea(SkillEffectBalanceSettings s, Set<ResourceLocation> active) {
        double area = 0;
        if(active.contains(SkillIds.EXPLOSIVE_PAYLOAD)) {
            // Share the safe-area target bound with Combustion. Payload may compose
            // with path continuation, but only a bounded number of confirmed victims.
            int contacts = 1;
            double retention = 1;
            if(active.contains(SkillIds.RICOCHET)) { contacts += s.projectiles().ricochets(); retention = s.projectiles().ricochetDamageMultiplier(); }
            if(active.contains(SkillIds.PIERCING_PROJECTILE)) { contacts += s.projectiles().penetrations(); retention = s.projectiles().piercingDamageMultiplier(); }
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
