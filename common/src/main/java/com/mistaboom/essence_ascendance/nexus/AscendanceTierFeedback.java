package com.mistaboom.essence_ascendance.nexus;

import com.mistaboom.essence_ascendance.network.CombatVisualFeedback;
import com.mistaboom.essence_ascendance.visual.transientfx.SemanticVisualColor;
import com.mistaboom.essence_ascendance.visual.transientfx.TransientVisualIds;
import com.mistaboom.essence_ascendance.visual.transientfx.VisualIntensity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** World acknowledgment hosted by the shared procedural runtime; the Nexus ritual owns its full presentation. */
public final class AscendanceTierFeedback {
    private AscendanceTierFeedback() { }

    public static void play(ServerPlayer player) {
        CombatVisualFeedback.at(player.serverLevel(), TransientVisualIds.WORLD_UTILITY_ACKNOWLEDGE,
                player.getBoundingBox().getCenter(), new Vec3(0, 1, 0), 1.8F, 1.0F,
                VisualIntensity.MAJOR, SemanticVisualColor.UTILITY, 26,
                player.level().getGameTime() ^ player.getId(), 0, 0);
    }
}
