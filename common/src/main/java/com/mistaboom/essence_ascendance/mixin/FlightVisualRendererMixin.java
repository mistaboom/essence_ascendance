package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.client.FlightVisualLayer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the procedural flight visual as a normal player render layer. */
@Mixin(PlayerRenderer.class)
public abstract class FlightVisualRendererMixin
        extends LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    protected FlightVisualRendererMixin(EntityRendererProvider.Context context,
                                        PlayerModel<AbstractClientPlayer> model,
                                        float shadowRadius) {
        super(context, model, shadowRadius);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void essenceAscendance$addFlightVisualLayer(EntityRendererProvider.Context context,
                                                         boolean slim,
                                                         CallbackInfo ci) {
        this.addLayer(new FlightVisualLayer(this));
    }
}
