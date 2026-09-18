package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.movement.TraversalService;
import net.minecraft.client.Minecraft;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.material.FluidState;

/** Main-thread committed permission snapshot for asynchronous fluid mesh builders.
 * Geometry never queries the mutable player/skill cache from a render worker. Inward faces
 * are removed whenever the ability is effective; ordinary back-face culling still shows the
 * exterior. Entering/leaving lava therefore needs no mesh rebuild, shader swap or world edit. */
public final class TraversalFluidRenderState {
    private static volatile boolean clearLavaInteriors;
    private static boolean rebuildPending;

    private TraversalFluidRenderState() { }

    /** Called by the existing local-player tick hook, on the client thread only. */
    public static void refresh() {
        Minecraft minecraft = Minecraft.getInstance();
        boolean next = minecraft.level != null && TraversalService.clearLavaVision(minecraft.player);
        if (next != clearLavaInteriors) {
            clearLavaInteriors = next;
            rebuildPending = true;
        }
        if (rebuildPending && minecraft.level != null) {
            rebuildPending = false;
            // Native invalidation discards old/in-flight section meshes. Only learning, losing
            // or otherwise changing effective vision permission causes this, not each swim.
            minecraft.levelRenderer.allChanged();
        }
    }

    /** Connection reset: revoke worker access immediately and invalidate any surviving meshes
     * on the next local tick, including early reconnects where the renderer was not unloaded. */
    public static void clear() {
        if (clearLavaInteriors) {
            clearLavaInteriors = false;
            rebuildPending = true;
        }
    }

    public static boolean singleSided(FluidState fluid) {
        return clearLavaInteriors && fluid.is(FluidTags.LAVA);
    }
}
