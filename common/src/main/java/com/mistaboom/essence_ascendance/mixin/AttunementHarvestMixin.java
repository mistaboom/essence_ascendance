package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mistaboom.essence_ascendance.attunement.AttunementGameplay;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ServerPlayerGameMode.class)
public abstract class AttunementHarvestMixin {
    @Shadow @Final protected ServerPlayer player;
    @Shadow protected ServerLevel level;
    @WrapMethod(method = "destroyBlock")
    private boolean essenceAscendance$committedHarvest(BlockPos pos, Operation<Boolean> original) {
        AttunementGameplay.beginHarvest(player, pos, level.getBlockState(pos), level.getBlockEntity(pos) != null);
        boolean completed = false;
        try {
            completed = original.call(pos);
            return completed;
        } finally { AttunementGameplay.finishHarvest(completed); }
    }
}
