package com.mistaboom.essence_ascendance.progression;

import net.minecraft.server.level.ServerPlayer;

@FunctionalInterface
public interface MilestoneProvider {

    MilestoneCheckResult evaluate(
            ServerPlayer player,
            MilestoneDefinition milestone
    );
}