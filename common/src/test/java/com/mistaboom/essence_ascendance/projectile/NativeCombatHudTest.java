package com.mistaboom.essence_ascendance.projectile;

import com.mistaboom.essence_ascendance.attunement.AttunementGameplay;
import com.mistaboom.essence_ascendance.equipment.EquipmentDamageService;
import com.mistaboom.essence_ascendance.equipment.EquipmentShieldService;
import com.mistaboom.essence_ascendance.item.AscendanceItems;
import com.mistaboom.essence_ascendance.posture.PostureService;
import com.mistaboom.essence_ascendance.skill.SkillGroups;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.SkillRegistry;
import com.mistaboom.essence_ascendance.skill.effect.CombatHudActivity;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudEntry;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Loader-transformed damage and real saved-skill HUD snapshots; no world or player constructors run. */
public final class NativeCombatHudTest {
    private static int checks;
    private NativeCombatHudTest() { }

    public static void run() throws ReflectiveOperationException {
        if (!Boolean.getBoolean("essence.projectile.nativeHookTest")) throw new IllegalStateException("Explicit no-world fixture required");
        var f = new ProjectileNativeInterceptionTest.Fixture(NativeGuardOutcomeTest.NativePlayer.class);
        NativeGuardOutcomeTest.registry(f);
        var p = f.player;
        var other = f.player(NativeGuardOutcomeTest.NativePlayer.class, new Vec3(0, 0, 2), "combat_hud_attacker");
        captureFixtureMilestones(f, other);
        var source = p.damageSources().playerAttack(other);
        var outgoing = p.damageSources().playerAttack(p);
        var data = f.saved.getPlayerData(p.getUUID());
        data.grantAllSkillsForAdmin(List.of(SkillRegistry.require(SkillIds.EVASIVE_CURRENT),
                SkillRegistry.require(SkillIds.BULWARK_STANCE), SkillRegistry.require(SkillIds.ADAPTIVE_GUARD)));
        select(f, SkillIds.ADAPTIVE_GUARD);
        CombatHudActivity.clear();
        check(!CombatHudActivity.active(p) && !card(p, SkillIds.ADAPTIVE_GUARD).active(), "Idle posture starts without a visible card");
        ready(p);
        check(p.hurt(source, 4), "Real native incoming player damage accepted");
        check(CombatHudActivity.active(p) && CombatHudActivity.active(other), "Measured native health damage marks both eligible players");
        check(CombatHudActivity.remainingTicks(p) == 100, "New native combat activity lasts exactly one hundred ticks");
        check(card(p, SkillIds.ADAPTIVE_GUARD).active() && PostureService.snapshot(p).stacks() == 1,
                "First real native hit publishes the effective Adaptive Guard card");
        var gameplayBeforeHud = PostureService.snapshot(p);
        long revisionBeforeHud = data.nexusRevision();
        data.setSkillHudEnabled(SkillIds.ADAPTIVE_GUARD, false);
        check(SkillEffectRuntime.hudSnapshot(p).entries().stream().noneMatch(e -> e.sourceSkill().equals(SkillIds.ADAPTIVE_GUARD)),
                "Hidden preference removes the real server card immediately");
        check(PostureService.snapshot(p).equals(gameplayBeforeHud) && data.nexusRevision() == revisionBeforeHud,
                "Native posture and progression remain identical when HUD is hidden");
        data.setSkillHudEnabled(SkillIds.ADAPTIVE_GUARD, true);
        check(card(p, SkillIds.ADAPTIVE_GUARD).active(), "Re-enabling presentation restores the existing native state");
        f.level.tick += 99;
        check(CombatHudActivity.active(p) && CombatHudActivity.remainingTicks(p) == 1, "Combat includes its final tick");
        f.level.tick++;
        check(!CombatHudActivity.active(p) && CombatHudActivity.remainingTicks(p) == 0, "Combat expires at the exact five-second boundary");
        check(card(p, SkillIds.ADAPTIVE_GUARD).active(), "Adaptive memory card remains independent of the shorter combat presentation window");

        SkillEffectRuntime.reset(p); select(f, SkillIds.ADAPTIVE_GUARD); CombatHudActivity.clear(); ready(p);
        check(p.hurt(p.damageSources().generic(), 4), "Native environmental health loss accepted");
        check(!CombatHudActivity.active(p) && card(p, SkillIds.ADAPTIVE_GUARD).active(),
                "Environmental adaptation is visible without classifying environmental damage as combat");
        CombatHudActivity.clear(); ready(p);
        p.hurt(source, 0);
        check(!CombatHudActivity.active(p), "Zero native probes cannot begin combat regardless of the native return value");
        EquipmentDamageService.withSecondarySkillDamage(() -> p.hurt(source, 4));
        check(!CombatHudActivity.active(p) && !CombatHudActivity.active(other), "Secondary native health writes do not start combat");
        ready(p); EquipmentDamageService.withReflection(() -> p.hurt(source, 4));
        check(!CombatHudActivity.active(p) && !CombatHudActivity.active(other), "Reflected native health writes do not start combat");
        float beforeNested = other.getHealth();
        EquipmentDamageService.withSkillDamageFrame(p, source, () -> {
            EquipmentDamageService.observeSkillHealthDamage(p, source, () ->
                    EquipmentDamageService.withSkillDamageFrame(other, outgoing, () -> {
                        EquipmentDamageService.observeSkillHealthDamage(other, outgoing, () -> other.setHealth(beforeNested - 1));
                        return true;
                    }));
            return true;
        });
        check(other.getHealth() == beforeNested - 1 && !CombatHudActivity.active(p) && !CombatHudActivity.active(other),
                "A nested-only measured health write cannot start combat for an outer frame with no loss");
        ready(p); p.hurt(source, 4); f.level.tick += 10;
        long remaining = CombatHudActivity.remainingTicks(p);
        p.hurt(source, 4);
        check(CombatHudActivity.remainingTicks(p) == remaining, "Native hurt-cooldown rejection cannot refresh combat");
        ready(p); EquipmentDamageService.withSecondarySkillDamage(() -> p.hurt(source, 4));
        check(CombatHudActivity.remainingTicks(p) == remaining, "Secondary damage cannot extend an existing combat window");
        ready(p); EquipmentDamageService.withReflection(() -> p.hurt(source, 4));
        check(CombatHudActivity.remainingTicks(p) == remaining, "Reflection cannot extend an existing combat window");
        ready(p); p.hurt(p.damageSources().generic(), 4);
        check(CombatHudActivity.remainingTicks(p) == remaining, "Environmental damage cannot extend an existing combat window");
        ready(p); p.hurt(source, 4);
        check(CombatHudActivity.remainingTicks(p) == 100 && CombatHudActivity.remainingTicks(other) == 100,
                "A later positive native primary hit refreshes both players to a full combat window");

        CombatHudActivity.clear();
        p.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(4); p.setAbsorptionAmount(4); ready(p);
        check(p.hurt(source, 2) && p.getHealth() == 20 && p.getAbsorptionAmount() < 4,
                "Native absorption-only hit has real absorbed loss and no health loss");
        check(CombatHudActivity.active(p), "Absorption-only native damage counts as combat");
        p.setAbsorptionAmount(0);
        CombatHudActivity.clear(); ready(other);
        check(other.hurt(outgoing, 4) && CombatHudActivity.active(p), "Real native outgoing damage starts the attacking player's combat window");
        var dying = f.player(DyingPlayer.class, new Vec3(0, 0, 2), "combat_hud_kill");
        captureFixtureMilestones(f, dying);
        dying.setHealth(1); CombatHudActivity.clear();
        check(dying.hurt(outgoing, 4) && dying.getHealth() == 0 && dying.deaths == 1, "Native outgoing lethal health write reaches the controlled death boundary");
        check(CombatHudActivity.active(p), "A killing hit still starts the surviving attacker's combat window");

        sourcePolicy(f, p, other, source, outgoing);
        CombatHudActivity.clear(); select(f, SkillIds.EVASIVE_CURRENT); chargeEvasive(f);
        check(PostureService.snapshot(p).meter() == 1 && !card(p, SkillIds.EVASIVE_CURRENT).active(),
                "Ordinary movement builds gameplay meter while the native HUD stays quiet outside combat");
        ready(other); other.hurt(outgoing, 4);
        check(card(p, SkillIds.EVASIVE_CURRENT).active(), "Real outgoing combat reveals the already charged Evasive Current card");
        f.level.tick += 100;
        check(!card(p, SkillIds.EVASIVE_CURRENT).active() && PostureService.snapshot(p).meter() == 1,
                "Evasive card expires without changing the server gameplay meter");
        other.setPos(p.getX(), p.getY(), p.getZ() + 2); ready(p); CombatHudActivity.clear();
        check(!EquipmentDamageService.withPostureTestRoll(() -> 0, () -> p.hurt(source, 4)), "Actual native deterministic Evasive dodge causes no damage");
        check(CombatHudActivity.active(p) && !CombatHudActivity.active(other) && card(p, SkillIds.EVASIVE_CURRENT).active(),
                "A confirmed native dodge reveals the defender's remaining meter without fabricating outgoing damage");
        f.level.tick++;
        check(!EquipmentDamageService.withPostureTestRoll(() -> 0, () -> p.hurt(source, 4))
                        && PostureService.snapshot(p).meter() == 0,
                "A second real dodge spends the final remaining meter");
        var dodgeSnapshot = PostureService.snapshot(p);
        check(card(p, SkillIds.EVASIVE_CURRENT).active()
                        && card(p, SkillIds.EVASIVE_CURRENT).lines().equals(List.of(SkillEffectHudEntry.Text.translated("hud.essence_ascendance.posture.dodged"))),
                "An empty-meter confirmed dodge replaces the existing dodge-percent line");
        f.level.tick++;
        PostureService.finish(p, dodgeSnapshot.incoming(), false, false, 0, 4);
        check(PostureService.snapshot(p).dodgeFeedbackUntil() == dodgeSnapshot.dodgeFeedbackUntil(),
                "Duplicate completion cannot extend the dodge indication");
        p.hurt(source, 0);
        check(!PostureService.snapshot(p).incoming().dodged() && PostureService.snapshot(p).recentDodge(),
                "A subsequent rejected probe cannot overwrite the independently remembered confirmed dodge");
        f.level.tick = dodgeSnapshot.dodgeFeedbackUntil() - 1;
        check(card(p, SkillIds.EVASIVE_CURRENT).active(), "The confirmed dodge badge includes its last presentation tick");
        f.level.tick++;
        check(!PostureService.snapshot(p).recentDodge() && card(p, SkillIds.EVASIVE_CURRENT).active()
                        && PostureService.snapshot(p).meter() == 0
                        && card(p, SkillIds.EVASIVE_CURRENT).lines().equals(List.of(SkillEffectHudEntry.Text.translated("hud.essence_ascendance.posture.evasive", "0.0"))),
                "The two-second dodge indication returns to the real zero chance while combat continues");

        CombatHudActivity.clear(); select(f, SkillIds.BULWARK_STANCE);
        p.setPos(0, 0, 0); other.setPos(0, 0, 2); p.setYRot(0); p.setXRot(0); ready(p);
        ProjectileNativeInterceptionTest.set(Entity.class, p, "onGround", true);
        PostureService.forget(p); SkillEffectRuntime.refresh(p);
        var tuning = SkillEffectRuntime.resolvedSettings(p).posture();
        for (int tick = 0; tick < tuning.bulwark().buildTicks() + tuning.movement().stableTicks(); tick++) {
            f.level.tick++; PostureService.tick(SkillEffectRuntime.context(p));
        }
        check(PostureService.snapshot(p).meter() == 1 && !card(p, SkillIds.BULWARK_STANCE).active(),
                "Facing a threat while still builds Bulwark without idle HUD clutter");
        ready(other); other.hurt(outgoing, 4);
        check(card(p, SkillIds.BULWARK_STANCE).active(), "Confirmed combat reveals charged Bulwark's actual resistance card");
        f.level.tick += 100;
        check(!card(p, SkillIds.BULWARK_STANCE).active(), "Bulwark card becomes inactive when combat expires");

        select(f, SkillIds.ADAPTIVE_GUARD); CombatHudActivity.clear(); ready(p);
        var reflector = f.player(NativeGuardOutcomeTest.MeasuredTarget.class, new Vec3(0, 0, 2), "combat_hud_block");
        captureFixtureMilestones(f, reflector);
        reflector.scale = 1;
        var shield = new ItemStack(AscendanceItems.ASCENDANCE_SHIELD.get());
        p.setItemInHand(InteractionHand.OFF_HAND, shield); p.startUsingItem(InteractionHand.OFF_HAND);
        ProjectileNativeInterceptionTest.set(LivingEntity.class, p, "useItemRemaining",
                shield.getUseDuration(p) - EquipmentShieldService.raiseDelayTicks(p, shield));
        check(!p.hurt(p.damageSources().playerAttack(reflector), 4)
                        && EquipmentDamageService.lastGuardOutcome(p).orElseThrow().successfulBlock(),
                "Actual native shield block stops all incoming health damage");
        check(CombatHudActivity.active(p) && !CombatHudActivity.active(reflector),
                "A confirmed shield block starts defender combat; reflected damage adds no attacker activity");
        p.stopUsingItem();
        SkillEffectRuntime.reset(p);
        check(!CombatHudActivity.active(p), "Runtime lifecycle reset clears combat presentation state");
        CombatHudActivity.confirmedDamage(other, outgoing);
        check(CombatHudActivity.active(p), "Fresh valid damage can begin a new lifecycle");
        ProjectileNativeInterceptionTest.set(Level.class, f.level, "dimension", Level.NETHER);
        check(!CombatHudActivity.active(p), "Dimension change discards stale combat presentation state");
        ProjectileNativeInterceptionTest.set(Level.class, f.level, "dimension", Level.OVERWORLD);
        CombatHudActivity.confirmedDamage(other, outgoing); long beforeRollback = f.level.tick; f.level.tick--;
        check(!CombatHudActivity.active(p), "A backwards clock cannot resurrect stale combat");
        f.level.tick = beforeRollback;
        CombatHudActivity.confirmedDamage(other, outgoing); SkillEffectRuntime.clearAll();
        check(!CombatHudActivity.active(p), "Global runtime clear removes all combat presentation state");
        mobilityAndPotionHud(f);
        var previousRuntime = com.mistaboom.essence_ascendance.config.EssenceConfigManager.serverRuntime();
        try {
            // Initialization-only fixtures have no server-start event to install authoritative balance.
            com.mistaboom.essence_ascendance.config.EssenceConfigManager.install(
                    com.mistaboom.essence_ascendance.config.EssenceConfigManager.runtime());
            mobilityLifecycle(f);
            mobilityImpacts(f);
        } finally {
            f.close();
            if (previousRuntime == null) com.mistaboom.essence_ascendance.config.EssenceConfigManager.reset();
            else com.mistaboom.essence_ascendance.config.EssenceConfigManager.install(previousRuntime);
        }
        System.out.println("Native combat HUD checks passed: " + checks
                + " (actual native health/absorption/dodge/block and saved-skill snapshots; no world)");
    }

