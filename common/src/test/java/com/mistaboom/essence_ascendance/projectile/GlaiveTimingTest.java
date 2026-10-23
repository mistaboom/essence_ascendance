package com.mistaboom.essence_ascendance.projectile;

import com.mistaboom.essence_ascendance.equipment.EquipmentTier;
import com.mistaboom.essence_ascendance.equipment.EquipmentWeaponService;
import com.mistaboom.essence_ascendance.equipment.GlaiveTuning;
import com.mistaboom.essence_ascendance.equipment.WeaponActionGeometry.Knot;
import com.mistaboom.essence_ascendance.equipment.WeaponActionGeometry.Pose;
import com.mistaboom.essence_ascendance.equipment.WeaponActionPresentation;
import com.mistaboom.essence_ascendance.equipment.WeaponActionRuntime;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Pure timing contract: animation may accelerate while the server's attack-rate gate stays unchanged. */
public final class GlaiveTimingTest {
    private static int checks;

    private GlaiveTimingTest() { }

    public static void main(String[] args) {
        var swipe=GlaiveTuning.SWIPE;
        check(swipe.extensionTicks()==1 && swipe.outwardTicks()==5 && swipe.retractionTicks()==3,
                "One-tick extension and five-tick strike use the shared server clock");
        check(WeaponActionPresentation.Timeline.DELAY==1,
                "Presentation retains one solved interval without a separate slow windup");

        double range=10;
        near(GlaiveTuning.swipeTipDistance(range,0),com.mistaboom.essence_ascendance.equipment.WeaponActionGeometry.REST_TIP,
                "Blade starts at rest length");
        near(GlaiveTuning.swipeTipDistance(range,.5),
                (com.mistaboom.essence_ascendance.equipment.WeaponActionGeometry.REST_TIP+range)/2,
                "Physical extension progresses during the first tick");
        near(GlaiveTuning.swipeTipDistance(range,1),range,"Blade is fully extended when damage starts");
        near(GlaiveTuning.swipeTipDistance(range,2),range,"Blade length holds through the sweep");

        near(GlaiveTuning.swipeAngle(0),-60,"Windup begins at the arc boundary");
        near(GlaiveTuning.swipeAngle(1),-60,"Extension does not consume the damaging arc");
        near(GlaiveTuning.swipeAngle(3.5),0,"The strike passes through center mid-sweep");
        near(GlaiveTuning.swipeAngle(6),60,"The full 120-degree arc completes in five ticks");
        near(GlaiveTuning.swipeAngle(7),60,"Arc remains bounded after completion");
        double opening=GlaiveTuning.swipeAngle(2)-GlaiveTuning.swipeAngle(1);
        double middle=GlaiveTuning.swipeAngle(4)-GlaiveTuning.swipeAngle(3);
        double closing=GlaiveTuning.swipeAngle(6)-GlaiveTuning.swipeAngle(5);
        check(opening>0 && middle>opening && middle>closing && closing>0,
                "Arc accelerates through contact and settles instead of rotating uniformly");

        for (double actionsPerSecond:new double[]{.8,1.2,4,40,1000}) {
            int cycle=swipe.extensionTicks()+swipe.outwardTicks()+swipe.retractionTicks()
                    +swipe.recoveryTicks(actionsPerSecond);
            int previous=Math.max(14,(int)Math.ceil(EquipmentWeaponService.rateToTicks(actionsPerSecond)));
            check(cycle==previous,"Faster animation retains the full-strength attack cadence at "+actionsPerSecond);
        }

        var rest=new Pose(Vec3.ZERO,0,0,
                com.mistaboom.essence_ascendance.equipment.WeaponActionGeometry.REST_TIP,-60);
        var extended=rest.withDistance(range);
        var first=snapshot(0,WeaponActionRuntime.Phase.EXTEND,rest,rest);
        var timeline=new WeaponActionPresentation.Timeline(first,100);
        near(timeline.frame(100,0).blend(),0,"Receipt begins at native rest");
        near(timeline.frame(100,.5F).blend(),.5,"Arm reaches halfway through the short preparation");
        near(timeline.frame(101,0).blend(),1,"Arm reaches attack tilt after one buffered tick");
        timeline.add(snapshot(1,WeaponActionRuntime.Phase.SWEEP,rest,extended));
        var extending=timeline.frame(101,.5F);
        near(extending.pose().tipDistance(),(rest.tipDistance()+range)/2,
                "Blade extends promptly after the one-tick arm preparation");
        near(timeline.frame(102,0).pose().tipDistance(),range,
                "The full blade appears at the same solved damaging boundary");

        System.out.println("GlaiveTimingTest: "+checks+" onset, eased arc, authority and cadence assertions PASS");
    }

    private static WeaponActionRuntime.Snapshot snapshot(long tick,WeaponActionRuntime.Phase phase,Pose before,Pose after) {
        return new WeaponActionRuntime.Snapshot(1,WeaponActionRuntime.Mode.SWIPE,phase,0,14,tick,0,
                EquipmentTier.TRANSCENDENT,10,before,after,
                List.of(new Knot(0,before),new Knot(1,after)),false,-1);
    }

    private static void near(double actual,double expected,String message) {
        check(Math.abs(actual-expected)<1e-9,message+": "+actual+" != "+expected);
    }
    private static void check(boolean condition,String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
