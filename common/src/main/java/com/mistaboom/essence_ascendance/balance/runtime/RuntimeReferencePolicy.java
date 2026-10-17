package com.mistaboom.essence_ascendance.balance.runtime;

import com.mistaboom.essence_ascendance.balance.engine.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import java.util.*;

/** Same-axis native measurements and explicitly labeled catalog estimates. */
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
            throw new IllegalArgumentException("Missing measured catalog " + axis + " reference for " + band
                    + "; correct registered measurements or explicit balance inputs before rebuilding");
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
        boolean meleeObserved = evidence.equipment().stream().anyMatch(r -> r.usableReference()
                && r.slot().equals("mainhand_melee") && r.axes().getOrDefault(CapabilityAxis.SUSTAINED_DAMAGE, 0.0) > 0);
        var references=evidence.equipment().stream().filter(r->switch(family) {
            case "ranged" -> r.slot().equals("mainhand_bow")||r.slot().equals("mainhand_crossbow");
            case "caster" -> r.slot().equals("mainhand_caster");
            default -> r.slot().equals(meleeObserved ? "mainhand_melee" : "mainhand_tool");
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
        throw new IllegalArgumentException("Missing measured catalog weapon reference for "+band);
    }
    record Armor(NativeAttributeContribution armorModifiers, NativeAttributeContribution toughnessModifiers,
                 NativeAttributeContribution healthModifiers) {
        Armor(double armor, double toughness) { this(additiveReference("fallback/armor", armor),
                additiveReference("fallback/toughness", toughness), NativeAttributeContribution.EMPTY); }
        double armor() { return Attributes.ARMOR.value().sanitizeValue(armorModifiers.apply(0)); }
        double toughness() { return Attributes.ARMOR_TOUGHNESS.value().sanitizeValue(toughnessModifiers.apply(0)); }
        double protection(double incoming) {
            return Math.min(20,Math.max(armor()/5,armor()-incoming/(2+toughness()/4)));
        }
        double health() { return Attributes.MAX_HEALTH.value().sanitizeValue(healthModifiers.apply(playerHealth())); }
        double effectiveHealth(double incoming) { return health() / (1 - protection(incoming) / 25); }
    }
    /** Choose a physically wearable set, retaining each item's armor/toughness pair. */
    static Armor armor(PackEvidence evidence, ProgressionBand band, String outlierPolicy, double incoming) {
        return armorSearch(evidence, band, outlierPolicy, incoming, false).candidates().stream().max(Comparator.comparingDouble((Armor set)->set.protection(incoming))
                .thenComparingDouble(Armor::armor).thenComparingDouble(Armor::toughness)).orElseThrow();
    }
    /** Health modifiers share the same actual outfit as its armor; the native player base is applied once. */
    static Armor survival(PackEvidence evidence, ProgressionBand band, String outlierPolicy, double incoming) {
        return armorSearch(evidence, band, outlierPolicy, incoming, true).candidates().stream().max(Comparator.comparingDouble((Armor set)->set.effectiveHealth(incoming))
                .thenComparingDouble(Armor::health).thenComparingDouble(Armor::armor).thenComparingDouble(Armor::toughness)).orElseThrow();
    }
    static final int ARMOR_BEAM_WIDTH = 64;
    static final int ARMOR_SEARCH_WORK_LIMIT = 4 * ARMOR_BEAM_WIDTH * ARMOR_BEAM_WIDTH;
    record ArmorSearch(List<Armor> candidates, int combinations) { }

    /** Bounded catalog estimate: keep at most 64 goal-ranked choices/partial outfits at every slot.
     * Native modifier arithmetic remains exact for retained compatible outfits; unusual cross-slot
     * synergies may lie outside this deterministic shortlist. No exhaustive-optimum claim is made. */
    static ArmorSearch armorSearch(PackEvidence evidence, ProgressionBand band, String outlierPolicy,
                                   double incoming, boolean survival) {
        List<Armor> sets=List.of(new Armor(0,0));boolean observed=false;
        int combinations = 0;
        Comparator<Armor> goal = Comparator.comparingDouble((Armor set) -> survival ? set.effectiveHealth(incoming) : set.protection(incoming))
                .thenComparingDouble(Armor::health).thenComparingDouble(Armor::armor).thenComparingDouble(Armor::toughness)
                .thenComparing(Armor::toString);
        var ceilings = RobustFrontiers.equipmentCeilings(evidence.equipment(), outlierPolicy);
        // Native equipment-slot order is feet, legs, chest, head. Duplicate modifier IDs have one owner.
        for(String slot:List.of("feet","legs","chest","head")) {
            List<Armor> choices=new ArrayList<>();
            for(var stage:ProgressionBand.values()) {
                if(stage.ordinal()>band.ordinal())continue;
                var rows=evidence.equipment().stream().filter(r->r.usableReference()&&r.stage()==stage&&r.slot().equals(slot)).toList();
                double armorCeiling=ceilings.getOrDefault(slot,Map.of()).getOrDefault(CapabilityAxis.ARMOR,0.0);
                double toughnessCeiling=ceilings.getOrDefault(slot,Map.of()).getOrDefault(CapabilityAxis.TOUGHNESS,0.0);
                double healthCeiling=ceilings.getOrDefault(slot,Map.of()).getOrDefault(CapabilityAxis.MAX_HEALTH,Double.POSITIVE_INFINITY);
                for(var row:rows) {
                    double armor=row.axes().getOrDefault(CapabilityAxis.ARMOR,0.0),toughness=row.axes().getOrDefault(CapabilityAxis.TOUGHNESS,0.0);
                    double health=row.axes().getOrDefault(CapabilityAxis.MAX_HEALTH,
                            row.attributeModifiers().getOrDefault(attributeKey(Attributes.MAX_HEALTH),NativeAttributeContribution.EMPTY).apply(playerHealth()));
                    if(outlierPolicy.equals("EXCLUDE_UNSUPPORTED")&&(armor>armorCeiling||toughness>toughnessCeiling||health>healthCeiling))continue;
                    choices.add(new Armor(
                            referenceContribution(row,CapabilityAxis.ARMOR,Attributes.ARMOR,0,armor,armorCeiling,outlierPolicy),
                            referenceContribution(row,CapabilityAxis.TOUGHNESS,Attributes.ARMOR_TOUGHNESS,0,toughness,toughnessCeiling,outlierPolicy),
                            referenceContribution(row,CapabilityAxis.MAX_HEALTH,Attributes.MAX_HEALTH,playerHealth(),health,healthCeiling,outlierPolicy)));
                    observed=true;
                }
            }
            if(choices.isEmpty())continue;
            choices.add(new Armor(0, 0)); // Wearing a negative modifier is optional.
            var shortlist = shortlist(choices, goal);
            List<Armor> combined=new ArrayList<>();
            for(var set:sets)for(var choice:shortlist) {
                combinations++;
                combined.add(new Armor(set.armorModifiers().combine(choice.armorModifiers()),set.toughnessModifiers().combine(choice.toughnessModifiers()),
                        set.healthModifiers().combine(choice.healthModifiers())));
            }
            sets=shortlist(combined, goal);
        }
        if(!observed)return new ArmorSearch(List.of(new Armor(observed(evidence,band,CapabilityAxis.ARMOR,0),
                observed(evidence,band,CapabilityAxis.TOUGHNESS,0))), combinations);
        return new ArmorSearch(sets, combinations);
    }
    private static List<Armor> shortlist(List<Armor> candidates, Comparator<Armor> goal) {
        return candidates.stream().distinct().sorted(goal.reversed()).limit(ARMOR_BEAM_WIDTH).toList();
    }
    private static String attributeKey(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute) {
        return attribute.unwrapKey().orElseThrow().location().toString();
    }
    private static NativeAttributeContribution referenceContribution(EquipmentReference row, CapabilityAxis axis,
            net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double base,
            double measured, double ceiling, String policy) {
        // Winsorization is explicitly a capped estimate, not an actual native item witness.
        // Only clipped axes replace their native bundle; every other axis retains exact IDs/operations.
        if(policy.equals("WINSORIZE")&&measured>ceiling)
            return additiveReference("winsorized/"+row.itemId()+"/"+axis,normalizeMeasurement(ceiling)-base);
        var nativeModifiers=row.attributeModifiers().get(attributeKey(attribute));
        return nativeModifiers!=null?nativeModifiers:additiveReference("scalar/"+row.itemId()+"/"+axis,normalizeMeasurement(measured)-base);
    }
    private static NativeAttributeContribution additiveReference(String identity,double amount) {
        return amount==0?NativeAttributeContribution.EMPTY:new NativeAttributeContribution(Map.of(identity,
                new NativeAttributeContribution.Modifier(amount,net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE)));
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
