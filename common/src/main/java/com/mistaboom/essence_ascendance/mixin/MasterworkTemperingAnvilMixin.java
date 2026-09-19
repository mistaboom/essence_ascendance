package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.equipment.EquipmentMaintenanceService;
import com.mistaboom.essence_ascendance.equipment.EquipmentReinforcementService;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds fully-repaired-only Masterwork Tempering to the native anvil result. */
@Mixin(value = AnvilMenu.class, priority = 1100)
public abstract class MasterworkTemperingAnvilMixin {
    @Shadow private int repairItemCountCost;

    @Unique private ServerPlayer essenceAscendance$owner;

    @Inject(
            method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V",
            at = @At("RETURN")
    )
    private void essenceAscendance$captureOwner(int containerId, Inventory inventory, ContainerLevelAccess access,
                                                 CallbackInfo ci) {
        if (inventory.player instanceof ServerPlayer serverPlayer) essenceAscendance$owner = serverPlayer;
    }

    /**
     * Masterwork Tempering is reinforcement, never repair. The target must already be at full native durability.
     * Damaged vanilla/modded gear therefore keeps its normal anvil behavior untouched, while damaged Ascendance
     * artifacts remain the Infuser's responsibility. The shared zero-cost anvil support allows a pure tempering
     * result to be taken without inventing a hidden XP fee solely to satisfy vanilla's pickup gate.
     */
    @Inject(method = "createResult", at = @At("RETURN"))
    private void essenceAscendance$masterworkTempering(CallbackInfo ci) {
        ServerPlayer player = essenceAscendance$owner;
        if (player == null) return;
        var context = SkillEffectRuntime.context(player);
        if (!context.isEffective(SkillIds.MASTERWORK_TEMPERING)) return;

        AnvilMenu menu = (AnvilMenu)(Object)this;
        ItemStack base = menu.getSlot(0).getItem();
        ItemStack material = menu.getSlot(1).getItem();
        if (!EquipmentMaintenanceService.eligible(base)
                || base.getDamageValue() > 0
                || material.isEmpty()) return;

        var reinforcement = EquipmentReinforcementService.reinforceFullyRepaired(
                base,
                menu.getSlot(2).getItem(),
                material,
                context.settings().utility().masterworkTempering(),
                player.server
        );
        if (!reinforcement.applied()) return;

        menu.getSlot(2).set(reinforcement.output());
        repairItemCountCost = reinforcement.materialItems();

        // Pure tempering intentionally leaves vanilla's work cost untouched. With no vanilla operation this is
        // zero; AnvilMenuEfficiencyMixin already permits synchronized zero-cost result pickup and still handles
        // material-efficiency refunds. If vanilla produced a legitimate cost (for example a rename), it remains.
    }
}
