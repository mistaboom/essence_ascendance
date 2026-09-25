package com.mistaboom.essence_ascendance.mixin;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A connection's selected hosts can change while vanilla's feature flags remain identical. */
@Mixin(CreativeModeTabs.class)
public interface LatentOreCreativeTabCacheAccess {
    @Accessor("CACHED_PARAMETERS")
    static void essence$invalidate(CreativeModeTab.ItemDisplayParameters parameters) { throw new AssertionError(); }
}
