package com.mistaboom.essence_ascendance.achievementfixture.mixin;
import net.minecraft.client.gui.components.toasts.AdvancementToast;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(AdvancementToast.class)
public abstract class AchievementToast1211Mixin {
    @Inject(method="render", at=@At("RETURN"))
    private void observe(CallbackInfoReturnable<?> callback) {
        com.mistaboom.essence_ascendance.achievementfixture.AchievementSocketFixture.toastRendered(this);
    }
}
