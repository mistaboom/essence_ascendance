package com.mistaboom.essence_ascendance.fabric.mixin;

import com.mistaboom.essence_ascendance.skill.effect.AbsorptionPoolService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Player-specific persistence only; the inherited absorption setter is hooked on LivingEntity. */
@Mixin(Player.class)
public abstract class PlayerAbsorptionPoolMixin {
    @Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
    private void essenceAscendance$saveExternalAbsorption(CompoundTag tag, CallbackInfo ci) {
        AbsorptionPoolService.saveExternal((Player) (Object) this, tag);
    }
}
