package com.mistaboom.essence_ascendance.visual;

/** Shared phase and easing math for world effects and GUI constellation geometry. */
public final class ProceduralMotion {
    private ProceduralMotion() { }

    public static double oscillate(double phase, double amplitude) {
        return Math.sin(phase) * amplitude;
    }

    public static double phase(double age, double speed) {
        double value = age * speed;
        return value - Math.floor(value);
    }

    public static double orbitAngle(int index, int count, double rotation) {
        return rotation + Math.PI * 2.0 * index / count;
    }

    public static double smoothStep(double fraction) {
        double t = Math.clamp(fraction, 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    /** Linear arrival/departure envelope; rates are inverse fractions of the effect duration. */
    public static double fadeEnvelope(double age, double fadeInRate, double fadeOutRate) {
        return Math.min(1, age * fadeInRate) * Math.min(1, (1 - age) * fadeOutRate);
    }
}
