package com.mistaboom.essence_ascendance.achievementfixture.mixin;
import net.minecraft.client.gui.components.toasts.AdvancementToast;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(AdvancementToast.class)
public abstract class AchievementToast2612Mixin {
    @Inject(method="extractRenderState", at=@At("RETURN"))
    private void observe(CallbackInfo callback) {
        com.mistaboom.essence_ascendance.achievementfixture.AchievementSocketFixture.toastRendered(this);
    }
}
