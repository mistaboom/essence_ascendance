package com.mistaboom.essence_ascendance.client.presentation;

import com.mistaboom.essence_ascendance.config.InfuserBalanceSettings;
import com.mistaboom.essence_ascendance.equipment.EquipmentActivationType;
import com.mistaboom.essence_ascendance.equipment.EquipmentBaselineConfig;
import com.mistaboom.essence_ascendance.equipment.EquipmentBaselineProperty;
import com.mistaboom.essence_ascendance.equipment.EquipmentProfileDefinition;
import com.mistaboom.essence_ascendance.equipment.EquipmentProfileRegistry;
import com.mistaboom.essence_ascendance.equipment.EquipmentProfiles;
import com.mistaboom.essence_ascendance.equipment.EquipmentTier;
import com.mistaboom.essence_ascendance.equipment.EquipmentBaselineService;
import com.mistaboom.essence_ascendance.equipment.ArmorStatWeights;
import com.mistaboom.essence_ascendance.equipment.EquipmentTierData;
import com.mistaboom.essence_ascendance.client.ui.content.ItemPresentation;
import com.mistaboom.essence_ascendance.text.EssenceText;
import com.mistaboom.essence_ascendance.visual.AscendancePalette;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.resources.ResourceLocation;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/** Registry/config-derived equipment reference data; ItemStack-dependent state remains explicitly contextual. */
public final class EquipmentPresentationData {
    private EquipmentPresentationData() { }

    public record Applicability(EquipmentActivationType activation, ResourceLocation statId, double strength) { }
    public record Profile(EquipmentProfileDefinition definition,
                          Map<EquipmentBaselineProperty, Double> baselineMultipliers,
                          List<Applicability> applicability) { }
    public record TierBaseline(EquipmentTier tier, EquipmentBaselineConfig.TierBaseline baseline,
                               int shieldDurability, double shieldNativeReflectionPercent,
                               double shieldBlockAmplification) { }
    public record InfusionTarget(EquipmentTier tier, long totalEssenceRequired, int matrixCount) { }

    public static double physicalValue(Profile profile, TierBaseline tier, EquipmentBaselineProperty property,
                                       boolean quantized, EquipmentSlot slot) {
        double value = EquipmentBaselineService.resolvedValue(tier.baseline().value(property),
                profile.definition(), property, quantized);
        return slot != null && (property == EquipmentBaselineProperty.ARMOR || property == EquipmentBaselineProperty.TOUGHNESS)
                ? ArmorStatWeights.pointsForSlot(value, slot, quantized) : value;
    }

    /** Current usable item durability, not an unconnected generated durability target. */
    public static OptionalInt nativeDurability(Profile profile, TierBaseline tier, ResourceLocation item) {
        if (profile.definition().id().equals(EquipmentProfiles.SHIELD.id())) return OptionalInt.of(tier.shieldDurability());
        if (!BuiltInRegistries.ITEM.containsKey(item)) return OptionalInt.empty();
        return OptionalInt.of(BuiltInRegistries.ITEM.get(item).getDefaultInstance().getMaxDamage());
    }

    public static double wornShare(EquipmentTier tier, EquipmentSlot slot) {
        return tier == EquipmentTier.LATENT ? 0 : ArmorStatWeights.weightFor(slot);
    }

    public static ItemPresentation itemModel(ResourceLocation item, EquipmentTier tier) {
        Component tierName = EssenceText.equipmentTier(tier).withColor(AscendancePalette.tierMetalRgb(tier));
        Component name = Component.translatable("item." + item.getNamespace() + "." + item.getPath());
        return new ItemPresentation(item, DataComponentPatch.builder()
                .set(DataComponents.CUSTOM_DATA, EquipmentTierData.tierData(tier)).build(),
                EssenceText.guide("archive.value.item_variant", tierName, name));
    }
    public record Projection(PresentationContext.Availability availability, List<Profile> profiles,
                             List<TierBaseline> tierBaselines, List<InfusionTarget> infusionTargets,
                             long repairEssencePerDurability, int fracturedLatentIngotCount,
                             Map<String, Map<String, Integer>> equipmentEssenceWeights,
                             boolean quantizedBaselines) {
        public boolean ready() { return availability == PresentationContext.Availability.READY; }
    }

    public static Projection project(PresentationContext context) {
        EquipmentProfiles.init();
        if (context.runtime().availability() != PresentationContext.Availability.READY
                || context.runtime().definition() == null) {
            return new Projection(context.runtime().availability(), List.of(), List.of(), List.of(),
                    0, 0, Map.of(), false);
        }
        var runtime = context.runtime().definition();
        List<Profile> profiles = EquipmentProfileRegistry.values().stream()
                .sorted(Comparator.comparing(profile -> profile.id().toString()))
                .map(profile -> new Profile(profile, profile.baselineMultipliers(), profile.statApplicability().entrySet()
                        .stream().flatMap(activation -> activation.getValue().entrySet().stream()
                                .map(stat -> new Applicability(activation.getKey(), stat.getKey(), stat.getValue())))
                        .sorted(Comparator.comparing((Applicability value) -> value.activation().ordinal())
                                .thenComparing(value -> value.statId().toString())).toList()))
                .toList();
        var baselineConfig = runtime.config().equipmentBaselineConfig();
        var shield = runtime.config().shieldBalance();
        List<TierBaseline> tiers = java.util.Arrays.stream(EquipmentTier.values()).map(tier -> {
            ResourceLocation id = tier.ascendanceTier().id();
            return new TierBaseline(tier, baselineConfig.tierBaselines().get(id), shield.durability().get(tier),
                    shield.nativeReflectionPercent(tier), shield.blockAmplification().get(tier));
        }).toList();
        InfuserBalanceSettings infuser = runtime.config().infuserBalance();
        List<InfusionTarget> targets = java.util.Arrays.stream(EquipmentTier.values())
                .filter(tier -> tier != EquipmentTier.LATENT).map(tier -> {
                    var values = infuser.equipmentUpgrade(tier.serializedName());
                    return new InfusionTarget(tier, values.totalEssenceRequired(), values.matrixCount());
                }).toList();
        return new Projection(PresentationContext.Availability.READY, profiles, tiers, targets,
                infuser.repair().essencePerDurability(), infuser.repair().fracturedLatentIngotCount(),
                infuser.equipmentEssenceWeights(),
                runtime.composition().getOrDefault("equipment_quantization", 0.0) == 1.0);
    }
}
