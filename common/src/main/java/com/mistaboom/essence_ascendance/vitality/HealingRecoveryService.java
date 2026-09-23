package com.mistaboom.essence_ascendance.vitality;

import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.CommittedSkillService;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime;
import com.mistaboom.essence_ascendance.network.ProgressionVisualFeedback;
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
        return valid(player) && usefulHealing(player) > 0;
    }
    public static double usefulHealing(ServerPlayer player) {
        return usefulHealing(player, ConsumableRecoveryService.foodOrigin(player));
    }
    public static double usefulHealing(ServerPlayer player, boolean foodOrigin) {
        if (!valid(player)) return 0;
        var context = SkillEffectRuntime.context(player);
        var tuning = context.settings().vitality().damage();
        boolean conversion = context.isEffective(SkillIds.METABOLIC_CONVERSION);
        var food = player.getFoodData();
        double missingFood = Math.max(0, 20 - food.getFoodLevel()) + Math.max(0, 20 - food.getSaturationLevel());
        return HealingRoutingMath.demand(player.getMaxHealth() - player.getHealth(), missingFood,
                conversion ? tuning.metabolicConversion().foodPointsPerOverflowHealth() : 0,
                hasRecoverableDebt(player) ? VitalityDamageService.ledger(player).delayed.total() : 0,
                tuning.painPurge().queuePerHealing(), foodOrigin);
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
            double recovered = debt.recover(amount);
            if (recovered > 0) {
                VitalityDamageService.dirty(player);
                com.mistaboom.essence_ascendance.skill.effect.SkillHudEvents.record(player, SkillIds.PAIN_PURGE, "purged", recovered);
                ProgressionVisualFeedback.vitalityPurge(player, recovered);
            }
        }
        ConsumableRecoveryService.overflow(player, Math.min(accepted, Math.max(0, proposed - player.getMaxHealth())));
    }
    private static boolean valid(ServerPlayer player) {
        return player.isAlive() && !player.isRemoved() && !player.isSpectator() && !player.getAbilities().instabuild;
    }
}
