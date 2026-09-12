package com.mistaboom.essence_ascendance.equipment;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import com.mistaboom.essence_ascendance.stat.EssenceStats;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Validates that block loot belongs to a real player harvest before applying
 * Crop Yield. Loader hooks supply Minecraft's drop context; every eligibility
 * and reward decision remains common and server-authoritative here.
 */
public final class PlayerAttributedBlockHarvestService {

    public static final TagKey<Block> CROP_YIELD_ELIGIBLE = TagKey.create(
            Registries.BLOCK,
            id("crop_yield_eligible")
    );

    public static final TagKey<Block> CROP_YIELD_EXCLUDED = TagKey.create(
            Registries.BLOCK,
            id("crop_yield_excluded")
    );

    private static final TagKey<Block> COMMON_CROPS = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("c", "crops")
    );

    private static final Map<UUID, LastCropHarvest> LAST_CROP_HARVEST =
            new ConcurrentHashMap<>();

    private PlayerAttributedBlockHarvestService() {
    }

    public static List<ItemStack> applyCropYield(
            BlockState state,
            ServerLevel level,
            BlockPos pos,
            BlockEntity blockEntity,
            Entity breaker,
            ItemStack tool,
            List<ItemStack> baseDrops
    ) {
        if (!(breaker instanceof ServerPlayer player)
                || player.level() != level
                || !player.server.isSameThread()
                || player.server.getPlayerList().getPlayer(player.getUUID()) != player
                || !player.isAlive()
                || player.isSpectator()
                || !level.hasChunkAt(pos)
                || blockEntity != null
                || tool == null
                || hasSilkTouch(tool)
                || baseDrops == null
                || baseDrops.isEmpty()
                || !isEligibleMatureCrop(state)) {
            return baseDrops;
        }

        PlayerEssenceData playerData = EssenceSavedData
                .get(player.server)
                .getPlayerData(player.getUUID());
        double percent = cropYieldPercent(player, playerData, tool);

        if (percent <= 0.0D) {
            return baseDrops;
        }

        PercentageBonusYieldService.YieldResult result =
                PercentageBonusYieldService.apply(
                        baseDrops,
                        percent,
                        player.getRandom(),
                        /*
                         * Scale the crop's real loot-table outputs as a unit.
                         * Do not filter BlockItems or seed tags here: carrots,
                         * potatoes, nether wart, and many modded crops use the
                         * same item as both produce and planting material.
                         */
                        stack -> true
                );

        LAST_CROP_HARVEST.put(
                player.getUUID(),
                new LastCropHarvest(
                        state.getBlock().getDescriptionId(),
                        result.eligibleBaseUnits(),
                        result.additionalUnits(),
                        percent
                )
        );
        /* Match vanilla's mutable drop-list behavior for later mixin hooks. */
        return new ArrayList<>(result.drops());
    }

    public static Optional<LastCropHarvest> lastCropHarvest(ServerPlayer player) {
        return Optional.ofNullable(LAST_CROP_HARVEST.get(player.getUUID()));
    }

    public static double cropYieldPercent(ServerPlayer player) {
        PlayerEssenceData playerData = EssenceSavedData
                .get(player.server)
                .getPlayerData(player.getUUID());
        return cropYieldPercent(player, playerData, player.getMainHandItem());
    }

    public static void forget(Entity entity) {
        LAST_CROP_HARVEST.remove(entity.getUUID());
    }

    private static double cropYieldPercent(
            ServerPlayer player,
            PlayerEssenceData playerData,
            ItemStack activeTool
    ) {
        /*
         * Resolve the exact stack supplied by the block-loot context. This
         * prevents armor or a different hand from granting Crop Yield and
         * lets registered tool profiles decide which crop-harvesting tools
         * expose the stat and its tooltip.
         */
        EquipmentStatState active = EquipmentStatResolver.evaluateItem(
                player,
                activeTool,
                EquipmentActivationType.HELD
        );
        return EquipmentValueService.scaledBonus(
                playerData,
                EssenceStats.CROP_YIELD,
                active.strength(EssenceStats.CROP_YIELD)
        );
    }

    private static boolean isEligibleMatureCrop(BlockState state) {
        if (state == null || state.is(CROP_YIELD_EXCLUDED)) {
            return false;
        }

        Block block = state.getBlock();
        if (block instanceof StemBlock || block instanceof AttachedStemBlock) {
            return false;
        }

        boolean explicit = state.is(CROP_YIELD_ELIGIBLE);
        if (!explicit && !state.is(BlockTags.CROPS) && !state.is(COMMON_CROPS)) {
            return false;
        }

        if (block instanceof CropBlock crop) {
            return crop.isMaxAge(state);
        }

        for (Property<?> property : state.getProperties()) {
            if (!(property instanceof IntegerProperty age)
                    || !"age".equals(age.getName())) {
                continue;
            }

            int maximum = age.getPossibleValues()
                    .stream()
                    .mapToInt(Integer::intValue)
                    .max()
                    .orElse(Integer.MAX_VALUE);
            return state.getValue(age) >= maximum;
        }

        /* A mod/datapack author may explicitly opt in an age-less crop. */
        return explicit;
    }

    private static boolean hasSilkTouch(ItemStack tool) {
        ItemEnchantments enchantments = tool.getOrDefault(
                DataComponents.ENCHANTMENTS,
                ItemEnchantments.EMPTY
        );
        return enchantments.keySet().stream()
                .anyMatch(holder -> holder.is(Enchantments.SILK_TOUCH));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                EssenceAscendance.MOD_ID,
                path
        );
    }

    public record LastCropHarvest(
            String blockDescriptionId,
            long eligibleBaseUnits,
            long additionalUnits,
            double bonusPercent
    ) {
    }
}
