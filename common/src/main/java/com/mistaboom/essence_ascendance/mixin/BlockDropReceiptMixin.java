package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mistaboom.essence_ascendance.gameplay.CommittedItemDrops;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import java.util.function.Supplier;

/** Identifies the exact native drop before the existing server addFreshEntity commit observer confirms it. */
@Mixin(Block.class)
public abstract class BlockDropReceiptMixin {
    @WrapMethod(method="popResource(Lnet/minecraft/world/level/Level;Ljava/util/function/Supplier;Lnet/minecraft/world/item/ItemStack;)V")
    private static void essenceAscendance$nativeDropIdentity(Level level,Supplier<ItemEntity> supplier,ItemStack stack,Operation<Void> original) {
        original.call(level,CommittedItemDrops.blockDropSupplier(supplier),stack);
    }
}
