package com.mistaboom.essence_ascendance.attunement;

import com.mistaboom.essence_ascendance.balance.economy.FractionalAmountService;
import com.mistaboom.essence_ascendance.equipment.PlayerAttributedBlockHarvestService;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import com.mistaboom.essence_ascendance.mapping.ItemEssenceMappingRegistry;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;

import java.util.List;
import java.util.Map;

/** Real mapped ItemStack, block-state, installed valuation and native adapter boundary invariants.
 * These do not assert mixin transformation or live server gameplay, which require loader smoke/acceptance. */
public final class AttunementAdapterTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); EssenceTypes.init();
        ItemEssenceMappingRegistry.installResolved(Map.of(
                BuiltInRegistries.ITEM.getKey(Items.COAL), Map.of(EssenceTypes.GATHERING, FractionalAmountService.SCALE / 4),
                BuiltInRegistries.ITEM.getKey(Items.COBBLESTONE), Map.of()),
                new ItemEssenceMappingRegistry.LoadSummary(0, 0, 0, 0, 0, List.of()), () -> {});
        check(AttunementGameplay.value(new ItemStack(Items.COAL, 8)) == 2, "Actual output counts preserve generated fractional value");
        check(AttunementGameplay.value(new ItemStack(Items.COBBLESTONE, 64)) == 0, "Suppressed cobblestone cannot regain value in adapter");
        check(AttunementGameplay.value(new ItemStack(Items.DIAMOND, 64)) == 0, "Unmapped item has no invented fallback payout");
        check(AttunementGameplay.value(ItemStack.EMPTY) == 0, "No empty harvest or fishing result");

        BuiltInRegistries.BLOCK.bindTags(Map.of(BlockTags.CROPS, List.of(Blocks.WHEAT.builtInRegistryHolder()),
                PlayerAttributedBlockHarvestService.CROP_YIELD_EXCLUDED, List.of(Blocks.MELON_STEM.builtInRegistryHolder())));
        CropBlock wheat = (CropBlock) Blocks.WHEAT;
        check(!PlayerAttributedBlockHarvestService.isEligibleMatureCrop(wheat.defaultBlockState()), "Immature crops cannot produce harvest credit");
        check(PlayerAttributedBlockHarvestService.isCrop(wheat.defaultBlockState()), "Immature crop cannot fall through to resource mining");
        check(PlayerAttributedBlockHarvestService.isEligibleMatureCrop(wheat.getStateForAge(wheat.getMaxAge())), "Mature crop farms remain eligible without skills or equipment");
        check(!PlayerAttributedBlockHarvestService.isEligibleMatureCrop(Blocks.MELON_STEM.defaultBlockState()), "Excluded stems cannot become crop credit");

        ItemStack input = new ItemStack(Items.COAL, 8);
        check(AttunementWorkstations.consumed(input, input.copy()) == 0, "Preview/no input mutation has no completed input consumption");
        check(AttunementWorkstations.consumed(input, input.copyWithCount(5)) == 3, "Shift craft consumption uses real count delta");
        check(AttunementWorkstations.consumed(input, input.copyWithCount(9)) == 0, "Adding input does not masquerade as consumption");
        check(AttunementWorkstations.consumed(input, ItemStack.EMPTY) == 8, "Final completed operation consumes the last input");
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        String source = AttunementGameplay.itemSignature(sword);
        sword.setDamageValue(42); sword.set(DataComponents.CUSTOM_NAME, Component.literal("A different rename"));
        check(source.equals(AttunementGameplay.itemSignature(sword)), "Renaming/durability changes cannot evade source repetition");
        String longSource = "workstation:" + "long_mod_namespace_and_recipe:".repeat(30);
        String normalized = AttunementGameplay.sourceSignature(longSource);
        check(normalized.length() <= 256 && normalized.equals(AttunementGameplay.sourceSignature(longSource)), "Composite source normalization bounded/deterministic");
        check(!normalized.equals(AttunementGameplay.sourceSignature(longSource + "other")), "Different long recipes retain distinct hashed identities");
        new AttunementEvent.Outcome("use_smithing_tables", normalized, 1, true, "");
        for (double bad : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY, 17})
            check(!AttunementGameplay.validDistance(bad, 16), "Idle/nonfinite/discontinuous movement rejected");
        check(AttunementGameplay.validDistance(.1, 16), "Small real displacement remains eligible");

        // Native XP thresholds and level costs, not totalExperience (which level spending leaves stale).
        int[] nativeLevels = {0, 1, 5, 15, 16, 30, 31, 40};
        int[] nativePoints = {0, 7, 55, 315, 352, 1395, 1507, 2920};
        for (int i = 0; i < nativeLevels.length; i++)
            check(AttunementGameplay.experienceCost(nativeLevels[i], 0, nativeLevels[i]) == nativePoints[i],
                    "Native total XP threshold at level " + nativeLevels[i]);
        check(AttunementGameplay.experienceCost(30, 0, 3) == 306, "Level-30 enchant spends 306 XP of undiscounted effort, not 3 XP");
        check(AttunementGameplay.experienceCost(30, .5f, 3) == 313.5, "Level spending retains fractional bar in native point conversion");
        check(AttunementGameplay.experienceCost(16, .5f, 2) == 75.5, "Level cost crosses first native curve breakpoint");
        check(AttunementGameplay.experienceCost(31, .5f, 2) == 226, "Level cost crosses second native curve breakpoint");
        check(AttunementGameplay.experienceCost(40, 0, 5) == 875, "Higher-level operation retains its higher XP effort");
        check(AttunementGameplay.experienceCost(0, 0, 3) == 27, "Zero-cost efficiency retains original minimum affordable effort");
        check(AttunementGameplay.experienceCost(1, 0, 3) == 27, "Discounted affordability cannot erase nominal levels above current level");
        check(AttunementGameplay.experienceCost(30, 0, 0) == 0, "No original XP cost creates no invented operation credit");
        check(AttunementGameplay.experienceCost(30, 0, -1) == 0, "Invalid negative costs create no operation credit");
        check(AttunementGameplay.experienceCost(30, Float.NaN, 3) == 306, "Invalid fractional metadata cannot poison credit");
        double extremeCost = AttunementGameplay.experienceCost(Integer.MAX_VALUE, 1, Integer.MAX_VALUE);
        check(Double.isFinite(extremeCost) && extremeCost > Integer.MAX_VALUE,
                "Large modded costs remain finite without integer overflow or iteration per level");
        for (int level : new int[]{15, 16, 30, 31, 40, 1000}) {
            double joined = AttunementGameplay.experienceCost(level, .5f, 5);
            double split = AttunementGameplay.experienceCost(level, .5f, 2)
                    + AttunementGameplay.experienceCost(level - 2, .5f, 3);
            check(joined == split, "Splitting a native level price preserves total effort at level " + level);
        }

        // Actual 1.21.1 mapped declarations, not external-API stand-ins.
        LivingEntity.class.getDeclaredMethod("heal", float.class);
        LivingEntity.class.getDeclaredMethod("getDamageAfterArmorAbsorb", net.minecraft.world.damagesource.DamageSource.class, float.class);
        LivingEntity.class.getDeclaredMethod("getDamageAfterMagicAbsorb", net.minecraft.world.damagesource.DamageSource.class, float.class);
        LivingEntity.class.getDeclaredMethod("addEffect", net.minecraft.world.effect.MobEffectInstance.class, net.minecraft.world.entity.Entity.class);
        Player.class.getDeclaredMethod("eat", Level.class, ItemStack.class, net.minecraft.world.food.FoodProperties.class);
        Player.class.getDeclaredMethod("getXpNeededForNextLevel");
        FoodData.class.getDeclaredMethod("tick", Player.class);
        ServerPlayerGameMode.class.getDeclaredMethod("destroyBlock", BlockPos.class);
        ServerGamePacketListenerImpl.class.getDeclaredMethod("handleMovePlayer", net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.class);
        AbstractContainerMenu.class.getDeclaredMethod("clicked", int.class, int.class, ClickType.class, Player.class);
        BrewingStandBlockEntity.class.getDeclaredMethod("doBrew", Level.class, BlockPos.class, NonNullList.class);
        checks += 11;
        System.out.println("AttunementAdapterTest: " + checks + " checks passed (native mixin/gameplay acceptance separate)");
    }
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
}
