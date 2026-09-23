package com.mistaboom.essence_ascendance.vitality;

import com.mistaboom.essence_ascendance.attunement.AttunementEvent;
import com.mistaboom.essence_ascendance.attunement.AttunementGameplay;
import com.mistaboom.essence_ascendance.equipment.EquipmentMaintenanceService;
import com.mistaboom.essence_ascendance.network.ProgressionVisualFeedback;
import com.mistaboom.essence_ascendance.skill.CommittedSkillService;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodConstants;
import net.minecraft.world.item.ItemStack;
import java.util.List;
import java.util.function.Supplier;

/** Native completed consumables share one scoped, exactly-once recovery transaction.
 * Ordinary healing is observed only at its accepted native health write, after loader cancellation. */
public final class ConsumableRecoveryService {
    private static final ThreadLocal<Frame> CONSUMPTION = new ThreadLocal<>();
    private static final ThreadLocal<ServerPlayer> FOOD_HEALING = new ThreadLocal<>();
    private static final class Frame {
        final ServerPlayer player;
        final ItemStack source;
        final ItemStack identity;
        final int nutrition;
        final double saturationPoints;
        final boolean fullHunger;
        Frame(ServerPlayer player, ItemStack source) {
            this.player = player; this.identity = source; this.source = source.copy();
            var food = source.get(DataComponents.FOOD);
            nutrition = food == null ? 0 : Math.max(0, food.nutrition());
            saturationPoints = food == null ? 0 : Math.max(0, FoodConstants.saturationByModifier(nutrition, food.saturation()));
            fullHunger = player.getFoodData().getFoodLevel() >= FoodConstants.MAX_FOOD;
        }
    }
    private ConsumableRecoveryService() { }
    public static boolean consumingFood() {
        return CONSUMPTION.get() != null && CONSUMPTION.get().nutrition > 0;
    }
    public static boolean foodOrigin(ServerPlayer player) {
        Frame frame = CONSUMPTION.get();
        return FOOD_HEALING.get() == player || frame != null && frame.player == player && frame.nutrition > 0;
    }
    public static void withFoodHealing(ServerPlayer player, Runnable action) {
        ServerPlayer previous = FOOD_HEALING.get();
        FOOD_HEALING.set(player);
        try { action.run(); }
        finally { if (previous == null) FOOD_HEALING.remove(); else FOOD_HEALING.set(previous); }
    }
    public static boolean canEatForRecovery(ServerPlayer player) {
        if (!valid(player)) return false;
        boolean healing = HealingRecoveryService.usefulHealing(player, true) > 0
                && CommittedSkillService.isEffective(player, SkillIds.METABOLIC_CONVERSION)
                && SkillEffectRuntime.resolvedSettings(player).vitality().damage().metabolicConversion().healthPerNutrition() > 0;
        return healing || canEatForMaintenance(player);
    }

    /** Shared manual/automatic meal gate for Metabolic Mending. */
    public static boolean canEatForMaintenance(ServerPlayer player) {
        return valid(player)
                && EquipmentMaintenanceService.hasDamagedItem(player)
                && CommittedSkillService.isEffective(player, SkillIds.METABOLIC_MENDING)
                && SkillEffectRuntime.resolvedSettings(player).utility().metabolicMending().repairFractionPerFoodPoint() > 0;
    }
    public static ItemStack complete(ServerPlayer player, ItemStack source, Supplier<ItemStack> original) {
        if (!valid(player) || source.isEmpty()) return original.get();
        Frame previous = CONSUMPTION.get(), frame = new Frame(player, source);
        if (previous != null && previous.player == player && previous.identity == source) return original.get();
        CONSUMPTION.set(frame);
        try {
            ItemStack result = original.get(); // native stack, container, effects, criteria and item callbacks
            if (valid(player)) {
                var context = SkillEffectRuntime.context(player);
                if (frame.fullHunger && frame.nutrition > 0 && context.isEffective(SkillIds.METABOLIC_CONVERSION)) {
                    double amount = frame.nutrition * context.settings().vitality().damage().metabolicConversion().healthPerNutrition();
                    ServerPlayer previousHealing = FOOD_HEALING.get(); FOOD_HEALING.set(player);
                    try {
                        float beforeHealth = player.getHealth();
                        if (amount > 0) player.heal((float)Math.min(Float.MAX_VALUE, amount));
                        if (player.getHealth() > beforeHealth) {
                            double healed = player.getHealth() - beforeHealth;
                            com.mistaboom.essence_ascendance.skill.effect.SkillHudEvents.record(player, SkillIds.METABOLIC_CONVERSION, "healed", healed);
                            ProgressionVisualFeedback.recoveryTransfer(player, healed, false);
                        }
                    }
                    finally { if (previousHealing == null) FOOD_HEALING.remove(); else FOOD_HEALING.set(previousHealing); }
                }
                SkillEffectRuntime.onFoodConsumed(player, frame.source, frame.nutrition, frame.saturationPoints);
            }
            return result;
        } finally { if (previous == null) CONSUMPTION.remove(); else CONSUMPTION.set(previous); }
    }
    /** Overflow is taken from the accepted post-event proposed HP, not the requested heal argument. */
    public static void overflow(ServerPlayer player, double excess) {
        if (!valid(player) || !Double.isFinite(excess) || excess <= 0 || foodOrigin(player)) return;
        var context = SkillEffectRuntime.context(player);
        if (!context.isEffective(SkillIds.METABOLIC_CONVERSION)) return;
        var food = player.getFoodData();
        double before = AttunementGameplay.food(player);
        var restored = DamageRoutingMath.restoreFood(
                excess * context.settings().vitality().damage().metabolicConversion().foodPointsPerOverflowHealth(),
                food.getFoodLevel(), food.getSaturationLevel(), VitalityDamageService.carry(player, SkillIds.METABOLIC_CONVERSION), FoodConstants.MAX_FOOD);
        food.setFoodLevel(restored.food()); food.setSaturation((float)restored.saturation());
        VitalityDamageService.carry(player, SkillIds.METABOLIC_CONVERSION, restored.carry());
        double actual = AttunementGameplay.food(player) - before;
        if (actual > 0) com.mistaboom.essence_ascendance.skill.effect.SkillHudEvents.record(player, SkillIds.METABOLIC_CONVERSION, "food", actual);
        if (actual > 0) ProgressionVisualFeedback.recoveryTransfer(player, actual, true);
        if (actual > 0) SkillEffectRuntime.reportOutcome(player,
                new AttunementEvent(AttunementGameplay.action("metabolic_conversion"), List.of(
                        AttunementEvent.Outcome.eligible("restore_hunger", SkillIds.METABOLIC_CONVERSION.toString(), actual))));
    }
    private static boolean valid(ServerPlayer player) {
        return player.isAlive() && !player.isRemoved() && !player.isSpectator() && !player.getAbilities().instabuild;
    }
}
