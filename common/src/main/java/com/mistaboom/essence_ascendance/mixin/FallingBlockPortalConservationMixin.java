package com.mistaboom.essence_ascendance.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
//? if >=26.1.2
/*import net.minecraft.world.level.portal.TeleportTransition;*/
//? if <26.1.2
import net.minecraft.world.level.portal.DimensionTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep the destination falling block; prevent the removed source from also placing or dropping. */
@Mixin(FallingBlockEntity.class)
abstract class FallingBlockPortalConservationMixin {
    //? if >=26.1.2
    /*@Inject(method = "teleport", at = @At("RETURN"))*/
    //? if <26.1.2
    @Inject(method = "changeDimension", at = @At("RETURN"))
    //? if >=26.1.2
    /*private void essenceAscendance$preserveSingleTransferredBlock(TeleportTransition transition, CallbackInfoReturnable<Entity> cir) {*/
    //? if <26.1.2
    private void essenceAscendance$preserveSingleTransferredBlock(DimensionTransition transition,
                                                                  //? if <26.1.2
                                                                  CallbackInfoReturnable<Entity> cir) {
        if (cir.getReturnValue() != null) {
            ((FallingBlockEntity) (Object) this).forceTickAfterTeleportToDuplicate = false;
        }
    }
}
