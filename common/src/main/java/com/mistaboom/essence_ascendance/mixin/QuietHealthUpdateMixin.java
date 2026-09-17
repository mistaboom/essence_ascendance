package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mistaboom.essence_ascendance.damage.DamageFeedbackAccess;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;

/** LocalPlayer.hurtTo creates its OWN flinch on health loss, independently of the damage packet.
 * Keep its entire native health/death update; restore presentation only for a quiet-only batch. */
@Mixin(LocalPlayer.class)
public abstract class QuietHealthUpdateMixin {
    @WrapMethod(method = "hurtTo")
    private void essenceAscendance$quietHealthUpdate(float health, Operation<Void> original) {
        LocalPlayer player = (LocalPlayer)(Object)this;
        boolean quiet = ((DamageFeedbackAccess)player).essenceAscendance$damageFeedback().consumeQuietUpdate();
        int hurt = player.hurtTime, duration = player.hurtDuration, immunity = player.invulnerableTime;
        try {
            original.call(health);
        } finally {
            if (quiet) {
                player.hurtTime = hurt;
                player.hurtDuration = duration;
                player.invulnerableTime = immunity;
            }
        }
    }
}
