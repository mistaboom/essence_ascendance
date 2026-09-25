package com.mistaboom.essence_ascendance.fabric.client;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.client.ore.LatentOreModels;
import net.fabricmc.fabric.api.blockview.v2.FabricBlockView;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Supplier;

/** Fabric's only adaptation: pass the chunk's immutable host snapshot to the shared baked model. */
public final class LatentOreFabricModels {
    private static final ResourceLocation ORE = ResourceLocation.fromNamespaceAndPath(
            EssenceAscendance.MOD_ID, "adaptive_latent_ore");

    private LatentOreFabricModels() { }

    public static void register() {
        ModelLoadingPlugin.register(context -> context.modifyModelAfterBake().register(
                ModelModifier.WRAP_PHASE, (model, bake) -> {
                    var id = bake.topLevelId();
                    return model != null && id != null && ORE.equals(id.id())
                            && !"inventory".equals(id.getVariant())
                            ? new HostModel(model) : model;
                }));
    }

    private static final class HostModel extends ForwardingBakedModel {
        private HostModel(BakedModel original) {
            super(original);
        }

        @Override
        public boolean isVanillaAdapter() {
            return false;
        }

        @Override
        public void emitBlockQuads(BlockAndTintGetter world, BlockState state, BlockPos pos,
                                   Supplier<RandomSource> random, RenderContext context) {
            Object data = ((FabricBlockView) world).getBlockEntityRenderData(pos);
            BakedModel model = data instanceof BlockState host
                    ? LatentOreModels.forHost(host, wrapped) : wrapped;
            context.fallbackConsumer().accept(model);
        }
    }
}
