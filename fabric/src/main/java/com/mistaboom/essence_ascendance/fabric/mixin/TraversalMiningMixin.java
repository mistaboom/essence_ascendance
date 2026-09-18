package com.mistaboom.essence_ascendance.fabric.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mistaboom.essence_ascendance.movement.TraversalService;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Remove only the native off-ground tool penalty in supported immersion. The native submerged
 * efficiency attribute handles the other water penalty; tool rules and other modifiers remain.
 * NeoForge's position-sensitive getDigSpeed and Fabric's getDestroySpeed consume the same policy. */
@Mixin(Player.class)
abstract class TraversalMiningMixin {
    @ModifyExpressionValue(method = "getDestroySpeed",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;onGround()Z", remap = true), require = 1, expect = 1)
    private boolean essenceAscendance$swimmingToolEfficiency(boolean original) {
        return original || TraversalService.fluidBody((Player)(Object)this);
    }
}
