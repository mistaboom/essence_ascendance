package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mistaboom.essence_ascendance.equipment.EnchantingMenuCostView;
import net.minecraft.client.gui.Font;
//? if >=26.1.2
/*import net.minecraft.client.gui.GuiGraphicsExtractor;*/
//? if <26.1.2
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.world.inventory.EnchantmentMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds each synchronized committed level cost beside its vanilla requirement. */
@Mixin(EnchantmentScreen.class)
public abstract class EnchantmentScreenCostMixin {
    @Unique private int essenceAscendance$renderedOffer;

    @Inject(
            //? if >=26.1.2
            /*method = "extractBackground",*/
            //? if <26.1.2
            method = "renderBg",
            at = @At("HEAD"), require = 1)
    private void essenceAscendance$resetRenderedOffer(
            //? if >=26.1.2
            /*GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick,*/
            //? if <26.1.2
            GuiGraphics graphics, float partialTick, int mouseX, int mouseY,
            CallbackInfo ci) {
        essenceAscendance$renderedOffer = 0;
    }

    @WrapOperation(
            //? if >=26.1.2
            /*method = "extractBackground",*/
            //? if <26.1.2
            method = "renderBg",
            at = @At(value = "INVOKE",
                    //? if >=26.1.2
                    /*target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V"),*/
                    //? if <26.1.2
                    target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)I"),
            require = 1)
    //? if >=26.1.2
    /*private void essenceAscendance$drawEfficientCost(GuiGraphicsExtractor graphics, Font font,*/
    //? if <26.1.2
    private int essenceAscendance$drawEfficientCost(GuiGraphics graphics, Font font,
            String requirementLabel, int x, int y, int color,
            //? if >=26.1.2
            /*Operation<Void> original) {*/
            //? if <26.1.2
            Operation<Integer> original) {
        String displayLabel = essenceAscendance$efficientCostLabel(requirementLabel);
        int right = x + font.width(requirementLabel);
        //? if >=26.1.2
        /*original.call(graphics, font, displayLabel, right - font.width(displayLabel), y, color);*/
        //? if <26.1.2
        return original.call(graphics, font, displayLabel, right - font.width(displayLabel), y, color);
    }

    @Unique
    private String essenceAscendance$efficientCostLabel(String requirementLabel) {
        EnchantmentMenu menu = ((EnchantmentScreen) (Object) this).getMenu();
        while (essenceAscendance$renderedOffer < menu.costs.length
                && menu.costs[essenceAscendance$renderedOffer] <= 0) essenceAscendance$renderedOffer++;
        int offer = essenceAscendance$renderedOffer++;
        if (offer < 0 || offer >= menu.costs.length) return requirementLabel;
        int vanillaSpent = offer + 1;
        int efficientSpent = ((EnchantingMenuCostView) menu).essenceAscendance$experienceCost(offer);
        // Left value remains the power-level requirement; right is spent.
        return efficientSpent >= vanillaSpent ? requirementLabel : requirementLabel + " → " + efficientSpent;
    }
}
