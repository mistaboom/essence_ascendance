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
    static final String GENERATION_REVISION = "defensive-posture-status-16";
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
            validation.addProperty("attunement", "multiple unrestricted base methods, positive rates, bounded policy, complete adjacent chapter topology passed");
            validation.addProperty("projectiles", "validated independent path/payload and bounded control policy; native gameplay remains manual");
            validation.addProperty("guard", "validated guard mobility, reflection and counterattack bounds; live gameplay remains manual");
            validation.addProperty("postureStatus", "validated generated posture mitigation and binary harmful-status capability; conditional native gameplay remains manual");
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
                EssenceAscendance.LOGGER.error("Profile installed, but balance diagnostics could not be written. Use /essence admin balance export", error);
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

    static JsonObject skillDiagnostics(RuntimeBalanceDefinition runtime) {
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
        result.addProperty("rankPolicy", "All current skills remain single purchases. A provisional five-rank stress projection reserves future headroom; rank eligibility, gates, costs and purchasing remain deferred catalog decisions.");
        result.add("projectilePolicy", projectilePolicy());
        result.add("guardPolicy", guardPolicy());
        result.add("postureStatusPolicy", postureStatusPolicy());
        result.add("semantics", serializer.toJsonTree(catalog.stream().map(skill ->
                com.mistaboom.essence_ascendance.skill.balance.SkillBalanceSemantics.require(skill.id())).toList()));
        JsonObject projections = new JsonObject();
        Map<com.mistaboom.essence_ascendance.balance.engine.CapabilityAxis, Double> budgets =
                new java.util.EnumMap<>(com.mistaboom.essence_ascendance.balance.engine.CapabilityAxis.class);
        for (var axis : com.mistaboom.essence_ascendance.balance.engine.CapabilityAxis.values())
            budgets.put(axis, 1 + 2 * runtime.composition().get("skill_share") + runtime.composition().get("partial_viability"));
        for (var tier : com.mistaboom.essence_ascendance.tier.AscendanceTierRegistry.powerTiers()) {
            for (boolean future : new boolean[]{false, true}) {
                Map<ResourceLocation, Integer> ranks = new java.util.TreeMap<>(java.util.Comparator.comparing(ResourceLocation::toString));
                catalog.forEach(skill -> {
                    var curve = runtime.skillCurves().get(skill.id().toString());
                    ranks.put(skill.id(), future ? curve.ranks().size() : curve.maximumRank());
                });
                var projection = com.mistaboom.essence_ascendance.skill.balance.SkillLoadoutProjection.project(catalog,
                        tier.id(), ranks, (id, rank) -> runtime.skillCurves().get(id.toString()).ranks().get(rank - 1).powerMultiplier()
                                * com.mistaboom.essence_ascendance.skill.balance.SkillRankEffectScaling.generatedPressureFactor(
                                        runtime.config().skillEffects(), id, runtime.composition().get("rank_safe_skill_scale")),
                        budgets, future);
                projections.add(tier.id().getPath() + (future ? "_future_catalog" : "_current_effects"), serializer.toJsonTree(projection));
            }
        }
        result.add("reachableProjections", projections);
        if (runtime.generationAnalysis() != null)
            result.add("combinedBuilds", serializer.toJsonTree(runtime.generationAnalysis()));
        return result;
    }

    static JsonObject projectilePolicy() {
        JsonObject policy = new JsonObject();
        policy.addProperty("tuning", "/runtime/effects/projectiles; exact TOML overrides; launch payload snapshots survive configuration reload");
        policy.addProperty("choices", "One path plus one independently selected payload; every eligible distinct confirmed victim may consume one bounded payload trigger");
        policy.addProperty("explosion", "Creature-only eligible secondary damage; shared Combustion target bound; no terrain, fire, self/allied damage or recursive payload activation");
        policy.addProperty("root", "Confirmed victim only; translation suppression preserves attacks; bounded refresh window and expiry; no speculative Attunement activity");
        policy.addProperty("hostility", "Explicit damaging adapter, valid responsible source and team/PvP eligibility; ownerless damaging shots handled by the shared ownership policy");
        policy.addProperty("drag", "Twelve-block viscous pocket in all directions; strongest field uses local time for velocity, squared time for gravity and fractional inertia; native entity data synchronizes client arrow physics; exit restores valid reference motion");
        policy.addProperty("interceptor", "Ready ordinary server melee swing evaluated before ServerPlayer.swing resets attack strength; initial left clicks through arrows check interception before mining; forward volume, per-swing deduplication and finite budget; held mining and right clicks excluded");
        policy.addProperty("theft", "Exact crosshair or hostile aim-cone assistance before responsible source; full launch-speed floor plus melee impulse and persisted homing; otherwise safe destruction; one transfer, original offensive path/payload cleared");
        policy.addProperty("attunement", "Only confirmed damage/defeat contributes through registered outcome metadata; slowing, rooting or interception alone awards nothing; no per-action caps");
        policy.addProperty("adapters", "Vanilla arrows, spectral arrows and real Caster bolts; unknown projectile classes require explicit opt-in; absent optional mods need no class loading");
        return policy;
    }

    static JsonObject postureStatusPolicy() {
        JsonObject policy = new JsonObject();
        policy.addProperty("tuning", "/runtime/effects/posture and /runtime/effects/status; existing commented exact TOML, validated bounds, required schema fields and content fingerprint; no separate configuration");
        policy.addProperty("choices", "One Evasive/Bulwark/Adaptive posture and one independent Mirror/Pure State status choice. Switching, lost effectiveness or lifecycle discontinuity resets transient state");
        policy.addProperty("movement", "Ordinary input must correlate with measured server movement. Crouch, grounded motion, intentional swimming/climbing/flight and legitimate guarded movement qualify; mounts, idle falling, forced motion, teleports and correction do not. Finite stillness and turning hysteresis reject jitter");
        policy.addProperty("evasive", "Bounded chance at full posture; one server roll for hostile living-owned nonfire/nonexplosive projectiles or direct physical damage. Direct armor-bypassing or witch-resistant magic, unavoidable/admin/environmental damage and secondaries do not roll. Success, taken hits and stopping drain meter; no fabricated block, reflection or guard reward");
        policy.addProperty("bulwark", "Build while stationary facing bounded valid visible hostile threats. Resistance requires frontal responsible living source. Full-meter threshold suppresses only correlated incoming knockback; loss of stance releases immunity");
        policy.addProperty("adaptive", "One exact DamageType registry ID with bounded stacks/window. First eligible hit seeds; repeated hits reach generated threshold; type switch reseeds before its mitigation. Rejected, zero and secondary damage never build stacks");
        policy.addProperty("incoming_order", "Native incoming event identity; Evasive decision; existing equipment resistance then posture resistance; native shield/armor/toughness/enchantment/Resistance/absorption and actual loss; confirmed guard/reflection/counter outcomes. A dodge is not a block. Prevention and correlated knockback are measured once");
        policy.addProperty("mirror", "First actual eligible harmful application from a valid hostile responsible living source is removed and copied once via native effect semantics. Confirmed interception starts cooldown once; rejected copy does not undo protection. Copy duration/amplifier/presentation are bounded; secondary copies cannot recurse");
        policy.addProperty("status_ticks", "Periodic HARMFUL effect callbacks, including restored effects, run as derived secondary outcomes: native damage and duration remain, but no posture/dodge/reflection/offense proc or transfer chain. BENEFICIAL/NEUTRAL ticks are untouched; no persisted provenance references");
        policy.addProperty("native_status_boundary", "Timed effects record actual native acceptance and hidden-chain changes; Mirror restores any prior chain after removing the intercepted application. Instant harmful interception records eligibility preflight separately from native damage. Source-less effects qualify only for Pure State prevention");
        policy.addProperty("pure_state", "Binary prevention of new harmful applications, including source-less effects. Existing effects are not cleansed. Beneficial/neutral effects remain native. No reflection, cooldown or numeric rank benefit is invented");
        policy.addProperty("rank_policy", "All 90 skills remain one purchase with five provisional diagnostic ranks. Evasive chance, Bulwark resistance, Adaptive per-stack resistance and Mirror cooldown have real typed consumers. Pure State capability pressure stays binary; later catalog-wide design must decide rank eligibility");
        policy.addProperty("balance", "Preserves developed-build-ceilings-15 final-output targets. Posture enters survival via generated avoidance and pre-armor reduction. Status prevention/transfer and knockback control retain separate capability units, never offense allowance or infinite immunity EHP");
        policy.addProperty("status_evidence", "No pack-wide harmful-application cadence or successful-copy rate has been observed. Peak prevention and maximum transfers/second are conditional bounds, not measured uptime, damage or survival. These remain explicit evidence limitations");
        policy.addProperty("attunement", "Only accepted general measured outcomes may contribute. Meter build, threat scans, dodge requests, cooldown readiness and rejected copies create no progression credit; no stable-ID dispatch or per-action cap");
        return policy;
    }

    static JsonObject guardPolicy() {
        JsonObject policy = new JsonObject();
        policy.addProperty("tuning", "/runtime/effects/guard; existing exact TOML overrides, finite validated bounds, content identity and procedural rank retention; no parallel configuration");
        policy.addProperty("eligibility", "Guard mobility and block rewards require an effective skill and functional Ascendance Shield, the current explicit shield contract; both hands use native readiness; Fractured, disabled, broken or invalid equipment grants no guard benefit; generic third-party shields are not inferred");
        policy.addProperty("mobility", "Guarded Advance uses the stronger of equipment investment and skill slowdown removal once, permitting native sprint, jump and short steps only during valid guarding; hunger, crouching, fluids, riding and collision remain authoritative");
        policy.addProperty("ram", "Meaningful forward guarded sprint sweep; physical contact clipped by terrain, deterministic target budget and repeat cooldown; bounded stagger and push, no direct damage or block/counter rewards");
        policy.addProperty("outcomes", "One bounded native-hit identity records incoming force, successful block prevention, actual health/absorption loss, responsible source and attempted knockback; duplicate or secondary callbacks cannot grant rewards");
        policy.addProperty("perfect", "Short window begins at the resolved native-ready tick of one continuous functional guard; pre-ready, late, swapped, disabled and invalid guard sessions do not qualify");
        policy.addProperty("ward", "Fully prevented positive eligible hostile hits extend existing direct-hit armor reflection or functional held-shield reflection toward responsible projectile owners; zero equipment reflection stays zero; finite capped attempted-knockback echo awards no speculative activity");
        policy.addProperty("amplifier", "Successful positive blocks grow a capped multiplier; perfect blocks set its maximum; both refresh expiry, and the granting block uses the new multiplier once");
        policy.addProperty("stored_force", "Positive blocks add converted force up to capacity and refresh idle expiry; next accepted positive primary melee hit consumes once; misses, cancellation, zero loss and secondary damage retain charge until expiry or invalid state");
        policy.addProperty("riposte", "Perfect blocks arm one timed primary melee counter; native cooldown/critical rules remain; damage increases the primary component before Stored Force flat damage; bonus reach and protection apply only to the eligible attack and close on return");
        policy.addProperty("reprisal", "Only confirmed primary reflected health/absorption loss seeds reduced area damage; deterministic safe creature area selection excludes defender and primary target; no repeated amplification or secondary cascade");
        policy.addProperty("choices", "Stored Force and Guard Amplifier use the existing exclusive selected-choice machinery; either may coexist with Riposte; invalid effect/choice/equipment/lifecycle state clears transient rewards");
        policy.addProperty("attunement", "Confirmed damage and kills use general outcome contribution metadata; movement, ram, charge, perfect timing, blocking and echoed knockback create no speculative damage credit; independent history, nonzero repetition floor and no per-action caps remain");
        policy.addProperty("evidence", "Generated numeric checks and saved-evidence replay are automated evidence only; live movement feel, shield timing and both-loader multiplayer/modpack acceptance remain separate");
        return policy;
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
            changes.add("Balance generator updated; use /essence admin balance rebuild to install the current runtime policy");
        if (!metadata.getAsJsonObject("environment").get("digest").getAsString().equals(current.digest()))
            changes.add("Pack identity changed; use /essence admin balance rebuild");
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
