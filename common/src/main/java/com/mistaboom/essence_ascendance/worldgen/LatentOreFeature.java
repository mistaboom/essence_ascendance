package com.mistaboom.essence_ascendance.worldgen;

import com.mistaboom.essence_ascendance.infuser.EssenceInfuserContent;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockMatchTest;

/** A single scheduled pass resolves policy from the actual generating dimension, never its biome. */
public final class LatentOreFeature extends Feature<NoneFeatureConfiguration> {
    private static final AdaptiveOreVein VEIN = new AdaptiveOreVein();

    public LatentOreFeature(Codec<NoneFeatureConfiguration> codec) { super(codec); }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        var selection = PrimarySubstrateDiscovery.selection(context.level().getLevel());
        if (!selection.enabled()) return false;
        var settings = selection.distribution();
        var level = context.level();
        var generator = context.chunkGenerator();
        int minY = Math.max(settings.minY(), Math.max(level.getMinBuildHeight(), generator.getMinY()));
        int maxY = Math.min(settings.maxY(), Math.min(level.getMaxBuildHeight(), generator.getMinY() + generator.getGenDepth()) - 1);
        if (minY > maxY) return false;
        var oreState = EssenceInfuserContent.LATENT_ORE.get().defaultBlockState();
        var targets = selection.hosts().keySet().stream()
                .map(block -> OreConfiguration.target(new BlockMatchTest(block), oreState)).toList();
        var configuration = new OreConfiguration(targets, settings.veinSize(), (float) settings.discardChanceOnAirExposure());
        boolean placed = false;
        for (int attempt = 0; attempt < settings.veinsPerChunk(); attempt++) {
            int x = context.origin().getX() + context.random().nextInt(16);
            int y = minY + context.random().nextInt(maxY - minY + 1);
            int z = context.origin().getZ() + context.random().nextInt(16);
            placed |= VEIN.place(configuration, level, generator, context.random(), new BlockPos(x, y, z));
        }
        return placed;
    }
}
