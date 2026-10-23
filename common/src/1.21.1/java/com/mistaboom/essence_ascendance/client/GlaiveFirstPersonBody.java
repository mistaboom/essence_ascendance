package com.mistaboom.essence_ascendance.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.joml.Matrix4f;

/** Evaluate the native hand attachment without drawing a first-person body or detached arm geometry. */
public final class GlaiveFirstPersonBody {
    private static boolean rendering;
    private GlaiveFirstPersonBody() { }
    public static boolean rendering() { return rendering; }
    public static void visibility(PlayerModel<?> model) {
        model.head.visible=false;model.hat.visible=false;model.body.visible=false;model.jacket.visible=false;
        model.rightLeg.visible=false;model.leftLeg.visible=false;model.rightPants.visible=false;model.leftPants.visible=false;
        model.rightArm.visible=false;model.leftArm.visible=false;model.rightSleeve.visible=false;model.leftSleeve.visible=false;
    }
    public static void render(Camera camera,Matrix4f view,float partial,MultiBufferSource.BufferSource buffers) {
        var mc=Minecraft.getInstance();var player=mc.player;
        if(player==null || ClientCameraCompat.entity(camera)!=player || !mc.options.getCameraType().isFirstPerson()
                || player.isInvisible() || player.isSleeping() || !GlaivePlayerAnimation.active(player)
                || GlaivePlayerAnimation.attack(player,partial)==null) return;
        if(!(mc.getEntityRenderDispatcher().getRenderer(player) instanceof PlayerRenderer renderer)) return;
        var model=renderer.getModel();
        var parts=new ModelPart[]{model.head,model.hat,model.body,model.jacket,model.rightArm,model.leftArm,
                model.rightSleeve,model.leftSleeve,model.rightLeg,model.leftLeg,model.rightPants,model.leftPants};
        var visible=new boolean[parts.length];for(int i=0;i<parts.length;i++) visible[i]=parts[i].visible;
        var pose=new PoseStack();
        var offset=player.getPosition(partial).subtract(ClientCameraCompat.position(camera)).add(renderer.getRenderOffset(player,partial));
        pose.translate(offset.x,offset.y,offset.z);
        var modelView=RenderSystem.getModelViewStack();modelView.pushMatrix();modelView.mul(view);RenderSystem.applyModelViewMatrix();
        rendering=true;
        try {
            // Native entity transforms omit camera rotation in their PoseStack; preserve that contract for palm capture.
            renderer.render(player,player.getViewYRot(partial),partial,pose,buffers,
                    renderer.getPackedLightCoords(player,partial));
            buffers.endBatch();
        } finally {
            rendering=false;
            for(int i=0;i<parts.length;i++) parts[i].visible=visible[i];
            modelView.popMatrix();RenderSystem.applyModelViewMatrix();
        }
    }
}
