package com.mistaboom.essence_ascendance.machine;

import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.equipment.SoulboundEquipmentData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Composition contract; structure, range, inventory and processing remain concrete policies. */
public interface BoundMachine {
    MachineBinding machineBinding();
    void bindingChanged();
    enum AccessState { UNBOUND, AUTHORIZATION_UNRESOLVED, ACCESS_DENIED, AVAILABLE }
    default AccessState accessState(net.minecraft.server.level.ServerPlayer viewer) {
        var saved = EssenceSavedData.get(viewer.server);
        if (machineBinding().refresh(saved)) bindingChanged();
        if (machineBinding().pool() == null) return AccessState.UNBOUND;
        var own = saved.resolvePool(viewer.getUUID()).snapshot().orElse(null);
        if (own == null) return AccessState.AUTHORIZATION_UNRESOLVED;
        return authorize(viewer, MachineAction.USE) ? AccessState.AVAILABLE : AccessState.ACCESS_DENIED;
    }
    default boolean canRecover(net.minecraft.server.level.ServerPlayer administrator) {
        BlockEntity entity = (BlockEntity) this;
        if (!administrator.server.isSameThread() || !administrator.hasPermissions(2)
                || administrator.server.getPlayerList().getPlayer(administrator.getUUID()) != administrator
                || !com.mistaboom.essence_ascendance.network.ServerMenuAccess.canReach(administrator, entity)) return false;
        var saved = EssenceSavedData.get(administrator.server);
        return saved.resolvePool(administrator.getUUID()).snapshot().isPresent()
                && (machineBinding().pool() == null || saved.reservoirs().isDissolved(machineBinding().pool()));
    }
    /** Item pipes have no authenticated actor; insertion separately checks any soulbound workpiece. */
    default boolean automationExposed(boolean insertion) {
        BlockEntity entity = (BlockEntity) this;
        return entity.getLevel() instanceof ServerLevel level && level.getServer().isSameThread()
                && com.mistaboom.essence_ascendance.network.ServerMenuAccess.isLoaded(entity);
    }
    /**
     * Do not let automation trap someone's equipment in a machine they cannot use.
     * Unbound items have no player identity to check. This read does not refresh or
     * modify the machine, item, job, or recipient, including during pipe simulation.
     */
    default boolean canAcceptAutomatedStack(ItemStack stack) {
        if (!automationExposed(true) || stack == null || stack.isEmpty()) return false;
        var itemOwner = SoulboundEquipmentData.read(stack).orElse(null);
        if (itemOwner == null) return true;
        var level = (ServerLevel) ((BlockEntity) this).getLevel();
        var saved = EssenceSavedData.get(level.getServer());
        var ownerMembership = saved.resolvePool(itemOwner.ownerId()).snapshot().orElse(null);
        var machineMembership = machineBinding().resolve(saved);
        return ownerMembership != null && machineMembership != null
                && ownerMembership.pool().equals(machineMembership.pool())
                && machineMembership.members().contains(itemOwner.ownerId());
    }
    default boolean authorize(Player player, MachineAction action) {
        BlockEntity entity = (BlockEntity) this;
        if (player == null || player.level() != entity.getLevel()) return false;
        if (!(entity.getLevel() instanceof ServerLevel level)) return player.level().isClientSide;
        if (!level.getServer().isSameThread() || entity.isRemoved()
                || !level.hasChunkAt(entity.getBlockPos()) || level.getBlockEntity(entity.getBlockPos()) != entity) return false;
        EssenceSavedData saved = EssenceSavedData.get(level.getServer());
        if (machineBinding().refresh(saved)) bindingChanged();
        return machineBinding().allows(saved, player.getUUID(), action);
    }
    default boolean recover(net.minecraft.server.level.ServerPlayer administrator, java.util.UUID recipient) {
        BlockEntity entity = (BlockEntity) this;
        if (!canRecover(administrator)) return false;
        var saved = EssenceSavedData.get(administrator.server);
        MachineBinding binding = machineBinding();
        binding.recover(saved, java.util.Objects.requireNonNull(recipient)); bindingChanged(); return true;
    }
}
