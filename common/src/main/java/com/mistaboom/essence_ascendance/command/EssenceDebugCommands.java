package com.mistaboom.essence_ascendance.command;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.config.EssenceServerConfig;
import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import com.mistaboom.essence_ascendance.equipment.ArmorStatWeights;
import com.mistaboom.essence_ascendance.equipment.EquipmentActivationType;
import com.mistaboom.essence_ascendance.equipment.EquipmentAttributeService;
import com.mistaboom.essence_ascendance.equipment.EquipmentBaselineProperty;
import com.mistaboom.essence_ascendance.equipment.EquipmentBaselineResult;
import com.mistaboom.essence_ascendance.equipment.EquipmentBaselineService;
import com.mistaboom.essence_ascendance.equipment.EquipmentDamageService;
import com.mistaboom.essence_ascendance.equipment.EquipmentVitalityService;
import com.mistaboom.essence_ascendance.equipment.EquipmentProfileDefinition;
import com.mistaboom.essence_ascendance.equipment.EquipmentProfileItem;
import com.mistaboom.essence_ascendance.equipment.EquipmentProfileRegistry;
import com.mistaboom.essence_ascendance.equipment.EquipmentStatProfile;
import com.mistaboom.essence_ascendance.equipment.EquipmentStatProviderRegistry;
import com.mistaboom.essence_ascendance.equipment.EquipmentStatResolver;
import com.mistaboom.essence_ascendance.equipment.EquipmentStatState;
import com.mistaboom.essence_ascendance.equipment.EquipmentValueService;
import com.mistaboom.essence_ascendance.progression.AscendanceEngine;
import com.mistaboom.essence_ascendance.progression.AscendanceEvaluationResult;
import com.mistaboom.essence_ascendance.progression.CategoryDevelopment;
import com.mistaboom.essence_ascendance.progression.HarvestProgressionSafety;
import com.mistaboom.essence_ascendance.progression.MilestoneDefinition;
import com.mistaboom.essence_ascendance.progression.MilestoneProgress;
import com.mistaboom.essence_ascendance.progression.MilestoneProviders;
import com.mistaboom.essence_ascendance.progression.MilestoneRequirement;
import com.mistaboom.essence_ascendance.progression.MilestoneService;
import com.mistaboom.essence_ascendance.progression.StatInvestmentLimit;
import com.mistaboom.essence_ascendance.progression.StatScalingResult;
import com.mistaboom.essence_ascendance.progression.StatScalingService;
import com.mistaboom.essence_ascendance.progression.TierInvestmentPolicy;
import com.mistaboom.essence_ascendance.stat.EssenceStatRegistry;
import com.mistaboom.essence_ascendance.stat.EssenceStats;
import com.mistaboom.essence_ascendance.stat.StatCategory;
import com.mistaboom.essence_ascendance.stat.StatDefinition;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/*
 * Development diagnostics for the post-conduit equipment architecture.
 *
 * The commands intentionally describe the same four layers used by gameplay:
 *
 * player stat scaling -> tier/archetype baseline -> item applicability -> context.
 */
public final class EssenceDebugCommands {

    private static final DynamicCommandExceptionType UNKNOWN_STAT =
            new DynamicCommandExceptionType(
                    value -> Component.literal("Unknown stat: " + value)
            );

    private static final DynamicCommandExceptionType UNKNOWN_CATEGORY =
            new DynamicCommandExceptionType(
                    value -> Component.literal("Unknown stat category: " + value)
            );

    private static final DynamicCommandExceptionType UNKNOWN_MILESTONE =
            new DynamicCommandExceptionType(
                    value -> Component.literal("Unknown milestone: " + value)
            );

