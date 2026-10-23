package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.client.GlaiveFirstPersonBody;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
public abstract class GlaivePlayerRendererMixin {
    @Inject(method="setModelProperties",at=@At("TAIL"))
    private void essenceAscendance$glaiveFirstPersonVisibility(AbstractClientPlayer player,CallbackInfo ci) {
        if(GlaiveFirstPersonBody.rendering()) GlaiveFirstPersonBody.visibility(((PlayerRenderer)(Object)this).getModel());
    }
}
