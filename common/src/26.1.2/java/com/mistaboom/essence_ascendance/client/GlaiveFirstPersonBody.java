package com.mistaboom.essence_ascendance.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import java.lang.reflect.Proxy;

/** Submits only the native held-hand layer to evaluate the same arm attachment used by observers. */
public final class GlaiveFirstPersonBody {
    private static boolean rendering;
    /** The hand layer still captures its transformed palm; no probe geometry enters the frame. */
    private static final SubmitNodeCollector SILENT = (SubmitNodeCollector) Proxy.newProxyInstance(
            SubmitNodeCollector.class.getClassLoader(), new Class<?>[]{SubmitNodeCollector.class},
            (proxy, method, args) -> {
                if (method.getName().equals("order")) return proxy;
                if (method.getDeclaringClass() == Object.class) return switch (method.getName()) {
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "Glaive hand-transform probe";
                    default -> null;
                };
                return null;
            });
    private GlaiveFirstPersonBody() { }
    public static boolean rendering() { return rendering; }

    public static void render(float partialTick) {
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (player == null || player.isInvisible() || player.isSleeping()
                || !GlaivePlayerAnimation.active(player)
                || GlaivePlayerAnimation.attack(player, partialTick) == null) return;
        var camera = minecraft.gameRenderer.getMainCamera();
        var renderer = minecraft.getEntityRenderDispatcher().getPlayerRenderer(player);
        AvatarRenderState state = renderer.createRenderState();
        renderer.extractRenderState(player, state, partialTick);
        var pose = new PoseStack();
        var offset = player.getPosition(partialTick).subtract(camera.position()).add(renderer.getRenderOffset(state));
        pose.translate(offset.x, offset.y, offset.z);
        CameraRenderState cameraState = new CameraRenderState();
        cameraState.pos = camera.position();
        cameraState.blockPos = net.minecraft.core.BlockPos.containing(camera.position());
        rendering = true;
        try {
            renderer.submit(state, pose, SILENT, cameraState);
        } finally {
            rendering = false;
        }
    }
}
