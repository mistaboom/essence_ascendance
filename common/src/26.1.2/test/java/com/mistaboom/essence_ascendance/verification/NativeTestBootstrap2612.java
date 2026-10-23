package com.mistaboom.essence_ascendance.verification;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.ResourceKey;

import java.util.Optional;
import java.util.stream.Stream;

/** Emulates the loader's post-registry item component binding for isolated JavaExec fixtures. */
public final class NativeTestBootstrap2612 {
    private static boolean bound;
    private static HolderLookup.Provider registries;

    private NativeTestBootstrap2612() { }

    public static synchronized void bindBuiltInComponents() {
        if (bound) return;
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        // The standalone JVM has no loader to force the complete vanilla item
        // catalog before the one-shot initializers are resolved.
        BuiltInRegistries.ITEM.getKey(net.minecraft.world.item.Items.IRON_PICKAXE);
        BuiltInRegistries.BLOCK.getKey(net.minecraft.world.level.block.Blocks.STONE);
        var builtIns = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var dynamic = VanillaRegistries.createLookup();
        registries = new HolderLookup.Provider() {
            @Override public Stream<ResourceKey<? extends Registry<?>>> listRegistryKeys() {
                return Stream.concat(builtIns.listRegistryKeys(), dynamic.listRegistryKeys()).distinct();
            }
            @Override public <T> Optional<? extends HolderLookup.RegistryLookup<T>> lookup(
                    ResourceKey<? extends Registry<? extends T>> key) {
                var staticLookup = builtIns.lookup(key);
                return staticLookup.isPresent() ? staticLookup : dynamic.lookup(key);
            }
        };
        bindRegisteredComponents();
        bound = true;
    }

    /** Refresh the real component initializer after a fixture registers its own ID-bound item. */
    public static synchronized void bindRegisteredComponents() {
        if (registries == null) throw new IllegalStateException("Native fixture bootstrap must run first");
        for (var pending : BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(registries))
            pending.forEach((holder, components) -> holder.bindComponents(components));
    }
}
