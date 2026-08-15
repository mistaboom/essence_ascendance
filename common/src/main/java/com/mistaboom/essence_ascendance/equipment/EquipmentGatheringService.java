package com.mistaboom.essence_ascendance.equipment;

import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import com.mistaboom.essence_ascendance.stat.EssenceStats;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/*
 * Authoritative gameplay layer for the gathering/utility tranche:
 *
 *   fortune
 *   looting
 *   experience_gain
 *   durability_efficiency
 *
 * Fortune and Looting are deliberately VIRTUAL enchantment levels. We never
 * write an enchantment component to the ItemStack, so Essence does not create
 * an enchantment tooltip or glint. Loader hooks ask this service for a gameplay
 * level and add it on top of the real enchantment level.
 *
 * LEVEL-valued Essence progression is continuous, while vanilla enchantment
 * mechanics require an integer. The virtual level therefore rounds to the
 * nearest integer (0.5 rounds upward). With the built-in max of III this gives
 * useful progression before Transcendent without inventing fractional vanilla
 * enchantment semantics.
 *
 * Experience and durability use fractional carry so small one-point events do
 * not lose the player's earned percentage to integer rounding.
 */
public final class EquipmentGatheringService {

    private static final int MAX_VIRTUAL_ENCHANTMENT_LEVEL = 255;

    /*
     * Hidden CUSTOM_DATA keys. These are not enchantment components, so they
     * do not create glint or tooltip entries. They exist so Minecraft copies
     * of the held tool (notably block-loot context stacks) carry the same
     * virtual Fortune/Looting levels as the authoritative held stack.
     */
    private static final String VIRTUAL_FORTUNE_LEVEL_TAG =
            "essence_ascendance_virtual_fortune_level";
    private static final String VIRTUAL_LOOTING_LEVEL_TAG =
            "essence_ascendance_virtual_looting_level";
    private static final String VIRTUAL_LOOT_OWNER_TAG =
            "essence_ascendance_virtual_loot_owner";

    /*
     * Identity matters here: these are the actual server-side held stacks.
     * WeakHashMap prevents a discarded/replaced stack from being retained by
     * the service forever.
     */
    private static final Map<ItemStack, VirtualLootState> VIRTUAL_LOOT_BY_STACK =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static final Map<UUID, HeldStacks> HELD_STACKS =
            new ConcurrentHashMap<>();

    private static final Map<ItemStack, Double> DURABILITY_CARRY =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static final Map<UUID, Double> EXPERIENCE_BONUS_CARRY =
            new ConcurrentHashMap<>();

    private static final Map<UUID, LastExperienceGain> LAST_EXPERIENCE =
            new ConcurrentHashMap<>();

    private static final Map<UUID, LastDurabilityEvent> LAST_DURABILITY =
            new ConcurrentHashMap<>();

    private static final Map<UUID, LastEnchantmentQuery> LAST_ENCHANTMENT_QUERY =
            new ConcurrentHashMap<>();

    private EquipmentGatheringService() {
    }

    /*
     * Called from the existing common server player tick.
     *
     * This only caches virtual enchantment levels for currently held stacks.
     * Nothing is written into the ItemStack's real enchantment component.
     */
    public static void sync(ServerPlayer player) {
        UUID playerId = player.getUUID();
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();

        /*
         * Do not remove/rewrite CUSTOM_DATA every tick. Only clear a stack
         * when it actually leaves both hands, then refresh the currently held
         * stacks if their resolved virtual levels changed.
         */
        HeldStacks previous = HELD_STACKS.get(playerId);
        if (previous != null) {
            clearIfNoLongerHeld(previous.mainHand(), mainHand, offHand);
            clearIfNoLongerHeld(previous.offHand(), mainHand, offHand);
        }

        cacheHeldStack(player, mainHand);
        if (offHand != mainHand) {
            cacheHeldStack(player, offHand);
        }

        HELD_STACKS.put(
                playerId,
                new HeldStacks(mainHand, offHand)
        );
    }

    private static void clearIfNoLongerHeld(
            ItemStack previous,
            ItemStack mainHand,
            ItemStack offHand
    ) {
        if (previous == null || previous.isEmpty()) {
            return;
        }

        if (previous != mainHand && previous != offHand) {
            removeVirtualState(previous);
        }
    }

