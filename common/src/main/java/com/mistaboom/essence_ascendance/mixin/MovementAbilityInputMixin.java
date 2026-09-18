package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.movement.MovementAbilityInput;
import com.mistaboom.essence_ascendance.movement.MovementAbilityRules;
import com.mistaboom.essence_ascendance.movement.MovementAbilityState;
import com.mistaboom.essence_ascendance.network.MovementAbilityInputPayload;
import dev.architectury.networking.NetworkManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Runs after native input polling (and NeoForge's input event), before jump/Elytra handling.
 * The shadow controller owns key routing only. It never grants motion or alters client fall distance. */
@Mixin(LocalPlayer.class)
public abstract class MovementAbilityInputMixin {
    @Unique private final MovementAbilityState essenceAscendance$jumpInput = new MovementAbilityState();
    @Unique private ResourceLocation essenceAscendance$jumpStyle;
    @Unique private Object essenceAscendance$jumpLevel, essenceAscendance$jumpBalance;
    @Unique private int essenceAscendance$jumpFlags = -1;
    @Unique private long essenceAscendance$jumpSent = Long.MIN_VALUE;
    @Unique private boolean essenceAscendance$suppressNativeJump;

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void essenceAscendance$beginJumpFrame(CallbackInfo ci) {
        essenceAscendance$suppressNativeJump = false;
    }

    @Inject(method = "aiStep", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/tutorial/Tutorial;onInput(Lnet/minecraft/client/player/Input;)V"),
            require = 1, expect = 1, allow = 1)
    private void essenceAscendance$routeJump(CallbackInfo ci) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        if (!NetworkManager.canServerReceive(MovementAbilityInputPayload.TYPE)) return;
        ResourceLocation style = MovementAbilityRules.style(player);
        Object balance = EssenceConfigManager.clientRuntime();
        if (!java.util.Objects.equals(style, essenceAscendance$jumpStyle)
                || player.level() != essenceAscendance$jumpLevel || balance != essenceAscendance$jumpBalance) {
            essenceAscendance$jumpInput.clear();
            essenceAscendance$jumpStyle = style;
            essenceAscendance$jumpLevel = player.level(); essenceAscendance$jumpBalance = balance;
            essenceAscendance$jumpFlags = -1; essenceAscendance$jumpSent = Long.MIN_VALUE;
        }
        Minecraft minecraft = Minecraft.getInstance();
        boolean eligible = style != null && MovementAbilityRules.allowed(player);
        boolean enabled = eligible && minecraft.screen == null && minecraft.isWindowActive();
        boolean supported = eligible && MovementAbilityRules.supported(player);
        // Read the ordinary Jump binding, not the input flag this mixin consumes or
        // vanilla's synthetic auto-jump. Remapped keys still work; no new keybind.
        boolean jumpDown = minecraft.options.keyJump.isDown();
        int flags = enabled ? MovementAbilityInput.ENABLED
                | (jumpDown ? MovementAbilityInput.JUMP : 0)
                | (player.input.up ? MovementAbilityInput.FORWARD : 0)
                | (player.input.down ? MovementAbilityInput.BACK : 0)
                | (player.input.left ? MovementAbilityInput.LEFT : 0)
                | (player.input.right ? MovementAbilityInput.RIGHT : 0) : 0;
        long now = player.level().getGameTime();
        var settings = EssenceConfigManager.skillEffects();
        var mode = MovementAbilityRules.mode(style);
        var decision = essenceAscendance$jumpInput.input(now,
                new MovementAbilityInput(flags | (enabled && !supported ? MovementAbilityInput.AIR_JUMP : 0)),
                mode, eligible, supported, settings.mobility().chargedJump().chargeTicks(),
                settings.posture().movement().intentTimeoutTicks());
        if (decision.action() == MovementAbilityState.Action.CHARGED_RELEASE)
            essenceAscendance$jumpInput.launched(now, true);
        // Mark only an extra-jump request. A native ground press must never become
        // an air jump if its queued server handler runs after the takeoff packet.
        if (decision.action() == MovementAbilityState.Action.AIR_JUMP) flags |= MovementAbilityInput.AIR_JUMP;
        if (flags != essenceAscendance$jumpFlags || (enabled
                && (now < essenceAscendance$jumpSent || now - essenceAscendance$jumpSent >= MovementAbilityInput.HEARTBEAT_TICKS))) {
            NetworkManager.sendToServer(new MovementAbilityInputPayload(new MovementAbilityInput(flags)));
            essenceAscendance$jumpFlags = flags; essenceAscendance$jumpSent = now;
        }
        // One native takeoff per physical press, never hold-to-hop. The spent-air
        // edge can still reach Elytra; skill-owned presses cannot deploy it too.
        essenceAscendance$suppressNativeJump = enabled && (jumpDown
                ? decision.action() != MovementAbilityState.Action.NATIVE_JUMP
                : mode == MovementAbilityState.Mode.CHARGED && supported);
        if (essenceAscendance$suppressNativeJump) player.input.jumping = false;
    }

    /** Vanilla may write synthetic auto-jump later in aiStep. Filter its consumers
     * as well, so a consumed held press cannot be reintroduced before native travel. */
    @ModifyExpressionValue(method = {"aiStep", "serverAiStep"}, at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/player/Input;jumping:Z", opcode = Opcodes.GETFIELD), require = 1)
    private boolean essenceAscendance$filterNativeJump(boolean jumping) {
        return jumping && !essenceAscendance$suppressNativeJump;
    }
}
