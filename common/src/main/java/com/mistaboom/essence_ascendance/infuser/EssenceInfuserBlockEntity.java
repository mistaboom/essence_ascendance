package com.mistaboom.essence_ascendance.infuser;

import com.mistaboom.essence_ascendance.network.ServerMenuAccess;
import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleBlockEntity;
import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleChannelService;
import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import com.mistaboom.essence_ascendance.essence.EssenceFamily;
import com.mistaboom.essence_ascendance.essence.EssenceRegistry;
import com.mistaboom.essence_ascendance.equipment.EquipmentTierData;
import com.mistaboom.essence_ascendance.equipment.SoulboundEquipmentData;
import com.mistaboom.essence_ascendance.pylon.EssencePylonContent;
import com.mistaboom.essence_ascendance.pylon.EssenceFocusTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class EssenceInfuserBlockEntity extends BlockEntity
        implements WorldlyContainer, MenuProvider {

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
    public static final int STATUS_PLAYER_CHANNELING = 8;
    public static final int STATUS_FOCUS_TIER_REQUIRED = 9;
    public static final int STATUS_FOCUS_MALFORMED = 10;
    public static final int STATUS_COMPONENT_REQUIRED = 12;
    public static final int STATUS_REPAIR_MATERIAL_REQUIRED = 13;

    private static final int FOCUS_INFUSION_INTERVAL_TICKS = 4;

    private static final int[] AUTOMATION_SLOTS = {INPUT_SLOT, OUTPUT_SLOT, COMPONENT_SLOT};

    private static final String OWNER_TAG = "owner";
    private static final String OWNER_NAME_TAG = "owner_name";
    private static final String LINKED_CRUCIBLE_TAG = "linked_crucible";
    private static final String SOURCE_ESSENCE_TAG = "source_essence";
    private static final String TARGET_ESSENCE_TAG = "target_essence";
    private static final String PROCESSING_TICKS_TAG = "processing_ticks";
    private static final String PROCESSING_ENABLED_TAG = "processing_enabled";

    private final NonNullList<ItemStack> items =
            NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);

    private UUID ownerId;
    private String ownerName = "";
    private BlockPos linkedCruciblePos;
    private ResourceLocation sourceEssenceId;
    private ResourceLocation targetEssenceId;
    private int processingTicks;
    private int focusThroughputRemainderTwentieths;
    private boolean processingEnabled;
    private boolean processingVisualActive;

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
        if (level.getGameTime() % 20L == 0L) {
            infuser.refreshLink();
        }
        infuser.normalizeSelections();
        infuser.tickProcessing(level);
        if (infuser.processingVisualActive && level.getGameTime() % 4L == 0L) {
            infuser.spawnTransferParticles(level);
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
        normalizeSelections();
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
        return profile().infusionThroughputPerSecond();
    }

    public long focusInfusionRatePerSecond() {
        return infusionThroughputPerSecond();
    }

    public int processingTicks() {
        return processingTicks;
    }

    public boolean processingEnabled() {
        return processingEnabled;
    }

    public void setProcessingEnabled(Player player, boolean enabled) {
        if (!bindOwner(player) || ownerId == null) {
            return;
        }
        if (enabled && player instanceof ServerPlayer serverPlayer) {
            /*
             * Start/Resume Processing is the explicit request to give this
             * machine priority. This also works when the Infuser was already
             * enabled but temporarily paused by player channeling.
             */
            EssenceCrucibleChannelService.stopForPlayer(serverPlayer);
        }
        if (processingEnabled == enabled) {
            return;
        }
        processingEnabled = enabled;
        focusThroughputRemainderTwentieths = 0;
        syncBlockEntity();
    }

    public int requiredProcessingTicks() {
        EssenceInfuserRecipeContext context = recipeContext();
        return currentInfusionRecipe()
                .map(recipe -> recipe.processingTicks(context))
                .orElse(1);
    }

    public int efficiencyBasisPoints() {
        return profile().efficiencyBasisPoints();
    }

    public long sourceAmountAvailable() {
        EssenceDefinition source = sourceEssence();
        PlayerEssenceData data = ownerPlayerData();
        return source == null || data == null
                ? 0L
                : data.getCrucibleStored(source);
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
        if (!processingEnabled) {
            return STATUS_STOPPED;
        }
        ItemStack input = getItem(INPUT_SLOT);
        if (input.isEmpty()) {
            return STATUS_IDLE;
        }
        if (!isCurrentLinkValid()) {
            return STATUS_UNLINKED;
        }
        if (EssenceCrucibleChannelService.isChanneling(ownerId)) {
            return STATUS_PLAYER_CHANNELING;
        }

        Optional<EssenceInfuserRecipe> resolvedRecipe =
                EssenceInfuserRecipeRegistry.resolve(input);
        if (resolvedRecipe.isEmpty()) {
            return STATUS_INVALID_INPUT;
        }
        EssenceInfuserRecipe recipe = resolvedRecipe.get();
        EssenceInfuserRecipeContext context = recipeContext();
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

    private int repairInfusionStatus(RepairInfusionRecipe recipe) {
        ItemStack workpiece = getItem(INPUT_SLOT);
        if (!RepairInfusionRecipe.isWorkpiece(workpiece)) {
            return STATUS_INVALID_INPUT;
        }
        ItemStack repairedPreview = recipe.createOutput(recipeContext());
        if (repairedPreview.isEmpty() || !canAcceptOutput(repairedPreview)) {
            return STATUS_OUTPUT_BLOCKED;
        }

        EssenceDefinition source = sourceEssence();
        if (source == null
                || source.family() != EssenceFamily.ATTRIBUTE
                || !isEnabled(source)) {
            return STATUS_INVALID_SELECTION;
        }

        if (recipe.latentIngotCount() > 0) {
            ItemStack component = getItem(COMPONENT_SLOT);
            if (!component.is(EssenceInfuserContent.LATENT_INGOT.get())
                    || component.getCount() < recipe.latentIngotCount()) {
                return STATUS_REPAIR_MATERIAL_REQUIRED;
            }
        }

        PlayerEssenceData data = ownerPlayerData();
        if (data == null) {
            return STATUS_UNLINKED;
        }
        return data.getCrucibleStored(source) >= recipe.essenceRequired()
                ? STATUS_PROCESSING
                : STATUS_INSUFFICIENT_SOURCE;
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
        ItemStack workpiece = getItem(INPUT_SLOT);
        if (!EquipmentTierData.isAscendanceEquipment(workpiece)
                || workpiece.getCount() != 1
                || EquipmentTierData.tier(workpiece) != recipe.currentTier()) {
            return STATUS_INVALID_INPUT;
        }
        // Tier upgrades produce one individualized artifact. Do not spend more
        // Essence while the output is occupied, or overwrite an earlier result.
        if (!getItem(OUTPUT_SLOT).isEmpty()) {
            return STATUS_OUTPUT_BLOCKED;
        }
        PlayerEssenceData data = ownerPlayerData();
        if (data == null) {
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
                    && data.getCrucibleStored(essence) > 0L) {
                return STATUS_PROCESSING;
            }
        }
        return STATUS_INSUFFICIENT_SOURCE;
    }

    public void refreshLink() {
        if (!(level instanceof ServerLevel serverLevel) || ownerId == null) {
            return;
        }
        if (isCurrentLinkValid(serverLevel)) {
            return;
        }

        BlockPos nearest = findNearestOwnedCrucible(serverLevel);
        if ((linkedCruciblePos == null && nearest == null)
                || (linkedCruciblePos != null && linkedCruciblePos.equals(nearest))) {
            return;
        }
        linkedCruciblePos = nearest == null ? null : nearest.immutable();
        syncBlockEntity();
    }

    public boolean isCurrentLinkValid() {
        return level instanceof ServerLevel serverLevel
                && isCurrentLinkValid(serverLevel);
    }

    private boolean isCurrentLinkValid(ServerLevel serverLevel) {
        if (ownerId == null
                || linkedCruciblePos == null
                || !serverLevel.hasChunkAt(linkedCruciblePos)) {
            return false;
        }
        double range = EssenceInfuserBalance.linkRange();
        if (worldPosition.distSqr(linkedCruciblePos) > range * range) {
            return false;
        }
        if (!(serverLevel.getBlockEntity(linkedCruciblePos)
                instanceof EssenceCrucibleBlockEntity crucible)) {
            return false;
        }
        return ownerId.equals(crucible.ownerId());
    }

    @Nullable
    private BlockPos findNearestOwnedCrucible(ServerLevel serverLevel) {
        double range = EssenceInfuserBalance.linkRange();
        int scan = (int) Math.ceil(range);
        double rangeSquared = range * range;
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        for (int dx = -scan; dx <= scan; dx++) {
            for (int dy = -scan; dy <= scan; dy++) {
                for (int dz = -scan; dz <= scan; dz++) {
                    BlockPos candidate = worldPosition.offset(dx, dy, dz);
                    double distance = worldPosition.distSqr(candidate);
                    if (distance > rangeSquared || !serverLevel.hasChunkAt(candidate)) {
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

    public void cycleSource(int direction) {
        cycleSelection(true, direction);
    }

    public void cycleTarget(int direction) {
        cycleSelection(false, direction);
    }

    private void cycleSelection(boolean source, int direction) {
        if (focusInfusionMode()) {
            return;
        }
        boolean repair = repairMode();
        List<EssenceDefinition> enabled = repair
                ? enabledAttributeEssences()
                : enabledEssences();
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
        List<EssenceDefinition> enabled = repairMode()
                ? enabledAttributeEssences()
                : enabledEssences();
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
                && !source.id().equals(target.id())
                && isEnabled(source)
                && isEnabled(target);
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

    private static List<EssenceDefinition> enabledEssences() {
        List<EssenceDefinition> result = new ArrayList<>();
        for (EssenceDefinition essence : EssenceRegistry.values()) {
            if (isEnabled(essence)) {
                result.add(essence);
            }
        }
        return result;
    }

    private static List<EssenceDefinition> enabledAttributeEssences() {
        List<EssenceDefinition> result = new ArrayList<>();
        for (EssenceDefinition essence : EssenceRegistry.values()) {
            if (essence.family() == EssenceFamily.ATTRIBUTE && isEnabled(essence)) {
                result.add(essence);
            }
        }
        return result;
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

    private static boolean isEnabled(EssenceDefinition essence) {
        return essence.family() != EssenceFamily.SKILL
                || EssenceConfigManager.get().skillEssencesEnabled();
    }

    private void tickProcessing(ServerLevel serverLevel) {
        Optional<EssenceInfuserRecipe> resolvedRecipe = currentInfusionRecipe();
        if (resolvedRecipe.isEmpty()) {
            processingVisualActive = false;
            return;
        }
        EssenceInfuserRecipe recipe = resolvedRecipe.get();
        if (recipe instanceof RepairInfusionRecipe repairRecipe) {
            tickRepairInfusion(serverLevel, repairRecipe);
            return;
        }
        if (recipe instanceof FocusInfusionRecipe focusRecipe) {
            tickFocusInfusion(serverLevel, focusRecipe);
            return;
        }
        if (recipe instanceof EquipmentInfusionRecipe equipmentRecipe) {
            tickEquipmentInfusion(serverLevel, equipmentRecipe);
            return;
        }
        if (recipe.progressModel() == EssenceInfuserProgressModel.TIMED_ATOMIC) {
            tickTimedAtomicRecipe(serverLevel, recipe);
            return;
        }
        processingVisualActive = false;
    }

    private void tickRepairInfusion(
            ServerLevel serverLevel,
            RepairInfusionRecipe recipe
    ) {
        int status = statusCode();
        if (status != STATUS_PROCESSING) {
            processingVisualActive = false;
            return;
        }

        int requiredTicks = Math.max(1, recipe.processingTicks(recipeContext()));
        processingVisualActive = true;
        if (processingTicks < requiredTicks) {
            processingTicks++;
            setChanged();
        }
        if (processingTicks < requiredTicks) {
            return;
        }

        completeRepairInfusion(serverLevel, recipe);
    }

    private void completeRepairInfusion(
            ServerLevel serverLevel,
            RepairInfusionRecipe expectedRecipe
    ) {
        if (ownerId == null || statusCode() != STATUS_PROCESSING) {
            return;
        }

        RepairInfusionRecipe current = repairInfusionRecipe().orElse(null);
        if (current == null
                || current.missingDurability() != expectedRecipe.missingDurability()
                || current.essenceRequired() != expectedRecipe.essenceRequired()
                || current.latentIngotCount() != expectedRecipe.latentIngotCount()) {
            return;
        }

        EssenceDefinition source = sourceEssence();
        if (source == null
                || source.family() != EssenceFamily.ATTRIBUTE
                || !isEnabled(source)) {
            return;
        }

        ItemStack component = getItem(COMPONENT_SLOT);
        if (current.latentIngotCount() > 0
                && (!component.is(EssenceInfuserContent.LATENT_INGOT.get())
                    || component.getCount() < current.latentIngotCount())) {
            return;
        }

        ItemStack repaired = current.createOutput(recipeContext());
        if (repaired.isEmpty() || !canAcceptOutput(repaired)) {
            return;
        }

        EssenceSavedData saved = EssenceSavedData.get(serverLevel.getServer());
        if (!saved.removeCrucibleStoredExact(ownerId, source, current.essenceRequired())) {
            return;
        }

        try {
            SoulboundEquipmentData.bind(repaired, ownerId, ownerName);
        } catch (RuntimeException failure) {
            saved.addCrucibleStored(ownerId, source, current.essenceRequired());
            EssenceAscendance.LOGGER.error(
                    "Failed to complete Ascendance equipment repair; rolled back {} {}",
                    current.essenceRequired(),
                    source.id(),
                    failure
            );
            return;
        }

        if (current.latentIngotCount() > 0) {
            component.shrink(current.latentIngotCount());
            if (component.isEmpty()) {
                items.set(COMPONENT_SLOT, ItemStack.EMPTY);
            }
        }

        items.set(INPUT_SLOT, ItemStack.EMPTY);
        items.set(OUTPUT_SLOT, repaired);

        processingTicks = 0;
        focusThroughputRemainderTwentieths = 0;
        processingEnabled = false;
        syncBlockEntity();
    }

    private void tickEquipmentInfusion(ServerLevel serverLevel, EquipmentInfusionRecipe recipe) {
        processingTicks = 0;
        if (statusCode() != STATUS_PROCESSING) {
            processingVisualActive = false;
            return;
        }
        ItemStack workpiece = getItem(INPUT_SLOT);
        if (EquipmentInfusionData.requirementsMet(workpiece, recipe)) {
            completeEquipmentInfusion(recipe);
            return;
        }
        if (serverLevel.getGameTime() % FOCUS_INFUSION_INTERVAL_TICKS != 0L) {
            return;
        }
        EssenceInfuserBalance.ThroughputSlice slice = EssenceInfuserBalance.throughputForTicks(
                infusionThroughputPerSecond(), FOCUS_INFUSION_INTERVAL_TICKS, focusThroughputRemainderTwentieths);
        focusThroughputRemainderTwentieths = slice.remainderTwentieths();
        long moved = transferEquipmentEssence(serverLevel, recipe, slice.amount());
        processingVisualActive = moved > 0L;
        if (moved > 0L) syncBlockEntity();
        if (EquipmentInfusionData.requirementsMet(getItem(INPUT_SLOT), recipe)) {
            completeEquipmentInfusion(recipe);
        }
    }

    private long transferEquipmentEssence(ServerLevel serverLevel, EquipmentInfusionRecipe recipe, long requestedBudget) {
        if (ownerId == null || requestedBudget <= 0L) return 0L;
        ItemStack workpiece = getItem(INPUT_SLOT);
        EquipmentInfusionData.ensure(workpiece, recipe);
        EssenceSavedData saved = EssenceSavedData.get(serverLevel.getServer());
        long budget = requestedBudget;
        long moved = 0L;
        for (var entry : recipe.requirements().entrySet()) {
            if (budget <= 0L) break;
            EssenceDefinition essence = EssenceRegistry.get(entry.getKey()).orElse(null);
            if (essence == null) continue;
            long current = EquipmentInfusionData.contribution(workpiece, essence);
            long room = entry.getValue() - current;
            if (room <= 0L) continue;
            long available = saved.getPlayerData(ownerId).getCrucibleStored(essence);
            long amount = Math.min(budget, Math.min(room, available));
            if (amount <= 0L || !saved.removeCrucibleStoredExact(ownerId, essence, amount)) continue;
            try {
                EquipmentInfusionData.addContribution(workpiece, recipe, essence, amount);
            } catch (RuntimeException failure) {
                saved.addCrucibleStored(ownerId, essence, amount);
                EssenceAscendance.LOGGER.error("Failed to persist equipment infusion contribution; rolled back {} {}", amount, essence.id(), failure);
                break;
            }

            /*
             * The first successful Essence contribution permanently awakens
             * the artifact's ownership identity. Merely inserting equipment
             * or creating zero-progress infusion state does not soulbind it.
             *
             * Binding happens after contribution persistence so a zero-Essence
             * attempt can never bind an untouched item. Binding failure must not
             * refund already-persisted Essence, which would duplicate progress.
             */
            try {
                SoulboundEquipmentData.bind(workpiece, ownerId, ownerName);
            } catch (RuntimeException failure) {
                EssenceAscendance.LOGGER.error(
                        "Equipment infusion contribution persisted but soulbinding failed for {}",
                        ownerId,
                        failure
                );
            }

            budget -= amount;
            moved = Math.addExact(moved, amount);
        }
        return moved;
    }

    private void completeEquipmentInfusion(EquipmentInfusionRecipe recipe) {
        ItemStack workpiece = getItem(INPUT_SLOT);
        PlayerEssenceData data = ownerPlayerData();
        ItemStack component = getItem(COMPONENT_SLOT);
        if (data == null
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
        // Publish the fully staged result only after every completion check.
        // Unused Matrices stay in their component slot; only the workpiece moves.
        component.shrink(recipe.matrixCount());
        if (component.isEmpty()) items.set(COMPONENT_SLOT, ItemStack.EMPTY);
        items.set(INPUT_SLOT, ItemStack.EMPTY);
        items.set(OUTPUT_SLOT, completed);
        processingVisualActive = false;
        processingTicks = 0;
        focusThroughputRemainderTwentieths = 0;
        processingEnabled = false;
        syncBlockEntity();
    }

    private void tickTimedAtomicRecipe(
            ServerLevel serverLevel,
            EssenceInfuserRecipe recipe
    ) {
        int status = statusCode();
        if (status != STATUS_PROCESSING) {
            processingVisualActive = false;
            return;
        }

        EssenceInfuserRecipeContext context = recipeContext();
        int requiredTicks = Math.max(1, recipe.processingTicks(context));
        processingVisualActive = true;
        if (processingTicks < requiredTicks) {
            processingTicks++;
            setChanged();
        }
        if (processingTicks < requiredTicks) {
            return;
        }

        completeTimedAtomicRecipe(serverLevel, recipe);
    }

    private void tickFocusInfusion(ServerLevel serverLevel, FocusInfusionRecipe recipe) {
        processingTicks = 0;
        if (statusCode() != STATUS_PROCESSING) {
            processingVisualActive = false;
            return;
        }

        ItemStack workpiece = getItem(INPUT_SLOT);
        if (FocusInfusionData.requirementsMet(workpiece, recipe)) {
            completeFocusInfusion(recipe);
            return;
        }

        if (serverLevel.getGameTime() % FOCUS_INFUSION_INTERVAL_TICKS != 0L) {
            return;
        }

        EssenceInfuserBalance.ThroughputSlice slice =
                EssenceInfuserBalance.throughputForTicks(
                        infusionThroughputPerSecond(),
                        FOCUS_INFUSION_INTERVAL_TICKS,
                        focusThroughputRemainderTwentieths
                );
        focusThroughputRemainderTwentieths = slice.remainderTwentieths();

        long moved = transferFocusEssence(serverLevel, recipe, slice.amount());
        processingVisualActive = moved > 0L;
        if (moved > 0L) {
            syncBlockEntity();
        }

        if (FocusInfusionData.requirementsMet(getItem(INPUT_SLOT), recipe)) {
            completeFocusInfusion(recipe);
        }
    }

    private long transferFocusEssence(
            ServerLevel serverLevel,
            FocusInfusionRecipe recipe,
            long requestedBudget
    ) {
        if (ownerId == null || requestedBudget <= 0L) {
            return 0L;
        }

        ItemStack workpiece = getItem(INPUT_SLOT);
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
                if (saved.getPlayerData(ownerId).getCrucibleStored(essence) > 0L) {
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
                                        essence -> saved.getPlayerData(ownerId)
                                                .getCrucibleStored(essence)
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
                long available = saved.getPlayerData(ownerId).getCrucibleStored(essence);
                long amount = Math.min(
                        budget,
                        Math.min(share, Math.min(contributionRoom, available))
                );
                if (amount <= 0L
                        || !saved.removeCrucibleStoredExact(ownerId, essence, amount)) {
                    continue;
                }

                try {
                    FocusInfusionData.addContribution(workpiece, recipe, essence, amount);
                } catch (RuntimeException failure) {
                    saved.addCrucibleStored(ownerId, essence, amount);
                    EssenceAscendance.LOGGER.error(
                            "Failed to persist Focus infusion contribution; rolled back {} {}",
                            amount,
                            essence.id(),
                            failure
                    );
                    return movedTotal;
                }

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

        return movedTotal;
    }

    private boolean hasUsefulFocusEssence(FocusInfusionRecipe recipe) {
        PlayerEssenceData data = ownerPlayerData();
        ItemStack workpiece = getItem(INPUT_SLOT);
        if (data == null || FocusInfusionData.readValidated(workpiece).isEmpty()) {
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
            if (data.getCrucibleStored(essence) > 0L) {
                return true;
            }
        }
        return false;
    }

    private void completeFocusInfusion(FocusInfusionRecipe recipe) {
        ItemStack workpiece = getItem(INPUT_SLOT);
        if (!FocusInfusionData.requirementsMet(workpiece, recipe)
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

        items.set(INPUT_SLOT, ItemStack.EMPTY);
        ItemStack output = getItem(OUTPUT_SLOT);
        if (output.isEmpty()) {
            items.set(OUTPUT_SLOT, result);
        } else {
            output.grow(1);
        }
        processingTicks = 0;
        focusThroughputRemainderTwentieths = 0;
        processingEnabled = false;
        syncBlockEntity();
    }

    private void completeTimedAtomicRecipe(
            ServerLevel serverLevel,
            EssenceInfuserRecipe expectedRecipe
    ) {
        if (ownerId == null || statusCode() != STATUS_PROCESSING) {
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
        if (!withdrawFixedRequirements(saved, requirements)) {
            return;
        }

        ItemStack input = getItem(INPUT_SLOT);
        int consumed = Math.max(1, current.inputCount());
        if (input.getCount() < consumed) {
            rollbackFixedRequirements(saved, requirements);
            return;
        }
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

        processingTicks = 0;
        focusThroughputRemainderTwentieths = 0;
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
            if (essence == null || !isEnabled(essence) || entry.getValue() <= 0L) {
                return false;
            }
        }
        return true;
    }

    private boolean hasFixedRequirementsAvailable(EssenceInfusionRequirements requirements) {
        PlayerEssenceData data = ownerPlayerData();
        if (data == null || !validFixedRequirements(requirements)) {
            return false;
        }
        for (var entry : requirements.minimumByEssence().entrySet()) {
            EssenceDefinition essence = EssenceRegistry.get(entry.getKey()).orElse(null);
            if (essence == null || data.getCrucibleStored(essence) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    private boolean withdrawFixedRequirements(
            EssenceSavedData saved,
            EssenceInfusionRequirements requirements
    ) {
        if (ownerId == null || !hasFixedRequirementsAvailable(requirements)) {
            return false;
        }
        List<EssenceWithdrawal> withdrawn = new ArrayList<>();
        for (var entry : requirements.minimumByEssence().entrySet()) {
            EssenceDefinition essence = EssenceRegistry.get(entry.getKey()).orElse(null);
            long amount = entry.getValue();
            if (essence == null
                    || !saved.removeCrucibleStoredExact(ownerId, essence, amount)) {
                rollbackWithdrawals(saved, withdrawn);
                return false;
            }
            withdrawn.add(new EssenceWithdrawal(essence, amount));
        }
        return true;
    }

    private void rollbackFixedRequirements(
            EssenceSavedData saved,
            EssenceInfusionRequirements requirements
    ) {
        List<EssenceWithdrawal> withdrawals = new ArrayList<>();
        for (var entry : requirements.minimumByEssence().entrySet()) {
            EssenceDefinition essence = EssenceRegistry.get(entry.getKey()).orElse(null);
            if (essence != null) {
                withdrawals.add(new EssenceWithdrawal(essence, entry.getValue()));
            }
        }
        rollbackWithdrawals(saved, withdrawals);
    }

    private void rollbackWithdrawals(
            EssenceSavedData saved,
            List<EssenceWithdrawal> withdrawals
    ) {
        if (ownerId == null) {
            return;
        }
        for (EssenceWithdrawal withdrawal : withdrawals) {
            saved.addCrucibleStored(ownerId, withdrawal.essence(), withdrawal.amount());
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
        if (!bindOwner(player) || !canPlayerUse(player)) {
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

    private void spawnTransferParticles(ServerLevel level) {
        if (linkedCruciblePos == null) {
            return;
        }
        Vec3 start = Vec3.atCenterOf(linkedCruciblePos).add(0.0D, 0.45D, 0.0D);
        Vec3 end = Vec3.atCenterOf(worldPosition).add(0.0D, 0.45D, 0.0D);
        Vec3 delta = end.subtract(start);
        for (int i = 1; i <= 5; i++) {
            double t = i / 6.0D;
            Vec3 at = start.add(delta.scale(t));
            level.sendParticles(
                    ParticleTypes.ENCHANT,
                    at.x,
                    at.y,
                    at.z,
                    1,
                    0.01D,
                    0.01D,
                    0.01D,
                    0.0D
            );
        }
    }

    @Nullable
    private PlayerEssenceData ownerPlayerData() {
        if (ownerId == null || !(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        return EssenceSavedData.get(serverLevel.getServer()).getPlayerData(ownerId);
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
        boolean changed = processingEnabled || processingTicks != 0 || processingVisualActive;
        processingEnabled = false;
        processingTicks = 0;
        focusThroughputRemainderTwentieths = 0;
        processingVisualActive = false;
        if (changed) {
            setChanged();
        }
        return changed;
    }

    private void resetProcessing() {
        if (processingTicks != 0 || focusThroughputRemainderTwentieths != 0) {
            processingTicks = 0;
            focusThroughputRemainderTwentieths = 0;
            setChanged();
        }
    }

    private void syncBlockEntity() {
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
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
        if (ownerId != null) {
            tag.putUUID(OWNER_TAG, ownerId);
        }
        if (!ownerName.isBlank()) {
            tag.putString(OWNER_NAME_TAG, ownerName);
        }
        if (linkedCruciblePos != null) {
            tag.putLong(LINKED_CRUCIBLE_TAG, linkedCruciblePos.asLong());
        }
        if (sourceEssenceId != null) {
            tag.putString(SOURCE_ESSENCE_TAG, sourceEssenceId.toString());
        }
        if (targetEssenceId != null) {
            tag.putString(TARGET_ESSENCE_TAG, targetEssenceId.toString());
        }
        tag.putInt(PROCESSING_TICKS_TAG, Math.max(0, processingTicks));
        tag.putBoolean(PROCESSING_ENABLED_TAG, processingEnabled);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.clear();
        ContainerHelper.loadAllItems(tag, items, registries);
        ownerId = tag.hasUUID(OWNER_TAG) ? tag.getUUID(OWNER_TAG) : null;
        ownerName = tag.getString(OWNER_NAME_TAG);
        linkedCruciblePos = tag.contains(LINKED_CRUCIBLE_TAG, Tag.TAG_LONG)
                ? BlockPos.of(tag.getLong(LINKED_CRUCIBLE_TAG))
                : null;
        sourceEssenceId = ResourceLocation.tryParse(tag.getString(SOURCE_ESSENCE_TAG));
        targetEssenceId = ResourceLocation.tryParse(tag.getString(TARGET_ESSENCE_TAG));
        processingTicks = Math.max(0, tag.getInt(PROCESSING_TICKS_TAG));
        processingEnabled = tag.getBoolean(PROCESSING_ENABLED_TAG);
        processingVisualActive = false;
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
        ItemStack removed = ContainerHelper.removeItem(items, slot, amount);
        if (!removed.isEmpty()) {
            if (slot == INPUT_SLOT || slot == FOCUS_SLOT) {
                resetProcessing();
            }
            syncBlockEntity();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) {
            return ItemStack.EMPTY;
        }
        ItemStack removed = ContainerHelper.takeItem(items, slot);
        if (!removed.isEmpty() && (slot == INPUT_SLOT || slot == FOCUS_SLOT)) {
            resetProcessing();
        }
        if (!removed.isEmpty()) {
            syncBlockEntity();
        }
        return removed;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= SLOT_COUNT) {
            return;
        }
        ItemStack normalized = stack == null ? ItemStack.EMPTY : stack;
        if (!normalized.isEmpty()) {
            int limit = slot == FOCUS_SLOT
                    ? 1
                    : (slot == INPUT_SLOT
                    ? workpieceStackLimit(normalized)
                    : (slot == COMPONENT_SLOT && repairMode()
                    ? Math.max(1, repairLatentIngotRequired())
                    : getMaxStackSize(normalized)));
            normalized.limitSize(limit);
        }
        ItemStack previous = items.get(slot);
        boolean changedType = !ItemStack.isSameItemSameComponents(previous, normalized);
        items.set(slot, normalized);
        if (changedType && (slot == INPUT_SLOT || slot == FOCUS_SLOT || slot == COMPONENT_SLOT)) {
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
        if (contextChanged) {
            resetProcessing();
        }
        syncBlockEntity();
    }

    @Override
    public boolean stillValid(Player player) {
        return ServerMenuAccess.canReach(player, this) && canPlayerUse(player);
    }

    @Override
    public void clearContent() {
        items.clear();
        resetProcessing();
        syncBlockEntity();
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
        if (slot == COMPONENT_SLOT) {
            if (stack == null || stack.isEmpty() || repairMode()) {
                return false;
            }
            return equipmentInfusionMode()
                    && stack.is(EssenceInfuserContent.ASCENDANCE_MATRIX.get());
        }
        return slot == INPUT_SLOT
                && EssenceInfuserRecipeRegistry.allowsAutomationInput(stack);
    }

    @Override
    public boolean canTakeItemThroughFace(
            int slot,
            ItemStack stack,
            Direction direction
    ) {
        return slot == OUTPUT_SLOT;
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

    private record EssenceWithdrawal(EssenceDefinition essence, long amount) {
    }
}
