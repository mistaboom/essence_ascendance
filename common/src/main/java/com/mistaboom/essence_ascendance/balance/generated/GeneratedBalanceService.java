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
    static final String GENERATION_REVISION = "meaningful-progression-44";
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
            validation.addProperty("vitality", "validated native-unit recovery, damage routing, ward and death-defiance settings with developed-build headroom; live gameplay remains manual");
            validation.addProperty("liveGameplay", "not performed by generator");
            validation.add("bonusTracks", com.mistaboom.essence_ascendance.balance.runtime.BonusTrackGenerator.diagnostics(runtime));
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
        result.addProperty("rankPolicy", "Purchasable maxima and exact native rank parameters are generated from meaningful calibrated states. Candidate curves are allocation inputs, never promised ranks. First-state floor excess is local ignored overage and never funds another allocation.");
        result.add("projectilePolicy", projectilePolicy());
        result.add("guardPolicy", guardPolicy());
        result.add("postureStatusPolicy", postureStatusPolicy());
        result.add("vitalityPolicy", vitalityPolicy(runtime));
        result.add("mobilityPolicy", mobilityPolicy(runtime));
        result.add("semantics", serializer.toJsonTree(catalog.stream().map(skill ->
                com.mistaboom.essence_ascendance.skill.balance.SkillBalanceSemantics.require(skill.id())).toList()));
        JsonObject projections = new JsonObject();
        Map<String, Double> publishedPressure = new java.util.HashMap<>();
        catalog.forEach(skill -> runtime.skillCurves().get(skill.id().toString()).ranks().forEach(rank -> {
            var effects = com.mistaboom.essence_ascendance.skill.balance.SkillRankEffectScaling.applyResolved(
                    runtime.config().skillEffects(), Map.of(skill.id(), rank.rank()), runtime.skillCurves());
            publishedPressure.put(skill.id() + "/" + rank.rank(),
                    com.mistaboom.essence_ascendance.skill.balance.SkillRankEffectScaling.publishedPressureFactor(effects, skill.id()));
        }));
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
                        tier.id(), ranks, (id, rank) -> publishedPressure.get(id + "/" + rank),
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

    static JsonObject vitalityPolicy(RuntimeBalanceDefinition runtime) {
        var policy = new JsonObject();
        var v = runtime.config().skillEffects().vitality();
        policy.addProperty("tuning", "/runtime/effects/vitality; required typed schema, native-unit bounds and exact TOML pointers; no independent settings or purchasable ranks");
        policy.addProperty("derivation", "Existing semantic weights allocate the Dormant first-purchase healing allowance. Saved pack sustained-damage pressure converts healing/second to Life Steal fraction; fastest native natural cadence bounds Rising Recovery. Native food/exhaustion units and the configured survival window resolve consumption and out-of-combat recovery.");
        policy.addProperty("preservation", "Equipment, Bonus mechanics, economy, Attunement and existing rank-one settings resolve first and remain unchanged. Newly implemented healing shares developed survival headroom with provisional posture growth; that diagnostic growth may be reduced globally to keep useful healing and all existing build ceilings.");
        policy.addProperty("naturalRecovery", "Smooth missing-health fraction raised to generated exponent; eligible native natural-regeneration timer keeps normal exhaustion/food checks, while the same multiplier applies to the existing Nexus passive health-regeneration bonus. Other healing is never accelerated.");
        policy.addProperty("maximumNaturalSpeedMultiplier", 1 + v.risingRecovery().maxSpeedBonus());
        policy.addProperty("firstHitHealingFraction", v.lifeSteal().baseHealingFraction());
        policy.addProperty("maximumChainHealingFraction", v.lifeSteal().baseHealingFraction() + (v.lifeSteal().maxChainHits() - 1) * v.lifeSteal().perHitHealingFraction());
        policy.addProperty("lifeSteal", "One accepted primary direct-weapon chain per owner; same target, timeout, miss/inactivity and all lifecycle/selection boundaries; actual accepted damage and useful missing-HP/recoverable-debt cap. Secondary, reflected, status, canceled, zero and invulnerable outcomes excluded; no recursive healing or duplicate Attunement.");
        policy.addProperty("foodUseDurationMultiplier", v.feastReflex().useDurationMultiplier());
        policy.addProperty("automaticMeal", "While hurt and below the native regeneration threshold, successive nutritious safe hotbar meals are eligible; one meal need not fill the whole gap. With effective Metabolic Conversion and a positive generated food-healing rate, meals continue through full hunger until health is full. With effective Metabolic Mending and damaged eligible gear, meals likewise continue through full hunger while food can still produce maintenance repair. Otherwise at full health, food is eligible only when strictly less than half its nutrition would be wasted. Ordinary item completion owns stacks, containers, effects, criteria, sounds and cancellation; never interrupts active use.");
        policy.addProperty("outOfCombatFoodPointsPerSecond", 20.0 * v.innerSustenance().hungerPerRecovery() / v.innerSustenance().hungerRecoveryIntervalTicks());
        policy.addProperty("outOfCombatSaturationPointsPerSecond", 20.0 * v.innerSustenance().saturationPerRecovery() / v.innerSustenance().hungerRecoveryIntervalTicks());
        policy.addProperty("combatTimeoutSeconds", v.innerSustenance().combatTimeoutTicks() / 20.0);
        policy.addProperty("innerSustenance", "Accepted recent hostile damage delays recovery. At full native food/saturation passive activity exhaustion stops, while healing exhaustion and explicit resource costs remain. Owner-only optional sleep/phantom behavior; voluntary sleep and spawn setting remain native.");
        var damage = v.damage();
        policy.addProperty("damageRouting", "After native armor, resistance, enchantments and absorption; one selected damage style. Void/admin bypass-invulnerability sources remain native. Delayed payments cannot be mitigated, absorbed, routed or proc-triggered again.");
        policy.addProperty("hungerWardDamageShare", damage.hungerWard().damageShare());
        policy.addProperty("hungerWardHealthPerFoodPoint", damage.hungerWard().healthPerFoodPoint());
        policy.addProperty("hungerWard", "Only the generated share of post-mitigation damage is offered to food. Unpaid redirected damage returns to health. Calibration/rank scaling changes the share, not food value; the survival projection is bounded by both the share and finite reservoir. No automatic unlimited food-throughput assumption.");
        policy.addProperty("hungerWardFullReservoirHealth", net.minecraft.world.food.FoodConstants.MAX_FOOD * 2.0 * damage.hungerWard().healthPerFoodPoint());
        policy.addProperty("staggeredPainPaymentSeconds", damage.staggeredPain().paymentTicks() / 20.0);
        policy.addProperty("delayedDamage", "Source-preserving, linear online-tick obligations; parallel hits keep their own deadlines. Unpaid totals survive refund, skill selection, dimension change, reconnect and configuration refresh. Native rejection pauses payment. Tagged quiet feedback preserves health/death handling but suppresses repeated impact tilt/sound and health-update flinches. Confirmed death clears debt; a Totem does not. The safety bound applies overflow immediately, never silently drops it.");
        policy.addProperty("damageCeilingTakenFraction", damage.damageCeiling().damageTakenFraction());
        policy.addProperty("maxHealthPerPreventedDamage", damage.damageCeiling().damageTakenFraction());
        policy.addProperty("trauma", "One generated fraction P: take incoming health damage times P, prevent the remainder, and temporarily lose P times prevented damage from maximum-health capacity. No hit-size threshold, max-HP damage cap, independent ratio, Trauma bank or deferred payment. Native capacity minimum and surviving HP bound only the cost, never prevention. Stored independently of effective skills. Quiet-online recovery removes the exact-ID native modifier without healing; native heart slots return empty.");
        policy.addProperty("metabolicFoodPerOverflowHealth", damage.metabolicConversion().foodPointsPerOverflowHealth());
        policy.addProperty("metabolicHealthPerNutrition", damage.metabolicConversion().healthPerNutrition());
        policy.addProperty("metabolicConversion", "Only accepted native overflow restores food, hunger first then saturation, with fractional carry. Full-hunger food uses native completion to heal; this healing cannot recursively refill food. A validated lossy round trip remains mandatory under exact overrides.");
        policy.addProperty("queueRecoveryPerHealing", damage.painPurge().queuePerHealing());
        policy.addProperty("painPurge", "Accepted positive native healing also cancels that amount times queuePerHealing from unpaid damage, capped by debt. Includes native food/passive regeneration, direct life steal and healing potions/effects, including at full HP. Cancellation is never a recursive heal or fictional Attunement health outcome. Nonhealing food alone no longer purges a percentage of the queue.");
        policy.addProperty("adrenalineHealthLossTriggerFraction", damage.adrenaline().triggerHealthLossFraction());
        policy.addProperty("adrenaline", "One refreshable timed stack after a native health write actually removes MORE than triggerHealthLossFraction times pre-hit current maximum HP. Strict comparison; includes equipped/previously reduced maximum HP, excludes absorption, prevented damage, this hit's capacity loss and deferred payments. No lethal-save condition. Existing immediate attribute/HUD sync and bow/caster speed consumers; generated duration/magnitudes and trigger, no new keybind or packet.");
        var wards = v.wards();
        policy.addProperty("wardTuning", "/runtime/effects/vitality/wards; required generated schema, existing validated exact TOML overrides, content identity and rank consumers; no parallel configuration or live fallback");
        policy.addProperty("soulWardVictimHealthFraction", wards.soulWard().victimHealthFraction());
        policy.addProperty("soulWardCapacityHealthFraction", wards.soulWard().capacityHealthFraction());
        policy.addProperty("soulWardDurationSeconds", wards.soulWard().durationTicks() / 20.0);
        policy.addProperty("deepWardCapacityBonusFraction", wards.deepWard().capacityBonusFraction());
        policy.addProperty("deepWardCombatTimeoutSeconds", wards.deepWard().combatTimeoutTicks() / 20.0);
        policy.addProperty("deepWardFullCapDecaySeconds", wards.deepWard().decayTicks() / 20.0);
        policy.addProperty("shatteringWardHealingFractionPerSecond", wards.shatteringWard().healingFractionPerSecond());
        policy.addProperty("shatteringWardRegenerationSeconds", wards.shatteringWard().regenerationTicks() / 20.0);
        policy.addProperty("shatteringWardRadius", wards.shatteringWard().radius());
        policy.addProperty("shatteringWardMaximumTargets", wards.shatteringWard().maximumTargets());
        policy.addProperty("shatteringWardKnockback", wards.shatteringWard().knockback());
        policy.addProperty("wardOwnership", "Confirmed attributed kills grant native absorption from victim current max HP, capped by owner current max HP times the generated capacity. Kills at cap refresh. Source-owned hearts are spent before external absorption; potion application/refresh/removal operates on its own reservoir. Exact-source cap modifiers and points are removed on expiry/refund/lifecycle reset; saved absorption excludes transient owned points. Native hard capacity still applies.");
        policy.addProperty("deepWard", "Mutually exclusive with Shattering Ward. Extends Soul Ward capacity and retains earned hearts during accepted hostile combat; attributed kills also refresh quiet time. After the generated quiet interval, decays by full capacity / decayTicks each online game tick. Returning to combat pauses decay without granting new hearts.");
        policy.addProperty("shatteringWard", "One pulse only when an accepted nondeferred native hit empties a positive Soul Ward pool and the player survives; expiry/capacity trimming/external effect edits/deactivation are not damage breaks. Bounded visible hostile targets, shared control/PvP/team immunity policy and native resisted/cancelable knockback. One refreshable regeneration window uses native healing, not a second healing or HUD system.");
        policy.addProperty("wardProjection", "Defense/rank calibration includes a full finite earned pool with native pre-routing mitigation; no extra Damage Ceiling multiplier is credited to absorption. Healing/rank calibration includes peak conditional Shattering regeneration and accepted-healing synergies. Crowd control is not credited as damage or EHP; no observed victim distribution, kill cadence or break uptime is assumed.");
        policy.addProperty("evidenceLimit", "No observed player food throughput, damage cadence, sleep population or phantom encounter telemetry. Food capacity and delayed payment remain conditional resource/time contracts; Ceiling is a bounded proportional prevention multiplier whose temporary capacity loss is not healing. Live gameplay, multiplayer, dedicated-server and representative-modpack acceptance remain pending.");
        return policy;
    }

    static JsonObject mobilityPolicy(RuntimeBalanceDefinition runtime) {
        var v = runtime.config().skillEffects().mobility();
        JsonObject policy = new JsonObject();
        policy.addProperty("tuning", "/runtime/effects/mobility; generated from existing skill-tier headroom, semantic weights/availability and survival reference window. Existing exact TOML overrides, rank consumers and fingerprint; no live defaults or parallel config.");
        policy.addProperty("maximumSprintSpeedBonus", v.runningMomentum().maximumSpeedBonus());
        policy.addProperty("buildSeconds", v.runningMomentum().buildTicks() / 20.0);
        policy.addProperty("fullDrainSeconds", v.runningMomentum().drainTicks() / 20.0);
        policy.addProperty("sharpTurnDegrees", v.runningMomentum().sharpTurnDegrees());
        policy.addProperty("vaultNativeStepHeight", v.momentumVault().stepHeight());
        policy.addProperty("vaultMinimumMomentum", v.momentumVault().minimumMomentum());
        policy.addProperty("rushRetentionSeconds", v.rush().durationTicks() / 20.0);
        policy.addProperty("impactReduction", v.impactControl().damageReduction());
        policy.addProperty("chargedJumpSeconds", v.chargedJump().chargeTicks() / 20.0);
        policy.addProperty("chargedJumpLaunchMultiplier", Math.sqrt(1 + v.chargedJump().heightBonus()));
        policy.addProperty("chargedJumpSteeringBonus", v.chargedJump().steeringBonus());
        policy.addProperty("doubleJumpLaunchMultiplier", Math.sqrt(1 + v.doubleJump().heightBonus()));
        policy.addProperty("doubleJumpSteeringBonus", v.doubleJump().steeringBonus());
        policy.addProperty("vectorJumpImpulseMultiplier", Math.sqrt(1 + v.vectorJump().impulseBonus()));
        policy.addProperty("vectorJumpDownwardBrake", v.vectorJump().brakeFraction());
        policy.addProperty("essenceWingsHorizontalDragCompensation", v.essenceWings().horizontalDragCompensation());
        policy.addProperty("fatigueFlightEnduranceSeconds", v.fatigueFlight().enduranceTicks() / 20.0);
        policy.addProperty("fatigueFlightGroundRechargeSeconds", v.fatigueFlight().groundRechargeTicks() / 20.0);
        policy.addProperty("fatigueFlightThrustGravityMultiplier", v.fatigueFlight().thrustGravityMultiplier());
        policy.addProperty("vectorBoostRechargeSeconds", v.vectorBoost().rechargeTicks() / 20.0);
        policy.addProperty("vectorBoostNativeFireworkCruiseMultiplier", Math.sqrt(1 + v.vectorBoost().rocketSpeedBonus()));
        policy.addProperty("untetheredAirRechargeSeconds", v.untetheredFlight().airRechargeTicks() / 20.0);
        policy.addProperty("impactControl", "Generated conditional impact reduction through the existing incoming-damage pipeline. Existing combined player/equipment Fall Resistance selects the FALL category for movement impacts, once; native IS_FALL plus extensible movement_impact damage tag covers falls, fly_into_wall and stalagmite by default. Native fall-damage multiplier transfers only to non-fall tagged impacts; no double application, combat/admin immunity or permanent HUD card.");
        policy.addProperty("jumpInput", "Bounded ordinary Jump/direction intent only; server elapsed time earns charge and fresh edges spend one landing-backed air resource. No client velocity, position, strength, skill, timestamp or charge claims. Ground support requires native collision under the feet; mode, lifecycle, rank/config and teleport discontinuities reset state. Existing posture intent expiry cancels stale input, never launches.");
        policy.addProperty("jumpMotion", "Native jump power includes live attributes, Jump Boost and block factors. Native jump callbacks/exertion remain. Charged/Double redirect using movement keys; Vector replaces Double, launches along look, and looks down to brake rather than accelerate descent. Kinematics reuse accepted native movement evidence. Only successful impulses adjust fall distance; packet component bounds are transport constraints, not gameplay tuning. No teleport or collision bypass.");
        policy.addProperty("jumpPresentation", "Existing shared progress HUD and synchronized rank-resolved tooltips. Charged card shows elapsed charge and launch multiplier; ready air resource uses normal closing grace after spending. Local routing suppresses a claimed press until release to avoid accidental Elytra deployment; physical motion is server-approved, not applied twice by client prediction.");
        policy.addProperty("flightInput", "The existing bounded ordinary-Jump packet is shared by flight skills; no extra keybind or second transport. Fatigue thrust requires a fresh rolling heartbeat so delayed/stale held input cannot consume an entire stamina bar; Double/Vector Jump and Vector Boost remain edge-driven. Server elapsed ticks alone drain/recover stamina and recharge boost.");
        policy.addProperty("flightMotion", "Fatigue is server-authoritative jetpack acceleration: generated vertical thrust is relative to live gravity, added to existing Y velocity without canceling descent, and bounded by live native jump power; yaw-relative WASD steering reuses resolved Abilities#flyingSpeed as horizontal acceleration, preserving the existing equipment Flight Speed route. Untethered alone leases native mayfly/flying permission. Essence Wings keeps native Elytra pitch/lift while generated horizontal drag compensation improves glide carry. Vector Boost fills missing look-axis velocity toward a generated native-firework-relative cruise target; later ranks shorten recharge rather than increasing burst strength.");
        policy.addProperty("flightLifecycle", "Releasing or stale Jump input immediately stops Fatigue thrust; landing recharges stamina. Untethered retains its sustained native-flight lifecycle, airborne recovery and zero-stamina permission. Wings ends on landing/invalid states; its lifecycle bridge only preserves an already-started effective skill glide through native chest-Elytra validation.");
        policy.addProperty("flightPresentation", "All four flight skills use existing shared HUD cards and rank-resolved tooltip localization. Wings reports ready/gliding, true flight reports normalized stamina, and Vector Boost reports normalized recharge; cards remain presentation-only.");
        policy.addProperty("traversalTuning", "Movement magnitudes stay in the existing generated balance profile: flight thrust, glide retention and boost/recharge derive from semantic capability weights and rank scaling; binary terrain/aquatic traversal remains native restoration. No parallel TOML scalar, hidden tuning table or fake numeric rank.");
        policy.addProperty("terrainFreedom", "Tagged contact damage, drag callbacks and native below-identity block speed/jump factors; powder-snow/soul-sand/mud firm footing and freezing protection. Opt-in block/damage tags extend terrain support. No attacks, suffocation, falls, drowning, ordinary fire, lava or bypass-invulnerability immunity.");
        policy.addProperty("aquaticBody", "An exact-ID SUBMERGED_MINING_SPEED floor and scoped off-ground mining restoration remove underwater mining penalties. No WATER_MOVEMENT_EFFICIENCY floor or movement off-ground bypass: Swim Speed remains entirely with the existing bonus/equipment pipeline, including native scaling. Normal tools, haste/fatigue, air supply and rain behavior remain. Normal scene fog, not night vision or wall visibility.");
        policy.addProperty("waterWalking", "Sprint-only exposed tagged liquid surfaces at native FluidState height, including flowing levels. Actual collision/headroom with native stepping onto neighboring surfaces; never lifts immersed players. Crouch, stopping, mounts and flight disable. No placement, teleport, forced lift, fall reset or waterlogged-solid replacement.");
        policy.addProperty("lavaborn", "Scoped native swimming branch/input/steering in lava, using the actual bonus/equipment-controlled swimming speed. Only source-less tagged lava/fire contact damage is suppressed while immersed. The native lava ignition call is blocked at its source; unrelated fire timers are never cleared and no protection persists after exit. Clear vision omits only duplicated inward-facing lava quads, preserving exterior surfaces and solid-block occlusion. Meshes refresh on effective vision permission changes, never on immersion transitions. Native drowning/air state and other water-only gameplay remain untouched.");
        policy.addProperty("traversalPrediction", "Existing committed server evaluation and synchronized local snapshot; no drafts, extra packets or remote-player grants. Pure passive skills add no permanent HUD cards; descriptions and debug lines share registered handlers.");
        policy.addProperty("movementEvidence", "Accepted native grounded movement plus fresh existing input signal; one state transition per server tick. No buildup from input alone, packet count, mounts, swimming, climbing, flight, forced displacement or teleports. Brief missing movement samples and ordinary sprint jumps may hold, never build.");
        policy.addProperty("runningMomentum", "One normalized reservoir. Stopping, crouching, lost input or a sharp turn drains unless Rush retains. Only land sprinting applies the proportional speed modifier. Flight/water/climbing/mounts/teleports/dimension changes and lifecycle resets clear movement state and its exact-ID modifiers.");
        policy.addProperty("momentumVault", "Generated native fence collision height defines a step floor while charged, grounded and sprinting with measured movement. Normal collision/headroom semantics; no teleport, velocity launch, fall reset or block modification. Sneak disables; existing stronger additive/multiplicative step modifiers remain intact. Readiness shares Running Momentum's HUD card.");
        policy.addProperty("rush", "Confirmed attributed nonallied kills, including native projectile/caster and skill-proc kills, fill Running Momentum and refresh one retention window. No stacked timers, extra speed bonus or bypass of movement-mode/discontinuity resets. Separate standard timed HUD card.");
        policy.addProperty("projection", "Ground-speed magnitude is projected as conditional movement, vault as vertical access/convenience, Rush as retention convenience. Jump strengths project on JUMP/VERTICAL_MOVEMENT and conditional FALL_CONTROL only, not global combat EHP/DPS. Native launch is the baseline; square-root impulse maps generated impulse-squared headroom without claiming exact discrete-physics block heights. Ranks scale typed strengths/reduction/braking, not air-jump count. Momentum branches retain their own speed, vault-threshold and retention consumers.");
        policy.addProperty("evidenceLimit", "No observed route geometry, turning behavior, obstacle frequency or kill cadence is assumed. Dedicated-server movement prediction and representative-modpack acceptance need in-game verification.");
        return policy;
    }

    static JsonObject postureStatusPolicy() {
        JsonObject policy = new JsonObject();
        policy.addProperty("tuning", "/runtime/effects/posture and /runtime/effects/status; existing commented exact TOML, validated bounds, required schema fields and content fingerprint; no separate configuration");
        policy.addProperty("choices", "One Evasive/Bulwark/Adaptive posture and one independent Mirror/Pure State status choice. Switching, lost effectiveness or lifecycle discontinuity resets transient state");
        policy.addProperty("movement", "Ordinary input must correlate with measured server movement. Crouch, grounded motion, intentional swimming/climbing/flight and legitimate guarded movement qualify; mounts, idle falling, forced motion, teleports and correction do not. Finite stillness and turning hysteresis reject jitter");
        policy.addProperty("evasive", "Bounded chance at full posture; one server roll for hostile living-owned nonfire/nonexplosive projectiles or direct physical damage. Direct armor-bypassing or witch-resistant magic, unavoidable/admin/environmental damage and secondaries do not roll. Success and actual taken hits each pay their configured full-meter debit once. Intentional hit recovery holds charge for the forced-motion quiet interval without buildup; stopping still drains. No fabricated block, reflection or guard reward");
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
        JsonObject revision = document.section("metadata");
        if (!revision.has("generatorRevision")
                || !GENERATION_REVISION.equals(revision.get("generatorRevision").getAsString()))
            throw new IllegalArgumentException("Generated balance revision changed; explicitly rebuild with /essence admin balance rebuild. No legacy runtime migration is supported");
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
