package com.mistaboom.essence_ascendance.vitality;

import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.CommittedSkillService;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime;
import net.minecraft.server.level.ServerPlayer;

/** Shared recovery eligibility and accepted-heal side effects. Queue recovery is not another heal event. */
public final class HealingRecoveryService {
    private HealingRecoveryService() { }
    public static boolean hasRecoverableDebt(ServerPlayer player) {
        return valid(player) && !VitalityDamageService.ledger(player).delayed.isEmpty()
                && CommittedSkillService.isEffective(player, SkillIds.PAIN_PURGE)
                && SkillEffectRuntime.resolvedSettings(player).vitality().damage().painPurge().queuePerHealing() > 0;
    }
    public static boolean needsRecovery(ServerPlayer player) {
        return valid(player) && (player.getHealth() < player.getMaxHealth() || hasRecoverableDebt(player));
    }
    public static double usefulHealing(ServerPlayer player) {
        double missing = Math.max(0, player.getMaxHealth() - player.getHealth());
        if (!hasRecoverableDebt(player)) return missing;
        return HealingRoutingMath.usefulHealing(missing, VitalityDamageService.ledger(player).delayed.total(),
                SkillEffectRuntime.resolvedSettings(player).vitality().damage().painPurge().queuePerHealing());
    }
    /** Runs only after the native accepted heal write succeeds, including heals clamped at full HP. */
    public static void acceptedHeal(ServerPlayer player, double before, double proposed) {
        if (!valid(player)) return;
        double accepted = HealingRoutingMath.accepted(before, proposed);
        if (accepted <= 0) return;
        var context = SkillEffectRuntime.context(player);
        if (context.isEffective(SkillIds.PAIN_PURGE)) {
            var debt = VitalityDamageService.ledger(player).delayed;
            double amount = HealingRoutingMath.mirrored(accepted, debt.total(),
                    context.settings().vitality().damage().painPurge().queuePerHealing());
            if (debt.recover(amount) > 0) VitalityDamageService.dirty(player);
        }
        ConsumableRecoveryService.overflow(player, Math.min(accepted, Math.max(0, proposed - player.getMaxHealth())));
    }
    private static boolean valid(ServerPlayer player) {
        return player.isAlive() && !player.isRemoved() && !player.isSpectator() && !player.getAbilities().instabuild;
    }
}
