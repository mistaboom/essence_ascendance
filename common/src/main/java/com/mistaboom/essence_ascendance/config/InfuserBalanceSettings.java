package com.mistaboom.essence_ascendance.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Global, pre-world Infuser balance values. The temporary defaults are kept
 * here instead of being scattered through machine code so modpacks can tune
 * conversion without a datapack.
 */
public record InfuserBalanceSettings(
        double linkRange,
        int noFocusEfficiencyBasisPoints,
        int noFocusProcessingTicks,
        Map<String, GradeSettings> grades
) {
    public InfuserBalanceSettings {
        if (!(linkRange > 0.0D) || !Double.isFinite(linkRange)) {
            throw new IllegalArgumentException("Infuser link range must be finite and positive");
        }
        validateEfficiency(noFocusEfficiencyBasisPoints, "No-Focus efficiency");
        if (noFocusProcessingTicks <= 0) {
            throw new IllegalArgumentException("No-Focus processing ticks must be positive");
        }
        Objects.requireNonNull(grades, "Infuser grade settings cannot be null");
        grades = Collections.unmodifiableMap(new LinkedHashMap<>(grades));
        for (String required : new String[]{
                "dormant", "awakened", "resonant", "ascendant", "transcendent"
        }) {
            if (!grades.containsKey(required)) {
                throw new IllegalArgumentException("Missing Infuser grade settings for " + required);
            }
        }
    }

    public GradeSettings grade(String serializedGrade) {
        GradeSettings settings = grades.get(serializedGrade);
        if (settings == null) {
            throw new IllegalArgumentException("Unknown Infuser grade " + serializedGrade);
        }
        return settings;
    }

    private static void validateEfficiency(int basisPoints, String label) {
        if (basisPoints <= 0 || basisPoints >= 10_000) {
            throw new IllegalArgumentException(label + " must be between 1 and 9999 basis points");
        }
    }

    public record GradeSettings(
            long ingotCapacity,
            int efficiencyBasisPoints,
            int processingTicks
    ) {
        public GradeSettings {
            if (ingotCapacity <= 0L) {
                throw new IllegalArgumentException("Essentium ingot capacity must be positive");
            }
            validateEfficiency(efficiencyBasisPoints, "Infuser efficiency");
            if (processingTicks <= 0) {
                throw new IllegalArgumentException("Infuser processing ticks must be positive");
            }
        }
    }

    public static InfuserBalanceSettings defaults() {
        Map<String, GradeSettings> grades = new LinkedHashMap<>();
        grades.put("dormant", new GradeSettings(100_000L, 5_500, 160));
        grades.put("awakened", new GradeSettings(250_000L, 6_200, 130));
        grades.put("resonant", new GradeSettings(500_000L, 7_000, 100));
        grades.put("ascendant", new GradeSettings(1_000_000L, 8_000, 80));
        grades.put("transcendent", new GradeSettings(2_000_000L, 9_000, 60));
        return new InfuserBalanceSettings(8.0D, 5_000, 200, grades);
    }
}
