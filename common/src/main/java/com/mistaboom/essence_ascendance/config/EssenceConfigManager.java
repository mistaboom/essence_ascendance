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
import com.mistaboom.essence_ascendance.tier.AscendanceTierRegistry;
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
            11;

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


    /**
     * Writes a fully materialized, preset-specific template beside the live
     * configuration file. The live config is never replaced by this method.
     */
    public static Path writePresetTemplate(
            BalanceProfileDefinition preset
    ) throws IOException {
        if (preset == null) {
            throw new IllegalArgumentException("Preset cannot be null");
        }

        Path liveConfig = getConfigPath();
        Path templatePath = liveConfig.resolveSibling(
                "essence_ascendance_template_" + preset.id().getPath() + ".json"
        );

        Path parent = templatePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        JsonObject root = createCompletePresetTemplate(preset);

        // Validate the generated document through the same parser used by the
        // live server before writing it to disk.
        parseConfig(root);
        writeConfigObject(templatePath, root);

        EssenceAscendance.LOGGER.info(
                "Generated complete Essence Ascendance {} config template at {}",
                preset.displayName(),
                templatePath
        );
        return templatePath;
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


        JsonObject loadedRoot = null;
        boolean configScaffoldingChanged = false;

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

            loadedRoot = parsed.getAsJsonObject();
            configScaffoldingChanged = addMissingCurrentConfigScaffolding(loadedRoot);

            current =
                    parseConfig(
                            loadedRoot
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
            return;
        }

        if (loadedRoot != null && configScaffoldingChanged) {
            try {
                writeConfigObject(configPath, loadedRoot);
                EssenceAscendance.LOGGER.info(
                        "Updated Essence Ascendance config scaffolding at {} to version {}",
                        configPath,
                        CURRENT_CONFIG_VERSION
                );
            } catch (IOException exception) {
                EssenceAscendance.LOGGER.warn(
                        "Loaded Essence Ascendance config successfully, but could not write new default config fields to {}. Runtime defaults will still be used for missing fields.",
                        configPath,
                        exception
                );
            }
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

        InfuserBalanceSettings defaultInfuser =
                InfuserBalanceSettings.defaults();
        JsonObject infuser = new JsonObject();
        infuser.addProperty(
                "_comment",
                "Global Essence Infuser progression values. Efficiency is expressed in basis points (5000 = 50%). Infusion throughput is Essence work per second and is independent from carrier density. These settings are loaded before world creation; no datapack is required."
        );
        infuser.addProperty("link_range", defaultInfuser.linkRange());

        JsonObject noFocus = new JsonObject();
        noFocus.addProperty(
                "efficiency_basis_points",
                defaultInfuser.noFocusEfficiencyBasisPoints()
        );
        noFocus.addProperty(
                "infusion_throughput_per_second",
                defaultInfuser.noFocusInfusionThroughputPerSecond()
        );
        infuser.add("no_focus", noFocus);

        JsonObject grades = new JsonObject();
        for (String gradeName : new String[]{
                "dormant", "awakened", "resonant", "ascendant", "transcendent"
        }) {
            InfuserBalanceSettings.GradeSettings grade =
                    defaultInfuser.grade(gradeName);
            JsonObject gradeObject = new JsonObject();
            gradeObject.addProperty("ingot_capacity", grade.ingotCapacity());
            gradeObject.addProperty(
                    "efficiency_basis_points",
                    grade.efficiencyBasisPoints()
            );
            gradeObject.addProperty(
                    "infusion_throughput_per_second",
                    grade.infusionThroughputPerSecond()
            );
            grades.add(gradeName, gradeObject);
        }
        infuser.add("grades", grades);

        JsonObject focusUpgrades = new JsonObject();
        focusUpgrades.addProperty(
                "_comment",
                "Focus upgrades consume all six core Essences automatically. minimum_per_attribute_essence is required from EACH core Essence; total_essence_required may be satisfied by any mix after all six minimums are met."
        );
        for (String targetTier : new String[]{
                "dormant", "awakened", "resonant", "ascendant", "transcendent"
        }) {
            InfuserBalanceSettings.FocusUpgradeSettings focus =
                    defaultInfuser.focusUpgrade(targetTier);
            JsonObject focusObject = new JsonObject();
            focusObject.addProperty(
                    "minimum_per_attribute_essence",
                    focus.minimumPerAttributeEssence()
            );
            focusObject.addProperty(
                    "total_essence_required",
                    focus.totalEssenceRequired()
            );
            focusUpgrades.add(targetTier, focusObject);
        }
        infuser.add("focus_upgrades", focusUpgrades);
        root.add("essence_infuser", infuser);
        root.add("ascendance_shield", createShieldJson(ShieldBalanceSettings.defaults()));

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


    private static boolean addMissingCurrentConfigScaffolding(
            JsonObject root
    ) {
        int version = readInt(root, "config_version", 0);
        if (version > CURRENT_CONFIG_VERSION) {
            return false;
        }

        boolean changed = false;

        /*
         * v10 retires the experimental Skill Essence family. These fields no
         * longer control runtime behavior, so remove them instead of leaving
         * a misleading dead toggle in upgraded configurations.
         */
        if (root.remove("_skill_essence_comment") != null) {
            changed = true;
        }
        if (root.remove("enable_skill_essences") != null) {
            changed = true;
        }

        /*
         * v11 splits the shield's old flat 20% native reflection into a 10%
         * baseline plus a 0-5% completed-item-tier bonus. Preserve a genuinely
         * customized legacy value as the new baseline, while translating the
         * generated 20% default to the new 10% balance.
         */
        if (version <= 10) {
            if (migrateV10ShieldBalance(root)) {
                changed = true;
            }
            if (migrateV10DamageReflectionMaximum(root)) {
                changed = true;
            }
        }

        /*
         * v8 briefly auto-materialized the Latent Ore defaults into every
         * config. v9 returns to the mod's sparse override philosophy. If the
         * section is byte-for-byte equivalent to the v8 generated defaults,
         * remove it. Any actual user edits are preserved.
         */
        if (version <= 8 && isGeneratedV8LatentOreDefaults(root.get("latent_ore_worldgen"))) {
            root.remove("latent_ore_worldgen");
            changed = true;
        }

        if (version < CURRENT_CONFIG_VERSION) {
            root.addProperty("config_version", CURRENT_CONFIG_VERSION);
            changed = true;
        }

        return changed;
    }

    private static boolean migrateV10DamageReflectionMaximum(JsonObject root) {
        JsonElement overridesElement = root.get("stat_max_bonus_overrides");
        if (overridesElement == null || !overridesElement.isJsonObject()) {
            return false;
        }

        JsonObject overrides = overridesElement.getAsJsonObject();
        String key = EssenceAscendance.MOD_ID + ":damage_reflection";
        JsonElement value = overrides.get(key);
        if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isNumber()
                || Double.compare(value.getAsDouble(), 25.0D) != 0) {
            return false;
        }

        overrides.addProperty(key, 15.0D);
        return true;
    }

    private static boolean migrateV10ShieldBalance(JsonObject root) {
        JsonElement shieldElement = root.get("ascendance_shield");
        if (shieldElement == null || !shieldElement.isJsonObject()) {
            return false;
        }

        JsonObject shield = shieldElement.getAsJsonObject();
        JsonElement legacyElement = shield.remove("innate_reflection_percent");
        boolean changed = legacyElement != null;

        if (!shield.has("base_reflection_percent")) {
            double baseReflection = ShieldBalanceSettings.defaults().baseReflectionPercent();
            if (legacyElement != null && legacyElement.isJsonPrimitive()
                    && legacyElement.getAsJsonPrimitive().isNumber()) {
                double legacyReflection = legacyElement.getAsDouble();
                if (Double.isFinite(legacyReflection) && Double.compare(legacyReflection, 20.0D) != 0) {
                    baseReflection = legacyReflection;
                }
            }
            shield.addProperty("base_reflection_percent", baseReflection);
            changed = true;
        }

        if (!shield.has("innate_reflection_bonus")) {
            JsonObject reflectionBonus = new JsonObject();
            ShieldBalanceSettings defaults = ShieldBalanceSettings.defaults();
            for (var tier : com.mistaboom.essence_ascendance.equipment.EquipmentTier.values()) {
                reflectionBonus.addProperty(tier.serializedName(), defaults.innateReflectionBonus().get(tier));
            }
            shield.add("innate_reflection_bonus", reflectionBonus);
            changed = true;
        }
        return changed;
    }

    private static boolean isGeneratedV8LatentOreDefaults(
            JsonElement element
    ) {
        if (element == null || !element.isJsonObject()) {
            return false;
        }

        JsonObject actual = element.getAsJsonObject().deepCopy();
        JsonObject expected = createLatentOreWorldgenJson(
                LatentOreWorldgenSettings.defaults(),
                false
        );

        // v8 predates custom_dimensions entirely. Ignore the v9-only empty
        // container when recognizing the exact auto-generated v8 defaults.
        expected.remove("custom_dimensions");
        actual.remove("_comment");
        expected.remove("_comment");
        return actual.equals(expected);
    }

    private static void writeConfigObject(
            Path configPath,
            JsonObject root
    ) throws IOException {
        try (Writer writer = Files.newBufferedWriter(configPath)) {
            GSON.toJson(root, writer);
        }
    }

    private static JsonObject createLatentOreWorldgenJson(
            LatentOreWorldgenSettings settings,
            boolean includeCustomExample
    ) {
        JsonObject worldgen = new JsonObject();
        worldgen.addProperty(
                "_comment",
                "Optional Latent Ore overrides. Missing sections use built-in defaults. Values affect NEW chunks only. Custom rules may target one exact dimension or a dimension_type_tag, replace a block or #block_tag, and either choose a built-in ore_variant or supply a registered ore_block from a compatibility addon."
        );
        worldgen.add(
                "overworld",
                createLatentOreDimensionJson(settings.overworld())
        );
        worldgen.add(
                "nether",
                createLatentOreDimensionJson(settings.nether())
        );
        worldgen.add(
                "end",
                createLatentOreDimensionJson(settings.end())
        );

        JsonObject custom = new JsonObject();
        for (Map.Entry<String, LatentOreWorldgenSettings.CustomDimensionSettings> entry
                : settings.customDimensions().entrySet()) {
            custom.add(entry.getKey(), createCustomLatentOreDimensionJson(entry.getValue()));
        }
        if (includeCustomExample) {
            JsonObject variantExample = new JsonObject();
            variantExample.addProperty(
                    "_comment",
                    "Ignored example using one of Essence Ascendance's built-in ore appearances. Copy to a new rule name and remove the leading underscore from the rule name."
            );
            variantExample.addProperty("dimension", "example_mod:moon");
            variantExample.addProperty("replace", "#example_mod:moon_stone_replaceables");
            variantExample.addProperty("ore_variant", "end_stone");
            variantExample.addProperty("enabled", true);
            variantExample.addProperty("vein_size", 8);
            variantExample.addProperty("veins_per_chunk", 5);
            variantExample.addProperty("min_y", -32);
            variantExample.addProperty("max_y", 96);
            variantExample.addProperty("discard_chance_on_air_exposure", 0.0D);
            custom.add("_example_builtin_variant", variantExample);

            JsonObject customBlockExample = new JsonObject();
            customBlockExample.addProperty(
                    "_comment",
                    "Ignored advanced example. ore_block may point at a registered ore block supplied by a compatibility addon; when present it overrides ore_variant."
            );
            customBlockExample.addProperty("dimension_type_tag", "#example_mod:moon_like");
            customBlockExample.addProperty("replace", "example_mod:moon_stone");
            customBlockExample.addProperty("ore_block", "example_addon:moon_latent_ore");
            customBlockExample.addProperty("enabled", true);
            customBlockExample.addProperty("vein_size", 8);
            customBlockExample.addProperty("veins_per_chunk", 5);
            customBlockExample.addProperty("min_y", -32);
            customBlockExample.addProperty("max_y", 96);
            customBlockExample.addProperty("discard_chance_on_air_exposure", 0.0D);
            custom.add("_example_custom_block", customBlockExample);
        }
        worldgen.add("custom_dimensions", custom);
        return worldgen;
    }

    private static JsonObject createLatentOreDimensionJson(
            LatentOreWorldgenSettings.DimensionSettings settings
    ) {
        JsonObject object = new JsonObject();
        object.addProperty("enabled", settings.enabled());
        object.addProperty("vein_size", settings.veinSize());
        object.addProperty("veins_per_chunk", settings.veinsPerChunk());
        object.addProperty("min_y", settings.minY());
        object.addProperty("max_y", settings.maxY());
        object.addProperty(
                "discard_chance_on_air_exposure",
                settings.discardChanceOnAirExposure()
        );
        return object;
    }

    private static JsonObject createCustomLatentOreDimensionJson(
            LatentOreWorldgenSettings.CustomDimensionSettings settings
    ) {
        JsonObject object = createLatentOreDimensionJson(settings.distribution());
        if (settings.dimension() != null) {
            object.addProperty("dimension", settings.dimension().toString());
        } else {
            object.addProperty("dimension_type_tag", "#" + settings.dimensionTypeTag());
        }
        object.addProperty("replace", settings.replacement().configValue());
        if (settings.usesCustomOreBlock()) {
            object.addProperty("ore_block", settings.oreBlock().toString());
        } else {
            object.addProperty("ore_variant", settings.oreVariant().configName());
        }
        return object;
    }

    private static JsonObject createCompletePresetTemplate(
            BalanceProfileDefinition preset
    ) {
        JsonObject root = new JsonObject();
        root.addProperty(
                "_comment",
                "Complete Essence Ascendance template generated from the "
                        + preset.displayName()
                        + " preset. Every currently configurable core setting is materialized so this file can be copied/renamed and edited for a modpack."
        );
        root.addProperty("config_version", CURRENT_CONFIG_VERSION);

        JsonObject cruciblePylons = new JsonObject();
        cruciblePylons.addProperty(
                "_comment",
                "Freeform Essence Pylons link to the nearest owned Crucible inside this spherical radius."
        );
        cruciblePylons.addProperty("radius", 6.0D);
        cruciblePylons.addProperty("max_active_pylons", 8);
        root.add("crucible_pylons", cruciblePylons);

        root.add("essence_infuser", createCompleteInfuserJson(InfuserBalanceSettings.defaults()));
        root.add("ascendance_shield", createShieldJson(ShieldBalanceSettings.defaults()));
        root.add(
                "latent_ore_worldgen",
                createLatentOreWorldgenJson(LatentOreWorldgenSettings.defaults(), true)
        );

        root.addProperty("preset", preset.id().toString());

        JsonObject tierCaps = new JsonObject();
        for (Map.Entry<ResourceLocation, Long> entry : preset.defaultTierCaps().entrySet()) {
            tierCaps.addProperty(entry.getKey().toString(), entry.getValue());
        }
        root.add("tier_cap_overrides", tierCaps);

        JsonObject statCaps = new JsonObject();
        for (StatDefinition stat : EssenceStatRegistry.values()) {
            JsonObject perTier = new JsonObject();
            for (var tier : AscendanceTierRegistry.values()) {
                perTier.addProperty(
                        tier.id().toString(),
                        preset.getInvestmentCap(tier, stat)
                );
            }
            statCaps.add(stat.id().toString(), perTier);
        }
        root.add("stat_cap_overrides", statCaps);

        JsonObject statMaxBonuses = new JsonObject();
        for (Map.Entry<ResourceLocation, Double> entry : StatScalingDefaults.values().entrySet()) {
            statMaxBonuses.addProperty(entry.getKey().toString(), entry.getValue());
        }
        root.add("stat_max_bonus_overrides", statMaxBonuses);

        root.add(
                "equipment_baseline_overrides",
                createCompleteEquipmentBaselineJson(EquipmentBaselineDefaults.create(preset))
        );

        root.addProperty(
                "_ascendance_requirements_comment",
                "Ascension requirements are global config, not datapacks. The generated template materializes the current built-in milestone and transition definitions."
        );
        root.add("milestone_overrides", createCompleteMilestoneJson());
        root.add("advancement_overrides", createCompleteAdvancementJson());
        return root;
    }

    private static JsonObject createCompleteInfuserJson(
            InfuserBalanceSettings settings
    ) {
        JsonObject infuser = new JsonObject();
        infuser.addProperty(
                "_comment",
                "Global Essence Infuser values. Efficiency is basis points (5000 = 50%). Infusion throughput is Essence work/second and is independent from carrier density."
        );
        infuser.addProperty("link_range", settings.linkRange());

        JsonObject noFocus = new JsonObject();
        noFocus.addProperty(
                "efficiency_basis_points",
                settings.noFocusEfficiencyBasisPoints()
        );
        noFocus.addProperty(
                "infusion_throughput_per_second",
                settings.noFocusInfusionThroughputPerSecond()
        );
        infuser.add("no_focus", noFocus);

        JsonObject grades = new JsonObject();
        for (String gradeName : new String[]{
                "dormant", "awakened", "resonant", "ascendant", "transcendent"
        }) {
            InfuserBalanceSettings.GradeSettings grade = settings.grade(gradeName);
            JsonObject gradeObject = new JsonObject();
            gradeObject.addProperty("ingot_capacity", grade.ingotCapacity());
            gradeObject.addProperty(
                    "efficiency_basis_points",
                    grade.efficiencyBasisPoints()
            );
            gradeObject.addProperty(
                    "infusion_throughput_per_second",
                    grade.infusionThroughputPerSecond()
            );
            grades.add(gradeName, gradeObject);
        }
        infuser.add("grades", grades);

        JsonObject focusUpgrades = new JsonObject();
        focusUpgrades.addProperty(
                "_comment",
                "minimum_per_attribute_essence is required from EACH of the six core Essences; the remainder of total_essence_required is flexible."
        );
        for (String targetTier : new String[]{
                "dormant", "awakened", "resonant", "ascendant", "transcendent"
        }) {
            InfuserBalanceSettings.FocusUpgradeSettings focus =
                    settings.focusUpgrade(targetTier);
            JsonObject focusObject = new JsonObject();
            focusObject.addProperty(
                    "minimum_per_attribute_essence",
                    focus.minimumPerAttributeEssence()
            );
            focusObject.addProperty(
                    "total_essence_required",
                    focus.totalEssenceRequired()
            );
            focusUpgrades.add(targetTier, focusObject);
        }
        infuser.add("focus_upgrades", focusUpgrades);

        JsonObject equipmentUpgrades = new JsonObject();
        equipmentUpgrades.addProperty(
                "_comment",
                "Equipment upgrades stream exact thematic Essence requirements. total_essence_required is divided by the per-equipment weights below; matrix_count is consumed atomically on completion."
        );
        for (String targetTier : new String[]{
                "dormant", "awakened", "resonant", "ascendant", "transcendent"
        }) {
            InfuserBalanceSettings.EquipmentUpgradeSettings upgrade =
                    settings.equipmentUpgrade(targetTier);
            JsonObject upgradeObject = new JsonObject();
            upgradeObject.addProperty("total_essence_required", upgrade.totalEssenceRequired());
            upgradeObject.addProperty("matrix_count", upgrade.matrixCount());
            equipmentUpgrades.add(targetTier, upgradeObject);
        }
        infuser.add("equipment_upgrades", equipmentUpgrades);

        JsonObject equipmentWeights = new JsonObject();
        equipmentWeights.addProperty(
                "_comment",
                "Positive relative weights for the six core Essences."
        );
        for (Map.Entry<String, Map<String, Integer>> equipment :
                settings.equipmentEssenceWeights().entrySet()) {
            JsonObject weightObject = new JsonObject();
            for (Map.Entry<String, Integer> weight : equipment.getValue().entrySet()) {
                weightObject.addProperty(weight.getKey(), weight.getValue());
            }
            equipmentWeights.add(equipment.getKey(), weightObject);
        }
        infuser.add("equipment_essence_weights", equipmentWeights);

        JsonObject repair = new JsonObject();
        repair.addProperty(
                "_comment",
                "Repair consumes any one selected enabled Essence. essence_per_durability scales with missing durability; Fractured artifacts additionally consume fractured_latent_ingot_count Latent Ingots."
        );
        repair.addProperty(
                "essence_per_durability",
                settings.repair().essencePerDurability()
        );
        repair.addProperty(
                "fractured_latent_ingot_count",
                settings.repair().fracturedLatentIngotCount()
        );
        infuser.add("repair", repair);
        return infuser;
    }

    private static JsonObject createCompleteEquipmentBaselineJson(
            EquipmentBaselineConfig config
    ) {
        JsonObject root = new JsonObject();
        root.addProperty(
                "_comment",
                "Complete effective equipment chassis values for the selected preset."
        );
        JsonObject tiers = new JsonObject();
        for (Map.Entry<ResourceLocation, EquipmentBaselineConfig.TierBaseline> entry
                : config.tierBaselines().entrySet()) {
            EquipmentBaselineConfig.TierBaseline value = entry.getValue();
            JsonObject tier = new JsonObject();
            tier.addProperty("full_set_armor", value.fullSetArmor());
            tier.addProperty("full_set_toughness", value.fullSetToughness());
            tier.addProperty("melee_damage", value.meleeDamage());
            tier.addProperty("melee_attack_speed", value.meleeAttackSpeed());
            tier.addProperty("ranged_damage", value.rangedDamage());
            tier.addProperty("ranged_attack_speed", value.rangedAttackSpeed());
            tier.addProperty("magic_damage", value.magicDamage());
            tier.addProperty("magic_cast_speed", value.magicCastSpeed());
            tier.addProperty("mining_speed", value.miningSpeed());
            tier.addProperty("harvest_level", value.harvestLevel());
            tier.addProperty("durability", value.durability());
            tiers.add(entry.getKey().toString(), tier);
        }
        root.add("tiers", tiers);
        return root;
    }

    private static JsonObject createCompleteMilestoneJson() {
        JsonObject root = new JsonObject();
        for (MilestoneDefinition milestone : MilestoneRegistry.values()) {
            JsonObject object = new JsonObject();
            object.addProperty("display_name", milestone.displayName());
            object.addProperty("provider", milestone.providerId().toString());
            object.addProperty("target", milestone.target());
            root.add(milestone.id().toString(), object);
        }
        return root;
    }

    private static JsonObject createCompleteAdvancementJson() {
        JsonObject root = new JsonObject();
        for (AscendanceAdvancementDefinition advancement
                : AscendanceAdvancementRegistry.values()) {
            JsonObject object = new JsonObject();
            object.addProperty("from_tier", advancement.fromTierId().toString());
            object.addProperty("to_tier", advancement.toTierId().toString());
            object.addProperty(
                    "total_investment_multiplier",
                    advancement.totalInvestmentMultiplier()
            );
            object.addProperty(
                    "minimum_developed_stats",
                    advancement.minimumDevelopedStats()
            );
            object.addProperty(
                    "minimum_represented_categories",
                    advancement.minimumRepresentedCategories()
            );
            object.addProperty(
                    "developed_stat_threshold",
                    advancement.developedStatThreshold()
            );
            object.add(
                    "world_requirement",
                    createRequirementJson(advancement.worldRequirement())
            );
            root.add(advancement.id().toString(), object);
        }
        return root;
    }

    private static JsonElement createRequirementJson(
            MilestoneRequirement requirement
    ) {
        if (requirement instanceof MilestoneRequirement.Milestone milestone) {
            return GSON.toJsonTree(milestone.milestoneId().toString());
        }
        if (requirement instanceof MilestoneRequirement.AllOf allOf) {
            JsonObject object = new JsonObject();
            object.addProperty("type", "all_of");
            JsonArray children = new JsonArray();
            for (MilestoneRequirement child : allOf.children()) {
                children.add(createRequirementJson(child));
            }
            object.add("children", children);
            return object;
        }
        if (requirement instanceof MilestoneRequirement.AnyOf anyOf) {
            JsonObject object = new JsonObject();
            object.addProperty("type", "any_of");
            JsonArray children = new JsonArray();
            for (MilestoneRequirement child : anyOf.children()) {
                children.add(createRequirementJson(child));
            }
            object.add("children", children);
            return object;
        }

        JsonObject object = new JsonObject();
        object.addProperty("type", "always");
        return object;
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

        InfuserBalanceSettings infuserBalance =
                parseInfuserBalance(root);

        LatentOreWorldgenSettings latentOreWorldgen =
                parseLatentOreWorldgen(root);

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
                pylonRadius,
                maxActivePylons,
                infuserBalance,
                parseShieldBalance(root),
                latentOreWorldgen,
                balanceProfile,
                milestones,
                advancements,
                statMaxBonuses,
                equipmentBaselineConfig
        );
    }


    private static JsonObject createShieldJson(ShieldBalanceSettings settings) {
        JsonObject result = new JsonObject();
        result.addProperty("_comment", "Durability, innate reflection, and block amplification follow the shield item's completed tier. Reflection investment uses the lower of holder tier and equipment tier. Latent amplification is always 1. Investments use stat_max_bonus_overrides.");
        result.addProperty("base_reflection_percent", settings.baseReflectionPercent());
        result.addProperty("minimum_disable_ticks", settings.minimumDisableTicks());
        JsonObject durability = new JsonObject();
        JsonObject reflectionBonus = new JsonObject();
        JsonObject amplification = new JsonObject();
        for (com.mistaboom.essence_ascendance.equipment.EquipmentTier tier :
                com.mistaboom.essence_ascendance.equipment.EquipmentTier.values()) {
            durability.addProperty(tier.serializedName(), settings.durability().get(tier));
            reflectionBonus.addProperty(tier.serializedName(), settings.innateReflectionBonus().get(tier));
            if (tier != com.mistaboom.essence_ascendance.equipment.EquipmentTier.LATENT) {
                amplification.addProperty(tier.serializedName(), settings.blockAmplification().get(tier));
            }
        }
        result.add("durability", durability);
        result.add("innate_reflection_bonus", reflectionBonus);
        result.add("block_amplification", amplification);
        return result;
    }

    private static ShieldBalanceSettings parseShieldBalance(JsonObject root) {
        ShieldBalanceSettings defaults = ShieldBalanceSettings.defaults();
        if (!root.has("ascendance_shield") || root.get("ascendance_shield").isJsonNull()) return defaults;
        if (!root.get("ascendance_shield").isJsonObject()) {
            throw new IllegalArgumentException("ascendance_shield must be an object");
        }
        JsonObject shield = root.getAsJsonObject("ascendance_shield");
        JsonObject durability = shield.has("durability") ? shield.getAsJsonObject("durability") : new JsonObject();
        JsonObject reflectionBonus = shield.has("innate_reflection_bonus") ? shield.getAsJsonObject("innate_reflection_bonus") : new JsonObject();
        JsonObject amplification = shield.has("block_amplification") ? shield.getAsJsonObject("block_amplification") : new JsonObject();
        var points = new java.util.EnumMap<com.mistaboom.essence_ascendance.equipment.EquipmentTier, Integer>(com.mistaboom.essence_ascendance.equipment.EquipmentTier.class);
        var innateBonuses = new java.util.EnumMap<com.mistaboom.essence_ascendance.equipment.EquipmentTier, Double>(com.mistaboom.essence_ascendance.equipment.EquipmentTier.class);
        var multipliers = new java.util.EnumMap<com.mistaboom.essence_ascendance.equipment.EquipmentTier, Double>(com.mistaboom.essence_ascendance.equipment.EquipmentTier.class);
        for (var tier : com.mistaboom.essence_ascendance.equipment.EquipmentTier.values()) {
            points.put(tier, readInt(durability, tier.serializedName(), defaults.durability().get(tier)));
            innateBonuses.put(tier, readDouble(reflectionBonus, tier.serializedName(), defaults.innateReflectionBonus().get(tier)));
            multipliers.put(tier, readDouble(amplification, tier.serializedName(), defaults.blockAmplification().get(tier)));
        }
        return new ShieldBalanceSettings(
                readDouble(shield, "base_reflection_percent", defaults.baseReflectionPercent()),
                readInt(shield, "minimum_disable_ticks", defaults.minimumDisableTicks()),
                points, innateBonuses, multipliers);
    }

    private static LatentOreWorldgenSettings parseLatentOreWorldgen(
            JsonObject root
    ) {
        LatentOreWorldgenSettings defaults =
                LatentOreWorldgenSettings.defaults();

        JsonElement element = root.get("latent_ore_worldgen");
        if (element == null || element.isJsonNull()) {
            return defaults;
        }
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException("latent_ore_worldgen must be an object");
        }

        JsonObject object = element.getAsJsonObject();
        return new LatentOreWorldgenSettings(
                parseLatentOreDimension(
                        object,
                        "overworld",
                        defaults.overworld()
                ),
                parseLatentOreDimension(
                        object,
                        "nether",
                        defaults.nether()
                ),
                parseLatentOreDimension(
                        object,
                        "end",
                        defaults.end()
                ),
                parseCustomLatentOreDimensions(object)
        );
    }

    private static Map<String, LatentOreWorldgenSettings.CustomDimensionSettings>
    parseCustomLatentOreDimensions(
            JsonObject worldgen
    ) {
        Map<String, LatentOreWorldgenSettings.CustomDimensionSettings> rules =
                new LinkedHashMap<>();

        JsonElement customElement = worldgen.get("custom_dimensions");
        if (customElement == null || customElement.isJsonNull()) {
            return rules;
        }
        if (!customElement.isJsonObject()) {
            throw new IllegalArgumentException(
                    "latent_ore_worldgen.custom_dimensions must be an object"
            );
        }

        for (Map.Entry<String, JsonElement> entry
                : customElement.getAsJsonObject().entrySet()) {
            String ruleName = entry.getKey();
            if (ruleName.startsWith("_")) {
                continue;
            }
            if (!entry.getValue().isJsonObject()) {
                throw new IllegalArgumentException(
                        "latent_ore_worldgen.custom_dimensions." + ruleName + " must be an object"
                );
            }
            rules.put(
                    ruleName,
                    parseCustomLatentOreDimension(
                            ruleName,
                            entry.getValue().getAsJsonObject()
                    )
            );
        }
        return rules;
    }

    private static LatentOreWorldgenSettings.CustomDimensionSettings
    parseCustomLatentOreDimension(
            String ruleName,
            JsonObject object
    ) {
        ResourceLocation dimension = null;
        ResourceLocation dimensionTypeTag = null;

        String dimensionRaw = readNullableString(object, "dimension");
        if (dimensionRaw != null) {
            dimension = ResourceLocation.tryParse(dimensionRaw);
            if (dimension == null) {
                throw new IllegalArgumentException(
                        "Invalid dimension id in custom Latent Ore rule '" + ruleName + "': " + dimensionRaw
                );
            }
        }

        String dimensionTypeTagRaw = readNullableString(object, "dimension_type_tag");
        if (dimensionTypeTagRaw != null) {
            if (dimensionTypeTagRaw.startsWith("#")) {
                dimensionTypeTagRaw = dimensionTypeTagRaw.substring(1);
            }
            dimensionTypeTag = ResourceLocation.tryParse(dimensionTypeTagRaw);
            if (dimensionTypeTag == null) {
                throw new IllegalArgumentException(
                        "Invalid dimension_type_tag in custom Latent Ore rule '"
                                + ruleName + "': " + dimensionTypeTagRaw
                );
            }
        }

        String replacementRaw = readString(object, "replace", "minecraft:stone");
        boolean replacementIsTag = replacementRaw.startsWith("#");
        String replacementIdRaw = replacementIsTag
                ? replacementRaw.substring(1)
                : replacementRaw;
        ResourceLocation replacementId = ResourceLocation.tryParse(replacementIdRaw);
        if (replacementId == null) {
            throw new IllegalArgumentException(
                    "Invalid replace target in custom Latent Ore rule '"
                            + ruleName + "': " + replacementRaw
            );
        }

        ResourceLocation oreBlock = null;
        String oreBlockRaw = readNullableString(object, "ore_block");
        if (oreBlockRaw != null) {
            oreBlock = ResourceLocation.tryParse(oreBlockRaw);
            if (oreBlock == null) {
                throw new IllegalArgumentException(
                        "Invalid ore_block in custom Latent Ore rule '"
                                + ruleName + "': " + oreBlockRaw
                );
            }
        }

        // ore_block is the advanced output override. When it is present, an
        // unused/missing ore_variant must not be able to invalidate the rule.
        LatentOreWorldgenSettings.OreVariant oreVariant = oreBlock != null
                ? LatentOreWorldgenSettings.OreVariant.STONE
                : LatentOreWorldgenSettings.OreVariant.parse(
                        readString(object, "ore_variant", "stone")
                );

        LatentOreWorldgenSettings.DimensionSettings distribution;
        try {
            distribution = new LatentOreWorldgenSettings.DimensionSettings(
                    readBoolean(object, "enabled", true),
                    readInt(object, "vein_size", 4),
                    readInt(object, "veins_per_chunk", 2),
                    readInt(object, "min_y", -64),
                    readInt(object, "max_y", 64),
                    readNonNegativeFiniteDouble(
                            object,
                            "discard_chance_on_air_exposure",
                            0.0D
                    )
            );
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Invalid distribution in custom Latent Ore rule '"
                            + ruleName + "': " + exception.getMessage(),
                    exception
            );
        }

        try {
            return new LatentOreWorldgenSettings.CustomDimensionSettings(
                    dimension,
                    dimensionTypeTag,
                    new LatentOreWorldgenSettings.ReplacementTarget(
                            replacementId,
                            replacementIsTag
                    ),
                    oreVariant,
                    oreBlock,
                    distribution
            );
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Invalid custom Latent Ore rule '" + ruleName + "': "
                            + exception.getMessage(),
                    exception
            );
        }
    }

    private static LatentOreWorldgenSettings.DimensionSettings parseLatentOreDimension(
            JsonObject worldgen,
            String dimensionName,
            LatentOreWorldgenSettings.DimensionSettings defaults
    ) {
        JsonElement dimensionElement = worldgen.get(dimensionName);
        if (dimensionElement == null || dimensionElement.isJsonNull()) {
            return defaults;
        }
        if (!dimensionElement.isJsonObject()) {
            throw new IllegalArgumentException(
                    "latent_ore_worldgen." + dimensionName + " must be an object"
            );
        }
        JsonObject object = dimensionElement.getAsJsonObject();

        try {
            return new LatentOreWorldgenSettings.DimensionSettings(
                    readBoolean(
                            object,
                            "enabled",
                            defaults.enabled()
                    ),
                    readInt(
                            object,
                            "vein_size",
                            defaults.veinSize()
                    ),
                    readInt(
                            object,
                            "veins_per_chunk",
                            defaults.veinsPerChunk()
                    ),
                    readInt(
                            object,
                            "min_y",
                            defaults.minY()
                    ),
                    readInt(
                            object,
                            "max_y",
                            defaults.maxY()
                    ),
                    readNonNegativeFiniteDouble(
                            object,
                            "discard_chance_on_air_exposure",
                            defaults.discardChanceOnAirExposure()
                    )
            );
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Invalid latent_ore_worldgen."
                            + dimensionName
                            + ": "
                            + exception.getMessage(),
                    exception
            );
        }
    }

    private static String readNullableString(
            JsonObject object,
            String key
    ) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(key + " must be a string");
        }
        String value = element.getAsString().trim();
        return value.isEmpty() ? null : value;
    }

    private static InfuserBalanceSettings parseInfuserBalance(
            JsonObject root
    ) {
        InfuserBalanceSettings defaults =
                InfuserBalanceSettings.defaults();

        JsonElement element = root.get("essence_infuser");
        if (element == null || element.isJsonNull()) {
            return defaults;
        }
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException("essence_infuser must be an object");
        }

        JsonObject object = element.getAsJsonObject();
        double linkRange = readNonNegativeFiniteDouble(
                object,
                "link_range",
                defaults.linkRange()
        );
        if (!(linkRange > 0.0D) || linkRange > 64.0D) {
            throw new IllegalArgumentException(
                    "essence_infuser.link_range must be greater than 0 and no more than 64"
            );
        }

        int noFocusEfficiency = defaults.noFocusEfficiencyBasisPoints();
        Long configuredNoFocusThroughput = null;
        Integer legacyNoFocusTicks = null;
        JsonObject noFocus = getObject(object, "no_focus");
        if (noFocus != null) {
            noFocusEfficiency = readInt(
                    noFocus,
                    "efficiency_basis_points",
                    noFocusEfficiency
            );
            if (noFocus.has("infusion_throughput_per_second")) {
                configuredNoFocusThroughput = readLong(
                        noFocus,
                        "infusion_throughput_per_second",
                        defaults.noFocusInfusionThroughputPerSecond()
                );
            } else if (noFocus.has("processing_ticks")) {
                legacyNoFocusTicks = readInt(
                        noFocus,
                        "processing_ticks",
                        200
                );
            }
        }

        Map<String, InfuserBalanceSettings.GradeSettings> grades =
                new LinkedHashMap<>(defaults.grades());
        JsonObject gradeObject = getObject(object, "grades");
        if (gradeObject != null) {
            for (String gradeName : new String[]{
                    "dormant", "awakened", "resonant", "ascendant", "transcendent"
            }) {
                JsonObject configured = getObject(gradeObject, gradeName);
                if (configured == null) {
                    continue;
                }
                InfuserBalanceSettings.GradeSettings fallback =
                        grades.get(gradeName);
                long ingotCapacity = normalizeEssentiumIngotCapacity(
                        readLong(
                                configured,
                                "ingot_capacity",
                                fallback.ingotCapacity()
                        ),
                        "essence_infuser.grades." + gradeName + ".ingot_capacity"
                );
                int efficiency = readInt(
                        configured,
                        "efficiency_basis_points",
                        fallback.efficiencyBasisPoints()
                );
                long throughput;
                if (configured.has("infusion_throughput_per_second")) {
                    throughput = readLong(
                            configured,
                            "infusion_throughput_per_second",
                            fallback.infusionThroughputPerSecond()
                    );
                } else if (configured.has("processing_ticks")) {
                    throughput = legacyInfusionThroughput(
                            ingotCapacity,
                            readInt(configured, "processing_ticks", 1),
                            fallback.infusionThroughputPerSecond()
                    );
                } else {
                    throughput = fallback.infusionThroughputPerSecond();
                }
                grades.put(
                        gradeName,
                        new InfuserBalanceSettings.GradeSettings(
                                ingotCapacity,
                                efficiency,
                                throughput
                        )
                );
            }
        }

        long noFocusThroughput = configuredNoFocusThroughput != null
                ? configuredNoFocusThroughput
                : legacyNoFocusTicks != null
                ? legacyInfusionThroughput(
                        grades.get("dormant").ingotCapacity(),
                        legacyNoFocusTicks,
                        defaults.noFocusInfusionThroughputPerSecond()
                )
                : defaults.noFocusInfusionThroughputPerSecond();

        Map<String, InfuserBalanceSettings.FocusUpgradeSettings> focusUpgrades =
                new LinkedHashMap<>(defaults.focusUpgrades());
        JsonObject focusUpgradeObject = getObject(object, "focus_upgrades");
        if (focusUpgradeObject != null) {
            for (String targetTier : new String[]{
                    "dormant", "awakened", "resonant", "ascendant", "transcendent"
            }) {
                JsonObject configured = getObject(focusUpgradeObject, targetTier);
                if (configured == null) {
                    continue;
                }
                InfuserBalanceSettings.FocusUpgradeSettings fallback =
                        focusUpgrades.get(targetTier);
                focusUpgrades.put(
                        targetTier,
                        new InfuserBalanceSettings.FocusUpgradeSettings(
                                readLong(
                                        configured,
                                        "minimum_per_attribute_essence",
                                        fallback.minimumPerAttributeEssence()
                                ),
                                readLong(
                                        configured,
                                        "total_essence_required",
                                        fallback.totalEssenceRequired()
                                )
                        )
                );
            }
        }

        Map<String, InfuserBalanceSettings.EquipmentUpgradeSettings> equipmentUpgrades =
                new LinkedHashMap<>(defaults.equipmentUpgrades());
        JsonObject equipmentUpgradeObject = getObject(object, "equipment_upgrades");
        if (equipmentUpgradeObject != null) {
            for (String targetTier : new String[]{
                    "dormant", "awakened", "resonant", "ascendant", "transcendent"
            }) {
                JsonObject configured = getObject(equipmentUpgradeObject, targetTier);
                if (configured == null) continue;
                InfuserBalanceSettings.EquipmentUpgradeSettings fallback =
                        equipmentUpgrades.get(targetTier);
                equipmentUpgrades.put(
                        targetTier,
                        new InfuserBalanceSettings.EquipmentUpgradeSettings(
                                readLong(configured, "total_essence_required", fallback.totalEssenceRequired()),
                                readInt(configured, "matrix_count", fallback.matrixCount())
                        )
                );
            }
        }

        Map<String, Map<String, Integer>> equipmentWeights = new LinkedHashMap<>();
        defaults.equipmentEssenceWeights().forEach((key, value) ->
                equipmentWeights.put(key, new LinkedHashMap<>(value)));
        JsonObject equipmentWeightObject = getObject(object, "equipment_essence_weights");
        if (equipmentWeightObject != null) {
            for (String equipmentKey : defaults.equipmentEssenceWeights().keySet()) {
                JsonObject configured = getObject(equipmentWeightObject, equipmentKey);
                // Backward-compatible read for sparse configs made before the
                // magic weapon was renamed Ascendance Caster. New templates
                // always emit the canonical magic_caster key.
                if (configured == null && equipmentKey.equals("magic_caster")) {
                    configured = getObject(equipmentWeightObject, "magic_weapon");
                }
                if (configured == null) continue;
                LinkedHashMap<String, Integer> configuredWeights = new LinkedHashMap<>();
                for (String essenceKey : new String[]{
                        "offense", "defense", "vitality", "mobility", "gathering", "utility"
                }) {
                    if (configured.has(essenceKey)) {
                        int weight = readInt(configured, essenceKey, 0);
                        if (weight <= 0) {
                            throw new IllegalArgumentException(
                                    "essence_infuser.equipment_essence_weights." + equipmentKey
                                            + "." + essenceKey + " must be positive"
                            );
                        }
                        configuredWeights.put(essenceKey, weight);
                    }
                }
                if (!configuredWeights.isEmpty()) {
                    equipmentWeights.put(equipmentKey, configuredWeights);
                }
            }
        }

        InfuserBalanceSettings.RepairSettings repair = defaults.repair();
        JsonObject repairObject = getObject(object, "repair");
        if (repairObject != null) {
            repair = new InfuserBalanceSettings.RepairSettings(
                    readLong(
                            repairObject,
                            "essence_per_durability",
                            repair.essencePerDurability()
                    ),
                    readInt(
                            repairObject,
                            "fractured_latent_ingot_count",
                            repair.fracturedLatentIngotCount()
                    )
            );
        }

        return new InfuserBalanceSettings(
                linkRange,
                noFocusEfficiency,
                noFocusThroughput,
                grades,
                focusUpgrades,
                equipmentUpgrades,
                equipmentWeights,
                repair
        );
    }


    /**
     * Backward compatibility for config versions that expressed Infuser speed
     * as flat ticks per ingot. Round the derived throughput up so the migrated
     * ingot never becomes slower than the old configured duration.
     */
    /**
     * Essentium Nugget is exactly one ninth of an Ingot. Older explicit
     * configurations may contain capacities from before nugget support that
     * are not divisible by nine. Normalize those values upward by at most
     * eight Essence. Rounding upward keeps already-created legacy carriers
     * valid while making all newly created nugget/ingot conversions exact.
     */
    private static long normalizeEssentiumIngotCapacity(long configured, String path) {
        if (configured <= 0L) {
            return configured;
        }
        long remainder = configured % 9L;
        long normalized;
        try {
            normalized = remainder == 0L
                    ? configured
                    : Math.addExact(configured, 9L - remainder);
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException(path + " is too large to normalize for nugget conversion", overflow);
        }
        if (normalized != configured) {
            EssenceAscendance.LOGGER.warn(
                    "Normalized {} from {} to {} so Essentium Nugget conversion remains exact",
                    path,
                    configured,
                    normalized
            );
        }
        return normalized;
    }

    private static long legacyInfusionThroughput(
            long ingotCapacity,
            int processingTicks,
            long fallback
    ) {
        if (ingotCapacity <= 0L || processingTicks <= 0) {
            return fallback;
        }
        long whole = ingotCapacity / processingTicks;
        long remainder = ingotCapacity % processingTicks;
        if (whole > Long.MAX_VALUE / 20L) {
            return Long.MAX_VALUE;
        }
        long numeratorWhole = whole * 20L;
        long numeratorRemainder = remainder * 20L;
        long extra = numeratorRemainder / processingTicks;
        if (numeratorRemainder % processingTicks != 0L) {
            extra++;
        }
        if (numeratorWhole > Long.MAX_VALUE - extra) {
            return Long.MAX_VALUE;
        }
        return Math.max(1L, numeratorWhole + extra);
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
                6.0D,
                8,
                InfuserBalanceSettings.defaults(),
                ShieldBalanceSettings.defaults(),
                LatentOreWorldgenSettings.defaults(),
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
