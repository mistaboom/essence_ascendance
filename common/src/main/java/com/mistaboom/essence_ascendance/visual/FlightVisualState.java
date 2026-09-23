package com.mistaboom.essence_ascendance.visual;

import com.mistaboom.essence_ascendance.movement.FlightAbilityState;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime;

/** Public presentation bits only; native entity tracking sends changes and initial observer state. */
public interface FlightVisualState {
    int FATIGUE = 1;
    int WINGS = 2;
    int THRUST = 4;
    int BOOST_TICKS = 8;

    int essenceAscendance$flightFlags();
    void essenceAscendance$flightFlags(int flags);
    long essenceAscendance$boostTick();
    void essenceAscendance$boostTick(long tick);

    static void synchronize(SkillEffectRuntime.Context context) {
        var player = context.player();
        int flags = 0;
        if (player.isAlive() && !player.isRemoved() && !player.isSpectator()) {
            if (context.isEffective(SkillIds.FATIGUE_FLIGHT)) {
                flags |= FATIGUE;
                FlightAbilityState state = context.existingState(SkillIds.FATIGUE_FLIGHT);
                if (state != null && state.thrusting()) flags |= THRUST;
            }
            if (context.isEffective(SkillIds.ESSENCE_WINGS)) flags |= WINGS;
        }
        ((FlightVisualState) player).essenceAscendance$flightFlags(flags);
    }
}
