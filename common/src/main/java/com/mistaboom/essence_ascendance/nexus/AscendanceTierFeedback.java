package com.mistaboom.essence_ascendance.nexus;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Intentionally simple placeholder for successful tier-Ascension feedback.
 * Replace this class when the final Nexus ritual/animation exists.
 */
public final class AscendanceTierFeedback {

    private AscendanceTierFeedback() {
    }

    public static void play(ServerPlayer player) {
        ServerLevel level = player.serverLevel();

        level.sendParticles(
                ParticleTypes.END_ROD,
                player.getX(),
                player.getY() + 1.0,
                player.getZ(),
                42,
                0.65,
                1.0,
                0.65,
                0.08
        );

        level.sendParticles(
                ParticleTypes.ENCHANT,
                player.getX(),
                player.getY() + 1.0,
                player.getZ(),
                30,
                0.8,
                1.1,
                0.8,
                0.12
        );
    }
}
