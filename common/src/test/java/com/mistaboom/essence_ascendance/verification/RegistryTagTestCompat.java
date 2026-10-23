package com.mistaboom.essence_ascendance.verification;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.tags.TagKey;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Native registry tag fixture bridge. It keeps the actual registry bindings under test. */
public final class RegistryTagTestCompat {
    private RegistryTagTestCompat() { }

    public static <T> Map<TagKey<T>, List<Holder<T>>> snapshot(Registry<T> registry) {
        Map<TagKey<T>, List<Holder<T>>> tags = new HashMap<>();
        //? if >=26.1.2
        /*registry.getTags().forEach(named -> tags.put(named.key(), named.stream().toList()));*/
        //? if <26.1.2
        registry.getTags().forEach(pair -> tags.put(pair.getFirst(), pair.getSecond().stream().toList()));
        return tags;
    }

    //? if >=26.1.2
    /*public static <T> HolderSet.Named<T> tag(Registry<T> registry, TagKey<T> key) { return registry.get(key).orElseThrow(); }*/

    @SuppressWarnings("unchecked")
    public static <T> void bind(Registry<T> registry, Map<TagKey<T>, List<Holder<T>>> tags) {
        // Built-in registries expose the Registry interface in 26.1.2. Their
        // concrete native implementation still owns the actual bind operation.
        if (!(registry instanceof MappedRegistry<?> mapped))
            throw new IllegalStateException("Expected a native mapped registry for tag fixture: " + registry.key());
        //? if >=26.1.2
        /*((MappedRegistry<T>) mapped).prepareTagReload(new net.minecraft.tags.TagLoader.LoadResult<>(registry.key(), tags)).apply();*/
        //? if <26.1.2
        ((MappedRegistry<T>) mapped).bindTags(tags);
    }
}
