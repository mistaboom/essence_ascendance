package com.mistaboom.essence_ascendance.infuser;

import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleEssences;

import com.mistaboom.essence_ascendance.machine.*;
import com.mistaboom.essence_ascendance.network.ServerMenuAccess;
import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleBlockEntity;
import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleChannelService;
import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.data.ReservoirService;
import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import com.mistaboom.essence_ascendance.essence.EssenceRegistry;
import com.mistaboom.essence_ascendance.equipment.EquipmentTierData;
import com.mistaboom.essence_ascendance.equipment.SoulboundEquipmentData;
import com.mistaboom.essence_ascendance.pylon.EssencePylonContent;
import com.mistaboom.essence_ascendance.pylon.EssenceFocusTier;
import com.mistaboom.essence_ascendance.visual.MachineVisualState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.UUID;

public final class EssenceInfuserBlockEntity extends BlockEntity
        implements WorldlyContainer, MenuProvider, BoundMachine {

    public static final int INPUT_SLOT = 0;
    public static final int OUTPUT_SLOT = 1;
    public static final int FOCUS_SLOT = 2;
    public static final int COMPONENT_SLOT = 3;
    public static final int SLOT_COUNT = 4;

    public static final int STATUS_IDLE = 0;
    public static final int STATUS_UNLINKED = 1;
    public static final int STATUS_INVALID_SELECTION = 2;
    public static final int STATUS_INSUFFICIENT_SOURCE = 3;
    public static final int STATUS_OUTPUT_BLOCKED = 4;
    public static final int STATUS_PROCESSING = 5;
    public static final int STATUS_INVALID_INPUT = 6;
    public static final int STATUS_STOPPED = 7;
    public static final int STATUS_FOCUS_TIER_REQUIRED = 9;
    public static final int STATUS_FOCUS_MALFORMED = 10;
    public static final int STATUS_NEEDS_FOCUS = 11;
    public static final int STATUS_COMPONENT_REQUIRED = 12;
    public static final int STATUS_REPAIR_MATERIAL_REQUIRED = 13;
    public static final int STATUS_JOB_CONTEXT_REQUIRED = 14;
    public static final int STATUS_RECIPIENT_REQUIRED = 15;
    public static final int STATUS_WAITING_BANDWIDTH = 16;


    private static final int[] AUTOMATION_SLOTS = {INPUT_SLOT, OUTPUT_SLOT, COMPONENT_SLOT};

    private static final String OWNER_TAG = "owner";
    private static final String OWNER_NAME_TAG = "owner_name";
    private static final String SOURCE_ESSENCE_TAG = "source_essence";
    private static final String TARGET_ESSENCE_TAG = "target_essence";
    private static final String PROCESSING_TICKS_TAG = "processing_ticks";
    private static final String PROCESSING_ENABLED_TAG = "processing_enabled";
    private static final String VISUAL_FOCUS_STACK_TAG = "visual_focus_stack";

    private final NonNullList<ItemStack> items =
            NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);

    private final MachineBinding binding = new MachineBinding();
    private UUID ownerId;
    private String ownerName = "";
    private final MachineLink link = new MachineLink();
    private BlockPos linkedCruciblePos;
    private ResourceLocation sourceEssenceId;
    private ResourceLocation targetEssenceId;
    private int processingTicks;
    private long channelRevision;
    private long grantRemaining;
    private InfusionJobProgress paidJob;
    private long demandMoneyRevision = -1, demandContextRevision = -1, cachedDemand;
    private boolean processingEnabled;
    private boolean processingVisualActive;
    private long lastGrantTick = -1;
    private int lastTransferMask;
    private MachineVisualState.Infuser clientVisualState;
    private MachineVisualState.Infuser lastSyncedVisualState;

    public EssenceInfuserBlockEntity(BlockPos pos, BlockState state) {
        super(EssenceInfuserContent.ESSENCE_INFUSER_BLOCK_ENTITY.get(), pos, state);
    }

    public static void serverTick(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            EssenceInfuserBlockEntity infuser
    ) {
        infuser.processingVisualActive = false;
        var saved = EssenceSavedData.get(level.getServer());
        if (infuser.binding.refresh(saved)) infuser.bindingChanged();
        boolean operatorValid = infuser.binding.operatorValid(saved);
        if (!com.mistaboom.essence_ascendance.team.MembershipAuthority.ready(saved.reservoirs().readiness())) {
            if (level.getGameTime() % 5L == 0L) infuser.syncBlockEntity();
            return;
        }
        if (com.mistaboom.essence_ascendance.team.MembershipAuthority.ready(saved.reservoirs().readiness())
                && infuser.processingEnabled && (!operatorValid || !infuser.binding.operational(saved))) infuser.disarmProcessingForContextChange();
        if (level.getGameTime() % 20L == 0L) {
            infuser.refreshLink();
        }
        infuser.normalizeSelections();
        infuser.tickProcessing(level);
        if (level.getGameTime() % 5L == 0L
                && !infuser.visualState().equals(infuser.lastSyncedVisualState)) {
            infuser.syncBlockEntity();
        }
    }

    public MachineVisualState.Infuser visualState() {
        if (level != null && level.isClientSide) return clientVisualState == null
                ? MachineVisualState.IDLE_INFUSER : clientVisualState;
        int status = displayStatusCode();
        boolean active = status == STATUS_PROCESSING;
        ItemStack input = getItem(INPUT_SLOT);
        var feedback = EssenceCrucibleChannelService.feedback(this);
        long rate = lastGrantTick >= 0 && level != null && level.getGameTime() - lastGrantTick <= 4
                ? com.mistaboom.essence_ascendance.crucible.ConcurrentChannelScheduler.sampledPerSecond(feedback.actual()).min(java.math.BigInteger.valueOf(Long.MAX_VALUE)).longValue() : 0;
        return new MachineVisualState.Infuser(linkedCruciblePos, processingEnabled,
                status, processingTicks, requiredProcessingTicks(), rate,
                input.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(input.getItem()), lastTransferMask,
                new MachineVisualState.Focus(MachineVisualState.Host.INFUSER,
                        hasInstalledFocus(), focusTier(), active && hasInstalledFocus(), rate));
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

    @Nullable
    public EssenceDefinition sourceEssence() {
        return sourceEssenceId == null
                ? null
                : EssenceRegistry.get(sourceEssenceId).orElse(null);
    }

    @Nullable
    public EssenceDefinition targetEssence() {
        return targetEssenceId == null
                ? null
                : EssenceRegistry.get(targetEssenceId).orElse(null);
    }

    public EssenceFocusTier focusTier() {
        return EssencePylonContent.focusTier(getItem(FOCUS_SLOT));
    }

    public boolean hasInstalledFocus() {
        return EssencePylonContent.isFocusItem(getItem(FOCUS_SLOT));
    }

    public EssenceInfuserBalance.Profile profile() {
        return EssenceInfuserBalance.profile(getItem(FOCUS_SLOT));
    }

    public Optional<EssenceInfuserRecipe> currentInfusionRecipe() {
        return EssenceInfuserRecipeRegistry.resolve(getItem(INPUT_SLOT));
    }

    public Optional<FocusInfusionRecipe> focusInfusionRecipe() {
        return currentInfusionRecipe()
                .filter(FocusInfusionRecipe.class::isInstance)
                .map(FocusInfusionRecipe.class::cast);
    }

    private Optional<EssentiumInfusionRecipe> essentiumInfusionRecipe() {
        return currentInfusionRecipe()
                .filter(EssentiumInfusionRecipe.class::isInstance)
                .map(EssentiumInfusionRecipe.class::cast);
    }

    private EssenceInfuserRecipeContext recipeContext() {
        return new EssenceInfuserRecipeContext(
                getItem(INPUT_SLOT),
                sourceEssence(),
                targetEssence(),
                focusTier(),
                profile()
        );
    }

    public boolean focusInfusionMode() {
        return currentInfusionRecipe()
                .map(EssenceInfuserRecipe::workpieceMode)
                .orElse(EssenceInfuserWorkpieceMode.NONE)
                == EssenceInfuserWorkpieceMode.FOCUS;
    }

    public boolean equipmentInfusionMode() {
        return EssenceInfuserWorkpieceMode.forContext(
                getItem(INPUT_SLOT),
                getItem(COMPONENT_SLOT)
        ) == EssenceInfuserWorkpieceMode.EQUIPMENT;
    }

    public Optional<EquipmentInfusionRecipe> equipmentInfusionRecipe() {
        return currentInfusionRecipe()
                .filter(EquipmentInfusionRecipe.class::isInstance)
                .map(EquipmentInfusionRecipe.class::cast);
    }

    public boolean repairMode() {
        return currentInfusionRecipe()
                .map(EssenceInfuserRecipe::workpieceMode)
                .orElse(EssenceInfuserWorkpieceMode.NONE)
                == EssenceInfuserWorkpieceMode.REPAIR;
    }

    public Optional<RepairInfusionRecipe> repairInfusionRecipe() {
        return currentInfusionRecipe()
                .filter(RepairInfusionRecipe.class::isInstance)
                .map(RepairInfusionRecipe.class::cast);
    }

    public int repairLatentIngotRequired() {
        return repairInfusionRecipe()
                .map(RepairInfusionRecipe::latentIngotCount)
                .orElse(0);
    }

    public long equipmentInfusionTotalRequired() {
        return equipmentInfusionRecipe().map(EquipmentInfusionRecipe::totalRequired).orElse(0L);
    }

    public long equipmentInfusionTotalContributed() {
        return EquipmentInfusionData.totalContributed(getItem(INPUT_SLOT));
    }

    public long equipmentInfusionContribution(EssenceDefinition essence) {
        return EquipmentInfusionData.contribution(getItem(INPUT_SLOT), essence);
    }

    public long focusInfusionMinimumPerEssence() {
        return focusInfusionRecipe()
                .map(FocusInfusionRecipe::minimumPerAttributeEssence)
                .orElse(0L);
    }

    public long focusInfusionTotalRequired() {
        return focusInfusionRecipe()
                .map(FocusInfusionRecipe::totalEssenceRequired)
                .orElse(0L);
    }

    public long focusInfusionTotalContributed() {
        return FocusInfusionData.totalContributed(getItem(INPUT_SLOT));
    }

    public long focusInfusionContribution(EssenceDefinition essence) {
        return FocusInfusionData.contribution(getItem(INPUT_SLOT), essence);
    }

    @Nullable
    public EssenceFocusTier focusInfusionTargetTier() {
        return focusInfusionRecipe().map(FocusInfusionRecipe::targetTier).orElse(null);
    }

    @Nullable
    public EssenceFocusTier focusInfusionRequiredInstalledTier() {
        return focusInfusionRecipe()
                .map(FocusInfusionRecipe::requiredInstalledTier)
                .orElse(null);
    }

    public long infusionThroughputPerSecond() {
        return hasInstalledFocus() ? profile().infusionThroughputPerSecond() : 0L;
    }

    public long focusInfusionRatePerSecond() {
        return infusionThroughputPerSecond();
    }

    public int processingTicks() {
        if (paidJob == null) return processingEnabled && currentInfusionRecipe().map(recipe -> recipe.progressModel() == EssenceInfuserProgressModel.TIMED_ATOMIC).orElse(false) ? 0 : processingTicks;
        long total = InfusionPayments.total(paidJob.required()), paid = InfusionPayments.total(paidJob.paid());
        int paidTicks = java.math.BigInteger.valueOf(paid).multiply(java.math.BigInteger.valueOf(requiredProcessingTicks()))
                .divide(java.math.BigInteger.valueOf(total)).intValueExact();
        return Math.min(processingTicks, paidTicks);
    }

    public boolean processingEnabled() {
        return processingEnabled;
    }

    public void setProcessingEnabled(Player player, boolean enabled) {
        setProcessingEnabled(player, enabled, false);
    }

    /** Team equipment requires an explicit, server-validated menu confirmation before its first binding. */
    public boolean requiresSoulbindConfirmation(Player player) {
        if (!canPlayerConfigure(player) || !(level instanceof ServerLevel serverLevel)) return false;
        var membership = binding.resolve(EssenceSavedData.get(serverLevel.getServer()));
        return membership != null && membership.pool().kind() == com.mistaboom.essence_ascendance.team.PoolId.Kind.TEAM
                && currentInfusionRecipe().map(EssenceInfuserRecipe::requiresEquipmentRecipient).orElse(false)
                && !SoulboundEquipmentData.isSoulbound(getItem(INPUT_SLOT));
    }

    /** Package-scoped entry point used only after this menu validates its one-use confirmation receipt. */
    void startConfirmedEquipment(Player player) {
        setProcessingEnabled(player, true, true);
    }

    private void setProcessingEnabled(Player player, boolean enabled, boolean soulbindConfirmed) {
        if (!canPlayerConfigure(player) || ownerId == null
                || enabled && requiresSoulbindConfirmation(player) && !soulbindConfirmed) return;
        UUID previousOperator = binding.operator(), previousRecipient = binding.recipient();
        var recipe = currentInfusionRecipe().orElse(null);
        boolean needsRecipient = recipe != null && recipe.requiresEquipmentRecipient();
        UUID bound = SoulboundEquipmentData.read(getItem(INPUT_SLOT)).map(SoulboundEquipmentData.Binding::ownerId).orElse(null);
        // The workpiece's original owner always wins, even when another member performs an upgrade.
        UUID recipient = needsRecipient ? (bound != null ? bound
                : paidJob != null && paidJob.recipient() != null ? paidJob.recipient() : player.getUUID()) : null;
        if (enabled && (!hasInstalledFocus() || !(level instanceof ServerLevel serverLevel)
                || !binding.start(EssenceSavedData.get(serverLevel.getServer()), player.getUUID(), recipient, needsRecipient ? bound : null))) return;
        if (enabled && needsRecipient && bound == null) {
            ItemStack claimed = getItem(INPUT_SLOT).copy();
            SoulboundEquipmentData.bind(claimed, recipient, player.getUUID().equals(recipient) ? player.getGameProfile().getName() : "");
            items.set(INPUT_SLOT, claimed);
        }
        if (enabled) setChanged();
        if (processingEnabled == enabled) {
            if (!java.util.Objects.equals(previousOperator, binding.operator()) || !java.util.Objects.equals(previousRecipient, binding.recipient())) invalidateChannel();
            return;
        }
        processingEnabled = enabled;
        invalidateChannel();
        syncBlockEntity();
    }

    public int requiredProcessingTicks() {
        EssenceInfuserRecipeContext context = recipeContext();
        return currentInfusionRecipe()
                .map(recipe -> recipe.processingTicks(context))
                .orElse(1);
    }

    public int efficiencyBasisPoints() {
        return hasInstalledFocus() ? profile().efficiencyBasisPoints() : 0;
    }

    public java.math.BigInteger sourceAmountAvailableExact() {
        EssenceDefinition essence = sourceEssence(); UUID actor = fundingActor();
        return essence == null || actor == null ? java.math.BigInteger.ZERO : EssenceSavedData.get(((ServerLevel) level).getServer())
                .reservoirs().read(actor).whole(essence.id());
    }
    public long sourceAmountAvailable() {
        EssenceDefinition source = sourceEssence();
        UUID funding = fundingActor();
        return source == null || funding == null
                ? 0L
                : reservoirAmount(source);
    }

    public long targetCarrierCapacity() {
        EssenceInfuserRecipeContext context = recipeContext();
        return essentiumInfusionRecipe()
                .map(recipe -> recipe.targetCapacity(context))
                .orElse(0L);
    }

    public long sourceRequired() {
        EssenceDefinition source = sourceEssence();
        if (source == null) {
            return 0L;
        }
        EssenceInfuserRecipeContext context = recipeContext();
        Optional<RepairInfusionRecipe> repair = repairInfusionRecipe();
        if (repair.isPresent()) {
            return repair.get().essenceRequired();
        }
        return essentiumInfusionRecipe()
                .map(recipe -> recipe.essenceRequirements(context).minimumFor(source))
                .orElse(0L);
    }

    public int statusCode() {
        if (!hasInstalledFocus()) {
            return STATUS_NEEDS_FOCUS;
        }
        if (!processingEnabled) return STATUS_STOPPED;
        if (level instanceof ServerLevel serverLevel && !binding.operatorValid(EssenceSavedData.get(serverLevel.getServer()))) return STATUS_STOPPED;
        ItemStack input = getItem(INPUT_SLOT);
        if (input.isEmpty()) {
            return STATUS_IDLE;
        }
        if (!isCurrentLinkValid()) {
            return STATUS_UNLINKED;
        }
        Optional<EssenceInfuserRecipe> resolvedRecipe =
                EssenceInfuserRecipeRegistry.resolve(input);
        if (resolvedRecipe.isEmpty()) {
            return STATUS_INVALID_INPUT;
        }
        EssenceInfuserRecipe recipe = resolvedRecipe.get();
        EssenceInfuserRecipeContext context = recipeContext();
        if (recipe.requiresEquipmentRecipient() && level instanceof ServerLevel serverLevel
                && !equipmentRecipientValid(EssenceSavedData.get(serverLevel.getServer()))) return STATUS_RECIPIENT_REQUIRED;
        if (paidJob != null && !paidJob.matches(recipe, context)) return STATUS_JOB_CONTEXT_REQUIRED;
        if (!recipe.installedFocusAllows(context)) {
            return STATUS_FOCUS_TIER_REQUIRED;
        }
        if (recipe instanceof RepairInfusionRecipe repairRecipe) {
            return repairInfusionStatus(repairRecipe);
        }
        if (recipe instanceof FocusInfusionRecipe focusRecipe) {
            return focusInfusionStatus(focusRecipe);
        }
        if (recipe instanceof EquipmentInfusionRecipe equipmentRecipe) {
            return equipmentInfusionStatus(equipmentRecipe);
        }
        if (recipe.progressModel() != EssenceInfuserProgressModel.TIMED_ATOMIC
                || input.getCount() < Math.max(1, recipe.inputCount())) {
            return STATUS_INVALID_INPUT;
        }

        if (recipe instanceof EssentiumInfusionRecipe && !hasValidSelection()) {
            return STATUS_INVALID_SELECTION;
        }

        ItemStack result = recipe.createOutput(context);
        if (result.isEmpty()) {
            return STATUS_INVALID_INPUT;
        }
        if (!canAcceptOutput(result)) {
            return STATUS_OUTPUT_BLOCKED;
        }

        EssenceInfusionRequirements requirements = recipe.essenceRequirements(context);
        if (!validFixedRequirements(requirements)) {
            return STATUS_INVALID_INPUT;
        }
        if (!hasFixedRequirementsAvailable(requirements)) {
            return STATUS_INSUFFICIENT_SOURCE;
        }
        return STATUS_PROCESSING;
    }
    public int displayStatusCode() {
        int status = statusCode();
        return status == STATUS_PROCESSING && level != null && channelDemand() > 0
                && (lastGrantTick < 0 || level.getGameTime() - lastGrantTick >= 4)
                ? STATUS_WAITING_BANDWIDTH : status;
    }

    private int repairInfusionStatus(RepairInfusionRecipe recipe) {
        if (!(level instanceof ServerLevel serverLevel) || !equipmentRecipientValid(EssenceSavedData.get(serverLevel.getServer()))) return STATUS_STOPPED;
        ItemStack workpiece = getItem(INPUT_SLOT);
        if (!RepairInfusionRecipe.isWorkpiece(workpiece)) {
            return STATUS_INVALID_INPUT;
        }
        ItemStack repairedPreview = recipe.createOutput(recipeContext());
        if (repairedPreview.isEmpty() || !canAcceptOutput(repairedPreview)) {
            return STATUS_OUTPUT_BLOCKED;
        }

        EssenceDefinition source = sourceEssence();
        if (source == null) {
            return STATUS_INVALID_SELECTION;
        }

        if (recipe.latentIngotCount() > 0) {
            ItemStack component = getItem(COMPONENT_SLOT);
            if (!component.is(EssenceInfuserContent.LATENT_INGOT.get())
                    || component.getCount() < recipe.latentIngotCount()) {
                return STATUS_REPAIR_MATERIAL_REQUIRED;
            }
        }

        UUID funding = fundingActor();
        if (funding == null) {
            return STATUS_UNLINKED;
        }
        return hasFixedRequirementsAvailable(recipe.essenceRequirements(recipeContext())) ? STATUS_PROCESSING : STATUS_INSUFFICIENT_SOURCE;
    }

    private int focusInfusionStatus(FocusInfusionRecipe recipe) {
        ItemStack workpiece = getItem(INPUT_SLOT);
        if (FocusInfusionData.readValidated(workpiece).isEmpty()) {
            return STATUS_FOCUS_MALFORMED;
        }
        if (!recipe.installedFocusAllows(focusTier())) {
            return STATUS_FOCUS_TIER_REQUIRED;
        }
        ItemStack result = recipe.createOutput();
        if (result.isEmpty()) {
            return STATUS_INVALID_INPUT;
        }
        if (!canAcceptOutput(result)) {
            return STATUS_OUTPUT_BLOCKED;
        }
        if (FocusInfusionData.requirementsMet(workpiece, recipe)) {
            return STATUS_PROCESSING;
        }
        return hasUsefulFocusEssence(recipe)
                ? STATUS_PROCESSING
                : STATUS_INSUFFICIENT_SOURCE;
    }

    private int equipmentInfusionStatus(EquipmentInfusionRecipe recipe) {
        if (!(level instanceof ServerLevel serverLevel) || !equipmentRecipientValid(EssenceSavedData.get(serverLevel.getServer()))) return STATUS_STOPPED;
        ItemStack workpiece = getItem(INPUT_SLOT);
        if (!EquipmentTierData.isAscendanceEquipment(workpiece)
                || workpiece.getCount() != 1
                || EquipmentTierData.tier(workpiece) != recipe.currentTier()
                || !EquipmentInfusionData.validForRecipe(workpiece, recipe)) {
            return STATUS_INVALID_INPUT;
        }
        // Tier upgrades produce one individualized artifact. Do not spend more
        // Essence while the output is occupied, or overwrite an earlier result.
        if (!getItem(OUTPUT_SLOT).isEmpty()) {
            return STATUS_OUTPUT_BLOCKED;
        }
        UUID funding = fundingActor();
        if (funding == null) {
            return STATUS_UNLINKED;
        }
        ItemStack component = getItem(COMPONENT_SLOT);
        if (!component.is(EssenceInfuserContent.ASCENDANCE_MATRIX.get())
                || component.getCount() < recipe.matrixCount()) {
            return STATUS_COMPONENT_REQUIRED;
        }
        if (EquipmentInfusionData.requirementsMet(workpiece, recipe)) {
            return STATUS_PROCESSING;
        }
        for (var entry : recipe.requirements().entrySet()) {
            EssenceDefinition essence = EssenceRegistry.get(entry.getKey()).orElse(null);
            if (essence != null
                    && EquipmentInfusionData.contribution(workpiece, essence) < entry.getValue()
                    && reservoirAmount(essence) > 0L) {
                return STATUS_PROCESSING;
            }
        }
        return STATUS_INSUFFICIENT_SOURCE;
    }

    public MachineLink.Mode linkMode() { return link.mode(); }
    public MachineLink.Source linkedSource() { return link.target(); }
    public MachineLink.State linkState() {
        return level instanceof ServerLevel serverLevel ? link.state(serverLevel, worldPosition, EssenceInfuserBalance.linkRange(), binding) : MachineLink.State.UNAVAILABLE;
    }
    public boolean setLink(Player player, MachineLink.Mode mode, MachineLink.Source source) {
        if (!ServerMenuAccess.canReach(player, this) || !authorize(player, MachineAction.LINK)
                || !(level instanceof ServerLevel serverLevel)) return false;
        BlockPos previous = linkedCruciblePos;
        if (!link.configure(serverLevel, worldPosition, EssenceInfuserBalance.linkRange(), binding, mode, source)) return false;
        linkedCruciblePos = link.target() == null ? null : link.target().pos();
        linkChanged(previous); refreshLink(); return true;
    }
    public void refreshLink() {
        if (!(level instanceof ServerLevel serverLevel)) return;
        var saved = com.mistaboom.essence_ascendance.data.EssenceSavedData.get(serverLevel.getServer());
        if (binding.refresh(saved)) bindingChanged();
        BlockPos previous = linkedCruciblePos;
        if (link.refresh(serverLevel, worldPosition, EssenceInfuserBalance.linkRange(), binding)) {
            linkedCruciblePos = link.target() == null ? null : link.target().pos();
            linkChanged(previous);
        }
    }
    public boolean isCurrentLinkValid() { return linkState() == MachineLink.State.VALID; }
    private boolean isCurrentLinkValid(ServerLevel ignored) { return isCurrentLinkValid(); }
    @Override public void bindingChanged() {
        disarmProcessingForContextChange();
        linkChanged(linkedCruciblePos); setChanged();
    }
    private void linkChanged(BlockPos previous) {
        invalidateChannel();
        syncBlockEntity();
    }


    public void cycleSource(Player actor, int direction) {
        if (canPlayerConfigure(actor)) cycleSelection(true, direction);
    }

    public void cycleTarget(Player actor, int direction) {
        if (canPlayerConfigure(actor)) cycleSelection(false, direction);
    }

    private void cycleSelection(boolean source, int direction) {
        if (focusInfusionMode()) {
            return;
        }
        boolean repair = repairMode();
        List<EssenceDefinition> enabled = allEssences();
        if (enabled.isEmpty() || (!repair && enabled.size() < 2)) {
            return;
        }

        ResourceLocation current = source ? sourceEssenceId : targetEssenceId;
        ResourceLocation other = source ? targetEssenceId : sourceEssenceId;
        int currentIndex = -1;
        for (int i = 0; i < enabled.size(); i++) {
            if (enabled.get(i).id().equals(current)) {
                currentIndex = i;
                break;
            }
        }

        int step = direction < 0 ? -1 : 1;
        for (int attempts = 0; attempts < enabled.size(); attempts++) {
            currentIndex = Math.floorMod(currentIndex + step, enabled.size());
            ResourceLocation candidate = enabled.get(currentIndex).id();
            if (!repair && candidate.equals(other)) {
                continue;
            }
            if (source) {
                sourceEssenceId = candidate;
            } else {
                targetEssenceId = candidate;
            }
            disarmProcessingState();
            syncBlockEntity();
            return;
        }
    }

    private void normalizeSelections() {
        List<EssenceDefinition> enabled = allEssences();
        if (enabled.isEmpty()) {
            return;
        }

        boolean changed = false;
        if (!containsEssence(enabled, sourceEssenceId)) {
            sourceEssenceId = enabled.get(0).id();
            changed = true;
        }

        if (enabled.size() >= 2) {
            if (!containsEssence(enabled, targetEssenceId)
                    || targetEssenceId.equals(sourceEssenceId)) {
                for (EssenceDefinition candidate : enabled) {
                    if (!candidate.id().equals(sourceEssenceId)) {
                        targetEssenceId = candidate.id();
                        changed = true;
                        break;
                    }
                }
            }
        } else if (!containsEssence(enabled, targetEssenceId)) {
            targetEssenceId = enabled.get(0).id();
            changed = true;
        }

        if (changed) {
            resetProcessing();
            setChanged();
        }
    }

    private boolean hasValidSelection() {
        EssenceDefinition source = sourceEssence();
        EssenceDefinition target = targetEssence();
        return source != null
                && target != null
                && !source.id().equals(target.id());
    }

    public static List<EssenceDefinition> allEssences() {
        return List.copyOf(EssenceRegistry.values());
    }

    public static int essenceIndex(@Nullable EssenceDefinition essence) {
        if (essence == null) {
            return -1;
        }
        List<EssenceDefinition> all = allEssences();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id().equals(essence.id())) {
                return i;
            }
        }
        return -1;
    }

    @Nullable
    public static EssenceDefinition essenceAtIndex(int index) {
        List<EssenceDefinition> all = allEssences();
        return index >= 0 && index < all.size() ? all.get(index) : null;
    }

    private static boolean containsEssence(
            List<EssenceDefinition> essences,
            @Nullable ResourceLocation id
    ) {
        if (id == null) {
            return false;
        }
        for (EssenceDefinition essence : essences) {
            if (essence.id().equals(id)) {
                return true;
            }
        }
        return false;
    }

    public long channelRevision() { return channelRevision; }
    private void invalidateChannel() {
        channelRevision = Math.incrementExact(channelRevision);
        EssenceCrucibleChannelService.removeInfuser(this);
    }
    private void tickProcessing(ServerLevel serverLevel) {
        if (!processingEnabled || !hasInstalledFocus() || !isCurrentLinkValid()) {
            EssenceCrucibleChannelService.removeInfuser(this); return;
        }
        var source = serverLevel.getBlockEntity(linkedCruciblePos);
        if (!(source instanceof EssenceCrucibleBlockEntity crucible)) return;
        EssenceCrucibleChannelService.registerInfuser(this, crucible);
        var recipe = currentInfusionRecipe().orElse(null);
        if (recipe == null || statusCode() != STATUS_PROCESSING) return;
        if (recipe.progressModel() == EssenceInfuserProgressModel.TIMED_ATOMIC) {
            int required = Math.max(1, recipe.processingTicks(recipeContext()));
            if (processingTicks < required) { processingTicks++; setChanged(); }
            if (paidJob != null && paidJob.complete() && processingTicks >= required) {
                if (recipe instanceof RepairInfusionRecipe repair) completeRepairInfusion(serverLevel, repair);
                else completeTimedAtomicRecipe(serverLevel, recipe);
            }
        } else if (recipe instanceof FocusInfusionRecipe focus && FocusInfusionData.requirementsMet(getItem(INPUT_SLOT), focus)) {
            completeFocusInfusion(focus);
        } else if (recipe instanceof EquipmentInfusionRecipe equipment && EquipmentInfusionData.requirementsMet(getItem(INPUT_SLOT), equipment)) {
            completeEquipmentInfusion(equipment);
        }
    }
    public long channelDemand() {
        if (statusCode() != STATUS_PROCESSING || !(level instanceof ServerLevel serverLevel)) return 0;
        long money = EssenceSavedData.get(serverLevel.getServer()).reservoirs().revision();
        if (money == demandMoneyRevision && channelRevision == demandContextRevision) return cachedDemand;
        var recipe = currentInfusionRecipe().orElseThrow();
        long demand = 0;
        if (recipe instanceof FocusInfusionRecipe focus) {
            var work = getItem(INPUT_SLOT);
            long room = focus.totalEssenceRequired() - FocusInfusionData.totalContributed(work);
            boolean minimum = !FocusInfusionData.minimumsMet(work, focus);
            for (var type : FocusInfusionRecipe.coreAttributeEssences())
                demand = com.mistaboom.essence_ascendance.crucible.ConcurrentChannelScheduler.saturatedAdd(demand,
                        Math.min(reservoirAmount(type), minimum ? Math.max(0, focus.minimumPerAttributeEssence() - FocusInfusionData.contribution(work, type)) : room));
            // Completing the minimum phase can make further same-grant types usable.
            if (minimum && demand > 0) {
                long stock = 0;
                for (var type : FocusInfusionRecipe.coreAttributeEssences()) stock = com.mistaboom.essence_ascendance.crucible.ConcurrentChannelScheduler.saturatedAdd(stock, reservoirAmount(type));
                demand = Math.min(room, stock);
            } else demand = Math.min(room, demand);
        } else {
            Map<ResourceLocation, Long> requirements = recipe.essenceRequirements(recipeContext()).minimumByEssence();
            var plan = InfusionPayments.plan(requirements, type -> recipe instanceof EquipmentInfusionRecipe
                    ? EquipmentInfusionData.contribution(getItem(INPUT_SLOT), EssenceRegistry.get(type).orElseThrow())
                    : paidJob == null ? 0 : paidJob.paid().getOrDefault(type, 0L),
                    type -> reservoirAmount(EssenceRegistry.get(type).orElseThrow()), Long.MAX_VALUE);
            demand = InfusionPayments.total(plan);
        }
        demandMoneyRevision = money; demandContextRevision = channelRevision; cachedDemand = demand;
        return demand;
    }
    public long deliverChannelGrant(long grant) {
        if (grant <= 0 || statusCode() != STATUS_PROCESSING || !(level instanceof ServerLevel serverLevel)) return 0;
        grantRemaining = grant;
        try {
            var recipe = currentInfusionRecipe().orElseThrow();
            long moved;
            if (recipe instanceof FocusInfusionRecipe focus) moved = transferFocusEssence(serverLevel, focus, grant);
            else if (recipe instanceof EquipmentInfusionRecipe equipment) moved = transferEquipmentEssence(serverLevel, equipment, grant);
            else {
                ItemStack preparedInput = getItem(INPUT_SLOT).copy();
                if (recipe.requiresEquipmentRecipient()) SoulboundEquipmentData.bind(preparedInput, equipmentRecipient(), "");
                var preparedContext = new EssenceInfuserRecipeContext(preparedInput, sourceEssence(), targetEssence(), focusTier(), profile());
                var job = paidJob == null ? InfusionJobProgress.begin(recipe, preparedContext,
                        recipe.requiresEquipmentRecipient() ? equipmentRecipient() : null) : paidJob;
                var costs = InfusionPayments.plan(job.required(), type -> job.paid().getOrDefault(type, 0L),
                        type -> reservoirAmount(EssenceRegistry.get(type).orElseThrow()), grant);
                var next = job.add(costs, binding.pool());
                moved = !costs.isEmpty() && payWithInventory(EssenceSavedData.get(serverLevel.getServer()), costs, () -> {
                    items.set(INPUT_SLOT, preparedInput); paidJob = next;
                })
                        ? InfusionPayments.total(costs) : 0;
            }
            processingVisualActive = moved > 0;
            if (moved > 0) lastGrantTick = serverLevel.getGameTime();
            demandMoneyRevision = -1;
            if (moved > 0) { setChanged(); syncBlockEntity(); }
            return moved;
        } finally { grantRemaining = 0; }
    }
    private void completeRepairInfusion(ServerLevel serverLevel, RepairInfusionRecipe expectedRecipe) {
        if (paidJob == null || !paidJob.complete() || statusCode() != STATUS_PROCESSING
                || !paidJob.matches(expectedRecipe, recipeContext())) return;
        ItemStack repaired = expectedRecipe.createOutput(recipeContext());
        try { SoulboundEquipmentData.bind(repaired, equipmentRecipient(), ""); } catch (RuntimeException failure) { return; }
        if (!canAcceptOutput(repaired)) return;
        if (!payWithInventory(EssenceSavedData.get(serverLevel.getServer()), Map.of(), () -> {
            if (expectedRecipe.latentIngotCount() > 0) {
                ItemStack component = getItem(COMPONENT_SLOT); component.shrink(expectedRecipe.latentIngotCount());
                if (component.isEmpty()) items.set(COMPONENT_SLOT, ItemStack.EMPTY);
            }
            items.set(INPUT_SLOT, ItemStack.EMPTY); items.set(OUTPUT_SLOT, repaired); paidJob = null;
        })) return;
        processingTicks = 0; processingEnabled = false; invalidateChannel(); syncBlockEntity();
    }

    private long transferEquipmentEssence(ServerLevel serverLevel, EquipmentInfusionRecipe recipe, long requestedBudget) {
        if (ownerId == null || requestedBudget <= 0L) return 0L;
        ItemStack workpiece = getItem(INPUT_SLOT).copy();
        Map<net.minecraft.resources.ResourceLocation, Long> costs = new LinkedHashMap<>();
        EquipmentInfusionData.ensure(workpiece, recipe);
        EssenceSavedData saved = EssenceSavedData.get(serverLevel.getServer());
        var costsPlan = InfusionPayments.plan(recipe.requirements(),
                type -> EquipmentInfusionData.contribution(workpiece, EssenceRegistry.get(type).orElseThrow()),
                type -> reservoirAmount(EssenceRegistry.get(type).orElseThrow()), requestedBudget);
        for (var entry : costsPlan.entrySet()) {
            try {
                EquipmentInfusionData.addContribution(workpiece, recipe, EssenceRegistry.get(entry.getKey()).orElseThrow(), entry.getValue());
                SoulboundEquipmentData.bind(workpiece, equipmentRecipient(), "");
            } catch (RuntimeException failure) { return 0; }
        }
        costs.putAll(costsPlan);
        if (!costs.isEmpty()) EquipmentInfusionData.recordFunding(workpiece, binding.pool(), costs);
        long moved = InfusionPayments.total(costs);
        return moved > 0 && payWithInventory(saved, costs, () -> items.set(INPUT_SLOT, workpiece)) ? moved : 0L;
    }

    private void completeEquipmentInfusion(EquipmentInfusionRecipe recipe) {
        ItemStack workpiece = getItem(INPUT_SLOT);
        UUID funding = fundingActor();
        ItemStack component = getItem(COMPONENT_SLOT);
        if (funding == null
                || !EquipmentTierData.isAscendanceEquipment(workpiece)
                || workpiece.getCount() != 1
                || EquipmentTierData.tier(workpiece) != recipe.currentTier()
                || !getItem(OUTPUT_SLOT).isEmpty()
                || !EquipmentInfusionData.requirementsMet(workpiece, recipe)
                || !component.is(EssenceInfuserContent.ASCENDANCE_MATRIX.get())
                || component.getCount() < recipe.matrixCount()) {
            return;
        }
        ItemStack completed = workpiece.copy();
        try {
            EquipmentInfusionData.complete(completed, recipe);
        } catch (RuntimeException failure) {
            EssenceAscendance.LOGGER.error(
                    "Failed to complete equipment infusion; retaining workpiece progress and Matrix", failure);
            return;
        }
        if (!canAcceptOutput(completed)) {
            return;
        }
        if (!(level instanceof ServerLevel serverLevel) || !payWithInventory(EssenceSavedData.get(serverLevel.getServer()), Map.of(), () -> {
            component.shrink(recipe.matrixCount());
            if (component.isEmpty()) items.set(COMPONENT_SLOT, ItemStack.EMPTY);
            items.set(INPUT_SLOT, ItemStack.EMPTY);
            items.set(OUTPUT_SLOT, completed);
        })) return;
        processingVisualActive = false;
        processingTicks = 0;
        processingEnabled = false;
        syncBlockEntity();
    }

    private long transferFocusEssence(
            ServerLevel serverLevel,
            FocusInfusionRecipe recipe,
            long requestedBudget
    ) {
        if (ownerId == null || requestedBudget <= 0L) {
            return 0L;
        }

        ItemStack workpiece = getItem(INPUT_SLOT).copy();
        Map<net.minecraft.resources.ResourceLocation, Long> costs = new LinkedHashMap<>();
        Optional<FocusInfusionData.Progress> progressOptional =
                FocusInfusionData.readValidated(workpiece);
        if (progressOptional.isEmpty()) {
            return 0L;
        }

        long remainingTotal = recipe.totalEssenceRequired()
                - progressOptional.get().totalContributed();
        long budget = Math.min(requestedBudget, Math.max(0L, remainingTotal));
        if (budget <= 0L) {
            return 0L;
        }

        boolean minimumPhase = !FocusInfusionData.minimumsMet(workpiece, recipe);
        EssenceSavedData saved = EssenceSavedData.get(serverLevel.getServer());
        long movedTotal = 0L;

        while (budget > 0L) {
            List<EssenceDefinition> candidates = new ArrayList<>();
            for (EssenceDefinition essence : FocusInfusionRecipe.coreAttributeEssences()) {
                long contribution = FocusInfusionData.contribution(workpiece, essence);
                if (minimumPhase
                        && contribution >= recipe.minimumPerAttributeEssence()) {
                    continue;
                }
                if ((reservoirAmount(essence) - costs.getOrDefault(essence.id(), 0L)) > 0L) {
                    candidates.add(essence);
                }
            }
            if (candidates.isEmpty()) {
                break;
            }

            if (!minimumPhase) {
                candidates.sort(
                        java.util.Comparator
                                .<EssenceDefinition>comparingLong(
                                        essence -> reservoirAmount(essence) - costs.getOrDefault(essence.id(), 0L)
                                )
                                .reversed()
                                .thenComparing(essence -> essence.id().toString())
                );
            }

            long divisor = candidates.size();
            long share = minimumPhase
                    ? Math.max(
                            1L,
                            budget / divisor + (budget % divisor == 0L ? 0L : 1L)
                    )
                    : budget;
            boolean movedThisPass = false;

            for (EssenceDefinition essence : candidates) {
                if (budget <= 0L) {
                    break;
                }
                long contribution = FocusInfusionData.contribution(workpiece, essence);
                long contributionRoom = minimumPhase
                        ? recipe.minimumPerAttributeEssence() - contribution
                        : recipe.totalEssenceRequired() - FocusInfusionData.totalContributed(workpiece);
                if (contributionRoom <= 0L) {
                    continue;
                }
                long available = (reservoirAmount(essence) - costs.getOrDefault(essence.id(), 0L));
                long amount = Math.min(
                        budget,
                        Math.min(share, Math.min(contributionRoom, available))
                );
                if (amount <= 0L) continue;
                try { FocusInfusionData.addContribution(workpiece, recipe, essence, amount); }
                catch (RuntimeException failure) { return 0L; }
                costs.merge(essence.id(), amount, Math::addExact);

                budget -= amount;
                movedTotal = Math.addExact(movedTotal, amount);
                movedThisPass = true;
            }

            if (!movedThisPass) {
                break;
            }

            if (minimumPhase && FocusInfusionData.minimumsMet(workpiece, recipe)) {
                minimumPhase = false;
            }
        }

        if (!costs.isEmpty()) FocusInfusionData.recordFunding(workpiece, binding.pool(), costs);
        return movedTotal > 0 && payWithInventory(saved, costs, () -> items.set(INPUT_SLOT, workpiece)) ? movedTotal : 0L;
    }

    private boolean hasUsefulFocusEssence(FocusInfusionRecipe recipe) {
        UUID funding = fundingActor();
        ItemStack workpiece = getItem(INPUT_SLOT);
        if (funding == null || FocusInfusionData.readValidated(workpiece).isEmpty()) {
            return false;
        }
        if (FocusInfusionData.requirementsMet(workpiece, recipe)) {
            return true;
        }

        boolean minimumPhase = !FocusInfusionData.minimumsMet(workpiece, recipe);
        for (EssenceDefinition essence : FocusInfusionRecipe.coreAttributeEssences()) {
            if (minimumPhase
                    && FocusInfusionData.contribution(workpiece, essence)
                    >= recipe.minimumPerAttributeEssence()) {
                continue;
            }
            if (reservoirAmount(essence) > 0L) {
                return true;
            }
        }
        return false;
    }

    private void completeFocusInfusion(FocusInfusionRecipe recipe) {
        ItemStack workpiece = getItem(INPUT_SLOT);
        if (!hasInstalledFocus()
                || !FocusInfusionData.requirementsMet(workpiece, recipe)
                || !recipe.installedFocusAllows(focusTier())) {
            return;
        }

        ItemStack result = workpiece.copy();
        result.setCount(1);
        try {
            FocusInfusionData.complete(result, recipe);
        } catch (RuntimeException failure) {
            EssenceAscendance.LOGGER.error("Failed to complete Essence Focus infusion", failure);
            return;
        }
        if (!canAcceptOutput(result)) {
            return;
        }

        if (!(level instanceof ServerLevel serverLevel) || !payWithInventory(EssenceSavedData.get(serverLevel.getServer()), Map.of(), () -> {
            items.set(INPUT_SLOT, ItemStack.EMPTY);
            ItemStack output = getItem(OUTPUT_SLOT);
            if (output.isEmpty()) items.set(OUTPUT_SLOT, result); else output.grow(1);
        })) return;
        processingTicks = 0;
        processingEnabled = false;
        syncBlockEntity();
    }

    private void completeTimedAtomicRecipe(
            ServerLevel serverLevel,
            EssenceInfuserRecipe expectedRecipe
    ) {
        if (paidJob == null || !paidJob.complete() || ownerId == null || statusCode() != STATUS_PROCESSING) {
            return;
        }

        EssenceInfuserRecipe current = currentInfusionRecipe().orElse(null);
        if (current == null
                || !current.id().equals(expectedRecipe.id())
                || current.progressModel() != EssenceInfuserProgressModel.TIMED_ATOMIC) {
            return;
        }

        EssenceInfuserRecipeContext context = recipeContext();
        ItemStack result = current.createOutput(context);
        EssenceInfusionRequirements requirements = current.essenceRequirements(context);
        if (result.isEmpty()
                || !canAcceptOutput(result)
                || !validFixedRequirements(requirements)) {
            return;
        }

        EssenceSavedData saved = EssenceSavedData.get(serverLevel.getServer());
        ItemStack input = getItem(INPUT_SLOT);
        int consumed = Math.max(1, current.inputCount());
        if (input.getCount() < consumed) return;
        if (!paidJob.matches(current, context)) return;
        if (!payWithInventory(saved, Map.of(), () -> {
            paidJob = null;
            input.shrink(consumed);
            if (input.isEmpty()) {
                items.set(INPUT_SLOT, ItemStack.EMPTY);
            }

            ItemStack output = getItem(OUTPUT_SLOT);
            if (output.isEmpty()) {
                items.set(OUTPUT_SLOT, result);
            } else {
                output.grow(result.getCount());
            }

        })) return;

        processingTicks = 0;
        invalidateChannel(); // The next carrier is a new demand even if no monetary revision changes.
        syncBlockEntity();
    }

    private boolean validFixedRequirements(EssenceInfusionRequirements requirements) {
        if (requirements == null
                || requirements.totalRequired() <= 0L
                || requirements.hasFlexibleRemainder()
                || requirements.minimumByEssence().isEmpty()) {
            return false;
        }
        for (var entry : requirements.minimumByEssence().entrySet()) {
            EssenceDefinition essence = EssenceRegistry.get(entry.getKey()).orElse(null);
            if (essence == null || entry.getValue() <= 0L) {
                return false;
            }
        }
        return true;
    }

    private boolean hasFixedRequirementsAvailable(EssenceInfusionRequirements requirements) {
        UUID funding = fundingActor();
        if (funding == null || !validFixedRequirements(requirements)) {
            return false;
        }
        if (paidJob != null && paidJob.complete()) return true;
        for (var entry : requirements.minimumByEssence().entrySet()) {
            var essence = EssenceRegistry.get(entry.getKey()).orElse(null);
            long paid = paidJob == null ? 0 : paidJob.paid().getOrDefault(entry.getKey(), 0L);
            if (essence != null && paid < entry.getValue() && reservoirAmount(essence) > 0) return true;
        }
        return false;
    }

    private boolean payWithInventory(EssenceSavedData saved,
                                     Map<net.minecraft.resources.ResourceLocation, Long> costs,
                                     Runnable apply) {
        var recipe = currentInfusionRecipe().orElse(null);
        if (!ServerMenuAccess.isLoaded(this) || !isCurrentLinkValid() || !binding.operatorValid(saved) || !binding.operational(saved)
                || recipe == null || recipe.requiresEquipmentRecipient() && !equipmentRecipientValid(saved)) return false;
        long preparedRevision = channelRevision, preparedBinding = binding.revision();
        long amount = InfusionPayments.total(costs);
        if (amount > grantRemaining) return false;
        UUID actor = binding.operator();
        InfusionJobProgress originalJob = paidJob;
        List<ItemStack> original = items.stream().map(ItemStack::copy).toList();
        int originalTicks = processingTicks;
        boolean originalEnabled = processingEnabled;
        boolean originalVisual = processingVisualActive;
        try {
            boolean committed = saved.reservoirs().debit(actor, costs, new ReservoirService.Mutation() {
                public boolean apply() {
                    // The service has just validated membership/SPEND. Participants must not re-enter it.
                    if (channelRevision != preparedRevision || binding.revision() != preparedBinding
                            || !ServerMenuAccess.isLoaded(EssenceInfuserBlockEntity.this)) return false;
                    apply.run(); return true;
                }
                public void rollback() {
                    for (int i = 0; i < original.size(); i++) items.set(i, original.get(i));
                    paidJob = originalJob;
                    processingTicks = originalTicks;
                    processingEnabled = originalEnabled;
                    processingVisualActive = originalVisual;
                }
            });
            if (committed) {
                grantRemaining -= amount; demandMoneyRevision = -1;
                if (amount > 0) {
                    if (lastGrantTick != level.getGameTime()) lastTransferMask = 0;
                    for (int i = 0; i < EssenceCrucibleEssences.ORDERED.size(); i++)
                        if (costs.getOrDefault(EssenceCrucibleEssences.ORDERED.get(i).id(), 0L) > 0) lastTransferMask |= 1 << i;
                }
            }
            return committed;
        } catch (RuntimeException failure) {
            EssenceAscendance.LOGGER.error("Infuser payment rejected; inventory and reservoir preserved", failure);
            return false;
        }
    }

    private boolean canAcceptOutput(ItemStack result) {
        if (result.isEmpty() || result.getCount() > result.getMaxStackSize()) {
            return false;
        }
        ItemStack output = getItem(OUTPUT_SLOT);
        if (output.isEmpty()) {
            return true;
        }
        return ItemStack.isSameItemSameComponents(output, result)
                && result.getCount() <= output.getMaxStackSize() - output.getCount();
    }

    public boolean installOrSwapFocus(Player player, net.minecraft.world.InteractionHand hand) {
        if (!ServerMenuAccess.canReach(player, this) || !authorize(player, MachineAction.FOCUS)) {
            player.displayClientMessage(
                    Component.translatable("message.essence_ascendance.infuser_private"),
                    true
            );
            return false;
        }

        ItemStack held = player.getItemInHand(hand);
        if (!EssencePylonContent.isFocus(held)) {
            return false;
        }

        ItemStack existing = getItem(FOCUS_SLOT).copy();
        if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, held)) {
            return true;
        }

        ItemStack installed = held.copy();
        installed.setCount(1);
        items.set(FOCUS_SLOT, installed);
        disarmProcessingState();

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

        syncBlockEntity();
        return true;
    }

    private boolean equipmentRecipientValid(EssenceSavedData saved) {
        UUID recipient = equipmentRecipient();
        if (recipient == null) return false;
        return SoulboundEquipmentData.read(getItem(INPUT_SLOT)).isPresent();
    }
    private UUID equipmentRecipient() {
        return SoulboundEquipmentData.read(getItem(INPUT_SLOT)).map(SoulboundEquipmentData.Binding::ownerId)
                .orElse(paidJob != null ? paidJob.recipient() : binding.recipient());
    }
    public boolean hasPaidRepairJob() {
        return paidJob != null && paidJob.recipient() != null && !paidJob.paid().isEmpty();
    }
    /** Own soulbound items only; this grants neither inventory browsing nor any spending capability. */
    public boolean recoverWorkpiece(ServerPlayer player, int slot) {
        if (!ServerMenuAccess.canReach(player, this) || player.server.getPlayerList().getPlayer(player.getUUID()) != player
                || slot != INPUT_SLOT && slot != OUTPUT_SLOT) return false;
        if (!SoulboundEquipmentData.isBoundTo(getItem(slot), player.getUUID())) return false;
        ItemStack recovered = removeItemNoUpdate(slot);
        if (recovered.isEmpty()) return false;
        if (!player.getInventory().add(recovered)) player.drop(recovered, false);
        return true;
    }
    public long reservoirAmount(EssenceDefinition essence) {
        UUID actor = fundingActor();
        return actor == null ? 0 : EssenceSavedData.get(((ServerLevel) level).getServer()).reservoirs().read(actor)
                .whole(essence.id()).min(java.math.BigInteger.valueOf(Long.MAX_VALUE)).longValueExact();
    }
    /**
     * Disarms the machine after a player changes the active recipe context.
     * Partial Focus infusion is stored on the workpiece itself and is not
     * discarded; only the machine's armed state and transient carrier timer
     * are cleared.
     */
    public void disarmProcessingForContextChange() {
        if (disarmProcessingState()) {
            syncBlockEntity();
        }
    }

    private boolean disarmProcessingState() {
        invalidateChannel();
        boolean changed = processingEnabled || processingTicks != 0 || processingVisualActive;
        processingEnabled = false;
        if (paidJob == null) processingTicks = 0;
        processingVisualActive = false;
        if (changed) {
            setChanged();
        }
        return changed;
    }

    private void resetProcessing() {
        invalidateChannel();
        if (paidJob == null && processingTicks != 0) {
            processingTicks = 0;
                setChanged();
        }
    }

    private void syncBlockEntity() {
        setChanged();
        if (level != null && !level.isClientSide) {
            lastSyncedVisualState = visualState();
            BlockState state = getBlockState();
            boolean lit = hasInstalledFocus();
            if (state.getValue(EssenceInfuserBlock.FOCUS_LIT) != lit) {
                state = state.setValue(EssenceInfuserBlock.FOCUS_LIT, lit);
                level.setBlock(worldPosition, state, Block.UPDATE_CLIENTS);
            }
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.essence_ascendance.essence_infuser");
    }

    @Override
    public AbstractContainerMenu createMenu(
            int containerId,
            Inventory playerInventory,
            Player player
    ) {
        return canPlayerUse(player)
                ? new EssenceInfuserMenu(containerId, playerInventory, this)
                : null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
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

        if (sourceEssenceId != null) {
            tag.putString(SOURCE_ESSENCE_TAG, sourceEssenceId.toString());
        }
        if (targetEssenceId != null) {
            tag.putString(TARGET_ESSENCE_TAG, targetEssenceId.toString());
        }
        tag.putInt(PROCESSING_TICKS_TAG, Math.max(0, processingTicks));
        tag.putBoolean(PROCESSING_ENABLED_TAG, processingEnabled);
        if (paidJob != null) tag.put("paid_job", paidJob.save(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.contains(MachineVisualState.TAG, Tag.TAG_COMPOUND)) {
            items.clear();
            if (tag.contains(VISUAL_FOCUS_STACK_TAG, Tag.TAG_COMPOUND)) {
                items.set(FOCUS_SLOT, ItemStack.parseOptional(registries,
                        tag.getCompound(VISUAL_FOCUS_STACK_TAG)));
            }
            clientVisualState = MachineVisualState.Infuser.read(tag.getCompound(MachineVisualState.TAG));
            return;
        }
        super.loadAdditional(tag, registries);
        items.clear();
        ContainerHelper.loadAllItems(tag, items, registries);
        binding.load(tag);
        link.load(tag);
        ownerId = tag.hasUUID(OWNER_TAG) ? tag.getUUID(OWNER_TAG) : null;
        ownerName = tag.getString(OWNER_NAME_TAG);
        linkedCruciblePos = link.target() == null ? null : link.target().pos();
        sourceEssenceId = ResourceLocation.tryParse(tag.getString(SOURCE_ESSENCE_TAG));
        targetEssenceId = ResourceLocation.tryParse(tag.getString(TARGET_ESSENCE_TAG));
        processingTicks = Math.max(0, tag.getInt(PROCESSING_TICKS_TAG));
        processingEnabled = tag.getBoolean(PROCESSING_ENABLED_TAG);
        // Old automated-recipient settings must never claim previously unbound equipment after an update.
        if (EquipmentTierData.isAscendanceEquipment(getItem(INPUT_SLOT)) && !SoulboundEquipmentData.isSoulbound(getItem(INPUT_SLOT))) processingEnabled = false;
        invalidateChannel();
        paidJob = tag.contains("paid_job", Tag.TAG_COMPOUND) ? InfusionJobProgress.load(tag.getCompound("paid_job"), registries) : null;
        processingVisualActive = false;
        clientVisualState = tag.contains(MachineVisualState.TAG, Tag.TAG_COMPOUND)
                ? MachineVisualState.Infuser.read(tag.getCompound(MachineVisualState.TAG)) : null;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        MachineVisualState.Infuser visual = visualState();
        if (visual != null) tag.put(MachineVisualState.TAG, visual.save());
        ItemStack focus = getItem(FOCUS_SLOT);
        if (!focus.isEmpty()) tag.put(VISUAL_FOCUS_STACK_TAG, focus.saveOptional(registries));
        return tag;
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this,
                (entity, registries) -> entity.getUpdateTag(registries));
    }

    @Override public void setRemoved() { EssenceCrucibleChannelService.removeInfuser(this); super.setRemoved(); }

    @Override
    public int getContainerSize() {
        return SLOT_COUNT;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < SLOT_COUNT ? items.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (slot < 0 || slot >= SLOT_COUNT || amount <= 0) {
            return ItemStack.EMPTY;
        }
        if (slot == INPUT_SLOT && hasPaidRepairJob()) return removeItemNoUpdate(slot);
        ItemStack removed = ContainerHelper.removeItem(items, slot, amount);
        if (!removed.isEmpty()) {
            if (slot == INPUT_SLOT && EquipmentTierData.isAscendanceEquipment(removed)) disarmProcessingState();
            else if (slot == INPUT_SLOT || slot == FOCUS_SLOT) resetProcessing();
            syncBlockEntity();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) {
            return ItemStack.EMPTY;
        }
        ItemStack removed;
        if (slot == INPUT_SLOT && hasPaidRepairJob()) {
            // A changed context stays trapped and recoverable with the machine until restored.
            if (!currentInfusionRecipe().map(recipe -> paidJob.matches(recipe, recipeContext())).orElse(false)) return ItemStack.EMPTY;
            removed = paidJob.carry(level.registryAccess(), processingTicks);
            paidJob = null; items.set(slot, ItemStack.EMPTY); processingEnabled = false;
        } else removed = ContainerHelper.takeItem(items, slot);
        if (!removed.isEmpty() && slot == INPUT_SLOT && EquipmentTierData.isAscendanceEquipment(removed)) disarmProcessingState();
        else if (!removed.isEmpty() && (slot == INPUT_SLOT || slot == FOCUS_SLOT)) resetProcessing();
        if (!removed.isEmpty()) {
            syncBlockEntity();
        }
        return removed;
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        int limit = WorldlyContainer.super.getMaxStackSize(stack);
        // Vanilla hopper accounting and loader adapters must see the same limit
        // that setItem enforces, so a transfer cannot silently discard surplus.
        return repairMode() && stack.is(EssenceInfuserContent.LATENT_INGOT.get())
                ? Math.min(limit, Math.max(1, repairLatentIngotRequired())) : limit;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= SLOT_COUNT) {
            return;
        }
        ItemStack normalized = stack == null ? ItemStack.EMPTY : stack;
        if (slot == INPUT_SLOT && !normalized.isEmpty() && !InfusionJobProgress.carried(normalized).isEmpty()) {
            if (paidJob != null) throw new IllegalStateException("Restore or recover the current paid job before inserting another");
            CompoundTag carried = InfusionJobProgress.carried(normalized);
            InfusionJobProgress restored = InfusionJobProgress.load(carried, level.registryAccess());
            normalized = InfusionJobProgress.withoutCarried(normalized);
            if (!ItemStack.matches(normalized, restored.workpiece())) throw new IllegalArgumentException("Paid repair workpiece mismatch");
            paidJob = restored;
            processingTicks = Math.max(0, carried.getInt("processing_ticks"));
            processingEnabled = false;
        }
        if (!normalized.isEmpty()) {
            int limit = slot == FOCUS_SLOT
                    ? 1
                    : (slot == INPUT_SLOT
                    ? workpieceStackLimit(normalized)
                    : getMaxStackSize(normalized));
            normalized.limitSize(limit);
        }
        ItemStack previous = items.get(slot);
        boolean changedType = !ItemStack.isSameItemSameComponents(previous, normalized);
        items.set(slot, normalized);
        if (changedType && slot == INPUT_SLOT) {
            prepareInputRecipient();
            if (EquipmentTierData.isAscendanceEquipment(previous) || EquipmentTierData.isAscendanceEquipment(normalized)) disarmProcessingState();
        }
        if (changedType && (slot == INPUT_SLOT || slot == FOCUS_SLOT || slot == COMPONENT_SLOT)
                && !(slot == INPUT_SLOT && paidJob != null && currentInfusionRecipe()
                .map(recipe -> paidJob.matches(recipe, recipeContext())).orElse(false))) {
            resetProcessing();
        }
        syncBlockEntity();
    }

    /**
     * Fabric Transfer API snapshot write. The adapter enforces slot/capacity rules.
     * Both provisional changes AND rollback must bypass processing/notification
     * side effects; only a successful outer commit may publish those effects.
     * Ordinary menus/hoppers/NeoForge execution continue to use setItem/removeItem.
     */
    public void setItemFromTransferSnapshot(int slot, ItemStack stack) {
        if (slot < 0 || slot >= items.size()) {
            throw new IndexOutOfBoundsException("Machine slot " + slot);
        }
        items.set(slot, stack == null ? ItemStack.EMPTY : stack);
    }

    /** Called once by an adapter after its successful outer transaction closes. */
    public void finishItemTransfer(boolean contextChanged) {
        finishItemTransfer(contextChanged, contextChanged);
    }
    public void finishItemTransfer(boolean contextChanged, boolean inputChanged) {
        if (contextChanged) {
            prepareInputRecipient();
            if (inputChanged && EquipmentTierData.isAscendanceEquipment(getItem(INPUT_SLOT))) disarmProcessingState();
            else resetProcessing();
        }
        syncBlockEntity();
    }
    private void prepareInputRecipient() {
        UUID bound = SoulboundEquipmentData.read(getItem(INPUT_SLOT)).map(SoulboundEquipmentData.Binding::ownerId).orElse(null);
        binding.workpieceRecipient(bound);
    }

    @Override
    public boolean stillValid(Player player) {
        return ServerMenuAccess.canReach(player, this) && canPlayerUse(player);
    }

    @Override
    public void clearContent() {
        if (paidJob != null || !isEmpty()) throw new IllegalStateException("Contents must be recovered or picked up with their machine");
        items.clear();
        resetProcessing();
        syncBlockEntity();
    }
    /** Trusted physical-custody move, called only after the complete pickup snapshot exists. */
    public void releasePickupContents() {
        invalidateChannel(); items.clear(); paidJob = null; processingEnabled = false;
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return AUTOMATION_SLOTS;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot == FOCUS_SLOT) {
            return EssencePylonContent.isFocus(stack);
        }
        if (slot == COMPONENT_SLOT) {
            if (stack == null || stack.isEmpty()) {
                return false;
            }
            if (repairMode()) {
                return repairLatentIngotRequired() > 0
                        && stack.is(EssenceInfuserContent.LATENT_INGOT.get());
            }
            return equipmentInfusionMode()
                    && stack.is(EssenceInfuserContent.ASCENDANCE_MATRIX.get());
        }
        return slot == INPUT_SLOT && isValidWorkpiece(stack);
    }

    @Override
    public boolean canPlaceItemThroughFace(
            int slot,
            ItemStack stack,
            @Nullable Direction direction
    ) {
        if (!canAcceptAutomatedStack(stack)) return false;
        // Vanilla transfers grow an occupied stack directly and insert an empty
        // slot wholesale. Reject batches that cannot fit instead of truncating them.
        if (slot == COMPONENT_SLOT) return canPlaceItem(slot, stack)
                && stack.getCount() <= getMaxStackSize(stack) - getItem(slot).getCount();
        if (slot != INPUT_SLOT || !InfusionJobProgress.carried(stack).isEmpty()) return false;
        var recipe = EssenceInfuserRecipeRegistry.resolve(stack).orElse(null);
        if (recipe == null) return false;
        // Equipment may be delivered automatically, but starting its work is an explicit player action.
        return recipe.allowsAutomationInput() || recipe.requiresEquipmentRecipient();
    }

    @Override
    public boolean canTakeItemThroughFace(
            int slot,
            ItemStack stack,
            Direction direction
    ) {
        return automationExposed(false) && slot == OUTPUT_SLOT;
    }

    public static boolean isLatentCarrier(ItemStack stack) {
        return EssenceInfuserRecipeRegistry.modeFor(stack)
                == EssenceInfuserWorkpieceMode.ESSENTIUM;
    }

    public static boolean isFocusWorkpiece(ItemStack stack) {
        return EssenceInfuserRecipeRegistry.modeFor(stack)
                == EssenceInfuserWorkpieceMode.FOCUS;
    }

    public static boolean isValidWorkpiece(ItemStack stack) {
        return EssenceInfuserRecipeRegistry.isValidWorkpiece(stack);
    }

    public static int workpieceStackLimit(ItemStack stack) {
        return EssenceInfuserRecipeRegistry.workpieceStackLimit(stack);
    }

    public static boolean allowsAutomationInput(ItemStack stack) {
        return EssenceInfuserRecipeRegistry.allowsAutomationInput(stack);
    }

    public static boolean allowsAutomationComponent(ItemStack stack) {
        return stack != null && stack.is(EssenceInfuserContent.ASCENDANCE_MATRIX.get());
    }

}
