package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mistaboom.essence_ascendance.achievement.AchievementPresentation;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.client.gui.screens.advancements.AdvancementTab;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Name the mod's tab without changing the root achievement's tooltip or toast. */
@Mixin(AdvancementTab.class)
public abstract class AchievementTabTitleMixin {
    @Shadow public abstract AdvancementNode getRootNode();

    @ModifyReturnValue(method = "getTitle", at = @At("RETURN"))
    private Component essenceAscendance$tabTitle(Component nativeTitle) {
        return AchievementPresentation.ROOT.equals(getRootNode().holder().id())
                ? Component.translatable("gui.essence_ascendance.achievements.title") : nativeTitle;
    }
}
