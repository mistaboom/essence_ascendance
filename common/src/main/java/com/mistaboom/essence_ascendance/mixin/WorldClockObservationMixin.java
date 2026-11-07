package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.gameplay.WorldClockObservations;
import net.minecraft.server.level.ServerLevel;
//? if >=26.1.2 {
/*import net.minecraft.world.clock.ServerClockManager;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Shadow;*/
//? }
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if >=26.1.2
/*@Mixin(ServerClockManager.class)*/
//? if <26.1.2
@Mixin(ServerLevel.class)
abstract class WorldClockObservationMixin {
    //? if >=26.1.2
    /*@Shadow private MinecraftServer server;*/
    @Unique private long essenceAscendance$previousClock;
    //? if >=26.1.2
    /*@Inject(method = "modifyClock", at = @At("HEAD"))*/
    //? if <26.1.2
    @Inject(method = "setDayTime", at = @At("HEAD"))
    private void essenceAscendance$beforeClock(CallbackInfo ci) {
        //? if >=26.1.2
        /*if (server != null && server.overworld() != null) essenceAscendance$previousClock = server.overworld().getDefaultClockTime();*/
        //? if <26.1.2
        essenceAscendance$previousClock = ((ServerLevel)(Object)this).getDayTime();
    }
    //? if >=26.1.2
    /*@Inject(method = "modifyClock", at = @At("RETURN"))*/
    //? if <26.1.2
    @Inject(method = "setDayTime", at = @At("RETURN"))
    private void essenceAscendance$afterClock(CallbackInfo ci) {
        //? if >=26.1.2
        /*if (server != null && server.overworld() != null) WorldClockObservations.changed(server, essenceAscendance$previousClock, server.overworld().getDefaultClockTime());*/
        //? if <26.1.2 {
        {
            var level = (ServerLevel)(Object)this;
            if (level == level.getServer().overworld()) WorldClockObservations.changed(level.getServer(), essenceAscendance$previousClock, level.getDayTime());
        }
        //? }
    }
}
