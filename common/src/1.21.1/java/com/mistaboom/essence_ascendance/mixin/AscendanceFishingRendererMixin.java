package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mistaboom.essence_ascendance.gathering.RodHookAccess;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.FishingHookRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native first/third-person hand position and line rendering, using the actual synchronized cast hand. */
@Mixin(FishingHookRenderer.class)
public abstract class AscendanceFishingRendererMixin {
    @Unique private static ResourceLocation essenceAscendance$hookTexture(FishingHook hook) {
        var personalized = com.mistaboom.essence_ascendance.client.PersonalizedEquipmentClient.hookTexture(hook);
        if (personalized != null) return personalized;
        return hook instanceof RodHookAccess access && access.essenceAscendance$rodHand()!=0
                ? ResourceLocation.fromNamespaceAndPath("essence_ascendance","textures/entity/ascendance_fishing_hook/"+access.essenceAscendance$rodTier().serializedName()+".png") : null;
    }
    @ModifyExpressionValue(method="render(Lnet/minecraft/world/entity/projectile/FishingHook;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at=@At(value="FIELD",target="Lnet/minecraft/client/renderer/entity/FishingHookRenderer;RENDER_TYPE:Lnet/minecraft/client/renderer/RenderType;"))
    private RenderType essenceAscendance$hookSprite(RenderType original, FishingHook hook, float yaw, float partialTick,
            PoseStack pose, MultiBufferSource buffers, int light) {
        var texture=essenceAscendance$hookTexture(hook);
        return texture==null ? original : RenderType.entityCutout(texture);
    }
    @Inject(method="getTextureLocation(Lnet/minecraft/world/entity/projectile/FishingHook;)Lnet/minecraft/resources/ResourceLocation;",at=@At("HEAD"),cancellable=true)
    private void essenceAscendance$hookTextureLocation(FishingHook hook, CallbackInfoReturnable<ResourceLocation> cir) {
        var texture=essenceAscendance$hookTexture(hook);if(texture!=null)cir.setReturnValue(texture);
    }
    @ModifyVariable(method = "getPlayerHandPos", ordinal = 0, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;getCameraType()Lnet/minecraft/client/CameraType;"))
    private int essenceAscendance$castHand(int nativeDirection, Player owner, float swing, float partialTick) {
        if (owner.fishing instanceof RodHookAccess access && access.essenceAscendance$rodHand() != 0)
            return (owner.getMainArm() == HumanoidArm.RIGHT ? 1 : -1) * (access.essenceAscendance$rodHand() == 1 ? 1 : -1);
        return nativeDirection;
    }
}
