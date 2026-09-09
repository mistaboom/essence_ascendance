package com.mistaboom.essence_ascendance.mapping;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import com.mistaboom.essence_ascendance.essence.EssenceRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/*
 * Strict schema/parser shared by:
 *
 * - the JSON defaults shipped inside the Essence Ascendance JAR; and
 * - config/essence_ascendance/item_mappings/*.json overrides/additions.
 *
 * Config mappings may additionally declare "id". Bundled defaults derive their
 * IDs from their file paths and therefore do not need an "id" field.
 */
final class ItemEssenceMappingJson {

    private static final Set<String> DEFAULT_FIELDS =
            Set.of(
                    "priority",
                    "item",
                    "tag",
                    "outputs"
            );

    private static final Set<String> CONFIG_FIELDS =
            Set.of(
                    "id",
                    "priority",
                    "item",
                    "tag",
                    "outputs"
            );

    private static final int MIN_PRIORITY =
            -1_000_000;

    private static final int MAX_PRIORITY =
            1_000_000;

    private static final long MAX_OUTPUT_AMOUNT =
            1_000_000_000_000L;

    private ItemEssenceMappingJson() {
    }

    static ItemEssenceMappingDefinition parseDefault(
            ResourceLocation mappingId,
            JsonElement element
    ) {
        return parseMapping(
                mappingId,
                element,
                false
        );
    }

    static ItemEssenceMappingDefinition parseConfig(
            ResourceLocation fallbackId,
            JsonElement element
    ) {
        if (element == null
                || !element.isJsonObject()) {
            throw new IllegalArgumentException(
                    "root must be a JSON object"
            );
        }

        JsonObject root =
                element.getAsJsonObject();

        ResourceLocation mappingId =
                root.has(
                        "id"
                )
                        ? readResourceLocation(
                                root.get(
                                        "id"
                                ),
                                "id"
                        )
                        : fallbackId;

        return parseMapping(
                mappingId,
                element,
                true
        );
    }

    private static ItemEssenceMappingDefinition parseMapping(
            ResourceLocation mappingId,
            JsonElement element,
            boolean configFile
    ) {
        if (element == null
                || !element.isJsonObject()) {
            throw new IllegalArgumentException(
                    "root must be a JSON object"
            );
        }

        JsonObject root =
                element.getAsJsonObject();

        Set<String> allowed =
                configFile
                        ? CONFIG_FIELDS
                        : DEFAULT_FIELDS;

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

        int priority =
                root.has(
                        "priority"
                )
                        ? readExactInt(
                                root.get(
                                        "priority"
                                ),
                                "priority"
                        )
                        : 0;

        if (priority < MIN_PRIORITY
                || priority > MAX_PRIORITY) {
            throw new IllegalArgumentException(
                    "priority must be between "
                            + MIN_PRIORITY
                            + " and "
                            + MAX_PRIORITY
            );
        }

        boolean hasItem =
                root.has(
                        "item"
                );

        boolean hasTag =
                root.has(
                        "tag"
                );

        if (hasItem
                == hasTag) {
            throw new IllegalArgumentException(
                    "exactly one selector is required: 'item' or 'tag'"
            );
        }

        JsonElement outputsElement =
                root.get(
                        "outputs"
                );

        if (outputsElement == null
                || !outputsElement.isJsonObject()) {
            throw new IllegalArgumentException(
                    "'outputs' must be a JSON object"
            );
        }

        Map<EssenceDefinition, Long> outputs =
                parseOutputs(
                        outputsElement.getAsJsonObject()
                );

        if (hasItem) {
            ResourceLocation itemId =
                    readResourceLocation(
                            root.get(
                                    "item"
                            ),
                            "item"
                    );

            Item item =
                    BuiltInRegistries.ITEM
                            .getOptional(
                                    itemId
                            )
                            .orElseThrow(
                                    () ->
                                            new IllegalArgumentException(
                                                    "unknown item '"
                                                            + itemId
                                                            + "'"
                                            )
                            );

            return new ItemEssenceMappingDefinition(
                    mappingId,
                    priority,
                    ItemEssenceMappingDefinition.SelectorType.ITEM,
                    itemId,
                    item,
                    null,
                    outputs
            );
        }

        ResourceLocation tagId =
                readResourceLocation(
                        root.get(
                                "tag"
                        ),
                        "tag"
                );

        return new ItemEssenceMappingDefinition(
                mappingId,
                priority,
                ItemEssenceMappingDefinition.SelectorType.TAG,
                tagId,
                null,
                TagKey.create(
                        Registries.ITEM,
                        tagId
                ),
                outputs
        );
    }

