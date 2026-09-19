package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.network.UtilitySensePayload;
import com.mistaboom.essence_ascendance.utility.UtilitySenseService;
import dev.architectury.networking.NetworkManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Connection-scoped cache plus non-authoritative Waylight presentation. */
public final class UtilitySenseClientState {
    private static final int WAYLIGHT_VISION_DURATION_TICKS = 240;

    // Presentation motion only. These values never affect skill range, selection, or gameplay.
    private static final double WISP_ATTRACTION = 0.085;
    private static final double WISP_DAMPING = 0.80;
    private static final double WISP_MAX_SPEED = 0.72;
    private static final double WISP_SNAP_DISTANCE = 0.035;

    private static UtilitySenseService.Snapshot snapshot;
    private static LocalPlayer receiptPlayer;
    private static ClientLevel receiptLevel;
    private static boolean injectedVision;
    private static boolean initialized;

    private static Vec3 wispPosition;
    private static Vec3 wispPreviousPosition;
    private static Vec3 wispVelocity = Vec3.ZERO;
    private static long wispAge;

    private UtilitySenseClientState() { }

    public static void init() {
        if (initialized) return;
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, UtilitySensePayload.TYPE, UtilitySensePayload.CODEC,
                (payload, context) -> ClientPacketDispatch.queue(context, () -> accept(payload.snapshot())));
        initialized = true;
    }

    public static void tickClient() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        boolean active = mode() == UtilitySenseService.Mode.WAYLIGHT && player != null && player.isAlive();
        if (active) {
            maintainVision(player);
            tickWisp(player);
        } else {
            removeInjectedVision(player);
            resetWisp();
        }
    }

    public static void clear() {
        removeInjectedVision(Minecraft.getInstance().player);
        snapshot = null;
        receiptPlayer = null;
        receiptLevel = null;
        resetWisp();
    }

    public static UtilitySenseService.Mode mode() {
        return valid() ? snapshot.mode() : UtilitySenseService.Mode.NONE;
    }

    public static boolean ledger() { return valid() && snapshot.ledger(); }
    public static List<UtilitySenseService.Threat> threats() { return valid() ? snapshot.threats() : List.of(); }
    public static List<UtilitySenseService.ProjectilePath> projectilePaths() { return valid() ? snapshot.projectilePaths() : List.of(); }
    public static List<UtilitySenseService.ExplosionDanger> explosions() { return valid() ? snapshot.explosions() : List.of(); }
    public static UtilitySenseService.WaylightMarker waylight() { return valid() ? snapshot.waylight() : null; }

    public static Vec3 wispPosition(float partialTick) {
        if (wispPosition == null || wispPreviousPosition == null) return null;
        double t = Math.clamp(partialTick, 0.0F, 1.0F);
        return wispPreviousPosition.add(wispPosition.subtract(wispPreviousPosition).scale(t));
    }

    public static Vec3 wispVelocity() { return wispVelocity; }
    public static long wispAge() { return wispAge; }

    private static void accept(UtilitySenseService.Snapshot next) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null
                || !minecraft.level.dimension().location().equals(next.dimension())) {
            clear();
            return;
        }
        snapshot = next;
        receiptPlayer = minecraft.player;
        receiptLevel = minecraft.level;
    }

    private static boolean valid() {
        Minecraft minecraft = Minecraft.getInstance();
        if (snapshot == null || minecraft.player != receiptPlayer || minecraft.level != receiptLevel
                || minecraft.player == null || minecraft.level == null || !minecraft.player.isAlive()
                || !minecraft.level.dimension().location().equals(snapshot.dimension())) {
            clear();
            return false;
        }
        return true;
    }

    private static void maintainVision(LocalPlayer player) {
        MobEffectInstance current = player.getEffect(MobEffects.NIGHT_VISION);
        if (current == null || isWaylightVision(current)) {
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,
                    WAYLIGHT_VISION_DURATION_TICKS, 0, true, false, false));
            injectedVision = true;
        } else {
            // A real effect has precedence. Do not replace or later remove it.
            injectedVision = false;
        }
    }

    private static void tickWisp(LocalPlayer player) {
        wispAge++;
        Vec3 desired = desiredWispPosition(player);
        if (wispPosition == null) {
            wispPosition = playerCompanionAnchor(player);
            wispPreviousPosition = wispPosition;
            wispVelocity = Vec3.ZERO;
        } else {
            wispPreviousPosition = wispPosition;
        }

        Vec3 delta = desired.subtract(wispPosition);
        if (delta.lengthSqr() <= WISP_SNAP_DISTANCE * WISP_SNAP_DISTANCE) {
            wispPosition = desired;
            wispVelocity = wispVelocity.scale(WISP_DAMPING);
            return;
        }

        wispVelocity = wispVelocity.scale(WISP_DAMPING).add(delta.scale(WISP_ATTRACTION));
        double speed = wispVelocity.length();
        if (speed > WISP_MAX_SPEED) wispVelocity = wispVelocity.scale(WISP_MAX_SPEED / speed);
        wispPosition = wispPosition.add(wispVelocity);
    }

    private static Vec3 desiredWispPosition(LocalPlayer player) {
        UtilitySenseService.WaylightMarker marker = waylight();
        double bob = Math.sin(wispAge * 0.18) * 0.09;
        double sway = Math.cos(wispAge * 0.11) * 0.05;
        if (marker != null) {
            return Vec3.atBottomCenterOf(marker.feet()).add(sway, 0.82 + bob, 0.0);
        }
        return playerCompanionAnchor(player).add(0.0, bob, 0.0);
    }

    private static Vec3 playerCompanionAnchor(LocalPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 lateral = look.cross(new Vec3(0, 1, 0));
        if (lateral.lengthSqr() < 1.0E-6) lateral = new Vec3(1, 0, 0);
        else lateral = lateral.normalize();

        // Presentation-only idle perch: stay behind and off the player's shoulder, but far
        // enough back that the glow cannot crowd first-person vision when no marker exists.
        return player.getEyePosition()
                .add(look.scale(-2.35))
                .add(lateral.scale(0.78))
                .add(0, -0.30, 0);
    }

    private static void resetWisp() {
        wispPosition = null;
        wispPreviousPosition = null;
        wispVelocity = Vec3.ZERO;
        wispAge = 0;
    }

    private static boolean isWaylightVision(MobEffectInstance effect) {
        return effect.getAmplifier() == 0 && effect.isAmbient() && !effect.isVisible() && !effect.showIcon();
    }

    private static void removeInjectedVision(LocalPlayer player) {
        if (!injectedVision || player == null) {
            injectedVision = false;
            return;
        }
        MobEffectInstance current = player.getEffect(MobEffects.NIGHT_VISION);
        if (current != null && isWaylightVision(current)) player.removeEffect(MobEffects.NIGHT_VISION);
        injectedVision = false;
    }
}
