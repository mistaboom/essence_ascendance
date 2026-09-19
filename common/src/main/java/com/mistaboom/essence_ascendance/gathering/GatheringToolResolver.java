package com.mistaboom.essence_ascendance.gathering;

import com.mistaboom.essence_ascendance.client.ClientCommittedSkills;
import com.mistaboom.essence_ascendance.client.ClientEssenceState;
import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.equipment.AscendanceToolMiningService;
import com.mistaboom.essence_ascendance.equipment.EquipmentActivationType;
import com.mistaboom.essence_ascendance.equipment.EquipmentAttributeService;
import com.mistaboom.essence_ascendance.equipment.EquipmentBaselineProperty;
import com.mistaboom.essence_ascendance.equipment.EquipmentBaselineService;
import com.mistaboom.essence_ascendance.equipment.EquipmentGatheringService;
import com.mistaboom.essence_ascendance.equipment.EquipmentProfileItem;
import com.mistaboom.essence_ascendance.equipment.EquipmentProfileRegistry;
import com.mistaboom.essence_ascendance.equipment.EquipmentStatResolver;
import com.mistaboom.essence_ascendance.equipment.EquipmentValueService;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.balance.SkillRankEffectScaling;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime;
import com.mistaboom.essence_ascendance.stat.EssenceStats;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;

/**
 * Shared Tool Instinct resolver. Selection, client break-speed prediction and
 * server harvest execution all ask the same deterministic question instead of
 * maintaining separate per-tool special cases.
 */
public final class GatheringToolResolver {
    private GatheringToolResolver() { }

