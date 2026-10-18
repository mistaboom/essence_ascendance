package com.mistaboom.essence_ascendance.projectile;

import com.mistaboom.essence_ascendance.balance.runtime.*;
import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.equipment.*;
import com.mistaboom.essence_ascendance.infuser.EquipmentInfusionRecipe;
import com.mistaboom.essence_ascendance.item.AscendanceItems;
import com.mistaboom.essence_ascendance.stat.EssenceStats;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/** Stage-one checks against real registered items and loader-transformed Player.attack. No world is loaded. */
public final class NativeGlaiveFoundationTest {
    private static int checks;

    public static void run() throws ReflectiveOperationException {
        var f = new ProjectileNativeInterceptionTest.Fixture();
        var previous = EssenceConfigManager.serverRuntime();
        var runtime = RuntimeBalanceDefinition.bootstrap();
        EssenceConfigManager.install(runtime);
        try {
            var player = f.player;
            var target = f.player(NativeGuardOutcomeTest.NativePlayer.class, new Vec3(0, 0, 2), "glaive_target");
            var stack = new ItemStack(AscendanceItems.ASCENDANCE_GLAIVE.get());
            var offhand = new ItemStack(Items.SHIELD);
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            player.setItemInHand(InteractionHand.OFF_HAND, offhand);
            stack.set(DataComponents.CUSTOM_NAME, Component.literal("Preserved glaive"));
            var binding = SoulboundEquipmentData.bind(stack, player.getUUID(), "Foundation");
            var data = f.saved.getPlayerData(player.getUUID());
            craftingAndDiscovery(f);
            double[] ranges = {4, 5, 6, 8, 9, 10};
            var stats = List.of(EssenceStats.MELEE_DAMAGE, EssenceStats.MELEE_ATTACK_SPEED,
                    EssenceStats.ATTACK_KNOCKBACK, EssenceStats.LOOTING, EssenceStats.DURABILITY_EFFICIENCY);
            var caps = EquipmentProfiles.GLAIVE.weaponCapabilities();
            check(caps.hands() == EquipmentWeaponCapabilities.HandRequirement.MAIN_HAND_TWO_HANDED
                    && caps.melee() == EquipmentWeaponCapabilities.MeleeExecution.ACTION, "Independent weapon capabilities");
            check(EquipmentProfiles.SWORD.weaponCapabilities().equals(EquipmentWeaponCapabilities.DEFAULT), "Existing profile default retained");
            for (var tier : EquipmentTier.values()) {
                EquipmentTierData.setTier(stack, tier);
                check(GlaiveTuning.maximumRange(stack) == ranges[tier.ordinal()], "Exact innate range at " + tier);
                var base = EquipmentBaselineService.evaluateForStack(data, EquipmentProfiles.GLAIVE.id(), stack);
                var sword = EquipmentBaselineService.evaluateForEquipmentTier(data, EquipmentProfiles.SWORD.id(), tier);
                check(base.meleeDamage() == sword.meleeDamage() && base.durability() == sword.durability(), "Shared tier physical baseline");
                check(base.meleeAttackSpeed() == EquipmentBaselineService.resolvedValue(sword.tierBaseline().meleeAttackSpeed(),
                        EquipmentProfiles.GLAIVE, EquipmentBaselineProperty.MELEE_ATTACK_SPEED), "Slower profile cadence");
                EquipmentAttributeService.sync(player);
                check(player.getAttributeValue(Attributes.ATTACK_DAMAGE) == base.meleeDamage(), "Existing attribute aggregation");
                var held = EquipmentStatResolver.evaluateItem(stack, EquipmentActivationType.HELD);
                for (var stat : stats) check(held.strength(stat) == (tier == EquipmentTier.LATENT ? 0 : 1), "Channel eligibility " + stat.id());
                check(held.strength(EssenceStats.REACH) == 0, "No interaction Reach channel");
                check(EquipmentTierVisuals.itemTint(stack, 0) != EquipmentTierVisuals.CLEAR_TINT, "Blade base uses grey metal tint");
                check(EquipmentTierVisuals.itemTint(stack, 1) == EquipmentTierVisuals.itemTint(stack, 3), "Blade and butt accents match");
                check(EquipmentTierVisuals.itemTint(stack, 2) == EquipmentTierVisuals.CLEAR_TINT, "Wood remains untinted");
                var swordStack = new ItemStack(AscendanceItems.ASCENDANCE_SWORD.get());
                EquipmentTierData.setTier(swordStack, tier);
                check(EquipmentInfusionRecipe.forWorkpiece(stack).equals(EquipmentInfusionRecipe.forWorkpiece(swordStack)), "Existing melee infusion cost category");
                var upgrade = EquipmentInfusionRecipe.forWorkpiece(stack);
                check(upgrade.isPresent() == (tier != EquipmentTier.TRANSCENDENT), "All five Infuser steps and terminal tier");
                upgrade.ifPresent(recipe -> check(recipe.currentTier()==tier && recipe.targetTier()==tier.next()
                        && recipe.matrixCount()>0 && recipe.totalRequired()>0,"Configured streamed upgrade requirements"));
                check(!WeaponActionRuntime.allowsNativeAttack(stack), "Action never falls through to native combat");
                float health = target.getHealth();
                float readiness = player.getAttackStrengthScale(0);
                player.attack(target);
                check(target.getHealth() == health, "Real transformed Player.attack cannot damage during stage one");
                check(player.getAttackStrengthScale(0) == readiness, "Rejected action never enters native cooldown handling");
                check(stack.use(f.level, player, InteractionHand.MAIN_HAND).getResult() == InteractionResult.FAIL, "Right-click unavailable");
                check(player.getOffhandItem() == offhand, "Offhand is never moved or cleared");
                FracturedEquipmentData.markFractured(stack);
                var fractured = EquipmentBaselineService.evaluateForStack(data, EquipmentProfiles.GLAIVE.id(), stack);
                var latent = EquipmentBaselineService.evaluateForEquipmentTier(data, EquipmentProfiles.GLAIVE.id(), EquipmentTier.LATENT);
                check(fractured.meleeDamage() == latent.meleeDamage() && fractured.meleeAttackSpeed() == latent.meleeAttackSpeed()
                        && GlaiveTuning.maximumRange(stack) == 4, "Fracture uses Latent physics and range");
                check(EquipmentStatResolver.evaluateItem(stack, EquipmentActivationType.HELD).values().isEmpty(), "Fractured channeling disabled");
                FracturedEquipmentData.clearFractured(stack);
                check(SoulboundEquipmentData.read(stack).orElseThrow().equals(binding)
                        && stack.get(DataComponents.CUSTOM_NAME).getString().equals("Preserved glaive"), "Existing ownership and name survive progression/fracture");
            }
            for (var tier : com.mistaboom.essence_ascendance.tier.AscendanceTierRegistry.powerTiers()) {
                check(NativeMeleeRateBonusFloor.contexts(runtime, tier).stream().noneMatch(c -> c.profile().equals(EquipmentProfiles.GLAIVE.id())), "Native charge model excludes action profiles");
            }
            check(NativeDamageBonusFloor.contexts(runtime, EssenceStats.MELEE_DAMAGE).stream()
                    .noneMatch(c -> c.profile().equals(EquipmentProfiles.GLAIVE.id())), "Native damage calibration excludes unavailable actions");
            check(GlaiveTuning.STAB.outwardTicks() == 4 && GlaiveTuning.SWIPE.outwardTicks() == 8, "Fixed outward phase contract");
            check(GlaiveTuning.STAB.recoveryTicks(1.2) > GlaiveTuning.STAB.recoveryTicks(40), "Attack speed reduces recovery");
            check(WeaponActionRuntime.allowsNativeAttack(new ItemStack(Items.IRON_SWORD)), "Ordinary attacks remain enabled");
            System.out.println("NativeGlaiveFoundationTest: " + checks + " registered foundation and native attack rejection checks PASS");
        } finally {
            for (var player : f.onlinePlayers) EquipmentAttributeService.resetTransientState(player);
            f.close();
            if (previous == null) EssenceConfigManager.reset(); else EssenceConfigManager.install(previous);
        }
    }

