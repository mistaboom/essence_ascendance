package com.mistaboom.essence_ascendance.fabric.mixin;

import com.mistaboom.essence_ascendance.equipment.EquipmentGatheringService;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/*
 * Fabric has no NeoForge-style gameplay-only enchantment query event.
 *
 * These two vanilla helpers are the gameplay seam for item-specific and
 * entity/equipment enchantment levels. The common service only returns a
 * virtual level for server-side stacks currently held by a ServerPlayer.
 * No ItemStack enchantment component is mutated.
 */
@Mixin(EnchantmentHelper.class)
public abstract class EnchantmentHelperGatheringMixin {

    @Inject(
            method = "getItemEnchantmentLevel",
            at = @At("RETURN"),
            cancellable = true
    )
    private static void essenceAscendance$virtualItemFortuneLooting(
            Holder<Enchantment> enchantment,
            ItemStack stack,
            CallbackInfoReturnable<Integer> cir
    ) {
        cir.setReturnValue(
                EquipmentGatheringService.resolveVirtualEnchantmentLevel(
                        stack,
                        enchantment,
                        cir.getReturnValue()
                )
        );
    }

    @Inject(
            method = "getEnchantmentLevel",
            at = @At("RETURN"),
            cancellable = true
    )
    private static void essenceAscendance$virtualHeldFortuneLooting(
            Holder<Enchantment> enchantment,
            LivingEntity entity,
            CallbackInfoReturnable<Integer> cir
    ) {
        cir.setReturnValue(
                EquipmentGatheringService.resolveVirtualHeldEnchantmentLevel(
                        entity,
                        enchantment,
                        cir.getReturnValue()
                )
        );
    }
}
