package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mistaboom.essence_ascendance.vitality.ConsumableRecoveryService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Player.class)
public abstract class MetabolicFoodEligibilityMixin {
    @ModifyReturnValue(method = "canEat", at = @At("RETURN"))
    private boolean essenceAscendance$eatToHeal(boolean original) {
        return original || (Object)this instanceof ServerPlayer player && ConsumableRecoveryService.canEatForRecovery(player);
    }
}
