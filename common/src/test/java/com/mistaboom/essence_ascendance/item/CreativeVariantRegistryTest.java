package com.mistaboom.essence_ascendance.item;

import dev.architectury.registry.CreativeTabOutput;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class CreativeVariantRegistryTest {
    private static int checks;
    public static void main(String[] args) {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        ItemStack first = new ItemStack(Items.STONE);
        first.set(DataComponents.CUSTOM_NAME, Component.literal("component-bearing variant"));
        // Actual Architectury default dispatch combined with NeoForge's observed anchor precondition.
        CreativeTabOutput anchored = new CreativeTabOutput() {
            public void acceptAfter(ItemStack anchor, ItemStack stack, CreativeModeTab.TabVisibility visibility) {
                if (anchor.isEmpty()) throw new IllegalArgumentException("Itemstack 0 minecraft:air does not exist in tab's list");
            }
            public void acceptBefore(ItemStack anchor, ItemStack stack, CreativeModeTab.TabVisibility visibility) {
                acceptAfter(anchor, stack, visibility);
            }
        };
        try { anchored.accept(first); throw new AssertionError("Old empty-anchor failure not reproduced"); }
        catch (IllegalArgumentException expected) { check(expected.getMessage().contains("minecraft:air"), "Observed Architectury dispatch reproduced"); }

        var calls = new AtomicInteger();
        var variants = new AtomicReference<>(List.of(first));
        CreativeVariantRegistry.register(CreativeModeTabs.NATURAL_BLOCKS, () -> { calls.incrementAndGet(); return variants.get(); });
        check(calls.get() == 0, "Registration must not resolve a world-dependent catalog");
        check(CreativeVariantRegistry.tabs().equals(List.of(CreativeModeTabs.NATURAL_BLOCKS)), "Native event keys are retained");
        List<ItemStack> accepted = new ArrayList<>();
        CreativeModeTab.Output nativeOutput = (stack, visibility) -> {
            check(!stack.isEmpty() && stack.getCount() == 1, "Native creative stack contract");
            check(visibility == CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS, "Both parent and search visibility retained");
            accepted.add(stack.copy());
        };
        CreativeVariantRegistry.append(CreativeModeTabs.NATURAL_BLOCKS, nativeOutput);
        check(calls.get() == 1 && ItemStack.matches(accepted.getFirst(), first), "Components survive native append");
        var second = new ItemStack(Items.DIRT);
        variants.set(List.of(second, first)); accepted.clear();
        CreativeVariantRegistry.append(CreativeModeTabs.NATURAL_BLOCKS, nativeOutput);
        check(calls.get() == 2 && accepted.size() == 2 && ItemStack.matches(accepted.get(0), second)
                && ItemStack.matches(accepted.get(1), first), "Rebuild resolves the current catalog in unchanged order");
        CreativeVariantRegistry.append(CreativeModeTabs.INGREDIENTS, nativeOutput);
        check(calls.get() == 2 && accepted.size() == 2, "Unrelated tabs are untouched");
        try { CreativeVariantRegistry.register(CreativeModeTabs.NATURAL_BLOCKS, List::of); throw new AssertionError("Duplicate provider accepted"); }
        catch (IllegalStateException expected) { check(true, "Duplicate provider cannot replace existing variants"); }
        System.out.println("CreativeVariantRegistryTest: " + checks + " checks PASS; old empty-anchor dispatch reproduced; lazy native append preserves components, order and rebuilds");
    }
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
}
