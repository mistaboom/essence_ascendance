package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.equipment.WeaponActionDamage;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DamageSource.class)
abstract class WeaponActionDamageSourceMixin {
    @Inject(method="getWeaponItem",at=@At("HEAD"),cancellable=true)
    private void essenceAscendance$acceptedWeapon(CallbackInfoReturnable<ItemStack> cir) {
        ItemStack weapon = WeaponActionDamage.weapon((DamageSource)(Object)this);
        if (weapon!=null) cir.setReturnValue(weapon);
    }
}
