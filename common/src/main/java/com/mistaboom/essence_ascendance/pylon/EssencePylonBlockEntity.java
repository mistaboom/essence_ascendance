package com.mistaboom.essence_ascendance.pylon;

import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleBlockEntity;
import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleStructureService;
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
        implements WorldlyContainer, MenuProvider {

    public static final int FOCUS_SLOT = 0;
    private static final int[] NO_AUTOMATION_SLOTS = {};

    private static final String OWNER_TAG = "owner";
    private static final String OWNER_NAME_TAG = "owner_name";
    private static final String LINKED_CRUCIBLE_TAG = "linked_crucible";

    private final NonNullList<ItemStack> items =
            NonNullList.withSize(1, ItemStack.EMPTY);

    private UUID ownerId;
    private String ownerName = "";
    private BlockPos linkedCruciblePos;

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
        }
    }

    public UUID ownerId() {
        return ownerId;
    }

    public String ownerDisplayName() {
        if (!ownerName.isBlank()) {
            return ownerName;
        }
        return ownerId == null ? "Unbound" : ownerId.toString();
    }

    public boolean bindOwner(Player player) {
        if (ownerId != null) {
            return ownerId.equals(player.getUUID());
        }

        ownerId = player.getUUID();
        ownerName = player.getGameProfile().getName();
        syncBlockEntity();
        return true;
    }

    public boolean canPlayerUse(Player player) {
        return ownerId == null || ownerId.equals(player.getUUID());
    }

    @Nullable
    public BlockPos linkedCruciblePos() {
        return linkedCruciblePos;
    }

    public boolean isLinkedTo(BlockPos cruciblePos) {
        return linkedCruciblePos != null && linkedCruciblePos.equals(cruciblePos);
    }

    public EssencePylonFocusTier focusTier() {
        return EssencePylonContent.focusTier(getItem(FOCUS_SLOT));
    }

    public EssencePylonContribution contribution() {
        return EssencePylonContent.contribution(getItem(FOCUS_SLOT));
    }

    public String focusDisplayName() {
        EssencePylonFocusTier tier = focusTier();
        return tier == null ? "Empty / Base Pylon" : tier.displayName() + " Focus";
    }

    public void refreshLink() {
        if (!(level instanceof ServerLevel serverLevel) || ownerId == null) {
            return;
        }

        if (isCurrentLinkValid(serverLevel)) {
            return;
        }

        BlockPos old = linkedCruciblePos;
        BlockPos nearest = findNearestOwnedCrucible(serverLevel);
        if ((old == null && nearest == null)
                || (old != null && old.equals(nearest))) {
            return;
        }

        linkedCruciblePos = nearest == null ? null : nearest.immutable();
        invalidateCrucibleAt(old);
        invalidateCrucibleAt(linkedCruciblePos);
        syncBlockEntity();
    }

    public void invalidateLinkedCrucible() {
        invalidateCrucibleAt(linkedCruciblePos);
    }

    private boolean isCurrentLinkValid(ServerLevel serverLevel) {
        if (linkedCruciblePos == null
                || !serverLevel.hasChunkAt(linkedCruciblePos)) {
            return false;
        }

        double radius = EssenceConfigManager.get().pylonRadius();
        if (worldPosition.distSqr(linkedCruciblePos) > radius * radius) {
            return false;
        }

        if (!(serverLevel.getBlockEntity(linkedCruciblePos)
                instanceof EssenceCrucibleBlockEntity crucible)) {
            return false;
        }

        return crucible.ownerId() != null
                && crucible.ownerId().equals(ownerId);
    }

    @Nullable
    private BlockPos findNearestOwnedCrucible(ServerLevel serverLevel) {
        double radius = EssenceConfigManager.get().pylonRadius();
        int scan = (int) Math.ceil(radius);
        double radiusSquared = radius * radius;

        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        for (int dx = -scan; dx <= scan; dx++) {
            for (int dy = -scan; dy <= scan; dy++) {
                for (int dz = -scan; dz <= scan; dz++) {
                    BlockPos candidate = worldPosition.offset(dx, dy, dz);
                    double distance = worldPosition.distSqr(candidate);
                    if (distance > radiusSquared
                            || !serverLevel.hasChunkAt(candidate)) {
                        continue;
                    }

                    if (!(serverLevel.getBlockEntity(candidate)
                            instanceof EssenceCrucibleBlockEntity crucible)
                            || crucible.ownerId() == null
                            || !crucible.ownerId().equals(ownerId)) {
                        continue;
                    }

                    if (distance < bestDistance
                            || (distance == bestDistance
                                && (best == null || candidate.asLong() < best.asLong()))) {
                        best = candidate.immutable();
                        bestDistance = distance;
                    }
                }
            }
        }

        return best;
    }

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
            BlockState state = getBlockState();
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
        if (!bindOwner(player) || !canPlayerUse(player)) {
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

        if (ownerId != null) {
            tag.putUUID(OWNER_TAG, ownerId);
        }
        if (!ownerName.isBlank()) {
            tag.putString(OWNER_NAME_TAG, ownerName);
        }
        if (linkedCruciblePos != null) {
            tag.putLong(LINKED_CRUCIBLE_TAG, linkedCruciblePos.asLong());
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

        ownerId = tag.hasUUID(OWNER_TAG) ? tag.getUUID(OWNER_TAG) : null;
        ownerName = tag.getString(OWNER_NAME_TAG);
        linkedCruciblePos = tag.contains(LINKED_CRUCIBLE_TAG)
                ? BlockPos.of(tag.getLong(LINKED_CRUCIBLE_TAG))
                : null;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
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
        if (level == null
                || level.getBlockEntity(worldPosition) != this
                || !canPlayerUse(player)) {
            return false;
        }

        return player.distanceToSqr(Vec3.atCenterOf(worldPosition)) <= 64.0D;
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
