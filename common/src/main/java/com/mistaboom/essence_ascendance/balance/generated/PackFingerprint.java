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
        try (var phase = BalancePerformance.phase("fingerprint_mods")) {
            Platform.getMods().forEach(mod -> mods.put(mod.getModId(), mod.getVersion()));
        }
        String minecraft = SharedConstants.getCurrentVersion().getName();
        String loader = Platform.isFabric() ? "fabric" : Platform.isNeoForge() ? "neoforge" : "unknown";
        var packs = server.getPackRepository().getSelectedIds().stream().sorted().toList();
        String registry;
        try (var phase = BalancePerformance.phase("fingerprint_registry_ids")) {
            registry = BalanceDocument.hash(BuiltInRegistries.ITEM.keySet().stream().map(Object::toString).sorted().toList().toString()
                    + BuiltInRegistries.ENTITY_TYPE.keySet().stream().map(Object::toString).sorted().toList());
            BalancePerformance.count("fingerprint_items", BuiltInRegistries.ITEM.size());
            BalancePerformance.count("fingerprint_entities", BuiltInRegistries.ENTITY_TYPE.size());
        }
        String recipes;
        try (var phase = BalancePerformance.phase("fingerprint_recipe_ids")) {
            var ids = server.getRecipeManager().getRecipeIds().map(Object::toString).sorted().toList();
            recipes = BalanceDocument.hash(ids.toString());
            BalancePerformance.count("fingerprint_recipes", ids.size());
        }
        String tags;
        try (var phase = BalancePerformance.phase("fingerprint_tag_members")) {
            tags = BalanceDocument.hash(tagIdentity(BuiltInRegistries.ITEM) + tagIdentity(BuiltInRegistries.BLOCK));
        }
        BalancePerformance.count("fingerprint_mods", mods.size());
        BalancePerformance.flag("identity_rescanned", true);
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
