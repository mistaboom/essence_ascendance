package com.mistaboom.essence_ascendance.worldgen;

import com.mistaboom.essence_ascendance.balance.config.BalanceSettings;
import com.mistaboom.essence_ascendance.balance.config.BalanceOverrides;
import com.mistaboom.essence_ascendance.balance.runtime.RuntimeBalanceGenerator;
import com.mistaboom.essence_ascendance.balance.runtime.RuntimeBalanceDefinition;
import com.mistaboom.essence_ascendance.balance.generated.BalanceDocument;
import com.mistaboom.essence_ascendance.config.LatentOreWorldgenSettings;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.feature.OreFeature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockMatchTest;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Real vanilla registry/generator fixtures; no server, external packs, chunks, or filesystem worlds. */
public final class PrimarySubstrateDiscoveryTest {
    private static int assertions;
    private static HolderLookup.Provider registries;
    private static FixedBiomeSource biomes;
    private static final LatentOreWorldgenSettings DEFAULTS = LatentOreWorldgenSettings.defaults();

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        registries = VanillaRegistries.createLookup();
        biomes = new FixedBiomeSource(registries.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS));
        vanillaHosts();
        customTerrainAndRanges();
        overridesAndExclusions();
        parserValidation();
        retainedHostCatalog();
        System.out.println("PrimarySubstrateDiscoveryTest: " + assertions + " assertions passed");
    }

    private static void vanillaHosts() {
        var overworld = discover("minecraft:overworld", vanilla(NoiseGeneratorSettings.OVERWORLD), -64, 320, DEFAULTS);
        check(overworld.enabled() && overworld.hosts().keySet().equals(Set.of(Blocks.STONE, Blocks.DEEPSLATE)), "Overworld primary layers");
        for (Block incidental : List.of(Blocks.DIORITE, Blocks.ANDESITE, Blocks.GRANITE, Blocks.DIRT, Blocks.BEDROCK, Blocks.IRON_ORE))
            check(!overworld.accepts(incidental.defaultBlockState()), "Skip incidental deposit " + incidental);
        check(discover("minecraft:the_nether", vanilla(NoiseGeneratorSettings.NETHER), 0, 256, DEFAULTS).hosts().keySet().equals(Set.of(Blocks.NETHERRACK)), "Nether default host");
        check(discover("minecraft:the_end", vanilla(NoiseGeneratorSettings.END), 0, 256, DEFAULTS).hosts().keySet().equals(Set.of(Blocks.END_STONE)), "End default host");
        check(overworld.distribution().equals(DEFAULTS.overworld()), "Discovery consumes the supplied Overworld distribution");
        check(discover("minecraft:the_nether", vanilla(NoiseGeneratorSettings.NETHER), 0, 256, DEFAULTS).distribution().equals(DEFAULTS.nether()),
                "Discovery consumes the supplied Nether distribution");
        var targets = overworld.hosts().keySet().stream().map(block -> OreConfiguration.target(new BlockMatchTest(block), Blocks.GOLD_BLOCK.defaultBlockState())).toList();
        var configuration = new OreConfiguration(targets, 4, 0);
        for (Block block : List.of(Blocks.STONE, Blocks.DEEPSLATE, Blocks.GRANITE, Blocks.ANDESITE, Blocks.DIORITE, Blocks.AIR)) {
            boolean replaceable = targets.stream().anyMatch(target -> OreFeature.canPlaceOre(block.defaultBlockState(),
                    ignored -> Blocks.STONE.defaultBlockState(), RandomSource.create(123), configuration, target, new BlockPos.MutableBlockPos()));
            check(replaceable == (block == Blocks.STONE || block == Blocks.DEEPSLATE), "Actual vanilla replacement predicate is exact for " + block);
        }
    }

    private static void customTerrainAndRanges() {
        var basalt = custom(Blocks.BASALT, -128, 512);
        var selected = discover("fixture:deep", basalt, -64, 256, DEFAULTS);
        check(selected.enabled() && selected.hosts().keySet().equals(Set.of(Blocks.BASALT)), "Custom noise default is primary evidence");
        check(selected.distribution().minY() == -64 && selected.distribution().maxY() == 255, "Generic distribution intersects actual heights");
        check(selected.distribution().veinSize() == DEFAULTS.overworld().veinSize()
                && selected.distribution().veinsPerChunk() == DEFAULTS.overworld().veinsPerChunk(), "Generic dimensions inherit generated abundance");
        check(selected.hosts().equals(discover("fixture:other", basalt, -64, 256, DEFAULTS).hosts()), "Host identities reused across dimensions");
        check(discover("fixture:other", custom(Blocks.STONE, 0, 128), 0, 128, DEFAULTS).hosts().keySet().equals(Set.of(Blocks.STONE)), "Custom stone default does not invent another layer");
        var flat = new FlatLevelSource(FlatLevelGeneratorSettings.getDefault(registries.lookupOrThrow(Registries.BIOME),
                registries.lookupOrThrow(Registries.STRUCTURE_SET), registries.lookupOrThrow(Registries.PLACED_FEATURE)));
        var unsupported = discover("fixture:flat", flat, -64, 320, DEFAULTS);
        check(!unsupported.enabled() && unsupported.reason().contains("does not expose supported noise default_block"), "Unsupported generator diagnostic");
        for (Block rejected : List.of(Blocks.DIRT, Blocks.BEDROCK, Blocks.WATER, Blocks.CHEST, Blocks.SAND, Blocks.COAL_ORE)) {
            var result = discover("fixture:unsafe", custom(rejected, 0, 128), 0, 128, DEFAULTS);
            check(!result.enabled() && !result.reason().isBlank(), "Unsafe primary rejected with reason: " + rejected);
        }
    }

    private static void overridesAndExclusions() {
        var parsed = BalanceSettings.parse("""
                [latent_ore]
                automatic_dimensions = false
                [[latent_ore.dimension]]
                dimension = "fixture:deep"
                hosts = ["minecraft:basalt", "minecraft:blackstone"]
                veins_per_chunk = 3
                [[latent_ore.dimension]]
                dimension = "minecraft:overworld"
                enabled = false
                hosts = ["minecraft:stone"]
                """, "fixture.toml").latentOre();
        var deep = discover("fixture:deep", custom(Blocks.STONE, -128, 512), -128, 384, parsed);
        check(deep.enabled() && deep.overridden() && deep.hosts().keySet().equals(Set.of(Blocks.BASALT, Blocks.BLACKSTONE)), "Explicit hosts replace automatic hosts");
        check(deep.distribution().veinsPerChunk() == 3, "Exact dimension distribution owns generation");
        check(!discover("minecraft:overworld", vanilla(NoiseGeneratorSettings.OVERWORLD), -64, 320, parsed).enabled(), "Explicit disable beats vanilla automatic policy and configured hosts");
        check(!discover("fixture:other", custom(Blocks.STONE, 0, 128), 0, 128, parsed).enabled(), "Automatic other dimensions can be disabled");
        var reenabled = BalanceSettings.parse("""
                [latent_ore.overworld]
                enabled = false
                [[latent_ore.dimension]]
                dimension = "minecraft:overworld"
                enabled = true
                """, "enable.toml").latentOre();
        check(discover("minecraft:overworld", vanilla(NoiseGeneratorSettings.OVERWORLD), -64, 320, reenabled).enabled(),
                "Exact enabled override can re-enable disabled baseline without restating distribution");
        var missing = new LatentOreWorldgenSettings(DEFAULTS.overworld(), DEFAULTS.nether(), DEFAULTS.end(), true,
                Map.of(id("fixture:missing"), new LatentOreWorldgenSettings.DimensionOverride(true, List.of(id("fixture:unknown")), null)));
        var missingResult = discover("fixture:missing", custom(Blocks.STONE, 0, 128), 0, 128, missing);
        check(!missingResult.enabled() && missingResult.reason().contains("not registered"), "Invalid explicit host does not fall back to automatic");
    }

    private static void parserValidation() {
        check(BalanceSettings.parse("", "empty").latentOre().equals(DEFAULTS), "Omitted ore policy uses defaults");
        check(RuntimeBalanceDefinition.worldgenFromJson(RuntimeBalanceDefinition.worldgenJson(DEFAULTS)).equals(DEFAULTS),
                "Ore policy shares resolved runtime serialization");
        var reordered = BalanceSettings.parse("""
                [[latent_ore.dimension]]
                dimension = "minecraft:overworld"
                veins_per_chunk = 3
                [latent_ore.overworld]
                vein_size = 6
                """, "order.toml").latentOre();
        check(reordered.distribution(id("minecraft:overworld"), -64, 319).veinSize() == 6,
                "Exact dimension distribution inherits resolved baseline independently of table order");
        var withHosts = BalanceSettings.parse("[[latent_ore.dimension]]\ndimension='fixture:deep'\nhosts=['minecraft:basalt']", "save.toml");
        check(BalanceDocument.GSON.fromJson(BalanceDocument.GSON.toJsonTree(withHosts), BalanceSettings.class).equals(withHosts),
                "Saved input settings round-trip resource ids in override keys and hosts");
        for (String bad : List.of(
                "[[latent_ore.dimension]]\ndimension='fixture:one'\nore_variant='stone'",
                "[[latent_ore.dimension]]\ndimension='fixture:one'\nhosts=['#minecraft:stone_ore_replaceables']",
                "[[latent_ore.dimension]]\ndimension='fixture:one'\nhosts=['minecraft:stone','minecraft:stone']",
                "[[latent_ore.dimension]]\ndimension='fixture:one'\n[[latent_ore.dimension]]\ndimension='fixture:one'",
                "[latent_ore.overworld]\nvein_size=0")) {
            try { BalanceSettings.parse(bad, "bad-ore.toml"); throw new AssertionError("Invalid ore input accepted: " + bad); }
            catch (IllegalArgumentException expected) { check(expected.getMessage().contains("bad-ore.toml"), "Ore error identifies source"); }
        }
    }

    private static void retainedHostCatalog() {
        CompoundTag tag = new CompoundTag();
        ListTag hosts = new ListTag();
        hosts.add(StringTag.valueOf("minecraft:stone"));
        hosts.add(StringTag.valueOf("minecraft:basalt"));
        tag.put("hosts", hosts);
        check(LatentOreHostCatalog.load(tag, registries).save(new CompoundTag(), registries).getList("hosts", 8).size() == 2,
                "Current host catalog saves and reloads stable identifiers");
        hosts.add(StringTag.valueOf("INVALID ID"));
        check(LatentOreHostCatalog.load(tag, registries).save(new CompoundTag(), registries).getList("hosts", 8).size() == 2,
                "Invalid current catalog identity safely omitted");
    }

    private static NoiseBasedChunkGenerator vanilla(net.minecraft.resources.ResourceKey<NoiseGeneratorSettings> key) {
        return new NoiseBasedChunkGenerator(biomes, registries.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(key));
    }
    private static NoiseBasedChunkGenerator custom(Block host, int minY, int height) {
        var base = registries.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD).value();
        var terrain = new NoiseGeneratorSettings(NoiseSettings.create(minY, height, 1, 2), host.defaultBlockState(),
                base.defaultFluid(), base.noiseRouter(), SurfaceRules.state(host.defaultBlockState()), base.spawnTarget(), base.seaLevel(),
                base.disableMobGeneration(), base.aquifersEnabled(), base.oreVeinsEnabled(), base.useLegacyRandomSource());
        return new NoiseBasedChunkGenerator(biomes, Holder.direct(terrain));
    }
    private static PrimarySubstrateDiscovery.Selection discover(String dimension, net.minecraft.world.level.chunk.ChunkGenerator generator,
                                                                int minY, int maxY, LatentOreWorldgenSettings settings) {
        return PrimarySubstrateDiscovery.discover(id(dimension), generator, minY, maxY, settings);
    }
    private static ResourceLocation id(String id) { return ResourceLocation.parse(id); }
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
