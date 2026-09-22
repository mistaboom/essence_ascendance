package com.mistaboom.essence_ascendance.projectile;

import com.mistaboom.essence_ascendance.equipment.EquipmentDamageService;
import com.mistaboom.essence_ascendance.lifecycle.PlayerRuntimeLifecycleService;
import com.mistaboom.essence_ascendance.skill.SkillGroups;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.SkillRegistry;
import com.mistaboom.essence_ascendance.skill.effect.*;
import com.mistaboom.essence_ascendance.vitality.RecoveryMath;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/** Real transformed FoodData and native damage/heal methods, in-memory saved-player data, no world constructors. */
public final class NativeVitalityRecoveryTest {
    private static int checks;
    private NativeVitalityRecoveryTest() { }
    public static void run() throws ReflectiveOperationException {
        if (!Boolean.getBoolean("essence.projectile.nativeHookTest")) throw new IllegalStateException("Explicit native fixture required");
        var f = new ProjectileNativeInterceptionTest.Fixture(NativeGuardOutcomeTest.NativePlayer.class);
        NativeGuardOutcomeTest.registry(f);
        var player = f.player;
        var target = f.player(NativeGuardOutcomeTest.NativePlayer.class, new Vec3(0, 0, 2), "recovery_target");
        var other = f.player(NativeGuardOutcomeTest.NativePlayer.class, new Vec3(1, 0, 2), "recovery_other");
        SkillRegistry.referencedPermanentMilestoneIds().forEach(f.saved.getPlayerData(target.getUUID())::completeMilestone);
        SkillRegistry.referencedPermanentMilestoneIds().forEach(f.saved.getPlayerData(other.getUUID())::completeMilestone);
        f.saved.getPlayerData(player.getUUID()).grantAllSkillsForAdmin(List.of(SkillRegistry.require(SkillIds.RISING_RECOVERY), SkillRegistry.require(SkillIds.LIFE_STEAL)));
        natural(f);
        select(f, SkillIds.LIFE_STEAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        var tuning = SkillEffectRuntime.resolvedSettings(player).vitality().lifeSteal();
        check(!card(player).active(), "Empty chain has no active HUD card");
        player.setHealth(5); target.setHealth(20);
        hit(f, target, 4);
        equal(player.getHealth(), 5 + 4 * tuning.baseHealingFraction(), "Native accepted mitigated primary weapon damage heals exact first fraction");
        check(VitalityRecoveryEffects.chainHits(player) == 1, "First accepted hit starts one chain");
        double before = player.getHealth(); hit(f, target, 4);
        equal(player.getHealth() - before, 4 * (tuning.baseHealingFraction() + tuning.perHitHealingFraction()), "Second same-target hit grows healing");
        var card = card(player);
        check(card.active() && card.accent() == 0xFFE889B5 && card.badge().arguments().equals(List.of("2"))
                && card.meter().expiresAt() == f.level.tick + tuning.chainTimeoutTicks(), "HUD carries Vitality accent, actual chain and authoritative expiry");
        check(VitalityRecoveryEffects.chainHits(other) == 0, "Another player cannot borrow chain state");
        hit(f, other, 4); check(VitalityRecoveryEffects.chainHits(player) == 1, "Changing targets resets chain growth");
        SkillEffectRuntime.onAirSwing(player); check(VitalityRecoveryEffects.chainHits(player) == 0, "Authoritative air miss ends chain");
        hit(f, target, 4); f.level.tick += tuning.chainTimeoutTicks(); check(VitalityRecoveryEffects.chainHits(player) == 0, "Exact inactivity timeout ends chain");
        target.setHealth(20); hit(f, target, 4);
        target.invulnerableTime = 20; before = player.getHealth(); rawHit(f, target, 1);
        equal(player.getHealth(), before, "Invulnerable rejected hit cannot heal");
        check(VitalityRecoveryEffects.chainHits(player) == 0, "Rejected native attempted hit breaks melee chain");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY); target.invulnerableTime = 0;
        before = player.getHealth(); hit(f, target, 4); equal(player.getHealth(), before, "Unarmed melee is excluded by weapon semantics");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        target.invulnerableTime = 0; target.setHealth(20); before = player.getHealth();
        EquipmentDamageService.withSecondarySkillDamage(() -> hit(f, target, 4));
        equal(player.getHealth(), before, "Secondary recursive proc damage cannot steal health");
        target.invulnerableTime = 0;
        EquipmentDamageService.withReflection(() -> { hit(f, target, 4); return true; });
        equal(player.getHealth(), before, "Reflected damage cannot steal health");
        target.invulnerableTime = 0; target.hurt(target.damageSources().onFire(), 1);
        equal(player.getHealth(), before, "Fire/status/environment damage cannot steal health");
        var arrow = f.arrow(Arrow.class); ProjectileOwnership.transferNative(arrow, player);
        target.invulnerableTime = 0; target.setHealth(20); before = player.getHealth();
        check(target.hurt(target.damageSources().arrow(arrow, player), 4), "Native primary projectile accepted");
        equal(player.getHealth() - before, 4 * tuning.baseHealingFraction(), "Accepted projectile damage uses shared responsible shooter attribution");
        target.invulnerableTime = 0; target.setHealth(20); player.setHealth(player.getMaxHealth() - .01F);
        hit(f, target, 4); equal(player.getHealth(), player.getMaxHealth(), "Healing cannot exceed missing health");
        resetBoundaries(f, target);
        canceledOutcome(f, target);
        lifecycleDuringNativeDamage(f, target);
        additionalOutcomes(f, target);
        attunement(f, target);
        sharedDomains(f, target);
        target.setHealth(20); hit(f, target, 1);
        f.saved.getPlayerData(player.getUUID()).clearAllSkillsForAdmin(); SkillEffectRuntime.refresh(player);
        check(VitalityRecoveryEffects.chainHits(player) == 0 && !SkillEffectRuntime.context(player).isEffective(SkillIds.LIFE_STEAL),
                "Removing the purchase receipt immediately removes gameplay and transient chain state");
        f.close(); SkillEffectRuntime.clearAll();
        routedHealthLocal();
        System.out.println("Native Vitality recovery checks passed: " + checks + " (actual regeneration, direct weapon damage, healing, lifecycle and HUD; no world)");
    }

