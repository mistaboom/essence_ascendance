package com.mistaboom.essence_ascendance.balance.generated;

import com.google.gson.JsonObject;
import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.balance.config.BalanceInputs;
import com.mistaboom.essence_ascendance.balance.economy.EconomyGenerator;
import com.mistaboom.essence_ascendance.balance.economy.EconomyProfile;
import com.mistaboom.essence_ascendance.balance.economy.FractionalAmountService;
import com.mistaboom.essence_ascendance.balance.engine.PackEvidence;
import com.mistaboom.essence_ascendance.balance.engine.PackEvidenceCollector;
import com.mistaboom.essence_ascendance.balance.runtime.RuntimeBalanceDefinition;
import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import com.mistaboom.essence_ascendance.essence.EssenceRegistry;
import com.mistaboom.essence_ascendance.lifecycle.PlayerRuntimeLifecycleService;
import com.mistaboom.essence_ascendance.mapping.ItemEssenceMappingRegistry;
import com.mistaboom.essence_ascendance.network.ItemEssenceTooltipSyncService;
import com.mistaboom.essence_ascendance.network.RuntimeBalanceSyncService;
import dev.architectury.platform.Platform;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Server-thread transaction: collect, construct, validate, persist, publish, then synchronize. */
public final class GeneratedBalanceService {
    private static final String GENERATION_REVISION = "economic-ascension-and-accessible-starters-6";
    private static final String DISSOLUTION_ACCOUNTING = "whole_essence_v1";
    private static volatile Active active;
    private static volatile boolean resourcesChanged;
    private static volatile long lastLoadMillis;
    private static volatile long lastGenerationMillis;
    private static volatile Map<String, Long> phaseTimings = Map.of();
    private GeneratedBalanceService() { }
    public record Active(BalanceDocument document, PackEvidence evidence, EconomyProfile economy,
                         RuntimeBalanceDefinition runtime) { }
    public static Active active() {
        Active result = active;
        if (result == null) throw new IllegalStateException("No validated server balance profile is active");
        return result;
    }
    public static Path directory() { return Platform.getConfigFolder().resolve(EssenceAscendance.MOD_ID); }
    public static Path profilePath() { return directory().resolve("generated_balance.json"); }
    public static long lastLoadMillis() { return lastLoadMillis; }
    public static long lastGenerationMillis() { return lastGenerationMillis; }
    public static Map<String, Long> phaseTimingsMillis() { return phaseTimings; }
    public static void markResourcesChanged() { resourcesChanged = true; }
    public static void clear() { active = null; resourcesChanged = false; phaseTimings = Map.of(); lastGenerationMillis = 0; EssenceConfigManager.reset(); RuntimeBalanceSyncService.clear(); }

