package com.mistaboom.essence_ascendance.crucible;

import com.mistaboom.essence_ascendance.network.ServerMenuAccess;
import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import com.mistaboom.essence_ascendance.mapping.ItemEssenceMappingRegistry;
import com.mistaboom.essence_ascendance.balance.economy.FractionalAmountService;
import com.mistaboom.essence_ascendance.data.ReservoirService;
import com.mistaboom.essence_ascendance.machine.*;
import com.mistaboom.essence_ascendance.infuser.EssentiumCarrierData;
import com.mistaboom.essence_ascendance.infuser.EssenceInfuserBalance;
import com.mistaboom.essence_ascendance.infuser.EssenceInfuserBlockEntity;
import com.mistaboom.essence_ascendance.visual.MachineVisualState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
//? if >=26.1.2
/*import net.minecraft.core.UUIDUtil;*/
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
//? if >=26.1.2
/*import net.minecraft.world.level.block.Block;*/
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
//? if >=26.1.2
/*import net.minecraft.world.level.storage.ValueInput;*/
//? if >=26.1.2
/*import net.minecraft.world.level.storage.ValueOutput;*/
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class EssenceCrucibleBlockEntity extends BlockEntity
        implements WorldlyContainer, MenuProvider, BoundMachine {

    //? if >=26.1.2
    /*@Override public void preRemoveSideEffects(BlockPos pos, BlockState state) { stopChanneling(); if (level instanceof ServerLevel server) Block.popResource(server, pos, MachineProtection.takePickup(this)); super.preRemoveSideEffects(pos, state); }*/

    public static final int MAX_INPUT_SLOTS = EssenceCrucibleStructureService.MAX_INPUT_SLOTS;
    private static final int[] NO_AUTOMATION_SLOTS = {};
    private static final long STRUCTURE_CACHE_TICKS = 10L;

    private static final String OWNER_TAG = "owner";
    private static final String OWNER_NAME_TAG = "owner_name";
    private static final String ACCESS_MODE_TAG = "access_mode";
    private static final String PROCESSING_TICKS_TAG = "processing_ticks";
    private static final String DISSOLUTION_MODE_TAG = "dissolution_mode";

    /* Number of full dissolution cycles Smart Round Robin gives a blocked lane. */
    private static final int SMART_ROUND_ROBIN_WAIT_CYCLES = 3;

    private final NonNullList<ItemStack> items =
            NonNullList.withSize(MAX_INPUT_SLOTS, ItemStack.EMPTY);

    private final MachineBinding binding = new MachineBinding();
    private UUID ownerId;
    private String ownerName = "";
    private EssenceCrucibleAccessMode accessMode =
            EssenceCrucibleAccessMode.PRIVATE;

    private boolean processingArmed = true;
    private int processingTicks = 0;
    private int dissolutionSlotCursor = 0;
    private EssenceCrucibleDissolutionMode dissolutionMode =
            EssenceCrucibleDissolutionMode.SMART_ROUND_ROBIN;

    /* Runtime-only fairness state for Smart Round Robin. */
    private int smartRoundRobinBlockedTicks = 0;
    private int smartRoundRobinWaitSlot = -1;

    private final Map<UUID, Integer> transferCursors = new LinkedHashMap<>();
    private final Map<UUID, Long> recentTransfers = new LinkedHashMap<>();
    private final Map<UUID, Integer> recentTransferTypes = new LinkedHashMap<>();

    /* Short-lived structure cache; pylon changes explicitly invalidate it. */
    private EssenceCrucibleStructureSnapshot cachedStructureSnapshot;
    private boolean cachedMembershipReady;
    private long cachedMembershipReceipt = Long.MIN_VALUE;
    private long cachedStructureGameTime = Long.MIN_VALUE;
    private boolean processingVisualActive = false;
    private MachineVisualState.Crucible clientVisualState;
    private MachineVisualState.Crucible lastSentVisualState;
    private long infusionVisualCheckTick = Long.MIN_VALUE;
    private boolean supplyingInfuserVisual;

    public EssenceCrucibleBlockEntity(
            BlockPos pos,
            BlockState state
    ) {
        super(
                EssenceCrucibleContent.ESSENCE_CRUCIBLE_BLOCK_ENTITY.get(),
                pos,
                state
        );
    }

    public static void serverTick(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            EssenceCrucibleBlockEntity crucible
    ) {
        crucible.processingVisualActive = false;
        EssenceCrucibleChannelService.sourceLoaded(crucible);
        var saved = EssenceSavedData.get(level.getServer());
        if (crucible.binding.refresh(saved)) crucible.bindingChanged();
        if (!crucible.binding.operational(saved)) {
            crucible.stopChanneling();
            if (level.getGameTime() % 5L == 0L) crucible.syncVisualState();
            return;
        }
        crucible.tickDissolution();

        if (level.getGameTime() % 5L == 0L) {
            crucible.syncVisualState();
        }
    }

    /** Observer snapshot; never used to decide gameplay or inventory behavior. */
    public MachineVisualState.Crucible visualState() {
        if (level != null && level.isClientSide) return clientVisualState == null
                ? MachineVisualState.IDLE_CRUCIBLE : clientVisualState;
        EssenceCrucibleStructureSnapshot structure = structureSnapshot();
        java.math.BigInteger largest = java.math.BigInteger.ZERO;
        int predominant = -1;
        for (int i = 0; i < EssenceCrucibleEssences.ORDERED.size(); i++) {
            var stored = storedEssenceExact(EssenceCrucibleEssences.ORDERED.get(i));
            if (stored.compareTo(largest) > 0) { largest = stored; predominant = i; }
        }
        int fill = MachineCapacity.visualFill(storedUnitsTotal(), localFillLimit());
        List<BlockPos> pylons = structure.activePylons().stream()
                .map(EssenceCrucibleStructureSnapshot.ActivePylon::pos).toList();
        List<net.minecraft.resources.ResourceLocation> inputs = new ArrayList<>();
        for (int slot = 0; slot < activeInputSlotCount(structure.stats()); slot++) {
            ItemStack stack = items.get(slot);
            if (!stack.isEmpty()) inputs.add(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        }
        return new MachineVisualState.Crucible(processingVisualActive, transferringPlayers().stream().limit(32).map(id -> {
                    var data = EssenceSavedData.get(((ServerLevel) level).getServer()).getPlayerData(id);
                    var f = EssenceCrucibleChannelService.feedback(this, id);
                    return new MachineVisualState.PlayerStream(id, recentTransferTypes.getOrDefault(id, 0) & data.crucibleChannelSelection(),
                            ConcurrentChannelScheduler.sampledPerSecond(f.actual()).min(java.math.BigInteger.valueOf(Long.MAX_VALUE)).longValue());
                }).filter(s -> s.essenceMask() != 0 && s.rate() > 0).toList(),
                structure.stats().transferRatePerSecond(), predominant, fill, pylons, inputs,
                supplyingInfuserVisual());
    }

    /** Observer-only aggregation of registered, currently usable Infuser demand. */
    private boolean supplyingInfuserVisual() {
        if (!(level instanceof ServerLevel serverLevel) || ownerId == null) return false;
        long tick = serverLevel.getGameTime();
        if (infusionVisualCheckTick == tick) return supplyingInfuserVisual;
        infusionVisualCheckTick = tick;
        supplyingInfuserVisual = false;
        supplyingInfuserVisual = EssenceCrucibleChannelService.supplyingInfuser(this);
        return supplyingInfuserVisual;
    }

    private void syncVisualState() {
        MachineVisualState.Crucible current = visualState();
        if (current.equals(lastSentVisualState)) return;
        lastSentVisualState = current;
        BlockState state = getBlockState();
        level.sendBlockUpdated(worldPosition, state, state, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
    }

    public EssenceCrucibleStructureSnapshot structureSnapshot() {
        if (level == null) {
            return EssenceCrucibleStructureService.baseSnapshot();
        }

        long gameTime = level.getGameTime();
        long membershipReceipt = level instanceof ServerLevel serverLevel
                ? EssenceSavedData.get(serverLevel.getServer()).reservoirs().rosterRevision() : 0;
        if (cachedStructureSnapshot != null && level instanceof ServerLevel serverLevel) {
            for (var active : cachedStructureSnapshot.activePylons()) {
                if (!serverLevel.hasChunkAt(active.pos()) || !(serverLevel.getBlockEntity(active.pos()) instanceof com.mistaboom.essence_ascendance.pylon.EssencePylonBlockEntity pylon)
                        || !pylon.isLinkedTo(worldPosition) || !pylon.hasInstalledFocus()) {
                    cachedStructureSnapshot = null; break;
                }
            }
        }
        boolean membershipReady = !(level instanceof ServerLevel serverLevel)
                || com.mistaboom.essence_ascendance.team.MembershipAuthority.ready(EssenceSavedData.get(serverLevel.getServer()).reservoirs().readiness());
        if (cachedMembershipReceipt != membershipReceipt || cachedMembershipReady != membershipReady) cachedStructureSnapshot = null;
        if (cachedStructureSnapshot == null
                || gameTime - cachedStructureGameTime >= STRUCTURE_CACHE_TICKS) {
            cachedStructureSnapshot =
                    EssenceCrucibleStructureService.evaluateSnapshot(level, worldPosition);
            cachedStructureGameTime = gameTime; cachedMembershipReceipt = membershipReceipt; cachedMembershipReady = membershipReady;
        }
        return cachedStructureSnapshot;
    }

    public EssenceCrucibleStructureStats structureStats() {
        return structureSnapshot().stats();
    }

    public void invalidateStructureCache() {
        cachedStructureSnapshot = null;
        cachedStructureGameTime = Long.MIN_VALUE;
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

    public EssenceCrucibleAccessMode accessMode() {
        return accessMode;
    }

    /** Placement establishes identity; later interactions only authorize existing infrastructure. */
    public boolean bindOnPlacement(Player player) {
        if (!(level instanceof ServerLevel serverLevel)) return false;
        if (!binding.place(EssenceSavedData.get(serverLevel.getServer()), player.getUUID())) return false;
        ownerId = player.getUUID(); ownerName = player.getGameProfile().getName();
        processingArmed = true; processingTicks = 0; bindingChanged(); return true;
    }
    @Override public MachineBinding machineBinding() { return binding; }
    public boolean canPlayerConfigure(Player player) { return ServerMenuAccess.canReach(player, this) && authorize(player, MachineAction.CONFIGURE); }
    public UUID fundingActor() {
        if (!(level instanceof ServerLevel serverLevel)) return null;
        var saved = EssenceSavedData.get(serverLevel.getServer());
        if (binding.refresh(saved)) bindingChanged();
        return binding.fundingActor(saved);
    }

    public boolean canPlayerUse(Player player) {
        return authorize(player, MachineAction.USE);
    }

    public boolean canPlayerManage(Player player) {
        return authorize(player, MachineAction.REBIND);
    }

    public long storedEssence(int index) {
        if (index < 0 || index >= EssenceCrucibleEssences.ORDERED.size()) {
            return 0L;
        }

        return storedEssence(
                EssenceCrucibleEssences.ORDERED.get(index)
        );
    }

    public long storedEssence(EssenceDefinition essence) {
        return storedEssenceExact(essence).min(java.math.BigInteger.valueOf(Long.MAX_VALUE)).longValueExact();
    }
    public java.math.BigInteger storedEssenceExact(EssenceDefinition essence) {
        UUID actor = fundingActor();
        return actor == null ? java.math.BigInteger.ZERO : EssenceSavedData.get(((ServerLevel) level).getServer())
                .reservoirs().read(actor).whole(essence.id());
    }
    public java.math.BigInteger storedUnitsTotal() {
        UUID actor = fundingActor();
        return actor == null ? java.math.BigInteger.ZERO : EssenceSavedData.get(((ServerLevel) level).getServer())
                .reservoirs().read(actor).units().values().stream().reduce(java.math.BigInteger.ZERO, java.math.BigInteger::add);
    }

    public long[] storedEssenceSnapshot() {
        long[] snapshot =
                new long[EssenceCrucibleEssences.ORDERED.size()];
        for (int i = 0; i < snapshot.length; i++) {
            snapshot[i] = storedEssence(i);
        }
        return snapshot;
    }

    public java.math.BigInteger totalStoredEssence() {
        java.math.BigInteger total = java.math.BigInteger.ZERO;
        for (EssenceDefinition essence : EssenceCrucibleEssences.ORDERED)
            total = total.add(storedEssenceExact(essence));
        return total;
    }

    public long effectiveReservoirCapacity() {
        return localFillLimit().min(java.math.BigInteger.valueOf(Long.MAX_VALUE)).longValueExact();
    }
    public java.math.BigInteger localFillLimit() {
        if (!(level instanceof ServerLevel serverLevel)) return java.math.BigInteger.valueOf(structureStats().reservoirCapacity());
        return MachineCapacity.limit(structureStats().reservoirCapacity(), binding.resolve(EssenceSavedData.get(serverLevel.getServer())));
    }

    public int processingTicks() {
        return processingTicks;
    }

    public boolean isChanneling() { return !channelingPlayers().isEmpty(); }
    public List<UUID> channelingPlayers() { return EssenceCrucibleChannelService.players(this); }
    public boolean isChanneling(UUID player) { return EssenceCrucibleChannelService.isChanneling(this, player); }
    public boolean isDissolving() { return processingVisualActive; }
    private List<UUID> transferringPlayers() {
        long tick = level == null ? 0 : level.getGameTime();
        return channelingPlayers().stream().filter(id -> recentTransfers.getOrDefault(id, Long.MIN_VALUE) >= tick - 4
                && !channelingPaused(id)).toList();
    }

    public double distanceTo(Player player) {
        Vec3 center = Vec3.atCenterOf(worldPosition);
        return Math.sqrt(player.distanceToSqr(center));
    }

    public boolean inTransferRange(Player player) {
        double range = structureStats().transferRange();
        return player.distanceToSqr(Vec3.atCenterOf(worldPosition))
                <= range * range;
    }

    public boolean startChanneling(ServerPlayer player) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        if (player.serverLevel() != serverLevel
                || !authorize(player, MachineAction.SPEND)
                || !inTransferRange(player)
                || level.getBlockEntity(worldPosition) != this
                || EssenceSavedData.get(serverLevel.getServer()).getPlayerData(player.getUUID()).crucibleChannelSelection() == 0) {
            return false;
        }

        if (!EssenceCrucibleChannelService.claim(player, this)) return false;
        setChanged();
        return true;
    }

    public void stopChanneling() {
        EssenceCrucibleChannelService.stopPlayers(this);
        recentTransfers.clear(); recentTransferTypes.clear(); setChanged();
    }
    public void stopChanneling(UUID player) {
        EssenceCrucibleChannelService.stop(this, player);
        recentTransfers.remove(player); recentTransferTypes.remove(player); setChanged();
    }
    public void setChannelingRequested(ServerPlayer player, boolean active) {
        if (!canPlayerUse(player) || ownerId == null) return;
        if (active) startChanneling(player); else stopChanneling(player.getUUID());
    }

    @Override public void bindingChanged() {
        processingTicks = 0; resetSmartRoundRobinWait(); stopChanneling();
        invalidateStructureCache(); infusionVisualCheckTick = Long.MIN_VALUE; setChanged();
    }
    public boolean setProcessingEnabled(Player player, boolean enabled) {
        if (!canPlayerConfigure(player)) return false;
        if (enabled && (!(level instanceof ServerLevel serverLevel) || !binding.start(EssenceSavedData.get(serverLevel.getServer()), player.getUUID(), null))) return false;
        processingArmed = enabled; processingTicks = 0; setChanged(); return true;
    }
    public boolean processingEnabled() { return processingArmed; }

    public EssenceCrucibleDissolutionMode dissolutionMode() {
        return dissolutionMode;
    }

    public void setDissolutionMode(EssenceCrucibleDissolutionMode mode) {
        EssenceCrucibleDissolutionMode normalized = mode == null
                ? EssenceCrucibleDissolutionMode.SMART_ROUND_ROBIN
                : mode;
        if (dissolutionMode == normalized) {
            return;
        }

        dissolutionMode = normalized;
        processingTicks = 0;
        resetSmartRoundRobinWait();
        setChanged();
    }

    private void resetSmartRoundRobinWait() {
        smartRoundRobinBlockedTicks = 0;
        smartRoundRobinWaitSlot = -1;
    }

    private void tickDissolution() {
        if (!(level instanceof ServerLevel serverLevel) || fundingActor() == null || !processingArmed) {
            return;
        }

        EssenceCrucibleStructureStats stats = structureStats();
        int activeSlots = activeInputSlotCount(stats);
        if (!hasAnyActiveInput(activeSlots)) {
            resetSmartRoundRobinWait();
            if (processingTicks != 0) {
                processingTicks = 0;
                setChanged();
            }
            return;
        }

        /*
         * A config reload may make one or more already-inserted stacks unmapped.
         * Those stacks remain untouched, while any other still-valid lanes may
         * continue processing normally. If every occupied active lane is now
         * invalid, preserve the current progress so a later mapping reload can
         * resume without destroying anything.
         */
        if (!hasAnyMappedActiveInput(activeSlots)) {
            processingVisualActive = false;
            return;
        }

        int requiredTicks = stats.dissolutionTicksPerItem();
        java.math.BigInteger capacity = localFillLimit();
        DissolutionPlan potential = buildDissolutionPlan(
                activeSlots,
                stats.simultaneousItemProcesses(),
                capacity
        );
        RoundRobinCapacityBlock capacityBlock = roundRobinCapacityBlock(
                activeSlots,
                capacity
        );
        /*
         * A capacity-blocked Smart/Strict lane is waiting, not actively
         * dissolving. Keep the progress bar behavior, but do not advertise
         * processing particles until the selected scheduler can actually
         * complete a dissolution plan.
         */
        processingVisualActive = potential.totalItems() > 0 && capacityBlock == null;

        if (processingTicks < requiredTicks) {
            processingTicks++;
            setChanged();
        }

        if (processingTicks < requiredTicks) {
            return;
        }

        /*
         * Strict Round Robin never skips a temporarily-full preferred lane.
         * Smart Round Robin gives that lane a few complete dissolution cycles
         * to become viable before allowing the normal skip/redistribution path.
         * An item whose output could never fit into an empty reservoir is not
         * considered a temporary capacity block and therefore cannot deadlock
         * either mode forever.
         */
        if (capacityBlock != null) {
            if (dissolutionMode == EssenceCrucibleDissolutionMode.STRICT_ROUND_ROBIN) {
                processingVisualActive = false;
                return;
            }

            if (dissolutionMode == EssenceCrucibleDissolutionMode.SMART_ROUND_ROBIN) {
                if (smartRoundRobinWaitSlot != capacityBlock.slot()) {
                    smartRoundRobinWaitSlot = capacityBlock.slot();
                    smartRoundRobinBlockedTicks = 0;
                }

                /*
                 * Hold the completed progress bar while Smart Round Robin waits
                 * for capacity. The old implementation reset processingTicks
                 * once per wait cycle, which made the bar loop continuously and
                 * incorrectly drove processing particles while nothing could be
                 * dissolved.
                 */
                long waitTicks = (long) Math.max(1, requiredTicks)
                        * SMART_ROUND_ROBIN_WAIT_CYCLES;
                if (smartRoundRobinBlockedTicks < waitTicks) {
                    smartRoundRobinBlockedTicks++;
                    processingVisualActive = false;
                    return;
                }

                /*
                 * The wait window has expired. Keep the bar full and allow the
                 * normal plan builder to skip this blocked turn. If nothing else
                 * fits yet, stay full and retry without restarting the animation.
                 */
                processingVisualActive = false;
            }
        } else {
            resetSmartRoundRobinWait();
        }

        /* Shared storage or structure state may have changed during the cycle. */
        DissolutionPlan plan = buildDissolutionPlan(
                activeSlots,
                stats.simultaneousItemProcesses(),
                capacity
        );
        if (plan.totalItems() <= 0) {
            processingVisualActive = false;
            return;
        }

        EssenceSavedData saved = EssenceSavedData.get(serverLevel.getServer());
        UUID actor = fundingActor();
        if (actor == null) return;
        Map<String, Long> previousCarry = fractionalCarry();
        Map<net.minecraft.resources.ResourceLocation, java.math.BigInteger> credit = new LinkedHashMap<>();
        for (EssenceDefinition essence : EssenceCrucibleEssences.ORDERED) {
            String key = "dissolution/" + essence.id();
            java.math.BigInteger units = java.math.BigInteger.valueOf(plan.outputs().getOrDefault(essence, 0L))
                    .multiply(ReservoirService.SCALE)
                    .add(java.math.BigInteger.valueOf(plan.nextCarry().getOrDefault(key, 0L)
                            - previousCarry.getOrDefault(key, 0L)));
            if (units.signum() != 0) credit.put(essence.id(), units);
        }
        if (!MachineCapacity.accepts(storedUnitsTotal(), credit.values().stream().reduce(java.math.BigInteger.ZERO, java.math.BigInteger::add), localFillLimit())) return;
        List<ItemStack> before = items.stream().map(ItemStack::copy).toList();
        try {
            if (!saved.reservoirs().credit(actor, credit, new ReservoirService.Mutation() {
                public boolean apply() {
                    for (int slot = 0; slot < plan.consumeCounts().length; slot++) {
                        int count = plan.consumeCounts()[slot];
                        if (items.get(slot).getCount() < count) return false;
                        if (count > 0) {
                            ItemStack remaining = items.get(slot).copy();
                            remaining.shrink(count);
                            items.set(slot, remaining.isEmpty() ? ItemStack.EMPTY : remaining);
                        }
                    }
                    return true;
                }
                public void rollback() { for (int i = 0; i < before.size(); i++) items.set(i, before.get(i)); }
            })) return;
        } catch (RuntimeException invalid) {
            processingVisualActive = false;
            return;
        }
        processingTicks = 0;
        resetSmartRoundRobinWait();
        dissolutionSlotCursor = activeSlots <= 1
                ? 0
                : (dissolutionSlotCursor + 1) % activeSlots;
        setChanged();
    }

    private DissolutionPlan buildDissolutionPlan(
            int activeSlots,
            int maxItems,
            java.math.BigInteger capacity
    ) {
        int[] consumeCounts = new int[MAX_INPUT_SLOTS];
        Map<EssenceDefinition, Long> outputs = new LinkedHashMap<>();

        if (activeSlots <= 0 || maxItems <= 0) {
            return new DissolutionPlan(consumeCounts, outputs, 0);
        }

        java.math.BigInteger exactTotal = totalStoredEssence();
        if (exactTotal.compareTo(capacity) > 0) {
            return new DissolutionPlan(consumeCounts, outputs, 0);
        }

        java.math.BigInteger currentTotal = exactTotal;
        List<DissolutionCandidate> candidates = collectDissolutionCandidates(activeSlots);
        if (candidates.isEmpty()) {
            return new DissolutionPlan(consumeCounts, outputs, 0);
        }

        if (dissolutionMode == EssenceCrucibleDissolutionMode.LOWEST_STORED) {
            candidates.sort(Comparator.comparing(
                    (DissolutionCandidate candidate) ->
                            lowestStoredPriority(candidate.mapping())
            ));
        } else if (dissolutionMode == EssenceCrucibleDissolutionMode.HIGHEST_STORED) {
            candidates.sort((left, right) -> highestStoredPriority(right.mapping()).compareTo(highestStoredPriority(left.mapping())));
        }

        return buildEvenDissolutionPlan(
                candidates,
                maxItems,
                capacity.subtract(currentTotal),
                consumeCounts,
                outputs
        );
    }

    /**
     * Collect mapped active lanes in rotating order. Java's List.sort is
     * stable, so reservoir-priority modes retain round-robin order as their
     * tie-breaker without needing a second slot-priority field.
     */
    private List<DissolutionCandidate> collectDissolutionCandidates(int activeSlots) {
        List<DissolutionCandidate> candidates = new ArrayList<>(activeSlots);
        int start = Math.floorMod(dissolutionSlotCursor, activeSlots);

        for (int offset = 0; offset < activeSlots; offset++) {
            int slot = (start + offset) % activeSlots;
            ItemStack stack = items.get(slot);
            if (stack.isEmpty()) {
                continue;
            }

            ResolvedDissolution mapping = resolveDissolution(stack);
            if (mapping != null) {
                candidates.add(new DissolutionCandidate(slot, mapping));
            }
        }
        return candidates;
    }

    private DissolutionPlan buildEvenDissolutionPlan(
            List<DissolutionCandidate> candidates, int maxItems, java.math.BigInteger room,
            int[] consumeCounts, Map<EssenceDefinition, Long> outputs) {
        Map<String, Long> carry = new LinkedHashMap<>(fractionalCarry());
        int totalItems = 0;
        int baseShare = maxItems / candidates.size();
        int remainder = maxItems % candidates.size();
        for (int i = 0; i < candidates.size(); i++) {
            DissolutionCandidate candidate = candidates.get(i);
            int requested = baseShare + (i < remainder ? 1 : 0);
            for (int n = 0; n < requested; n++) {
                long credited = addPlannedItem(candidate, room, consumeCounts, outputs, carry);
                if (credited < 0) break;
                room = room.subtract(java.math.BigInteger.valueOf(credited));
                totalItems++;
            }
        }
        while (totalItems < maxItems) {
            boolean progressed = false;
            for (DissolutionCandidate candidate : candidates) {
                if (totalItems >= maxItems) break;
                long credited = addPlannedItem(candidate, room, consumeCounts, outputs, carry);
                if (credited < 0) continue;
                room = room.subtract(java.math.BigInteger.valueOf(credited));
                totalItems++;
                progressed = true;
            }
            if (!progressed) break;
        }
        return new DissolutionPlan(consumeCounts, outputs, totalItems, Map.copyOf(carry));
    }

    private Map<String, Long> fractionalCarry() {
        if (ownerId == null || !(level instanceof ServerLevel serverLevel)) return Map.of();
        var saved = EssenceSavedData.get(serverLevel.getServer());
        UUID actor = fundingActor();
        if (actor == null) return Map.of();
        var view = saved.reservoirs().read(actor);
        Map<String, Long> result = new LinkedHashMap<>();
        for (var essence : EssenceCrucibleEssences.ORDERED)
            result.put("dissolution/" + essence.id(), view.units(essence.id()).remainder(ReservoirService.SCALE).longValueExact());
        return result;
    }

    /** Preview one item without touching persistent state; zero-whole yields still accumulate. */
    private long addPlannedItem(DissolutionCandidate candidate, java.math.BigInteger room, int[] consumeCounts,
                                Map<EssenceDefinition, Long> outputs, Map<String, Long> carry) {
        int slot = candidate.slot();
        if (consumeCounts[slot] >= items.get(slot).getCount()) return -1;
        Map<EssenceDefinition, Long> credit = new LinkedHashMap<>();
        Map<String, Long> next = new LinkedHashMap<>();
        long total = 0;
        try {
            for (Map.Entry<EssenceDefinition, Long> output : candidate.mapping().outputs().entrySet()) {
                String key = "dissolution/" + output.getKey().id();
                FractionalAmountService.Resolution result = FractionalAmountService.accumulate(
                        output.getValue(), 1, carry.getOrDefault(key, 0L));
                total = Math.addExact(total, result.wholeAmount());
                credit.put(output.getKey(), result.wholeAmount());
                next.put(key, result.nextCarry());
                Math.addExact(outputs.getOrDefault(output.getKey(), 0L), result.wholeAmount());
            }
        } catch (ArithmeticException invalid) { return -1; }
        if (java.math.BigInteger.valueOf(total).compareTo(room) > 0) return -1;
        credit.forEach((essence, amount) -> { if (amount > 0) outputs.merge(essence, amount, Math::addExact); });
        carry.putAll(next);
        consumeCounts[slot]++;
        return total;
    }

    /**
     * Returns a temporary capacity block only for the currently preferred
     * round-robin lane. Lowest/Highest/Skip modes deliberately never wait.
     */
    private RoundRobinCapacityBlock roundRobinCapacityBlock(
            int activeSlots,
            java.math.BigInteger capacity
    ) {
        if (dissolutionMode != EssenceCrucibleDissolutionMode.SMART_ROUND_ROBIN
                && dissolutionMode != EssenceCrucibleDissolutionMode.STRICT_ROUND_ROBIN) {
            return null;
        }

        java.math.BigInteger exactTotal = totalStoredEssence();
        if (exactTotal.compareTo(capacity) > 0) {
            return null;
        }

        java.math.BigInteger currentTotal = exactTotal;
        List<DissolutionCandidate> candidates = collectDissolutionCandidates(activeSlots);
        if (candidates.isEmpty()) {
            return null;
        }

        DissolutionCandidate preferred = candidates.get(0);
        long perItemTotal = mappingOutputTotal(preferred.mapping());
        if (perItemTotal <= 0L || java.math.BigInteger.valueOf(perItemTotal).compareTo(capacity) > 0) {
            return null;
        }

        java.math.BigInteger room = capacity.subtract(currentTotal);
        return java.math.BigInteger.valueOf(perItemTotal).compareTo(room) > 0
                ? new RoundRobinCapacityBlock(preferred.slot())
                : null;
    }

    /**
     * For multi-output mapped items, Lowest Stored considers the least-stocked
     * Essence they produce. This makes any depleted output sufficient to pull
     * that lane forward. Ties retain rotating slot order.
     */
    private java.math.BigInteger lowestStoredPriority(ResolvedDissolution mapping) {
        java.math.BigInteger lowest = null;
        boolean found = false;
        for (EssenceDefinition essence : mapping.outputs().keySet()) {
            if (!isDissolutionEssenceSupported(essence)) {
                continue;
            }
            var amount = storedEssenceExact(essence);
            lowest = lowest == null ? amount : lowest.min(amount);
            found = true;
        }
        return found ? lowest : java.math.BigInteger.valueOf(Long.MAX_VALUE);
    }

    /**
     * Highest Stored mirrors Lowest Stored using the most-stocked output type.
     * This is useful when deliberately concentrating production into an Essence
     * that the player's automation is already consuming heavily.
     */
    private java.math.BigInteger highestStoredPriority(ResolvedDissolution mapping) {
        java.math.BigInteger highest = java.math.BigInteger.ZERO;
        boolean found = false;
        for (EssenceDefinition essence : mapping.outputs().keySet()) {
            if (!isDissolutionEssenceSupported(essence)) {
                continue;
            }
            highest = highest.max(storedEssenceExact(essence));
            found = true;
        }
        return found ? highest : java.math.BigInteger.valueOf(Long.MIN_VALUE);
    }

    private record DissolutionCandidate(
            int slot,
            ResolvedDissolution mapping
    ) {
    }

    private record RoundRobinCapacityBlock(int slot) {
    }

    private long mappingOutputTotal(ResolvedDissolution mapping) {
        long total = 0L;
        Map<String, Long> carry = fractionalCarry();
        try {
            for (Map.Entry<EssenceDefinition, Long> output : mapping.outputs().entrySet()) {
                if (output.getValue() <= 0 || !isDissolutionEssenceSupported(output.getKey())) return 0;
                total = Math.addExact(total, FractionalAmountService.accumulate(output.getValue(), 1,
                        carry.getOrDefault("dissolution/" + output.getKey().id(), 0L)).wholeAmount());
            }
        } catch (ArithmeticException invalid) { return 0; }
        return total;
    }

    private boolean hasAnyActiveInput(int activeSlots) {
        for (int slot = 0; slot < activeSlots; slot++) {
            if (!items.get(slot).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAnyMappedActiveInput(int activeSlots) {
        for (int slot = 0; slot < activeSlots; slot++) {
            ItemStack stack = items.get(slot);
            if (!stack.isEmpty() && resolveDissolution(stack) != null) {
                return true;
            }
        }
        return false;
    }

    private record DissolutionPlan(
            int[] consumeCounts,
            Map<EssenceDefinition, Long> outputs,
            int totalItems,
            Map<String, Long> nextCarry
    ) {
        private DissolutionPlan(int[] consumeCounts, Map<EssenceDefinition, Long> outputs, int totalItems) {
            this(consumeCounts, outputs, totalItems, Map.of());
        }
    }

    private record ResolvedDissolution(
            Map<EssenceDefinition, Long> outputs
    ) {
    }

    private static ResolvedDissolution resolveDissolution(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }

        if (EssentiumCarrierData.isEssentium(stack)) {
            return EssentiumCarrierData.readValidated(stack)
                    .filter(value -> value.amount() <= Long.MAX_VALUE / FractionalAmountService.SCALE)
                    .map(value -> new ResolvedDissolution(
                            Map.of(value.essence(), EssentiumCarrierData.extractionYieldMicroUnits(value))
                    ))
                    .orElse(null);
        }

        Map<EssenceDefinition, Long> mapping = ItemEssenceMappingRegistry.resolveDissolution(stack);
        return mapping.isEmpty() ? null : new ResolvedDissolution(mapping);
    }

    private static boolean isDissolutionEssenceSupported(EssenceDefinition essence) {
        return essence != null
                && EssenceCrucibleEssences.indexOf(essence) >= 0;
    }


    public static boolean isValidNewInput(ItemStack stack) {
        return resolveDissolution(stack) != null;
    }

    boolean validPlayerChannel(ServerPlayer player) {
        return level instanceof ServerLevel serverLevel && player.isAlive()
                && player.server.getPlayerList().getPlayer(player.getUUID()) == player
                && player.serverLevel() == serverLevel && level.hasChunkAt(worldPosition)
                && level.getBlockEntity(worldPosition) == this && authorize(player, MachineAction.SPEND)
                && inTransferRange(player);
    }
    long playerChannelDemand(ServerPlayer player) {
        if (!validPlayerChannel(player)) return 0;
        var data = EssenceSavedData.get(player.server).getPlayerData(player.getUUID());
        long demand = 0;
        for (int i = 0; i < EssenceCrucibleEssences.ORDERED.size(); i++) {
            var essence = EssenceCrucibleEssences.ORDERED.get(i);
            if (CrucibleChannelSelection.selected(data.crucibleChannelSelection(), i))
                demand = ConcurrentChannelScheduler.saturatedAdd(demand, Math.min(storedEssence(essence), Long.MAX_VALUE - data.getAvailable(essence)));
        }
        return demand;
    }
    public boolean channelingPaused(UUID playerId) {
        if (!isChanneling(playerId) || !(level instanceof ServerLevel serverLevel)) return false;
        var player = serverLevel.getServer().getPlayerList().getPlayer(playerId);
        return player == null || playerChannelDemand(player) == 0;
    }
    public boolean channelingPaused() { return isChanneling() && channelingPlayers().stream().allMatch(this::channelingPaused); }
    long deliverPlayer(ServerPlayer player, long budget) {
        long moved = transferFairly(player, budget);
        if (moved > 0) { recentTransfers.put(player.getUUID(), level.getGameTime()); setChanged(); }
        return moved;
    }

    private long transferFairly(
            ServerPlayer player,
            long budget
    ) {
        EssenceSavedData saved =
                EssenceSavedData.get(player.server);
        PlayerEssenceData playerData =
                saved.getPlayerData(player.getUUID());

        if (!authorize(player, MachineAction.SPEND)) return 0L;
        Map<net.minecraft.resources.ResourceLocation, Long> planned = new LinkedHashMap<>();
        var essences = EssenceCrucibleEssences.ORDERED;
        int essenceCount = essences.size();
        long selectionRevision = playerData.crucibleChannelSelectionRevision();
        int selected = playerData.crucibleChannelSelection();
        int transferCursor = transferCursors.getOrDefault(player.getUUID(), 0);
        long remaining = budget;
        long movedTotal = 0L;

        /*
         * Redistribute budget left behind by a reservoir category that empties
         * mid-share. The starting category rotates so no enabled Essence type
         * can be permanently starved.
         */
        for (int pass = 0; pass < essenceCount && remaining > 0L; pass++) {
            int eligible = 0;
            for (int i = 0; i < essenceCount; i++) {
                int index = (transferCursor + i) % essenceCount;
                if (!CrucibleChannelSelection.selected(selected, index)) continue;
                EssenceDefinition essence = essences.get(index);
                long stored = storedEssence(essence) - planned.getOrDefault(essence.id(), 0L);
                long playerCurrent = Math.addExact(playerData.getAvailable(essence), planned.getOrDefault(essence.id(), 0L));
                if (stored > 0L && playerCurrent < Long.MAX_VALUE) {
                    eligible++;
                }
            }

            if (eligible == 0) {
                break;
            }

            long share = remaining / eligible;
            long extra = remaining % eligible;
            if (share == 0L) {
                share = 1L;
                extra = 0L;
            }

            boolean movedThisPass = false;
            int seenEligible = 0;

            for (int i = 0; i < essenceCount && remaining > 0L; i++) {
                int index = (transferCursor + i) % essenceCount;
                if (!CrucibleChannelSelection.selected(selected, index)) continue;
                EssenceDefinition essence = essences.get(index);
                long stored = storedEssence(essence) - planned.getOrDefault(essence.id(), 0L);
                if (stored <= 0L) {
                    continue;
                }

                long playerCurrent = Math.addExact(playerData.getAvailable(essence), planned.getOrDefault(essence.id(), 0L));
                long room = Long.MAX_VALUE - playerCurrent;
                if (room <= 0L) {
                    continue;
                }

                long allocation = share + (seenEligible < extra ? 1L : 0L);
                seenEligible++;

                long requested = Math.min(
                        remaining,
                        Math.min(
                                stored,
                                Math.min(room, allocation)
                        )
                );

                if (requested <= 0L) {
                    continue;
                }

                long moved = requested;
                planned.merge(essence.id(), moved, Math::addExact);

                remaining -= moved;
                movedTotal += moved;
                movedThisPass = true;
            }

            if (!movedThisPass) {
                break;
            }
        }

        if (movedTotal > 0 && !saved.reservoirs().channel(player.getUUID(), planned, new ReservoirService.Mutation() {
            public boolean apply() { return playerData.crucibleChannelSelectionRevision() == selectionRevision && playerData.crucibleChannelSelection() == selected; }
            public void rollback() { }
        })) return 0L;
        if (movedTotal > 0) {
            int mask = 0;
            for (int i = 0; i < essences.size(); i++) if (planned.getOrDefault(essences.get(i).id(), 0L) > 0) mask |= 1 << i;
            recentTransferTypes.put(player.getUUID(), mask);
        }
        transferCursors.put(player.getUUID(), (transferCursor + 1) % essenceCount);
        return movedTotal;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.essence_ascendance.essence_crucible");
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

        return new EssenceCrucibleMenu(
                containerId,
                playerInventory,
                this
        );
    }

    @Override
    //? if >=26.1.2
    /*protected void saveAdditional(ValueOutput tag) {*/
    //? if <26.1.2
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        //? if >=26.1.2
        /*super.saveAdditional(tag);*/
        //? if <26.1.2
        super.saveAdditional(tag, registries);
        //? if >=26.1.2
        /*ContainerHelper.saveAllItems(tag, items);*/
        //? if <26.1.2
        ContainerHelper.saveAllItems(tag, items, registries);
        binding.save(tag);

        if (ownerId != null) {
            //? if >=26.1.2
            /*tag.store(OWNER_TAG, UUIDUtil.CODEC, ownerId);*/
            //? if <26.1.2
            tag.putUUID(OWNER_TAG, ownerId);
        }
        if (!ownerName.isBlank()) {
            tag.putString(OWNER_NAME_TAG, ownerName);
        }
        tag.putString(ACCESS_MODE_TAG, accessMode.serializedName());
        tag.putBoolean("processing_armed", processingArmed);
        tag.putInt(PROCESSING_TICKS_TAG, processingTicks);
        tag.putString(DISSOLUTION_MODE_TAG, dissolutionMode.serializedName());

    }

    @Override
    //? if >=26.1.2
    /*protected void loadAdditional(ValueInput tag) {*/
    //? if <26.1.2
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        //? if >=26.1.2
        /*CompoundTag visual = tag.read(MachineVisualState.TAG, CompoundTag.CODEC).orElse(null);*/
        //? if <26.1.2
        CompoundTag visual = tag.contains(MachineVisualState.TAG, net.minecraft.nbt.Tag.TAG_COMPOUND)
                //? if <26.1.2
                ? tag.getCompound(MachineVisualState.TAG) : null;
        if (visual != null) {
            clientVisualState = MachineVisualState.Crucible.read(visual);
            return;
        }
        //? if >=26.1.2
        /*super.loadAdditional(tag);*/
        //? if <26.1.2
        super.loadAdditional(tag, registries);
        //? if >=26.1.2
        /*ContainerHelper.loadAllItems(tag, items);*/
        //? if <26.1.2
        ContainerHelper.loadAllItems(tag, items, registries);
        binding.load(tag);

        //? if >=26.1.2
        /*ownerId = tag.read(OWNER_TAG, UUIDUtil.CODEC).orElse(null);*/
        //? if <26.1.2
        ownerId = tag.hasUUID(OWNER_TAG) ? tag.getUUID(OWNER_TAG) : null;
        ownerName = tag.getString(OWNER_NAME_TAG);
        accessMode = EssenceCrucibleAccessMode.fromSerializedName(
                tag.getString(ACCESS_MODE_TAG)
        );
        //? if >=26.1.2
        /*processingArmed = tag.getBooleanOr("processing_armed", true);*/
        //? if <26.1.2
        processingArmed = !tag.contains("processing_armed") || tag.getBoolean("processing_armed");
        processingTicks = Math.max(0, tag.getInt(PROCESSING_TICKS_TAG));
        dissolutionMode = EssenceCrucibleDissolutionMode.fromSerializedName(
                tag.getString(DISSOLUTION_MODE_TAG)
        );
        dissolutionSlotCursor = 0;
        resetSmartRoundRobinWait();

        /* Active channel state deliberately does not survive save/reload. */
        EssenceCrucibleChannelService.removeSource(this);
        transferCursors.clear(); recentTransfers.clear(); recentTransferTypes.clear();
        cachedStructureSnapshot = null;
        cachedStructureGameTime = Long.MIN_VALUE;
        processingVisualActive = false;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        MachineVisualState.Crucible visual = visualState();
        if (visual != null) tag.put(MachineVisualState.TAG, visual.save());
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this,
                (entity, registries) -> entity.getUpdateTag(registries));
    }

    @Override
    public void setRemoved() {
        EssenceCrucibleChannelService.removeSource(this);
        stopChanneling();
        super.setRemoved();
    }

    public int activeInputSlotCount() {
        return activeInputSlotCount(structureStats());
    }

    private int activeInputSlotCount(EssenceCrucibleStructureStats stats) {
        return Math.max(1, Math.min(MAX_INPUT_SLOTS, stats.usableItemSlots()));
    }

    public boolean isInputSlotActive(int slot) {
        return slot >= 0 && slot < activeInputSlotCount();
    }

    public boolean containsMatchingItemInOtherSlot(int slot, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        for (int other = 0; other < items.size(); other++) {
            if (other == slot) {
                continue;
            }
            ItemStack existing = items.get(other);
            if (!existing.isEmpty()
                    && ItemStack.isSameItemSameComponents(existing, stack)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int getContainerSize() {
        return items.size();
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
        return slot >= 0 && slot < items.size()
                ? items.get(slot)
                : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (slot < 0 || slot >= items.size() || amount <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack removed = ContainerHelper.removeItem(items, slot, amount);
        if (!removed.isEmpty()) {
            processingTicks = 0;
            resetSmartRoundRobinWait();
            setChanged();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (slot < 0 || slot >= items.size()) {
            return ItemStack.EMPTY;
        }
        ItemStack removed = ContainerHelper.takeItem(items, slot);
        processingTicks = 0;
        resetSmartRoundRobinWait();
        if (!removed.isEmpty()) {
            setChanged();
        }
        return removed;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= items.size()) {
            return;
        }

        ItemStack normalized = stack == null ? ItemStack.EMPTY : stack;
        if (!normalized.isEmpty()) {
            normalized.limitSize(getMaxStackSize(normalized));
        }

        ItemStack previous = items.get(slot);
        boolean sameItem = ItemStack.isSameItemSameComponents(previous, normalized);
        boolean replacingOccupiedType = !previous.isEmpty()
                && !normalized.isEmpty()
                && !sameItem;

        items.set(slot, normalized);
        if (replacingOccupiedType || (normalized.isEmpty() && !previous.isEmpty())) {
            processingTicks = 0;
            resetSmartRoundRobinWait();
        }
        setChanged();
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

    @Override
    public boolean stillValid(Player player) {
        return ServerMenuAccess.canReach(player, this) && canPlayerUse(player);
    }

    @Override
    public void clearContent() {
        items.clear();
        processingTicks = 0;
        resetSmartRoundRobinWait();
        setChanged();
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        if (level != null
                && !EssenceCrucibleStructureService.allowsAutomationConnection(
                        level,
                        worldPosition,
                        side
                )) {
            return NO_AUTOMATION_SLOTS;
        }

        int active = activeInputSlotCount();
        int[] slots = new int[active];
        for (int slot = 0; slot < active; slot++) {
            slots[slot] = slot;
        }
        return slots;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return isInputSlotActive(slot)
                && isValidNewInput(stack)
                && !containsMatchingItemInOtherSlot(slot, stack);
    }

    @Override
    public boolean canPlaceItemThroughFace(
            int slot,
            ItemStack stack,
            Direction direction
    ) {
        return canAcceptAutomatedStack(stack) && (level == null
                || EssenceCrucibleStructureService.allowsAutomationConnection(
                        level,
                        worldPosition,
                        direction
                ))
                && canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(
            int slot,
            ItemStack stack,
            Direction direction
    ) {
        /* Automation is insertion-only. GUI/manual extraction remains valid. */
        return false;
    }
}
