package com.mistaboom.essence_ascendance.pylon;

import com.mistaboom.essence_ascendance.machine.*;
import com.mistaboom.essence_ascendance.network.ServerMenuAccess;
import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleBlockEntity;
import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleStructureService;
import com.mistaboom.essence_ascendance.visual.MachineVisualState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class EssencePylonBlockEntity extends BlockEntity
        implements WorldlyContainer, MenuProvider, BoundMachine {

    public static final int FOCUS_SLOT = 0;
    private static final int[] NO_AUTOMATION_SLOTS = {};

    private static final String OWNER_TAG = "owner";
    private static final String OWNER_NAME_TAG = "owner_name";

    private final NonNullList<ItemStack> items =
            NonNullList.withSize(1, ItemStack.EMPTY);

    private final MachineBinding binding = new MachineBinding();
    private UUID ownerId;
    private String ownerName = "";
    private final MachineLink link = new MachineLink();
    private BlockPos linkedCruciblePos;
    private MachineVisualState.Pylon clientVisualState;
    private MachineVisualState.Pylon lastSyncedVisualState;

    public EssencePylonBlockEntity(BlockPos pos, BlockState state) {
        super(
                EssencePylonContent.ESSENCE_PYLON_BLOCK_ENTITY.get(),
                pos,
                state
        );
    }

    public static void serverTick(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            EssencePylonBlockEntity pylon
    ) {
        if (level.getGameTime() % 20L == 0L) {
            pylon.refreshLink();
            if (!pylon.visualState().equals(pylon.lastSyncedVisualState)) {
                pylon.syncBlockEntity();
            }
        }
    }

    public MachineVisualState.Pylon visualState() {
        if (level != null && level.isClientSide) return clientVisualState == null
                ? MachineVisualState.IDLE_PYLON : clientVisualState;
        boolean active = false;
        if (level instanceof ServerLevel serverLevel && isCurrentLinkValid(serverLevel)
                && serverLevel.getBlockEntity(linkedCruciblePos) instanceof EssenceCrucibleBlockEntity crucible) {
            active = crucible.structureSnapshot().containsPylon(worldPosition);
        }
        EssenceFocusTier tier = focusTier();
        boolean installed = hasInstalledFocus();
        long rate = active ? contribution().transferRatePerSecondBonus() : 0L;
        return new MachineVisualState.Pylon(linkedCruciblePos,
                new MachineVisualState.Focus(MachineVisualState.Host.PYLON, installed, tier,
                        active && installed, rate));
    }

    public UUID ownerId() {
        return ownerId;
    }

    public String ownerDisplayName() {
        if (level instanceof ServerLevel serverLevel)
            return com.mistaboom.essence_ascendance.machine.MachineOwnerDisplay.ownerName(
                    serverLevel.getServer(), binding, ownerId, ownerName);
        return ownerName.isBlank() ? "Unknown owner" : ownerName;
    }

    /** Placement establishes identity; later interactions authorize only. */
    public boolean bindOnPlacement(Player player) {
        if (!(level instanceof ServerLevel serverLevel)) return false;
        if (!binding.place(com.mistaboom.essence_ascendance.data.EssenceSavedData.get(serverLevel.getServer()), player.getUUID())) return false;
        ownerId = player.getUUID(); ownerName = player.getGameProfile().getName();
        link.configure(MachineLink.Mode.AUTOMATIC, null); linkedCruciblePos = null;
        bindingChanged(); refreshLink(); return true;
    }
    @Override public MachineBinding machineBinding() { return binding; }
    public boolean canPlayerConfigure(Player player) { return ServerMenuAccess.canReach(player, this) && authorize(player, MachineAction.CONFIGURE); }
    public UUID fundingActor() {
        if (!(level instanceof ServerLevel serverLevel)) return null;
        var saved = com.mistaboom.essence_ascendance.data.EssenceSavedData.get(serverLevel.getServer());
        if (binding.refresh(saved)) bindingChanged();
        return binding.fundingActor(saved);
    }

    public boolean canPlayerUse(Player player) {
        return authorize(player, MachineAction.USE);
    }

    @Nullable
    public BlockPos linkedCruciblePos() {
        return linkedCruciblePos;
    }

    public boolean isLinkedTo(BlockPos cruciblePos) {
        return isCurrentLinkValid() && linkedCruciblePos != null && linkedCruciblePos.equals(cruciblePos);
    }

    public EssenceFocusTier focusTier() {
        return EssencePylonContent.focusTier(getItem(FOCUS_SLOT));
    }

    public boolean hasInstalledFocus() {
        return EssencePylonContent.isFocusItem(getItem(FOCUS_SLOT));
    }

    public EssencePylonContribution contribution() {
        return EssencePylonContent.contribution(getItem(FOCUS_SLOT));
    }

    public String focusDisplayName() {
        if (!hasInstalledFocus()) return "No Focus";
        EssenceFocusTier tier = focusTier();
        return tier == null ? "Latent Focus" : tier.displayName() + " Focus";
    }

    public MachineLink.Mode linkMode() { return link.mode(); }
    public MachineLink.Source linkedSource() { return link.target(); }
    public MachineLink.State linkState() {
        return level instanceof ServerLevel serverLevel ? link.state(serverLevel, worldPosition, EssenceConfigManager.get().pylonRadius(), binding) : MachineLink.State.UNAVAILABLE;
    }
    public boolean setLink(Player player, MachineLink.Mode mode, MachineLink.Source source) {
        if (!ServerMenuAccess.canReach(player, this) || !authorize(player, MachineAction.LINK)
                || !(level instanceof ServerLevel serverLevel)) return false;
        BlockPos previous = linkedCruciblePos;
        if (!link.configure(serverLevel, worldPosition, EssenceConfigManager.get().pylonRadius(), binding, mode, source)) return false;
        linkedCruciblePos = link.target() == null ? null : link.target().pos();
        linkChanged(previous); refreshLink(); return true;
    }
    public void refreshLink() {
        if (!(level instanceof ServerLevel serverLevel)) return;
        var saved = com.mistaboom.essence_ascendance.data.EssenceSavedData.get(serverLevel.getServer());
        if (binding.refresh(saved)) bindingChanged();
        BlockPos previous = linkedCruciblePos;
        if (link.refresh(serverLevel, worldPosition, EssenceConfigManager.get().pylonRadius(), binding)) {
            linkedCruciblePos = link.target() == null ? null : link.target().pos();
            linkChanged(previous);
        }
    }
    public boolean isCurrentLinkValid() { return linkState() == MachineLink.State.VALID; }
    private boolean isCurrentLinkValid(ServerLevel ignored) { return isCurrentLinkValid(); }
    @Override public void bindingChanged() {
        invalidateLinkedCrucible();
        linkChanged(linkedCruciblePos); setChanged();
    }
    private void linkChanged(BlockPos previous) {
        invalidateCrucibleAt(previous); invalidateCrucibleAt(linkedCruciblePos);
        syncBlockEntity();
    }
    public void invalidateLinkedCrucible() { invalidateCrucibleAt(linkedCruciblePos); }

    private void invalidateCrucibleAt(@Nullable BlockPos pos) {
        if (pos == null || level == null || !level.hasChunkAt(pos)) {
            return;
        }

        if (level.getBlockEntity(pos) instanceof EssenceCrucibleBlockEntity crucible) {
            crucible.invalidateStructureCache();
        }
    }

    public double distanceToLinkedCrucible() {
        if (linkedCruciblePos == null) {
            return Double.NaN;
        }
        return Math.sqrt(worldPosition.distSqr(linkedCruciblePos));
    }

    private void syncBlockEntity() {
        setChanged();
        if (level != null && !level.isClientSide) {
            lastSyncedVisualState = visualState();
            BlockState state = getBlockState();
            boolean lit = hasInstalledFocus();
            if (state.getValue(EssencePylonBlock.FOCUS_LIT) != lit) {
                state = state.setValue(EssencePylonBlock.FOCUS_LIT, lit);
                level.setBlock(worldPosition, state, Block.UPDATE_CLIENTS);
            }
            level.sendBlockUpdated(
                    worldPosition,
                    state,
                    state,
                    Block.UPDATE_CLIENTS
            );
        }
    }

    public InteractionResult installOrSwapFocus(
            Player player,
            InteractionHand hand
    ) {
        if (!ServerMenuAccess.canReach(player, this) || !authorize(player, MachineAction.FOCUS)) {
            player.displayClientMessage(
                    Component.translatable("message.essence_ascendance.pylon_private"),
                    true
            );
            return InteractionResult.FAIL;
        }

        ItemStack held = player.getItemInHand(hand);
        if (!EssencePylonContent.isFocus(held)) {
            return InteractionResult.PASS;
        }

        ItemStack existing = getItem(FOCUS_SLOT).copy();
        if (!existing.isEmpty()
                && ItemStack.isSameItemSameComponents(existing, held)) {
            return InteractionResult.CONSUME;
        }

        ItemStack installed = held.copy();
        installed.setCount(1);
        setItem(FOCUS_SLOT, installed);

        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }

        if (!existing.isEmpty()) {
            if (!player.getAbilities().instabuild && held.isEmpty()) {
                player.setItemInHand(hand, existing);
            } else if (!player.getInventory().add(existing)) {
                player.drop(existing, false);
            }
        }

        return InteractionResult.CONSUME;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.essence_ascendance.essence_pylon");
    }

    @Override
    public AbstractContainerMenu createMenu(
            int containerId,
            Inventory playerInventory,
            Player player
    ) {
        if (!canPlayerUse(player)) {
            return null;
        }
        return new EssencePylonMenu(containerId, playerInventory, this);
    }

    @Override
    protected void saveAdditional(
            CompoundTag tag,
            HolderLookup.Provider registries
    ) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        binding.save(tag);
        link.save(tag);

        if (ownerId != null) {
            tag.putUUID(OWNER_TAG, ownerId);
        }
        if (!ownerName.isBlank()) {
            tag.putString(OWNER_NAME_TAG, ownerName);
        }

    }

    @Override
    protected void loadAdditional(
            CompoundTag tag,
            HolderLookup.Provider registries
    ) {
        super.loadAdditional(tag, registries);
        /* Empty update tags must actively clear a previously-rendered Focus. */
        items.clear();
        ContainerHelper.loadAllItems(tag, items, registries);
        binding.load(tag);
        link.load(tag);

        ownerId = tag.hasUUID(OWNER_TAG) ? tag.getUUID(OWNER_TAG) : null;
        ownerName = tag.getString(OWNER_NAME_TAG);
        linkedCruciblePos = link.target() == null ? null : link.target().pos();
        clientVisualState = tag.contains(MachineVisualState.TAG, net.minecraft.nbt.Tag.TAG_COMPOUND)
                ? MachineVisualState.Pylon.read(tag.getCompound(MachineVisualState.TAG)) : null;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        MachineVisualState.Pylon visual = visualState();
        if (visual != null) tag.put(MachineVisualState.TAG, visual.save());
        return tag;
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this,
                (entity, registries) -> entity.getUpdateTag(registries));
    }

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        return items.get(FOCUS_SLOT).isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot == FOCUS_SLOT ? items.get(FOCUS_SLOT) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (slot != FOCUS_SLOT || amount <= 0) {
            return ItemStack.EMPTY;
        }

        ItemStack removed = ContainerHelper.removeItem(items, slot, amount);
        if (!removed.isEmpty()) {
            invalidateLinkedCrucible();
            syncBlockEntity();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (slot != FOCUS_SLOT) {
            return ItemStack.EMPTY;
        }

        ItemStack removed = ContainerHelper.takeItem(items, slot);
        if (!removed.isEmpty()) {
            invalidateLinkedCrucible();
            /* No client packet: this path is used while the block is being removed. */
            setChanged();
        }
        return removed;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot != FOCUS_SLOT) {
            return;
        }

        ItemStack normalized = stack == null || stack.isEmpty()
                ? ItemStack.EMPTY
                : stack.copy();
        if (!normalized.isEmpty()) {
            normalized.setCount(1);
        }

        items.set(FOCUS_SLOT, normalized);
        invalidateLinkedCrucible();
        syncBlockEntity();
    }

    @Override
    public boolean stillValid(Player player) {
        return ServerMenuAccess.canReach(player, this) && canPlayerUse(player);
    }

    @Override
    public void clearContent() {
        items.clear();
        invalidateLinkedCrucible();
        syncBlockEntity();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == FOCUS_SLOT && EssencePylonContent.isFocus(stack);
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        /* Focus management is intentionally manual; pylons expose no item I/O. */
        return NO_AUTOMATION_SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(
            int slot,
            ItemStack stack,
            Direction direction
    ) {
        return false;
    }

    @Override
    public boolean canTakeItemThroughFace(
            int slot,
            ItemStack stack,
            Direction direction
    ) {
        return false;
    }
}
