package com.mistaboom.essence_ascendance.projectile;

import com.mistaboom.essence_ascendance.client.GlaiveRenderer;
import com.mistaboom.essence_ascendance.client.ore.LatentOreModels;
import com.mistaboom.essence_ascendance.equipment.*;
import com.mistaboom.essence_ascendance.infuser.EssenceInfuserContent;
import com.mistaboom.essence_ascendance.ore.LatentOreHost;
import com.mistaboom.essence_ascendance.ore.LatentOreItems;
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
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import net.minecraft.world.phys.Vec3;
import java.nio.file.*;
import java.util.*;
import static com.mistaboom.essence_ascendance.equipment.WeaponActionGeometry.*;

/** Opt-in real OpenGL captures after ordinary resource loading. No gameplay world or production entrypoint. */
public final class GlaiveNativeVisualFixture {
    private static boolean done;
    public static void init() {
        if (!Boolean.getBoolean("essence.glaive.visualTest")) throw new IllegalStateException("Explicit visual fixture required");
        long started = System.nanoTime();
        String[] waiting = {""};
        System.out.println("GlaiveNativeVisualFixture: registered; waiting for loaded title screen");
        ClientTickEvent.CLIENT_POST.register(client -> {
            if (done) return;
            if (!(client.screen instanceof TitleScreen) || client.getOverlay()!=null) {
                String state = String.valueOf(client.screen) + "; overlay=" + client.getOverlay();
                if (!state.equals(waiting[0])) {
                    waiting[0] = state;
                    System.out.println("GlaiveNativeVisualFixture: waiting: " + state);
                }
                if (System.nanoTime() - started > java.util.concurrent.TimeUnit.SECONDS.toNanos(90)) {
                    new IllegalStateException("Visual fixture did not reach a loaded title screen: " + state).printStackTrace();
                    System.exit(1);
                }
                return;
            }
            done=true;
            try {
                if (Boolean.getBoolean("essence.latentOre.modelsOnly")) {
                    com.mistaboom.essence_ascendance.ore.LatentOreClientModelFixture.verify();
                    verifyLatentOreItemModels(client);
                    client.stop();
                    return;
                }
                NativeGlaivePresentationTest.run();
                verifyLatentOreItemModels(client);
                renderedShaftMotion();
                captures(client);
                System.out.println("GlaiveNativeVisualFixture: native OpenGL body/material/repeat/depth captures PASS");
                if (!Boolean.getBoolean("essence.glaive.visualKeepOpen")) client.stop();
            } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
        });
    }

