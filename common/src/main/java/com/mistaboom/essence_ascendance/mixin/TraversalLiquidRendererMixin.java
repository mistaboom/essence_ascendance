package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mistaboom.essence_ascendance.client.SingleSidedFluidVertexConsumer;
import com.mistaboom.essence_ascendance.client.TraversalFluidRenderState;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.block.LiquidBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;

/** Fabric and NeoForge share this public tessellation boundary. Their private vertex helpers
 * differ (NeoForge adds fluid alpha), so forward the native VertexConsumer stream instead. */
@Mixin(LiquidBlockRenderer.class)
abstract class TraversalLiquidRendererMixin {
    @WrapMethod(method = "tesselate")
    private void essenceAscendance$singleSidedFluid(BlockAndTintGetter level, BlockPos pos,
            VertexConsumer vertices, BlockState block, FluidState fluid, Operation<Void> original) {
        if (!TraversalFluidRenderState.singleSided(fluid)) {
            original.call(level, pos, vertices, block, fluid);
            return;
        }
        SingleSidedFluidVertexConsumer filtered = new SingleSidedFluidVertexConsumer(vertices);
        original.call(level, pos, filtered, block, fluid);
        filtered.finish();
    }
}
