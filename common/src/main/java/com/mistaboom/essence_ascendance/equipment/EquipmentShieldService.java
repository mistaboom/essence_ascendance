package com.mistaboom.essence_ascendance.equipment;

import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import com.mistaboom.essence_ascendance.item.AscendanceItems;
import com.mistaboom.essence_ascendance.item.AscendanceShieldItem;
import com.mistaboom.essence_ascendance.progression.StatScalingService;
import com.mistaboom.essence_ascendance.stat.EssenceStats;
import com.mistaboom.essence_ascendance.stat.StatDefinition;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Small shared shield context; no independent progression, ownership or combat system. */
public final class EquipmentShieldService {
    private EquipmentShieldService() {}

    public static boolean isShield(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof AscendanceShieldItem;
    }

    public static boolean functional(ItemStack stack) {
        return isShield(stack) && !FracturedEquipmentData.isFractured(stack)
                && stack.getDamageValue() < stack.getMaxDamage();
    }

    public static boolean canGuard(LivingEntity holder, ItemStack stack) {
        return functional(stack) && (!(holder instanceof Player player)
                || !player.getCooldowns().isOnCooldown(stack.getItem()));
    }

    public static boolean isUsingShield(LivingEntity holder) {
        return holder.isUsingItem() && canGuard(holder, holder.getUseItem());
    }

    public static boolean isGuarding(LivingEntity holder) {
        return isUsingShield(holder) && holder.isBlocking();
    }

    public static PlayerEssenceData playerData(ServerPlayer player) {
        return EssenceSavedData.get(player.server).getPlayerData(player.getUUID());
    }

    /** Main hand wins a dual-shield tie. Always select the complete context of ONE stack. */
    public static Context heldContext(ServerPlayer holder) {
        if (functional(holder.getMainHandItem())) return context(holder, holder.getMainHandItem());
        if (functional(holder.getOffhandItem())) return context(holder, holder.getOffhandItem());
        return null;
    }

    public static Context blockingContext(ServerPlayer holder) {
        return isGuarding(holder) ? context(holder, holder.getUseItem()) : null;
    }

    public static Context context(ServerPlayer holder, ItemStack stack) {
        if (!isShield(stack)) return null;
        return resolve(playerData(holder), EquipmentTierData.tier(stack), !functional(stack));
    }

    /**
     * Combat and S2C tooltips share this calculation, even while inspected/lowered.
     * Durability and amplification belong to the completed item tier; only investments
     * are limited by the lower of the holder's tier and the item's tier.
     */
    public static Context resolve(PlayerEssenceData data, EquipmentTier itemTier, boolean fractured) {
        var settings = EssenceConfigManager.get().shieldBalance();
        EquipmentTier playerTier = EquipmentTier.fromAscendanceTier(data.getTier());
        EquipmentTier effective = itemTier.order() <= playerTier.order() ? itemTier : playerTier;
        int durability = settings.durability().get(itemTier);
        if (fractured) return new Context(itemTier, effective, durability, false, 0, 0, 0, 0, 0, 0);
        double reflection = invested(data, effective, EssenceStats.DAMAGE_REFLECTION, EquipmentActivationType.HELD);
        return new Context(itemTier, effective, durability, true, settings.innateReflectionPercent(), reflection,
                settings.blockAmplification().get(itemTier),
                ShieldMath.percent(invested(data, effective, EssenceStats.GUARD_RECOVERY, EquipmentActivationType.GUARDING)),
                ShieldMath.percent(invested(data, effective, EssenceStats.GUARDED_MOVEMENT, EquipmentActivationType.GUARDING)),
                ShieldMath.percent(invested(data, effective, EssenceStats.DURABILITY_EFFICIENCY, EquipmentActivationType.HELD)));
    }

    private static double invested(PlayerEssenceData data, EquipmentTier effective, StatDefinition stat,
                                   EquipmentActivationType activation) {
        if (effective == EquipmentTier.LATENT) return 0;
        return StatScalingService.scaledBonusForTier(data, stat, effective.ascendanceTier())
                * EquipmentProfiles.SHIELD.statStrength(activation, stat);
    }

    public static int nativeDurability(EquipmentTier tier) {
        return EssenceConfigManager.get().shieldBalance().durability().get(tier);
    }

    /** Native max damage belongs to the artifact, including while Fractured. Preserve absolute wear/data. */
    public static void refreshNativeDurability(ItemStack stack) {
        if (!isShield(stack)) return;
        int maximum = nativeDurability(EquipmentTierData.tier(stack));
        if (stack.getMaxDamage() == maximum) return;
        int damage = stack.getDamageValue();
        stack.set(DataComponents.MAX_DAMAGE, maximum);
        if (FracturedEquipmentData.isFractured(stack) || damage >= maximum) {
            FracturedEquipmentData.markFractured(stack);
            stack.setDamageValue(Math.max(0, maximum - 1));
        } else {
            stack.setDamageValue(Math.max(0, damage));
        }
    }

    /** Called only at Player.disableShield's authoritative cooldown operation, before stopUsingItem. */
    public static void applyDisableCooldown(Player holder, ItemCooldowns cooldowns, Item originalItem, int ticks) {
        ItemStack active = holder.getUseItem();
        if (isShield(active)) {
            int duration = ticks;
            if (holder instanceof ServerPlayer player && functional(active)) {
                Context context = context(player, active);
                duration = ShieldMath.disableTicks(ticks, context.guardRecoveryPercent(),
                        EssenceConfigManager.get().shieldBalance().minimumDisableTicks());
            }
            // One registered item means every tier/hand/slot/copy shares the same native player cooldown.
            cooldowns.addCooldown(active.getItem(), duration);
            // Switching to a vanilla shield must not bypass an Ascendance guard break.
            cooldowns.addCooldown(Items.SHIELD, duration);
        } else {
            cooldowns.addCooldown(originalItem, ticks);
            if (originalItem == Items.SHIELD) {
                cooldowns.addCooldown(AscendanceItems.ASCENDANCE_SHIELD.get(), ticks);
            }
        }
    }

    public static void tick(ServerPlayer holder) {
        if (!holder.isUsingItem() || !isShield(holder.getUseItem())) return;
        if (!canGuard(holder, holder.getUseItem())) holder.stopUsingItem();
        else holder.setSprinting(false);
    }

    public record Context(EquipmentTier itemTier, EquipmentTier effectiveTier, int nativeDurability,
                          boolean functional, double innateReflectionPercent, double investedReflectionPercent,
                          double amplification, double guardRecoveryPercent, double guardedMovementPercent,
                          double durabilityEfficiencyPercent) {
        /** Native on-block reflection, independent of every player slider. */
        public double nativeBlockedReflectionPercent() {
            return ShieldMath.blockedPercent(innateReflectionPercent, 0, amplification);
        }
        /** Additional on-block reflection supplied by the applicable reflection investment. */
        public double investedBlockedReflectionPercent() {
            return ShieldMath.blockedPercent(0, investedReflectionPercent, amplification);
        }
        public double blockedReflectionPercent() {
            return ShieldMath.blockedPercent(innateReflectionPercent, investedReflectionPercent, amplification);
        }
        public double ordinaryReflectionPercent(double armorReflectionPercent) {
            return ShieldMath.ordinaryPercent(innateReflectionPercent, investedReflectionPercent, armorReflectionPercent);
        }
    }
}
