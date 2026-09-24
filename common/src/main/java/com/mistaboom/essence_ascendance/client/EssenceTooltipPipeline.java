package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.equipment.EquipmentTier;
import com.mistaboom.essence_ascendance.infuser.EssentiumCarrierData;
import com.mistaboom.essence_ascendance.text.EssenceText;
import com.mistaboom.essence_ascendance.visual.AscendancePalette;
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
 * 2. append item-local Masterwork Tempering state when present;
 * 3. append the compact item -> Essence acquisition line.
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

        MasterworkTemperingTooltip.append(stack, tooltip);

        if (EssentiumCarrierData.isEssentium(stack)) {
            EssentiumCarrierData.readValidated(stack)
                    .filter(value -> value.amount() <= Long.MAX_VALUE
                            / com.mistaboom.essence_ascendance.balance.economy.FractionalAmountService.SCALE)
                    .ifPresent(value -> {
                tooltip.add(
                        Component.literal(" ")
                                .append(EssenceText.tooltip("tier", EssenceText.focusTier(value.grade())))
                                .withStyle(style -> style
                                        .withColor(AscendancePalette.tierMetalRgb(
                                                EquipmentTier.fromSerializedName(value.grade().serializedName())
                                        ))
                                        .withBold(true))
                );
                ItemEssenceTooltipClientState.appendDirectEssenceTooltipMicros(
                        value.essence().id(),
                        EssentiumCarrierData.extractionYieldMicroUnits(value),
                        tooltip
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

}
