package com.mistaboom.essence_ascendance.projectile;

import com.mistaboom.essence_ascendance.balance.runtime.RuntimeBalanceDefinition;
import com.mistaboom.essence_ascendance.balance.runtime.NativeMeleeRateBonusFloor;
import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.equipment.*;
import com.mistaboom.essence_ascendance.item.AscendanceItems;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime;
import com.mistaboom.essence_ascendance.stat.EssenceStats;
import com.mistaboom.essence_ascendance.stat.EssenceStatRegistry;
import com.mistaboom.essence_ascendance.progression.StatScalingService;
import com.mistaboom.essence_ascendance.tier.AscendanceTierRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.phys.EntityHitResult;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/** Real native combat and equipment aggregation; flat damage and player-only Bonus limits. */
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
            double priorValue = 0;
            int endpoints = 0;
            for (var point : track.checkpoints()) if (point.purchasable()) {
                var tier = AscendanceTierRegistry.get(point.tierId()).orElseThrow();
                data.setTier(tier);
                double increment = point.effectFraction() * track.maximumEffect() - priorValue;
                for (Item item : meleeItems()) {
                    var stack = equipped(attacker, item, EquipmentTier.fromAscendanceTier(tier));
                    var before = attack(f, attacker, target, priorInvestment);
                    var after = attack(f, attacker, target, point.cumulativeCap());
                    check(attacker.getMainHandItem() == stack && !FracturedEquipmentData.isFractured(stack),
                            "The same active-tier item supplies both native attacks");
                    check(increment >= 1 - 1e-6 && Math.abs(after.attribute() - before.attribute() - increment) < 1e-6,
                            "Every supported melee profile receives the same flat increment at " + point.tierId()
                                    + ": " + before + " -> " + after + ", expected " + increment);
                    check(Math.abs(after.healthLoss() - before.healthLoss() - increment) < 1e-4,
                            "Actual native health loss receives exactly one flat increment at " + point.tierId());
                }
                priorInvestment = point.cumulativeCap();
                priorValue += increment;
                endpoints++;
            }
            check(endpoints == track.activeStateCount() && endpoints > 0, "Every published melee Bonus endpoint was exercised");
            damagePlayerTierLimits(f, attacker, runtime);
            heldAndWornPlayerTierLimits(f, attacker, runtime);
            projectileEndpoints(f, attacker, target, runtime);
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

    private static List<Item> meleeItems() {
        return List.of(AscendanceItems.ASCENDANCE_SWORD.get(), AscendanceItems.ASCENDANCE_HOE.get(),
                AscendanceItems.ASCENDANCE_PICKAXE.get(), AscendanceItems.ASCENDANCE_AXE.get(), AscendanceItems.ASCENDANCE_SHOVEL.get());
    }

    private static ItemStack equipped(ServerPlayer player, Item item, EquipmentTier tier) {
        var stack = new ItemStack(item);
        EquipmentTierData.setTier(stack, tier);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return stack;
    }

    private static void damagePlayerTierLimits(ProjectileNativeInterceptionTest.Fixture f, ServerPlayer player,
                                       RuntimeBalanceDefinition runtime) {
        var data = f.saved.getPlayerData(player.getUUID());
        for (var stat : List.of(EssenceStats.MELEE_DAMAGE, EssenceStats.RANGED_DAMAGE, EssenceStats.MAGIC_DAMAGE)) {
            var track = runtime.config().balanceProfile().bonusTrack(stat.id());
            long maximumInvestment = track.checkpoints().getLast().cumulativeCap();
            var items = stat == EssenceStats.MELEE_DAMAGE ? meleeItems() : List.<Item>of(stat == EssenceStats.RANGED_DAMAGE
                    ? AscendanceItems.ASCENDANCE_RANGED_WEAPON.get() : AscendanceItems.ASCENDANCE_CASTER.get());
            for (var playerTier : AscendanceTierRegistry.values()) for (var itemTier : EquipmentTier.values()) {
                data.setTier(playerTier);
                double expected = itemTier == EquipmentTier.LATENT || !playerTier.grantsPower() ? 0 : track.checkpoints().stream()
                        .filter(point -> point.tierId().equals(playerTier.id())).findFirst().orElseThrow().effectFraction() * track.maximumEffect();
                for (var item : items) {
                    var stack = equipped(player, item, itemTier);
                    data.setInvested(stat, 0);
                    double before = damageValue(player, stack, stat);
                    data.setInvested(stat, maximumInvestment);
                    double after = damageValue(player, stack, stat);
                    check(Math.abs(after - before - expected) < 1e-6,
                            "Damage points follow player unlocks independently of the physical gear tier: "
                                    + stat.id() + ", player=" + playerTier.id() + ", item=" + itemTier + ", added=" + (after - before) + ", expected=" + expected);
                    check(Math.abs(damageValue(player, stack, stat) - after) < 1e-9,
                            "Repeated runtime evaluation does not double-apply the flat Bonus");
                }
            }
            data.setInvested(stat, 0);
        }
    }

    private static void heldAndWornPlayerTierLimits(ProjectileNativeInterceptionTest.Fixture f, ServerPlayer player,
                                                   RuntimeBalanceDefinition runtime) {
        var data = f.saved.getPlayerData(player.getUUID());
        for (var stat : EssenceStatRegistry.values()) data.setInvested(stat,
                runtime.config().balanceProfile().getInvestmentCap(
                        com.mistaboom.essence_ascendance.tier.AscendanceTiers.TRANSCENDENT, stat));
        var slots = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
        var armor = List.of(AscendanceItems.ASCENDANCE_HELMET.get(), AscendanceItems.ASCENDANCE_CHESTPLATE.get(),
                AscendanceItems.ASCENDANCE_LEGGINGS.get(), AscendanceItems.ASCENDANCE_BOOTS.get());
        for (var playerTier : AscendanceTierRegistry.values()) for (var itemTier : EquipmentTier.values()) {
            data.setTier(playerTier);
            var tool = equipped(player, AscendanceItems.ASCENDANCE_PICKAXE.get(), itemTier);
            for (int i = 0; i < slots.size(); i++) {
                var stack = new ItemStack(armor.get(i)); EquipmentTierData.setTier(stack, itemTier);
                player.setItemSlot(slots.get(i), stack);
            }
            var held = EquipmentStatResolver.evaluateHeldOnly(player, InteractionHand.MAIN_HAND);
            var worn = EquipmentStatResolver.evaluateWornArmor(player);
            var active = EquipmentStatResolver.evaluateForHand(player, InteractionHand.MAIN_HAND);
            for (var stat : EssenceStatRegistry.values()) {
                double heldExpected = itemTier == EquipmentTier.LATENT ? 0
                        : EquipmentProfiles.PICKAXE.statStrength(EquipmentActivationType.HELD, stat);
                double wornExpected = itemTier == EquipmentTier.LATENT ? 0
                        : EquipmentProfiles.ARMOR.statStrength(EquipmentActivationType.WORN, stat);
                check(Math.abs(held.strength(stat) - heldExpected) < 1e-8
                                && Math.abs(worn.strength(stat) - wornExpected) < 1e-8
                                && Math.abs(active.strength(stat) - Math.max(heldExpected, wornExpected)) < 1e-8,
                        "All held/worn Bonus channels retain coverage and MAX merge independently of tiers: "
                                + stat.id() + ", player=" + playerTier.id() + ", item=" + itemTier);
            }
            var applied = EquipmentAttributeService.evaluate(player);
            double eligible = itemTier == EquipmentTier.LATENT ? 0 : 1;
            check(Math.abs(applied.maxHealthPoints() - eligible * 2 * StatScalingService.evaluate(data, EssenceStats.MAX_HEALTH).scaledBonus()) < 1e-8
                            && Math.abs(applied.miningSpeedFraction() - eligible * StatScalingService.evaluate(data, EssenceStats.MINING_SPEED).scaledBonus() / 100) < 1e-8
                            && Math.abs(applied.reachBlocks() - eligible * StatScalingService.evaluate(data, EssenceStats.REACH).scaledBonus()) < 1e-8,
                    "Actual health, mining and shared reach consumers apply the player Bonus once");
            FracturedEquipmentData.markFractured(tool);
            check(EquipmentStatResolver.evaluateHeldOnly(player, InteractionHand.MAIN_HAND).values().isEmpty(),
                    "Fractured equipment remains ineligible");
        }
        for (var slot : slots) player.setItemSlot(slot, ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        for (var stat : EssenceStatRegistry.values()) data.setInvested(stat, 0);
    }

    private static double damageValue(ServerPlayer player, ItemStack stack,
                                      com.mistaboom.essence_ascendance.stat.StatDefinition stat) {
        if (stat == EssenceStats.RANGED_DAMAGE) return EquipmentWeaponService.evaluateRanged(player, stack).finalDamage();
        if (stat == EssenceStats.MAGIC_DAMAGE) return EquipmentWeaponService.evaluateMagic(player, stack).finalDamage();
        EquipmentAttributeService.sync(player);
        return player.getAttributeValue(Attributes.ATTACK_DAMAGE);
    }

    private static void projectileEndpoints(ProjectileNativeInterceptionTest.Fixture f, ServerPlayer player,
                                             ServerPlayer target, RuntimeBalanceDefinition runtime) throws ReflectiveOperationException {
        var data = f.saved.getPlayerData(player.getUUID());
        for (var stat : List.of(EssenceStats.RANGED_DAMAGE, EssenceStats.MAGIC_DAMAGE)) {
            var track = runtime.config().balanceProfile().bonusTrack(stat.id());
            long previousInvestment = 0;
            double previousValue = 0;
            for (var point : track.checkpoints()) if (point.purchasable()) {
                var tier = AscendanceTierRegistry.get(point.tierId()).orElseThrow();
                data.setTier(tier);
                var stack = equipped(player, stat == EssenceStats.RANGED_DAMAGE ? AscendanceItems.ASCENDANCE_RANGED_WEAPON.get()
                        : AscendanceItems.ASCENDANCE_CASTER.get(), EquipmentTier.fromAscendanceTier(tier));
                data.setInvested(stat, previousInvestment);
                double before = projectileDamage(f, player, target, stack, stat);
                data.setInvested(stat, point.cumulativeCap());
                double after = projectileDamage(f, player, target, stack, stat);
                double increment = point.effectFraction() * track.maximumEffect() - previousValue;
                check(increment >= 1 - 1e-6 && Math.abs(after - before - increment) < 1e-4,
                        "Native projectile damage receives one flat increment on identical equipment: " + stat.id()
                                + " / " + point.tierId() + ": " + before + " -> " + after + ", expected " + increment);
                previousInvestment = point.cumulativeCap(); previousValue += increment;
            }
            data.setInvested(stat, 0);
        }
    }

    private static double projectileDamage(ProjectileNativeInterceptionTest.Fixture f, ServerPlayer player,
                                             ServerPlayer target, ItemStack stack,
                                             com.mistaboom.essence_ascendance.stat.StatDefinition stat) throws ReflectiveOperationException {
        if (stat == EssenceStats.RANGED_DAMAGE) {
            var state = EquipmentWeaponService.evaluateRanged(player, stack);
            // Exercise the real launch adapter with both ordinary and speed-scaled flight.
            for (double speed : new double[]{1, 1.75}) {
                var launch = new EquipmentWeaponService.RangedState(state.baselineDamage(), state.finalDamage(), state.baselineAttackSpeed(),
                        state.finalAttackSpeed(), state.fullDrawTicks(), (speed - 1) * 100, speed);
                var arrow = f.arrow(Arrow.class);
                arrow.setBaseDamage(2); arrow.setDeltaMovement(0, 0, 3); arrow.setOwner(player);
                EquipmentWeaponService.withRangedShotContext(player, launch, launch.fullDrawTicks(), 20,
                        () -> EquipmentWeaponService.configureRangedProjectile(player, arrow, 3));
                double launchDamage = arrow.getBaseDamage() * arrow.getDeltaMovement().length();
                check(Math.abs(launchDamage - state.finalDamage()) < 1e-6,
                        "Real arrow launch applies flat damage once and speed compensation does not multiply it");
                f.level.entities.remove(arrow);
            }
            return state.finalDamage();
        }
        var state = EquipmentWeaponService.evaluateMagic(player, stack);
        var bolt = new MagicBoltEntity(player, state.finalDamage(), 1);
        var stored = new net.minecraft.nbt.CompoundTag();
        bolt.addAdditionalSaveData(stored);
        check(stored.getDouble("LaunchDamage") == state.finalDamage(), "Magic launch snapshots additive damage exactly once");
        target.invulnerableTime = 0; target.setHealth(1000); target.setDeltaMovement(Vec3.ZERO);
        bolt.onHitEntity(new EntityHitResult(target));
        double loss = 1000 - target.getHealth();
        check(Math.abs(loss - state.finalDamage()) < 1e-4, "Native magic impact health loss matches the launch value once");
        return loss;
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
        EquipmentAttributeService.sync(attacker);
        check(attribute == attacker.getAttributeValue(Attributes.ATTACK_DAMAGE), "Repeated attribute synchronization does not double-apply Bonus damage");
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