    /** Proves the mutable local reaches the real health write, not merely the CombatTracker argument. */
    private static void sharedDomains(ProjectileNativeInterceptionTest.Fixture f, ServerPlayer target) throws ReflectiveOperationException {
        var player = f.player;
        var data = f.saved.getPlayerData(player.getUUID());
        data.grantAllSkillsForAdmin(List.of(SkillRegistry.require(SkillIds.HUNGER_WARD),
                SkillRegistry.require(SkillIds.METABOLIC_CONVERSION), SkillRegistry.require(SkillIds.MASTERWORK_TEMPERING),
                SkillRegistry.require(SkillIds.RESTFUL_MENDING), SkillRegistry.require(SkillIds.METABOLIC_MENDING)));
        data.setLoadoutSelection(SkillRegistry.require(SkillIds.HUNGER_WARD).choiceGroup(), SkillIds.HUNGER_WARD);
        data.setLoadoutSelection(SkillGroups.VITALITY_RECOVERY, SkillIds.LIFE_STEAL);
        SkillEffectRuntime.refresh(player);
        check(SkillEffectRuntime.context(player).isEffective(SkillIds.METABOLIC_CONVERSION), "Legal Conversion branch active");
        player.setHealth(player.getMaxHealth()); player.getFoodData().setFoodLevel(10); player.getFoodData().setSaturation(0);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        target.setHealth(20); hit(f, target, 4);
        check(com.mistaboom.essence_ascendance.vitality.VitalityDamageService.carry(player, SkillIds.METABOLIC_CONVERSION) > 0
                || player.getFoodData().getFoodLevel() > 10 || player.getFoodData().getSaturationLevel() > 0,
                "Independent Life Steal serves food deficit at full HP");
        double beforeFood = player.getFoodData().getFoodLevel() + player.getFoodData().getSaturationLevel();
        com.mistaboom.essence_ascendance.vitality.ConsumableRecoveryService.withFoodHealing(player, () -> player.heal(4));
        equal(player.getFoodData().getFoodLevel() + player.getFoodData().getSaturationLevel(), beforeFood, "Food healing cannot generate food");
        var effect = new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.REGENERATION, 100);
        ((com.mistaboom.essence_ascendance.vitality.FoodEffectOrigin) effect).essenceAscendance$foodOrigin(true);
        var copy = new net.minecraft.world.effect.MobEffectInstance(effect);
        check(((com.mistaboom.essence_ascendance.vitality.FoodEffectOrigin) copy).essenceAscendance$foodOrigin(), "Timed effect copy retains lineage");
        var loaded = net.minecraft.world.effect.MobEffectInstance.load((net.minecraft.nbt.CompoundTag)copy.save());
        check(((com.mistaboom.essence_ascendance.vitality.FoodEffectOrigin) loaded).essenceAscendance$foodOrigin(), "Timed effect save/load retains lineage");
        player.setHealth(player.getMaxHealth()-.01F); loaded.tick(player, () -> {});
        equal(player.getFoodData().getFoodLevel() + player.getFoodData().getSaturationLevel(), beforeFood, "Delayed food healing cannot generate food");
        var stronger = new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.REGENERATION, 20, 1);
        effect.update(stronger);
        var hiddenSaved = net.minecraft.world.effect.MobEffectInstance.load((net.minecraft.nbt.CompoundTag)effect.save());
        var hiddenOrigin = (com.mistaboom.essence_ascendance.vitality.FoodEffectOrigin) hiddenSaved;
        check(!hiddenOrigin.essenceAscendance$foodOrigin() && hiddenOrigin.essenceAscendance$hiddenOrigin().essenceAscendance$foodOrigin(),
                "Native stronger independent effect and hidden food effect keep distinct saved provenance");
        try {
            com.mistaboom.essence_ascendance.vitality.ConsumableRecoveryService.withFoodHealing(player, () -> { throw new IllegalStateException("probe"); });
        } catch (IllegalStateException expected) { }
        check(!com.mistaboom.essence_ascendance.vitality.ConsumableRecoveryService.foodOrigin(player), "Exceptional native effect unwinds food scope");

        data.setLoadoutSelection(SkillGroups.UTILITY_MAINTENANCE, SkillIds.MASTERWORK_TEMPERING); SkillEffectRuntime.refresh(player);
        ItemStack tool = new ItemStack(Items.IRON_PICKAXE); tool.set(net.minecraft.core.component.DataComponents.MAX_DAMAGE, 1000);
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        double capacity = 1000 * SkillEffectRuntime.resolvedSettings(player).utility().masterworkTempering().maximumOverdurabilityFraction();
        com.mistaboom.essence_ascendance.equipment.EquipmentMaintenanceData.setOverdurability(tool, capacity, capacity);
        tool.hurtAndBreak(1, f.level, player, ignored -> {});
        equal(tool.getDamageValue(), 0, "Transformed native wear consumes active buffer before native damage");
        equal(com.mistaboom.essence_ascendance.equipment.EquipmentMaintenanceData.overdurability(tool), capacity-1, "One buffer debit");
        // A real native wear call must apply the invested efficiency before the active Masterwork buffer.
        // Compare the buffer debit with the same unenchanted item at zero investment; native damage must
        // remain untouched while the buffer is available.
        data.setTier(com.mistaboom.essence_ascendance.tier.AscendanceTiers.TRANSCENDENT);
        data.setInvested(com.mistaboom.essence_ascendance.stat.EssenceStats.DURABILITY_EFFICIENCY, 100_000);
        ItemStack efficientTool = com.mistaboom.essence_ascendance.item.AscendanceItems.ASCENDANCE_PICKAXE.get().getDefaultInstance();
        com.mistaboom.essence_ascendance.equipment.EquipmentTierData.setTier(efficientTool,
                com.mistaboom.essence_ascendance.equipment.EquipmentTier.TRANSCENDENT);
        efficientTool.set(net.minecraft.core.component.DataComponents.MAX_DAMAGE, 1000);
        com.mistaboom.essence_ascendance.equipment.EquipmentMaintenanceData.setOverdurability(efficientTool, capacity, capacity);
        player.setItemInHand(InteractionHand.MAIN_HAND, efficientTool);
        double efficiency = com.mistaboom.essence_ascendance.equipment.EquipmentGatheringService.evaluate(player)
                .durabilityEfficiencyPercent();
        check(efficiency > 0 && efficiency < 100, "Invested held durability efficiency resolves to a usable fraction");
        for (int i = 0; i < 100; i++) efficientTool.hurtAndBreak(1, f.level, player, ignored -> {});
        int expectedBufferDebit = (int) Math.floor(100 * (1 - efficiency / 100) + 1e-9);
        equal(com.mistaboom.essence_ascendance.equipment.EquipmentMaintenanceData.overdurability(efficientTool),
                capacity - expectedBufferDebit, "Efficiency reduces actual Overdurability consumption once");
        equal(com.mistaboom.essence_ascendance.equipment.EquipmentMaintenanceData.wearDebt(efficientTool),
                100 * (1 - efficiency / 100) - expectedBufferDebit, "Fractional wear is conserved alongside buffer absorption");
        equal(efficientTool.getDamageValue(), 0, "Active buffer prevents native wear after efficiency");
        enchantedWear(f, efficientTool, capacity, expectedBufferDebit);
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        data.setLoadoutSelection(SkillGroups.UTILITY_MAINTENANCE, SkillIds.RESTFUL_MENDING); SkillEffectRuntime.refresh(player);
        tool.hurtAndBreak(1, f.level, player, ignored -> {});
        equal(tool.getDamageValue(), 1, "Inactive buffer grants no absorption to excluded maintenance branch");
        equal(com.mistaboom.essence_ascendance.equipment.EquipmentMaintenanceData.overdurability(tool), capacity-1, "Dormant paid buffer preserved");
        data.setLoadoutSelection(SkillGroups.UTILITY_MAINTENANCE, SkillIds.MASTERWORK_TEMPERING); SkillEffectRuntime.refresh(player);
        equal(com.mistaboom.essence_ascendance.equipment.EquipmentMaintenanceData.overdurability(tool), capacity-1, "Re-enable never refills buffer");
        com.mistaboom.essence_ascendance.equipment.EquipmentMaintenanceData.resolveWear(tool, 1, 50);
        var savedTool = ItemStack.parseOptional(player.registryAccess(), (net.minecraft.nbt.CompoundTag)tool.save(player.registryAccess()));
        equal(com.mistaboom.essence_ascendance.equipment.EquipmentMaintenanceData.wearDebt(savedTool), .5, "Native item save/load retains fractional wear");

        data.setLoadoutSelection(SkillGroups.UTILITY_MAINTENANCE, SkillIds.METABOLIC_MENDING); SkillEffectRuntime.refresh(player);
        tool.setDamageValue(50); player.setHealth(5); player.getFoodData().setFoodLevel(20); player.getFoodData().setSaturation(1);
        ItemStack meal = new ItemStack(Items.BREAD, 2);
        double paidBuffer = com.mistaboom.essence_ascendance.equipment.EquipmentMaintenanceData.overdurability(tool);
        meal.finishUsingItem(f.level, player);
        check(meal.getCount() == 1 && player.getHealth() > 5 && tool.getDamageValue() < 50,
                "One completed native full-hunger meal independently heals and repairs");
        equal(com.mistaboom.essence_ascendance.equipment.EquipmentMaintenanceData.overdurability(tool), paidBuffer,
                "Metabolic Mending never refills dormant paid buffer");
        player.setHealth(player.getMaxHealth()); tool.setDamageValue(0);
        check(!com.mistaboom.essence_ascendance.vitality.ConsumableRecoveryService.canEatForRecovery(player),
                "No useful heal or native repair demand stops full-hunger meals");

        data.setLoadoutSelection(SkillGroups.UTILITY_MAINTENANCE, SkillIds.MASTERWORK_TEMPERING); SkillEffectRuntime.refresh(player);
        var anvil = new net.minecraft.world.inventory.AnvilMenu(77, player.getInventory(), net.minecraft.world.inventory.ContainerLevelAccess.NULL);
        anvil.getSlot(0).set(new ItemStack(Items.IRON_SWORD));
        anvil.getSlot(1).set(new ItemStack(Items.IRON_INGOT));
        anvil.createResult();
        check(com.mistaboom.essence_ascendance.equipment.EquipmentMaintenanceData.overdurability(anvil.getSlot(2).getItem()) > 0,
                "Native anvil preview supplies paid tempering output");
        data.setLoadoutSelection(SkillGroups.UTILITY_MAINTENANCE, SkillIds.RESTFUL_MENDING); SkillEffectRuntime.refresh(player);
        var mayPickup = net.minecraft.world.inventory.AnvilMenu.class.getDeclaredMethod("mayPickup", Player.class, boolean.class);
        mayPickup.setAccessible(true);
        check(!(boolean)mayPickup.invoke(anvil, player, true), "Stale tempering preview cannot bypass inactive branch at native pickup");

        TargetConditionState conditions = new TargetConditionState(player.getUUID());
        PropagationBudget root = new PropagationBudget(2, 3);
        root.seed(target.getUUID());
        conditions.apply(target, f.level.tick, 100, 2, 1, new TargetConditionState.Lineage(root, 1, SkillIds.COMBUSTION));
        var pulse = conditions.drainDuePulses(f.level.tick + 1).getFirst();
        check(pulse.lineage().root() == root && pulse.lineage().generation() == 1, "Delayed pulse retains exact root budget and generation");
        check(conditions.drainDuePulses(f.level.tick + 1).isEmpty(), "No replayed delayed pulse");
        conditions.discardInactiveLineage(id -> false);
        check(conditions.size() == 0, "Inactive source cannot resume stored effects after switching");

        var cow = new net.minecraft.world.entity.animal.Cow(net.minecraft.world.entity.EntityType.COW, f.level);
        var fed = (com.mistaboom.essence_ascendance.gathering.AnimalFeedingState) cow;
        check(!fed.essenceAscendance$fed(f.level.tick), "Breeding age is not successful feeding");
        ItemStack wheat = new ItemStack(Items.WHEAT, 2);
        var feed = net.minecraft.world.entity.animal.Animal.class.getDeclaredMethod("usePlayerItem", Player.class, InteractionHand.class, ItemStack.class);
        feed.setAccessible(true); feed.invoke(cow, player, InteractionHand.MAIN_HAND, wheat);
        check(wheat.getCount() == 1 && fed.essenceAscendance$fed(f.level.tick), "Native consumed feed starts window");
        long fedAt = fed.essenceAscendance$fedAt();
        cow.setAge(0);
        check(fed.essenceAscendance$fed(fedAt + fed.essenceAscendance$feedWindow() - 1), "Herd recovery cannot shorten fed window");
        check(!fed.essenceAscendance$fed(fedAt + fed.essenceAscendance$feedWindow()), "Fed window expires at native breeding interval");
        net.minecraft.nbt.CompoundTag animalData = new net.minecraft.nbt.CompoundTag();
        cow.addAdditionalSaveData(animalData);
        var cowCopy = new net.minecraft.world.entity.animal.Cow(net.minecraft.world.entity.EntityType.COW, f.level);
        cowCopy.readAdditionalSaveData(animalData);
        check(((com.mistaboom.essence_ascendance.gathering.AnimalFeedingState)cowCopy).essenceAscendance$fedAt() == fedAt,
                "Native animal save/load retains feed timestamp");
    }
    private static void enchantedWear(ProjectileNativeInterceptionTest.Fixture f, ItemStack source,
                                      double capacity, int unenchantedDebit) {
        var previousRegistries = f.level.memoryRegistries;
        var enchantments = new net.minecraft.core.MappedRegistry<net.minecraft.world.item.enchantment.Enchantment>(
                net.minecraft.core.registries.Registries.ENCHANTMENT, com.mojang.serialization.Lifecycle.stable());
        var unbreaking = net.minecraft.data.registries.VanillaRegistries.createLookup()
                .lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .getOrThrow(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING).value();
        var holder = enchantments.register(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING,
                unbreaking, net.minecraft.core.RegistrationInfo.BUILT_IN);
        enchantments.freeze();
        var registries = new java.util.ArrayList<net.minecraft.core.Registry<?>>();
        previousRegistries.registries()
                .filter(entry -> !entry.key().equals(net.minecraft.core.registries.Registries.ENCHANTMENT))
                .forEach(entry -> registries.add(entry.value()));
        registries.add(enchantments);
        try {
            f.level.memoryRegistries = new net.minecraft.core.RegistryAccess.ImmutableRegistryAccess(registries).freeze();
            ItemStack buffered = source.copy();
            buffered.enchant(holder, 3);
            com.mistaboom.essence_ascendance.equipment.EquipmentMaintenanceData.setOverdurability(buffered, capacity, capacity);
            ItemStack ordinary = buffered.copy();
            com.mistaboom.essence_ascendance.equipment.EquipmentMaintenanceData.setOverdurability(ordinary, 0, 0);
            for (var stack : List.of(buffered, ordinary)) {
                f.player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                f.level.getRandom().setSeed(130013L);
                for (int i = 0; i < 100; i++) stack.hurtAndBreak(1, f.level, f.player, ignored -> {});
            }
            double debit = capacity - com.mistaboom.essence_ascendance.equipment.EquipmentMaintenanceData.overdurability(buffered);
            check(debit > 0 && debit < unenchantedDebit, "Native Unbreaking saves real post-efficiency wear");
            equal(debit, ordinary.getDamageValue(), "Identical native enchantment outcomes debit buffer or native damage once");
            equal(buffered.getDamageValue(), 0, "Unbreaking and Efficiency preserve native damage while buffer remains");
            equal(com.mistaboom.essence_ascendance.equipment.EquipmentMaintenanceData.wearDebt(buffered),
                    com.mistaboom.essence_ascendance.equipment.EquipmentMaintenanceData.wearDebt(ordinary),
                    "Buffer presence cannot change fractional efficiency carry");
        } finally {
            f.level.memoryRegistries = previousRegistries;
            f.player.setItemInHand(InteractionHand.MAIN_HAND, source);
        }
    }

    private static void routedHealthLocal() throws ReflectiveOperationException {
        var f = new ProjectileNativeInterceptionTest.Fixture(NativeGuardOutcomeTest.NativePlayer.class);
        NativeGuardOutcomeTest.registry(f);
        var player = f.player;
        var attacker = f.player(NativeGuardOutcomeTest.NativePlayer.class, new Vec3(0, 0, 2), "routing_attacker");
        var source = player.damageSources().playerAttack(attacker);
        var data = f.saved.getPlayerData(player.getUUID());
        data.clearAllSkillsForAdmin(); SkillEffectRuntime.refresh(player);
        player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_ABSORPTION).setBaseValue(2);
        player.setHealth(20); player.setAbsorptionAmount(2); player.invulnerableTime = 0;
        equal(player.getAbsorptionAmount(), 2, "Native routing fixture starts with absorption");
        check(player.hurt(source, 4), "Unselected native routing control hit accepted");
        equal(player.getHealth(), 18, "Unselected native hit spends absorption before health");
        var skill = SkillRegistry.require(SkillIds.STAGGERED_PAIN);
        data.grantAllSkillsForAdmin(List.of(skill));
        data.setLoadoutSelection(skill.choiceGroup(), skill.id());
        SkillEffectRuntime.refresh(player);
        check(SkillEffectRuntime.context(player).isEffective(skill.id()), "Real committed Staggered Pain selection is effective");
        player.setHealth(20); player.setAbsorptionAmount(2); player.invulnerableTime = 0; f.level.tick++;
        check(player.hurt(source, 4), "Selected native routing hit accepted");
        equal(player.getAbsorptionAmount(), 0, "Routing preserves the native absorption spend");
        equal(player.getHealth(), 20, "Routed post-absorption damage changes the actual native health-write local");
        equal(com.mistaboom.essence_ascendance.vitality.VitalityDamageService.ledger(player).delayed.total(), 2,
                "Only post-absorption damage becomes debt; no immediate double charge");
        f.close(); SkillEffectRuntime.clearAll();
    }

    private static void natural(ProjectileNativeInterceptionTest.Fixture f) throws ReflectiveOperationException {
        select(f, SkillIds.RISING_RECOVERY); var player = f.player;
        var tuning = SkillEffectRuntime.resolvedSettings(player).vitality().risingRecovery();
        for (boolean saturated : new boolean[] {true, false}) {
            SkillEffectRuntime.reset(player); player.setHealth(4);
            var food = new FoodData(); food.setFoodLevel(saturated ? 20 : 18); food.setSaturation(saturated ? 6 : 0);
            ProjectileNativeInterceptionTest.set(Player.class, player, "foodData", food);
            int vanillaPeriod = saturated ? 10 : 80;
            int ticks = (int) Math.ceil(vanillaPeriod / RecoveryMath.speed(4, 20, tuning.maxSpeedBonus(), tuning.recoveryCurveExponent()));
            for (int i = 0; i < ticks - 1; i++) { f.level.tick++; food.tick(player); }
            equal(player.getHealth(), 4, "Natural timer cannot heal early or duplicate ticks");
            f.level.tick++; food.tick(player);
            equal(player.getHealth(), 5, "Only native natural-regeneration timer accelerates");
            equal(food.getExhaustionLevel(), 6, "Native exhaustion cost per healing event remains exact");
            var recoveryCard = SkillEffectRuntime.hudSnapshot(player).entries().stream()
                    .filter(entry -> entry.sourceSkill().equals(SkillIds.RISING_RECOVERY)).findFirst().orElseThrow();
            check(recoveryCard.active()
                            && recoveryCard.badge().equals(SkillEffectHudEntry.Text.translated(
                            "hud.essence_ascendance.recovery.active"))
                            && recoveryCard.lines().equals(List.of(SkillEffectHudEntry.Text.translated(
                            "hud.essence_ascendance.recovery.speed", SkillEffectHudCards.decimal(
                                    RecoveryMath.speed(5, 20, tuning.maxSpeedBonus(), tuning.recoveryCurveExponent())))))
                            && recoveryCard.meter().kind() == SkillEffectHudEntry.MeterKind.NONE,
                    "Rising Recovery HUD refreshes its multiplier to the current post-heal health");
            player.setHealth(20);
            check(SkillEffectRuntime.hudSnapshot(player).entries().stream()
                            .filter(entry -> entry.sourceSkill().equals(SkillIds.RISING_RECOVERY)).noneMatch(SkillEffectHudEntry::active),
                    "Completed recovery cannot keep advertising an active regeneration bonus");
        }
        var food = new FoodData(); food.setFoodLevel(17); food.setSaturation(0);
        ProjectileNativeInterceptionTest.set(Player.class, player, "foodData", food); player.setHealth(4);
        for (int i = 0; i < 100; i++) { f.level.tick++; food.tick(player); }
        equal(player.getHealth(), 4, "Insufficient native hunger blocks healing");
        food.setFoodLevel(20); food.setSaturation(20);
        f.level.memoryGameRules = new GameRules();
        f.level.memoryGameRules.getRule(GameRules.RULE_NATURAL_REGENERATION).set(false, null);
        for (int i = 0; i < 100; i++) { f.level.tick++; food.tick(player); }
        equal(player.getHealth(), 4, "Natural-regeneration gamerule remains authoritative");
        player.heal(2); equal(player.getHealth(), 6, "External potion/command-style healing is not amplified by Rising Recovery");
        f.level.memoryGameRules = null;
        select(f, SkillIds.LIFE_STEAL); food = new FoodData(); food.setSaturation(6);
        ProjectileNativeInterceptionTest.set(Player.class, player, "foodData", food); player.setHealth(4);
        for (int i = 0; i < 9; i++) { f.level.tick++; food.tick(player); }
        equal(player.getHealth(), 4, "Branch deselection immediately restores native timing");
        f.level.tick++; food.tick(player); equal(player.getHealth(), 5, "Unselected native timer still heals normally");
        naturalTransitions(f);
    }

    private static void naturalTransitions(ProjectileNativeInterceptionTest.Fixture f) throws ReflectiveOperationException {
        var player = f.player;
        for (int transition = 0; transition < 4; transition++) {
            select(f, SkillIds.RISING_RECOVERY); SkillEffectRuntime.reset(player);
            var food = new FoodData(); food.setFoodLevel(18); food.setSaturation(0); player.setHealth(4);
            player.invulnerableTime = 0; player.hurtTime = 0;
            ProjectileNativeInterceptionTest.set(Player.class, player, "foodData", food);
            var tuning = SkillEffectRuntime.resolvedSettings(player).vitality().risingRecovery();
            double speed = RecoveryMath.speed(4, 20, tuning.maxSpeedBonus(), tuning.recoveryCurveExponent());
            int elapsed = Math.max(2, (int) Math.ceil(1.01 / (speed - 1)));
            check(elapsed * speed < 80, "Configured acceleration produces measurable surplus within a native slow-regeneration period");
            for (int i = 0; i < elapsed; i++) { f.level.tick++; food.tick(player); }
            var clock = (com.mistaboom.essence_ascendance.vitality.NaturalRecoveryClockAccess) food;
            check(clock.essenceAscendance$naturalTimer() > elapsed, "Native regeneration period contains actual injected acceleration: timer="
                    + clock.essenceAscendance$naturalTimer() + ", elapsed=" + elapsed + ", speed=" + speed);
            var savedFood = new net.minecraft.nbt.CompoundTag(); food.addAdditionalSaveData(savedFood);
            check(savedFood.getInt("foodTickTimer") == elapsed, "Mid-session save retains ordinary progress and excludes transient skill acceleration");
            var restoredFood = new FoodData(); restoredFood.readAdditionalSaveData(savedFood);
            check(((com.mistaboom.essence_ascendance.vitality.NaturalRecoveryClockAccess) restoredFood).essenceAscendance$naturalTimer() == elapsed,
                    "Reconnected native food state contains no persisted recovery acceleration");
            if (transition == 0 || transition == 3) { food.setFoodLevel(0); food.setSaturation(0); }
            if (transition == 1) {
                f.level.memoryGameRules = new GameRules();
                f.level.memoryGameRules.getRule(GameRules.RULE_NATURAL_REGENERATION).set(false, null);
            }
            if (transition == 2) { food.setFoodLevel(17); food.setSaturation(0); }
            if (transition == 3) {
                // Deliberately defer refresh: the native hook's committed reconciliation must still remove the old surplus.
                f.saved.getPlayerData(player.getUUID()).setLoadoutSelection(SkillGroups.VITALITY_RECOVERY, SkillIds.LIFE_STEAL);
            }
            f.level.tick++; food.tick(player);
            check(clock.essenceAscendance$naturalTimer() == (transition == 0 || transition == 3 ? elapsed + 1 : 0),
                    "Natural acceleration cannot leak across starvation, gamerule, food or deselection boundary " + transition);
            if (transition == 0 || transition == 3) {
                for (int i = elapsed + 1; i < 79; i++) { f.level.tick++; food.tick(player); }
                equal(player.getHealth(), 4, "Starvation retains its native last waiting tick");
                f.level.tick++; food.tick(player);
                equal(player.getHealth(), 3, "First starvation damage occurs at the ordinary shared-timer boundary " + transition);
            }
            f.level.memoryGameRules = null;
        }
    }

    private static void resetBoundaries(ProjectileNativeInterceptionTest.Fixture f, ServerPlayer target) throws ReflectiveOperationException {
        var player = f.player;
        target.setHealth(20); target.invulnerableTime = 0; hit(f, target, 2);
        select(f, SkillIds.RISING_RECOVERY); check(VitalityRecoveryEffects.chainHits(player) == 0 && SkillEffectRuntime.hudSnapshot(player).entries().stream().noneMatch(e -> e.sourceSkill().equals(SkillIds.LIFE_STEAL)), "Mutual exclusion immediately removes chain and HUD card");
        select(f, SkillIds.LIFE_STEAL);
        for (int reset = 0; reset < 5; reset++) {
            target.invulnerableTime = 0; target.setHealth(20); hit(f, target, 2);
            check(VitalityRecoveryEffects.chainHits(player) > 0, "Fixture begins with real active chain");
            switch (reset) {
                case 0 -> SkillEffectRuntime.reset(player);
                case 1 -> SkillEffectRuntime.forget(player);
                case 2 -> { ProjectileNativeInterceptionTest.set(Level.class, f.level, "dimension", Level.NETHER); }
                case 3 -> PlayerRuntimeLifecycleService.onDeath(player);
                case 4 -> SkillEffectRuntime.onEntityRemoved(target);
            }
            check(VitalityRecoveryEffects.chainHits(player) == 0, "Death/logout/dimension/reset/target-removal boundary discards chain " + reset);
            ProjectileNativeInterceptionTest.set(Level.class, f.level, "dimension", Level.OVERWORLD);
        }
    }

    private static void canceledOutcome(ProjectileNativeInterceptionTest.Fixture f, ServerPlayer target) {
        var player = f.player; player.setHealth(5); target.setHealth(20);
        var source = player.damageSources().playerAttack(player);
        EquipmentDamageService.runPrimarySkillAttack(player, target, () -> EquipmentDamageService.observePrimarySkillHit(target, source,
                () -> EquipmentDamageService.withSkillDamageFrame(target, source, () -> {
                    EquipmentDamageService.observeSkillHealthDamage(target, source, () -> target.setHealth(16));
                    return false;
                })));
        equal(player.getHealth(), 5, "A native callback returning canceled after a health write still cannot heal");
        check(VitalityRecoveryEffects.chainHits(player) == 0, "Canceled callback cannot commit chain state");
        target.setHealth(20);
        EquipmentDamageService.runPrimarySkillAttack(player, target, () -> EquipmentDamageService.observePrimarySkillHit(target, source,
                () -> EquipmentDamageService.withSkillDamageFrame(target, source, () -> {
                    EquipmentDamageService.observeSkillHealthDamage(target, source, () -> target.setHealth(16));
                    SkillEffectRuntime.reset(player);
                    return true;
                })));
        equal(player.getHealth(), 5, "Same-selection lifecycle reset during accepted damage invalidates the captured runtime identity");
    }
    private static void additionalOutcomes(ProjectileNativeInterceptionTest.Fixture f, ServerPlayer target) throws ReflectiveOperationException {
        var player = f.player; select(f, SkillIds.LIFE_STEAL); SkillEffectRuntime.reset(player);
        var tuning = SkillEffectRuntime.resolvedSettings(player).vitality().lifeSteal();
        player.setHealth(5); target.setHealth(20);
        target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_ABSORPTION).setBaseValue(3);
        target.setAbsorptionAmount(3);
        hit(f, target, 4);
        equal(target.getHealth(), 19, "Native absorption consumes three points before the actual one-point health loss");
        equal(player.getHealth(), 5 + 4 * tuning.baseHealingFraction(), "Life Steal uses actual accepted health plus absorption damage");
        double before = player.getHealth(); target.setAbsorptionAmount(0); rawHit(f, target, 0);
        equal(player.getHealth(), before, "Accepted or rejected zero-damage outcomes never heal");
        check(VitalityRecoveryEffects.chainHits(player) == 0, "A zero-outcome melee attempt breaks the chain");
        var dying = f.player(NativeCombatHudTest.DyingPlayer.class, new Vec3(0, 0, 3), "recovery_dying");
        SkillRegistry.referencedPermanentMilestoneIds().forEach(f.saved.getPlayerData(dying.getUUID())::completeMilestone);
        dying.setHealth(3); hit(f, dying, 2); before = player.getHealth(); hit(f, dying, 2);
        equal(player.getHealth() - before, tuning.baseHealingFraction() + tuning.perHitHealingFraction(),
                "Killing blow heals from the one remaining accepted health point at its final chain fraction");
        check(dying.deaths == 1 && VitalityRecoveryEffects.chainHits(player) == 0, "Target death clears the chain immediately after its accepted killing hit");
        target.setHealth(20); hit(f, target, 1);
        var replacement = f.player(NativeGuardOutcomeTest.NativePlayer.class, Vec3.ZERO, "recovery_reconnected");
        f.players.remove(replacement.getUUID()); replacement.setUUID(player.getUUID()); f.players.put(replacement.getUUID(), replacement);
        check(VitalityRecoveryEffects.chainHits(replacement) == 0, "Reconnect/respawn replacement identity cannot inherit the previous entity's chain");
        f.players.put(player.getUUID(), player);
        check(VitalityRecoveryEffects.chainHits(player) == 0, "Switching back cannot resurrect forgotten entity state");
    }
    private static void lifecycleDuringNativeDamage(ProjectileNativeInterceptionTest.Fixture f, ServerPlayer target) throws ReflectiveOperationException {
        var player = f.player;
        for (int boundary = 0; boundary < 4; boundary++) {
            final int transition = boundary;
            select(f, boundary == 1 ? SkillIds.RISING_RECOVERY : SkillIds.LIFE_STEAL);
            player.setHealth(5); target.setHealth(20);
            var source = player.damageSources().playerAttack(player);
            EquipmentDamageService.runPrimarySkillAttack(player, target, () -> {
                if (transition == 1) select(f, SkillIds.LIFE_STEAL);
                if (transition == 2) { select(f, SkillIds.RISING_RECOVERY); select(f, SkillIds.LIFE_STEAL); }
                EquipmentDamageService.observePrimarySkillHit(target, source,
                        () -> EquipmentDamageService.withSkillDamageFrame(target, source, () -> {
                            EquipmentDamageService.observeSkillHealthDamage(target, source, () -> {
                                target.setHealth(16);
                                if (transition == 0) SkillEffectRuntime.reset(player);
                                if (transition == 3) { select(f, SkillIds.RISING_RECOVERY); select(f, SkillIds.LIFE_STEAL); }
                            });
                            return true;
                        }));
            });
            equal(target.getHealth(), 16, "Native accepted damage still completes across authority transition " + boundary);
            equal(player.getHealth(), 5, "Reset/selection callbacks cannot award an in-flight melee hit to the replacement state " + boundary);
            check(VitalityRecoveryEffects.chainHits(player) == 0, "Changed authority has no inherited melee chain " + boundary);
        }
        var arrow = f.arrow(Arrow.class); ProjectileOwnership.transferNative(arrow, player);
        var source = player.damageSources().arrow(arrow, player);
        target.setHealth(20); player.setHealth(5);
        EquipmentDamageService.withSkillDamageFrame(target, source, () -> {
            EquipmentDamageService.observeSkillHealthDamage(target, source, () -> {
                target.setHealth(16);
                SkillEffectRuntime.reset(player);
            });
            return true;
        });
        equal(target.getHealth(), 16, "Accepted projectile health write completes even when callback resets attacker runtime");
        equal(player.getHealth(), 5, "Projectile authority is captured before the native health-write callback");
        check(VitalityRecoveryEffects.chainHits(player) == 0, "Projectile callback reset cannot create a fresh chain");
    }
    private static void attunement(ProjectileNativeInterceptionTest.Fixture f, ServerPlayer target) {
        var previous = com.mistaboom.essence_ascendance.config.EssenceConfigManager.serverRuntime();
        try {
            com.mistaboom.essence_ascendance.config.EssenceConfigManager.install(
                    com.mistaboom.essence_ascendance.balance.runtime.RuntimeBalanceDefinition.bootstrap());
            var data = f.saved.getPlayerData(f.player.getUUID());
            data.setTier(com.mistaboom.essence_ascendance.tier.AscendanceTiers.DORMANT);
            SkillEffectRuntime.reset(f.player); f.player.setHealth(5); target.setHealth(20);
            hit(f, target, 4);
            var heals = data.attunement().recent().stream().filter(c -> c.activityId().equals("heal_health")).toList();
            check(heals.size() == 1 && heals.getFirst().credited(),
                    "One accepted Life Steal healing event earns one native Vitality Attunement contribution");
            data.setTier(com.mistaboom.essence_ascendance.tier.AscendanceTiers.TRANSCENDENT);
        } finally {
            if (previous == null) com.mistaboom.essence_ascendance.config.EssenceConfigManager.reset();
            else com.mistaboom.essence_ascendance.config.EssenceConfigManager.install(previous);
        }
    }
    private static void hit(ProjectileNativeInterceptionTest.Fixture f, ServerPlayer target, float amount) {
        target.invulnerableTime = 0;
        f.level.tick++;
        rawHit(f, target, amount);
    }
    private static void rawHit(ProjectileNativeInterceptionTest.Fixture f, ServerPlayer target, float amount) {
        var source = f.player.damageSources().playerAttack(f.player);
        EquipmentDamageService.runPrimarySkillAttack(f.player, target, () -> EquipmentDamageService.observePrimarySkillHit(target, source,
                () -> target.hurt(source, amount)));
    }
    private static void select(ProjectileNativeInterceptionTest.Fixture f, ResourceLocation id) {
        f.saved.getPlayerData(f.player.getUUID()).setLoadoutSelection(SkillGroups.VITALITY_RECOVERY, id); SkillEffectRuntime.refresh(f.player);
    }
    private static SkillEffectHudEntry card(ServerPlayer player) {
        return SkillEffectRuntime.hudSnapshot(player).entries().stream().filter(e -> e.sourceSkill().equals(SkillIds.LIFE_STEAL)).findFirst().orElseThrow();
    }
    private static void equal(double actual, double expected, String message) { check(Math.abs(actual - expected) < 1.0E-4, message + ": " + actual + " != " + expected); }
    private static void check(boolean pass, String message) { checks++; if (!pass) throw new AssertionError(message); }
}
