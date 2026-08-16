package com.mistaboom.essence_ascendance.client;

import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashSet;
import java.util.Set;

/*
 * JEI-specific view of the exact dynamic tooltip words owned by Essence
 * Ascendance.
 *
 * Kept separate from JEI classes so both loader mixins can call one common
 * source and JEI remains optional.
 */
public final class JeiTooltipSearchTerms {

    private JeiTooltipSearchTerms() {
    }

    public static Set<String> forStack(
            ItemStack stack
    ) {
        if (stack == null
                || stack.isEmpty()) {
            return Set.of();
        }

        Set<String> result =
                new LinkedHashSet<>();

        result.addAll(
                EquipmentTooltipClientState.getSearchTerms(
                        stack
                )
        );

        result.addAll(
                ItemEssenceTooltipClientState.getSearchTerms(
                        stack
                )
        );

        return Set.copyOf(
                result
        );
    }
}
