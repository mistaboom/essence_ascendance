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
        var attempt = OptionalIntegration.attempt(key, "readiness", () -> {
        try (var phase = BalancePerformance.phase("provider_readiness/" + key)) {
            if (disabled) entry.readiness = new ProviderReadiness(ProviderReadiness.Status.DISABLED, "unknown", "Disabled by factual override");
            else {
                Map<String, String> dependencies = new TreeMap<>();
                provider.dependencyModIds().forEach(id -> dependencies.put(id, installedVersions.apply(id)));
                entry.dependencies = dependencies;
                entry.dependenciesDeclared = true;
                if (dependencies.containsValue(null)) entry.readiness = new ProviderReadiness(ProviderReadiness.Status.ABSENT, "unknown",
                        "Missing optional dependencies: " + dependencies.entrySet().stream()
                                .filter(row -> row.getValue() == null).map(Map.Entry::getKey).toList());
                else {
                    entry.axes = java.util.Set.copyOf(provider.capabilityAxes());
                    entry.readiness = java.util.Objects.requireNonNull(inputs == null ? provider.readiness(null)
                            : inputs.readOnlyIntegration(() -> provider.readiness(inputs)), "Provider returned null readiness");
                }
            }
            return entry.readiness;
        }
        });
        if (!attempt.succeeded()) entry.readiness = new ProviderReadiness(ProviderReadiness.Status.FAILED, "unknown", attempt.failure());
        entry.probeNanos = System.nanoTime() - start;
        log(entry);
        if (attempt.succeeded() && entry.readiness.status() != ProviderReadiness.Status.AVAILABLE
                && entry.readiness.status() != ProviderReadiness.Status.ABSENT && entry.readiness.status() != ProviderReadiness.Status.DISABLED)
            OptionalIntegration.warn(key, entry.readiness.status() + ": " + entry.readiness.detail());
        return entry.readiness.collectable();
    }

    /** Only detached values may escape this boundary. Use collect for mutable sinks. */
    public <T> java.util.Optional<T> run(String family, GenerationProvider provider, String hook, Supplier<T> action) {
        Entry entry = java.util.Objects.requireNonNull(entries.get(family + "/" + provider.id()), "Probe provider first");
        if (!entry.readiness.collectable()) {
            entry.skippedHooks.add(hook);
            return java.util.Optional.empty();
        }
        if (!entry.hooks.add(hook)) throw new IllegalStateException("Provider hook invoked twice: " + provider.id() + "/" + hook);
        long start = System.nanoTime();
        var attempt = OptionalIntegration.attempt(family + "/" + provider.id(), hook, () -> {
        try (var phase = BalancePerformance.phase("provider/" + family + "/" + provider.id() + "/" + hook)) {
            return inputs == null ? action.get() : inputs.readOnlyIntegration(action);
        }
        });
        entry.collectionNanos += System.nanoTime() - start;
        if (attempt.succeeded()) entry.completedHooks.add(hook);
        else {
            entry.readiness = new ProviderReadiness(ProviderReadiness.Status.FAILED, entry.readiness.version(), hook + ": " + attempt.failure());
            log(entry);
        }
        return attempt.value();
    }

    /** Reusable transaction for any future sink: publish only a fully completed hook. */
    public <S> boolean collect(String family, GenerationProvider provider, String hook, Supplier<S> staging,
                              java.util.function.Consumer<S> action, java.util.function.Consumer<S> publish) {
        var completed = run(family, provider, hook, () -> {
            S output = staging.get(); action.accept(output); return output;
        });
        completed.ifPresent(publish);
        return completed.isPresent();
    }
    public String detail(String family, GenerationProvider provider) {
        Entry entry = entries.get(family + "/" + provider.id());
        return entry.readiness.status() + ": " + entry.readiness.detail();
    }
    /** Read captured declarations without re-entering a failed optional adapter. */
    public java.util.Set<CapabilityAxis> capabilityAxes(String family, GenerationProvider provider) {
        return entries.get(family + "/" + provider.id()).axes;
    }
    public ProviderReadiness.Status status(String family, GenerationProvider provider) {
        return entries.get(family + "/" + provider.id()).readiness.status();
    }
    public boolean dependenciesInstalled(String family, GenerationProvider provider) {
        Entry entry = entries.get(family + "/" + provider.id());
        return entry.dependenciesDeclared && !entry.dependencies.containsValue(null);
    }
    public CapabilitySink.Reason unavailableReason(String family, GenerationProvider provider) {
        return switch (status(family, provider)) {
            case FAILED -> CapabilitySink.Reason.READ_FAILED;
            case NOT_READY -> CapabilitySink.Reason.DATA_NOT_READY;
            case DISABLED -> CapabilitySink.Reason.CONFIGURATION_DISABLED;
            case ABSENT, UNSUPPORTED -> CapabilitySink.Reason.UNSUPPORTED_API;
            default -> CapabilitySink.Reason.UNCLASSIFIED;
        };
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
            JsonArray axes = new JsonArray(); entry.axes.stream().sorted().forEach(axis -> axes.add(axis.name()));
            row.add("capabilityAxes", axes);
            row.addProperty("status", entry.readiness.status().name()); row.addProperty("version", entry.readiness.version());
            row.addProperty("ready", entry.readiness.collectable());
            row.addProperty("support", entry.readiness.status() == ProviderReadiness.Status.AVAILABLE ? "supported" : entry.readiness.status().name());
            row.addProperty("detail", entry.readiness.detail()); row.addProperty("required", entry.required);
            row.addProperty("failurePolicy", "exclude_failed_hook_and_continue");
            row.addProperty("adapterAvailable", true); row.addProperty("requiredEvidenceComplete", entry.readiness.requiredEvidenceComplete());
            JsonObject dependencies = new JsonObject();
            entry.dependencies.forEach((id, version) -> { JsonObject dependency = new JsonObject(); dependency.addProperty("installed", version != null);
                if (version != null) dependency.addProperty("version", version); dependencies.add(id, dependency); });
            row.add("dependencies", dependencies);
            row.addProperty("evidenceCollected", !entry.completedHooks.isEmpty()); row.addProperty("facts", entry.facts); row.addProperty("sources", entry.sources);
            JsonArray attempted = new JsonArray(); entry.hooks.stream().sorted().forEach(attempted::add); row.add("attemptedHooks", attempted);
            JsonArray completed = new JsonArray(); entry.completedHooks.stream().sorted().forEach(completed::add); row.add("completedHooks", completed);
            JsonArray skipped = new JsonArray(); entry.skippedHooks.stream().sorted().forEach(skipped::add); row.add("skippedHooks", skipped);
            if (entry.facts + entry.sources > 0) row.addProperty("minimumConfidence", entry.minimumConfidence);
            row.addProperty("probeNanos", entry.probeNanos); row.addProperty("collectionNanos", entry.collectionNanos);
            out.add(row);
        });
        return out;
    }
    /** Stable decision provenance: observational timings never become balance input. */
    public JsonArray capabilityDiagnostics() {
        JsonArray out = diagnostics();
        out.forEach(element -> {
            element.getAsJsonObject().remove("probeNanos");
            element.getAsJsonObject().remove("collectionNanos");
        });
        return out;
    }
    public java.util.List<String> warnings() {
        return entries.values().stream().filter(entry -> entry.readiness.status() != ProviderReadiness.Status.AVAILABLE
                        && entry.readiness.status() != ProviderReadiness.Status.ABSENT && entry.readiness.status() != ProviderReadiness.Status.DISABLED)
                .map(entry -> "Provider " + entry.family + "/" + entry.id + " [" + entry.readiness.status() + "]: " + entry.readiness.detail()
                        + "; affected evidence excluded/advisory; balance accuracy may be reduced").sorted().toList();
    }

    private static void log(Entry entry) {
        BalancePerformance.detail("provider/" + entry.family + "/" + entry.id, entry.readiness.status() + ": " + entry.readiness.detail());
    }
    private static final class Entry {
        final String id, family;
        final boolean required;
        final java.util.Set<String> hooks = new java.util.HashSet<>();
        final java.util.Set<String> completedHooks = new java.util.HashSet<>();
        final java.util.Set<String> skippedHooks = new java.util.HashSet<>();
        ProviderReadiness readiness;
        Map<String, String> dependencies = Map.of();
        boolean dependenciesDeclared;
        java.util.Set<CapabilityAxis> axes = java.util.Set.of();
        long facts, sources, probeNanos, collectionNanos;
        double minimumConfidence = 1;
        Entry(String id, String family, boolean required) { this.id = id; this.family = family; this.required = required; }
    }
}
