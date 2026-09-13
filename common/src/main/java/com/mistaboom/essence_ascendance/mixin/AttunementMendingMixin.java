package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.mistaboom.essence_ascendance.attunement.AttunementGameplay;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ExperienceOrb.class)
public abstract class AttunementMendingMixin {
    @WrapMethod(method = "playerTouch")
    private void essenceAscendance$experienceRoot(Player player, Operation<Void> original) {
        AttunementGameplay.withExperienceRoot(AttunementGameplay.action("experience_orb"), () -> original.call(player));
    }
    @WrapOperation(method = "repairPlayerItems", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;setDamageValue(I)V"))
    private void essenceAscendance$completedRepair(ItemStack stack, int damage, Operation<Void> original,
                                                  ServerPlayer player, int experience) {
        int before = stack.getDamageValue();
        original.call(stack, damage);
        AttunementGameplay.award(player, AttunementGameplay.experienceRoot(),
                "repair_equipment", "experience_repair:" + AttunementGameplay.itemSignature(stack), before - stack.getDamageValue());
    }
}
