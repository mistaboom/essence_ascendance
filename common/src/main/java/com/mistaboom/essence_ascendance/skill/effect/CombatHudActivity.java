package com.mistaboom.essence_ascendance.skill.effect;

import com.mistaboom.essence_ascendance.projectile.ProjectileOwnership;
import com.mistaboom.essence_ascendance.projectile.ProjectileRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;

import java.util.Map;
import java.util.WeakHashMap;

/** Presentation only: confirmed combat reveals posture cards without changing their gameplay conditions. */
public final class CombatHudActivity {
    public static final int WINDOW_TICKS = 100;
    private static final Map<ServerPlayer, Activity> ACTIVITY = new WeakHashMap<>();
    private record Activity(ResourceLocation dimension, long tick) { }
    private CombatHudActivity() { }

    /** Called only after positive native health/absorption loss, outside secondary/reflected/nested damage. */
    public static void confirmedDamage(LivingEntity target, DamageSource source) {
        if (target instanceof ServerPlayer defender) confirmedDefense(defender, source);
        if (!(source.getEntity() instanceof ServerPlayer attacker) || !combatant(target)
                || target.isRemoved() || target.isSpectator() || target.level() != attacker.level()
                || !ProjectileOwnership.validDefender(attacker)
                || !ProjectileOwnership.hostileDamageSource(attacker, target) || !validDirectSource(source, attacker)) return;
        // The measured write may already have reduced a killing blow's target to zero health.
        mark(attacker);
    }

    /** Actual shield blocks and dodges also reveal the defender's posture, even without health loss. */
    public static void confirmedDefense(ServerPlayer defender, DamageSource source) {
        if (!ProjectileOwnership.validDefender(defender)) return;
        LivingEntity responsible = ProjectileOwnership.damageSource(source, defender);
        if (combatant(responsible) && ProjectileOwnership.hostileDamageSource(defender, responsible)) mark(defender);
    }

    private static boolean combatant(LivingEntity entity) { return entity instanceof Mob || entity instanceof Player; }

    private static boolean validDirectSource(DamageSource source, ServerPlayer attacker) {
        var direct = source.getDirectEntity();
        if (direct != null && (direct.isRemoved() || direct.level() != attacker.level())) return false;
        if (direct instanceof Projectile projectile) {
            if (projectile.getOwner() != attacker) return false;
            var state = ProjectileRuntime.state(projectile);
            if (state != null && !ProjectileRuntime.validOwnership(projectile, state)) return false;
        }
        return true;
    }

    private static void mark(ServerPlayer player) {
        ACTIVITY.put(player, new Activity(player.level().dimension().location(), player.level().getGameTime()));
    }

    public static boolean active(ServerPlayer player) { return remainingTicks(player) > 0; }

    public static int remainingTicks(ServerPlayer player) {
        Activity activity = ACTIVITY.get(player);
        if (activity == null) return 0;
        long now = player.level().getGameTime();
        long elapsed = now - activity.tick();
        if (!ProjectileOwnership.validDefender(player) || !activity.dimension().equals(player.level().dimension().location())
                || elapsed < 0 || elapsed >= WINDOW_TICKS) {
            ACTIVITY.remove(player);
            return 0;
        }
        return WINDOW_TICKS - (int) elapsed;
    }

    public static void forget(ServerPlayer player) { ACTIVITY.remove(player); }
    public static void clear() { ACTIVITY.clear(); }
}