    private static void craftingAndDiscovery(ProjectileNativeInterceptionTest.Fixture f) {
        try (var reader = new java.io.InputStreamReader(java.util.Objects.requireNonNull(
                NativeGlaiveFoundationTest.class.getResourceAsStream("/data/essence_ascendance/recipe/ascendance_glaive.json")),
                java.nio.charset.StandardCharsets.UTF_8)) {
            var json = com.google.gson.JsonParser.parseReader(reader);
            var recipe = (net.minecraft.world.item.crafting.ShapedRecipe)net.minecraft.world.item.crafting.Recipe.CODEC
                    .parse(com.mojang.serialization.JsonOps.INSTANCE,json).getOrThrow();
            var ingot = com.mistaboom.essence_ascendance.infuser.EssenceInfuserContent.LATENT_INGOT.get();
            var input = net.minecraft.world.item.crafting.CraftingInput.of(3,3,List.of(ItemStack.EMPTY,new ItemStack(ingot),new ItemStack(ingot),
                    ItemStack.EMPTY,new ItemStack(Items.STICK),new ItemStack(ingot),new ItemStack(Items.STICK),ItemStack.EMPTY,ItemStack.EMPTY));
            check(recipe.matches(input,f.level),"Native crafting recipe matches three ingots and two sticks");
            var output = recipe.assemble(input,f.level.registryAccess());
            check(output.is(AscendanceItems.ASCENDANCE_GLAIVE.get()) && output.getCount()==1
                    && EquipmentTierData.tier(output)==EquipmentTier.LATENT && !SoulboundEquipmentData.isSoulbound(output),
                    "Crafting produces the ordinary unbound Latent glaive");
            var jei = com.mistaboom.essence_ascendance.compat.jei.InfuserJeiRecipe.createAll();
            check(jei.stream().filter(r -> r.kind()==com.mistaboom.essence_ascendance.compat.jei.InfuserJeiRecipe.Kind.EQUIPMENT)
                    .filter(r -> r.workpieceVariants().stream().anyMatch(s -> s.is(output.getItem()))).count()==5,
                    "JEI exposes every subsequent glaive Infuser tier");
            output.setDamageValue(10);
            var repair = com.mistaboom.essence_ascendance.infuser.RepairInfusionRecipe.forWorkpiece(output).orElseThrow();
            check(repair.missingDurability()==10 && repair.latentIngotCount()==0,"Ordinary durability uses existing repair costs");
            FracturedEquipmentData.markFractured(output);
            repair = com.mistaboom.essence_ascendance.infuser.RepairInfusionRecipe.forWorkpiece(output).orElseThrow();
            var binding = SoulboundEquipmentData.bind(output,f.player.getUUID(),"Foundation");
            var repaired = repair.createOutput(new com.mistaboom.essence_ascendance.infuser.EssenceInfuserRecipeContext(output,
                    null,null,null,com.mistaboom.essence_ascendance.infuser.EssenceInfuserBalance.profile((com.mistaboom.essence_ascendance.pylon.EssenceFocusTier)null)));
            check(repair.fractured() && repair.latentIngotCount()>0 && !FracturedEquipmentData.isFractured(repaired)
                    && repaired.getDamageValue()==0 && SoulboundEquipmentData.read(repaired).orElseThrow().equals(binding),
                    "Actual Infuser repair clears fracture and preserves permanent ownership");
            var recovery = new SoulboundRecoverySavedData();
            check(recovery.ensureQueued(binding.ownerId(),0,repaired)
                    && recovery.ensureQueued(binding.ownerId(),0,repaired) && recovery.pending(binding.ownerId()).size()==1,
                    "Existing recovery queue preserves one glaive per binding");
        } catch (java.io.IOException e) { throw new AssertionError(e); }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
}
