package com.mistaboom.essence_ascendance.balance.engine;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mistaboom.essence_ascendance.balance.generated.BalancePerformance;
import com.mistaboom.essence_ascendance.valuation.GenerationDataSnapshot;

import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

/** One operation owns its provider probes, counts and timings; no server state is retained globally. */
public final class GenerationProviders {
    private final GenerationDataSnapshot inputs;
    private final java.util.function.Function<String, String> installedVersions;
    private final Map<String, Entry> entries = new TreeMap<>();
    public GenerationProviders(GenerationDataSnapshot inputs) { this(inputs, inputs::installedVersion); }
    GenerationProviders(GenerationDataSnapshot inputs, java.util.function.Function<String, String> installedVersions) {
        this.inputs = inputs; this.installedVersions = installedVersions;
    }
    public static boolean disabled(String id, com.mistaboom.essence_ascendance.balance.config.BalanceOverrides overrides) {
        return overrides.facts().stream().filter(fact -> fact.kind() == com.mistaboom.essence_ascendance.balance.config.BalanceOverrides.SubjectKind.PROVIDER
                        && fact.selector().equals(id) && fact.flag("disabled").isPresent())
                .sorted(java.util.Comparator.comparingInt(com.mistaboom.essence_ascendance.balance.config.BalanceOverrides.FactOverride::priority)
                        .reversed().thenComparing(com.mistaboom.essence_ascendance.balance.config.BalanceOverrides.FactOverride::id))
                .findFirst().map(fact -> fact.flag("disabled").orElse(false)).orElse(false);
    }

    public boolean prepare(String family, GenerationProvider provider, boolean disabled) {
        String key = family + "/" + provider.id();
        if (entries.containsKey(key)) throw new IllegalStateException("Provider probed twice: " + key);
        Entry entry = new Entry(provider.id(), family, provider.requiredForGeneration());
        entries.put(key, entry);
        long start = System.nanoTime();
        try (var phase = BalancePerformance.phase("provider_readiness/" + key)) {
            if (disabled) entry.readiness = new ProviderReadiness(ProviderReadiness.Status.DISABLED, "unknown", "Disabled by factual override");
            else {
                Map<String, String> dependencies = new TreeMap<>();
                provider.dependencyModIds().forEach(id -> dependencies.put(id, installedVersions.apply(id)));
                entry.dependencies = dependencies;
                entry.readiness = dependencies.containsValue(null)
                        ? new ProviderReadiness(ProviderReadiness.Status.ABSENT, "unknown", "Missing optional dependencies: "
                            + dependencies.entrySet().stream().filter(row -> row.getValue() == null).map(Map.Entry::getKey).toList())
                        : java.util.Objects.requireNonNull(provider.readiness(inputs), "Provider returned null readiness");
            }
        } catch (RuntimeException | LinkageError failure) {
            entry.readiness = new ProviderReadiness(ProviderReadiness.Status.FAILED, "unknown", describe(failure));
            // Probe failures have no emitted evidence; an explicitly advisory adapter can be skipped safely.
        } finally { entry.probeNanos = System.nanoTime() - start; }
        log(entry);
        entry.readiness.requireSafe(key, entry.required);
        return entry.readiness.collectable();
    }

    public <T> T run(String family, GenerationProvider provider, String hook, Supplier<T> action) {
        Entry entry = java.util.Objects.requireNonNull(entries.get(family + "/" + provider.id()), "Probe provider first");
        if (!entry.readiness.collectable()) throw new IllegalStateException("Unavailable provider cannot emit evidence");
        if (!entry.hooks.add(hook)) throw new IllegalStateException("Provider hook invoked twice: " + provider.id() + "/" + hook);
        long start = System.nanoTime();
        try (var phase = BalancePerformance.phase("provider/" + family + "/" + provider.id() + "/" + hook)) {
            T result = java.util.Objects.requireNonNull(action.get(), "Provider returned null evidence");
            entry.completedHooks.add(hook);
            return result;
        } catch (RuntimeException | LinkageError failure) {
            entry.readiness = new ProviderReadiness(ProviderReadiness.Status.FAILED, entry.readiness.version(), describe(failure));
            log(entry);
            // Once a hook starts, facts or pre-acquisition inputs may already have escaped. Never publish a partial result.
            throw new IllegalStateException("Balance generation provider " + family + "/" + provider.id() + " failed in " + hook, failure);
        } finally { entry.collectionNanos += System.nanoTime() - start; }
    }

    public void emitted(String family, GenerationProvider provider, long facts, long sources, double confidence) {
        Entry entry = entries.get(family + "/" + provider.id());
        entry.facts += facts; entry.sources += sources;
        if (facts + sources > 0) entry.minimumConfidence = Math.min(entry.minimumConfidence, confidence);
    }

    public JsonArray diagnostics() {
        JsonArray out = new JsonArray();
        entries.values().forEach(entry -> {
            JsonObject row = new JsonObject();
            row.addProperty("id", entry.id); row.addProperty("family", entry.family);
            row.addProperty("status", entry.readiness.status().name()); row.addProperty("version", entry.readiness.version());
            row.addProperty("ready", entry.readiness.collectable());
            row.addProperty("support", entry.readiness.status() == ProviderReadiness.Status.AVAILABLE ? "supported" : entry.readiness.status().name());
            row.addProperty("detail", entry.readiness.detail()); row.addProperty("required", entry.required);
            row.addProperty("adapterAvailable", true); row.addProperty("requiredEvidenceComplete", entry.readiness.requiredEvidenceComplete());
            JsonObject dependencies = new JsonObject();
            entry.dependencies.forEach((id, version) -> { JsonObject dependency = new JsonObject(); dependency.addProperty("installed", version != null);
                if (version != null) dependency.addProperty("version", version); dependencies.add(id, dependency); });
            row.add("dependencies", dependencies);
            row.addProperty("evidenceCollected", !entry.completedHooks.isEmpty()); row.addProperty("facts", entry.facts); row.addProperty("sources", entry.sources);
            JsonArray completed = new JsonArray(); entry.completedHooks.stream().sorted().forEach(completed::add); row.add("completedHooks", completed);
            if (entry.facts + entry.sources > 0) row.addProperty("minimumConfidence", entry.minimumConfidence);
            row.addProperty("probeNanos", entry.probeNanos); row.addProperty("collectionNanos", entry.collectionNanos);
            out.add(row);
        });
        return out;
    }
    public java.util.List<String> warnings() {
        return entries.values().stream().filter(entry -> entry.readiness.status() != ProviderReadiness.Status.AVAILABLE
                        && entry.readiness.status() != ProviderReadiness.Status.ABSENT && entry.readiness.status() != ProviderReadiness.Status.DISABLED)
                .map(entry -> "Provider " + entry.family + "/" + entry.id + " [" + entry.readiness.status() + "]: " + entry.readiness.detail()).sorted().toList();
    }

    private static void log(Entry entry) {
        BalancePerformance.detail("provider/" + entry.family + "/" + entry.id, entry.readiness.status() + ": " + entry.readiness.detail());
    }
    private static String describe(Throwable failure) { return failure.getClass().getSimpleName() + ": " + failure.getMessage(); }
    private static final class Entry {
        final String id, family;
        final boolean required;
        final java.util.Set<String> hooks = new java.util.HashSet<>();
        final java.util.Set<String> completedHooks = new java.util.HashSet<>();
        ProviderReadiness readiness;
        Map<String, String> dependencies = Map.of();
        long facts, sources, probeNanos, collectionNanos;
        double minimumConfidence = 1;
        Entry(String id, String family, boolean required) { this.id = id; this.family = family; this.required = required; }
    }
}
