package com.mistaboom.essence_ascendance.audiofixture.mixin;

import com.mojang.blaze3d.platform.Window;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Test-only native context creation: rendering/audio still execute, without a desktop window. */
@Mixin(Window.class)
public abstract class AudioHiddenWindowMixin {
    //? if >=26.1.2 {
    /*@Inject(method="createGlfwWindow",at=@At(value="INVOKE",target="Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J",remap=false),require=1,allow=1)
    private static void prepareNativeCreation(CallbackInfoReturnable<Long> callback) {
        prepareHiddenWindow();
    }
    *///?}
    //? if <26.1.2 {
    // NeoForge's patched constructor delegates creation through its disabled-early-window provider.
    // Exactly one native path must match before construction can execute; neither call is replaced.
    @Inject(method="<init>",at={
            @At(value="INVOKE",target="Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J",remap=false),
            @At(value="INVOKE",target="Lnet/neoforged/fml/loading/ImmediateWindowHandler;setupMinecraftWindow(Ljava/util/function/IntSupplier;Ljava/util/function/IntSupplier;Ljava/util/function/Supplier;Ljava/util/function/LongSupplier;)J",remap=false)
    },require=1,allow=1)
    private void prepareNativeCreation(CallbackInfo callback) {
        prepareHiddenWindow();
    }
    //?}
    @Inject(method="<init>",at=@At("RETURN"))
    private void verifyNativeCreation(CallbackInfo callback) {
        //? if >=26.1.2
        /*verifyHiddenWindow(((Window)(Object)this).handle());*/
        //? if <26.1.2
        verifyHiddenWindow(((Window)(Object)this).getWindow());
    }
    private static void prepareHiddenWindow() {
        if(Boolean.getBoolean("essence.audio.hiddenWindow")) {
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_FOCUSED,GLFW.GLFW_FALSE);
        }
    }
    private static void verifyHiddenWindow(long window) {
        if(Boolean.getBoolean("essence.audio.hiddenWindow") && window!=0) {
            if(GLFW.glfwGetWindowAttrib(window,GLFW.GLFW_VISIBLE)!=GLFW.GLFW_FALSE
                    || GLFW.glfwGetWindowAttrib(window,GLFW.GLFW_FOCUSED)!=GLFW.GLFW_FALSE)
                throw new AssertionError("Native fixture window unexpectedly visible or focused");
            System.out.println("AudioHiddenWindow: PASS; native GLFW window created invisible; rendering and audio retained");
        }
    }
}
