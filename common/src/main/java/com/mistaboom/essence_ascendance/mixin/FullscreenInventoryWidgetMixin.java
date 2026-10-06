package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.client.ui.fullscreen.FullscreenSidebarCompatibility;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Apply the shared fullscreen policy after inventory widgets finish being injected. */
@Mixin(Screen.class)
public abstract class FullscreenInventoryWidgetMixin {
    @Shadow protected abstract void removeWidget(GuiEventListener widget);

    @Inject(method = {"init(Lnet/minecraft/client/Minecraft;II)V", "rebuildWidgets()V"}, at = @At("RETURN"))
    private void essenceAscendance$suppressInventoryWidgets(CallbackInfo ci) {
        FullscreenSidebarCompatibility.suppressInjectedWidgets((Screen)(Object)this, this::removeWidget);
    }
}
