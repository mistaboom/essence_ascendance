package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.pylon.PylonLocalFrame;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.core.Direction;

/** Client pose composition matching {@code PylonLocalFrame}. */
public final class PylonRenderTransform {
    private PylonRenderTransform() { }

    public static void applyAroundBlockCenter(PoseStack pose, Direction direction) {
        applyAroundBlockCenter(pose, PylonLocalFrame.of(direction));
    }

    public static void applyAroundBlockCenter(PoseStack pose, PylonLocalFrame frame) {
        pose.translate(0.5, 0.5, 0.5);
        applyRotation(pose, frame);
        pose.translate(-0.5, -0.5, -0.5);
    }

    public static void applyRotation(PoseStack pose, PylonLocalFrame frame) {
        applyRotation(pose, frame.direction());
        if (frame.quarterTurns() != 0) pose.mulPose(Axis.YP.rotationDegrees(frame.quarterTurns() * 90));
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
