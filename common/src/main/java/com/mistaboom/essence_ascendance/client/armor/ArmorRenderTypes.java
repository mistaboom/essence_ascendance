package com.mistaboom.essence_ascendance.client.armor;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** Focus emission semantics with the same view-depth layering as vanilla's normal armor pass. */
public abstract class ArmorRenderTypes extends RenderType {
    private ArmorRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int size,
                             boolean crumbling, boolean sort, Runnable setup, Runnable clear) {
        super(name, format, mode, size, crumbling, sort, setup, clear);
    }

    /** Construct once per slot, not per frame. Textures are still owned/reloaded by Minecraft. */
    public static RenderType emission(ResourceLocation texture) {
        return create("essence_ascendance_armor_emission", DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS, TRANSIENT_BUFFER_SIZE, true, true,
                CompositeState.builder()
                        .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                        .setTextureState(new TextureStateShard(texture, false, false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_WRITE)
                        .setOverlayState(OVERLAY)
                        .setDepthTestState(LEQUAL_DEPTH_TEST)
                        .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                        .createCompositeState(true));
    }
}
