package com.mistaboom.essence_ascendance.mixin;

import net.minecraft.core.RegistryAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

/** Invokes vanilla's own deterministic offer expansion so preview and commitment cannot drift. */
@Mixin(EnchantmentMenu.class)
public interface EnchantmentMenuInsightAccess {
    @Invoker("getEnchantmentList")
    List<EnchantmentInstance> essenceAscendance$getEnchantmentList(RegistryAccess registries, ItemStack stack,
                                                                    int offer, int level);
}
