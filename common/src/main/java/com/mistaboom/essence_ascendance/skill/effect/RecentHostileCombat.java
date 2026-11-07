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
    private long encounterLastDamage = Long.MIN_VALUE;
    private ResourceLocation encounterDimension;
    private String encounter;
    private long encounterStartedAt = Long.MIN_VALUE;
    private int automaticMeals;

    /** Separate accepted-loss encounter evidence: routing to food/debt may lock sustain without starting a fight. */
    public static void acceptedEncounterDamage(ServerPlayer player, LivingEntity other) {
        if (!acceptedHostile(player, other)) return;
        var context = SkillEffectRuntime.context(player);
        RecentHostileCombat state = context.state(KEY, RecentHostileCombat::new);
        if (!com.mistaboom.essence_ascendance.attunement.AttunementGameplay.eligible(player)) {
            state.encounterLastDamage = Long.MIN_VALUE; state.encounterDimension = null; state.encounter = null; state.automaticMeals = 0;
            return;
        }
        long now = context.now();
        var dimension = player.level().dimension().location();
        int timeout = context.settings().vitality().innerSustenance().combatTimeoutTicks();
        if (!com.mistaboom.essence_ascendance.gameplay.OutcomePolicies.sameFight(state.encounterLastDamage, now, timeout,
                dimension.equals(state.encounterDimension))) {
            state.encounter = java.util.UUID.randomUUID().toString(); state.automaticMeals = 0;
            state.encounterStartedAt = now;
        }
        state.encounterLastDamage = now; state.encounterDimension = dimension;
    }
    public static String encounter(SkillEffectRuntime.Context context, int timeout) {
        RecentHostileCombat state = context.existingState(KEY);
        return state != null && com.mistaboom.essence_ascendance.gameplay.OutcomePolicies.sameFight(
                state.encounterLastDamage, context.now(), timeout,
                context.player().level().dimension().location().equals(state.encounterDimension)) ? state.encounter : null;
    }
    /** Beginning of the currently observed accepted-loss encounter; -1 means unknown/inactive. */
    public static long encounterStartedAt(SkillEffectRuntime.Context context, int timeout) {
        RecentHostileCombat state = context.existingState(KEY);
        return state == null || encounter(context, timeout) == null ? -1 : state.encounterStartedAt;
    }
    /** A bounded encounter checkpoint; only newly completed first/second meals are returned. */
    public static int completedAutomaticMeal(SkillEffectRuntime.Context context, int timeout) {
        RecentHostileCombat state = context.existingState(KEY);
        return state == null || encounter(context, timeout) == null || state.automaticMeals >= 2 ? 0 : ++state.automaticMeals;
    }

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
        encounterLastDamage = Long.MIN_VALUE; encounterDimension = null; encounter = null; automaticMeals = 0;
        encounterStartedAt = Long.MIN_VALUE;
    }
}
