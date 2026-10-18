package com.mistaboom.essence_ascendance.balance.runtime;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.balance.config.BalanceSettings;
import com.mistaboom.essence_ascendance.balance.engine.*;
import com.mistaboom.essence_ascendance.equipment.*;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Fits every physical curve between the native iron-era chassis and the pack endpoint. */
final class RuntimeEquipmentNormalization {
    private RuntimeEquipmentNormalization() {}
    /** Vanilla iron is an explicit starting policy, not inferred acquisition evidence.
     * Bow damage is the native full-draw core (speed 3 * arrow damage 2). */
    static EquipmentBaselineConfig.TierBaseline nativeStart() {
        var iron = net.minecraft.world.item.Tiers.IRON;
        var caster = RuntimeReferencePolicy.casterFromBow(new RuntimeReferencePolicy.Weapon(6, 1, 6, true));
        return new EquipmentBaselineConfig.TierBaseline(15, 0, 4 + iron.getAttackDamageBonus(), 1.6,
                6, 1, caster.damage(), caster.rate(), iron.getSpeed(), 2, iron.getUses());
    }
    static void apply(Map<ResourceLocation,EquipmentBaselineConfig.TierBaseline> equipment,
                      PackEvidence evidence, BalanceSettings settings, Map<String,Double> diagnostics) {
        var apex=Objects.requireNonNull(equipment.get(AscendanceTiers.TRANSCENDENT.id()));
        var latent=Objects.requireNonNull(equipment.get(AscendanceTiers.LATENT.id()));
        var start=nativeStart();
        String policy=settings.outlierPolicy().name();
        var melee=RuntimeReferencePolicy.weapon(evidence,ProgressionBand.APEX,"melee_shield",policy);
        var ranged=RuntimeReferencePolicy.weapon(evidence,ProgressionBand.APEX,"ranged",policy);
        var caster=RuntimeReferencePolicy.weapon(evidence,ProgressionBand.APEX,"caster",policy);
        var armor=RuntimeReferencePolicy.armor(evidence,ProgressionBand.APEX,policy,
                diagnostics.getOrDefault("enemy_damage_apex",RuntimeReferencePolicy.playerHit()));
        double harvest=RuntimeReferencePolicy.observed(evidence,ProgressionBand.APEX,CapabilityAxis.HARVEST_LEVEL,0);
        if(harvest!=Math.rint(harvest)||harvest>32)
            throw new IllegalArgumentException("Pack harvest reference exceeds the supported discrete level domain: "+harvest);
        long durability=Math.round(RuntimeReferencePolicy.required(evidence,ProgressionBand.APEX,CapabilityAxis.DURABILITY));
        if(durability<=0||durability>Integer.MAX_VALUE/64)
            throw new IllegalArgumentException("Pack durability reference exceeds supported equipment arithmetic: "+durability);
        var targetBow=new RuntimeReferencePolicy.Weapon(Math.max(start.rangedDamage(),ranged.damage()),
                Math.max(start.rangedAttackSpeed(),ranged.rate()),ranged.burst(),ranged.familyObserved());
        var targetCaster=RuntimeReferencePolicy.casterFromBow(targetBow);
        var observed=new EquipmentBaselineConfig.TierBaseline(armor.armor(),armor.toughness(),melee.damage(),melee.rate(),
                ranged.damage(),ranged.rate(),caster.damage(),caster.rate(),
                RuntimeReferencePolicy.required(evidence,ProgressionBand.APEX,CapabilityAxis.MINING_SPEED),Math.max(2,(int)harvest),(int)durability);
        var target=new EquipmentBaselineConfig.TierBaseline(Math.max(start.fullSetArmor(),armor.armor()),armor.toughness(),
                Math.max(start.meleeDamage(),melee.damage()),Math.max(start.meleeAttackSpeed(),melee.rate()),
                targetBow.damage(),targetBow.rate(),targetCaster.damage(),targetCaster.rate(),
                Math.max(start.miningSpeed(),observed.miningSpeed()),observed.harvestLevel(),Math.max(start.durability(),(int)durability));
        for(var property:EquipmentBaselineProperty.values()) {
            String name=property.name().toLowerCase(Locale.ROOT);
            diagnostics.put("equipment_latent_benchmark_"+name,start.value(property));
            diagnostics.put("equipment_apex_observed_"+name,observed.value(property));
            diagnostics.put("equipment_apex_reference_"+name,target.value(property));
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
            double tierPosition=com.mistaboom.essence_ascendance.tier.AscendanceTierRegistry.get(entry.getKey()).orElseThrow().order()
                    /(double)AscendanceTiers.TRANSCENDENT.order();
            var values=new EnumMap<EquipmentBaselineProperty,Double>(EquipmentBaselineProperty.class);
            for(var property:EquipmentBaselineProperty.values()) values.put(property,interpolate(row.value(property),
                    latent.value(property),apex.value(property),start.value(property),target.value(property),tierPosition));
            // The caster's identity follows the corresponding bow at every tier, not a second catalog curve.
            var bowCaster=RuntimeReferencePolicy.casterFromBow(new RuntimeReferencePolicy.Weapon(
                    values.get(EquipmentBaselineProperty.RANGED_DAMAGE),values.get(EquipmentBaselineProperty.RANGED_ATTACK_SPEED),0,false));
            entry.setValue(new EquipmentBaselineConfig.TierBaseline(
                    values.get(EquipmentBaselineProperty.ARMOR),values.get(EquipmentBaselineProperty.TOUGHNESS),
                    values.get(EquipmentBaselineProperty.MELEE_DAMAGE),values.get(EquipmentBaselineProperty.MELEE_ATTACK_SPEED),
                    values.get(EquipmentBaselineProperty.RANGED_DAMAGE),values.get(EquipmentBaselineProperty.RANGED_ATTACK_SPEED),
                    bowCaster.damage(),bowCaster.rate(),values.get(EquipmentBaselineProperty.MINING_SPEED),
                    Objects.requireNonNull(harvestLevels.get(entry.getKey()),"Unknown equipment tier: "+entry.getKey()),
                    (int)Math.round(values.get(EquipmentBaselineProperty.DURABILITY))));
        }
        diagnostics.put("equipment_apex_parity",1.0);
        diagnostics.put("ranged_family_observed",ranged.familyObserved()?1.0:0.0);
        diagnostics.put("caster_family_observed",caster.familyObserved()?1.0:0.0);
    }
    /** Keep the pack's infusion ladder, with iron access as its minimum.
     * Larger ladders spread levels across all infusions, rounding down. */
    static int harvestLevel(EquipmentTier tier,int packMaximum) {
        if(packMaximum<0||packMaximum>32)
            throw new IllegalArgumentException("Unsupported pack harvest level: "+packMaximum);
        int infusions=EquipmentTier.values().length-1;
        return Math.max(EquipmentBaselineConfig.MINIMUM_HARVEST_LEVEL,
                Math.min(packMaximum,tier.ordinal()*Math.max(infusions,packMaximum)/infusions));
    }
    static double interpolate(double value,double oldStart,double oldEnd,double start,double end,double tierPosition) {
        double position=oldEnd>oldStart?Math.clamp((value-oldStart)/(oldEnd-oldStart),0,1):tierPosition;
        return start+(Math.max(start,end)-start)*position;
    }
}
