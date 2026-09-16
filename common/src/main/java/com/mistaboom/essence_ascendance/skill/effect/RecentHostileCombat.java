package com.mistaboom.essence_ascendance.skill.effect;

import com.mistaboom.essence_ascendance.projectile.ProjectileOwnership;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** Accepted hostile health/absorption loss, shared by sustain consumers; never inferred from swings. */
public final class RecentHostileCombat implements SkillEffectState {
    private static final ResourceLocation KEY = ResourceLocation.fromNamespaceAndPath("essence_ascendance", "recent_hostile_combat");
    private long lastDamage = Long.MIN_VALUE;
    private ResourceLocation dimension;

    public static void acceptedDamage(ServerPlayer player, LivingEntity other) {
        if (other == null || other == player || !ProjectileOwnership.hostileDamageSource(player, other)) return;
        var context = SkillEffectRuntime.context(player);
        context.state(KEY, RecentHostileCombat::new).mark(context.now(), player.level().dimension().location());
    }

    public static int remaining(SkillEffectRuntime.Context context, int timeout) {
        RecentHostileCombat state = context.existingState(KEY);
        return state == null ? 0 : state.remaining(context.now(), context.player().level().dimension().location(), timeout);
    }

    public void mark(long now, ResourceLocation currentDimension) { lastDamage = now; dimension = currentDimension; }
    public int remaining(long now, ResourceLocation currentDimension, int timeout) {
        if (dimension == null || !dimension.equals(currentDimension) || now < lastDamage) { clear(); return 0; }
        long elapsed = now - lastDamage;
        return elapsed >= timeout ? 0 : (int) (timeout - elapsed);
    }
    @Override public void clear() { lastDamage = Long.MIN_VALUE; dimension = null; }
}
