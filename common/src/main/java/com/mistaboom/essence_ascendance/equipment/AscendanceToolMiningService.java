package com.mistaboom.essence_ascendance.equipment;

import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
 * Applies the player's dynamic Ascendance harvesting capability to a concrete
 * tool ItemStack.
 *
 * Vanilla's Tier object is static per Item, but an Ascendance tool's harvest
 * capability belongs to the player tier. Minecraft 1.21 stores mining speed
 * and drop-correctness rules in the TOOL data component, so the server keeps
 * that component synchronized with the current player's tier/archetype
 * baseline. Invested mining_speed remains a separate player attribute.
 *
 * Numeric modded mining-level tags are discovered generically. Tags named
 * "needs_tool_level_N" or "needs_tool_level/N" in any namespace are treated
 * as requiring logical harvest level N. This gives pack authors an unbounded
 * integer convention without making the common architecture depend on a
 * particular loader or third-party equipment integration.
 */
public final class AscendanceToolMiningService {

    private static final Pattern UNDERSCORE_LEVEL_PATTERN =
            Pattern.compile("(?:^|/)needs_tool_level_(\\d+)$");

    private static final Pattern PATH_LEVEL_PATTERN =
            Pattern.compile("(?:^|/)needs_tool_level/(\\d+)$");

    private static final int MAX_COMPONENT_CACHE_ENTRIES = 512;

    private static final Map<ToolKey, Tool> TOOL_CACHE =
            new LinkedHashMap<>();

    private AscendanceToolMiningService() {
    }

    public static void sync(
            ItemStack stack,
            Level level,
            Entity entity,
            TagKey<Block> mineableTag,
            ResourceLocation profileId
    ) {
        if (level.isClientSide || !(entity instanceof ServerPlayer player)) {
            return;
        }

        PlayerEssenceData playerData =
                EssenceSavedData
                        .get(player.server)
                        .getPlayerData(player.getUUID());

        EquipmentBaselineResult baseline =
                EquipmentBaselineService.evaluateForStack(
                        playerData,
                        profileId,
                        stack
                );

        /*
         * The TOOL component owns only the physical tier/archetype mining
         * baseline. The invested mining_speed percentage is applied separately
         * to the player through Attributes.BLOCK_BREAK_SPEED by
         * EquipmentAttributeService. Keeping those layers separate prevents
         * stat investment from becoming baked into an ItemStack.
         */
        float miningSpeed = toPositiveFloat(baseline.miningSpeed());
        int harvestLevel = baseline.harvestLevel();

        Tool tool = toolFor(
                mineableTag,
                harvestLevel,
                miningSpeed
        );

        Tool current = stack.get(DataComponents.TOOL);
        if (!tool.equals(current)) {
            stack.set(DataComponents.TOOL, tool);
        }
    }

    public static void invalidateCache() {
        synchronized (TOOL_CACHE) {
            TOOL_CACHE.clear();
        }
    }

    private static Tool toolFor(
            TagKey<Block> mineableTag,
            int harvestLevel,
            float miningSpeed
    ) {
        ToolKey key = new ToolKey(
                mineableTag,
                harvestLevel,
                Float.floatToIntBits(miningSpeed)
        );

        synchronized (TOOL_CACHE) {
            Tool existing = TOOL_CACHE.get(key);
            if (existing != null) {
                return existing;
            }

            if (TOOL_CACHE.size() >= MAX_COMPONENT_CACHE_ENTRIES) {
                TOOL_CACHE.clear();
            }

            Tool created = createTool(
                    mineableTag,
                    harvestLevel,
                    miningSpeed
            );
            TOOL_CACHE.put(key, created);
            return created;
        }
    }

    private static Tool createTool(
            TagKey<Block> mineableTag,
            int harvestLevel,
            float miningSpeed
    ) {
        List<Tool.Rule> rules = new ArrayList<>();

        /*
         * Deny custom numeric requirements above the player's logical level.
         * The vanilla 0-4 ladder is handled by the normal incorrect-for-tool
         * tags immediately below.
         */
        for (RequiredLevelTag required : discoverNumericRequirementTags()) {
            if (required.level() > harvestLevel) {
                rules.add(
                        Tool.Rule.deniesDrops(
                                required.tag()
                        )
                );
            }
        }

        rules.add(
                Tool.Rule.deniesDrops(
                        vanillaIncorrectTag(harvestLevel)
                )
        );

        rules.add(
                Tool.Rule.minesAndDrops(
                        mineableTag,
                        miningSpeed
                )
        );

        return new Tool(
                List.copyOf(rules),
                1.0F,
                1
        );
    }

    private static List<RequiredLevelTag> discoverNumericRequirementTags() {
        return BuiltInRegistries.BLOCK
                .getTagNames()
                .map(tag -> new RequiredLevelTag(
                        tag,
                        parseRequiredLevel(tag.location())
                ))
                .filter(required -> required.level() >= 0)
                .sorted(
                        Comparator
                                .comparingInt(RequiredLevelTag::level)
                                .thenComparing(
                                        required -> required.tag().location().toString()
                                )
                )
                .toList();
    }

    private static int parseRequiredLevel(ResourceLocation id) {
        String path = id.getPath();

        Matcher underscore = UNDERSCORE_LEVEL_PATTERN.matcher(path);
        if (underscore.find()) {
            return parseLevel(underscore.group(1));
        }

        Matcher slash = PATH_LEVEL_PATTERN.matcher(path);
        if (slash.find()) {
            return parseLevel(slash.group(1));
        }

        return -1;
    }

    private static int parseLevel(String raw) {
        try {
            long parsed = Long.parseLong(raw);
            if (parsed > Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }
            return (int) parsed;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static TagKey<Block> vanillaIncorrectTag(int harvestLevel) {
        if (harvestLevel <= 0) {
            return BlockTags.INCORRECT_FOR_WOODEN_TOOL;
        }
        if (harvestLevel == 1) {
            return BlockTags.INCORRECT_FOR_STONE_TOOL;
        }
        if (harvestLevel == 2) {
            return BlockTags.INCORRECT_FOR_IRON_TOOL;
        }
        if (harvestLevel == 3) {
            return BlockTags.INCORRECT_FOR_DIAMOND_TOOL;
        }
        return BlockTags.INCORRECT_FOR_NETHERITE_TOOL;
    }

    private static float toPositiveFloat(double value) {
        if (!Double.isFinite(value) || value <= 0.0) {
            return 1.0F;
        }

        return (float) Math.min(
                value,
                Float.MAX_VALUE
        );
    }

    private record ToolKey(
            TagKey<Block> mineableTag,
            int harvestLevel,
            int miningSpeedBits
    ) {
    }

    private record RequiredLevelTag(
            TagKey<Block> tag,
            int level
    ) {
    }
}
