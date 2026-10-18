package com.mistaboom.essence_ascendance.projectile;

import com.mistaboom.essence_ascendance.client.GlaiveRenderer;
import com.mistaboom.essence_ascendance.equipment.*;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.architectury.event.events.client.ClientTickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.*;
import org.joml.Matrix4f;
import net.minecraft.world.phys.Vec3;
import java.nio.file.*;
import static com.mistaboom.essence_ascendance.equipment.WeaponActionGeometry.*;

/** Opt-in real OpenGL captures after ordinary resource loading. No gameplay world or production entrypoint. */
public final class GlaiveNativeVisualFixture {
    private static boolean done;
    public static void init() {
        if (!Boolean.getBoolean("essence.glaive.visualTest")) throw new IllegalStateException("Explicit visual fixture required");
        ClientTickEvent.CLIENT_POST.register(client -> {
            if (done || !(client.screen instanceof TitleScreen) || client.getOverlay()!=null) return;
            done=true;
            try {
                NativeGlaivePresentationTest.run();
                captures(client);
                System.out.println("GlaiveNativeVisualFixture: native OpenGL body/material/repeat/depth captures PASS");
                if (!Boolean.getBoolean("essence.glaive.visualKeepOpen")) client.stop();
            } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
        });
    }
    private static void captures(Minecraft client) throws Exception {
        var output=Path.of(System.getProperty("essence.glaive.visualOutput")); Files.createDirectories(output);
        // Deterministic white environment lightmap: geometry still uses native normals and material shaders.
        var light=client.gameRenderer.lightTexture();
        for (var field:LightTexture.class.getDeclaredFields()) if (field.getType()==NativeImage.class) {
            field.setAccessible(true); var pixels=(NativeImage)field.get(light);
            for (int y=0;y<pixels.getHeight();y++) for (int x=0;x<pixels.getWidth();x++) pixels.setPixelRGBA(x,y,0xFFFFFFFF);
        }
        for (var field:LightTexture.class.getDeclaredFields()) if (field.getType()==net.minecraft.client.renderer.texture.DynamicTexture.class) {
            field.setAccessible(true); ((net.minecraft.client.renderer.texture.DynamicTexture)field.get(light)).upload();
        }
        var grey=new EquipmentAppearance(EquipmentTierVisuals.primaryRgb(EquipmentTier.LATENT),EquipmentTierVisuals.armorAccentRgb(EquipmentTier.LATENT),0);
        var tier=new EquipmentAppearance(EquipmentTierVisuals.primaryRgb(EquipmentTier.TRANSCENDENT),EquipmentTierVisuals.armorAccentRgb(EquipmentTier.TRANSCENDENT),180);
        var personal=new EquipmentAppearance(0x0d0967,0xb6f800,180);
        capture(output.resolve("compact-grey.png"),REST_TIP,grey,0,1.6,false);
        capture(output.resolve("compact-tier.png"),REST_TIP,tier,0,1.6,false);
        capture(output.resolve("compact-personalized.png"),REST_TIP,personal,0,1.6,false);
        capture(output.resolve("extended-partial.png"),4.013,personal,0,4.3,false);
        capture(output.resolve("repeat-closeup.png"),4.013,grey,.15,1.1,false);
        capture(output.resolve("range64.png"),64,personal,0,64.3,false);
        capture(output.resolve("shortened.png"),.8,tier,0,1.6,false);
        capture(output.resolve("depth-occlusion.png"),4.013,personal,0,4.3,true);
    }
    private static void capture(Path file,double tip,EquipmentAppearance appearance,double start,double width,boolean occluder) throws Exception {
        var mc=Minecraft.getInstance(); var target=new TextureTarget(2048,512,true,Minecraft.ON_OSX);
        var projection=new Matrix4f(RenderSystem.getProjectionMatrix());
        target.setClearColor(.08F,.10F,.14F,1); target.clear(Minecraft.ON_OSX); target.bindWrite(true);
        RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho((float)(start-.1),(float)(start+width-.1),
                (float)(-width/8),(float)(width/8),-10,10),VertexSorting.ORTHOGRAPHIC_Z);
        RenderSystem.getModelViewStack().pushMatrix().identity(); RenderSystem.applyModelViewMatrix();
        RenderSystem.enableDepthTest(); RenderSystem.depthMask(true); RenderSystem.colorMask(true,true,true,true);
        RenderSystem.setShaderFogStart(10000); RenderSystem.setShaderFogEnd(10001);
        Lighting.setupForFlatItems(); RenderSystem.setShaderColor(1,1,1,1);
        try {
            if (occluder) {
                RenderSystem.setShader(GameRenderer::getPositionColorShader);
                var b=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);
                b.addVertex(1,-1,1).setColor(100,110,120,255); b.addVertex(2,-1,1).setColor(100,110,120,255);
                b.addVertex(2,1,1).setColor(100,110,120,255); b.addVertex(1,1,1).setColor(100,110,120,255);
                BufferUploader.drawWithShader(b.buildOrThrow());
            }
            var pose=new PoseStack();
            pose.mulPose(new Matrix4f().m00(0).m01(1).m02(0).m10(0).m11(0).m12(1).m20(1).m21(0).m22(0));
            var buffers=mc.renderBuffers().bufferSource();
            GlaiveRenderer.renderBody(new Pose(Vec3.ZERO,0,0,tip),appearance,pose,buffers,LightTexture.FULL_BRIGHT);
            buffers.endBatch();
            try (var image=Screenshot.takeScreenshot(target)) { image.writeToFile(file); }
        } finally {
            RenderSystem.getModelViewStack().popMatrix(); RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projection,VertexSorting.DISTANCE_TO_ORIGIN);
            target.destroyBuffers(); mc.getMainRenderTarget().bindWrite(true);
        }
    }
}
