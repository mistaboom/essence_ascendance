package com.mistaboom.essence_ascendance.item;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.function.Supplier;

/** Dynamic stacks use each loader's native append event, with no synthetic insertion anchor. */
public final class CreativeVariantRegistry {
    private static final Map<ResourceKey<CreativeModeTab>, Supplier<List<ItemStack>>> PROVIDERS = new LinkedHashMap<>();
    private static long revision;
    private CreativeVariantRegistry() { }

    public static void register(ResourceKey<CreativeModeTab> tab, Supplier<List<ItemStack>> variants) {
        if (PROVIDERS.putIfAbsent(tab, variants) != null)
            throw new IllegalStateException("Duplicate creative variant provider: " + tab.location());
    }

    public static List<ResourceKey<CreativeModeTab>> tabs() { return List.copyOf(PROVIDERS.keySet()); }

    /** Include dynamic catalog changes in native creative-tab cache validity. */
    public static void invalidate() { revision++; }
    public static long revision() { return revision; }

    /** Explicitly registered variant families, used by the optional recipe-viewer ordering. */
    public static Set<Item> variantItems() {
        return PROVIDERS.values().stream().flatMap(provider -> provider.get().stream())
                .filter(stack -> !stack.isEmpty()).map(ItemStack::getItem).collect(Collectors.toUnmodifiableSet());
    }

    public static void append(ResourceKey<CreativeModeTab> tab, CreativeModeTab.Output output) {
        var provider = PROVIDERS.get(tab);
        if (provider != null) provider.get().forEach(output::accept);
    }
}
