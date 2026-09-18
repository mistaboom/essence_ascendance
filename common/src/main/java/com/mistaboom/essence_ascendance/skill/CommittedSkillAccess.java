package com.mistaboom.essence_ascendance.skill;

import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.Objects;
import java.util.function.BiPredicate;

/** Shared permission boundary for native gameplay hooks that also need local movement prediction.
 * The client supplies only its existing synchronized committed snapshot; drafts never grant gameplay.
 * No client classes, extra packets or second skill cache are loaded on a dedicated server. */
public final class CommittedSkillAccess {
    private static BiPredicate<Player, ResourceLocation> clientPrediction = (player, skill) -> false;
    private CommittedSkillAccess() { }

    public static void installClientPrediction(BiPredicate<Player, ResourceLocation> prediction) {
        clientPrediction = Objects.requireNonNull(prediction);
    }

    public static boolean isEffective(Entity entity, ResourceLocation skill) {
        if (!(entity instanceof Player player) || !player.isAlive() || player.isRemoved() || player.isSpectator()) return false;
        if (player.level().isClientSide) return EssenceConfigManager.clientRuntime() != null
                && clientPrediction.test(player, skill);
        return player instanceof ServerPlayer server && EssenceConfigManager.authoritativeReady()
                && CommittedSkillService.isEffective(server, skill);
    }
}
