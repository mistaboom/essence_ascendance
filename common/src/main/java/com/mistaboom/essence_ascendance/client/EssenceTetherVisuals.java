package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.client.procedural.ProceduralGeometry;
import com.mistaboom.essence_ascendance.client.procedural.ProceduralRenderTypes;
import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleBlockEntity;
import com.mistaboom.essence_ascendance.infuser.EssenceInfuserBlockEntity;
import com.mistaboom.essence_ascendance.pylon.EssencePylonBlock;
import com.mistaboom.essence_ascendance.pylon.EssencePylonBlockEntity;
import com.mistaboom.essence_ascendance.pylon.PylonLocalFrame;
import com.mistaboom.essence_ascendance.visual.MachineVisualState;
import com.mistaboom.essence_ascendance.visual.ProceduralMotion;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Shared persistent world-space Essence link. Machine adapters only select
 * authoritative endpoints, activity, rate, direction, and semantic color;
 * this class owns the single beam/pulse/detail composition.
 */
public final class EssenceTetherVisuals {
    public static final int VIEW_DISTANCE = 72;

    private static final double DETAIL_DISTANCE_SQUARED = 20.0 * 20.0;
    private static final double VIEW_DISTANCE_SQUARED = VIEW_DISTANCE * VIEW_DISTANCE;
    private static final double TAU = Math.PI * 2.0;
    private static final Vec3 WORLD_UP = new Vec3(0, 1, 0);
    private static final Vec3 WORLD_EAST = new Vec3(1, 0, 0);

    private EssenceTetherVisuals() { }

    /** Flow direction is expressed relative to the supplied start and end. */
    public enum Flow { FORWARD, REVERSE, BOTH }

    /** Optional fixed plane for a small endpoint diamond. Its normal faces the link axis. */
    public record EndpointFrame(Vec3 right, Vec3 up) { }

    /** Reusable local-space stream description for any real synchronized link. */
    public record Stream(Vec3 start, Vec3 end, int rgb, boolean active,
                         long ratePerSecond, Flow flow, double phaseSeed,
                         EndpointFrame startFrame, EndpointFrame endFrame) {
        public Stream {
            ratePerSecond = Math.max(0L, ratePerSecond);
        }
    }

    public static void renderPylon(EssencePylonBlockEntity pylon, float partialTick,
                                   PoseStack pose, MultiBufferSource buffers) {
        ClientLevel level = clientLevel(pylon);
        if (level == null) return;

        MachineVisualState.Pylon state = pylon.visualState();
        BlockPos cruciblePos = state.linkedCrucible();
        if (cruciblePos == null || !state.focus().active()) return;

        MachineVisualState.Crucible crucibleState = level.getBlockEntity(cruciblePos)
                instanceof EssenceCrucibleBlockEntity crucible
                ? crucible.visualState() : MachineVisualState.IDLE_CRUCIBLE;
        if (!crucibleState.needsPylonSupport()) return;
        PylonLocalFrame pylonFrame = PylonLocalFrame.of(
                pylon.getBlockState().getValue(EssencePylonBlock.FACING));
        Vec3 start = PylonVisuals.tetherAnchor(pylon.getBlockPos(), state, pylonFrame);
        Vec3 end = blockOffset(pylon.getBlockPos(), cruciblePos)
                .add(CrucibleVisuals.tetherAnchor(cruciblePos,
                        Vec3.atLowerCornerOf(pylon.getBlockPos()).add(start)));
        int rgb = FocusVisuals.color(state.focus().tier());
        Vec3 senderCenter = pylonFrame.localToBlock(new Vec3(0.5, 1.05, 0.5));
        Vec3 senderRadial = start.subtract(senderCenter);
        EndpointFrame startFrame = senderRadial.lengthSqr() < 1.0E-6
                ? new EndpointFrame(pylonFrame.right(), pylonFrame.axis())
                : new EndpointFrame(senderRadial.normalize().cross(pylonFrame.axis()),
                        pylonFrame.axis());
        Vec3 crucibleLocalEnd = end.subtract(blockOffset(pylon.getBlockPos(), cruciblePos));
        EndpointFrame endFrame = axialFrame(
                crucibleLocalEnd.subtract(new Vec3(0.5, crucibleLocalEnd.y, 0.5)));

        double age = level.getGameTime() + partialTick;
        Stream stream = new Stream(start, end, rgb, true, state.focus().ratePerSecond(),
                Flow.FORWARD, positionPhase(pylon.getBlockPos()), startFrame, endFrame);
        Vec3 focus = FocusVisuals.center(FocusVisuals.Context.installed(
                state.focus(), state.linked(), pylonFrame.direction()), age);
        renderContinuation(pylon.getBlockPos(), age, pose, buffers, stream, focus, start);
        render(pylon.getBlockPos(), age, pose, buffers, stream);

        // The receiver redirects this same feed down into the dissolution bowl.
        // Keep both spans straight and reuse the stream composition without
        // drawing a duplicate receiver or eight stacked diamonds at the hub.
        Vec3 convergence = blockOffset(pylon.getBlockPos(), cruciblePos)
                .add(CrucibleVisuals.TETHER_CONVERGENCE);
        renderContinuation(pylon.getBlockPos(), age, pose, buffers, stream, end, convergence);
    }

