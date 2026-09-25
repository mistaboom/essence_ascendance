package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.client.ore.LatentOreModels;
import com.mistaboom.essence_ascendance.client.ore.LatentOreParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Select one already-baked sprite at particle creation; particle rendering remains vanilla. */
@Mixin(TerrainParticle.class)
abstract class LatentOreTerrainParticleMixin extends TextureSheetParticle {
    protected LatentOreTerrainParticleMixin(ClientLevel level, double x, double y, double z) {
        super(level, x, y, z);
    }

    @Inject(method = "<init>(Lnet/minecraft/client/multiplayer/ClientLevel;DDDDDDLnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)V", at = @At("RETURN"))
    private void essence$useSavedHostSprite(ClientLevel level, double x, double y, double z,
                                           double dx, double dy, double dz, BlockState state, BlockPos pos,
                                           CallbackInfo ci) {
        if (!LatentOreModels.isOre(state)) return;
        LatentOreParticles.host(level, pos).ifPresent(host -> setSprite(LatentOreModels.forHost(host,
                Minecraft.getInstance().getBlockRenderer().getBlockModel(state)).getParticleIcon()));
    }
}
