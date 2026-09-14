package com.mistaboom.essence_ascendance.projectile;

import com.mistaboom.essence_ascendance.equipment.EquipmentDamageService;
import com.mistaboom.essence_ascendance.equipment.EquipmentShieldService;
import com.mistaboom.essence_ascendance.guard.GuardLifecycle;
import com.mistaboom.essence_ascendance.guard.GuardReflectionEffects;
import com.mistaboom.essence_ascendance.guard.ReflectionRouter;
import com.mistaboom.essence_ascendance.item.AscendanceItems;
import com.mistaboom.essence_ascendance.skill.SkillGroups;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.SkillRegistry;
import com.mistaboom.essence_ascendance.skill.effect.GuardCounterattackService;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudCards;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudEntry;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudPresentation;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime;
import com.mistaboom.essence_ascendance.skill.effect.SkillProcDamageService;
import com.mojang.serialization.Lifecycle;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;

/** Native Player.hurt/block plus deterministic mitigation/cancellation at the target's health-write boundary. */
public final class NativeGuardOutcomeTest {
    private static int assertions;
    private NativeGuardOutcomeTest() { }

    public static void run() throws ReflectiveOperationException {
        if (!Boolean.getBoolean("essence.projectile.nativeHookTest")) throw new IllegalStateException("Explicit no-world fixture required");
        var f = new ProjectileNativeInterceptionTest.Fixture(NativePlayer.class);
        registry(f);
        var player = f.player;
        var attacker = f.player(MeasuredTarget.class, new Vec3(0, 0, 2), "guard_attacker");
        attacker.scale = .5;
        var neighbor = f.player(MeasuredTarget.class, new Vec3(1, 0, 1), "reprisal_neighbor");
        neighbor.scale = 1;
        var shield = new ItemStack(AscendanceItems.ASCENDANCE_SHIELD.get());
        player.setItemInHand(InteractionHand.OFF_HAND, shield);
        player.startUsingItem(InteractionHand.OFF_HAND);
        long start = GuardLifecycle.observe(player).startTick();
        player.startUsingItem(InteractionHand.OFF_HAND);
        check(GuardLifecycle.observe(player).startTick() == start, "Duplicate native raise packet cannot reset timing");
        int delay = EquipmentShieldService.raiseDelayTicks(player, shield);
        ProjectileNativeInterceptionTest.set(LivingEntity.class, player, "useItemRemaining", shield.getUseDuration(player) - delay);
        f.level.tick += delay;
        check(player.isBlocking() && GuardLifecycle.observe(player).nativeReady(), "Native resolved raise delay actually gates blocking");
        var source = player.damageSources().playerAttack(attacker);
        int durability = shield.getDamageValue();
        boolean accepted = player.hurt(source, 8);
        var outcome = EquipmentDamageService.lastGuardOutcome(player).orElseThrow();
        check(!accepted && outcome.successfulBlock() && outcome.blocked() == 8, "Actual Player.hurt false return still commits a complete native shield block");
        check(shield.getDamageValue() > durability, "Real native shield durability path executes");
        check(outcome.perfect() && outcome.healthLost() == 0 && outcome.absorptionLost() == 0, "Actual native block records perfect timing and zero loss");
        check(outcome.requestedReflection() > 0, "Base shield reflection works with no guard skill receipts");
        equal(outcome.confirmedReflection(), outcome.requestedReflection() * .5, "Reflection records actual mitigated health loss, not request");
        check(neighbor.hits == 0, "No unowned Crowd Reprisal effect");
        int hits = attacker.hits;
        EquipmentDamageService.endDamage(player, source, true, accepted);
        check(attacker.hits == hits, "Duplicate native completion cannot reflect twice");

        var data = f.saved.getPlayerData(player.getUUID());
        data.grantAllSkillsForAdmin(List.of(SkillRegistry.require(SkillIds.REFLEXIVE_WARD),
                SkillRegistry.require(SkillIds.GUARD_AMPLIFIER), SkillRegistry.require(SkillIds.CROWD_REPRISAL), SkillRegistry.require(SkillIds.RIPOSTE)));
        data.setLoadoutSelection(SkillGroups.DEFENSE_BLOCK_REWARD, SkillIds.GUARD_AMPLIFIER);
        SkillEffectRuntime.refresh(player);
        check(!amplifierCard(player).active(), "Effective empty Amplifier emits an inactive default card");
        player.stopUsingItem(); player.startUsingItem(InteractionHand.OFF_HAND);
        ProjectileNativeInterceptionTest.set(LivingEntity.class, player, "useItemRemaining", shield.getUseDuration(player) - delay);
        f.level.tick += delay;
        // Service-scope commit below isolates simultaneous rewards from native hurt resistance cooldown.
        blocked(player, source, 8);
        outcome = EquipmentDamageService.lastGuardOutcome(player).orElseThrow();
        var tuning = SkillEffectRuntime.resolvedSettings(player).guard();
        equal(outcome.skillAmplifier(), tuning.amplifier().maximumMultiplier(), "Same perfect native outcome promotes amplifier before reflection");
        var amplifierHud = amplifierCard(player);
        check(amplifierHud.active() && amplifierHud.accent() == 0xFF70BCD4, "Native Amplifier card has an opaque shared accent");
        check(amplifierHud.badge().equals(SkillEffectHudEntry.Text.translated("hud.essence_ascendance.percent", "100")),
                "Amplifier uses a compact normalized badge instead of a long effect sentence");
        check(amplifierHud.lines().equals(List.of(SkillEffectHudEntry.Text.translated("hud.essence_ascendance.guard.amplifier",
                        SkillEffectHudCards.decimal(tuning.amplifier().maximumMultiplier()))))
                        && amplifierHud.meter().kind() == SkillEffectHudEntry.MeterKind.TIMER,
                "Amplifier keeps its actual multiplier in a detail line above the shared timer footer");
        check(GuardCounterattackService.bonusReach(player) > 0, "Same perfect outcome also arms Riposte");
        equal(outcome.requestedReflection(), (outcome.nativeBlockReflection() + outcome.investedBlockReflection()) * outcome.skillAmplifier(),
                "Primary reflection is amplified exactly once");
        check(neighbor.hits == 1 && neighbor.lastSkill.equals(SkillIds.CROWD_REPRISAL), "Confirmed primary reflection hits neighbor with Crowd Reprisal metadata");
        equal(neighbor.lastRequest, outcome.confirmedReflection() * tuning.reprisal().damageScale(), "Crowd Reprisal derives from confirmed primary and is not amplified again");
        check(neighbor.lastSecondary && neighbor.lastOwner == player, "Secondary damage preserves defender ownership and recursion suppression");
        hits = neighbor.hits;
        attacker.cancel = true;
        blocked(player, source, 8);
        outcome = EquipmentDamageService.lastGuardOutcome(player).orElseThrow();
        check(outcome.confirmedReflection() == 0 && neighbor.hits == hits, "Canceled reflected hurt never grants Crowd Reprisal");
        attacker.cancel = false; attacker.scale = 0;
        blocked(player, source, 8);
        check(EquipmentDamageService.lastGuardOutcome(player).orElseThrow().confirmedReflection() == 0 && neighbor.hits == hits,
                "Accepted zero-loss reflection never grants Crowd Reprisal");
        attacker.scale = .5;
        attacker.setHealth(20); player.stopUsingItem();

        // Measured armor/magic prevention contract: an accepted fully mitigated hostile hit can reflect with Ward.
        EquipmentDamageService.beginDamage(player, source, 6);
        EquipmentDamageService.recordGuardPrevention(player, source, 6, 0);
        EquipmentDamageService.endDamage(player, source, true, true);
        outcome = EquipmentDamageService.lastGuardOutcome(player).orElseThrow();
        check(outcome.wardExtension() > 0 && outcome.blocked() == 0, "Ward extends a proved fully mitigated non-block hit");
        EquipmentDamageService.beginDamage(player, source, 6);
        EquipmentDamageService.recordGuardPrevention(player, source, 6, 0);
        EquipmentDamageService.endDamage(player, source, true, false);
        outcome = EquipmentDamageService.lastGuardOutcome(player).orElseThrow();
        check(outcome.wardExtension() == 0 && outcome.confirmedReflection() == 0, "Canceled mitigation probe is never a combat reward");
        EquipmentDamageService.beginDamage(player, source, 0);
        EquipmentDamageService.endDamage(player, source, true, false);
        check(EquipmentDamageService.lastGuardOutcome(player).orElseThrow().requestedReflection() == 0, "Zero probes do not reflect");
        var environment = player.damageSources().generic();
        EquipmentDamageService.beginDamage(player, environment, 6);
        EquipmentDamageService.recordGuardPrevention(player, environment, 6, 0);
        EquipmentDamageService.endDamage(player, environment, true, true);
        check(EquipmentDamageService.lastGuardOutcome(player).orElseThrow().responsible() == null, "Ownerless environment has no retaliation target");

        // Real native health/absorption writes, with a nested write subtracted from the enclosing hit.
        player.setHealth(20);
        player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_ABSORPTION).setBaseValue(4);
        player.setAbsorptionAmount(4);
        EquipmentDamageService.beginDamage(player, source, 5);
        EquipmentDamageService.observeSkillHealthDamage(player, source, () -> {
            player.setAbsorptionAmount(2);
            EquipmentDamageService.observeSkillHealthDamage(player, environment, () -> player.setHealth(player.getHealth() - 3));
        });
        EquipmentDamageService.endDamage(player, source, true, true);
        outcome = EquipmentDamageService.lastGuardOutcome(player).orElseThrow();
        equal(outcome.absorptionLost(), 2, "Absorption loss is measured separately");
        equal(outcome.healthLost(), 0, "Nested unrelated health loss is excluded from enclosing outcome");
        nestedMeasurements(player, source, environment);
        player.stopUsingItem();
        player.setHealth(20); player.setAbsorptionAmount(4);
        player.invulnerableTime = 0;
        check(player.hurt(source, 6), "Actual native lowered-shield hit accepts ordinary health/absorption damage");
        outcome = EquipmentDamageService.lastGuardOutcome(player).orElseThrow();
        equal(outcome.healthLost(), 2, "Native armor/absorption ordering leaves the expected health loss");
        equal(outcome.absorptionLost(), 4, "Native absorption damage is kept separately");
        equal(outcome.ordinaryReflection(), 2 * EquipmentDamageService.evaluateStats(player).damageReflectionPercent() / 100,
                "Ordinary reflection shares the single exact health-only measurement");
        equal(outcome.wardExtension(), 0, "A partially damaging hit cannot also add fully-prevented Ward reflection");
        long previousEvent = outcome.eventId();
        ProjectileNativeInterceptionTest.set(ServerPlayer.class, player, "spawnInvulnerableTime", 5);
        check(!player.hurt(source, 6), "Native ServerPlayer spawn immunity rejects before Player.hurt");
        outcome = EquipmentDamageService.lastGuardOutcome(player).orElseThrow();
        check(outcome.eventId() > previousEvent && !outcome.accepted() && outcome.healthLost() == 0
                        && outcome.requestedReflection() == 0 && outcome.echo().requested() == 0,
                "Early spawn rejection still finalizes a fresh empty guard outcome");
        ProjectileNativeInterceptionTest.set(ServerPlayer.class, player, "spawnInvulnerableTime", 0);

