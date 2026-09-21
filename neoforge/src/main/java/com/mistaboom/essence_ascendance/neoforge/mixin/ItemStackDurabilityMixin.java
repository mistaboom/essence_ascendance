package com.mistaboom.essence_ascendance.neoforge.mixin;

import com.mistaboom.essence_ascendance.equipment.AscendanceArtifactDurabilityService;
import com.mistaboom.essence_ascendance.equipment.EquipmentGatheringService;
import com.mistaboom.essence_ascendance.equipment.FracturedEquipmentData;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(ItemStack.class)
public abstract class ItemStackDurabilityMixin {

    @Unique private ServerPlayer essenceAscendance$durabilityOwner;

    @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(
            method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V")
    private void essenceAscendance$wearFrame(int amount, ServerLevel level, ServerPlayer player,
            Consumer<Item> onBreak, com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
        ItemStack stack = (ItemStack)(Object)this;
        ServerPlayer previous = essenceAscendance$durabilityOwner;
        boolean fracturedBefore = FracturedEquipmentData.isFractured(stack);
        essenceAscendance$durabilityOwner = player;
        try {
            original.call(amount, level, player, onBreak);
            if (!fracturedBefore && FracturedEquipmentData.isFractured(stack) && player != null)
                player.playSound(stack.getBreakingSound());
        } finally {
            essenceAscendance$durabilityOwner = previous;
        }
    }

    @ModifyVariable(
            method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private int essenceAscendance$scaleHeldDurabilityLoss(
            int modifiedAmount,
            int originalAmount,
            ServerLevel level,
            LivingEntity entity,
            Consumer<Item> onBreak
    ) {
        /*
         * @ModifyVariable passes the value being modified first. When target
         * arguments are captured, the complete target argument list follows.
         * Because the variable being modified is itself the target method's
         * first int argument, the amount therefore appears twice here:
         *
         *   modifiedAmount, originalAmount, level, entity, onBreak
         *
         * Use modifiedAmount as the current value so this remains composable
         * with any earlier transformer touching the same argument.
         */
        ItemStack stack = (ItemStack) (Object) this;
        modifiedAmount = AscendanceArtifactDurabilityService.preventDamageWhileFractured(
                stack,
                modifiedAmount
        );
        if (modifiedAmount <= 0 || !(entity instanceof ServerPlayer player)) {
            return modifiedAmount;
        }

        return EquipmentGatheringService.modifyHeldDurabilityDamage(
                player,
                stack,
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
         * NeoForge's server-level LivingEntity overload above, where HELD applicability is applied
         * exactly once. Applying it here as well would double-scale
         * tools/weapons.
         */
        ItemStack stack = (ItemStack) (Object) this;
        modifiedAmount = AscendanceArtifactDurabilityService.preventDamageWhileFractured(
                stack,
                modifiedAmount
        );
        if (modifiedAmount <= 0) {
            return 0;
        }

        if (slot == EquipmentSlot.MAINHAND
                || slot == EquipmentSlot.OFFHAND) {
            return modifiedAmount;
        }

        return EquipmentGatheringService.modifyWornDurabilityDamage(
                entity,
                stack,
                slot,
                modifiedAmount
        );
    }

    @Redirect(
            method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;processDurabilityChange(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;I)I"
            )
    )
    private int essenceAscendance$fractureBeforeVanillaBreak(
            ServerLevel level,
            ItemStack stack,
            int requestedDamage
    ) {
        // Let vanilla/enchants (especially Unbreaking) determine the real
        // durability loss first. Only then decide whether that real loss
        // would cross the break threshold. This prevents the stack from ever
        // reaching vanilla's shrink/remove path.
        int actualDamage = EnchantmentHelper.processDurabilityChange(
                level,
                stack,
                requestedDamage
        );
        if (essenceAscendance$durabilityOwner != null) {
            actualDamage = SkillEffectRuntime.modifyDurabilityLoss(
                    essenceAscendance$durabilityOwner, stack, actualDamage);
        }
        return AscendanceArtifactDurabilityService.fractureBeforeVanillaBreak(
                stack,
                actualDamage
        );
    }
}
