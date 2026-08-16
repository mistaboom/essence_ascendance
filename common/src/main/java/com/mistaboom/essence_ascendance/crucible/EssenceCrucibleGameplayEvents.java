package com.mistaboom.essence_ascendance.crucible;

/*
 * Reserved common Crucible gameplay-event hook.
 *
 * The first Crucible tranche used this class to block breaking whenever a
 * block-owned reservoir contained Essence. Reservoir storage is now owned by
 * PlayerEssenceData, so breaking a Crucible no longer needs validation and no
 * break event is registered here.
 */
public final class EssenceCrucibleGameplayEvents {

    private static boolean initialized = false;

    private EssenceCrucibleGameplayEvents() {
    }

    public static void init() {
        if (initialized) {
            return;
        }

        initialized = true;
    }
}
