package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.utility.VillagePatronService;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.inventory.MerchantResultSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.Merchant;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Times Village Patron feedback to a completed discounted trade while the merchant UI is visible. */
@Mixin(MerchantResultSlot.class)
public abstract class VillagePatronTradeMixin {
    @Shadow @Final private MerchantContainer slots;
    @Shadow @Final private Merchant merchant;
    @Unique private MerchantOffer essenceAscendance$completedOffer;

    @Inject(method = "onTake", at = @At("HEAD"))
    private void essenceAscendance$captureDiscountedOffer(Player player, ItemStack stack, CallbackInfo ci) {
        essenceAscendance$completedOffer = slots.getActiveOffer();
    }

    @Inject(method = "onTake", at = @At("RETURN"))
    private void essenceAscendance$acknowledgeDiscountedTrade(Player player, ItemStack stack, CallbackInfo ci) {
        VillagePatronService.completedDiscountedTrade(player, merchant, essenceAscendance$completedOffer);
        essenceAscendance$completedOffer = null;
    }
}
