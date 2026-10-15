package com.mistaboom.essence_ascendance.projectile;

import com.mistaboom.essence_ascendance.balance.runtime.RuntimeBalanceDefinition;
import com.mistaboom.essence_ascendance.balance.runtime.NativeMeleeRateBonusFloor;
import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.equipment.*;
import com.mistaboom.essence_ascendance.item.AscendanceItems;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime;
import com.mistaboom.essence_ascendance.stat.EssenceStats;
import com.mistaboom.essence_ascendance.tier.AscendanceTierRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Actual fully charged Player.attack through native attributes and health writes, on fixed same-tier hoes. */
public final class NativeMeleeBonusTest {
    private static int checks;
    private NativeMeleeBonusTest() { }

    static void run(ProjectileNativeInterceptionTest.Fixture f) throws ReflectiveOperationException {
        var previous = EssenceConfigManager.serverRuntime();
        var attacker = f.player(NativeGuardOutcomeTest.NativePlayer.class, Vec3.ZERO, "melee_bonus_attacker");
        var target = f.player(NativeGuardOutcomeTest.NativePlayer.class, new Vec3(0, 0, 2), "melee_bonus_target");
        try {
            var runtime = RuntimeBalanceDefinition.bootstrap();
            EssenceConfigManager.install(runtime);
            var data = f.saved.getPlayerData(attacker.getUUID());
            data.clearAllSkillsForAdmin();
            SkillEffectRuntime.refresh(attacker);
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1024);
            target.setAbsorptionAmount(0);
            // The fixture has no chunk source. Ground contact is initial entity storage,
            // as in the native posture tests; Player.attack still reads its real state.
            ProjectileNativeInterceptionTest.set(Entity.class, attacker, "onGround", true);
            attacker.fallDistance = 0;
            check(attacker.getActiveEffects().isEmpty() && target.getActiveEffects().isEmpty()
                    && target.getArmorValue() == 0, "Bonus damage fixture has no effects, armor or absorption");
            var track = runtime.config().balanceProfile().bonusTrack(EssenceStats.MELEE_DAMAGE.id());
            long priorInvestment = 0;
            int endpoints = 0;
            for (var point : track.checkpoints()) if (point.purchasable()) {
                var tier = AscendanceTierRegistry.get(point.tierId()).orElseThrow();
                data.setTier(tier);
                var hoe = new ItemStack(AscendanceItems.ASCENDANCE_HOE.get());
                EquipmentTierData.setTier(hoe, EquipmentTier.fromAscendanceTier(tier));
                attacker.setItemInHand(InteractionHand.MAIN_HAND, hoe);
                var before = attack(f, attacker, target, priorInvestment);
                var after = attack(f, attacker, target, point.cumulativeCap());
                check(attacker.getMainHandItem() == hoe && !FracturedEquipmentData.isFractured(hoe),
                        "The same active-tier hoe supplies both native attacks");
                check(after.attribute() - before.attribute() >= 1 - 1e-6,
                        "Completed Bonus endpoint adds at least 1 native attribute damage at " + point.tierId()
                                + ": " + before + " -> " + after);
                check(after.healthLoss() - before.healthLoss() >= 1 - 1e-4,
                        "Completed Bonus endpoint adds at least 1 actual native health damage at " + point.tierId()
                                + ": " + before + " -> " + after);
                priorInvestment = point.cumulativeCap(); endpoints++;
            }
            check(endpoints == track.activeStateCount() && endpoints > 0, "Every published melee Bonus endpoint was exercised");
            attackReadinessSaturation(attacker);
            System.out.println("Native melee Bonus checks passed: " + checks + ", endpoints=" + endpoints);
        } finally {
            for (var player : new ServerPlayer[]{attacker, target}) {
                EquipmentAttributeService.forget(player); SkillEffectRuntime.forget(player);
                com.mistaboom.essence_ascendance.skill.CommittedSkillService.forget(player);
                f.players.remove(player.getUUID()); f.onlinePlayers.remove(player); f.level.entities.remove(player);
            }
            if (previous == null) EssenceConfigManager.reset(); else EssenceConfigManager.install(previous);
        }
    }

    private static void attackReadinessSaturation(ServerPlayer attacker) throws ReflectiveOperationException {
        attacker.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        EquipmentAttributeService.resetTransientState(attacker);
        var speed = attacker.getAttribute(Attributes.ATTACK_SPEED);
        for (double rate : new double[]{.1, 1, 10, 20, 30, 39.9, 40, 80}) {
            speed.setBaseValue(rate);
            check(attacker.getAttributeValue(Attributes.ATTACK_SPEED) == rate,
                    "Native attack-speed sample uses the exact requested attribute: " + rate);
            for (int ticks : new int[]{0, 1, 2, 10}) {
                ProjectileNativeInterceptionTest.set(LivingEntity.class, attacker, "attackStrengthTicker", ticks);
                float readiness = attacker.getAttackStrengthScale(.5F);
                float actualFactor = .2F + readiness * readiness * .8F;
                check(actualFactor == NativeMeleeRateBonusFloor.damageFactor(rate, ticks),
                        "Melee-rate generation reference agrees with native readiness at rate=" + rate + ", ticks=" + ticks);
                if (rate >= 40) check(readiness == 1F,
                        "Forty attacks per second already saturates native partial charge at ticker=" + ticks);
            }
        }
        ProjectileNativeInterceptionTest.set(LivingEntity.class, attacker, "attackStrengthTicker", 0);
        speed.setBaseValue(20); float twenty = attacker.getAttackStrengthScale(.5F);
        speed.setBaseValue(30); float thirty = attacker.getAttackStrengthScale(.5F);
        speed.setBaseValue(40); float forty = attacker.getAttackStrengthScale(.5F);
        speed.setBaseValue(80); float eighty = attacker.getAttackStrengthScale(.5F);
        check(twenty < thirty && thirty < forty && forty == eighty,
                "Native charge still improves below forty, while forty to eighty is a real gameplay plateau");
    }

    private static Hit attack(ProjectileNativeInterceptionTest.Fixture f, ServerPlayer attacker,
                              ServerPlayer target, long investment) throws ReflectiveOperationException {
        f.saved.getPlayerData(attacker.getUUID()).setInvested(EssenceStats.MELEE_DAMAGE, investment);
        EquipmentAttributeService.sync(attacker);
        double attribute = attacker.getAttributeValue(Attributes.ATTACK_DAMAGE);
        ProjectileNativeInterceptionTest.set(LivingEntity.class, attacker, "attackStrengthTicker", 10000);
        target.invulnerableTime = 0; target.setHealth(1000); target.setDeltaMovement(Vec3.ZERO);
        check(attacker.getAttackStrengthScale(.5F) == 1 && !attacker.isSprinting()
                        && attacker.onGround() && attacker.fallDistance == 0 && attacker.getMainHandItem().getEnchantments().isEmpty(),
                "Every sample is a fully charged ordinary noncritical unenchanted attack");
        attacker.attack(target);
        double loss = 1000 - target.getHealth();
        check(loss > 0 && target.getHealth() > 0 && Math.abs(loss - attribute) < 1e-4,
                "Real Player.attack health loss agrees with the native attribute: " + loss + " / " + attribute);
        return new Hit(attribute, loss);
    }

    private record Hit(double attribute, double healthLoss) { }
    private static void check(boolean pass, String message) { checks++; if (!pass) throw new AssertionError(message); }
}
