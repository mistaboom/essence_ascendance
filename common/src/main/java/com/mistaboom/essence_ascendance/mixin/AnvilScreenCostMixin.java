package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.equipment.AnvilMenuCostView;
import com.mistaboom.essence_ascendance.visual.AscendancePalette;
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
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps the anvil label on the last server-authoritative cost, including zero. */
@Mixin(AnvilScreen.class)
public abstract class AnvilScreenCostMixin
        extends ItemCombinerScreen<AnvilMenu> {

    @Unique
    private static final int LABEL_COLOR = AscendancePalette.UTILITY;

    @Unique
    private static final int LABEL_BACKGROUND = 0x4F000000;

    protected AnvilScreenCostMixin(
            AnvilMenu menu,
            Inventory inventory,
            Component title,
            ResourceLocation menuResource
    ) {
        super(menu, inventory, title, menuResource);
    }

    @Redirect(
            method = "renderLabels(Lnet/minecraft/client/gui/GuiGraphics;II)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/inventory/AnvilMenu;getCost()I"
            )
    )
    private int essenceAscendance$useSynchronizedCost(AnvilMenu anvilMenu) {
        int synchronizedCost = ((AnvilMenuCostView) anvilMenu)
                .essenceAscendance$displayCost();
        return synchronizedCost >= 0
                ? synchronizedCost
                : anvilMenu.getCost();
    }

    @Inject(
            method = "renderLabels(Lnet/minecraft/client/gui/GuiGraphics;II)V",
            at = @At("RETURN")
    )
    private void essenceAscendance$renderFreeCost(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            CallbackInfo ci
    ) {
        int synchronizedCost = ((AnvilMenuCostView) menu)
                .essenceAscendance$displayCost();
        if (synchronizedCost != 0
                || !menu.getSlot(AnvilMenu.RESULT_SLOT).hasItem()) {
            return;
        }

        Component label = Component.translatable("container.repair.cost", 0);
        int right = imageWidth - 8;
        int labelX = right - font.width(label) - 2;
        graphics.fill(labelX - 2, 67, right, 79, LABEL_BACKGROUND);
        graphics.drawString(font, label, labelX, 69, LABEL_COLOR);
    }
}
