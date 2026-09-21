package com.mistaboom.essence_ascendance.projectile;

import com.mistaboom.essence_ascendance.equipment.EquipmentDamageService;
import com.mistaboom.essence_ascendance.equipment.EquipmentShieldService;
import com.mistaboom.essence_ascendance.equipment.FracturedEquipmentData;
import com.mistaboom.essence_ascendance.item.AscendanceItems;
import com.mistaboom.essence_ascendance.skill.CommittedSkillService;
import com.mistaboom.essence_ascendance.skill.SkillGroups;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.SkillRegistry;
import com.mistaboom.essence_ascendance.skill.effect.GuardCounterattackService;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudEntry;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudPresentation;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime;
import com.mojang.serialization.Lifecycle;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Real skills, shield use, damage scopes and transformed displacement with memory-only world geometry. */
public final class NativeGuardCounterattackTest {
    private static int assertions;
    private static long event = 10_000;
    private NativeGuardCounterattackTest() { }

    public static void run() throws ReflectiveOperationException {
        if (!Boolean.getBoolean("essence.projectile.nativeHookTest")) throw new IllegalStateException("Explicit no-world fixture required");
        for (InteractionHand hand : InteractionHand.values()) {
            var f = new ProjectileNativeInterceptionTest.Fixture();
            var data = f.saved.getPlayerData(f.player.getUUID());
            data.grantAllSkillsForAdmin(List.of(SkillRegistry.require(SkillIds.REFLEXIVE_WARD),
                    SkillRegistry.require(SkillIds.STORED_FORCE), SkillRegistry.require(SkillIds.GUARD_AMPLIFIER),
                    SkillRegistry.require(SkillIds.RIPOSTE)));
            data.setLoadoutSelection(SkillGroups.DEFENSE_BLOCK_REWARD, SkillIds.STORED_FORCE);
            check(CommittedSkillService.isEffective(f.player, SkillIds.STORED_FORCE)
                    && CommittedSkillService.isEffective(f.player, SkillIds.RIPOSTE), "Real committed graph enables both counterattacks");
            ItemStack shield = new ItemStack(AscendanceItems.ASCENDANCE_SHIELD.get());
            f.player.setItemInHand(hand, shield);
            f.player.startUsingItem(hand);
            check(EquipmentShieldService.isUsingShield(f.player), "Native use accepts functional shield in " + hand);
            var defaultHud = SkillEffectRuntime.hudSnapshot(f.player);
            check(!card(defaultHud.entries(), SkillIds.STORED_FORCE).active() && !card(defaultHud.entries(), SkillIds.RIPOSTE).active(),
                    "Effective unused counterattacks emit inactive defaults for the shared HUD lifecycle");
            GuardCounterattackService.onBlock(f.player, ++event, 8, true);
            var tuning = SkillEffectRuntime.resolvedSettings(f.player).guard();
            equal(GuardCounterattackService.bonusReach(f.player), tuning.riposte().bonusReach(), "Perfect block arms resolved reach");
            var hud = SkillEffectRuntime.hudSnapshot(f.player);
            equal(hud.primaryMeleeBonusReach(), tuning.riposte().bonusReach(), "Same server snapshot drives client reach");
            check(card(hud.entries(), SkillIds.STORED_FORCE).active() && card(hud.entries(), SkillIds.RIPOSTE).active(),
                    "Both active rewards have active HUD cards");
            check(hud.entries().stream().filter(entry -> entry.active()).allMatch(entry -> entry.lines().size() == 1
                            && entry.meter().kind() == SkillEffectHudEntry.MeterKind.TIMER && (entry.accent() >>> 24) == 255),
                    "Counterattacks use the shared opaque accent, detail line and timer layout");
            var presentation = new SkillEffectHudPresentation();
            presentation.replace(hud.entries(), 100);
            f.player.stopUsingItem();
            check(GuardCounterattackService.bonusReach(f.player) > 0, "Lowering a still-held shield preserves the counterattack");
            var target = f.player(new Vec3(0, 0, 2), "counter_target");
            check(GuardCounterattackService.isAttack(ServerboundInteractPacket.createAttackPacket(target, false)), "Native attack packet decode");
            check(!GuardCounterattackService.isAttack(ServerboundInteractPacket.createInteractionPacket(target, false, InteractionHand.MAIN_HAND)), "Native interact packet excluded");
            check(GuardCounterattackService.canReach(f.player, target), "Near eligible creature is in range");
            f.level.occluded = true;
            check(!GuardCounterattackService.canReach(f.player, target), "Terrain rejects extended reach");
            f.level.occluded = false;

            float[] requested = {0};
            var registry = new MappedRegistry<DamageType>(Registries.DAMAGE_TYPE, Lifecycle.stable());
            var type = registry.register(DamageTypes.PLAYER_ATTACK, new DamageType("player", .1F), RegistrationInfo.BUILT_IN);
            registry.freeze();
            DamageSource source = new DamageSource(type, f.player);
            // Exercise the real authoritative reservation and outgoing frame. Health writes are measured through
            // the production adapter; fixture avoids full hurt cosmetics/world access covered by guard outcome tests.
            EquipmentDamageService.runPrimarySkillAttack(f.player, target, () -> {
                check(GuardCounterattackService.suppressDisplacement(f.player), "Protection begins only inside armed native primary scope");
                Vec3 velocity = f.player.getDeltaMovement();
                f.player.push(1, .1, 0);
                equal(f.player.getDeltaMovement().distanceToSqr(velocity), 0, "Transformed Entity.push cannot displace resolving Riposte");
                f.player.knockback(.5, 1, 0);
                equal(f.player.getDeltaMovement().distanceToSqr(velocity), 0, "Transformed LivingEntity.knockback shares resolution protection");
                NativeGuardExplosionTest.verify(f, target, source, true);
                EquipmentDamageService.withSkillDamageFrame(target, source, () -> {
                    requested[0] = EquipmentDamageService.modifyOutgoingSkillDamage(target, source, 4);
                    return false;
                });
            });
            check(!GuardCounterattackService.suppressDisplacement(f.player), "Protection ends at attack return, including cancel");
            NativeGuardExplosionTest.verify(f, target, source, false);
            double stored = Math.min(tuning.storedForce().capacity(), 8 * tuning.storedForce().conversion());
            equal(requested[0], 4 * (1 + tuning.riposte().damageScale()) + stored * tuning.storedForce().damageScale(),
                    "Both rewards compose into one outgoing amount");
            check(GuardCounterattackService.bonusReach(f.player) > 0, "Rejected attack retains Riposte");

            EquipmentDamageService.runPrimarySkillAttack(f.player, target, () ->
                    EquipmentDamageService.withSkillDamageFrame(target, source, () ->
                            EquipmentDamageService.observePrimarySkillHit(target, source, () -> {
                                EquipmentDamageService.modifyOutgoingSkillDamage(target, source, 4);
                                EquipmentDamageService.observeSkillHealthDamage(target, source, () -> target.setHealth(target.getHealth() - 2));
                                return true;
                            })));
            equal(GuardCounterattackService.bonusReach(f.player), 0, "Measured accepted primary strike consumes Riposte");
            var consumedHud = SkillEffectRuntime.hudSnapshot(f.player);
            check(!card(consumedHud.entries(), SkillIds.STORED_FORCE).active() && !card(consumedHud.entries(), SkillIds.RIPOSTE).active(),
                    "Confirmed strike consumes both rewards while retaining inactive card identities");
            presentation.replace(consumedHud.entries(), 101);
            check(presentation.visibleEntries(101).isEmpty(), "Consumed guard rewards immediately stop advertising stored power and readiness");
            check(presentation.visibleEntries(161).isEmpty(), "Consumed guard rewards cannot reappear during an event grace period");
            f.player.push(.2, 0, 0);
            check(f.player.getDeltaMovement().x > 0, "Native displacement resumes immediately after strike");

            arm(f, hand);
            f.player.getCooldowns().addCooldown(shield.getItem(), 20);
            equal(GuardCounterattackService.bonusReach(f.player), 0, "Disable cooldown immediately invalidates pending Riposte");
            check(!card(SkillEffectRuntime.hudSnapshot(f.player).entries(), SkillIds.STORED_FORCE).active()
                            && !card(SkillEffectRuntime.hudSnapshot(f.player).entries(), SkillIds.RIPOSTE).active(),
                    "Equipment disable returns still-effective skills to inactive HUD defaults");
            f.player.getCooldowns().removeCooldown(shield.getItem());
            equal(GuardCounterattackService.bonusReach(f.player), 0, "Cooldown ending cannot resurrect discarded arm");
            arm(f, hand);
            FracturedEquipmentData.markFractured(shield);
            equal(GuardCounterattackService.bonusReach(f.player), 0, "Fracture invalidates reward");
            f.player.stopUsingItem();
            shield = new ItemStack(AscendanceItems.ASCENDANCE_SHIELD.get());
            f.player.setItemInHand(hand, shield);
            arm(f, hand);
            f.player.setItemInHand(hand, new ItemStack(Items.STICK));
            equal(GuardCounterattackService.bonusReach(f.player), 0, "No-longer-held exact shield invalidates reward");
            f.player.setItemInHand(hand, shield);
            equal(GuardCounterattackService.bonusReach(f.player), 0, "Restoring item cannot resurrect reward");
            arm(f, hand);
            f.level.tick += Math.max(tuning.storedForce().durationTicks(), tuning.riposte().durationTicks());
            check(!card(SkillEffectRuntime.hudSnapshot(f.player).entries(), SkillIds.STORED_FORCE).active()
                            && !card(SkillEffectRuntime.hudSnapshot(f.player).entries(), SkillIds.RIPOSTE).active(),
                    "Natural expiry emits inactive defaults instead of omitting the effective guard cards");
            arm(f, hand);
            data.setLoadoutSelection(SkillGroups.DEFENSE_BLOCK_REWARD, SkillIds.GUARD_AMPLIFIER);
            SkillEffectRuntime.refresh(f.player);
            check(!CommittedSkillService.isEffective(f.player, SkillIds.STORED_FORCE), "Owned alternatives retain exclusive effectiveness");
            check(SkillEffectRuntime.hudSnapshot(f.player).entries().stream().noneMatch(entry -> entry.sourceSkill().equals(SkillIds.STORED_FORCE)),
                    "Choice change immediately discards Stored Force");
            f.level.tick += tuning.riposte().durationTicks();
            equal(GuardCounterattackService.bonusReach(f.player), 0, "Pending arm expires at exact configured boundary");
            arm(f, hand);
            SkillEffectRuntime.reset(f.player);
            equal(GuardCounterattackService.bonusReach(f.player), 0, "Death/logout/respawn/dimension reset clears pending state");
            f.close();
        }
        System.out.println("Native guard counterattacks passed: " + assertions + " (real skills/items/scopes, both hands, transformed push/knockback; no world)");
    }

    private static void arm(ProjectileNativeInterceptionTest.Fixture fixture, InteractionHand hand) {
        fixture.player.stopUsingItem();
        fixture.player.startUsingItem(hand);
        GuardCounterattackService.onBlock(fixture.player, ++event, 8, true);
        fixture.player.stopUsingItem();
    }
    private static SkillEffectHudEntry card(List<SkillEffectHudEntry> entries, net.minecraft.resources.ResourceLocation skill) {
        return entries.stream().filter(entry -> entry.sourceSkill().equals(skill)).findFirst().orElseThrow();
    }
    private static void equal(double actual, double expected, String message) {
        check(Math.abs(actual - expected) < 1.0E-5, message + ": " + actual + " != " + expected);
    }
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
