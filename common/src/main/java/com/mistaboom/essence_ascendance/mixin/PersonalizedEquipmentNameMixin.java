package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.client.PersonalizedEquipmentClient;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Owner-held name overlay for all observers; persisted names are never rewritten. */
@Mixin(ItemStack.class)
public abstract class PersonalizedEquipmentNameMixin {
    @Inject(method = "getHoverName", at = @At("HEAD"), cancellable = true)
    private void essenceAscendance$personalizedName(CallbackInfoReturnable<Component> callback) {
        var name = PersonalizedEquipmentClient.name((ItemStack) (Object) this);
        if (name != null) callback.setReturnValue(name);
    }
}
