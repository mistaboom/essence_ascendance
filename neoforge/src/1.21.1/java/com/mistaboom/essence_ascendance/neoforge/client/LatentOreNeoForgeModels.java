package com.mistaboom.essence_ascendance.neoforge.client;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.client.ore.LatentOreModels;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

import java.util.List;

/** NeoForge's only adaptation: attach an immutable host state to chunk model data. */
public final class LatentOreNeoForgeModels {
    private static final ResourceLocation ORE = ResourceLocation.fromNamespaceAndPath(
            EssenceAscendance.MOD_ID, "adaptive_latent_ore");
    private static final ModelProperty<BlockState> HOST = new ModelProperty<>();

    private LatentOreNeoForgeModels() { }

    /** Called by the block entity on NeoForge's main-thread model-data snapshot boundary. */
    public static ModelData hostData(BlockState host) {
        return host == null ? ModelData.EMPTY : ModelData.of(HOST, host);
    }

    public static void modifyBakingResult(ModelEvent.ModifyBakingResult event) {
        event.getModels().replaceAll((id, model) -> ORE.equals(id.id())
                && !"inventory".equals(id.getVariant()) ? new HostModel(model) : model);
    }

    private static final class HostModel extends BakedModelWrapper<BakedModel> {
        private HostModel(BakedModel original) {
            super(original);
        }

        @Override
        public ModelData getModelData(BlockAndTintGetter world, BlockPos pos, BlockState state, ModelData data) {
            // SectionCompiler supplies the RenderRegionCache's immutable main-thread snapshot.
            // Never fetch a live block entity from a chunk-meshing worker.
            return data;
        }

        @Override
        public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random,
                                        ModelData data, RenderType renderType) {
            return resolved(data).getQuads(state, side, random);
        }

        @Override
        public TextureAtlasSprite getParticleIcon(ModelData data) {
            return resolved(data).getParticleIcon();
        }

        private BakedModel resolved(ModelData data) {
            BlockState host = data.get(HOST);
            return host == null ? originalModel : LatentOreModels.forHost(host, originalModel);
        }
    }
}
