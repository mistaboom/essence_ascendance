package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.client.ore.LatentOreModels;
import com.mistaboom.essence_ascendance.client.ore.LatentOreParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.TerrainParticle;
//? if <26.1.2
import net.minecraft.client.particle.TextureSheetParticle;
//? if >=26.1.2
/*import net.minecraft.client.particle.SingleQuadParticle;*/
//? if >=26.1.2
/*import net.minecraft.client.renderer.texture.TextureAtlasSprite;*/
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Select one already-baked sprite at particle creation; particle rendering remains vanilla. */
@Mixin(TerrainParticle.class)
//? if >=26.1.2
/*abstract class LatentOreTerrainParticleMixin extends SingleQuadParticle {*/
//? if <26.1.2
abstract class LatentOreTerrainParticleMixin extends TextureSheetParticle {
    protected LatentOreTerrainParticleMixin(ClientLevel level, double x, double y, double z) {
        //? if >=26.1.2
        /*super(level, x, y, z, (TextureAtlasSprite) null);*/
        //? if <26.1.2
        super(level, x, y, z);
    }

    @Inject(method = "<init>(Lnet/minecraft/client/multiplayer/ClientLevel;DDDDDDLnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)V", at = @At("RETURN"))
    private void essence$useSavedHostSprite(ClientLevel level, double x, double y, double z,
                                           double dx, double dy, double dz, BlockState state, BlockPos pos,
                                           CallbackInfo ci) {
        if (!LatentOreModels.isOre(state)) return;
        //? if >=26.1.2
        /*LatentOreParticles.host(level, pos).ifPresent(host -> setSprite(LatentOreModels.forHost(host, Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state)).particleMaterial().sprite()));*/
        //? if <26.1.2
        LatentOreParticles.host(level, pos).ifPresent(host -> setSprite(LatentOreModels.forHost(host,
                //? if <26.1.2
                Minecraft.getInstance().getBlockRenderer().getBlockModel(state)).getParticleIcon()));
    }
}