        var arrow = f.arrow(Arrow.class);
        ProjectileOwnership.transferNative(arrow, attacker);
        var projectile = player.damageSources().arrow(arrow, attacker);
        check(ReflectionRouter.source(player, projectile).target() == attacker, "Projectile reflection resolves real living shooter");
        ProjectileOwnership.transferNative(arrow, neighbor);
        check(!ReflectionRouter.source(player, projectile).eligible(), "Mismatched projectile owner fails closed");
        var indirect = player.damageSources().indirectMagic(arrow, attacker);
        check(!ReflectionRouter.source(player, indirect).eligible(), "Indirect projectile source also obeys current ownership");
        attacker.getAbilities().invulnerable = true;
        check(!ReflectionRouter.source(player, source).eligible(), "Creative source cannot receive retaliation");
        attacker.getAbilities().invulnerable = false;
        ProjectileNativeInterceptionTest.set(net.minecraft.server.MinecraftServer.class, f.server, "pvp", false);
        check(!ReflectionRouter.source(player, source).eligible(), "Server PvP rejection is preserved by reflection");
        previousEvent = EquipmentDamageService.lastGuardOutcome(player).orElseThrow().eventId();
        check(!player.hurt(source, 6), "Native ServerPlayer PvP rejection exits before Player.hurt");
        outcome = EquipmentDamageService.lastGuardOutcome(player).orElseThrow();
        check(outcome.eventId() > previousEvent && !outcome.accepted() && outcome.healthLost() == 0
                        && outcome.requestedReflection() == 0, "Early PvP rejection finalizes a fresh empty outcome");
        ProjectileNativeInterceptionTest.set(net.minecraft.server.MinecraftServer.class, f.server, "pvp", true);
        var allies = f.level.scoreboard.addPlayerTeam("guard_fixture_allies");
        allies.setAllowFriendlyFire(false);
        f.level.scoreboard.addPlayerToTeam(player.getScoreboardName(), allies);
        f.level.scoreboard.addPlayerToTeam(attacker.getScoreboardName(), allies);
        check(!ReflectionRouter.source(player, source).eligible(), "Team friendly-fire rejection is preserved");
        f.level.scoreboard.removePlayerFromTeam(player.getScoreboardName(), allies);
        f.level.scoreboard.removePlayerFromTeam(attacker.getScoreboardName(), allies);
        var recursive = EquipmentDamageService.withReflection(() -> ReflectionRouter.reflect(player,
                ReflectionRouter.source(player, source), 4, false).confirmed() > 0);
        check(!recursive, "Reflection recursion is rejected");
        player.stopUsingItem(); player.startUsingItem(InteractionHand.OFF_HAND);
        long ready = GuardLifecycle.observe(player).readyTick();
        f.level.tick = ready - 1;
        ProjectileNativeInterceptionTest.set(LivingEntity.class, player, "useItemRemaining", shield.getUseDuration(player) - delay + 1);
        check(!player.isBlocking(), "One tick before resolved readiness cannot block");
        f.level.tick = ready;
        ProjectileNativeInterceptionTest.set(LivingEntity.class, player, "useItemRemaining", shield.getUseDuration(player) - delay);
        check(player.isBlocking(), "Exact resolved readiness may block");
        f.level.tick = ready + tuning.perfectGuard().windowTicks() - 1;
        blocked(player, source, 1);
        check(EquipmentDamageService.lastGuardOutcome(player).orElseThrow().perfect(), "Last configured perfect-window tick is included");
        f.level.tick++;
        blocked(player, source, 1);
        check(!EquipmentDamageService.lastGuardOutcome(player).orElseThrow().perfect(), "First tick after perfect window is excluded");
        GuardLifecycle.invalidateTiming(player);
        check(GuardLifecycle.observe(player).readyTick() < 0, "Loadout lifecycle invalidates timing without fabricating a new raise");
        attacker.setHealth(20); attacker.scale = 0;
        player.setDeltaMovement(Vec3.ZERO);
        EquipmentDamageService.withExplosionHit(player, source, () -> {
            EquipmentDamageService.beginDamage(player, source, 5);
            EquipmentDamageService.recordGuardPrevention(player, source, 5, 0);
            EquipmentDamageService.endDamage(player, source, true, true);
            return true;
        });
        EquipmentDamageService.explosionKnockback(player, source, new Vec3(.4, 0, 0), new Vec3(.4, 0, 0),
                () -> player.setDeltaMovement(.4, 0, 0));
        outcome = EquipmentDamageService.lastGuardOutcome(player).orElseThrow();
        check(outcome.echo().requested() > 0 && outcome.knockbackDecision().equals("native_explosion_resolved"),
                "Correlated post-hurt explosion impulse reaches Ward once");
        equal(outcome.attemptedKnockback().x, .4, "Explosion diagnostic retains positive attempted force");
        Vec3 echoed = attacker.getDeltaMovement();
        EquipmentDamageService.explosionKnockback(player, source, new Vec3(.4, 0, 0), new Vec3(.4, 0, 0), () -> {});
        equal(attacker.getDeltaMovement().distanceToSqr(echoed), 0, "Duplicate explosion callback cannot echo twice");
        EquipmentDamageService.withExplosionHit(player, source, () -> {
            EquipmentDamageService.beginDamage(player, source, 5);
            EquipmentDamageService.endDamage(player, source, true, true);
            return true;
        });
        EquipmentDamageService.explosionKnockback(player, environment, new Vec3(.4, 0, 0), new Vec3(.4, 0, 0), () -> {});
        check(EquipmentDamageService.lastGuardOutcome(player).orElseThrow().echo().requested() == 0,
                "Different source cannot borrow a correlated explosion hit");
        EquipmentDamageService.withExplosionHit(player, environment, () -> {
            EquipmentDamageService.beginDamage(player, environment, 5);
            EquipmentDamageService.endDamage(player, environment, true, true);
            return true;
        });
        EquipmentDamageService.explosionKnockback(player, environment, new Vec3(.4, 0, 0), new Vec3(.4, 0, 0), () -> {});
        check(EquipmentDamageService.lastGuardOutcome(player).orElseThrow().echo().requested() == 0,
                "Ownerless explosion cannot echo toward a guessed source");
        var amplifierPresentation = new SkillEffectHudPresentation();
        amplifierPresentation.replace(List.of(amplifierHud), 100);
        f.level.tick += tuning.amplifier().durationTicks();
        var expiredAmplifierHud = amplifierCard(player);
        check(!expiredAmplifierHud.active(), "Natural Amplifier expiry keeps its effective inactive card identity");
        amplifierPresentation.replace(List.of(expiredAmplifierHud), 101);
        check(amplifierPresentation.visibleEntries(160).equals(List.of(amplifierHud))
                        && amplifierPresentation.visibleEntries(161).isEmpty(),
                "Actual Amplifier snapshots share the offense sixty-tick closing behavior");
        f.close();
        System.out.println("Native guard outcomes passed: " + assertions + " (actual Player.hurt/block/durability; controlled measured retaliation/cancellation/area outcomes; no world)");
    }

    private static void blocked(ServerPlayer player, DamageSource source, float amount) {
        EquipmentDamageService.beginDamage(player, source, amount);
        EquipmentDamageService.captureBlockingShield(player, source);
        EquipmentDamageService.recordBlockedDamage(player, source, amount);
        EquipmentDamageService.recordBlockedDamage(player, source, amount);
        EquipmentDamageService.commitBlock(player, source);
        EquipmentDamageService.commitBlock(player, source);
        EquipmentDamageService.endDamage(player, source, true, false);
    }

    private static SkillEffectHudEntry amplifierCard(ServerPlayer player) {
        return SkillEffectRuntime.hudSnapshot(player).entries().stream()
                .filter(entry -> entry.sourceSkill().equals(SkillIds.GUARD_AMPLIFIER)).findFirst().orElseThrow();
    }

    /** Controlled real health writes isolate exact measurement identity even when a nested proc reuses its source. */
    private static void nestedMeasurements(ServerPlayer player, DamageSource source, DamageSource nestedSource) {
        player.setHealth(20); player.setAbsorptionAmount(0);
        var measured = EquipmentDamageService.measureDamage(player, source, () -> {
            EquipmentDamageService.observeSkillHealthDamage(player, source, () -> {
                player.setHealth(player.getHealth() - 2);
                EquipmentDamageService.observeSkillHealthDamage(player, source, () -> player.setHealth(player.getHealth() - 3));
            });
            return true;
        });
        equal(measured.loss(), 2, "Unframed controlled target binds the first exact sample, excluding nested same-source loss");
        equal(player.getHealth(), 15, "The excluded nested same-source damage still actually changes health");

        player.setHealth(20);
        measured = EquipmentDamageService.measureDamage(player, source, () ->
                EquipmentDamageService.withSkillDamageFrame(player, source, () -> {
                    EquipmentDamageService.observeSkillHealthDamage(player, source, () -> {
                        player.setHealth(player.getHealth() - 2);
                        EquipmentDamageService.withSkillDamageFrame(player, source, () -> {
                            EquipmentDamageService.observeSkillHealthDamage(player, source, () -> player.setHealth(player.getHealth() - 3));
                            return true;
                        });
                    });
                    return true;
                }));
        equal(measured.loss(), 2, "Native outer damage frame excludes a nested same-source frame during its health sample");

        player.setHealth(20);
        measured = EquipmentDamageService.measureDamage(player, source, () ->
                EquipmentDamageService.withSkillDamageFrame(player, source, () -> {
                    EquipmentDamageService.withSkillDamageFrame(player, source, () -> {
                        EquipmentDamageService.observeSkillHealthDamage(player, source, () -> player.setHealth(player.getHealth() - 3));
                        return true;
                    });
                    EquipmentDamageService.observeSkillHealthDamage(player, source, () -> player.setHealth(player.getHealth() - 2));
                    return true;
                }));
        equal(measured.loss(), 2, "Frame identity excludes nested same-source damage before the root health sample begins");

        player.setHealth(20);
        EquipmentDamageService.DamageResult[] nested = {null};
        measured = EquipmentDamageService.measureDamage(player, source, () ->
                EquipmentDamageService.withSkillDamageFrame(player, source, () -> {
                    EquipmentDamageService.observeSkillHealthDamage(player, source, () -> {
                        player.setHealth(player.getHealth() - 1);
                        nested[0] = EquipmentDamageService.measureDamage(player, nestedSource, () ->
                                EquipmentDamageService.withSkillDamageFrame(player, nestedSource, () -> {
                                    EquipmentDamageService.observeSkillHealthDamage(player, nestedSource,
                                            () -> player.setHealth(player.getHealth() - 3));
                                    return true;
                                }));
                        player.setHealth(player.getHealth() - 1);
                    });
                    return true;
                }));
        equal(nested[0].loss(), 3, "Nested distinct measured action receives only its own loss");
        equal(measured.loss(), 2, "Nested measured action restores the enclosing measurement for its remaining health writes");

        player.setHealth(20);
        measured = EquipmentDamageService.measureDamage(player, source, () ->
                EquipmentDamageService.withSkillDamageFrame(player, source, () -> {
                    EquipmentDamageService.withSkillDamageFrame(player, source, () -> {
                        EquipmentDamageService.observeSkillHealthDamage(player, source, () -> player.setHealth(player.getHealth() - 3));
                        return true;
                    });
                    return false;
                }));
        check(!measured.accepted() && measured.loss() == 0,
                "Canceled root frame cannot borrow positive nested same-source damage as confirmed loss");
    }

    @SuppressWarnings("unchecked")
    private static void registry(ProjectileNativeInterceptionTest.Fixture fixture) throws ReflectiveOperationException {
        var registry = new MappedRegistry<DamageType>(Registries.DAMAGE_TYPE, Lifecycle.stable());
        for (var field : DamageTypes.class.getDeclaredFields()) {
            if (field.getType() != ResourceKey.class) continue;
            var key = (ResourceKey<DamageType>) field.get(null);
            registry.register(key, new DamageType(key.location().getPath(), .1F), RegistrationInfo.BUILT_IN);
        }
        registry.freeze();
        registry.bindTags(new HashMap<>(java.util.Map.of(DamageTypeTags.IS_PROJECTILE,
                List.of(registry.getHolderOrThrow(DamageTypes.ARROW)))));
        fixture.level.memoryRegistries = new RegistryAccess.ImmutableRegistryAccess(List.of(registry)).freeze();
        fixture.level.memoryDamageSources = new DamageSources(fixture.level.memoryRegistries);
        var managers = ProjectileNativeInterceptionTest.instance(net.minecraft.server.ReloadableServerResources.class);
        ProjectileNativeInterceptionTest.set(net.minecraft.server.ReloadableServerResources.class, managers, "fullRegistryHolder",
                new net.minecraft.server.ReloadableServerRegistries.Holder(fixture.level.memoryRegistries.freeze()));
        var resourcesType = Class.forName("net.minecraft.server.MinecraftServer$ReloadableResources");
        var constructor = resourcesType.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        ProjectileNativeInterceptionTest.set(net.minecraft.server.MinecraftServer.class, fixture.server, "resources",
                constructor.newInstance(null, managers));
    }

    /** Cosmetic/stat/network boundaries are silent; actual native Player.hurt/block/health logic is inherited. */
    static class NativePlayer extends ServerPlayer {
        private NativePlayer() { super(null, null, null, null); throw new AssertionError("No player constructor in fixture"); }
        @Override public void awardStat(Stat<?> statistic, int amount) { }
        @Override public void onEnterCombat() { }
        @Override public void onLeaveCombat() { }
        @Override public void indicateDamage(double x, double z) { }
    }

    /** The native health-write adapter measures this controlled target, independent of native retaliation request. */
    static final class MeasuredTarget extends NativePlayer {
        double scale; boolean cancel; int hits; float lastRequest;
        net.minecraft.resources.ResourceLocation lastSkill;
        ServerPlayer lastOwner; boolean lastSecondary;
        private MeasuredTarget() { throw new AssertionError("No player constructor in fixture"); }
        @Override public boolean hurt(DamageSource source, float amount) {
            hits++; lastRequest = amount;
            var proc = SkillProcDamageService.current();
            lastSkill = proc == null ? null : proc.sourceSkill();
            lastOwner = proc == null ? null : proc.owner();
            lastSecondary = EquipmentDamageService.isSecondarySkillDamage();
            if (cancel) return false;
            EquipmentDamageService.observeSkillHealthDamage(this, source,
                    () -> setHealth((float) Math.max(0, getHealth() - amount * scale)));
            return true;
        }
    }
    private static void equal(double actual, double expected, String message) {
        check(Math.abs(actual - expected) < 1.0E-5, message + ": " + actual + " != " + expected);
    }
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
