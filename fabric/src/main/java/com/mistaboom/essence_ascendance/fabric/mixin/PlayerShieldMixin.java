package com.mistaboom.essence_ascendance.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mistaboom.essence_ascendance.equipment.EquipmentDamageService;
import com.mistaboom.essence_ascendance.equipment.EquipmentShieldService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldowns;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerShieldMixin {
    @WrapOperation(method = "hurt", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean essenceAscendance$reflectionScope(Player receiver, DamageSource source, float amount,
                                                      Operation<Boolean> original) {
        if (!(receiver instanceof ServerPlayer player)) return original.call(receiver, source, amount);
        EquipmentDamageService.beginDamage(player, source);
        boolean returned = false;
        try {
            boolean result = original.call(receiver, source, amount);
            returned = true;
            return result;
        } finally {
            EquipmentDamageService.endDamage(player, source, returned);
        }
    }

    // Player overrides actuallyHurt; a hook on LivingEntity.actuallyHurt misses player health changes.
    @Inject(method = "actuallyHurt", at = @At("HEAD"))
    private void essenceAscendance$beforeHealth(DamageSource source, float amount, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player) EquipmentDamageService.beginHealthMeasurement(player, source);
    }

    @Inject(method = "actuallyHurt", at = @At("RETURN"))
    private void essenceAscendance$afterHealth(DamageSource source, float amount, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player) EquipmentDamageService.endHealthMeasurement(player);
    }

    @WrapOperation(method = "disableShield", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemCooldowns;addCooldown(Lnet/minecraft/world/item/Item;I)V"))
    private void essenceAscendance$guardRecovery(ItemCooldowns cooldowns, Item item, int ticks, Operation<Void> original) {
        Player player = (Player) (Object) this;
        if (EquipmentShieldService.isShield(player.getUseItem()) || item == net.minecraft.world.item.Items.SHIELD) {
            EquipmentShieldService.applyDisableCooldown(player, cooldowns, item, ticks);
        } else original.call(cooldowns, item, ticks);
    }

    @WrapOperation(method = "hurtCurrentlyUsedShield", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z"))
    private boolean essenceAscendance$nativeShieldDurability(net.minecraft.world.item.ItemStack stack,
                                                           Item item, Operation<Boolean> original) {
        return original.call(stack, item) || (item == net.minecraft.world.item.Items.SHIELD
                && EquipmentShieldService.functional(stack));
    }

}
