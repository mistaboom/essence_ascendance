package com.mistaboom.essence_ascendance.projectile;

import com.mistaboom.essence_ascendance.equipment.EquipmentShieldService;
import com.mistaboom.essence_ascendance.item.AscendanceItems;
import com.mistaboom.essence_ascendance.skill.CommittedSkillService;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.SkillRegistry;
import com.mistaboom.essence_ascendance.skill.effect.CrowdControlEligibility;
import com.mistaboom.essence_ascendance.skill.effect.GuardMobilityController;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime;
import com.mistaboom.essence_ascendance.skill.effect.StaggerController;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Real committed/equipment/native knockback/attribute integration around completed translation; no damage call/world. */
public final class NativeGuardRamTest {
    private static int checks;
    private NativeGuardRamTest() { }
    public static void run() throws ReflectiveOperationException {
        for (InteractionHand hand : InteractionHand.values()) {
            var f = new ProjectileNativeInterceptionTest.Fixture();
            var data = f.saved.getPlayerData(f.player.getUUID());
            data.grantAllSkillsForAdmin(List.of(SkillRegistry.require(SkillIds.GUARDED_ADVANCE), SkillRegistry.require(SkillIds.SHIELD_RAM)));
            check(CommittedSkillService.isEffective(f.player, SkillIds.SHIELD_RAM), "Real prerequisite graph enables Guarded Advance and Shield Ram");
            ItemStack shield = AscendanceItems.ASCENDANCE_SHIELD.get().getDefaultInstance();
            f.player.setItemInHand(hand, shield); f.player.startUsingItem(hand);
            ProjectileNativeInterceptionTest.set(LivingEntity.class, f.player, "useItemRemaining", shield.getUseDuration(f.player) - 10);
            f.player.setSprinting(true);
            check(EquipmentShieldService.isGuarding(f.player) && GuardMobilityController.sprintAllowed(f.player), "Server functional ready shield grants same sprint permission in " + hand);
            var tuning = SkillEffectRuntime.resolvedSettings(f.player).guard().ram();
            var victim = f.player(new Vec3(0, 0, .8), "ram_victim");
            double nativeSpeed = victim.getAttributeValue(Attributes.MOVEMENT_SPEED);
            float health = victim.getHealth();
            check(CrowdControlEligibility.canControl(f.player, victim), "Hostile PvP creature accepts bounded crowd control");
            check(GuardMobilityController.beginMove(f.player, MoverType.PISTON) == null, "Piston translation never starts a ram");
            f.player.setSprinting(false);
            check(GuardMobilityController.beginMove(f.player, MoverType.PLAYER) == null, "Ordinary guard never starts a ram");
            f.player.setSprinting(true);
            var initial = GuardMobilityController.beginMove(f.player, MoverType.PLAYER);
            check(initial != null, "Actual native player movement captures bounded physical start");
            f.player.setPos(0, 0, .3);
            GuardMobilityController.completedMove(f.player, initial);
            check(victim.getAttributeValue(Attributes.MOVEMENT_SPEED) < nativeSpeed
                    && victim.getAttributeValue(Attributes.MOVEMENT_SPEED) > 0, "Accepted ram applies a nonzero native locomotion penalty, never root");
            check(victim.getDeltaMovement().z > 0 && Math.abs(victim.getDeltaMovement().x) > 0, "Native resistant knockback drives victim forward/lateral out of path");
            check(victim.getHealth() == health, "Shield Ram performs no direct damage or health write");
            Vec3 acceptedVelocity = victim.getDeltaMovement();
            GuardMobilityController.completedMove(f.player, initial);
            check(victim.getDeltaMovement().equals(acceptedVelocity), "Duplicate completed callback cannot repeat knockback");
            check(GuardMobilityController.diagnostics(f.player).get(1).contains("damage=none"), "Bounded diagnostics expose no-damage semantics");
            f.level.tick += tuning.staggerTicks() - 1; StaggerController.tick(victim);
            check(victim.getAttributeValue(Attributes.MOVEMENT_SPEED) < nativeSpeed, "Stagger remains through final active tick");
            f.level.tick++; StaggerController.tick(victim);
            check(victim.getAttributeValue(Attributes.MOVEMENT_SPEED) == nativeSpeed, "Exact expiry restores native movement speed");
            f.player.setPos(0, 0, 0);
            GuardMobilityController.completedMove(f.player, initial);
            check(victim.getDeltaMovement().equals(acceptedVelocity), "Expired stagger cannot machine-gun same continuous charge");
            GuardMobilityController.remove(f.player);
            f.player.setPos(0, 0, .3);
            f.level.occluded = true;
            GuardMobilityController.completedMove(f.player, initial);
            check(victim.getAttributeValue(Attributes.MOVEMENT_SPEED) == nativeSpeed, "Terrain-clipped contact visibility prevents through-wall stagger");
            f.level.occluded = false;
            ProjectileNativeInterceptionTest.set(MinecraftServer.class, f.server, "pvp", false);
            GuardMobilityController.completedMove(f.player, initial);
            check(victim.getAttributeValue(Attributes.MOVEMENT_SPEED) == nativeSpeed, "PvP-disabled player cannot be rammed");
            ProjectileNativeInterceptionTest.set(MinecraftServer.class, f.server, "pvp", true);
            var team = f.level.scoreboard.addPlayerTeam("ram_allies");
            team.setAllowFriendlyFire(true);
            f.level.scoreboard.addPlayerToTeam(f.player.getScoreboardName(), team);
            f.level.scoreboard.addPlayerToTeam(victim.getScoreboardName(), team);
            GuardMobilityController.completedMove(f.player, initial);
            check(victim.getAttributeValue(Attributes.MOVEMENT_SPEED) == nativeSpeed, "Allied creatures remain excluded even with friendly fire enabled");
            f.level.scoreboard.removePlayerFromTeam(victim.getScoreboardName(), team);
            victim.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
            GuardMobilityController.completedMove(f.player, initial);
            check(victim.getAttributeValue(Attributes.MOVEMENT_SPEED) == nativeSpeed, "Native total control/knockback resistance rejects stagger");
            victim.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0);
            GuardMobilityController.completedMove(f.player, initial);
            check(victim.getAttributeValue(Attributes.MOVEMENT_SPEED) < nativeSpeed, "Removing immunity permits a subsequent real charge");
            GuardMobilityController.remove(f.player);
            check(victim.getAttributeValue(Attributes.MOVEMENT_SPEED) == nativeSpeed, "Owner lifecycle cleanup removes accepted transient controls");
            var sourceValid = GuardMobilityController.staggerSource(f.player);
            check(StaggerController.apply(f.player, victim, SkillIds.SHIELD_RAM, tuning.staggerTicks(), tuning.staggerMovementMultiplier(), tuning.maximumSweep(), sourceValid), "Reusable stagger applies bounded skill-owned state");
            f.player.stopUsingItem(); StaggerController.tick(victim);
            check(victim.getAttributeValue(Attributes.MOVEMENT_SPEED) < nativeSpeed, "Lowering the exact still-held shield preserves accepted stagger");
            f.player.getCooldowns().addCooldown(shield.getItem(), 20); StaggerController.tick(victim);
            check(victim.getAttributeValue(Attributes.MOVEMENT_SPEED) == nativeSpeed, "Source shield disable immediately clears accepted stagger");
            f.player.getCooldowns().removeCooldown(shield.getItem());
            StaggerController.apply(f.player, victim, SkillIds.SHIELD_RAM, tuning.staggerTicks(), tuning.staggerMovementMultiplier(), tuning.maximumSweep(), sourceValid);
            f.player.setItemInHand(hand, shield.copy()); StaggerController.tick(victim);
            check(victim.getAttributeValue(Attributes.MOVEMENT_SPEED) == nativeSpeed, "Swapping exact source shield clears stagger even if replacement is functional");
            f.player.setItemInHand(hand, shield);
            StaggerController.apply(f.player, victim, SkillIds.SHIELD_RAM, tuning.staggerTicks(), tuning.staggerMovementMultiplier(), tuning.maximumSweep(), sourceValid);
            victim.getAbilities().invulnerable = true; StaggerController.tick(victim);
            check(victim.getAttributeValue(Attributes.MOVEMENT_SPEED) == nativeSpeed, "Dynamic target creative/control immunity clears native stagger attribute");
            victim.getAbilities().invulnerable = false;
            StaggerController.apply(f.player, victim, SkillIds.SHIELD_RAM, tuning.staggerTicks(), tuning.staggerMovementMultiplier(), tuning.maximumSweep(), sourceValid);
            victim.setPos(0, 0, 8); StaggerController.tick(victim);
            check(victim.getAttributeValue(Attributes.MOVEMENT_SPEED) == nativeSpeed, "External teleport releases stagger without moving target back");
            f.close();
        }
        System.out.println("Guard Ram native contracts passed: " + checks + " (real skills/guard/native knockback/transient attributes, no world or direct damage)");
    }
    private static void check(boolean pass, String message) { checks++; if (!pass) throw new AssertionError(message); }
}
