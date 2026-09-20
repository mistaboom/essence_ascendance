package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.movement.FlightAbilityRules;
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
        Minecraft minecraft = Minecraft.getInstance();
        boolean focused = minecraft.screen == null && minecraft.isWindowActive();
        // Read the physical binding once for both presentation and the authoritative input packet.
        boolean jumpDown = minecraft.options.keyJump.isDown();
        com.mistaboom.essence_ascendance.client.FlightVisualClientState.tick(player, jumpDown, focused);
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
        boolean jumpEligible = style != null && MovementAbilityRules.allowed(player);
        boolean jumpEnabled = jumpEligible && focused;
        boolean flightEnabled = FlightAbilityRules.usesMovementInput(player)
                && FlightAbilityRules.inputAllowed(player) && focused;
        boolean enabled = jumpEnabled || flightEnabled;
        boolean supported = MovementAbilityRules.supported(player);
        // The ordinary Jump binding was sampled above before any local suppression.
        int flags = enabled ? MovementAbilityInput.ENABLED
                | (jumpDown ? MovementAbilityInput.JUMP : 0)
                | (player.input.up ? MovementAbilityInput.FORWARD : 0)
                | (player.input.down ? MovementAbilityInput.BACK : 0)
                | (player.input.left ? MovementAbilityInput.LEFT : 0)
                | (player.input.right ? MovementAbilityInput.RIGHT : 0) : 0;
        long now = player.level().getGameTime();
        var settings = EssenceConfigManager.skillEffects();
        MovementAbilityState.Decision decision = MovementAbilityState.Decision.NONE;
        MovementAbilityState.Mode mode = style == null ? MovementAbilityState.Mode.AIR : MovementAbilityRules.mode(style);
        if (style != null) {
            decision = essenceAscendance$jumpInput.input(now,
                    new MovementAbilityInput(flags | (jumpEnabled && !supported ? MovementAbilityInput.AIR_JUMP : 0)),
                    mode, jumpEnabled, supported, settings.mobility().chargedJump().chargeTicks(),
                    settings.posture().movement().intentTimeoutTicks());
            if (decision.action() == MovementAbilityState.Action.CHARGED_RELEASE)
                essenceAscendance$jumpInput.launched(now, true);
        } else {
            essenceAscendance$jumpInput.clear();
        }
        // Mark only an extra-jump request. A native ground press must never become
        // an air jump if its queued server handler runs after the takeoff packet.
        if (decision.action() == MovementAbilityState.Action.AIR_JUMP) flags |= MovementAbilityInput.AIR_JUMP;
        if (flags != essenceAscendance$jumpFlags || (enabled
                && (now < essenceAscendance$jumpSent || now - essenceAscendance$jumpSent >= MovementAbilityInput.HEARTBEAT_TICKS))) {
            NetworkManager.sendToServer(new MovementAbilityInputPayload(new MovementAbilityInput(flags)));
            essenceAscendance$jumpFlags = flags; essenceAscendance$jumpSent = now;
        }
        // Flight consumes the same transport but never suppresses vanilla Jump. Only
        // the existing jump-style controller may claim native takeoff/air-jump input.
        essenceAscendance$suppressNativeJump = jumpEnabled && (jumpDown
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
