package com.mistaboom.essence_ascendance.client.transientfx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mistaboom.essence_ascendance.client.ClientPacketDispatch;
import com.mistaboom.essence_ascendance.client.procedural.ProceduralRenderTypes;
import com.mistaboom.essence_ascendance.network.WorldVisualEventPayload;
import com.mistaboom.essence_ascendance.visual.transientfx.TransientVisualIds;
import com.mistaboom.essence_ascendance.visual.transientfx.WorldVisualEvent;
import dev.architectury.networking.NetworkManager;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Connection/world-scoped lifecycle, culling, budgeting and rendering for one-shot world cues. */
public final class TransientWorldVisuals {
    private static final int ACTIVE_BUDGET = 256;
    private static final int DETAIL_BUDGET = 64;
    private static final List<Active> ACTIVE = new ArrayList<>();
    private static boolean initialized;

    private TransientWorldVisuals() { }

    public static void init() {
        if (initialized) return;
        NetworkManager.registerReceiver(NetworkManager.Side.S2C,
                WorldVisualEventPayload.TYPE, WorldVisualEventPayload.CODEC,
                (payload, context) -> ClientPacketDispatch.queue(context, () -> accept(payload.event())));
        initialized = true;
    }

    public static void clear() { ACTIVE.clear(); }

    /** Local entry point is useful for client-predicted acknowledgements and visual test harnesses. */
    public static void emit(WorldVisualEvent event) { accept(event); }

    private static void accept(WorldVisualEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || !level.dimension().location().equals(event.dimension())
                || WorldVisualRecipes.get(event.recipeId()) == null) return;
        if (event.recipeId().equals(TransientVisualIds.WORLD_RIPOSTE_RELEASE)) {
            ACTIVE.removeIf(active -> active.level() == level
                    && active.event().recipeId().equals(TransientVisualIds.WORLD_GUARD_RESPONSE)
                    && active.event().sourceEntityId() == event.sourceEntityId());
            // A zero-parameter release is the server's early invalidation notice.
            if (event.parameterA() <= 0.5F) return;
        } else if (event.recipeId().equals(TransientVisualIds.WORLD_GUARD_RESPONSE)) {
            // Re-arming replaces the earlier ring so its lifetime matches the refreshed charge.
            ACTIVE.removeIf(active -> active.level() == level
                    && active.event().recipeId().equals(TransientVisualIds.WORLD_GUARD_RESPONSE)
                    && active.event().sourceEntityId() == event.sourceEntityId());
        }
        int repeatInterval = WorldVisualRecipes.get(event.recipeId()).repeatIntervalTicks();
        if (repeatInterval > 0 && event.targetEntityId() != WorldVisualEvent.NO_ENTITY
                && ACTIVE.stream().anyMatch(active -> active.level() == level
                && active.event().recipeId().equals(event.recipeId())
                && active.event().targetEntityId() == event.targetEntityId()
                && level.getGameTime() - active.startedTick() < repeatInterval)) return;
        int incoming = event.presentation().budgetCost();
        while (budgetUsed() + incoming > ACTIVE_BUDGET) {
            Active victim = ACTIVE.stream()
                    .filter(active -> active.event().presentation().ordinal() <= event.presentation().ordinal())
                    .min(Comparator.comparingInt(active -> active.event().presentation().ordinal()))
                    .orElse(null);
            if (victim == null) return;
            ACTIVE.remove(victim);
        }
        ACTIVE.add(new Active(event, level, level.getGameTime()));
    }

    private static int budgetUsed() {
        return ACTIVE.stream().mapToInt(active -> active.event().presentation().budgetCost()).sum();
    }

    public static void render(Camera camera, Matrix4f positionMatrix, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) { clear(); return; }
        ACTIVE.removeIf(active -> active.level() != level
                || !active.event().dimension().equals(level.dimension().location())
                || progress(active, level, partialTick) >= 1.0);
        if (ACTIVE.isEmpty()) return;

        Vec3 cameraPosition = camera.getPosition();
        List<Active> visible = ACTIVE.stream()
                .filter(active -> active.event().position().distanceToSqr(cameraPosition)
                        <= square(active.event().presentation().worldRange()))
                .sorted(Comparator.comparingInt((Active active) -> active.event().presentation().ordinal()).reversed()
                        .thenComparingDouble(active -> active.event().position().distanceToSqr(cameraPosition)))
                .toList();
        if (visible.isEmpty()) return;

        PoseStack pose = new PoseStack();
        pose.mulPose(positionMatrix);
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        Map<Active, Boolean> detailed = new IdentityHashMap<>();
        int detailCost = 0;
        for (Active active : visible) {
            boolean allow = active.event().presentation().ordinal() > 0
                    && active.event().position().distanceToSqr(cameraPosition)
                    <= square(active.event().presentation().detailRange())
                    && detailCost + active.event().presentation().budgetCost() <= DETAIL_BUDGET;
            detailed.put(active, allow);
            if (allow) detailCost += active.event().presentation().budgetCost();
        }
        // Far-to-near submission is preferable for the shared translucent passes.
        visible = visible.stream().sorted(Comparator.comparingDouble(
                (Active active) -> active.event().position().distanceToSqr(cameraPosition)).reversed()).toList();

        // The fallback BufferSource builder is shared by these custom render types. Finish every plane
        // submission before acquiring the line consumer; acquiring another type finalizes the prior builder.
        var planes = buffers.getBuffer(ProceduralRenderTypes.WORLD_PLANES);
        for (Active active : visible) {
            WorldVisualRecipe recipe = WorldVisualRecipes.get(active.event().recipeId());
            if (recipe == null) continue;
            WorldVisualRenderContext context = new WorldVisualRenderContext(active.event(), level,
                    pose, planes, planes, cameraPosition, partialTick, progress(active, level, partialTick));
            recipe.renderPrimaryPlanes(context);
            if (detailed.getOrDefault(active, false)) recipe.renderDetailPlanes(context);
        }
        buffers.endBatch(ProceduralRenderTypes.WORLD_PLANES);

        var lines = buffers.getBuffer(ProceduralRenderTypes.WORLD_DEPTH_LINES);
        for (Active active : visible) {
            WorldVisualRecipe recipe = WorldVisualRecipes.get(active.event().recipeId());
            if (recipe == null) continue;
            WorldVisualRenderContext context = new WorldVisualRenderContext(active.event(), level,
                    pose, lines, lines, cameraPosition, partialTick, progress(active, level, partialTick));
            recipe.renderPrimaryLines(context);
            if (detailed.getOrDefault(active, false)) recipe.renderDetailLines(context);
        }
        buffers.endBatch(ProceduralRenderTypes.WORLD_DEPTH_LINES);
    }

    private static double progress(Active active, ClientLevel level, float partialTick) {
        return Math.max(0.0, (level.getGameTime() - active.startedTick() + partialTick)
                / active.event().lifetimeTicks());
    }

    private static double square(double value) { return value * value; }

    private record Active(WorldVisualEvent event, ClientLevel level, long startedTick) { }
}
