package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.client.armor.ArmorRenderTypes;
import com.mistaboom.essence_ascendance.equipment.*;
import com.mistaboom.essence_ascendance.item.AscendanceGlaiveItem;
import com.mistaboom.essence_ascendance.network.WeaponActionStatePayload;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
//? if >=26.1.2
/*import net.minecraft.client.renderer.rendertype.RenderType;*/
//? if >=26.1.2
/*import net.minecraft.client.renderer.rendertype.RenderTypes;*/
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix4f;
import java.util.HashSet;
import static com.mistaboom.essence_ascendance.equipment.WeaponActionGeometry.*;

/** Essential depth-tested body, independent of optional transient effect budgets and entity visibility. */
public final class GlaiveRenderer {
    private static final String[] PARTS={"blade_base","blade_accent","butt_accent","nochange","wood_grip","wood_front","wood_repeat"};
    private static final BlockbenchStaticMesh[] MESH=new BlockbenchStaticMesh[PARTS.length];
    private static final RenderType[] BODY=new RenderType[4];
    private static final RenderType[] GLOW=new RenderType[4];
    private static final java.util.Map<java.util.UUID,Pose> HELD=new java.util.HashMap<>();
    private record FirstPersonFrame(Matrix4f transform,Matrix4f projection,Pose weapon,float blend) { }
    private static FirstPersonFrame firstPerson;
    public static final double REPEAT_LENGTH=2*DIAGONAL_PIXEL;
    static {
        for (int i=0;i<PARTS.length;i++) {
            int texture=i<4 ? i:3;
            var png=resource("textures/item/ascendance_glaive/"+PARTS[texture]+".png");
            MESH[i]=new BlockbenchStaticMesh(resource("meshes/item/ascendance_glaive/"+PARTS[i]+".eamesh"),png);
            if (i<4) {
                //? if >=26.1.2
                /*BODY[i]=RenderTypes.entityCutout(png);*/
                //? if <26.1.2
                BODY[i]=RenderType.entityCutoutNoCull(png);
                GLOW[i]=ArmorRenderTypes.emission(png);
            }
        }
    }
    private GlaiveRenderer() { }
    private static ResourceLocation resource(String path) { return ResourceLocation.fromNamespaceAndPath("essence_ascendance",path); }
    public static void invalidateResources() { for (var mesh:MESH) mesh.invalidate(); }
    public static void accept(WeaponActionStatePayload payload,long now) {
        var before=WeaponActionStatePayload.presentation(payload.owner());
        if (!WeaponActionStatePayload.accept(payload,now)) return;
        var state=payload.state();
        if (!state.physicalContact() || !WeaponActionPresentation.extended(state.phase())
                || before!=null && before.state().actionId()==state.actionId() && before.state().physicalContact()
                && before.state().contactedEntity()==state.contactedEntity()) return;
        // Optional acknowledgment uses the existing budgeted recipe. Essential meshes never enter that queue.
        com.mistaboom.essence_ascendance.client.transientfx.TransientWorldVisuals.emit(
                new com.mistaboom.essence_ascendance.visual.transientfx.WorldVisualEvent(
                        com.mistaboom.essence_ascendance.visual.transientfx.TransientVisualIds.WORLD_IMPACT_PULSE,
                        payload.dimension(),state.current().blade().center(),-1,-1,null,state.current().forward(),
                        .25F,.5F,com.mistaboom.essence_ascendance.visual.transientfx.VisualIntensity.MICRO,
                        com.mistaboom.essence_ascendance.visual.transientfx.SemanticVisualColor.valueOf(state.tier().name()),
                        6,state.actionId()^state.sampledAt(),0,0));
    }
    public static boolean held(Player player) { return player.getMainHandItem().getItem() instanceof AscendanceGlaiveItem; }
    public static void beginFrame() { HELD.clear();firstPerson=null; }
    public static void captureHand(Player player,PoseStack modelHand) {
        if (!com.mistaboom.essence_ascendance.client.procedural.ProceduralWorldQueue.inWorldFrame()
                && !GlaiveFirstPersonBody.rendering()) return;
        var mc=Minecraft.getInstance();
        var frame=GlaivePlayerAnimation.frame(player,
                //? if >=26.1.2
                /*mc.getDeltaTracker().getGameTimeDeltaPartialTick(true));*/
                //? if <26.1.2
                mc.getTimer().getGameTimeDeltaPartialTick(true));
        float blend=frame==null ? 0:GlaiveFirstPersonBody.rendering() ? 1:frame.blend();
        HELD.put(player.getUUID(),heldFromModel(modelHand,player.getMainArm(),ClientCameraCompat.position(mc.gameRenderer.getMainCamera()),
                frame==null ? null:frame.pose(),blend));
    }
    /** The complete weapon frame comes from the arm bone, never a separately re-aimed wrist. */
    public static Pose heldFromModel(PoseStack modelHand,HumanoidArm main,net.minecraft.world.phys.Vec3 camera,Pose attack) {
        return heldFromModel(modelHand,main,camera,attack,attack==null ? 0:1);
    }
    public static Pose heldFromModel(PoseStack modelHand,HumanoidArm main,net.minecraft.world.phys.Vec3 camera,Pose attack,float blend) {
        var hand=handFromModel(modelHand,main,camera);
        var matrix=modelHand.last().pose();
        var f=matrix.transformDirection(GlaivePlayerAnimation.shaft(GlaivePlayerAnimation.ATTACK_TILT*blend)).normalize();
        var r=matrix.transformDirection(new org.joml.Vector3f(1,0,0)).normalize();
        var forward=new net.minecraft.world.phys.Vec3(f.x,f.y,f.z);
        var right=new net.minecraft.world.phys.Vec3(r.x,r.y,r.z);
        double yaw=Math.toDegrees(Math.atan2(-f.x,f.z)),pitch=Math.toDegrees(Math.asin(Math.clamp(-f.y,-1,1)));
        double distance=attack==null ? REST_TIP:REST_TIP+(Math.max(0,attack.tip().subtract(hand).dot(forward))-REST_TIP)*blend;
        var basis=new Pose(hand,yaw,pitch,distance);
        double roll=Math.toDegrees(Math.atan2(right.dot(basis.up()),right.dot(basis.right())));
        return new Pose(hand,yaw,pitch,distance,0,roll);
    }
    public static net.minecraft.world.phys.Vec3 handFromModel(PoseStack modelHand,HumanoidArm main,net.minecraft.world.phys.Vec3 camera) {
        var point=modelHand.last().pose().transformPosition(new org.joml.Vector3f(-GlaiveGrip.sign(main)/16F,.5F,0));
        return camera.add(point.x,point.y,point.z);
    }
    public static Pose rest(Player player,float partial) {
        return GlaiveGrip.rest(player,partial,view(player,partial));
    }
    public static Pose view(Player player,float partial) {
        return GlaiveGrip.frame(player.getEyePosition(partial),player.getViewYRot(partial),player.getViewXRot(partial));
    }
    public static void renderWorld(Camera camera,Matrix4f view,Matrix4f projection,float partial) {
        var minecraft=Minecraft.getInstance(); var level=minecraft.level;
        if (level==null) return;
        var buffers=minecraft.renderBuffers().bufferSource();
        //? if <26.1.2
        GlaiveFirstPersonBody.render(camera,view,partial,buffers);
        var rendered=new HashSet<java.util.UUID>();
        for (var entry:WeaponActionStatePayload.presentations(level.dimension().location(),level.getGameTime())) {
            var frame=entry.frame(level.getGameTime(),partial);
            if (!WeaponActionPresentation.extended(frame.phase()) || frame.blend()<=0) continue;
            var payload=entry.payload();
            var entity=level.getEntity(payload.entityId());
            if (entity!=null && (!(entity instanceof Player player) || !player.getUUID().equals(payload.owner())
                    || !player.isAlive() || !held(player) ||
                    //? if >=26.1.2
                    /*player.getInventory().getSelectedSlot()!=entry.state().selectedSlot())) continue;*/
                    //? if <26.1.2
                    player.getInventory().selected!=entry.state().selectedSlot())) continue;
            var weapon=frame.pose();
            if (entity instanceof Player player) weapon=HELD.getOrDefault(player.getUUID(),weapon);
            if (entity==ClientCameraCompat.entity(camera) && minecraft.options.getCameraType().isFirstPerson()) {
                firstPerson=new FirstPersonFrame(new Matrix4f(com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix())
                        .mul(bodyTransform(weapon,ClientCameraCompat.position(camera),view)),new Matrix4f(projection),weapon,frame.blend());
                // The short, non-damaging pivots join the native first-person rest placement.
                // Fully active attacks keep their existing world depth and exact solved blade path.
                if (frame.blend()<1) { rendered.add(payload.owner());continue; }
            }
            draw(weapon,payload.appearance(),camera,view,buffers,0);
            rendered.add(payload.owner());
        }
        for (var player:level.players()) {
            if (!player.isAlive() || player.isSpectator() || !held(player) || rendered.contains(player.getUUID())) continue;
            if (ClientCameraCompat.entity(camera)==player && minecraft.options.getCameraType().isFirstPerson()) continue;
            var weapon=HELD.get(player.getUUID());
            if(weapon==null) continue; // A culled idle owner has no visible hand to attach an idle weapon to.
            draw(weapon,EquipmentAppearance.resolve(player,player.getMainHandItem()),camera,view,buffers,0);
        }
        buffers.endBatch();
    }
    /** Use the actual vanilla trident display transform inside the ordinary first-person hand pass. */
    public static void renderFirstPersonRest(Player player,PoseStack pose,MultiBufferSource buffers,int light) {
        renderFirstPerson(player,pose,buffers,light);
    }
    public static void renderFirstPerson(Player player,PoseStack pose,MultiBufferSource buffers,int light) {
        if (firstPerson!=null && firstPerson.blend()>=1) return;
        firstPersonRestTransform(player.getMainArm(),pose);
        double distance=REST_TIP;
        if (firstPerson!=null) {
            float blend=firstPerson.blend();
            //? if >=26.1.2
            /*var projection=new Matrix4f(Minecraft.getInstance().gameRenderer.getMainCamera().getViewRotationProjectionMatrix(new Matrix4f())).mul(new Matrix4f(Minecraft.getInstance().gameRenderer.getMainCamera().getViewRotationMatrix(new Matrix4f())).invert());*/
            //? if <26.1.2
            var projection=com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix();
            var transform=firstPersonTransform(pose.last().pose(),firstPerson.transform(),blend,
                    projection,firstPerson.projection(),com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix());
            pose=new PoseStack();pose.mulPose(transform);
            distance+=(firstPerson.weapon().tipDistance()-REST_TIP)*blend;
        }
        renderBody(new Pose(net.minecraft.world.phys.Vec3.ZERO,0,0,distance),
                EquipmentAppearance.resolve(player,player.getMainHandItem()),pose,buffers,light);
    }
    //? if >=26.1.2 {
    /*public static void submitFirstPerson(Player player, PoseStack pose, SubmitNodeCollector collector, int light) {
        if (firstPerson != null && firstPerson.blend() >= 1) return;
        firstPersonRestTransform(player.getMainArm(), pose);
        double distance = REST_TIP;
        if (firstPerson != null) {
            float blend = firstPerson.blend();
            var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
            var projection = new Matrix4f(camera.getViewRotationProjectionMatrix(new Matrix4f()))
                    .mul(new Matrix4f(camera.getViewRotationMatrix(new Matrix4f())).invert());
            var transform = firstPersonTransform(pose.last().pose(), firstPerson.transform(), blend,
                    projection, firstPerson.projection(), com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix());
            pose = new PoseStack();
            pose.mulPose(transform);
            distance += (firstPerson.weapon().tipDistance() - REST_TIP) * blend;
        }
        submitBody(new Pose(net.minecraft.world.phys.Vec3.ZERO, 0, 0, distance),
                EquipmentAppearance.resolve(player, player.getMainHandItem()), pose, collector, light, 0);
    }

    public static void submitBody(Pose weapon, EquipmentAppearance appearance, PoseStack pose,
                                  SubmitNodeCollector collector, int light, double cameraCutoff) {
        pose.pushPose();
        try {
            pose.translate(0, 0, -GRIP_OFFSET);
            double cutoff = cameraCutoff == 0 ? 0 : cameraCutoff + GRIP_OFFSET;
            double attachment = weapon.tipDistance() - BLADE_LENGTH + GRIP_OFFSET;
            double extension = weapon.tipDistance() - REST_TIP;
            if (cutoff == 0) submitPart(2, appearance, pose, collector, light, 0);
            if (extension < 0) {
                if (attachment + DIAGONAL_PIXEL > cutoff)
                    MESH[3].submitClipped(pose, collector, BODY[3], light, OverlayTexture.NO_OVERLAY,
                            0xFFFFFF, 255, 0, 1, attachment + DIAGONAL_PIXEL, cutoff);
            } else {
                if (cutoff == 0) MESH[4].submit(pose, collector, BODY[3], light,
                        OverlayTexture.NO_OVERLAY, 0xFFFFFF, 255);
                else MESH[4].submitClipped(pose, collector, BODY[3], light, OverlayTexture.NO_OVERLAY,
                        0xFFFFFF, 255, 0, 1, Double.POSITIVE_INFINITY, cutoff);
            }
            pose.pushPose();
            try {
                var shaftOrigin = new Matrix4f(pose.last().pose());
                pose.translate(0, 0, extension);
                if (extension >= 0) {
                    if (cutoff == 0) MESH[5].submit(pose, collector, BODY[3], light,
                            OverlayTexture.NO_OVERLAY, 0xFFFFFF, 255);
                    else MESH[5].submitClipped(pose, collector, BODY[3], light, OverlayTexture.NO_OVERLAY,
                            0xFFFFFF, 255, 0, 1, Double.POSITIVE_INFINITY, cutoff - extension);
                    int repeats = (int)Math.ceil(Math.min(64, extension) / REPEAT_LENGTH);
                    pose.pushPose();
                    try {
                        for (int i = 1; i <= repeats; i++) {
                            double offset = extension - i * REPEAT_LENGTH;
                            pose.last().pose().set(shaftOrigin).translate(0, 0, (float)offset);
                            double cut = cutoff - offset;
                            if (cutoff == 0 || cut <= 13 * DIAGONAL_PIXEL)
                                MESH[6].submit(pose, collector, BODY[3], light,
                                        OverlayTexture.NO_OVERLAY, 0xFFFFFF, 255);
                            else MESH[6].submitClipped(pose, collector, BODY[3], light,
                                    OverlayTexture.NO_OVERLAY, 0xFFFFFF, 255, 0, 1,
                                    Double.POSITIVE_INFINITY, cut);
                        }
                    } finally { pose.popPose(); }
                }
                pose.translate(0, 0, REST_ATTACHMENT + GRIP_OFFSET);
                double bladeCutoff = cutoff == 0 ? 0 : Math.max(0, cutoff - attachment);
                submitPart(0, appearance, pose, collector, light, bladeCutoff);
                submitPart(1, appearance, pose, collector, light, bladeCutoff);
            } finally { pose.popPose(); }
        } finally { pose.popPose(); }
    }

    private static void submitPart(int part, EquipmentAppearance appearance, PoseStack pose,
                                   SubmitNodeCollector collector, int light, double minimumZ) {
        int material = part == 0 ? 0 : 1;
        if (minimumZ == 0) MESH[part].submit(pose, collector, BODY[part], light,
                OverlayTexture.NO_OVERLAY, appearance.tint(material), 255);
        else MESH[part].submitClipped(pose, collector, BODY[part], light,
                OverlayTexture.NO_OVERLAY, appearance.tint(material), 255,
                0, 1, Double.POSITIVE_INFINITY, minimumZ);
        if (material == 1 && appearance.emissionAlpha() > 0) {
            if (minimumZ == 0) MESH[part].submit(pose, collector, GLOW[part],
                    ClientLightCompat.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                    appearance.luminousRgb(), appearance.emissionAlpha());
            else MESH[part].submitClipped(pose, collector, GLOW[part],
                    ClientLightCompat.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                    appearance.luminousRgb(), appearance.emissionAlpha(),
                    0, 1, Double.POSITIVE_INFINITY, minimumZ);
        }
    }
    *///? }
    public static void firstPersonRestTransform(HumanoidArm main,PoseStack pose) {
        //? if >=26.1.2 {
        /*ClientTridentTransform2612.apply(main, pose);
        *///?} else {
        var mc=Minecraft.getInstance();
        var trident=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.TRIDENT);
        var model=mc.getItemRenderer().getModel(trident,mc.level,mc.player,0);
        boolean left=main==HumanoidArm.LEFT;
        var context=left ? net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                :net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
        model.getTransforms().getTransform(context).apply(left,pose);
        pose.translate(-.5,-.5,-.5);
        //?}
        pose.scale(1,-1,-1);
        // Native trident shaft grip, expressed in its model coordinates; reuse its display placement.
        pose.translate(0,11.0/16,0);
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90));
    }
    /** Rigid keyframes, with gradual FOV compensation so the hand/world render boundary is continuous. */
    public static Matrix4f firstPersonTransform(Matrix4f rest,Matrix4f attack,float blend,float projectionX,float projectionY) {
        return firstPersonTransform(rest,attack,blend,new Matrix4f().scaling(projectionX,projectionY,1));
    }
    /** Hand rendering has its own model-view stack; interpolate in view space, then return to that stack. */
    public static Matrix4f firstPersonTransform(Matrix4f rest,Matrix4f attack,float blend,Matrix4f handProjection,
                                               Matrix4f worldProjection,Matrix4f handView) {
        var correction=new Matrix4f(handProjection).invert().mul(worldProjection);
        var transform=firstPersonTransform(new Matrix4f(handView).mul(rest),attack,blend,correction);
        return new Matrix4f(handView).invert().mul(transform);
    }
    private static Matrix4f firstPersonTransform(Matrix4f rest,Matrix4f attack,float blend,Matrix4f correction) {
        var position=rest.getTranslation(new org.joml.Vector3f()).lerp(attack.getTranslation(new org.joml.Vector3f()),blend);
        var rotation=rest.getUnnormalizedRotation(new org.joml.Quaternionf()).normalize()
                .slerp(attack.getUnnormalizedRotation(new org.joml.Quaternionf()).normalize(),blend);
        var scale=rest.getScale(new org.joml.Vector3f()).lerp(attack.getScale(new org.joml.Vector3f()),blend);
        return new Matrix4f().lerp(correction,blend)
                .translate(position).rotate(rotation).scale(scale);
    }
    public static Matrix4f bodyTransform(Pose weapon,net.minecraft.world.phys.Vec3 camera,Matrix4f view) {
        var origin=weapon.pivot().subtract(camera);
        var r=weapon.right();var u=weapon.up();var f=weapon.forward();
        return new Matrix4f(view).translate((float)origin.x,(float)origin.y,(float)origin.z)
                .mul(new Matrix4f().m00((float)r.x).m01((float)r.y).m02((float)r.z)
                        .m10((float)u.x).m11((float)u.y).m12((float)u.z).m20((float)f.x).m21((float)f.y).m22((float)f.z));
    }
    private static void draw(Pose weapon,EquipmentAppearance appearance,Camera camera,Matrix4f view,
                             MultiBufferSource.BufferSource buffers,double cameraCutoff) {
        var pose=new PoseStack(); pose.mulPose(view);
        var origin=weapon.pivot().subtract(ClientCameraCompat.position(camera)); pose.translate(origin.x,origin.y,origin.z);
        var r=weapon.right(); var u=weapon.up(); var f=weapon.forward();
        pose.mulPose(new Matrix4f().m00((float)r.x).m01((float)r.y).m02((float)r.z)
                .m10((float)u.x).m11((float)u.y).m12((float)u.z).m20((float)f.x).m21((float)f.y).m22((float)f.z));
        var level=Minecraft.getInstance().level; var block=BlockPos.containing(weapon.attachment());
        int light=level.hasChunkAt(block) ? ClientLightCompat.packed(level,block):0x00A000A0;
        renderBody(weapon,appearance,pose,buffers,light,cameraCutoff);
    }
    /** Public native-fixture entry: all distances are already solved world-space attachment distances. */
    public static void renderBody(Pose weapon,EquipmentAppearance appearance,PoseStack pose,
                                  MultiBufferSource buffers,int light) {
        renderBody(weapon,appearance,pose,buffers,light,0);
    }
    public static void renderBody(Pose weapon,EquipmentAppearance appearance,PoseStack pose,
                                  MultiBufferSource buffers,int light,double cameraCutoff) {
        pose.pushPose();
        try {
            pose.translate(0,0,-GRIP_OFFSET);
            renderFromButt(weapon,appearance,pose,buffers,light,cameraCutoff==0 ? 0:cameraCutoff+GRIP_OFFSET);
        } finally { pose.popPose(); }
    }
    private static void renderFromButt(Pose weapon,EquipmentAppearance appearance,PoseStack pose,
                                      MultiBufferSource buffers,int light,double cameraCutoff) {
        double attachment=weapon.tipDistance()-BLADE_LENGTH+GRIP_OFFSET;
        double extension=weapon.tipDistance()-REST_TIP;
        if (cameraCutoff==0) part(2,appearance,pose,buffers,light,0);
        var wood=buffers.getBuffer(BODY[3]);
        if (extension<0) {
            // Preserve the authored one-texel socket overlap when terrain shortens the weapon below rest length.
            if (attachment+DIAGONAL_PIXEL>cameraCutoff) MESH[3].renderClipped(pose,wood,light,OverlayTexture.NO_OVERLAY,0xFFFFFF,
                    255,0,1,attachment+DIAGONAL_PIXEL,cameraCutoff);
        } else {
            if (cameraCutoff==0) MESH[4].render(pose,wood,light,OverlayTexture.NO_OVERLAY,0xFFFFFF,255);
            else MESH[4].renderClipped(pose,wood,light,OverlayTexture.NO_OVERLAY,0xFFFFFF,255,
                    0,1,Double.POSITIVE_INFINITY,cameraCutoff);
        }
        pose.pushPose();
        try {
            // One continuously interpolated transform owns the head AND every upper shaft pixel.
            var shaftOrigin=new Matrix4f(pose.last().pose());
            pose.translate(0,0,extension);
            if (extension>=0) {
                if (cameraCutoff==0) MESH[5].render(pose,wood,light,OverlayTexture.NO_OVERLAY,0xFFFFFF,255);
                else MESH[5].renderClipped(pose,wood,light,OverlayTexture.NO_OVERLAY,0xFFFFFF,255,0,1,
                        Double.POSITIVE_INFINITY,cameraCutoff-extension);
                int repeats=(int)Math.ceil(Math.min(64,extension)/REPEAT_LENGTH);
                pose.pushPose();
                try {
                    for (int i=1;i<=repeats;i++) {
                        double offset=extension-i*REPEAT_LENGTH;
                        // Compose the shared extension and row offset in double precision before the
                        // matrix conversion, avoiding cancellation near the camera at long ranges.
                        pose.last().pose().set(shaftOrigin).translate(0,0,(float)offset);
                        double cut=cameraCutoff-offset;
                        // Complete rows feed backwards into the fixed grip. The last row overlaps by less
                        // than one repeat inside the palm, so changing the row count never opens a gap.
                        if (cameraCutoff==0 || cut<=13*DIAGONAL_PIXEL)
                            MESH[6].render(pose,wood,light,OverlayTexture.NO_OVERLAY,0xFFFFFF,255);
                        else MESH[6].renderClipped(pose,wood,light,OverlayTexture.NO_OVERLAY,0xFFFFFF,255,
                                0,1,Double.POSITIVE_INFINITY,cut);
                    }
                } finally { pose.popPose(); }
            }
            pose.translate(0,0,REST_ATTACHMENT+GRIP_OFFSET);
            double bladeCutoff=cameraCutoff==0 ? 0:Math.max(0,cameraCutoff-attachment);
            part(0,appearance,pose,buffers,light,bladeCutoff); part(1,appearance,pose,buffers,light,bladeCutoff);
        } finally { pose.popPose(); }
    }
    private static void part(int part,EquipmentAppearance appearance,PoseStack pose,MultiBufferSource buffers,int light,double minimumZ) {
        int material=part==0 ? 0:1;
        var body=buffers.getBuffer(BODY[part]);
        if (minimumZ==0) MESH[part].render(pose,body,light,OverlayTexture.NO_OVERLAY,appearance.tint(material),255);
        else MESH[part].renderClipped(pose,body,light,OverlayTexture.NO_OVERLAY,appearance.tint(material),255,
                0,1,Double.POSITIVE_INFINITY,minimumZ);
        if (material==1 && appearance.emissionAlpha()>0) {
            if (buffers instanceof MultiBufferSource.BufferSource source) source.endBatch(BODY[part]);
            var glow=buffers.getBuffer(GLOW[part]);
            if (minimumZ==0) MESH[part].render(pose,glow,ClientLightCompat.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY,appearance.luminousRgb(),appearance.emissionAlpha());
            else MESH[part].renderClipped(pose,glow,ClientLightCompat.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,
                    appearance.luminousRgb(),appearance.emissionAlpha(),0,1,Double.POSITIVE_INFINITY,minimumZ);
        }
    }
}
