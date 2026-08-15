package com.mistaboom.essence_ascendance.command;

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
import com.mistaboom.essence_ascendance.equipment.EquipmentGatheringService;
import com.mistaboom.essence_ascendance.equipment.EquipmentMobilityService;
import com.mistaboom.essence_ascendance.equipment.EquipmentProfileDefinition;
import com.mistaboom.essence_ascendance.equipment.EquipmentProfileItem;
import com.mistaboom.essence_ascendance.equipment.EquipmentProfileRegistry;
import com.mistaboom.essence_ascendance.equipment.EquipmentProfiles;
import com.mistaboom.essence_ascendance.equipment.EquipmentStatProfile;
import com.mistaboom.essence_ascendance.equipment.EquipmentStatProviderRegistry;
import com.mistaboom.essence_ascendance.equipment.EquipmentStatResolver;
import com.mistaboom.essence_ascendance.equipment.EquipmentStatState;
import com.mistaboom.essence_ascendance.equipment.EquipmentValueService;
import com.mistaboom.essence_ascendance.equipment.EquipmentVitalityService;
import com.mistaboom.essence_ascendance.equipment.EquipmentWeaponService;
import com.mistaboom.essence_ascendance.mapping.ItemEssenceMappingDefinition;
import com.mistaboom.essence_ascendance.mapping.ItemEssenceMappingRegistry;
import com.mistaboom.essence_ascendance.mapping.ItemEssenceMappingResult;
import com.mistaboom.essence_ascendance.progression.AscendanceEngine;
import com.mistaboom.essence_ascendance.progression.AscendanceEvaluationResult;
import com.mistaboom.essence_ascendance.progression.CategoryDevelopment;
import com.mistaboom.essence_ascendance.progression.StatInvestmentLimit;
import com.mistaboom.essence_ascendance.progression.StatScalingResult;
import com.mistaboom.essence_ascendance.progression.StatScalingService;
import com.mistaboom.essence_ascendance.progression.TierInvestmentPolicy;
import com.mistaboom.essence_ascendance.stat.EssenceStatRegistry;
import com.mistaboom.essence_ascendance.stat.EssenceStats;
import com.mistaboom.essence_ascendance.stat.StatCategory;
import com.mistaboom.essence_ascendance.stat.StatDefinition;
import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;
import java.util.Map;

final class EssenceDebugCommands {