    private static void cacheHeldStack(
            ServerPlayer player,
            ItemStack stack
    ) {
        if (stack.isEmpty()) {
            return;
        }

        VirtualLootState state = calculateVirtualLootState(player, stack);
        if (state.fortuneLevel() <= 0 && state.lootingLevel() <= 0) {
            removeVirtualState(stack);
            return;
        }

        VIRTUAL_LOOT_BY_STACK.put(stack, state);
        writeSyncedVirtualLootState(stack, state);
    }

    private static void writeSyncedVirtualLootState(
            ItemStack stack,
            VirtualLootState state
    ) {
        SyncedVirtualLootState current = readSyncedVirtualLootState(stack);
        if (current.fortuneLevel() == state.fortuneLevel()
                && current.lootingLevel() == state.lootingLevel()
                && state.playerId().equals(current.playerId())) {
            return;
        }

        CustomData.update(
                DataComponents.CUSTOM_DATA,
                stack,
                tag -> {
                    tag.putInt(
                            VIRTUAL_FORTUNE_LEVEL_TAG,
                            state.fortuneLevel()
                    );
                    tag.putInt(
                            VIRTUAL_LOOTING_LEVEL_TAG,
                            state.lootingLevel()
                    );
                    tag.putUUID(
                            VIRTUAL_LOOT_OWNER_TAG,
                            state.playerId()
                    );
                }
        );
    }

    private static void removeVirtualState(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }

        VIRTUAL_LOOT_BY_STACK.remove(stack);

        SyncedVirtualLootState current = readSyncedVirtualLootState(stack);
        if (current.isEmpty()) {
            return;
        }