    public static void load(MinecraftServer server, boolean rebuild) throws IOException {
        if (!server.isSameThread()) throw new IllegalStateException("Balance generation must run on the server thread");
        long start = System.nanoTime();
        BalanceInputs.scaffold(Platform.getConfigFolder());
        PackFingerprint environment = PackFingerprint.capture(server);
        boolean generate = rebuild || !Files.exists(profilePath());
        Active previous = active;
        Active candidate;
        Map<String, Long> timings = new java.util.TreeMap<>();
        long measuredGenerationMillis = 0;
        if (generate) {
            BalanceInputs inputs = BalanceInputs.read(Platform.getConfigFolder());
            for (String exact : inputs.overrides().exactValues().keySet()) {
                if (!exact.startsWith("/runtime/") && !exact.startsWith("/economy/"))
                    throw new IllegalArgumentException("Unsupported exact override path " + exact + "; supported roots: /runtime/ and /economy/");
            }
            EssenceAscendance.LOGGER.info("Generating pack balance: {} mods; previous profile retained until validation completes", environment.mods().size());
            long phase = System.nanoTime();
            PackEvidence evidence = PackEvidenceCollector.collect(server, inputs.settings(), inputs.overrides());
            timings.put("evidence", elapsedMillis(phase)); phase = System.nanoTime();
            EconomyProfile economy = EconomyGenerator.generate(server, evidence, inputs.settings(), inputs.overrides());
            timings.put("economy", elapsedMillis(phase)); phase = System.nanoTime();
            RuntimeBalanceDefinition runtime = RuntimeBalanceDefinition.generate(evidence, economy, inputs.settings(), inputs.overrides());
            timings.put("runtime", elapsedMillis(phase)); phase = System.nanoTime();
            JsonObject metadata = new JsonObject();
            metadata.addProperty("generatorRevision", GENERATION_REVISION);
            metadata.addProperty("dissolutionAccounting", DISSOLUTION_ACCOUNTING);
            metadata.add("environment", BalanceDocument.GSON.toJsonTree(environment));
            metadata.addProperty("settingsFingerprint", inputs.settingsFingerprint());
            metadata.addProperty("overridesFingerprint", inputs.overridesFingerprint());
            metadata.addProperty("evidenceDigest", BalanceDocument.hash(BalanceDocument.GSON.toJsonTree(evidence)));
            metadata.addProperty("freshnessPolicy", "Explicit rebuild required after changed inputs or resources; identity checks do not prove unchanged script or recipe behavior");
            JsonObject validation = new JsonObject();
            validation.addProperty("runtime", "passed");
            validation.addProperty("economy", "passed");
            validation.addProperty("serialization", "passed");
            validation.addProperty("liveGameplay", "not performed by generator");
            JsonObject document = new JsonObject();
            document.add("metadata", metadata);
            document.add("settings", BalanceDocument.GSON.toJsonTree(inputs.settings()));
            document.add("overrides", BalanceDocument.GSON.toJsonTree(inputs.overrides()));
            document.add("evidence", BalanceDocument.GSON.toJsonTree(evidence));
            document.add("runtime", runtime.toJson());
            document.add("economy", BalanceDocument.GSON.toJsonTree(economy));
            JsonObject skills = skillDiagnostics(runtime);
            timings.put("skill_diagnostics", elapsedMillis(phase)); phase = System.nanoTime();
            document.add("skills", skills);
            document.add("validation", validation);
            candidate = decode(BalanceDocument.seal(document));
            timings.put("serialization_validation", elapsedMillis(phase));
            measuredGenerationMillis = elapsedMillis(start);
        } else {
            candidate = decode(BalanceProfileStore.read(profilePath()));
            EssenceAscendance.LOGGER.info("Loaded generated_balance.json; evidence collection, graph solving and curve generation skipped");
        }
        long commitStart = System.nanoTime();
        List<String> warnings = new ArrayList<>(candidate.evidence().warnings());
        warnings.addAll(candidate.economy().warnings());
        if (!generate) {
            List<String> staleness = status(candidate, environment);
            warnings.addAll(staleness);
            staleness.forEach(warning -> EssenceAscendance.LOGGER.warn("Generated balance: {}", warning));
        }
        Map<ResourceLocation, Map<EssenceDefinition, Long>> yields = resolvedMappings(candidate.economy(), warnings);
        Active prepared = candidate;
        // Registry prepares every item before invoking the durable commit. No tick
        // or player transaction can interleave on this server thread.
        ItemEssenceMappingRegistry.installResolved(yields,
                new ItemEssenceMappingRegistry.LoadSummary(yields.size(), 0, 0, 0, 0, warnings), () -> {
                    if (generate) {
                        try { BalanceProfileStore.replace(profilePath(), prepared.document()); }
                        catch (IOException error) { throw new UncheckedIOException(error); }
                    }
                    EssenceConfigManager.install(prepared.runtime());
                    active = prepared;
                });
        timings.put("commit", elapsedMillis(commitStart));
        if (generate) {
            phaseTimings = java.util.Collections.unmodifiableMap(new java.util.TreeMap<>(timings));
            lastGenerationMillis = measuredGenerationMillis;
        }
        resourcesChanged = generate ? false : resourcesChanged;
        lastLoadMillis = elapsedMillis(start);
        EssenceAscendance.LOGGER.info("Balance {} installed: {} resources, {} equipment references, {} enemy references; {} ms",
                candidate.document().integrity().substring(0, 12), candidate.economy().resources().size(),
                candidate.evidence().equipment().size(), candidate.evidence().enemies().size(), lastLoadMillis);
        if (generate) {
            try { BalanceReports.export(candidate, previous, directory(), lastGenerationMillis); }
            catch (IOException | RuntimeException error) {
                // Diagnostics are derived output; failure cannot invalidate an already committed profile.
                EssenceAscendance.LOGGER.error("Profile installed, but balance diagnostics could not be written. Use /essence debug balance export", error);
            }
        }
        try {
            RuntimeBalanceSyncService.syncAll(server);
            PlayerRuntimeLifecycleService.refreshAll(server);
            ItemEssenceTooltipSyncService.syncAll(server);
        } catch (RuntimeException error) {
            EssenceAscendance.LOGGER.error("Profile installed; client synchronization failed and must be retried by reconnecting", error);
        }
    }