    public static void renderInfuser(EssenceInfuserBlockEntity infuser, float partialTick,
                                     PoseStack pose, MultiBufferSource buffers) {
        ClientLevel level = clientLevel(infuser);
        if (level == null) return;

        MachineVisualState.Infuser state = infuser.visualState();
        BlockPos cruciblePos = state.linkedCrucible();
        if (!state.transferring()) return;

        Vec3 end = InfuserVisuals.tetherAnchor(infuser.getBlockPos(), state);
        Vec3 start = blockOffset(infuser.getBlockPos(), cruciblePos)
                .add(CrucibleVisuals.tetherAnchor(cruciblePos,
                        Vec3.atLowerCornerOf(infuser.getBlockPos()).add(end)));
        // Outgoing Essence has the same identity as the player stream, independent
        // of the receiving Infuser's installed Focus tier.
        MachineVisualState.Crucible crucibleState = level.getBlockEntity(cruciblePos)
                instanceof EssenceCrucibleBlockEntity crucible
                ? crucible.visualState() : MachineVisualState.IDLE_CRUCIBLE;
        int rgb = CrucibleVisuals.streamColor(crucibleState);
        Vec3 crucibleLocalStart = start.subtract(blockOffset(infuser.getBlockPos(), cruciblePos));
        EndpointFrame axial = axialFrame(crucibleLocalStart.subtract(
                new Vec3(0.5, crucibleLocalStart.y, 0.5)));

        double age = level.getGameTime() + partialTick;
        Stream stream = new Stream(start, end, rgb, true, state.throughputPerSecond(),
                Flow.FORWARD, positionPhase(infuser.getBlockPos()), axial, axial);
        Vec3 convergence = blockOffset(infuser.getBlockPos(), cruciblePos)
                .add(CrucibleVisuals.TETHER_CONVERGENCE);
        Vec3 focus = FocusVisuals.center(FocusVisuals.Context.installed(state.focus(), state.linked()), age);
        renderContinuation(infuser.getBlockPos(), age, pose, buffers, stream, convergence, start);
        render(infuser.getBlockPos(), age, pose, buffers, stream);
        renderContinuation(infuser.getBlockPos(), age, pose, buffers, stream, end, focus);
    }

    public static void renderPlayerChannel(EssenceCrucibleBlockEntity crucible, float partialTick,
                                           PoseStack pose, MultiBufferSource buffers) {
        ClientLevel level = clientLevel(crucible);
        if (level == null) return;

        MachineVisualState.Crucible state = crucible.visualState();
        if (state.channelingPlayer() == null) return;
        Player player = level.getPlayerByUUID(state.channelingPlayer());
        if (player == null || !player.isAlive()) return;

        // Entity interpolation keeps the observer and first-person endpoint free
        // of block/integer-position jitter. The lower torso leaves the incoming
        // line visible beneath the local camera instead of terminating at the eye.
        Vec3 body = player.getPosition(partialTick)
                .add(0, player.getBbHeight() * 0.58, 0);
        Vec3 end = body.subtract(Vec3.atLowerCornerOf(crucible.getBlockPos()));
        Vec3 start = CrucibleVisuals.tetherAnchor(crucible.getBlockPos(), body);
        Vec3 playerRadial = new Vec3(end.x - 0.5, 0, end.z - 0.5);
        EndpointFrame axial = axialFrame(playerRadial);

        double age = level.getGameTime() + partialTick;
        Stream stream = new Stream(start, end, CrucibleVisuals.streamColor(state), true,
                state.transferRatePerSecond(), Flow.FORWARD,
                uuidPhase(player.getUUID().getMostSignificantBits()
                        ^ player.getUUID().getLeastSignificantBits()), axial, axial);
        renderContinuation(crucible.getBlockPos(), age, pose, buffers, stream,
                CrucibleVisuals.TETHER_CONVERGENCE, start);
        render(crucible.getBlockPos(), age, pose, buffers, stream);
    }

