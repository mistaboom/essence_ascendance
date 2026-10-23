package com.mistaboom.essence_ascendance.compat.jei;

import com.mistaboom.essence_ascendance.ore.LatentOreHost;
import mezz.jei.api.ingredients.subtypes.UidContext;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.List;

/** Exercise the real JEI subtype contract with native host components, without a game session. */
public final class LatentOreJeiTest {
    private static int checks;

    public static void main(String[] args) {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        var interpreter = EssenceAscendanceJeiPlugin.LATENT_ORE_SUBTYPE;
        var identities = new HashSet<Object>();
        for (var host : List.of(Blocks.STONE.defaultBlockState(), Blocks.DEEPSLATE.defaultBlockState(),
                Blocks.NETHERRACK.defaultBlockState(), Blocks.END_STONE.defaultBlockState(),
                Blocks.TUFF.defaultBlockState())) {
            var stack = stack(host);
            var identity = interpreter.getSubtypeData(stack, UidContext.Ingredient);
            check(identity != null && identities.add(identity), "Every baseline/custom host has a distinct JEI ingredient");
            check(identity.equals(interpreter.getSubtypeData(stack.copy(), UidContext.Ingredient)),
                    "Copies and bookmarks retain the same subtype");
            check(interpreter.getSubtypeData(stack, UidContext.Recipe) == null,
                    "Every host matches the common ore smelting/blasting ingredient");
        }
        var upright = stack(Blocks.DEEPSLATE.defaultBlockState());
        var sideways = stack(Blocks.DEEPSLATE.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
        check(!interpreter.getSubtypeData(upright, UidContext.Ingredient)
                        .equals(interpreter.getSubtypeData(sideways, UidContext.Ingredient)),
                "Relevant host properties remain distinct");
        var renamed = upright.copy();
        renamed.set(DataComponents.CUSTOM_NAME, Component.literal("Personal ore"));
        check(interpreter.getSubtypeData(upright, UidContext.Ingredient)
                        .equals(interpreter.getSubtypeData(renamed, UidContext.Ingredient)),
                "Unrelated stack data does not invent extra ore variants");
        check(interpreter.getSubtypeData(new ItemStack(Items.STONE), UidContext.Ingredient) == null,
                "Missing host is not silently identified as stone");
        check(interpreter.getSubtypeData(ItemStack.EMPTY, UidContext.Ingredient) == null,
                "Empty stacks are safe");
        catalogVariants();
        catalogOrder();
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out))
                .println("LatentOreJeiTest: " + checks + " subtype, shared-recipe and late catalog order checks PASS");
    }

    private static void catalogVariants() {
        var stone = stack(Blocks.STONE.defaultBlockState());
        var deepslate = stack(Blocks.DEEPSLATE.defaultBlockState());
        var unusedAlias = new ItemStack(Items.COAL);
        var legacyWithoutHost = new ItemStack(Items.STONE);
        var changes = EssenceAscendanceJeiPlugin.oreCatalogChanges(
                List.of(stone.copy(), unusedAlias, legacyWithoutHost), List.of(stone, deepslate));
        check(changes.removed().equals(List.of(unusedAlias, legacyWithoutHost)),
                "Unused registered aliases and the bare legacy item leave JEI's browse list");
        check(changes.added().equals(List.of(deepslate)),
                "Only the missing selected host is added to JEI");
        var stable = EssenceAscendanceJeiPlugin.oreCatalogChanges(List.of(stone, deepslate),
                List.of(stone.copy(), deepslate.copy()));
        check(stable.removed().isEmpty() && stable.added().isEmpty(),
                "A repeated catalog refresh does not duplicate unchanged host entries");
        var sideways = stack(Blocks.DEEPSLATE.defaultBlockState()
                .setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
        var changedState = EssenceAscendanceJeiPlugin.oreCatalogChanges(
                List.of(deepslate), List.of(sideways));
        check(changedState.removed().equals(List.of(deepslate))
                        && changedState.added().equals(List.of(sideways)),
                "JEI replaces an alias entry when its saved host-state properties change");
    }

    private static void catalogOrder() {
        var revision = new java.util.concurrent.atomic.AtomicLong();
        var family = new java.util.concurrent.atomic.AtomicReference<>(java.util.Set.of(Items.STONE));
        var order = new JeiCatalogOrder(family::get, revision::get);
        var base = stack(Blocks.STONE.defaultBlockState());
        var other = new ItemStack(Items.COAL);
        var initialVariant = stack(Blocks.DEEPSLATE.defaultBlockState());
        int baseIndex = order.createdIndex(base, 10);
        int unrelatedIndex = order.createdIndex(other, 20);
        int initialIndex = order.createdIndex(initialVariant, 30);
        check(baseIndex == 10 && initialIndex == 10, "Initial own subtypes share the family's creative position");
        check(unrelatedIndex == 20, "Unregistered items retain their exact native ordering key");
        check(!order.takePendingSort(), "Initial index construction does not schedule a second sort");
        order.runtimeAvailable();
        int lateIndex = order.createdIndex(stack(Blocks.TUFF.defaultBlockState()), 90000);
        check(lateIndex == baseIndex, "Server-selected late subtype retains the first variant's position");
        check(order.takePendingSort() && !order.takePendingSort(), "A late family addition schedules exactly one native re-sort");
        record Entry(String name, int index) { }
        var entries = new java.util.ArrayList<>(List.of(new Entry("base", baseIndex),
                new Entry("unrelated", unrelatedIndex), new Entry("initial", initialIndex), new Entry("late", lateIndex)));
        entries.sort(java.util.Comparator.comparingInt(Entry::index));
        check(entries.stream().map(Entry::name).toList().equals(List.of("base", "initial", "late", "unrelated")),
                "Stable native creative-order comparison groups late and existing variants together");
        check(order.createdIndex(other, 100000) == 100000 && !order.takePendingSort(),
                "Other plugins' additions keep their own positions without requesting a sort");
        check(order.createdIndex(ItemStack.EMPTY, 100001) == 100001, "Empty ingredients are untouched");
        family.set(java.util.Set.of(Items.STONE, Items.DIRT)); revision.incrementAndGet();
        check(order.createdIndex(new ItemStack(Items.DIRT), 100002) == 100002,
                "A newly declared family starts at its native position after catalog invalidation");
        check(order.createdIndex(new ItemStack(Items.DIRT), 100003) == 100002 && order.takePendingSort(),
                "A new family uses the same generic late-variant policy");
        order.clear();
        check(order.createdIndex(base, 500) == 500 && !order.takePendingSort(),
                "A new JEI runtime does not inherit positions or pending work from an old runtime");
    }

    private static ItemStack stack(BlockState host) {
        // The interpreter receives the registered ore in-game; a vanilla carrier avoids loader bootstrap here.
        var stack = new ItemStack(Items.STONE);
        LatentOreHost.write(stack, host);
        return stack;
    }

    private static void check(boolean passed, String message) {
        checks++;
        if (!passed) throw new AssertionError(message);
    }
}
