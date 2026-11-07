package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mistaboom.essence_ascendance.achievement.AchievementPresentation;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.advancements.AdvancementTab;
import net.minecraft.client.gui.screens.advancements.AdvancementWidget;
import net.minecraft.network.chat.FormattedText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** 1.21 truncates titles before sizing the tooltip; 26 already wraps the complete title. */
@Mixin(AdvancementWidget.class)
public abstract class AchievementTitleMixin {
    @ModifyExpressionValue(method = "<init>", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Font;substrByWidth(Lnet/minecraft/network/chat/FormattedText;I)Lnet/minecraft/network/chat/FormattedText;"))
    private FormattedText essenceAscendance$completeSelectedTitle(FormattedText nativeTitle,
            AdvancementTab tab, Minecraft minecraft, AdvancementNode node, DisplayInfo display) {
        // Keep native visual ordering, width calculation, description wrapping and tooltip rendering.
        return AchievementPresentation.selectedIds().contains(node.holder().id())
                ? display.getTitle() : nativeTitle;
    }
}
