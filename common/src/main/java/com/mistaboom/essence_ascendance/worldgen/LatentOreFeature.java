package com.mistaboom.essence_ascendance.worldgen;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.config.LatentOreWorldgenSettings;
import com.mistaboom.essence_ascendance.infuser.EssenceInfuserContent;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockMatchTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.TagMatchTest;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Config-driven Latent Ore placement. Vanilla dimensions use their built-in
 * substrate rules; the CUSTOM instance evaluates optional exact-dimension or
 * dimension-type-tag rules from the global server config.
 */
public final class LatentOreFeature extends Feature<NoneFeatureConfiguration> {

    public enum Target {
        OVERWORLD,
        NETHER,
        END,
        CUSTOM
    }

    private static final Set<String> WARNED_INVALID_CUSTOM_RULES =
            ConcurrentHashMap.newKeySet();

    private final Target target;

    public LatentOreFeature(
            Codec<NoneFeatureConfiguration> codec,
            Target target
    ) {
        super(codec);
        this.target = target;
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        return switch (target) {
            case OVERWORLD -> placeBuiltIn(
                    context,
                    EssenceConfigManager.get().latentOreWorldgen().overworld(),
                    overworldOreConfiguration(EssenceConfigManager.get().latentOreWorldgen().overworld())
            );
            case NETHER -> placeBuiltIn(
                    context,
                    EssenceConfigManager.get().latentOreWorldgen().nether(),
                    simpleOreConfiguration(
                            new BlockMatchTest(Blocks.NETHERRACK),
                            EssenceInfuserContent.NETHERRACK_LATENT_ORE.get().defaultBlockState(),
                            EssenceConfigManager.get().latentOreWorldgen().nether()
                    )
            );
            case END -> placeBuiltIn(
                    context,
                    EssenceConfigManager.get().latentOreWorldgen().end(),
                    simpleOreConfiguration(
                            new BlockMatchTest(Blocks.END_STONE),
                            EssenceInfuserContent.END_STONE_LATENT_ORE.get().defaultBlockState(),
                            EssenceConfigManager.get().latentOreWorldgen().end()
                    )
            );
            case CUSTOM -> placeCustom(context);
        };
    }

    private boolean placeBuiltIn(
            FeaturePlaceContext<NoneFeatureConfiguration> context,
            LatentOreWorldgenSettings.DimensionSettings settings,
            OreConfiguration oreConfiguration
    ) {
        /*
         * Preserve the v14 convention-tag behavior for modded dimension
         * families, but let an explicit custom rule take ownership so a pack
         * never receives both inherited and custom Latent Ore generation.
         */
        if (hasMatchingCustomRule(context)) {
            return false;
        }
        return placeDistribution(context, settings, oreConfiguration);
    }

    private boolean hasMatchingCustomRule(
            FeaturePlaceContext<NoneFeatureConfiguration> context
    ) {
        LatentOreWorldgenSettings settings = EssenceConfigManager.get().latentOreWorldgen();
        if (settings.customDimensions().isEmpty()) {
            return false;
        }

        var level = context.level().getLevel();
        for (LatentOreWorldgenSettings.CustomDimensionSettings rule
                : settings.customDimensions().values()) {
            if (matches(rule, level.dimension(), level.dimensionTypeRegistration())) {
                return true;
            }
        }
        return false;
    }

    private boolean placeCustom(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        LatentOreWorldgenSettings settings = EssenceConfigManager.get().latentOreWorldgen();
        if (settings.customDimensions().isEmpty()) {
            return false;
        }

        ResourceKey<Level> dimension = context.level().getLevel().dimension();
        var dimensionType = context.level().getLevel().dimensionTypeRegistration();
        boolean placedAny = false;

        for (Map.Entry<String, LatentOreWorldgenSettings.CustomDimensionSettings> entry
                : settings.customDimensions().entrySet()) {
            LatentOreWorldgenSettings.CustomDimensionSettings rule = entry.getValue();
            if (!matches(rule, dimension, dimensionType)) {
                continue;
            }

            RuleTest replacement = createReplacementTest(entry.getKey(), rule.replacement());
            if (replacement == null) {
                continue;
            }

            BlockState oreState = resolveOreState(entry.getKey(), rule);
            if (oreState == null) {
                continue;
            }
            OreConfiguration oreConfiguration = simpleOreConfiguration(
                    replacement,
                    oreState,
                    rule.distribution()
            );

            placedAny |= placeDistribution(context, rule.distribution(), oreConfiguration);
        }

        return placedAny;
    }

    private boolean matches(
            LatentOreWorldgenSettings.CustomDimensionSettings rule,
            ResourceKey<Level> dimension,
            net.minecraft.core.Holder<DimensionType> dimensionType
    ) {
        if (rule.dimension() != null) {
            return dimension.location().equals(rule.dimension());
        }

        TagKey<DimensionType> tag = TagKey.create(
                Registries.DIMENSION_TYPE,
                rule.dimensionTypeTag()
        );
        return dimensionType.is(tag);
    }