    private static void sourcePolicy(ProjectileNativeInterceptionTest.Fixture f, ServerPlayer p, ServerPlayer other,
                                     DamageSource incoming, DamageSource outgoing) throws ReflectiveOperationException {
        CombatHudActivity.clear();
        CombatHudActivity.confirmedDamage(p, outgoing);
        check(!CombatHudActivity.active(p), "Self damage is not combat");
        var arrow = f.arrow(Arrow.class);
        CombatHudActivity.confirmedDamage(p, p.damageSources().arrow(arrow, null));
        check(!CombatHudActivity.active(p), "Ownerless projectile cannot invent a combat opponent");
        ProjectileOwnership.transferNative(arrow, p);
        CombatHudActivity.confirmedDamage(p, p.damageSources().arrow(arrow, other));
        check(!CombatHudActivity.active(p), "Mismatched projectile ownership fails closed");
        ProjectileOwnership.transferNative(arrow, other);
        ready(p); p.hurt(p.damageSources().arrow(arrow, other), 4);
        check(CombatHudActivity.active(p) && CombatHudActivity.active(other), "Actual native projectile damage uses the responsible living shooter");
        CombatHudActivity.clear();
        ProjectileNativeInterceptionTest.set(net.minecraft.server.MinecraftServer.class, f.server, "pvp", false);
        CombatHudActivity.confirmedDamage(p, incoming); CombatHudActivity.confirmedDamage(other, outgoing);
        check(!CombatHudActivity.active(p) && !CombatHudActivity.active(other), "Both incoming and outgoing obey disabled PvP");
        ProjectileNativeInterceptionTest.set(net.minecraft.server.MinecraftServer.class, f.server, "pvp", true);
        var team = f.level.scoreboard.addPlayerTeam("combat_hud_allies"); team.setAllowFriendlyFire(false);
        f.level.scoreboard.addPlayerToTeam(p.getScoreboardName(), team);
        f.level.scoreboard.addPlayerToTeam(other.getScoreboardName(), team);
        CombatHudActivity.confirmedDamage(p, incoming); CombatHudActivity.confirmedDamage(other, outgoing);
        check(!CombatHudActivity.active(p) && !CombatHudActivity.active(other), "Both directions preserve team relationship policy");
        f.level.scoreboard.removePlayerFromTeam(p.getScoreboardName(), team);
        f.level.scoreboard.removePlayerFromTeam(other.getScoreboardName(), team);
        var mob = ProjectileNativeInterceptionTest.instance(NativeStatusInterceptionTest.Pet.class);
        ProjectileNativeInterceptionTest.initializeEntity(mob, EntityType.WOLF, f.level, new Vec3(0, 0, 2), new AABB(-.5, 0, 1.5, .5, 1, 2.5));
        ProjectileNativeInterceptionTest.defineData(mob, net.minecraft.world.entity.TamableAnimal.class);
        ProjectileNativeInterceptionTest.set(LivingEntity.class, mob, "attributes", new AttributeMap(net.minecraft.world.entity.Mob.createMobAttributes().build()));
        ProjectileNativeInterceptionTest.set(LivingEntity.class, mob, "activeEffects", new java.util.HashMap<>());
        ProjectileNativeInterceptionTest.set(LivingEntity.class, mob, "useItem", ItemStack.EMPTY);
        ProjectileNativeInterceptionTest.set(net.minecraft.world.entity.Mob.class, mob, "handItems", net.minecraft.core.NonNullList.withSize(2, ItemStack.EMPTY));
        ProjectileNativeInterceptionTest.set(net.minecraft.world.entity.Mob.class, mob, "armorItems", net.minecraft.core.NonNullList.withSize(4, ItemStack.EMPTY));
        ProjectileNativeInterceptionTest.set(net.minecraft.world.entity.Mob.class, mob, "bodyArmorItem", ItemStack.EMPTY);
        ProjectileNativeInterceptionTest.set(net.minecraft.world.entity.Mob.class, mob, "handDropChances", new float[] {.085F, .085F});
        ProjectileNativeInterceptionTest.set(net.minecraft.world.entity.Mob.class, mob, "armorDropChances", new float[] {.085F, .085F, .085F, .085F});
        mob.setHealth(20);
        ready(p); p.hurt(p.damageSources().mobAttack(mob), 4);
        check(CombatHudActivity.active(p), "Actual native mob damage starts defender combat");
        CombatHudActivity.clear();
        EquipmentDamageService.withSkillDamageFrame(mob, outgoing, () -> {
            EquipmentDamageService.observeSkillHealthDamage(mob, outgoing, () -> mob.setHealth(16)); return true;
        });
        check(CombatHudActivity.active(p), "Generic living damage frame measures outgoing mob loss without requiring a player guard frame");
        CombatHudActivity.clear(); mob.owner = p; mob.setTame(true, false);
        CombatHudActivity.confirmedDamage(mob, outgoing);
        check(!CombatHudActivity.active(p), "Harming one's own pet does not activate the hostile combat presentation");
        var stand = ProjectileNativeInterceptionTest.instance(ArmorStand.class);
        ProjectileNativeInterceptionTest.initializeEntity(stand, EntityType.ARMOR_STAND, f.level, Vec3.ZERO, new AABB(-.5, 0, -.5, .5, 2, .5));
        CombatHudActivity.confirmedDamage(stand, outgoing);
        check(!CombatHudActivity.active(p), "Armor stands are excluded from mob/player combat activity");
    }

