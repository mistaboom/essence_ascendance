package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mistaboom.essence_ascendance.attunement.AttunementGameplay;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.commands.DamageCommand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

/** /damage may name a survival player as its source; that does not make it a survival action. */
@Mixin(DamageCommand.class)
abstract class AdministrativeDamageCommandMixin {
    @WrapMethod(method = "damage")
    private static int essenceAscendance$damage(CommandSourceStack source, Entity target, float amount,
                                                DamageSource damageSource, Operation<Integer> original) {
        try (var scope = AttunementGameplay.suppressAdministrativeOutcomes()) {
            return original.call(source, target, amount, damageSource);
        }
    }
}
