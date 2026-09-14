package com.mistaboom.essence_ascendance.guard;

import com.mistaboom.essence_ascendance.equipment.EquipmentDamageService;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.effect.GuardMobilityController;
import com.mistaboom.essence_ascendance.skill.effect.SafeCreatureAreaService;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime;
import net.minecraft.server.level.ServerPlayer;
import java.util.ArrayList;
import java.util.List;

/** Bounded on-demand diagnostics through the existing shield command. No per-tick log output. */
public final class GuardDiagnostics {
    private GuardDiagnostics() { }
    public static List<String> lines(ServerPlayer player) {
        List<String> lines = new ArrayList<>(GuardMobilityController.diagnostics(player));
        lines.add("Continuous guard: " + GuardLifecycle.observe(player));
        EquipmentDamageService.lastGuardOutcome(player).ifPresent(hit -> {
            lines.add("Guard event=" + hit.eventId() + "; tick=" + hit.tick() + "; source=" + hit.source()
                    + "; direct=" + hit.direct() + "; responsible=" + hit.responsible() + "; source decision=" + hit.sourceDecision());
            lines.add("Incoming=" + hit.incoming() + "; blocked=" + hit.blocked() + "; measured mitigation=" + hit.mitigated()
                    + "; health/absorption loss=" + hit.healthLost() + "/" + hit.absorptionLost()
                    + "; native accepted=" + hit.accepted() + "; successful/perfect=" + hit.successfulBlock() + "/" + hit.perfect());
            lines.add("Knockback attempt=" + hit.attemptedKnockback() + "; accepted delta=" + hit.acceptedKnockback()
                    + "; result=" + hit.knockbackDecision() + "; echo=" + hit.echo());
            lines.add("Reflection ordinary=" + hit.ordinaryReflection() + "; block native/invested=" + hit.nativeBlockReflection()
                    + "/" + hit.investedBlockReflection() + "; Ward extension=" + hit.wardExtension()
                    + "; skill multiplier=" + hit.skillAmplifier() + "; requested/confirmed=" + hit.requestedReflection()
                    + "/" + hit.confirmedReflection() + "; depth=" + hit.recursionDepth() + "; result=" + hit.reflectionDecision());
        });
        for (var skill : List.of(SkillIds.REFLEXIVE_WARD, SkillIds.STORED_FORCE, SkillIds.GUARD_AMPLIFIER,
                SkillIds.CROWD_REPRISAL, SkillIds.RIPOSTE)) {
            lines.add("Guard skill " + skill.getPath());
            lines.addAll(SkillEffectRuntime.debugLines(player, skill));
        }
        lines.addAll(ReflectionRouter.diagnostics(player));
        lines.addAll(SafeCreatureAreaService.diagnostics(player, SkillIds.CROWD_REPRISAL));
        return List.copyOf(lines);
    }
}