    /** Same full beam appearance and flow as its main span, without duplicate endpoint fittings. */
    private static void renderContinuation(BlockPos origin, double age, PoseStack pose,
                                           MultiBufferSource buffers, Stream main, Vec3 start, Vec3 end) {
        render(origin, age, pose, buffers,
                new Stream(start, end, main.rgb(), main.active(), main.ratePerSecond(),
                        main.flow(), main.phaseSeed(), null, null), false);
    }

    /** Draws one perfectly straight, depth-tested stream in the caller's local pose. */
    public static void render(BlockPos origin, double age, PoseStack pose,
                              MultiBufferSource buffers, Stream stream) {
        render(origin, age, pose, buffers, stream, true);
    }

    /** Continuations share every stream layer but need no extra endpoint fittings. */
    private static void render(BlockPos origin, double age, PoseStack pose,
                               MultiBufferSource buffers, Stream stream, boolean endpointDetails) {
        Vec3 direction = stream.end().subtract(stream.start());
        double length = direction.length();
        if (length < 0.08) return;

        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition()
                .subtract(Vec3.atLowerCornerOf(origin));
        double distanceSquared = distanceToSegmentSquared(camera, stream.start(), stream.end());
        if (distanceSquared > VIEW_DISTANCE_SQUARED) return;

        Vec3 along = direction.scale(1.0 / length);
        Vec3 lateral = cameraFacingLateral(along, camera.subtract(stream.start()));
        Vec3 binormal = along.cross(lateral).normalize();
        boolean close = distanceSquared <= DETAIL_DISTANCE_SQUARED;
        double rate = normalizedRate(stream.ratePerSecond());
        int core = towardWhite(stream.rgb(), 0.70);
        int edge = towardWhite(stream.rgb(), 0.34);

        // Major silhouette: fixed widths keep configured throughput from turning
        // the relationship into an oversized laser. Rate belongs to pulse cadence.
        VertexConsumer planes = buffers.getBuffer(ProceduralRenderTypes.WORLD_PLANES);
        renderPrimaryPlanes(pose, planes, stream, lateral, binormal, core);
        renderMajorPulses(pose, planes, stream, age, lateral, binormal, rate, edge, core);
        if (close && endpointDetails) {
            renderEndpointPlanes(pose, planes, stream, lateral, binormal, edge, core);
        }

        VertexConsumer lines = buffers.getBuffer(ProceduralRenderTypes.WORLD_DEPTH_LINES);
        renderPrimaryLines(pose, lines, stream, lateral, binormal, edge, core);
        if (close) {
            renderCloseDetail(pose, lines, stream, direction, lateral, binormal,
                    age, rate, edge, core, endpointDetails);
        }
    }

    private static void renderPrimaryPlanes(PoseStack pose, VertexConsumer planes, Stream stream,
                                            Vec3 lateral, Vec3 binormal, int core) {
        float outerAlpha = stream.active() ? 0.20F : 0.105F;
        float coreAlpha = stream.active() ? 0.70F : 0.36F;
        ProceduralGeometry.beam(pose, planes, stream.start(), stream.end(), lateral,
                0.040, stream.rgb(), outerAlpha);
        ProceduralGeometry.beam(pose, planes, stream.start(), stream.end(), binormal,
                0.031, stream.rgb(), outerAlpha * 0.72F);
        ProceduralGeometry.beam(pose, planes, stream.start(), stream.end(), lateral,
                0.012, core, coreAlpha);
        ProceduralGeometry.beam(pose, planes, stream.start(), stream.end(), binormal,
                0.008, core, coreAlpha * 0.82F);
    }

