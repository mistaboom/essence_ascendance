package com.mistaboom.essence_ascendance.client.armor;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Unlit eyes shader with ordinary translucent blending and armor view-depth layering. */
public final class ArmorRenderTypes {
    private static final Map<Identifier, RenderType> EMISSION = new ConcurrentHashMap<>();
    private ArmorRenderTypes() {}

    public static RenderType emission(Identifier texture) {
        return EMISSION.computeIfAbsent(texture, ArmorRenderTypes::create);
    }

    private static RenderType create(Identifier texture) {
        String name = "essence_ascendance_armor_emission_" + texture.getPath().replace('/', '_');
        RenderPipeline pipeline = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("essence_ascendance", name))
                .withVertexShader("core/entity")
                .withFragmentShader("core/entity")
                .withShaderDefine("EMISSIVE")
                .withShaderDefine("NO_OVERLAY")
                .withShaderDefine("NO_CARDINAL_LIGHTING")
                .withSampler("Sampler0")
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withVertexFormat(DefaultVertexFormat.ENTITY, VertexFormat.Mode.QUADS)
                .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
                .withCull(false)
                .build();
        return RenderType.create(name, RenderSetup.builder(pipeline)
                .withTexture("Sampler0", texture)
                .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                .bufferSize(RenderType.TRANSIENT_BUFFER_SIZE)
                .sortOnUpload()
                .createRenderSetup());
    }
}