        CustomData.update(
                DataComponents.CUSTOM_DATA,
                stack,
                tag -> {
                    tag.remove(VIRTUAL_FORTUNE_LEVEL_TAG);
                    tag.remove(VIRTUAL_LOOTING_LEVEL_TAG);
                    tag.remove(VIRTUAL_LOOT_OWNER_TAG);
                }
        );
    }

    private static SyncedVirtualLootState readSyncedVirtualLootState(
            ItemStack stack
    ) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return SyncedVirtualLootState.EMPTY;
        }

        CompoundTag tag = customData.copyTag();
        int fortune = Math.max(0, tag.getInt(VIRTUAL_FORTUNE_LEVEL_TAG));
        int looting = Math.max(0, tag.getInt(VIRTUAL_LOOTING_LEVEL_TAG));
        UUID playerId = tag.hasUUID(VIRTUAL_LOOT_OWNER_TAG)
                ? tag.getUUID(VIRTUAL_LOOT_OWNER_TAG)
                : null;

        return new SyncedVirtualLootState(
                playerId,
                fortune,
                looting
        );
    }

    /*
     * Loader-facing hook for an item-specific enchantment gameplay query.
     */
    public static int resolveVirtualEnchantmentLevel(
            ItemStack stack,
            Holder<Enchantment> enchantment,
            int vanillaLevel
    ) {
        if (stack == null || stack.isEmpty()) {
            return vanillaLevel;
        }

        VirtualLootState direct = VIRTUAL_LOOT_BY_STACK.get(stack);
        SyncedVirtualLootState synced = readSyncedVirtualLootState(stack);

        int virtualLevel = direct != null
                ? levelFor(direct, enchantment)
                : levelFor(synced, enchantment);

        if (virtualLevel <= 0) {
            return vanillaLevel;
        }

        int resolved = addEnchantmentLevels(vanillaLevel, virtualLevel);
        UUID playerId = direct != null
                ? direct.playerId()
                : synced.playerId();

        if (playerId != null) {
            LAST_ENCHANTMENT_QUERY.put(
                    playerId,
                    new LastEnchantmentQuery(
                            enchantmentName(enchantment),
                            vanillaLevel,
                            virtualLevel,
                            resolved,
                            direct != null ? "ITEM_HELD" : "ITEM_COPY"
                    )
            );
        }

        return resolved;
    }

    /*
     * Fabric also hooks the LivingEntity-level helper. This covers gameplay
     * paths (notably mob loot) that query the attacker's effective enchantment
     * level rather than querying one ItemStack directly.
     */
    public static int resolveVirtualHeldEnchantmentLevel(
            LivingEntity entity,
            Holder<Enchantment> enchantment,
            int vanillaLevel
    ) {
        if (!(entity instanceof ServerPlayer player)) {
            return vanillaLevel;
        }

        int virtualLevel = Math.max(
                calculateLevelFor(player, player.getMainHandItem(), enchantment),
                calculateLevelFor(player, player.getOffhandItem(), enchantment)
        );

        int resolved = addEnchantmentLevels(vanillaLevel, virtualLevel);

        if (virtualLevel > 0) {
            LAST_ENCHANTMENT_QUERY.put(
                    player.getUUID(),
                    new LastEnchantmentQuery(
                            enchantmentName(enchantment),
                            vanillaLevel,
                            virtualLevel,
                            resolved,
                            "HELD_ENTITY"
                    )
            );
        }

        return resolved;
    }

    private static int calculateLevelFor(
            ServerPlayer player,
            ItemStack stack,
            Holder<Enchantment> enchantment
    ) {
        if (stack.isEmpty()) {
            return 0;
        }

        return levelFor(
                calculateVirtualLootState(player, stack),
                enchantment
        );
    }

    private static VirtualLootState calculateVirtualLootState(
            ServerPlayer player,
            ItemStack stack
    ) {
        PlayerEssenceData playerData = playerData(player);
        EquipmentStatState held = EquipmentStatResolver.evaluateItem(
                stack,
                EquipmentActivationType.HELD
        );

        double fortuneLevels = EquipmentValueService.scaledBonus(
                playerData,
                EssenceStats.FORTUNE,
                held.strength(EssenceStats.FORTUNE)
        );

        double lootingLevels = EquipmentValueService.scaledBonus(
                playerData,
                EssenceStats.LOOTING,
                held.strength(EssenceStats.LOOTING)
        );

        return new VirtualLootState(
                player.getUUID(),
                fortuneLevels,
                toVirtualLevel(fortuneLevels),
                lootingLevels,
                toVirtualLevel(lootingLevels)
        );
    }

    private static int levelFor(
            VirtualLootState state,
            Holder<Enchantment> enchantment
    ) {
        if (enchantment.is(Enchantments.FORTUNE)) {
            return state.fortuneLevel();
        }

        if (enchantment.is(Enchantments.LOOTING)) {
            return state.lootingLevel();
        }

        return 0;
    }

    private static int levelFor(
            SyncedVirtualLootState state,
            Holder<Enchantment> enchantment
    ) {
        if (enchantment.is(Enchantments.FORTUNE)) {
            return state.fortuneLevel();
        }

        if (enchantment.is(Enchantments.LOOTING)) {
            return state.lootingLevel();
        }

        return 0;
    }

    private static int addEnchantmentLevels(
            int vanillaLevel,
            int virtualLevel
    ) {
        if (virtualLevel <= 0) {
            return vanillaLevel;
        }

        long resolved = (long) Math.max(0, vanillaLevel)
                + virtualLevel;

        return resolved >= Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : (int) resolved;
    }

    private static String enchantmentName(Holder<Enchantment> enchantment) {
        if (enchantment.is(Enchantments.FORTUNE)) {
            return "fortune";
        }
        if (enchantment.is(Enchantments.LOOTING)) {
            return "looting";
        }
        return "other";
    }

    private static int toVirtualLevel(double levels) {
        if (!Double.isFinite(levels) || levels <= 0.0) {
            return 0;
        }

        long rounded = (long) Math.floor(levels + 0.5D);
        return (int) Math.max(
                0L,
                Math.min(MAX_VIRTUAL_ENCHANTMENT_LEVEL, rounded)
        );
    }

    /*
     * Positive experience gains are multiplied by the active percentage.
     * Active context is passive worn armor merged by MAX with the main hand,
     * matching the rest of the equipment architecture.
     *
     * The base XP is never delayed. Only the fractional BONUS is carried.
     */
    public static int modifyExperienceGain(
            ServerPlayer player,
            int amount
    ) {
        if (amount <= 0) {
            return amount;
        }

        double percent = experienceGainPercent(player);
        UUID playerId = player.getUUID();

        if (percent <= 0.0) {
            EXPERIENCE_BONUS_CARRY.remove(playerId);
            LAST_EXPERIENCE.put(
                    playerId,
                    new LastExperienceGain(amount, amount, 0.0, 0.0)
            );
            return amount;
        }

        double carry = EXPERIENCE_BONUS_CARRY.getOrDefault(playerId, 0.0);
        double rawBonus = amount * (percent / 100.0) + carry;
        long wholeBonus = (long) Math.floor(rawBonus + 1.0E-9D);
        double nextCarry = rawBonus - wholeBonus;

        EXPERIENCE_BONUS_CARRY.put(playerId, nextCarry);

        long resolvedLong = (long) amount + wholeBonus;
        int resolved = resolvedLong > Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : (int) resolvedLong;

        LAST_EXPERIENCE.put(
                playerId,
                new LastExperienceGain(
                        amount,
                        resolved,
                        percent,
                        nextCarry
                )
        );

        return resolved;
    }

    public static double experienceGainPercent(ServerPlayer player) {
        PlayerEssenceData playerData = playerData(player);
        EquipmentStatState active = EquipmentStatResolver.evaluateForHand(
                player,
                InteractionHand.MAIN_HAND
        );

        return EquipmentValueService.scaledBonus(
                playerData,
                EssenceStats.EXPERIENCE_GAIN,
                active.strength(EssenceStats.EXPERIENCE_GAIN)
        );
    }

    /*
     * Held tool/weapon durability hook.
     *
     * We explicitly require that this exact stack is currently in a hand so a
     * random inventory stack cannot activate HELD applicability merely because
     * some caller damages it programmatically.
     */
    public static int modifyHeldDurabilityDamage(
            ServerPlayer player,
            ItemStack stack,
            int requestedDamage
    ) {
        if (requestedDamage <= 0) {
            return requestedDamage;
        }

        if (player.getMainHandItem() != stack
                && player.getOffhandItem() != stack) {
            return requestedDamage;
        }

        EquipmentStatState state = EquipmentStatResolver.evaluateItem(
                stack,
                EquipmentActivationType.HELD
        );

        return modifyDurabilityDamage(
                player,
                stack,
                requestedDamage,
                state.strength(EssenceStats.DURABILITY_EFFICIENCY),
                "HELD"
        );
    }

    /*
     * Worn armor durability hook. Raw WORN applicability is intentional here:
     * durability loss is local to this one ItemStack and must not be multiplied
     * by its 15/40/30/15 passive armor coverage share.
     */
    public static int modifyWornDurabilityDamage(
            LivingEntity entity,
            ItemStack stack,
            EquipmentSlot slot,
            int requestedDamage
    ) {
        if (!(entity instanceof ServerPlayer player)
                || requestedDamage <= 0) {
            return requestedDamage;
        }

        if (player.getItemBySlot(slot) != stack) {
            return requestedDamage;
        }

        EquipmentStatState state = EquipmentStatResolver.evaluateWornItem(
                player,
                slot
        );

        return modifyDurabilityDamage(
                player,
                stack,
                requestedDamage,
                state.strength(EssenceStats.DURABILITY_EFFICIENCY),
                "WORN:" + slot.getName()
        );
    }

    private static int modifyDurabilityDamage(
            ServerPlayer player,
            ItemStack stack,
            int requestedDamage,
            double applicability,
            String context
    ) {
        PlayerEssenceData playerData = playerData(player);

        double percent = EquipmentValueService.scaledBonus(
                playerData,
                EssenceStats.DURABILITY_EFFICIENCY,
                applicability
        );

        if (percent <= 0.0) {
            DURABILITY_CARRY.remove(stack);
            recordDurability(
                    player,
                    stack,
                    requestedDamage,
                    requestedDamage,
                    0.0,
                    0.0,
                    context
            );
            return requestedDamage;
        }

        double multiplier = Math.max(0.0, 1.0 - percent / 100.0);
        double carry = DURABILITY_CARRY.getOrDefault(stack, 0.0);
        double rawResolved = requestedDamage * multiplier + carry;
        int resolved = (int) Math.floor(rawResolved + 1.0E-9D);
        double nextCarry = rawResolved - resolved;

        DURABILITY_CARRY.put(stack, nextCarry);

        recordDurability(
                player,
                stack,
                requestedDamage,
                resolved,
                percent,
                nextCarry,
                context
        );

        return resolved;
    }

    private static void recordDurability(
            ServerPlayer player,
            ItemStack stack,
            int requested,
            int resolved,
            double percent,
            double carry,
            String context
    ) {
        LAST_DURABILITY.put(
                player.getUUID(),
                new LastDurabilityEvent(
                        stack.getHoverName().getString(),
                        requested,
                        resolved,
                        percent,
                        carry,
                        context
                )
        );
    }

    public static GatheringState evaluate(ServerPlayer player) {
        PlayerEssenceData playerData = playerData(player);
        ItemStack mainHand = player.getMainHandItem();

        EquipmentStatState held = EquipmentStatResolver.evaluateHeldOnly(
                player,
                InteractionHand.MAIN_HAND
        );

        double fortuneLevels = EquipmentValueService.scaledBonus(
                playerData,
                EssenceStats.FORTUNE,
                held.strength(EssenceStats.FORTUNE)
        );

        double lootingLevels = EquipmentValueService.scaledBonus(
                playerData,
                EssenceStats.LOOTING,
                held.strength(EssenceStats.LOOTING)
        );

        double durabilityPercent = EquipmentValueService.scaledBonus(
                playerData,
                EssenceStats.DURABILITY_EFFICIENCY,
                held.strength(EssenceStats.DURABILITY_EFFICIENCY)
        );

        return new GatheringState(
                mainHand.isEmpty() ? "EMPTY" : mainHand.getHoverName().getString(),
                fortuneLevels,
                toVirtualLevel(fortuneLevels),
                lootingLevels,
                toVirtualLevel(lootingLevels),
                experienceGainPercent(player),
                durabilityPercent
        );
    }

    public static Optional<LastExperienceGain> lastExperienceGain(
            ServerPlayer player
    ) {
        return Optional.ofNullable(
                LAST_EXPERIENCE.get(player.getUUID())
        );
    }

    public static Optional<LastDurabilityEvent> lastDurabilityEvent(
            ServerPlayer player
    ) {
        return Optional.ofNullable(
                LAST_DURABILITY.get(player.getUUID())
        );
    }

    public static Optional<LastEnchantmentQuery> lastEnchantmentQuery(
            ServerPlayer player
    ) {
        return Optional.ofNullable(
                LAST_ENCHANTMENT_QUERY.get(player.getUUID())
        );
    }

    public static void forget(LivingEntity entity) {
        UUID playerId = entity.getUUID();
        HeldStacks held = HELD_STACKS.remove(playerId);
        if (held != null) {
            removeVirtualState(held.mainHand());
            removeVirtualState(held.offHand());
        }
        EXPERIENCE_BONUS_CARRY.remove(playerId);
        LAST_EXPERIENCE.remove(playerId);
        LAST_DURABILITY.remove(playerId);
        LAST_ENCHANTMENT_QUERY.remove(playerId);
    }

    private static PlayerEssenceData playerData(ServerPlayer player) {
        return EssenceSavedData
                .get(player.server)
                .getPlayerData(player.getUUID());
    }

    private record SyncedVirtualLootState(
            UUID playerId,
            int fortuneLevel,
            int lootingLevel
    ) {
        private static final SyncedVirtualLootState EMPTY =
                new SyncedVirtualLootState(null, 0, 0);

        private boolean isEmpty() {
            return fortuneLevel <= 0
                    && lootingLevel <= 0
                    && playerId == null;
        }
    }

    private record HeldStacks(
            ItemStack mainHand,
            ItemStack offHand
    ) {
    }

    public record VirtualLootState(
            UUID playerId,
            double fortuneEarnedLevels,
            int fortuneLevel,
            double lootingEarnedLevels,
            int lootingLevel
    ) {
    }

    public record GatheringState(
            String mainHandName,
            double fortuneEarnedLevels,
            int fortuneVirtualLevel,
            double lootingEarnedLevels,
            int lootingVirtualLevel,
            double experienceGainPercent,
            double durabilityEfficiencyPercent
    ) {
    }

    public record LastExperienceGain(
            int requested,
            int resolved,
            double bonusPercent,
            double fractionalBonusCarry
    ) {
    }

    public record LastDurabilityEvent(
            String itemName,
            int requested,
            int resolved,
            double efficiencyPercent,
            double fractionalDamageCarry,
            String context
    ) {
    }

    public record LastEnchantmentQuery(
            String enchantment,
            int vanillaLevel,
            int virtualLevel,
            int resolvedLevel,
            String queryPath
    ) {
    }
}
