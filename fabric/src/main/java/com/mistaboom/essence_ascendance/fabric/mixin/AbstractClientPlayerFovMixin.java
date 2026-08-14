package com.mistaboom.essence_ascendance.fabric.mixin;

import com.mistaboom.essence_ascendance.equipment.EquipmentWeaponService;
import com.mistaboom.essence_ascendance.item.AscendanceRangedWeaponItem;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/*
 * Adds vanilla-strength bow FOV zoom to the Ascendance Bow.
 *
 * Vanilla 1.21.1 only applies its bow zoom to the actual minecraft:bow item,
 * so a custom BowItem does not inherit that camera behavior automatically.
 *
 * We preserve vanilla's zoom curve and maximum amount:
 *
 *   progress = usedTicks / fullDrawTicks
 *   curved   = min(progress, 1)^2
 *   FOV      = existingFov * (1 - curved * 0.15)
 *
 * The only Ascendance-specific change is replacing vanilla's 20-tick timing
 * with the same synchronized fullDrawTicks already used by gameplay, the bow
 * model predicates, and the first-person hand transform.
 */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerFovMixin {

    @Inject(
            method = "getFieldOfViewModifier",
            at = @At("RETURN"),
            cancellable = true
    )
    private void essenceAscendance$applyAcceleratedVanillaBowZoom(
            CallbackInfoReturnable<Float> cir
    ) {
        AbstractClientPlayer player =
                (AbstractClientPlayer) (Object) this;

        if (!player.isUsingItem()) {
            return;
        }

        ItemStack stack = player.getUseItem();

        if (!(stack.getItem() instanceof AscendanceRangedWeaponItem)) {
            return;
        }

        int fullDrawTicks = Math.max(
                1,
                EquipmentWeaponService.syncedRangedFullDrawTicks(stack)
        );

        float progress = Math.min(
                1.0F,
                player.getTicksUsingItem() / (float) fullDrawTicks
        );

        // This quadratic curve and 15% maximum reduction match vanilla bow FOV.
        float curvedProgress = progress * progress;
        float vanillaBowZoomMultiplier =
                1.0F - curvedProgress * 0.15F;

        Float existingFov = cir.getReturnValue();

        if (existingFov == null) {
            return;
        }

        cir.setReturnValue(
                existingFov * vanillaBowZoomMultiplier
        );
    }
}
