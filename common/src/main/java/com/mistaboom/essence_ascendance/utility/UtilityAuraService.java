package com.mistaboom.essence_ascendance.utility;

import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToDoubleFunction;

/** Shared server-authoritative proximity query for Utility aura/domain skills. */
public final class UtilityAuraService {
    private UtilityAuraService() { }

    public record Match(ServerPlayer player, SkillEffectRuntime.Context context,
                        double radiusBlocks, double strength, double distanceSquared) { }

    public static List<Match> matches(ServerLevel level, Vec3 point, ResourceLocation skill,
                                      ToDoubleFunction<SkillEffectRuntime.Context> radius) {
        return matches(level, point, skill, radius, ignored -> 1.0);
    }

    public static List<Match> matches(ServerLevel level, Vec3 point, ResourceLocation skill,
                                      ToDoubleFunction<SkillEffectRuntime.Context> radius,
                                      ToDoubleFunction<SkillEffectRuntime.Context> strength) {
        List<Match> result = new ArrayList<>();
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (player.serverLevel() != level || !player.isAlive() || player.isRemoved() || player.isSpectator()) continue;
            SkillEffectRuntime.Context context = SkillEffectRuntime.context(player);
            if (!context.isEffective(skill)) continue;

            double resolvedRadius = radius.applyAsDouble(context);
            double resolvedStrength = strength.applyAsDouble(context);
            if (!Double.isFinite(resolvedRadius) || resolvedRadius <= 0
                    || !Double.isFinite(resolvedStrength) || resolvedStrength <= 0) continue;
            double distanceSquared = player.position().distanceToSqr(point);
            if (distanceSquared <= resolvedRadius * resolvedRadius) {
                result.add(new Match(player, context, resolvedRadius, resolvedStrength, distanceSquared));
            }
        }
        result.sort(Comparator.comparingDouble(Match::distanceSquared)
                .thenComparing(match -> match.player().getUUID().toString()));
        return List.copyOf(result);
    }

    public static Match strongest(ServerLevel level, Vec3 point, ResourceLocation skill,
                                  ToDoubleFunction<SkillEffectRuntime.Context> radius,
                                  ToDoubleFunction<SkillEffectRuntime.Context> strength) {
        return matches(level, point, skill, radius, strength).stream()
                .min(Comparator.<Match>comparingDouble(Match::strength).reversed()
                        .thenComparingDouble(Match::distanceSquared)
                        .thenComparing(match -> match.player().getUUID().toString()))
                .orElse(null);
    }
}
