package com.mistaboom.essence_ascendance.gathering;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Uses ordinary ItemStack#useOn so vanilla/modded light items retain their placement and consumption behavior. */
public final class HotbarLightPlacementService {
    private static final TagKey<Item> COMMON_TORCHES = itemTag("c", "torches");
    private static final TagKey<Item> CUSTOM_LIGHTS = itemTag(EssenceAscendance.MOD_ID, "torchbearer_lights");
    private static final Direction[] SUPPORT_FACES = {
            Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN
    };

    private HotbarLightPlacementService() { }

    public static boolean placeAtFeetIfSpawnDark(ServerPlayer player) {
        if (player == null) return false;
        ServerLevel level = player.serverLevel();
        BlockPos target = player.blockPosition();
        if (!level.getBlockState(target).canBeReplaced() || !couldHostileSpawnFromLighting(level, target)) return false;
        int slot = hotbarLight(player);
        if (slot < 0) return false;
        var inventory = player.getInventory();
        int selected = inventory.selected;
        boolean swapped = slot != selected;
        if (swapped) {
            ItemStack prior = inventory.getItem(selected);
            inventory.setItem(selected, inventory.getItem(slot));
            inventory.setItem(slot, prior);
        }
        boolean placed = false;
        try {
            for (Direction face : SUPPORT_FACES) {
                BlockPos clicked = target.relative(face.getOpposite());
                BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(clicked), face, clicked, false);
                ItemStack activeLight = player.getMainHandItem();
                int creativeCount = activeLight.getCount();
                InteractionResult result = activeLight.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
                if (player.isCreative()) activeLight.setCount(creativeCount);
                if (result.consumesAction()) { placed = true; break; }
            }
            return placed;
        } finally {
            if (swapped) {
                ItemStack usedLight = inventory.getItem(selected);
                ItemStack prior = inventory.getItem(slot);
                inventory.setItem(selected, prior);
                inventory.setItem(slot, usedLight);
                inventory.setChanged();
            }
            if (placed) player.inventoryMenu.broadcastChanges();
        }
    }

    /**
     * Deterministic version of the vanilla monster-light gate. We deliberately
     * test only lighting, not distance/mob-cap/surface/entity-specific rules:
     * Torchbearer answers whether the player's current foot position could be
     * hostile-spawn-dark, then lets ordinary item placement decide whether a
     * torch can actually occupy that exact block.
     */
    private static boolean couldHostileSpawnFromLighting(ServerLevel level, BlockPos pos) {
        int blockLightLimit = level.dimensionType().monsterSpawnBlockLightLimit();
        if (level.getBrightness(LightLayer.BLOCK, pos) > blockLightLimit) return false;

        int localBrightness = level.isThundering()
                ? level.getMaxLocalRawBrightness(pos, 10)
                : level.getMaxLocalRawBrightness(pos);
        return localBrightness <= level.dimensionType().monsterSpawnLightTest().getMaxValue();
    }

    private static int hotbarLight(ServerPlayer player) {
        for (int slot = 0; slot < Inventory.getSelectionSize() && slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty()) continue;
            if (stack.is(Items.TORCH) || stack.is(Items.SOUL_TORCH) || stack.is(COMMON_TORCHES) || stack.is(CUSTOM_LIGHTS)) return slot;
        }
        return -1;
    }

    private static TagKey<Item> itemTag(String namespace, String path) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(namespace, path));
    }
}
