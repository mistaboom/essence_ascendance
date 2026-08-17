package com.mistaboom.essence_ascendance.crucible;

import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import com.mistaboom.essence_ascendance.mapping.ItemEssenceMappingRegistry;
import com.mistaboom.essence_ascendance.mapping.ItemEssenceMappingResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.LinkedHashMap;
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
    private static final String RESERVOIR_TAG = "reservoir";

    private final NonNullList<ItemStack> items =
            NonNullList.withSize(MAX_INPUT_SLOTS, ItemStack.EMPTY);

    /*
     * One-time migration buffer for worlds saved by the first Crucible tranche,
     * where reservoir Essence lived inside each block entity. New storage is
     * player-owned in PlayerEssenceData.
     */
    private final long[] legacyStoredEssence =
            new long[EssenceCrucibleEssences.ORDERED.size()];

    private UUID ownerId;
    private String ownerName = "";
    private EssenceCrucibleAccessMode accessMode =
            EssenceCrucibleAccessMode.PRIVATE;

    private int processingTicks = 0;
    private int dissolutionSlotCursor = 0;

    /* Runtime-only channel state. */
    private UUID channelingPlayerId;
    private long transferRateRemainder = 0L;
    private int transferCursor = 0;

    /* Short-lived structure cache; pylon changes explicitly invalidate it. */
    private EssenceCrucibleStructureSnapshot cachedStructureSnapshot;
    private long cachedStructureGameTime = Long.MIN_VALUE;
    private boolean processingVisualActive = false;

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
        crucible.migrateLegacyReservoirIfNeeded();
        crucible.tickDissolution();
        crucible.tickChanneling();
        crucible.tickPylonSupportParticles(level);
    }

    public EssenceCrucibleStructureSnapshot structureSnapshot() {
        if (level == null) {
            return EssenceCrucibleStructureService.BASE_SNAPSHOT;
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
        if (index < 0 || index >= EssenceCrucibleEssences.ATTRIBUTE_ORDERED.size()) {
            return 0L;
        }

        return storedEssence(
                EssenceCrucibleEssences.ATTRIBUTE_ORDERED.get(index)
        );
    }

    public long storedEssence(EssenceDefinition essence) {
        PlayerEssenceData ownerData = ownerPlayerData();
        long shared = ownerData == null
                ? 0L
                : ownerData.getCrucibleStored(essence);

        int legacyIndex = EssenceCrucibleEssences.indexOf(essence);
        if (legacyIndex < 0) {
            return shared;
        }

        /* Include not-yet-migrated legacy Attribute data so debug/UI never hides it. */
        return Math.addExact(shared, legacyStoredEssence[legacyIndex]);
    }

    public long[] storedEssenceSnapshot() {
        long[] snapshot =
                new long[EssenceCrucibleEssences.ATTRIBUTE_ORDERED.size()];
        for (int i = 0; i < snapshot.length; i++) {
            snapshot[i] = storedEssence(i);
        }
        return snapshot;
    }

    public long[] storedSkillEssenceSnapshot() {
        long[] snapshot =
                new long[EssenceCrucibleEssences.SKILL_ORDERED.size()];
        for (int i = 0; i < snapshot.length; i++) {
            snapshot[i] = storedEssence(
                    EssenceCrucibleEssences.SKILL_ORDERED.get(i)
            );
        }
        return snapshot;
    }

    /**
     * Total of only the Essence families currently enabled for gameplay/UI.
     * Disabled Skill Essence remains persisted but deliberately does not occupy
     * visible capacity until that family is enabled again.
     */
    public long totalStoredEssence() {
        boolean skillEssencesEnabled =
                EssenceConfigManager.get().skillEssencesEnabled();

        long total = 0L;
        for (EssenceDefinition essence :
                EssenceCrucibleEssences.enabledOrdered(skillEssencesEnabled)) {
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

    private boolean hasLegacyReservoir() {
        for (long amount : legacyStoredEssence) {
            if (amount > 0L) {
                return true;
            }
        }
        return false;
    }

    private void migrateLegacyReservoirIfNeeded() {
        if (!(level instanceof ServerLevel serverLevel)
                || ownerId == null
                || !hasLegacyReservoir()) {
            return;
        }

        EssenceSavedData saved =
                EssenceSavedData.get(serverLevel.getServer());

        /*
         * Preserve every previously stored point, even if several old
         * Crucibles temporarily put this owner above the new shared cap.
         * Over-cap state is allowed to drain, but new dissolution pauses.
         */
        for (int i = 0; i < legacyStoredEssence.length; i++) {
            long amount = legacyStoredEssence[i];
            if (amount <= 0L) {
                continue;
            }

            saved.addCrucibleStored(
                    ownerId,
                    EssenceCrucibleEssences.ORDERED.get(i),
                    amount
            );
            legacyStoredEssence[i] = 0L;
        }

        setChanged();
    }

    private void tickDissolution() {
        if (!(level instanceof ServerLevel serverLevel) || ownerId == null) {
            return;
        }

        EssenceCrucibleStructureStats stats = structureStats();
        int activeSlots = activeInputSlotCount(stats);
        if (!hasAnyActiveInput(activeSlots)) {
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
        processingVisualActive = potential.totalItems() > 0;

        if (processingTicks < requiredTicks) {
            processingTicks++;
            setChanged();
        }

        if (processingTicks < requiredTicks) {
            return;
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

        processingTicks = 0;
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

        /*
         * Build the occupied/mapped lane list in a rotating order. Each batch
         * divides its item budget as evenly as possible across these lanes.
         * The rotation only decides who receives remainder items, so one slot
         * does not permanently get the extra item when maxItems is not evenly
         * divisible by the occupied-lane count.
         */
        int start = Math.floorMod(dissolutionSlotCursor, activeSlots);
        int[] eligibleSlots = new int[activeSlots];
        ItemEssenceMappingResult[] mappings = new ItemEssenceMappingResult[activeSlots];
        int eligibleCount = 0;

        for (int offset = 0; offset < activeSlots; offset++) {
            int slot = (start + offset) % activeSlots;
            ItemStack stack = items.get(slot);
            if (stack.isEmpty()) {
                continue;
            }

            ItemEssenceMappingResult mapping = resolvePositiveAttributeMapping(stack);
            if (mapping == null) {
                continue;
            }

            eligibleSlots[eligibleCount] = slot;
            mappings[eligibleCount] = mapping;
            eligibleCount++;
        }

        if (eligibleCount == 0) {
            return new DissolutionPlan(consumeCounts, outputs, 0);
        }

        long room = capacity - currentTotal;
        int baseShare = maxItems / eligibleCount;
        int remainder = maxItems % eligibleCount;
        int totalItems = 0;

        /* First pass: give every occupied lane its even share. */
        for (int i = 0; i < eligibleCount; i++) {
            int requested = baseShare + (i < remainder ? 1 : 0);
            if (requested <= 0) {
                continue;
            }

            long perItemTotal = mappingOutputTotal(mappings[i]);
            if (perItemTotal <= 0L) {
                continue;
            }

            int added = addPlannedItemsFromSlot(
                    eligibleSlots[i],
                    mappings[i],
                    requested,
                    room,
                    consumeCounts,
                    outputs
            );
            if (added < 0) {
                return new DissolutionPlan(new int[MAX_INPUT_SLOTS], Map.of(), 0);
            }

            totalItems += added;
            room -= perItemTotal * added;
        }

        /*
         * If a lane was short on items (or its requested share would not fit),
         * redistribute unused batch capacity one item at a time across lanes
         * that can still contribute. This preserves throughput while remaining
         * as even as the actual stacks/capacity allow.
         */
        while (totalItems < maxItems) {
            boolean addedThisPass = false;

            for (int i = 0; i < eligibleCount && totalItems < maxItems; i++) {
                int slot = eligibleSlots[i];
                ItemStack stack = items.get(slot);
                if (consumeCounts[slot] >= stack.getCount()) {
                    continue;
                }

                long perItemTotal = mappingOutputTotal(mappings[i]);
                if (perItemTotal <= 0L || perItemTotal > room) {
                    continue;
                }

                int added = addPlannedItemsFromSlot(
                        slot,
                        mappings[i],
                        1,
                        room,
                        consumeCounts,
                        outputs
                );
                if (added < 0) {
                    return new DissolutionPlan(new int[MAX_INPUT_SLOTS], Map.of(), 0);
                }
                if (added == 0) {
                    continue;
                }

                totalItems += added;
                room -= perItemTotal;
                addedThisPass = true;
            }

            if (!addedThisPass) {
                break;
            }
        }

        return new DissolutionPlan(consumeCounts, outputs, totalItems);
    }

    /**
     * Adds up to {@code requested} copies of one mapped stack to an in-memory
     * atomic dissolution plan. Returns -1 on arithmetic overflow.
     */
    private int addPlannedItemsFromSlot(
            int slot,
            ItemEssenceMappingResult mapping,
            int requested,
            long room,
            int[] consumeCounts,
            Map<EssenceDefinition, Long> outputs
    ) {
        ItemStack stack = items.get(slot);
        int available = Math.max(0, stack.getCount() - consumeCounts[slot]);
        long perItemTotal = mappingOutputTotal(mapping);
        if (available <= 0 || requested <= 0 || perItemTotal <= 0L) {
            return 0;
        }

        int byRoom = (int) Math.min(Integer.MAX_VALUE, room / perItemTotal);
        int count = Math.min(requested, Math.min(available, byRoom));
        if (count <= 0) {
            return 0;
        }

        try {
            for (Map.Entry<EssenceDefinition, Long> output : mapping.outputs().entrySet()) {
                long amount = Math.multiplyExact(output.getValue(), (long) count);
                outputs.merge(output.getKey(), amount, Math::addExact);
            }
            consumeCounts[slot] = Math.addExact(consumeCounts[slot], count);
        } catch (ArithmeticException overflow) {
            return -1;
        }

        return count;
    }

    private long mappingOutputTotal(ItemEssenceMappingResult mapping) {
        long total = 0L;
        try {
            for (Map.Entry<EssenceDefinition, Long> output : mapping.outputs().entrySet()) {
                if (output.getValue() <= 0L
                        || EssenceCrucibleEssences.indexOf(output.getKey()) < 0) {
                    return 0L;
                }
                total = Math.addExact(total, output.getValue());
            }
        } catch (ArithmeticException overflow) {
            return 0L;
        }
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
            if (!stack.isEmpty() && resolvePositiveAttributeMapping(stack) != null) {
                return true;
            }
        }
        return false;
    }

    private record DissolutionPlan(
            int[] consumeCounts,
            Map<EssenceDefinition, Long> outputs,
            int totalItems
    ) {
    }

    public static ItemEssenceMappingResult resolvePositiveAttributeMapping(
            ItemStack stack
    ) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }

        ItemEssenceMappingResult result =
                ItemEssenceMappingRegistry.resolve(stack);

        if (!result.mapped() || result.outputs().isEmpty()) {
            return null;
        }

        for (Map.Entry<EssenceDefinition, Long> output : result.outputs().entrySet()) {
            if (output.getValue() <= 0L
                    || EssenceCrucibleEssences.indexOf(output.getKey()) < 0) {
                return null;
            }
        }

        return result;
    }

    public static boolean isValidNewInput(ItemStack stack) {
        return resolvePositiveAttributeMapping(stack) != null;
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

        if (player.tickCount % 4 == 0) {
            spawnChannelParticles(serverLevel, player, stats);
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

        boolean skillEssencesEnabled =
                EssenceConfigManager.get().skillEssencesEnabled();
        var enabledEssences =
                EssenceCrucibleEssences.enabledOrdered(skillEssencesEnabled);
        int essenceCount = enabledEssences.size();
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
                EssenceDefinition essence = enabledEssences.get(index);
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
                EssenceDefinition essence = enabledEssences.get(index);
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

    private void tickPylonSupportParticles(ServerLevel level) {
        if ((!processingVisualActive && !isChanneling())
                || level.getGameTime() % 5L != 0L) {
            return;
        }

        Vec3 target = new Vec3(
                worldPosition.getX() + 0.5D,
                worldPosition.getY() + 0.70D,
                worldPosition.getZ() + 0.5D
        );

        for (EssenceCrucibleStructureSnapshot.ActivePylon pylon :
                structureSnapshot().activePylons()) {
            Vec3 start = new Vec3(
                    pylon.pos().getX() + 0.5D,
                    pylon.pos().getY() + 1.15D,
                    pylon.pos().getZ() + 0.5D
            );
            Vec3 delta = target.subtract(start);

            for (int point = 1; point <= 3; point++) {
                double t = point / 4.0D;
                Vec3 at = start.add(delta.scale(t));
                level.sendParticles(
                        ParticleTypes.END_ROD,
                        at.x,
                        at.y,
                        at.z,
                        1,
                        0.005D,
                        0.005D,
                        0.005D,
                        0.0D
                );
            }
        }
    }

    private void spawnChannelParticles(
            ServerLevel level,
            ServerPlayer player,
            EssenceCrucibleStructureStats stats
    ) {
        Vec3 target = player.position().add(0.0D, player.getBbHeight() * 0.55D, 0.0D);

        for (EssenceCrucibleStructureService.TransferEndpoint endpoint :
                EssenceCrucibleStructureService.transferEndpoints(level, worldPosition, stats)) {

            Vec3 start = new Vec3(endpoint.x(), endpoint.y(), endpoint.z());
            Vec3 delta = target.subtract(start);
            int segments = Math.max(
                    4,
                    Math.min(12, (int) Math.ceil(delta.length() / 1.5D))
            );

            /* Include t=1.0 so the final particle remains attached to the player. */
            for (int point = 1; point <= segments; point++) {
                double t = point / (double) segments;
                Vec3 at = start.add(delta.scale(t));
                level.sendParticles(
                        ParticleTypes.END_ROD,
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

        /*
         * New saves do not write normal reservoir data to the block. Keep only
         * an unbound/unmigrated legacy buffer if one still exists.
         */
        if (hasLegacyReservoir()) {
            CompoundTag reservoir = new CompoundTag();
            for (int i = 0; i < legacyStoredEssence.length; i++) {
                reservoir.putLong(
                        EssenceCrucibleEssences.ORDERED.get(i).id().getPath(),
                        legacyStoredEssence[i]
                );
            }
            tag.put(RESERVOIR_TAG, reservoir);
        }
    }

    @Override
    protected void loadAdditional(
            CompoundTag tag,
            HolderLookup.Provider registries
    ) {
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
        dissolutionSlotCursor = 0;

        Arrays.fill(legacyStoredEssence, 0L);
        if (tag.contains(RESERVOIR_TAG, Tag.TAG_COMPOUND)) {
            CompoundTag reservoir = tag.getCompound(RESERVOIR_TAG);
            for (int i = 0; i < legacyStoredEssence.length; i++) {
                legacyStoredEssence[i] = Math.max(
                        0L,
                        reservoir.getLong(
                                EssenceCrucibleEssences.ORDERED.get(i).id().getPath()
                        )
                );
            }
        }

        /* Active channel state deliberately does not survive save/reload. */
        channelingPlayerId = null;
        transferRateRemainder = 0L;
        transferCursor = 0;
        cachedStructureSnapshot = null;
        cachedStructureGameTime = Long.MIN_VALUE;
        processingVisualActive = false;
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
        }
        setChanged();
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
        processingTicks = 0;
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
