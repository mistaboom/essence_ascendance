package com.mistaboom.essence_ascendance.client;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Version boundary for the camera accessors used by the authored world visuals. */
public final class ClientCameraCompat {
    private ClientCameraCompat() {}

    public static Vec3 position(Camera camera) {
        //? if >=26.1.2
        /*return camera.position();*/
        //? if <26.1.2
        return camera.getPosition();
    }

    public static Entity entity(Camera camera) {
        //? if >=26.1.2
        /*return camera.entity();*/
        //? if <26.1.2
        return camera.getEntity();
    }

    public static Vector3f left(Camera camera) {
        //? if >=26.1.2
        /*return new Vector3f(camera.leftVector());*/
        //? if <26.1.2
        return camera.getLeftVector();
    }

    public static Vector3f up(Camera camera) {
        //? if >=26.1.2
        /*return new Vector3f(camera.upVector());*/
        //? if <26.1.2
        return camera.getUpVector();
    }

    public static float partialTick(Camera camera) {
        //? if >=26.1.2
        /*return Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);*/
        //? if <26.1.2
        return camera.getPartialTickTime();
    }
}