    public static Candidate resolve(Player player, BlockState state) {
        if (player == null || state == null || !toolInstinctEffective(player)) return null;
        Inventory inventory = player.getInventory();
        Candidate best = null;
        int limit = Math.min(Inventory.INVENTORY_SIZE, inventory.getContainerSize());
        for (int slot = 0; slot < limit; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!isAscendanceMiningTool(stack)) continue;
            float speed = stack.getDestroySpeed(state);
            boolean correct = stack.isCorrectToolForDrops(state);
            if (!correct && speed <= 1.0F) continue;
            Candidate candidate = new Candidate(slot, stack, correct, speed,
                    EquipmentBaselineService.physicalHarvestLevel(stack));
            if (better(candidate, best)) best = candidate;
        }
        return best;
    }

    public static boolean isAscendanceMiningTool(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof EquipmentProfileItem profile)) return false;
        return EquipmentProfileRegistry.get(profile.equipmentProfileId())
                .map(definition -> definition.baselineMultiplier(EquipmentBaselineProperty.MINING_SPEED) > 0)
                .orElse(false);
    }

    public static boolean canHarvest(Player player, BlockState state) {
        Candidate candidate = resolve(player, state);
        return candidate != null && candidate.correctForDrops();
    }

    /**
     * Runs Player#getDestroySpeed with the resolved stack physically occupying
     * the selected slot for the duration of the native calculation. This lets
     * vanilla and other mixins see that stack's tool component and enchantments
     * without permanently changing the player's selected hotbar slot.
     */
    public static PreviewScope beginPreview(Player player, BlockState state) {
        if (player == null) return PreviewScope.empty();
        Inventory inventory = player.getInventory();
        int selected = inventory.selected;
        Candidate candidate = resolve(player, state);
        if (candidate == null || candidate.slot() == selected) {
            return new PreviewScope(inventory, selected, selected, candidate, false);
        }
        ItemStack selectedStack = inventory.getItem(selected);
        ItemStack candidateStack = inventory.getItem(candidate.slot());
        inventory.setItem(selected, candidateStack);
        inventory.setItem(candidate.slot(), selectedStack);
        return new PreviewScope(inventory, selected, candidate.slot(), candidate, true);
    }

    /**
     * Native Player#getDestroySpeed has already used the resolved stack while
     * the preview scope is open. Replace only Essence Ascendance's held Mining
     * Speed modifier, preserve every unrelated attribute modifier, then apply
     * Tool Instinct's generated lower-tier material advantage.
     */
    public static float adjustResolvedDestroySpeed(Player player, BlockState state, float nativeSpeed, Candidate candidate) {
        if (candidate == null || !Float.isFinite(nativeSpeed) || nativeSpeed <= 0) return nativeSpeed;
        double attributeRatio = miningSpeedAttributeRatio(player, candidate.stack());
        double advantage = lowerTierAdvantage(candidate, state);
        double bonus = toolInstinctMaximumBonus(player) * advantage;
        double result = nativeSpeed * attributeRatio * (1.0 + Math.max(0, bonus));
        return (float) Math.min(Float.MAX_VALUE, Math.max(0, result));
    }

    /**
     * Temporarily presents the selected Ascendance tool as the main-hand stack
     * to vanilla destroyBlock. Native drops, enchantments, durability and mod
     * hooks therefore observe the real resolved tool. The existing gathering
     * sync is refreshed inside the scope so virtual Fortune/Looting state is
     * present even when the resolved tool came from a non-selected inventory slot.
     */
    public static HarvestScope beginHarvest(ServerPlayer player, BlockState state) {
        Inventory inventory = player.getInventory();
        int selected = inventory.selected;
        Candidate candidate = resolve(player, state);
        if (candidate == null) return new HarvestScope(player, inventory, selected, selected, false, false);
        boolean swapped = candidate.slot() != selected;
        if (swapped) {
            ItemStack selectedStack = inventory.getItem(selected);
            ItemStack candidateStack = inventory.getItem(candidate.slot());
            inventory.setItem(selected, candidateStack);
            inventory.setItem(candidate.slot(), selectedStack);
        }
        EquipmentGatheringService.sync(player);
        return new HarvestScope(player, inventory, selected, candidate.slot(), swapped, true);
    }

    private static boolean better(Candidate candidate, Candidate current) {
        if (current == null) return true;
        if (candidate.correctForDrops() != current.correctForDrops()) return candidate.correctForDrops();
        int speed = Float.compare(candidate.destroySpeed(), current.destroySpeed());
        if (speed != 0) return speed > 0;
        if (candidate.harvestLevel() != current.harvestLevel()) return candidate.harvestLevel() > current.harvestLevel();
        return candidate.slot() < current.slot();
    }

    private static double lowerTierAdvantage(Candidate candidate, BlockState state) {
        int toolLevel = Math.max(0, candidate.harvestLevel());
        int required = Math.max(0, AscendanceToolMiningService.requiredHarvestLevel(state));
        if (toolLevel <= required) return 0;
        return (toolLevel - required) / (double) Math.max(1, toolLevel);
    }

    /** Exact attribute replacement for our ADD_MULTIPLIED_BASE mining modifier while retaining every foreign modifier. */
    private static double miningSpeedAttributeRatio(Player player, ItemStack candidate) {
        var instance = player.getAttribute(Attributes.BLOCK_BREAK_SPEED);
        if (instance == null) return 1.0;
        double current = instance.getValue();
        if (!(current > 0) || !Double.isFinite(current)) return 1.0;

        double additive = instance.getBaseValue();
        double baseMultiplier = 1.0;
        double totalMultiplier = 1.0;
        for (AttributeModifier modifier : instance.getModifiers()) {
            if (modifier.id().equals(EquipmentAttributeService.MINING_SPEED_MODIFIER_ID)) continue;
            switch (modifier.operation()) {
                case ADD_VALUE -> additive += modifier.amount();
                case ADD_MULTIPLIED_BASE -> baseMultiplier += modifier.amount();
                case ADD_MULTIPLIED_TOTAL -> totalMultiplier *= 1.0 + modifier.amount();
            }
        }
        baseMultiplier += candidateMiningSpeedFraction(player, candidate);
        double desired = additive * baseMultiplier * totalMultiplier;
        if (!(desired >= 0) || !Double.isFinite(desired)) return 1.0;
        desired = Attributes.BLOCK_BREAK_SPEED.value().sanitizeValue(desired);
        return desired / current;
    }

    private static double candidateMiningSpeedFraction(Player player, ItemStack candidate) {
        double applicability = EquipmentStatResolver.evaluateItem(candidate, EquipmentActivationType.HELD)
                .strength(EssenceStats.MINING_SPEED);
        if (!(applicability > 0)) return 0;
        if (player instanceof ServerPlayer serverPlayer) {
            var data = EssenceSavedData.get(serverPlayer.server).getPlayerData(serverPlayer.getUUID());
            return EquipmentValueService.scaledBonus(data, EssenceStats.MINING_SPEED, applicability) / 100.0;
        }
        return ClientEssenceState.stat(EssenceStats.MINING_SPEED)
                .map(snapshot -> snapshot.scaledBonus() * applicability / 100.0)
                .orElse(0.0);
    }

    private static boolean toolInstinctEffective(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            return SkillEffectRuntime.context(serverPlayer).isEffective(SkillIds.TOOL_INSTINCT);
        }
        return ClientCommittedSkills.isEffective(SkillIds.TOOL_INSTINCT);
    }

    private static double toolInstinctMaximumBonus(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            return SkillEffectRuntime.context(serverPlayer).settings().gathering().toolInstinct().maximumLowerTierSpeedBonus();
        }
        var base = EssenceConfigManager.skillEffects();
        int rank = ClientCommittedSkills.effectiveRank(SkillIds.TOOL_INSTINCT);
        return rank <= 1 ? base.gathering().toolInstinct().maximumLowerTierSpeedBonus()
                : SkillRankEffectScaling.apply(base, Map.of(SkillIds.TOOL_INSTINCT, rank))
                .gathering().toolInstinct().maximumLowerTierSpeedBonus();
    }

    public record Candidate(int slot, ItemStack stack, boolean correctForDrops, float destroySpeed, int harvestLevel) { }

    public static final class PreviewScope implements AutoCloseable {
        private final Inventory inventory;
        private final int selectedSlot;
        private final int sourceSlot;
        private final Candidate candidate;
        private final boolean swapped;
        private boolean closed;

        private PreviewScope(Inventory inventory, int selectedSlot, int sourceSlot, Candidate candidate, boolean swapped) {
            this.inventory = inventory;
            this.selectedSlot = selectedSlot;
            this.sourceSlot = sourceSlot;
            this.candidate = candidate;
            this.swapped = swapped;
        }

        private static PreviewScope empty() { return new PreviewScope(null, -1, -1, null, false); }
        public Candidate candidate() { return candidate; }

        @Override public void close() {
            if (closed || !swapped || inventory == null) { closed = true; return; }
            ItemStack candidateStack = inventory.getItem(selectedSlot);
            ItemStack priorSelection = inventory.getItem(sourceSlot);
            inventory.setItem(selectedSlot, priorSelection);
            inventory.setItem(sourceSlot, candidateStack);
            closed = true;
        }
    }

    public static final class HarvestScope implements AutoCloseable {
        private final ServerPlayer player;
        private final Inventory inventory;
        private final int selectedSlot;
        private final int sourceSlot;
        private final boolean swapped;
        private final boolean gatheringSynced;
        private boolean closed;

        private HarvestScope(ServerPlayer player, Inventory inventory, int selectedSlot, int sourceSlot,
                             boolean swapped, boolean gatheringSynced) {
            this.player = player;
            this.inventory = inventory;
            this.selectedSlot = selectedSlot;
            this.sourceSlot = sourceSlot;
            this.swapped = swapped;
            this.gatheringSynced = gatheringSynced;
        }

        public ItemStack activeTool() {
            return inventory == null || selectedSlot < 0 ? ItemStack.EMPTY : inventory.getItem(selectedSlot);
        }

        @Override public void close() {
            if (closed) return;
            if (swapped && inventory != null) {
                ItemStack toolAfterUse = inventory.getItem(selectedSlot);
                ItemStack priorSelection = inventory.getItem(sourceSlot);
                inventory.setItem(selectedSlot, priorSelection);
                inventory.setItem(sourceSlot, toolAfterUse);
            }
            if (gatheringSynced && player != null) EquipmentGatheringService.sync(player);
            if (swapped && inventory != null) {
                inventory.setChanged();
                if (player != null) player.inventoryMenu.broadcastChanges();
            }
            closed = true;
        }
    }
}
