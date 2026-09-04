package com.mistaboom.essence_ascendance.infuser;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.config.InfuserBalanceSettings;
import com.mistaboom.essence_ascendance.equipment.FracturedEquipmentData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.Optional;

/**
 * Full-repair recipe for one damaged Ascendance artifact.
 *
 * The player chooses any enabled Essence through the Infuser's existing source
 * selector. Cost scales only with missing durability. A Fractured artifact also
 * needs Latent Ingots to restore its physical structure.
 */
public record RepairInfusionRecipe(
        int missingDurability,
        long essenceRequired,
        int latentIngotCount,
        boolean fractured
) implements EssenceInfuserRecipe {

    public RepairInfusionRecipe {
        if (missingDurability <= 0 || essenceRequired <= 0L || latentIngotCount < 0) {
            throw new IllegalArgumentException("Invalid repair infusion recipe");
        }
        if (fractured && latentIngotCount <= 0) {
            throw new IllegalArgumentException("Fractured repair must require Latent material");
        }
    }

    public static boolean isWorkpiece(ItemStack stack) {
        return FracturedEquipmentData.isAscendanceArtifact(stack)
                && stack.getMaxDamage() > 0
                && (FracturedEquipmentData.isFractured(stack)
                    || stack.getDamageValue() > 0);
    }

    public static Optional<RepairInfusionRecipe> forWorkpiece(ItemStack stack) {
        if (!isWorkpiece(stack)) {
            return Optional.empty();
        }

        InfuserBalanceSettings.RepairSettings settings =
                EssenceConfigManager.get().infuserBalance().repair();
        boolean fractured = FracturedEquipmentData.isFractured(stack);
        int maxDamage = Math.max(1, stack.getMaxDamage());
        int missing = fractured
                ? maxDamage
                : Math.max(1, Math.min(maxDamage, stack.getDamageValue()));

        long essence;
        try {
            essence = Math.multiplyExact((long) missing, settings.essencePerDurability());
        } catch (ArithmeticException overflow) {
            essence = Long.MAX_VALUE;
        }
        essence = Math.max(1L, essence);

        return Optional.of(new RepairInfusionRecipe(
                missing,
                essence,
                fractured ? settings.fracturedLatentIngotCount() : 0,
                fractured
        ));
    }

    @Override
    public ResourceLocation id() {
        return ResourceLocation.fromNamespaceAndPath(
                EssenceAscendance.MOD_ID,
                fractured ? "infuser/repair_fractured" : "infuser/repair"
        );
    }

    @Override
    public EssenceInfuserWorkpieceMode workpieceMode() {
        return EssenceInfuserWorkpieceMode.REPAIR;
    }

    @Override
    public EssenceInfuserProgressModel progressModel() {
        return EssenceInfuserProgressModel.TIMED_ATOMIC;
    }

    @Override
    public int workpieceStackLimit(ItemStack workpiece) {
        return 1;
    }

    @Override
    public boolean allowsAutomationInput() {
        return false;
    }

    @Override
    public EssenceInfusionRequirements essenceRequirements(EssenceInfuserRecipeContext context) {
        if (context.sourceEssence() == null) {
            return EssenceInfusionRequirements.none();
        }
        return new EssenceInfusionRequirements(
                Map.of(context.sourceEssence().id(), essenceRequired),
                essenceRequired
        );
    }

    @Override
    public long infusionWork(EssenceInfuserRecipeContext context) {
        return essenceRequired;
    }

    @Override
    public ItemStack createOutput(EssenceInfuserRecipeContext context) {
        ItemStack workpiece = context == null ? ItemStack.EMPTY : context.workpiece();
        if (!isWorkpiece(workpiece)) {
            return ItemStack.EMPTY;
        }
        ItemStack repaired = workpiece.copy();
        repaired.setCount(1);
        repaired.setDamageValue(0);
        FracturedEquipmentData.clearFractured(repaired);
        return repaired;
    }
}
