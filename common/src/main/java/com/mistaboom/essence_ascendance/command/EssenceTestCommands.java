package com.mistaboom.essence_ascendance.command;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import com.mistaboom.essence_ascendance.equipment.EquipmentMobilityService;
import com.mistaboom.essence_ascendance.equipment.EquipmentStatResolver;
import com.mistaboom.essence_ascendance.equipment.EquipmentStatState;
import com.mistaboom.essence_ascendance.progression.StatScalingResult;
import com.mistaboom.essence_ascendance.progression.StatScalingService;
import com.mistaboom.essence_ascendance.stat.EssenceStatRegistry;
import com.mistaboom.essence_ascendance.stat.EssenceStats;
import com.mistaboom.essence_ascendance.stat.StatDefinition;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class EssenceTestCommands {

    private static final double EPSILON = 0.0000001;
    private static final ResourceLocation LUCK_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(
                    EssenceAscendance.MOD_ID,
                    "luck"
            );

    private EssenceTestCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("test")
                .requires(source -> source.hasPermission(EssenceCommandUtil.ADMIN_PERMISSION))
                .executes(context -> showHelp(context.getSource()))
                .then(
                        Commands.literal("help")
                                .executes(context -> showHelp(context.getSource()))
                )
                .then(
                        Commands.literal("luck")
                                .executes(context -> testLuck(context.getSource()))
                )
                .then(
                        Commands.literal("activation")
                                .executes(context -> testActivation(context.getSource()))
                );
    }

    static int showHelp(CommandSourceStack source) {
        EssenceCommandUtil.send(source, EssenceCommandUtil.title("Essence Test Commands"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command(
                "/essence test luck",
                "deterministically validate the Essence -> vanilla Luck attribute pipeline"
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command(
                "/essence test activation",
                "show which invested bonuses are active in the current worn/main-hand context"
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.muted(
                "These are diagnostic tests only; they do not alter progression or equipment."
        ));
        return 1;
    }

    private static int testLuck(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();

        /* Force the normal server-side service to reconcile the modifier now. */
        EquipmentMobilityService.sync(player);
        EquipmentMobilityService.MobilityState state = EquipmentMobilityService.evaluate(player);

        AttributeInstance luck = player.getAttribute(Attributes.LUCK);
        if (luck == null) {
            EssenceCommandUtil.fail(source, "Player has no vanilla Luck attribute instance.");
            return 0;
        }

        boolean expected = state.luckBonus() > EPSILON;
        boolean present = luck.hasModifier(LUCK_MODIFIER_ID);
        boolean modifierStatePasses = expected == present;
        boolean valueFinite = Double.isFinite(state.actualLuck());
        boolean pass = modifierStatePasses && valueFinite;

        EssenceCommandUtil.send(source, EssenceCommandUtil.title("Luck Integration Test"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Essence Luck bonus",
                EssenceCommandUtil.value(EssenceCommandUtil.formatDecimal(state.luckBonus()))
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Actual vanilla Luck",
                EssenceCommandUtil.value(EssenceCommandUtil.formatDecimal(state.actualLuck()))
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Ascendance modifier expected",
                expected ? EssenceCommandUtil.good("YES") : EssenceCommandUtil.muted("NO")
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Ascendance modifier present",
                present ? EssenceCommandUtil.good("YES") : EssenceCommandUtil.muted("NO")
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Modifier ID",
                EssenceCommandUtil.muted(LUCK_MODIFIER_ID.toString())
        ));

        if (pass) {
            EssenceCommandUtil.send(
                    source,
                    Component.literal("  PASS ")
                            .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                            .append(Component.literal(
                                    "Essence Luck is reconciled into Minecraft's native Luck attribute."
                            ).withStyle(ChatFormatting.GRAY))
            );
        } else {
            EssenceCommandUtil.send(
                    source,
                    Component.literal("  FAIL ")
                            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                            .append(Component.literal(
                                    "The expected Essence Luck modifier state does not match the vanilla attribute."
                            ).withStyle(ChatFormatting.GRAY))
            );
        }

        if (!expected) {
            EssenceCommandUtil.send(source, EssenceCommandUtil.warn(
                    "  NOTE: This is a zero-bonus validation. To test a nonzero modifier, wear qualifying Ascendance armor and invest Luck."
            ));
            EssenceCommandUtil.send(source, EssenceCommandUtil.muted(
                    "  Fast setup: /essence admin tier set transcendent, then /essence admin stat max luck"
            ));
        }

        EssenceCommandUtil.send(source, EssenceCommandUtil.muted(
                "  This test validates the attribute pipeline directly; it intentionally does not rely on random fishing or chest rolls."
        ));

        return pass ? 1 : 0;
    }

    private static int testActivation(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PlayerEssenceData playerData = EssenceSavedData
                .get(player.server)
                .getPlayerData(player.getUUID());

        EquipmentStatState active = EquipmentStatResolver.evaluate(player);

        int activeCount = 0;
        int inactiveCount = 0;
        int zeroCount = 0;

        EssenceCommandUtil.send(source, EssenceCommandUtil.title("Current Stat Activation Test"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.muted(
                "Context: worn passive equipment + main hand; same-stat contexts merge by MAX."
        ));

        for (StatDefinition stat : EssenceStatRegistry.values()) {
            StatScalingResult scaling = StatScalingService.evaluate(playerData, stat);
            double bonus = scaling.scaledBonus();
            double strength = active.strength(stat);

            if (Math.abs(bonus) <= EPSILON) {
                zeroCount++;
                continue;
            }

            double applied = bonus * strength;
            if (strength > EPSILON) {
                activeCount++;
                EssenceCommandUtil.send(
                        source,
                        Component.literal("  ACTIVE ")
                                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                                .append(Component.literal(stat.displayName()).withStyle(
                                        EssenceCommandUtil.categoryColor(stat.category())
                                ))
                                .append(Component.literal(
                                        " | bonus " + EssenceCommandUtil.formatBonus(stat, bonus)
                                                + " | strength " + EssenceCommandUtil.formatStrength(strength)
                                                + " | applied " + EssenceCommandUtil.formatBonus(stat, applied)
                                ).withStyle(ChatFormatting.GRAY))
                );
            } else {
                inactiveCount++;
                EssenceCommandUtil.send(
                        source,
                        Component.literal("  INACTIVE ")
                                .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)
                                .append(Component.literal(stat.displayName()).withStyle(
                                        EssenceCommandUtil.categoryColor(stat.category())
                                ))
                                .append(Component.literal(
                                        " | earned " + EssenceCommandUtil.formatBonus(stat, bonus)
                                                + " | no active equipment applicability"
                                ).withStyle(ChatFormatting.GRAY))
                );
            }
        }

        EssenceCommandUtil.send(source, EssenceCommandUtil.section("Summary"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Active nonzero stats",
                EssenceCommandUtil.good(Integer.toString(activeCount))
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Invested but inactive",
                inactiveCount == 0
                        ? EssenceCommandUtil.good("0")
                        : EssenceCommandUtil.warn(Integer.toString(inactiveCount))
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Currently zero-bonus stats",
                EssenceCommandUtil.muted(Integer.toString(zeroCount))
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.muted(
                "  Note: durability efficiency is item-local during durability events; action-specific/off-hand contexts can differ from this snapshot."
        ));

        return 1;
    }
}
