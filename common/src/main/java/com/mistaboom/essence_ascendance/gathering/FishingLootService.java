package com.mistaboom.essence_ascendance.gathering;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Shared access to loaded fishing loot tables for skill rewards that are not replacements for native fishing. */
public final class FishingLootService {
    private FishingLootService() { }

    public static List<ItemStack> roll(ServerPlayer player, Entity source, Vec3 origin,
                                       ItemStack tool, float luck) {
        return rollTable(player, source, origin, tool, luck, BuiltInLootTables.FISHING);
    }

    /**
     * Resolves exactly one concrete fishing reward after some other mechanic has already succeeded.
     * There is deliberately no second proc chance here: the main fishing table is rolled once only
     * to choose the reward. The fish sub-table and fish item tag are data-driven safety fallbacks for
     * datapacks that make the main fishing table return no stack.
     */
    public static ItemStack rollOneGuaranteedReward(ServerPlayer player, Entity source, Vec3 origin,
                                                    ItemStack tool, float luck) {
        ItemStack reward = randomNonEmpty(player,
                rollTable(player, source, origin, tool, luck, BuiltInLootTables.FISHING));
        if (!reward.isEmpty()) return reward;

        reward = randomNonEmpty(player,
                rollTable(player, source, origin, tool, luck, BuiltInLootTables.FISHING_FISH));
        if (!reward.isEmpty()) return reward;

        return BuiltInRegistries.ITEM.getTag(ItemTags.FISHES)
                .flatMap(fishes -> fishes.getRandomElement(player.getRandom()))
                .map(holder -> new ItemStack(holder.value()))
                .orElse(ItemStack.EMPTY);
    }

    private static ItemStack randomNonEmpty(ServerPlayer player, List<ItemStack> generated) {
        if (player == null || generated == null || generated.isEmpty()) return ItemStack.EMPTY;
        List<ItemStack> candidates = new ArrayList<>();
        for (ItemStack stack : generated) {
            if (stack != null && !stack.isEmpty()) candidates.add(stack);
        }
        if (candidates.isEmpty()) return ItemStack.EMPTY;
        return candidates.get(player.getRandom().nextInt(candidates.size())).copy();
    }

    private static List<ItemStack> rollTable(ServerPlayer player, Entity source, Vec3 origin,
                                             ItemStack tool, float luck, ResourceKey<LootTable> table) {
        if (player == null || source == null || origin == null || tool == null || tool.isEmpty() || table == null) {
            return List.of();
        }
        LootParams params = new LootParams.Builder(player.serverLevel())
                .withParameter(LootContextParams.ORIGIN, origin)
                .withParameter(LootContextParams.TOOL, tool)
                .withParameter(LootContextParams.THIS_ENTITY, source)
                .withLuck(luck)
                .create(LootContextParamSets.FISHING);
        return List.copyOf(player.getServer().reloadableRegistries()
                .getLootTable(table).getRandomItems(params));
    }

    /**
     * Delivers generated stacks only through verified inventory placement or a verified world drop.
     * This intentionally avoids Inventory.add(stack): in Creative that helper can silently consume an
     * unplaceable stack, which made Pocket Nets report a catch that never actually reached the player.
     */
    public static int giveOrDrop(ServerPlayer player, List<ItemStack> loot) {
        if (player == null || loot == null) return 0;

        int totalDelivered = 0;
        boolean changedInventory = false;
        for (ItemStack generated : loot) {
            if (generated == null || generated.isEmpty()) continue;

            ItemStack remainder = generated.copy();
            int beforeInventory = remainder.getCount();
            placeInInventory(player.getInventory(), remainder);
            int inserted = beforeInventory - remainder.getCount();
            if (inserted > 0) {
                totalDelivered += inserted;
                changedInventory = true;
            }

            if (!remainder.isEmpty()) {
                int dropCount = remainder.getCount();
                ItemEntity dropped = player.drop(remainder.copy(), false);
                if (dropped != null) {
                    dropped.setNoPickUpDelay();
                    dropped.setTarget(player.getUUID());
                    totalDelivered += dropCount;
                }
            }
        }

        if (changedInventory) {
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
            if (player.containerMenu != player.inventoryMenu) player.containerMenu.broadcastChanges();
        }
        return totalDelivered;
    }

    /** Shrinks {@code stack} only as items are placed into concrete player inventory slots. */
    private static void placeInInventory(Inventory inventory, ItemStack stack) {
        while (!stack.isEmpty()) {
            int slot = inventory.getSlotWithRemainingSpace(stack);
            if (slot < 0) slot = inventory.getFreeSlot();
            if (slot < 0) return;

            int before = stack.getCount();
            inventory.add(slot, stack);
            if (stack.getCount() >= before) return;
        }
    }
}
