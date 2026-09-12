package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mistaboom.essence_ascendance.item.AscendanceCasterItem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldowns;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Hide only the Caster's slot shading. Its native cooldown and S2C synchronization still run. */
@Mixin(GuiGraphics.class)
abstract class CasterCooldownOverlayMixin {
    @WrapOperation(method = "renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemCooldowns;getCooldownPercent(Lnet/minecraft/world/item/Item;F)F"))
    private float essenceAscendance$hideCasterSlotCooldown(ItemCooldowns cooldowns, Item item,
                                                          float partialTick, Operation<Float> original) {
        return item instanceof AscendanceCasterItem ? 0.0F : original.call(cooldowns, item, partialTick);
    }
}
