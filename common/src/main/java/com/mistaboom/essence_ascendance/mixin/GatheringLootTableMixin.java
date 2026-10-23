package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mistaboom.essence_ascendance.gathering.GatheringLootService;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(LivingEntity.class)
public abstract class GatheringLootTableMixin {
    //? if >=26.1.2
    /*@WrapMethod(method = "dropFromLootTable(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;Z)V")*/
    //? if <26.1.2
    @WrapMethod(method = "dropFromLootTable")
    //? if >=26.1.2
    /*private void essenceAscendance$gatheringStudyLoot(net.minecraft.server.level.ServerLevel level, DamageSource source, boolean causedByPlayer,*/
    //? if <26.1.2
    private void essenceAscendance$gatheringStudyLoot(DamageSource source, boolean causedByPlayer,
                                                      Operation<Void> original) {
        GatheringLootService.dropWithStudy((LivingEntity) (Object) this, source, causedByPlayer,
                //? if >=26.1.2
                /*() -> original.call(level, source, causedByPlayer));*/
                //? if <26.1.2
                () -> original.call(source, causedByPlayer));
    }
}
