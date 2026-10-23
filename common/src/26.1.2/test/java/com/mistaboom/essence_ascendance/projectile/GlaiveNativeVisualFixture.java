package com.mistaboom.essence_ascendance.projectile;

import com.mistaboom.essence_ascendance.client.ClientLightCompat;
import com.mistaboom.essence_ascendance.client.GlaiveRenderer;
import com.mistaboom.essence_ascendance.client.ore.LatentOreModels;
import com.mistaboom.essence_ascendance.equipment.*;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.architectury.event.events.client.ClientTickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

import static com.mistaboom.essence_ascendance.equipment.WeaponActionGeometry.*;

/** Explicit offscreen native GPU captures for 26.1; never enters a gameplay world. */
public final class GlaiveNativeVisualFixture {
    private static final ArrayDeque<Shot> SHOTS = new ArrayDeque<>();
    private static final int CLEAR = 0xFF141A24;
    private static volatile boolean waitingForScreenshot;
    private static volatile Throwable failure;
    private static boolean prepared;
    private static int completed;
    private static long started;
    private static ProjectionMatrixBuffer projectionBuffer;

    private record Shot(Path file, int width, int height, Matrix4f projection,
                        ProjectionType projectionType, Matrix4f view, Matrix4f model,
                        double tip, EquipmentAppearance appearance, boolean occluder) { }

    private GlaiveNativeVisualFixture() { }

    public static void init() {
        if (!Boolean.getBoolean("essence.glaive.visualTest"))
            throw new IllegalStateException("Explicit visual fixture required");
        started = System.nanoTime();
        System.out.println("GlaiveNativeVisualFixture: registered; waiting for loaded title screen");
        ClientTickEvent.CLIENT_POST.register(client -> {
            if (failure != null) {
                failure.printStackTrace();
                System.exit(1);
            }
            if (System.nanoTime() - started > TimeUnit.MINUTES.toNanos(10)) {
                new IllegalStateException("Native captures did not complete: " + completed + " saved, "
                        + SHOTS.size() + " queued; screen=" + client.screen).printStackTrace();
                System.exit(1);
            }
            if (!prepared) {
                if (!(client.screen instanceof TitleScreen) || client.getOverlay() != null) return;
                try {
                    // 26.1 binds ItemStack components when a world is joined. Keep
                    // item/material assertions for a registry-bound native run;
                    // playback and queued-input checks need no world registry.
                    System.out.println("GlaiveNativeVisualFixture: ItemStack assertions require a registry-bound run");
                    NativeGlaivePresentationTest.runPlaybackAndInput();
                    verifyLatentOreNativeModels();
                    renderedShaftMotion();
                    prepareShots(Path.of(System.getProperty("essence.glaive.visualOutput")));
                    projectionBuffer = new ProjectionMatrixBuffer("Glaive native visual fixture");
                    prepared = true;
                    System.out.println("GlaiveNativeVisualFixture: " + SHOTS.size() + " native GPU captures queued");
                } catch (Throwable e) { failure = e; }
                return;
            }
            if (waitingForScreenshot) return;
            Shot shot = SHOTS.pollFirst();
            if (shot == null) {
                projectionBuffer.close();
                System.out.println("GlaiveNativeVisualFixture: " + completed
                        + " native GPU body/material/repeat/depth captures PASS");
                if (!Boolean.getBoolean("essence.glaive.visualKeepOpen")) client.stop();
                return;
            }
            waitingForScreenshot = true;
            try { capture(client, shot); }
            catch (Throwable e) { failure = e; waitingForScreenshot = false; }
        });
    }

