package com.mistaboom.essence_ascendance.valuation;

import com.mistaboom.essence_ascendance.balance.engine.NativeItemActions;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;

import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

/** Applications, rather than recipe registrations, supply bounded demand evidence. */
final class DownstreamDemand {
    private DownstreamDemand() { }

    record Application(String family, double weight, boolean significant, boolean crossMod) { }
    record Summary(int applicationCount, double weightedApplications, int significantCount,
                   int crossModCount, double multiplier) { }

    static Application application(Item input, Item output, boolean progressionEquipment) {
        var outputId = BuiltInRegistries.ITEM.getKey(output);
        var inputId = BuiltInRegistries.ITEM.getKey(input);
        boolean crossMod = !outputId.getNamespace().equals(inputId.getNamespace());
        ItemStack stack = new ItemStack(output);
        if (output instanceof BlockItem blockItem) {
            Block block = blockItem.getBlock();
            // Material/color/oxidation variants have the same demonstrated application.
            // These are native behaviors, never item-name or namespace guesses.
            if (block instanceof DoorBlock) return family("door", crossMod);
            if (block instanceof TrapDoorBlock) return family("trapdoor", crossMod);
            if (block instanceof FenceGateBlock) return family("fence_gate", crossMod);
            if (block instanceof FenceBlock || block instanceof WallBlock || block instanceof IronBarsBlock)
                return family("barrier", crossMod);
            if (block instanceof BedBlock) return family("rest", crossMod);
            if (block instanceof SignBlock) return family("sign", crossMod);
            if (block instanceof BannerBlock || block instanceof WallBannerBlock) return family("banner", crossMod);
            if (block instanceof AbstractSkullBlock) return family("skull_display", crossMod);
            if (block instanceof ShulkerBoxBlock) return family("portable_storage", crossMod);
            // A block entity is concrete operating-state evidence. Keep distinct machine
            // outputs distinct, including modded machines implemented by one shared class.
            if (block.defaultBlockState().hasBlockEntity())
                return new Application("block_entity:" + outputId, 1, false, crossMod);
            if (block.defaultBlockState().isSignalSource())
                return family("signal:" + block.getClass().getName(), crossMod);
            if (block instanceof BaseRailBlock || block instanceof TntBlock || block instanceof RespawnAnchorBlock
                    || block instanceof CraftingTableBlock || block instanceof AnvilBlock
                    || block instanceof GrindstoneBlock || block instanceof StonecutterBlock
                    || block instanceof CartographyTableBlock || block instanceof SmithingTableBlock
                    || block instanceof LoomBlock)
                return family("behavior:" + block.getClass().getName(), crossMod);
            if (block.defaultBlockState().is(ProceduralValuationTags.CLIMBABLE)) return family("climbing", crossMod);
            if (block.getStateDefinition().getPossibleStates().stream().anyMatch(state -> state.getLightEmission() > 0))
                return family("lighting", crossMod);
            // All otherwise unproven placeable shapes share one small construction use.
            // Hundreds of stairs/slabs/waxed/colored forms cannot manufacture demand.
            return new Application("construction", ProceduralValuationSettings.DOWNSTREAM_CONSTRUCTION_WEIGHT, false, false);
        }
        boolean nativeFunction = progressionEquipment || stack.has(DataComponents.TOOL)
                || stack.has(DataComponents.FOOD) || stack.isDamageableItem()
                || !NativeItemActions.describe(output).isEmpty()
                || output instanceof BoatItem || output instanceof MinecartItem
                || output instanceof FireworkRocketItem || output instanceof ArrowItem
                || output instanceof BucketItem || output instanceof MapItem || output instanceof PotionItem;
        // Distinct non-block intermediates retain a small application signal. Registering
        // another recipe for that same plate/component does not increase it.
        return new Application("item:" + outputId,
                nativeFunction ? 1 : ProceduralValuationSettings.DOWNSTREAM_COMPONENT_WEIGHT,
                progressionEquipment, crossMod && nativeFunction);
    }

    private static Application family(String family, boolean crossMod) {
        return new Application(family, 1, false, crossMod);
    }

    static Summary summarize(Collection<Application> applications) {
        Map<String, Application> unique = new TreeMap<>();
        for (Application application : applications) unique.merge(application.family(), application,
                (a, b) -> new Application(a.family(), Math.max(a.weight(), b.weight()),
                        a.significant() || b.significant(), a.crossMod() || b.crossMod()));
        double weighted = unique.values().stream().mapToDouble(Application::weight).sum();
        int significant = (int) unique.values().stream().filter(Application::significant).count();
        int crossMod = (int) unique.values().stream().filter(Application::crossMod).count();
        // A namespace is provenance, not independent functional/acquisition proof.
        double multiplier = Math.min(ProceduralValuationSettings.DOWNSTREAM_MAX_MULTIPLIER,
                1 + weighted * ProceduralValuationSettings.DOWNSTREAM_USE_STEP
                        + significant * ProceduralValuationSettings.DOWNSTREAM_SIGNIFICANT_STEP);
        return new Summary(unique.size(), weighted, significant, crossMod, multiplier);
    }
}
