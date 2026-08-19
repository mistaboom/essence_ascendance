package com.mistaboom.essence_ascendance.nexus;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Intentionally tiny placeholder for successful Nexus allocation feedback.
 * Replace this class when the final Essence-transfer ritual/animation exists.
 */
public final class AscendanceAllocationFeedback {

    private AscendanceAllocationFeedback() {
    }

    public static void play(ServerPlayer player) {
        ServerLevel level = player.serverLevel();

        level.sendParticles(
                ParticleTypes.END_ROD,
                player.getX(),
                player.getY() + 1.0,
                player.getZ(),
                24,
                0.45,
                0.70,
                0.45,
                0.05
        );

        level.sendParticles(
                ParticleTypes.END_ROD,
                player.getX(),
                player.getY() + 1.0,
                player.getZ(),
                6,
                0.30,
                0.55,
                0.30,
                0.02
        );
    }
}
