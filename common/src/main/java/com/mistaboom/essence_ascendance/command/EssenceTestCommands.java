package com.mistaboom.essence_ascendance.command;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import com.mistaboom.essence_ascendance.equipment.EquipmentMobilityService;
import com.mistaboom.essence_ascendance.equipment.EquipmentStatResolver;
import com.mistaboom.essence_ascendance.equipment.EquipmentStatState;
import com.mistaboom.essence_ascendance.progression.MilestoneProgress;
import com.mistaboom.essence_ascendance.progression.MilestoneRequirement;
import com.mistaboom.essence_ascendance.progression.MilestoneService;
import com.mistaboom.essence_ascendance.progression.PermanentMilestoneService;
import com.mistaboom.essence_ascendance.progression.StatScalingResult;
import com.mistaboom.essence_ascendance.progression.StatScalingService;
import com.mistaboom.essence_ascendance.skill.SkillRegistry;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime;
import com.mistaboom.essence_ascendance.stat.EssenceStatRegistry;
import com.mistaboom.essence_ascendance.stat.StatDefinition;
import com.mistaboom.essence_ascendance.text.EssenceText;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.List;
import java.util.Set;

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
                )
                .then(
                        Commands.literal("skills")
                                .executes(context -> showSkillHelp(context.getSource()))
                                .then(Commands.literal("help")
                                        .executes(context -> showSkillHelp(context.getSource())))
                                .then(Commands.literal("milestones")
                                        .executes(context -> testSkillMilestones(context.getSource())))
                                .then(Commands.literal("effects")
                                        .executes(context -> testSkillEffects(context.getSource())))
                );
    }

    static int showHelp(CommandSourceStack source) {
        EssenceCommandUtil.send(source, EssenceCommandUtil.title(EssenceText.command("test.help.title")));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command(
                "/essence test luck",
                EssenceText.command("test.help.luck")
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command(
                "/essence test activation",
                EssenceText.command("test.help.activation")
        ));
        showSkillHelp(source);
        EssenceCommandUtil.send(source, EssenceCommandUtil.muted(EssenceText.command("test.help.scope")));
        return 1;
    }

    private static int showSkillHelp(CommandSourceStack source) {
        EssenceCommandUtil.send(source, EssenceCommandUtil.section(EssenceText.command("test.help.skills")));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command(
                "/essence test skills milestones",
                EssenceText.command("test.help.milestones")
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command(
                "/essence test skills effects",
                EssenceText.command("test.help.effects")
        ));
        return 1;
    }

    private static int testSkillEffects(CommandSourceStack source) {
        EssenceCommandUtil.send(source, EssenceCommandUtil.title("Skill Effect Invariants"));
        List<String> failures;
        try {
            failures = SkillEffectRuntime.validateInvariants();
        } catch (RuntimeException exception) {
            EssenceAscendance.LOGGER.error("Skill effect invariant diagnostic failed", exception);
            failures = List.of("Diagnostic could not complete: " + exception.getMessage());
        }

        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Implemented skills", Integer.toString(SkillEffectRuntime.implementedIds().size())
        ));
        if (failures.isEmpty()) {
            EssenceCommandUtil.send(source, EssenceCommandUtil.good(
                    "  PASS Registrations, relationships, elemental budgets, HUD codecs, config, combat math, and persistent-data boundary."
            ));
        } else {
            for (String failure : failures) {
                EssenceCommandUtil.send(source, EssenceCommandUtil.bad("  FAIL " + failure));
            }
        }
        EssenceCommandUtil.send(source, EssenceCommandUtil.muted(
                "  Pure diagnostics only: no combat is simulated and no player progression is changed."
        ));
        return failures.isEmpty() ? 1 : 0;
    }

    private static int testSkillMilestones(
            CommandSourceStack source
    ) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Set<ResourceLocation> referencedIds =
                SkillRegistry.referencedPermanentMilestoneIds();
        List<PermanentMilestoneService.Resolution> resolutions =
                PermanentMilestoneService.resolveAll(player, referencedIds);
        Set<ResourceLocation> completedIds =
                PermanentMilestoneService.completedIds(resolutions);

        int passed = 0;

        EssenceCommandUtil.send(
                source,
                EssenceCommandUtil.title("Skill Milestone Integration Test")
        );

        for (PermanentMilestoneService.Resolution resolution : resolutions) {
            /*
             * Check the live provider separately. Permanent resolution stays
             * valid after capture by design, even if a later override breaks.
             */
            MilestoneRequirement providerRequirement =
                    MilestoneRequirement.milestone(resolution.milestoneId());
            MilestoneProgress providerProgress;
            try {
                providerProgress = MilestoneService.evaluate(
                        player,
                        providerRequirement
                );
            } catch (RuntimeException exception) {
                EssenceAscendance.LOGGER.error(
                        "Skill milestone '{}' provider diagnostic failed",
                        resolution.milestoneId(),
                        exception
                );
                providerProgress = new MilestoneProgress(
                        providerRequirement,
                        false,
                        false,
                        List.of()
                );
            }

            boolean completedProjectionMatches =
                    completedIds.contains(resolution.milestoneId())
                            == resolution.complete();
            boolean rowPass = providerProgress.resolvable()
                    && resolution.resolvable()
                    && completedProjectionMatches;

            if (rowPass) {
                passed++;
            }

            String providerState = !providerProgress.resolvable()
                    ? "UNRESOLVED"
                    : providerProgress.complete()
                    ? "COMPLETE"
                    : "INCOMPLETE";
            String permanentState = resolution.captured()
                    ? "CAPTURED"
                    : resolution.providerComplete()
                    ? "PROVIDER COMPLETE"
                    : "INCOMPLETE";

            EssenceCommandUtil.send(
                    source,
                    Component.literal("  ")
                            .append(rowPass
                                    ? EssenceCommandUtil.good("PASS ")
                                    : EssenceCommandUtil.bad("FAIL "))
                            .append(Component.literal(resolution.displayName())
                                    .withStyle(ChatFormatting.WHITE))
                            .append(Component.literal(
                                    " [" + resolution.milestoneId() + "]"
                            ).withStyle(ChatFormatting.DARK_GRAY))
                            .append(Component.literal(
                                    " | provider=" + providerState
                                            + " | permanent=" + permanentState
                            ).withStyle(ChatFormatting.GRAY))
            );
        }

        boolean coverageMatches = resolutions.size() == referencedIds.size();
        boolean pass = coverageMatches && passed == referencedIds.size();

        EssenceCommandUtil.send(source, EssenceCommandUtil.section("Summary"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Resolvable catalog gates",
                passed + " / " + referencedIds.size()
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Completed permanent gates",
                Integer.toString(completedIds.size())
        ));
        EssenceCommandUtil.send(
                source,
                pass
                        ? EssenceCommandUtil.good(
                                "  PASS All referenced skill milestones use the configurable provider framework."
                        )
                        : EssenceCommandUtil.bad(
                                "  FAIL One or more referenced skill milestones are unresolved or inconsistent."
                        )
        );
        EssenceCommandUtil.send(source, EssenceCommandUtil.muted(
                "  This diagnostic is read-only; it does not grant or capture milestone completion."
        ));

        return pass ? 1 : 0;
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
                    "  Fast player setup: /essence admin player tier set transcendent, then /essence admin player bonuses max luck"
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
                                .append(Component.literal(stat.displayName()).withStyle(style -> style.withColor(EssenceCommandUtil.categoryColor(stat.category()))))
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
                                .append(Component.literal(stat.displayName()).withStyle(style -> style.withColor(EssenceCommandUtil.categoryColor(stat.category()))))
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
