package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.equipment.AnvilMenuCostView;
import com.mistaboom.essence_ascendance.equipment.MenuCostModificationService;
import com.mistaboom.essence_ascendance.equipment.ResourceCostEfficiencyService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes the server's anvil DataSlot itself efficient, so vanilla container
 * synchronization sends the same level cost that will actually be charged.
 */
@Mixin(AnvilMenu.class)
public abstract class AnvilMenuEfficiencyMixin implements AnvilMenuCostView {

    @Shadow
    @Final
    private DataSlot cost;

    @Shadow
    private int repairItemCountCost;

    @Unique
    private ServerPlayer essenceAscendance$owner;

    @Unique
    private ResourceCostEfficiencyService.CostQuote essenceAscendance$experienceQuote;

    @Unique
    private ResourceCostEfficiencyService.CostQuote essenceAscendance$materialQuote;

    @Unique
    private ResourceCostEfficiencyService.CostQuote essenceAscendance$pendingExperience;

    @Unique
    private ResourceCostEfficiencyService.CostQuote essenceAscendance$pendingMaterials;

    @Unique
    private ItemStack essenceAscendance$pendingMaterialRefund = ItemStack.EMPTY;

    @Unique
    private final int[] essenceAscendance$displayCost = {-1};

    @Inject(
            method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V",
            at = @At("RETURN")
    )
    private void essenceAscendance$captureOwner(
            int containerId,
            Inventory inventory,
            ContainerLevelAccess access,
            CallbackInfo ci
    ) {
        if (inventory.player instanceof ServerPlayer serverPlayer) {
            essenceAscendance$owner = serverPlayer;
        }
        ((AbstractContainerMenuAccessor) this)
                .essenceAscendance$addDataSlot(
                        DataSlot.shared(essenceAscendance$displayCost, 0)
                );
    }

    @Inject(method = "createResult", at = @At("RETURN"))
    private void essenceAscendance$quoteEfficientCosts(CallbackInfo ci) {
        ServerPlayer player = essenceAscendance$owner;
        if (player == null || player.hasInfiniteMaterials()) {
            return;
        }

        int baseExperience = Math.max(0, cost.get());
        int baseMaterials = Math.max(0, repairItemCountCost);

        essenceAscendance$experienceQuote =
                MenuCostModificationService.quoteAnvilExperience(
                        player,
                        baseExperience
                );
        essenceAscendance$materialQuote =
                MenuCostModificationService.quoteAnvilMaterials(
                        player,
                        baseMaterials
                );

        cost.set(essenceAscendance$experienceQuote.resolvedCost());
        essenceAscendance$displayCost[0] =
                essenceAscendance$experienceQuote.resolvedCost();
    }

    @Override
    public int essenceAscendance$displayCost() {
        return essenceAscendance$displayCost[0];
    }

    @Override public int essenceAscendance$originalCost() {
        return essenceAscendance$experienceQuote == null ? cost.get() : essenceAscendance$experienceQuote.baseCost();
    }

    @Inject(method = "mayPickup", at = @At("RETURN"), cancellable = true)
    private void essenceAscendance$allowSynchronizedZeroCostResult(
            Player player,
            boolean hasResult,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (hasResult && cost.get() == 0) {
            cir.setReturnValue(true);
        }
    }

    @Inject(
            method = "onTake(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("HEAD")
    )
    private void essenceAscendance$captureCommittedQuotes(
            Player player,
            ItemStack result,
            CallbackInfo ci
    ) {
        essenceAscendance$pendingExperience = essenceAscendance$experienceQuote;
        essenceAscendance$pendingMaterials = essenceAscendance$materialQuote;
        essenceAscendance$pendingMaterialRefund = ItemStack.EMPTY;

        if (essenceAscendance$pendingMaterials != null
                && essenceAscendance$pendingMaterials.saved() > 0) {
            ItemStack material = ((AnvilMenu) (Object) this).getSlot(1).getItem();
            if (!material.isEmpty()) {
                essenceAscendance$pendingMaterialRefund = material.copyWithCount(
                        Math.min(
                                material.getCount(),
                                essenceAscendance$pendingMaterials.saved()
                        )
                );
            }
        }
    }

    @Inject(
            method = "onTake(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("RETURN")
    )
    private void essenceAscendance$commitEfficientCosts(
            Player player,
            ItemStack result,
            CallbackInfo ci
    ) {
        try {
            if (player instanceof ServerPlayer serverPlayer
                    && !serverPlayer.hasInfiniteMaterials()) {
                MenuCostModificationService.commitAnvilQuote(
                        serverPlayer,
                        essenceAscendance$pendingExperience
                );
                MenuCostModificationService.commitAnvilQuote(
                        serverPlayer,
                        essenceAscendance$pendingMaterials
                );
                if (!essenceAscendance$pendingMaterialRefund.isEmpty()) {
                    serverPlayer.getInventory().placeItemBackInInventory(
                            essenceAscendance$pendingMaterialRefund
                    );
                }
            }
        } finally {
            essenceAscendance$pendingExperience = null;
            essenceAscendance$pendingMaterials = null;
            essenceAscendance$pendingMaterialRefund = ItemStack.EMPTY;
        }
    }
}
