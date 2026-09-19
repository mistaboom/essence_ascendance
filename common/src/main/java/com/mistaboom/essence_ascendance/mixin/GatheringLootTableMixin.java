package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mistaboom.essence_ascendance.gathering.GatheringLootService;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(LivingEntity.class)
public abstract class GatheringLootTableMixin {
    @WrapMethod(method = "dropFromLootTable")
    private void essenceAscendance$gatheringStudyLoot(DamageSource source, boolean causedByPlayer,
                                                      Operation<Void> original) {
        GatheringLootService.dropWithStudy((LivingEntity) (Object) this, source, causedByPlayer,
                () -> original.call(source, causedByPlayer));
    }
}
