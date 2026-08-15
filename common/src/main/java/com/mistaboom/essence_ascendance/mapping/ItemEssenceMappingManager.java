package com.mistaboom.essence_ascendance.mapping;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mistaboom.essence_ascendance.EssenceAscendance;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.platform.Platform;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/*
 * Loads item mappings from exactly two places:
 *
 * 1. Bundled defaults inside the Essence Ascendance mod JAR:
 *      data/essence_ascendance/essence_mappings/*.json
 *
 *    These files are read directly from the physical mod resource path.
 *    They are NOT loaded through Minecraft's datapack ResourceManager.
 *
 * 2. Global instance/server config:
 *      config/essence_ascendance/item_mappings/
 *
 * No world folder and no user datapack is required.
 */
public final class ItemEssenceMappingManager {

    private static final Gson GSON =
            new GsonBuilder()
                    .disableHtmlEscaping()
                    .setPrettyPrinting()
                    .create();

    private static final String DEFAULT_DIRECTORY =
            "essence_mappings";

    /*
     * Do not discover the bundled directory itself through a loader-specific
     * mod container. Architectury dev runs can place common-module resources on
     * the runtime classpath without exposing that common resource root as one
     * of the platform mod container's physical roots.
     *
     * Looking up one exact marker resource is reliable in exploded dev
     * classpaths and packaged JARs. From that marker we enumerate its sibling
     * JSON files.
     */
    private static final String DEFAULT_RESOURCE_PREFIX =
            "data/"
                    + EssenceAscendance.MOD_ID
                    + "/"
                    + DEFAULT_DIRECTORY;

    private static final String DEFAULT_ROOT_MARKER =
            DEFAULT_RESOURCE_PREFIX
                    + "/_root.marker";

    private static final String SETTINGS_FILE =
            "_settings.json";

    private static final String README_FILE =
            "README.txt";

    private static boolean initialized =
            false;

