package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.gathering.RodHookAccess;
import com.mistaboom.essence_ascendance.client.PersonalizedEquipmentClient;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.entity.FishingHookRenderer;
import net.minecraft.client.renderer.entity.state.FishingHookRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Capture the hook skin while the entity is available, then submit it with the render state. */
@Mixin(FishingHookRenderer.class)
public abstract class AscendanceFishingRendererMixin {
    @Unique private final Map<FishingHookRenderState, Identifier> essenceAscendance$skins =
            Collections.synchronizedMap(new WeakHashMap<>());

    @Unique private static Identifier essenceAscendance$hookTexture(FishingHook hook) {
        Identifier personalized = PersonalizedEquipmentClient.hookTexture(hook);
        if (personalized != null) return personalized;
        return hook instanceof RodHookAccess access && access.essenceAscendance$rodHand() != 0
                ? Identifier.fromNamespaceAndPath("essence_ascendance",
                        "textures/entity/ascendance_fishing_hook/" + access.essenceAscendance$rodTier().serializedName() + ".png")
                : null;
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/projectile/FishingHook;Lnet/minecraft/client/renderer/entity/state/FishingHookRenderState;F)V",
            at = @At("TAIL"))
    private void essenceAscendance$captureSkin(FishingHook hook, FishingHookRenderState state,
            float partialTick, CallbackInfo ci) {
        Identifier texture = essenceAscendance$hookTexture(hook);
        if (texture == null) essenceAscendance$skins.remove(state);
        else essenceAscendance$skins.put(state, texture);
    }

    @ModifyArg(method = "submit(Lnet/minecraft/client/renderer/entity/state/FishingHookRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(value = "INVOKE", ordinal = 0,
                    target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitCustomGeometry(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;Lnet/minecraft/client/renderer/SubmitNodeCollector$CustomGeometryRenderer;)V"),
            index = 1)
    private RenderType essenceAscendance$hookSprite(RenderType original,
            @Local(argsOnly = true) FishingHookRenderState state) {
        Identifier texture = essenceAscendance$skins.get(state);
        return texture == null ? original : RenderTypes.entityCutoutCull(texture);
    }

    @ModifyVariable(method = "getPlayerHandPos", ordinal = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;getCameraType()Lnet/minecraft/client/CameraType;"))
    private int essenceAscendance$castHand(int nativeDirection, Player owner, float swing, float partialTick) {
        if (owner.fishing instanceof RodHookAccess access && access.essenceAscendance$rodHand() != 0)
            return (owner.getMainArm() == HumanoidArm.RIGHT ? 1 : -1)
                    * (access.essenceAscendance$rodHand() == 1 ? 1 : -1);
        return nativeDirection;
    }
}
