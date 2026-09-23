package com.mistaboom.essence_ascendance.nexus;

import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.network.TransientVisualDispatch;
import com.mistaboom.essence_ascendance.visual.transientfx.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** One confirmed transition, reconstructed locally by every nearby observer. */
public final class AscendanceTierFeedback {
    private AscendanceTierFeedback() { }

    public static void play(ServerPlayer player, boolean awakening) {
        var tier = EssenceSavedData.get(player.server).getTier(player.getUUID()).id();
        SemanticVisualColor color = switch (tier.getPath()) {
            case "dormant" -> SemanticVisualColor.DORMANT;
            case "awakened" -> SemanticVisualColor.AWAKENED;
            case "resonant" -> SemanticVisualColor.RESONANT;
            case "ascendant" -> SemanticVisualColor.ASCENDANT;
            case "transcendent" -> SemanticVisualColor.TRANSCENDENT;
            default -> SemanticVisualColor.LATENT;
        };
        var importance = awakening ? VisualIntensity.MAJOR : VisualIntensity.SIGNATURE;
        var event = new WorldVisualEvent(TransientVisualIds.WORLD_ASCENDANCE_CEREMONY,
                player.level().dimension().location(), player.position(), player.getId(),
                WorldVisualEvent.NO_ENTITY, null, new Vec3(0, 1, 0),
                awakening ? 0.55F : 1.0F, awakening ? 0.7F : 1.0F, importance, color,
                awakening ? 76 : 160, player.level().getGameTime() ^ player.getUUID().getLeastSignificantBits(),
                awakening ? 1 : 0, 0);
        TransientVisualDispatch.nearby(player.serverLevel(), event, importance.worldRange());
    }
}
