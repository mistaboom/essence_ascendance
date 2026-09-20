package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.client.ClientCommittedSkills;
import com.mistaboom.essence_ascendance.equipment.EquipmentMaintenanceService;
import com.mistaboom.essence_ascendance.gathering.GatheringSalvageService;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.GrindstoneScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the explicit Salvager's Craft recycle action to the vanilla grindstone screen. */
@Mixin(AbstractContainerScreen.class)
public abstract class GatheringSalvageGrindstoneScreenMixin extends Screen {
    @Shadow protected int imageWidth;
    @Shadow protected int leftPos;
    @Shadow protected int topPos;
    @Shadow @Final protected AbstractContainerMenu menu;

    @Unique private Button essenceAscendance$salvageButton;

    protected GatheringSalvageGrindstoneScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void essenceAscendance$addSalvageButton(CallbackInfo ci) {
        if (!((Object) this instanceof GrindstoneScreen)) return;
        Button button = Button.builder(Component.literal("\u267B"), ignored -> {
                    if (minecraft != null && minecraft.gameMode != null) {
                        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, GatheringSalvageService.BUTTON_RECYCLE);
                    }
                })
                .bounds(leftPos + imageWidth - 22, topPos + 6, 18, 18)
                .build();
        button.setTooltip(Tooltip.create(Component.translatable(
                "gui.essence_ascendance.grindstone.recycle.tooltip")));
        essenceAscendance$salvageButton = addRenderableWidget(button);
        essenceAscendance$updateSalvageButton();
    }

    @Inject(method = "containerTick", at = @At("TAIL"))
    private void essenceAscendance$tickSalvageButton(CallbackInfo ci) {
        if (essenceAscendance$salvageButton != null) essenceAscendance$updateSalvageButton();
    }

    @Unique
    private void essenceAscendance$updateSalvageButton() {
        if (essenceAscendance$salvageButton == null) return;
        boolean effective = ClientCommittedSkills.isEffective(SkillIds.SALVAGERS_CRAFT);
        essenceAscendance$salvageButton.visible = effective;
        essenceAscendance$salvageButton.active = effective && menu.slots.size() > 1
                && (EquipmentMaintenanceService.eligible(menu.getSlot(0).getItem())
                || EquipmentMaintenanceService.eligible(menu.getSlot(1).getItem()));
    }
}
