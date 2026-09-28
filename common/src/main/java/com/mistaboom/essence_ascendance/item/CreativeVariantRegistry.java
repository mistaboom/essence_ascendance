package com.mistaboom.essence_ascendance.item;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Dynamic stacks use each loader's native append event, with no synthetic insertion anchor. */
public final class CreativeVariantRegistry {
    private static final Map<ResourceKey<CreativeModeTab>, Supplier<List<ItemStack>>> PROVIDERS = new LinkedHashMap<>();
    private CreativeVariantRegistry() { }

    public static void register(ResourceKey<CreativeModeTab> tab, Supplier<List<ItemStack>> variants) {
        if (PROVIDERS.putIfAbsent(tab, variants) != null)
            throw new IllegalStateException("Duplicate creative variant provider: " + tab.location());
    }

    public static List<ResourceKey<CreativeModeTab>> tabs() { return List.copyOf(PROVIDERS.keySet()); }

    public static void append(ResourceKey<CreativeModeTab> tab, CreativeModeTab.Output output) {
        var provider = PROVIDERS.get(tab);
        if (provider != null) provider.get().forEach(output::accept);
    }
}
