package com.mistaboom.essence_ascendance.projectile;

import com.mistaboom.essence_ascendance.mixin.ProjectileNativeAccess;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;

/** Auditable, shared damaging-projectile ownership policy. A missing owner is not an unresolved owner. */
public final class ProjectileOwnership {
    private ProjectileOwnership() { }
    /** Damage reflection uses the native responsible source, never an inferred projectile owner.
     * Unlike proximity control this remains valid at the final collision after a shot stops flying. */
    public static LivingEntity damageSource(net.minecraft.world.damagesource.DamageSource source, ServerPlayer defender) {
        if (!(source.getEntity() instanceof LivingEntity responsible) || responsible == defender
                || !responsible.isAlive() || responsible.isRemoved() || responsible.isSpectator()
                || responsible.level() != defender.level()) return null;
        if (source.getDirectEntity() != null && (source.getDirectEntity().isRemoved()
                || source.getDirectEntity().level() != defender.level())) return null;
        if (source.getDirectEntity() instanceof Projectile projectile) {
            if (projectile.getOwner() != responsible) return null;
            var state = ProjectileRuntime.state(projectile);
            if (state != null && !ProjectileRuntime.validOwnership(projectile, state)) return null;
        }
        return responsible;
    }
    /** Reuse the same pet/team/PvP relationship as defensive projectile control. */
    public static boolean hostileDamageSource(ServerPlayer defender, LivingEntity actual) {
        return actual != null && relationship(defender, actual) == Decision.HOSTILE;
    }
    private static Decision relationship(ServerPlayer defender, Entity actual) {
        Entity responsible = actual instanceof TamableAnimal pet && pet.getOwner() != null ? pet.getOwner() : actual;
        boolean self = actual == defender || responsible == defender;
        boolean allied = defender.isAlliedTo(actual) || defender.isAlliedTo(responsible);
        boolean pvp = !(responsible instanceof Player player) || defender.server.isPvpAllowed()
                && defender.canHarmPlayer(player) && player.canHarmPlayer(defender);
        return classify(true, true, true, false, self, allied, pvp);
    }
    public enum Decision { HOSTILE, OWNERLESS_DAMAGING, UNSUPPORTED, INACTIVE, INVALID_DEFENDER,
        UNRESOLVED_OWNER, INVALID_OWNER, OWN, ALLIED, PVP_DISABLED }
    public record Resolution(Decision decision, LivingEntity source) {
        public boolean hostile() { return decision == Decision.HOSTILE || decision == Decision.OWNERLESS_DAMAGING; }
    }
    public static Resolution resolve(Projectile projectile, ServerPlayer defender) {
        if (!ProjectileAdapters.damaging(projectile)) return new Resolution(Decision.UNSUPPORTED, null);
        if (!ProjectileAdapters.inFlight(projectile)) return new Resolution(Decision.INACTIVE, null);
        if (!validDefender(defender) || defender.level() != projectile.level())
            return new Resolution(Decision.INVALID_DEFENDER, null);
        Entity actual = projectile.getOwner();
        boolean declaredOwner = ((ProjectileNativeAccess) projectile).essenceAscendance$ownerUUID() != null;
        if (actual == null) return new Resolution(declaredOwner ? Decision.UNRESOLVED_OWNER : Decision.OWNERLESS_DAMAGING, null);
        if (!(actual instanceof LivingEntity living) || !actual.isAlive() || actual.isRemoved()
                || actual.level() != projectile.level() || actual.isSpectator()) return new Resolution(Decision.INVALID_OWNER, null);
        var launch = ProjectileRuntime.state(projectile);
        if (launch != null && !ProjectileRuntime.validOwnership(projectile, launch))
            return new Resolution(Decision.INVALID_OWNER, living);
        return new Resolution(relationship(defender, actual), living);
    }
    public static boolean validDefender(ServerPlayer player) {
        return player.isAlive() && !player.isRemoved() && !player.isSpectator() && !player.getAbilities().invulnerable
                && player.server.getPlayerList().getPlayer(player.getUUID()) == player;
    }
    /** Native arrows change DISALLOWED pickup to ALLOWED for player owners; responsibility must not mint ammunition. */
    public static void transferNative(Projectile projectile, LivingEntity owner) {
        AbstractArrow.Pickup pickup = projectile instanceof AbstractArrow arrow ? arrow.pickup : null;
        try { projectile.setOwner(owner); }
        finally { if (projectile instanceof AbstractArrow arrow) arrow.pickup = pickup; }
    }
    /** Pure contract used by deterministic tests; resolved stale owners never become dispenser shots. */
    public static Decision classify(boolean supported, boolean active, boolean validOwner, boolean ownerless,
                                    boolean self, boolean allied, boolean pvp) {
        if (!supported) return Decision.UNSUPPORTED;
        if (!active) return Decision.INACTIVE;
        if (!validOwner) return Decision.INVALID_OWNER;
        if (ownerless) return Decision.OWNERLESS_DAMAGING;
        if (self) return Decision.OWN;
        if (allied) return Decision.ALLIED;
        return pvp ? Decision.HOSTILE : Decision.PVP_DISABLED;
    }
}