    private EssenceDebugCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("debug")
                .requires(source -> source.hasPermission(EssenceCommandUtil.ADMIN_PERMISSION))
                .executes(context -> showSummary(context.getSource()))
                .then(Commands.literal("help").executes(context -> showHelp(context.getSource())))
                .then(Commands.literal("summary").executes(context -> showSummary(context.getSource())))
                .then(Commands.literal("equipment").executes(context -> showEquipment(context.getSource())))
                .then(Commands.literal("item").executes(context -> showItem(context.getSource())))
                .then(Commands.literal("mapping").executes(context -> showItemMapping(context.getSource())))
                .then(Commands.literal("mappings").executes(context -> showMappingRegistry(context.getSource())))
                .then(Commands.literal("baselines").executes(context -> showBaselines(context.getSource())))
                .then(Commands.literal("offense").executes(context -> showOffense(context.getSource())))
                .then(Commands.literal("defense").executes(context -> showDefense(context.getSource())))
                .then(Commands.literal("vitality").executes(context -> showVitality(context.getSource())))
                .then(Commands.literal("mobility").executes(context -> showMobility(context.getSource())))
                .then(Commands.literal("gathering").executes(context -> showGathering(context.getSource())))
                .then(Commands.literal("utility").executes(context -> showUtility(context.getSource())));
    }

    static int showHelp(CommandSourceStack source) {
        EssenceCommandUtil.send(source, EssenceCommandUtil.title("Essence Debug Commands"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence debug summary", "system/player diagnostic overview"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence debug equipment", "all active equipment profiles plus resolved stat applicability"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence debug item", "deep-dive the main-hand Ascendance item"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence debug mapping", "resolve the main-hand item to Attribute Essence"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence debug mappings", "mapping registry/reload summary"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence debug baselines", "tier/archetype equipment baselines"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.section("Gameplay categories"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence debug offense", "melee, ranged, magic, knockback, reflection"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence debug defense", "all resistance stats and last incoming-damage/status events"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence debug vitality", "health, regeneration, healing, hunger, breath"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence debug mobility", "movement, swimming, jumping, stepping, flight"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence debug gathering", "mining, Fortune, Looting, reach, XP gain"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence debug utility", "Luck, sneak speed, durability efficiency"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.muted("Stat investment/scaling/applicability is intentionally centralized at /essence stat <stat>."));
        return 1;
    }

    private static int showSummary(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PlayerEssenceData data = playerData(player);

        long stored = 0L;
        long effective = 0L;
        long capacity = 0L;
        for (StatDefinition stat : EssenceStatRegistry.values()) {
            StatInvestmentLimit limit = TierInvestmentPolicy.evaluate(data, stat);
            stored = Math.addExact(stored, limit.storedInvestment());
            effective = Math.addExact(effective, limit.effectiveInvestment());
            capacity = Math.addExact(capacity, limit.investmentCap());
        }

        EssenceServerConfig config = EssenceConfigManager.get();
        EssenceCommandUtil.send(source, EssenceCommandUtil.title("Essence Debug Summary"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line("Player", player.getGameProfile().getName()));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line("Tier", data.getTier().displayName()));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Investment",
                EssenceCommandUtil.format(effective) + " / " + EssenceCommandUtil.format(capacity)
                        + " effective (" + EssenceCommandUtil.format(stored) + " stored)"
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Registries",
                EssenceStatRegistry.size() + " stats, "
                        + EquipmentProfileRegistry.size() + " equipment profiles, "
                        + EquipmentStatProviderRegistry.providers().size() + " providers"
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Balance profile",
                config.balanceProfile().displayName() + " [" + config.balanceProfile().id() + "]"
        ));

        AscendanceEvaluationResult evaluation = AscendanceEngine.evaluate(player);
        switch (evaluation.status()) {
            case AVAILABLE -> EssenceCommandUtil.send(
                    source,
                    EssenceCommandUtil.line(
                            "Next tier",
                            Component.literal(evaluation.nextTier().displayName() + " - ")
                                    .withStyle(ChatFormatting.WHITE)
                                    .append(evaluation.progress().readyToAscend()
                                            ? EssenceCommandUtil.good("READY")
                                            : EssenceCommandUtil.warn("NOT READY"))
                    )
            );
            case MAX_TIER -> EssenceCommandUtil.send(source, EssenceCommandUtil.line("Next tier", EssenceCommandUtil.good("MAX TIER")));
            case CONFIGURATION_ERROR -> EssenceCommandUtil.send(source, EssenceCommandUtil.line("Next tier", EssenceCommandUtil.bad("CONFIGURATION ERROR")));
        }

        return 1;
    }

    private static int showEquipment(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        EssenceCommandUtil.send(source, EssenceCommandUtil.title("Active Equipment"));

        showItemProfile(source, "Main hand", player.getItemInHand(InteractionHand.MAIN_HAND));
        showItemProfile(source, "Off hand", player.getItemInHand(InteractionHand.OFF_HAND));
        for (EquipmentSlot slot : EquipmentStatResolver.armorSlots()) {
            showItemProfile(source, slot.getName(), player.getItemBySlot(slot));
        }

        EquipmentStatState active = EquipmentStatResolver.evaluate(player);
        EssenceCommandUtil.send(source, EssenceCommandUtil.section("Resolved active stat applicability"));
        if (active.values().isEmpty()) {
            EssenceCommandUtil.send(source, EssenceCommandUtil.muted("  NONE"));
            return 1;
        }

        for (StatCategory category : StatCategory.values()) {
            boolean headingShown = false;
            for (StatDefinition stat : EssenceStatRegistry.values()) {
                if (stat.category() != category) {
                    continue;
                }
                double strength = active.strength(stat);
                if (strength <= 0.0) {
                    continue;
                }
                if (!headingShown) {
                    EssenceCommandUtil.send(
                            source,
                            Component.literal(EssenceCommandUtil.categoryName(category))
                                    .withStyle(EssenceCommandUtil.categoryColor(category), ChatFormatting.BOLD)
                    );
                    headingShown = true;
                }
                EssenceCommandUtil.send(source, EssenceCommandUtil.line(stat.displayName(), EssenceCommandUtil.formatStrength(strength)));
            }
        }
        return 1;
    }

    private static void showItemProfile(CommandSourceStack source, String label, ItemStack stack) {
        if (stack.isEmpty()) {
            EssenceCommandUtil.send(source, EssenceCommandUtil.line(label, EssenceCommandUtil.muted("EMPTY")));
            return;
        }

        EquipmentStatProfile statProfile = EquipmentStatResolver.inspectItem(stack);
        StringBuilder details = new StringBuilder();
        for (EquipmentActivationType activation : statProfile.activations()) {
            if (details.length() > 0) {
                details.append(" | ");
            }
            details.append(activation.name().toLowerCase(Locale.ROOT)).append(": ");
            appendStrengths(details, statProfile.strengths(activation));
        }
        if (details.length() == 0) {
            details.append("no Essence stat profile");
        }

        EssenceCommandUtil.send(
                source,
                EssenceCommandUtil.line(
                        label,
                        stack.getHoverName().getString() + " -> " + details
                )
        );
    }

    private static int showItem(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ItemStack stack = player.getMainHandItem();

        if (stack.isEmpty() || !(stack.getItem() instanceof EquipmentProfileItem profileItem)) {
            EssenceCommandUtil.fail(source, "Main hand is not a first-party Ascendance equipment item.");
            return 0;
        }

        EquipmentProfileDefinition profile = EquipmentProfileRegistry
                .get(profileItem.equipmentProfileId())
                .orElse(null);
        if (profile == null) {
            EssenceCommandUtil.fail(source, "Main-hand item references an unknown equipment profile: " + profileItem.equipmentProfileId());
            return 0;
        }

        PlayerEssenceData data = playerData(player);
        EquipmentBaselineResult baseline = EquipmentBaselineService.evaluate(data, profile.id());
        EquipmentStatProfile statProfile = EquipmentStatResolver.inspectItem(stack);

        EssenceCommandUtil.send(source, EssenceCommandUtil.title("Item Debug - " + stack.getHoverName().getString()));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line("Profile", profile.displayName() + " [" + profile.id() + "]"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line("Tier", data.getTier().displayName()));

        EssenceCommandUtil.send(source, EssenceCommandUtil.section("Baseline"));
        boolean anyBaseline = false;
        for (EquipmentBaselineProperty property : EquipmentBaselineProperty.values()) {
            if (profile.baselineMultiplier(property) <= 0.0) {
                continue;
            }
            anyBaseline = true;
            EssenceCommandUtil.send(
                    source,
                    EssenceCommandUtil.line(
                            property.name().toLowerCase(Locale.ROOT),
                            EssenceCommandUtil.formatDecimal(baseline.value(property))
                    )
            );
        }
        if (!anyBaseline) {
            EssenceCommandUtil.send(source, EssenceCommandUtil.muted("  No physical baseline properties."));
        }

        EssenceCommandUtil.send(source, EssenceCommandUtil.section("Item stat applicability"));
        if (statProfile.activations().isEmpty()) {
            EssenceCommandUtil.send(source, EssenceCommandUtil.muted("  NONE"));
        } else {
            for (EquipmentActivationType activation : statProfile.activations()) {
                StringBuilder strengths = new StringBuilder();
                appendStrengths(strengths, statProfile.strengths(activation));
                EssenceCommandUtil.send(
                        source,
                        EssenceCommandUtil.line(activation.name().toLowerCase(Locale.ROOT), strengths.toString())
                );
            }
        }

        EquipmentAttributeService.sync(player);
        EquipmentAttributeService.AppliedState attributes = EquipmentAttributeService.evaluate(player);

        if (profile.baselineMultiplier(EquipmentBaselineProperty.MELEE_DAMAGE) > 0.0) {
            EssenceCommandUtil.send(source, EssenceCommandUtil.section("Melee runtime"));
            EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                    "Baseline damage / speed",
                    EssenceCommandUtil.formatDecimal(baseline.meleeDamage()) + " / "
                            + EssenceCommandUtil.formatDecimal(baseline.meleeAttackSpeed())
            ));
            EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                    "Actual player damage / speed",
                    EssenceCommandUtil.formatDecimal(player.getAttributeValue(Attributes.ATTACK_DAMAGE)) + " / "
                            + EssenceCommandUtil.formatDecimal(player.getAttributeValue(Attributes.ATTACK_SPEED))
            ));
            EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                    "Ascendance attack knockback",
                    EssenceCommandUtil.formatDecimal(attributes.attackKnockback())
            ));
        }

        if (profile.baselineMultiplier(EquipmentBaselineProperty.MINING_SPEED) > 0.0) {
            double applicability = profile.statStrength(EquipmentActivationType.HELD, EssenceStats.MINING_SPEED);
            double resolved = EquipmentValueService.applyPercentBonus(
                    data,
                    EssenceStats.MINING_SPEED,
                    applicability,
                    baseline.miningSpeed()
            );
            EssenceCommandUtil.send(source, EssenceCommandUtil.section("Tool runtime"));
            EssenceCommandUtil.send(source, EssenceCommandUtil.line("Harvest level", Integer.toString(baseline.harvestLevel())));
            EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                    "Mining speed",
                    EssenceCommandUtil.formatDecimal(baseline.miningSpeed()) + " -> "
                            + EssenceCommandUtil.formatDecimal(resolved)
            ));
        }

        if (profile.id().equals(EquipmentProfiles.RANGED_WEAPON.id())) {
            showRangedRuntime(source, player, stack);
        }

        if (profile.id().equals(EquipmentProfiles.MAGIC_FOCUS.id())) {
            showMagicRuntime(source, player, stack);
        }

        return 1;
    }

    private static void showRangedRuntime(CommandSourceStack source, ServerPlayer player, ItemStack stack) {
        EquipmentWeaponService.RangedState state = EquipmentWeaponService.evaluateRanged(player, stack);
        EssenceCommandUtil.send(source, EssenceCommandUtil.section("Ranged runtime"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Damage",
                EssenceCommandUtil.formatDecimal(state.baselineDamage()) + " -> " + EssenceCommandUtil.formatDecimal(state.finalDamage())
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Draw rate",
                EssenceCommandUtil.formatDecimal(state.baselineAttackSpeed()) + "/sec -> "
                        + EssenceCommandUtil.formatDecimal(state.finalAttackSpeed()) + "/sec ("
                        + state.fullDrawTicks() + " ticks)"
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Projectile speed",
                "+" + EssenceCommandUtil.formatDecimal(state.projectileSpeedPercent()) + "% | velocity x"
                        + EssenceCommandUtil.formatDecimal(state.projectileSpeedMultiplier())
        ));

        EquipmentWeaponService.lastRangedShot(player).ifPresentOrElse(
                shot -> EssenceCommandUtil.send(
                        source,
                        EssenceCommandUtil.line(
                                "Last shot",
                                "held " + shot.actualUseTicks() + " ticks -> vanilla-equivalent "
                                        + shot.syntheticUseTicks() + " | velocity "
                                        + EssenceCommandUtil.formatDecimal(shot.vanillaVelocity()) + " -> "
                                        + EssenceCommandUtil.formatDecimal(shot.resolvedVelocity())
                                        + " | arrow base damage "
                                        + (shot.resolvedArrowBaseDamage() < 0.0
                                        ? "N/A"
                                        : EssenceCommandUtil.formatDecimal(shot.resolvedArrowBaseDamage()))
                        )
                ),
                () -> EssenceCommandUtil.send(source, EssenceCommandUtil.muted("  Last shot: NONE RECORDED"))
        );
    }

    private static void showMagicRuntime(CommandSourceStack source, ServerPlayer player, ItemStack stack) {
        EquipmentWeaponService.MagicState state = EquipmentWeaponService.evaluateMagic(player, stack);
        EssenceCommandUtil.send(source, EssenceCommandUtil.section("Magic runtime"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Damage",
                EssenceCommandUtil.formatDecimal(state.baselineDamage()) + " -> " + EssenceCommandUtil.formatDecimal(state.finalDamage())
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Cast rate",
                EssenceCommandUtil.formatDecimal(state.baselineCastSpeed()) + "/sec -> "
                        + EssenceCommandUtil.formatDecimal(state.finalCastSpeed()) + "/sec ("
                        + state.castTicks() + " tick cooldown)"
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Neutral range",
                EssenceCommandUtil.formatDecimal(EquipmentWeaponService.MAGIC_RANGE_BLOCKS) + " blocks"
        ));

        EquipmentWeaponService.lastMagicCast(player).ifPresentOrElse(
                cast -> EssenceCommandUtil.send(
                        source,
                        EssenceCommandUtil.line(
                                "Last cast",
                                (cast.castPerformed() ? "CAST" : "BLOCKED")
                                        + " | target " + cast.targetName()
                                        + " | distance " + EssenceCommandUtil.formatDecimal(cast.targetDistance())
                                        + " | attempted damage " + EssenceCommandUtil.formatDecimal(cast.attemptedDamage())
                                        + " | applied " + (cast.damageApplied() ? "YES" : "NO")
                        )
                ),
                () -> EssenceCommandUtil.send(source, EssenceCommandUtil.muted("  Last cast: NONE RECORDED"))
        );
    }


    private static int showItemMapping(
            CommandSourceStack source
    ) throws CommandSyntaxException {
        ServerPlayer player =
                source.getPlayerOrException();

        ItemStack stack =
                player.getMainHandItem();

        EssenceCommandUtil.send(
                source,
                EssenceCommandUtil.title(
                        "Item → Essence Mapping"
                )
        );

        if (stack.isEmpty()) {
            EssenceCommandUtil.send(
                    source,
                    EssenceCommandUtil.muted(
                            "Hold an item in your main hand."
                    )
            );

            return 0;
        }

        ResourceLocation itemId =
                BuiltInRegistries.ITEM.getKey(
                        stack.getItem()
                );

        ItemEssenceMappingResult result =
                ItemEssenceMappingRegistry.resolve(
                        stack
                );

        EssenceCommandUtil.send(
                source,
                EssenceCommandUtil.line(
                        "Item",
                        stack.getHoverName()
                                .getString()
                                + " ["
                                + itemId
                                + "]"
                )
        );

        if (!result.mapped()) {
            EssenceCommandUtil.send(
                    source,
                    EssenceCommandUtil.line(
                            "Mapping",
                            EssenceCommandUtil.warn(
                                    "NONE"
                            )
                    )
            );

            return 1;
        }

        EssenceCommandUtil.send(
                source,
                EssenceCommandUtil.line(
                        "Matched rules",
                        Integer.toString(
                                result.matchedMappings()
                                        .size()
                        )
                )
        );

        EssenceCommandUtil.send(
                source,
                EssenceCommandUtil.line(
                        "Winning priority",
                        Integer.toString(
                                result.priority()
                                        .orElseThrow()
                        )
                )
        );

        EssenceCommandUtil.send(
                source,
                EssenceCommandUtil.section(
                        "Applied rules"
                )
        );

        for (ResourceLocation mappingId :
                result.appliedMappings()) {

            ItemEssenceMappingDefinition definition =
                    ItemEssenceMappingRegistry
                            .definitions()
                            .stream()
                            .filter(
                                    candidate ->
                                            candidate.id()
                                                    .equals(
                                                            mappingId
                                                    )
                            )
                            .findFirst()
                            .orElse(
                                    null
                            );

            String detail =
                    definition == null
                            ? mappingId.toString()
                            : mappingId
                                    + " | "
                                    + definition.selectorDisplay();

            EssenceCommandUtil.send(
                    source,
                    EssenceCommandUtil.muted(
                            "  "
                                    + detail
                    )
            );
        }

        EssenceCommandUtil.send(
                source,
                EssenceCommandUtil.section(
                        "Resolved Attribute Essence"
                )
        );

        if (result.outputs()
                .isEmpty()) {

            EssenceCommandUtil.send(
                    source,
                    EssenceCommandUtil.muted(
                            "  BLOCKED / no outputs at winning priority"
                    )
            );

            return 1;
        }

        result.outputs()
                .entrySet()
                .stream()
                .sorted(
                        Map.Entry.comparingByKey(
                                java.util.Comparator.comparing(
                                        essence ->
                                                essence.id()
                                                        .toString()
                                )
                        )
                )
                .forEach(
                        entry ->
                                EssenceCommandUtil.send(
                                        source,
                                        EssenceCommandUtil.line(
                                                entry.getKey()
                                                        .displayName(),
                                                EssenceCommandUtil.format(
                                                        entry.getValue()
                                                )
                                        )
                                )
                );

        if (result.matchedMappings()
                .size()
                > result.appliedMappings()
                        .size()) {

            EssenceCommandUtil.send(
                    source,
                    EssenceCommandUtil.muted(
                            "  "
                                    + (
                                    result.matchedMappings()
                                            .size()
                                            - result.appliedMappings()
                                            .size()
                            )
                                    + " lower-priority match(es) shadowed"
                    )
            );
        }

        return 1;
    }


    private static int showMappingRegistry(
            CommandSourceStack source
    ) {
        ItemEssenceMappingRegistry.ReloadReport report =
                ItemEssenceMappingRegistry.lastReload();

        EssenceCommandUtil.send(
                source,
                EssenceCommandUtil.title(
                        "Item → Essence Mapping Registry"
                )
        );

        EssenceCommandUtil.send(
                source,
                EssenceCommandUtil.line(
                        "Last reload",
                        report.successful()
                                ? EssenceCommandUtil.good(
                                        "SUCCESS"
                                )
                                : EssenceCommandUtil.bad(
                                        "REJECTED / NOT LOADED"
                                )
                )
        );

        EssenceCommandUtil.send(
                source,
                EssenceCommandUtil.line(
                        "Generation",
                        Long.toString(
                                report.generation()
                        )
                )
        );

        EssenceCommandUtil.send(
                source,
                EssenceCommandUtil.line(
                        "Bundled defaults",
                        Integer.toString(
                                report.bundledDefaultCount()
                        )
                )
        );

        EssenceCommandUtil.send(
                source,
                EssenceCommandUtil.line(
                        "Default changes",
                        report.removedDefaultCount()
                                + " removed, "
                                + report.replacedDefaultCount()
                                + " replaced"
                )
        );

        EssenceCommandUtil.send(
                source,
                EssenceCommandUtil.line(
                        "Config",
                        report.configMappingCount()
                                + " mapping(s) / "
                                + report.configFileCount()
                                + " file(s)"
                )
        );

        EssenceCommandUtil.send(
                source,
                EssenceCommandUtil.line(
                        "Active mappings",
                        report.activeMappingCount()
                                + " ("
                                + report.explicitItemRuleCount()
                                + " item, "
                                + report.tagRuleCount()
                                + " tag)"
                )
        );

        if (!report.warnings()
                .isEmpty()) {

            EssenceCommandUtil.send(
                    source,
                    EssenceCommandUtil.section(
                            "Last load warnings"
                    )
            );

            for (String warning :
                    report.warnings()) {

                EssenceCommandUtil.send(
                        source,
                        EssenceCommandUtil.warn(
                                "  "
                                        + warning
                        )
                );
            }
        }

        if (!report.errors()
                .isEmpty()) {

            EssenceCommandUtil.send(
                    source,
                    EssenceCommandUtil.section(
                            "Last reload errors"
                    )
            );

            for (String error :
                    report.errors()) {

                EssenceCommandUtil.send(
                        source,
                        EssenceCommandUtil.bad(
                                "  "
                                        + error
                        )
                );
            }
        }

        return report.successful()
                ? 1
                : 0;
    }


    private static int showBaselines(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PlayerEssenceData data = playerData(player);
        EssenceCommandUtil.send(source, EssenceCommandUtil.title("Equipment Baselines - " + data.getTier().displayName()));

        for (EquipmentProfileDefinition profile : EquipmentProfileRegistry.values()) {
            EquipmentBaselineResult baseline = EquipmentBaselineService.evaluate(player, profile.id());
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
                        .append(EssenceCommandUtil.formatDecimal(baseline.value(property)));
            }
            EssenceCommandUtil.send(source, EssenceCommandUtil.line(profile.displayName(), values.toString()));
        }

        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Tier harvest capability",
                Integer.toString(EssenceConfigManager.get()
                        .equipmentBaselineConfig()
                        .baselineFor(data.getTier())
                        .harvestLevel())
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Armor coverage weights",
                "head " + EssenceCommandUtil.formatStrength(ArmorStatWeights.weightFor(EquipmentSlot.HEAD))
                        + ", chest " + EssenceCommandUtil.formatStrength(ArmorStatWeights.weightFor(EquipmentSlot.CHEST))
                        + ", legs " + EssenceCommandUtil.formatStrength(ArmorStatWeights.weightFor(EquipmentSlot.LEGS))
                        + ", feet " + EssenceCommandUtil.formatStrength(ArmorStatWeights.weightFor(EquipmentSlot.FEET))
        ));
        return 1;
    }

    private static int showOffense(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        showCategoryHeaderAndStats(source, player, StatCategory.OFFENSE);

        EquipmentAttributeService.sync(player);
        EquipmentAttributeService.AppliedState attributes = EquipmentAttributeService.evaluate(player);
        EssenceCommandUtil.send(source, EssenceCommandUtil.section("Runtime"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Melee",
                "damage modifier " + EssenceCommandUtil.formatSigned(attributes.meleeDamageModifier())
                        + " | actual damage " + EssenceCommandUtil.formatDecimal(player.getAttributeValue(Attributes.ATTACK_DAMAGE))
                        + " | speed modifier " + EssenceCommandUtil.formatSigned(attributes.meleeAttackSpeedModifier())
                        + " | actual speed " + EssenceCommandUtil.formatDecimal(player.getAttributeValue(Attributes.ATTACK_SPEED))
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Attack knockback",
                "+" + EssenceCommandUtil.formatDecimal(attributes.attackKnockback())
                        + " | actual " + EssenceCommandUtil.formatDecimal(player.getAttributeValue(Attributes.ATTACK_KNOCKBACK))
        ));

        ItemStack held = player.getMainHandItem();
        if (usesProfile(held, EquipmentProfiles.RANGED_WEAPON.id())) {
            EquipmentWeaponService.RangedState ranged = EquipmentWeaponService.evaluateRanged(player, held);
            EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                    "Ranged",
                    "damage " + EssenceCommandUtil.formatDecimal(ranged.finalDamage())
                            + " | draw " + EssenceCommandUtil.formatDecimal(ranged.finalAttackSpeed()) + "/sec"
                            + " | projectile +" + EssenceCommandUtil.formatDecimal(ranged.projectileSpeedPercent()) + "%"
            ));
        }
        if (usesProfile(held, EquipmentProfiles.MAGIC_FOCUS.id())) {
            EquipmentWeaponService.MagicState magic = EquipmentWeaponService.evaluateMagic(player, held);
            EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                    "Magic",
                    "damage " + EssenceCommandUtil.formatDecimal(magic.finalDamage())
                            + " | cast " + EssenceCommandUtil.formatDecimal(magic.finalCastSpeed()) + "/sec"
            ));
        }

        EquipmentDamageService.DamageStatState damage = EquipmentDamageService.evaluateStats(player);
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Damage reflection",
                EssenceCommandUtil.formatDecimal(damage.damageReflectionPercent()) + "%"
        ));
        EquipmentDamageService.lastDamage(player).ifPresent(
                last -> EssenceCommandUtil.send(
                        source,
                        EssenceCommandUtil.line(
                                "Last reflected result",
                                "health lost " + damageValue(last.actualHealthDamage())
                                        + " | reflected " + damageValue(last.reflectedDamage())
                                        + " @ " + EssenceCommandUtil.formatDecimal(last.reflectionPercent()) + "%"
                        )
                )
        );
        return 1;
    }

    private static int showDefense(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        showCategoryHeaderAndStats(source, player, StatCategory.DEFENSE);

        EquipmentDamageService.DamageStatState stats = EquipmentDamageService.evaluateStats(player);
        EquipmentAttributeService.sync(player);
        EquipmentAttributeService.AppliedState attributes = EquipmentAttributeService.evaluate(player);

        EssenceCommandUtil.send(source, EssenceCommandUtil.section("Runtime"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Physical defense",
                "Ascendance armor +" + EssenceCommandUtil.formatDecimal(attributes.armor())
                        + " | actual " + EssenceCommandUtil.formatDecimal(player.getAttributeValue(Attributes.ARMOR))
                        + " | Ascendance toughness +" + EssenceCommandUtil.formatDecimal(attributes.toughness())
                        + " | actual " + EssenceCommandUtil.formatDecimal(player.getAttributeValue(Attributes.ARMOR_TOUGHNESS))
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Combat resistances",
                "melee " + EssenceCommandUtil.formatDecimal(stats.meleeResistancePercent()) + "%"
                        + " | ranged " + EssenceCommandUtil.formatDecimal(stats.rangedResistancePercent()) + "%"
                        + " | magic " + EssenceCommandUtil.formatDecimal(stats.magicResistancePercent()) + "%"
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Environmental resistances",
                "fall " + EssenceCommandUtil.formatDecimal(stats.fallResistancePercent()) + "%"
                        + " | fire " + EssenceCommandUtil.formatDecimal(stats.fireResistancePercent()) + "%"
                        + " | explosion " + EssenceCommandUtil.formatDecimal(stats.explosionResistancePercent()) + "%"
        ));

        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Knockback resistance",
                EssenceCommandUtil.formatDecimal(player.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE))
        ));

        EquipmentDamageService.lastDamage(player).ifPresentOrElse(
                last -> EssenceCommandUtil.send(
                        source,
                        EssenceCommandUtil.line(
                                "Last incoming hit",
                                last.category() + " | incoming " + damageValue(last.incomingDamage())
                                        + " -> Ascendance " + damageValue(last.resolvedIncomingDamage())
                                        + " | actual health lost " + damageValue(last.actualHealthDamage())
                        )
                ),
                () -> EssenceCommandUtil.send(source, EssenceCommandUtil.muted("  Last incoming hit: NONE RECORDED"))
        );

        EquipmentVitalityService.VitalityRuntimeSnapshot vitality = EquipmentVitalityService.runtimeSnapshot(player);
        double statusPercent = vitality.stats().statusResistancePercent();
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Status resistance",
                EssenceCommandUtil.formatDecimal(statusPercent) + "% | harmful duration x"
                        + EssenceCommandUtil.formatDecimal(Math.max(0.0, 1.0 - statusPercent / 100.0))
        ));
        EquipmentVitalityService.lastStatusEffect(player).ifPresentOrElse(
                effect -> EssenceCommandUtil.send(
                        source,
                        EssenceCommandUtil.line(
                                "Last harmful effect",
                                effect.effectDescriptionId() + " | "
                                        + EssenceCommandUtil.formatDecimal(effect.originalDurationTicks() / 20.0)
                                        + " sec -> "
                                        + EssenceCommandUtil.formatDecimal(effect.resolvedDurationTicks() / 20.0) + " sec"
                        )
                ),
                () -> EssenceCommandUtil.send(source, EssenceCommandUtil.muted("  Last harmful effect: NONE RECORDED"))
        );
        return 1;
    }

    private static int showVitality(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        showCategoryHeaderAndStats(source, player, StatCategory.VITALITY);

        EquipmentAttributeService.sync(player);
        EquipmentVitalityService.VitalityRuntimeSnapshot snapshot = EquipmentVitalityService.runtimeSnapshot(player);
        EquipmentVitalityService.VitalityStatState stats = snapshot.stats();

        EssenceCommandUtil.send(source, EssenceCommandUtil.section("Runtime"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Health",
                EssenceCommandUtil.formatDecimal(snapshot.health()) + " / "
                        + EssenceCommandUtil.formatDecimal(snapshot.maxHealth()) + " health points"
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Health regeneration",
                EssenceCommandUtil.formatDecimal(stats.healthRegenerationHeartsPerSecond())
                        + " hearts/sec | last tick restored "
                        + EssenceCommandUtil.formatDecimal(snapshot.lastPassiveRegenHealthPoints()) + " health points"
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Healing effectiveness",
                "+" + EssenceCommandUtil.formatDecimal(stats.healingEffectivenessPercent()) + "%"
        ));
        EquipmentVitalityService.lastHealing(player).ifPresentOrElse(
                healing -> EssenceCommandUtil.send(
                        source,
                        EssenceCommandUtil.line(
                                "Last heal",
                                EssenceCommandUtil.formatDecimal(healing.requestedHealing()) + " -> "
                                        + EssenceCommandUtil.formatDecimal(healing.resolvedHealing())
                                        + " | bypass " + healing.bypassReason()
                        )
                ),
                () -> EssenceCommandUtil.send(source, EssenceCommandUtil.muted("  Last heal: NONE RECORDED"))
        );

        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Hunger efficiency",
                EssenceCommandUtil.formatDecimal(stats.hungerEfficiencyPercent()) + "%"
                        + " | food/saturation/exhaustion " + snapshot.foodLevel() + "/"
                        + EssenceCommandUtil.formatDecimal(snapshot.saturationLevel()) + "/"
                        + EssenceCommandUtil.formatDecimal(snapshot.exhaustionLevel())
        ));
        EquipmentVitalityService.lastExhaustion(player).ifPresentOrElse(
                exhaustion -> EssenceCommandUtil.send(
                        source,
                        EssenceCommandUtil.line(
                                "Last exhaustion",
                                EssenceCommandUtil.formatDecimal(exhaustion.requestedExhaustion()) + " -> "
                                        + EssenceCommandUtil.formatDecimal(exhaustion.resolvedExhaustion())
                                        + " (" + EssenceCommandUtil.formatDecimal(exhaustion.hungerEfficiencyPercent()) + "% reduced)"
                        )
                ),
                () -> EssenceCommandUtil.send(source, EssenceCommandUtil.muted("  Last exhaustion: NONE RECORDED"))
        );

        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Breath hold",
                "+" + EssenceCommandUtil.formatDecimal(stats.breathHoldSeconds()) + " sec"
                        + " | estimated total " + EssenceCommandUtil.formatDecimal(snapshot.estimatedTotalBreathSeconds()) + " sec"
                        + " | air " + snapshot.airSupply() + "/" + snapshot.maxAirSupply()
                        + " | last refund " + snapshot.lastAirRefund()
        ));
        return 1;
    }

    private static int showMobility(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        showCategoryHeaderAndStats(source, player, StatCategory.MOBILITY);

        EquipmentAttributeService.sync(player);
        EquipmentMobilityService.sync(player);
        EquipmentAttributeService.AppliedState attributes = EquipmentAttributeService.evaluate(player);
        EquipmentMobilityService.MobilityState mobility = EquipmentMobilityService.evaluate(player);

        EssenceCommandUtil.send(source, EssenceCommandUtil.section("Runtime"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Movement speed",
                "+" + EssenceCommandUtil.formatDecimal(attributes.movementSpeedFraction() * 100.0) + "%"
                        + " | actual " + EssenceCommandUtil.formatDecimal(player.getAttributeValue(Attributes.MOVEMENT_SPEED))
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Swim speed",
                "+" + EssenceCommandUtil.formatDecimal(mobility.swimSpeedPercent()) + "%"
                        + " | water movement efficiency " + EssenceCommandUtil.formatDecimal(mobility.waterMovementEfficiency())
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Jump height",
                "+" + EssenceCommandUtil.formatDecimal(mobility.jumpHeightPercent()) + "%"
                        + " | jump strength " + EssenceCommandUtil.formatDecimal(mobility.jumpStrength())
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Step height",
                "+" + EssenceCommandUtil.formatDecimal(attributes.stepHeightBlocks()) + " blocks"
                        + " | actual " + EssenceCommandUtil.formatDecimal(player.getAttributeValue(Attributes.STEP_HEIGHT))
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Flight speed",
                "+" + EssenceCommandUtil.formatDecimal(mobility.flightSpeedPercent()) + "%"
                        + " | ability " + EssenceCommandUtil.formatDecimal(mobility.baselineFlightSpeed())
                        + " -> " + EssenceCommandUtil.formatDecimal(mobility.resolvedFlightSpeed())
                        + " | mayfly " + (mobility.mayFly() ? "YES" : "NO")
                        + " | flying " + (mobility.flying() ? "YES" : "NO")
        ));
        return 1;
    }

    private static int showGathering(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        showCategoryHeaderAndStats(source, player, StatCategory.GATHERING);

        EquipmentAttributeService.sync(player);
        EquipmentAttributeService.AppliedState attributes = EquipmentAttributeService.evaluate(player);
        EquipmentGatheringService.GatheringState gathering = EquipmentGatheringService.evaluate(player);

        EssenceCommandUtil.send(source, EssenceCommandUtil.section("Runtime"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line("Main hand", gathering.mainHandName()));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Mining speed",
                "+" + EssenceCommandUtil.formatDecimal(attributes.miningSpeedFraction() * 100.0) + "%"
                        + " | block-break multiplier " + EssenceCommandUtil.formatDecimal(player.getAttributeValue(Attributes.BLOCK_BREAK_SPEED))
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Fortune",
                EssenceCommandUtil.formatDecimal(gathering.fortuneEarnedLevels())
                        + " Essence levels -> virtual " + gathering.fortuneVirtualLevel()
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Looting",
                EssenceCommandUtil.formatDecimal(gathering.lootingEarnedLevels())
                        + " Essence levels -> virtual " + gathering.lootingVirtualLevel()
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Reach",
                "+" + EssenceCommandUtil.formatDecimal(attributes.reachBlocks()) + " blocks"
                        + " | actual block/entity "
                        + EssenceCommandUtil.formatDecimal(player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE)) + "/"
                        + EssenceCommandUtil.formatDecimal(player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE))
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Experience gain",
                "+" + EssenceCommandUtil.formatDecimal(gathering.experienceGainPercent()) + "%"
        ));

        EquipmentGatheringService.lastEnchantmentQuery(player).ifPresentOrElse(
                query -> EssenceCommandUtil.send(
                        source,
                        EssenceCommandUtil.line(
                                "Last Fortune/Looting query",
                                query.enchantment() + " | vanilla " + query.vanillaLevel()
                                        + " + Essence " + query.virtualLevel()
                                        + " = " + query.resolvedLevel()
                                        + " | " + query.queryPath()
                        )
                ),
                () -> EssenceCommandUtil.send(source, EssenceCommandUtil.muted("  Last Fortune/Looting query: NONE RECORDED"))
        );
        EquipmentGatheringService.lastExperienceGain(player).ifPresentOrElse(
                xp -> EssenceCommandUtil.send(
                        source,
                        EssenceCommandUtil.line(
                                "Last XP gain",
                                xp.requested() + " -> " + xp.resolved()
                                        + " | +" + EssenceCommandUtil.formatDecimal(xp.bonusPercent()) + "%"
                                        + " | carry " + EssenceCommandUtil.formatDecimal(xp.fractionalBonusCarry())
                        )
                ),
                () -> EssenceCommandUtil.send(source, EssenceCommandUtil.muted("  Last XP gain: NONE RECORDED"))
        );
        return 1;
    }

    private static int showUtility(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        showCategoryHeaderAndStats(source, player, StatCategory.UTILITY);

        EquipmentAttributeService.sync(player);
        EquipmentMobilityService.sync(player);
        EquipmentAttributeService.AppliedState attributes = EquipmentAttributeService.evaluate(player);
        EquipmentMobilityService.MobilityState mobility = EquipmentMobilityService.evaluate(player);
        EquipmentGatheringService.GatheringState gathering = EquipmentGatheringService.evaluate(player);
        PlayerEssenceData data = playerData(player);

        EssenceCommandUtil.send(source, EssenceCommandUtil.section("Runtime"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Luck",
                "+" + EssenceCommandUtil.formatDecimal(mobility.luckBonus())
                        + " | actual vanilla Luck " + EssenceCommandUtil.formatDecimal(mobility.actualLuck())
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Sneak speed",
                "+" + EssenceCommandUtil.formatDecimal(attributes.sneakSpeedFraction() * 100.0) + "%"
                        + " | actual " + EssenceCommandUtil.formatDecimal(player.getAttributeValue(Attributes.SNEAKING_SPEED))
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Main-hand durability efficiency",
                EssenceCommandUtil.formatDecimal(gathering.durabilityEfficiencyPercent()) + "%"
        ));

        for (EquipmentSlot slot : EquipmentStatResolver.armorSlots()) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            EquipmentStatState wornItem = EquipmentStatResolver.evaluateWornItem(player, slot);
            double percent = EquipmentValueService.scaledBonus(
                    data,
                    EssenceStats.DURABILITY_EFFICIENCY,
                    wornItem.strength(EssenceStats.DURABILITY_EFFICIENCY)
            );
            if (percent > 0.0) {
                EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                        slot.getName() + " durability efficiency",
                        EssenceCommandUtil.formatDecimal(percent) + "% (item-local)"
                ));
            }
        }

        EquipmentGatheringService.lastDurabilityEvent(player).ifPresentOrElse(
                durability -> EssenceCommandUtil.send(
                        source,
                        EssenceCommandUtil.line(
                                "Last durability event",
                                durability.itemName() + " | " + durability.requested() + " -> " + durability.resolved()
                                        + " | " + EssenceCommandUtil.formatDecimal(durability.efficiencyPercent()) + "% efficient"
                                        + " | carry " + EssenceCommandUtil.formatDecimal(durability.fractionalDamageCarry())
                                        + " | " + durability.context()
                        )
                ),
                () -> EssenceCommandUtil.send(source, EssenceCommandUtil.muted("  Last durability event: NONE RECORDED"))
        );
        return 1;
    }

    private static void showCategoryHeaderAndStats(
            CommandSourceStack source,
            ServerPlayer player,
            StatCategory category
    ) {
        EssenceCommandUtil.send(
                source,
                Component.literal(EssenceCommandUtil.categoryName(category) + " Debug")
                        .withStyle(EssenceCommandUtil.categoryColor(category), ChatFormatting.BOLD)
        );

        PlayerEssenceData data = playerData(player);
        EquipmentStatState active = EquipmentStatResolver.evaluate(player);
        CategoryDevelopment development = StatScalingService.evaluateCategory(data, category);

        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Category development",
                EssenceCommandUtil.format(development.effectiveInvestment()) + " / "
                        + EssenceCommandUtil.format(development.currentCapacity()) + " effective | "
                        + EssenceCommandUtil.formatProgress(development.development()) + " developed"
        ));

        EssenceCommandUtil.send(source, EssenceCommandUtil.section("Stats"));
        for (StatDefinition stat : EssenceStatRegistry.values()) {
            if (stat.category() != category) {
                continue;
            }

            StatScalingResult scaling = StatScalingService.evaluate(data, stat);
            double strength = active.strength(stat);
            double applied = scaling.scaledBonus() * strength;

            if (stat == EssenceStats.DURABILITY_EFFICIENCY) {
                EssenceCommandUtil.send(
                        source,
                        EssenceCommandUtil.line(
                                stat.displayName(),
                                "investment " + EssenceCommandUtil.formatBonus(stat, scaling.scaledBonus())
                                        + " | item-local applicability; runtime details below"
                        )
                );
                continue;
            }

            EssenceCommandUtil.send(
                    source,
                    EssenceCommandUtil.line(
                            stat.displayName(),
                            "investment " + EssenceCommandUtil.formatBonus(stat, scaling.scaledBonus())
                                    + " | active " + EssenceCommandUtil.formatStrength(strength)
                                    + " | applied " + EssenceCommandUtil.formatBonus(stat, applied)
                    )
            );
        }
    }

    private static boolean usesProfile(ItemStack stack, ResourceLocation profileId) {
        return !stack.isEmpty()
                && stack.getItem() instanceof EquipmentProfileItem item
                && item.equipmentProfileId().equals(profileId);
    }

    private static String damageValue(float value) {
        return value < 0.0F ? "N/A" : EssenceCommandUtil.formatDecimal(value);
    }

    private static PlayerEssenceData playerData(ServerPlayer player) {
        return EssenceSavedData.get(player.server).getPlayerData(player.getUUID());
    }

    private static void appendStrengths(StringBuilder builder, Map<ResourceLocation, Double> strengths) {
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
                    .append(EssenceCommandUtil.formatStrength(entry.getValue()));
        }
    }
}
