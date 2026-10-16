package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.client.ui.fullscreen.FullscreenContainerScreen;
import com.mistaboom.essence_ascendance.client.ui.fullscreen.FullscreenScreen;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Fullscreen compositions own their widget layer; native inventory additions have no reserved space there. */
@Mixin(Screen.class)
public abstract class FullscreenWidgetLayerMixin {
    @Shadow protected abstract void clearWidgets();
    @Shadow public abstract void clearFocus();

    @Unique private boolean essenceAscendance$composedWidgetLayer() {
        return (Object) this instanceof FullscreenScreen || (Object) this instanceof FullscreenContainerScreen<?>;
    }

    @Inject(method = {"addRenderableWidget", "addWidget"}, at = @At("HEAD"), cancellable = true)
    private void essenceAscendance$composedWidgets(GuiEventListener widget, CallbackInfoReturnable<GuiEventListener> cir) {
        if (essenceAscendance$composedWidgetLayer()) cir.setReturnValue(widget);
    }

    @Inject(method = "addRenderableOnly", at = @At("HEAD"), cancellable = true)
    private void essenceAscendance$composedRenderables(Renderable widget, CallbackInfoReturnable<Renderable> cir) {
        if (essenceAscendance$composedWidgetLayer()) cir.setReturnValue(widget);
    }

    // NeoForge's native init-event consumer writes all three collections directly instead of calling addWidget.
    @Dynamic("NeoForge Screen initialization event adapter; absent on Fabric")
    @Inject(method = "addEventWidget", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void essenceAscendance$composedEventWidgets(GuiEventListener widget, CallbackInfo ci) {
        if (essenceAscendance$composedWidgetLayer()) ci.cancel();
    }

    @Inject(method = {"init(Lnet/minecraft/client/Minecraft;II)V", "rebuildWidgets"}, at = @At("RETURN"))
    private void essenceAscendance$finishComposedWidgets(CallbackInfo ci) {
        essenceAscendance$clearNativeWidgets();
    }

    @Inject(method = "runNarration", at = @At("HEAD"))
    private void essenceAscendance$composedNarration(CallbackInfo ci) {
        essenceAscendance$clearNativeWidgets();
    }

    @Unique private void essenceAscendance$clearNativeWidgets() {
        if (essenceAscendance$composedWidgetLayer()) {
            clearFocus();
            clearWidgets();
        }
    }
}
