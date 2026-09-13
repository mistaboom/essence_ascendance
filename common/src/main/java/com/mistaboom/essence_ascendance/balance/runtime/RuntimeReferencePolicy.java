package com.mistaboom.essence_ascendance.balance.runtime;

import com.mistaboom.essence_ascendance.balance.engine.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import java.util.*;

/** Same-axis, already-attainable evidence only. Missing observations never invent stronger later gear. */
final class RuntimeReferencePolicy {
    private RuntimeReferencePolicy() {}
    static final double LATENT_CAPABILITY_RATIO = .7;
    static final double LATENT_DURABILITY_RATIO = .6;
    static final double CASTER_HIT_RATIO = .6;
    static final double ENCOUNTER_OFFENSE_ELASTICITY = .25;
    static final double ENCOUNTER_DEFENSE_ELASTICITY = .20;
    static final double MAX_OFFENSE_PRESSURE_RATIO = 4;
    static final double MAX_DEFENSE_PRESSURE_RATIO = 8;
    static final double SHIELD_INNATE_REFLECTION_RATIO = .10;
    static final double SHIELD_TIER_REFLECTION_GROWTH_RATIO = .5;
    static final double SHIELD_BLOCK_HEADROOM_RATIO = 4;
    /* NeoForge adds deferred holders (including swim_speed) to LivingEntity.createLivingAttributes.
     * Its full Player factory cannot run from the mod constructor before those holders bind.
     * Restrict the vanilla fallback to the disposable pre-world placeholder; real generation still
     * reads the complete registered Player supplier and never caches these provisional references. */
    private static final ThreadLocal<Boolean> BOOTSTRAP_REFERENCES = new ThreadLocal<>();
    // Player.createAttributes explicitly overrides attack damage to 1, unlike the Attribute default.
    private static final double BOOTSTRAP_PLAYER_HIT = 1.0;
    static boolean usingBootstrapReferences() { return Boolean.TRUE.equals(BOOTSTRAP_REFERENCES.get()); }
    static <T> T withBootstrapReferences(java.util.function.Supplier<T> work) {
        boolean nested = usingBootstrapReferences();
        BOOTSTRAP_REFERENCES.set(true);
        try { return work.get(); }
        finally { if (nested) BOOTSTRAP_REFERENCES.set(true); else BOOTSTRAP_REFERENCES.remove(); }
    }
    static double playerHealth() { return usingBootstrapReferences() ? Attributes.MAX_HEALTH.value().getDefaultValue()
            : Player.createAttributes().build().getValue(Attributes.MAX_HEALTH); }
    static double playerHit() { return usingBootstrapReferences() ? BOOTSTRAP_PLAYER_HIT
            : Player.createAttributes().build().getValue(Attributes.ATTACK_DAMAGE); }
    static double playerRate() { return usingBootstrapReferences() ? Attributes.ATTACK_SPEED.value().getDefaultValue()
            : Player.createAttributes().build().getValue(Attributes.ATTACK_SPEED); }
    static double observed(PackEvidence evidence, ProgressionBand band, CapabilityAxis axis, double absent) {
        for (int i=band.ordinal(); i>=0; i--) {
            Double value=evidence.frontiers().getOrDefault(ProgressionBand.at(i),Map.of()).get(axis);
            if(value!=null && Double.isFinite(value) && value>=0)return normalizeMeasurement(value);
        }
        return absent;
    }
    static double required(PackEvidence evidence, ProgressionBand band, CapabilityAxis axis) {
        double result=observed(evidence,band,axis,Double.NaN);
        if(!Double.isFinite(result)||result<=0)
            throw new IllegalArgumentException("Missing attainable " + axis + " reference for " + band
                    + "; correct acquisition/measurement evidence or supply a balance provider before rebuilding");
        return result;
    }
    static double rangedRate(PackEvidence evidence, ProgressionBand band, double matchedWeaponRate,String outlierPolicy) {
        var ranged=evidence.equipment().stream().filter(r->r.slot().contains("bow")||r.slot().contains("ranged")).toList();
        double rate=RobustFrontiers.build(ranged,outlierPolicy).getOrDefault(band,Map.of()).getOrDefault(CapabilityAxis.ATTACK_RATE,0.0);
        return rate>0?normalizeMeasurement(rate):matchedWeaponRate;
    }
    record Weapon(double damage, double rate, double burst, boolean familyObserved) {
        double dps() { return damage*rate; }
    }
    /** Select a sustained-damage winner and its own cadence within one equipment family. */
    static Weapon weapon(PackEvidence evidence, ProgressionBand band, String family, String outlierPolicy) {
        var references=evidence.equipment().stream().filter(r->switch(family) {
            case "ranged" -> r.slot().equals("mainhand_bow")||r.slot().equals("mainhand_crossbow");
            case "caster" -> r.slot().equals("mainhand_caster");
            default -> r.slot().equals("mainhand_melee")||r.slot().equals("mainhand_tool");
        }).toList();
        var axes=RobustFrontiers.build(references,outlierPolicy).getOrDefault(band,Map.of());
        double dps=axes.getOrDefault(CapabilityAxis.SUSTAINED_DAMAGE,0.0);
        double rate=axes.getOrDefault(CapabilityAxis.ATTACK_RATE,0.0);
        if(dps>0&&rate>0)return new Weapon(normalizeMeasurement(dps/rate),normalizeMeasurement(rate),
                normalizeMeasurement(Math.max(dps/rate,axes.getOrDefault(CapabilityAxis.BURST_DAMAGE,0.0))),true);
        if(family.equals("caster")) {
            var ranged=weapon(evidence,band,"ranged",outlierPolicy);
            return new Weapon(ranged.damage()*CASTER_HIT_RATIO,ranged.rate()/CASTER_HIT_RATIO,
                    ranged.burst()*CASTER_HIT_RATIO,false);
        }
        if(family.equals("ranged")) {
            var melee=weapon(evidence,band,"melee_shield",outlierPolicy);
            return new Weapon(melee.damage(),melee.rate(),melee.burst(),false);
        }
        // Synthetic/provider-only evidence and the pre-world placeholder can supply a frontier
        // without item rows. Keep the original paired raw observation before removing float noise.
        for(int index=band.ordinal();index>=0;index--) {
            axes=evidence.frontiers().getOrDefault(ProgressionBand.at(index),Map.of());
            dps=axes.getOrDefault(CapabilityAxis.SUSTAINED_DAMAGE,0.0);
            rate=axes.getOrDefault(CapabilityAxis.ATTACK_RATE,0.0);
            if(dps>0&&rate>0)return new Weapon(normalizeMeasurement(dps/rate),normalizeMeasurement(rate),
                    normalizeMeasurement(Math.max(dps/rate,axes.getOrDefault(CapabilityAxis.BURST_DAMAGE,0.0))),false);
        }
        throw new IllegalArgumentException("Missing attainable weapon reference for "+band);
    }
    record Armor(double armor,double toughness) {
        double protection(double incoming) {
            return Math.min(20,Math.max(armor/5,armor-incoming/(2+toughness/4)));
        }
    }
    /** Choose a physically wearable set, retaining each item's armor/toughness pair. */
    static Armor armor(PackEvidence evidence, ProgressionBand band, String outlierPolicy, double incoming) {
        List<Armor> sets=List.of(new Armor(0,0));boolean observed=false;
        for(String slot:List.of("head","chest","legs","feet")) {
            List<Armor> choices=new ArrayList<>();
            for(var stage:ProgressionBand.values()) {
                if(stage.ordinal()>band.ordinal())continue;
                var rows=evidence.equipment().stream().filter(r->r.included()&&r.reachable()&&r.stage()==stage&&r.slot().equals(slot)).toList();
                double armorCeiling=RobustFrontiers.percentile(rows.stream().map(r->r.axes().getOrDefault(CapabilityAxis.ARMOR,0.0)).filter(v->v>0).toList(),1,outlierPolicy);
                double toughnessCeiling=RobustFrontiers.percentile(rows.stream().map(r->r.axes().getOrDefault(CapabilityAxis.TOUGHNESS,0.0)).filter(v->v>0).toList(),1,outlierPolicy);
                for(var row:rows) {
                    double armor=row.axes().getOrDefault(CapabilityAxis.ARMOR,0.0),toughness=row.axes().getOrDefault(CapabilityAxis.TOUGHNESS,0.0);
                    if(outlierPolicy.equals("EXCLUDE_UNSUPPORTED")&&(armor>armorCeiling||toughness>toughnessCeiling))continue;
                    if(outlierPolicy.equals("WINSORIZE")){armor=Math.min(armor,armorCeiling);toughness=Math.min(toughness,toughnessCeiling);}
                    choices.add(new Armor(normalizeMeasurement(armor),normalizeMeasurement(toughness)));observed=true;
                }
            }
            if(choices.isEmpty())continue;
            List<Armor> combined=new ArrayList<>();
            for(var set:sets)for(var choice:pareto(choices))combined.add(new Armor(set.armor()+choice.armor(),set.toughness()+choice.toughness()));
            sets=pareto(combined);
        }
        if(!observed)return new Armor(observed(evidence,band,CapabilityAxis.ARMOR,0),observed(evidence,band,CapabilityAxis.TOUGHNESS,0));
        return sets.stream().max(Comparator.comparingDouble((Armor set)->set.protection(incoming))
                .thenComparingDouble(Armor::armor).thenComparingDouble(Armor::toughness)).orElseThrow();
    }
    private static List<Armor> pareto(List<Armor> values) {
        List<Armor> result=new ArrayList<>();double highestToughness=-1;
        for(var value:values.stream().distinct().sorted(Comparator.comparingDouble(Armor::armor).reversed()
                .thenComparing(Comparator.comparingDouble(Armor::toughness).reversed())).toList()) {
            if(value.toughness()>highestToughness){result.add(value);highestToughness=value.toughness();}
        }
        return result;
    }
    /** Attribute-component float noise is not an intentional one-tenth cadence reduction. */
    private static double normalizeMeasurement(double value) {
        return java.math.BigDecimal.valueOf(value).setScale(6,java.math.RoundingMode.HALF_UP).doubleValue();
    }
    /** Temporary pre-world/network placeholder only; never called to fill gaps in a real pack scan. */
    static PackEvidence bootstrapEvidence() {
        Map<CapabilityAxis,Double> player=Map.of(CapabilityAxis.ARMOR,0.0,CapabilityAxis.TOUGHNESS,0.0,
                CapabilityAxis.ATTACK_RATE,playerRate(),
                CapabilityAxis.SUSTAINED_DAMAGE,playerHit()*playerRate(),
                CapabilityAxis.BURST_DAMAGE,playerHit(),CapabilityAxis.MINING_SPEED,1.0,
                CapabilityAxis.DURABILITY,1.0,CapabilityAxis.HARVEST_LEVEL,0.0);
        Map<ProgressionBand,Map<CapabilityAxis,Double>> bands=new EnumMap<>(ProgressionBand.class);
        for(var band:ProgressionBand.values())bands.put(band,player);
        return new PackEvidence(Map.of(),List.of(),List.of(),bands,List.of(),
                List.of("Temporary vanilla player-baseline bootstrap before loader attributes bind; not a generated pack reference"),Map.of("bootstrap_placeholder",1L));
    }
}
