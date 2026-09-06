package com.mistaboom.essence_ascendance.infuser;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.equipment.EquipmentTier;
import com.mistaboom.essence_ascendance.equipment.EquipmentTierData;
import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.config.InfuserBalanceSettings;
import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import com.mistaboom.essence_ascendance.item.AscendanceItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Exact thematic streamed infusion recipe for one evolving Ascendance equipment stack. */
public record EquipmentInfusionRecipe(
        EquipmentTier currentTier,
        EquipmentTier targetTier,
        Map<ResourceLocation, Long> requirements,
        int matrixCount
) implements EssenceInfuserRecipe {

    public EquipmentInfusionRecipe {
        requirements = Collections.unmodifiableMap(new LinkedHashMap<>(requirements));
        if (targetTier == null || targetTier == EquipmentTier.LATENT || matrixCount <= 0 || requirements.isEmpty()) {
            throw new IllegalArgumentException("Invalid equipment infusion recipe");
        }
    }

    public static boolean isWorkpiece(ItemStack stack) {
        return EquipmentTierData.isAscendanceEquipment(stack)
                && EquipmentTierData.tier(stack) != EquipmentTier.TRANSCENDENT;
    }

    public static Optional<EquipmentInfusionRecipe> forWorkpiece(ItemStack stack) {
        if (!isWorkpiece(stack)) return Optional.empty();
        EquipmentTier current = EquipmentTierData.tier(stack);
        EquipmentTier target = current.next();
        if (target == null) return Optional.empty();
        return Optional.of(new EquipmentInfusionRecipe(
                current,
                target,
                buildRequirements(stack, target),
                matrixCount(target)
        ));
    }

    private static Map<ResourceLocation, Long> buildRequirements(ItemStack stack, EquipmentTier target) {
        InfuserBalanceSettings settings = EssenceConfigManager.get().infuserBalance();
        long total = settings.equipmentUpgrade(target.serializedName()).totalEssenceRequired();
        Map<String, Integer> weights = settings.equipmentWeights(equipmentKey(stack));

        int weightTotal = weights.values().stream().mapToInt(Integer::intValue).sum();
        LinkedHashMap<ResourceLocation, Long> result = new LinkedHashMap<>();
        long assigned = 0L;
        int index = 0;
        for (Map.Entry<String, Integer> entry : weights.entrySet()) {
            index++;
            long amount = index == weights.size()
                    ? total - assigned
                    : Math.multiplyExact(total, entry.getValue()) / weightTotal;
            assigned += amount;
            result.put(essence(entry.getKey()).id(), amount);
        }
        return result;
    }

    private static int matrixCount(EquipmentTier target) {
        return EssenceConfigManager.get().infuserBalance()
                .equipmentUpgrade(target.serializedName())
                .matrixCount();
    }

    private static String equipmentKey(ItemStack stack) {
        if (stack.is(AscendanceItems.ASCENDANCE_HELMET.get())) return "helmet";
        if (stack.is(AscendanceItems.ASCENDANCE_CHESTPLATE.get())) return "chestplate";
        if (stack.is(AscendanceItems.ASCENDANCE_LEGGINGS.get())) return "leggings";
        if (stack.is(AscendanceItems.ASCENDANCE_BOOTS.get())) return "boots";
        if (stack.is(AscendanceItems.ASCENDANCE_MELEE_WEAPON.get())) return "melee_weapon";
        if (stack.is(AscendanceItems.ASCENDANCE_RANGED_WEAPON.get())) return "ranged_weapon";
        if (stack.is(AscendanceItems.ASCENDANCE_CASTER.get())) return "magic_caster";
        if (stack.is(AscendanceItems.ASCENDANCE_PICKAXE.get())) return "pickaxe";
        if (stack.is(AscendanceItems.ASCENDANCE_AXE.get())) return "axe";
        if (stack.is(AscendanceItems.ASCENDANCE_SHOVEL.get())) return "shovel";
        if (stack.is(AscendanceItems.ASCENDANCE_HOE.get())) return "hoe";
        if (stack.is(AscendanceItems.ASCENDANCE_SHIELD.get())) return "shield";
        throw new IllegalArgumentException("Unsupported Ascendance equipment workpiece");
    }

    private static EssenceDefinition essence(String key) {
        return switch (key) {
            case "offense" -> EssenceTypes.OFFENSE;
            case "defense" -> EssenceTypes.DEFENSE;
            case "vitality" -> EssenceTypes.VITALITY;
            case "mobility" -> EssenceTypes.MOBILITY;
            case "gathering" -> EssenceTypes.GATHERING;
            case "utility" -> EssenceTypes.UTILITY;
            default -> throw new IllegalArgumentException("Unknown equipment Essence key " + key);
        };
    }

    @Override public ResourceLocation id() {
        return ResourceLocation.fromNamespaceAndPath(EssenceAscendance.MOD_ID, "infuser/equipment_" + targetTier.serializedName());
    }
    @Override public EssenceInfuserWorkpieceMode workpieceMode() { return EssenceInfuserWorkpieceMode.EQUIPMENT; }
    @Override public EssenceInfuserProgressModel progressModel() { return EssenceInfuserProgressModel.STREAMED_PERSISTENT; }
    @Override public int workpieceStackLimit(ItemStack workpiece) { return 1; }
    @Override public boolean allowsAutomationInput() { return false; }
    @Override public EssenceInfusionRequirements essenceRequirements(EssenceInfuserRecipeContext context) {
        return new EssenceInfusionRequirements(requirements, requirements.values().stream().mapToLong(Long::longValue).sum());
    }
    @Override public int processingTicks(EssenceInfuserRecipeContext context) { return 0; }
    @Override public ItemStack createOutput(EssenceInfuserRecipeContext context) { return ItemStack.EMPTY; }

    public long requirementFor(EssenceDefinition essence) { return requirements.getOrDefault(essence.id(), 0L); }
    public long totalRequired() { return requirements.values().stream().mapToLong(Long::longValue).sum(); }
}
