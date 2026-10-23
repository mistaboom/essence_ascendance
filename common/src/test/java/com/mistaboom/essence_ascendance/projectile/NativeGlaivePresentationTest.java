package com.mistaboom.essence_ascendance.projectile;

import com.mistaboom.essence_ascendance.equipment.*;
import com.mistaboom.essence_ascendance.equipment.personalized.PersonalizedEquipmentDefinition;
import com.mistaboom.essence_ascendance.item.AscendanceItems;
import com.mistaboom.essence_ascendance.network.WeaponActionStatePayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import static com.mistaboom.essence_ascendance.equipment.WeaponActionGeometry.*;

/** Native material resolution, bounded wire geometry, untracked-owner reception and client hook shape. */
public final class NativeGlaivePresentationTest {
    private static int checks;
    public static void run() throws Exception {
        runPlaybackAndInput();
        var stack=new ItemStack(AscendanceItems.ASCENDANCE_GLAIVE.get());
        var profile=new PersonalizedEquipmentDefinition("presentation_fixture",UUID.randomUUID(),
                "essence_ascendance:ascendance_glaive","transcendent","Fixture",0x0d0967,0xb6f800,Map.of());
        var appearance=EquipmentAppearance.resolve(stack,Optional.of(profile));
        check(appearance.tint(0)==0x0d0967 && appearance.tint(1)==0xb6f800 && appearance.tint(2)==0xFFFFFF,
                "Fixture changes only base/accent; wood stays white-tinted");
        for (var tier:EquipmentTier.values()) {
            EquipmentTierData.setTier(stack,tier);
            var ordinary=EquipmentAppearance.resolve(stack,Optional.empty());
            check(ordinary.baseRgb()==EquipmentTierVisuals.primaryRgb(tier),"Normal grey palette "+tier);
            check(ordinary.accentRgb()==EquipmentTierVisuals.armorAccentRgb(tier),"Shared tier accent "+tier);
        }
        var longPose=new Pose(Vec3.ZERO,0,0,64,-60);
        var shortPose=longPose.withDistance(3).withSweep(0);
        var knots=List.of(new Knot(0,longPose),new Knot(.5,shortPose),new Knot(.5,shortPose.withDistance(2)),
                new Knot(1,longPose.withSweep(60)));
        var state=snapshot(1,knots);
        check(WeaponActionPresentation.sample(state,.5).tipDistance()==2,"Last duplicate-time shortening wins");
        check(WeaponActionPresentation.sample(state,.25).tipDistance()<34,"Playback visits shortening between long endpoints");
        var distant=snapshot(2,List.of(new Knot(0,new Pose(Vec3.ZERO,0,0,64))));
        check(WeaponActionStatePayload.near(distant,new Vec3(0,0,63),4),"Blade-near observer discovered 63 blocks from owner");
        check(!WeaponActionStatePayload.near(distant,new Vec3(10,0,63),4),"Segment interest excludes unrelated distant viewers");
        var many=new ArrayList<Knot>();
        for (int i=0;i<2000;i++) many.add(new Knot(i/1999.0,new Pose(Vec3.ZERO,i%2*90,0,i%2==0 ? 64:2)));
        var bounded=WeaponActionPresentation.wireKnots(many);
        check(bounded.size()==256 && bounded.stream().allMatch(k->k.pose().tipDistance()==2),
                "Pathological history has bounded conservative constraints");
        var dimension=ResourceLocation.withDefaultNamespace("overworld"); var id=UUID.randomUUID();
        var payload=new WeaponActionStatePayload(dimension,12345,id,state,appearance);
        var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),net.minecraft.core.RegistryAccess.EMPTY);
        try {
            WeaponActionStatePayload.CODEC.encode(buffer,new WeaponActionStatePayload(dimension,12345,id,snapshot(3,many),appearance));
            check(buffer.readableBytes()<19000,"Wire packet including stable grip roll stays below 19KB even for 2000 observed turns");
            var decoded=WeaponActionStatePayload.CODEC.decode(buffer);
            check(decoded.appearance().equals(appearance) && decoded.state().knots().equals(bounded),
                    "Wire preserves personalized appearance and conservative bounded history");
        } finally { buffer.release(); }
        WeaponActionStatePayload.clearClient(); WeaponActionStatePayload.accept(payload,100);
        check(WeaponActionStatePayload.presentations(dimension,100).size()==1,"Untracked owner accepted without native entity");
        check(!WeaponActionStatePayload.accept(payload,101)
                && WeaponActionStatePayload.presentation(id).receivedAt()==100,"Duplicate packets never restart playback");
        WeaponActionStatePayload.accept(new WeaponActionStatePayload(dimension,12345,id,snapshot(0,knots),appearance),101);
        check(WeaponActionStatePayload.presentations(dimension,101).iterator().next().state().actionId()==1,"Stale action rejected");
        check(WeaponActionStatePayload.presentations(ResourceLocation.withDefaultNamespace("the_nether"),102).isEmpty(),
                "Dimension change clears remote presentation");
        WeaponActionStatePayload.accept(payload,100);
        check(WeaponActionStatePayload.presentations(dimension,141).isEmpty(),"Lost terminal packet has bounded cleanup lifetime");
        WeaponActionStatePayload.clearClient();
        if (dev.architectury.platform.Platform.getEnvironment()==dev.architectury.utils.Env.CLIENT) Client.checkHooks();
        System.out.println("NativeGlaivePresentationTest: "+checks+" material, playback, interest, lifecycle and client hooks PASS");
    }
    /** Can run at a loaded title screen before 26.1.2 binds item components to a world registry. */
    public static void runPlaybackAndInput() throws Exception {
        gripGeometry();
        continuousPlayback();
        animationKeyframes();
        interruptedPlayback();
        if (dev.architectury.platform.Platform.getEnvironment()==dev.architectury.utils.Env.CLIENT) Client.checkRapidPresses();
        System.out.println("NativeGlaivePresentationTest: "+checks+" world-free playback and input checks PASS");
    }
    private static void gripGeometry() {
        for (var main:net.minecraft.world.entity.HumanoidArm.values()) for (double yaw:new double[]{0,90,179,270})
            for (double pitch:new double[]{-90,-45,0,45,90}) {
                var view=GlaiveGrip.frame(new Vec3(3,5,7),yaw,pitch);
                var parallel=GlaiveGrip.aim(view,main,null,16,null);
                check(parallel.pivot().subtract(view.pivot()).dot(view.right())*GlaiveGrip.sign(main)<0,
                        "Grip appears on anatomical main side in world camera basis");
                var shoulder=GlaiveGrip.shoulder(parallel,main,1);
                check(Math.abs(parallel.pivot().distanceTo(shoulder)-Math.sqrt(.25+1.0/256)*GlaiveGrip.MODEL_SCALE)<1e-8,
                        "Aiming preserves rigid native shoulder-to-hand length");
                check(parallel.forward().distanceTo(view.forward())<1e-8,"Empty-space aiming is parallel including vertical view");
                for (double range:new double[]{1,4,16,64}) {
                    Vec3 target=view.pivot().add(view.forward().scale(range));
                    var aimed=GlaiveGrip.aim(view,main,target,range,null);
                    var convergence=aimed.pivot().add(aimed.forward().scale(aimed.pivot().distanceTo(target)));
                    check(convergence.distanceTo(target)<1e-8,"Hand-origin line intersects actual camera target");
                    check(Math.abs(aimed.right().dot(aimed.forward()))<1e-8 && Math.abs(aimed.right().length()-1)<1e-8,
                            "Fixed-grip orientation remains orthonormal at vertical and close aim");
                    for (double sweep:new double[]{-60,0,60}) {
                        var pose=GlaiveGrip.sweep(aimed,sweep,main,1);
                        check(GlaiveGrip.shoulder(pose,main,1).distanceTo(GlaiveGrip.shoulder(aimed,main,1))<1e-8,
                                "Whole arm and shaft sweep about a fixed shoulder, not the wrist");
                        Vec3 nativePalm=pose.pivot().add(.07,-.1,.12);
                        var anchored=GlaiveGrip.atHand(pose,nativePalm);
                        check(anchored.pivot().equals(nativePalm) && anchored.tip().distanceTo(pose.tip())<1e-8,
                                "Native palm correction keeps both the exact grip and server-solved blade front");
                    }
                }
            }
        check(GRIP_OFFSET>0 && GRIP_OFFSET<21*DIAGONAL_PIXEL && Math.abs(REST_TIP+GRIP_OFFSET-60*DIAGONAL_PIXEL)<1e-10,
                "Choked grip sits inside the authored handle without changing the total rest length");
    }
    private static void continuousPlayback() {
        var timeline=new WeaponActionPresentation.Timeline(timed(0,WeaponActionRuntime.Phase.SWEEP),100);
        timeline.add(timed(1,WeaponActionRuntime.Phase.SWEEP));
        var before=timeline.frame(101,.5F);
        check(Math.abs(before.pose().sweepDegrees()-5)<1e-9,"Buffered playback interpolates within the recorded interval");
        timeline.add(timed(2,WeaponActionRuntime.Phase.SWEEP));
        check(timeline.frame(101,.5F).equals(before),"New packets cannot restart the displayed interval");
        timeline.add(timed(3,WeaponActionRuntime.Phase.SWEEP));
        timeline.add(timed(4,WeaponActionRuntime.Phase.SWEEP));
        check(Math.abs(timeline.frame(102,.25F).pose().sweepDegrees()-12.5)<1e-9,
                "Batched packets retain intermediate sweep history instead of skipping it");
        var shortening=timed(3,WeaponActionRuntime.Phase.SWEEP);
        var knots=List.of(new Knot(0,shortening.previous()),new Knot(.5,shortening.current().withDistance(2)),new Knot(1,shortening.current()));
        var obstacle=new WeaponActionPresentation.Timeline(new WeaponActionRuntime.Snapshot(1,shortening.mode(),shortening.phase(),
                0,20,3,0,shortening.tier(),64,shortening.previous(),shortening.current(),knots,true,-1),103);
        check(obstacle.frame(103,.5F).pose().tipDistance()==2,"Timeline preserves collision minima inside a tick");
        timeline.add(timed(5,WeaponActionRuntime.Phase.RECOVERY));
        check(timeline.frame(105,.5F).phase()==WeaponActionRuntime.Phase.RETRACT,"Terminal packet finishes the recorded return before idle");
        check(timeline.frame(106,0).phase()==WeaponActionRuntime.Phase.RECOVERY,"Return completes at its actual endpoint");
        var idle=new WeaponActionPresentation.Timeline(timed(5,WeaponActionRuntime.Phase.IDLE),105);
        check(idle.frame(105,.5F).phase()==WeaponActionRuntime.Phase.RETRACT,"Ready-at-return idle packets also retain the final return interval");
        timeline.add(timed(6,WeaponActionRuntime.Phase.CANCELLED));
        check(timeline.frame(106,0).phase()==WeaponActionRuntime.Phase.CANCELLED,"Cancellation hides buffered geometry immediately");
        for(int i=7;i<40;i++) timeline.add(timed(i,WeaponActionRuntime.Phase.SWEEP));
        check(timeline.size()==16,"Presentation history has a fixed memory bound");
        var early=timed(2,WeaponActionRuntime.Phase.RETRACT);
        var earlyStop=new WeaponActionRuntime.Snapshot(1,WeaponActionRuntime.Mode.STAB,early.phase(),0,20,2,0,
                early.tier(),16,early.previous(),early.current(),List.of(new Knot(0,early.previous()),new Knot(.25,early.current())),true,-1);
        var earlyTimeline=new WeaponActionPresentation.Timeline(earlyStop,102);
        check(earlyTimeline.retractAt()==1.25,"Body animation returns at the actual fractional early contact time");
        earlyTimeline.add(timed(3,WeaponActionRuntime.Phase.RETRACT));
        check(earlyTimeline.retractAt()==1.25,"Later retraction packets never restart the body return");
    }
    private static WeaponActionRuntime.Snapshot timed(long time,WeaponActionRuntime.Phase phase) {
        var before=new Pose(Vec3.ZERO,0,0,16,Math.max(0,time-1)*10);
        var after=before.withSweep(time*10);
        return new WeaponActionRuntime.Snapshot(1,WeaponActionRuntime.Mode.SWIPE,phase,0,20,time,0,
                EquipmentTier.TRANSCENDENT,64,before,after,List.of(new Knot(0,before),new Knot(1,after)),false,-1);
    }
    private static void animationKeyframes() {
        for (var mode:WeaponActionRuntime.Mode.values()) {
            var timing=mode==WeaponActionRuntime.Mode.STAB ? GlaiveTuning.STAB:GlaiveTuning.SWIPE;
            int outward=timing.extensionTicks()+timing.outwardTicks(),end=outward+timing.retractionTicks();
            var slow=new WeaponActionPresentation.Timeline(animationState(mode,0,500),100);
            var fast=new WeaponActionPresentation.Timeline(animationState(mode,0,end+1),100);
            check(slow.frame(100,0).blend()==0,"Preparation begins at native rest: "+mode);
            check(Math.abs(slow.frame(100,.5F).blend()-.5)<1e-6,"Preparation pivots within the first buffered tick: "+mode);
            check(slow.frame(101,0).blend()==1,"Full attack pose reached before damaging playback: "+mode);
            for (int t=1;t<=end+1;t++) { slow.add(animationState(mode,t,500));fast.add(animationState(mode,t,end+1)); }
            var active=slow.frame(101+outward-1,.5F);
            check(active.blend()==1 && active.pose().tipDistance()==8,
                    "Damage interval preserves the exact solved blade pose: "+mode);
            check(active.equals(fast.frame(101+outward-1,.5F)),"Recharge duration never changes active playback: "+mode);
            var returning=slow.frame(101+outward,1);
            check(returning.blend()>0 && returning.blend()<1,"Retraction interpolates back toward native rest: "+mode);
            var complete=slow.frame(101+end,.5F);
            check(complete.blend()==0 && complete.phase()==WeaponActionRuntime.Phase.RECOVERY,
                    "First recovery interval releases the final pose before readiness: "+mode);
            check(animationState(mode,end+1,500).readiness(end+1)<.1,"Fixture still has a long recharge remaining");
            for (int t=end+2;t<end+35;t++) {
                slow.add(animationState(mode,t,500));
                var idle=slow.frame(101+t-1,.5F);
                check(idle.phase()==WeaponActionRuntime.Phase.RECOVERY && idle.blend()==0,
                        "Later recovery packets cannot resurrect the attack, including after history eviction: "+mode+" tick "+t);
            }
            WeaponActionStatePayload.clearClient();
            var owner=UUID.randomUUID();var dimension=ResourceLocation.withDefaultNamespace("overworld");
            for(int t=0;t<=end+1;t++) WeaponActionStatePayload.accept(
                    new WeaponActionStatePayload(dimension,12345,owner,animationState(mode,t,end+1)),100+t);
            var start=animationState(mode,0,500);
            var next=new WeaponActionRuntime.Snapshot(2,mode,start.phase(),end+1,500,end+1,0,start.tier(),start.range(),
                    start.previous(),start.current(),start.knots(),false,-1);
            WeaponActionStatePayload.accept(new WeaponActionStatePayload(dimension,12345,owner,next),101+end);
            var nextAction=WeaponActionStatePayload.presentation(owner);
            var tail=nextAction.frame(101+end,0);
            check(tail.blend()==0,"Fast actions meet at the same native rest keyframe: "+mode);
            check(nextAction.frame(101+end,.5F).blend()>0 && nextAction.frame(102+end,0).blend()==1,
                    "Fast preparation uses the available gap without delaying damage playback: "+mode);
            WeaponActionStatePayload.clearClient();
        }
    }
    private static WeaponActionRuntime.Snapshot animationState(WeaponActionRuntime.Mode mode,int time,long ready) {
        var timing=mode==WeaponActionRuntime.Mode.STAB ? GlaiveTuning.STAB:GlaiveTuning.SWIPE;
        int outward=timing.extensionTicks()+timing.outwardTicks(),end=outward+timing.retractionTicks();
        var phase=time>=end ? WeaponActionRuntime.Phase.RECOVERY:time>=outward ? WeaponActionRuntime.Phase.RETRACT
                :mode==WeaponActionRuntime.Mode.STAB ? WeaponActionRuntime.Phase.OUTWARD
                :time<timing.extensionTicks() ? WeaponActionRuntime.Phase.EXTEND:WeaponActionRuntime.Phase.SWEEP;
        var pose=new Pose(Vec3.ZERO,0,0,time>outward ? REST_TIP+(8-REST_TIP)*Math.max(0,(double)(end-time)/timing.retractionTicks()):8);
        var previous=new Pose(Vec3.ZERO,0,0,time>outward ? REST_TIP+(8-REST_TIP)*Math.max(0,(double)(end-time+1)/timing.retractionTicks()):8);
        var knots=time>end ? List.of(new Knot(0,pose)):List.of(new Knot(0,previous),new Knot(1,pose));
        return new WeaponActionRuntime.Snapshot(1,mode,phase,0,ready,time,0,EquipmentTier.TRANSCENDENT,8,
                previous,pose,knots,false,-1);
    }
    private static void interruptedPlayback() {
        var dimension=ResourceLocation.withDefaultNamespace("overworld");
        for (var before:WeaponActionRuntime.Mode.values()) for (var after:WeaponActionRuntime.Mode.values()) {
            var timing=before==WeaponActionRuntime.Mode.STAB ? GlaiveTuning.STAB:GlaiveTuning.SWIPE;
            int outward=timing.extensionTicks()+timing.outwardTicks();
            for (int interruptedAt:new int[]{2,outward+1,outward+timing.retractionTicks()+1}) {
                WeaponActionStatePayload.clearClient();
                var owner=UUID.randomUUID();
                for (int tick=0;tick<=interruptedAt;tick++) WeaponActionStatePayload.accept(
                        new WeaponActionStatePayload(dimension,12345,owner,animationState(before,tick,500)),100+tick);
                var initial=animationState(after,0,500);
                var next=new WeaponActionRuntime.Snapshot(2,after,initial.phase(),interruptedAt,500,interruptedAt,0,
                        initial.tier(),initial.range(),initial.previous(),initial.current(),initial.knots(),false,-1);
                var packet=new WeaponActionStatePayload(dimension,12345,owner,next);
                check(WeaponActionStatePayload.accept(packet,100+interruptedAt),"New press replaces active/retracting/recharging "+before+" with "+after);
                var presentation=WeaponActionStatePayload.presentation(owner);
                check(presentation.frame(100+interruptedAt,0).blend()==0,"Restart begins its own preparation without finishing the old action");
                var midway=presentation.frame(100+interruptedAt,.5F);
                check(Math.abs(midway.blend()-.5)<1e-6,"Replacement prepares immediately, including interruption during return");
                check(!WeaponActionStatePayload.accept(packet,101+interruptedAt)
                        && presentation.frame(100+interruptedAt,.5F).equals(midway),"Duplicate replacement packet never restarts the new animation");
                check(presentation.frame(101+interruptedAt,0).blend()==1,"Replacement reaches its attack pose in one buffered tick");
                check(!WeaponActionStatePayload.accept(new WeaponActionStatePayload(dimension,12345,owner,
                        animationState(before,interruptedAt+1,500)),102+interruptedAt),"Late old-action packet cannot resurrect the interrupted swing");
                check(WeaponActionStatePayload.presentation(owner).state().actionId()==2,"Replacement keeps its identity after stale traffic");
            }
        }
        WeaponActionStatePayload.clearClient();
    }
    private static WeaponActionRuntime.Snapshot snapshot(long id,List<Knot> knots) {
        return new WeaponActionRuntime.Snapshot(id,WeaponActionRuntime.Mode.SWIPE,WeaponActionRuntime.Phase.SWEEP,
                0,20,4,0,EquipmentTier.TRANSCENDENT,64,knots.getFirst().pose(),knots.getLast().pose(),knots,true,-1);
    }
    private static void check(boolean result,String message) { checks++; if (!result) throw new AssertionError(message); }
    private static final class Client {
        static void checkHooks() throws Exception {
            for (var entry:Map.of("net.minecraft.client.renderer.ItemInHandRenderer","offhand",
                    "net.minecraft.client.renderer.entity.layers.ItemInHandLayer","heldModel",
                    "net.minecraft.client.renderer.entity.player.PlayerRenderer","glaiveFirstPersonVisibility",
                    "net.minecraft.client.renderer.entity.LivingEntityRenderer","glaiveShoulder").entrySet()) {
                check(Arrays.stream(Class.forName(entry.getKey()).getDeclaredMethods())
                        .anyMatch(m->m.getName().contains(entry.getValue())),"Transformed native client hook "+entry.getValue());
            }
        }
        private static void checkRapidPresses() throws Exception {
            var client=net.minecraft.client.Minecraft.getInstance();
            var type=client.getClass();
            var consume=Arrays.stream(type.getDeclaredMethods()).filter(m->m.getName().contains("pressConsumed")).findFirst().orElseThrow();
            consume.setAccessible(true);
            var clicks=net.minecraft.client.KeyMapping.class.getDeclaredField("clickCount");
            clicks.setAccessible(true);
            com.llamalad7.mixinextras.injector.wrapoperation.Operation<Boolean> nativeConsume=
                    arguments->((net.minecraft.client.KeyMapping)arguments[0]).consumeClick();
            for (String name:List.of("attackLatched","useLatched")) {
                var field=Arrays.stream(type.getDeclaredFields()).filter(f->f.getName().endsWith(name)).findFirst().orElseThrow();
                field.setAccessible(true);
                var key=name.equals("attackLatched") ? client.options.keyAttack:client.options.keyUse;
                boolean originalLatch=(boolean)field.get(client);
                int originalClicks=(int)clicks.get(key),originalJumpClicks=(int)clicks.get(client.options.keyJump);
                try {
                    clicks.setInt(key,3);
                    for (int click=0;click<3;click++) {
                        field.setBoolean(client,true);
                        check((boolean)consume.invoke(client,key,nativeConsume) && !(boolean)field.get(client)
                                        && (int)clicks.get(key)==2-click,
                                "Every consumed native click releases "+name+" even within one tick");
                    }
                    field.setBoolean(client,true);
                    check(!(boolean)consume.invoke(client,key,nativeConsume) && (boolean)field.get(client),
                            "Holding a key without another click preserves the one-press latch");
                    clicks.setInt(client.options.keyJump,1);
                    check((boolean)consume.invoke(client,client.options.keyJump,nativeConsume) && (boolean)field.get(client),
                            "Unrelated clicks cannot release the combat latch");
                } finally {
                    field.setBoolean(client,originalLatch);
                    clicks.setInt(key,originalClicks);
                    clicks.setInt(client.options.keyJump,originalJumpClicks);
                }
            }
        }
    }
}
