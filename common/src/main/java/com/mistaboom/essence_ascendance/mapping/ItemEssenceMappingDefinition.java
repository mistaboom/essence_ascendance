package com.mistaboom.essence_ascendance.mapping;

import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.Objects;

/*
 * One datapack-defined item -> Attribute Essence mapping rule.
 *
 * Exactly one selector is active:
 * - an explicit Item, or
 * - an Item tag.
 */
public record ItemEssenceMappingDefinition(
        ResourceLocation id,
        int priority,
        SelectorType selectorType,
        ResourceLocation selectorId,
        Item explicitItem,
        TagKey<Item> itemTag,
        Map<EssenceDefinition, Long> outputs
) {

    public ItemEssenceMappingDefinition {
        Objects.requireNonNull(id, "Mapping ID cannot be null");
        Objects.requireNonNull(selectorType, "Selector type cannot be null");
        Objects.requireNonNull(selectorId, "Selector ID cannot be null");
        Objects.requireNonNull(outputs, "Mapping outputs cannot be null");

        outputs = Map.copyOf(outputs);

        switch (selectorType) {
            case ITEM -> {
                Objects.requireNonNull(
                        explicitItem,
                        "Explicit-item mapping requires an Item"
                );

                if (itemTag != null) {
                    throw new IllegalArgumentException(
                            "Explicit-item mapping cannot also contain an item tag"
                    );
                }
            }

            case TAG -> {
                Objects.requireNonNull(
                        itemTag,
                        "Tag mapping requires an item TagKey"
                );

                if (explicitItem != null) {
                    throw new IllegalArgumentException(
                            "Tag mapping cannot also contain an explicit Item"
                    );
                }
            }
        }
    }

    public boolean matches(
            ItemStack stack
    ) {
        if (stack == null
                || stack.isEmpty()) {
            return false;
        }

        return switch (selectorType) {
            case ITEM ->
                    stack.getItem()
                            == explicitItem;

            case TAG ->
                    stack.is(
                            itemTag
                    );
        };
    }

    public String selectorDisplay() {
        return switch (selectorType) {
            case ITEM -> selectorId.toString();
            case TAG -> "#" + selectorId;
        };
    }

    public enum SelectorType {
        ITEM,
        TAG
    }
}
