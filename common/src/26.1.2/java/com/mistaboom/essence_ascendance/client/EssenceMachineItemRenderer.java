package com.mistaboom.essence_ascendance.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.Locale;
import java.util.function.Consumer;

/** 26 special item model for the authored machine meshes and animated Focus. */
public final class EssenceMachineItemRenderer implements SpecialModelRenderer<ItemStack> {
    public enum Kind { CRUCIBLE, PYLON, INFUSER, NEXUS, FOCUS }
    private final Kind kind;

    public EssenceMachineItemRenderer(Kind kind) { this.kind = kind; }

    private BlockbenchStaticMesh mesh() {
        return switch (kind) {
            case CRUCIBLE -> EssenceMachineMeshes.CRUCIBLE;
            case PYLON -> EssenceMachineMeshes.PYLON;
            case INFUSER -> EssenceMachineMeshes.INFUSER;
            case NEXUS -> EssenceMachineMeshes.NEXUS;
            case FOCUS -> null;
        };
    }

    @Override public ItemStack extractArgument(ItemStack stack) { return stack.copy(); }

    @Override public void submit(ItemStack stack, PoseStack pose, SubmitNodeCollector collector,
            int light, int overlay, boolean foil, int outlineColor) {
        if (kind == Kind.FOCUS) {
            FocusVisuals.submitItem(stack, pose, collector, light, overlay, foil);
            return;
        }
        BlockbenchStaticMesh mesh = mesh();
        mesh.submit(pose, collector, light, overlay);
        if (foil) mesh.submit(pose, collector, RenderTypes.entityGlint(),
                0xF000F0, overlay, 0xFFFFFF, 255);
    }

    @Override public void getExtents(Consumer<Vector3fc> consumer) {
        BlockbenchStaticMesh mesh = mesh();
        if (mesh != null) mesh.visitExtents(consumer);
        else FocusVisuals.visitItemExtents(consumer);
    }

    public record Unbaked(String kind) implements SpecialModelRenderer.Unbaked<ItemStack> {
        public static final MapCodec<Unbaked> MAP_CODEC = Codec.STRING.fieldOf("kind")
                .xmap(Unbaked::new, Unbaked::kind);

        @Override public MapCodec<Unbaked> type() { return MAP_CODEC; }

        @Override public SpecialModelRenderer<ItemStack> bake(SpecialModelRenderer.BakingContext context) {
            return new EssenceMachineItemRenderer(Kind.valueOf(kind.toUpperCase(Locale.ROOT)));
        }
    }
}
