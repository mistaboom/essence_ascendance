package com.mistaboom.essence_ascendance.fabric.client;

import com.mistaboom.essence_ascendance.client.ore.LatentOreModels;
import com.mistaboom.essence_ascendance.infuser.EssenceInfuserContent;
import net.fabricmc.fabric.api.blockgetter.v2.FabricBlockGetter;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.model.FabricBlockStateModel;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.function.Predicate;

/** Select the saved host model from Fabric's immutable section render-data snapshot. */
public final class LatentOreFabricModels {
    private LatentOreFabricModels() { }

    public static void register() {
        ModelLoadingPlugin.register(context -> context.modifyBlockModelAfterBake().register(
                ModelModifier.WRAP_PHASE, (model, bake) ->
                        bake.state().is(EssenceInfuserContent.LATENT_ORE.get())
                                ? new HostModel(model) : model));
    }

    private static final class HostModel implements BlockStateModel, FabricBlockStateModel {
        private final BlockStateModel fallback;

        private HostModel(BlockStateModel fallback) { this.fallback = fallback; }

        private BlockStateModel selected(BlockAndTintGetter world, BlockPos pos) {
            Object data = ((FabricBlockGetter) world).getBlockEntityRenderData(pos);
            return data instanceof BlockState host ? LatentOreModels.forHost(host, fallback) : fallback;
        }

        @Override public void collectParts(RandomSource random, List<BlockStateModelPart> parts) {
            fallback.collectParts(random, parts);
        }

        @Override public Material.Baked particleMaterial() { return fallback.particleMaterial(); }
        @Override public int materialFlags() { return fallback.materialFlags(); }

        @Override public void emitQuads(QuadEmitter emitter, BlockAndTintGetter world, BlockPos pos,
                BlockState state, RandomSource random, Predicate<Direction> cullTest) {
            ((FabricBlockStateModel) selected(world, pos)).emitQuads(
                    emitter, world, pos, state, random, cullTest);
        }

        @Override public Object createGeometryKey(BlockAndTintGetter world, BlockPos pos,
                BlockState state, RandomSource random) {
            return selected(world, pos);
        }

        @Override public Material.Baked particleMaterial(BlockAndTintGetter world,
                BlockPos pos, BlockState state) {
            return ((FabricBlockStateModel) selected(world, pos)).particleMaterial(world, pos, state);
        }

        @Override public int materialFlags(BlockAndTintGetter world, BlockPos pos,
                BlockState state, RandomSource random) {
            return ((FabricBlockStateModel) selected(world, pos)).materialFlags(
                    world, pos, state, random);
        }
    }
}
