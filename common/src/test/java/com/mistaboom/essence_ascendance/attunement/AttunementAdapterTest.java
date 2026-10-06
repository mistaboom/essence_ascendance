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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.DoubleSupplier;

/** Real mapped ItemStack, block-state, installed valuation and native adapter boundary invariants.
 * These do not assert mixin transformation or live server gameplay, which require loader smoke/acceptance. */
public final class AttunementAdapterTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); EssenceTypes.init();
        ItemEssenceMappingRegistry.installResolved(Map.of(
                BuiltInRegistries.ITEM.getKey(Items.COAL), Map.of(EssenceTypes.GATHERING, FractionalAmountService.SCALE / 4),
                BuiltInRegistries.ITEM.getKey(Items.WHEAT_SEEDS), Map.of(EssenceTypes.GATHERING, 117 * FractionalAmountService.SCALE),
                BuiltInRegistries.ITEM.getKey(Items.WHEAT), Map.of(),
                BuiltInRegistries.ITEM.getKey(Items.COBBLESTONE), Map.of()),
                new ItemEssenceMappingRegistry.LoadSummary(0, 0, 0, 0, 0, List.of()), () -> {});
        check(AttunementGameplay.value(new ItemStack(Items.COAL, 8)) == 2, "Actual output counts preserve generated fractional value");
        check(AttunementGameplay.value(new ItemStack(Items.COBBLESTONE, 64)) == 0, "Suppressed cobblestone cannot regain value in adapter");
        check(AttunementGameplay.value(new ItemStack(Items.DIAMOND, 64)) == 0, "Unmapped item has no invented fallback payout");
        check(AttunementGameplay.value(ItemStack.EMPTY) == 0, "No empty harvest or fishing result");
        committedHarvestReceipts();

        BuiltInRegistries.BLOCK.bindTags(Map.of(
                BlockTags.CROPS, List.of(Blocks.WHEAT.builtInRegistryHolder(), Blocks.COCOA.builtInRegistryHolder(),
                        Blocks.CARROTS.builtInRegistryHolder(), Blocks.OAK_LOG.builtInRegistryHolder()),
                PlayerAttributedBlockHarvestService.CROP_YIELD_ELIGIBLE, List.of(Blocks.STONE.builtInRegistryHolder(),
                        Blocks.ATTACHED_MELON_STEM.builtInRegistryHolder()),
                PlayerAttributedBlockHarvestService.CROP_YIELD_EXCLUDED, List.of(Blocks.MELON_STEM.builtInRegistryHolder(),
                        Blocks.CARROTS.builtInRegistryHolder())));
        CropBlock wheat = (CropBlock) Blocks.WHEAT;
        check(!PlayerAttributedBlockHarvestService.isEligibleMatureCrop(wheat.defaultBlockState()), "Immature crops cannot produce harvest credit");
        check(PlayerAttributedBlockHarvestService.isCrop(wheat.defaultBlockState()), "Immature crop cannot fall through to resource mining");
        check(PlayerAttributedBlockHarvestService.isEligibleMatureCrop(wheat.getStateForAge(wheat.getMaxAge())), "Mature crop farms remain eligible without skills or equipment");
        check(!PlayerAttributedBlockHarvestService.isEligibleMatureCrop(Blocks.MELON_STEM.defaultBlockState()), "Excluded stems cannot become crop credit");
        check(PlayerAttributedBlockHarvestService.isEligibleCropBlock(Blocks.WHEAT), "Generation recognizes a mature crop even when its default state is immature");
        check(!PlayerAttributedBlockHarvestService.isEligibleMatureCrop(Blocks.COCOA.defaultBlockState()), "Generic age-property crop starts immature");
        check(PlayerAttributedBlockHarvestService.isEligibleCropBlock(Blocks.COCOA), "Generation shares generic maximum-age crop eligibility");
        check(!PlayerAttributedBlockHarvestService.isEligibleCropBlock(Blocks.CARROTS), "Explicit exclusion wins over a mature native crop");
        check(!PlayerAttributedBlockHarvestService.isEligibleCropBlock(Blocks.ATTACHED_MELON_STEM), "Explicit opt-in cannot admit an attached stem");
        check(PlayerAttributedBlockHarvestService.isEligibleCropBlock(Blocks.STONE), "Explicit age-less opt-in is shared by gameplay and saved evidence");
        check(!PlayerAttributedBlockHarvestService.isEligibleCropBlock(Blocks.OAK_LOG), "A broad crops tag alone cannot admit an age-less block");
        check(!PlayerAttributedBlockHarvestService.isEligibleCropBlock(null), "Missing block produces no crop eligibility proof");
        BlockState matureWheat = wheat.getStateForAge(wheat.getMaxAge());
        // Valid native metadata-only entity exercises the storage-interface boundary without
        // registering a synthetic type after bootstrap. The state is the captured harvest state.
        BlockEntity cropMetadata = new ComparatorBlockEntity(BlockPos.ZERO, Blocks.COMPARATOR.defaultBlockState());
        check(!PlayerAttributedBlockHarvestService.blocksPlayerHarvest(matureWheat, cropMetadata), "A mature crop's metadata entity does not suppress harvest credit or yield");
        check(PlayerAttributedBlockHarvestService.blocksPlayerHarvest(wheat.defaultBlockState(), cropMetadata), "Immature crop metadata cannot bypass maturity");
        CropBlock carrots = (CropBlock) Blocks.CARROTS;
        check(PlayerAttributedBlockHarvestService.blocksPlayerHarvest(carrots.getStateForAge(carrots.getMaxAge()), cropMetadata), "Crop metadata cannot bypass explicit exclusions");
        check(PlayerAttributedBlockHarvestService.blocksPlayerHarvest(Blocks.ATTACHED_MELON_STEM.defaultBlockState(), cropMetadata), "Crop metadata cannot bypass stem exclusion");
        check(PlayerAttributedBlockHarvestService.blocksPlayerHarvest(Blocks.DIRT.defaultBlockState(), cropMetadata), "An unrelated block entity retains conservative harvest exclusion");
        check(!PlayerAttributedBlockHarvestService.blocksPlayerHarvest(Blocks.DIRT.defaultBlockState(), null), "Ordinary resource blocks without metadata remain eligible");
        var furnace = new FurnaceBlockEntity(BlockPos.ZERO, Blocks.FURNACE.defaultBlockState());
        check(PlayerAttributedBlockHarvestService.blocksPlayerHarvest(matureWheat, furnace), "Native stored inventory is excluded even when the supplied state passes crop eligibility");
        check(PlayerAttributedBlockHarvestService.blocksPlayerHarvest(matureWheat, new MenuCropMetadata()), "Menu-only block entities cannot bypass stored-content protection");

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
        check(ServerLevel.class.getDeclaredMethod("addFreshEntity", net.minecraft.world.entity.Entity.class).getReturnType() == boolean.class,
                "Shared server insertion boundary reports actual spawn success");
        ServerGamePacketListenerImpl.class.getDeclaredMethod("handleMovePlayer", net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.class);
        AbstractContainerMenu.class.getDeclaredMethod("clicked", int.class, int.class, ClickType.class, Player.class);
        BrewingStandBlockEntity.class.getDeclaredMethod("doBrew", Level.class, BlockPos.class, NonNullList.class);
        checks += 11;
        System.out.println("AttunementAdapterTest: " + checks + " checks passed (native mixin/gameplay acceptance separate)");
    }
    private static void committedHarvestReceipts() {
        Object level = new Object(), foreignLevel = new Object();
        BlockPos root = new BlockPos(-7, 64, -9);
        Vec3 local = new Vec3(-6.75, 64.75, -8.5);
        DoubleSupplier unvalued = () -> { throw new AssertionError("Unrelated/rejected/duplicate drop must not resolve valuation"); };
        var drops = new CommittedHarvestDrops(level, root);
        ItemStack finalSeeds = new ItemStack(Items.WHEAT_SEEDS, 1);
        finalSeeds.setCount(3); // A captured drop may be changed before the final successful spawn.
        drops.observe(level, new UUID(0, 1), local, true, () -> AttunementGameplay.value(new ItemStack(Items.WHEAT, 1)));
        drops.observe(level, new UUID(0, 2), local, true, () -> AttunementGameplay.value(finalSeeds));
        drops.observe(level, new UUID(0, 2), local, true, unvalued);
        drops.observe(foreignLevel, new UUID(0, 3), local, true, unvalued);
        drops.observe(level, new UUID(0, 3), local.add(1, 0, 0), true, unvalued);
        drops.observe(level, new UUID(0, 3), local, false, unvalued);
        drops.observe(level, new UUID(0, 3), local, true, () -> AttunementGameplay.value(new ItemStack(Items.COAL, 8)));
        drops.observe(level, new UUID(0, 4), new Vec3(Double.NaN, 64, -9), true, unvalued);
        drops.observe(level, new UUID(0, 5), new Vec3(-7, Double.POSITIVE_INFINITY, -9), true, unvalued);
        drops.observe(level, new UUID(0, 6), new Vec3(-7, 64, Double.NEGATIVE_INFINITY), true, unvalued);
        drops.observe(level, null, local, true, unvalued);
        drops.observe(level, new UUID(0, 7), local, true, () -> Double.NaN);
        drops.observe(level, new UUID(0, 8), local, true, () -> Double.POSITIVE_INFINITY);
        drops.observe(level, new UUID(0, 9), local, true, () -> -1);
        check(drops.complete(true) == 353, "Committed wheat/seeds use final installed payouts and fractions, once per local entity; invalid spawns contribute nothing");
        check(drops.complete(true) == 0, "Repeated completion cannot award the same harvest twice");
        drops.observe(level, new UUID(0, 10), local, true, unvalued);
        check(drops.complete(true) == 0, "Closed harvest cannot absorb a later spawn");

        var canceled = new CommittedHarvestDrops(level, root);
        canceled.observe(level, new UUID(0, 11), local, true, () -> AttunementGameplay.value(finalSeeds));
        check(canceled.complete(false) == 0, "Failed block destruction discards even successfully spawned items");
        check(canceled.complete(true) == 0, "A canceled harvest cannot later be credited");

        var parent = new CommittedHarvestDrops(level, root);
        var child = new CommittedHarvestDrops(level, root.above());
        parent.observe(level, new UUID(0, 12), local, true, () -> 1);
        child.observe(level, new UUID(0, 13), local.add(0, 1, 0), true, () -> 2);
        parent.observe(level, new UUID(0, 14), local, true, () -> 3);
        check(child.complete(true) == 2 && parent.complete(true) == 4, "Nested scopes keep independent receipts while the parent resumes");

        var mutablePosition = root.mutable();
        var pinned = new CommittedHarvestDrops(level, mutablePosition);
        mutablePosition.move(100, 0, 0);
        pinned.observe(level, new UUID(0, 15), new Vec3(-7, 64, -9), true, () -> 1);
        pinned.observe(level, new UUID(0, 16), new Vec3(-6, 64, -9), true, unvalued);
        check(pinned.complete(true) == 1, "Scope pins the original block; adjacent and relocated positions do not gain credit");
        var bounded = new CommittedHarvestDrops(level, root);
        bounded.observe(level, new UUID(0, 17), local, true, () -> Double.MAX_VALUE);
        bounded.observe(level, new UUID(0, 18), local, true, () -> Double.MAX_VALUE);
        check(bounded.complete(true) == (double) Long.MAX_VALUE, "Large finite installed payouts remain bounded");
        AttunementGameplay.spawnedHarvest(null, false);
        AttunementGameplay.spawnedHarvest(null, true);
        AttunementGameplay.finishHarvest(false);
    }
    private static final class MenuCropMetadata extends BlockEntity implements MenuProvider {
        private MenuCropMetadata() { super(BlockEntityType.SIGN, BlockPos.ZERO, Blocks.OAK_SIGN.defaultBlockState()); }
        @Override public Component getDisplayName() { return Component.literal("Crop metadata menu fixture"); }
        @Override public AbstractContainerMenu createMenu(int windowId, Inventory inventory, Player player) { return null; }
    }
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
}
