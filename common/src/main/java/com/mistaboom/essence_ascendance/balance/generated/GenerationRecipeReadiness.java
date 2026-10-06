package com.mistaboom.essence_ascendance.balance.generated;

import dev.architectury.platform.Mod;
import dev.architectury.platform.Platform;
import net.minecraft.server.MinecraftServer;
import com.mistaboom.essence_ascendance.balance.engine.OptionalIntegration;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.Objects;
import java.util.Set;

/** Completes audited late recipe publication only when generating a new balance profile. */
public final class GenerationRecipeReadiness {
    static final String MOD_ID = "ars_unification";
    static final String AUDITED_VERSION = "1.2.21";
    private static final EpochGate GATE = new EpochGate();
    // AU sets its static processorsRegistered flag before populating its processor list. Once
    // publication is entered, an exception can leave that global list incomplete for this JVM.
    private static final PublicationGate PUBLICATION = new PublicationGate();

    private GenerationRecipeReadiness() {}

    /** Prepare optional late recipes; unavailable integration evidence is excluded from generation. */
    static void requireGenerationReady(MinecraftServer server) {
        requireServerThread(server);
        String version = Platform.getMods().stream().filter(mod -> MOD_ID.equals(mod.getModId()))
                .map(Mod::getVersion).findFirst().orElse(null);
        long start = System.nanoTime();
        try {
            GATE.prepare(server, server.getResourceManager(), server.getRecipeManager(), version,
                    () -> prepareArsUnification(server));
        } finally {
            GATE.preparationNanos = System.nanoTime() - start;
            BalancePerformance.detail("recipe_provider/" + MOD_ID, GATE.result == null ? "NOT_READY" : GATE.result.status().name());
        }
        GATE.requireReady(server, server.getResourceManager(), server.getRecipeManager());
    }

    static void clear() { GATE.clear(); }
    static com.google.gson.JsonObject diagnostics() { return GATE.diagnostics(); }

    /** All recipe consumers share this exclusion, while the current server/resource epoch stays mandatory. */
    public static Set<String> excludedRecipeNamespaces(MinecraftServer server) {
        requireServerThread(server);
        return GATE.excludedRecipeNamespaces(server, server.getResourceManager(), server.getRecipeManager());
    }

    private static void requireServerThread(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("Recipe preparation must run on the server thread");
    }

    private static Result prepareArsUnification(MinecraftServer server) throws ReflectiveOperationException {
        // Optional integration: neither loader nor AU classes link when the mod is absent. In 1.2.21
        // the first player sync runs this same public entry point, after early world balance generation.
        var loader = GenerationRecipeReadiness.class.getClassLoader();
        var config = Class.forName("dev.qther.ars_unification.Config", true, loader);
        var specField = config.getField("SPEC");
        if (specField.getDeclaringClass() != config || !Modifier.isStatic(specField.getModifiers())
                || !Modifier.isFinal(specField.getModifiers())
                || !specField.getType().getName().equals("net.neoforged.neoforge.common.ModConfigSpec"))
            throw new IllegalStateException("Ars Unification Config.SPEC signature changed");
        Object spec = specField.get(null);
        if (spec == null || spec.getClass() != specField.getType())
            throw new IllegalStateException("Ars Unification config spec is unavailable");
        var isLoaded = specField.getType().getMethod("isLoaded");
        if (Modifier.isStatic(isLoaded.getModifiers()) || isLoaded.getReturnType() != boolean.class)
            throw new IllegalStateException("Ars Unification config readiness signature changed");
        if (!Boolean.TRUE.equals(invoke(isLoaded, spec)))
            return Result.unavailable("Ars Unification server configuration is not loaded yet");

        var integration = Class.forName("dev.qther.ars_unification.ArsUnification", true, loader);
        var process = integration.getMethod("processRecipes", MinecraftServer.class);
        if (process.getDeclaringClass() != integration || !Modifier.isStatic(process.getModifiers())
                || process.getReturnType() != void.class)
            throw new IllegalStateException("Ars Unification processRecipes signature changed");
        return PUBLICATION.publish(() -> invoke(process, null, server));
    }

