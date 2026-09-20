package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Preserves server-authoritative enchanting offers until the inserted item actually changes. */
@Mixin(EnchantmentMenu.class)
public abstract class EnchantmentMenuInsightMixin {
    @Unique private ServerPlayer essenceAscendance$insightOwner;
    @Unique private ItemStack essenceAscendance$stableItem = ItemStack.EMPTY;
    @Unique private boolean essenceAscendance$stableInitialized;

    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V",
            at = @At("RETURN"))
    private void essenceAscendance$captureInsightOwner(int containerId, Inventory inventory,
                                                       ContainerLevelAccess access, CallbackInfo ci) {
        if (inventory.player instanceof ServerPlayer player) essenceAscendance$insightOwner = player;
    }

    @Inject(method = "slotsChanged", at = @At("HEAD"), cancellable = true)
    private void essenceAscendance$keepInsightOffers(Container container, CallbackInfo ci) {
        ServerPlayer owner = essenceAscendance$insightOwner;
        if (owner == null || !SkillEffectRuntime.context(owner).isEffective(SkillIds.ENCHANTING_INSIGHT)) {
            essenceAscendance$stableInitialized = false;
            essenceAscendance$stableItem = ItemStack.EMPTY;
            return;
        }

        ItemStack current = ((EnchantmentMenu) (Object) this).getSlot(0).getItem();
        if (essenceAscendance$stableInitialized && sameInsertedItem(current, essenceAscendance$stableItem)) {
            ci.cancel();
        }
    }

    @Inject(method = "slotsChanged", at = @At("RETURN"))
    private void essenceAscendance$rememberInsightItem(Container container, CallbackInfo ci) {
        ServerPlayer owner = essenceAscendance$insightOwner;
        if (owner == null || !SkillEffectRuntime.context(owner).isEffective(SkillIds.ENCHANTING_INSIGHT)) return;
        essenceAscendance$stableItem = ((EnchantmentMenu) (Object) this).getSlot(0).getItem().copy();
        essenceAscendance$stableInitialized = true;
    }

    @Unique
    private static boolean sameInsertedItem(ItemStack first, ItemStack second) {
        return first.getCount() == second.getCount() && ItemStack.isSameItemSameComponents(first, second);
    }
}
