package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mistaboom.essence_ascendance.client.ClientCommittedSkills;
import com.mistaboom.essence_ascendance.equipment.EquipmentMaintenanceService;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Client predicts synchronized recovery skills; the server independently validates the action and tuning. */
@Mixin(Player.class)
public abstract class MetabolicClientFoodEligibilityMixin {
    @ModifyReturnValue(method = "canEat", at = @At("RETURN"))
    private boolean essenceAscendance$predictRecoveryEating(boolean original) {
        Player player = (Player)(Object)this;
        if (original || player != Minecraft.getInstance().player || !player.isAlive() || player.isSpectator()
                || player.getAbilities().instabuild) return original;
        boolean healing = player.getHealth() < player.getMaxHealth()
                && ClientCommittedSkills.isEffective(SkillIds.METABOLIC_CONVERSION);
        boolean maintenance = EquipmentMaintenanceService.hasDamagedItem(player)
                && ClientCommittedSkills.isEffective(SkillIds.METABOLIC_MENDING);
        return healing || maintenance;
    }
}
