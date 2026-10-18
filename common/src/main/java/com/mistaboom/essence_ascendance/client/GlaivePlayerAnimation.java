package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.equipment.GlaiveGrip;
import com.mistaboom.essence_ascendance.equipment.WeaponActionGeometry.Pose;
import com.mistaboom.essence_ascendance.equipment.WeaponActionPresentation;
import com.mistaboom.essence_ascendance.network.WeaponActionStatePayload;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** One rigid arm and a fixed wrist angle. All attack aiming happens at the native shoulder. */
public final class GlaivePlayerAnimation {
    public static final float ATTACK_TILT=(float)GlaiveGrip.ATTACK_TILT;
    private GlaivePlayerAnimation() { }
    public static boolean active(Player player) {
        return player.isAlive() && !player.isSpectator() && GlaiveRenderer.held(player);
    }
    /** Null means ordinary native held-item rest pose, including walking and crouching. */
    public static Pose attack(Player player,float partial) {
        var entry=WeaponActionStatePayload.presentation(player);
        if(entry==null || entry.state().selectedSlot()!=player.getInventory().selected
                || !WeaponActionPresentation.extended(entry.phase(player.level().getGameTime(),partial))) return null;
        return entry.pose(player.level().getGameTime(),partial);
    }
    public static Vector3f palm(HumanoidArm arm) { return new Vector3f(-GlaiveGrip.sign(arm)/16F,.5F,0); }
    /** At rest the shaft is perpendicular to the arm, as with the native held trident. */
    public static Vector3f shaft(float tilt) {
        double angle=Math.toRadians(tilt);
        return new Vector3f(0,(float)Math.sin(angle),-(float)Math.cos(angle));
    }
    public record Solution(Quaternionf rotation,float distance,boolean reachable) { }
    /** ModelPart uses Rz*Ry*Rx. Read the matrix directly, including the vertical/gimbal case. */
    public static Vector3f armAngles(Quaternionf rotation) {
        var m=new org.joml.Matrix3f().rotation(new Quaternionf(rotation).normalize());
        double cosine=Math.hypot(m.m00(),m.m01());
        float y=(float)Math.atan2(-m.m02(),cosine);
        if(cosine<1e-6)
            return new Vector3f((float)Math.atan2(-m.m21(),m.m11()),y,0);
        return new Vector3f((float)Math.atan2(m.m12(),m.m22()),y,(float)Math.atan2(m.m01(),m.m00()));
    }
    /**
     * Solve |palm + distance*shaft| = |target - shoulder|, then rotate that rigid assembly.
     * A fixed wrist angle leaves only shoulder rotation and telescoping length as degrees of freedom.
     * Targets inside the arm's minimum reach use the nearest possible pose, never detach the hand.
     */
    public static Solution solve(Vector3f target,Vector3f lateral,HumanoidArm main,float tilt) {
        var palm=palm(main);var shaft=shaft(tilt);
        float along=palm.dot(shaft),perpendicular=Math.max(0,palm.lengthSquared()-along*along);
        float distance=Math.max(0,-along+(float)Math.sqrt(Math.max(0,target.lengthSquared()-perpendicular)));
        var local=new Vector3f(shaft).mul(distance).add(palm).normalize();
        var desired=target.lengthSquared()<1e-10F ? new Vector3f(shaft):new Vector3f(target).normalize();
        var rotation=new Quaternionf().rotationTo(local,desired);
        var localRight=new Vector3f(1,0,0).fma(-local.x,local).normalize();
        var right=rotation.transform(localRight);
        var wanted=new Vector3f(lateral).fma(-lateral.dot(desired),desired);
        if(wanted.lengthSquared()>1e-8F) {
            wanted.normalize();
            float twist=(float)Math.atan2(desired.dot(new Vector3f(right).cross(wanted)),right.dot(wanted));
            rotation.premul(new Quaternionf().rotationAxis(twist,desired));
        }
        boolean reachable=Math.abs(new Vector3f(shaft).mul(distance).add(palm).length()-target.length())<1e-5F;
        return new Solution(rotation,distance,reachable);
    }
    /** Called after native setupAnim, with the final body/posture/scale matrix, before any model layer draws. */
    public static void model(Player player,PlayerModel<?> model,PoseStack body,float partial) {
        var attack=attack(player,partial);if(attack==null) return;
        var camera=Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        poseArm(model,player.getMainArm(),body,camera,attack);
    }
    public static void poseArm(PlayerModel<?> model,HumanoidArm main,PoseStack body,Vec3 camera,Pose attack) {
        if(attack==null) return;
        var arm=main==HumanoidArm.RIGHT ? model.rightArm:model.leftArm;
        var inverse=new Matrix4f(body.last().pose()).invert();
        var target=inverse.transformPosition(vector(attack.tip().subtract(camera)));
        target.sub(arm.x/16,arm.y/16,arm.z/16);
        var lateral=inverse.transformDirection(vector(attack.right())).normalize();
        var solution=solve(target,lateral,main,ATTACK_TILT);
        var angles=armAngles(solution.rotation());
        arm.setRotation(angles.x,angles.y,angles.z);
        (main==HumanoidArm.RIGHT ? model.rightSleeve:model.leftSleeve).copyFrom(arm);
    }
    public static Vector3f vector(Vec3 v) { return new Vector3f((float)v.x,(float)v.y,(float)v.z); }
}