    private static void verifyLatentOreNativeModels() throws Exception {
        var nativePixels = SpriteContents.class.getDeclaredField("originalImage");
        nativePixels.setAccessible(true);
        Set<Set<Identifier>> variants = new HashSet<>();
        Set<Object> guiIdentities = new HashSet<>();
        Set<Set<Identifier>> itemVariants = new HashSet<>();
        Set<Set<Integer>> pixelVariants = new HashSet<>();
        for (BlockState host : List.of(Blocks.STONE.defaultBlockState(), Blocks.DEEPSLATE.defaultBlockState(),
                Blocks.NETHERRACK.defaultBlockState(), Blocks.END_STONE.defaultBlockState())) {
            var model = LatentOreModels.forHost(host, null);
            if (model == null) throw new AssertionError("Missing prepared Latent Ore host model: " + host);
            List<BlockStateModelPart> parts = new ArrayList<>();
            model.collectParts(RandomSource.create(0), parts);
            Set<Identifier> sprites = new HashSet<>();
            Set<Integer> pixelHashes = new HashSet<>();
            Map<Identifier, NativeImage> worldImages = new HashMap<>();
            int quads = 0;
            for (BlockStateModelPart part : parts) {
                for (Direction face : Direction.values()) {
                    for (BakedQuad quad : part.getQuads(face)) {
                        quads++;
                        assertOreOverlayUpright(quad, "world " + host);
                        var sprite = quad.materialInfo().sprite();
                        sprites.add(sprite.contents().name());
                        var image = (NativeImage) nativePixels.get(sprite.contents());
                        worldImages.put(sprite.contents().name(), image);
                        int[] pixels = new int[image.getWidth() * image.getHeight()];
                        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++)
                            pixels[y * image.getWidth() + x] = image.getPixel(x, y);
                        pixelHashes.add(Arrays.hashCode(pixels));
                    }
                }
            }
            if (quads < 6 || !variants.add(sprites))
                throw new AssertionError("Latent Ore host model is missing faces or shares sprites: " + host);
            if (sprites.stream().anyMatch(id -> !id.getPath().startsWith("block/latent_ore/generated/")))
                throw new AssertionError("Latent Ore host model uses a noncomposite sprite: " + sprites);
            if (!pixelVariants.add(pixelHashes))
                throw new AssertionError("Distinct Latent Ore host sprites have identical pixel contents: " + host);
            var guiState = new TrackingItemStackRenderState();
            List<BakedQuad> itemQuads = LatentOreModels.itemQuadsForHost(host, guiState);
            if (itemQuads == null || itemQuads.size() < 6)
                throw new AssertionError("Latent Ore item lacks its six native host faces: " + host);
            List<BlockStateModelPart> nativeItemParts = new ArrayList<>();
            Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(host)
                    .collectParts(RandomSource.create(0), nativeItemParts);
            List<BakedQuad> nativeItemQuads = new ArrayList<>();
            for (BlockStateModelPart part : nativeItemParts) {
                nativeItemQuads.addAll(part.getQuads(null));
                for (Direction side : Direction.values()) nativeItemQuads.addAll(part.getQuads(side));
            }
            if (nativeItemQuads.size() != itemQuads.size())
                throw new AssertionError("Latent Ore item changed its native host face count: " + host);
            for (int i = 0; i < itemQuads.size(); i++)
                assertNativeHostGeometry(nativeItemQuads.get(i), itemQuads.get(i));
            var repeatedState = new TrackingItemStackRenderState();
            if (itemQuads != LatentOreModels.itemQuadsForHost(host, repeatedState))
                throw new AssertionError("Latent Ore item quads must be cached for repeated inventory renders: " + host);
            if (!guiIdentities.add(guiState.getModelIdentity()))
                throw new AssertionError("Two host variants share a GUI atlas image identity: " + host);
            if (!guiState.getModelIdentity().equals(repeatedState.getModelIdentity()))
                throw new AssertionError("Repeated host renders must reuse the same GUI atlas image: " + host);
            Set<Identifier> itemSprites = new HashSet<>();
            for (BakedQuad quad : itemQuads) {
                assertOreOverlayUpright(quad, "item " + host);
                var itemSprite = quad.materialInfo().sprite();
                Identifier itemId = itemSprite.contents().name();
                if (!itemId.getPath().startsWith("block/latent_ore/item/"))
                    throw new AssertionError("Latent Ore item uses a noncomposite sprite: " + itemId);
                itemSprites.add(itemId);
                Identifier worldId = Identifier.parse(itemId.toString().replace(
                        "block/latent_ore/item/", "block/latent_ore/generated/"));
                NativeImage worldImage = worldImages.get(worldId);
                if (worldImage == null)
                    throw new AssertionError("Latent Ore item lost its matching native world face: " + itemId);
                NativeImage itemImage = (NativeImage) nativePixels.get(itemSprite.contents());
                if (itemImage.getWidth() != worldImage.getWidth() || itemImage.getHeight() != worldImage.getHeight())
                    throw new AssertionError("Latent Ore item changed native host texture dimensions: " + itemId);
                for (int y = 0; y < itemImage.getHeight(); y++) for (int x = 0; x < itemImage.getWidth(); x++) {
                    int worldPixel = worldImage.getPixel(x, y), itemPixel = itemImage.getPixel(x, y);
                    if (itemPixel != worldPixel)
                        throw new AssertionError("Latent Ore item changed the original transition color: host="
                                + host + ", sprite=" + itemId + ", pixel=" + x + "," + y);
                }
            }
            if (!itemVariants.add(itemSprites))
                throw new AssertionError("Two Latent Ore item hosts share sprite sets: " + host);
            // World models can choose different rotations with the block-position seed.
            for (int seed = 0; seed < 32; seed++) {
                List<BlockStateModelPart> selected = new ArrayList<>(), nativeParts = new ArrayList<>();
                model.collectParts(RandomSource.create(seed), selected);
                Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(host)
                        .collectParts(RandomSource.create(seed), nativeParts);
                if (selected.size() != nativeParts.size()) throw new AssertionError("Host parts changed: " + host);
                for (int p = 0; p < selected.size(); p++) for (Direction face : Direction.values()) {
                    var actual = selected.get(p).getQuads(face);
                    var original = nativeParts.get(p).getQuads(face);
                    if (actual.size() != original.size()) throw new AssertionError("Host faces changed: " + host);
                    for (int q = 0; q < actual.size(); q++) {
                        assertOreOverlayUpright(actual.get(q), "world " + host + " seed=" + seed);
                        assertNativeHostGeometry(original.get(q), actual.get(q));
                    }
                }
            }
            System.out.println("LatentOreNativeModelProbe: " + host.getBlock() + " world=" + sprites
                    + " item=" + itemSprites);
        }
        System.out.println("LatentOreNativeModelProbe: four distinct GUI atlas identities, world/item host pixels,"
                + " identical original transition colors, upright overlays at 32 world seeds,"
                + " unchanged native geometry/UVs PASS");
    }

    /** Resolve the composed mask back through the native face UVs, then compare with world height. */
    private static void assertOreOverlayUpright(BakedQuad quad, String context) {
        if (!quad.direction().getAxis().isHorizontal()) return;
        var sprite = quad.materialInfo().sprite();
        String path = sprite.contents().name().getPath();
        for (int vertex = 0; vertex < 4; vertex++) {
            long packed = quad.packedUV(vertex);
            float u = (net.minecraft.client.model.geom.builders.UVPair.unpackU(packed) - sprite.getU0())
                    / (sprite.getU1() - sprite.getU0());
            float v = (net.minecraft.client.model.geom.builders.UVPair.unpackV(packed) - sprite.getV0())
                    / (sprite.getV1() - sprite.getV0());
            float maskV = path.contains("_u_down/") ? u : path.contains("_u_up/") ? 1 - u
                    : path.contains("_v_up/") ? 1 - v : v;
            float down = 1 - quad.position(vertex).y();
            if (Math.abs(maskV - down) > 0.002f)
                throw new AssertionError("Sideways ore overlay: " + context + " face=" + quad.direction()
                        + " vertex=" + vertex + " maskV=" + maskV + " down=" + down);
        }
    }

    private static void assertNativeHostGeometry(BakedQuad original, BakedQuad actual) {
        var before = original.materialInfo().sprite();
        var after = actual.materialInfo().sprite();
        for (int v = 0; v < 4; v++) {
            if (!original.position(v).equals(actual.position(v)))
                throw new AssertionError("Ore rendering changed native host vertex");
            float bu = (net.minecraft.client.model.geom.builders.UVPair.unpackU(original.packedUV(v)) - before.getU0())
                    / (before.getU1() - before.getU0());
            float bv = (net.minecraft.client.model.geom.builders.UVPair.unpackV(original.packedUV(v)) - before.getV0())
                    / (before.getV1() - before.getV0());
            float au = (net.minecraft.client.model.geom.builders.UVPair.unpackU(actual.packedUV(v)) - after.getU0())
                    / (after.getU1() - after.getU0());
            float av = (net.minecraft.client.model.geom.builders.UVPair.unpackV(actual.packedUV(v)) - after.getV0())
                    / (after.getV1() - after.getV0());
            if (Math.abs(bu - au) > 0.002f || Math.abs(bv - av) > 0.002f)
                throw new AssertionError("Ore rendering rotated native host UVs");
        }
    }


    private static void prepareShots(Path output) throws Exception {
        Files.createDirectories(output);
        var grey = new EquipmentAppearance(EquipmentTierVisuals.primaryRgb(EquipmentTier.LATENT),
                EquipmentTierVisuals.armorAccentRgb(EquipmentTier.LATENT), 0);
        var tier = new EquipmentAppearance(EquipmentTierVisuals.primaryRgb(EquipmentTier.TRANSCENDENT),
                EquipmentTierVisuals.armorAccentRgb(EquipmentTier.TRANSCENDENT), 180);
        var personal = new EquipmentAppearance(0x0d0967, 0xb6f800, 180);
        ortho(output, "compact-grey.png", REST_TIP, grey, 0, 1.6, false);
        ortho(output, "compact-tier.png", REST_TIP, tier, 0, 1.6, false);
        ortho(output, "compact-personalized.png", REST_TIP, personal, 0, 1.6, false);
        ortho(output, "extended-partial.png", 4.013, personal, 0, 4.3, false);
        ortho(output, "repeat-closeup.png", 4.013, grey, .15, 1.1, false);
        ortho(output, "range64.png", 64, personal, 0, 64.3, false);
        ortho(output, "shortened.png", .8, tier, 0, 1.6, false);
        ortho(output, "depth-occlusion.png", 4.013, personal, 0, 4.3, true);
        for (int i = 0; i <= 12; i++)
            ortho(output, "slide-%02d.png".formatted(i), REST_TIP + (8 + i / 12.0) * 2 * DIAGONAL_PIXEL,
                    grey, -.2, 1.7, false);
        keyframes(output, grey);
    }

    private static void ortho(Path output, String name, double tip, EquipmentAppearance appearance,
                              double start, double width, boolean occluder) {
        var projection = new Matrix4f().setOrtho((float) (start - .1), (float) (start + width - .1),
                (float) (-width / 8), (float) (width / 8), -10, 10);
        var model = new Matrix4f().m00(0).m01(1).m02(0).m10(0).m11(0).m12(1).m20(1).m21(0).m22(0);
        SHOTS.add(new Shot(output.resolve(name), 2048, 512, projection, ProjectionType.ORTHOGRAPHIC,
                new Matrix4f(), model, tip, appearance, occluder));
    }

    private static void keyframes(Path output, EquipmentAppearance appearance) throws Exception {
        var mc = Minecraft.getInstance();
        var main = net.minecraft.world.entity.HumanoidArm.RIGHT;
        var rest = new PoseStack();
        var placement = ItemInHandRenderer.class.getDeclaredMethod("applyItemArmTransform", PoseStack.class,
                net.minecraft.world.entity.HumanoidArm.class, float.class);
        placement.setAccessible(true);
        placement.invoke(mc.gameRenderer.itemInHandRenderer, rest, main, 0F);
        GlaiveRenderer.firstPersonRestTransform(main, rest);
        var camera = new Vec3(0, 1.62, 0);
        var aim = GlaiveGrip.aim(GlaiveGrip.frame(camera, 180, 0), main, null, REST_TIP, null);
        var handProjection = new Matrix4f().perspective((float) Math.toRadians(70), 16F / 9, .05F, 256);
        var worldProjection = new Matrix4f().perspective((float) Math.toRadians(90), 16F / 9, .05F, 256)
                .rotateZ(.025F).translate(.01F, -.01F, 0);
        var handView = new Matrix4f().rotateYXZ(.8F, -.3F, .1F);
        var nativeRest = new Matrix4f(handView).invert().mul(rest.last().pose());
        for (var mode : WeaponActionRuntime.Mode.values()) for (int i = 0; i <= 24; i++) {
            float time = i / 24F, blend;
            Pose weapon;
            if (time < .2F) {
                blend = smooth(time / .2F);
                weapon = GlaiveGrip.sweep(aim, mode == WeaponActionRuntime.Mode.SWIPE ? -60 : 0, main, 1);
            } else if (time < .7F) {
                blend = 1;
                float progress = (time - .2F) / .5F;
                weapon = GlaiveGrip.sweep(aim.withDistance(REST_TIP + (8 - REST_TIP) * progress),
                        mode == WeaponActionRuntime.Mode.SWIPE ? -60 + 120 * progress : 0, main, 1);
            } else {
                float progress = (time - .7F) / .3F;
                blend = 1 - smooth(progress);
                weapon = interpolate(GlaiveGrip.sweep(aim.withDistance(8),
                        mode == WeaponActionRuntime.Mode.SWIPE ? 60 : 0, main, 1),
                        GlaiveGrip.rest(GlaiveGrip.frame(camera, 180, 0), main), progress);
            }
            var active = GlaiveRenderer.bodyTransform(weapon, camera, new Matrix4f());
            var transform = GlaiveRenderer.firstPersonTransform(nativeRest, active, blend,
                    handProjection, worldProjection, handView);
            SHOTS.add(new Shot(output.resolve("keyframe-" + mode.name().toLowerCase(Locale.ROOT)
                    + "-%02d.png".formatted(i)), 1280, 720, new Matrix4f(handProjection),
                    ProjectionType.PERSPECTIVE, new Matrix4f(handView), transform,
                    REST_TIP + (weapon.tipDistance() - REST_TIP) * blend, appearance, false));
        }
    }

    private static float smooth(float value) { float t = Math.clamp(value, 0, 1); return t * t * (3 - 2 * t); }

    private static void capture(Minecraft client, Shot shot) {
        var target = new TextureTarget("Glaive visual: " + shot.file().getFileName(), shot.width(), shot.height(), true);
        var previousColor = RenderSystem.outputColorTextureOverride;
        var previousDepth = RenderSystem.outputDepthTextureOverride;
        var previousProjection = RenderSystem.getProjectionMatrixBuffer();
        var previousProjectionType = RenderSystem.getProjectionType();
        var modelView = RenderSystem.getModelViewStack();
        try {
            RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
                    target.getColorTexture(), CLEAR, target.getDepthTexture(), 1);
            RenderSystem.outputColorTextureOverride = target.getColorTextureView();
            RenderSystem.outputDepthTextureOverride = target.getDepthTextureView();
            RenderSystem.setProjectionMatrix(projectionBuffer.getBuffer(shot.projection()), shot.projectionType());
            modelView.pushMatrix().identity().mul(shot.view());
            if (shot.occluder()) drawOccluder(client);
            var pose = new PoseStack();
            pose.mulPose(shot.model());
            var buffers = client.renderBuffers().bufferSource();
            GlaiveRenderer.renderBody(new Pose(Vec3.ZERO, 0, 0, shot.tip()), shot.appearance(),
                    pose, buffers, ClientLightCompat.FULL_BRIGHT);
            buffers.endBatch();
            Screenshot.takeScreenshot(target, image -> {
                try (image) {
                    require(image.getWidth() == shot.width() && image.getHeight() == shot.height(),
                            "Native screenshot dimensions must match the fixture target");
                    boolean different = false;
                    for (int y = 0; y < image.getHeight() && !different; y++)
                        for (int x = 0; x < image.getWidth(); x++)
                            if (image.getPixel(x, y) != CLEAR) { different = true; break; }
                    if (!different) {
                        // During the wide swipe the complete blade can legitimately cross
                        // outside the camera. Prove this from the actual emitted vertices;
                        // an empty target while geometry is in view remains a failure.
                        require(shot.file().getFileName().toString().startsWith("keyframe-swipe-")
                                        && outsideView(shot),
                                "Native GPU screenshot is empty with geometry potentially in view: " + shot.file());
                        System.out.println("GlaiveNativeVisualFixture: verified offscreen swipe " + shot.file().getFileName());
                    }
                    image.writeToFile(shot.file());
                    require(Files.size(shot.file()) > 100, "Native screenshot was not written: " + shot.file());
                    completed++;
                } catch (Throwable e) { failure = e; }
                finally { target.destroyBuffers(); waitingForScreenshot = false; }
            });
        } catch (Throwable e) {
            target.destroyBuffers();
            throw e;
        } finally {
            modelView.popMatrix();
            RenderSystem.setProjectionMatrix(previousProjection, previousProjectionType);
            RenderSystem.outputColorTextureOverride = previousColor;
            RenderSystem.outputDepthTextureOverride = previousDepth;
        }
    }

    private static boolean outsideView(Shot shot) {
        var clip = new Matrix4f(shot.projection()).mul(shot.view()).mul(shot.model());
        boolean left = true, right = true, below = true, above = true;
        var vertices = renderVertices(shot.tip() - REST_TIP, 0);
        require(!vertices.isEmpty(), "Offscreen proof must have emitted native Glaive geometry");
        for (var vertex : vertices) {
            var point = clip.transform(new Vector4f(vertex[0], vertex[1], vertex[2], 1));
            left &= point.x < -point.w - 1e-5F;
            right &= point.x > point.w + 1e-5F;
            below &= point.y < -point.w - 1e-5F;
            above &= point.y > point.w + 1e-5F;
        }
        return left || right || below || above;
    }

    private static void drawOccluder(Minecraft client) {
        var type = RenderTypes.entitySolid(TextureAtlas.LOCATION_ITEMS);
        require(type.pipeline().getDepthStencilState().writeDepth(), "Occluder must write native depth");
        var buffers = client.renderBuffers().bufferSource();
        var v = buffers.getBuffer(type);
        for (float[] p : new float[][]{{1, -1, 1}, {2, -1, 1}, {2, 1, 1}, {1, 1, 1}})
            v.addVertex(p[0], p[1], p[2]).setColor(100, 110, 120, 255).setUv(0, 0)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(ClientLightCompat.FULL_BRIGHT)
                    .setNormal(0, 0, 1);
        buffers.endBatch(type);
    }

    /** The same moving shaft and clipping invariants as the 1.21 fixture, using native 26 vertex consumers. */
    private static void renderedShaftMotion() {
        double repeat = 2 * DIAGONAL_PIXEL;
        for (double extension : new double[]{0, .01 * repeat, .5 * repeat, .99 * repeat, 1.01 * repeat,
                2.99 * repeat, 8.99 * repeat, 4 - REST_TIP, 10 - REST_TIP, 64 - REST_TIP}) {
            var vertices = renderVertices(extension, 0);
            var moving = shaftPoints(vertices, 8, 22);
            require(!moving.isEmpty() && Math.abs(moving.getFirst() - extension) < 1e-5,
                    "Upper shaft follows head");
            require(moving.getLast() >= -repeat - 1e-5 && moving.getLast() <= 1e-5,
                    "Last whole row overlaps inside palm");
            for (int i = 1; i < moving.size(); i++)
                require(Math.abs(moving.get(i - 1) - moving.get(i) - repeat) < 1e-5,
                        "Repeated rows meet without gaps");
            var grip = shaftPoints(vertices, 5, 25);
            require(grip.size() == 1 && Math.abs(grip.getFirst() + 6 * DIAGONAL_PIXEL) < 1e-5,
                    "Grip stays fixed");
            double bladeTip = vertices.stream().filter(v -> v[5] == 0x11 && v[6] == 0x44 && v[7] == 0x77)
                    .mapToDouble(v -> v[2]).max().orElseThrow();
            require(Math.abs(bladeTip - REST_TIP - extension) < 1e-5,
                    "Authored blade tip matches solved tip distance");
            for (double cutoff : new double[]{.2, .35}) for (var vertex : renderVertices(extension, cutoff))
                require(vertex[2] >= cutoff - 1e-6,
                        "Camera cutoff clips moving sections in world coordinates");
        }
        for (double[] pair : new double[][]{{8.2, 8.45}, {8.99, 9.01}, {9.01, 8.99}}) {
            var before = shaftPoints(renderVertices(pair[0] * repeat, 0), 8, 22);
            var after = shaftPoints(renderVertices(pair[1] * repeat, 0), 8, 22);
            for (int i = 0; i < 5; i++)
                require(Math.abs(after.get(i) - before.get(i) - (pair[1] - pair[0]) * repeat) < 1e-5,
                        "Shaft texture slides through repeat boundaries in both directions");
        }
        System.out.println("GlaiveNativeVisualFixture: shaft motion, palm overlap, blade bounds and clipping PASS");
    }

    private static List<float[]> renderVertices(double extension, double cutoff) {
        var buffer = new RecordingBuffer();
        GlaiveRenderer.renderBody(new Pose(Vec3.ZERO, 0, 0, REST_TIP + extension),
                new EquipmentAppearance(0x114477, 0x77AA44, 0), new PoseStack(),
                type -> buffer, ClientLightCompat.FULL_BRIGHT, cutoff);
        return buffer.vertices;
    }

    private static List<Double> shaftPoints(List<float[]> vertices, int pixelX, int pixelY) {
        var points = new TreeSet<Double>(Comparator.reverseOrder());
        for (var v : vertices) if (v[5] == 255 && v[6] == 255 && v[7] == 255
                && Math.abs(v[3] - (pixelX + .5) / 32) < 1e-6
                && Math.abs(v[4] - (pixelY + .5) / 32) < 1e-6
                && Math.abs(v[0] + 2 * DIAGONAL_PIXEL) < 1e-6
                && Math.abs(v[1] + THICKNESS / 2) < 1e-6) points.add((double) v[2]);
        return new ArrayList<>(points);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class RecordingBuffer implements VertexConsumer {
        private final List<float[]> vertices = new ArrayList<>();
        @Override public VertexConsumer addVertex(float x, float y, float z) {
            vertices.add(new float[]{x, y, z, 0, 0, 0, 0, 0}); return this;
        }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) {
            var v = vertices.getLast(); v[5] = r; v[6] = g; v[7] = b; return this;
        }
        @Override public VertexConsumer setColor(int argb) {
            return setColor(argb >>> 16 & 255, argb >>> 8 & 255, argb & 255, argb >>> 24 & 255);
        }
        @Override public VertexConsumer setUv(float u, float v) {
            var p = vertices.getLast(); p[3] = u; p[4] = v; return this;
        }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { return this; }
        @Override public VertexConsumer setLineWidth(float width) { return this; }
    }
}
