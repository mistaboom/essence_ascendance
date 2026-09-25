package com.mistaboom.essence_ascendance.worldgen;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Set;
import java.util.TreeSet;

/** Retains current-format host appearances after generation policy changes. Server lifecycle calls only. */
final class LatentOreHostCatalog extends SavedData {
    private final Set<ResourceLocation> hosts = new TreeSet<>();

    static Set<ResourceLocation> remember(MinecraftServer server, Set<ResourceLocation> selected) {
        var data = server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(LatentOreHostCatalog::new, LatentOreHostCatalog::load, null),
                "essence_ascendance_latent_ore_hosts");
        if (data.hosts.addAll(selected)) data.setDirty();
        return Set.copyOf(data.hosts);
    }

    static LatentOreHostCatalog load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new LatentOreHostCatalog();
        ListTag entries = tag.getList("hosts", Tag.TAG_STRING);
        int invalid = 0;
        for (int i = 0; i < entries.size(); i++) {
            ResourceLocation id = ResourceLocation.tryParse(entries.getString(i));
            if (id != null) data.hosts.add(id);
            else invalid++;
        }
        if (invalid > 0) EssenceAscendance.LOGGER.warn("Latent Ore host catalog ignored {} invalid block identifiers", invalid);
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        hosts.forEach(host -> entries.add(StringTag.valueOf(host.toString())));
        tag.put("hosts", entries);
        return tag;
    }
}
