package com.mistaboom.essence_ascendance.infuser;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleBlockEntity;
import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleChannelService;
import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import com.mistaboom.essence_ascendance.essence.EssenceFamily;
import com.mistaboom.essence_ascendance.essence.EssenceRegistry;
import com.mistaboom.essence_ascendance.pylon.EssencePylonContent;
import com.mistaboom.essence_ascendance.pylon.EssencePylonFocusTier;
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
import net.minecraft.world.item.Item;
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
    public static final int SLOT_COUNT = 3;

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

    private static final int FOCUS_INFUSION_INTERVAL_TICKS = 4;

    private static final int[] AUTOMATION_SLOTS = {INPUT_SLOT, OUTPUT_SLOT};

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

    public EssencePylonFocusTier focusTier() {
        return EssencePylonContent.focusTier(getItem(FOCUS_SLOT));
    }

    public EssenceInfuserBalance.Profile profile() {
        return EssenceInfuserBalance.profile(getItem(FOCUS_SLOT));
    }

    public Optional<FocusInfusionRecipe> focusInfusionRecipe() {
        return FocusInfusionRecipe.forWorkpiece(getItem(INPUT_SLOT));
    }

    public boolean focusInfusionMode() {
        return focusInfusionRecipe().isPresent();
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
    public EssencePylonFocusTier focusInfusionTargetTier() {
        return focusInfusionRecipe().map(FocusInfusionRecipe::targetTier).orElse(null);
    }

    @Nullable
    public EssencePylonFocusTier focusInfusionRequiredInstalledTier() {
        return focusInfusionRecipe()
                .map(FocusInfusionRecipe::requiredInstalledTier)
                .orElse(null);
    }

    public long focusInfusionRatePerSecond() {
        EssenceInfuserBalance.Profile current = profile();
        long carrierCapacity = EssenceConfigManager.get()
                .infuserBalance()
                .grade(current.grade().serializedName())
                .ingotCapacity();
        long perTick = Math.max(1L, carrierCapacity / Math.max(1, current.processingTicks()));
        try {
            return Math.multiplyExact(perTick, 20L);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    private long focusInfusionRatePerTick() {
        return Math.max(1L, focusInfusionRatePerSecond() / 20L);
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
        syncBlockEntity();
    }

    public int requiredProcessingTicks() {
        return profile().processingTicks();
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
        ItemStack input = getItem(INPUT_SLOT);
        EssentiumItem output = outputCarrierFor(input);
        return output == null
                ? 0L
                : EssentiumCarrierData.capacityFor(output, profile().grade());
    }

    public long sourceRequired() {
        long target = targetCarrierCapacity();
        return EssenceInfuserBalance.requiredSource(
                target,
                profile().efficiencyBasisPoints()
        );
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

        Optional<FocusInfusionRecipe> focusRecipe = FocusInfusionRecipe.forWorkpiece(input);
        if (focusRecipe.isPresent()) {
            return focusInfusionStatus(focusRecipe.get());
        }

        if (!hasValidSelection()) {
            return STATUS_INVALID_SELECTION;
        }
        if (outputCarrierFor(input) == null) {
            return STATUS_INVALID_INPUT;
        }

        ItemStack result = createCurrentOutput();
        if (result.isEmpty()) {
            return STATUS_INVALID_INPUT;
        }
        if (!canAcceptOutput(result)) {
            return STATUS_OUTPUT_BLOCKED;
        }

        long required = sourceRequired();
        if (required <= 0L || sourceAmountAvailable() < required) {
            return STATUS_INSUFFICIENT_SOURCE;
        }
        return STATUS_PROCESSING;
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
        List<EssenceDefinition> enabled = enabledEssences();
        if (enabled.size() < 2) {
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
            if (candidate.equals(other)) {
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
        List<EssenceDefinition> enabled = enabledEssences();
        if (enabled.size() < 2) {
            return;
        }

        boolean changed = false;
        if (!containsEssence(enabled, sourceEssenceId)) {
            sourceEssenceId = enabled.get(0).id();
            changed = true;
        }
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
        Optional<FocusInfusionRecipe> focusRecipe = focusInfusionRecipe();
        if (focusRecipe.isPresent()) {
            tickFocusInfusion(serverLevel, focusRecipe.get());
            return;
        }
        tickCarrierConversion(serverLevel);
    }

    private void tickCarrierConversion(ServerLevel serverLevel) {
        int status = statusCode();
        if (status != STATUS_PROCESSING) {
            processingVisualActive = false;
            return;
        }

        processingVisualActive = true;
        int requiredTicks = requiredProcessingTicks();
        if (processingTicks < requiredTicks) {
            processingTicks++;
            setChanged();
        }
        if (processingTicks < requiredTicks) {
            return;
        }

        completeConversion(serverLevel);
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

        long perPulse;
        try {
            perPulse = Math.multiplyExact(
                    focusInfusionRatePerTick(),
                    (long) FOCUS_INFUSION_INTERVAL_TICKS
            );
        } catch (ArithmeticException overflow) {
            perPulse = Long.MAX_VALUE;
        }

        long moved = transferFocusEssence(serverLevel, recipe, perPulse);
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

        ItemStack result = recipe.createOutput();
        if (result.isEmpty() || !canAcceptOutput(result)) {
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
        syncBlockEntity();
    }

    private void completeConversion(ServerLevel serverLevel) {
        if (ownerId == null
                || statusCode() != STATUS_PROCESSING) {
            return;
        }

        EssenceDefinition source = sourceEssence();
        ItemStack result = createCurrentOutput();
        long required = sourceRequired();
        if (source == null
                || result.isEmpty()
                || required <= 0L
                || !canAcceptOutput(result)) {
            return;
        }

        EssenceSavedData saved = EssenceSavedData.get(serverLevel.getServer());
        if (!saved.removeCrucibleStoredExact(ownerId, source, required)) {
            return;
        }

        ItemStack input = getItem(INPUT_SLOT);
        input.shrink(1);
        if (input.isEmpty()) {
            items.set(INPUT_SLOT, ItemStack.EMPTY);
        }

        ItemStack output = getItem(OUTPUT_SLOT);
        if (output.isEmpty()) {
            items.set(OUTPUT_SLOT, result);
        } else {
            output.grow(1);
        }

        processingTicks = 0;
        syncBlockEntity();
    }

    private ItemStack createCurrentOutput() {
        EssentiumItem item = outputCarrierFor(getItem(INPUT_SLOT));
        EssenceDefinition target = targetEssence();
        if (item == null || target == null || !isEnabled(target)) {
            return ItemStack.EMPTY;
        }
        return EssentiumCarrierData.createFull(item, target, profile().grade());
    }

    @Nullable
    private static EssentiumItem outputCarrierFor(ItemStack input) {
        if (input == null || input.isEmpty()) {
            return null;
        }
        Item output;
        if (input.is(EssenceInfuserContent.LATENT_INGOT.get())) {
            output = EssenceInfuserContent.ESSENTIUM_INGOT.get();
        } else if (input.is(EssenceInfuserContent.LATENT_BLOCK_ITEM.get())) {
            output = EssenceInfuserContent.ESSENTIUM_BLOCK.get();
        } else {
            return null;
        }
        return output instanceof EssentiumItem essentium ? essentium : null;
    }

    private boolean canAcceptOutput(ItemStack result) {
        if (result.isEmpty()) {
            return false;
        }
        ItemStack output = getItem(OUTPUT_SLOT);
        if (output.isEmpty()) {
            return true;
        }
        return ItemStack.isSameItemSameComponents(output, result)
                && output.getCount() < output.getMaxStackSize();
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
        processingVisualActive = false;
        if (changed) {
            setChanged();
        }
        return changed;
    }

    private void resetProcessing() {
        if (processingTicks != 0) {
            processingTicks = 0;
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
                    : getMaxStackSize(normalized));
            normalized.limitSize(limit);
        }
        ItemStack previous = items.get(slot);
        boolean changedType = !ItemStack.isSameItemSameComponents(previous, normalized);
        items.set(slot, normalized);
        if (changedType && (slot == INPUT_SLOT || slot == FOCUS_SLOT)) {
            resetProcessing();
        }
        syncBlockEntity();
    }

    @Override
    public boolean stillValid(Player player) {
        return level != null
                && level.getBlockEntity(worldPosition) == this
                && canPlayerUse(player)
                && player.distanceToSqr(Vec3.atCenterOf(worldPosition)) <= 64.0D;
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
        return slot == INPUT_SLOT && isValidWorkpiece(stack);
    }

    @Override
    public boolean canPlaceItemThroughFace(
            int slot,
            ItemStack stack,
            @Nullable Direction direction
    ) {
        return slot == INPUT_SLOT && isLatentCarrier(stack);
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
        return stack != null
                && !stack.isEmpty()
                && (stack.is(EssenceInfuserContent.LATENT_INGOT.get())
                || stack.is(EssenceInfuserContent.LATENT_BLOCK_ITEM.get()));
    }

    public static boolean isFocusWorkpiece(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (stack.is(EssenceInfuserContent.LATENT_FOCUS.get())) {
            return true;
        }
        EssencePylonFocusTier tier = EssencePylonContent.rawFocusTier(stack);
        return tier != null && tier != EssencePylonFocusTier.TRANSCENDENT;
    }

    public static boolean isValidWorkpiece(ItemStack stack) {
        return isLatentCarrier(stack) || isFocusWorkpiece(stack);
    }

    public static int workpieceStackLimit(ItemStack stack) {
        return isFocusWorkpiece(stack) ? 1 : Math.max(1, stack.getMaxStackSize());
    }
}
