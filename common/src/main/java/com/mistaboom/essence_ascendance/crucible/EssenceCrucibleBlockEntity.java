package com.mistaboom.essence_ascendance.crucible;

import com.mistaboom.essence_ascendance.network.ServerMenuAccess;
import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import com.mistaboom.essence_ascendance.mapping.ItemEssenceMappingRegistry;
import com.mistaboom.essence_ascendance.balance.economy.FractionalAmountService;
import com.mistaboom.essence_ascendance.balance.economy.FractionalLedgerSavedData;
import com.mistaboom.essence_ascendance.infuser.EssentiumCarrierData;
import com.mistaboom.essence_ascendance.infuser.EssenceInfuserBalance;
import com.mistaboom.essence_ascendance.infuser.EssenceInfuserBlockEntity;
import com.mistaboom.essence_ascendance.visual.MachineVisualState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class EssenceCrucibleBlockEntity extends BlockEntity
        implements WorldlyContainer, MenuProvider {

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

    private UUID ownerId;
    private String ownerName = "";
    private EssenceCrucibleAccessMode accessMode =
            EssenceCrucibleAccessMode.PRIVATE;

    private int processingTicks = 0;
    private int dissolutionSlotCursor = 0;
    private EssenceCrucibleDissolutionMode dissolutionMode =
            EssenceCrucibleDissolutionMode.SMART_ROUND_ROBIN;

    /* Runtime-only fairness state for Smart Round Robin. */
    private int smartRoundRobinBlockedTicks = 0;
    private int smartRoundRobinWaitSlot = -1;

    /* Runtime-only channel state. */
    private UUID channelingPlayerId;
    private long transferRateRemainder = 0L;
    private int transferCursor = 0;

    /* Short-lived structure cache; pylon changes explicitly invalidate it. */
    private EssenceCrucibleStructureSnapshot cachedStructureSnapshot;
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
        crucible.tickDissolution();
        crucible.tickChanneling();
        if (level.getGameTime() % 5L == 0L) {
            crucible.syncVisualState();
        }
    }

    /** Observer snapshot; never used to decide gameplay or inventory behavior. */
    public MachineVisualState.Crucible visualState() {
        if (level != null && level.isClientSide) return clientVisualState == null
                ? MachineVisualState.IDLE_CRUCIBLE : clientVisualState;
        EssenceCrucibleStructureSnapshot structure = structureSnapshot();
        long[] stored = storedEssenceSnapshot();
        double total = 0.0D;
        long largest = 0L;
        int predominant = -1;
        for (int i = 0; i < stored.length; i++) {
            total += stored[i];
            if (stored[i] > largest) {
                largest = stored[i];
                predominant = i;
            }
        }
        long capacity = effectiveReservoirCapacity();
        int fill = capacity <= 0L ? 0 : (int) Math.min(10000L,
                Math.round(10000.0D * total / capacity / Math.max(1, stored.length)));
        List<BlockPos> pylons = structure.activePylons().stream()
                .map(EssenceCrucibleStructureSnapshot.ActivePylon::pos).toList();
        List<net.minecraft.resources.ResourceLocation> inputs = new ArrayList<>();
        for (int slot = 0; slot < activeInputSlotCount(structure.stats()); slot++) {
            ItemStack stack = items.get(slot);
            if (!stack.isEmpty()) inputs.add(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        }
        return new MachineVisualState.Crucible(processingVisualActive, channelingPlayerId,
                structure.stats().transferRatePerSecond(), predominant, fill, pylons, inputs,
                supplyingInfuserVisual());
    }

    /** Observer-only aggregation; inspects loaded chunks without requesting loads. */
    private boolean supplyingInfuserVisual() {
        if (!(level instanceof ServerLevel serverLevel) || ownerId == null) return false;
        long tick = serverLevel.getGameTime();
        if (infusionVisualCheckTick == tick) return supplyingInfuserVisual;
        infusionVisualCheckTick = tick;
        supplyingInfuserVisual = false;
        int range = (int) Math.ceil(EssenceInfuserBalance.linkRange());
        int minX = (worldPosition.getX() - range) >> 4;
        int maxX = (worldPosition.getX() + range) >> 4;
        int minZ = (worldPosition.getZ() - range) >> 4;
        int maxZ = (worldPosition.getZ() + range) >> 4;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (!(serverLevel.getChunk(x, z, ChunkStatus.FULL, false) instanceof LevelChunk chunk)) {
                    continue;
                }
                for (BlockEntity entity : chunk.getBlockEntities().values()) {
                    if (entity instanceof EssenceInfuserBlockEntity infuser
                            && !infuser.isRemoved()
                            && worldPosition.equals(infuser.linkedCruciblePos())
                            && ownerId.equals(infuser.ownerId())
                            && infuser.visualState().transferring()) {
                        supplyingInfuserVisual = true;
                        return true;
                    }
                }
            }
        }
        return false;
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
        if (cachedStructureSnapshot == null
                || gameTime - cachedStructureGameTime >= STRUCTURE_CACHE_TICKS) {
            cachedStructureSnapshot =
                    EssenceCrucibleStructureService.evaluateSnapshot(level, worldPosition);
            cachedStructureGameTime = gameTime;
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
        if (!ownerName.isBlank()) {
            return ownerName;
        }
        return ownerId == null ? "Unbound" : ownerId.toString();
    }

    public EssenceCrucibleAccessMode accessMode() {
        return accessMode;
    }

    public boolean bindOwner(Player player) {
        if (ownerId != null) {
            return ownerId.equals(player.getUUID());
        }

        ownerId = player.getUUID();
        ownerName = player.getGameProfile().getName();
        accessMode = EssenceCrucibleAccessMode.PRIVATE;
        setChanged();
        return true;
    }

    public boolean canPlayerUse(Player player) {
        if (ownerId == null) {
            return true;
        }

        /* TEAM/PUBLIC are reserved data shapes; PRIVATE is enforced for now. */
        return ownerId.equals(player.getUUID());
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
        PlayerEssenceData ownerData = ownerPlayerData();
        long shared = ownerData == null
                ? 0L
                : ownerData.getCrucibleStored(essence);

        return shared;
    }

    public long[] storedEssenceSnapshot() {
        long[] snapshot =
                new long[EssenceCrucibleEssences.ORDERED.size()];
        for (int i = 0; i < snapshot.length; i++) {
            snapshot[i] = storedEssence(i);
        }
        return snapshot;
    }

    public long totalStoredEssence() {
        long total = 0L;
        for (EssenceDefinition essence :
                EssenceCrucibleEssences.ORDERED) {
            total = Math.addExact(total, storedEssence(essence));
        }
        return total;
    }

    public long effectiveReservoirCapacity() {
        return EssenceCrucibleStructureService.effectiveReservoirCapacity(
                structureStats()
        );
    }

    public int processingTicks() {
        return processingTicks;
    }

    public boolean isChanneling() {
        return channelingPlayerId != null;
    }

    public boolean isDissolving() {
        return processingVisualActive;
    }

    public UUID channelingPlayerId() {
        return channelingPlayerId;
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
                || !canPlayerUse(player)
                || !inTransferRange(player)
                || level.getBlockEntity(worldPosition) != this
                || totalStoredEssence() <= 0L) {
            return false;
        }

        bindOwner(player);
        EssenceCrucibleChannelService.claim(player, this);
        channelingPlayerId = player.getUUID();
        setChanged();
        return true;
    }

    public void stopChanneling() {
        UUID previous = channelingPlayerId;
        channelingPlayerId = null;
        transferRateRemainder = 0L;

        if (previous != null) {
            EssenceCrucibleChannelService.release(previous, this);
            setChanged();
        }
    }

    public void setChannelingRequested(
            ServerPlayer player,
            boolean active
    ) {
        if (!bindOwner(player) || ownerId == null) {
            return;
        }

        if (active) {
            startChanneling(player);
        } else if (channelingPlayerId != null
                && channelingPlayerId.equals(player.getUUID())) {
            stopChanneling();
        }
    }

    private PlayerEssenceData ownerPlayerData() {
        if (ownerId == null || !(level instanceof ServerLevel serverLevel)) {
            return null;
        }

        return EssenceSavedData
                .get(serverLevel.getServer())
                .getPlayerData(ownerId);
    }

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
        if (!(level instanceof ServerLevel serverLevel) || ownerId == null) {
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
        long capacity = effectiveReservoirCapacity();
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

        for (int slot = 0; slot < plan.consumeCounts().length; slot++) {
            int consume = plan.consumeCounts()[slot];
            if (consume <= 0) {
                continue;
            }

            ItemStack stack = items.get(slot);
            stack.shrink(consume);
            if (stack.isEmpty()) {
                items.set(slot, ItemStack.EMPTY);
            }
        }

        EssenceSavedData saved = EssenceSavedData.get(serverLevel.getServer());
        for (Map.Entry<EssenceDefinition, Long> output : plan.outputs().entrySet()) {
            saved.addCrucibleStored(ownerId, output.getKey(), output.getValue());
        }

        FractionalLedgerSavedData.get(serverLevel.getServer()).commit(ownerId, plan.nextCarry());
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
            long capacity
    ) {
        int[] consumeCounts = new int[MAX_INPUT_SLOTS];
        Map<EssenceDefinition, Long> outputs = new LinkedHashMap<>();

        if (activeSlots <= 0 || maxItems <= 0) {
            return new DissolutionPlan(consumeCounts, outputs, 0);
        }

        long currentTotal = totalStoredEssence();
        if (currentTotal > capacity) {
            return new DissolutionPlan(consumeCounts, outputs, 0);
        }

        List<DissolutionCandidate> candidates = collectDissolutionCandidates(activeSlots);
        if (candidates.isEmpty()) {
            return new DissolutionPlan(consumeCounts, outputs, 0);
        }

        if (dissolutionMode == EssenceCrucibleDissolutionMode.LOWEST_STORED) {
            candidates.sort(Comparator.comparingLong(
                    (DissolutionCandidate candidate) ->
                            lowestStoredPriority(candidate.mapping())
            ));
        } else if (dissolutionMode == EssenceCrucibleDissolutionMode.HIGHEST_STORED) {
            candidates.sort((left, right) -> Long.compare(
                    highestStoredPriority(right.mapping()),
                    highestStoredPriority(left.mapping())
            ));
        }

        return buildEvenDissolutionPlan(
                candidates,
                maxItems,
                capacity - currentTotal,
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
            List<DissolutionCandidate> candidates, int maxItems, long room,
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
                room -= credited;
                totalItems++;
            }
        }
        while (totalItems < maxItems) {
            boolean progressed = false;
            for (DissolutionCandidate candidate : candidates) {
                if (totalItems >= maxItems) break;
                long credited = addPlannedItem(candidate, room, consumeCounts, outputs, carry);
                if (credited < 0) continue;
                room -= credited;
                totalItems++;
                progressed = true;
            }
            if (!progressed) break;
        }
        return new DissolutionPlan(consumeCounts, outputs, totalItems, Map.copyOf(carry));
    }

    private Map<String, Long> fractionalCarry() {
        return ownerId != null && level instanceof ServerLevel serverLevel
                ? FractionalLedgerSavedData.get(serverLevel.getServer()).snapshot(ownerId) : Map.of();
    }

    /** Preview one item without touching persistent state; zero-whole yields still accumulate. */
    private long addPlannedItem(DissolutionCandidate candidate, long room, int[] consumeCounts,
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
        if (total > room) return -1;
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
            long capacity
    ) {
        if (dissolutionMode != EssenceCrucibleDissolutionMode.SMART_ROUND_ROBIN
                && dissolutionMode != EssenceCrucibleDissolutionMode.STRICT_ROUND_ROBIN) {
            return null;
        }

        long currentTotal = totalStoredEssence();
        if (currentTotal > capacity) {
            return null;
        }

        List<DissolutionCandidate> candidates = collectDissolutionCandidates(activeSlots);
        if (candidates.isEmpty()) {
            return null;
        }

        DissolutionCandidate preferred = candidates.get(0);
        long perItemTotal = mappingOutputTotal(preferred.mapping());
        if (perItemTotal <= 0L || perItemTotal > capacity) {
            return null;
        }

        long room = capacity - currentTotal;
        return perItemTotal > room
                ? new RoundRobinCapacityBlock(preferred.slot())
                : null;
    }

    /**
     * For multi-output mapped items, Lowest Stored considers the least-stocked
     * Essence they produce. This makes any depleted output sufficient to pull
     * that lane forward. Ties retain rotating slot order.
     */
    private long lowestStoredPriority(ResolvedDissolution mapping) {
        long lowest = Long.MAX_VALUE;
        boolean found = false;
        for (EssenceDefinition essence : mapping.outputs().keySet()) {
            if (!isDissolutionEssenceSupported(essence)) {
                continue;
            }
            lowest = Math.min(lowest, storedEssence(essence));
            found = true;
        }
        return found ? lowest : Long.MAX_VALUE;
    }

    /**
     * Highest Stored mirrors Lowest Stored using the most-stocked output type.
     * This is useful when deliberately concentrating production into an Essence
     * that the player's automation is already consuming heavily.
     */
    private long highestStoredPriority(ResolvedDissolution mapping) {
        long highest = Long.MIN_VALUE;
        boolean found = false;
        for (EssenceDefinition essence : mapping.outputs().keySet()) {
            if (!isDissolutionEssenceSupported(essence)) {
                continue;
            }
            highest = Math.max(highest, storedEssence(essence));
            found = true;
        }
        return found ? highest : Long.MIN_VALUE;
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

    private void tickChanneling() {
        if (!(level instanceof ServerLevel serverLevel)
                || channelingPlayerId == null) {
            return;
        }

        ServerPlayer player =
                serverLevel.getServer()
                        .getPlayerList()
                        .getPlayer(channelingPlayerId);

        if (player == null
                || !player.isAlive()
                || player.serverLevel() != serverLevel
                || level.getBlockEntity(worldPosition) != this
                || !canPlayerUse(player)
                || !inTransferRange(player)) {
            stopChanneling();
            return;
        }

        if (totalStoredEssence() <= 0L) {
            stopChanneling();
            return;
        }

        EssenceCrucibleStructureStats stats = structureStats();
        long budget = nextTransferBudget(stats.transferRatePerSecond());
        if (budget > 0L) {
            long moved = transferFairly(player, budget);
            if (moved > 0L) {
                setChanged();
            }
        }

        if (totalStoredEssence() <= 0L) {
            stopChanneling();
        }
    }

    private long nextTransferBudget(long perSecond) {
        long budget = perSecond / 20L;
        transferRateRemainder += perSecond % 20L;
        if (transferRateRemainder >= 20L) {
            budget += transferRateRemainder / 20L;
            transferRateRemainder %= 20L;
        }
        return budget;
    }

    private long transferFairly(
            ServerPlayer player,
            long budget
    ) {
        EssenceSavedData saved =
                EssenceSavedData.get(player.server);
        PlayerEssenceData playerData =
                saved.getPlayerData(player.getUUID());

        var essences = EssenceCrucibleEssences.ORDERED;
        int essenceCount = essences.size();
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
                EssenceDefinition essence = essences.get(index);
                long stored = playerData.getCrucibleStored(essence);
                long playerCurrent = playerData.getAvailable(essence);
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
                EssenceDefinition essence = essences.get(index);
                long stored = playerData.getCrucibleStored(essence);
                if (stored <= 0L) {
                    continue;
                }

                long playerCurrent = playerData.getAvailable(essence);
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

                long moved = saved.transferCrucibleToAvailable(
                        player.getUUID(),
                        essence,
                        requested
                );
                if (moved <= 0L) {
                    continue;
                }

                remaining -= moved;
                movedTotal += moved;
                movedThisPass = true;
            }

            if (!movedThisPass) {
                break;
            }
        }

        transferCursor = (transferCursor + 1) % essenceCount;
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
        tag.putString(ACCESS_MODE_TAG, accessMode.serializedName());
        tag.putInt(PROCESSING_TICKS_TAG, processingTicks);
        tag.putString(DISSOLUTION_MODE_TAG, dissolutionMode.serializedName());

    }

    @Override
    protected void loadAdditional(
            CompoundTag tag,
            HolderLookup.Provider registries
    ) {
        if (tag.contains(MachineVisualState.TAG, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            clientVisualState = MachineVisualState.Crucible.read(tag.getCompound(MachineVisualState.TAG));
            return;
        }
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);

        ownerId = tag.hasUUID(OWNER_TAG)
                ? tag.getUUID(OWNER_TAG)
                : null;
        ownerName = tag.getString(OWNER_NAME_TAG);
        accessMode = EssenceCrucibleAccessMode.fromSerializedName(
                tag.getString(ACCESS_MODE_TAG)
        );
        processingTicks = Math.max(0, tag.getInt(PROCESSING_TICKS_TAG));
        dissolutionMode = EssenceCrucibleDissolutionMode.fromSerializedName(
                tag.getString(DISSOLUTION_MODE_TAG)
        );
        dissolutionSlotCursor = 0;
        resetSmartRoundRobinWait();

        /* Active channel state deliberately does not survive save/reload. */
        channelingPlayerId = null;
        transferRateRemainder = 0L;
        transferCursor = 0;
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
        return (level == null
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
