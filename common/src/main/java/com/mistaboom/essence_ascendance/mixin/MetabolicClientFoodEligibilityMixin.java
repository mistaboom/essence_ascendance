package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mistaboom.essence_ascendance.client.ClientCommittedSkills;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Client predicts only its synchronized committed skill; the server independently validates the action. */
@Mixin(Player.class)
public abstract class MetabolicClientFoodEligibilityMixin {
    @ModifyReturnValue(method = "canEat", at = @At("RETURN"))
    private boolean essenceAscendance$predictEatToHeal(boolean original) {
        Player player = (Player)(Object)this;
        return original || player == Minecraft.getInstance().player && player.isAlive() && !player.isSpectator()
                && !player.getAbilities().instabuild && player.getHealth() < player.getMaxHealth()
                && ClientCommittedSkills.isEffective(SkillIds.METABOLIC_CONVERSION);
    }
}
