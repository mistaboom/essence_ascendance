package com.mistaboom.essence_ascendance.neoforge.client;

import com.mistaboom.essence_ascendance.client.ore.LatentOreModels;
import com.mistaboom.essence_ascendance.infuser.EssenceInfuserContent;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.DelegateBlockStateModel;
import net.neoforged.neoforge.client.model.DynamicBlockStateModel;
import net.neoforged.neoforge.model.data.ModelData;
import net.neoforged.neoforge.model.data.ModelProperty;

import java.util.List;

/** The saved host state travels in the immutable section model-data snapshot. */
public final class LatentOreNeoForgeModels {
    private static final ModelProperty<BlockState> HOST = new ModelProperty<>();
    private LatentOreNeoForgeModels() {}

    public static ModelData hostData(BlockState host) {
        return host == null ? ModelData.EMPTY : ModelData.of(HOST, host);
    }

    public static void modifyBakingResult(ModelEvent.ModifyBakingResult event) {
        event.getBakingResult().blockStateModels().replaceAll((state, model) ->
                state.is(EssenceInfuserContent.LATENT_ORE.get()) ? new HostModel(model) : model);
    }

    private static final class HostModel extends DelegateBlockStateModel implements DynamicBlockStateModel {
        private HostModel(BlockStateModel original) { super(original); }

        private BlockStateModel selected(BlockAndTintGetter world, BlockPos pos) {
            ModelData data = world.getModelData(pos);
            BlockState host = data.get(HOST);
            return host == null ? delegate : LatentOreModels.forHost(host, delegate);
        }

        @Override public Object createGeometryKey(BlockAndTintGetter world, BlockPos pos,
                                                  BlockState state, RandomSource random) {
            return selected(world, pos);
        }

        @Override public void collectParts(BlockAndTintGetter world, BlockPos pos, BlockState state,
                                           RandomSource random, List<BlockStateModelPart> parts) {
            selected(world, pos).collectParts(random, parts);
        }

        @Override public Material.Baked particleMaterial(BlockAndTintGetter world, BlockPos pos, BlockState state) {
            return selected(world, pos).particleMaterial();
        }

        @Override public int materialFlags(BlockAndTintGetter world, BlockPos pos, BlockState state) {
            return selected(world, pos).materialFlags();
        }
    }
}
