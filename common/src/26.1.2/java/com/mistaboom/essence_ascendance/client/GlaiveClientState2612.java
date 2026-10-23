package com.mistaboom.essence_ascendance.client;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.HumanoidArm;
import java.util.Map;
import java.util.WeakHashMap;

/** The 26.1 player renderer extracts entities before submitting their model and held layers. */
public final class GlaiveClientState2612 {
    private static final Map<AvatarRenderState, AbstractClientPlayer> PLAYERS = new WeakHashMap<>();
    private static final Map<AvatarRenderState, ArmPose> ARMS = new WeakHashMap<>();
    private record ArmPose(HumanoidArm side, PartPose pose) { }
    private GlaiveClientState2612() { }
    public static void capture(AbstractClientPlayer player, AvatarRenderState state) {
        PLAYERS.put(state, player);
        ARMS.remove(state);
    }
    public static AbstractClientPlayer player(AvatarRenderState state) {
        return PLAYERS.get(state);
    }
    /** Keep the submitted hand bone for the later deferred player draw. */
    public static void captureArm(AvatarRenderState state, PlayerModel model, HumanoidArm side) {
        ARMS.put(state, new ArmPose(side,
                (side == HumanoidArm.RIGHT ? model.rightArm : model.leftArm).storePose()));
    }
    public static void restoreArm(AvatarRenderState state, PlayerModel model) {
        var saved = ARMS.get(state);
        if (saved == null) return;
        var arm = saved.side() == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        arm.loadPose(saved.pose());
        // The native sleeve is an arm child. Its local pose must stay untouched.
    }
}