    private ItemEssenceMappingManager() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }

        ensureConfigScaffold();

        /*
         * Explicit item validation needs the final game/mod item registries.
         * SERVER_STARTED is late enough on both Fabric and NeoForge and also
         * keeps the mapping system server-authoritative.
         */
        LifecycleEvent.SERVER_STARTED.register(
                server ->
                        reload()
        );

        initialized =
                true;
    }

    public static Path configDirectory() {
        return Platform.getConfigFolder()
                .resolve(
                        EssenceAscendance.MOD_ID
                )
                .resolve(
                        "item_mappings"
                );
    }

    public static Path settingsPath() {
        return configDirectory()
                .resolve(
                        SETTINGS_FILE
                );
    }

    public static synchronized ItemEssenceMappingRegistry.ReloadReport reload() {
        ensureConfigScaffold();

        List<String> errors =
                new ArrayList<>();

        List<String> warnings =
                new ArrayList<>();

        Map<ResourceLocation, ItemEssenceMappingDefinition> bundledDefaults =
                loadBundledDefaults(
                        errors
                );

        ConfigSettings settings =
                loadSettings(
                        errors
                );

        Map<ResourceLocation, ItemEssenceMappingDefinition> configMappings =
                loadConfigMappings(
                        errors
                );

        int bundledDefaultCount =
                bundledDefaults.size();

        int configFileCount =
                countConfigMappingFiles();

        int removedDefaultCount =
                0;

        int replacedDefaultCount =
                0;

        /*
         * A bad config never partially replaces the active generation.
         */
        if (!errors.isEmpty()) {
            ItemEssenceMappingRegistry.LoadSummary summary =
                    new ItemEssenceMappingRegistry.LoadSummary(
                            bundledDefaultCount,
                            0,
                            0,
                            configFileCount,
                            configMappings.size(),
                            warnings
                    );

            ItemEssenceMappingRegistry.rejectReload(
                    summary,
                    errors
            );

            return ItemEssenceMappingRegistry.lastReload();
        }

        Map<ResourceLocation, ItemEssenceMappingDefinition> merged =
                new LinkedHashMap<>();

        if (!settings.removeAllDefaults()) {
            merged.putAll(
                    bundledDefaults
            );
        } else {
            removedDefaultCount +=
                    bundledDefaults.size();
        }

        if (!settings.removeAllDefaults()) {
            for (ResourceLocation removal :
                    settings.removeDefaults()) {

                if (merged.remove(
                        removal
                ) != null) {
                    removedDefaultCount++;

                } else if (!bundledDefaults.containsKey(
                        removal
                )) {
                    warnings.add(
                            "remove_defaults references no bundled default mapping: "
                                    + removal
                    );
                }
            }
        }

        /*
         * Config mapping IDs are authoritative over bundled IDs.
         *
         * Therefore a config mapping with:
         *
         *   "id": "essence_ascendance:offense_iron_sword"
         *
         * is a true one-for-one replacement of that shipped default. No
         * priority trick or zero-value placeholder is necessary.
         */
        for (Map.Entry<ResourceLocation, ItemEssenceMappingDefinition> entry :
                configMappings.entrySet()) {

            ResourceLocation id =
                    entry.getKey();

            if (bundledDefaults.containsKey(
                    id
            )) {
                if (merged.remove(
                        id
                ) != null) {
                    replacedDefaultCount++;

                } else if (settings.removeAllDefaults()
                        || settings.removeDefaults()
                                .contains(
                                        id
                                )) {
                    /*
                     * Still count this as replacement intent even when settings
                     * removed the default before the replacement was applied.
                     */
                    replacedDefaultCount++;
                }
            }

            merged.put(
                    id,
                    entry.getValue()
            );
        }

        ItemEssenceMappingRegistry.LoadSummary summary =
                new ItemEssenceMappingRegistry.LoadSummary(
                        bundledDefaultCount,
                        removedDefaultCount,
                        replacedDefaultCount,
                        configFileCount,
                        configMappings.size(),
                        warnings
                );

        ItemEssenceMappingRegistry.install(
                List.copyOf(
                        merged.values()
                ),
                summary
        );

        return ItemEssenceMappingRegistry.lastReload();
    }

    private static Map<ResourceLocation, ItemEssenceMappingDefinition> loadBundledDefaults(
            List<String> errors
    ) {
        Map<ResourceLocation, ItemEssenceMappingDefinition> result =
                new LinkedHashMap<>();

        int discoveredRoots =
                0;

        /*
         * Primary path: resolve a known marker through the runtime classloader.
         *
         * This intentionally does NOT use Platform.getMod(...).findResource().
         * In a multi-project Architectury development run, common resources can
         * exist on the runtime classpath without belonging to the Fabric/
         * NeoForge mod container's reported resource roots.
         */
        try {
            ClassLoader loader =
                    EssenceAscendance.class
                            .getClassLoader();

            Enumeration<URL> markers =
                    loader.getResources(
                            DEFAULT_ROOT_MARKER
                    );

            while (markers.hasMoreElements()) {
                URL marker =
                        markers.nextElement();

                discoveredRoots++;

                loadBundledRoot(
                        marker,
                        result,
                        errors
                );
            }

        } catch (IOException exception) {
            errors.add(
                    "Unable to discover bundled mapping resources: "
                            + message(
                                    exception
                            )
            );
        }

        if (discoveredRoots == 0) {
            errors.add(
                    "Bundled mapping resources are missing from the runtime classpath: "
                            + DEFAULT_RESOURCE_PREFIX
                            + " (marker "
                            + DEFAULT_ROOT_MARKER
                            + " was not found)"
            );
        }

        return result;
    }


    private static void loadBundledRoot(
            URL marker,
            Map<ResourceLocation, ItemEssenceMappingDefinition> result,
            List<String> errors
    ) {
        String protocol =
                marker.getProtocol();

        /*
         * First try the URL as a mounted NIO filesystem path.
         *
         * This covers normal file: development roots and also loader-provided
         * filesystem schemes (for example the mounted resource filesystems used
         * by modern Forge-like launchers) without hard-coding their protocol.
         */
        try {
            Path markerPath =
                    Path.of(
                            marker.toURI()
                    );

            Path root =
                    markerPath.getParent();

            if (root != null
                    && Files.isDirectory(
                            root
                    )) {

                loadBundledDirectory(
                        root,
                        result,
                        errors
                );

                return;
            }

        } catch (RuntimeException
                 | URISyntaxException ignored) {
            /*
             * Not every URL scheme has an installed NIO provider. Fall through
             * to the standard JAR URL path below.
             */
        }

        try {
            if ("jar".equalsIgnoreCase(
                    protocol
            )) {
                URLConnection rawConnection =
                        marker.openConnection();

                if (!(rawConnection instanceof JarURLConnection connection)) {
                    errors.add(
                            "Unsupported JAR URL connection for bundled mappings: "
                                    + marker
                    );

                    return;
                }

                /*
                 * Do not cache the JarURLConnection. This loader can be invoked
                 * repeatedly through /essence admin mappings reload.
                 */
                connection.setUseCaches(
                        false
                );

                try (JarFile jar =
                             connection.getJarFile()) {

                    loadBundledJar(
                            jar,
                            result,
                            errors
                    );
                }

                return;
            }

            errors.add(
                    "Bundled mapping marker resolved through unsupported resource protocol '"
                            + protocol
                            + "' and could not be exposed as an NIO path: "
                            + marker
            );

        } catch (IOException exception) {

            errors.add(
                    "Unable to read bundled mapping root "
                            + marker
                            + ": "
                            + message(
                                    exception
                            )
            );
        }
    }


    private static void loadBundledDirectory(
            Path root,
            Map<ResourceLocation, ItemEssenceMappingDefinition> result,
            List<String> errors
    ) {
        if (root == null
                || !Files.isDirectory(
                        root
                )) {

            errors.add(
                    "Bundled mapping marker resolved without a readable sibling directory: "
                            + root
            );

            return;
        }

        for (Path file :
                jsonFiles(
                        root,
                        errors,
                        "bundled defaults"
                )) {

            String relative =
                    normalizeRelative(
                            root,
                            file
                    );

            ResourceLocation mappingId =
                    idFromRelativePath(
                            EssenceAscendance.MOD_ID,
                            relative,
                            false
                    );

            if (mappingId == null) {
                errors.add(
                        "Bundled default mapping path cannot become a ResourceLocation: "
                                + relative
                );

                continue;
            }

            try {
                installBundledDefinition(
                        relative,
                        mappingId,
                        ItemEssenceMappingJson.parseDefault(
                                mappingId,
                                readJson(
                                        file
                                )
                        ),
                        result,
                        errors
                );

            } catch (RuntimeException
                     | IOException exception) {

                errors.add(
                        "Bundled "
                                + relative
                                + ": "
                                + message(
                                        exception
                                )
                );
            }
        }
    }


    private static void loadBundledJar(
            JarFile jar,
            Map<ResourceLocation, ItemEssenceMappingDefinition> result,
            List<String> errors
    ) {
        String prefix =
                DEFAULT_RESOURCE_PREFIX
                        + "/";

        Enumeration<JarEntry> entries =
                jar.entries();

        while (entries.hasMoreElements()) {
            JarEntry entry =
                    entries.nextElement();

            String name =
                    entry.getName();

            if (entry.isDirectory()
                    || !name.startsWith(
                            prefix
                    )
                    || !name.toLowerCase(
                                    java.util.Locale.ROOT
                            )
                            .endsWith(
                                    ".json"
                            )) {
                continue;
            }

            String relative =
                    name.substring(
                            prefix.length()
                    );

            ResourceLocation mappingId =
                    idFromRelativePath(
                            EssenceAscendance.MOD_ID,
                            relative,
                            false
                    );

            if (mappingId == null) {
                errors.add(
                        "Bundled default mapping path cannot become a ResourceLocation: "
                                + relative
                );

                continue;
            }

            try (Reader reader =
                         new InputStreamReader(
                                 jar.getInputStream(
                                         entry
                                 ),
                                 StandardCharsets.UTF_8
                         )) {

                installBundledDefinition(
                        relative,
                        mappingId,
                        ItemEssenceMappingJson.parseDefault(
                                mappingId,
                                readJson(
                                        reader
                                )
                        ),
                        result,
                        errors
                );

            } catch (RuntimeException
                     | IOException exception) {

                errors.add(
                        "Bundled "
                                + relative
                                + ": "
                                + message(
                                        exception
                                )
                );
            }
        }
    }


    private static void installBundledDefinition(
            String relative,
            ResourceLocation mappingId,
            ItemEssenceMappingDefinition definition,
            Map<ResourceLocation, ItemEssenceMappingDefinition> result,
            List<String> errors
    ) {
        ItemEssenceMappingDefinition previous =
                result.putIfAbsent(
                        mappingId,
                        definition
                );

        if (previous != null
                && !previous.equals(
                        definition
                )) {

            errors.add(
                    "Duplicate bundled mapping ID with different definitions: "
                            + mappingId
                            + " (latest resource "
                            + relative
                            + ")"
            );
        }
    }


    private static ConfigSettings loadSettings(
            List<String> errors
    ) {
        Path path =
                settingsPath();

        if (!Files.isRegularFile(
                path
        )) {
            return ConfigSettings.defaults();
        }

        try {
            JsonElement element =
                    readJson(
                            path
                    );

            if (!element.isJsonObject()) {
                throw new IllegalArgumentException(
                        "root must be a JSON object"
                );
            }

            JsonObject root =
                    element.getAsJsonObject();

            Set<String> allowed =
                    Set.of(
                            "remove_all_defaults",
                            "remove_defaults"
                    );

            for (String key :
                    root.keySet()) {

                if (!allowed.contains(
                        key
                )) {
                    throw new IllegalArgumentException(
                            "unknown field '"
                                    + key
                                    + "'"
                    );
                }
            }

            boolean removeAll =
                    root.has(
                            "remove_all_defaults"
                    )
                            && root.get(
                                    "remove_all_defaults"
                            )
                            .getAsBoolean();

            Set<ResourceLocation> removeDefaults =
                    new LinkedHashSet<>();

            if (root.has(
                    "remove_defaults"
            )) {
                JsonElement removals =
                        root.get(
                                "remove_defaults"
                        );

                if (!removals.isJsonArray()) {
                    throw new IllegalArgumentException(
                            "'remove_defaults' must be an array of mapping IDs"
                    );
                }

                for (JsonElement removal :
                        removals.getAsJsonArray()) {

                    ResourceLocation id =
                            ItemEssenceMappingJson.readResourceLocation(
                                    removal,
                                    "remove_defaults[]"
                            );

                    removeDefaults.add(
                            id
                    );
                }
            }

            return new ConfigSettings(
                    removeAll,
                    Set.copyOf(
                            removeDefaults
                    )
            );

        } catch (RuntimeException
                 | IOException exception) {

            errors.add(
                    SETTINGS_FILE
                            + ": "
                            + message(
                                    exception
                            )
            );

            return ConfigSettings.defaults();
        }
    }

    private static Map<ResourceLocation, ItemEssenceMappingDefinition> loadConfigMappings(
            List<String> errors
    ) {
        Path root =
                configDirectory();

        Map<ResourceLocation, ItemEssenceMappingDefinition> result =
                new LinkedHashMap<>();

        for (Path file :
                jsonFiles(
                        root,
                        errors,
                        "config mappings"
                )) {

            if (file.getFileName()
                    .toString()
                    .equalsIgnoreCase(
                            SETTINGS_FILE
                    )) {
                continue;
            }

            String relative =
                    normalizeRelative(
                            root,
                            file
                    );

            ResourceLocation fallbackId =
                    idFromRelativePath(
                            EssenceAscendance.MOD_ID,
                            relative,
                            true
                    );

            if (fallbackId == null) {
                errors.add(
                        relative
                                + ": file path cannot become a default config mapping ID; "
                                + "rename the file/folders or add an explicit valid 'id' field"
                );

                continue;
            }

            try {
                ItemEssenceMappingDefinition definition =
                        ItemEssenceMappingJson.parseConfig(
                                fallbackId,
                                readJson(
                                        file
                                )
                        );

                ItemEssenceMappingDefinition previous =
                        result.putIfAbsent(
                                definition.id(),
                                definition
                        );

                if (previous != null) {
                    errors.add(
                            relative
                                    + ": duplicate config mapping ID "
                                    + definition.id()
                    );
                }

            } catch (RuntimeException
                     | IOException exception) {

                errors.add(
                        relative
                                + ": "
                                + message(
                                        exception
                                )
                );
            }
        }

        return result;
    }

    private static List<Path> jsonFiles(
            Path root,
            List<String> errors,
            String label
    ) {
        if (!Files.exists(
                root
        )) {
            return List.of();
        }

        try (Stream<Path> stream =
                     Files.walk(
                             root
                     )) {

            return stream
                    .filter(
                            Files::isRegularFile
                    )
                    .filter(
                            path ->
                                    path.getFileName()
                                            .toString()
                                            .toLowerCase(
                                                    java.util.Locale.ROOT
                                            )
                                            .endsWith(
                                                    ".json"
                                            )
                    )
                    .sorted(
                            Comparator.comparing(
                                    Path::toString
                            )
                    )
                    .toList();

        } catch (IOException exception) {
            errors.add(
                    "Unable to enumerate "
                            + label
                            + " at "
                            + root
                            + ": "
                            + message(
                                    exception
                            )
            );

            return List.of();
        }
    }

    private static int countConfigMappingFiles() {
        List<String> ignoredErrors =
                new ArrayList<>();

        int count =
                0;

        for (Path file :
                jsonFiles(
                        configDirectory(),
                        ignoredErrors,
                        "config mappings"
                )) {

            if (!file.getFileName()
                    .toString()
                    .equalsIgnoreCase(
                            SETTINGS_FILE
                    )) {
                count++;
            }
        }

        return count;
    }

    private static JsonElement readJson(
            Path path
    ) throws IOException {
        try (Reader reader =
                     Files.newBufferedReader(
                             path,
                             StandardCharsets.UTF_8
                     )) {

            return readJson(
                    reader
            );
        }
    }


    private static JsonElement readJson(
            Reader reader
    ) {
        JsonElement element =
                GSON.fromJson(
                        reader,
                        JsonElement.class
                );

        if (element == null) {
            throw new IllegalArgumentException(
                    "file is empty"
            );
        }

        return element;
    }

    private static ResourceLocation idFromRelativePath(
            String namespace,
            String relative,
            boolean config
    ) {
        String path =
                relative;

        if (path.toLowerCase(
                        java.util.Locale.ROOT
                )
                .endsWith(
                        ".json"
                )) {
            path =
                    path.substring(
                            0,
                            path.length()
                                    - ".json".length()
                    );
        }

        if (config) {
            path =
                    "config/"
                            + path;
        }

        return ResourceLocation.tryParse(
                namespace
                        + ":"
                        + path
        );
    }

    private static String normalizeRelative(
            Path root,
            Path file
    ) {
        return root.relativize(
                        file
                )
                .toString()
                .replace(
                        '\\',
                        '/'
                );
    }

    private static void ensureConfigScaffold() {
        Path directory =
                configDirectory();

        try {
            Files.createDirectories(
                    directory
            );

            Path settings =
                    directory.resolve(
                            SETTINGS_FILE
                    );

            if (!Files.exists(
                    settings
            )) {
                Files.writeString(
                        settings,
                        """
                                {
                                  "remove_all_defaults": false,
                                  "remove_defaults": []
                                }
                                """,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE_NEW
                );
            }

            Path readme =
                    directory.resolve(
                            README_FILE
                    );

            if (!Files.exists(
                    readme
            )) {
                Files.writeString(
                        readme,
                        README_TEXT,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE_NEW
                );
            }

        } catch (IOException exception) {
            EssenceAscendance.LOGGER.error(
                    "Unable to create Essence item-mapping config directory {}",
                    directory,
                    exception
            );
        }
    }

    private static String message(
            Throwable throwable
    ) {
        String message =
                throwable.getMessage();

        return message == null
                || message.isBlank()
                ? throwable.getClass()
                        .getSimpleName()
                : message;
    }

    private record ConfigSettings(
            boolean removeAllDefaults,
            Set<ResourceLocation> removeDefaults
    ) {
        private ConfigSettings {
            removeDefaults =
                    Set.copyOf(
                            removeDefaults
                    );
        }

        private static ConfigSettings defaults() {
            return new ConfigSettings(
                    false,
                    Set.of()
            );
        }
    }

    private static final String README_TEXT =
            """
            ESSENCE ASCENDANCE - ITEM -> ATTRIBUTE ESSENCE MAPPINGS
            =======================================================

            This directory is global to the Minecraft instance/server.
            It is NOT world-specific and does NOT use datapacks.

            SETTINGS
            --------
            _settings.json controls shipped defaults:

            {
              "remove_all_defaults": false,
              "remove_defaults": [
                "essence_ascendance:offense_iron_sword"
              ]
            }

            remove_all_defaults=true removes every mapping shipped by the mod.
            Config mapping files are still loaded afterward.

            remove_defaults removes only the listed shipped mapping IDs.

            ONE-FOR-ONE REPLACEMENT
            -----------------------
            A config mapping whose explicit "id" equals a shipped default ID
            replaces that default directly.

            Example:

            {
              "id": "essence_ascendance:offense_iron_sword",
              "priority": 0,
              "item": "minecraft:iron_sword",
              "outputs": {
                "essence_ascendance:offense": 20
              }
            }

            ADDING NEW / THIRD-PARTY ITEMS
            ------------------------------
            Add any other *.json file here. The same item/tag mapping schema is
            used. If "id" is omitted, an ID is derived from the config file path.

            Example:

            {
              "priority": 25,
              "item": "some_mod:some_weapon",
              "outputs": {
                "essence_ascendance:offense": 40,
                "essence_ascendance:utility": 5
              }
            }

            Tag selectors are also supported:

            {
              "priority": 10,
              "tag": "some_mod:weapons",
              "outputs": {
                "essence_ascendance:offense": 20
              }
            }

            PRIORITY
            --------
            For a given ItemStack, only the highest matching priority applies.
            All rules tied at that priority merge additively.

            An empty outputs object at the winning priority blocks lower rules.

            RELOAD
            ------
            /essence admin mappings reload

            INSPECT
            -------
            /essence admin mappings
            /essence admin mappings list
            /essence debug mapping
            """;
}
