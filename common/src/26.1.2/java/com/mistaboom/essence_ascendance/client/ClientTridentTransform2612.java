package com.mistaboom.essence_ascendance.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Vector3f;

/** Native 26.1.2 trident_in_hand.json display transforms, whose baked model no longer exposes getTransforms. */
public final class ClientTridentTransform2612 {
    private static final Vector3f SCALE = new Vector3f(1, 1, 1);
    private static final ItemTransform RIGHT = new ItemTransform(
            new Vector3f(0, -90, 25), new Vector3f(-3F / 16, 17F / 16, 1F / 16), SCALE);
    private static final ItemTransform LEFT = new ItemTransform(
            new Vector3f(0, 90, -25), new Vector3f(13F / 16, 17F / 16, 1F / 16), SCALE);

    private ClientTridentTransform2612() { }

    public static void apply(HumanoidArm hand, PoseStack pose) {
        boolean left = hand == HumanoidArm.LEFT;
        (left ? LEFT : RIGHT).apply(left, pose.last());
    }
}
