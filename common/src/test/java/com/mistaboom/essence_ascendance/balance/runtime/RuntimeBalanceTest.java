package com.mistaboom.essence_ascendance.balance.runtime;

import com.google.gson.JsonObject;
import com.mistaboom.essence_ascendance.balance.config.*;
import com.mistaboom.essence_ascendance.balance.engine.*;
import com.mistaboom.essence_ascendance.balance.economy.*;
import java.util.*;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import com.mistaboom.essence_ascendance.equipment.*;
import com.mistaboom.essence_ascendance.progression.*;
import com.mistaboom.essence_ascendance.skill.Skills;
import com.mistaboom.essence_ascendance.stat.EssenceStats;
import com.mistaboom.essence_ascendance.stat.EssenceStatRegistry;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import com.mistaboom.essence_ascendance.tier.AscendanceTierRegistry;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

/** Uses real Minecraft bootstrap/registries; no fake game or loader stubs. */
public final class RuntimeBalanceTest {
    public static void main(String[] args) {
        Thread.currentThread().setUncaughtExceptionHandler((thread,failure)->failure.printStackTrace(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.err))));
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        EssenceTypes.init(); AscendanceTiers.init(); EssenceStats.init();
        MilestoneProviders.init(); Milestones.init(); Skills.init(); AscendanceAdvancements.init();
        com.mistaboom.essence_ascendance.equipment.EquipmentProfiles.init();
        var first=RuntimeBalanceDefinition.bootstrap();
        var second=RuntimeBalanceDefinition.bootstrap();
        check(first.toJson().equals(second.toJson()),"Runtime generation was not deterministic");
        check(first.toJson().equals(RuntimeBalanceDefinition.fromJson(first.toJson()).toJson()),"Runtime serialization roundtrip failed");
        JsonObject missing=first.toJson(); missing.getAsJsonObject("statMaxBonuses").remove(EssenceStats.MELEE_DAMAGE.id().toString());
        rejected(()->RuntimeBalanceDefinition.fromJson(missing),"Missing registered stat accepted");
        JsonObject unknown=first.toJson(); unknown.addProperty("unknownRuntimeValue",1);
        rejected(()->RuntimeBalanceDefinition.fromJson(unknown),"Unknown runtime property accepted");
        JsonObject rank=first.toJson(); String skill=rank.getAsJsonObject("skillCurves").keySet().iterator().next();
        rank.getAsJsonObject("skillCurves").getAsJsonObject(skill).addProperty("maximumRank",2);
        rejected(()->RuntimeBalanceDefinition.fromJson(rank),"Unregistered rank policy accepted before publication");
        JsonObject huge=first.toJson(); huge.getAsJsonObject("infuser").getAsJsonObject("grades").getAsJsonObject("dormant").addProperty("ingotCapacity",Long.MAX_VALUE-Long.MAX_VALUE%9);
        rejected(()->RuntimeBalanceDefinition.fromJson(huge),"Unsafe carrier capacity accepted");
        JsonObject fraction=first.toJson(); fraction.getAsJsonObject("balanceProfile").getAsJsonObject("tierFractions").addProperty(AscendanceTiers.DORMANT.id().toString(),1.0);
        rejected(()->RuntimeBalanceDefinition.fromJson(fraction),"Nonmonotonic fraction accepted");
        var profile=first.config().balanceProfile();
        for(var stat:EssenceStatRegistry.values()) for(var tier:AscendanceTierRegistry.values()) {
            long cap=profile.getInvestmentCap(tier,stat);
            double last=-1;
            for(int step=0;step<=100;step++) {
                long amount=cap*step/100;
                double progress=StatScalingService.progressionForInvestment(stat,amount,tier,profile);
                check(progress>=last,"Investment curve decreases");last=progress;
                long inverse=StatScalingService.investmentForProgression(stat,progress,tier,profile);
                check(Math.abs(inverse-amount)<=1,"Investment inverse changes paid amount");
            }
        }
        var resources=new TreeMap<String,ResourceEvidence>();
        var values=new TreeMap<String,EconomyProfile.ResourceValue>();
        int index=0;
        for(var essence:com.mistaboom.essence_ascendance.essence.EssenceRegistry.values()) {
            String item="test:resource_"+index++; double amount=10*index;
            resources.put(item,new ResourceEvidence(item,ProgressionBand.ENTRY,Availability.FINITE,Automation.NONE,true,true,
                    100,.9,List.of(),List.of()));
            values.put(item,new EconomyProfile.ResourceValue(new EconomicValue(100),DissolutionYield.of(amount),Map.of(essence.id().toString(),amount),List.of()));
        }
        // Explicit synthetic observations: absence of evidence must not manufacture a hidden tier table.
        Map<ProgressionBand,Map<CapabilityAxis,Double>> references=new EnumMap<>(ProgressionBand.class);
        for(var band:ProgressionBand.values()) {
            double attackRate=1.2+band.ordinal()*.28;
            references.put(band,Map.of(CapabilityAxis.ATTACK_RATE,attackRate,
                    CapabilityAxis.SUSTAINED_DAMAGE,(4+band.ordinal()*2.5)*attackRate,
                    CapabilityAxis.BURST_DAMAGE,4+band.ordinal()*2.5,
                    CapabilityAxis.ARMOR,7+band.ordinal()*3.5,CapabilityAxis.TOUGHNESS,band.ordinal()*2.0,
                    CapabilityAxis.MINING_SPEED,4+band.ordinal()*2.0,CapabilityAxis.DURABILITY,180*Math.pow(1.8,band.ordinal()),
                    CapabilityAxis.HARVEST_LEVEL,(double)Math.min(3,band.ordinal())));
        }
        var evidence=new PackEvidence(resources,List.of(),List.of(),references,List.of(),List.of(),Map.of());
        var economy=new EconomyProfile(values,List.of(),List.of(),List.of(),0,EconomyProcessingPolicy.defaults());
        if (Arrays.asList(args).contains("--adversarial-only")) {
            adversarialProfiles(evidence, economy);
            return;
        }
        var generated=RuntimeBalanceDefinition.generate(evidence,economy,BalanceSettings.defaults(),BalanceOverrides.empty());
        check(generated.generationAnalysis()!=null,"Numeric composition scenarios missing");
        generated.generationAnalysis().requireSafe();
        com.mistaboom.essence_ascendance.skill.balance.SkillBalanceGenerator.validatePublished(generated.config().skillEffects(), generated.skillCurves());
        check(generated.generationAnalysis().assumptions().stream().anyMatch(a -> a.contains("local ignored overage")), "Allocation report must identify excluded floor overage");
        var posture=generated.config().skillEffects().posture();
        var postureDefaults=com.mistaboom.essence_ascendance.config.PostureBalanceSettings.defaults();
        check(posture.evasive().maximumDodgeChance()>0 && posture.evasive().maximumDodgeChance()<=postureDefaults.evasive().maximumDodgeChance(),
                "Rank-one Evasive recovery exceeds requested gameplay strength");
        check(posture.bulwark().maximumResistance()>0 && posture.bulwark().maximumResistance()<=postureDefaults.bulwark().maximumResistance(),
                "Rank-one Bulwark recovery exceeds requested gameplay strength");
        check(posture.adaptive().resistancePerStack()>0 && posture.adaptive().resistancePerStack()<=postureDefaults.adaptive().resistancePerStack(),
                "Rank-one Adaptive recovery exceeds requested gameplay strength");
        if(generated.composition().get("defense_calibration")<1) {
            check(posture.evasive().maximumDodgeChance()>postureDefaults.evasive().maximumDodgeChance()*generated.composition().get("defense_calibration"),
                    "Earlier-tier Nexus defense bottleneck unnecessarily suppresses late-tier posture");
            check(generated.composition().get("posture_evasive_rank_one_recovery")>0,
                    "Independent posture recovery lacks diagnostic evidence");
        }
        BuildPowerTargetsTest.run(generated);
        check(generated.composition().get("equipment_standalone_factor")==1.0,
                "Ordinary equipment must not pay a budget penalty for unowned Nexus upgrades or skills");
        for(var channel:BuildComposition.Channel.values())check(generated.composition().containsKey(channel.name().toLowerCase(Locale.ROOT)+"_calibration"),
                "Independent combat-channel diagnostics missing");
        var generatedApex=generated.config().equipmentBaselineConfig().baselineFor(AscendanceTiers.TRANSCENDENT);
        check(generatedApex.meleeDamage()==14&&generatedApex.meleeAttackSpeed()==2.3,
                "Transcendent physical weapon does not match the observed damage/cadence within gameplay units");
        check(generatedApex.fullSetArmor()==21&&generatedApex.fullSetToughness()==8
                &&generatedApex.miningSpeed()==12&&generatedApex.durability()==1890&&generatedApex.harvestLevel()==3,
                "Transcendent armor, tool or discrete harvest endpoint is not at pack parity");
        verifyEquipmentNormalization(evidence,generated);
        verifyHarvestProgression(evidence,generated);
        rejected(()->RuntimeBalanceDefinition.generate(new PackEvidence(resources,List.of(),List.of(),Map.of(),List.of(),List.of(),Map.of()),economy,
                BalanceSettings.defaults(),BalanceOverrides.empty()),"Missing pack measurements accepted as fabricated equipment");
        check(generated.toJson().equals(RuntimeValueQuantization.apply(generated).toJson()),"Published runtime endpoints are not quantized");
        rejected(()->RuntimeBalanceDefinition.generate(evidence,economy,BalanceSettings.defaults(),new BalanceOverrides(List.of(),
                Map.of("/runtime/statMaxBonuses/essence_ascendance:melee_damage",12.34))),"Off-grid exact override accepted");
        rejected(()->RuntimeBalanceDefinition.generate(evidence,economy,BalanceSettings.defaults(),new BalanceOverrides(List.of(),
                Map.of("/runtime/equipment/essence_ascendance:transcendent/meleeDamage",1000))),"Equipment override raised its own parity ceiling");
        rejected(()->RuntimeBalanceDefinition.generate(evidence,economy,BalanceSettings.defaults(),new BalanceOverrides(List.of(),
                Map.of("/runtime/skillCurves/essence_ascendance:frenzy/ranks/4/powerMultiplier",1000))),"Rank-five exact override bypassed developed-build validation");
        check(RuntimeValueQuantization.down(.049,.01)==.04,"Small regeneration rate lost its unit precision");
        check(RuntimeValueQuantization.down(1.65,.1)==1.6,"Attack cadence was floored to whole attacks");
        check(RuntimeValueQuantization.down(10.9,.5)==10.5,"Health does not use half-heart endpoints");
        check(RuntimeReferencePolicy.playerHit()==net.minecraft.world.entity.player.Player.createAttributes().build()
                .getValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE),"Bootstrap is not based on actual player attributes");
        check(!RuntimeReferencePolicy.usingBootstrapReferences(), "Provisional reference scope leaked into real generation");
        RuntimeReferencePolicy.withBootstrapReferences(() -> {
            check(RuntimeReferencePolicy.usingBootstrapReferences(), "Bootstrap did not enter its registration-safe scope");
            check(RuntimeReferencePolicy.playerHealth()==net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH.value().getDefaultValue()
                    && RuntimeReferencePolicy.playerRate()==net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED.value().getDefaultValue()
                    && RuntimeReferencePolicy.playerHit()==1.0, "Pre-registry fallback differs from vanilla player baseline");
            RuntimeReferencePolicy.withBootstrapReferences(() -> {
                check(RuntimeReferencePolicy.usingBootstrapReferences(), "Nested bootstrap lost its reference scope");
                return null;
            });
            check(RuntimeReferencePolicy.usingBootstrapReferences(), "Nested bootstrap cleared its parent scope");
            return null;
        });
        rejected(() -> RuntimeReferencePolicy.withBootstrapReferences(() -> {
            throw new IllegalArgumentException("intentional bootstrap failure");
        }), "Bootstrap suppressed its failure");
        check(!RuntimeReferencePolicy.usingBootstrapReferences(), "Failed bootstrap leaked provisional player references");
        var registeredPlayer=net.minecraft.world.entity.player.Player.createAttributes().build();
        check(RuntimeReferencePolicy.playerHealth()==registeredPlayer.getValue(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH)
                && RuntimeReferencePolicy.playerHit()==registeredPlayer.getValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)
                && RuntimeReferencePolicy.playerRate()==registeredPlayer.getValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED),
                "Real generation no longer reads registered player attributes");
        var floatNoisy=new PackEvidence(Map.of(),List.of(),List.of(),Map.of(ProgressionBand.ENTRY,
                Map.of(CapabilityAxis.ATTACK_RATE,1.5999999046325684)),List.of(),List.of(),Map.of());
        check(RuntimeReferencePolicy.required(floatNoisy,ProgressionBand.APEX,CapabilityAxis.ATTACK_RATE)==1.6,
                "Minecraft float observation noise removes one tenth of an attack from the reference");
        var futureOnly=new PackEvidence(Map.of(),List.of(),List.of(),Map.of(ProgressionBand.APEX,
                Map.of(CapabilityAxis.ATTACK_RATE,4.0)),List.of(),List.of(),Map.of());
        rejected(()->RuntimeReferencePolicy.required(futureOnly,ProgressionBand.ENTRY,CapabilityAxis.ATTACK_RATE),
                "Missing early evidence was filled with an unattainable future weapon");
        check(com.mistaboom.essence_ascendance.equipment.EquipmentBaselineService.resolvedValue(8,
                com.mistaboom.essence_ascendance.equipment.EquipmentProfiles.AXE,
                com.mistaboom.essence_ascendance.equipment.EquipmentBaselineProperty.MELEE_DAMAGE)==9,
                "Archetype rounding erased the axe's heavier hit");
        for(int points=0;points<200;points++) {
            double total=0;
            for(var slot:com.mistaboom.essence_ascendance.equipment.ArmorStatWeights.values().keySet()) {
                double piece=com.mistaboom.essence_ascendance.equipment.ArmorStatWeights.physicalPointsForSlot(points,slot);
                check(piece==Math.floor(piece),"Armor piece has fractional physical points");total+=piece;
            }
            check(total==points,"Armor slot allocation lost or created full-set points");
        }
        for(double points:new double[]{1_000_000_000_000_000_128d,Math.nextDown((double)Long.MAX_VALUE),(double)Long.MAX_VALUE,1e100}) {
            double allocated=0;
            for(var slot:com.mistaboom.essence_ascendance.equipment.ArmorStatWeights.values().keySet()) {
                double piece=com.mistaboom.essence_ascendance.equipment.ArmorStatWeights.physicalPointsForSlot(points,slot);
                check(Double.isFinite(piece)&&piece>=0&&piece==Math.floor(piece),"Huge armor allocation overflowed");allocated+=piece;
            }
            check(Math.abs(allocated-points)<=Math.ulp(points),"Huge armor allocation lost more than double representation precision");
        }
        var legacy=generated.toJson();legacy.getAsJsonObject("composition").remove("equipment_quantization");
        var legacyRuntime=RuntimeBalanceDefinition.fromJson(legacy);
        com.mistaboom.essence_ascendance.config.EssenceConfigManager.installClient(legacyRuntime);
        check(!com.mistaboom.essence_ascendance.equipment.EquipmentBaselineService.quantizationEnabled(),
                "An existing cached profile was silently switched to new physical rounding");
        var axe=com.mistaboom.essence_ascendance.equipment.EquipmentProfiles.AXE;
        var actualLegacy=com.mistaboom.essence_ascendance.equipment.EquipmentBaselineService.evaluateAtTier(
                new com.mistaboom.essence_ascendance.data.PlayerEssenceData(),axe.id(),AscendanceTiers.TRANSCENDENT);
        var legacyBaseline=legacyRuntime.config().equipmentBaselineConfig().baselineFor(AscendanceTiers.TRANSCENDENT);
        check(RuntimeBuildScenarios.equipmentValue(legacyRuntime,legacyBaseline.meleeDamage(),axe,
                com.mistaboom.essence_ascendance.equipment.EquipmentBaselineProperty.MELEE_DAMAGE)==actualLegacy.meleeDamage(),
                "Legacy combat projection does not match actual unquantized archetype damage");
        check(RuntimeBuildScenarios.equipmentValue(legacyRuntime,legacyBaseline.meleeAttackSpeed(),axe,
                com.mistaboom.essence_ascendance.equipment.EquipmentBaselineProperty.MELEE_ATTACK_SPEED)==actualLegacy.meleeAttackSpeed(),
                "Legacy combat projection does not match actual unquantized archetype cadence");
        com.mistaboom.essence_ascendance.config.EssenceConfigManager.clearClient();
        check(generated.generationAnalysis().cases().stream().allMatch(c->c.evaluation().scenarios().size()==7),"Participation scenario coverage missing");
        rejected(()->RuntimeBalanceDefinition.generate(evidence,economy,BalanceSettings.defaults(),new BalanceOverrides(List.of(),
                Map.of("/runtime/statMaxBonuses/essence_ascendance:melee_damage",1_000_000.0))),"Unsafe exact combat override accepted");
        rejected(()->RuntimeBalanceDefinition.generate(evidence,economy,BalanceSettings.defaults(),new BalanceOverrides(List.of(),
                Map.of("/runtime/composition/equipment_share",.9))),"Derived diagnostic override accepted");
        check(generated.toJson().equals(RuntimeBalanceDefinition.generate(evidence,economy,BalanceSettings.defaults(),BalanceOverrides.empty()).toJson()),"Full runtime pass nondeterministic");
        var meleeTrack = generated.config().balanceProfile().bonusTrack(EssenceStats.MELEE_DAMAGE.id());
        var reflectionTrack = generated.config().balanceProfile().bonusTrack(EssenceStats.DAMAGE_REFLECTION.id());
        check(!meleeTrack.inputs().get("category_supply").equals(reflectionTrack.inputs().get("category_supply")), "Category supply input ignored");
        check(meleeTrack.checkpoints().stream().filter(BonusTrackDefinition.Checkpoint::purchasable).findFirst().orElseThrow().segmentCost()
                != reflectionTrack.checkpoints().stream().filter(BonusTrackDefinition.Checkpoint::purchasable).findFirst().orElseThrow().segmentCost(),
                "Generated state pricing must retain category economy despite independently placed tier windows");
        var exact=new BalanceOverrides(List.of(),Map.of("/runtime/worldgen/overworld/veinsPerChunk",3L,
                "/runtime/skillCurves/"+skill+"/ranks/0/cost",1L));
        var edited=RuntimeBalanceDefinition.generate(evidence,economy,BalanceSettings.defaults(),exact);
        check(edited.config().latentOreWorldgen().overworld().veinsPerChunk()==3,"Exact override failed");
        check(edited.skillCurves().get(skill).ranks().get(0).cost()==1,"Array pointer override failed");
        check(!edited.config().balanceProfile().id().equals(generated.config().balanceProfile().id()),"Changed profile reused transaction identity");
        var threatening=new PackEvidence(resources,List.of(),List.of(new EnemyReference("test:strong_mob",EnemyReference.Encounter.ROUTINE,
                ProgressionBand.ENTRY,Map.of(CapabilityAxis.EFFECTIVE_HEALTH,400.0,CapabilityAxis.BURST_DAMAGE,12.0,
                        CapabilityAxis.SUSTAINED_DAMAGE,120.0),true,.9,List.of(),"synthetic")),
                references,List.of(),List.of(),Map.of());
        var againstEnemies=RuntimeBalanceDefinition.generate(threatening,economy,BalanceSettings.defaults(),BalanceOverrides.empty());
        check(againstEnemies.composition().get("offense_encounter_factor_entry")
                >generated.composition().get("offense_encounter_factor_entry"),"Enemy evidence ignored by the original physical curve calculation");
        check(againstEnemies.config().equipmentBaselineConfig().baselineFor(AscendanceTiers.TRANSCENDENT).meleeDamage()
                ==generated.config().equipmentBaselineConfig().baselineFor(AscendanceTiers.TRANSCENDENT).meleeDamage(),
                "Enemy pressure moved the final physical endpoint away from pack parity");
        check(againstEnemies.composition().get("enemy_damage_entry")==12.0,
                "Incoming-hit pressure must use observed attack damage, not sustained DPS or the missing-cadence fallback");
        adversarialProfiles(evidence, economy);
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out)).println(
                "RuntimeBalanceTest: deterministic generation, strict roundtrip, rejected malformed runtime/rank/carrier fields, exact pointers, category supply, enemy evidence, harvest progression/cache compatibility, monotonic/inverse curves PASS");
    }
    /** Supported settings retain native floors; infeasible targets must reject rather than sell negligible power. */
    private static void adversarialProfiles(PackEvidence evidence, EconomyProfile economy) {
        Map<String, String> profiles = new LinkedHashMap<>();
        profiles.put("very-low-budget", "[power]\noverall=0.1\n[budget]\nequipment=0.98\nnexus=0.01\nskills=0.01\n");
        profiles.put("high-budget", "[power]\noverall=4.0\n[budget]\nequipment=0.02\nnexus=0.49\nskills=0.49\n");
        profiles.put("narrow-range", "[power]\nearly=1.0\nmid=1.01\nlate=1.02\napex=1.03\n");
        profiles.put("wide-range", "[power]\nearly=0.1\nmid=0.5\nlate=2.0\napex=4.0\n");
        profiles.put("wide-range-supported", "[power]\nearly=0.5\nmid=1.0\nlate=2.0\napex=4.0\n");
        var output = new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out));
        var failures = new ArrayList<Throwable>();
        for (var entry : profiles.entrySet()) {
            output.println("Adversarial profile START: " + entry.getKey());
            try {
            var settings = BalanceSettings.parse("schema_version=1\n" + entry.getValue(), entry.getKey());
            var runtime = RuntimeBalanceDefinition.generate(evidence, economy, settings, BalanceOverrides.empty());
            check(!entry.getKey().equals("wide-range"),
                    "Extreme wide range must reject targets below the minimum rank-one power envelope");
            com.mistaboom.essence_ascendance.skill.balance.SkillBalanceGenerator.validatePublished(
                    runtime.config().skillEffects(), runtime.skillCurves());
            check(runtime.skillCurves().size() == 90, "Stress profile retains all skills: " + entry.getKey());
            check(runtime.toJson().equals(RuntimeBalanceDefinition.fromJson(runtime.toJson()).toJson()),
                    "Stress profile survives strict published validation: " + entry.getKey());
            runtime.generationAnalysis().requireSafe();
            for (var stat : EssenceStatRegistry.values()) {
                var track = runtime.config().balanceProfile().bonusTrack(stat.id());
                check(track != null, "Stress profile accounts for bonus: " + stat.id());
                check(track.checkpoint(AscendanceTiers.LATENT.id()).effectFraction() == 0,
                        "Stress profile cannot activate a Latent bonus");
                if (track.applicability() == BonusTrackDefinition.Applicability.UNAVAILABLE) continue;
                var values = track.activeValues();
                var floors = track.progressionRequirements();
                check(values.getFirst() + 1e-9 >= floors.firstStateFloor(), "Stress bonus first floor");
                for (int i = 1; i < values.size(); i++) check(
                        MeaningfulProgression.improves(
                                values.get(i - 1), values.get(i), floors.tierImprovementFloor()), "Stress bonus later floor");
                check(values.size() == track.activeStateCount() && values.getLast() == track.maximumEffect(),
                        "Stress bonus generated count and endpoint");
            }
            output.println("Adversarial profile PASS: " + entry.getKey());
            } catch (RuntimeException | AssertionError failure) {
                if (entry.getKey().equals("wide-range") && failure instanceof IllegalArgumentException
                        && failure.getMessage() != null && failure.getMessage().startsWith(
                        "Requested OFFENSE targets leave less than 2% of rank-one added power;")) {
                    output.println("Adversarial profile PASS: " + entry.getKey() + " (expected minimum-power rejection)");
                    continue;
                }
                output.println("Adversarial profile FAIL: " + entry.getKey());
                failure.printStackTrace(output);
                failures.add(new AssertionError("Adversarial profile: " + entry.getKey(), failure));
            }
        }
        if (!failures.isEmpty()) {
            var failure = new AssertionError("Adversarial profiles failed: " + failures.size());
            failures.forEach(failure::addSuppressed);
            throw failure;
        }
    }
    private static void verifyHarvestProgression(PackEvidence evidence,RuntimeBalanceDefinition generated) {
        Map<Integer,int[]> ladders=Map.of(
                0,new int[]{0,0,0,0,0,0},1,new int[]{0,1,1,1,1,1},
                3,new int[]{0,1,2,3,3,3},5,new int[]{0,1,2,3,4,5},
                7,new int[]{0,1,2,4,5,7},10,new int[]{0,2,4,6,8,10},
                32,new int[]{0,6,12,19,25,32});
        var physicalReference=new LinkedHashMap<>(generated.config().equipmentBaselineConfig().tierBaselines());
        RuntimeEquipmentNormalization.apply(physicalReference,evidence,BalanceSettings.defaults(),new TreeMap<>());
        for(var ladder:ladders.entrySet()) {
            var frontiers=new EnumMap<ProgressionBand,Map<CapabilityAxis,Double>>(ProgressionBand.class);
            evidence.frontiers().forEach((band,axes)->{
                var adjusted=new EnumMap<CapabilityAxis,Double>(CapabilityAxis.class);adjusted.putAll(axes);
                adjusted.put(CapabilityAxis.HARVEST_LEVEL,
                        band==ProgressionBand.APEX?(double)ladder.getKey():Math.min(ladder.getKey(),axes.get(CapabilityAxis.HARVEST_LEVEL)));
                frontiers.put(band,adjusted);
            });
            var pack=new PackEvidence(Map.of(),List.of(),List.of(),frontiers,List.of(),List.of(),Map.of());
            var curve=new LinkedHashMap<>(generated.config().equipmentBaselineConfig().tierBaselines());
            var diagnostics=new TreeMap<String,Double>();
            RuntimeEquipmentNormalization.apply(curve,pack,BalanceSettings.defaults(),diagnostics);
            check(diagnostics.get("equipment_harvest_tier_intervals")==EquipmentTier.values().length-1
                    &&diagnostics.get("equipment_harvest_pack_maximum").intValue()==ladder.getKey()
                    &&diagnostics.get("equipment_apex_reference_harvest_level").intValue()==ladder.getKey(),
                    "Harvest diagnostics omit the infusion count or accepted pack maximum");
            check(!diagnostics.containsKey("equipment_normalization_harvest_level"),
                    "Discrete harvest access was reported as a physical curve normalization factor");
            for(var tier:EquipmentTier.values()) {
                var id=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("essence_ascendance",tier.serializedName());
                check(curve.get(id).harvestLevel()==ladder.getValue()[tier.ordinal()],
                        "Incorrect "+tier+" harvest access for pack ceiling "+ladder.getKey());
                for(var property:EquipmentBaselineProperty.values())check(curve.get(id).value(property)==physicalReference.get(id).value(property),
                        "Harvest ceiling reshaped physical stat "+property+" at "+tier);
            }
        }
        // Every supported pack ceiling reaches parity without moving backward or
        // delaying the first ordinary mining unlock past the first infusion.
        for(int maximum=0;maximum<=32;maximum++) {
            int previous=0;
            for(var tier:EquipmentTier.values()) {
                int level=RuntimeEquipmentNormalization.harvestLevel(tier,maximum);
                check(level>=previous&&level<=maximum,"Harvest ladder leaves its monotonic pack bounds");previous=level;
            }
            check(previous==maximum,"Final infusion misses the pack's maximum harvest level");
            check(RuntimeEquipmentNormalization.harvestLevel(EquipmentTier.LATENT,maximum)==0,"Latent skipped the lowest harvest level");
            if(maximum>0)check(RuntimeEquipmentNormalization.harvestLevel(EquipmentTier.DORMANT,maximum)>=1,"Dormant gains no harvest level");
        }
        com.mistaboom.essence_ascendance.config.EssenceConfigManager.installClient(generated);
        try {
            for(var tier:EquipmentTier.values()) {
                var actual=EquipmentBaselineService.evaluateForEquipmentTier(
                        new com.mistaboom.essence_ascendance.data.PlayerEssenceData(),EquipmentProfiles.PICKAXE.id(),tier);
                check(actual.harvestLevel()==ladders.get(3)[tier.ordinal()],
                        "Actual pickaxe harvest capability differs from its generated item tier");
            }
        } finally {
            com.mistaboom.essence_ascendance.config.EssenceConfigManager.clearClient();
        }
        var cached=generated.toJson();
        cached.getAsJsonObject("equipment").getAsJsonObject(AscendanceTiers.DORMANT.id().toString()).addProperty("harvestLevel",0);
        cached.getAsJsonObject("equipment").getAsJsonObject(AscendanceTiers.AWAKENED.id().toString()).addProperty("harvestLevel",1);
        cached.getAsJsonObject("equipment").getAsJsonObject(AscendanceTiers.RESONANT.id().toString()).addProperty("harvestLevel",2);
        var restored=RuntimeBalanceDefinition.fromJson(cached);
        check(restored.toJson().equals(cached),"Loading a cached profile silently regenerated its harvest progression");
        com.mistaboom.essence_ascendance.config.EssenceConfigManager.installClient(restored);
        try {
            check(EquipmentBaselineService.evaluateForEquipmentTier(new com.mistaboom.essence_ascendance.data.PlayerEssenceData(),
                    EquipmentProfiles.PICKAXE.id(),EquipmentTier.AWAKENED).harvestLevel()==1,
                    "Cached harvest policy changed without an explicit rebuild");
        } finally {
            com.mistaboom.essence_ascendance.config.EssenceConfigManager.clearClient();
        }
    }
    private static void verifyEquipmentNormalization(PackEvidence evidence,RuntimeBalanceDefinition generated) {
        var apexTier=AscendanceTiers.TRANSCENDENT.id();
        var curve=new LinkedHashMap<net.minecraft.resources.ResourceLocation,com.mistaboom.essence_ascendance.equipment.EquipmentBaselineConfig.TierBaseline>();
        var apex=new com.mistaboom.essence_ascendance.equipment.EquipmentBaselineConfig.TierBaseline(42,16,28,4.6,28,4.6,16.8,7.666666666,24,3,3780);
        var earlier=new com.mistaboom.essence_ascendance.equipment.EquipmentBaselineConfig.TierBaseline(14,0,7,2.3,14,2.3,8.4,3.833333333,6,1,945);
        curve.put(AscendanceTiers.DORMANT.id(),earlier);curve.put(apexTier,apex);
        RuntimeEquipmentNormalization.apply(curve,evidence,BalanceSettings.defaults(),new TreeMap<>());
        for(var property:com.mistaboom.essence_ascendance.equipment.EquipmentBaselineProperty.values()) {
            double before=earlier.value(property)/apex.value(property);
            double after=curve.get(AscendanceTiers.DORMANT.id()).value(property)/curve.get(apexTier).value(property);
            double tolerance=property==com.mistaboom.essence_ascendance.equipment.EquipmentBaselineProperty.DURABILITY?1.0/curve.get(apexTier).durability():1e-12;
            check(Math.abs(before-after)<=tolerance,"Endpoint normalization reshaped the tier curve for "+property);
        }
        check(curve.get(AscendanceTiers.DORMANT.id()).harvestLevel()==1&&curve.get(apexTier).harvestLevel()==3,
                "Discrete harvest progression did not advance with equipment infusion");
        var unsafeReferences=new EnumMap<ProgressionBand,Map<CapabilityAxis,Double>>(ProgressionBand.class);
        unsafeReferences.putAll(evidence.frontiers());
        var unsafeApex=new EnumMap<CapabilityAxis,Double>(CapabilityAxis.class);unsafeApex.putAll(unsafeReferences.get(ProgressionBand.APEX));
        unsafeApex.put(CapabilityAxis.DURABILITY,4_294_967_300.0);unsafeReferences.put(ProgressionBand.APEX,unsafeApex);
        var unsafeEvidence=new PackEvidence(Map.of(),List.of(),List.of(),unsafeReferences,List.of(),List.of(),Map.of());
        rejected(()->RuntimeEquipmentNormalization.apply(new LinkedHashMap<>(curve),unsafeEvidence,BalanceSettings.defaults(),new TreeMap<>()),
                "Oversized provider durability wrapped into a small positive equipment value");
        var rows=List.of(
                reference("test:sword","mainhand_melee",Map.of(CapabilityAxis.SUSTAINED_DAMAGE,16.0,CapabilityAxis.ATTACK_RATE,2.0)),
                reference("test:bow","mainhand_bow",Map.of(CapabilityAxis.SUSTAINED_DAMAGE,6.0,CapabilityAxis.ATTACK_RATE,1.0)),
                reference("test:crossbow","mainhand_crossbow",Map.of(CapabilityAxis.SUSTAINED_DAMAGE,7.2,CapabilityAxis.ATTACK_RATE,.8)),
                reference("test:caster","mainhand_caster",Map.of(CapabilityAxis.SUSTAINED_DAMAGE,12.0,CapabilityAxis.ATTACK_RATE,3.0)),
                reference("test:hard_helmet","head",Map.of(CapabilityAxis.ARMOR,10.0,CapabilityAxis.TOUGHNESS,0.0)),
                reference("test:tough_helmet","head",Map.of(CapabilityAxis.ARMOR,1.0,CapabilityAxis.TOUGHNESS,10.0)));
        var paired=new PackEvidence(Map.of(),rows,List.of(),evidence.frontiers(),List.of(),List.of(),Map.of());
        var melee=RuntimeReferencePolicy.weapon(paired,ProgressionBand.APEX,"melee_shield","EXCLUDE_UNSUPPORTED");
        var ranged=RuntimeReferencePolicy.weapon(paired,ProgressionBand.APEX,"ranged","EXCLUDE_UNSUPPORTED");
        var caster=RuntimeReferencePolicy.weapon(paired,ProgressionBand.APEX,"caster","EXCLUDE_UNSUPPORTED");
        check(melee.damage()==8&&melee.rate()==2,"Melee reference inherited another family's cadence");
        check(ranged.damage()==9&&ranged.rate()==.8,"Ranged reference lost its winning weapon pairing");
        check(caster.damage()==4&&caster.rate()==3&&caster.familyObserved(),"Provider caster reference ignored");
        var armor=RuntimeReferencePolicy.armor(paired,ProgressionBand.APEX,"EXCLUDE_UNSUPPORTED",10);
        check(armor.armor()==10&&armor.toughness()==0,"Armor reference invented a set with incompatible slot maxima");
        var unguarded=RuntimeBalanceDefinition.generate(evidence,BalanceSettings.defaults(),BalanceOverrides.empty());
        check(unguarded.toJson().get("equipment").equals(generated.toJson().get("equipment")),
                "Offense/defense/healing calibration changed ordinary equipment parity");
        var baseline=generated.config().equipmentBaselineConfig().baselineFor(AscendanceTiers.TRANSCENDENT);
        for(var archetype:com.mistaboom.essence_ascendance.equipment.EquipmentProfileRegistry.values()) {
            if(archetype.baselineMultiplier(com.mistaboom.essence_ascendance.equipment.EquipmentBaselineProperty.MELEE_DAMAGE)<=0)continue;
            double hit=RuntimeBuildScenarios.equipmentValue(generated,baseline.meleeDamage(),archetype,
                    com.mistaboom.essence_ascendance.equipment.EquipmentBaselineProperty.MELEE_DAMAGE);
            double rate=RuntimeBuildScenarios.equipmentValue(generated,baseline.meleeAttackSpeed(),archetype,
                    com.mistaboom.essence_ascendance.equipment.EquipmentBaselineProperty.MELEE_ATTACK_SPEED);
            check(hit*rate<=baseline.meleeDamage()*baseline.meleeAttackSpeed()+1e-9,
                    "A default tool archetype multiplied the matching weapon's complete DPS budget");
        }
        var legacy=generated.toJson();legacy.getAsJsonObject("composition").remove("equipment_apex_parity");
        check(RuntimeBalanceDefinition.fromJson(legacy).toJson().equals(legacy),"Old cached equipment was silently renormalized");
    }
    private static EquipmentReference reference(String id,String slot,Map<CapabilityAxis,Double> axes) {
        return new EquipmentReference(id,slot,ProgressionBand.ENTRY,axes,List.of(),true,true,1,"Explicit test measurement");
    }
    private static void rejected(Runnable runnable,String message) {
        try { runnable.run(); } catch(RuntimeException expected) { return; }
        throw new AssertionError(message);
    }
    private static void check(boolean condition,String message) { if(!condition)throw new AssertionError(message); }
}