    private static void renderMajorPulses(PoseStack pose, VertexConsumer planes, Stream stream,
                                          double age, Vec3 right, Vec3 up, double rate,
                                          int edge, int core) {
        if (!stream.active()) return;
        int count = 1 + (int) Math.floor(rate * 3.0);
        double speed = 0.026 + rate * 0.044;
        for (int index = 0; index < count; index++) {
            double progress = ProceduralMotion.phase(
                    age + stream.phaseSeed() * 17.0 + index / (double) count / speed, speed);
            progress = directedProgress(progress, stream.flow(), index);
            Vec3 center = stream.start().lerp(stream.end(), progress);
            double scale = 0.86 + 0.14 * Math.sin(progress * Math.PI);
            ProceduralGeometry.diamondRing(pose, planes, center, right, up,
                    0.074 * scale, 0.108 * scale, 0.043 * scale, 0.063 * scale,
                    stream.rgb(), 0.58F);
            ProceduralGeometry.diamond(pose, planes, center, right, up,
                    0.043 * scale, 0.063 * scale, core, 0.90F);
            // The crossed facet keeps the Nexus-derived pulse volumetric when
            // the first diamond turns edge-on to the link.
            ProceduralGeometry.diamond(pose, planes, center, up, right,
                    0.026 * scale, 0.039 * scale, edge, 0.42F);
        }
    }

    private static void renderPrimaryLines(PoseStack pose, VertexConsumer lines, Stream stream,
                                           Vec3 lateral, Vec3 binormal, int edge, int core) {
        float alpha = stream.active() ? 0.42F : 0.22F;
        ProceduralGeometry.line(pose, lines,
                stream.start().add(lateral.scale(0.043)), stream.end().add(lateral.scale(0.043)),
                edge, alpha);
        ProceduralGeometry.line(pose, lines,
                stream.start().subtract(lateral.scale(0.043)), stream.end().subtract(lateral.scale(0.043)),
                edge, alpha);
        ProceduralGeometry.line(pose, lines,
                stream.start().add(binormal.scale(0.033)), stream.end().add(binormal.scale(0.033)),
                stream.rgb(), alpha * 0.68F);
        ProceduralGeometry.line(pose, lines, stream.start(), stream.end(), core,
                stream.active() ? 0.78F : 0.43F);
    }

    private static void renderEndpointPlanes(PoseStack pose, VertexConsumer planes, Stream stream,
                                             Vec3 right, Vec3 up, int edge, int core) {
        Vec3[] endpoints = {stream.start(), stream.end()};
        EndpointFrame[] frames = {stream.startFrame(), stream.endFrame()};
        for (int index = 0; index < endpoints.length; index++) {
            Vec3 endpoint = endpoints[index];
            Vec3 endpointRight = frames[index] == null ? right : frames[index].right().normalize();
            Vec3 endpointUp = frames[index] == null ? up : frames[index].up().normalize();
            ProceduralGeometry.diamondRing(pose, planes, endpoint, endpointRight, endpointUp,
                    0.086, 0.118, 0.054, 0.073, stream.rgb(),
                    stream.active() ? 0.38F : 0.22F);
            ProceduralGeometry.diamond(pose, planes, endpoint, endpointRight, endpointUp,
                    0.030, 0.043, core, stream.active() ? 0.67F : 0.40F);
        }
    }

    private static void renderCloseDetail(PoseStack pose, VertexConsumer lines, Stream stream,
                                          Vec3 direction, Vec3 lateral, Vec3 binormal,
                                          double age, double rate, int edge, int core,
                                          boolean endpointDetails) {
        double phase = age * (0.018 + rate * 0.028) + stream.phaseSeed();
        Vec3 helixA = lateral.scale(Math.cos(phase)).add(binormal.scale(Math.sin(phase)));
        Vec3 helixB = binormal.scale(Math.cos(phase)).subtract(lateral.scale(Math.sin(phase)));
        Vec3 detailStart = stream.start().lerp(stream.end(), 0.035);
        Vec3 detailDirection = direction.scale(0.93);
        float faint = stream.active() ? 0.25F : 0.13F;

        ProceduralGeometry.helix(pose, lines, detailStart, detailDirection,
                helixA, helixB, 0.055, 1.6 + rate * 1.4, 28,
                stream.rgb(), faint);
        ProceduralGeometry.helix(pose, lines, detailStart, detailDirection,
                helixA.scale(-1), helixB, 0.030, -(1.1 + rate), 22,
                edge, faint * 0.58F);

        int tinyCount = stream.active() ? 2 + (int) Math.floor(rate * 3.0) : 1;
        double tinySpeed = stream.active() ? 0.038 + rate * 0.052 : 0.010;
        for (int index = 0; index < tinyCount; index++) {
            double progress = ProceduralMotion.phase(
                    age + stream.phaseSeed() * 9.0 + index / (double) tinyCount / tinySpeed,
                    tinySpeed);
            progress = directedProgress(progress, stream.flow(), index);
            Vec3 pulse = stream.start().lerp(stream.end(), progress);
            Vec3 wake = stream.start().lerp(stream.end(), Math.max(0.0, progress - 0.045));
            ProceduralGeometry.line(pose, lines, wake, pulse, core, faint * 1.15F);
            ProceduralGeometry.line(pose, lines,
                    pulse.subtract(lateral.scale(0.025)), pulse.add(lateral.scale(0.025)),
                    edge, faint * 0.78F);
            ProceduralGeometry.line(pose, lines,
                    pulse.subtract(binormal.scale(0.018)), pulse.add(binormal.scale(0.018)),
                    stream.rgb(), faint * 0.60F);
        }

        // Fine endpoint radial ticks and nested registration linework remain
        // subordinate to the beam and disappear with the close-detail LOD.
        if (!endpointDetails) return;
        Vec3[] endpoints = {stream.start(), stream.end()};
        EndpointFrame[] frames = {stream.startFrame(), stream.endFrame()};
        for (int endpointIndex = 0; endpointIndex < endpoints.length; endpointIndex++) {
            Vec3 endpoint = endpoints[endpointIndex];
            Vec3 endpointRight = frames[endpointIndex] == null
                    ? lateral : frames[endpointIndex].right().normalize();
            Vec3 endpointUp = frames[endpointIndex] == null
                    ? binormal : frames[endpointIndex].up().normalize();
            for (int tick = 0; tick < 6; tick++) {
                double angle = TAU * tick / 6.0 + phase * 0.20;
                Vec3 radial = endpointRight.scale(Math.cos(angle))
                        .add(endpointUp.scale(Math.sin(angle)));
                ProceduralGeometry.line(pose, lines,
                        endpoint.add(radial.scale(0.090)), endpoint.add(radial.scale(0.125)),
                        tick % 2 == 0 ? edge : stream.rgb(), faint * 0.84F);
            }
        }
    }

