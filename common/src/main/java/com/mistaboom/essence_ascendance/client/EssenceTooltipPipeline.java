package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.infuser.EssentiumCarrierData;
import com.mistaboom.essence_ascendance.infuser.EquipmentInfusionData;
import com.mistaboom.essence_ascendance.essence.EssenceFamily;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/*
 * Single client-side pipeline for every Essence Ascendance tooltip addition.
 *
 * This runs at ItemStack#getTooltipLines RETURN rather than only at a screen
 * tooltip event. Consumers such as JEI's tooltip-search index call
 * ItemStack#getTooltipLines directly, so putting the final text here makes the
 * searchable tooltip and the visible tooltip use the same source of truth.
 *
 * Order intentionally preserves the already-approved visual layout:
 * 1. organize Ascendance equipment tooltip;
 * 2. append the compact item -> Essence acquisition line.
 */
public final class EssenceTooltipPipeline {

    private EssenceTooltipPipeline() {
    }

    public static void apply(
            ItemStack stack,
            List<Component> tooltip,
            Item.TooltipContext tooltipContext,
            TooltipFlag tooltipFlag
    ) {
        if (stack == null
                || stack.isEmpty()
                || tooltip == null) {
            return;
        }

        EquipmentTooltipClientState.applyToGeneratedTooltip(
                stack,
                tooltip,
                tooltipContext,
                tooltipFlag
        );

        EquipmentInfusionData.appendTooltip(stack, tooltip);

        if (EssentiumCarrierData.isEssentium(stack)) {
            EssentiumCarrierData.read(stack)
                    .filter(EssenceTooltipPipeline::isCarrierEssenceVisible)
                    .ifPresent(value -> {
                ItemEssenceTooltipClientState.appendDirectEssenceTooltip(
                        value.essence().id(),
                        value.amount(),
                        tooltip
                );
                tooltip.add(
                        Component.literal(
                                        "Infusion Grade: "
                                                + value.grade().displayName()
                                )
                                .withStyle(ChatFormatting.DARK_GRAY)
                );
            });
            return;
        }

        ItemEssenceTooltipClientState.appendToGeneratedTooltip(
                stack,
                tooltip,
                tooltipContext,
                tooltipFlag
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
