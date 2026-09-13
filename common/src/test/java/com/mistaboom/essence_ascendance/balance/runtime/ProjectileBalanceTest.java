package com.mistaboom.essence_ascendance.balance.runtime;

import com.google.gson.JsonObject;
import com.mistaboom.essence_ascendance.balance.config.BalanceOverrides;
import com.mistaboom.essence_ascendance.balance.config.BalanceSettings;
import com.mistaboom.essence_ascendance.config.ProjectileBalanceSettings;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import com.mistaboom.essence_ascendance.progression.*;
import com.mistaboom.essence_ascendance.skill.Skills;
import com.mistaboom.essence_ascendance.stat.EssenceStats;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import java.util.List;
import java.util.function.Consumer;

/** Resolved schema, normalized seeds, strict overrides and unchanged Attunement policy. */
public final class ProjectileBalanceTest {
    private static int checks;
    public static void main(String[] args) {
        Thread.currentThread().setUncaughtExceptionHandler((thread,error) -> error.printStackTrace(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.err))));
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        EssenceTypes.init(); AscendanceTiers.init(); EssenceStats.init();
        MilestoneProviders.init(); Milestones.init(); Skills.init(); AscendanceAdvancements.init();
        com.mistaboom.essence_ascendance.equipment.EquipmentProfiles.init();
        var defaults = ProjectileBalanceSettings.defaults();
        check(defaults.payloadTriggerBudget() == 1 + defaults.penetrations(), "Payload continuation seed lost its existing path reference");
        check(defaults.explosiveRadius() == defaults.ricochetRadius() / 2, "Payload radius no longer shares path reference");
        check(defaults.rootMaxDurationTicks() == defaults.caster().lifetimeTicks(), "Root refresh seed lost lifetime reference");
        check(defaults.control().minimumSpeedFactor() > 0 && defaults.control().redirectBudget() == 1, "Invalid finite control/anti-loop policy");
        var first = RuntimeBalanceDefinition.bootstrap();
        var second = RuntimeBalanceDefinition.bootstrap();
        check(first.toJson().equals(second.toJson()), "New projectile generation is not deterministic");
        check(first.toJson().equals(RuntimeBalanceDefinition.fromJson(first.toJson()).toJson()), "New settings do not round trip");
        for (String section : List.of("payload", "control")) reject(first, j -> projectile(j).remove(section));
        reject(first, j -> projectile(j).getAsJsonObject("payload").addProperty("triggerBudget", -1));
        reject(first, j -> projectile(j).getAsJsonObject("payload").addProperty("rootDurationTicks", 201));
        reject(first, j -> projectile(j).getAsJsonObject("payload").addProperty("rootMaxDurationTicks", 1));
        reject(first, j -> projectile(j).getAsJsonObject("payload").addProperty("rootMovementTolerance", 0));
        reject(first, j -> projectile(j).getAsJsonObject("payload").addProperty("particleCount", 65));
        reject(first, j -> projectile(j).getAsJsonObject("payload").addProperty("rootRadius", 5));
        reject(first, j -> projectile(j).getAsJsonObject("control").addProperty("minimumSpeedFactor", 0));
        reject(first, j -> projectile(j).getAsJsonObject("control").addProperty("innerRadius",
                projectile(j).getAsJsonObject("control").get("outerRadius").getAsDouble()));
        reject(first, j -> projectile(j).getAsJsonObject("control").addProperty("scanCadenceTicks", 0));
        reject(first, j -> projectile(j).getAsJsonObject("control").addProperty("redirectBudget", 2));
        reject(first, j -> projectile(j).getAsJsonObject("control").addProperty("swingHalfAngleDegrees", 180));
        reject(first, j -> projectile(j).getAsJsonObject("control").addProperty("theftSpeedMultiplier", 0));
        reject(first, j -> projectile(j).getAsJsonObject("control").addProperty("theftTurnDegreesPerTick", 46));
        for (String section : List.of("payload", "control")) {
            for (var entry : projectile(first.toJson()).getAsJsonObject(section).entrySet()) {
                String name = entry.getKey();
                reject(first, j -> projectile(j).getAsJsonObject(section).remove(name));
                reject(first, j -> projectile(j).getAsJsonObject(section).addProperty(name, Double.NaN));
            }
        }
        var parsed = BalanceOverrides.parse("[exact]\n\"/runtime/effects/projectiles/payload/triggerBudget\" = 2\n"
                + "\"/runtime/effects/projectiles/control/minimumSpeedFactor\" = 0.4\n", "projectile test");
        check(parsed.exactValues().size() == 2, "Projectile settings require a parallel override engine");
        var overridden = RuntimeReferencePolicy.withBootstrapReferences(() -> RuntimeBalanceDefinition.generate(
                RuntimeReferencePolicy.bootstrapEvidence(), BalanceSettings.defaults(), parsed));
        check(overridden.config().skillEffects().projectiles().payloadTriggerBudget() == 2
                && overridden.config().skillEffects().projectiles().control().minimumSpeedFactor() == .4,
                "Exact projectile fields bypassed the existing validated generation pipeline");
        JsonObject tuned = first.toJson();
        projectile(tuned).getAsJsonObject("payload").addProperty("triggerBudget", 2);
        projectile(tuned).getAsJsonObject("control").addProperty("minimumSpeedFactor", 0.4);
        var changed = RuntimeBalanceDefinition.fromJson(tuned).withContentIdentity();
        check(!changed.config().balanceProfile().id().equals(first.config().balanceProfile().id()), "Changed payload/control settings reused cached content identity");
        check(changed.toJson().get("attunement").equals(first.toJson().get("attunement")), "Projectile tuning changed Attunement policy or chapter calibration");
        check(changed.attunement().policy().repetitionFloor() > 0, "Attunement repetition floor disappeared");
        var effects = first.config().skillEffects();
        var explosion = com.mistaboom.essence_ascendance.skill.SkillIds.EXPLOSIVE_PAYLOAD;
        double one = effects.projectiles().explosiveDamageScale() * effects.combustion().targetsPerBurst();
        check(RuntimeBuildScenarios.projectilePayloadArea(effects, java.util.Set.of(explosion)) == one, "Explosion primary contact model omitted bounded nearby targets");
        double retained = effects.projectiles().piercingDamageMultiplier();
        double expected = one * (1 + retained + retained * retained);
        check(Math.abs(RuntimeBuildScenarios.projectilePayloadArea(effects,
                java.util.Set.of(explosion, com.mistaboom.essence_ascendance.skill.SkillIds.PIERCING_PROJECTILE)) - expected) < 1e-12,
                "Continuing payload ignored path damage retention or trigger budget");
        check(RuntimeBuildScenarios.projectilePayloadArea(effects, java.util.Set.of(
                com.mistaboom.essence_ascendance.skill.SkillIds.ROOTING_PAYLOAD,
                com.mistaboom.essence_ascendance.skill.SkillIds.PROJECTILE_DRAG_FIELD,
                com.mistaboom.essence_ascendance.skill.SkillIds.INTERCEPTOR,
                com.mistaboom.essence_ascendance.skill.SkillIds.TRAJECTORY_THEFT)) == 0,
                "Control-only skills fabricated numeric damage");
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out)).println("ProjectileBalanceTest: " + checks + " deterministic schema, finite bounds, strict missing/unknown values, fingerprints and unchanged Attunement checks PASS");
    }
    private static JsonObject projectile(JsonObject json) { return json.getAsJsonObject("effects").getAsJsonObject("projectiles"); }
    private static void reject(RuntimeBalanceDefinition runtime, Consumer<JsonObject> mutation) {
        JsonObject json = runtime.toJson(); mutation.accept(json);
        try { RuntimeBalanceDefinition.fromJson(json); }
        catch (RuntimeException expected) { checks++; return; }
        throw new AssertionError("Malformed projectile profile accepted: " + json.getAsJsonObject("effects").get("projectiles"));
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); checks++; }
}
