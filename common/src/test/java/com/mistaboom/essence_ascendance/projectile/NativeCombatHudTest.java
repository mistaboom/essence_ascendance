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
        f.close();
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

    private static void select(ProjectileNativeInterceptionTest.Fixture f, ResourceLocation id) {
        f.saved.getPlayerData(f.player.getUUID()).setLoadoutSelection(SkillGroups.DEFENSE_POSTURE, id);
        SkillEffectRuntime.refresh(f.player);
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
