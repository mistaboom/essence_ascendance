package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.utility.VillagePatronService;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the generated patron discount after native gossip and Hero-of-the-Village pricing. */
@Mixin(Villager.class)
public abstract class VillagePatronMixin {
    @Inject(method = "updateSpecialPrices", at = @At("TAIL"))
    private void essenceAscendance$villagePatronPrices(Player player, CallbackInfo ci) {
        VillagePatronService.applyTradeDiscount((Villager)(Object)this, player);
    }
}
