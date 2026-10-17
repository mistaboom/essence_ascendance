package com.mistaboom.essence_ascendance.worldgen;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.config.LatentOreWorldgenSettings;
import com.mistaboom.essence_ascendance.ore.LatentOreHost;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import com.mojang.serialization.JsonOps;
import com.google.gson.JsonElement;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.WeakHashMap;

/**
 * Primary terrain evidence only. The valuation natural-block index also contains
 * vegetation, fluids, ores and surface patches, so it cannot authorize replacement.
 * Reads typed generator settings once per world/profile; never samples or loads chunks.
 */
public final class PrimarySubstrateDiscovery {
    private static final TagKey<Block> ORES = TagKey.create(Registries.BLOCK, ResourceLocation.parse("c:ores"));
    private static final Map<ServerLevel, Cached> CACHE = new WeakHashMap<>();
    private record Cached(LatentOreWorldgenSettings settings, Selection selection) { }

    public record Selection(Map<Block, String> hosts, LatentOreWorldgenSettings.DimensionSettings distribution,
                            boolean enabled, boolean overridden, String reason) {
        public Selection { hosts = Collections.unmodifiableMap(new LinkedHashMap<>(hosts)); }
        public boolean accepts(BlockState state) { return enabled && hosts.containsKey(state.getBlock()); }
    }

    private PrimarySubstrateDiscovery() { }

    public static synchronized Selection selection(ServerLevel level) {
        var settings = LatentOreWorldgen.settings();
        Cached cached = CACHE.get(level);
        if (cached != null && cached.settings() == settings) return cached.selection();
        Selection result = discover(level.dimension().location(), level.getChunkSource().getGenerator(),
                level.getMinBuildHeight(), level.getMaxBuildHeight(), settings);
        CACHE.put(level, new Cached(settings, result));
        var hosts = result.hosts().entrySet().stream().map(entry -> BuiltInRegistries.BLOCK.getKey(entry.getKey()) + " (" + entry.getValue() + ")").toList();
        EssenceAscendance.LOGGER.info("Latent Ore dimension={} selection={} status={} hosts={} reason={}",
                level.dimension().location(), result.overridden() ? "override" : "automatic",
                result.enabled() ? "enabled" : "excluded/skipped", hosts, result.reason());
        return result;
    }

    public static List<BlockState> selectedStates(ServerLevel level) {
        Selection selection = selection(level);
        if (!selection.enabled()) return List.of();
        return selection.hosts().keySet().stream().map(Block::defaultBlockState).toList();
    }

    public static Set<ResourceLocation> selectedHostIds(MinecraftServer server) {
        Set<ResourceLocation> result = new TreeSet<>();
        for (ServerLevel level : server.getAllLevels())
            selection(level).hosts().keySet().forEach(block -> result.add(BuiltInRegistries.BLOCK.getKey(block)));
        return LatentOreHostCatalog.remember(server, result);
    }

    public static synchronized void clear() { CACHE.clear(); }

    // Package-visible to exercise real local generator fixtures without constructing a server or generating chunks.
    static Selection discover(ResourceLocation dimension, ChunkGenerator generator, int minBuildY, int maxBuildY,
                              LatentOreWorldgenSettings settings) {
        int minY = Math.max(minBuildY, generator.getMinY());
        int maxY = Math.min(maxBuildY, generator.getMinY() + generator.getGenDepth()) - 1;
        // Empty terrain range is diagnosed before a distribution can try to sample it.
        var distribution = settings.distribution(dimension, Math.max(-2048, Math.min(2048, minY)),
                Math.max(-2048, Math.min(2048, Math.max(minY, maxY))));
        var override = settings.dimensions().get(dimension);
        boolean overridden = override != null;
        if (!settings.enabled(dimension) || !distribution.enabled() || distribution.veinsPerChunk() == 0)
            return new Selection(Map.of(), distribution, false, overridden, "disabled by generation policy");
        if (minY > maxY || Math.max(minY, distribution.minY()) > Math.min(maxY, distribution.maxY()))
            return new Selection(Map.of(), distribution, false, overridden, "no usable vertical intersection with generator/build range");

        Map<Block, String> candidates = new LinkedHashMap<>();
        List<String> rejected = new ArrayList<>();
        if (override != null && !override.hosts().isEmpty()) {
            for (ResourceLocation host : override.hosts()) {
                Block block = BuiltInRegistries.BLOCK.getOptional(host).orElse(null);
                if (block == null) rejected.add(host + ": block is not registered");
                else add(candidates, rejected, block.defaultBlockState(), "explicit dimension primary host");
            }
        } else if (generator instanceof NoiseBasedChunkGenerator noise) {
            var terrain = noise.generatorSettings();
            BlockState base = terrain.value().defaultBlock();
            String source = "noise default_block " + terrain.unwrapKey().map(key -> key.location().toString()).orElse("inline settings");
            add(candidates, rejected, base, source);
            // Vanilla defines this as a base geological layer, not as an ore/deposit target.
            // Recognize this exact basal branch even inside a generator's composed rule tree.
            if (base.is(Blocks.STONE) && hasVanillaBasalLayer(terrain.value().surfaceRule(), rejected))
                add(candidates, rejected, Blocks.DEEPSLATE.defaultBlockState(), "vanilla Overworld basal deepslate layer");
        } else {
            rejected.add("generator " + generator.getClass().getName() + " does not expose supported noise default_block; configure exact primary hosts");
        }
        String reason = rejected.isEmpty() ? "primary terrain evidence resolved; placement requires exact host block"
                : String.join("; ", rejected);
        return new Selection(candidates, distribution, !candidates.isEmpty(), overridden, reason);
    }