    private static JsonObject skillDiagnostics(RuntimeBalanceDefinition runtime) {
        var catalog = com.mistaboom.essence_ascendance.skill.SkillRegistry.values();
        var serializer = new com.google.gson.GsonBuilder().disableHtmlEscaping()
                // Set iteration can differ between JVM launches. Canonicalize only
                // unordered collections; rank/curve and other ordered lists keep order.
                .registerTypeHierarchyAdapter(java.util.Set.class, (com.google.gson.JsonSerializer<java.util.Set<?>>)
                        (values, type, context) -> {
                            var result = new com.google.gson.JsonArray();
                            values.stream().map(context::serialize)
                                    .sorted(java.util.Comparator.comparing(com.google.gson.JsonElement::toString))
                                    .forEach(result::add);
                            return result;
                        })
                .registerTypeAdapter(ResourceLocation.class, (com.google.gson.JsonSerializer<ResourceLocation>)
                        (value, type, context) -> new com.google.gson.JsonPrimitive(value.toString())).create();
        JsonObject result = new JsonObject();
        result.addProperty("curveSource", "/runtime/skillCurves");
        result.addProperty("rankPolicy", "Current maximum ranks remain one; future ranks are projections, not new effects");
        result.add("semantics", serializer.toJsonTree(catalog.stream().map(skill ->
                com.mistaboom.essence_ascendance.skill.balance.SkillBalanceSemantics.require(skill.id())).toList()));
        JsonObject projections = new JsonObject();
        Map<com.mistaboom.essence_ascendance.balance.engine.CapabilityAxis, Double> budgets =
                new java.util.EnumMap<>(com.mistaboom.essence_ascendance.balance.engine.CapabilityAxis.class);
        for (var axis : com.mistaboom.essence_ascendance.balance.engine.CapabilityAxis.values())
            budgets.put(axis, 1 + 2 * runtime.composition().get("skill_share") + runtime.composition().get("partial_viability"));
        for (var tier : com.mistaboom.essence_ascendance.tier.AscendanceTierRegistry.values()) {
            for (boolean future : new boolean[]{false, true}) {
                Map<ResourceLocation, Integer> ranks = new java.util.TreeMap<>(java.util.Comparator.comparing(ResourceLocation::toString));
                catalog.forEach(skill -> {
                    var curve = runtime.skillCurves().get(skill.id().toString());
                    ranks.put(skill.id(), future ? curve.ranks().size() : curve.maximumRank());
                });
                var projection = com.mistaboom.essence_ascendance.skill.balance.SkillLoadoutProjection.project(catalog,
                        tier.id(), ranks, (id, rank) -> runtime.skillCurves().get(id.toString()).ranks().get(rank - 1).powerMultiplier()
                                * runtime.composition().get("rank_safe_skill_scale"),
                        budgets, future);
                projections.add(tier.id().getPath() + (future ? "_future_catalog" : "_current_effects"), serializer.toJsonTree(projection));
            }
        }
        result.add("reachableProjections", projections);
        if (runtime.generationAnalysis() != null)
            result.add("combinedBuilds", serializer.toJsonTree(runtime.generationAnalysis()));
        return result;
    }

