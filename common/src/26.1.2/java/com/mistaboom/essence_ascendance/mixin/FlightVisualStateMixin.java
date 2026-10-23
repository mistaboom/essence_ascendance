package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.visual.FlightVisualState;
import com.mistaboom.essence_ascendance.visual.EntityPresentationBridge2612;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;

/** Public flight effects use a loader-owned tracking attachment in 26.1.2. */
@Mixin(Player.class)
public abstract class FlightVisualStateMixin implements FlightVisualState {
    private Player essenceAscendance$self() { return (Player)(Object)this; }
    @Override public int essenceAscendance$flightFlags() {
        return EntityPresentationBridge2612.get(essenceAscendance$self()).flightFlags();
    }
    @Override public void essenceAscendance$flightFlags(int flags) {
        var player = essenceAscendance$self();
        EntityPresentationBridge2612.set(player, EntityPresentationBridge2612.get(player).withFlight(flags));
    }
    @Override public long essenceAscendance$boostTick() {
        return EntityPresentationBridge2612.get(essenceAscendance$self()).boostTick();
    }
    @Override public void essenceAscendance$boostTick(long tick) {
        var player = essenceAscendance$self();
        EntityPresentationBridge2612.set(player, EntityPresentationBridge2612.get(player).withBoost(tick));
    }
}
