package com.mistaboom.essence_ascendance.valuation;

import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import java.util.*;

/** Exact audited definition getters. Never invokes conversion, filler, reward filters or private loot data APIs. */
final class LootrReader {
    private static final String API = "noobanidus.mods.lootr.common.api.LootrAPI";
    static boolean ready(GenerationDataSnapshot inputs) {
        if (!LootrProvider.supported(inputs.installedVersion("lootr"))) return false;
        if (!flag("isReady")) return false;
        try {
            Object api = Class.forName(API).getField("INSTANCE").get(null);
            if (!api.getClass().getName().equals("noobanidus.mods.lootr.neoforge.impl.LootrAPIImpl"))
                throw new IllegalStateException("Unaudited replacement Lootr API implementation");
            if (call("getServer") != inputs.server()) return false;
            Object spec = Class.forName("noobanidus.mods.lootr.neoforge.config.ConfigManager").getField("COMMON_CONFIG").get(null);
            return spec != null && (boolean) spec.getClass().getMethod("isLoaded").invoke(spec);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Cannot probe loaded Lootr configuration", failure); }
    }
    static LootrPolicy capture(GenerationDataSnapshot inputs) {
        if (!ready(inputs)) throw new IllegalStateException("Lootr service is not ready for the owning server");
        requireSafeBlacklistReader(inputs.installedVersion("lootr"));
        // .125 has per-player loot only; the team setting/API arrived in .126.
        boolean teamLoot = LootrProvider.teamLootSettingAvailable(inputs.installedVersion("lootr")) && flag("isTeamLoot");
        Set<String> dimensions = new TreeSet<>();
        for (var dimension : inputs.dimensions()) {
            var key = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(dimension.id()));
            if ((boolean) invoke("isDimensionBlocked", new Class<?>[]{ResourceKey.class}, key)) dimensions.add(dimension.id());
        }
        List<String> unsupported = new ArrayList<>();
        for (String getter : List.of("getFilters", "getBlockEntityPreProcessors", "getBlockEntityPostProcessors", "getEntityPreProcessors", "getEntityPostProcessors")) {
            var extensions = (Collection<?>) call(getter);
            if (!extensions.isEmpty()) unsupported.add(getter + ": " + extensions.stream().map(e -> e.getClass().getName()).sorted().toList()
                    + "; callbacks are not executed");
        }
        List<String> conversion = List.of("Eligible conversion block/entity tags and replacement providers required; custom converters unresolved",
                "world_border_check=" + flag("shouldCheckWorldBorder"), "convert_mineshafts=" + flag("shouldConvertMineshafts"),
                "convert_elytras=" + flag("shouldConvertElytras"), "convert_structure_item_frames=" + flag("shouldConvertStructureItemFrames"),
                "team_loot=" + teamLoot, "decay_replaces_block=" + flag("shouldReplaceWhenDecayed"));
        return new LootrPolicy(true, flag("isDisabled"), teamLoot, ids("getLootTableBlacklist"), ids("getLootModidBlacklist"), dimensions,
                rule(inputs, true), rule(inputs, false), conversion, unsupported, conversionTags());
    }
    private static void requireSafeBlacklistReader(String version) {
        // In .125 this getter reads configuration and built-in problematic-table
        // definitions; extensible processors arrived in .126. Never infer their
        // absence for an unaudited version.
        if (!LootrProvider.blacklistUsesProblematicProcessors(version)) return;
        // The public blacklist getter lazily invokes problematic-table processors. Inspect definitions first,
        // never run an unaudited callback merely to discover conversion exclusions.
        try {
            Class<?> type = Class.forName("noobanidus.mods.lootr.common.impl.LootrServiceRegistry");
            Object registry = type.getMethod("getInstance").invoke(null);
            var field = type.getDeclaredField("problematicProcessors"); field.setAccessible(true);
            for (Object processor : (Collection<?>) field.get(registry))
                if (!processor.getClass().getName().equals("noobanidus.mods.lootr.common.impl.integration.DefaultLootrProblematicLootTables"))
                    throw new IllegalStateException("Unsupported problematic-table callback: " + processor.getClass().getName());
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Cannot inspect Lootr blacklist definition providers", failure); }
    }
    private static Map<String, List<String>> conversionTags() {
        Map<String, List<String>> tags = new TreeMap<>();
        tags(BuiltInRegistries.BLOCK, "block", tags);
        tags(BuiltInRegistries.BLOCK_ENTITY_TYPE, "block_entity_type", tags);
        tags(BuiltInRegistries.ENTITY_TYPE, "entity_type", tags);
        tags(BuiltInRegistries.ITEM, "item", tags);
        return tags;
    }
    private static <T> void tags(net.minecraft.core.Registry<T> registry, String family, Map<String, List<String>> out) {
        registry.getTags().filter(pair -> pair.getFirst().location().getNamespace().equals("lootr")
                        && pair.getFirst().location().getPath().startsWith("convert/"))
                .forEach(pair -> out.put(family + "/" + pair.getFirst().location(), pair.getSecond().stream()
                        .map(holder -> holder.unwrapKey().orElseThrow().location().toString()).sorted().toList()));
    }
    private static LootrPolicy.Rule rule(GenerationDataSnapshot inputs, boolean refresh) {
        String noun = refresh ? "Refresh" : "Decay";
        Set<String> structures = new TreeSet<>();
        inputs.server().registryAccess().registryOrThrow(Registries.STRUCTURE)
                .getTag(TagKey.create(Registries.STRUCTURE, ResourceLocation.parse("lootr:" + (refresh ? "refresh" : "decay"))))
                .ifPresent(tag -> tag.forEach(holder -> holder.unwrapKey().ifPresent(key -> structures.add(key.location().toString()))));
        return new LootrPolicy.Rule(flag("should" + noun + "All"), ids("get" + noun + "Whitelist"),
                ids(refresh ? "getRefreshModids" : "getModidDecayWhitelist"), ids("get" + noun + "Dimensions"), structures,
                (int) call("get" + noun + "Value"), flag("shouldStart" + noun + "WhileTicking"), flag("shouldPerform" + noun + "WhileTicking"));
    }
    private static Set<String> ids(String getter) {
        Set<String> out = new TreeSet<>();
        for (Object value : (Collection<?>) call(getter)) out.add(value instanceof ResourceKey<?> key ? key.location().toString() : value.toString());
        return out;
    }
    private static boolean flag(String getter) { return (boolean) call(getter); }
    private static Object call(String getter) { return invoke(getter, new Class<?>[0]); }
    private static Object invoke(String getter, Class<?>[] parameters, Object... arguments) {
        try { return Class.forName(API).getMethod(getter, parameters).invoke(null, arguments); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException("Lootr read-only getter failed: " + getter, failure); }
    }
}