    private static Map<EssenceDefinition, Long> parseOutputs(
            JsonObject outputsObject
    ) {
        /*
         * Empty is valid. At a winning priority it deliberately blocks lower
         * priority mappings for the selected item/tag.
         */
        List<Map.Entry<String, JsonElement>> ordered =
                outputsObject.entrySet()
                        .stream()
                        .sorted(
                                Map.Entry.comparingByKey()
                        )
                        .toList();

        Map<EssenceDefinition, Long> outputs =
                new LinkedHashMap<>();

        for (Map.Entry<String, JsonElement> entry :
                ordered) {

            ResourceLocation essenceId =
                    ResourceLocation.tryParse(
                            entry.getKey()
                    );

            if (essenceId == null) {
                throw new IllegalArgumentException(
                        "invalid Essence ID in outputs: '"
                                + entry.getKey()
                                + "'"
                );
            }

            EssenceDefinition essence =
                    EssenceRegistry
                            .get(
                                    essenceId
                            )
                            .orElseThrow(
                                    () ->
                                            new IllegalArgumentException(
                                                    "unknown Essence in outputs: '"
                                                            + essenceId
                                                            + "'"
                                            )
                            );

            long amount =
                    readExactLong(
                            entry.getValue(),
                            "outputs."
                                    + essenceId
                    );

            if (amount <= 0L
                    || amount > MAX_OUTPUT_AMOUNT) {
                throw new IllegalArgumentException(
                        "output amount for "
                                + essenceId
                                + " must be between 1 and "
                                + MAX_OUTPUT_AMOUNT
                );
            }

            outputs.put(
                    essence,
                    amount
            );
        }

        return outputs;
    }

    static ResourceLocation readResourceLocation(
            JsonElement element,
            String field
    ) {
        if (element == null
                || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive()
                        .isString()) {
            throw new IllegalArgumentException(
                    "'"
                            + field
                            + "' must be a ResourceLocation string"
            );
        }

        String raw =
                element.getAsString();

        ResourceLocation id =
                ResourceLocation.tryParse(
                        raw
                );

        if (id == null) {
            throw new IllegalArgumentException(
                    "invalid ResourceLocation for '"
                            + field
                            + "': '"
                            + raw
                            + "'"
            );
        }

        return id;
    }

    private static int readExactInt(
            JsonElement element,
            String field
    ) {
        BigDecimal decimal =
                readNumber(
                        element,
                        field
                );

        try {
            return decimal.intValueExact();

        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "'"
                            + field
                            + "' must be an integer within the supported range"
            );
        }
    }

    private static long readExactLong(
            JsonElement element,
            String field
    ) {
        BigDecimal decimal =
                readNumber(
                        element,
                        field
                );

        try {
            return decimal.longValueExact();

        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "'"
                            + field
                            + "' must be an integer within the supported range"
            );
        }
    }

    private static BigDecimal readNumber(
            JsonElement element,
            String field
    ) {
        if (element == null
                || !element.isJsonPrimitive()) {
            throw new IllegalArgumentException(
                    "'"
                            + field
                            + "' must be numeric"
            );
        }

        JsonPrimitive primitive =
                element.getAsJsonPrimitive();

        if (!primitive.isNumber()) {
            throw new IllegalArgumentException(
                    "'"
                            + field
                            + "' must be numeric"
            );
        }

        try {
            return new BigDecimal(
                    primitive.getAsString()
            );

        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "'"
                            + field
                            + "' must be a finite integer"
            );
        }
    }
}
