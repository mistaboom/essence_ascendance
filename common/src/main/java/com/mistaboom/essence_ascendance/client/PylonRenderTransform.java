package com.mistaboom.essence_ascendance.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.core.Direction;

/** Client pose composition matching {@code PylonLocalFrame}. */
public final class PylonRenderTransform {
    private PylonRenderTransform() { }

    public static void applyAroundBlockCenter(PoseStack pose, Direction direction) {
        pose.translate(0.5, 0.5, 0.5);
        applyRotation(pose, direction);
        pose.translate(-0.5, -0.5, -0.5);
    }

    public static void applyRotation(PoseStack pose, Direction direction) {
        switch (direction) {
            case UP -> { }
            case DOWN -> pose.mulPose(Axis.XP.rotationDegrees(180));
            case NORTH -> pose.mulPose(Axis.XP.rotationDegrees(-90));
            case SOUTH -> pose.mulPose(Axis.XP.rotationDegrees(90));
            case EAST -> pose.mulPose(Axis.ZP.rotationDegrees(-90));
            case WEST -> pose.mulPose(Axis.ZP.rotationDegrees(90));
        }
    }
}
