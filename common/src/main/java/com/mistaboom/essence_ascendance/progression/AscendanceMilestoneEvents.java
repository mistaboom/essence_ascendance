package com.mistaboom.essence_ascendance.progression;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.EntityEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.damagesource.DamageSource;

public final class AscendanceMilestoneEvents {

    private AscendanceMilestoneEvents() {
    }


    public static void init() {

        EntityEvent.LIVING_DEATH.register(
                AscendanceMilestoneEvents::onLivingDeath
        );
    }


    private static EventResult onLivingDeath(
            LivingEntity entity,
            DamageSource source
    ) {

        /*
         * DamageSource#getEntity() represents the entity responsible
         * for the damage, including the shooter for ordinary
         * player-fired projectile damage.
         *
         * We only award the built-in kill milestones when a
         * ServerPlayer is credited as the causing entity.
         */

        if (!(source.getEntity()
                instanceof ServerPlayer player)) {

            return EventResult.pass();
        }


        if (entity instanceof WitherBoss) {

            completeInternalMilestone(
                    player,
                    Milestones.DEFEAT_WITHER
            );


        } else if (entity instanceof Warden) {

            completeInternalMilestone(
                    player,
                    Milestones.DEFEAT_WARDEN
            );
        }


        return EventResult.pass();
    }


    private static void completeInternalMilestone(
            ServerPlayer player,
            MilestoneDefinition milestone
    ) {

        ResourceLocation target =
                ResourceLocation.tryParse(
                        milestone.target()
                );


        if (target == null) {

            EssenceAscendance.LOGGER.error(
                    "Built-in internal milestone {} has invalid target '{}'",
                    milestone.id(),
                    milestone.target()
            );

            return;
        }


        boolean newlyCompleted =
                EssenceSavedData
                        .get(player.server)
                        .completeInternalMilestone(
                                player.getUUID(),
                                target
                        );


        if (newlyCompleted) {

            EssenceAscendance.LOGGER.info(
                    "Player {} completed internal milestone {}",
                    player.getGameProfile().getName(),
                    target
            );
        }
    }
}