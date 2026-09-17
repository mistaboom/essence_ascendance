package com.mistaboom.essence_ascendance.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mistaboom.essence_ascendance.vitality.ConsumableRecoveryService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;

/** Both manual and Feast Reflex consumption enter the native completed item-use boundary. */
@Mixin(ItemStack.class)
public abstract class ConsumableRecoveryMixin {
    @WrapMethod(method = "finishUsingItem")
    private ItemStack essenceAscendance$completedRecovery(Level level, LivingEntity user, Operation<ItemStack> original) {
        return user instanceof ServerPlayer player
                ? ConsumableRecoveryService.complete(player, (ItemStack)(Object)this, () -> original.call(level, user))
                : original.call(level, user);
    }
}