    private static double directedProgress(double progress, Flow flow, int index) {
        return switch (flow) {
            case FORWARD -> progress;
            case REVERSE -> 1.0 - progress;
            case BOTH -> (index & 1) == 0 ? progress : 1.0 - progress;
        };
    }

    private static Vec3 cameraFacingLateral(Vec3 along, Vec3 towardCamera) {
        Vec3 lateral = along.cross(towardCamera);
        if (lateral.lengthSqr() < 1.0E-6) lateral = along.cross(WORLD_UP);
        if (lateral.lengthSqr() < 1.0E-6) lateral = along.cross(WORLD_EAST);
        return lateral.normalize();
    }

    private static EndpointFrame axialFrame(Vec3 radial) {
        Vec3 horizontal = new Vec3(radial.x, 0, radial.z);
        if (horizontal.lengthSqr() < 1.0E-6) horizontal = WORLD_EAST;
        horizontal = horizontal.normalize();
        return new EndpointFrame(new Vec3(-horizontal.z, 0, horizontal.x), WORLD_UP);
    }

    private static double distanceToSegmentSquared(Vec3 point, Vec3 start, Vec3 end) {
        Vec3 segment = end.subtract(start);
        double lengthSquared = segment.lengthSqr();
        if (lengthSquared <= 1.0E-8) return point.distanceToSqr(start);
        double t = Math.clamp(point.subtract(start).dot(segment) / lengthSquared, 0.0, 1.0);
        return point.distanceToSqr(start.add(segment.scale(t)));
    }

    private static double normalizedRate(long rate) {
        return rate <= 0L ? 0.0 : Math.min(1.0, Math.log1p(rate) / 12.0);
    }

    private static Vec3 blockOffset(BlockPos origin, BlockPos target) {
        return new Vec3(target.getX() - origin.getX(), target.getY() - origin.getY(),
                target.getZ() - origin.getZ());
    }

    private static ClientLevel clientLevel(BlockEntity entity) {
        return entity.getLevel() instanceof ClientLevel level ? level : null;
    }

    private static double positionPhase(BlockPos pos) {
        return uuidPhase(pos.asLong() * 0x9E3779B97F4A7C15L);
    }

    private static double uuidPhase(long hash) {
        return ((hash >>> 24) & 0xFFFFL) / 65535.0 * TAU;
    }

    private static int towardWhite(int rgb, double amount) {
        int r = (int) Math.round((rgb >> 16 & 255) * (1.0 - amount) + 255 * amount);
        int g = (int) Math.round((rgb >> 8 & 255) * (1.0 - amount) + 255 * amount);
        int b = (int) Math.round((rgb & 255) * (1.0 - amount) + 255 * amount);
        return r << 16 | g << 8 | b;
    }
}
