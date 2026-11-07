package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.achievement.AchievementPresentation;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.ServerAdvancementManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

/** A fresh player has no completed root to dirty; schedule the tab's ordinary first visibility flush. */
@Mixin(PlayerAdvancements.class)
public abstract class PlayerAdvancementsPresentationMixin {
    @Shadow @Final private Set<AdvancementNode> rootsToUpdate;

    @Inject(method = "load", at = @At("TAIL"))
    private void essenceAscendance$discoverSelectedTab(ServerAdvancementManager manager, CallbackInfo callback) {
        var root = manager.tree().get(AchievementPresentation.ROOT);
        if (root != null && AchievementPresentation.discoverable(root)) rootsToUpdate.add(root);
    }
}
