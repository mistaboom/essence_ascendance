package com.mistaboom.essence_ascendance.attunement;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Immutable generated calibration. Contains no registries, player objects or loader APIs. */
public record AttunementProfile(Policy policy, Map<String, Chapter> chapters,
                               Map<String, Method> methods, Map<String, Double> references,
                               List<String> assumptions) {
    public AttunementProfile {
        Objects.requireNonNull(policy, "Missing Attunement policy; explicitly rebuild generated balance");
        chapters = immutable(chapters); methods = immutable(methods); references = immutable(references);
        assumptions = List.copyOf(assumptions);
        references.forEach((key, value) -> positive(key, value));
        methods.forEach((key, method) -> {
            if (!key.equals(method.activityId())) throw new IllegalArgumentException("Attunement method key/id mismatch");
        });
        var registeredMethods = methods;
        chapters.forEach((key, chapter) -> {
            if (!key.equals(chapter.fromTierId())) throw new IllegalArgumentException("Attunement chapter key/from-tier mismatch");
            if (!chapter.activities().keySet().equals(registeredMethods.keySet()))
                throw new IllegalArgumentException("Incomplete generated Attunement activity rates");
            for (var category : chapter.categories().values()) {
                if (registeredMethods.values().stream().filter(method -> method.categoryId().equals(category.categoryId())).count() > 128)
                    throw new IllegalArgumentException("Attunement category exceeds supported synchronized method count");
                long baseMethods = registeredMethods.values().stream().filter(method -> method.categoryId().equals(category.categoryId())
                        && method.baseGameAccessible()).count();
                if (baseMethods < 2) throw new IllegalArgumentException("Attunement category requires multiple unrestricted base methods: " + category.categoryId());
            }
            chapter.activities().forEach((id, rate) -> {
                Method method = registeredMethods.get(id);
                if (!id.equals(rate.activityId()) || !method.categoryId().equals(rate.categoryId())
                        || !chapter.categories().containsKey(rate.categoryId()))
                    throw new IllegalArgumentException("Attunement rate/category registration mismatch: " + id);
            });
        });
    }

    /** Null only for a completed maximum tier or an unregistered tier. */
    public Chapter chapter(String fromTierId) { return chapters.get(fromTierId); }

    public record Policy(double maximumAcceleration, double repetitionFloor, double varietyStrength,
                         int historyWindow) {
        public Policy {
            range("maximum acceleration", maximumAcceleration, 0, 4);
            range("repetition floor", repetitionFloor, .01, 1);
            range("variety strength", varietyStrength, 0, 1);
            if (historyWindow < 8 || historyWindow > 256) throw new IllegalArgumentException("Attunement history window must be 8..256");
        }
    }
    public record Chapter(String id, String fromTierId, String toTierId, int requiredCategories,
                          Map<String, Category> categories, Map<String, Rate> activities) {
        public Chapter {
            identifier(id, 128); identifier(fromTierId, 128); identifier(toTierId, 128);
            categories = immutable(categories); activities = immutable(activities);
            if (categories.size() > 128) throw new IllegalArgumentException("Attunement exceeds supported synchronized category count");
            if (requiredCategories < 1 || requiredCategories > Math.max(1, categories.size() - 1))
                throw new IllegalArgumentException("Invalid Attunement category breadth");
            categories.forEach((key, category) -> {
                if (!key.equals(category.categoryId())) throw new IllegalArgumentException("Attunement category key/id mismatch");
            });
        }
    }
    public record Category(String categoryId, long target, long investmentReference) {
        public Category {
            identifier(categoryId, 128);
            if (target < 1 || target > 1_000_000_000_000L || investmentReference < 1 || investmentReference > Long.MAX_VALUE / 1024)
                throw new IllegalArgumentException("Attunement target/reference outside safe accounting bounds");
        }
    }
    public record Rate(String activityId, String categoryId, double contributionPerUnit,
                       double referenceUnits, String units, String evidence, boolean stageAccessible) {
        public Rate(String activityId, String categoryId, double contributionPerUnit, double referenceUnits, String units, String evidence) {
            this(activityId, categoryId, contributionPerUnit, referenceUnits, units, evidence, true);
        }
        public Rate {
            identifier(activityId, 128); identifier(categoryId, 128); text(units); text(evidence);
            positive("contribution rate", contributionPerUnit); positive("calibration reference", referenceUnits);
        }
    }
    public record Method(String activityId, String categoryId, String units, String calibrationFamily,
                         String labelKey, String descriptionKey, boolean baseGameAccessible) {
        public Method {
            identifier(activityId, 128); identifier(categoryId, 128); text(units); text(calibrationFamily); identifier(labelKey, 256); identifier(descriptionKey, 256);
        }
    }
    /** One shared breadth curve, including registries smaller or larger than the current six. */
    public static int requiredCategories(int categoryCount, int transitionPosition, int transitionCount, double breadthExponent) {
        if (categoryCount < 1 || transitionCount < 1) throw new IllegalArgumentException("Attunement needs categories and transitions");
        range("breadth exponent", breadthExponent, .25, 4);
        int ordinal = Math.clamp(transitionPosition, 1, transitionCount);
        int maximum = Math.max(1, categoryCount - 1);
        // Integer ceiling avoids an exact rational boundary rounding above its integer.
        // Products of two positive ints remain within signed long.
        if (breadthExponent == 1) {
            long numerator = (long) maximum * ordinal;
            return (int) Math.max(1, 1 + (numerator - 1) / transitionCount);
        }
        double position = ordinal / (double) transitionCount;
        return Math.clamp((int) Math.ceil(maximum * Math.pow(position, breadthExponent)), 1, maximum);
    }
    private static void positive(String key, double value) { range(key, value, Double.MIN_NORMAL, 1e18); }
    private static void range(String key, double value, double min, double max) {
        if (!Double.isFinite(value) || value < min || value > max)
            throw new IllegalArgumentException("Attunement " + key + " outside " + min + ".." + max);
    }
    private static void text(String value) { if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing Attunement identifier/text"); }
    private static void identifier(String value, int limit) { text(value); if (value.length() > limit) throw new IllegalArgumentException("Attunement identifier exceeds synchronized length limit " + limit); }
    private static <T> Map<String, T> immutable(Map<String, T> values) { return Collections.unmodifiableMap(new TreeMap<>(Objects.requireNonNull(values))); }
}
