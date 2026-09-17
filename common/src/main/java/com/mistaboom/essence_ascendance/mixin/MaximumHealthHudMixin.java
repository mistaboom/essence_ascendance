package com.mistaboom.essence_ascendance.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Native health rendering remembers old filled hearts for the damage flash. Do not let that
 * memory create ghost slots above synchronized maximum HP. No custom heart overlay or health value.
 * The outer render entry works on Fabric AND NeoForge's separately registered health layer. */
@Mixin(Gui.class)
public abstract class MaximumHealthHudMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow private int lastHealth;
    @Shadow private int displayHealth;

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("HEAD"), require = 1)
    private void essenceAscendance$respectCapacity(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
        if (!(minecraft.getCameraEntity() instanceof Player player)) return;
        int maximum = Mth.ceil(player.getMaxHealth());
        lastHealth = Math.min(lastHealth, maximum);
        displayHealth = Math.min(displayHealth, maximum);
    }
}
