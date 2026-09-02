package com.mistaboom.essence_ascendance.infuser;

import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import com.mistaboom.essence_ascendance.pylon.EssencePylonFocusTier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class EssenceInfuserMenu extends AbstractContainerMenu {

    public static final int BUTTON_SOURCE_PREVIOUS = 0;
    public static final int BUTTON_SOURCE_NEXT = 1;
    public static final int BUTTON_TARGET_PREVIOUS = 2;
    public static final int BUTTON_TARGET_NEXT = 3;
    public static final int BUTTON_TOGGLE_PROCESSING = 4;

    private static final int DATA_PROCESSING_TICKS = 0;
    private static final int DATA_REQUIRED_TICKS = 1;
    private static final int DATA_EFFICIENCY = 2;
    private static final int DATA_GRADE = 3;
    private static final int DATA_STATUS = 4;
    private static final int DATA_SOURCE_INDEX = 5;
    private static final int DATA_TARGET_INDEX = 6;

    /*
     * Vanilla container-data synchronization carries each property as a 16-bit
     * value. Long-valued Essence amounts and packed BlockPos values therefore
     * have to be split into four 16-bit words rather than two 32-bit halves.
     */
    private static final int DATA_SOURCE_AVAILABLE_0 = 7;
    private static final int DATA_SOURCE_AVAILABLE_1 = 8;
    private static final int DATA_SOURCE_AVAILABLE_2 = 9;
    private static final int DATA_SOURCE_AVAILABLE_3 = 10;
    private static final int DATA_LINKED_POS_0 = 11;
    private static final int DATA_LINKED_POS_1 = 12;
    private static final int DATA_LINKED_POS_2 = 13;
    private static final int DATA_LINKED_POS_3 = 14;
    private static final int DATA_LINKED = 15;
    private static final int DATA_SOURCE_REQUIRED_0 = 16;
    private static final int DATA_SOURCE_REQUIRED_1 = 17;
    private static final int DATA_SOURCE_REQUIRED_2 = 18;
    private static final int DATA_SOURCE_REQUIRED_3 = 19;
    private static final int DATA_TARGET_CAPACITY_0 = 20;
    private static final int DATA_TARGET_CAPACITY_1 = 21;
    private static final int DATA_TARGET_CAPACITY_2 = 22;
    private static final int DATA_TARGET_CAPACITY_3 = 23;
    private static final int DATA_FOCUS_TIER = 24;
    private static final int DATA_PROCESSING_ENABLED = 25;
    private static final int DATA_FOCUS_MODE = 26;
    private static final int DATA_FOCUS_MINIMUM_0 = 27;
    private static final int DATA_FOCUS_MINIMUM_1 = 28;
    private static final int DATA_FOCUS_MINIMUM_2 = 29;
    private static final int DATA_FOCUS_MINIMUM_3 = 30;
    private static final int DATA_FOCUS_TOTAL_0 = 31;
    private static final int DATA_FOCUS_TOTAL_1 = 32;
    private static final int DATA_FOCUS_TOTAL_2 = 33;
    private static final int DATA_FOCUS_TOTAL_3 = 34;
    private static final int DATA_FOCUS_RATE_0 = 35;
    private static final int DATA_FOCUS_RATE_1 = 36;
    private static final int DATA_FOCUS_RATE_2 = 37;
    private static final int DATA_FOCUS_RATE_3 = 38;
    private static final int DATA_FOCUS_TARGET_TIER = 39;
    private static final int DATA_FOCUS_REQUIRED_INSTALLED_TIER = 40;
    private static final int DATA_COUNT = 41;

    public static final int MACHINE_SLOT_COUNT = 3;
    public static final int PLAYER_INVENTORY_START = MACHINE_SLOT_COUNT;
    public static final int PLAYER_INVENTORY_END = PLAYER_INVENTORY_START + 36;

    private final Container infuserContainer;
    private final EssenceInfuserBlockEntity serverInfuser;
    private final ContainerData data;

    public EssenceInfuserMenu(int containerId, Inventory playerInventory) {
        this(
                containerId,
                playerInventory,
                new SimpleContainer(EssenceInfuserBlockEntity.SLOT_COUNT),
                null,
                new SimpleContainerData(DATA_COUNT)
        );
    }

    public EssenceInfuserMenu(
            int containerId,
            Inventory playerInventory,
            EssenceInfuserBlockEntity infuser
    ) {
        this(
                containerId,
                playerInventory,
                infuser,
                infuser,
                serverData(infuser)
        );
    }

    private EssenceInfuserMenu(
            int containerId,
            Inventory playerInventory,
            Container container,
            EssenceInfuserBlockEntity serverInfuser,
            ContainerData data
    ) {
        super(EssenceInfuserContent.ESSENCE_INFUSER_MENU.get(), containerId);
        checkContainerSize(container, EssenceInfuserBlockEntity.SLOT_COUNT);
        checkContainerDataCount(data, DATA_COUNT);
        this.infuserContainer = container;
        this.serverInfuser = serverInfuser;
        this.data = data;

        container.startOpen(playerInventory.player);

        addSlot(new Slot(container, EssenceInfuserBlockEntity.INPUT_SLOT, 64, 60) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return EssenceInfuserBlockEntity.isValidWorkpiece(stack);
            }

            @Override
            public int getMaxStackSize(ItemStack stack) {
                return EssenceInfuserBlockEntity.workpieceStackLimit(stack);
            }
        });
        addSlot(new Slot(container, EssenceInfuserBlockEntity.OUTPUT_SLOT, 148, 60) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
        addSlot(new Slot(container, EssenceInfuserBlockEntity.FOCUS_SLOT, 106, 24) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return com.mistaboom.essence_ascendance.pylon.EssencePylonContent.isFocus(stack);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(
                        playerInventory,
                        column + row * 9 + 9,
                        34 + column * 18,
                        234 + row * 18
                ));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(
                    playerInventory,
                    column,
                    34 + column * 18,
                    292
            ));
        }

        addDataSlots(data);
    }

    private static ContainerData serverData(EssenceInfuserBlockEntity infuser) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case DATA_PROCESSING_TICKS -> infuser.processingTicks();
                    case DATA_REQUIRED_TICKS -> infuser.requiredProcessingTicks();
                    case DATA_EFFICIENCY -> infuser.efficiencyBasisPoints();
                    case DATA_GRADE -> infuser.profile().grade().ordinal();
                    case DATA_STATUS -> infuser.statusCode();
                    case DATA_SOURCE_INDEX -> EssenceInfuserBlockEntity.essenceIndex(infuser.sourceEssence());
                    case DATA_TARGET_INDEX -> EssenceInfuserBlockEntity.essenceIndex(infuser.targetEssence());
                    case DATA_SOURCE_AVAILABLE_0 -> word(infuser.sourceAmountAvailable(), 0);
                    case DATA_SOURCE_AVAILABLE_1 -> word(infuser.sourceAmountAvailable(), 16);
                    case DATA_SOURCE_AVAILABLE_2 -> word(infuser.sourceAmountAvailable(), 32);
                    case DATA_SOURCE_AVAILABLE_3 -> word(infuser.sourceAmountAvailable(), 48);
                    case DATA_LINKED_POS_0 -> infuser.linkedCruciblePos() == null
                            ? 0
                            : word(infuser.linkedCruciblePos().asLong(), 0);
                    case DATA_LINKED_POS_1 -> infuser.linkedCruciblePos() == null
                            ? 0
                            : word(infuser.linkedCruciblePos().asLong(), 16);
                    case DATA_LINKED_POS_2 -> infuser.linkedCruciblePos() == null
                            ? 0
                            : word(infuser.linkedCruciblePos().asLong(), 32);
                    case DATA_LINKED_POS_3 -> infuser.linkedCruciblePos() == null
                            ? 0
                            : word(infuser.linkedCruciblePos().asLong(), 48);
                    case DATA_LINKED -> infuser.isCurrentLinkValid() ? 1 : 0;
                    case DATA_SOURCE_REQUIRED_0 -> word(infuser.sourceRequired(), 0);
                    case DATA_SOURCE_REQUIRED_1 -> word(infuser.sourceRequired(), 16);
                    case DATA_SOURCE_REQUIRED_2 -> word(infuser.sourceRequired(), 32);
                    case DATA_SOURCE_REQUIRED_3 -> word(infuser.sourceRequired(), 48);
                    case DATA_TARGET_CAPACITY_0 -> word(infuser.targetCarrierCapacity(), 0);
                    case DATA_TARGET_CAPACITY_1 -> word(infuser.targetCarrierCapacity(), 16);
                    case DATA_TARGET_CAPACITY_2 -> word(infuser.targetCarrierCapacity(), 32);
                    case DATA_TARGET_CAPACITY_3 -> word(infuser.targetCarrierCapacity(), 48);
                    case DATA_FOCUS_TIER -> infuser.focusTier() == null
                            ? -1
                            : infuser.focusTier().ordinal();
                    case DATA_PROCESSING_ENABLED -> infuser.processingEnabled() ? 1 : 0;
                    case DATA_FOCUS_MODE -> infuser.focusInfusionMode() ? 1 : 0;
                    case DATA_FOCUS_MINIMUM_0 -> word(infuser.focusInfusionMinimumPerEssence(), 0);
                    case DATA_FOCUS_MINIMUM_1 -> word(infuser.focusInfusionMinimumPerEssence(), 16);
                    case DATA_FOCUS_MINIMUM_2 -> word(infuser.focusInfusionMinimumPerEssence(), 32);
                    case DATA_FOCUS_MINIMUM_3 -> word(infuser.focusInfusionMinimumPerEssence(), 48);
                    case DATA_FOCUS_TOTAL_0 -> word(infuser.focusInfusionTotalRequired(), 0);
                    case DATA_FOCUS_TOTAL_1 -> word(infuser.focusInfusionTotalRequired(), 16);
                    case DATA_FOCUS_TOTAL_2 -> word(infuser.focusInfusionTotalRequired(), 32);
                    case DATA_FOCUS_TOTAL_3 -> word(infuser.focusInfusionTotalRequired(), 48);
                    case DATA_FOCUS_RATE_0 -> word(infuser.focusInfusionRatePerSecond(), 0);
                    case DATA_FOCUS_RATE_1 -> word(infuser.focusInfusionRatePerSecond(), 16);
                    case DATA_FOCUS_RATE_2 -> word(infuser.focusInfusionRatePerSecond(), 32);
                    case DATA_FOCUS_RATE_3 -> word(infuser.focusInfusionRatePerSecond(), 48);
                    case DATA_FOCUS_TARGET_TIER -> infuser.focusInfusionTargetTier() == null
                            ? -1
                            : infuser.focusInfusionTargetTier().ordinal();
                    case DATA_FOCUS_REQUIRED_INSTALLED_TIER -> infuser.focusInfusionRequiredInstalledTier() == null
                            ? -1
                            : infuser.focusInfusionRequiredInstalledTier().ordinal();
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                // Client synchronization target only; authoritative state lives on the BE.
            }

            @Override
            public int getCount() {
                return DATA_COUNT;
            }
        };
    }

    private static int word(long value, int shift) {
        return (int) ((value >>> shift) & 0xFFFFL);
    }

    private static long combineWords(int word0, int word1, int word2, int word3) {
        return (word0 & 0xFFFFL)
                | ((word1 & 0xFFFFL) << 16)
                | ((word2 & 0xFFFFL) << 32)
                | ((word3 & 0xFFFFL) << 48);
    }

    public int processingTicks() {
        return data.get(DATA_PROCESSING_TICKS);
    }

    public int requiredProcessingTicks() {
        return Math.max(1, data.get(DATA_REQUIRED_TICKS));
    }

    public int efficiencyBasisPoints() {
        return data.get(DATA_EFFICIENCY);
    }

    public int statusCode() {
        return data.get(DATA_STATUS);
    }

    public boolean processingEnabled() {
        return data.get(DATA_PROCESSING_ENABLED) != 0;
    }

    public boolean focusInfusionMode() {
        return data.get(DATA_FOCUS_MODE) != 0;
    }

    public long focusMinimumPerEssence() {
        return combineWords(
                data.get(DATA_FOCUS_MINIMUM_0),
                data.get(DATA_FOCUS_MINIMUM_1),
                data.get(DATA_FOCUS_MINIMUM_2),
                data.get(DATA_FOCUS_MINIMUM_3)
        );
    }

    public long focusTotalRequired() {
        return combineWords(
                data.get(DATA_FOCUS_TOTAL_0),
                data.get(DATA_FOCUS_TOTAL_1),
                data.get(DATA_FOCUS_TOTAL_2),
                data.get(DATA_FOCUS_TOTAL_3)
        );
    }

    public long focusInfusionRatePerSecond() {
        return combineWords(
                data.get(DATA_FOCUS_RATE_0),
                data.get(DATA_FOCUS_RATE_1),
                data.get(DATA_FOCUS_RATE_2),
                data.get(DATA_FOCUS_RATE_3)
        );
    }

    public EssencePylonFocusTier focusTargetTier() {
        int ordinal = data.get(DATA_FOCUS_TARGET_TIER);
        EssencePylonFocusTier[] values = EssencePylonFocusTier.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : null;
    }

    public EssencePylonFocusTier focusRequiredInstalledTier() {
        int ordinal = data.get(DATA_FOCUS_REQUIRED_INSTALLED_TIER);
        EssencePylonFocusTier[] values = EssencePylonFocusTier.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : null;
    }

    public ItemStack workpieceStack() {
        return getSlot(EssenceInfuserBlockEntity.INPUT_SLOT).getItem();
    }

    public EssenceInfuserWorkpieceMode workpieceMode() {
        return EssenceInfuserWorkpieceMode.forStack(workpieceStack());
    }

    public long focusContribution(EssenceDefinition essence) {
        return FocusInfusionData.rawContribution(workpieceStack(), essence);
    }

    public long focusTotalContributed() {
        return FocusInfusionData.rawTotalContributed(workpieceStack());
    }

    public long sourceAvailable() {
        return combineWords(
                data.get(DATA_SOURCE_AVAILABLE_0),
                data.get(DATA_SOURCE_AVAILABLE_1),
                data.get(DATA_SOURCE_AVAILABLE_2),
                data.get(DATA_SOURCE_AVAILABLE_3)
        );
    }

    public long sourceRequired() {
        return combineWords(
                data.get(DATA_SOURCE_REQUIRED_0),
                data.get(DATA_SOURCE_REQUIRED_1),
                data.get(DATA_SOURCE_REQUIRED_2),
                data.get(DATA_SOURCE_REQUIRED_3)
        );
    }

    public long targetCapacity() {
        return combineWords(
                data.get(DATA_TARGET_CAPACITY_0),
                data.get(DATA_TARGET_CAPACITY_1),
                data.get(DATA_TARGET_CAPACITY_2),
                data.get(DATA_TARGET_CAPACITY_3)
        );
    }

    public boolean linked() {
        return data.get(DATA_LINKED) != 0;
    }

    public BlockPos linkedPos() {
        return BlockPos.of(combineWords(
                data.get(DATA_LINKED_POS_0),
                data.get(DATA_LINKED_POS_1),
                data.get(DATA_LINKED_POS_2),
                data.get(DATA_LINKED_POS_3)
        ));
    }

    public EssencePylonFocusTier grade() {
        int ordinal = data.get(DATA_GRADE);
        EssencePylonFocusTier[] values = EssencePylonFocusTier.values();
        return ordinal >= 0 && ordinal < values.length
                ? values[ordinal]
                : EssencePylonFocusTier.DORMANT;
    }

    public EssencePylonFocusTier installedFocusTier() {
        int ordinal = data.get(DATA_FOCUS_TIER);
        EssencePylonFocusTier[] values = EssencePylonFocusTier.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : null;
    }

    public EssenceDefinition sourceEssence() {
        return EssenceInfuserBlockEntity.essenceAtIndex(data.get(DATA_SOURCE_INDEX));
    }

    public EssenceDefinition targetEssence() {
        return EssenceInfuserBlockEntity.essenceAtIndex(data.get(DATA_TARGET_INDEX));
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (serverInfuser == null || !serverInfuser.canPlayerUse(player)) {
            return false;
        }
        switch (id) {
            case BUTTON_SOURCE_PREVIOUS -> serverInfuser.cycleSource(-1);
            case BUTTON_SOURCE_NEXT -> serverInfuser.cycleSource(1);
            case BUTTON_TARGET_PREVIOUS -> serverInfuser.cycleTarget(-1);
            case BUTTON_TARGET_NEXT -> serverInfuser.cycleTarget(1);
            case BUTTON_TOGGLE_PROCESSING -> {
                if (serverInfuser.processingEnabled()
                        && serverInfuser.statusCode()
                                == EssenceInfuserBlockEntity.STATUS_PLAYER_CHANNELING) {
                    serverInfuser.setProcessingEnabled(player, true);
                } else {
                    serverInfuser.setProcessingEnabled(
                            player,
                            !serverInfuser.processingEnabled()
                    );
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }


    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        ItemStack inputBefore = serverInfuser == null
                ? ItemStack.EMPTY
                : serverInfuser.getItem(EssenceInfuserBlockEntity.INPUT_SLOT).copy();
        ItemStack focusBefore = serverInfuser == null
                ? ItemStack.EMPTY
                : serverInfuser.getItem(EssenceInfuserBlockEntity.FOCUS_SLOT).copy();

        super.clicked(slotId, button, clickType, player);

        if (serverInfuser == null) {
            return;
        }
        ItemStack inputAfter = serverInfuser.getItem(EssenceInfuserBlockEntity.INPUT_SLOT);
        ItemStack focusAfter = serverInfuser.getItem(EssenceInfuserBlockEntity.FOCUS_SLOT);
        if (!sameContextStack(inputBefore, inputAfter)
                || !sameContextStack(focusBefore, focusAfter)) {
            serverInfuser.disarmProcessingForContextChange();
        }
    }

    private static boolean sameContextStack(ItemStack first, ItemStack second) {
        if (first.isEmpty() || second.isEmpty()) {
            return first.isEmpty() && second.isEmpty();
        }
        return ItemStack.isSameItemSameComponents(first, second);
    }

    @Override
    public boolean stillValid(Player player) {
        return serverInfuser == null || serverInfuser.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack source = slot.getItem();
        ItemStack copy = source.copy();
        if (index < MACHINE_SLOT_COUNT) {
            if (!moveItemStackTo(source, PLAYER_INVENTORY_START, PLAYER_INVENTORY_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (com.mistaboom.essence_ascendance.pylon.EssencePylonContent.isFocus(source)) {
            boolean moved = moveItemStackTo(
                    source,
                    EssenceInfuserBlockEntity.FOCUS_SLOT,
                    EssenceInfuserBlockEntity.FOCUS_SLOT + 1,
                    false
            );
            if (!moved && EssenceInfuserBlockEntity.isFocusWorkpiece(source)) {
                moved = moveItemStackTo(
                        source,
                        EssenceInfuserBlockEntity.INPUT_SLOT,
                        EssenceInfuserBlockEntity.INPUT_SLOT + 1,
                        false
                );
            }
            if (!moved) {
                return ItemStack.EMPTY;
            }
        } else if (EssenceInfuserBlockEntity.isValidWorkpiece(source)) {
            if (!moveItemStackTo(
                    source,
                    EssenceInfuserBlockEntity.INPUT_SLOT,
                    EssenceInfuserBlockEntity.INPUT_SLOT + 1,
                    false
            )) {
                return ItemStack.EMPTY;
            }
        } else {
            return ItemStack.EMPTY;
        }

        if (source.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (source.getCount() == copy.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, source);
        return copy;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        infuserContainer.stopOpen(player);
    }
}
