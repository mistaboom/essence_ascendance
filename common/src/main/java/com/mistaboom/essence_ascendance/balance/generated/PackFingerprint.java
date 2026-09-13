package com.mistaboom.essence_ascendance.balance.generated;

import com.google.gson.JsonObject;
import dev.architectury.platform.Platform;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;

import java.util.Map;
import java.util.TreeMap;

/** Identity-only check: no loot traversal, ingredient graph, or valuation. */
public record PackFingerprint(String digest, Map<String, String> mods, String minecraft, String loader,
                              java.util.List<String> datapacks, String registryIdentity, String recipeIdentity, String tagIdentity) {
    public PackFingerprint {
        mods = java.util.Collections.unmodifiableMap(new TreeMap<>(mods));
        datapacks = java.util.List.copyOf(datapacks);
    }
    public static PackFingerprint capture(MinecraftServer server) {
        Map<String, String> mods = new TreeMap<>();
        Platform.getMods().forEach(mod -> mods.put(mod.getModId(), mod.getVersion()));
        String minecraft = SharedConstants.getCurrentVersion().getName();
        String loader = Platform.isFabric() ? "fabric" : Platform.isNeoForge() ? "neoforge" : "unknown";
        var packs = server.getPackRepository().getSelectedIds().stream().sorted().toList();
        String registry = BalanceDocument.hash(BuiltInRegistries.ITEM.keySet().stream().map(Object::toString).sorted().toList().toString()
                + BuiltInRegistries.ENTITY_TYPE.keySet().stream().map(Object::toString).sorted().toList());
        String recipes = BalanceDocument.hash(server.getRecipeManager().getRecipeIds().map(Object::toString).sorted().toList().toString());
        String tags = BalanceDocument.hash(tagIdentity(BuiltInRegistries.ITEM) + tagIdentity(BuiltInRegistries.BLOCK));
        JsonObject identity = new JsonObject();
        identity.add("mods", BalanceDocument.GSON.toJsonTree(mods));
        identity.addProperty("minecraft", minecraft);
        identity.addProperty("loader", loader);
        identity.add("datapacks", BalanceDocument.GSON.toJsonTree(packs));
        identity.addProperty("registry", registry);
        identity.addProperty("recipes", recipes);
        identity.addProperty("tags", tags);
        identity.addProperty("generator", BalanceDocument.GENERATOR);
        return new PackFingerprint(BalanceDocument.hash(identity), mods, minecraft, loader, packs, registry, recipes, tags);
    }
    private static <T> String tagIdentity(net.minecraft.core.Registry<T> registry) {
        return registry.getTags().map(pair -> pair.getFirst().location() + "=" +
                pair.getSecond().stream().map(holder -> registry.getKey(holder.value()).toString()).sorted().toList())
                .sorted().toList().toString();
    }
}
