package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.utility.UtilitySanctuaryService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Rejects only ordinary natural monster-spawn candidates inside an effective Sanctuary. */
@Mixin(NaturalSpawner.class)
public abstract class NaturalSpawnerSanctuaryMixin {
    @Inject(method = "canSpawnMobAt", at = @At("HEAD"), cancellable = true)
    private static void essenceAscendance$sanctuarySpawn(ServerLevel level, StructureManager structures,
                                                          ChunkGenerator generator, MobCategory category,
                                                          MobSpawnSettings.SpawnerData spawnData, BlockPos pos,
                                                          CallbackInfoReturnable<Boolean> cir) {
        if (category == MobCategory.MONSTER && UtilitySanctuaryService.suppressesNaturalMonsterSpawn(level, pos)) {
            cir.setReturnValue(false);
        }
    }
}