    private static void mobilityAndPotionHud(ProjectileNativeInterceptionTest.Fixture f) {
        var p = f.player;
        var data = f.saved.getPlayerData(p.getUUID());
        data.grantAllSkillsForAdmin(List.of(SkillRegistry.require(SkillIds.ALCHEMICAL_AMPLIFICATION)));
        SkillEffectRuntime.refresh(p);
        var context = SkillEffectRuntime.context(p);
        var wings = context.state(SkillIds.ESSENCE_WINGS, com.mistaboom.essence_ascendance.movement.FlightAbilityState::new);
        wings.startWings(context.now());
        p.startFallFlying();
        var handler = com.mistaboom.essence_ascendance.skill.effect.SkillEffectRegistry.get(SkillIds.ESSENCE_WINGS);
        var gliding = handler.hudEntries(context).getFirst();
        check(gliding.active() && gliding.meter().kind() == SkillEffectHudEntry.MeterKind.NONE,
                "Real fall-flying flag produces a Wings status without a binary progress bar");
        p.stopFallFlying();
        var landed = handler.hudEntries(context).getFirst();
        check(!landed.active(), "Stale owned Wings flag cannot override stopped native gliding");
        var presentation = new com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudPresentation();
        presentation.replace(List.of(gliding), context.now());
        presentation.replace(List.of(landed), context.now() + 1);
        check(presentation.visibleEntries(context.now() + 1).isEmpty(), "Real native glide stop removes the live card immediately");

        var effect = new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, 200);
        check(com.mistaboom.essence_ascendance.utility.UtilityPotionService.applyExternalPotion(p, effect, p,
                (resolved, source) -> p.addEffect(resolved, source)), "Native timed potion was accepted");
        check(com.mistaboom.essence_ascendance.utility.UtilityPotionService.amplificationSnapshot(context) != null,
                "Accepted amplified potion exposes a current HUD timer");
        p.removeEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED);
        check(com.mistaboom.essence_ascendance.utility.UtilityPotionService.amplificationSnapshot(context) == null,
                "Removed native potion immediately clears the amplification HUD timer");
    }

    private static void select(ProjectileNativeInterceptionTest.Fixture f, ResourceLocation id) {
        f.saved.getPlayerData(f.player.getUUID()).setLoadoutSelection(SkillGroups.DEFENSE_POSTURE, id);
        SkillEffectRuntime.refresh(f.player);
    }
    private static void mobilityLifecycle(ProjectileNativeInterceptionTest.Fixture f) throws ReflectiveOperationException {
        var p = f.player;
        p.removeAllEffects(); p.stopFallFlying(); ready(p);
        ProjectileNativeInterceptionTest.set(Entity.class, p, "onGround", false);
        SkillEffectRuntime.reset(p);
        var data = f.saved.getPlayerData(p.getUUID());
        data.grantAllSkillsForAdmin(List.of(SkillRegistry.require(SkillIds.IMPACT_CONTROL),
                SkillRegistry.require(SkillIds.FATIGUE_FLIGHT), SkillRegistry.require(SkillIds.ESSENCE_WINGS),
                SkillRegistry.require(SkillIds.VECTOR_BOOST), SkillRegistry.require(SkillIds.UNTETHERED_FLIGHT)));
        data.clearLoadoutSelection(SkillGroups.MOBILITY_JUMP_STYLE);
        data.setLoadoutSelection(SkillGroups.MOBILITY_FLIGHT_REPLACEMENT, SkillIds.ESSENCE_WINGS);
        var context = SkillEffectRuntime.context(p);
        check(context.isEffective(SkillIds.ESSENCE_WINGS) && context.isEffective(SkillIds.VECTOR_BOOST)
                && !context.isEffective(SkillIds.FATIGUE_FLIGHT) && !context.isEffective(SkillIds.DOUBLE_JUMP)
                && !context.isEffective(SkillIds.VECTOR_JUMP) && !context.isEffective(SkillIds.CHARGED_JUMP),
                "Native saved loadout activates Wings/Boost with no selected jump skill");
        var wingsHandler = com.mistaboom.essence_ascendance.skill.effect.SkillEffectRegistry.get(SkillIds.ESSENCE_WINGS);
        var wings = context.state(SkillIds.ESSENCE_WINGS, com.mistaboom.essence_ascendance.movement.FlightAbilityState::new);
        wings.startWings(f.level.tick); p.startFallFlying();
        check(p.isFallFlying(), "Native glide fixture starts fall-flying");
        check(com.mistaboom.essence_ascendance.movement.FlightAbilityRules.wingsAllowed(p), "Native glide fixture permits the Wings mode");
        check(com.mistaboom.essence_ascendance.skill.CommittedSkillAccess.isEffective(p, SkillIds.ESSENCE_WINGS),
                "Native glide fixture grants authoritative Wings access");
        check(!com.mistaboom.essence_ascendance.movement.MovementAbilityRules.supported(p), "Native glide fixture is unsupported in air");
        var nativeGlide = LivingEntity.class.getDeclaredMethod("updateFallFlying"); nativeGlide.setAccessible(true);
        nativeGlide.invoke(p);
        check(p.isFallFlying(), "Transformed native chest validation preserves legitimate equipment-free Wings");
        p.stopFallFlying();
        wingsHandler.tick(context);
        check(!p.isFallFlying() && !wings.wingsActive() && !wingsHandler.hudEntries(context).getFirst().active(),
                "Native glide stop stays stopped on the next gameplay tick and clears owned state/card");
        var boostHandler = com.mistaboom.essence_ascendance.skill.effect.SkillEffectRegistry.get(SkillIds.VECTOR_BOOST);
        var boost = context.state(SkillIds.VECTOR_BOOST, com.mistaboom.essence_ascendance.movement.FlightAbilityState::new);
        boost.updateBoost(f.level.tick, context.settings().mobility().vectorBoost().rechargeTicks());
        check(boost.spendBoost(), "Native effective Boost starts with one charge");
        data.setLoadoutSelection(SkillIds.VECTOR_BOOST, SkillIds.VECTOR_BOOST); SkillEffectRuntime.refresh(p);
        check(SkillEffectRuntime.hudSnapshot(p).entries().stream().noneMatch(e -> e.sourceSkill().equals(SkillIds.VECTOR_BOOST)),
                "Disabled Boost card disappears immediately");
        data.clearLoadoutSelection(SkillIds.VECTOR_BOOST); context = SkillEffectRuntime.context(p);
        boostHandler.tick(context);
        check(context.<com.mistaboom.essence_ascendance.movement.FlightAbilityState>existingState(SkillIds.VECTOR_BOOST).boostCharge() == 0,
                "Disable/re-enable cannot refill a spent Boost in the same tick");
        data.clearLoadoutSelection(SkillGroups.MOBILITY_FLIGHT_REPLACEMENT); context = SkillEffectRuntime.context(p);
        check(context.isEffective(SkillIds.FATIGUE_FLIGHT) && !context.isEffective(SkillIds.ESSENCE_WINGS),
                "Clearing the replacement restores only the owned Fatigue branch");
        var fatigueHandler = com.mistaboom.essence_ascendance.skill.effect.SkillEffectRegistry.get(SkillIds.FATIGUE_FLIGHT);
        var fatigue = context.state(SkillIds.FATIGUE_FLIGHT, com.mistaboom.essence_ascendance.movement.FlightAbilityState::new);
        int endurance = context.settings().mobility().fatigueFlight().enduranceTicks();
        fatigue.updateStamina(f.level.tick, endurance, 100, false, false);
        f.level.tick += endurance;
        fatigue.updateStamina(f.level.tick, endurance, 100, true, false);
        check(fatigue.stamina() == 0, "Native Fatigue reservoir can exhaust fully");
        data.setLoadoutSelection(SkillIds.FATIGUE_FLIGHT, SkillIds.FATIGUE_FLIGHT); SkillEffectRuntime.refresh(p);
        data.clearLoadoutSelection(SkillIds.FATIGUE_FLIGHT); context = SkillEffectRuntime.context(p);
        fatigueHandler.tick(context);
        check(context.<com.mistaboom.essence_ascendance.movement.FlightAbilityState>existingState(SkillIds.FATIGUE_FLIGHT).stamina() == 0,
                "Disable/re-enable in the air cannot refill exhausted Fatigue stamina");
        data.setLoadoutSelection(SkillGroups.MOBILITY_FLIGHT_REPLACEMENT, SkillIds.UNTETHERED_FLIGHT);
        context = SkillEffectRuntime.context(p);
        var free = context.state(SkillIds.UNTETHERED_FLIGHT, com.mistaboom.essence_ascendance.movement.FlightAbilityState::new);
        var freeHandler = com.mistaboom.essence_ascendance.skill.effect.SkillEffectRegistry.get(SkillIds.UNTETHERED_FLIGHT);
        free.activateFlight(p);
        check(p.getAbilities().flying && p.getAbilities().mayfly && freeHandler.hudEntries(context).getFirst().active(),
                "Owned Untethered lease and actual native flight expose the live card");
        free.updateStamina(f.level.tick, 1, 100, false, false); f.level.tick++;
        free.updateStamina(f.level.tick, 1, 100, true, false);
        freeHandler.tick(context);
        check(p.getAbilities().flying && freeHandler.hudEntries(context).getFirst().meter().kind() == SkillEffectHudEntry.MeterKind.NONE,
                "Zero stamina cannot stop sustained Untethered or create a progress fill");
        data.clearLoadoutSelection(SkillGroups.MOBILITY_FLIGHT_REPLACEMENT); SkillEffectRuntime.refresh(p);
        check(!p.getAbilities().mayfly && !p.getAbilities().flying,
                "Deactivation releases only skill-owned native flight permission and active flight");
        p.getAbilities().mayfly = true; p.getAbilities().flying = true;
        var externalLease = new com.mistaboom.essence_ascendance.movement.FlightAbilityState();
        externalLease.activateFlight(p); externalLease.releaseFlight(p);
        check(p.getAbilities().mayfly && p.getAbilities().flying, "Releasing a skill lease preserves pre-existing external flight");
        p.getAbilities().mayfly = false; p.getAbilities().flying = false;
        SkillEffectRuntime.reset(p);
    }
    private static void mobilityImpacts(ProjectileNativeInterceptionTest.Fixture f) {
        var p = f.player;
        var data = f.saved.getPlayerData(p.getUUID());
        var registry = f.level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE);
        var fall = registry.getHolderOrThrow(net.minecraft.world.damagesource.DamageTypes.FALL);
        var wall = registry.getHolderOrThrow(net.minecraft.world.damagesource.DamageTypes.FLY_INTO_WALL);
        var spike = registry.getHolderOrThrow(net.minecraft.world.damagesource.DamageTypes.STALAGMITE);
        var admin = registry.getHolderOrThrow(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL);
        registry.bindTags(java.util.Map.of(net.minecraft.tags.DamageTypeTags.IS_FALL, List.of(fall, spike),
                com.mistaboom.essence_ascendance.equipment.AscendanceDamageTypeTags.FALL, List.of(fall, spike),
                com.mistaboom.essence_ascendance.movement.ImpactDamageService.MOVEMENT_IMPACT, List.of(fall, wall, spike, admin),
                net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY, List.of(admin)));
        var stat = com.mistaboom.essence_ascendance.stat.EssenceStats.FALL_RESISTANCE;
        data.setInvested(stat, 1_000_000);
        var boots = AscendanceItems.ASCENDANCE_BOOTS.get().getDefaultInstance();
        com.mistaboom.essence_ascendance.equipment.EquipmentTierData.setTier(boots,
                com.mistaboom.essence_ascendance.equipment.EquipmentTier.TRANSCENDENT);
        p.getInventory().armor.set(0, boots);
        double resistance = EquipmentDamageService.evaluateStats(p).fallResistancePercent() / 100;
        check(resistance > 0 && resistance < 1, "Real Defense investment and worn equipment supply positive Fall Resistance");
        double impact = SkillEffectRuntime.resolvedSettings(p).mobility().impactControl().damageReduction();
        p.getAttribute(Attributes.FALL_DAMAGE_MULTIPLIER).setBaseValue(.5);
        var fallSource = new DamageSource(fall);
        var wallSource = new DamageSource(wall);
        var spikeSource = new DamageSource(spike);
        double expected = 10 * (1 - resistance) * (1 - impact);
        check(Math.abs(EquipmentDamageService.modifyIncomingDamage(p, fallSource, 10) - expected) < 1e-5,
                "Fall Resistance and Impact Control each apply once; native fall multiplier is not applied twice");
        check(Math.abs(EquipmentDamageService.modifyIncomingDamage(p, spikeSource, 10) - expected) < 1e-5,
                "Pointed-dripstone landing retains the native fall category and once-only composition");
        check(Math.abs(EquipmentDamageService.modifyIncomingDamage(p, wallSource, 10) - expected * .5) < 1e-5,
                "Wall collision receives conditional Fall Resistance and one transferred native fall multiplier");
        check(com.mistaboom.essence_ascendance.movement.ImpactDamageService.incoming(p, new DamageSource(admin), 10) == 10
                && com.mistaboom.essence_ascendance.movement.ImpactDamageService.incoming(p, p.damageSources().generic(), 10) == 10,
                "Combat/untagged and administrative damage cannot inherit movement-impact protection");
        data.setLoadoutSelection(SkillIds.IMPACT_CONTROL, SkillIds.IMPACT_CONTROL); SkillEffectRuntime.refresh(p);
        check(EquipmentDamageService.modifyIncomingDamage(p, wallSource, 10) == 10,
                "Disabling Impact Control removes conditional wall protection even with Defense Fall Resistance");
        check(Math.abs(EquipmentDamageService.modifyIncomingDamage(p, fallSource, 10) - 10 * (1 - resistance)) < 1e-5,
                "Ordinary Defense fall protection survives disabling Mobility Impact Control");
        p.getAttribute(Attributes.FALL_DAMAGE_MULTIPLIER).setBaseValue(1);
        SkillEffectRuntime.reset(p);
    }
    private static void captureFixtureMilestones(ProjectileNativeInterceptionTest.Fixture f, ServerPlayer player) {
        // Match the primary fixture's captured receipts: no native advancement manager exists before world setup.
        var data = f.saved.getPlayerData(player.getUUID());
        SkillRegistry.referencedPermanentMilestoneIds().forEach(data::completeMilestone);
    }
    private static void chargeEvasive(ProjectileNativeInterceptionTest.Fixture f) throws ReflectiveOperationException {
        var p = f.player; ready(p);
        ProjectileNativeInterceptionTest.set(Entity.class, p, "onGround", true);
        PostureService.forget(p); SkillEffectRuntime.refresh(p);
        for (int tick = 0; tick < SkillEffectRuntime.resolvedSettings(p).posture().evasive().buildTicks(); tick++) {
            f.level.tick++; AttunementGameplay.setMovementIntent(p, true); Vec3 before = p.position(); p.setPos(before.x + .1, before.y, before.z);
            PostureService.moved(p, before, p.level().dimension().location().toString(), p.getYRot(), p.getXRot());
            PostureService.tick(SkillEffectRuntime.context(p));
        }
    }
    private static SkillEffectHudEntry card(ServerPlayer player, ResourceLocation id) {
        return SkillEffectRuntime.hudSnapshot(player).entries().stream().filter(entry -> entry.sourceSkill().equals(id)).findFirst().orElseThrow();
    }
    private static void ready(ServerPlayer player) {
        player.invulnerableTime = 0; player.hurtTime = 0; player.setHealth(20); player.setDeltaMovement(Vec3.ZERO);
    }
    private static void check(boolean pass, String message) { checks++; if (!pass) throw new AssertionError(message); }

    /** Only suppress death's network/world side effects; inherited transformed hurt performs the lethal native health write. */
    static final class DyingPlayer extends ServerPlayer {
        int deaths;
        private DyingPlayer() { super(null, null, null, null); throw new AssertionError("No player constructor in fixture"); }
        @Override public void awardStat(Stat<?> statistic, int amount) { }
        @Override public void onEnterCombat() { }
        @Override public void onLeaveCombat() { }
        @Override public void indicateDamage(double x, double z) { }
        @Override public void die(DamageSource source) { deaths++; }
    }
}
