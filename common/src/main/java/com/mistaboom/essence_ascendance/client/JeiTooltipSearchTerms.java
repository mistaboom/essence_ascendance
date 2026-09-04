package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.infuser.EssentiumCarrierData;
import com.mistaboom.essence_ascendance.essence.EssenceFamily;
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

        if (EssentiumCarrierData.isEssentium(stack)) {
            EssentiumCarrierData.read(stack)
                    .filter(JeiTooltipSearchTerms::isCarrierEssenceVisible)
                    .ifPresent(value -> {
                result.addAll(
                        ItemEssenceTooltipClientState.getDirectSearchTerms(
                                value.essence().id(),
                                value.amount()
                        )
                );
                result.add("tier");
                result.add("infusion");
                result.add("grade");
                result.add(value.grade().displayName().toLowerCase(java.util.Locale.ROOT));
            });
        } else {
            result.addAll(
                    ItemEssenceTooltipClientState.getSearchTerms(
                            stack
                    )
            );
        }

        return Set.copyOf(
                result
        );
    }

    private static boolean isCarrierEssenceVisible(EssentiumCarrierData.Value value) {
        if (value.essence().family() != EssenceFamily.SKILL) {
            return true;
        }
        return ClientEssenceState.ready()
                && ClientEssenceState.snapshot().availableEssence().containsKey(value.essence().id());
    }
}
