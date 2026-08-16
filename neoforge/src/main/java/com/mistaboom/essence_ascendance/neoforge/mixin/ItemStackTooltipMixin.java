package com.mistaboom.essence_ascendance.neoforge.mixin;

import com.mistaboom.essence_ascendance.client.EssenceTooltipPipeline;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/*
 * Makes Essence Ascendance tooltip text part of ItemStack's generated tooltip
 * result itself.
 *
 * JEI 1.21.1 calls ItemStack#getTooltipLines directly when creating the `$`
 * tooltip-search index, so a later screen-only tooltip event is too late.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackTooltipMixin {

    @Inject(
            method = "getTooltipLines",
            at = @At("RETURN"),
            cancellable = true
    )
    private void essenceAscendance$applyGeneratedTooltip(
            Item.TooltipContext tooltipContext,
            @Nullable Player player,
            TooltipFlag tooltipFlag,
            CallbackInfoReturnable<List<Component>> callback
    ) {
        ItemStack stack =
                (ItemStack) (Object) this;

        /*
         * Vanilla currently returns a mutable list, but copying here prevents
         * another mod's immutable return wrapper from breaking our appender.
         */
        List<Component> tooltip =
                new ArrayList<>(
                        callback.getReturnValue()
                );

        EssenceTooltipPipeline.apply(
                stack,
                tooltip,
                tooltipContext,
                tooltipFlag
        );

        callback.setReturnValue(
                tooltip
        );
    }
}
