package com.mistaboom.essence_ascendance.equipment;

import com.mistaboom.essence_ascendance.config.UtilityBalanceSettings;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;

/**
 * Reusable Masterwork Tempering transaction.
 *
 * <p>Tempering is deliberately separate from repair: the target must already be at full native durability.
 * One compatible construction/repair-material item contributes a generated Overdurability budget based on its
 * material-family units. Normal repair remains owned by the item's native repair path (or the Infuser for
 * Ascendance artifacts).</p>
 */
public final class EquipmentReinforcementService {
    private static final double EPSILON = 1.0E-9;

    private EquipmentReinforcementService() { }

    public static Result reinforceFullyRepaired(ItemStack input, ItemStack proposedOutput, ItemStack material,
                                                UtilityBalanceSettings.MasterworkTempering tuning,
                                                MinecraftServer server) {
        if (!EquipmentMaintenanceService.eligible(input)
                || input.getDamageValue() > 0
                || material == null || material.isEmpty()
                || tuning == null) return Result.NONE;

        RepairMaterialResolver.Match match = RepairMaterialResolver.resolve(input, material, server);
        if (!match.valid() || match.materialUnits() <= 0) return Result.NONE;

        double capacity = input.getMaxDamage() * tuning.maximumOverdurabilityFraction();
        if (!Double.isFinite(capacity) || capacity <= EPSILON) return Result.NONE;

        double currentOverdurability = Math.min(capacity, EquipmentMaintenanceData.overdurability(input));
        double reinforcementBudget = input.getMaxDamage() * tuning.reinforcementFractionPerMaterialUnit()
                * match.materialUnits();
        if (!Double.isFinite(reinforcementBudget) || reinforcementBudget <= EPSILON) return Result.NONE;

        double addedOverdurability = Math.min(Math.max(0, capacity - currentOverdurability), reinforcementBudget);
        if (addedOverdurability <= EPSILON) return Result.NONE;

        // Prefer vanilla's proposed output when it is the same item so ordinary metadata changes (for example a
        // rename) are preserved. A pure tempering operation normally has no vanilla output, so input.copy() is the
        // standard path.
        ItemStack output = proposedOutput != null && !proposedOutput.isEmpty()
                && proposedOutput.getItem() == input.getItem() ? proposedOutput.copy() : input.copy();
        output.setDamageValue(0);
        EquipmentMaintenanceData.setOverdurability(
                output,
                currentOverdurability + addedOverdurability,
                capacity
        );

        return new Result(output, 1, addedOverdurability, capacity, match.materialUnits(), match.family());
    }

    public record Result(ItemStack output, int materialItems, double addedOverdurability,
                         double capacity, double materialUnits, String materialFamily) {
        private static final Result NONE = new Result(ItemStack.EMPTY, 0, 0, 0, 0, "");

        public boolean applied() {
            return !output.isEmpty() && materialItems > 0 && addedOverdurability > EPSILON;
        }
    }
}
