package com.mistaboom.essence_ascendance.neoforge.mixin;

import com.mistaboom.essence_ascendance.equipment.EquipmentGatheringService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.function.Consumer;

@Mixin(ItemStack.class)
public abstract class ItemStackDurabilityMixin {

    @ModifyVariable(
            method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private int essenceAscendance$scaleHeldDurabilityLoss(
            int modifiedAmount,
            int originalAmount,
            ServerLevel level,
            ServerPlayer player,
            Consumer<Item> onBreak
    ) {
        /*
         * @ModifyVariable passes the value being modified first. When target
         * arguments are captured, the complete target argument list follows.
         * Because the variable being modified is itself the target method's
         * first int argument, the amount therefore appears twice here:
         *
         *   modifiedAmount, originalAmount, level, player, onBreak
         *
         * Use modifiedAmount as the current value so this remains composable
         * with any earlier transformer touching the same argument.
         */
        if (player == null) {
            return modifiedAmount;
        }

        return EquipmentGatheringService.modifyHeldDurabilityDamage(
                player,
                (ItemStack) (Object) this,
                modifiedAmount
        );
    }

    @ModifyVariable(
            method = "hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private int essenceAscendance$scaleWornDurabilityLoss(
            int modifiedAmount,
            int originalAmount,
            LivingEntity entity,
            EquipmentSlot slot
    ) {
        /*
         * Hand equipment is intentionally left unchanged at this outer
         * overload. Vanilla funnels ServerPlayer durability work into the
         * server-level overload above, where HELD applicability is applied
         * exactly once. Applying it here as well would double-scale
         * tools/weapons.
         */
        if (slot == EquipmentSlot.MAINHAND
                || slot == EquipmentSlot.OFFHAND) {
            return modifiedAmount;
        }

        return EquipmentGatheringService.modifyWornDurabilityDamage(
                entity,
                (ItemStack) (Object) this,
                slot,
                modifiedAmount
        );
    }
}
