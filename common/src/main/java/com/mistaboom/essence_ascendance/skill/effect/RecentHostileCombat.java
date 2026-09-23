package com.mistaboom.essence_ascendance.skill.effect;

import com.mistaboom.essence_ascendance.projectile.ProjectileOwnership;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** Accepted hostile health/absorption loss, shared by sustain consumers; never inferred from swings.
 * Also remembers deliberate outgoing damage so pacification effects can distinguish a player choosing to fight
 * from a hostile merely landing another hit. */
public final class RecentHostileCombat implements SkillEffectState {
    private static final ResourceLocation KEY = ResourceLocation.fromNamespaceAndPath("essence_ascendance", "recent_hostile_combat");
    private long lastDamage = Long.MIN_VALUE;
    private long lastOutgoingDamage = Long.MIN_VALUE;
    private ResourceLocation dimension;

    public static void acceptedDamage(ServerPlayer player, LivingEntity other) {
        if (!acceptedHostile(player, other)) return;
        var context = SkillEffectRuntime.context(player);
        context.state(KEY, RecentHostileCombat::new).mark(context.now(), player.level().dimension().location());
    }

    /** Accepted hostile damage dealt by this player. This is intentionally narrower than acceptedDamage:
     * incoming hits and automatic retaliation still count for sustain, but do not renew Sanctuary's aggression timer. */
    public static void acceptedOutgoingDamage(ServerPlayer player, LivingEntity other) {
        if (!acceptedHostile(player, other)) return;
        var context = SkillEffectRuntime.context(player);
        RecentHostileCombat state = context.state(KEY, RecentHostileCombat::new);
        var proc = SkillProcDamageService.current();
        boolean retaliation = com.mistaboom.essence_ascendance.equipment.EquipmentDamageService.isReflectionInProgress()
                || proc != null && proc.owner() == player && proc.kind().reflectedOutcome();
        // Retaliation still locks sustain recovery, but it is not a decision to break Sanctuary.
        if (retaliation) state.mark(context.now(), player.level().dimension().location());
        else state.markOutgoing(context.now(), player.level().dimension().location());
    }

    private static boolean acceptedHostile(ServerPlayer player, LivingEntity other) {
        return other != null && other != player && ProjectileOwnership.hostileDamageSource(player, other);
    }

    public static int remaining(SkillEffectRuntime.Context context, int timeout) {
        RecentHostileCombat state = context.existingState(KEY);
        return state == null ? 0 : state.remaining(context.now(), context.player().level().dimension().location(), timeout);
    }

    public static int outgoingRemaining(SkillEffectRuntime.Context context, int timeout) {
        RecentHostileCombat state = context.existingState(KEY);
        return state == null ? 0 : state.outgoingRemaining(context.now(), context.player().level().dimension().location(), timeout);
    }

    public void mark(long now, ResourceLocation currentDimension) {
        lastDamage = now;
        dimension = currentDimension;
    }

    public void markOutgoing(long now, ResourceLocation currentDimension) {
        lastDamage = now;
        lastOutgoingDamage = now;
        dimension = currentDimension;
    }

    public int remaining(long now, ResourceLocation currentDimension, int timeout) {
        return remainingSince(lastDamage, now, currentDimension, timeout);
    }

    public int outgoingRemaining(long now, ResourceLocation currentDimension, int timeout) {
        return remainingSince(lastOutgoingDamage, now, currentDimension, timeout);
    }

    private int remainingSince(long last, long now, ResourceLocation currentDimension, int timeout) {
        if (dimension == null || !dimension.equals(currentDimension) || now < last) {
            clear();
            return 0;
        }
        if (last == Long.MIN_VALUE) return 0;
        long elapsed = now - last;
        return elapsed >= timeout ? 0 : (int) (timeout - elapsed);
    }

    @Override public void clear() {
        lastDamage = Long.MIN_VALUE;
        lastOutgoingDamage = Long.MIN_VALUE;
        dimension = null;
    }
}
