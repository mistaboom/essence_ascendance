package com.mistaboom.essence_ascendance.equipment;

/*
 * Loader mixins add this tiny bridge directly to MobEffectInstance.
 *
 * Minecraft exposes duration reads publicly but not an ordinary public setter.
 * Status Resistance needs to shorten the authoritative active instance, so the
 * loader mixin writes the real private duration field and this common interface
 * keeps the gameplay service loader-agnostic.
 */
public interface MobEffectDurationAccess {

    void essenceAscendance$setDurationTicks(int durationTicks);
}
