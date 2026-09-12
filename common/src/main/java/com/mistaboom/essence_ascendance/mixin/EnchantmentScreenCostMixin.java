package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.equipment.EnchantingMenuCostView;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.world.inventory.EnchantmentMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds each synchronized committed level cost beside its vanilla requirement. */
@Mixin(EnchantmentScreen.class)
public abstract class EnchantmentScreenCostMixin {

    @Unique
    private int essenceAscendance$renderedOffer;

    @Inject(
            method = "renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V",
            at = @At("HEAD")
    )
    private void essenceAscendance$resetRenderedOffer(
            GuiGraphics graphics,
            float partialTick,
            int mouseX,
            int mouseY,
            CallbackInfo ci
    ) {
        essenceAscendance$renderedOffer = 0;
    }

    @Redirect(
            method = "renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)I"
            )
    )
    private int essenceAscendance$drawEfficientCost(
            GuiGraphics graphics,
            Font font,
            String requirementLabel,
            int x,
            int y,
            int color
    ) {
        String displayLabel = essenceAscendance$efficientCostLabel(
                requirementLabel
        );
        int vanillaRightEdge = x + font.width(requirementLabel);
        int displayX = vanillaRightEdge - font.width(displayLabel);
        return graphics.drawString(font, displayLabel, displayX, y, color);
    }

    @Unique
    private String essenceAscendance$efficientCostLabel(
            String requirementLabel
    ) {
        EnchantmentMenu menu = ((EnchantmentScreen) (Object) this).getMenu();
        while (essenceAscendance$renderedOffer < menu.costs.length
                && menu.costs[essenceAscendance$renderedOffer] <= 0) {
            essenceAscendance$renderedOffer++;
        }

        int offer = essenceAscendance$renderedOffer++;
        if (offer < 0 || offer >= menu.costs.length) {
            return requirementLabel;
        }

        int vanillaSpent = offer + 1;
        int efficientSpent = ((EnchantingMenuCostView) menu)
                .essenceAscendance$experienceCost(offer);
        if (efficientSpent >= vanillaSpent) {
            return requirementLabel;
        }

        /* Left value remains the power-level requirement; right is spent. */
        return requirementLabel + " → " + efficientSpent;
    }
}
