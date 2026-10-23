package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mistaboom.essence_ascendance.item.AscendanceCasterItem;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Suppress only the caster's slot shade; its native cooldown remains authoritative. */
@Mixin(GuiGraphicsExtractor.class)
abstract class CasterCooldownOverlayMixin {
    @WrapOperation(method = "itemCooldown", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemCooldowns;getCooldownPercent(Lnet/minecraft/world/item/ItemStack;F)F"),
            require = 1)
    private float essenceAscendance$hideCasterSlotCooldown(ItemCooldowns cooldowns, ItemStack stack,
            float partialTick, Operation<Float> original) {
        return stack.getItem() instanceof AscendanceCasterItem ? 0.0F
                : original.call(cooldowns, stack, partialTick);
    }
}