    public static Active decode(BalanceDocument document) {
        PackEvidence evidence = BalanceDocument.GSON.fromJson(document.section("evidence"), PackEvidence.class);
        EconomyProfile economy = BalanceDocument.GSON.fromJson(document.section("economy"), EconomyProfile.class);
        RuntimeBalanceDefinition runtime = RuntimeBalanceDefinition.fromJson(document.section("runtime"));
        if (evidence == null || economy == null) throw new IllegalArgumentException("Incomplete generated evidence/economy");
        if (!document.section("metadata").get("evidenceDigest").getAsString().equals(BalanceDocument.hash(BalanceDocument.GSON.toJsonTree(evidence))))
            throw new IllegalArgumentException("Evidence digest mismatch");
        EconomyGenerator.validate(economy);
        JsonObject metadata = document.section("metadata");
        if (metadata.has("dissolutionAccounting")) {
            if (!DISSOLUTION_ACCOUNTING.equals(metadata.get("dissolutionAccounting").getAsString()))
                throw new IllegalArgumentException("Unsupported generated dissolution accounting policy; explicitly rebuild with this mod version");
            EconomyGenerator.validateWhole(economy);
        }
        runtime.validate();
        runtime.validateServerReferences();
        var processing = economy.processingPolicy();
        var infuser = runtime.config().infuserBalance();
        if (infuser.conversionEfficiencyBasisPoints() != processing.conversionEfficiencyBasisPoints()
                || infuser.carrierExtractionEfficiencyBasisPoints() != processing.carrierExtractionEfficiencyBasisPoints())
            throw new IllegalArgumentException("Runtime conversion/extraction projections differ from the canonical economy policy");
        return new Active(document, evidence, economy, runtime);
    }
    public static List<String> status(MinecraftServer server) { return status(active(), PackFingerprint.capture(server)); }
    private static List<String> status(Active profile, PackFingerprint current) {
        JsonObject metadata = profile.document().section("metadata");
        List<String> changes = new ArrayList<>();
        if (!metadata.has("generatorRevision")
                || !GENERATION_REVISION.equals(metadata.get("generatorRevision").getAsString()))
            changes.add("Balance generator updated; use /essence debug balance rebuild to replace obsolete mining/boss ascension gates with generated Essence qualification");
        if (!metadata.getAsJsonObject("environment").get("digest").getAsString().equals(current.digest()))
            changes.add("Pack identity changed; use /essence debug balance rebuild");
        try {
            if (!metadata.get("settingsFingerprint").getAsString().equals(BalanceInputs.fingerprint(BalanceInputs.settingsPath(Platform.getConfigFolder()))))
                changes.add("Friendly TOML changed; current generated values remain active until explicit rebuild");
            if (!metadata.get("overridesFingerprint").getAsString().equals(BalanceInputs.fingerprint(BalanceInputs.overridesPath(Platform.getConfigFolder()))))
                changes.add("Override TOML changed; current generated values remain active until explicit rebuild");
        } catch (IOException error) { changes.add("Cannot read input fingerprints: " + error.getMessage()); }
        if (resourcesChanged) changes.add("Server resources reloaded since profile load; explicit rebuild required");
        return List.copyOf(changes);
    }
    public static void export() throws IOException { BalanceReports.export(active(), null, directory(), lastGenerationMillis); }
    private static Map<ResourceLocation, Map<EssenceDefinition, Long>> resolvedMappings(EconomyProfile economy, List<String> warnings) {
        Map<ResourceLocation, Map<EssenceDefinition, Long>> result = new LinkedHashMap<>();
        int absent = 0;
        for (var entry : economy.resources().entrySet()) {
            ResourceLocation item = ResourceLocation.parse(entry.getKey());
            if (!BuiltInRegistries.ITEM.containsKey(item)) { absent++; continue; }
            Map<EssenceDefinition, Long> outputs = new LinkedHashMap<>();
            for (var route : entry.getValue().routedYields().entrySet()) {
                EssenceDefinition essence = EssenceRegistry.get(ResourceLocation.parse(route.getKey()))
                        .orElseThrow(() -> new IllegalArgumentException("Unknown generated Essence " + route.getKey()));
                long units = FractionalAmountService.units(route.getValue());
                if (units > 0) outputs.put(essence, units);
            }
            if (!outputs.isEmpty()) result.put(item, Map.copyOf(outputs));
        }
        if (absent > 0) warnings.add(absent + " saved resources are no longer registered and were omitted; rebuild profile");
        return result;
    }
    private static long elapsedMillis(long start) { return (System.nanoTime() - start) / 1_000_000L; }
}
