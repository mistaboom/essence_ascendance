package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.movement.FlightAbilityRules;
import com.mistaboom.essence_ascendance.movement.MovementAbilityInput;
import com.mistaboom.essence_ascendance.movement.MovementAbilityRules;
import com.mistaboom.essence_ascendance.skill.CommittedSkillAccess;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudEntry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Local presentation state for the procedural aerial-harness effects. Gameplay
 * authority remains on the server; this class only mirrors already-synchronized
 * skill/HUD state and ordinary Jump input so the geometry responds immediately.
 */
public final class FlightVisualClientState {
    private static final int VECTOR_BOOST_VISUAL_TICKS = 8;

    private static LocalPlayer player;
    private static ClientLevel level;
    private static boolean previousJumpDown;
    private static long jumpHeldSince = Long.MIN_VALUE;
    private static boolean fatigueEnabled;
    private static boolean wingsEnabled;
    private static boolean fatigueThrusting;
    private static boolean wingsActive;
    private static long vectorBoostUntil = Long.MIN_VALUE;

    private FlightVisualClientState() { }

    public static void tick(LocalPlayer current, boolean jumpDown, boolean focused) {
        if (current == null || current.level() == null) {
            clear();
            return;
        }
        if (player != current || level != current.level()) {
            clear();
            player = current;
            level = (ClientLevel) current.level();
        }

        long now = current.level().getGameTime();
        if (!focused || !current.isAlive() || current.isRemoved() || current.isSpectator()) {
            previousJumpDown = false;
            jumpHeldSince = Long.MIN_VALUE;
            fatigueEnabled = false;
            wingsEnabled = false;
            fatigueThrusting = false;
            wingsActive = false;
            vectorBoostUntil = Long.MIN_VALUE;
            return;
        }

        if (jumpDown) {
            if (!previousJumpDown || jumpHeldSince == Long.MIN_VALUE || now < jumpHeldSince) jumpHeldSince = now;
        } else {
            jumpHeldSince = Long.MIN_VALUE;
        }

        fatigueEnabled = CommittedSkillAccess.isEffective(current, SkillIds.FATIGUE_FLIGHT);
        wingsEnabled = CommittedSkillAccess.isEffective(current, SkillIds.ESSENCE_WINGS);

        boolean previousWings = wingsActive;
        wingsActive = hudActive(SkillIds.ESSENCE_WINGS)
                && current.isFallFlying()
                && CommittedSkillAccess.isEffective(current, SkillIds.ESSENCE_WINGS);

        double fatigueStamina = hudProgress(SkillIds.FATIGUE_FLIGHT, 1.0);
        fatigueThrusting = fatigueEnabled
                && FlightAbilityRules.freeFlightAllowed(current)
                && !MovementAbilityRules.supported(current)
                && fatigueStamina > 0.001
                && jumpDown
                && jumpHeldSince != Long.MIN_VALUE
                && now >= jumpHeldSince
                && now - jumpHeldSince >= MovementAbilityInput.HEARTBEAT_TICKS;

        boolean freshPress = jumpDown && !previousJumpDown;
        if (freshPress && previousWings && wingsActive
                && CommittedSkillAccess.isEffective(current, SkillIds.VECTOR_BOOST)
                && hudProgress(SkillIds.VECTOR_BOOST, 1.0) >= 0.999) {
            vectorBoostUntil = now + VECTOR_BOOST_VISUAL_TICKS;
        }
        if (!wingsActive && now >= vectorBoostUntil) vectorBoostUntil = Long.MIN_VALUE;
        previousJumpDown = jumpDown;
    }

    public static boolean fatigueEnabled() { return valid() && fatigueEnabled; }
    public static boolean wingsEnabled() { return valid() && wingsEnabled; }
    public static boolean fatigueThrusting() { return valid() && fatigueThrusting; }
    public static boolean wingsActive() { return valid() && wingsActive; }

    public static double vectorBoostIntensity(float partialTick) {
        if (!valid() || vectorBoostUntil == Long.MIN_VALUE || level == null) return 0.0;
        double remaining = vectorBoostUntil - (level.getGameTime() + Math.clamp(partialTick, 0.0F, 1.0F));
        if (remaining <= 0.0) return 0.0;
        return Math.clamp(remaining / VECTOR_BOOST_VISUAL_TICKS, 0.0, 1.0);
    }

    public static void clear() {
        player = null;
        level = null;
        previousJumpDown = false;
        jumpHeldSince = Long.MIN_VALUE;
        fatigueEnabled = false;
        wingsEnabled = false;
        fatigueThrusting = false;
        wingsActive = false;
        vectorBoostUntil = Long.MIN_VALUE;
    }

    private static boolean valid() {
        return player != null && level != null && player.level() == level && player.isAlive() && !player.isRemoved();
    }

    private static boolean hudActive(ResourceLocation id) {
        SkillEffectHudEntry entry = SkillEffectHudClientState.currentEntry(id);
        return entry != null && entry.active();
    }

    private static double hudProgress(ResourceLocation id, double fallback) {
        SkillEffectHudEntry entry = SkillEffectHudClientState.currentEntry(id);
        if (entry == null || entry.meter().kind() != SkillEffectHudEntry.MeterKind.PROGRESS) return fallback;
        return entry.meter().fraction();
    }
}
