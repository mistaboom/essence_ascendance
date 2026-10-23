package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.ore.LatentOreItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagEntry;
import net.minecraft.tags.TagLoader;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** Contribute registered host items before item tags resolve, including dependent recipe tags. */
@Mixin(TagLoader.class)
public abstract class LatentOreAliasTagMixin {
    private static final ResourceLocation ADAPTIVE = ResourceLocation.fromNamespaceAndPath(
            "essence_ascendance", "adaptive_latent_ore");
    private static final List<ResourceLocation> ORE_TAGS = List.of(
            ResourceLocation.fromNamespaceAndPath("essence_ascendance", "latent_ores"),
            ResourceLocation.fromNamespaceAndPath("c", "ores"),
            ResourceLocation.fromNamespaceAndPath("c", "ores/latent"));

    @Shadow @Final private String directory;

    @Inject(method = "build(Ljava/util/Map;)Ljava/util/Map;", at = @At("HEAD"))
    private void essenceAscendance$addRegisteredOreAliases(
            Map<ResourceLocation, List<TagLoader.EntryWithSource>> entries,
            CallbackInfoReturnable<?> callback) {
        if (!Registries.tagsDirPath(Registries.ITEM).equals(directory)) return;

        var aliases = LatentOreItems.items().stream().map(BuiltInRegistries.ITEM::getKey)
                .filter(id -> id != null && !id.equals(ADAPTIVE)).toList();
        if (aliases.isEmpty()) return;
        // A pack may replace any of these tags. Contribute to a tag only when its
        // final data-pack definition still includes our legacy ore element.
        String legacy = TagEntry.element(ADAPTIVE).toString();
        String optionalLegacy = TagEntry.optionalElement(ADAPTIVE).toString();
        for (ResourceLocation tag : ORE_TAGS) {
            entries.computeIfPresent(tag, (id, original) -> {
                if (original.stream().map(value -> value.entry().toString())
                        .noneMatch(value -> value.equals(legacy) || value.equals(optionalLegacy))) return original;
                var present = new HashSet<String>();
                original.forEach(value -> present.add(value.entry().toString()));
                var expanded = new ArrayList<>(original);
                for (ResourceLocation alias : aliases) {
                    String name = alias.toString();
                    if (present.contains(name) || present.contains(name + "?")) continue;
                    expanded.add(new TagLoader.EntryWithSource(TagEntry.optionalElement(alias),
                            "essence_ascendance:registered_latent_ore_alias"));
                }
                return expanded;
            });
        }
    }
}
