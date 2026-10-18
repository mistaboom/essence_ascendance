package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.equipment.EquipmentProfileItem;
import com.mistaboom.essence_ascendance.equipment.EquipmentProfiles;
import com.mistaboom.essence_ascendance.equipment.EquipmentTier;
import com.mistaboom.essence_ascendance.equipment.EquipmentTierData;
import com.mistaboom.essence_ascendance.equipment.EquipmentTierVisuals;
import com.mistaboom.essence_ascendance.item.AscendanceArmorItem;
import com.mistaboom.essence_ascendance.item.AscendanceArchiveItem;
import com.mistaboom.essence_ascendance.item.AscendanceAxeItem;
import com.mistaboom.essence_ascendance.item.AscendanceCasterItem;
import com.mistaboom.essence_ascendance.item.AscendanceHoeItem;
import com.mistaboom.essence_ascendance.item.AscendanceSwordItem;
import com.mistaboom.essence_ascendance.item.AscendancePickaxeItem;
import com.mistaboom.essence_ascendance.item.AscendanceRangedWeaponItem;
import com.mistaboom.essence_ascendance.item.AscendanceShieldItem;
import com.mistaboom.essence_ascendance.item.AscendanceShovelItem;
import com.mistaboom.essence_ascendance.item.EmissiveAccentItem;
import com.mistaboom.essence_ascendance.visual.ArmorEmission;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Exercises the real generated-item accent pass without opening a rendering window. */
public final class InventoryItemEmissionTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        checkItemContract();
        checkMeshClipping();
        checkGlaiveGeometry();
        checkGlaiveHandAnchor();
        checkGlaiveArms();
        permitTestItemRegistration();
        Item emissive = Registry.register(BuiltInRegistries.ITEM,
                ResourceLocation.fromNamespaceAndPath("essence_ascendance", "emission_test_item"),
                new TestEquipmentItem());
        BakedModel model = new TestModel();

        int[] expectedAlpha = {0, 36, 72, 108, 144, 180};
        for (EquipmentTier tier : EquipmentTier.values()) {
            ItemStack stack = new ItemStack(emissive);
            if (tier != EquipmentTier.LATENT) {
                stack.set(DataComponents.CUSTOM_DATA, EquipmentTierData.tierData(tier));
            }
            PoseStack pose = new PoseStack();
            pose.translate(0.25F, -0.5F, 1.0F);
            RecordingBuffers buffers = new RecordingBuffers();
            InventoryItemEmissionRenderer.render(stack, model, pose, buffers);

            if (tier == EquipmentTier.LATENT) {
                check(buffers.recordings.isEmpty(), "Latent item does not request an emission pass");
                continue;
            }
            check(buffers.recordings.size() == 1, "Higher tier requests exactly one shared emission buffer");
            Map.Entry<RenderType, RecordingBuffer> pass = buffers.recordings.entrySet().iterator().next();
            check(texture(pass.getKey()).equals(TextureAtlas.LOCATION_BLOCKS),
                    "Generated-item emission samples the shared item/block atlas");
            check(pass.getValue().vertices.size() == 4,
                    "Only the tint-index-one accent quad emits; base, nochange and untinted quads do not");
            int rgb = ArmorEmission.luminousColor(EquipmentTierVisuals.armorAccentRgb(tier));
            for (float[] vertex : pass.getValue().vertices) {
                check(channel(vertex[3], rgb >> 16 & 255)
                                && channel(vertex[4], rgb >> 8 & 255)
                                && channel(vertex[5], rgb & 255)
                                && channel(vertex[6], expectedAlpha[tier.ordinal()]),
                        "Item emission uses the canonical luminous tint and progressive armor alpha");
                check((int) vertex[7] == LightTexture.FULL_BRIGHT,
                        "Item accent is independent of environmental light");
                check((int) vertex[8] == OverlayTexture.NO_OVERLAY,
                        "Item accent uses the armor/shield overlay contract");
            }
            float[] first = pass.getValue().vertices.getFirst();
            check(first[0] == 0.25F && first[1] == -0.5F && first[2] == 1.0F,
                    "Emission reuses the already-selected model's current display transform");
        }

        RecordingBuffers unrelated = new RecordingBuffers();
        InventoryItemEmissionRenderer.render(new ItemStack(Item.byBlock(net.minecraft.world.level.block.Blocks.STONE)),
                model, new PoseStack(), unrelated);
        check(unrelated.recordings.isEmpty(), "Unmarked vanilla items retain vanilla rendering only");
        checkDeferredDrawOrder(emissive, model);
        Item glaive = Registry.register(BuiltInRegistries.ITEM,
                ResourceLocation.fromNamespaceAndPath("essence_ascendance", "emission_test_glaive"),
                new com.mistaboom.essence_ascendance.item.AscendanceGlaiveItem(new Item.Properties().durability(100)));
        var glaiveStack = new ItemStack(glaive);
        glaiveStack.set(DataComponents.CUSTOM_DATA, EquipmentTierData.tierData(EquipmentTier.TRANSCENDENT));
        var glaiveBuffers = new RecordingBuffers();
        InventoryItemEmissionRenderer.render(glaiveStack, model, new PoseStack(), glaiveBuffers);
        check(glaiveBuffers.recordings.values().iterator().next().vertices.size() == 8,
                "Both blade and butt accent quads emit, while base and wood retain the normal pass");
        checkLoaderHooks();
        System.out.println("InventoryItemEmissionTest: " + checks
                + " accent filtering, six-tier brightness, transform, atlas and loader-hook checks PASS");
    }
    private static void checkGlaiveArms() {
        var camera=new net.minecraft.world.phys.Vec3(0,1.62,0);
        for(boolean slim:new boolean[]{false,true}) for(var main:net.minecraft.world.entity.HumanoidArm.values()) {
            var root=net.minecraft.client.model.geom.builders.LayerDefinition.create(
                    net.minecraft.client.model.PlayerModel.createMesh(net.minecraft.client.model.geom.builders.CubeDeformation.NONE,slim),64,64).bakeRoot();
            var model=new net.minecraft.client.model.PlayerModel<net.minecraft.world.entity.LivingEntity>(root,slim);
            var arm=main==net.minecraft.world.entity.HumanoidArm.RIGHT ? model.rightArm:model.leftArm;
            var off=main==net.minecraft.world.entity.HumanoidArm.RIGHT ? model.leftArm:model.rightArm;
            for(double pitch:new double[]{-90,-60,0,60,90}) for(double yaw:new double[]{0,123})
                for(double distance:new double[]{1,4,16,64}) for(int sweep=-60;sweep<=60;sweep+=15) {
                root.getAllParts().forEach(net.minecraft.client.model.geom.ModelPart::resetPose);
                off.setRotation(.17F,-.13F,.08F);
                var body=new PoseStack();body.translate(-camera.x,-camera.y,-camera.z);
                body.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180));
                body.scale(-.9375F,-.9375F,.9375F);body.translate(0,-1.501,0);
                var target=new com.mistaboom.essence_ascendance.equipment.WeaponActionGeometry.Pose(camera,yaw,pitch,distance,sweep);
                var localTarget=new org.joml.Matrix4f(body.last().pose()).invert().transformPosition(GlaivePlayerAnimation.vector(target.tip().subtract(camera)))
                        .sub(arm.x/16,arm.y/16,arm.z/16);
                var localRight=new org.joml.Matrix4f(body.last().pose()).invert().transformDirection(GlaivePlayerAnimation.vector(target.right())).normalize();
                var solution=GlaivePlayerAnimation.solve(localTarget,localRight,main,45);
                var localTip=GlaivePlayerAnimation.shaft(45).mul(solution.distance()).add(GlaivePlayerAnimation.palm(main));
                var actual=solution.rotation().transform(new org.joml.Vector3f(localTip));
                check(actual.distance(localTarget)<.00015,"Rigid solve quaternion maps local tip exactly: target="+localTarget+" actual="+actual+" q="+solution.rotation());
                // Native float Euler reconstruction loses precision near half-turns: allow at most one mesh texel at 64 blocks.
                var euler=GlaivePlayerAnimation.armAngles(solution.rotation());
                var fromEuler=new org.joml.Quaternionf().rotationZYX(euler.z,euler.y,euler.x).transform(new org.joml.Vector3f(localTip));
                check(fromEuler.distance(localTarget)<.0005*Math.max(1,distance),"Native Euler conversion retains solution: "+fromEuler+" target="+localTarget+" angles="+euler);
                GlaivePlayerAnimation.poseArm(model,main,body,camera,target);
                check(model.rightArm.x==-5 && model.leftArm.x==5 && model.rightArm.y==(slim ? 2.5F:2F)
                                && model.leftArm.y==(slim ? 2.5F:2F),"Aiming keeps both native shoulder anchors");
                check(off.xRot==.17F && off.yRot==-.13F && off.zRot==.08F && model.body.yRot==0,
                        "Only the main shoulder rotates; off arm and torso remain native");
                body.pushPose();arm.translateAndRotate(body);
                var held=GlaiveRenderer.heldFromModel(body,main,camera,target);
                var palm=GlaiveRenderer.handFromModel(body,main,camera);
                check(held.pivot().distanceTo(palm)<1e-7,"Weapon is rigidly attached to the native palm");
                check(held.tip().distanceTo(target.tip())<.0005*Math.max(1,distance),"Shoulder solve retains the solved attack tip: "+held.tip().distanceTo(target.tip())+" main="+main+" pitch="+pitch+" yaw="+yaw+" distance="+distance+" sweep="+sweep+" held="+held+" target="+target.tip()+" angles="+arm.xRot+","+arm.yRot+","+arm.zRot);
                var local=new org.joml.Matrix4f(body.last().pose()).invert()
                        .transformDirection(GlaivePlayerAnimation.vector(held.forward())).normalize();
                check(local.distance(GlaivePlayerAnimation.shaft(45))<.0001,
                        "Stab, aim and horizontal sweep never change weapon orientation relative to the hand");
                body.popPose();
            }
            for(float tilt:new float[]{0,45}) {
                var solution=GlaivePlayerAnimation.solve(new org.joml.Vector3f(0,.02F,0),new org.joml.Vector3f(1,0,0),main,tilt);
                check(!solution.reachable() && Float.isFinite(solution.rotation().x) && solution.distance()>=0,
                        "Impossible inside-shoulder targets remain finite without detaching the hand");
            }
            root.getAllParts().forEach(net.minecraft.client.model.geom.ModelPart::resetPose);
            arm.setRotation(-.314F,.08F,.025F);
            var pose=new PoseStack();arm.translateAndRotate(pose);
            var rest=GlaiveRenderer.heldFromModel(pose,main,net.minecraft.world.phys.Vec3.ZERO,null);
            var armAxis=pose.last().pose().transformDirection(new org.joml.Vector3f(0,1,0)).normalize();
            check(Math.abs(GlaivePlayerAnimation.vector(rest.forward()).dot(armAxis))<1e-6,
                    "Idle shaft is perpendicular to the arm like a native held trident");
            GlaivePlayerAnimation.poseArm(model,main,new PoseStack(),camera,null);
            check(arm.xRot==-.314F && arm.yRot==.08F && arm.zRot==.025F,"Idle leaves the native item arm pose unchanged");
            model.setAllVisible(true);GlaiveFirstPersonBody.visibility(model);
            check(!model.rightArm.visible && !model.leftArm.visible && !model.rightSleeve.visible && !model.leftSleeve.visible
                    && !model.head.visible && !model.body.visible,"First-person attachment evaluation draws no detached arms or body");
        }
    }
    private static void checkMeshClipping() throws Exception {
        var mesh=new BlockbenchStaticMesh(ResourceLocation.withDefaultNamespace("fixture"),TextureAtlas.LOCATION_BLOCKS);
        var data=BlockbenchStaticMesh.class.getDeclaredField("vertexData"); data.setAccessible(true);
        data.set(mesh,new float[]{-1,0,0,0,0,0,1,0, 1,0,0,1,0,0,1,0, 0,0,1,.5F,1,0,1,0});
        var buffer=new RecordingBuffer();
        mesh.renderClipped(new PoseStack(),buffer,LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,
                0xb6f800,180,0,1,.8,.35);
        check(!buffer.vertices.isEmpty(),"Intersecting camera/partial planes retain visible triangles");
        for (var vertex:buffer.vertices) {
            check(vertex[2]>=.35F-1e-6 && vertex[2]<=.8F+1e-6,"Two-plane clipping preserves exact axial limits");
            check(vertex[3]==182 && vertex[4]==248 && vertex[5]==0 && vertex[6]==180,
                    "Clipped emission preserves resolved RGB and alpha");
        }
        buffer.vertices.clear();
        mesh.renderClipped(new PoseStack(),buffer,LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,
                0xFFFFFF,255,0,1,Double.POSITIVE_INFINITY,2);
        check(buffer.vertices.isEmpty(),"Entire camera-side geometry is omitted without moving its world coordinates");
    }

    /** Draw the real generated parts through recording native buffers, without a rendering window. */
    private static void checkGlaiveGeometry() throws Exception {
        var meshesField=GlaiveRenderer.class.getDeclaredField("MESH"); meshesField.setAccessible(true);
        var bodyField=GlaiveRenderer.class.getDeclaredField("BODY"); bodyField.setAccessible(true);
        var glowField=GlaiveRenderer.class.getDeclaredField("GLOW"); glowField.setAccessible(true);
        var meshes=(BlockbenchStaticMesh[])meshesField.get(null);
        var body=(RenderType[])bodyField.get(null); var glow=(RenderType[])glowField.get(null);
        var resourceField=BlockbenchStaticMesh.class.getDeclaredField("meshResource"); resourceField.setAccessible(true);
        var dataField=BlockbenchStaticMesh.class.getDeclaredField("vertexData"); dataField.setAccessible(true);
        try {
            for (var mesh:meshes) {
                var resource=(ResourceLocation)resourceField.get(mesh);
                try (var in=new java.io.DataInputStream(InventoryItemEmissionTest.class.getResourceAsStream(
                        "/assets/"+resource.getNamespace()+"/"+resource.getPath()))) {
                    check(in.readInt()==0x45414D31,"Generated glaive uses existing EAM1 mesh data");
                    var data=new float[in.readInt()*24];
                    for (int i=0;i<data.length;i++) data[i]=in.readFloat();
                    dataField.set(mesh,data);
                }
            }
            var appearance=new com.mistaboom.essence_ascendance.equipment.EquipmentAppearance(0x0d0967,0xb6f800,180);
            for (double range:new double[]{.1,.5,com.mistaboom.essence_ascendance.equipment.WeaponActionGeometry.REST_TIP,4.013,64}) for (double cutoff:new double[]{0,.35}) {
                var buffers=new RecordingBuffers();
                var weapon=new com.mistaboom.essence_ascendance.equipment.WeaponActionGeometry.Pose(
                        net.minecraft.world.phys.Vec3.ZERO,0,0,range);
                GlaiveRenderer.renderBody(weapon,appearance,new PoseStack(),buffers,LightTexture.FULL_BRIGHT,cutoff);
                var vertices=buffers.recordings.values().stream().flatMap(b->b.vertices.stream()).toList();
                if (range<cutoff) check(vertices.isEmpty(),"Camera-side weapon is hidden without changing authority");
                else {
                    check(!vertices.isEmpty(),"Essential body survives the independent camera cutoff");
                    double maximum=vertices.stream().mapToDouble(v->v[2]).max().orElseThrow();
                    check(Math.abs(maximum-range)<1e-5,"Physical blade front remains at solved range: "+range);
                    if (cutoff>0) check(vertices.stream().allMatch(v->v[2]>=cutoff-1e-6),
                            "No near-eye cap/shaft/blade vertices enter first-person view");
                    if (cutoff==0 && range==.1) check(vertices.stream().anyMatch(v->v[2]<-.4),
                            "World observers retain the entire rigid blade at signed negative attachment");
                    if(cutoff==0 && range==com.mistaboom.essence_ascendance.equipment.WeaponActionGeometry.REST_TIP) {
                        var cap=buffers.recordings.get(body[2]).vertices;
                        check(cap.stream().allMatch(v->v[2]<-.2),"Butt cap sits behind the palm after choking up the grip");
                        check(vertices.stream().anyMatch(v->v[2]>0),"Blade and forward shaft remain ahead of the palm");
                    }
                }
                if (range>1) {
                    for (int accent:new int[]{1,2}) {
                        var normal=buffers.recordings.get(body[accent]);
                        if (cutoff>0 && accent==2) { check(normal==null,"Only self first-person cap is hidden"); continue; }
                        check(normal!=null && normal.vertices.stream().allMatch(v->v[3]==182 && v[4]==248 && v[5]==0),
                                "Both independent accent bodies use the identical personalized tint");
                        check(buffers.recordings.get(glow[accent]).vertices.stream().allMatch(v->v[6]==180),
                                "Both independent accent masks retain emission alpha");
                    }
                    check(buffers.recordings.get(body[3]).vertices.stream().allMatch(v->v[3]==255 && v[4]==255 && v[5]==255),
                            "Authored wood remains untinted in extended geometry");
                }
            }
            checkGlaiveSubmissionCost(body,appearance);
        } finally { GlaiveRenderer.invalidateResources(); }
    }

    /** Real renderer/mesh traversal into a counting consumer; excludes GPU/driver and buffer upload cost. */
    private static void checkGlaiveSubmissionCost(RenderType[] body,com.mistaboom.essence_ascendance.equipment.EquipmentAppearance appearance) {
        var bean=java.lang.management.ManagementFactory.getThreadMXBean();
        var meter=bean instanceof com.sun.management.ThreadMXBean m && m.isThreadAllocatedMemorySupported() ? m:null;
        if (meter!=null && !meter.isThreadAllocatedMemoryEnabled()) meter.setThreadAllocatedMemoryEnabled(true);
        long rigid=-1,previousWood=0;
        for (double range:new double[]{4.013,10,16,30,64}) {
            var weapon=new com.mistaboom.essence_ascendance.equipment.WeaponActionGeometry.Pose(net.minecraft.world.phys.Vec3.ZERO,0,0,range);
            var buffers=new CountingBuffers();
            for (int i=0;i<3;i++) GlaiveRenderer.renderBody(weapon,appearance,new PoseStack(),buffers,LightTexture.FULL_BRIGHT);
            buffers.clear();
            long before=meter==null ? -1:meter.getThreadAllocatedBytes(Thread.currentThread().threadId()),start=System.nanoTime();
            for (int i=0;i<5;i++) GlaiveRenderer.renderBody(weapon,appearance,new PoseStack(),buffers,LightTexture.FULL_BRIGHT);
            double millis=(System.nanoTime()-start)/5_000_000.0;
            long allocated=meter==null ? -1:(meter.getThreadAllocatedBytes(Thread.currentThread().threadId())-before)/5;
            long wood=buffers.count(body[3])/5,ends=(buffers.count(body[0])+buffers.count(body[1])+buffers.count(body[2]))/5;
            check(rigid<0 || rigid==ends,"Blade and butt geometry counts stay fixed as range grows");
            check(wood>previousWood,"Only shaft geometry grows with extension");
            rigid=ends;previousWood=wood;
            System.out.println("Glaive submission audit range="+range+": "+wood+" wood vertices, "+ends
                    +" rigid body vertices, "+millis+" ms/draw, "+allocated+" allocated bytes/draw (counting consumer; excludes GPU/upload)"
                    +(range==16 || range==64 ? " (unsupported-range stress only)":""));
        }
    }

    private static void checkGlaiveHandAnchor() {
        var eye=new net.minecraft.world.phys.Vec3(3,5,7);
        for(boolean slim:new boolean[]{false,true}) for(var arm:net.minecraft.world.entity.HumanoidArm.values())
            for(double yaw:new double[]{0,90,179}) for(double pitch:new double[]{-90,0,90}) {
                var root=net.minecraft.client.model.PlayerModel.createMesh(net.minecraft.client.model.geom.builders.CubeDeformation.NONE,slim)
                        .getRoot().bake(64,64);
                var model=new net.minecraft.client.model.PlayerModel<net.minecraft.world.entity.LivingEntity>(root,slim);
                var part=arm==net.minecraft.world.entity.HumanoidArm.RIGHT ? model.rightArm:model.leftArm;
                net.minecraft.world.phys.Vec3 previous=null;
                for(var camera:List.of(eye,eye.add(4,3,-5))) {
                    var pose=new PoseStack(); var position=eye.add(0,-1.62,0).subtract(camera);
                    pose.translate(position.x,position.y,position.z);
                    pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees((float)(180-yaw)));
                    pose.scale(-1,-1,1);pose.scale(.9375F,.9375F,.9375F);pose.translate(0,-1.501,0);
                    var authority=com.mistaboom.essence_ascendance.equipment.GlaiveGrip.aim(
                            com.mistaboom.essence_ascendance.equipment.GlaiveGrip.frame(eye,yaw,pitch),arm,null,16,null);
                    GlaivePlayerAnimation.poseArm(model,arm,pose,camera,authority);
                    part.translateAndRotate(pose);
                    var palm=GlaiveRenderer.handFromModel(pose,arm,camera);
                    if(previous!=null) check(previous.distanceTo(palm)<1e-6,"Native hand anchor is invariant under a third-person camera orbit");
                    if(!slim) check(palm.distanceTo(com.mistaboom.essence_ascendance.equipment.GlaiveGrip.origin(
                            com.mistaboom.essence_ascendance.equipment.GlaiveGrip.frame(eye,yaw,pitch),arm))<.001,
                            "Authority palm matches the native transformed main-arm bone: "+arm+" yaw="+yaw+" pitch="+pitch+" native="+palm+" expected="+
                                    com.mistaboom.essence_ascendance.equipment.GlaiveGrip.origin(com.mistaboom.essence_ascendance.equipment.GlaiveGrip.frame(eye,yaw,pitch),arm));
                    previous=palm;
                }
            }
    }

    private static void checkDeferredDrawOrder(Item item, BakedModel model) {
        for (EquipmentTier tier : EquipmentTier.values()) {
            ItemStack stack = new ItemStack(item);
            stack.set(DataComponents.CUSTOM_DATA, EquipmentTierData.tierData(tier));
            try (var shared = new ByteBufferBuilder(4096); var atlas = new ByteBufferBuilder(4096)) {
                var buffers = new ScheduledBuffers(shared, atlas);
                var pose = new PoseStack();
                // RenderBuffers reserves a fixed buffer for this sheet. getBuffer does not flush it
                // when the custom emission pass requests a different, shared-buffer RenderType.
                buffers.getBuffer(Sheets.translucentCullBlockSheet()).putBulkData(pose.last(), quad(1),
                        1, 1, 1, 1, 0, OverlayTexture.NO_OVERLAY);
                InventoryItemEmissionRenderer.render(stack, model, pose, buffers);
                if (tier == EquipmentTier.LATENT) {
                    check(buffers.draws.isEmpty(), "Latent leaves normal item batching untouched");
                }
                buffers.endBatch();
                check(buffers.draws.getFirst() == Sheets.translucentCullBlockSheet(),
                        "Deferred normal item sheet must draw BEFORE emission, or it overwrites the glow: " + tier);
                check(buffers.draws.size() == (tier == EquipmentTier.LATENT ? 1 : 2),
                        "Draw exactly the normal sheet plus the non-Latent emission pass");
            }
        }
    }

    /** Real BufferSource scheduling and vertex buffers; intercept only the terminal GPU upload. */
    private static final class ScheduledBuffers extends MultiBufferSource.BufferSource {
        private final List<RenderType> draws = new ArrayList<>();

        private ScheduledBuffers(ByteBufferBuilder shared, ByteBufferBuilder atlas) {
            super(shared, new LinkedHashMap<>(Map.of(Sheets.translucentCullBlockSheet(), atlas)));
        }

        @Override
        public void endBatch(RenderType type) {
            var builder = startedBuilders.remove(type);
            if (builder != null) {
                try (var mesh = builder.build()) {
                    if (mesh != null) draws.add(type);
                }
            }
            if (lastSharedType == type) lastSharedType = null;
        }
    }

    private static void checkItemContract() {
        for (Class<?> type : List.of(AscendanceSwordItem.class, AscendanceRangedWeaponItem.class,
                AscendanceCasterItem.class, AscendancePickaxeItem.class, AscendanceAxeItem.class,
                AscendanceShovelItem.class, AscendanceHoeItem.class)) {
            check(EmissiveAccentItem.class.isAssignableFrom(type), type.getSimpleName() + " opts into accent emission");
        }
        for (Class<?> type : List.of(AscendanceArmorItem.class, AscendanceShieldItem.class, AscendanceArchiveItem.class)) {
            check(!EmissiveAccentItem.class.isAssignableFrom(type),
                    type.getSimpleName() + " stays on its established non-item-pass renderer");
        }
    }

    private static void checkLoaderHooks() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isRegularFile(root.resolve("art/asset-manifest.json"))) root = root.getParent();
        check(root != null, "Project root is available for loader hook checks");
        for (String loader : List.of("fabric", "neoforge")) {
            String mixin = Files.readString(root.resolve(loader + "/src/main/java/com/mistaboom/essence_ascendance/"
                    + loader + "/mixin/ItemRendererEmissionMixin.java"));
            check(mixin.contains("shift = At.Shift.AFTER")
                            && mixin.contains("renderModelLists(")
                            && mixin.contains("InventoryItemEmissionRenderer.render(stack, model, pose, buffers)"),
                    loader + " draws once after vanilla using the selected model and active pose");
            String configName = loader.equals("fabric")
                    ? "essence_ascendance.damage.mixins.json" : "essence_ascendance.vitality.mixins.json";
            String config = Files.readString(root.resolve(loader + "/src/main/resources/" + configName));
            check(config.contains("\"ItemRendererEmissionMixin\""), loader + " registers its client-only emission hook");
        }
    }

    private static ResourceLocation texture(RenderType type) throws Exception {
        var stateField = type.getClass().getDeclaredField("state");
        stateField.setAccessible(true);
        Object state = stateField.get(type);
        var textureStateField = state.getClass().getDeclaredField("textureState");
        textureStateField.setAccessible(true);
        Object textureState = textureStateField.get(state);
        var textureField = textureState.getClass().getDeclaredField("texture");
        textureField.setAccessible(true);
        return (ResourceLocation) ((Optional<?>) textureField.get(textureState)).orElseThrow();
    }

    private static boolean channel(float actual, int expected) {
        return Math.abs(actual - expected) <= 1;
    }

    private static BakedQuad quad(int tintIndex) {
        int[] vertices = new int[32];
        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * 8;
            vertices[offset] = Float.floatToRawIntBits(vertex == 1 || vertex == 2 ? 1.0F : 0.0F);
            vertices[offset + 1] = Float.floatToRawIntBits(vertex >= 2 ? 1.0F : 0.0F);
            vertices[offset + 2] = Float.floatToRawIntBits(0.0F);
            vertices[offset + 3] = -1;
            vertices[offset + 4] = Float.floatToRawIntBits(vertex == 1 || vertex == 2 ? 1.0F : 0.0F);
            vertices[offset + 5] = Float.floatToRawIntBits(vertex >= 2 ? 1.0F : 0.0F);
        }
        return new BakedQuad(vertices, tintIndex, Direction.NORTH, null, false);
    }

    private static void permitTestItemRegistration() throws Exception {
        var frozen = MappedRegistry.class.getDeclaredField("frozen");
        frozen.setAccessible(true);
        frozen.setBoolean(BuiltInRegistries.ITEM, false);
        var intrusive = MappedRegistry.class.getDeclaredField("unregisteredIntrusiveHolders");
        intrusive.setAccessible(true);
        intrusive.set(BuiltInRegistries.ITEM, new IdentityHashMap<>());
    }

    private static void check(boolean passed, String message) {
        checks++;
        if (!passed) throw new AssertionError(message);
    }

    private static final class TestEquipmentItem extends Item
            implements EquipmentProfileItem, EmissiveAccentItem {
        private TestEquipmentItem() {
            super(new Item.Properties().durability(100));
        }

        @Override
        public ResourceLocation equipmentProfileId() {
            return EquipmentProfiles.SWORD.id();
        }
    }

    private static final class TestModel implements BakedModel {
        private static final List<BakedQuad> QUADS = List.of(quad(0), quad(1), quad(2), quad(3), quad(-1));

        @Override
        public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random) {
            return side == null ? QUADS : List.of();
        }

        @Override public boolean useAmbientOcclusion() { return false; }
        @Override public boolean isGui3d() { return false; }
        @Override public boolean usesBlockLight() { return false; }
        @Override public boolean isCustomRenderer() { return false; }
        @Override public TextureAtlasSprite getParticleIcon() { return null; }
        @Override public ItemTransforms getTransforms() { return ItemTransforms.NO_TRANSFORMS; }
        @Override public ItemOverrides getOverrides() { return ItemOverrides.EMPTY; }
    }

    private static final class RecordingBuffers implements MultiBufferSource {
        private final Map<RenderType, RecordingBuffer> recordings = new IdentityHashMap<>();

        @Override
        public VertexConsumer getBuffer(RenderType type) {
            return recordings.computeIfAbsent(type, ignored -> new RecordingBuffer());
        }
    }

    private static final class CountingBuffers implements MultiBufferSource {
        private final Map<RenderType,CountingBuffer> counters=new IdentityHashMap<>();
        public VertexConsumer getBuffer(RenderType type) { return counters.computeIfAbsent(type,t->new CountingBuffer()); }
        void clear() { counters.values().forEach(c->c.vertices=0); }
        long count(RenderType type) { var counter=counters.get(type);return counter==null ? 0:counter.vertices; }
    }
    private static final class CountingBuffer implements VertexConsumer {
        long vertices;
        public VertexConsumer addVertex(float x,float y,float z) { vertices++;return this; }
        public VertexConsumer setColor(int red,int green,int blue,int alpha) { return this; }
        public VertexConsumer setUv(float u,float v) { return this; }
        public VertexConsumer setUv1(int u,int v) { return this; }
        public VertexConsumer setUv2(int u,int v) { return this; }
        public VertexConsumer setNormal(float x,float y,float z) { return this; }
    }

    private static final class RecordingBuffer implements VertexConsumer {
        private final List<float[]> vertices = new ArrayList<>();

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            vertices.add(new float[]{x, y, z, 0, 0, 0, 0, 0, 0, 0, 0});
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            float[] vertex = vertices.getLast();
            vertex[3] = red;
            vertex[4] = green;
            vertex[5] = blue;
            vertex[6] = alpha;
            return this;
        }

        @Override public VertexConsumer setUv(float u, float v) { vertices.getLast()[9]=u;vertices.getLast()[10]=v;return this; }
        @Override public VertexConsumer setUv1(int u, int v) {
            vertices.getLast()[8] = u | v << 16;
            return this;
        }
        @Override public VertexConsumer setUv2(int u, int v) {
            vertices.getLast()[7] = u | v << 16;
            return this;
        }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
    }
}
