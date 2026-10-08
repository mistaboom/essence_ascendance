package com.mistaboom.essence_ascendance.balance.engine;

import com.mojang.serialization.Lifecycle;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import java.util.*;

public final class NativeEnchantingTest {
    private static int checks;
    public static void main(String[] args) {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        check(NativeEnchanting.xpForLevel(0) == 0 && NativeEnchanting.xpForLevel(15) == 315
                && NativeEnchanting.xpForLevel(16) == 352 && NativeEnchanting.xpForLevel(30) == 1395
                && NativeEnchanting.xpForLevel(31) == 1507, "Native level boundaries/initial XP incorrectly converted");
        xp(); offers();
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out)).println("NativeEnchantingTest: " + checks + " checks PASS");
    }
    private static void xp() {
        for (var difficulty : net.minecraft.world.Difficulty.values()) for (boolean loot : List.of(false, true)) for (boolean spawn : List.of(false, true))
            check(NativeEnchanting.mobXpEnabled(difficulty, loot, spawn) == (difficulty != net.minecraft.world.Difficulty.PEACEFUL && loot && spawn),
                    "XP encounter guard must retain difficulty, drop and recurring spawn settings");
        var enemy = new EnemyReference("minecraft:creeper", EnemyReference.Encounter.ROUTINE, ProgressionBand.MID,
                Map.of(CapabilityAxis.EXPERIENCE, 5.0), true, .8, List.of(), "fixture observed reward");
        check(!NativeEnchanting.xpSupply(List.of(enemy), Map.of(), true).access().placement().reachable(), "Registered XP is not encounter access");
        var timer = new SourceAvailability.Timer(SourceAvailability.Applicability.OFF, 0, List.of());
        var availability = new SourceAvailability(enemy.entityId(), SourceAvailability.Category.CONDITIONAL_RENEWABLE,
                SourceAvailability.Scope.SHARED, List.of(), List.of(), List.of("supported recurring encounter"), timer, timer, true, .5, 1, List.of());
        var source = new AcquisitionSource(enemy.entityId(), AcquisitionSource.Kind.MOB_DROP, ProgressionBand.EARLY, 1, true, false, 0,
                .7, List.of(), "fixture supported spawn and drop", availability);
        var resource = new ResourceEvidence("minecraft:gunpowder", ProgressionBand.EARLY, Availability.RENEWABLE_MANUAL, Automation.NONE,
                true, true, 1, .7, List.of(source), List.of());
        var resources = Map.of(resource.itemId(), resource);
        var supply = NativeEnchanting.xpSupply(List.of(enemy), resources, true);
        check(supply.access().placement().reachable() && supply.access().placement().stage() == ProgressionBand.MID,
                "XP supply must preserve both encounter and acquisition stage");
        check(!NativeEnchanting.xpSupply(List.of(enemy), resources, false).access().placement().reachable(), "Disabled hostile XP admitted");
        var noXp = new EnemyReference(enemy.entityId(), enemy.encounter(), enemy.stage(), Map.of(), true, .8, List.of(), "no reward observation");
        check(!NativeEnchanting.xpSupply(List.of(noXp), resources, true).access().placement().reachable(), "Missing XP became an invented default");
        var unknown = new SourceAvailability(enemy.entityId(), availability.category(), availability.scope(), List.of(), List.of(), List.of(),
                timer, timer, true, .5, 1, List.of("unresolved spawn condition"));
        var uncertain = new AcquisitionSource(source.id(), source.kind(), source.stage(), 1, true, false, 0, .7, List.of(), "unresolved", unknown);
        check(!NativeEnchanting.xpSupply(List.of(enemy), Map.of(resource.itemId(), new ResourceEvidence(resource.itemId(), resource.stage(), resource.availability(),
                resource.automation(), true, true, 1, .7, List.of(uncertain), List.of())), true).access().placement().reachable(), "Unresolved loot/spawn path proved XP access");
    }
    private static void offers() {
        var registry = new MappedRegistry<Enchantment>(Registries.ENCHANTMENT, Lifecycle.stable());
        var key = ResourceKey.create(Registries.ENCHANTMENT, ResourceLocation.parse("fixture:mining"));
        var definition = Enchantment.definition(HolderSet.direct(Items.IRON_PICKAXE.builtInRegistryHolder()), 10, 5,
                Enchantment.dynamicCost(1, 10), Enchantment.dynamicCost(50, 10), 1, EquipmentSlotGroup.MAINHAND);
        Holder<Enchantment> enchantment = registry.register(key, new Enchantment(Component.literal("fixture"), definition, HolderSet.direct(), DataComponentMap.EMPTY), RegistrationInfo.BUILT_IN);
        registry.freeze();
        var stack = new ItemStack(Items.IRON_PICKAXE);
        var low = NativeEnchanting.offers(stack, List.of(enchantment), 0, 128);
        var high = NativeEnchanting.offers(stack, List.of(enchantment), 15, 128);
        check(!low.isEmpty() && high.stream().mapToInt(NativeEnchanting.Offer::level).max().orElseThrow()
                > low.stream().mapToInt(NativeEnchanting.Offer::level).max().orElseThrow(), "Actual bookshelf setup must affect witnessed levels");
        check(high.equals(NativeEnchanting.offers(stack, List.of(enchantment), 15, 128)), "Offer witnesses are not deterministic");
        check(NativeEnchanting.offers(stack, List.of(), 15, 128).isEmpty(), "Absent table tag entry became available");
        check(NativeEnchanting.offers(new ItemStack(Items.IRON_SWORD), List.of(enchantment), 15, 128).isEmpty(), "Unsupported target borrowed pickaxe enchantment");
        check(NativeEnchanting.offers(new ItemStack(Items.BOOK), List.of(enchantment), 15, 128).isEmpty(), "Stored book inferred equipment application without anvil proof");
        for (var offer : high) {
            var random = net.minecraft.util.RandomSource.create(offer.seed()); int cost = 0;
            for (int slot = 0; slot <= offer.slot(); slot++) cost = EnchantmentHelper.getEnchantmentCost(random, slot, offer.bookshelves(), stack);
            check(cost == offer.requiredPlayerLevel() && offer.spentLevels() == offer.slot() + 1 && offer.lapis() == offer.spentLevels(), "Offer cost diverged from menu semantics");
            var selected = EnchantmentHelper.selectEnchantment(net.minecraft.util.RandomSource.create((long)offer.seed() + offer.slot()), stack, cost, List.of(enchantment).stream());
            check(selected.stream().anyMatch(e -> e.enchantment == enchantment && e.level == offer.level()), "Published offer cannot be reproduced by native selection");
            var bill = NativeEnchanting.bill(offer);
            check(bill.getFirst().has("components") && bill.getFirst().getAsJsonObject("components").isEmpty(), "Already-enchanted loot/trade equipment could stand in for an exact unenchanted crafting result");
            check(bill.size() == 4 && bill.stream().anyMatch(s -> s.get("id").getAsString().equals("minecraft:bookshelf") && s.get("count").getAsInt() == 15),
                    "Bookshelves were omitted or treated as one cheap shared token");
        }
    }
    private static void check(boolean condition, String reason) { checks++; if (!condition) throw new AssertionError(reason); }
}
