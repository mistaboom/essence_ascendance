package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.equipment.EnchantingMenuCostView;
import com.mistaboom.essence_ascendance.equipment.MenuCostModificationService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.EnchantmentMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;

/**
 * Quotes all three enchanting offers on the server and appends their actual
 * XP/lapis costs to the vanilla menu data, giving the client an authoritative
 * display without a loader-specific packet.
 */
@Mixin(EnchantmentMenu.class)
public abstract class EnchantmentMenuEfficiencyMixin
        implements EnchantingMenuCostView {

    @Shadow
    @Final
    public int[] costs;

    @Unique
    private int[] essenceAscendance$experienceCosts = new int[0];

    @Unique
    private int[] essenceAscendance$lapisCosts = new int[0];

    @Unique
    private ServerPlayer essenceAscendance$owner;

    @Inject(
            method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V",
            at = @At("RETURN")
    )
    private void essenceAscendance$installSynchronizedCosts(
            int containerId,
            Inventory inventory,
            ContainerLevelAccess access,
            CallbackInfo ci
    ) {
        if (inventory.player instanceof ServerPlayer serverPlayer) {
            essenceAscendance$owner = serverPlayer;
        }

        essenceAscendance$experienceCosts = new int[costs.length];
        essenceAscendance$lapisCosts = new int[costs.length];
        Arrays.fill(essenceAscendance$experienceCosts, -1);
        Arrays.fill(essenceAscendance$lapisCosts, -1);
        AbstractContainerMenuAccessor menu =
                (AbstractContainerMenuAccessor) this;
        for (int offer = 0; offer < costs.length; offer++) {
            menu.essenceAscendance$addDataSlot(DataSlot.shared(
                    essenceAscendance$experienceCosts,
                    offer
            ));
            menu.essenceAscendance$addDataSlot(DataSlot.shared(
                    essenceAscendance$lapisCosts,
                    offer
            ));
        }
    }

    @Inject(method = "slotsChanged", at = @At("RETURN"))
    private void essenceAscendance$refreshSynchronizedCosts(
            Container container,
            CallbackInfo ci
    ) {
        ServerPlayer player = essenceAscendance$owner;
        if (player == null) {
            return;
        }

        for (int offer = 0; offer < costs.length; offer++) {
            if (costs[offer] <= 0) {
                essenceAscendance$experienceCosts[offer] = 0;
                essenceAscendance$lapisCosts[offer] = 0;
                continue;
            }

            int vanillaCost = offer + 1;
            essenceAscendance$experienceCosts[offer] =
                    MenuCostModificationService
                            .quoteEnchantingExperience(player, vanillaCost)
                            .resolvedCost();
            essenceAscendance$lapisCosts[offer] =
                    MenuCostModificationService
                            .quoteEnchantingLapis(player, vanillaCost)
                            .resolvedCost();
        }

        /* Send the appended DataSlots in the same vanilla container sync. */
        ((EnchantmentMenu) (Object) this).broadcastChanges();
    }

    @Override
    public int essenceAscendance$experienceCost(int offerIndex) {
        return synchronizedCost(essenceAscendance$experienceCosts, offerIndex);
    }

    @Override
    public int essenceAscendance$lapisCost(int offerIndex) {
        return synchronizedCost(essenceAscendance$lapisCosts, offerIndex);
    }

    @Unique
    private static int synchronizedCost(int[] values, int offerIndex) {
        if (offerIndex < 0 || offerIndex >= values.length) {
            throw new IndexOutOfBoundsException("Invalid enchanting offer index");
        }

        int value = values[offerIndex];
        return value >= 0 ? value : offerIndex + 1;
    }
}
