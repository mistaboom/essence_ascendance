package com.mistaboom.essence_ascendance.guard;

import com.mistaboom.essence_ascendance.equipment.EquipmentDamageService;
import com.mistaboom.essence_ascendance.equipment.ShieldMath;
import com.mistaboom.essence_ascendance.projectile.ProjectileOwnership;
import com.mistaboom.essence_ascendance.projectile.ProjectileTargeting;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.effect.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** One composed equipment retaliation, measured at the native health write, then bounded secondary reprisals. */
public final class ReflectionRouter {
    private static final Map<ServerPlayer, String> RECENT = new WeakHashMap<>();
    private ReflectionRouter() { }
    public record Source(LivingEntity target, String decision) { public boolean eligible() { return target != null; } }
    public record Result(double requested, double confirmed, String decision) { }
    public static Source source(ServerPlayer player, DamageSource source) {
        LivingEntity target = ProjectileOwnership.damageSource(source, player);
        if (target == null) return new Source(null, "no_valid_responsible_living_source");
        if (target.isInvulnerable() || target instanceof net.minecraft.world.entity.player.Player other
                && (other.getAbilities().invulnerable || !player.server.isPvpAllowed() || !player.canHarmPlayer(other))
                || player.getTeam() != null && player.isAlliedTo(target) && !player.getTeam().isAllowFriendlyFire())
            return new Source(null, "team_pvp_immunity_or_lifecycle");
        return new Source(target, "responsible_native_source");
    }
    /** Shield's blocked portion is already reflected. Ward only fills the disjoint fully mitigated portion. */
    public static double wardExtension(double incoming, double blocked, double loss, double equipmentPercent,
                                        double scale, boolean eligible) {
        if (!eligible || !Double.isFinite(incoming) || !Double.isFinite(blocked) || !Double.isFinite(loss)
                || incoming <= 0 || loss > 0 || loss < 0) return 0;
        return ShieldMath.reflectedPortion(ShieldMath.safeDamage(Math.max(0, incoming - blocked)), equipmentPercent) * scale;
    }
    public static double compose(double ordinary, double block, double ward, double multiplier) {
        return ShieldMath.safeDamage((ordinary + block + ward) * multiplier);
    }
    public static Result reflect(ServerPlayer player, Source responsible, double requested, boolean suppressed) {
        float damage = ShieldMath.safeDamage(requested);
        if (suppressed || EquipmentDamageService.isReflectionInProgress() || EquipmentDamageService.isSecondarySkillDamage())
            return new Result(damage, 0, "secondary_or_recursion");
        if (!responsible.eligible()) return new Result(damage, 0, responsible.decision());
        if (damage <= 0) return new Result(0, 0, "no_equipment_reflection");
        DamageSource retaliation = player.damageSources().thorns(player);
        var measured = EquipmentDamageService.measureDamage(responsible.target(), retaliation,
                () -> EquipmentDamageService.withReflection(() -> responsible.target().hurt(retaliation, damage)));
        return new Result(damage, measured.accepted() ? measured.loss() : 0,
                !measured.accepted() ? "native_rejected" : measured.loss() <= 0 ? "fully_prevented" : "confirmed");
    }
    public static void reprisal(ServerPlayer player, LivingEntity primary, double confirmed, SkillEffectRuntime.Context context) {
        if (!context.isEffective(SkillIds.CROWD_REPRISAL) || confirmed <= 0 || !Double.isFinite(confirmed)) return;
        var tuning = context.settings().guard().reprisal();
        var budget = new PropagationBudget(0, tuning.maximumTargets());
        budget.seed(player.getUUID()); budget.seed(primary.getUUID());
        int accepted = SafeCreatureAreaService.burst(player, player.getBoundingBox().getCenter(), tuning.radius(),
                tuning.maximumTargets(), ShieldMath.safeDamage(confirmed * tuning.damageScale()), SkillIds.CROWD_REPRISAL,
                SkillProcDamageService.DamageKind.CROWD_REPRISAL, budget, 0, target -> {},
                target -> ProjectileTargeting.hostile(player, target) && player.hasLineOfSight(target),
                target -> com.mistaboom.essence_ascendance.network.CombatVisualFeedback.defenseImpact(player.serverLevel(), target));
        RECENT.put(player, "Crowd Reprisal: confirmed primary=" + confirmed + "; source=" + primary.getUUID()
                + "; requested per target=" + confirmed * tuning.damageScale() + "; accepted=" + accepted
                + "; visited=" + budget.visitedIds() + "; attribution=defender/skill; LOS=defender; no recursive amplification");
    }
    public static List<String> diagnostics(ServerPlayer player) { return RECENT.containsKey(player) ? List.of(RECENT.get(player)) : List.of(); }
    public static void forget(ServerPlayer player) { RECENT.remove(player); }
    public static void clear() { RECENT.clear(); }
}
