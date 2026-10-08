package com.mistaboom.essence_ascendance.mixin;

import net.minecraft.world.item.alchemy.PotionBrewing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.List;

/** Read the effective native recipe list without executing brewing or custom callbacks. */
@Mixin(PotionBrewing.class)
public interface PotionBrewingAccess {
    @Accessor("potionMixes") List<?> essenceAscendance$potionMixes();
}
