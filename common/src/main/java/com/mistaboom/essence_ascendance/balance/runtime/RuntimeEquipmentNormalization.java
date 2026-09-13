package com.mistaboom.essence_ascendance.balance.runtime;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.balance.config.BalanceSettings;
import com.mistaboom.essence_ascendance.balance.engine.*;
import com.mistaboom.essence_ascendance.equipment.*;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Preserves physical stat curves while assigning discrete harvest access to equipment tiers. */
final class RuntimeEquipmentNormalization {
    private RuntimeEquipmentNormalization() {}
    static void apply(Map<ResourceLocation,EquipmentBaselineConfig.TierBaseline> equipment,
                      PackEvidence evidence, BalanceSettings settings, Map<String,Double> diagnostics) {
        var apex=Objects.requireNonNull(equipment.get(AscendanceTiers.TRANSCENDENT.id()));
        String policy=settings.outlierPolicy().name();
        var melee=RuntimeReferencePolicy.weapon(evidence,ProgressionBand.APEX,"melee_shield",policy);
        var ranged=RuntimeReferencePolicy.weapon(evidence,ProgressionBand.APEX,"ranged",policy);
        var caster=RuntimeReferencePolicy.weapon(evidence,ProgressionBand.APEX,"caster",policy);
        var playableCaster=caster.familyObserved()?caster:representableFallback(caster);
        var armor=RuntimeReferencePolicy.armor(evidence,ProgressionBand.APEX,policy,
                diagnostics.getOrDefault("enemy_damage_apex",RuntimeReferencePolicy.playerHit()));
        double harvest=RuntimeReferencePolicy.observed(evidence,ProgressionBand.APEX,CapabilityAxis.HARVEST_LEVEL,0);
        if(harvest!=Math.rint(harvest)||harvest>32)
            throw new IllegalArgumentException("Pack harvest reference exceeds the supported discrete level domain: "+harvest);
        long durability=Math.round(RuntimeReferencePolicy.required(evidence,ProgressionBand.APEX,CapabilityAxis.DURABILITY));
        if(durability<=0||durability>Integer.MAX_VALUE/64)
            throw new IllegalArgumentException("Pack durability reference exceeds supported equipment arithmetic: "+durability);
        var target=new EquipmentBaselineConfig.TierBaseline(armor.armor(),armor.toughness(),melee.damage(),melee.rate(),
                ranged.damage(),ranged.rate(),playableCaster.damage(),playableCaster.rate(),
                RuntimeReferencePolicy.required(evidence,ProgressionBand.APEX,CapabilityAxis.MINING_SPEED),(int)harvest,
                (int)durability);
        for(var property:EquipmentBaselineProperty.values()) {
            diagnostics.put("equipment_normalization_"+property.name().toLowerCase(Locale.ROOT),factor(apex.value(property),target.value(property)));
            double reference=switch(property) {
                case MAGIC_DAMAGE -> caster.damage();
                case MAGIC_CAST_SPEED -> caster.rate();
                default -> target.value(property);
            };
            diagnostics.put("equipment_apex_reference_"+property.name().toLowerCase(Locale.ROOT),reference);
        }
        diagnostics.put("equipment_harvest_tier_intervals",(double)(EquipmentTier.values().length-1));
        diagnostics.put("equipment_harvest_pack_maximum",(double)target.harvestLevel());
        diagnostics.put("equipment_apex_reference_harvest_level",(double)target.harvestLevel());
        Map<ResourceLocation,Integer> harvestLevels=new HashMap<>();
        for(var tier:EquipmentTier.values()) {
            var id=tier.ascendanceTier()==null
                    ?ResourceLocation.fromNamespaceAndPath(EssenceAscendance.MOD_ID,tier.serializedName())
                    :tier.ascendanceTier().id();
            harvestLevels.put(id,harvestLevel(tier,target.harvestLevel()));
        }
        for(var entry:equipment.entrySet()) {
            var row=entry.getValue();
            entry.setValue(new EquipmentBaselineConfig.TierBaseline(
                    scale(row.fullSetArmor(),apex.fullSetArmor(),target.fullSetArmor()),
                    scale(row.fullSetToughness(),apex.fullSetToughness(),target.fullSetToughness()),
                    scale(row.meleeDamage(),apex.meleeDamage(),target.meleeDamage()),
                    scale(row.meleeAttackSpeed(),apex.meleeAttackSpeed(),target.meleeAttackSpeed()),
                    scale(row.rangedDamage(),apex.rangedDamage(),target.rangedDamage()),
                    scale(row.rangedAttackSpeed(),apex.rangedAttackSpeed(),target.rangedAttackSpeed()),
                    scale(row.magicDamage(),apex.magicDamage(),target.magicDamage()),
                    scale(row.magicCastSpeed(),apex.magicCastSpeed(),target.magicCastSpeed()),
                    Math.max(1,scale(row.miningSpeed(),apex.miningSpeed(),target.miningSpeed())),
                    Objects.requireNonNull(harvestLevels.get(entry.getKey()),"Unknown equipment tier: "+entry.getKey()),
                    Math.max(1,(int)Math.round(scale(row.durability(),apex.durability(),target.durability())))));
        }
        diagnostics.put("equipment_apex_parity",1.0);
        diagnostics.put("ranged_family_observed",ranged.familyObserved()?1.0:0.0);
        diagnostics.put("caster_family_observed",caster.familyObserved()?1.0:0.0);
    }
    /** Unlock one level per infusion until the pack ceiling. Larger ladders spread
     * their levels across all infusions, with integer division rounding down. */
    static int harvestLevel(EquipmentTier tier,int packMaximum) {
        if(packMaximum<0||packMaximum>32)
            throw new IllegalArgumentException("Unsupported pack harvest level: "+packMaximum);
        int infusions=EquipmentTier.values().length-1;
        return Math.min(packMaximum,tier.ordinal()*Math.max(infusions,packMaximum)/infusions);
    }
    /** A derived caster has no observed cadence to preserve. Resolve its whole-hit/tenth-rate
     * pair together, so rounding cannot discard a sizeable share of its borrowed DPS budget. */
    private static RuntimeReferencePolicy.Weapon representableFallback(RuntimeReferencePolicy.Weapon target) {
        RuntimeReferencePolicy.Weapon best=null;double bestError=Double.POSITIVE_INFINITY,bestDistance=Double.POSITIVE_INFINITY;
        for(double hit:new double[]{Math.max(1,Math.floor(target.damage())),Math.max(1,Math.ceil(target.damage()))}) {
            double idealRate=target.dps()/hit;
            for(var mode:List.of(java.math.RoundingMode.FLOOR,java.math.RoundingMode.CEILING)) {
                double rate=Math.max(.1,java.math.BigDecimal.valueOf(idealRate).setScale(1,mode).doubleValue());
                double error=Math.abs(hit*rate-target.dps());
                double distance=Math.abs(hit/target.damage()-1)+Math.abs(rate/target.rate()-1);
                if(error<bestError-1e-9||(Math.abs(error-bestError)<=1e-9&&distance<bestDistance)) {
                    best=new RuntimeReferencePolicy.Weapon(hit,rate,target.burst(),false);bestError=error;bestDistance=distance;
                }
            }
        }
        return Objects.requireNonNull(best);
    }
    static double scale(double value,double oldApex,double target) {
        if(value==oldApex)return target;
        return value*factor(oldApex,target);
    }
    private static double factor(double oldApex,double target) {
        if(oldApex==0) {
            if(target!=0)throw new IllegalArgumentException("Cannot normalize an absent equipment curve to a positive reference");
            return 1;
        }
        return target/oldApex;
    }
}
