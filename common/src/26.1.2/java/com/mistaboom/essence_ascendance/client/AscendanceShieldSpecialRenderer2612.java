package com.mistaboom.essence_ascendance.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3fc;
import java.util.function.Consumer;

/** Native 26 special item model preserving the authored tier mesh, mask and foil. */
public final class AscendanceShieldSpecialRenderer2612 implements SpecialModelRenderer<ItemStack> {
    @Override public ItemStack extractArgument(ItemStack stack) { return stack.copy(); }
    @Override public void submit(ItemStack stack, PoseStack pose, SubmitNodeCollector collector,
            int light, int overlay, boolean foil, int outlineColor) {
        AscendanceShieldRenderer.submit(stack, pose, collector, light, overlay, foil);
    }
    @Override public void getExtents(Consumer<Vector3fc> consumer) {
        AscendanceShieldRenderer.visitExtents(consumer);
    }
    public record Unbaked() implements SpecialModelRenderer.Unbaked<ItemStack> {
        public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(new Unbaked());
        @Override public MapCodec<Unbaked> type() { return MAP_CODEC; }
        @Override public SpecialModelRenderer<ItemStack> bake(SpecialModelRenderer.BakingContext context) {
            return new AscendanceShieldSpecialRenderer2612();
        }
    }
}
