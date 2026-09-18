package com.mistaboom.essence_ascendance.movement;

/** Pure native-boundary rules, shared by prediction/gameplay and executable without a game world.
 * One is the identity factor/full normalized efficiency, not a balancing constant. */
public final class TraversalRules {
    private TraversalRules() { }
    public static float withoutPenalty(float nativeFactor) {
        return Float.isFinite(nativeFactor) && nativeFactor >= 0 ? Math.max(1, nativeFactor) : nativeFactor;
    }
    public static boolean surfaceEnabled(boolean granted, boolean nativeMovement, boolean sprinting, boolean sneaking) {
        return granted && nativeMovement && sprinting && !sneaking;
    }
    public static boolean exposedSurface(double height, boolean fluidAboveEmpty) {
        return fluidAboveEmpty && Double.isFinite(height) && height > 0 && height <= 1;
    }
    public static boolean canStepOntoSurface(double feetY, double surfaceY, double nativeStep,
                                              boolean grounded, boolean immersed) {
        double rise = surfaceY - feetY;
        return grounded && !immersed && Double.isFinite(rise) && Double.isFinite(nativeStep)
                && rise > 0 && nativeStep > 0 && rise <= nativeStep;
    }
    public static boolean protectsContact(boolean terrain, boolean lavaProtection, boolean inLava,
                                          boolean terrainSource, boolean immersionSource,
                                          boolean responsibleEntity, boolean unavoidable) {
        return !responsibleEntity && !unavoidable && (terrain && terrainSource
                || lavaProtection && inLava && immersionSource);
    }
}