    private EssenceDebugCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("debug")
                .requires(source -> source.hasPermission(2))
                .executes(context -> showSummary(context.getSource()))
                .then(
                        Commands.literal("summary")
                                .executes(context -> showSummary(context.getSource()))
                )
                .then(
                        Commands.literal("stat")
                                .then(
                                        Commands.argument("stat", StringArgumentType.word())
                                                .suggests(EssenceDebugCommands::suggestStats)
                                                .executes(context -> showStat(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "stat")
                                                ))
                                )
                )
                .then(
                        Commands.literal("category")
                                .then(
                                        Commands.argument("category", StringArgumentType.word())
                                                .suggests(EssenceDebugCommands::suggestCategories)
                                                .executes(context -> showCategory(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "category")
                                                ))
                                )
                )
                .then(
                        Commands.literal("config")
                                .executes(context -> showConfig(context.getSource()))
                )
                .then(
                        Commands.literal("reloadconfig")
                                .executes(context -> reloadConfig(context.getSource()))
                )
                .then(
                        Commands.literal("milestones")
                                .executes(context -> showMilestones(context.getSource()))
                )
                .then(
                        Commands.literal("milestone")
                                .then(
                                        Commands.argument("milestone", StringArgumentType.string())
                                                .suggests(EssenceDebugCommands::suggestMilestones)
                                                .executes(context -> showMilestone(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "milestone")
                                                ))
                                )
                )
                .then(
                        Commands.literal("setmilestone")
                                .then(
                                        Commands.argument("milestone", StringArgumentType.string())
                                                .suggests(EssenceDebugCommands::suggestMilestones)
                                                .then(
                                                        Commands.argument("complete", BoolArgumentType.bool())
                                                                .executes(context -> setMilestone(
                                                                        context.getSource(),
                                                                        StringArgumentType.getString(context, "milestone"),
                                                                        BoolArgumentType.getBool(context, "complete")
                                                                ))
                                                )
                                )
                )
                .then(
                        Commands.literal("equipment")
                                .executes(context -> showEquipment(context.getSource()))
                )
                .then(
                        Commands.literal("equipmentstats")
                                .executes(context -> showEquipmentStats(context.getSource()))
                )
                .then(
                        Commands.literal("gameplay")
                                .executes(context -> showGameplay(context.getSource()))
                )
                .then(
                        Commands.literal("damage")
                                .executes(context -> showDamage(context.getSource()))
                )
                .then(
                        Commands.literal("vitality")
                                .executes(context -> showVitality(context.getSource()))
                )
                .then(
                        Commands.literal("tool")
                                .executes(context -> showTool(context.getSource()))
                )
                .then(
                        Commands.literal("statprofile")
                                .then(
                                        Commands.argument("stat", StringArgumentType.word())
                                                .suggests(EssenceDebugCommands::suggestStats)
                                                .executes(context -> showStatProfile(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "stat")
                                                ))
                                )
                )
                .then(
                        Commands.literal("baseline")
                                .executes(context -> showBaselines(context.getSource()))
                );
    }

    private static int showSummary(
            CommandSourceStack source
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PlayerEssenceData playerData = EssenceSavedData
                .get(source.getServer())
                .getPlayerData(player.getUUID());

        long totalStored = 0L;
        long totalEffective = 0L;
        long totalCapacity = 0L;

        for (StatDefinition stat : EssenceStatRegistry.values()) {
            StatInvestmentLimit limit = TierInvestmentPolicy.evaluate(playerData, stat);
            totalStored = Math.addExact(totalStored, limit.storedInvestment());
            totalEffective = Math.addExact(totalEffective, limit.effectiveInvestment());
            totalCapacity = Math.addExact(totalCapacity, limit.investmentCap());
        }

        EssenceServerConfig config = EssenceConfigManager.get();

        final long finalTotalStored = totalStored;
        final long finalTotalEffective = totalEffective;
        final long finalTotalCapacity = totalCapacity;

        source.sendSuccess(
                () -> Component.literal("Essence Ascendance debug summary:"),
                false
        );
        source.sendSuccess(
                () -> Component.literal(
                        "  Player: " + player.getGameProfile().getName()
                                + " | Tier: " + playerData.getTier().displayName()
                ),
                false
        );
        source.sendSuccess(
                () -> Component.literal(
                        "  Investment: " + format(finalTotalEffective)
                                + " effective / " + format(finalTotalCapacity)
                                + " capacity (" + format(finalTotalStored) + " stored)"
                ),
                false
        );
        source.sendSuccess(
                () -> Component.literal(
                        "  Registries: "
                                + EssenceStatRegistry.size() + " stats, "
                                + EquipmentProfileRegistry.size() + " equipment profiles, "
                                + EquipmentStatProviderRegistry.providers().size() + " equipment providers"
                ),
                false
        );
        source.sendSuccess(
                () -> Component.literal(
                        "  Balance profile: " + config.balanceProfile().displayName()
                                + " [" + config.balanceProfile().id() + "]"
                ),
                false
        );

        AscendanceEvaluationResult evaluation = AscendanceEngine.evaluate(player);
        switch (evaluation.status()) {
            case AVAILABLE -> source.sendSuccess(
                    () -> Component.literal(
                            "  Next tier: " + evaluation.nextTier().displayName()
                                    + " | Ready: "
                                    + (evaluation.progress().readyToAscend() ? "YES" : "NO")
                    ),
                    false
            );
            case MAX_TIER -> source.sendSuccess(
                    () -> Component.literal("  Next tier: MAX TIER"),
                    false
            );
            case CONFIGURATION_ERROR -> source.sendSuccess(
                    () -> Component.literal("  Next tier: CONFIGURATION ERROR"),
                    false
            );
        }

        return 1;
    }

    private static int showStat(
            CommandSourceStack source,
            String statName
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        StatDefinition stat = resolveStat(statName);
        PlayerEssenceData playerData = EssenceSavedData
                .get(source.getServer())
                .getPlayerData(player.getUUID());

        StatInvestmentLimit limit = TierInvestmentPolicy.evaluate(playerData, stat);
        StatScalingResult scaling = StatScalingService.evaluate(playerData, stat);

        source.sendSuccess(
                () -> Component.literal("Stat Debug: " + stat.displayName()),
                false
        );
        source.sendSuccess(
                () -> Component.literal("  ID: " + stat.id()),
                false
        );
        source.sendSuccess(
                () -> Component.literal(
                        "  Category: " + stat.category().name().toLowerCase(Locale.ROOT)
                                + " | Essence: " + stat.essenceType().displayName()
                                + " | Unit: " + stat.unit().name().toLowerCase(Locale.ROOT)
                ),
                false
        );
        source.sendSuccess(
                () -> Component.literal(
                        "  Investment: " + format(limit.effectiveInvestment())
                                + " / " + format(limit.investmentCap())
                                + " effective; " + format(limit.storedInvestment())
                                + " stored; state " + limit.state()
                ),
                false
        );
        source.sendSuccess(
                () -> Component.literal(
                        "  Progression: " + formatPercent(scaling.progression())
                ),
                false
        );
        source.sendSuccess(
                () -> Component.literal(
                        "  Current bonus: " + formatBonus(stat, scaling.scaledBonus())
                                + " | Tier ceiling: "
                                + formatBonus(stat, scaling.currentTierMaximumBonus())
                                + " | Transcendent max: "
                                + formatBonus(stat, scaling.transcendentMaximumBonus())
                ),
                false
        );

        return showStatProfile(source, statName);
    }

    private static int showCategory(
            CommandSourceStack source,
            String categoryName
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        StatCategory category = resolveCategory(categoryName);
        CategoryDevelopment development = StatScalingService.evaluateCategory(player, category);

        int count = 0;
        for (StatDefinition stat : EssenceStatRegistry.values()) {
            if (stat.category() == category) {
                count++;
            }
        }

        int finalCount = count;
        source.sendSuccess(
                () -> Component.literal(
                        "Category Debug: " + category.name().toLowerCase(Locale.ROOT)
                ),
                false
        );
        source.sendSuccess(
                () -> Component.literal(
                        "  " + finalCount + " registered stats | "
                                + format(development.effectiveInvestment()) + " / "
                                + format(development.currentCapacity()) + " effective | "
                                + formatPercent(development.development()) + " developed"
                ),
                false
        );

        return 1;
    }

    private static int showConfig(CommandSourceStack source) {
        EssenceServerConfig config = EssenceConfigManager.get();

        source.sendSuccess(
                () -> Component.literal("Essence Ascendance configuration:"),
                false
        );
        source.sendSuccess(
                () -> Component.literal(
                        "  Path: " + EssenceConfigManager.getConfigPath().toAbsolutePath()
                ),
                false
        );
        source.sendSuccess(
                () -> Component.literal(
                        "  Config version: " + config.configVersion()
                                + " | Balance profile: " + config.balanceProfile().displayName()
                                + " [" + config.balanceProfile().id() + "]"
                ),
                false
        );
        source.sendSuccess(
                () -> Component.literal(
                        "  Definitions: " + config.statMaxBonuses().size() + " stat bonuses, "
                                + config.equipmentBaselineConfig().tierBaselines().size()
                                + " equipment tier baselines, "
                                + config.milestones().size() + " milestones, "
                                + config.advancements().size() + " Ascendance transitions"
                ),
                false
        );

        var harvestIssues = HarvestProgressionSafety.evaluate(config);
        source.sendSuccess(
                () -> Component.literal(
                        "  Harvest progression safety: "
                                + (harvestIssues.isEmpty()
                                ? "PASS"
                                : "WARNING (" + harvestIssues.size() + ")")
                ),
                false
        );

        for (HarvestProgressionSafety.Issue issue : harvestIssues) {
            source.sendSuccess(
                    () -> Component.literal(
                            "    " + issue.fromTierId() + " -> " + issue.toTierId()
                                    + ": configured level " + issue.configuredHarvestLevel()
                                    + ", milestone requires at least "
                                    + issue.requiredHarvestLevel()
                    ),
                    false
            );
        }

        return 1;
    }

    private static int reloadConfig(CommandSourceStack source) {
        EssenceConfigManager.reload();
        source.sendSuccess(
                () -> Component.literal("Reloaded Essence Ascendance configuration."),
                false
        );
        return showConfig(source);
    }

    private static int showMilestones(
            CommandSourceStack source
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        EssenceServerConfig config = EssenceConfigManager.get();

        source.sendSuccess(
                () -> Component.literal("Configured milestones:"),
                false
        );

        for (MilestoneDefinition milestone : config.milestones().values()) {
            MilestoneProgress progress = evaluateMilestone(player, milestone);
            source.sendSuccess(
                    () -> Component.literal(
                            "  " + milestone.displayName()
                                    + " [" + milestone.id() + "]: "
                                    + milestoneState(progress)
                    ),
                    false
            );
        }

        return 1;
    }

    private static int showMilestone(
            CommandSourceStack source,
            String milestoneName
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        MilestoneDefinition milestone = resolveMilestone(milestoneName);
        MilestoneProgress progress = evaluateMilestone(player, milestone);

        source.sendSuccess(
                () -> Component.literal("Milestone Debug: " + milestone.displayName()),
                false
        );
        source.sendSuccess(
                () -> Component.literal(
                        "  ID: " + milestone.id()
                                + " | Provider: " + milestone.providerId()
                                + " | Target: " + milestone.target()
                ),
                false
        );
        source.sendSuccess(
                () -> Component.literal("  State: " + milestoneState(progress)),
                false
        );

        return 1;
    }

    private static int setMilestone(
            CommandSourceStack source,
            String milestoneName,
            boolean complete
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        MilestoneDefinition milestone = resolveMilestone(milestoneName);

        if (!milestone.providerId().equals(MilestoneProviders.INTERNAL)) {
            source.sendFailure(
                    Component.literal(
                            "Only INTERNAL milestones can be changed directly. "
                                    + milestone.id() + " uses " + milestone.providerId()
                    )
            );
            return 0;
        }

        ResourceLocation target = ResourceLocation.tryParse(milestone.target());
        if (target == null) {
            source.sendFailure(
                    Component.literal("Milestone has invalid internal target: " + milestone.target())
            );
            return 0;
        }

        EssenceSavedData savedData = EssenceSavedData.get(player.server);
        boolean changed = complete
                ? savedData.completeInternalMilestone(player.getUUID(), target)
                : savedData.revokeInternalMilestone(player.getUUID(), target);

        source.sendSuccess(
                () -> Component.literal(
                        "Milestone " + milestone.displayName() + " is now "
                                + (complete ? "COMPLETE" : "INCOMPLETE")
                                + (changed ? "." : " (state was already set).")
                ),
                false
        );
        return 1;
    }

    private static int showEquipment(
            CommandSourceStack source
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();

        source.sendSuccess(
                () -> Component.literal("Active equipment profiles:"),
                false
        );

        showItemProfile(source, "Main hand", player.getItemInHand(InteractionHand.MAIN_HAND));
        showItemProfile(source, "Off hand", player.getItemInHand(InteractionHand.OFF_HAND));

        for (EquipmentSlot slot : EquipmentStatResolver.armorSlots()) {
            showItemProfile(
                    source,
                    slot.getName(),
                    player.getItemBySlot(slot)
            );
        }

        return 1;
    }

    private static void showItemProfile(
            CommandSourceStack source,
            String label,
            ItemStack stack
    ) {
        if (stack.isEmpty()) {
            source.sendSuccess(
                    () -> Component.literal("  " + label + ": EMPTY"),
                    false
            );
            return;
        }

        EquipmentStatProfile profile = EquipmentStatResolver.inspectItem(stack);
        StringBuilder details = new StringBuilder();

        for (EquipmentActivationType activation : profile.activations()) {
            if (details.length() > 0) {
                details.append(" | ");
            }
            details.append(activation.name()).append(": ");
            appendStrengths(details, profile.strengths(activation));
        }

        if (details.length() == 0) {
            details.append("NO ESSENCE STAT PROFILE");
        }

        String itemName = stack.getHoverName().getString();
        String finalDetails = details.toString();
        source.sendSuccess(
                () -> Component.literal(
                        "  " + label + ": " + itemName + " -> " + finalDetails
                ),
                false
        );
    }

    private static int showEquipmentStats(
            CommandSourceStack source
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        EquipmentStatState state = EquipmentStatResolver.evaluate(player);

        source.sendSuccess(
                () -> Component.literal(
                        "Resolved active stats (worn armor + main hand; same-stat contexts merge by MAX):"
                ),
                false
        );

        if (state.values().isEmpty()) {
            source.sendSuccess(
                    () -> Component.literal("  NONE"),
                    false
            );
            return 1;
        }

        for (Map.Entry<ResourceLocation, Double> entry : state.values().entrySet()) {
            source.sendSuccess(
                    () -> Component.literal(
                            "  " + entry.getKey() + ": " + formatStrength(entry.getValue())
                    ),
                    false
            );
        }

        return 1;
    }

    private static int showGameplay(
            CommandSourceStack source
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();

        /* Ensure command output reflects equipment/stat changes immediately. */
        EquipmentAttributeService.sync(player);
        EquipmentAttributeService.AppliedState state =
                EquipmentAttributeService.evaluate(player);

        source.sendSuccess(
                () -> Component.literal(
                        "Ascendance Gameplay Attribute Debug:"
                ),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                        "  Armor: +" + formatDecimal(state.armor())
                                + " Ascendance | actual "
                                + formatDecimal(player.getAttributeValue(Attributes.ARMOR))
                                + " | Toughness: +"
                                + formatDecimal(state.toughness())
                                + " Ascendance | actual "
                                + formatDecimal(player.getAttributeValue(Attributes.ARMOR_TOUGHNESS))
                ),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                        "  Melee: damage modifier "
                                + formatSigned(state.meleeDamageModifier())
                                + " | actual attack damage "
                                + formatDecimal(player.getAttributeValue(Attributes.ATTACK_DAMAGE))
                                + " | speed modifier "
                                + formatSigned(state.meleeAttackSpeedModifier())
                                + " | actual attack speed "
                                + formatDecimal(player.getAttributeValue(Attributes.ATTACK_SPEED))
                ),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                        "  Attack knockback: +"
                                + formatDecimal(state.attackKnockback())
                                + " | actual "
                                + formatDecimal(player.getAttributeValue(Attributes.ATTACK_KNOCKBACK))
                                + " | Mining Speed: +"
                                + formatPercent(state.miningSpeedFraction())
                                + " | block-break multiplier "
                                + formatDecimal(player.getAttributeValue(Attributes.BLOCK_BREAK_SPEED))
                ),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                        "  Max Health: +"
                                + formatDecimal(state.maxHealthPoints() / 2.0)
                                + " hearts | actual "
                                + formatDecimal(player.getMaxHealth() / 2.0)
                                + " hearts | Movement Speed: +"
                                + formatPercent(state.movementSpeedFraction())
                                + " | actual "
                                + formatDecimal(player.getAttributeValue(Attributes.MOVEMENT_SPEED))
                ),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                        "  Knockback Resistance: +"
                                + formatPercent(state.knockbackResistance())
                                + " | Sneak Speed: +"
                                + formatPercent(state.sneakSpeedFraction())
                                + " | Step Height: +"
                                + formatDecimal(state.stepHeightBlocks())
                                + " blocks"
                ),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                        "  Reach: +"
                                + formatDecimal(state.reachBlocks())
                                + " blocks | actual block/entity range "
                                + formatDecimal(player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE))
                                + "/"
                                + formatDecimal(player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE))
                ),
                false
        );

        return 1;
    }

    private static int showDamage(
            CommandSourceStack source
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();

        EquipmentDamageService.DamageStatState stats =
                EquipmentDamageService.evaluateStats(player);

        source.sendSuccess(
                () -> Component.literal(
                        "Ascendance Damage Debug:"
                ),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                        "  Resistances: melee "
                                + formatDecimal(stats.meleeResistancePercent()) + "%"
                                + " | ranged "
                                + formatDecimal(stats.rangedResistancePercent()) + "%"
                                + " | magic "
                                + formatDecimal(stats.magicResistancePercent()) + "%"
                ),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                        "  Environment: fall "
                                + formatDecimal(stats.fallResistancePercent()) + "%"
                                + " | fire "
                                + formatDecimal(stats.fireResistancePercent()) + "%"
                                + " | explosion "
                                + formatDecimal(stats.explosionResistancePercent()) + "%"
                ),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                        "  Damage Reflection: "
                                + formatDecimal(stats.damageReflectionPercent()) + "%"
                ),
                false
        );

        EquipmentDamageService.lastDamage(player).ifPresentOrElse(
                evaluation -> {
                    source.sendSuccess(
                            () -> Component.literal(
                                    "  Last hit: " + evaluation.category()
                                            + " | incoming "
                                            + damageValue(evaluation.incomingDamage())
                                            + " -> after Ascendance resistance "
                                            + damageValue(evaluation.resolvedIncomingDamage())
                                            + " ("
                                            + formatDecimal(evaluation.resistancePercent())
                                            + "% resistance)"
                            ),
                            false
                    );

                    source.sendSuccess(
                            () -> Component.literal(
                                    "  Last result: actual health lost "
                                            + damageValue(evaluation.actualHealthDamage())
                                            + " | reflected "
                                            + damageValue(evaluation.reflectedDamage())
                                            + " @ "
                                            + formatDecimal(evaluation.reflectionPercent())
                                            + "%"
                            ),
                            false
                    );
                },
                () -> source.sendSuccess(
                        () -> Component.literal(
                                "  Last hit: NONE RECORDED"
                        ),
                        false
                )
        );

        return 1;
    }

    private static String damageValue(float value) {
        if (value < 0.0F) {
            return "N/A";
        }
        return formatDecimal(value);
    }

    private static int showVitality(
            CommandSourceStack source
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();

        EquipmentVitalityService.VitalityRuntimeSnapshot snapshot =
                EquipmentVitalityService.runtimeSnapshot(player);

        EquipmentVitalityService.VitalityStatState stats =
                snapshot.stats();

        source.sendSuccess(
                () -> Component.literal(
                        "Ascendance Vitality Debug:"
                ),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                        "  Health Regeneration: "
                                + formatDecimal(
                                stats.healthRegenerationHeartsPerSecond()
                        )
                                + " hearts/sec | last tick restored "
                                + formatDecimal(
                                snapshot.lastPassiveRegenHealthPoints()
                        )
                                + " health points"
                ),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                        "  Healing Effectiveness: +"
                                + formatDecimal(
                                stats.healingEffectivenessPercent()
                        )
                                + "% | Health "
                                + formatDecimal(snapshot.health())
                                + "/"
                                + formatDecimal(snapshot.maxHealth())
                ),
                false
        );

        EquipmentVitalityService.lastHealing(player).ifPresentOrElse(
                healing -> source.sendSuccess(
                        () -> Component.literal(
                                "  Last heal: "
                                        + formatDecimal(
                                        healing.requestedHealing()
                                )
                                        + " -> "
                                        + formatDecimal(
                                        healing.resolvedHealing()
                                )
                                        + " | bypass "
                                        + healing.bypassReason()
                        ),
                        false
                ),
                () -> source.sendSuccess(
                        () -> Component.literal(
                                "  Last heal: NONE RECORDED"
                        ),
                        false
                )
        );

        source.sendSuccess(
                () -> Component.literal(
                        "  Hunger Efficiency: "
                                + formatDecimal(
                                stats.hungerEfficiencyPercent()
                        )
                                + "% | food/saturation/exhaustion "
                                + snapshot.foodLevel()
                                + "/"
                                + formatDecimal(snapshot.saturationLevel())
                                + "/"
                                + formatDecimal(snapshot.exhaustionLevel())
                ),
                false
        );

        EquipmentVitalityService.lastExhaustion(player).ifPresentOrElse(
                exhaustion -> source.sendSuccess(
                        () -> Component.literal(
                                "  Last exhaustion: "
                                        + formatDecimal(
                                        exhaustion.requestedExhaustion()
                                )
                                        + " -> "
                                        + formatDecimal(
                                        exhaustion.resolvedExhaustion()
                                )
                                        + " ("
                                        + formatDecimal(
                                        exhaustion.hungerEfficiencyPercent()
                                )
                                        + "% reduced)"
                        ),
                        false
                ),
                () -> source.sendSuccess(
                        () -> Component.literal(
                                "  Last exhaustion: NONE RECORDED"
                        ),
                        false
                )
        );

        source.sendSuccess(
                () -> Component.literal(
                        "  Breath Hold: +"
                                + formatDecimal(stats.breathHoldSeconds())
                                + " sec | estimated total "
                                + formatDecimal(
                                snapshot.estimatedTotalBreathSeconds()
                        )
                                + " sec | air "
                                + snapshot.airSupply()
                                + "/"
                                + snapshot.maxAirSupply()
                                + " | last refund "
                                + snapshot.lastAirRefund()
                ),
                false
        );

        double statusDurationMultiplier = Math.max(
                0.0,
                1.0 - stats.statusResistancePercent() / 100.0
        );

        source.sendSuccess(
                () -> Component.literal(
                        "  Status Resistance: "
                                + formatDecimal(
                                stats.statusResistancePercent()
                        )
                                + "% | harmful duration x"
                                + formatDecimal(statusDurationMultiplier)
                ),
                false
        );

        EquipmentVitalityService.lastStatusEffect(player).ifPresentOrElse(
                effect -> source.sendSuccess(
                        () -> Component.literal(
                                "  Last harmful effect: "
                                        + effect.effectDescriptionId()
                                        + " | "
                                        + formatDecimal(
                                        effect.originalDurationTicks() / 20.0
                                )
                                        + " sec -> "
                                        + formatDecimal(
                                        effect.resolvedDurationTicks() / 20.0
                                )
                                        + " sec"
                        ),
                        false
                ),
                () -> source.sendSuccess(
                        () -> Component.literal(
                                "  Last harmful effect: NONE RECORDED"
                        ),
                        false
                )
        );

        return 1;
    }

    private static int showTool(
            CommandSourceStack source
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ItemStack stack = player.getMainHandItem();

        if (stack.isEmpty()
                || !(stack.getItem() instanceof EquipmentProfileItem profileItem)) {
            source.sendFailure(
                    Component.literal(
                            "Main hand is not a first-party Ascendance equipment item."
                    )
            );
            return 0;
        }

        EquipmentProfileDefinition profile = EquipmentProfileRegistry
                .get(profileItem.equipmentProfileId())
                .orElse(null);

        if (profile == null
                || profile.baselineMultiplier(EquipmentBaselineProperty.MINING_SPEED) <= 0.0
                || profile.statStrength(
                        EquipmentActivationType.HELD,
                        EssenceStats.MINING_SPEED
                ) <= 0.0) {
            source.sendFailure(
                    Component.literal(
                            "Main-hand Ascendance item is not a mining tool profile."
                    )
            );
            return 0;
        }

        PlayerEssenceData playerData = EssenceSavedData
                .get(player.server)
                .getPlayerData(player.getUUID());

        EquipmentBaselineResult baseline = EquipmentBaselineService.evaluate(
                playerData,
                profile.id()
        );

        double applicability = profile.statStrength(
                EquipmentActivationType.HELD,
                EssenceStats.MINING_SPEED
        );

        StatScalingResult miningScaling = StatScalingService.evaluate(
                playerData,
                EssenceStats.MINING_SPEED
        );

        double resolvedMiningSpeed = EquipmentValueService.applyPercentBonus(
                playerData,
                EssenceStats.MINING_SPEED,
                applicability,
                baseline.miningSpeed()
        );

        source.sendSuccess(
                () -> Component.literal(
                        "Ascendance Tool Debug: " + stack.getHoverName().getString()
                ),
                false
        );
        source.sendSuccess(
                () -> Component.literal(
                        "  Profile: " + profile.displayName() + " [" + profile.id() + "]"
                                + " | Tier: " + playerData.getTier().displayName()
                ),
                false
        );
        source.sendSuccess(
                () -> Component.literal(
                        "  Harvest level: " + baseline.harvestLevel()
                                + " | Base mining speed: " + formatDecimal(baseline.miningSpeed())
                ),
                false
        );
        source.sendSuccess(
                () -> Component.literal(
                        "  Mining Speed investment bonus: "
                                + formatBonus(
                                EssenceStats.MINING_SPEED,
                                miningScaling.scaledBonus()
                        )
                                + " @ " + formatStrength(applicability)
                                + " applicability | Resolved speed: "
                                + formatDecimal(resolvedMiningSpeed)
                ),
                false
        );
        source.sendSuccess(
                () -> Component.literal(
                        "  Melee archetype baseline: damage "
                                + formatDecimal(baseline.meleeDamage())
                                + ", attack speed "
                                + formatDecimal(baseline.meleeAttackSpeed())
                ),
                false
        );

        return 1;
    }

    private static int showStatProfile(
            CommandSourceStack source,
            String statName
    ) throws CommandSyntaxException {
        StatDefinition stat = resolveStat(statName);

        source.sendSuccess(
                () -> Component.literal(
                        "Built-in equipment applicability for " + stat.displayName() + ":"
                ),
                false
        );

        boolean found = false;
        for (EquipmentProfileDefinition profile : EquipmentProfileRegistry.values()) {
            for (EquipmentActivationType activation : profile.activationTypes()) {
                double strength = profile.statStrength(activation, stat);
                if (strength <= 0.0) {
                    continue;
                }

                found = true;
                source.sendSuccess(
                        () -> Component.literal(
                                "  " + profile.displayName()
                                        + " [" + profile.id() + "]: "
                                        + formatStrength(strength)
                                        + " when " + activation
                        ),
                        false
                );
            }
        }

        if (!found) {
            source.sendSuccess(
                    () -> Component.literal("  No built-in profile uses this stat."),
                    false
            );
        }

        return 1;
    }

    private static int showBaselines(
            CommandSourceStack source
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();

        source.sendSuccess(
                () -> Component.literal(
                        "Tier/archetype equipment baselines for "
                                + EssenceSavedData.get(player.server)
                                .getPlayerData(player.getUUID())
                                .getTier()
                                .displayName()
                                + ":"
                ),
                false
        );

        for (EquipmentProfileDefinition profile : EquipmentProfileRegistry.values()) {
            EquipmentBaselineResult baseline = EquipmentBaselineService.evaluate(
                    player,
                    profile.id()
            );

            StringBuilder values = new StringBuilder();
            for (EquipmentBaselineProperty property : EquipmentBaselineProperty.values()) {
                if (profile.baselineMultiplier(property) <= 0.0) {
                    continue;
                }
                if (values.length() > 0) {
                    values.append(", ");
                }
                values.append(property.name().toLowerCase(Locale.ROOT))
                        .append('=')
                        .append(formatDecimal(baseline.value(property)));
            }

            source.sendSuccess(
                    () -> Component.literal(
                            "  " + profile.displayName() + " [" + profile.id() + "]: " + values
                    ),
                    false
            );
        }

        source.sendSuccess(
                () -> Component.literal(
                        "  Tier harvest capability level: "
                                + EssenceConfigManager.get()
                                .equipmentBaselineConfig()
                                .baselineFor(
                                        EssenceSavedData.get(player.server)
                                                .getPlayerData(player.getUUID())
                                                .getTier()
                                )
                                .harvestLevel()
                ),
                false
        );
        source.sendSuccess(
                () -> Component.literal(
                        "  Armor slot weights: HEAD " + formatStrength(ArmorStatWeights.weightFor(EquipmentSlot.HEAD))
                                + ", CHEST " + formatStrength(ArmorStatWeights.weightFor(EquipmentSlot.CHEST))
                                + ", LEGS " + formatStrength(ArmorStatWeights.weightFor(EquipmentSlot.LEGS))
                                + ", FEET " + formatStrength(ArmorStatWeights.weightFor(EquipmentSlot.FEET))
                ),
                false
        );

        return 1;
    }

    private static MilestoneProgress evaluateMilestone(
            ServerPlayer player,
            MilestoneDefinition milestone
    ) {
        return MilestoneService.evaluate(
                player,
                MilestoneRequirement.milestone(milestone.id())
        );
    }

    private static StatDefinition resolveStat(
            String input
    ) throws CommandSyntaxException {
        ResourceLocation id = parseId(input);
        if (id == null) {
            throw UNKNOWN_STAT.create(input);
        }
        return EssenceStatRegistry
                .get(id)
                .orElseThrow(() -> UNKNOWN_STAT.create(input));
    }

    private static StatCategory resolveCategory(
            String input
    ) throws CommandSyntaxException {
        try {
            return StatCategory.valueOf(input.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw UNKNOWN_CATEGORY.create(input);
        }
    }

    private static MilestoneDefinition resolveMilestone(
            String input
    ) throws CommandSyntaxException {
        ResourceLocation id = parseId(input);
        if (id == null) {
            throw UNKNOWN_MILESTONE.create(input);
        }
        return EssenceConfigManager
                .get()
                .getMilestone(id)
                .orElseThrow(() -> UNKNOWN_MILESTONE.create(input));
    }

    private static ResourceLocation parseId(String input) {
        String fullId = input.contains(":")
                ? input
                : EssenceAscendance.MOD_ID + ":" + input;
        return ResourceLocation.tryParse(fullId);
    }

    private static CompletableFuture<Suggestions> suggestStats(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder
    ) {
        String remaining = builder.getRemainingLowerCase();
        for (StatDefinition stat : EssenceStatRegistry.values()) {
            String name = stat.id().getPath();
            if (name.startsWith(remaining)) {
                builder.suggest(name);
            }
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestCategories(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder
    ) {
        String remaining = builder.getRemainingLowerCase();
        for (StatCategory category : StatCategory.values()) {
            String name = category.name().toLowerCase(Locale.ROOT);
            if (name.startsWith(remaining)) {
                builder.suggest(name);
            }
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestMilestones(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder
    ) {
        String remaining = builder.getRemainingLowerCase();
        for (MilestoneDefinition milestone : EssenceConfigManager.get().milestones().values()) {
            ResourceLocation id = milestone.id();
            String suggestion = id.getNamespace().equals(EssenceAscendance.MOD_ID)
                    ? id.getPath()
                    : "\"" + id + "\"";

            if (suggestion.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                builder.suggest(suggestion);
            }
        }
        return builder.buildFuture();
    }

    private static void appendStrengths(
            StringBuilder builder,
            Map<ResourceLocation, Double> strengths
    ) {
        if (strengths.isEmpty()) {
            builder.append("NONE");
            return;
        }

        boolean first = true;
        for (Map.Entry<ResourceLocation, Double> entry : strengths.entrySet()) {
            if (!first) {
                builder.append(", ");
            }
            first = false;
            builder.append(entry.getKey().getPath())
                    .append(' ')
                    .append(formatStrength(entry.getValue()));
        }
    }

    private static String milestoneState(MilestoneProgress progress) {
        if (!progress.resolvable()) {
            return "UNRESOLVED";
        }
        return progress.complete() ? "COMPLETE" : "INCOMPLETE";
    }

    private static String formatSigned(double value) {
        return String.format(Locale.ROOT, "%+.2f", value);
    }

    private static String formatStrength(double value) {
        return String.format(Locale.ROOT, "%.2fx", value);
    }

    private static String formatPercent(double value) {
        return String.format(Locale.ROOT, "%.2f%%", value * 100.0);
    }

    private static String formatBonus(
            StatDefinition stat,
            double value
    ) {
        return switch (stat.unit()) {
            case PERCENT -> String.format(Locale.ROOT, "%.2f%%", value);
            case HEARTS -> String.format(Locale.ROOT, "%.2f hearts", value);
            case HEARTS_PER_SECOND -> String.format(Locale.ROOT, "%.3f hearts/sec", value);
            case BLOCKS -> String.format(Locale.ROOT, "%.2f blocks", value);
            case SECONDS -> String.format(Locale.ROOT, "%.2f seconds", value);
            case LEVELS -> String.format(Locale.ROOT, "%.2f levels", value);
            case FLAT -> String.format(Locale.ROOT, "%.3f", value);
        };
    }

    private static String format(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    private static String formatDecimal(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
