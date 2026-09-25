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
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out))
                .println("LatentOreJeiTest: " + checks + " subtype and shared-recipe checks PASS");
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
