package com.mistaboom.essence_ascendance.balance.engine;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.trading.*;
import java.util.*;

public final class NativeTradeProgressionTest {
    private static int checks;
    public static void main(String[] args) {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        var paper = offer("paper", 1, new MerchantOffer(new ItemCost(Items.PAPER, 24), new ItemStack(Items.EMERALD), 16, 2, .05F));
        var target = offer("book", 2, new MerchantOffer(new ItemCost(Items.EMERALD, 12), Optional.of(new ItemCost(Items.BOOK)), new ItemStack(Items.ENCHANTED_BOOK), 12, 5, .2F));
        var search = NativeTradeProgression.plans(List.of(paper, target), target); var plan = search.plans().getFirst();
        check(plan.steps().size() == 2 && plan.steps().getFirst().uses() == 5 && plan.steps().getLast().unlockedLevel() == 2, "Target level unlocked without native 10 villager XP");
        check(plan.externalInputs().equals(Map.of("minecraft:paper", 120L, "minecraft:emerald", 7L, "minecraft:book", 1L)), "Trade outputs, bulk input quantity or second input counted incorrectly");
        check(plan.villagerXp() == 15 && plan.defaultSurplus().getOrDefault("minecraft:emerald", 0L) == 0, "Villager XP or spent emeralds not conserved");
        check(NativeTradeProgression.bill(plan).stream().anyMatch(r -> r.get("id").getAsString().equals("minecraft:paper") && r.get("count").getAsInt() == 120 && r.has("components")), "Bulk material bill lost default component identity");
        check(NativeTradeProgression.plans(List.of(target), target).plans().isEmpty(), "Missing novice offers granted later level");
        var scarce = offer("scarce", 1, new MerchantOffer(new ItemCost(Items.PAPER, 24), new ItemStack(Items.EMERALD), 4, 2, .05F));
        check(NativeTradeProgression.plans(List.of(scarce), target).plans().isEmpty(), "Insufficient first stock invented restocking");
        var noXp = offer("noXp", 1, new MerchantOffer(new ItemCost(Items.PAPER, 24), new ItemStack(Items.EMERALD), 16, 0, .05F));
        check(NativeTradeProgression.plans(List.of(noXp), target).plans().isEmpty(), "Trading with zero NPC XP unlocked a level");
        var wrongType = new NativeTradeProgression.Offer("wrong", paper.profession(), "minecraft:desert", 1, paper.nativeOffer());
        var wrongProfession = new NativeTradeProgression.Offer("wrongProfession", "minecraft:farmer", paper.villagerType(), 1, paper.nativeOffer());
        check(NativeTradeProgression.plans(List.of(wrongType, wrongProfession), target).plans().isEmpty(), "Different NPC types/professions pooled into one villager");
        var future = offer("future", 3, paper.nativeOffer());
        check(NativeTradeProgression.plans(List.of(future), target).plans().isEmpty(), "Later-level income financed earlier unlocking");
        var componentInput = new ItemCost(Items.PAPER).withComponents(p -> p.expect(DataComponents.CUSTOM_NAME, Component.literal("special")));
        var special = offer("special", 1, new MerchantOffer(componentInput, new ItemStack(Items.EMERALD), 16, 10, .05F));
        check(!NativeTradeProgression.supported(special), "Component-sensitive payment became ordinary paper");
        var specialEmerald = new ItemStack(Items.EMERALD); specialEmerald.set(DataComponents.CUSTOM_NAME, Component.literal("marked"));
        var marked = offer("marked", 1, new MerchantOffer(new ItemCost(Items.PAPER, 24), specialEmerald, 16, 2, .05F));
        check(NativeTradeProgression.plans(List.of(marked), target).plans().getFirst().externalInputs().get("minecraft:emerald") == 12, "Configured output credited as exact default input stock");
        var used = paper.nativeOffer().copy(); used.increaseUses();
        check(!NativeTradeProgression.supported(offer("used", 1, used)), "Used offer borrowed full initial stock");
        var discounted = paper.nativeOffer().copy(); discounted.addToSpecialPriceDiff(-1);
        check(!NativeTradeProgression.supported(offer("discounted", 1, discounted)), "Discount context silently admitted");
        var overshoot = offer("overshoot", 1, new MerchantOffer(new ItemCost(Items.PAPER), new ItemStack(Items.EMERALD), 1, 100, .05F));
        var expert = offer("expert", 3, target.nativeOffer());
        var middle = offer("middle", 2, new MerchantOffer(new ItemCost(Items.PAPER), new ItemStack(Items.EMERALD), 1, 1, .05F));
        check(NativeTradeProgression.plans(List.of(overshoot, middle), expert).plans().getFirst().steps().get(1).uses() == 1, "Overshot XP skipped native level-up trade trigger");
        check(NativeTradeProgression.plans(List.of(overshoot), expert).plans().isEmpty(), "Large XP award jumped over a level with no offer");
        var alternatives = new ArrayList<NativeTradeProgression.Offer>();
        for (int i = 0; i < 200; i++) alternatives.add(offer(String.format("choice-%03d", i), 1, paper.nativeOffer()));
        var limited = NativeTradeProgression.plans(alternatives, target);
        check(limited.bounded() && limited.plans().size() == 128, "Search budget absent or truncation reported as exhaustive");
        check(search.equals(NativeTradeProgression.plans(List.of(target, paper), target)), "Input order affected deterministic plan");
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out)).println("NativeTradeProgressionTest: " + checks + " checks PASS");
    }
    private static NativeTradeProgression.Offer offer(String id, int level, MerchantOffer offer) {
        return new NativeTradeProgression.Offer(id, "minecraft:librarian", "minecraft:plains", level, offer);
    }
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
