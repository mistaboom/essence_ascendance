package com.mistaboom.essence_ascendance.equipment;

import com.mistaboom.essence_ascendance.config.*;
import com.mistaboom.essence_ascendance.data.*;
import com.mistaboom.essence_ascendance.item.AscendanceFishingRodItem;
import com.mistaboom.essence_ascendance.progression.StatScalingService;
import com.mistaboom.essence_ascendance.stat.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
import java.util.*;

/** One rod context for gameplay, synchronization and projected equipment inspection. */
public final class EquipmentFishingRodService {
    private static final TagKey<Item> RODS = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "fishing_rods"));
    private EquipmentFishingRodService() { }
    public static boolean isRod(ItemStack stack) {
        return stack != null && !stack.isEmpty() && (stack.getItem() instanceof FishingRodItem || stack.is(RODS));
    }
    public static boolean holdsRod(LivingEntity entity) { return isRod(entity.getMainHandItem()) || isRod(entity.getOffhandItem()); }
    public static boolean isAscendanceRod(ItemStack stack) { return stack != null && stack.getItem() instanceof AscendanceFishingRodItem; }
    public static boolean functional(ItemStack stack) {
        return isAscendanceRod(stack) && !FracturedEquipmentData.isFractured(stack) && stack.getDamageValue() < stack.getMaxDamage();
    }
    /** Existing MAX convention for passive Luck/XP, including an actual offhand rod. */
    public static EquipmentStatState actionState(LivingEntity player) {
        var state = EquipmentStatResolver.evaluateForHand(player, net.minecraft.world.InteractionHand.MAIN_HAND);
        if (isRod(player.getOffhandItem())) state = state.mergeMax(EquipmentStatResolver.evaluateHeldOnly(player, net.minecraft.world.InteractionHand.OFF_HAND));
        return state;
    }
    public static Context context(ServerPlayer player, ItemStack stack) {
        return resolve(EssenceSavedData.get(player.server).getPlayerData(player.getUUID()), EquipmentTierData.tier(stack), !functional(stack));
    }
    public static Context resolve(PlayerEssenceData data, EquipmentTier tier, boolean fractured) {
        var tuning = EssenceConfigManager.get().fishingRodBalance();
        var nativeTier = tuning.tier(tier);
        var playerTier = EquipmentTier.fromAscendanceTier(data.getTier());
        var effective = tier.order() <= playerTier.order() ? tier : playerTier;
        Map<ResourceLocation, Double> bonuses = new LinkedHashMap<>();
        for (var entry : EquipmentProfiles.FISHING_ROD.statApplicability(EquipmentActivationType.HELD).entrySet()) {
            var stat = EssenceStatRegistry.get(entry.getKey()).orElseThrow();
            bonuses.put(stat.id(), fractured || effective == EquipmentTier.LATENT ? 0 :
                    StatScalingService.scaledBonusForTier(data, stat, effective.ascendanceTier()) * entry.getValue());
        }
        return new Context(tier, effective, nativeTier, !fractured, bonuses);
    }
    public static void refreshNativeDurability(ItemStack stack) {
        if (!isAscendanceRod(stack) || !EssenceConfigManager.authoritativeReady() && EssenceConfigManager.clientRuntime() == null) return;
        int maximum = EssenceConfigManager.get().fishingRodBalance().tier(EquipmentTierData.tier(stack)).durability();
        int damage = stack.getDamageValue();
        stack.set(DataComponents.MAX_DAMAGE, maximum);
        if (damage >= maximum || FracturedEquipmentData.isFractured(stack)) {
            FracturedEquipmentData.markFractured(stack); stack.setDamageValue(maximum - 1);
        } else stack.setDamageValue(damage);
    }
    public record Context(EquipmentTier itemTier, EquipmentTier effectiveTier, FishingRodBalanceSettings.Tier baseline,
                          boolean functional, Map<ResourceLocation, Double> bonuses) {
        public Context { bonuses = Map.copyOf(bonuses); }
        public double bonus(StatDefinition stat) { return bonuses.getOrDefault(stat.id(), 0.0); }
        public double castSpeed() { return baseline.castSpeed() * (1 + bonus(EssenceStats.PROJECTILE_SPEED) / 100); }
        public double targetForceMultiplier() { return 1 + bonus(EssenceStats.ATTACK_KNOCKBACK) / 100; }
        public double fishingMultiplier() { return 1 + bonus(EssenceStats.FISHING_SPEED) / 100; }
    }
}
