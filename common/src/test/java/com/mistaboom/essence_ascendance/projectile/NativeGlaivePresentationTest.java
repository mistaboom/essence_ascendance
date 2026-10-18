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
        gripGeometry();
        continuousPlayback();
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
        check(GRIP_OFFSET>0 && GRIP_OFFSET<21*DIAGONAL_PIXEL && Math.abs(REST_TIP+GRIP_OFFSET-59*DIAGONAL_PIXEL)<1e-10,
                "Choked grip sits inside the authored handle without changing the total rest length");
    }
    private static void continuousPlayback() {
        var timeline=new WeaponActionPresentation.Timeline(timed(0,WeaponActionRuntime.Phase.SWEEP),100);
        timeline.add(timed(1,WeaponActionRuntime.Phase.SWEEP));
        var before=timeline.frame(102,.5F);
        check(Math.abs(before.pose().sweepDegrees()-5)<1e-9,"Buffered playback interpolates within the recorded interval");
        timeline.add(timed(2,WeaponActionRuntime.Phase.SWEEP));
        check(timeline.frame(102,.5F).equals(before),"New packets cannot restart the displayed interval");
        timeline.add(timed(3,WeaponActionRuntime.Phase.SWEEP));
        timeline.add(timed(4,WeaponActionRuntime.Phase.SWEEP));
        check(Math.abs(timeline.frame(103,.25F).pose().sweepDegrees()-12.5)<1e-9,
                "Batched packets retain intermediate sweep history instead of skipping it");
        var shortening=timed(3,WeaponActionRuntime.Phase.SWEEP);
        var knots=List.of(new Knot(0,shortening.previous()),new Knot(.5,shortening.current().withDistance(2)),new Knot(1,shortening.current()));
        var obstacle=new WeaponActionPresentation.Timeline(new WeaponActionRuntime.Snapshot(1,shortening.mode(),shortening.phase(),
                0,20,3,0,shortening.tier(),64,shortening.previous(),shortening.current(),knots,true,-1),103);
        check(obstacle.frame(104,.5F).pose().tipDistance()==2,"Timeline preserves collision minima inside a tick");
        timeline.add(timed(5,WeaponActionRuntime.Phase.RECOVERY));
        check(timeline.frame(106,.5F).phase()==WeaponActionRuntime.Phase.RETRACT,"Terminal packet finishes the recorded return before idle");
        check(timeline.frame(107,0).phase()==WeaponActionRuntime.Phase.RECOVERY,"Return completes at its actual endpoint");
        var idle=new WeaponActionPresentation.Timeline(timed(5,WeaponActionRuntime.Phase.IDLE),105);
        check(idle.frame(106,.5F).phase()==WeaponActionRuntime.Phase.RETRACT,"Ready-at-return idle packets also retain the final return interval");
        timeline.add(timed(6,WeaponActionRuntime.Phase.CANCELLED));
        check(timeline.frame(107,0).phase()==WeaponActionRuntime.Phase.CANCELLED,"Cancellation hides buffered geometry immediately");
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
    }
}
