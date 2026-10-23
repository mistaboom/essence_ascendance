package com.mistaboom.essence_ascendance.balance.runtime;

import com.google.gson.*;
import com.mistaboom.essence_ascendance.archive.*;
import com.mistaboom.essence_ascendance.balance.config.*;
import com.mistaboom.essence_ascendance.balance.engine.*;
import com.mistaboom.essence_ascendance.client.presentation.*;
import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import com.mistaboom.essence_ascendance.equipment.*;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import com.mistaboom.essence_ascendance.gathering.*;
import com.mistaboom.essence_ascendance.movement.TraversalRules;
import com.mistaboom.essence_ascendance.network.*;
import com.mistaboom.essence_ascendance.progression.*;
import com.mistaboom.essence_ascendance.skill.*;
import com.mistaboom.essence_ascendance.skill.balance.*;
import com.mistaboom.essence_ascendance.stat.*;
import com.mistaboom.essence_ascendance.tier.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import io.netty.buffer.Unpooled;
import java.nio.file.*;
import java.util.*;

/** Contract tests exercise published profiles, accounting, codecs and actual force/countdown consumers. */
public final class FishingRodBalanceTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        Thread.currentThread().setUncaughtExceptionHandler((t, e) -> e.printStackTrace(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.err))));
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); EssenceTypes.init(); AscendanceTiers.init(); EssenceStats.init();
        MilestoneProviders.init(); Milestones.init(); Skills.init(); AscendanceAdvancements.init(); EquipmentProfiles.init();
        var runtime = RuntimeBalanceDefinition.bootstrap();
        var track = runtime.config().balanceProfile().bonusTrack(EssenceStats.FISHING_SPEED.id());
        check(track.applicability() == BonusTrackDefinition.Applicability.AVAILABLE && track.purchaseStyle() == BonusTrackDefinition.PurchaseStyle.FUNDED_STATES, "Fishing Speed publishes funded states");
        double priorEffect = 0; long priorCap = 0;
        for (var point : track.checkpoints()) {
            if (!point.purchasable()) continue;
            double value = track.maximumEffect() * point.effectFraction();
            check(value - priorEffect >= 10 - 1e-8 && point.segmentCost() > 0 && point.cumulativeCap() > priorCap, "Every fishing state is meaningful and paid");
            priorEffect = value; priorCap = point.cumulativeCap();
        }
        var delayed = new ResourceEvidence("essence_ascendance:ascendance_fishing_rod", ProgressionBand.LATE,
                Availability.RENEWABLE_MANUAL, Automation.NONE, true, false, 4, .9, List.of(), List.of());
        var seed = RuntimeReferencePolicy.bootstrapEvidence();
        var evidence = new PackEvidence(Map.of(delayed.itemId(), delayed), List.of(), List.of(), seed.frontiers(), List.of(), List.of(), Map.of());
        var delayedRuntime = RuntimeReferencePolicy.withBootstrapReferences(() -> RuntimeBalanceDefinition.generate(evidence, BalanceSettings.defaults(), null));
        var delayedTrack = delayedRuntime.config().balanceProfile().bonusTrack(EssenceStats.FISHING_SPEED.id());
        check(delayedTrack.startTier().equals(track.startTier()), "Acquisition labels do not reposition generated Fishing Speed");
        var blocked = new ResourceEvidence(delayed.itemId(), ProgressionBand.LATE, Availability.UNKNOWN, Automation.NONE, false, false, 4, .9, List.of(), List.of());
        var blockedEvidence = new PackEvidence(Map.of(blocked.itemId(), blocked), List.of(), List.of(), seed.frontiers(), List.of(), List.of(), Map.of());
        var blockedRuntime = RuntimeReferencePolicy.withBootstrapReferences(() -> RuntimeBalanceDefinition.generate(blockedEvidence, BalanceSettings.defaults(), null));
        check(blockedRuntime.config().balanceProfile().bonusTrack(EssenceStats.FISHING_SPEED.id()).equals(delayedTrack), "Unknown source access does not change the native Fishing Speed progression");
        check(BonusSemantics.require(EssenceStats.FISHING_SPEED).axis() == CapabilityAxis.FISHING_TIME_REDUCTION, "Fishing has legitimate native classification");
        check(EquipmentProfiles.FISHING_ROD.statApplicability(EquipmentActivationType.HELD).keySet().equals(Set.of(
                EssenceStats.LUCK.id(), EssenceStats.EXPERIENCE_GAIN.id(), EssenceStats.DURABILITY_EFFICIENCY.id(), EssenceStats.PROJECTILE_SPEED.id(), EssenceStats.ATTACK_KNOCKBACK.id(), EssenceStats.FISHING_SPEED.id())), "Exactly six rod channels");
        var data = new PlayerEssenceData(); data.setTier(AscendanceTiers.TRANSCENDENT);
        EssenceConfigManager.install(runtime);
        long cap = track.checkpoints().getLast().cumulativeCap();
        Map<ResourceLocation,Long> targets = new LinkedHashMap<>(); EssenceStatRegistry.values().forEach(s -> targets.put(s.id(), 0L)); targets.put(track.statId(), cap);
        var purchase = BonusTransactionPlan.resolve(data, runtime.config().balanceProfile(), targets);
        check(purchase.accepted() && purchase.plan().spending().get(EssenceTypes.GATHERING.id()) == cap, "Shared purchase charges exact generated cost");
        check(purchase.plan().settle(Map.of(EssenceTypes.GATHERING.id(), cap), Map.of(), Map.of()).accepted(), "Shared transaction settles");
        data.setInvested(EssenceStats.FISHING_SPEED, cap);
        targets.put(track.statId(), 0L);
        var refund = BonusTransactionPlan.resolve(data, runtime.config().balanceProfile(), targets);
        check(refund.accepted() && refund.plan().refunds().get(EssenceTypes.GATHERING.id()) == cap, "Refund uses actual invested amount");
        var restored = PlayerEssenceData.load(data.save());
        check(restored.getInvested(EssenceStats.FISHING_SPEED) == cap, "Fishing investment survives player serialization");
        check(EquipmentFishingRodService.resolve(data, EquipmentTier.LATENT, false).fishingMultiplier() == 1, "Latent rod never channels");
        check(EquipmentFishingRodService.resolve(data, EquipmentTier.TRANSCENDENT, true).fishingMultiplier() == 1, "Fractured rod never channels");
        assertPresentation(runtime, data);
        var changedJson = runtime.toJson();
        for (var row : changedJson.getAsJsonObject("fishingRod").getAsJsonObject("tiers").entrySet()) {
            var values = row.getValue().getAsJsonObject();
            for (String key : List.of("range", "castSpeed", "acceleration", "maximumForce", "reelSpeed")) values.addProperty(key, values.get(key).getAsDouble() * 1.15);
            values.addProperty("durability", values.get("durability").getAsInt() + 17);
        }
        changedJson.getAsJsonObject("statMaxBonuses").addProperty(track.statId().toString(), track.maximumEffect() + 20);
        changedJson.getAsJsonObject("balanceProfile").getAsJsonObject("bonusTracks").getAsJsonObject(track.statId().toString()).addProperty("maximumEffect", track.maximumEffect() + 20);
        for (var element : changedJson.getAsJsonObject("skillCurves").getAsJsonObject(SkillIds.FISHING_INSTINCT.toString()).getAsJsonArray("ranks")) {
            var parameters=element.getAsJsonObject().getAsJsonObject("parameters");
            String key="gathering/fishingInstinct/biteSpeedMultiplier";
            parameters.addProperty(key,parameters.get(key).getAsDouble()*1.1);
        }
        var firstParameters=changedJson.getAsJsonObject("skillCurves").getAsJsonObject(SkillIds.FISHING_INSTINCT.toString()).getAsJsonArray("ranks").get(0).getAsJsonObject().getAsJsonObject("parameters");
        changedJson.getAsJsonObject("effects").getAsJsonObject("gathering").getAsJsonObject("fishingInstinct").add("biteSpeedMultiplier",firstParameters.get("gathering/fishingInstinct/biteSpeedMultiplier"));
        var changed = RuntimeBalanceDefinition.fromJson(changedJson).withContentIdentity();
        EssenceConfigManager.install(changed); assertPresentation(changed, data);
        check(EquipmentFishingRodService.resolve(data, EquipmentTier.TRANSCENDENT, false).fishingMultiplier() > 1 + track.maximumEffect()/100, "Non-default profile changes actual fishing effect");
        var wire = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        RuntimeBalancePayload.CODEC.encode(wire, changed.networkPayload());
        var decoded = RuntimeBalanceDefinition.fromJson(JsonParser.parseString(RuntimeBalancePayload.CODEC.decode(wire).runtimeJson()).getAsJsonObject()); wire.release();
        check(decoded.toJson().equals(changed.toJson()), "Complete rod/bonus/renamed-skill runtime survives actual network codec");
        Path output = Path.of(System.getProperty("essence.test.buildDirectory", "../build"), "fishing-verification").toAbsolutePath().normalize(); Files.createDirectories(output);
        Files.writeString(output.resolve("bootstrap-runtime.json"), runtime.toJson().toString());
        Files.writeString(output.resolve("changed-runtime.json"), changed.toJson().toString());
        var restart = RuntimeBalanceDefinition.fromJson(JsonParser.parseString(Files.readString(output.resolve("changed-runtime.json"))).getAsJsonObject());
        EssenceConfigManager.reset(); EssenceConfigManager.install(restart); assertPresentation(restart, restored);
        check(restart.config().fishingRodBalance().equals(changed.config().fishingRodBalance()), "Saved restart installs published rod values");
        physics(restart); surface();
        check(!restart.toJson().toString().contains("water_" + "walking") && SkillRegistry.get(ResourceLocation.parse("essence_ascendance:surface_tension")).isPresent(), "Complete internal rename without alias");
        var binary = restart.skillCurves().get(SkillIds.SURFACE_TENSION.toString());
        check(binary.maximumRank() == 1 && binary.ranks().size() == 1, "Surface Tension remains a binary generated skill");
        for (var id : List.of(SkillIds.FISHING_INSTINCT, SkillIds.FISHERS_CALL, SkillIds.SURFACE_TENSION)) {
            var projection = SkillPresentationData.project(SkillRegistry.require(id), PresentationContext.catalog(restart));
            var curve = restart.skillCurves().get(id.toString());
            check(projection.maximumRank() == curve.maximumRank(), "Skill maximum rank is generated");
            for (var rank : projection.ranks()) check(rank.cost() == curve.ranks().get(rank.rank()-1).cost() && rank.requiredTier().equals(SkillBalanceRuntime.requiredTier(SkillRegistry.require(id), curve.requiredTierId(), rank.rank())), "Shared rank prices/gates agree");
            if(id.equals(SkillIds.FISHING_INSTINCT)) for(var rank:projection.ranks()) {
                var actual=SkillRankEffectScaling.applyResolved(restart.config().skillEffects(),Map.of(id,rank.rank()),restart.skillCurves());
                check(rank.effects().getFirst().metrics().stream().anyMatch(m->Math.abs(m.rawValue()-(actual.gathering().fishingInstinct().biteSpeedMultiplier()-1))<1e-9 || Math.abs(m.rawValue()-actual.gathering().fishingInstinct().biteSpeedMultiplier())<1e-9),"Changed skill rank preview consumes actual generated parameters");
            }
        }
        Files.writeString(output.resolve("bootstrap-fishing.json"), FishingBalanceDiagnostics.project(runtime).toString());
        Files.writeString(output.resolve("changed-fishing.json"), FishingBalanceDiagnostics.project(restart).toString());
        EssenceConfigManager.reset(); System.out.println("FishingRodBalanceTest: " + checks + " profile, transaction, presentation, synchronization, native countdown, force and surface checks PASS");
    }
    private static void assertPresentation(RuntimeBalanceDefinition runtime, PlayerEssenceData data) {
        var projection = EquipmentPresentationData.project(PresentationContext.catalog(runtime));
        for (var tier : projection.tierBaselines()) {
            var gameplay = EquipmentFishingRodService.resolve(data, tier.tier(), false);
            check(tier.fishingRod().equals(gameplay.baseline()), "Runtime and shared equipment reference agree for " + tier.tier());
            var tooltip = EquipmentTooltipSyncService.fishingRod(data, tier.tier(), false);
            check(tooltip.lines().stream().anyMatch(l -> l.translationKey().endsWith("rod.range")
                    && Math.abs(Double.parseDouble(l.arguments().getFirst())-gameplay.baseline().range())<=.000500001), "Item tooltip uses actual range at display precision");
            for (var stat : Map.of("rod.cast", gameplay.castSpeed(), "rod.gravity", gameplay.baseline().gravity(),
                    "rod.force", gameplay.baseline().maximumForce(), "rod.reel_speed", gameplay.baseline().reelSpeed()).entrySet())
                check(tooltip.lines().stream().anyMatch(l -> l.translationKey().endsWith(stat.getKey()) && l.arguments().size()==1
                        && Math.abs(Double.parseDouble(l.arguments().getFirst())-stat.getValue())<=.000500001
                        && l.arguments().getFirst().matches("-?\\d+(\\.\\d{1,3})?")), "Compact tooltip rounds the actual "+stat.getKey()+" to at most three decimals");
            check(tooltip.lines().stream().noneMatch(l -> l.translationKey().contains("rod.next") || l.translationKey().endsWith("rod.durability")),
                    "Rod tooltip omits next-tier previews and leaves numeric durability to the advanced tooltip");
        }
        var bonus = BonusPresentationData.project(EssenceStats.FISHING_SPEED, PresentationContext.catalog(runtime));
        var track = runtime.config().balanceProfile().bonusTrack(EssenceStats.FISHING_SPEED.id());
        check(bonus.maximumEffect() == track.maximumEffect() && bonus.startTier().equals(track.startTier()) && bonus.completionTier().equals(track.completionTier()), "Bonus preview span and maximum agree");
        for (String path : List.of("reference/equipment/fishing_rod", "reference/bonuses/fishing_speed", "reference/skills/surface_tension", "guide/equipment/fishing")) {
            var entry = ArchiveCatalog.DEFAULT.entry(ResourceLocation.parse("essence_ascendance:" + path));
            check(entry != null && !entry.content().get().searchableText().isEmpty(), "Feature discoverable in shared Archive: " + path);
        }
        var article = ArchiveCatalog.DEFAULT.entry(ResourceLocation.parse("essence_ascendance:reference/equipment/fishing_rod")).content().get();
        String range = java.math.BigDecimal.valueOf(runtime.config().fishingRodBalance().tier(EquipmentTier.TRANSCENDENT).range()).stripTrailingZeros().toPlainString();
        check(article.searchableText().stream().anyMatch(c -> c.getString().equals(range)), "Archive contains actual generated range");
    }
    private static void physics(RuntimeBalanceDefinition runtime) {
        var policy = runtime.config().fishingRodBalance(); var tier = policy.tier(EquipmentTier.TRANSCENDENT);
        var state = new RodAttachmentState(); check(!state.press() && !state.release() && state.press(), "Separate cast release and pull press");
        Vec3 velocity = Vec3.ZERO, position = Vec3.ZERO, anchor = new Vec3(40, 0, 0);
        for (int tick = 0; tick < 500; tick++) {
            Vec3 inward = anchor.subtract(position), force = RodPullMath.force(inward, velocity, new Vec3(-1, 1, 0), tier, tick, policy, 1, 0);
            check(Double.isFinite(force.lengthSqr()) && force.dot(inward) >= -1e-9, "Steering always pulls inward");
            check(force.length() <= (tick<policy.forceRampTicks() ? Math.max(tier.reelSpeed(),tier.maximumForce()) : tier.maximumForce()) + 1e-9, "Bounded opening impulse and sustained acceleration");
            velocity = velocity.add(force); position = position.add(velocity); velocity = velocity.scale(.91);
        }
        check(position.distanceTo(anchor) < 1, "Sustained hauling settles near target");
        Vec3 earned = new Vec3(1.7, .2, -.4); check(state.release() && earned.equals(new Vec3(1.7,.2,-.4)), "Release changes only attachment state");
        check(RodPullMath.force(Vec3.ZERO, earned, earned, tier, 100, policy, 1, 0).equals(Vec3.ZERO), "Coincident positions safe");
        check(RodPullMath.force(new Vec3(20,0,0), Vec3.ZERO, null, tier, 100, policy, 1, 1).equals(Vec3.ZERO), "Full knockback resistance respected");
        check(RodPullMath.force(new Vec3(20,0,0), new Vec3(10,0,0), null, tier, 100, policy, 1, 0).equals(Vec3.ZERO), "Speed limit never deletes pre-existing momentum");
        double eyeY=0, vertical=0;
        for(int tick=0;tick<400;tick++) {
            Vec3 force=RodPullMath.anchorForce(new Vec3(0,5-eyeY,0),new Vec3(0,vertical,0),null,tier,tick,policy,.08);
            check(force.length()<=(tick<policy.forceRampTicks() ? Math.max(tier.reelSpeed(),tier.maximumForce()) : tier.maximumForce())+1e-9,"Gravity support shares the current impulse or sustained force ceiling");
            vertical+=force.y;eyeY+=vertical;vertical=(vertical-.08)*.98;
        }
        check(Math.abs(eyeY-5)<.5 && Math.abs(vertical)<.15,"Continuous gravity-relative pull settles into hanging");
        var delta=new RodForcePayload(ResourceLocation.parse("minecraft:overworld"),42,7,new Vec3(.03,.08,-.01));
        var wire=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);RodForcePayload.CODEC.encode(wire,delta);
        check(RodForcePayload.CODEC.decode(wire).equals(delta),"Incremental force crosses native packet codec exactly once");wire.release();
        for (var row : policy.tiers().values()) {
            Vec3 toward = new Vec3(20,0,0);
            double initial = RodPullMath.force(toward, Vec3.ZERO, null, row, 0, policy, 1, 0).length();
            double steady = RodPullMath.force(toward, Vec3.ZERO, null, row, 40, policy, 1, 0).length();
            check(initial>steady && initial<=row.reelSpeed(), "Every tier starts with a stronger hook-setting tug within reel speed");
            check(RodPullMath.anchorForce(toward,Vec3.ZERO,null,row,0,policy,.08).length()>steady, "Block anchors receive the same stronger initial tug");
            double second=RodPullMath.force(toward,Vec3.ZERO,null,row,1,policy,1,0).length();
            check(second<initial && second>steady, "Opening force continues beyond one tick and tapers toward the existing reel force");
            check(RodPullMath.force(new Vec3(.1,0,0),Vec3.ZERO,null,row,0,policy,1,0).length()<=.025+1e-9, "Near-target hook-setting remains gentle");
            check(RodPullMath.force(toward,Vec3.ZERO,null,row,0,policy,1,1).equals(Vec3.ZERO), "Initial tug respects full control resistance");
            check(RodPullMath.force(toward,new Vec3(row.reelSpeed()+1,0,0),null,row,0,policy,1,0).equals(Vec3.ZERO), "Initial tug preserves faster existing momentum");
            for (var look : List.of(new Vec3(0,1,0),new Vec3(0,-1,0),new Vec3(0,0,1),new Vec3(0,0,-1))) {
                Vec3 launch=RodPullMath.force(toward,Vec3.ZERO,look,row,0,policy,1,0);
                check(launch.dot(look)>launch.x && launch.x>0, "Opening target tug follows vertical/sideways aim strongly while retaining inward draw");
                Vec3 anchorLaunch=RodPullMath.anchorForce(toward,Vec3.ZERO,look,row,0,policy,.08);
                check(anchorLaunch.dot(look)>anchorLaunch.x && anchorLaunch.x>0, "Player grapple uses the same opening aim steering");
                Vec3 middle=RodPullMath.force(toward,Vec3.ZERO,look,row,policy.forceRampTicks()/2,policy,1,0);
                check(middle.dot(look)>0 && middle.dot(look)/middle.x<launch.dot(look)/launch.x, "Steering lasts through the opening burst and fades");
                Vec3 reel=RodPullMath.force(toward,Vec3.ZERO,look,row,policy.forceRampTicks(),policy,1,0);
                check(reel.equals(RodPullMath.force(toward,Vec3.ZERO,null,row,policy.forceRampTicks(),policy,1,0)), "Sustained reel pulls straight inward independently of view direction");
            }
        }
        final int[] countdown = {100}; double carry = 0;
        for (int tick=0;tick<25;tick++) { int before=countdown[0]; countdown[0]--; carry=GatheringFishingService.accelerateNativeProgress(before,countdown[0],carry,2, v->countdown[0]=v); }
        check(countdown[0] == 50, "Speed multiplies actual native progress once");
        GatheringFishingService.accelerateNativeProgress(2,1,0,1000,v->countdown[0]=v); check(countdown[0] == 1, "Native phase transition is preserved");
        check(RodPullMath.skiingStep(Vec3.ZERO,new Vec3(3,0,0),true,true,false,false,4)==3 && RodPullMath.skiingStep(Vec3.ZERO,new Vec3(0,3,0),true,true,false,false,4)==0, "Skiing counts horizontal water pulling only");
        for (int mask=0; mask<4; mask++) check(RodPullMath.skiingStep(Vec3.ZERO,new Vec3(3,0,0),mask!=0,mask!=1,mask==2,mask==3,4)==0, "Skiing excludes other states");
    }
    private static void surface() {
        for (boolean rod : List.of(false,true)) for(boolean sprint : List.of(false,true)) for(boolean shift : List.of(false,true))
            check(TraversalRules.surfaceEnabled(true,true,sprint,rod,shift)==((rod||sprint)&&!shift), "Rod/sprint/Shift surface truth table");
        check(TraversalRules.rodBuoyancy(true,true,true,false,false,.2,1.82,.888), "Head-above-fluid partial immersion buoys");
        check(!TraversalRules.rodBuoyancy(true,true,true,false,true,.2,1.82,.888), "Submerged eyes never buoy");
        check(!TraversalRules.rodBuoyancy(false,true,true,false,false,.2,1.82,.888), "Rod alone grants nothing");
        check(!TraversalRules.rodBuoyancy(true,true,true,true,false,.2,1.82,.888), "Shift overrides buoyancy");
        check(!TraversalRules.rodBuoyancy(true,true,false,false,false,.2,1.82,.888), "Removing rod stops buoyancy");
    }
    private static void check(boolean ok, String why) { checks++; if(!ok) throw new AssertionError(why); }
}
