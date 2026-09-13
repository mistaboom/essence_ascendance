package com.mistaboom.essence_ascendance.attunement;

import java.util.List;

/** Screen-neutral server-owned presentation state. Exact policy stays in diagnostics. */
public record AttunementSnapshot(String chapterId, int requiredCategories, int completedCategories,
                                 boolean maximumTier, List<Category> categories) {
    public AttunementSnapshot {
        categories = List.copyOf(categories);
        if (chapterId == null || chapterId.length() > 128 || categories.size() > 128
                || requiredCategories < 0 || requiredCategories > categories.size()
                || completedCategories != categories.stream().filter(Category::completed).count()
                || categories.stream().map(Category::categoryId).distinct().count() != categories.size()
                || (maximumTier && completedCategories != categories.size()))
            throw new IllegalArgumentException("Invalid Attunement constellation snapshot");
    }
    public boolean ready() { return !maximumTier && requiredCategories > 0 && completedCategories >= requiredCategories; }
    public static AttunementSnapshot empty() { return new AttunementSnapshot("", 0, 0, false, List.of()); }
    public record Category(String categoryId, long progress, long target, double investmentMultiplier,
                           List<Method> methods, List<AttunementContribution> recent) {
        public Category {
            methods = List.copyOf(methods); recent = List.copyOf(recent);
            if (categoryId == null || categoryId.isBlank() || categoryId.length() > 128
                    || progress < 0 || progress > AttunementLedger.SCALE || target < 0 || target > 1_000_000_000_000L
                    || !Double.isFinite(investmentMultiplier) || investmentMultiplier < 1 || investmentMultiplier > 5
                    || methods.size() > 128 || recent.size() > 32
                    || methods.stream().map(Method::activityId).distinct().count() != methods.size())
                throw new IllegalArgumentException("Invalid Attunement category snapshot");
        }
        public int percent() { return (int) Math.min(100, progress * 100 / AttunementLedger.SCALE); }
        public boolean completed() { return progress >= AttunementLedger.SCALE; }
    }
    public record Method(String activityId, String labelKey, String descriptionKey, long progress, boolean available) {
        public Method {
            if (activityId == null || activityId.isBlank() || activityId.length() > 128
                    || labelKey == null || labelKey.length() > 256 || descriptionKey == null || descriptionKey.length() > 256
                    || progress < 0 || progress > AttunementLedger.SCALE)
                throw new IllegalArgumentException("Invalid Attunement method snapshot");
        }
        public int percent() { return (int) Math.min(100, progress * 100 / AttunementLedger.SCALE); }
    }
}
