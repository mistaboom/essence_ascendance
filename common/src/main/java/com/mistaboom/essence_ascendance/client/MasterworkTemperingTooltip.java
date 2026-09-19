package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.balance.runtime.RuntimeBalanceDefinition;
import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.equipment.EquipmentMaintenanceData;
import com.mistaboom.essence_ascendance.equipment.MasterworkTemperingRoles;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.balance.SkillRankEffectScaling;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudCards;
import com.mistaboom.essence_ascendance.visual.AscendancePalette;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;

/** Client presentation for item-local Masterwork Tempering state, including anvil result previews. */
public final class MasterworkTemperingTooltip {
    private MasterworkTemperingTooltip() { }

    public static void append(ItemStack stack, List<Component> tooltip) {
        if (stack == null || stack.isEmpty() || tooltip == null) return;
        double capacity = EquipmentMaintenanceData.overdurabilityCapacity(stack);
        double current = Math.min(capacity, EquipmentMaintenanceData.overdurability(stack));
        if (capacity <= 0 || current <= 0) return;

        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("tooltip.essence_ascendance.masterwork.header")
                .withStyle(style -> style.withColor(AscendancePalette.UTILITY).withBold(true)));
        tooltip.add(Component.literal("  ").append(Component.translatable(
                        "tooltip.essence_ascendance.masterwork.overdurability",
                        SkillEffectHudCards.compact(current), SkillEffectHudCards.compact(capacity)))
                .withStyle(ChatFormatting.GRAY));

        double performance = currentPerformance(current, capacity);
        if (performance <= 0) {
            tooltip.add(Component.literal("  ").append(Component.translatable(
                            "tooltip.essence_ascendance.masterwork.inactive"))
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }

        String percent = SkillEffectHudCards.compact(performance * 100.0);
        boolean specific = false;
        if (MasterworkTemperingRoles.melee(stack)) {
            addBuff(tooltip, "attack_damage", percent);
            specific = true;
        }
        if (MasterworkTemperingRoles.attackSpeed(stack)) {
            addBuff(tooltip, "attack_speed", percent);
            specific = true;
        }
        if (MasterworkTemperingRoles.mining(stack)) {
            addBuff(tooltip, "mining_speed", percent);
            specific = true;
        }
        if (MasterworkTemperingRoles.ranged(stack)) {
            addBuff(tooltip, "ranged_damage", percent);
            specific = true;
        }
        if (MasterworkTemperingRoles.caster(stack)) {
            addBuff(tooltip, "magic_damage", percent);
            specific = true;
        }
        if (MasterworkTemperingRoles.armor(stack)) {
            addBuff(tooltip, "armor_toughness", percent);
            specific = true;
        }
        if (!specific) {
            tooltip.add(Component.literal("  ").append(Component.translatable(
                            "tooltip.essence_ascendance.masterwork.buffer_only"))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static void addBuff(List<Component> tooltip, String key, String percent) {
        tooltip.add(Component.literal("  ").append(Component.translatable(
                        "tooltip.essence_ascendance.masterwork." + key, percent))
                .withStyle(ChatFormatting.GREEN));
    }

    private static double currentPerformance(double current, double capacity) {
        int rank = ClientCommittedSkills.effectiveRank(SkillIds.MASTERWORK_TEMPERING);
        if (rank <= 0 || capacity <= 0) return 0;

        RuntimeBalanceDefinition runtime = EssenceConfigManager.clientRuntime();
        if (runtime == null) runtime = EssenceConfigManager.serverRuntime();
        if (runtime == null) return 0;
        RuntimeBalanceDefinition resolvedRuntime = runtime;

        var resolved = SkillRankEffectScaling.apply(
                resolvedRuntime.config().skillEffects(),
                Map.of(SkillIds.MASTERWORK_TEMPERING, rank),
                (id, resolvedRank) -> {
                    var curve = resolvedRuntime.skillCurves().get(id.toString());
                    if (curve == null || resolvedRank < 1 || resolvedRank > curve.ranks().size()) return 1.0;
                    return curve.ranks().get(resolvedRank - 1).powerMultiplier()
                            / curve.ranks().getFirst().powerMultiplier();
                }
        );
        double maximum = resolved.utility().masterworkTempering().maximumPerformanceBonus();
        return Math.clamp(current / capacity, 0, 1) * maximum;
    }
}
