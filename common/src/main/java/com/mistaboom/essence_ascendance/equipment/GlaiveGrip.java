package com.mistaboom.essence_ascendance.equipment;

import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import static com.mistaboom.essence_ascendance.equipment.WeaponActionGeometry.*;

/** Shared physical shoulder/rigid-grip geometry. The client fits the native arm to the solved blade front. */
public final class GlaiveGrip {
    private GlaiveGrip() { }
    public static Pose frame(Vec3 eye,double yaw,double pitch) { return new Pose(eye,yaw,pitch,REST_TIP); }
    public static int sign(HumanoidArm arm) { return arm==HumanoidArm.RIGHT ? 1:-1; }
    public static final double MODEL_SCALE=.9375;
    public static final double ATTACK_TILT=45;
    private static final double SHOULDER_BELOW_EYE=1.62-(1.501-2.0/16)*MODEL_SCALE;
    public static Vec3 origin(Pose view,HumanoidArm main) {
        return origin(view,view.yaw(),main,1);
    }
    public static Vec3 origin(Player player,float partial,Pose view) {
        double body=net.minecraft.util.Mth.rotLerp(partial,player.yBodyRotO,player.yBodyRot);
        return origin(view,body,player.getMainArm(),player.getScale());
    }
    private static Vec3 origin(Pose view,double bodyYaw,HumanoidArm main,double scale) {
        return rigidAim(view,bodyYaw,main,scale,null,REST_TIP,null).pivot();
    }
    public static Pose rest(Pose view,HumanoidArm main) {
        return new Pose(origin(view,main),view.yaw(),view.pitch()-30,REST_TIP);
    }
    public static Pose rest(Player player,float partial,Pose view) {
        return new Pose(origin(player,partial,view),view.yaw(),view.pitch()-30,REST_TIP);
    }
    /** A missing target is truly parallel. A finite hit converges from the held grip onto the eye ray. */
    public static Pose aim(Pose view,HumanoidArm main,Vec3 target,double distance,Pose previous) {
        return rigidAim(view,view.yaw(),main,1,target,distance,previous);
    }
    public static Pose rigidAim(Pose view,double bodyYaw,HumanoidArm main,double scale,Vec3 target,double distance,Pose previous) {
        Vec3 shoulder=view.pivot().add(frame(view.pivot(),bodyYaw,0).right().scale(-sign(main)*5.0/16*MODEL_SCALE*scale))
                .add(0,-SHOULDER_BELOW_EYE*scale,0);
        double angle=Math.toRadians(ATTACK_TILT);
        Vec3 shaft=new Vec3(0,Math.sin(angle),-Math.cos(angle));
        Vec3 palm=new Vec3(-sign(main)/16.0,.5,0).scale(MODEL_SCALE*scale);
        Vec3 localDirection,worldDirection;
        if(target==null) { localDirection=shaft;worldDirection=view.forward(); }
        else {
            Vec3 delta=target.subtract(shoulder);
            double along=palm.dot(shaft),perpendicular=palm.lengthSqr()-along*along;
            double reach=Math.max(0,-along+Math.sqrt(Math.max(0,delta.lengthSqr()-perpendicular)));
            localDirection=palm.add(shaft.scale(reach)).normalize();
            worldDirection=delta.lengthSqr()<1e-12 ? view.forward():delta.normalize();
        }
        Vec3 localRight=new Vec3(1,0,0).subtract(localDirection.scale(localDirection.x)).normalize();
        Vec3 worldRight=view.right().subtract(worldDirection.scale(view.right().dot(worldDirection))).normalize();
        if(worldRight.lengthSqr()<.5) worldRight=view.up().cross(worldDirection).normalize();
        Vec3 localUp=localDirection.cross(localRight),worldUp=worldDirection.cross(worldRight);
        Vec3 grip=shoulder.add(rotate(palm,localRight,localUp,localDirection,worldRight,worldUp,worldDirection));
        Vec3 forward=rotate(shaft,localRight,localUp,localDirection,worldRight,worldUp,worldDirection);
        Vec3 right=rotate(new Vec3(1,0,0),localRight,localUp,localDirection,worldRight,worldUp,worldDirection);
        Pose aimed=aim(view,grip,grip.add(forward),distance,previous);
        Pose basis=new Pose(grip,aimed.yaw(),aimed.pitch(),distance);
        double roll=Math.toDegrees(Math.atan2(right.dot(basis.up()),right.dot(basis.right())));
        return new Pose(grip,aimed.yaw(),aimed.pitch(),distance,0,unwrapYaw(previous==null ? 0:previous.roll(),roll));
    }
    private static Vec3 rotate(Vec3 value,Vec3 x,Vec3 y,Vec3 z,Vec3 a,Vec3 b,Vec3 c) {
        return a.scale(value.dot(x)).add(b.scale(value.dot(y))).add(c.scale(value.dot(z)));
    }
    public static Vec3 shoulder(Pose weapon,HumanoidArm main,double scale) {
        return weapon.pivot().subtract(armOffset(weapon,main,scale));
    }
    private static Vec3 armOffset(Pose weapon,HumanoidArm main,double scale) {
        double angle=Math.toRadians(ATTACK_TILT);
        return weapon.right().scale(-sign(main)/16.0).add(weapon.up().scale(-.5*Math.cos(angle)))
                .add(weapon.forward().scale(.5*Math.sin(angle))).scale(MODEL_SCALE*scale);
    }
    /** Rotate the entire arm/weapon assembly around its shoulder, keeping the wrist angle fixed. */
    public static Pose sweep(Pose aim,double angle,HumanoidArm main,double scale) {
        Vec3 shoulder=shoulder(aim,main,scale);
        Pose turned=aim.withSweep(angle);
        return new Pose(shoulder.add(armOffset(turned,main,scale)),turned.yaw(),turned.pitch(),turned.tipDistance(),
                turned.sweepDegrees(),turned.roll());
    }
    public static Pose aim(Pose view,Vec3 origin,Vec3 target,double distance,Pose previous) {
        Vec3 forward=target==null ? view.forward():target.subtract(origin).normalize();
        if (forward.lengthSqr()<.5) forward=view.forward();
        double yaw=Math.toDegrees(Math.atan2(-forward.x,forward.z));
        double pitch=Math.toDegrees(Math.asin(Math.clamp(-forward.y,-1,1)));
        Pose basis=new Pose(origin,yaw,pitch,distance);
        // Project the camera's lateral axis into the converged plane, preserving roll even at vertical aim.
        Vec3 right=view.right().subtract(forward.scale(view.right().dot(forward))).normalize();
        if (right.lengthSqr()<.5) right=view.up().cross(forward).normalize();
        double roll=Math.toDegrees(Math.atan2(right.dot(basis.up()),right.dot(basis.right())));
        return new Pose(origin,unwrapYaw(previous==null ? view.yaw():previous.yaw(),yaw),pitch,distance,0,
                unwrapYaw(previous==null ? 0:previous.roll(),roll));
    }
    /** Move the visible grip onto the animated palm without moving the server-solved blade front. */
    public static Pose atHand(Pose solved,Vec3 hand) {
        if (solved.pivot().distanceToSqr(hand)<1e-12) return solved;
        Vec3 front=solved.tip();
        return aim(solved,hand,front,hand.distanceTo(front),null);
    }
}
