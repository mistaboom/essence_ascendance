package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mistaboom.essence_ascendance.gameplay.CompanionOutcomes;
import net.minecraft.world.entity.TamableAnimal;
import org.spongepowered.asm.mixin.Mixin;

/** Native ownership mutations invalidate old-owner challenges even when transferred back in the same tick. */
@Mixin(TamableAnimal.class)
public abstract class TamedCompanionOwnershipMixin {
    //? if >=26.1.2 {
    /*@WrapMethod(method = "setOwnerReference")
    private void essenceAscendance$companionOwner(net.minecraft.world.entity.EntityReference<net.minecraft.world.entity.LivingEntity> owner, Operation<Void> original) {
        var self=(TamableAnimal)(Object)this; var previous=CompanionOutcomes.ownerId(self); boolean tame=self.isTame();
        original.call(owner); CompanionOutcomes.ownershipChanged(self,previous,tame);
    }
    @WrapMethod(method = "setOwner(Lnet/minecraft/world/entity/LivingEntity;)V")
    private void essenceAscendance$companionEntityOwner(net.minecraft.world.entity.LivingEntity owner, Operation<Void> original) {
        var self=(TamableAnimal)(Object)this; var previous=CompanionOutcomes.ownerId(self); boolean tame=self.isTame();
        original.call(owner); CompanionOutcomes.ownershipChanged(self,previous,tame);
    }
    *///? } else {
    @WrapMethod(method = "setOwnerUUID")
    private void essenceAscendance$companionOwner(java.util.UUID owner, Operation<Void> original) {
        var self=(TamableAnimal)(Object)this; var previous=CompanionOutcomes.ownerId(self); boolean tame=self.isTame();
        original.call(owner); CompanionOutcomes.ownershipChanged(self,previous,tame);
    }
    //? }
    @WrapMethod(method = "setTame")
    private void essenceAscendance$companionTame(boolean tamed,boolean applySideEffects,Operation<Void> original) {
        var self=(TamableAnimal)(Object)this; var previous=CompanionOutcomes.ownerId(self); boolean tame=self.isTame();
        original.call(tamed,applySideEffects); CompanionOutcomes.ownershipChanged(self,previous,tame);
    }
}
