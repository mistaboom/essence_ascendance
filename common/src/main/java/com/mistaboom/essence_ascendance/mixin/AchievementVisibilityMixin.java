package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.achievement.AchievementPresentation;
import net.minecraft.server.advancements.AdvancementVisibilityEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Keep native completion evaluation; only the outgoing discovery result is augmented. */
@Mixin(AdvancementVisibilityEvaluator.class)
public abstract class AchievementVisibilityMixin {
    @ModifyVariable(method = "evaluateVisibility(Lnet/minecraft/advancements/AdvancementNode;Ljava/util/function/Predicate;Lnet/minecraft/server/advancements/AdvancementVisibilityEvaluator$Output;)V",
            at = @At("HEAD"), argsOnly = true)
    private static AdvancementVisibilityEvaluator.Output essenceAscendance$selectedDiscovery(
            AdvancementVisibilityEvaluator.Output output) {
        return AchievementPresentation.revealing(output);
    }
}
