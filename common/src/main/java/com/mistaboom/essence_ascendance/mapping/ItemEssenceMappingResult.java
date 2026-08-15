package com.mistaboom.essence_ascendance.mapping;

import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;

/*
 * Immutable result returned by the item mapping registry.
 *
 * matchedMappings includes every rule whose selector matched.
 * appliedMappings contains only rules at the winning priority.
 */
public record ItemEssenceMappingResult(
        ResourceLocation itemId,
        List<ResourceLocation> matchedMappings,
        List<ResourceLocation> appliedMappings,
        Integer appliedPriority,
        Map<EssenceDefinition, Long> outputs
) {

    public ItemEssenceMappingResult {
        Objects.requireNonNull(itemId, "Item ID cannot be null");
        Objects.requireNonNull(matchedMappings, "Matched mapping list cannot be null");
        Objects.requireNonNull(appliedMappings, "Applied mapping list cannot be null");
        Objects.requireNonNull(outputs, "Resolved outputs cannot be null");

        matchedMappings = List.copyOf(matchedMappings);
        appliedMappings = List.copyOf(appliedMappings);
        outputs = Map.copyOf(outputs);

        if (matchedMappings.isEmpty()) {
            if (!appliedMappings.isEmpty()
                    || appliedPriority != null
                    || !outputs.isEmpty()) {
                throw new IllegalArgumentException(
                        "Unmapped item result cannot contain applied mappings or outputs"
                );
            }
        } else {
            if (appliedMappings.isEmpty()
                    || appliedPriority == null) {
                throw new IllegalArgumentException(
                        "Mapped item result requires applied mappings and priority"
                );
            }
        }
    }

    public boolean mapped() {
        return !matchedMappings.isEmpty();
    }

    public OptionalInt priority() {
        return appliedPriority == null
                ? OptionalInt.empty()
                : OptionalInt.of(
                        appliedPriority
                );
    }

    public long amountFor(
            EssenceDefinition essence
    ) {
        return outputs.getOrDefault(
                essence,
                0L
        );
    }
}
