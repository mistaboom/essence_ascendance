package com.mistaboom.essence_ascendance.config;

import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleStructureService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.balance.BalanceProfileDefinition;
import com.mistaboom.essence_ascendance.balance.BalanceProfileRegistry;
import com.mistaboom.essence_ascendance.balance.BalanceProfiles;
import com.mistaboom.essence_ascendance.progression.AscendanceAdvancementDefinition;
import com.mistaboom.essence_ascendance.progression.AscendanceAdvancementRegistry;
import com.mistaboom.essence_ascendance.progression.MilestoneDefinition;
import com.mistaboom.essence_ascendance.progression.MilestoneRegistry;
import com.mistaboom.essence_ascendance.progression.HarvestProgressionSafety;
import com.mistaboom.essence_ascendance.progression.MilestoneRequirement;
import com.mistaboom.essence_ascendance.progression.StatScalingDefaults;
import com.mistaboom.essence_ascendance.stat.EssenceStatRegistry;
import com.mistaboom.essence_ascendance.equipment.AscendanceToolMiningService;
import com.mistaboom.essence_ascendance.equipment.EquipmentBaselineConfig;
import com.mistaboom.essence_ascendance.equipment.EquipmentBaselineDefaults;
import com.mistaboom.essence_ascendance.stat.StatDefinition;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import dev.architectury.platform.Platform;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class EssenceConfigManager {

    public static final int CURRENT_CONFIG_VERSION =
            4;

    private static final int MAX_REQUIREMENT_DEPTH =
            32;

    private static final String CONFIG_FILE_NAME =
            "essence_ascendance.json";


    private static final Gson GSON =
            new GsonBuilder()
                    .setPrettyPrinting()
                    .disableHtmlEscaping()
                    .create();


    private static EssenceServerConfig current;


    private EssenceConfigManager() {
    }

    private static Map<ResourceLocation, Double> parseStatMaxBonuses(
            JsonObject root
    ) {

        Map<ResourceLocation, Double> values =
                new LinkedHashMap<>(
                        StatScalingDefaults.values()
                );

        JsonElement overridesElement =
                root.get(
                        "stat_max_bonus_overrides"
                );

        if (overridesElement == null
                || overridesElement.isJsonNull()) {
            return values;
        }

        if (!overridesElement.isJsonObject()) {
            throw new IllegalArgumentException(
                    "stat_max_bonus_overrides must be a JSON object"
            );
        }

        JsonObject overrides =
                overridesElement.getAsJsonObject();

        for (Map.Entry<String, JsonElement> entry :
                overrides.entrySet()) {

            ResourceLocation statId =
                    ResourceLocation.tryParse(
                            entry.getKey()
                    );

            if (statId == null) {
                EssenceAscendance.LOGGER.warn(
                        "Ignoring invalid stat ID '{}' in stat_max_bonus_overrides",
                        entry.getKey()
                );
                continue;
            }

            if (EssenceStatRegistry.get(statId).isEmpty()) {
                EssenceAscendance.LOGGER.warn(
                        "Ignoring stat_max_bonus_overrides entry for unknown/removed stat '{}'",
                        statId
                );
                continue;
            }

            JsonElement valueElement = entry.getValue();

            if (!valueElement.isJsonPrimitive()
                    || !valueElement.getAsJsonPrimitive().isNumber()) {
                EssenceAscendance.LOGGER.warn(
                        "Ignoring non-numeric stat max bonus for '{}'",
                        statId
                );
                continue;
            }

            double value = valueElement.getAsDouble();

            if (!Double.isFinite(value) || value < 0.0) {
                EssenceAscendance.LOGGER.warn(
                        "Ignoring invalid stat max bonus for '{}': {}",
                        statId,
                        value
                );
                continue;
            }

            values.put(statId, value);
        }

        return values;
    }


    private static EquipmentBaselineConfig parseEquipmentBaselineConfig(
            JsonObject root,
            BalanceProfileDefinition balanceProfile
    ) {

        EquipmentBaselineConfig defaults =
                EquipmentBaselineDefaults.create(
                        balanceProfile
                );

        JsonObject overrides =
                getObject(
                        root,
                        "equipment_baseline_overrides"
                );

        if (overrides == null) {
            return defaults;
        }

        JsonObject tierOverrides =
                getObject(
                        overrides,
                        "tiers"
                );

        if (tierOverrides == null) {
            return defaults;
        }

        Map<ResourceLocation, EquipmentBaselineConfig.TierBaseline> baselines =
                new LinkedHashMap<>(
                        defaults.tierBaselines()
                );

        for (Map.Entry<String, JsonElement> entry :
                tierOverrides.entrySet()) {

            ResourceLocation tierId =
                    ResourceLocation.tryParse(
                            entry.getKey()
                    );

            if (tierId == null) {
                EssenceAscendance.LOGGER.warn(
                        "Ignoring invalid tier ID '{}' in equipment baseline overrides",
                        entry.getKey()
                );
                continue;
            }

            EquipmentBaselineConfig.TierBaseline existing =
                    baselines.get(tierId);

            if (existing == null) {
                EssenceAscendance.LOGGER.warn(
                        "Ignoring equipment baseline override for unknown tier '{}'",
                        tierId
                );
                continue;
            }

            if (!entry.getValue().isJsonObject()) {
                EssenceAscendance.LOGGER.warn(
                        "Ignoring equipment baseline override '{}' because it is not an object",
                        tierId
                );
                continue;
            }

            JsonObject object = entry.getValue().getAsJsonObject();

            try {
                baselines.put(
                        tierId,
                        new EquipmentBaselineConfig.TierBaseline(
                                readNonNegativeFiniteDouble(
                                        object,
                                        "full_set_armor",
                                        existing.fullSetArmor()
                                ),
                                readNonNegativeFiniteDouble(
                                        object,
                                        "full_set_toughness",
                                        existing.fullSetToughness()
                                ),
                                readNonNegativeFiniteDouble(
                                        object,
                                        "melee_damage",
                                        existing.meleeDamage()
                                ),
                                readNonNegativeFiniteDouble(
                                        object,
                                        "melee_attack_speed",
                                        existing.meleeAttackSpeed()
                                ),
                                readNonNegativeFiniteDouble(
                                        object,
                                        "ranged_damage",
                                        existing.rangedDamage()
                                ),
                                readNonNegativeFiniteDouble(
                                        object,
                                        "ranged_attack_speed",
                                        existing.rangedAttackSpeed()
                                ),
                                readNonNegativeFiniteDouble(
                                        object,
                                        "magic_damage",
                                        existing.magicDamage()
                                ),
                                readNonNegativeFiniteDouble(
                                        object,
                                        "magic_cast_speed",
                                        existing.magicCastSpeed()
                                ),
                                readNonNegativeFiniteDouble(
                                        object,
                                        "mining_speed",
                                        existing.miningSpeed()
                                ),
                                readInt(
                                        object,
                                        "harvest_level",
                                        existing.harvestLevel()
                                ),
                                readInt(
                                        object,
                                        "durability",
                                        existing.durability()
                                )
                        )
                );

            } catch (RuntimeException exception) {
                EssenceAscendance.LOGGER.warn(
                        "Ignoring invalid equipment baseline for tier '{}': {}",
                        tierId,
                        exception.getMessage()
                );
            }
        }

        return new EquipmentBaselineConfig(baselines);
    }


    /*
     * ============================================================
     * PUBLIC ACCESS
     * ============================================================
     */

    public static EssenceServerConfig get() {

        if (current == null) {
            current =
                    createBuiltInDefault();
            AscendanceToolMiningService.invalidateCache();
            HarvestProgressionSafety.logWarnings(current);
        }

        return current;
    }


    public static Path getConfigPath() {
        return Platform
                .getConfigFolder()
                .resolve(
                        CONFIG_FILE_NAME
                );
    }


    /*
     * Loads the configuration from disk.
     *
     * Invalid configuration never destroys the user's file.
     * If loading fails, the mod continues using safe built-in
     * Vanilla defaults.
     */
    public static void load() {

        Path configPath =
                getConfigPath();


        if (!Files.exists(
                configPath
        )) {

            try {

                writeDefaultConfig(
                        configPath
                );

            } catch (IOException exception) {

                EssenceAscendance.LOGGER.error(
                        "Could not create Essence Ascendance config at {}. Using built-in defaults.",
                        configPath,
                        exception
                );

                current =
                        createBuiltInDefault();
                AscendanceToolMiningService.invalidateCache();
                HarvestProgressionSafety.logWarnings(current);

                return;
            }
        }


        try (
                Reader reader =
                        Files.newBufferedReader(
                                configPath
                        )
        ) {

            JsonElement parsed =
                    JsonParser.parseReader(
                            reader
                    );


            if (!parsed.isJsonObject()) {
                throw new IllegalArgumentException(
                        "Root configuration value must be a JSON object"
                );
            }


            current =
                    parseConfig(
                            parsed.getAsJsonObject()
                    );

            AscendanceToolMiningService.invalidateCache();
            HarvestProgressionSafety.logWarnings(current);


            EssenceAscendance.LOGGER.info(
                    "Loaded Essence Ascendance config from {} using balance profile {}",
                    configPath,
                    current.balanceProfile().id()
            );


        } catch (Exception exception) {

            EssenceAscendance.LOGGER.error(
                    "Could not load Essence Ascendance config from {}. The file has been left unchanged and built-in Vanilla defaults will be used.",
                    configPath,
                    exception
            );


            current =
                    createBuiltInDefault();
            AscendanceToolMiningService.invalidateCache();
            HarvestProgressionSafety.logWarnings(current);
        }
    }


    public static void reload() {
        load();
    }


    /*
     * ============================================================
     * DEFAULT CONFIG FILE
     * ============================================================
     */

    private static void writeDefaultConfig(
            Path configPath
    ) throws IOException {

        Path parent =
                configPath.getParent();

        if (parent != null) {
            Files.createDirectories(
                    parent
            );
        }


        JsonObject root =
                new JsonObject();


        root.addProperty(
                "_comment",
                "Essence Ascendance server balance configuration. Built-in presets provide defaults; values below override only what you explicitly configure."
        );


        root.addProperty(
                "config_version",
                CURRENT_CONFIG_VERSION
        );

        root.addProperty(
                "_skill_essence_comment",
                "Skill Essence is future/expansion content. When false, Skill Essence stays registered and persisted but is hidden from player commands and interfaces."
        );
        root.addProperty(
                "enable_skill_essences",
                false
        );

        JsonObject cruciblePylons = new JsonObject();
        cruciblePylons.addProperty(
                "_comment",
                "Freeform Essence Pylons link to the nearest owned Crucible inside this spherical radius. Each active pylon adds one distinct-item Crucible input lane; the configured maximum is capped at 8."
        );
        cruciblePylons.addProperty(
                "radius",
                6.0D
        );
        cruciblePylons.addProperty(
                "max_active_pylons",
                8
        );
        root.add(
                "crucible_pylons",
                cruciblePylons
        );


        root.addProperty(
                "preset",
                BalanceProfiles.VANILLA
                        .id()
                        .toString()
        );


        root.add(
                "tier_cap_overrides",
                new JsonObject()
        );


        root.add(
                "stat_cap_overrides",
                new JsonObject()
        );

        root.add(
                "stat_max_bonus_overrides",
                new JsonObject()
        );


        JsonObject equipmentBaselines =
                new JsonObject();

        equipmentBaselines.addProperty(
                "_harvest_level_comment",
                "Logical tool harvest capability. Defaults are progression-safe: Dormant=2 (iron/Diamond-capable), Awakened=3 (diamond/Ancient-Debris-capable), then 4/5/6. Any non-negative integer is allowed for modpack tiers."
        );

        JsonObject tierEquipmentOverrides =
                new JsonObject();

        addDefaultHarvestLevel(
                tierEquipmentOverrides,
                AscendanceTiers.DORMANT.id(),
                2
        );
        addDefaultHarvestLevel(
                tierEquipmentOverrides,
                AscendanceTiers.AWAKENED.id(),
                3
        );
        addDefaultHarvestLevel(
                tierEquipmentOverrides,
                AscendanceTiers.RESONANT.id(),
                4
        );
        addDefaultHarvestLevel(
                tierEquipmentOverrides,
                AscendanceTiers.ASCENDANT.id(),
                5
        );
        addDefaultHarvestLevel(
                tierEquipmentOverrides,
                AscendanceTiers.TRANSCENDENT.id(),
                6
        );

        equipmentBaselines.add(
                "tiers",
                tierEquipmentOverrides
        );

        root.add(
                "equipment_baseline_overrides",
                equipmentBaselines
        );

        root.addProperty(
                "_ascendance_requirements_comment",
                "Ascension requirements are global config, not datapacks. Use milestone_overrides for world-objective definitions and advancement_overrides for tier-transition requirements."
        );

        root.add(
                "milestone_overrides",
                new JsonObject()
        );


        root.add(
                "advancement_overrides",
                new JsonObject()
        );


        try (
                Writer writer =
                        Files.newBufferedWriter(
                                configPath
                        )
        ) {

            GSON.toJson(
                    root,
                    writer
            );
        }


        EssenceAscendance.LOGGER.info(
                "Created default Essence Ascendance config at {}",
                configPath
        );
    }


    private static void addDefaultHarvestLevel(
            JsonObject tierOverrides,
            ResourceLocation tierId,
            int harvestLevel
    ) {
        JsonObject tier = new JsonObject();
        tier.addProperty(
                "harvest_level",
                harvestLevel
        );
        tierOverrides.add(
                tierId.toString(),
                tier
        );
    }


    /*
     * ============================================================
     * CONFIG PARSING
     * ============================================================
     */

    private static EssenceServerConfig parseConfig(
            JsonObject root
    ) {

        int version =
                readInt(
                        root,
                        "config_version",
                        0
                );


        if (version > CURRENT_CONFIG_VERSION) {

            EssenceAscendance.LOGGER.warn(
                    "Essence Ascendance config version {} is newer than supported version {}. Attempting best-effort load.",
                    version,
                    CURRENT_CONFIG_VERSION
            );
        }


        boolean skillEssencesEnabled =
                readBoolean(
                        root,
                        "enable_skill_essences",
                        false
                );

        double pylonRadius = 6.0D;
        int maxActivePylons = 8;
        JsonElement pylonElement = root.get("crucible_pylons");
        if (pylonElement != null && !pylonElement.isJsonNull()) {
            if (!pylonElement.isJsonObject()) {
                throw new IllegalArgumentException("crucible_pylons must be an object");
            }

            JsonObject pylonObject = pylonElement.getAsJsonObject();
            pylonRadius = readNonNegativeFiniteDouble(
                    pylonObject,
                    "radius",
                    pylonRadius
            );
            maxActivePylons = readInt(
                    pylonObject,
                    "max_active_pylons",
                    maxActivePylons
            );

            if (!(pylonRadius > 0.0D) || pylonRadius > 32.0D) {
                throw new IllegalArgumentException(
                        "crucible_pylons.radius must be greater than 0 and no more than 32"
                );
            }
            if (maxActivePylons < 0
                    || maxActivePylons > EssenceCrucibleStructureService.MAX_SUPPORTED_ACTIVE_PYLONS) {
                throw new IllegalArgumentException(
                        "crucible_pylons.max_active_pylons must be between 0 and "
                                + EssenceCrucibleStructureService.MAX_SUPPORTED_ACTIVE_PYLONS
                );
            }
        }

        BalanceProfileDefinition balanceProfile =
                parseBalanceProfile(
                        root
                );

        Map<ResourceLocation, Double> statMaxBonuses =
                parseStatMaxBonuses(
                        root
                );


        EquipmentBaselineConfig equipmentBaselineConfig =
                parseEquipmentBaselineConfig(
                        root,
                        balanceProfile
                );

        Map<ResourceLocation, MilestoneDefinition> milestones =
                parseMilestones(
                        root
                );


        Map<ResourceLocation, AscendanceAdvancementDefinition> advancements =
                parseAdvancements(
                        root
                );


        return new EssenceServerConfig(
                version,
                skillEssencesEnabled,
                pylonRadius,
                maxActivePylons,
                balanceProfile,
                milestones,
                advancements,
                statMaxBonuses,
                equipmentBaselineConfig
        );
    }


    /*
     * ============================================================
     * BALANCE PROFILE
     * ============================================================
     */

    private static BalanceProfileDefinition parseBalanceProfile(
            JsonObject root
    ) {

        ResourceLocation presetId =
                readPresetId(
                        root
                );


        BalanceProfileDefinition preset =
                BalanceProfileRegistry
                        .get(
                                presetId
                        )
                        .orElseGet(
                                () -> {

                                    EssenceAscendance.LOGGER.warn(
                                            "Unknown balance profile '{}'; falling back to {}",
                                            presetId,
                                            BalanceProfiles.VANILLA.id()
                                    );

                                    return BalanceProfiles.VANILLA;
                                }
                        );


        Map<ResourceLocation, Long> tierCaps =
                new LinkedHashMap<>(
                        preset.defaultTierCaps()
                );


        Map<
                ResourceLocation,
                Map<ResourceLocation, Long>
                > statOverrides =
                deepCopyOverrides(
                        preset.statOverrides()
                );


        JsonObject tierOverrideObject =
                getObject(
                        root,
                        "tier_cap_overrides"
                );


        if (tierOverrideObject != null) {

            for (Map.Entry<String, JsonElement> entry :
                    tierOverrideObject.entrySet()) {

                ResourceLocation tierId =
                        ResourceLocation.tryParse(
                                entry.getKey()
                        );


                if (tierId == null) {

                    EssenceAscendance.LOGGER.warn(
                            "Ignoring invalid tier ID '{}' in tier_cap_overrides",
                            entry.getKey()
                    );

                    continue;
                }


                Long cap =
                        readNonNegativeLong(
                                entry.getValue()
                        );


                if (cap == null) {

                    EssenceAscendance.LOGGER.warn(
                            "Ignoring invalid investment cap for tier '{}'",
                            tierId
                    );

                    continue;
                }


                tierCaps.put(
                        tierId,
                        cap
                );
            }
        }


        JsonObject statOverrideObject =
                getObject(
                        root,
                        "stat_cap_overrides"
                );


        if (statOverrideObject != null) {

            for (Map.Entry<String, JsonElement> statEntry :
                    statOverrideObject.entrySet()) {

                ResourceLocation statId =
                        ResourceLocation.tryParse(
                                statEntry.getKey()
                        );


                if (statId == null) {

                    EssenceAscendance.LOGGER.warn(
                            "Ignoring invalid stat ID '{}' in stat_cap_overrides",
                            statEntry.getKey()
                    );

                    continue;
                }


                if (EssenceStatRegistry.get(statId).isEmpty()) {
                    EssenceAscendance.LOGGER.warn(
                            "Ignoring stat_cap_overrides entry for unknown/removed stat '{}'",
                            statId
                    );
                    continue;
                }


                if (!statEntry
                        .getValue()
                        .isJsonObject()) {

                    EssenceAscendance.LOGGER.warn(
                            "Ignoring stat cap overrides for '{}' because the value is not an object",
                            statId
                    );

                    continue;
                }


                Map<ResourceLocation, Long> perTier =
                        statOverrides.computeIfAbsent(
                                statId,
                                ignored ->
                                        new LinkedHashMap<>()
                        );


                for (Map.Entry<String, JsonElement> tierEntry :
                        statEntry
                                .getValue()
                                .getAsJsonObject()
                                .entrySet()) {

                    ResourceLocation tierId =
                            ResourceLocation.tryParse(
                                    tierEntry.getKey()
                            );


                    if (tierId == null) {

                        EssenceAscendance.LOGGER.warn(
                                "Ignoring invalid tier ID '{}' in stat cap override for '{}'",
                                tierEntry.getKey(),
                                statId
                        );

                        continue;
                    }


                    Long cap =
                            readNonNegativeLong(
                                    tierEntry.getValue()
                            );


                    if (cap == null) {

                        EssenceAscendance.LOGGER.warn(
                                "Ignoring invalid cap for stat '{}' at tier '{}'",
                                statId,
                                tierId
                        );

                        continue;
                    }


                    perTier.put(
                            tierId,
                            cap
                    );
                }
            }
        }


        return new BalanceProfileDefinition(
                preset.id(),
                preset.displayName(),
                tierCaps,
                statOverrides
        );
    }


    private static ResourceLocation readPresetId(
            JsonObject root
    ) {

        String rawPreset =
                readString(
                        root,
                        "preset",
                        BalanceProfiles.VANILLA
                                .id()
                                .toString()
                );


        ResourceLocation parsed =
                ResourceLocation.tryParse(
                        rawPreset
                );


        if (parsed == null) {

            EssenceAscendance.LOGGER.warn(
                    "Invalid balance profile ID '{}'; falling back to {}",
                    rawPreset,
                    BalanceProfiles.VANILLA.id()
            );

            return BalanceProfiles.VANILLA.id();
        }


        return parsed;
    }


    /*
     * ============================================================
     * MILESTONES
     * ============================================================
     */

    private static Map<ResourceLocation, MilestoneDefinition> parseMilestones(
            JsonObject root
    ) {

        Map<ResourceLocation, MilestoneDefinition> milestones =
                new LinkedHashMap<>();


        for (MilestoneDefinition milestone :
                MilestoneRegistry.values()) {

            milestones.put(
                    milestone.id(),
                    milestone
            );
        }


        JsonObject overrides =
                getObject(
                        root,
                        "milestone_overrides"
                );


        if (overrides == null) {
            return milestones;
        }


        for (Map.Entry<String, JsonElement> entry :
                overrides.entrySet()) {

            ResourceLocation milestoneId =
                    ResourceLocation.tryParse(
                            entry.getKey()
                    );


            if (milestoneId == null) {

                EssenceAscendance.LOGGER.warn(
                        "Ignoring invalid milestone ID '{}'",
                        entry.getKey()
                );

                continue;
            }


            if (!entry
                    .getValue()
                    .isJsonObject()) {

                EssenceAscendance.LOGGER.warn(
                        "Ignoring milestone '{}' because its configuration is not an object",
                        milestoneId
                );

                continue;
            }


            JsonObject object =
                    entry.getValue()
                            .getAsJsonObject();


            MilestoneDefinition existing =
                    milestones.get(
                            milestoneId
                    );


            try {

                String displayName =
                        readString(
                                object,
                                "display_name",
                                existing == null
                                        ? null
                                        : existing.displayName()
                        );


                ResourceLocation providerId =
                        readResourceLocation(
                                object,
                                "provider",
                                existing == null
                                        ? null
                                        : existing.providerId()
                        );


                String target =
                        readString(
                                object,
                                "target",
                                existing == null
                                        ? null
                                        : existing.target()
                        );


                if (displayName == null
                        || providerId == null
                        || target == null) {

                    throw new IllegalArgumentException(
                            "New milestone definitions require display_name, provider, and target"
                    );
                }


                milestones.put(
                        milestoneId,
                        new MilestoneDefinition(
                                milestoneId,
                                displayName,
                                providerId,
                                target
                        )
                );


            } catch (RuntimeException exception) {

                EssenceAscendance.LOGGER.warn(
                        "Ignoring invalid milestone configuration for '{}': {}",
                        milestoneId,
                        exception.getMessage()
                );
            }
        }


        return milestones;
    }


    /*
     * ============================================================
     * ASCENDANCE ADVANCEMENTS
     * ============================================================
     */

    private static Map<
            ResourceLocation,
            AscendanceAdvancementDefinition
            > parseAdvancements(
            JsonObject root
    ) {

        Map<
                ResourceLocation,
                AscendanceAdvancementDefinition
                > advancements =
                new LinkedHashMap<>();


        for (AscendanceAdvancementDefinition advancement :
                AscendanceAdvancementRegistry.values()) {

            advancements.put(
                    advancement.id(),
                    advancement
            );
        }


        JsonObject overrides =
                getObject(
                        root,
                        "advancement_overrides"
                );


        if (overrides == null) {
            return advancements;
        }


        for (Map.Entry<String, JsonElement> entry :
                overrides.entrySet()) {

            ResourceLocation advancementId =
                    ResourceLocation.tryParse(
                            entry.getKey()
                    );


            if (advancementId == null) {

                EssenceAscendance.LOGGER.warn(
                        "Ignoring invalid Ascendance advancement ID '{}'",
                        entry.getKey()
                );

                continue;
            }


            if (!entry
                    .getValue()
                    .isJsonObject()) {

                EssenceAscendance.LOGGER.warn(
                        "Ignoring Ascendance advancement '{}' because its configuration is not an object",
                        advancementId
                );

                continue;
            }


            JsonObject object =
                    entry.getValue()
                            .getAsJsonObject();


            AscendanceAdvancementDefinition existing =
                    advancements.get(
                            advancementId
                    );


            try {

                ResourceLocation fromTier =
                        readResourceLocation(
                                object,
                                "from_tier",
                                existing == null
                                        ? null
                                        : existing.fromTierId()
                        );


                ResourceLocation toTier =
                        readResourceLocation(
                                object,
                                "to_tier",
                                existing == null
                                        ? null
                                        : existing.toTierId()
                        );


                long multiplier =
                        readLong(
                                object,
                                "total_investment_multiplier",
                                existing == null
                                        ? -1L
                                        : existing.totalInvestmentMultiplier()
                        );


                int developedStats =
                        readInt(
                                object,
                                "minimum_developed_stats",
                                existing == null
                                        ? -1
                                        : existing.minimumDevelopedStats()
                        );


                int representedCategories =
                        readInt(
                                object,
                                "minimum_represented_categories",
                                existing == null
                                        ? -1
                                        : existing.minimumRepresentedCategories()
                        );


                double developedThreshold =
                        readDouble(
                                object,
                                "developed_stat_threshold",
                                existing == null
                                        ? -1.0D
                                        : existing.developedStatThreshold()
                        );


                MilestoneRequirement worldRequirement;


                if (object.has(
                        "world_requirement"
                )) {

                    worldRequirement =
                            parseRequirement(
                                    object.get(
                                            "world_requirement"
                                    ),
                                    0
                            );

                } else {

                    worldRequirement =
                            existing == null
                                    ? null
                                    : existing.worldRequirement();
                }


                if (fromTier == null
                        || toTier == null
                        || worldRequirement == null) {

                    throw new IllegalArgumentException(
                            "New advancement definitions require from_tier, to_tier, and world_requirement"
                    );
                }


                AscendanceAdvancementDefinition definition =
                        new AscendanceAdvancementDefinition(
                                advancementId,
                                fromTier,
                                toTier,
                                multiplier,
                                developedStats,
                                representedCategories,
                                developedThreshold,
                                worldRequirement
                        );


                advancements.put(
                        advancementId,
                        definition
                );


            } catch (RuntimeException exception) {

                EssenceAscendance.LOGGER.warn(
                        "Ignoring invalid Ascendance advancement configuration for '{}': {}",
                        advancementId,
                        exception.getMessage()
                );
            }
        }


        return advancements;
    }


    /*
     * ============================================================
     * REQUIREMENT TREE
     * ============================================================
     */

    private static MilestoneRequirement parseRequirement(
            JsonElement element,
            int depth
    ) {

        if (depth > MAX_REQUIREMENT_DEPTH) {
            throw new IllegalArgumentException(
                    "Milestone requirement nesting exceeds maximum depth of "
                            + MAX_REQUIREMENT_DEPTH
            );
        }


        /*
         * Shorthand:
         *
         * "some_namespace:some_milestone"
         */
        if (element.isJsonPrimitive()
                && element.getAsJsonPrimitive().isString()) {

            ResourceLocation milestoneId =
                    ResourceLocation.tryParse(
                            element.getAsString()
                    );


            if (milestoneId == null) {
                throw new IllegalArgumentException(
                        "Invalid milestone ID: "
                                + element.getAsString()
                );
            }


            return MilestoneRequirement.milestone(
                    milestoneId
            );
        }


        if (!element.isJsonObject()) {
            throw new IllegalArgumentException(
                    "Milestone requirement must be an object or milestone ID string"
            );
        }


        JsonObject object =
                element.getAsJsonObject();


        String type =
                readString(
                        object,
                        "type",
                        null
                );


        if (type == null) {
            throw new IllegalArgumentException(
                    "Milestone requirement is missing type"
            );
        }


        return switch (
                type.toLowerCase()
                ) {

            case "milestone" -> {

                ResourceLocation milestoneId =
                        readResourceLocation(
                                object,
                                "milestone",
                                null
                        );


                if (milestoneId == null) {
                    throw new IllegalArgumentException(
                            "Milestone requirement is missing milestone ID"
                    );
                }


                yield MilestoneRequirement.milestone(
                        milestoneId
                );
            }


            case "all_of" ->
                    MilestoneRequirement.allOf(
                            parseChildren(
                                    object,
                                    depth + 1
                            )
                    );


            case "any_of" ->
                    MilestoneRequirement.anyOf(
                            parseChildren(
                                    object,
                                    depth + 1
                            )
                    );


            case "always",
                 "none",
                 "disabled" ->
                    MilestoneRequirement.always();


            default ->
                    throw new IllegalArgumentException(
                            "Unknown milestone requirement type: "
                                    + type
                    );
        };
    }


    private static MilestoneRequirement[] parseChildren(
            JsonObject object,
            int depth
    ) {

        JsonArray children =
                getArray(
                        object,
                        "children"
                );


        if (children == null
                || children.isEmpty()) {

            throw new IllegalArgumentException(
                    "Composite milestone requirement must contain children"
            );
        }


        MilestoneRequirement[] requirements =
                new MilestoneRequirement[
                        children.size()
                        ];


        for (int i = 0;
             i < children.size();
             i++) {

            requirements[i] =
                    parseRequirement(
                            children.get(i),
                            depth
                    );
        }


        return requirements;
    }


    /*
     * ============================================================
     * DEFAULT RUNTIME CONFIG
     * ============================================================
     */

    private static EssenceServerConfig createBuiltInDefault() {

        Map<ResourceLocation, MilestoneDefinition> milestones =
                new LinkedHashMap<>();


        for (MilestoneDefinition milestone :
                MilestoneRegistry.values()) {

            milestones.put(
                    milestone.id(),
                    milestone
            );
        }


        Map<
                ResourceLocation,
                AscendanceAdvancementDefinition
                > advancements =
                new LinkedHashMap<>();


        for (AscendanceAdvancementDefinition advancement :
                AscendanceAdvancementRegistry.values()) {

            advancements.put(
                    advancement.id(),
                    advancement
            );
        }


        return new EssenceServerConfig(
                CURRENT_CONFIG_VERSION,
                false,
                6.0D,
                8,
                BalanceProfiles.VANILLA,
                milestones,
                advancements,
                new LinkedHashMap<>(
                        StatScalingDefaults.values()
                ),
                EquipmentBaselineDefaults.create(
                        BalanceProfiles.VANILLA
                )
        );
    }


    /*
     * ============================================================
     * JSON HELPERS
     * ============================================================
     */

    private static double readNonNegativeFiniteDouble(
            JsonObject object,
            String key,
            double fallback
    ) {

        if (!object.has(
                key
        )) {

            return fallback;
        }


        try {

            double value =
                    object
                            .get(key)
                            .getAsDouble();


            if (!Double.isFinite(
                    value
            )
                    || value < 0.0) {

                EssenceAscendance.LOGGER.warn(
                        "Ignoring invalid non-negative decimal value for '{}'",
                        key
                );

                return fallback;
            }


            return value;


        } catch (RuntimeException exception) {

            EssenceAscendance.LOGGER.warn(
                    "Ignoring invalid decimal value for '{}'",
                    key
            );

            return fallback;
        }
    }

    private static JsonObject getObject(
            JsonObject parent,
            String key
    ) {

        if (!parent.has(
                key
        )) {
            return null;
        }


        JsonElement element =
                parent.get(
                        key
                );


        if (!element.isJsonObject()) {
            return null;
        }


        return element.getAsJsonObject();
    }


    private static JsonArray getArray(
            JsonObject parent,
            String key
    ) {

        if (!parent.has(
                key
        )) {
            return null;
        }


        JsonElement element =
                parent.get(
                        key
                );


        if (!element.isJsonArray()) {
            return null;
        }


        return element.getAsJsonArray();
    }


    private static boolean readBoolean(
            JsonObject object,
            String key,
            boolean fallback
    ) {

        if (!object.has(
                key
        )) {
            return fallback;
        }

        try {
            JsonElement element = object.get(key);
            if (!element.isJsonPrimitive()
                    || !element.getAsJsonPrimitive().isBoolean()) {
                throw new IllegalArgumentException();
            }
            return element.getAsBoolean();

        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    "Invalid boolean value for '"
                            + key
                            + "'"
            );
        }
    }


    private static String readString(
            JsonObject object,
            String key,
            String fallback
    ) {

        if (!object.has(
                key
        )) {
            return fallback;
        }


        try {

            return object
                    .get(key)
                    .getAsString();

        } catch (RuntimeException exception) {

            throw new IllegalArgumentException(
                    "Invalid string value for '"
                            + key
                            + "'"
            );
        }
    }


    private static ResourceLocation readResourceLocation(
            JsonObject object,
            String key,
            ResourceLocation fallback
    ) {

        if (!object.has(
                key
        )) {
            return fallback;
        }


        String raw =
                readString(
                        object,
                        key,
                        null
                );


        ResourceLocation parsed =
                ResourceLocation.tryParse(
                        raw
                );


        if (parsed == null) {
            throw new IllegalArgumentException(
                    "Invalid ResourceLocation for '"
                            + key
                            + "': "
                            + raw
            );
        }


        return parsed;
    }


    private static long readLong(
            JsonObject object,
            String key,
            long fallback
    ) {

        if (!object.has(
                key
        )) {
            return fallback;
        }


        try {

            return object
                    .get(key)
                    .getAsLong();

        } catch (RuntimeException exception) {

            throw new IllegalArgumentException(
                    "Invalid long value for '"
                            + key
                            + "'"
            );
        }
    }


    private static int readInt(
            JsonObject object,
            String key,
            int fallback
    ) {

        if (!object.has(
                key
        )) {
            return fallback;
        }


        try {

            return object
                    .get(key)
                    .getAsInt();

        } catch (RuntimeException exception) {

            throw new IllegalArgumentException(
                    "Invalid integer value for '"
                            + key
                            + "'"
            );
        }
    }


    private static double readDouble(
            JsonObject object,
            String key,
            double fallback
    ) {

        if (!object.has(
                key
        )) {
            return fallback;
        }


        try {

            return object
                    .get(key)
                    .getAsDouble();

        } catch (RuntimeException exception) {

            throw new IllegalArgumentException(
                    "Invalid decimal value for '"
                            + key
                            + "'"
            );
        }
    }


    private static Long readNonNegativeLong(
            JsonElement element
    ) {

        try {

            long value =
                    element.getAsLong();


            if (value < 0) {
                return null;
            }


            return value;


        } catch (RuntimeException exception) {

            return null;
        }
    }


    private static Map<
            ResourceLocation,
            Map<ResourceLocation, Long>
            > deepCopyOverrides(
            Map<
                    ResourceLocation,
                    Map<ResourceLocation, Long>
                    > source
    ) {

        Map<
                ResourceLocation,
                Map<ResourceLocation, Long>
                > copy =
                new LinkedHashMap<>();


        for (Map.Entry<
                ResourceLocation,
                Map<ResourceLocation, Long>
                > entry :
                source.entrySet()) {

            copy.put(
                    entry.getKey(),
                    new LinkedHashMap<>(
                            entry.getValue()
                    )
            );
        }


        return copy;
    }
}