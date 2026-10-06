package com.mistaboom.essence_ascendance.balance.runtime;

import com.google.gson.*;
import com.mistaboom.essence_ascendance.balance.config.*;
import com.mistaboom.essence_ascendance.balance.engine.*;
import com.mistaboom.essence_ascendance.balance.economy.*;
import com.mistaboom.essence_ascendance.balance.generated.*;
import com.mistaboom.essence_ascendance.essence.*;
import com.mistaboom.essence_ascendance.equipment.*;
import com.mistaboom.essence_ascendance.progression.*;
import com.mistaboom.essence_ascendance.skill.*;
import com.mistaboom.essence_ascendance.stat.*;
import com.mistaboom.essence_ascendance.tier.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import java.util.*;
import java.nio.file.*;
import static com.mistaboom.essence_ascendance.balance.engine.CapabilityAxis.*;
import static com.mistaboom.essence_ascendance.balance.engine.CapabilityEvidence.*;

/** Reusable generic environments A-M exercise the production generator and final native rank values. */
public final class AdaptiveCompetitionCalibrationTest {
    private static int checks;
    private static PackEvidence evidence;
    private static final Map<String, JsonObject> reports = new TreeMap<>();
    private static final Map<String, JsonObject> runtimes = new TreeMap<>();
    public static Functional fact(String id, ProgressionBand band, CapabilityAxis axis, double magnitude, String unit) {
        var m = CompetitiveCapabilities.measurement(axis, magnitude, unit, "supported synthetic operation",
                Scope.self(), Operation.manual(), "synthetic", Origin.TYPED_ADAPTER, List.of());
        return new Functional(new CapabilityEvidence(id, band, Map.of(), true, .9, "Attainable synthetic configuration"),
                "configured", List.of(m), List.of(), true);
    }
    public static JsonObject environment(Functional... facts) {
        CapabilitySink sink = new CapabilitySink(); for (var fact : facts) sink.add(fact);
        return CompetitiveCapabilities.report(sink, "WINSORIZE");
    }
    private static RuntimeBalanceDefinition generate(String name, JsonObject capabilities, EconomyProfile economy) {
        var policy = new AdaptiveCompetitionCalibration(evidence, capabilities);
        var runtime = RuntimeBalanceGenerator.generate(evidence, economy, BalanceSettings.defaults(), BalanceOverrides.empty(), policy);
        runtime.validate(); com.mistaboom.essence_ascendance.skill.balance.SkillBalanceGenerator.validatePublished(runtime.config().skillEffects(), runtime.skillCurves());
        if (runtime.generationAnalysis() != null) runtime.generationAnalysis().requireSafe();
        reports.put(name, policy.complete(runtime)); runtimes.put(name, runtime.toJson());
        check(runtime.toJson().equals(RuntimeBalanceDefinition.fromJson(runtime.toJson()).toJson()), name + " runtime roundtrip");
        return runtime;
    }
    private static void operatingRestrictions(RuntimeBalanceDefinition baseline) {
        var pearl = new AdaptiveCompetitionCalibration(evidence, environment(fact("synthetic:pearl", ProgressionBand.ENTRY, TELEPORTATION, 1, "presence")));
        check(pearl.skillTiers().get(SkillIds.FATIGUE_FLIGHT).equals(baseline.skillCurves().get(SkillIds.FATIGUE_FLIGHT.toString()).requiredTierId()),
                "Consumable teleportation cannot establish flight availability");
        var source = fact("synthetic:timed_growth", ProgressionBand.ENTRY, CROP_ACCELERATION, 8, "multiplier");
        var measurement = source.measurements().getFirst();
        var continuous = new Operation(Automation.NONE, Activity.PLAYER_ACTIVE, Renewal.RENEWABLE, null, 10.0, null, 10.0, List.of("one reagent"), List.of());
        var limited = new Operation(Automation.NONE, Activity.PLAYER_ACTIVE, Renewal.RENEWABLE, null, 1.0, null, 10.0, List.of("one reagent"), List.of());
        check(Operation.manual().uptimeBound() == null && continuous.uptimeBound() == 1 && limited.uptimeBound() == .1,
                "Only measured timing establishes a duty bound; unknown cadence stays unknown");
        var policies = new ArrayList<AdaptiveCompetitionCalibration>();
        for (var operation : List.of(continuous, limited)) {
            var m = new Measurement(measurement.family(), measurement.axis(), measurement.magnitude(), measurement.unit(), measurement.applicability(),
                    measurement.scope(), operation, measurement.provider(), measurement.origin(), measurement.unsupported());
            policies.add(new AdaptiveCompetitionCalibration(evidence, environment(new Functional(source.source(), source.configuration(), List.of(m), source.acquisition(), true))));
        }
        check(policies.getFirst().factor("skill.verdant_stride", ProgressionBand.ENTRY) > policies.getLast().factor("skill.verdant_stride", ProgressionBand.ENTRY),
                "Captured cooldown/duration restrict competitive strength");
        check(policies.getFirst().skillTiers().equals(policies.getLast().skillTiers()), "Timing restrictions must not erase proven functional access");
    }
    private static double value(RuntimeBalanceDefinition runtime, String path) { return ProgressionRequirements.read(runtime.toJson(), path); }
    private static void responds(RuntimeBalanceDefinition baseline, RuntimeBalanceDefinition adapted, String path) {
        check(value(adapted, path) > value(baseline, path) * 1.05, "Meaningful response missing at " + path);
    }
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        Thread.currentThread().setUncaughtExceptionHandler((t,e) -> e.printStackTrace(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.err))));
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); EssenceTypes.init(); EssenceStats.init(); AscendanceTiers.init();
        MilestoneProviders.init(); Milestones.init(); Skills.init(); AscendanceAdvancements.init(); EquipmentProfiles.init();
        evidence = RuntimeReferencePolicy.bootstrapEvidence();
        var vanilla = generate("A-vanilla", environment(), null);
        operatingRestrictions(vanilla);
        var earlyFlight = generate("early-functional-flight", environment(fact("synthetic:starter_flight", ProgressionBand.ENTRY, FLIGHT, 1, "presence")), null);
        check(earlyFlight.skillCurves().get(SkillIds.FATIGUE_FLIGHT.toString()).requiredTierId().equals(AscendanceTiers.AWAKENED.id()), "Early flight must retain an earlier Impact Control prerequisite");
        check(earlyFlight.skillCurves().get(SkillIds.FATIGUE_FLIGHT.toString()).ranks().getFirst().cost()
                < vanilla.skillCurves().get(SkillIds.FATIGUE_FLIGHT.toString()).ranks().getFirst().cost(), "Earlier flight did not use its access-tier price");
        var boneGrowth = generate("native-bonemeal-growth", environment(fact("synthetic:free_growth", ProgressionBand.ENTRY, CROP_ACCELERATION, .5, "bonemeal_growth_chance_per_action")), null);
        check(boneGrowth.skillCurves().get(SkillIds.VERDANT_STRIDE.toString()).requiredTierId().equals(AscendanceTiers.DORMANT.id()), "Free growth did not move Verdant access");
        var verdant = boneGrowth.config().skillEffects().gathering().verdantStride();
        check(verdant.usesBoneMealGrowth() && verdant.growthPulseTicks() == 20 && verdant.growthChance() >= .5, "Native growth identity/chance/bounded scenario was lost");
        check(!vanilla.config().skillEffects().gathering().verdantStride().usesBoneMealGrowth(), "Neutral environment fabricated bone-meal competition");
        var bottlePolicy = new AdaptiveCompetitionCalibration(evidence, environment(fact("synthetic:charged_accelerator", ProgressionBand.EARLY, BLOCK_ENTITY_ACCELERATION, 128, "extra_tick_calls_per_server_tick")));
        check(bottlePolicy.factor("skill.industrious_presence", ProgressionBand.EARLY) > 1, "Extra tick units were ignored");
        check(bottlePolicy.skillTiers().get(SkillIds.INDUSTRIOUS_PRESENCE).equals(AscendanceTiers.AWAKENED.id()), "Accelerator access did not move industry tier");
        var ordinaryDamage = new AdaptiveCompetitionCalibration(evidence, environment(fact("synthetic:ordinary_weapon", ProgressionBand.ENTRY, MELEE_DAMAGE, 10000, "health_points")));
        check(ordinaryDamage.skillTiers().size() == SkillRegistry.size(), "Some registered skills bypass availability decisions");
        check(ordinaryDamage.skillTiers().get(SkillIds.FRENZY).equals(vanilla.skillCurves().get(SkillIds.FRENZY.toString()).requiredTierId()), "Ordinary damage fabricated distinct skill access");
        var lateFlight = new AdaptiveCompetitionCalibration(evidence, environment(fact("synthetic:late_flight", ProgressionBand.APEX, FLIGHT, 1, "presence")));
        check(lateFlight.skillTiers().get(SkillIds.FATIGUE_FLIGHT).equals(vanilla.skillCurves().get(SkillIds.FATIGUE_FLIGHT.toString()).requiredTierId()), "Late flight moved early access");
        availabilityDiagnostics(vanilla);
        var protectedItem = fact("synthetic:protected_chest", ProgressionBand.ENTRY, INDESTRUCTIBILITY, 1, "presence");
        var protection = CompetitiveCapabilities.measurement(INDESTRUCTIBILITY, 1.0, "presence", "equipment slot=chest; source-bound native wear pool",
                new Scope("source_equipment:synthetic:protected_chest", "single", null, 1.0), Operation.manual(), "synthetic", Origin.NATIVE, List.of());
        var protectedPolicy = new AdaptiveCompetitionCalibration(evidence, environment(new Functional(protectedItem.source(), "protected", List.of(protection), List.of(), true)));
        check(protectedPolicy.factor("bonus.durability_efficiency", ProgressionBand.ENTRY) == 1
                && protectedPolicy.factor("skill.restful_mending", ProgressionBand.ENTRY) == 1, "One protected item contaminated general wear/repair");
        var passiveSpawns = CompetitiveCapabilities.measurement(SPAWN_SUPPRESSION, 64.0, "cube_radius_blocks", "natural spawn filter; affected_monsters=false",
                Scope.unknown(), Operation.manual(), "synthetic", Origin.TYPED_ADAPTER, List.of());
        var spawnFact = fact("synthetic:passive_filter", ProgressionBand.ENTRY, SPAWN_SUPPRESSION, 64, "cube_radius_blocks");
        var passivePolicy = new AdaptiveCompetitionCalibration(evidence, environment(new Functional(spawnFact.source(), "passive", List.of(passiveSpawns), List.of(), true)));
        check(passivePolicy.factor("skill.sanctuary", ProgressionBand.APEX) == 1, "Passive spawn control fabricated hostile protection");
        check(vanilla.toJson().equals(RuntimeBalanceGenerator.generate(evidence, null, BalanceSettings.defaults(), BalanceOverrides.empty()).toJson()), "No-competitor generation changed");
        var direct = generate("B-direct-enchantment", environment(fact("synthetic:enchanted_tool", ProgressionBand.ENTRY, MINING_SPEED, 1000, "attribute_addition")), null);
        responds(vanilla, direct, "statMaxBonuses/essence_ascendance:mining_speed");
        responds(vanilla, direct, "effects/gathering/naturesBoon/dropChance");
        var quarry = generate("C-quarry", environment(fact("synthetic:extractor", ProgressionBand.MID, AUTOMATED_EXTRACTION, 1_000_000, "items_per_second")), null);
        responds(vanilla, quarry, "effects/gathering/naturesBoon/dropChance");
        responds(vanilla, quarry, "statMaxBonuses/essence_ascendance:mining_speed");
        check(value(quarry,"statMaxBonuses/essence_ascendance:mining_speed") < 10_000, "Quarry throughput became literal player speed");
        check(value(quarry,"effects/utility/sanctuary/radiusBlocks") == value(vanilla,"effects/utility/sanctuary/radiusBlocks"), "Quarry contaminated Sanctuary");
        var growth = generate("D-active-growth", environment(fact("synthetic:active_growth", ProgressionBand.EARLY, CROP_ACCELERATION, 1000, "multiplier")), null);
        responds(vanilla, growth, "effects/gathering/verdantStride/growthChance");
        check(value(growth,"effects/gathering/verdantStride/growthPulseTicks") < value(vanilla,"effects/gathering/verdantStride/growthPulseTicks"), "Growth cadence did not adapt");
        var fishingEnv = environment(fact("synthetic:fishing", ProgressionBand.ENTRY, AUTOMATED_FISHING, 10000, "items_per_second"));
        var fishing = generate("E-automated-fishing", fishingEnv, null);
        responds(vanilla, fishing, "effects/gathering/fishersCall/maximumBiteSpeedMultiplier");
        responds(vanilla, fishing, "effects/gathering/pocketNets/dropChance");
        var fishPolicy = new AdaptiveCompetitionCalibration(evidence, fishingEnv);
        check(fishPolicy.factor("skill.fishers_call",ProgressionBand.MID) > fishPolicy.factor("skill.pocket_nets",ProgressionBand.MID), "Fishing share distribution missing");
        var machine = generate("F-time-acceleration", environment(fact("synthetic:accelerator", ProgressionBand.LATE, LOCAL_TIME_ACCELERATION, 1000, "multiplier")), null);
        responds(vanilla, machine, "effects/utility/industriousPresence/processingSpeedMultiplier");
        var spawn = generate("G-spawn-suppression", environment(fact("synthetic:spawn_control", ProgressionBand.ENTRY, SPAWN_SUPPRESSION, 64, "blocks")), null);
        responds(vanilla, spawn, "effects/utility/sanctuary/radiusBlocks");
        var nativeTorch = com.mistaboom.essence_ascendance.balance.capability.InstalledCapabilityProviders.torch(64,true,true,List.of("minecraft:zombie"),
                new CompetitiveCapabilities.Placement(ProgressionBand.ENTRY,true,.9,List.of()));
        var torchPolicy = new AdaptiveCompetitionCalibration(evidence,environment(nativeTorch));
        check(torchPolicy.factor("skill.sanctuary",ProgressionBand.ENTRY) > 1,"Actual typed cube-radius unit did not pressure Sanctuary");
        check(torchPolicy.factor("equipment.meleeDamage",ProgressionBand.ENTRY) == 1,"Spawn radius inflated unrelated offense");
        check(torchPolicy.factor("skill.sanctuary",ProgressionBand.ENTRY) == torchPolicy.factor("skill.sanctuary",ProgressionBand.ENTRY),
                "Memoized pressure changed on reuse");
        var unbreakable = generate("H-indestructible", environment(fact("synthetic:unbreakable", ProgressionBand.ENTRY, INDESTRUCTIBILITY, 1, "presence")), null);
        responds(vanilla, unbreakable, "statMaxBonuses/essence_ascendance:durability_efficiency");
        responds(vanilla, unbreakable, "effects/utility/restfulMending/repairFractionPerSecond");
        check(value(unbreakable,"statMaxBonuses/essence_ascendance:durability_efficiency") <= 90, "Preservation exceeded probability cap");
        var modularEnv = environment(fact("synthetic:configured_weapon", ProgressionBand.LATE, MELEE_DAMAGE, 10000, "health_points"),
                fact("synthetic:configured_tool",ProgressionBand.LATE,MINING_SPEED,10000,"native_destroy_speed"),
                fact("synthetic:configured_tool",ProgressionBand.LATE,DURABILITY,10000000,"uses"));
        var modular = generate("I-modular", modularEnv, null);
        responds(vanilla, modular, "equipment/essence_ascendance:transcendent/meleeDamage");
        responds(vanilla, modular, "equipment/essence_ascendance:transcendent/miningSpeed");
        responds(vanilla, modular, "equipment/essence_ascendance:transcendent/durability");
        var broadEnv = environment(fact("synthetic:offense", ProgressionBand.ENTRY, MELEE_DAMAGE,10000,"health_points"),
                fact("synthetic:defense",ProgressionBand.ENTRY,MAX_HEALTH,10000,"health_points"),
                fact("synthetic:mining",ProgressionBand.ENTRY,MINING_SPEED,10000,"native_destroy_speed"),
                fact("synthetic:mobility",ProgressionBand.ENTRY,GROUND_SPEED,100,"attribute_total_factor"),
                fact("synthetic:durability",ProgressionBand.ENTRY,INDESTRUCTIBILITY,1,"presence"));
        var broad = generate("J-broad-extreme", broadEnv, null);
        for (String path : List.of("equipment/essence_ascendance:transcendent/meleeDamage", "equipment/essence_ascendance:transcendent/miningSpeed",
                "statMaxBonuses/essence_ascendance:max_health", "statMaxBonuses/essence_ascendance:movement_speed", "statMaxBonuses/essence_ascendance:durability_efficiency")) responds(vanilla,broad,path);
        var equivalents = generate("K-equivalent-alternatives", environment(fact("synthetic:a",ProgressionBand.ENTRY,SPAWN_SUPPRESSION,64,"blocks"),
                fact("synthetic:b",ProgressionBand.ENTRY,SPAWN_SUPPRESSION,64,"blocks"), fact("synthetic:c",ProgressionBand.ENTRY,SPAWN_SUPPRESSION,64,"blocks")), null);
        check(equivalents.toJson().equals(spawn.toJson()), "Equivalent competitors amplified generated values");
        var late = generate("L-late-only", environment(fact("synthetic:late_tool",ProgressionBand.APEX,MINING_SPEED,1_000_000,"native_destroy_speed")), null);
        check(late.config().equipmentBaselineConfig().baselineFor(AscendanceTiers.DORMANT).equals(vanilla.config().equipmentBaselineConfig().baselineFor(AscendanceTiers.DORMANT)), "Late gear leaked into Dormant equipment");
        check(late.config().equipmentBaselineConfig().baselineFor(AscendanceTiers.LATENT).equals(vanilla.config().equipmentBaselineConfig().baselineFor(AscendanceTiers.LATENT)), "Late gear leaked into Latent equipment");
        check(value(late,"effects/gathering/toolInstinct/maximumLowerTierSpeedBonus") == value(vanilla,"effects/gathering/toolInstinct/maximumLowerTierSpeedBonus"), "Late competitor inflated early skill");
        for (var tier : List.of(AscendanceTiers.DORMANT, AscendanceTiers.AWAKENED, AscendanceTiers.RESONANT)) {
            var before=vanilla.config().balanceProfile().bonusTrack(EssenceStats.MINING_SPEED.id());
            var after=late.config().balanceProfile().bonusTrack(EssenceStats.MINING_SPEED.id());
            check(after.maximumEffect()*after.checkpoint(tier.id()).effectFraction() <= before.maximumEffect()*before.checkpoint(tier.id()).effectFraction()+1,
                    "Late competitor inflated early Bonus at " + tier.id());
        }
        var early = generate("M-extreme-early", environment(fact("synthetic:early_tool",ProgressionBand.ENTRY,MINING_SPEED,1_000_000,"native_destroy_speed")), null);
        responds(vanilla,early,"equipment/essence_ascendance:dormant/miningSpeed");
        responds(vanilla,early,"effects/gathering/toolInstinct/maximumLowerTierSpeedBonus");
        // Outlier, unknown semantics, low confidence, unreachable, renamed source and policy controls.
        var outlierEnv = environment(fact("synthetic:normal1",ProgressionBand.ENTRY,MINING_SPEED,8,"native_destroy_speed"),
                fact("synthetic:normal2",ProgressionBand.ENTRY,MINING_SPEED,8,"native_destroy_speed"),
                fact("synthetic:pathological",ProgressionBand.ENTRY,MINING_SPEED,Double.MAX_VALUE,"native_destroy_speed"));
        var outlier = generate("outlier",outlierEnv,null);
        check(value(outlier,"equipment/essence_ascendance:transcendent/miningSpeed") < 100, "One outlier exploded mining");
        var unknown = generate("unknown-unit",environment(fact("synthetic:unknown",ProgressionBand.ENTRY,MINING_SPEED,1e100,"unsupported_units")),null);
        check(unknown.toJson().equals(vanilla.toJson()), "Unknown units fabricated pressure");
        check(reports.get("unknown-unit").getAsJsonArray("warnings").size() > 0,"Unsupported unit warning missing");
        var neutralCandidate = environment(); var candidates = new JsonArray(); var candidate = new JsonObject();
        candidate.addProperty("subject","synthetic:unsupported"); candidate.addProperty("detail","configured behavior unproven"); candidates.add(candidate); neutralCandidate.add("candidates",candidates);
        check(generate("unsupported",neutralCandidate,null).toJson().equals(vanilla.toJson()),"Unsupported configuration inflated balance");
        check(reports.get("unsupported").getAsJsonArray("warnings").get(0).getAsString().contains("may be underestimated"),"Unsupported competitor diagnosis absent");
        var renamed = generate("renamed",environment(fact("unrelated_namespace:alternative",ProgressionBand.ENTRY,SPAWN_SUPPRESSION,64,"blocks")),null);
        check(renamed.toJson().equals(spawn.toJson()), "Source namespace affected calibration");
        // Exercise the complete existing economy/composition path, not just nominal effects.
        var guarded = generate("J-with-composition",broadEnv,new EconomyProfile(Map.of(),List.of(),List.of(),List.of(),0,EconomyProcessingPolicy.defaults()));
        check(guarded.generationAnalysis()!=null,"Composition guard omitted");
        check(value(guarded,"equipment/essence_ascendance:transcendent/meleeDamage") > value(vanilla,"equipment/essence_ascendance:transcendent/meleeDamage"),"Guard erased modular equipment response");
        var defense = generate("defense-with-composition",environment(fact("synthetic:mitigation",ProgressionBand.ENTRY,DAMAGE_REDUCTION,.99,"fraction")),
                new EconomyProfile(Map.of(),List.of(),List.of(),List.of(),0,EconomyProcessingPolicy.defaults()));
        check(Math.abs(defense.composition().get("competitive_ENTRY_effective_health") - 2000) < .000001,"Mitigation lost physical EHP reference");
        check(defense.composition().getOrDefault("competitive_ENTRY_health",20.0) == 20,"Mitigation was converted to raw health");
        var extreme = generate("finite-extreme",environment(fact("synthetic:extreme",ProgressionBand.ENTRY,GROUND_SPEED,Double.MAX_VALUE,"attribute_addition")),null);
        check(Double.isFinite(value(extreme,"statMaxBonuses/essence_ascendance:movement_speed")),"Extreme input produced nonfinite power");
        var bow = fact("synthetic:bow",ProgressionBand.ENTRY,SUSTAINED_DAMAGE,10000,"health_points_per_second");
        var bowMeasurement = CompetitiveCapabilities.measurement(SUSTAINED_DAMAGE,10000.0,"health_points_per_second",
                "equipment slot=mainhand_bow",Scope.self(),Operation.manual(),"synthetic",Origin.TYPED_ADAPTER,List.of());
        var bowPolicy = new AdaptiveCompetitionCalibration(evidence,environment(new Functional(bow.source(),bow.configuration(),List.of(bowMeasurement),List.of(),true)));
        check(bowPolicy.factor("equipment.rangedDamage",ProgressionBand.ENTRY)>1,"Bow did not pressure ranged equipment");
        check(bowPolicy.factor("equipment.meleeDamage",ProgressionBand.ENTRY)==1 && bowPolicy.factor("equipment.magicDamage",ProgressionBand.ENTRY)==1,
                "Bow contaminated unrelated weapon families");
        var physicalReferences = new TreeMap<String,Double>(); bowPolicy.combatReferences(physicalReferences);
        check(physicalReferences.containsKey("competitive_ENTRY_ranged_damage") && !physicalReferences.containsKey("competitive_ENTRY_melee_shield_damage")
                && !physicalReferences.containsKey("competitive_ENTRY_caster_damage"),"Combat witness crossed weapon families");
        var harvestPolicy = new AdaptiveCompetitionCalibration(evidence,environment(fact("synthetic:access",ProgressionBand.ENTRY,HARVEST_LEVEL,10,"native_harvest_level")));
        var harvestEquipment = new TreeMap<>(vanilla.config().equipmentBaselineConfig().tierBaselines()); harvestPolicy.equipment(harvestEquipment);
        check(harvestEquipment.get(AscendanceTiers.DORMANT.id()).harvestLevel()==10,"Discrete harvest access became an ordinal multiplier");
        var unsupported = fact("synthetic:potential",ProgressionBand.ENTRY,MINING_SPEED,1e100,"native_destroy_speed");
        var potential = new Functional(unsupported.source(),unsupported.configuration(),unsupported.measurements(),List.of(),false);
        check(generate("potential",environment(potential),null).toJson().equals(vanilla.toJson()),"Potential configuration affected balance");
        var uncertain = new Functional(new CapabilityEvidence("synthetic:uncertain",ProgressionBand.ENTRY,Map.of(),true,.3,"Uncertain"),
                unsupported.configuration(),unsupported.measurements(),List.of(),true);
        check(generate("uncertain",environment(uncertain),null).toJson().equals(vanilla.toJson()),"Low confidence affected balance");
        var unreachable = new Functional(new CapabilityEvidence("synthetic:unreachable",ProgressionBand.ENTRY,Map.of(),false,.9,"Unreachable"),
                unsupported.configuration(),unsupported.measurements(),List.of(),true);
        check(generate("unreachable",environment(unreachable),null).toJson().equals(vanilla.toJson()),"Unreachable source affected balance");
        for (var report : reports.values()) {
            check(report.get("calibrationNanos").getAsLong()>0,"Calibration cost missing");
            for (var row : report.getAsJsonArray("rows")) {
                var r=row.getAsJsonObject(); check(r.has("baseline") && r.has("adaptiveTarget") && r.has("finalGeneratedValue") && r.has("confidence") && r.has("limit"),"Incomplete decision diagnostics");
            }
        }
        if (args.length>0) { Path folder=Path.of(args[0]); Files.createDirectories(folder);
            Files.writeString(folder.resolve("synthetic-adaptive-reports.json"),BalanceDocument.GSON.toJson(reports));
            Files.writeString(folder.resolve("synthetic-runtimes.json"),BalanceDocument.GSON.toJson(runtimes)); }
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out)).println("AdaptiveCompetitionCalibrationTest: " + checks + " checks passed; A-M and guarded generation");
    }
    private static JsonObject availabilityRow(JsonObject report, net.minecraft.resources.ResourceLocation id) {
        for (var row : report.getAsJsonArray("skillAvailability"))
            if (row.getAsJsonObject().get("skill").getAsString().equals(id.toString())) return row.getAsJsonObject();
        throw new AssertionError("Missing availability row for " + id);
    }
    private static JsonObject availabilityReport(JsonObject environment, RuntimeBalanceDefinition runtime) {
        var policy = new AdaptiveCompetitionCalibration(evidence, environment); policy.skillTiers(); return policy.complete(runtime);
    }
    private static void availabilityDiagnostics(RuntimeBalanceDefinition baseline) {
        var neutralReport = availabilityReport(environment(), baseline);
        check(availabilityRow(neutralReport, SkillIds.TOOL_INSTINCT).get("decisionReason").getAsString().equals("EXPLICIT_MECHANICAL_UTILITY_POLICY"),
                "An already-earliest skill was mislabeled as a failed search for earlier competition");
        var emptyFlight = availabilityRow(neutralReport, SkillIds.FATIGUE_FLIGHT);
        check(emptyFlight.get("decisionReason").getAsString().equals("EXPLICIT_MECHANICAL_UTILITY_POLICY")
                        && emptyFlight.get("evidenceStatus").getAsString().equals("UNKNOWN"),
                "No axis evidence incorrectly certified catalog-tier parity");
        check(emptyFlight.getAsJsonArray("evidenceGaps").toString().contains("NO_AXIS_MEASUREMENTS"),
                "Missing axis coverage was not reported");
        var earlyReport = availabilityReport(environment(fact("synthetic:early_flight", ProgressionBand.ENTRY, FLIGHT, 1, "presence")), baseline);
        var earlyFlight = availabilityRow(earlyReport, SkillIds.FATIGUE_FLIGHT);
        check(earlyFlight.get("decisionReason").getAsString().equals("PROGRESSION_GRAPH_CONSTRAINT")
                        && earlyFlight.getAsJsonObject("competitionCounts").get("admittedEarlierTier").getAsLong() == 1,
                "Cumulative frontier copies multiplied a single earlier acquisition witness");
        check(earlyFlight.get("evidenceStatus").getAsString().equals("PARTIAL"), "A supported witness incorrectly implied exhaustive coverage");
        var alternatives = availabilityRow(availabilityReport(environment(
                fact("synthetic:flight_a", ProgressionBand.ENTRY, FLIGHT, 1, "presence"),
                fact("synthetic:flight_b", ProgressionBand.ENTRY, FLIGHT, 1, "presence"),
                fact("synthetic:flight_c", ProgressionBand.ENTRY, FLIGHT, 1, "presence")), baseline), SkillIds.FATIGUE_FLIGHT);
        check(alternatives.getAsJsonObject("competitionCounts").get("admittedEarlierTier").getAsLong() == 3
                        && !alternatives.getAsJsonObject("competitionCounts").getAsJsonObject("rejectedEarlierByReason")
                            .has("NOT_SELECTED_FOR_COMPACT_FRONTIER"),
                "Other retained frontier representatives were falsely classified as unselected or counted repeatedly");
        var later = availabilityRow(availabilityReport(environment(fact("synthetic:late_flight", ProgressionBand.APEX, FLIGHT, 1, "presence")), baseline), SkillIds.FATIGUE_FLIGHT);
        check(later.get("decisionReason").getAsString().equals("EXPLICIT_MECHANICAL_UTILITY_POLICY")
                        && later.getAsJsonObject("competitionCounts").get("admittedLaterTier").getAsLong() == 1,
                "Later supported competition was confused with missing evidence");
        var same = availabilityRow(availabilityReport(environment(fact("synthetic:same_flight", ProgressionBand.MID, FLIGHT, 1, "presence")), baseline), SkillIds.FATIGUE_FLIGHT);
        check(same.getAsJsonObject("competitionCounts").get("admittedSameTier").getAsLong() == 1,
                "Same-tier competition was not distinguished from later access");

        var sink = new CapabilitySink();
        for (int i = 0; i < 700; i++) sink.candidate("synthetic:unproven_" + i, "arbitrary_adapter", "Unproven setup",
                Set.of(FLIGHT), CapabilitySink.Reason.ACCESS_UNPROVEN);
        sink.candidate("synthetic:failed", "other_adapter", "Getter failed", Set.of(FLIGHT), CapabilitySink.Reason.READ_FAILED);
        sink.candidate("synthetic:global_unknown", "unscoped_adapter", "Unclassified behavior");
        var incomplete = CompetitiveCapabilities.report(sink, "WINSORIZE");
        var providers = new JsonArray(); var provider = new JsonObject(); provider.addProperty("id", "unrelated_namespace");
        provider.addProperty("status", "FAILED"); provider.addProperty("detail", "Optional getter failed");
        var providerAxes = new JsonArray(); providerAxes.add("FLIGHT"); provider.add("capabilityAxes", providerAxes); providers.add(provider);
        incomplete.add("providerDiagnostics", providers);
        var incompletePolicy = new AdaptiveCompetitionCalibration(evidence, incomplete);
        var unchanged = incompletePolicy.skillTiers();
        check(unchanged.equals(new AdaptiveCompetitionCalibration(evidence, environment()).skillTiers())
                        && incompletePolicy.factor("skill.fatigue_flight", ProgressionBand.ENTRY) == 1,
                "Diagnostic candidate scope/reason changed calibration selection");
        var failed = availabilityRow(incompletePolicy.complete(baseline), SkillIds.FATIGUE_FLIGHT);
        var reasons = failed.getAsJsonObject("candidateReasonCountsByAxis").getAsJsonObject("FLIGHT");
        check(reasons.get("ACCESS_UNPROVEN").getAsLong() == 700 && reasons.get("READ_FAILED").getAsLong() == 1,
                "Bounded candidate samples hid uncapped per-axis failures/access gaps");
        check(failed.getAsJsonArray("relevantCandidates").size() == 12
                        && failed.getAsJsonArray("relevantProviders").size() == 1,
                "Candidate/provider diagnostics were not explicitly axis-scoped and bounded");
        check(failed.getAsJsonArray("evidenceGaps").toString().contains("PROVIDER_FAILED")
                        && failed.get("evidenceStatus").getAsString().equals("PARTIAL"),
                "A failed optional read was not distinguished from complete competition coverage");
        check(failed.getAsJsonObject("unscopedCandidateReasonCountsGlobal").get("UNCLASSIFIED").getAsLong() == 1,
                "Unclassified diagnostic scope disappeared or was silently attributed to a skill");

        var low = fact("synthetic:low_confidence", ProgressionBand.ENTRY, FLIGHT, 1, "presence");
        var uncertain = new Functional(new CapabilityEvidence(low.source().subjectId(), ProgressionBand.ENTRY,
                Map.of(), true, .3, "Uncertain"), low.configuration(), low.measurements(), List.of(), true);
        var unproven = new Functional(low.source(), "not_attainable", low.measurements(), List.of(), false);
        var rejected = availabilityRow(availabilityReport(environment(uncertain, unproven,
                fact("synthetic:unknown_flight_unit", ProgressionBand.ENTRY, FLIGHT, 1, "opaque_native_unit")), baseline), SkillIds.FATIGUE_FLIGHT);
        var rejectedCounts = rejected.getAsJsonObject("competitionCounts").getAsJsonObject("rejectedEarlierByReason");
        check(rejectedCounts.get("LOW_CONFIDENCE").getAsLong() == 1
                        && rejectedCounts.get("UNATTAINABLE_CONFIGURATION").getAsLong() == 1
                        && rejectedCounts.get("UNSUPPORTED_AVAILABILITY_UNIT").getAsLong() == 1,
                "Earlier rejected factual measurements lost their confidence/access/unit reasons");
        var gliding = availabilityRow(availabilityReport(environment(fact("synthetic:early_gliding", ProgressionBand.ENTRY,
                GLIDING, 1, "presence")), baseline), SkillIds.ESSENCE_WINGS);
        check(gliding.get("decisionReason").getAsString().equals("PROGRESSION_GRAPH_CONSTRAINT"),
                "An earlier gliding candidate must retain its independent flight prerequisite");
        var clamp = java.util.stream.StreamSupport.stream(gliding.getAsJsonArray("prerequisiteClamps").spliterator(), false)
                .map(e -> e.getAsJsonObject()).filter(e -> e.get("prerequisiteSkill").getAsString().equals(SkillIds.FATIGUE_FLIGHT.toString())).findFirst().orElseThrow();
        check(clamp.get("prerequisiteSkill").getAsString().equals(SkillIds.FATIGUE_FLIGHT.toString())
                        && clamp.get("requiredRank").getAsInt() == 1
                        && clamp.get("bindingTier").getAsString().equals(baseline.skillCurves().get(SkillIds.FATIGUE_FLIGHT.toString()).requiredTierId().toString()),
                "Prerequisite clamp omitted its exact skill/rank/effective tier");

        var legacy = environment(); legacy.remove("candidateCoverage"); legacy.remove("unscopedCandidateReasons");
        var oldCandidate = new JsonObject(); oldCandidate.addProperty("subject", "legacy:unknown");
        oldCandidate.addProperty("detail", "Old unclassified candidate"); legacy.getAsJsonArray("candidates").add(oldCandidate);
        var old = availabilityRow(availabilityReport(legacy, baseline), SkillIds.FATIGUE_FLIGHT);
        check(!old.get("scopedCandidateCoverageAvailable").getAsBoolean()
                        && old.get("unscopedCandidateSamplesGlobal").getAsInt() == 1
                        && old.getAsJsonArray("evidenceGaps").toString().contains("LEGACY_CANDIDATE_AXIS_COVERAGE_UNKNOWN"),
                "Older direct-decoded reports silently acquired invented candidate scope");
        var malformed = environment(); malformed.getAsJsonArray("evidence").add(new JsonObject());
        var safelyRead = availabilityReport(malformed, baseline);
        check(safelyRead.getAsJsonObject("skillAvailabilityCoverage").get("diagnosticEvidenceReadFailures").getAsInt() == 1,
                "Optional diagnostic read failure prevented otherwise valid compact calibration");
        var nullMeasurement = environment();
        var nullFact = BalanceDocument.GSON.toJsonTree(fact("synthetic:null_diagnostic", ProgressionBand.ENTRY,
                FLIGHT, 1, "presence")).getAsJsonObject(); var nullList = new JsonArray(); nullList.add(JsonNull.INSTANCE);
        nullFact.add("measurements", nullList); nullMeasurement.getAsJsonArray("evidence").add(nullFact);
        var nullReport = availabilityReport(nullMeasurement, baseline);
        check(nullReport.getAsJsonObject("skillAvailabilityCoverage").get("diagnosticEvidenceReadFailures").getAsInt() == 1
                        && availabilityRow(nullReport, SkillIds.FATIGUE_FLIGHT).get("evidenceStatus").getAsString().equals("UNKNOWN"),
                "A null supplemental measurement escaped staging and broke every skill row projection");
        var intact = environment(fact("synthetic:intact_early_flight", ProgressionBand.ENTRY, FLIGHT, 1, "presence"));
        var intactPolicy = new AdaptiveCompetitionCalibration(evidence, intact); var intactTiers = intactPolicy.skillTiers();
        for (String malformedField : List.of("candidateCoverage", "unscopedCandidateReasons", "providerDiagnostics", "candidateAxes", "candidateReason",
                "nullCandidate", "textCandidate", "missingSubject", "nullSubject", "missingDetail", "nullDetail", "candidateListShape")) {
            var invalid = intact.deepCopy();
            switch (malformedField) {
                case "candidateCoverage" -> {
                    var axis = new JsonObject(); axis.addProperty("ACCESS_UNPROVEN", "not a count");
                    invalid.getAsJsonObject("candidateCoverage").add("FLIGHT", axis);
                }
                case "unscopedCandidateReasons" -> invalid.getAsJsonObject("unscopedCandidateReasons").addProperty("READ_FAILED", -1);
                case "providerDiagnostics" -> {
                    var brokenProviders = new JsonArray(); var brokenProvider = new JsonObject();
                    brokenProvider.addProperty("status", "FAILED"); brokenProvider.addProperty("capabilityAxes", "FLIGHT");
                    brokenProviders.add(brokenProvider); invalid.add("providerDiagnostics", brokenProviders);
                }
                case "nullCandidate" -> invalid.getAsJsonArray("candidates").add(JsonNull.INSTANCE);
                case "textCandidate" -> invalid.getAsJsonArray("candidates").add("Malformed candidate row");
                case "candidateListShape" -> invalid.add("candidates", new JsonObject());
                default -> {
                    var brokenCandidate = new JsonObject(); brokenCandidate.addProperty("subject", "synthetic:broken_optional_diagnostic");
                    brokenCandidate.addProperty("provider", "fixture"); brokenCandidate.addProperty("detail", "Diagnostic field malformed");
                    switch (malformedField) {
                        case "candidateAxes" -> brokenCandidate.addProperty("axes", "FLIGHT");
                        case "candidateReason" -> brokenCandidate.add("reason", new JsonArray());
                        case "missingSubject" -> brokenCandidate.remove("subject");
                        case "nullSubject" -> brokenCandidate.add("subject", JsonNull.INSTANCE);
                        case "missingDetail" -> brokenCandidate.remove("detail");
                        case "nullDetail" -> brokenCandidate.add("detail", JsonNull.INSTANCE);
                    }
                    invalid.getAsJsonArray("candidates").add(brokenCandidate);
                }
            }
            var tolerant = new AdaptiveCompetitionCalibration(evidence, invalid);
            check(tolerant.skillTiers().equals(intactTiers)
                            && tolerant.factor("skill.fatigue_flight", ProgressionBand.ENTRY)
                                == intactPolicy.factor("skill.fatigue_flight", ProgressionBand.ENTRY),
                    "Malformed supplemental " + malformedField + " altered a valid compact tier/pressure decision");
            var diagnosed = tolerant.complete(baseline);
            check(diagnosed.getAsJsonObject("skillAvailabilityCoverage").get("diagnosticEvidenceReadFailures").getAsInt() > 0
                            && diagnosed.getAsJsonArray("warnings").toString().contains("affected coverage is unknown"),
                    "Malformed supplemental " + malformedField + " lacked an explicit unknown/read-failure diagnosis");
        }
    }
}