    private static void add(Map<Block, String> candidates, List<String> rejected, BlockState state, String evidence) {
        String rejection = rejection(state);
        if (rejection != null) rejected.add(BuiltInRegistries.BLOCK.getKey(state.getBlock()) + ": " + rejection);
        else candidates.putIfAbsent(state.getBlock(), evidence);
    }

    /** Recognizes an exact basal rule; arbitrary surface/deposit block states are not hosts. */
    private static boolean hasVanillaBasalLayer(SurfaceRules.RuleSource surface, List<String> diagnostics) {
        try {
            var encoded = SurfaceRules.RuleSource.CODEC.encodeStart(JsonOps.INSTANCE, surface)
                    .resultOrPartial(error -> diagnostics.add("basal-layer evidence unavailable: " + error)).orElse(null);
            return hasVanillaBasalLayer(encoded, diagnostics);
        } catch (RuntimeException unsupportedCodec) {
            diagnostics.add("basal-layer evidence unavailable: " + unsupportedCodec.getClass().getSimpleName());
            return false;
        }
    }

    // A wrapper may retain the native branch under any field. Inspect the encoded tree rather
    // than assuming one root sequence or depending on another mod's class/serializer identity.
    static boolean hasVanillaBasalLayer(JsonElement encoded, List<String> diagnostics) {
        if (encoded == null) return false;
        var knownLayer = SurfaceRules.ifTrue(SurfaceRules.verticalGradient("deepslate", VerticalAnchor.absolute(0),
                VerticalAnchor.absolute(8)), SurfaceRules.state(Blocks.DEEPSLATE.defaultBlockState()));
        var expected = SurfaceRules.RuleSource.CODEC.encodeStart(JsonOps.INSTANCE, knownLayer).getOrThrow();
        var pending = new ArrayDeque<JsonElement>();
        pending.add(encoded);
        int remaining = 16_384;
        while (!pending.isEmpty() && remaining-- > 0) {
            var node = pending.removeFirst();
            if (node.equals(expected)) return true;
            if (node.isJsonObject()) {
                for (var child : node.getAsJsonObject().entrySet()) {
                    if (pending.size() >= remaining) break;
                    pending.addLast(child.getValue());
                }
            } else if (node.isJsonArray()) {
                for (var child : node.getAsJsonArray()) {
                    if (pending.size() >= remaining) break;
                    pending.addLast(child);
                }
            }
        }
        if (!pending.isEmpty() || remaining <= 0) diagnostics.add("basal-layer evidence unavailable: encoded rule inspection bound reached");
        return false;
    }

    private static String rejection(BlockState state) {
        if (!LatentOreHost.isValid(state)) return "requires opaque full solid host without fluid/block entity/unbreakable behavior";
        if (state.is(Blocks.BEDROCK) || state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK)
                || state.getBlock() instanceof FallingBlock || state.getBlock() instanceof DropExperienceBlock || state.is(BlockTags.DIRT)
                || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS) || state.is(ORES))
            return "incidental soil/vegetation/falling material/ore is not supported primary rock";
        return null;
    }
}
