package com.mistaboom.essence_ascendance.gathering;

import com.mistaboom.essence_ascendance.balance.capability.SquatGrowCapabilityProvider;
import com.mistaboom.essence_ascendance.balance.engine.*;
import com.mistaboom.essence_ascendance.balance.generated.BalanceDocument;
import com.mistaboom.essence_ascendance.config.GatheringBalanceSettings;
import com.mistaboom.essence_ascendance.equipment.PlayerAttributedBlockHarvestService;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Real native crop/sapling selection, legacy serialization and honest action-unit contracts. */
public final class GatheringGrowthTest {
    private static int checks;
    public static void main(String[] arguments) {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        BuiltInRegistries.BLOCK.bindTags(Map.of(
                BlockTags.CROPS, List.of(Blocks.WHEAT.builtInRegistryHolder(), Blocks.CARROTS.builtInRegistryHolder()),
                BlockTags.SAPLINGS, List.of(Blocks.OAK_SAPLING.builtInRegistryHolder()),
                PlayerAttributedBlockHarvestService.CROP_YIELD_EXCLUDED, List.of(Blocks.CARROTS.builtInRegistryHolder())));
        var wheat = Blocks.WHEAT.defaultBlockState();
        check(GatheringRuralService.isEligibleGrowthTarget(wheat, null, true), "Immature wheat must remain eligible for growth");
        var mature = ((CropBlock) Blocks.WHEAT).getStateForAge(((CropBlock) Blocks.WHEAT).getMaxAge());
        check(!((CropBlock) Blocks.WHEAT).isValidBonemealTarget(null, BlockPos.ZERO, mature), "Native mature crop gate blocks unnecessary bone-meal operations");
        check(GatheringRuralService.isEligibleGrowthTarget(Blocks.OAK_SAPLING.defaultBlockState(), null, true), "Native bone-meal growth includes ordinary saplings");
        check(!GatheringRuralService.isEligibleGrowthTarget(Blocks.OAK_SAPLING.defaultBlockState(), null, false), "Legacy crop random ticks do not silently expand to trees");
        check(!GatheringRuralService.isEligibleGrowthTarget(Blocks.MOSS_BLOCK.defaultBlockState(), null, true), "Bonemealable spreading terrain does not enter crop/tree growth scope");
        check(!GatheringRuralService.isEligibleGrowthTarget(Blocks.CARROTS.defaultBlockState(), null, true), "Explicit crop exclusion wins for growth");
        check(!GatheringRuralService.isEligibleGrowthTarget(null, null, true), "Missing target is not a growth witness");
        var metadata = new ComparatorBlockEntity(BlockPos.ZERO, Blocks.COMPARATOR.defaultBlockState());
        check(GatheringRuralService.isEligibleGrowthTarget(wheat, metadata, true), "Metadata-only crop entities do not block immature crop growth");
        var furnace = new FurnaceBlockEntity(BlockPos.ZERO, Blocks.FURNACE.defaultBlockState());
        check(!GatheringRuralService.isEligibleGrowthTarget(wheat, furnace, true), "A crop tag cannot bypass native stored inventory/menu protection");
        check(!GatheringRuralService.isEligibleGrowthTarget(Blocks.STONE.defaultBlockState(), metadata, true), "Non-crop metadata cannot opt a production block into growth");
        var legacy = new GatheringBalanceSettings.VerdantStride(4, 108, .54);
        var legacyJson = BalanceDocument.GSON.toJsonTree(legacy).getAsJsonObject();
        check(!legacy.usesBoneMealGrowth() && !legacyJson.has("boneMealGrowth"), "Legacy constructor preserves absent growth-mode field");
        var loadedLegacy = BalanceDocument.GSON.fromJson(legacyJson, GatheringBalanceSettings.VerdantStride.class);
        check(!loadedLegacy.usesBoneMealGrowth() && BalanceDocument.GSON.toJsonTree(loadedLegacy).equals(legacyJson), "Old saved JSON retains exact shape and random-tick behavior");
        var upgraded = new GatheringBalanceSettings.VerdantStride(4, 20, .5, true);
        check(BalanceDocument.GSON.fromJson(BalanceDocument.GSON.toJsonTree(upgraded), GatheringBalanceSettings.VerdantStride.class).usesBoneMealGrowth(), "Typed growth mode round-trips");
        var free = facts(false, true, List.of(), Map.of(), .5, true);
        check(new SquatGrowCapabilityProvider().capabilityAxes().equals(Set.of(CapabilityAxis.CROP_ACCELERATION, CapabilityAxis.TREE_ACCELERATION)),
                "Growth adapter declaration lost native crop/tree domains or invented a production rate");
        check(SquatGrowCapabilityProvider.isFreeUnrestrictedAction(free), "Empty native requirements prove access without acquiring a tool");
        check(!SquatGrowCapabilityProvider.isFreeUnrestrictedAction(facts(true, true, List.of(), Map.of(), .5, true)), "Configured hoe is an acquisition gate");
        check(!SquatGrowCapabilityProvider.isFreeUnrestrictedAction(facts(false, true, List.of("minecraft:diamond"), Map.of(), .5, true)), "Held-item gate cannot be inferred reachable");
        check(!SquatGrowCapabilityProvider.isFreeUnrestrictedAction(facts(false, true, List.of(), Map.of("FEET", "minecraft:diamond_boots"), .5, true)), "Equipment gate cannot become free access");
        check(SquatGrowCapabilityProvider.isFreeUnrestrictedAction(facts(false, false, List.of("minecraft:diamond"), Map.of(), .5, true)), "Disabled native requirements do not impose a phantom item gate");
        for (double invalid : new double[]{0, -1, 1.01, Double.NaN, Double.POSITIVE_INFINITY})
            check(!SquatGrowCapabilityProvider.isFreeUnrestrictedAction(facts(false, true, List.of(), Map.of(), invalid, true)), "Nonproductive/invalid native chance cannot certify growth");
        check(!SquatGrowCapabilityProvider.isFreeUnrestrictedAction(facts(false, true, List.of(), Map.of(), .5, false)), "Missing native bone-meal action cannot certify operation semantics");
        var action = SquatGrowCapabilityProvider.freeAction(free, List.of(CapabilityAxis.CROP_ACCELERATION, CapabilityAxis.TREE_ACCELERATION));
        check(action.attainable() && action.source().reachable() && action.source().stage() == ProgressionBand.ENTRY, "Reusable requirement-free native action has early access");
        check(action.measurements().size() == 2 && action.measurements().stream().allMatch(m -> m.magnitude() == .5
                && m.unit().equals(SquatGrowCapabilityProvider.BONE_MEAL_CHANCE_PER_ACTION)), "Crop/tree facts preserve measured chance-per-action units");
        check(action.measurements().stream().allMatch(m -> m.operation().unitsPerSecond() == null
                && m.operation().uptimeFraction() == null && m.operation().cooldownSeconds() == null
                && m.operation().recurringCosts().isEmpty() && m.scope().targetCount() == null), "No human cadence, uptime or populated farm size is invented");
        check(!action.acquisition().getFirst().rateKnown(), "An immediately available action is not a measured production rate");
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out)).println("GatheringGrowthTest: " + checks + " native growth, access, units and legacy-shape checks PASS");
    }
    private static SquatGrowCapabilityProvider.Configuration facts(boolean hoe, boolean enabled, List<String> held,
                                                                   Map<String,String> equipment, double chance, boolean action) {
        return new SquatGrowCapabilityProvider.Configuration(chance, 3, hoe, enabled, held, equipment,
                false, false, false, List.of(), action);
    }
    private static void check(boolean passed, String message) { checks++; if (!passed) throw new AssertionError(message); }
}
