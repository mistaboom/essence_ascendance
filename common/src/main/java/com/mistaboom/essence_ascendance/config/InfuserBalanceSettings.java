package com.mistaboom.essence_ascendance.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Global, pre-world Infuser balance values. Carrier density, conversion
 * efficiency, machine throughput, and progression recipe costs are deliberately
 * independent knobs.
 */
public record InfuserBalanceSettings(
        double linkRange,
        int noFocusEfficiencyBasisPoints,
        long noFocusInfusionThroughputPerSecond,
        Map<String, GradeSettings> grades,
        Map<String, FocusUpgradeSettings> focusUpgrades,
        Map<String, EquipmentUpgradeSettings> equipmentUpgrades,
        Map<String, Map<String, Integer>> equipmentEssenceWeights,
        RepairSettings repair,
        int conversionEfficiencyBasisPoints,
        int carrierExtractionEfficiencyBasisPoints
) {
    private static final String[] TIERS = {
            "dormant", "awakened", "resonant", "ascendant", "transcendent"
    };
    private static final String[] EQUIPMENT_KEYS = {
            "helmet", "chestplate", "leggings", "boots", "melee_weapon",
            "ranged_weapon", "magic_caster", "pickaxe", "axe", "shovel", "hoe", "shield"
    };

    public InfuserBalanceSettings {
        new com.mistaboom.essence_ascendance.balance.economy.EconomyProcessingPolicy(
                conversionEfficiencyBasisPoints, carrierExtractionEfficiencyBasisPoints);
        if (!(linkRange > 0.0D) || !Double.isFinite(linkRange)) {
            throw new IllegalArgumentException("Infuser link range must be finite and positive");
        }
        validateEfficiency(noFocusEfficiencyBasisPoints, "No-Focus efficiency");
        if (noFocusInfusionThroughputPerSecond <= 0L) {
            throw new IllegalArgumentException("No-Focus Infuser throughput must be positive");
        }
        Objects.requireNonNull(grades, "Infuser grade settings cannot be null");
        grades = Collections.unmodifiableMap(new LinkedHashMap<>(grades));
        for (String required : TIERS) {
            if (!grades.containsKey(required)) {
                throw new IllegalArgumentException("Missing Infuser grade settings for " + required);
            }
        }
        Objects.requireNonNull(focusUpgrades, "Infuser Focus upgrade settings cannot be null");
        focusUpgrades = Collections.unmodifiableMap(new LinkedHashMap<>(focusUpgrades));
        for (String required : TIERS) {
            if (!focusUpgrades.containsKey(required)) {
                throw new IllegalArgumentException("Missing Infuser Focus upgrade settings for " + required);
            }
        }
        Objects.requireNonNull(equipmentUpgrades, "Infuser equipment upgrade settings cannot be null");
        equipmentUpgrades = Collections.unmodifiableMap(new LinkedHashMap<>(equipmentUpgrades));
        for (String required : TIERS) {
            if (!equipmentUpgrades.containsKey(required)) {
                throw new IllegalArgumentException("Missing equipment upgrade settings for " + required);
            }
        }
        Objects.requireNonNull(equipmentEssenceWeights, "Equipment Essence weights cannot be null");
        Map<String, Map<String, Integer>> copiedWeights = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Integer>> entry : equipmentEssenceWeights.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isEmpty()) {
                throw new IllegalArgumentException("Equipment Essence weights cannot be empty for " + entry.getKey());
            }
            LinkedHashMap<String, Integer> weights = new LinkedHashMap<>();
            for (Map.Entry<String, Integer> weight : entry.getValue().entrySet()) {
                if (weight.getValue() == null || weight.getValue() <= 0) {
                    throw new IllegalArgumentException("Equipment Essence weights must be positive");
                }
                weights.put(weight.getKey(), weight.getValue());
            }
            copiedWeights.put(entry.getKey(), Collections.unmodifiableMap(weights));
        }
        for (String required : EQUIPMENT_KEYS) {
            if (!copiedWeights.containsKey(required)) {
                throw new IllegalArgumentException("Missing equipment Essence weights for " + required);
            }
        }
        equipmentEssenceWeights = Collections.unmodifiableMap(copiedWeights);

        Objects.requireNonNull(repair, "Infuser repair settings cannot be null");
    }

    /** Bootstrap construction; generated runtime copies the canonical economy policy. */
    public InfuserBalanceSettings(double linkRange, int noFocusEfficiencyBasisPoints,
            long noFocusInfusionThroughputPerSecond, Map<String, GradeSettings> grades,
            Map<String, FocusUpgradeSettings> focusUpgrades, Map<String, EquipmentUpgradeSettings> equipmentUpgrades,
            Map<String, Map<String, Integer>> equipmentEssenceWeights, RepairSettings repair) {
        this(linkRange, noFocusEfficiencyBasisPoints, noFocusInfusionThroughputPerSecond, grades, focusUpgrades,
                equipmentUpgrades, equipmentEssenceWeights, repair,
                com.mistaboom.essence_ascendance.balance.economy.EconomyProcessingPolicy.defaults().conversionEfficiencyBasisPoints(),
                com.mistaboom.essence_ascendance.balance.economy.EconomyProcessingPolicy.defaults().carrierExtractionEfficiencyBasisPoints());
    }

    public GradeSettings grade(String serializedGrade) {
        GradeSettings settings = grades.get(serializedGrade);
        if (settings == null) throw new IllegalArgumentException("Unknown Infuser grade " + serializedGrade);
        return settings;
    }

    public FocusUpgradeSettings focusUpgrade(String serializedTargetTier) {
        FocusUpgradeSettings settings = focusUpgrades.get(serializedTargetTier);
        if (settings == null) throw new IllegalArgumentException("Unknown Infuser Focus target " + serializedTargetTier);
        return settings;
    }

    public EquipmentUpgradeSettings equipmentUpgrade(String serializedTargetTier) {
        EquipmentUpgradeSettings settings = equipmentUpgrades.get(serializedTargetTier);
        if (settings == null) throw new IllegalArgumentException("Unknown equipment infusion target " + serializedTargetTier);
        return settings;
    }

    public Map<String, Integer> equipmentWeights(String equipmentKey) {
        Map<String, Integer> weights = equipmentEssenceWeights.get(equipmentKey);
        if (weights == null) throw new IllegalArgumentException("Unknown equipment progression key " + equipmentKey);
        return weights;
    }

    private static void validateEfficiency(int basisPoints, String label) {
        if (basisPoints <= 0 || basisPoints >= 10_000) {
            throw new IllegalArgumentException(label + " must be between 1 and 9999 basis points");
        }
    }

    public record FocusUpgradeSettings(long minimumPerAttributeEssence, long totalEssenceRequired) {
        public FocusUpgradeSettings {
            if (minimumPerAttributeEssence <= 0L || totalEssenceRequired <= 0L) {
                throw new IllegalArgumentException("Focus infusion requirements must be positive");
            }
            long minimumTotal = Math.multiplyExact(minimumPerAttributeEssence, 6L);
            if (totalEssenceRequired < minimumTotal) {
                throw new IllegalArgumentException("Focus infusion total must cover all six Essence minimums");
            }
        }
    }

    public record EquipmentUpgradeSettings(long totalEssenceRequired, int matrixCount) {
        public EquipmentUpgradeSettings {
            if (totalEssenceRequired <= 0L || matrixCount <= 0) {
                throw new IllegalArgumentException("Equipment infusion Essence and Matrix requirements must be positive");
            }
        }
    }

    public record RepairSettings(long essencePerDurability, int fracturedLatentIngotCount) {
        public RepairSettings {
            if (essencePerDurability <= 0L || fracturedLatentIngotCount <= 0) {
                throw new IllegalArgumentException(
                        "Repair Essence-per-durability and Fractured Latent Ingot count must be positive"
                );
            }
        }
    }

    public record GradeSettings(long ingotCapacity, int efficiencyBasisPoints, long infusionThroughputPerSecond) {
        public GradeSettings {
            if (ingotCapacity <= 0L) throw new IllegalArgumentException("Essentium ingot capacity must be positive");
            if (ingotCapacity % 9L != 0L) {
                throw new IllegalArgumentException(
                        "Essentium ingot capacity must be divisible by 9 for exact nugget conversion"
                );
            }
            validateEfficiency(efficiencyBasisPoints, "Infuser efficiency");
            if (infusionThroughputPerSecond <= 0L) throw new IllegalArgumentException("Infuser throughput must be positive");
        }
    }

    /** Semantic category distribution; generated economy supplies every final cost. */
    public static Map<String, Map<String, Integer>> defaultEquipmentEssenceWeights() {
        Map<String, Map<String, Integer>> equipmentWeights = new LinkedHashMap<>();
        equipmentWeights.put("helmet", weights("defense", 5, "utility", 4, "vitality", 1));
        equipmentWeights.put("chestplate", weights("defense", 6, "vitality", 4));
        equipmentWeights.put("leggings", weights("defense", 4, "mobility", 3, "vitality", 3));
        equipmentWeights.put("boots", weights("mobility", 6, "defense", 3, "utility", 1));
        equipmentWeights.put("melee_weapon", weights("offense", 7, "mobility", 3));
        equipmentWeights.put("ranged_weapon", weights("offense", 5, "mobility", 3, "utility", 2));
        equipmentWeights.put("magic_caster", weights("utility", 5, "offense", 4, "vitality", 1));
        equipmentWeights.put("pickaxe", weights("gathering", 7, "utility", 3));
        equipmentWeights.put("axe", weights("gathering", 6, "offense", 4));
        equipmentWeights.put("shovel", weights("gathering", 6, "mobility", 4));
        equipmentWeights.put("hoe", weights("gathering", 7, "utility", 3));
        equipmentWeights.put("shield", weights("defense", 5, "offense", 3, "mobility", 2));

        return Collections.unmodifiableMap(equipmentWeights);
    }

    private static Map<String, Integer> weights(Object... values) {
        LinkedHashMap<String, Integer> result = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            result.put((String) values[i], (Integer) values[i + 1]);
        }
        return Collections.unmodifiableMap(result);
    }
}