    /** ItemRenderer's selected model is the native inventory path on both 1.21 loaders. */
    private static void verifyLatentOreItemModels(Minecraft client) throws ReflectiveOperationException {
        var nativePixels = SpriteContents.class.getDeclaredField("originalImage");
        nativePixels.setAccessible(true);
        Set<Set<ResourceLocation>> variants = new HashSet<>();
        Set<BlockState> hosts = new HashSet<>();
        ItemStack registeredDefault = EssenceInfuserContent.LATENT_ORE_ITEM.get().getDefaultInstance();
        if (LatentOreHost.read(registeredDefault).orElse(null) != Blocks.STONE.defaultBlockState())
            throw new AssertionError("Registered Latent Ore default item must carry its Stone host");
        if (registeredDefault.is(Blocks.END_STONE.asItem()))
            throw new AssertionError("Vanilla End Stone is not the registered adaptive Latent Ore item");
        for (ItemStack stack : LatentOreHost.creativeStacks()) {
            BlockState host = LatentOreHost.read(stack).orElseThrow(() ->
                    new AssertionError("Creative Latent Ore item lost its host before model selection"));
            var alias = LatentOreItems.itemFor(host);
            ResourceLocation aliasId = BuiltInRegistries.ITEM.getKey(alias);
            ResourceLocation expectedId = LatentOreItems.itemId(BuiltInRegistries.BLOCK.getKey(host.getBlock()));
            ItemStack giveStack = new ItemStack(alias);
            if (!aliasId.equals(expectedId) || !stack.is(alias) || !hosts.add(host)
                    || !ItemStack.isSameItemSameComponents(stack, LatentOreHost.stack(host)))
                throw new AssertionError("Creative Latent Ore catalog diverged from its host alias: "
                        + host + " alias=" + aliasId);
            if (LatentOreHost.read(giveStack).orElse(null) != host)
                throw new AssertionError("/give Latent Ore alias lacks its default host: " + aliasId);
            BakedModel item = client.getItemRenderer().getModel(stack, null, null, 0);
            BakedModel given = client.getItemRenderer().getModel(giveStack, null, null, 0);
            BakedModel world = LatentOreModels.forHost(host, null);
            if (item == null || item != given || world == null)
                throw new AssertionError("Creative and /give Latent Ore aliases did not select the same host model: "
                        + host + " alias=" + aliasId);
            BakedModel adaptive = client.getItemRenderer().getItemModelShaper()
                    .getItemModel(EssenceInfuserContent.LATENT_ORE_ITEM.get());
            if (item.getTransforms() != adaptive.getTransforms())
                throw new AssertionError("Latent Ore alias lost authored inventory transforms: " + aliasId);
            Set<ResourceLocation> itemSprites = new HashSet<>(), worldSprites = new HashSet<>();
            Map<ResourceLocation, NativeImage> itemImages = new HashMap<>(), worldImages = new HashMap<>();
            int itemQuads = 0, worldQuads = 0;
            for (int side = -1; side < Direction.values().length; side++) {
                Direction face = side < 0 ? null : Direction.values()[side];
                for (var quad : item.getQuads(null, face, RandomSource.create(0))) {
                    itemQuads++;
                    var contents = quad.getSprite().contents();
                    itemSprites.add(contents.name());
                    itemImages.put(contents.name(), (NativeImage) nativePixels.get(contents));
                }
                for (var quad : world.getQuads(host, face, RandomSource.create(0))) {
                    worldQuads++;
                    var contents = quad.getSprite().contents();
                    worldSprites.add(contents.name());
                    worldImages.put(contents.name(), (NativeImage) nativePixels.get(contents));
                }
            }
            if (itemQuads < 6 || itemQuads != worldQuads)
                throw new AssertionError("Latent Ore item lost native cube faces: host=" + host
                        + ", item=" + itemQuads + ", world=" + worldQuads);
            if (itemSprites.isEmpty() || itemSprites.stream().anyMatch(id ->
                    !id.getPath().startsWith("block/latent_ore/generated/")))
                throw new AssertionError("ItemRenderer selected a noncomposite Latent Ore inventory sprite: "
                        + host + " " + itemSprites);
            if (worldSprites.stream().anyMatch(id ->
                    !id.getPath().startsWith("block/latent_ore/generated/")))
                throw new AssertionError("Latent Ore world model lost its native host composite: "
                        + host + " " + worldSprites);
            Set<String> itemHosts = new HashSet<>(), worldHosts = new HashSet<>();
            itemSprites.forEach(id -> itemHosts.add(id.getPath().substring("block/latent_ore/generated/".length())));
            worldSprites.forEach(id -> worldHosts.add(id.getPath().substring("block/latent_ore/generated/".length())));
            if (!itemHosts.equals(worldHosts) || !variants.add(itemSprites))
                throw new AssertionError("Latent Ore inventory host differs from its placed block or another item: "
                        + host + " " + itemSprites);
            for (ResourceLocation itemId : itemSprites) {
                ResourceLocation worldId = itemId;
                NativeImage itemImage = itemImages.get(itemId), worldImage = worldImages.get(worldId);
                if (worldImage == null || itemImage.getWidth() != worldImage.getWidth()
                        || itemImage.getHeight() != worldImage.getHeight())
                    throw new AssertionError("Latent Ore item lacks its matching native world texture: " + itemId);
                for (int y = 0; y < itemImage.getHeight(); y++) for (int x = 0; x < itemImage.getWidth(); x++)
                    if (itemImage.getPixelRGBA(x, y) != worldImage.getPixelRGBA(x, y))
                        throw new AssertionError("Latent Ore item changed the original transition color: host="
                                + host + ", sprite=" + itemId + ", pixel=" + x + "," + y);
            }
            System.out.println("LatentOreItemModelProbe: " + aliasId + " item=" + itemSprites);
        }
        if (!hosts.containsAll(Set.of(Blocks.STONE.defaultBlockState(), Blocks.DEEPSLATE.defaultBlockState(),
                Blocks.NETHERRACK.defaultBlockState(), Blocks.END_STONE.defaultBlockState())))
            throw new AssertionError("Creative Latent Ore catalog is missing a baseline host: " + hosts);
        System.out.println("LatentOreItemModelProbe: four registered 1.21 ItemRenderer aliases and /give defaults with distinct item-only"
                + " host composites, matching world faces and original transition pixels PASS");
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
        for (int i=0;i<=12;i++) capture(output.resolve("slide-%02d.png".formatted(i)),
                REST_TIP+(8+i/12.0)*2*DIAGONAL_PIXEL,grey,-.2,1.7,false);
        keyframes(output,grey);
    }
    private static void keyframes(Path output,EquipmentAppearance appearance) throws Exception {
        var mc=Minecraft.getInstance();var main=net.minecraft.world.entity.HumanoidArm.RIGHT;
        var rest=new PoseStack();
        var placement=ItemInHandRenderer.class.getDeclaredMethod("applyItemArmTransform",PoseStack.class,
                net.minecraft.world.entity.HumanoidArm.class,float.class);
        placement.setAccessible(true);placement.invoke(mc.gameRenderer.itemInHandRenderer,rest,main,0F);
        GlaiveRenderer.firstPersonRestTransform(main,rest);
        var camera=new Vec3(0,1.62,0);
        var aim=GlaiveGrip.aim(GlaiveGrip.frame(camera,180,0),main,null,REST_TIP,null);
        var handProjection=new Matrix4f().perspective((float)Math.toRadians(70),16F/9,.05F,256);
        var worldProjection=new Matrix4f().perspective((float)Math.toRadians(90),16F/9,.05F,256)
                .rotateZ(.025F).translate(.01F,-.01F,0);
        var handView=new Matrix4f().rotateYXZ(.8F,-.3F,.1F);
        var nativeRest=new Matrix4f(handView).invert().mul(rest.last().pose());
        for(var mode:WeaponActionRuntime.Mode.values()) for(int i=0;i<=24;i++) {
            float time=i/24F,blend;Pose weapon;
            if(time<.2F) { blend=smooth(time/.2F);weapon=GlaiveGrip.sweep(aim,mode==WeaponActionRuntime.Mode.SWIPE ? -60:0,main,1); }
            else if(time<.7F) {
                blend=1;float progress=(time-.2F)/.5F;
                weapon=GlaiveGrip.sweep(aim.withDistance(REST_TIP+(8-REST_TIP)*progress),
                        mode==WeaponActionRuntime.Mode.SWIPE ? -60+120*progress:0,main,1);
            } else {
                float progress=(time-.7F)/.3F;blend=1-smooth(progress);
                weapon=interpolate(GlaiveGrip.sweep(aim.withDistance(8),mode==WeaponActionRuntime.Mode.SWIPE ? 60:0,main,1),
                        GlaiveGrip.rest(GlaiveGrip.frame(camera,180,0),main),progress);
            }
            var active=GlaiveRenderer.bodyTransform(weapon,camera,new Matrix4f());
            var transform=GlaiveRenderer.firstPersonTransform(nativeRest,active,blend,handProjection,worldProjection,handView);
            captureKeyframe(output.resolve("keyframe-"+mode.name().toLowerCase(java.util.Locale.ROOT)+"-%02d.png".formatted(i)),
                    transform,handProjection,handView,REST_TIP+(weapon.tipDistance()-REST_TIP)*blend,appearance);
        }
    }
    private static float smooth(float value) { float t=Math.clamp(value,0,1);return t*t*(3-2*t); }
    private static void captureKeyframe(Path file,Matrix4f transform,Matrix4f projection,Matrix4f handView,double distance,EquipmentAppearance appearance) throws Exception {
        var mc=Minecraft.getInstance();var target=new TextureTarget(1280,720,true,Minecraft.ON_OSX);
        var previous=new Matrix4f(RenderSystem.getProjectionMatrix());
        target.setClearColor(.08F,.10F,.14F,1);target.clear(Minecraft.ON_OSX);target.bindWrite(true);
        RenderSystem.setProjectionMatrix(projection,VertexSorting.DISTANCE_TO_ORIGIN);
        RenderSystem.getModelViewStack().pushMatrix().identity().mul(handView);RenderSystem.applyModelViewMatrix();
        try {
            var pose=new PoseStack();pose.mulPose(transform);
            var buffers=mc.renderBuffers().bufferSource();
            GlaiveRenderer.renderBody(new Pose(Vec3.ZERO,0,0,distance),appearance,pose,buffers,LightTexture.FULL_BRIGHT);
            buffers.endBatch();
            try(var image=Screenshot.takeScreenshot(target)) { image.writeToFile(file); }
        } finally {
            RenderSystem.getModelViewStack().popMatrix();RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(previous,VertexSorting.DISTANCE_TO_ORIGIN);
            target.destroyBuffers();mc.getMainRenderTarget().bindWrite(true);
        }
    }
    /** Record actual transformed mesh vertices, including frames on both sides of a repeat boundary. */
    private static void renderedShaftMotion() {
        double repeat=2*DIAGONAL_PIXEL;
        for (double extension:new double[]{0,.01*repeat,.5*repeat,.99*repeat,1.01*repeat,2.99*repeat,
                8.99*repeat,4-REST_TIP,10-REST_TIP,64-REST_TIP}) {
            var vertices=renderVertices(extension,0);
            var moving=shaftPoints(vertices,8,22);
            require(!moving.isEmpty() && Math.abs(moving.getFirst()-extension)<1e-5,"Upper shaft follows head");
            require(moving.getLast()>=-repeat-1e-5 && moving.getLast()<=1e-5,"Last whole row overlaps inside palm");
            for (int i=1;i<moving.size();i++)
                require(Math.abs(moving.get(i-1)-moving.get(i)-repeat)<1e-5,"Repeated rows meet without gaps");
            var grip=shaftPoints(vertices,5,25);
            require(grip.size()==1 && Math.abs(grip.getFirst()+6*DIAGONAL_PIXEL)<1e-5,"Grip stays fixed");
            double bladeTip=vertices.stream().filter(v->v[5]==0x11 && v[6]==0x44 && v[7]==0x77)
                    .mapToDouble(v->v[2]).max().orElseThrow();
            require(Math.abs(bladeTip-REST_TIP-extension)<1e-5,"Authored blade tip matches solved tip distance");
            for (double cutoff:new double[]{.2,.35}) for (var vertex:renderVertices(extension,cutoff))
                require(vertex[2]>=cutoff-1e-6,"Camera cutoff clips every moving section in world coordinates");
        }
        for (double[] pair:new double[][]{{8.2,8.45},{8.99,9.01},{9.01,8.99}}) {
            var before=shaftPoints(renderVertices(pair[0]*repeat,0),8,22);
            var after=shaftPoints(renderVertices(pair[1]*repeat,0),8,22);
            for (int i=0;i<5;i++) require(Math.abs(after.get(i)-before.get(i)-(pair[1]-pair[0])*repeat)<1e-5,
                    "Shaft texture slides continuously through repeat boundaries in both directions");
        }
        System.out.println("GlaiveNativeVisualFixture: rendered shaft motion, palm overlap, blade bounds and camera clipping PASS");
    }
    private static List<float[]> renderVertices(double extension,double cutoff) {
        var buffer=new RecordingBuffer();
        GlaiveRenderer.renderBody(new Pose(Vec3.ZERO,0,0,REST_TIP+extension),
                new EquipmentAppearance(0x114477,0x77AA44,0),new PoseStack(),type->buffer,LightTexture.FULL_BRIGHT,cutoff);
        return buffer.vertices;
    }
    private static List<Double> shaftPoints(List<float[]> vertices,int pixelX,int pixelY) {
        var points=new TreeSet<Double>(Comparator.reverseOrder());
        for (var v:vertices) if (v[5]==255 && v[6]==255 && v[7]==255
                && Math.abs(v[3]-(pixelX+.5)/32)<1e-6 && Math.abs(v[4]-(pixelY+.5)/32)<1e-6
                && Math.abs(v[0]+2*DIAGONAL_PIXEL)<1e-6 && Math.abs(v[1]+THICKNESS/2)<1e-6)
            points.add((double)v[2]);
        return new ArrayList<>(points);
    }
    private static void require(boolean condition,String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static final class RecordingBuffer implements VertexConsumer {
        private final List<float[]> vertices=new ArrayList<>();
        public VertexConsumer addVertex(float x,float y,float z) { vertices.add(new float[]{x,y,z,0,0,0,0,0}); return this; }
        public VertexConsumer setColor(int r,int g,int b,int a) {
            var v=vertices.getLast(); v[5]=r; v[6]=g; v[7]=b; return this;
        }
        //? if >=26.1.2
        /*@Override public VertexConsumer setColor(int argb) { return setColor(argb >>> 16 & 255, argb >>> 8 & 255, argb & 255, argb >>> 24 & 255); }*/
        public VertexConsumer setUv(float u,float v) { var p=vertices.getLast(); p[3]=u; p[4]=v; return this; }
        public VertexConsumer setNormal(float x,float y,float z) { return this; }
        public VertexConsumer setUv1(int u,int v) { return this; }
        public VertexConsumer setUv2(int u,int v) { return this; }
        //? if >=26.1.2
        /*@Override public VertexConsumer setLineWidth(float width) { if (!Float.isFinite(width) || width <= 0) throw new AssertionError("Invalid native line width"); return this; }*/
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
