package com.mistaboom.essence_ascendance.balance.generated;

import java.util.concurrent.atomic.AtomicInteger;

public final class GenerationRecipeReadinessTest {
    private static int checks;

    public static void main(String[] args) {
        var gate = new GenerationRecipeReadiness.EpochGate();
        Object server = new Object(), resources = new Object(), recipes = new Object();
        AtomicInteger calls = new AtomicInteger();
        GenerationRecipeReadiness.Preparation prepare = () -> {
            calls.incrementAndGet();
            return new GenerationRecipeReadiness.Result(true, "", com.mistaboom.essence_ascendance.balance.engine.ProviderReadiness.Status.AVAILABLE);
        };
        String version = GenerationRecipeReadiness.AUDITED_VERSION;
        blocked(() -> gate.requireReady(server, resources, recipes), "Unprepared generation must stop before evidence capture");
        check(gate.prepare(server, resources, recipes, null, prepare).ready() && calls.get() == 0,
                "Absent optional mod never resolves or invokes its classes");
        check(gate.diagnostics().get("status").getAsString().equals("ABSENT") && !gate.diagnostics().get("ready").getAsBoolean(), "Absent adapter does not claim provider readiness");
        gate.requireReady(server, resources, recipes);
        check(gate.excludedRecipeNamespaces(server, resources, recipes).isEmpty(), "Absent optional integration excludes no recipes");
        check(gate.prepare(server, resources, recipes, version, prepare).ready() && calls.get() == 1,
                "Audited mod is prepared before consumers can continue");
        gate.requireReady(server, resources, recipes);
        check(gate.excludedRecipeNamespaces(server, resources, recipes).isEmpty(), "Completed publication retains recipe evidence");
        check(gate.prepare(server, resources, recipes, version, prepare).ready() && calls.get() == 1,
                "Same epoch reuses completed preparation without rerunning processors");
        Object reload = new Object();
        blocked(() -> gate.requireReady(server, reload, recipes), "Reload invalidates generation permission even before capture");
        blocked(() -> gate.excludedRecipeNamespaces(server, reload, recipes), "Recipe exclusion decisions cannot cross resource epochs");
        check(gate.prepare(server, reload, recipes, version, prepare).ready() && calls.get() == 2,
                "New resources reprocess recipes");
        Object replacement = new Object();
        blocked(() -> gate.requireReady(server, reload, replacement), "Recipe manager replacement invalidates generation permission");
        check(gate.prepare(server, reload, replacement, version, prepare).ready() && calls.get() == 3,
                "New recipe manager reprocesses recipes");
        check(gate.prepare(new Object(), reload, replacement, version, prepare).ready() && calls.get() == 4,
                "Different servers never inherit recipe preparation");
        gate.clear();
        blocked(() -> gate.requireReady(server, resources, recipes), "Stop/reload clear removes prior permission");
        check(gate.prepare(server, resources, recipes, version, prepare).ready() && calls.get() == 5,
                "Cleared scope prepares again");

        var unknown = gate.prepare(server, resources, recipes, "1.2.22", prepare);
        check(!unknown.ready() && unknown.unavailableInput().contains("unsupported version") && calls.get() == 5,
                "Unsupported versions exclude integration evidence without invoking unaudited processors");
        excluded(gate, server, resources, recipes, "Unsupported version permits generation with integration recipes excluded");
        check(gate.diagnostics().get("evidenceExcluded").getAsBoolean()
                        && gate.diagnostics().getAsJsonArray("excludedRecipeNamespaces").get(0).getAsString().equals(GenerationRecipeReadiness.MOD_ID),
                "Unsupported recipe exclusion is explicit in saved provenance");
        var unloaded = gate.prepare(server, resources, recipes, version,
                () -> GenerationRecipeReadiness.Result.unavailable("config not loaded"));
        check(unloaded.status() == com.mistaboom.essence_ascendance.balance.engine.ProviderReadiness.Status.NOT_READY, "Unloaded config has explicit not-ready status");
        check(!unloaded.ready() && unloaded.unavailableInput().contains("config not loaded"),
                "Config-not-loaded remains an actionable integration readiness failure");
        excluded(gate, server, resources, recipes, "Unloaded optional config excludes only its integration recipes");
        check(gate.prepare(server, resources, recipes, version, prepare).ready() && calls.get() == 6,
                "Previously unloaded configuration retries once ready");

        gate.clear();
        var missingMethod = gate.prepare(server, resources, recipes, version,
                () -> { throw new NoSuchMethodException("processRecipes"); });
        check(missingMethod.status() == com.mistaboom.essence_ascendance.balance.engine.ProviderReadiness.Status.FAILED, "ABI failure has explicit failed status");
        check(!missingMethod.ready() && missingMethod.unavailableInput().contains("NoSuchMethodException"),
                "Reflection failure becomes actionable unavailable input");
        excluded(gate, server, resources, recipes, "Optional reflection failure permits generation with recipes excluded");
        check(gate.prepare(server, resources, recipes, version, prepare).ready() && calls.get() == 7,
                "A preflight reflection failure remains retryable because publication was never entered");
        gate.clear();
        var publication = new GenerationRecipeReadiness.PublicationGate();
        AtomicInteger publications = new AtomicInteger();
        var partial = gate.prepare(server, resources, recipes, version, () -> publication.publish(() -> {
            publications.incrementAndGet();
            throw new IllegalStateException("processor failure\n" + "x".repeat(1024));
        }));
        check(!partial.ready() && !partial.unavailableInput().contains("\n") && partial.unavailableInput().length() < 550,
                "Partial processor failure remains bounded and never grants readiness");
        excluded(gate, server, resources, recipes, "Partial optional publication cannot contribute recipe evidence");
        var linkage = gate.prepare(server, resources, recipes, version,
                () -> { throw new NoClassDefFoundError("optional dependency"); });
        check(!linkage.ready() && linkage.unavailableInput().contains("NoClassDefFoundError"),
                "Missing optional integration linkage records failed evidence");
        excluded(gate, server, resources, recipes, "Optional linkage failure excludes recipes and permits generation");
        GenerationRecipeReadiness.Preparation poisonedRetry = () -> publication.publish(publications::incrementAndGet);
        check(!gate.prepare(server, resources, recipes, version, poisonedRetry).ready() && publications.get() == 1,
                "An entered failure never reruns partially registered global processors");
        gate.clear();
        check(!gate.prepare(server, resources, recipes, version, poisonedRetry).ready() && publications.get() == 1,
                "Epoch clear cannot recover corrupted JVM-global registration");
        Object anotherServer = new Object(), anotherResources = new Object(), anotherRecipes = new Object();
        check(!gate.prepare(anotherServer, anotherResources, anotherRecipes, version, poisonedRetry).ready()
                        && publications.get() == 1,
                "A new server and recipe manager cannot recover poisoned JVM-global registration");
        excluded(gate, anotherServer, anotherResources, anotherRecipes,
                "Poison excludes optional recipe evidence in a new world without blocking generation");

        var successfulPublication = new GenerationRecipeReadiness.PublicationGate();
        GenerationRecipeReadiness.Preparation successful = () -> successfulPublication.publish(publications::incrementAndGet);
        check(gate.prepare(server, resources, recipes, version, successful).ready() && publications.get() == 2,
                "An intact publisher remains usable after preflight failures");
        check(gate.excludedRecipeNamespaces(server, resources, recipes).isEmpty() && !gate.diagnostics().get("evidenceExcluded").getAsBoolean(),
                "Successful retry removes prior integration exclusion");
        check(gate.prepare(server, resources, recipes, version, successful).ready() && publications.get() == 2,
                "Successful publication is not repeated in the same epoch");
        gate.clear();
        check(gate.prepare(server, resources, recipes, version, successful).ready() && publications.get() == 3,
                "Successful publication may run again after reload");
        gate.clear();
        var fatalPublication = new GenerationRecipeReadiness.PublicationGate();
        try {
            gate.prepare(server, resources, recipes, version, () -> fatalPublication.publish(() -> {
                publications.incrementAndGet();
                throw new OutOfMemoryError("test sentinel");
            }));
            throw new AssertionError("VM failure was swallowed");
        } catch (OutOfMemoryError expected) { checks++; }
        blocked(() -> gate.requireReady(server, resources, recipes), "Fatal attempt never retains a successful readiness state");
        gate.clear();
        check(!gate.prepare(anotherServer, anotherResources, anotherRecipes, version,
                        () -> fatalPublication.publish(publications::incrementAndGet)).ready() && publications.get() == 4,
                "Fatal publication error leaves a sticky poison after epoch clear and new server");
        excluded(gate, anotherServer, anotherResources, anotherRecipes, "Sticky fatal-publication poison never contributes partial recipes on a later completed attempt");
        System.out.println("Generation recipe readiness invariants passed: " + checks + " checks");
    }

    private static void blocked(Runnable action, String message) {
        try { action.run(); throw new AssertionError(message); }
        catch (IllegalStateException expected) { checks++; }
    }

    private static void excluded(GenerationRecipeReadiness.EpochGate gate, Object server, Object resources, Object recipes, String message) {
        gate.requireReady(server, resources, recipes);
        check(gate.excludedRecipeNamespaces(server, resources, recipes).equals(java.util.Set.of(GenerationRecipeReadiness.MOD_ID)), message);
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
}
