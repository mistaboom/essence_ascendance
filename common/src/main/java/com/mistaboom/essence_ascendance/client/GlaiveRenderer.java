package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.client.armor.ArmorRenderTypes;
import com.mistaboom.essence_ascendance.equipment.*;
import com.mistaboom.essence_ascendance.item.AscendanceGlaiveItem;
import com.mistaboom.essence_ascendance.network.WeaponActionStatePayload;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
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
    public static final double REPEAT_LENGTH=2*DIAGONAL_PIXEL;
    static {
        for (int i=0;i<PARTS.length;i++) {
            int texture=i<4 ? i:3;
            var png=resource("textures/item/ascendance_glaive/"+PARTS[texture]+".png");
            MESH[i]=new BlockbenchStaticMesh(resource("meshes/item/ascendance_glaive/"+PARTS[i]+".eamesh"),png);
            if (i<4) { BODY[i]=RenderType.entityCutoutNoCull(png); GLOW[i]=ArmorRenderTypes.emission(png); }
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
    public static void beginFrame() { HELD.clear(); }
    public static void captureHand(Player player,PoseStack modelHand) {
        if (!com.mistaboom.essence_ascendance.client.procedural.ProceduralWorldQueue.inWorldFrame()) return;
        var mc=Minecraft.getInstance();
        var attack=GlaivePlayerAnimation.attack(player,mc.getTimer().getGameTimeDeltaPartialTick(true));
        HELD.put(player.getUUID(),heldFromModel(modelHand,player.getMainArm(),mc.gameRenderer.getMainCamera().getPosition(),attack));
    }
    /** The complete weapon frame comes from the arm bone, never a separately re-aimed wrist. */
    public static Pose heldFromModel(PoseStack modelHand,HumanoidArm main,net.minecraft.world.phys.Vec3 camera,Pose attack) {
        var hand=handFromModel(modelHand,main,camera);
        var matrix=modelHand.last().pose();
        var f=matrix.transformDirection(GlaivePlayerAnimation.shaft(attack==null ? 0:GlaivePlayerAnimation.ATTACK_TILT)).normalize();
        var r=matrix.transformDirection(new org.joml.Vector3f(1,0,0)).normalize();
        var forward=new net.minecraft.world.phys.Vec3(f.x,f.y,f.z);
        var right=new net.minecraft.world.phys.Vec3(r.x,r.y,r.z);
        double yaw=Math.toDegrees(Math.atan2(-f.x,f.z)),pitch=Math.toDegrees(Math.asin(Math.clamp(-f.y,-1,1)));
        double distance=attack==null ? REST_TIP:Math.max(0,attack.tip().subtract(hand).dot(forward));
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
    public static void renderWorld(Camera camera,Matrix4f view,float partial) {
        var minecraft=Minecraft.getInstance(); var level=minecraft.level;
        if (level==null) return;
        var buffers=minecraft.renderBuffers().bufferSource();
        GlaiveFirstPersonBody.render(camera,view,partial,buffers);
        var rendered=new HashSet<java.util.UUID>();
        for (var entry:WeaponActionStatePayload.presentations(level.dimension().location(),level.getGameTime())) {
            if (!WeaponActionPresentation.extended(entry.phase(level.getGameTime(),partial))) continue;
            var payload=entry.payload();
            var entity=level.getEntity(payload.entityId());
            if (entity!=null && (!(entity instanceof Player player) || !player.getUUID().equals(payload.owner())
                    || !player.isAlive() || !held(player) || player.getInventory().selected!=entry.state().selectedSlot())) continue;
            var weapon=entry.pose(level.getGameTime(),partial);
            if (entity instanceof Player player) weapon=HELD.getOrDefault(player.getUUID(),weapon);
            draw(weapon,payload.appearance(),camera,view,buffers,0);
            rendered.add(payload.owner());
        }
        for (var player:level.players()) {
            if (!player.isAlive() || player.isSpectator() || !held(player) || rendered.contains(player.getUUID())) continue;
            if (camera.getEntity()==player && minecraft.options.getCameraType().isFirstPerson()) continue;
            var weapon=HELD.get(player.getUUID());
            if(weapon==null) continue; // A culled idle owner has no visible hand to attach an idle weapon to.
            draw(weapon,EquipmentAppearance.resolve(player,player.getMainHandItem()),camera,view,buffers,0);
        }
        buffers.endBatch();
    }
    /** Use the actual vanilla trident display transform inside the ordinary first-person hand pass. */
    public static void renderFirstPersonRest(Player player,PoseStack pose,MultiBufferSource buffers,int light) {
        var mc=Minecraft.getInstance();
        var trident=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.TRIDENT);
        var model=mc.getItemRenderer().getModel(trident,player.level(),player,0);
        boolean left=player.getMainArm()==HumanoidArm.LEFT;
        var context=left ? net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                :net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
        model.getTransforms().getTransform(context).apply(left,pose);
        pose.translate(-.5,-.5,-.5);
        pose.scale(1,-1,-1);
        // Native trident shaft grip, expressed in its model coordinates; reuse its display placement.
        pose.translate(0,11.0/16,0);
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90));
        renderBody(new Pose(net.minecraft.world.phys.Vec3.ZERO,0,0,REST_TIP),
                EquipmentAppearance.resolve(player,player.getMainHandItem()),pose,buffers,light);
    }
    private static void draw(Pose weapon,EquipmentAppearance appearance,Camera camera,Matrix4f view,
                             MultiBufferSource.BufferSource buffers,double cameraCutoff) {
        var pose=new PoseStack(); pose.mulPose(view);
        var origin=weapon.pivot().subtract(camera.getPosition()); pose.translate(origin.x,origin.y,origin.z);
        var r=weapon.right(); var u=weapon.up(); var f=weapon.forward();
        pose.mulPose(new Matrix4f().m00((float)r.x).m01((float)r.y).m02((float)r.z)
                .m10((float)u.x).m11((float)u.y).m12((float)u.z).m20((float)f.x).m21((float)f.y).m22((float)f.z));
        var level=Minecraft.getInstance().level; var block=BlockPos.containing(weapon.attachment());
        int light=level.hasChunkAt(block) ? LevelRenderer.getLightColor(level,block):0x00A000A0;
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
        double extension=attachment-REST_ATTACHMENT-GRIP_OFFSET;
        if (cameraCutoff==0) part(2,appearance,pose,buffers,light,0);
        var wood=buffers.getBuffer(BODY[3]);
        if (extension<0) {
            // The source wood overlaps the blade socket by two projected texels. Preserve that joint when clipped.
            if (attachment+2*DIAGONAL_PIXEL>cameraCutoff) MESH[3].renderClipped(pose,wood,light,OverlayTexture.NO_OVERLAY,0xFFFFFF,
                    255,0,1,attachment+2*DIAGONAL_PIXEL,cameraCutoff);
        } else {
            if (cameraCutoff==0) MESH[4].render(pose,wood,light,OverlayTexture.NO_OVERLAY,0xFFFFFF,255);
            else MESH[4].renderClipped(pose,wood,light,OverlayTexture.NO_OVERLAY,0xFFFFFF,255,
                    0,1,Double.POSITIVE_INFINITY,cameraCutoff);
            int repeats=(int)Math.floor(Math.min(64,extension)/REPEAT_LENGTH);
            pose.pushPose();
            try {
                var instanceOrigin=new Matrix4f(pose.last().pose());
                for (int i=0;i<repeats;i++) {
                    pose.last().pose().set(instanceOrigin).translate(0,0,(float)(i*REPEAT_LENGTH));
                    double cut=cameraCutoff-i*REPEAT_LENGTH;
                    if (cameraCutoff==0 || cut<=17*DIAGONAL_PIXEL) MESH[6].render(pose,wood,light,OverlayTexture.NO_OVERLAY,0xFFFFFF,255);
                    else MESH[6].renderClipped(pose,wood,light,OverlayTexture.NO_OVERLAY,0xFFFFFF,255,0,1,Double.POSITIVE_INFINITY,cut);
                }
                pose.last().pose().set(instanceOrigin).translate(0,0,(float)(repeats*REPEAT_LENGTH));
                double remainder=extension-repeats*REPEAT_LENGTH;
                if (remainder>1e-8) MESH[6].renderClipped(pose,wood,light,OverlayTexture.NO_OVERLAY,0xFFFFFF,
                        255,-1,1,18*DIAGONAL_PIXEL+remainder,cameraCutoff==0 ? Double.NEGATIVE_INFINITY:cameraCutoff-repeats*REPEAT_LENGTH);
            } finally { pose.popPose(); }
            pose.pushPose();
            try {
                pose.translate(0,0,extension);
                if (cameraCutoff==0) MESH[5].render(pose,wood,light,OverlayTexture.NO_OVERLAY,0xFFFFFF,255);
                else MESH[5].renderClipped(pose,wood,light,OverlayTexture.NO_OVERLAY,0xFFFFFF,255,0,1,Double.POSITIVE_INFINITY,cameraCutoff-extension);
            }
            finally { pose.popPose(); }
        }
        pose.pushPose();
        try {
            pose.translate(0,0,attachment);
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
            if (minimumZ==0) MESH[part].render(pose,glow,LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY,appearance.luminousRgb(),appearance.emissionAlpha());
            else MESH[part].renderClipped(pose,glow,LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,
                    appearance.luminousRgb(),appearance.emissionAlpha(),0,1,Double.POSITIVE_INFINITY,minimumZ);
        }
    }
}
