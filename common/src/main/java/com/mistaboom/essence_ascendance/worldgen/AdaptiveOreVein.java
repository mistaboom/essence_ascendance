package com.mistaboom.essence_ascendance.worldgen;

import com.mistaboom.essence_ascendance.ore.LatentOreHost;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.feature.OreFeature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;

import java.util.BitSet;

/**
 * Uses OreFeature's vanilla 1.21.1 origin/heightmap and exposure behavior. Its
 * sphere-union traversal is adapted here because vanilla writes directly to a
 * chunk section, bypassing the block-entity identity that this ore must retain.
 * All shape/random constants match the mapped 1.21.1 OreFeature implementation.
 */
final class AdaptiveOreVein extends OreFeature {
    AdaptiveOreVein() { super(OreConfiguration.CODEC); }

    @Override
    protected boolean doPlace(WorldGenLevel level, RandomSource random, OreConfiguration config,
                              double startX, double endX, double startZ, double endZ,
                              double startY, double endY, int minX, int minY, int minZ,
                              int width, int height) {
        double[] spheres = new double[config.size * 4];
        for (int i = 0; i < config.size; i++) {
            float fraction = (float) i / config.size;
            int index = i * 4;
            spheres[index] = Mth.lerp(fraction, startX, endX);
            spheres[index + 1] = Mth.lerp(fraction, startY, endY);
            spheres[index + 2] = Mth.lerp(fraction, startZ, endZ);
            double scale = random.nextDouble() * config.size / 16.0;
            spheres[index + 3] = ((Mth.sin((float) Math.PI * fraction) + 1.0) * scale + 1.0) / 2.0;
        }
        for (int i = 0; i < config.size - 1; i++) {
            int a = i * 4;
            if (spheres[a + 3] <= 0) continue;
            for (int j = i + 1; j < config.size; j++) {
                int b = j * 4;
                if (spheres[b + 3] <= 0) continue;
                double dx = spheres[a] - spheres[b], dy = spheres[a + 1] - spheres[b + 1], dz = spheres[a + 2] - spheres[b + 2];
                double radiusDifference = spheres[a + 3] - spheres[b + 3];
                if (radiusDifference * radiusDifference > dx * dx + dy * dy + dz * dz)
                    spheres[(radiusDifference > 0 ? b : a) + 3] = -1.0;
            }
        }
        BitSet visited = new BitSet(width * height * width);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean placed = false;
        for (int i = 0; i < config.size; i++) {
            int index = i * 4;
            double radius = spheres[index + 3];
            if (radius < 0) continue;
            double cx = spheres[index], cy = spheres[index + 1], cz = spheres[index + 2];
            int x0 = Math.max(Mth.floor(cx - radius), minX), y0 = Math.max(Mth.floor(cy - radius), minY), z0 = Math.max(Mth.floor(cz - radius), minZ);
            int x1 = Math.max(Mth.floor(cx + radius), x0), y1 = Math.max(Mth.floor(cy + radius), y0), z1 = Math.max(Mth.floor(cz + radius), z0);
            for (int x = x0; x <= x1; x++) {
                double dx = (x + 0.5 - cx) / radius;
                if (dx * dx >= 1) continue;
                for (int y = y0; y <= y1; y++) {
                    double dy = (y + 0.5 - cy) / radius;
                    if (dx * dx + dy * dy >= 1 || level.isOutsideBuildHeight(y)) continue;
                    for (int z = z0; z <= z1; z++) {
                        double dz = (z + 0.5 - cz) / radius;
                        if (dx * dx + dy * dy + dz * dz >= 1) continue;
                        int bit = x - minX + (y - minY) * width + (z - minZ) * width * height;
                        if (visited.get(bit)) continue;
                        visited.set(bit);
                        pos.set(x, y, z);
                        if (!level.ensureCanWrite(pos)) continue;
                        var host = level.getBlockState(pos);
                        for (var target : config.targetStates) {
                            if (!canPlaceOre(host, level::getBlockState, random, config, target, pos)) continue;
                            placed |= LatentOreHost.place(level, pos.immutable(), host, Block.UPDATE_CLIENTS);
                            break;
                        }
                    }
                }
            }
        }
        return placed;
    }
}
