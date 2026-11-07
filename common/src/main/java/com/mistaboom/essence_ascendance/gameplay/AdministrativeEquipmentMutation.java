package com.mistaboom.essence_ascendance.gameplay;

import com.mistaboom.essence_ascendance.attunement.AttunementGameplay;
import com.mistaboom.essence_ascendance.equipment.TwoHandedEquipmentService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import java.util.Collection;
import java.util.List;

/** Synchronous native command scope; final equipment becomes a non-gameplay baseline. */
public final class AdministrativeEquipmentMutation implements AutoCloseable {
    private final List<ServerPlayer> players;
    private final AttunementGameplay.OutcomeSuppression suppression;
    private boolean closed;

    private AdministrativeEquipmentMutation(Collection<? extends Entity> entities) {
        players = entities.stream().filter(ServerPlayer.class::isInstance).map(ServerPlayer.class::cast)
                .filter(p -> p.server.isSameThread() && p.server.getPlayerList().getPlayer(p.getUUID()) == p)
                .distinct().toList();
        suppression = AttunementGameplay.suppressAdministrativeOutcomes();
    }

    public static AdministrativeEquipmentMutation begin(Collection<? extends Entity> entities) {
        return new AdministrativeEquipmentMutation(entities);
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        try {
            for (ServerPlayer player : players) {
                TwoHandedEquipmentService.refresh(player);
                // Publish while eligibility remains suppressed, including commands whose mutation
                // did not pass through a watched slot setter. No award-service dependency is needed.
                EquipmentObservations.refresh(player);
            }
        } finally { suppression.close(); }
    }
}