    private RuleTest createReplacementTest(
            String ruleName,
            LatentOreWorldgenSettings.ReplacementTarget replacement
    ) {
        if (replacement.tag()) {
            return new TagMatchTest(
                    TagKey.create(Registries.BLOCK, replacement.id())
            );
        }

        Block block = BuiltInRegistries.BLOCK
                .getOptional(replacement.id())
                .orElse(null);
        if (block == null) {
            warnInvalidRuleOnce(
                    ruleName + ":replace:" + replacement.id(),
                    "Skipping custom Latent Ore rule '{}' because replacement block '{}' is not registered",
                    ruleName,
                    replacement.id()
            );
            return null;
        }
        return new BlockMatchTest(block);
    }

    private BlockState resolveOreState(
            String ruleName,
            LatentOreWorldgenSettings.CustomDimensionSettings rule
    ) {
        if (rule.usesCustomOreBlock()) {
            Block block = BuiltInRegistries.BLOCK
                    .getOptional(rule.oreBlock())
                    .orElse(null);
            if (block == null) {
                warnInvalidRuleOnce(
                        ruleName + ":ore_block:" + rule.oreBlock(),
                        "Skipping custom Latent Ore rule '{}' because ore_block '{}' is not registered",
                        ruleName,
                        rule.oreBlock()
                );
                return null;
            }
            return block.defaultBlockState();
        }
        return oreState(rule.oreVariant());
    }

    private static void warnInvalidRuleOnce(
            String warningKey,
            String message,
            Object... arguments
    ) {
        if (WARNED_INVALID_CUSTOM_RULES.add(warningKey)) {
            EssenceAscendance.LOGGER.warn(message, arguments);
        }
    }

    private boolean placeDistribution(
            FeaturePlaceContext<NoneFeatureConfiguration> context,
            LatentOreWorldgenSettings.DimensionSettings settings,
            OreConfiguration oreConfiguration
    ) {
        if (!settings.enabled() || settings.veinsPerChunk() <= 0) {
            return false;
        }

        WorldGenLevel level = context.level();
        int minY = Math.max(settings.minY(), level.getMinBuildHeight());
        int maxY = Math.min(settings.maxY(), level.getMaxBuildHeight() - 1);
        if (minY > maxY) {
            return false;
        }

        RandomSource random = context.random();
        BlockPos origin = context.origin();
        boolean placedAny = false;

        int heightSpan = maxY - minY + 1;
        for (int attempt = 0; attempt < settings.veinsPerChunk(); attempt++) {
            int x = origin.getX() + random.nextInt(16);
            int y = minY + random.nextInt(heightSpan);
            int z = origin.getZ() + random.nextInt(16);

            if (Feature.ORE.place(
                    oreConfiguration,
                    level,
                    context.chunkGenerator(),
                    random,
                    new BlockPos(x, y, z)
            )) {
                placedAny = true;
            }
        }

        return placedAny;
    }

    private OreConfiguration overworldOreConfiguration(
            LatentOreWorldgenSettings.DimensionSettings settings
    ) {
        float discardChance = (float) settings.discardChanceOnAirExposure();
        return new OreConfiguration(
                List.of(
                        OreConfiguration.target(
                                new TagMatchTest(BlockTags.STONE_ORE_REPLACEABLES),
                                EssenceInfuserContent.LATENT_ORE.get().defaultBlockState()
                        ),
                        OreConfiguration.target(
                                new TagMatchTest(BlockTags.DEEPSLATE_ORE_REPLACEABLES),
                                EssenceInfuserContent.DEEPSLATE_LATENT_ORE.get().defaultBlockState()
                        )
                ),
                settings.veinSize(),
                discardChance
        );
    }

    private OreConfiguration simpleOreConfiguration(
            RuleTest replacement,
            BlockState oreState,
            LatentOreWorldgenSettings.DimensionSettings settings
    ) {
        return new OreConfiguration(
                replacement,
                oreState,
                settings.veinSize(),
                (float) settings.discardChanceOnAirExposure()
        );
    }

    private BlockState oreState(LatentOreWorldgenSettings.OreVariant variant) {
        return switch (variant) {
            case STONE -> EssenceInfuserContent.LATENT_ORE.get().defaultBlockState();
            case DEEPSLATE -> EssenceInfuserContent.DEEPSLATE_LATENT_ORE.get().defaultBlockState();
            case NETHERRACK -> EssenceInfuserContent.NETHERRACK_LATENT_ORE.get().defaultBlockState();
            case END_STONE -> EssenceInfuserContent.END_STONE_LATENT_ORE.get().defaultBlockState();
        };
    }
}
