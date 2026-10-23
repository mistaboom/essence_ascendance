package com.mistaboom.essence_ascendance.fabric.mixin;

import com.mistaboom.essence_ascendance.client.ClientBlockTint2612;
import net.minecraft.client.color.block.BlockColors;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Fabric initializes our client entrypoint before Minecraft has created its block colors. */
@Mixin(BlockColors.class)
public abstract class BlockColorsMixin {
    @Inject(method = "createDefault", at = @At("RETURN"), require = 1)
    private static void essenceAscendance$registerBlockTints(CallbackInfoReturnable<BlockColors> cir) {
        ClientBlockTint2612.register(cir.getReturnValue());
    }
}
