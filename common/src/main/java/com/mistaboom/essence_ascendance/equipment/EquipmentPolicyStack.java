package com.mistaboom.essence_ascendance.equipment;

import net.minecraft.server.level.ServerPlayer;

/** Stack-local revision and bounded hand-owner notification. No inventory/player search. */
public interface EquipmentPolicyStack {
    long essenceAscendance$equipmentRevision();
    void essenceAscendance$watchHand(ServerPlayer player);
}
