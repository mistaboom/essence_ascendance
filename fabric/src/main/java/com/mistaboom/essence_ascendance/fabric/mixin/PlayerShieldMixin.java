package com.mistaboom.essence_ascendance.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mistaboom.essence_ascendance.equipment.EquipmentShieldService;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerShieldMixin {
    @Unique
    private ItemStack essenceAscendance$disabledShield = ItemStack.EMPTY;

    @Inject(method = "disableShield", at = @At("HEAD"))
    private void essenceAscendance$captureDisabledShield(CallbackInfo ci) {
        essenceAscendance$disabledShield = ((Player) (Object) this).getUseItem().copy();
    }

    @WrapOperation(method = "disableShield", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemCooldowns;addCooldown(Lnet/minecraft/world/item/Item;I)V"))
    private void essenceAscendance$guardReadiness(ItemCooldowns cooldowns, Item item, int ticks, Operation<Void> original) {
        Player player = (Player) (Object) this;
        boolean ascendanceShield = EquipmentShieldService.isShield(essenceAscendance$disabledShield);

        /*
         * The server sends the authoritative reduced cooldown. Re-applying the
         * stock 100 ticks on the client can arrive later and make Guard Readiness
         * look—and locally behave—as though it never changed.
         */
        if (player.level().isClientSide && ascendanceShield) {
            return;
        }
        if (ascendanceShield || (!player.level().isClientSide && item == Items.SHIELD)) {
            EquipmentShieldService.applyDisableCooldown(
                    player, essenceAscendance$disabledShield, cooldowns, item, ticks
            );
        } else {
            original.call(cooldowns, item, ticks);
        }
    }

    @WrapOperation(method = "hurtCurrentlyUsedShield", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z"))
    private boolean essenceAscendance$nativeShieldDurability(net.minecraft.world.item.ItemStack stack,
                                                           Item item, Operation<Boolean> original) {
        return original.call(stack, item) || (item == Items.SHIELD
                && EquipmentShieldService.functional(stack));
    }

}
