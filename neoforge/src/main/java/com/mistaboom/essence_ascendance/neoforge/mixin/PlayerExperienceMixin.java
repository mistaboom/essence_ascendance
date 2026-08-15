package com.mistaboom.essence_ascendance.neoforge.mixin;

import com.mistaboom.essence_ascendance.equipment.EquipmentGatheringService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Player.class)
public abstract class PlayerExperienceMixin {

    @ModifyVariable(
            method = "giveExperiencePoints",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private int essenceAscendance$scalePositiveExperienceGain(
            int amount
    ) {
        Player player = (Player) (Object) this;
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return amount;
        }
        return EquipmentGatheringService.modifyExperienceGain(serverPlayer, amount);
    }
}
