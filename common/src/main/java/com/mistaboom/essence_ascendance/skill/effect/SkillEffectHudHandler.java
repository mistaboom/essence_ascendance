package com.mistaboom.essence_ascendance.skill.effect;

import java.util.List;
import java.util.Objects;

/**
 * Standard single-card skill presentation. Return an inactive card when the
 * reward is empty, spent or expired; the shared presentation retains its last
 * active appearance for the normal closing delay. The runtime alone omits an
 * ineffective skill. Gameplay state and timers must never be prolonged for HUD use.
 *
 * Use SkillEffectHudCards for the established timed/progress layouts. A future
 * multi-card skill can implement SkillEffectHandler.hudEntries with stable card
 * IDs and the same active/default contract; it needs no new renderer or packet.
 */
public interface SkillEffectHudHandler extends SkillEffectHandler {
    SkillEffectHudEntry hudEntry(SkillEffectRuntime.Context context);

    @Override
    default List<SkillEffectHudEntry> hudEntries(SkillEffectRuntime.Context context) {
        return List.of(Objects.requireNonNull(hudEntry(context), "Effective HUD skills must emit their inactive card"));
    }
}
