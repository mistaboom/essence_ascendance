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
import net.minecraft.world.item.component.CustomData;

/** Small shared shield context; no independent progression, ownership or combat system. */
public final class EquipmentShieldService {
    public static final int VANILLA_RAISE_DELAY_TICKS = 5;
    public static final int MINIMUM_RAISE_DELAY_TICKS = 1;

    private static final String RAISE_DELAY_TICKS_TAG =
            "essence_ascendance_shield_raise_delay_ticks";

    private EquipmentShieldService() {}

    public static boolean isShield(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof AscendanceShieldItem;
    }

    public static boolean functional(ItemStack stack) {
        return isShield(stack) && !FracturedEquipmentData.isFractured(stack)
                && stack.getDamageValue() < stack.getMaxDamage();
    }

    public static boolean canGuard(LivingEntity holder, ItemStack stack) {
        return holder != null && holder.isAlive() && !holder.isRemoved() && !holder.isSpectator()
                && functional(stack) && (!(holder instanceof Player player)
                || !player.getCooldowns().isOnCooldown(stack.getItem()));
    }

    public static boolean isUsingShield(LivingEntity holder) {
        return holder.isUsingItem() && canGuard(holder, holder.getUseItem())
                && holder.getItemInHand(holder.getUsedItemHand()) == holder.getUseItem();
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
     * Native durability, reflection, and successful-block amplification belong to
     * the completed item tier. Invested bonuses are limited by the lower of the
     * holder's tier and the item's completed tier.
     */
    public static Context resolve(PlayerEssenceData data, EquipmentTier itemTier, boolean fractured) {
        var settings = EssenceConfigManager.get().shieldBalance();
        EquipmentTier playerTier = EquipmentTier.fromAscendanceTier(data.getTier());
        EquipmentTier effective = itemTier.order() <= playerTier.order() ? itemTier : playerTier;
        int durability = settings.durability().get(itemTier);
        if (fractured) {
            return new Context(itemTier, effective, durability, false,
                    0, 0, 0, 0, 0, 0, 0, 0);
        }
        double reflection = invested(data, effective, EssenceStats.DAMAGE_REFLECTION, EquipmentActivationType.HELD);
        return new Context(itemTier, effective, durability, true,
                settings.baseReflectionPercent(), settings.innateReflectionBonus().get(itemTier), reflection,
                settings.blockAmplification().get(itemTier),
                ShieldMath.percent(invested(data, effective, EssenceStats.GUARD_READINESS, EquipmentActivationType.GUARDING)),
                ShieldMath.percent(invested(data, effective, EssenceStats.GUARDED_MOVEMENT, EquipmentActivationType.GUARDING)),
                ShieldMath.percent(invested(data, effective, EssenceStats.KNOCKBACK_RESISTANCE, EquipmentActivationType.GUARDING)),
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

    /**
     * The logical server resolves live progression. Player ItemStack data gives
     * clients the same threshold without letting them calculate authority.
     */
    public static int raiseDelayTicks(LivingEntity holder, ItemStack stack) {
        if (!functional(stack)) {
            return VANILLA_RAISE_DELAY_TICKS;
        }
        if (holder instanceof ServerPlayer player) {
            Context context = context(player, stack);
            return ShieldMath.raiseDelayTicks(
                    VANILLA_RAISE_DELAY_TICKS,
                    context.guardReadinessPercent(),
                    MINIMUM_RAISE_DELAY_TICKS
            );
        }
        if (holder instanceof Player) {
            return syncedRaiseDelayTicks(stack);
        }
        return VANILLA_RAISE_DELAY_TICKS;
    }

    /** Client-safe fallback remains vanilla until the first authoritative stack sync. */
    public static int syncedRaiseDelayTicks(ItemStack stack) {
        CustomData customData = stack == null
                ? null
                : stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return VANILLA_RAISE_DELAY_TICKS;
        }

        int ticks = customData.copyTag().getInt(RAISE_DELAY_TICKS_TAG);
        return ticks >= MINIMUM_RAISE_DELAY_TICKS
                && ticks <= VANILLA_RAISE_DELAY_TICKS
                ? ticks
                : VANILLA_RAISE_DELAY_TICKS;
    }

    /** Synchronize only held shields and mutate their data only when the value changes. */
    public static void syncReadinessState(ServerPlayer holder) {
        syncRaiseDelayTicks(holder, holder.getMainHandItem());
        syncRaiseDelayTicks(holder, holder.getOffhandItem());
    }

    private static void syncRaiseDelayTicks(ServerPlayer holder, ItemStack stack) {
        if (!isShield(stack)) {
            return;
        }

        int resolvedTicks = raiseDelayTicks(holder, stack);
        if (syncedRaiseDelayTicks(stack) == resolvedTicks) {
            return;
        }

        CustomData.update(
                DataComponents.CUSTOM_DATA,
                stack,
                tag -> tag.putInt(RAISE_DELAY_TICKS_TAG, resolvedTicks)
        );
    }

    /** Native max damage belongs to the artifact, including while Fractured. Preserve absolute wear/data. */
    public static void refreshNativeDurability(ItemStack stack) {
        if (!isShield(stack)) return;
        int maximum = nativeDurability(EquipmentTierData.tier(stack));
        int damage = stack.getDamageValue();
        boolean fractured = FracturedEquipmentData.isFractured(stack) || damage >= maximum;

        if (stack.getMaxDamage() != maximum) {
            stack.set(DataComponents.MAX_DAMAGE, maximum);
        }

        // Also normalize corrupted/legacy stacks whose configured max already
        // matches but whose damage reached it without passing the fracture hook.
        if (fractured) {
            FracturedEquipmentData.markFractured(stack);
            stack.setDamageValue(Math.max(0, maximum - 1));
        } else if (stack.getDamageValue() != damage) {
            stack.setDamageValue(Math.max(0, damage));
        }
    }

    /** Called only for Player.disableShield's authoritative server-side cooldown operation. */
    public static void applyDisableCooldown(
            Player holder,
            ItemStack disabledStack,
            ItemCooldowns cooldowns,
            Item originalItem,
            int ticks
    ) {
        if (isShield(disabledStack)) {
            int duration = ticks;
            if (holder instanceof ServerPlayer player && functional(disabledStack)) {
                Context context = context(player, disabledStack);
                duration = ShieldMath.disableTicks(ticks, context.guardReadinessPercent(),
                        EssenceConfigManager.get().shieldBalance().minimumDisableTicks());
            }
            // One registered item means every tier/hand/slot/copy shares the same native player cooldown.
            cooldowns.addCooldown(disabledStack.getItem(), duration);
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
        syncReadinessState(holder);
        if (holder.isUsingItem() && isShield(holder.getUseItem()) && !isUsingShield(holder)) holder.stopUsingItem();
        com.mistaboom.essence_ascendance.skill.effect.GuardMobilityController.tick(holder);
    }

    public record Context(EquipmentTier itemTier, EquipmentTier effectiveTier, int nativeDurability,
                          boolean functional, double baseReflectionPercent, double innateReflectionBonusPercent,
                          double investedReflectionPercent,
                          double amplification, double guardReadinessPercent, double guardedMovementPercent,
                          double knockbackResistancePercent, double durabilityEfficiencyPercent) {
        /** Total no-investment reflection supplied by this shield's completed tier. */
        public double nativeReflectionPercent() {
            return ShieldMath.ordinaryPercent(baseReflectionPercent, innateReflectionBonusPercent, 0);
        }
        /** Native on-block reflection, independent of every player slider. */
        public double nativeBlockedReflectionPercent() {
            return ShieldMath.blockedPercent(nativeReflectionPercent(), 0, amplification);
        }
        /** Additional on-block reflection supplied by the applicable reflection investment. */
        public double investedBlockedReflectionPercent() {
            return ShieldMath.blockedPercent(0, investedReflectionPercent, amplification);
        }
        public double blockedReflectionPercent() {
            return ShieldMath.blockedPercent(nativeReflectionPercent(), investedReflectionPercent, amplification);
        }
        public double ordinaryReflectionPercent(double armorReflectionPercent) {
            return ShieldMath.ordinaryPercent(nativeReflectionPercent(), investedReflectionPercent, armorReflectionPercent);
        }
    }
}
