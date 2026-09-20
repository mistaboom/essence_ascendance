package com.mistaboom.essence_ascendance.movement;

import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime;
import net.minecraft.server.level.ServerPlayer;

/** Server-authoritative input fan-out for every ordinary-Jump flight skill. */
public final class FlightAbilityService {
    private FlightAbilityService() { }

    public static void input(ServerPlayer player, MovementAbilityInput input) {
        if (!EssenceConfigManager.authoritativeReady() || !FlightAbilityRules.inputAllowed(player)) return;
        var context = SkillEffectRuntime.context(player);
        int timeout = context.settings().posture().movement().intentTimeoutTicks();
        accept(context, SkillIds.ESSENCE_WINGS, input, timeout);
        accept(context, SkillIds.FATIGUE_FLIGHT, input, timeout);
        accept(context, SkillIds.VECTOR_BOOST, input, timeout);
        accept(context, SkillIds.UNTETHERED_FLIGHT, input, timeout);
    }

    private static void accept(SkillEffectRuntime.Context context, net.minecraft.resources.ResourceLocation id,
                               MovementAbilityInput input, int timeout) {
        if (context.isEffective(id)) context.state(id, FlightAbilityState::new).input(context.now(), input, timeout);
    }
}
