package com.mistaboom.essence_ascendance.balance.generated;

import dev.architectury.platform.Mod;
import dev.architectury.platform.Platform;
import net.minecraft.server.MinecraftServer;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.Objects;

/** Completes audited late recipe publication only when generating a new balance profile. */
final class GenerationRecipeReadiness {
    static final String MOD_ID = "ars_unification";
    static final String AUDITED_VERSION = "1.2.21";
    private static final EpochGate GATE = new EpochGate();
    // AU sets its static processorsRegistered flag before populating its processor list. Once
    // publication is entered, an exception can leave that global list incomplete for this JVM.
    private static final PublicationGate PUBLICATION = new PublicationGate();

    private GenerationRecipeReadiness() {}

    /** New generation must wait for complete recipes; loading a saved profile never enters this path. */
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

        Result prepare(Object server, Object resources, Object recipes, String version, Preparation preparation) {
            Objects.requireNonNull(server); Objects.requireNonNull(resources); Objects.requireNonNull(recipes);
            if (matches(server, resources, recipes) && Objects.equals(this.version, version)
                    && result != null && result.ready()) return result;
            this.server = server; this.resources = resources; this.recipes = recipes; this.version = version;
            // Invalidate any previous success before calling code that may fail or publish only part of its recipes.
            result = Result.unavailable("recipe preparation has not completed");
            if (version == null) return result = new Result(true, "Optional dependency absent", com.mistaboom.essence_ascendance.balance.engine.ProviderReadiness.Status.ABSENT);
            if (!AUDITED_VERSION.equals(version))
                return result = new Result(false, Result.unavailable("unsupported version " + version + "; audited " + AUDITED_VERSION).unavailableInput(),
                        com.mistaboom.essence_ascendance.balance.engine.ProviderReadiness.Status.UNSUPPORTED);
            try {
                return result = Objects.requireNonNull(preparation.run());
            } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                return result = Result.failed(error.getClass().getSimpleName()
                        + (error.getMessage() == null ? "" : ": " + error.getMessage()));
            }
        }

        void requireReady(Object server, Object resources, Object recipes) {
            if (!matches(server, resources, recipes) || result == null)
                throw new IllegalStateException("Balance generation requires recipe preparation for the current server resources");
            if (!result.ready()) throw new IllegalStateException("Balance generation deferred: " + result.unavailableInput());
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
            row.addProperty("provenance", "Audited early recipe publication; quantities are included in the effective recipe snapshot, not emitted as capability facts");
            return row;
        }
        void clear() { server = null; resources = null; recipes = null; version = null; result = null; preparationNanos = 0; }
    }
}
