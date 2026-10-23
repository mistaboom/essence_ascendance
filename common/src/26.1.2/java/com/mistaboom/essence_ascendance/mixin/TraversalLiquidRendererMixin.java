package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mistaboom.essence_ascendance.client.SingleSidedFluidVertexConsumer;
import com.mistaboom.essence_ascendance.client.TraversalFluidRenderState;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import java.util.HashMap;
import java.util.Map;

/** Filter the native fluid layer's vertices without replacing its geometry or tint. */
@Mixin(FluidRenderer.class)
abstract class TraversalLiquidRendererMixin {
    @WrapMethod(method = "tesselate")
    private void essenceAscendance$singleSidedFluid(BlockAndTintGetter level, BlockPos pos,
            FluidRenderer.Output output, BlockState block, FluidState fluid, Operation<Void> original) {
        if (!TraversalFluidRenderState.singleSided(fluid)) {
            original.call(level, pos, output, block, fluid);
            return;
        }
        Map<ChunkSectionLayer, SingleSidedFluidVertexConsumer> consumers = new HashMap<>();
        FluidRenderer.Output filtered = new FluidRenderer.Output() {
            @Override
            public com.mojang.blaze3d.vertex.VertexConsumer getBuilder(ChunkSectionLayer layer) {
                return consumers.computeIfAbsent(layer,
                        key -> new SingleSidedFluidVertexConsumer(output.getBuilder(key)));
            }
        };
        original.call(level, pos, filtered, block, fluid);
        consumers.values().forEach(SingleSidedFluidVertexConsumer::finish);
    }
}
