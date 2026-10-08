package com.mistaboom.essence_ascendance.mixin;

import net.minecraft.core.Holder;
import net.minecraft.world.item.crafting.Ingredient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.world.item.alchemy.PotionBrewing$Mix")
public interface PotionMixAccess {
    @Accessor("from") Holder<?> essenceAscendance$from();
    @Accessor("to") Holder<?> essenceAscendance$to();
    @Accessor("ingredient") Ingredient essenceAscendance$ingredient();
}