    private static Object invoke(java.lang.reflect.Method method, Object target, Object... arguments)
            throws ReflectiveOperationException {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException error) {
            var cause = error.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error fatal) throw fatal;
            throw error;
        }
    }

    record Result(boolean ready, String unavailableInput, com.mistaboom.essence_ascendance.balance.engine.ProviderReadiness.Status status) {
        private static final Result READY = new Result(true, "", com.mistaboom.essence_ascendance.balance.engine.ProviderReadiness.Status.AVAILABLE);
        static Result unavailable(String reason) {
            String bounded = reason.replace('\r', ' ').replace('\n', ' ');
            if (bounded.length() > 512) bounded = bounded.substring(0, 512);
            return new Result(false, "recipe readiness " + MOD_ID + " (" + bounded + ")", com.mistaboom.essence_ascendance.balance.engine.ProviderReadiness.Status.NOT_READY);
        }
        static Result failed(String reason) {
            return new Result(false, unavailable(reason).unavailableInput(), com.mistaboom.essence_ascendance.balance.engine.ProviderReadiness.Status.FAILED);
        }
    }

    @FunctionalInterface
    interface Preparation { Result run() throws ReflectiveOperationException; }

    @FunctionalInterface
    interface Publication { void run() throws ReflectiveOperationException; }

    /** A failed entered publication can damage AU's static state; epoch clear cannot repair it. */
    static final class PublicationGate {
        private Result poisoned;

        Result publish(Publication publication) {
            if (poisoned != null) return poisoned;
            // Set before entering external code, including errors that must propagate (for example OOM).
            poisoned = Result.failed("entered recipe publication did not complete; restart Minecraft before retrying");
            try {
                publication.run();
                poisoned = null;
                return Result.READY;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                poisoned = Result.failed("entered recipe publication failed; restart Minecraft before retrying: "
                        + error.getClass().getSimpleName()
                        + (error.getMessage() == null ? "" : ": " + error.getMessage()));
                return poisoned;
            }
        }
    }

    /** Identity scopes avoid repeated publication; failures before publication remain retryable. */
    static final class EpochGate {
        private Object server, resources, recipes;
        private String version;
        private Result result;
        private long preparationNanos;
        private boolean completed;

        Result prepare(Object server, Object resources, Object recipes, String version, Preparation preparation) {
            Objects.requireNonNull(server); Objects.requireNonNull(resources); Objects.requireNonNull(recipes);
            if (matches(server, resources, recipes) && Objects.equals(this.version, version)
                    && completed && result != null && result.ready()) return result;
            this.server = server; this.resources = resources; this.recipes = recipes; this.version = version;
            // Invalidate any previous success before calling code that may fail or publish only part of its recipes.
            completed = false;
            result = Result.unavailable("recipe preparation has not completed");
            if (version == null) return complete(new Result(true, "Optional dependency absent", com.mistaboom.essence_ascendance.balance.engine.ProviderReadiness.Status.ABSENT));
            if (!AUDITED_VERSION.equals(version))
                return complete(new Result(false, Result.unavailable("unsupported version " + version + "; audited " + AUDITED_VERSION).unavailableInput(),
                        com.mistaboom.essence_ascendance.balance.engine.ProviderReadiness.Status.UNSUPPORTED));
            var attempted = OptionalIntegration.attempt(MOD_ID, "early recipe preparation", () -> {
                try { return Objects.requireNonNull(preparation.run()); }
                catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure.getClass().getSimpleName()
                        + (failure.getMessage() == null ? "" : ": " + failure.getMessage()), failure); }
            });
            return complete(attempted.value().orElseGet(() -> Result.failed(attempted.failure())));
        }

        private Result complete(Result prepared) {
            result = prepared;
            completed = true;
            if (!result.ready()) OptionalIntegration.warn(MOD_ID, result.unavailableInput()
                    + "; recipes owned by " + MOD_ID + " are excluded from this generation");
            return result;
        }

        void requireReady(Object server, Object resources, Object recipes) {
            if (!matches(server, resources, recipes) || !completed || result == null)
                throw new IllegalStateException("Balance generation requires recipe preparation for the current server resources");
        }

        Set<String> excludedRecipeNamespaces(Object server, Object resources, Object recipes) {
            requireReady(server, resources, recipes);
            return result.ready() ? Set.of() : Set.of(MOD_ID);
        }

        private boolean matches(Object server, Object resources, Object recipes) {
            return this.server == server && this.resources == resources && this.recipes == recipes;
        }

        com.google.gson.JsonObject diagnostics() {
            var row = new com.google.gson.JsonObject(); row.addProperty("id", MOD_ID); row.addProperty("family", "recipe_readiness");
            row.addProperty("adapterAvailable", true); row.addProperty("installed", version != null);
            row.addProperty("version", version == null ? "absent" : version);
            row.addProperty("status", result == null ? "NOT_READY" : result.status().name());
            row.addProperty("ready", result != null && result.status() == com.mistaboom.essence_ascendance.balance.engine.ProviderReadiness.Status.AVAILABLE);
            row.addProperty("detail", result == null ? "Not prepared" : result.unavailableInput()); row.addProperty("preparationNanos", preparationNanos);
            row.addProperty("evidenceExcluded", completed && result != null && !result.ready());
            var excluded = new com.google.gson.JsonArray();
            if (completed && result != null && !result.ready()) excluded.add(MOD_ID);
            row.add("excludedRecipeNamespaces", excluded);
            row.addProperty("provenance", "Audited early recipe publication; unavailable integration namespaces are excluded from the shared effective recipe snapshot");
            return row;
        }
        void clear() { server = null; resources = null; recipes = null; version = null; result = null; preparationNanos = 0; completed = false; }
    }
}
