package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.visual.FlightVisualState;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No loadout or private meters are broadcast, and unchanged values do not dirty metadata. */
@Mixin(Player.class)
public abstract class FlightVisualStateMixin implements FlightVisualState {
    @Unique private static final EntityDataAccessor<Byte> essenceAscendance$flight =
            SynchedEntityData.defineId(Player.class, EntityDataSerializers.BYTE);
    @Unique private static final EntityDataAccessor<Long> essenceAscendance$boost =
            SynchedEntityData.defineId(Player.class, EntityDataSerializers.LONG);

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void essenceAscendance$defineFlight(SynchedEntityData.Builder builder, CallbackInfo ci) {
        builder.define(essenceAscendance$flight, (byte) 0);
        builder.define(essenceAscendance$boost, Long.MIN_VALUE);
    }

    @Override public int essenceAscendance$flightFlags() {
        return ((Player) (Object) this).getEntityData().get(essenceAscendance$flight);
    }
    @Override public void essenceAscendance$flightFlags(int flags) {
        ((Player) (Object) this).getEntityData().set(essenceAscendance$flight, (byte) flags);
    }
    @Override public long essenceAscendance$boostTick() {
        return ((Player) (Object) this).getEntityData().get(essenceAscendance$boost);
    }
    @Override public void essenceAscendance$boostTick(long tick) {
        ((Player) (Object) this).getEntityData().set(essenceAscendance$boost, tick);
    }
}
