package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mistaboom.essence_ascendance.equipment.AnvilMenuCostView;
import com.mistaboom.essence_ascendance.visual.AscendancePalette;
//? if >=26.1.2
/*import net.minecraft.client.gui.GuiGraphicsExtractor;*/
//? if <26.1.2
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.ItemCombinerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps the anvil label on the last server-authoritative cost, including zero. */
@Mixin(AnvilScreen.class)
public abstract class AnvilScreenCostMixin extends ItemCombinerScreen<AnvilMenu> {
    @Unique private static final int LABEL_COLOR = AscendancePalette.UTILITY;
    @Unique private static final int LABEL_BACKGROUND = 0x4F000000;

    protected AnvilScreenCostMixin(AnvilMenu menu, Inventory inventory, Component title,
            ResourceLocation menuResource) {
        super(menu, inventory, title, menuResource);
    }

    @WrapOperation(
            //? if >=26.1.2
            /*method = "extractLabels",*/
            //? if <26.1.2
            method = "renderLabels",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/AnvilMenu;getCost()I"), require = 1)
    private int essenceAscendance$useSynchronizedCost(AnvilMenu anvilMenu, Operation<Integer> original) {
        int synchronizedCost = ((AnvilMenuCostView) anvilMenu).essenceAscendance$displayCost();
        return synchronizedCost >= 0 ? synchronizedCost : original.call(anvilMenu);
    }

    @Inject(
            //? if >=26.1.2
            /*method = "extractLabels",*/
            //? if <26.1.2
            method = "renderLabels",
            at = @At("RETURN"), require = 1)
    private void essenceAscendance$renderFreeCost(
            //? if >=26.1.2
            /*GuiGraphicsExtractor graphics,*/
            //? if <26.1.2
            GuiGraphics graphics,
            int mouseX, int mouseY, CallbackInfo ci) {
        int synchronizedCost = ((AnvilMenuCostView) menu).essenceAscendance$displayCost();
        if (synchronizedCost != 0 || !menu.getSlot(AnvilMenu.RESULT_SLOT).hasItem()) return;
        Component label = Component.translatable("container.repair.cost", 0);
        int right = imageWidth - 8;
        int labelX = right - font.width(label) - 2;
        graphics.fill(labelX - 2, 67, right, 79, LABEL_BACKGROUND);
        //? if >=26.1.2
        /*graphics.text(font, label, labelX, 69, LABEL_COLOR);*/
        //? if <26.1.2
        graphics.drawString(font, label, labelX, 69, LABEL_COLOR);
    }
}
