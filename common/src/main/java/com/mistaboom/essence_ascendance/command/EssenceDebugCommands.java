package com.mistaboom.essence_ascendance.command;

import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.config.EssenceServerConfig;
import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import com.mistaboom.essence_ascendance.data.SkillPurchase;
import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleBlockEntity;
import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleChannelService;
import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleEssences;
import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleStructureStats;
import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleStructureSnapshot;
import com.mistaboom.essence_ascendance.infuser.EssenceInfuserBalance;
import com.mistaboom.essence_ascendance.infuser.EssenceInfuserBlockEntity;
import com.mistaboom.essence_ascendance.infuser.EssentiumInfusionRecipe;
import com.mistaboom.essence_ascendance.infuser.FocusInfusionRecipe;
import com.mistaboom.essence_ascendance.pylon.EssencePylonBlockEntity;
import com.mistaboom.essence_ascendance.pylon.EssencePylonContribution;
import com.mistaboom.essence_ascendance.equipment.ArmorStatWeights;
import com.mistaboom.essence_ascendance.equipment.EquipmentActivationType;
import com.mistaboom.essence_ascendance.equipment.EquipmentAttributeService;
import com.mistaboom.essence_ascendance.equipment.EquipmentBaselineProperty;
import com.mistaboom.essence_ascendance.equipment.EquipmentBaselineResult;
import com.mistaboom.essence_ascendance.equipment.EquipmentBaselineService;
import com.mistaboom.essence_ascendance.equipment.EquipmentDamageService;
import com.mistaboom.essence_ascendance.equipment.EquipmentShieldService;
import com.mistaboom.essence_ascendance.equipment.ShieldMath;
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
import com.mistaboom.essence_ascendance.progression.MilestoneDefinition;
import com.mistaboom.essence_ascendance.progression.MilestoneProgress;
import com.mistaboom.essence_ascendance.progression.MilestoneProviders;
import com.mistaboom.essence_ascendance.progression.MilestoneRequirement;
import com.mistaboom.essence_ascendance.progression.MilestoneService;
import com.mistaboom.essence_ascendance.progression.PermanentMilestoneService;
import com.mistaboom.essence_ascendance.progression.StatInvestmentLimit;
import com.mistaboom.essence_ascendance.progression.StatScalingResult;
import com.mistaboom.essence_ascendance.progression.StatScalingService;
import com.mistaboom.essence_ascendance.progression.TierInvestmentPolicy;
import com.mistaboom.essence_ascendance.skill.SkillDefinition;
import com.mistaboom.essence_ascendance.skill.CommittedSkillService;
import com.mistaboom.essence_ascendance.skill.SkillEvaluationContext;
import com.mistaboom.essence_ascendance.skill.SkillEvaluationResult;
import com.mistaboom.essence_ascendance.skill.SkillRegistry;
import com.mistaboom.essence_ascendance.skill.SkillRequirementStatus;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime;
import com.mistaboom.essence_ascendance.skill.requirement.BonusInvestmentRequirement;
import com.mistaboom.essence_ascendance.skill.requirement.DiscoveryRequirement;
import com.mistaboom.essence_ascendance.skill.requirement.PermanentMilestoneRequirement;
import com.mistaboom.essence_ascendance.skill.requirement.PlayerAttunementRequirement;
import com.mistaboom.essence_ascendance.skill.requirement.SkillRequirement;
import com.mistaboom.essence_ascendance.stat.EssenceStatRegistry;
import com.mistaboom.essence_ascendance.stat.EssenceStats;
import com.mistaboom.essence_ascendance.stat.StatCategory;
import com.mistaboom.essence_ascendance.stat.StatDefinition;
import com.mistaboom.essence_ascendance.valuation.ProceduralValuationCsvExporter;
import com.mistaboom.essence_ascendance.mapping.ItemEssenceMappingManager;
import com.mistaboom.essence_ascendance.valuation.ProceduralValuationEngine;
import com.mistaboom.essence_ascendance.valuation.ProceduralValuationResult;
import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.io.IOException;
import java.util.List;
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
                .then(Commands.literal("valuation")
                        .executes(context -> showProceduralValuation(context.getSource()))
                        .then(Commands.literal("rebuild")
                                .executes(context -> rebuildProceduralValuation(context.getSource())))
                        .then(Commands.literal("export")
                                .executes(context -> exportProceduralValuation(context.getSource()))))
                .then(Commands.literal("mappings").executes(context -> showMappingRegistry(context.getSource())))
                .then(Commands.literal("crucible").executes(context -> showCrucible(context.getSource())))
                .then(Commands.literal("pylon").executes(context -> showPylon(context.getSource())))
                .then(Commands.literal("infuser").executes(context -> showInfuser(context.getSource())))
                .then(Commands.literal("baselines").executes(context -> showBaselines(context.getSource())))
                .then(Commands.literal("skills")
                        .executes(context -> showSkills(context.getSource())))
                .then(Commands.literal("skill")
                        .then(Commands.argument("skill", StringArgumentType.string())
                                .suggests(EssenceCommandUtil::suggestSkills)
                                .executes(context -> showSkill(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "skill")
                                ))))
                .then(Commands.literal("attunements")
                        .executes(context -> showAttunements(context.getSource()))
                        .then(Commands.argument("attunement", StringArgumentType.string())
                                .suggests(EssenceCommandUtil::suggestAttunements)
                                .executes(context -> showAttunement(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "attunement")
                                ))))
                .then(Commands.literal("milestone")
                        .executes(context -> showStoredMilestones(context.getSource()))
                        .then(Commands.argument("milestone", StringArgumentType.string())
                                .suggests(EssenceCommandUtil::suggestDevelopmentMilestones)
                                .executes(context -> showMilestone(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "milestone")
                                ))))
                .then(Commands.literal("offense").executes(context -> showOffense(context.getSource())))
                .then(Commands.literal("defense").executes(context -> showDefense(context.getSource())))
                .then(Commands.literal("shield").executes(context -> showShield(context.getSource())))
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
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence debug mapping", "resolve the main-hand item to Essence"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence debug valuation", "analyze current data on demand and compare with the saved/live mapping (may calculate)"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence debug valuation rebuild", "recalculate, save and install the generated baseline; later startups reuse the saved file"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence debug valuation export", "analyze/export current values and installed results; does not replace the saved baseline"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence debug mappings", "mapping registry/reload summary"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence debug crucible", "inspect the Essence Crucible you are looking at"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence debug pylon", "inspect the Essence Pylon you are looking at"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence debug infuser", "inspect the Essence Infuser you are looking at"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence debug baselines", "player-tier baseline preset reference"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence debug skills", "inspect authoritative owned-skill receipts and loadout selections"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence debug skill <skill_id>", "inspect one catalog definition and its saved player state"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence debug attunements [attunement_id]", "inspect authoritative raw Player Attunement state"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence debug milestone [milestone_id]", "inspect saved milestone flags or one configured milestone"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.section("Gameplay categories"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence debug offense", "melee, ranged, magic, and attack knockback"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence debug defense", "resistance/reflection stats and last incoming-damage/status events"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence debug shield", "held/block context and disjoint blocked/health-loss retaliation measurements"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence debug vitality", "health, regeneration, healing, hunger, breath"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence debug mobility", "movement, swimming, jumping, stepping, flight"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence debug gathering", "mining, Fortune, Looting, reach, XP gain"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence debug utility", "Luck, sneak speed, durability efficiency"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.muted("Stat investment/scaling/applicability is intentionally centralized at /essence stat <stat>."));
        return 1;
    }

    private static int showSkills(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PlayerEssenceData data = playerData(player);
        long knownOwned = data.getOwnedSkills().keySet().stream()
                .filter(id -> SkillRegistry.get(id).isPresent())
                .count();
        Map<ResourceLocation, SkillEvaluationResult> evaluated =
                CommittedSkillService.evaluations(player);
        long effective = evaluated.values().stream()
                .filter(SkillEvaluationResult::effective)
                .count();
        long suspended = evaluated.values().stream()
                .filter(SkillEvaluationResult::suspended)
                .count();
        long replaced = evaluated.values().stream()
                .filter(SkillEvaluationResult::replaced)
                .count();

        EssenceCommandUtil.send(source, EssenceCommandUtil.title("Skill Framework State"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Catalog",
                SkillRegistry.size() + " definitions; version " + SkillRegistry.catalogVersion()
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Gameplay implementations",
                SkillEffectRuntime.implementedIds().size() + " implemented; "
                        + (SkillRegistry.size() - SkillEffectRuntime.implementedIds().size())
                        + " deferred"
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Owned receipts",
                data.getOwnedSkills().size() + " total; " + knownOwned + " known"
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Loadout selections",
                Integer.toString(data.getLoadoutSelections().size())
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Evaluated",
                effective + " effective; " + suspended + " suspended; "
                        + replaced + " replaced"
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Nexus revision",
                Long.toString(data.nexusRevision())
        ));

        if (data.getOwnedSkills().isEmpty()) {
            EssenceCommandUtil.send(source, EssenceCommandUtil.muted("  No owned skills."));
        } else {
            EssenceCommandUtil.send(source, EssenceCommandUtil.section("Permanent purchase receipts"));
            data.getOwnedSkills().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> {
                        SkillPurchase purchase = entry.getValue();
                        String known = SkillRegistry.get(entry.getKey()).isPresent()
                                ? "KNOWN"
                                : "UNKNOWN DEFINITION";
                        EssenceCommandUtil.send(
                                source,
                                EssenceCommandUtil.line(
                                        entry.getKey().toString(),
                                        EssenceCommandUtil.format(purchase.paidCost()) + " "
                                                + purchase.essenceId() + " [" + known + "]"
                                )
                        );
                    });
        }

        if (!data.getLoadoutSelections().isEmpty()) {
            EssenceCommandUtil.send(source, EssenceCommandUtil.section("Saved loadout selections"));
            data.getLoadoutSelections().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> EssenceCommandUtil.send(
                            source,
                            EssenceCommandUtil.line(
                                    entry.getKey().toString(),
                                    entry.getValue().toString()
                            )
                    ));
        }

        EssenceCommandUtil.send(source, EssenceCommandUtil.muted(
                "Use /essence debug skill <skill_id> for catalog metadata and requirement state."
        ));
        return 1;
    }

    private static int showSkill(
            CommandSourceStack source,
            String skillName
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PlayerEssenceData data = playerData(player);
        ResourceLocation skillId = EssenceCommandUtil.resolveResourceId(skillName);
        SkillDefinition skill = SkillRegistry.get(skillId).orElse(null);
        SkillPurchase purchase = data.getSkillPurchase(skillId).orElse(null);

        if (skill == null && purchase == null) {
            EssenceCommandUtil.fail(source, "Unknown skill ID: " + skillId);
            return 0;
        }

        EssenceCommandUtil.send(source, EssenceCommandUtil.title("Skill Debug"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line("ID", EssenceCommandUtil.muted(skillId.toString())));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Owned",
                purchase == null
                        ? EssenceCommandUtil.warn("NO")
                        : EssenceCommandUtil.good("YES")
        ));

        if (purchase != null) {
            EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                    "Purchase receipt",
                    EssenceCommandUtil.format(purchase.paidCost()) + " " + purchase.essenceId()
            ));
        }

        if (skill == null) {
            EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                    "Definition",
                    EssenceCommandUtil.bad("UNKNOWN; RAW SAVED RECEIPT PRESERVED")
            ));
            EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                    "Nexus revision",
                    Long.toString(data.nexusRevision())
            ));
            return 1;
        }

        SkillEvaluationContext evaluationContext = CommittedSkillService.context(player);
        SkillEvaluationResult evaluation = CommittedSkillService.evaluation(player, skillId);

        EssenceCommandUtil.send(source, EssenceCommandUtil.line("Name key", skill.nameTranslationKey()));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line("Description key", skill.descriptionTranslationKey()));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line("Essence", skill.essenceId().toString()));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line("Required tier", skill.requiredTierId().toString()));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Derived cost",
                EssenceCommandUtil.format(skill.cost(EssenceConfigManager.get().balanceProfile()))
                        + " [" + skill.costBand().name() + ", "
                        + skill.costBand().percentage() + "%]"
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line("Activation", skill.activationPolicy().name()));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Display",
                "order " + skill.displayOrder() + ", hint " + skill.layoutHint().name()
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Evaluated state",
                evaluation.displayState().name()
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Purchase gates",
                "tier=" + evaluation.purchaseEligibility().tierSatisfied()
                        + ", prerequisites=" + evaluation.purchaseEligibility().prerequisitesSatisfied()
                        + ", requirements=" + evaluation.purchaseEligibility().requirementsSatisfied()
                        + ", eligible=" + evaluation.eligibleToPurchase()
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Activation state",
                "selected=" + evaluation.selected()
                        + ", effective=" + evaluation.effective()
                        + ", suspended=" + evaluation.suspended()
                        + ", replaced=" + evaluation.replaced()
                        + ", fallback=" + evaluation.fallbackActive()
        ));
        if (!evaluation.replacedBy().isEmpty()) {
            EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                    "Replaced by",
                    evaluation.replacedBy().toString()
            ));
        }
        if (!evaluation.inactivePrerequisites().isEmpty()) {
            EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                    "Inactive prerequisite effects",
                    evaluation.inactivePrerequisites().toString()
            ));
        }

        EssenceCommandUtil.send(source, EssenceCommandUtil.section("Immediate prerequisites"));
        if (evaluation.prerequisites().isEmpty()) {
            EssenceCommandUtil.send(source, EssenceCommandUtil.muted("  NONE"));
        } else {
            for (var prerequisite : evaluation.prerequisites()) {
                EssenceCommandUtil.send(
                        source,
                        Component.literal("  " + prerequisite.skillId() + " ")
                                .withStyle(ChatFormatting.WHITE)
                                .append(prerequisite.authoritativeOwned()
                                        ? EssenceCommandUtil.good("OWNED")
                                        : EssenceCommandUtil.warn("MISSING"))
                );
            }
        }

        EssenceCommandUtil.send(source, EssenceCommandUtil.section("Additional requirements"));
        if (skill.requirements().isEmpty()) {
            EssenceCommandUtil.send(source, EssenceCommandUtil.muted("  NONE"));
        } else {
            for (int index = 0; index < skill.requirements().size(); index++) {
                showSkillRequirement(
                        source,
                        evaluationContext.authoritativeBonusTotals(),
                        skill.requirements().get(index),
                        evaluation.requirements().get(index)
                );
            }
        }

        if (skill.choiceGroupId().isPresent()) {
            ResourceLocation groupId = skill.choiceGroupId().orElseThrow();
            String selected = data.getLoadoutSelection(groupId)
                    .map(ResourceLocation::toString)
                    .orElse("NONE");
            EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                    "Choice group",
                    groupId + " -> " + selected
            ));
        } else {
            EssenceCommandUtil.send(source, EssenceCommandUtil.line("Choice group", "NONE"));
        }

        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Replaces",
                skill.replacementTargetId()
                        .map(ResourceLocation::toString)
                        .orElse("NONE")
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Nexus revision",
                Long.toString(data.nexusRevision())
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.section("Gameplay runtime"));
        if (SkillEffectRuntime.isImplemented(skillId)) {
            EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                    "Implementation", EssenceCommandUtil.good("IMPLEMENTED; PROVISIONAL BALANCE")
            ));
            for (String line : SkillEffectRuntime.debugLines(player, skillId)) {
                EssenceCommandUtil.send(source, EssenceCommandUtil.muted("  " + line));
            }
        } else {
            EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                    "Implementation", EssenceCommandUtil.warn("DEFERRED; FRAMEWORK STATE ONLY")
            ));
        }
        return 1;
    }

    private static void showSkillRequirement(
            CommandSourceStack source,
            Map<ResourceLocation, Long> bonusTotals,
            SkillRequirement requirement,
            SkillRequirementStatus status
    ) {
        String details;

        if (requirement instanceof PlayerAttunementRequirement attunement) {
            details = "PLAYER_ATTUNEMENT " + attunement.attunementId();
        } else if (requirement instanceof PermanentMilestoneRequirement milestone) {
            details = "PERMANENT_MILESTONE " + milestone.milestoneId();
        } else if (requirement instanceof BonusInvestmentRequirement bonus) {
            long current = bonusTotals.getOrDefault(bonus.essenceId(), 0L);
            details = "BONUS_INVESTMENT " + bonus.essenceId() + " >= "
                    + EssenceCommandUtil.format(bonus.minimumInvestment())
                    + " (current allocated " + EssenceCommandUtil.format(current) + ")";
        } else if (requirement instanceof DiscoveryRequirement discovery) {
            details = "DISCOVERY " + discovery.discoveryId();
        } else {
            details = requirement.kind().name() + " " + requirement.id();
        }

        if (status.live()) {
            details += " [LIVE]";
        }

        EssenceCommandUtil.send(
                source,
                Component.literal("  " + details + " ").withStyle(ChatFormatting.WHITE)
                        .append(status.authoritativeSatisfied()
                                ? EssenceCommandUtil.good("SATISFIED")
                                : EssenceCommandUtil.warn("MISSING"))
        );
    }

    private static int showAttunements(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PlayerEssenceData data = playerData(player);

        EssenceCommandUtil.send(source, EssenceCommandUtil.title("Player Attunements"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Completed",
                data.getCompletedAttunements().size() + " raw stable IDs"
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Known development IDs",
                Integer.toString(SkillRegistry.knownAttunementIds().size())
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Nexus revision",
                Long.toString(data.nexusRevision())
        ));

        if (data.getCompletedAttunements().isEmpty()) {
            EssenceCommandUtil.send(source, EssenceCommandUtil.muted("  NONE"));
            return 1;
        }

        data.getCompletedAttunements().stream()
                .sorted()
                .forEach(id -> EssenceCommandUtil.send(
                        source,
                        Component.literal("  " + id + " ").withStyle(ChatFormatting.WHITE)
                                .append(SkillRegistry.knownAttunementIds().contains(id)
                                        ? EssenceCommandUtil.good("KNOWN")
                                        : EssenceCommandUtil.warn("UNREGISTERED/PRESERVED"))
                ));
        return 1;
    }

    private static int showAttunement(
            CommandSourceStack source,
            String attunementName
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PlayerEssenceData data = playerData(player);
        ResourceLocation attunementId = EssenceCommandUtil.resolveResourceId(attunementName);
        boolean completed = data.hasAttunement(attunementId);

        EssenceCommandUtil.send(source, EssenceCommandUtil.title("Player Attunement"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line("ID", EssenceCommandUtil.muted(attunementId.toString())));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Catalog identity",
                SkillRegistry.knownAttunementIds().contains(attunementId)
                        ? EssenceCommandUtil.good("KNOWN")
                        : EssenceCommandUtil.warn("UNREGISTERED/FORWARD-COMPATIBLE")
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Saved state",
                completed
                        ? EssenceCommandUtil.good("GRANTED")
                        : EssenceCommandUtil.warn("NOT GRANTED")
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Nexus revision",
                Long.toString(data.nexusRevision())
        ));
        return 1;
    }

    private static int showStoredMilestones(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PlayerEssenceData data = playerData(player);

        EssenceCommandUtil.send(source, EssenceCommandUtil.title("Saved Milestone Flags"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Completed",
                data.getCompletedMilestones().size() + " raw stable IDs"
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Configured definitions",
                Integer.toString(EssenceConfigManager.get().milestones().size())
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Skill requirement IDs",
                Integer.toString(EssenceCommandUtil.knownSkillMilestoneIds().size())
        ));

        if (data.getCompletedMilestones().isEmpty()) {
            EssenceCommandUtil.send(source, EssenceCommandUtil.muted("  NONE"));
            return 1;
        }

        data.getCompletedMilestones().stream()
                .sorted()
                .forEach(id -> EssenceCommandUtil.send(
                        source,
                        Component.literal("  " + id).withStyle(ChatFormatting.WHITE)
                ));
        return 1;
    }

    private static int showMilestone(
            CommandSourceStack source,
            String milestoneName
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PlayerEssenceData data = playerData(player);
        ResourceLocation requestedId = EssenceCommandUtil.resolveResourceId(milestoneName);
        MilestoneDefinition milestone = EssenceConfigManager.get()
                .getMilestone(requestedId)
                .orElse(null);

        if (milestone == null) {
            boolean knownSkillMilestone = EssenceCommandUtil
                    .knownSkillMilestoneIds()
                    .contains(requestedId);
            boolean stored = data.hasCompletedMilestone(requestedId);
            if (!knownSkillMilestone && !stored) {
                EssenceCommandUtil.fail(source, "Unknown milestone ID: " + requestedId);
                return 0;
            }

            EssenceCommandUtil.send(source, EssenceCommandUtil.title("Milestone Debug"));
            EssenceCommandUtil.send(source, EssenceCommandUtil.line("ID", EssenceCommandUtil.muted(requestedId.toString())));
            EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                    "Source",
                    knownSkillMilestone
                            ? "permanent skill requirement"
                            : "unknown preserved saved flag"
            ));
            EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                    "Saved state",
                    stored
                            ? EssenceCommandUtil.good("PRESENT")
                            : EssenceCommandUtil.warn("ABSENT")
            ));
            EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                    "Nexus revision",
                    Long.toString(data.nexusRevision())
            ));
            return 1;
        }

        MilestoneProgress progress = MilestoneService.evaluate(
                player,
                MilestoneRequirement.milestone(milestone.id())
        );

        EssenceCommandUtil.send(source, EssenceCommandUtil.title("Milestone Debug"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line("ID", EssenceCommandUtil.muted(milestone.id().toString())));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line("Provider", milestone.providerId().toString()));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line("Target", milestone.target()));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Provider state",
                !progress.resolvable()
                        ? EssenceCommandUtil.bad("UNRESOLVED")
                        : progress.complete()
                        ? EssenceCommandUtil.good("COMPLETE")
                        : EssenceCommandUtil.warn("INCOMPLETE")
        ));

        if (EssenceCommandUtil.knownSkillMilestoneIds().contains(milestone.id())) {
            PermanentMilestoneService.Resolution permanent =
                    PermanentMilestoneService.resolveAll(
                                    player,
                                    List.of(milestone.id())
                            )
                            .get(0);
            EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                    "Permanent skill state",
                    !permanent.resolvable()
                            ? EssenceCommandUtil.bad("UNRESOLVED")
                            : permanent.complete()
                            ? EssenceCommandUtil.good("COMPLETE")
                            : EssenceCommandUtil.warn("INCOMPLETE")
            ));
            EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                    "Captured",
                    permanent.captured()
                            ? EssenceCommandUtil.good("YES")
                            : EssenceCommandUtil.muted("NO")
            ));
        }

        if (milestone.providerId().equals(MilestoneProviders.INTERNAL)) {
            ResourceLocation targetId = ResourceLocation.tryParse(milestone.target());
            Component storedState = targetId == null
                    ? EssenceCommandUtil.bad("INVALID TARGET ID")
                    : data.hasCompletedMilestone(targetId)
                    ? EssenceCommandUtil.good("PRESENT")
                    : EssenceCommandUtil.warn("ABSENT");
            EssenceCommandUtil.send(source, EssenceCommandUtil.line("Saved internal target", storedState));
        }

        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Nexus revision",
                Long.toString(data.nexusRevision())
        ));
        return 1;
    }

    private static int showShield(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        EssenceCommandUtil.send(source, EssenceCommandUtil.title("Ascendance Shield"));
        EquipmentShieldService.Context held = EquipmentShieldService.heldContext(player);
        EquipmentShieldService.Context blocking = EquipmentShieldService.blockingContext(player);
        EssenceCommandUtil.send(source, EssenceCommandUtil.line("Selection", "main-hand functional shield first, otherwise offhand; no stacking"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line("Guarding", Boolean.toString(blocking != null)));
        if (held == null) {
            EssenceCommandUtil.send(source, EssenceCommandUtil.muted("No functional held Ascendance shield; Fractured shields provide no blocking/reflection."));
        } else {
            EssenceCommandUtil.send(source, EssenceCommandUtil.line("Item tier / investment tier",
                    held.itemTier().serializedName() + " / " + held.effectiveTier().serializedName()));
            EssenceCommandUtil.send(source, EssenceCommandUtil.line("Native durability", Integer.toString(held.nativeDurability())));
            EssenceCommandUtil.send(source, EssenceCommandUtil.line("Base + innate / shield investment / armor investment",
                    held.baseReflectionPercent() + "% + " + held.innateReflectionBonusPercent() + "% / "
                            + held.investedReflectionPercent() + "% / "
                            + EquipmentDamageService.armorReflectionPercent(player) + "%"));
            EssenceCommandUtil.send(source, EssenceCommandUtil.line("Ordinary held reflection",
                    held.ordinaryReflectionPercent(EquipmentDamageService.armorReflectionPercent(player)) + "% of health lost"));
            EssenceCommandUtil.send(source, EssenceCommandUtil.line("On-block (item-tier multiplier / total)",
                    "x" + held.amplification() + "; " + held.blockedReflectionPercent() + "% of final blocked damage"));
            EssenceCommandUtil.send(source, EssenceCommandUtil.line("Guard Readiness",
                    held.guardReadinessPercent() + "%"));
            EssenceCommandUtil.send(source, EssenceCommandUtil.line("Raise delay / normal",
                    ShieldMath.raiseDelayTicks(
                            EquipmentShieldService.VANILLA_RAISE_DELAY_TICKS,
                            held.guardReadinessPercent(),
                            EquipmentShieldService.MINIMUM_RAISE_DELAY_TICKS
                    ) + " / " + EquipmentShieldService.VANILLA_RAISE_DELAY_TICKS + " ticks"));
            EssenceCommandUtil.send(source, EssenceCommandUtil.line("Axe disable / normal",
                    ShieldMath.disableTicks(
                            100,
                            held.guardReadinessPercent(),
                            EssenceConfigManager.get().shieldBalance().minimumDisableTicks()
                    ) + " / 100 ticks"));
            EssenceCommandUtil.send(source, EssenceCommandUtil.line("Shield slowdown removed / input multiplier",
                    held.guardedMovementPercent() + "% / " + ShieldMath.movementMultiplier(held.guardedMovementPercent())));
            EssenceCommandUtil.send(source, EssenceCommandUtil.line("Guarding knockback resistance",
                    held.knockbackResistancePercent() + "% (merged with armor by maximum)"));
        }
        EquipmentDamageService.lastReflection(player).ifPresent(last -> {
            EssenceCommandUtil.send(source, EssenceCommandUtil.section("Last incoming hit (before target mitigation)"));
            EssenceCommandUtil.send(source, EssenceCommandUtil.line("Blocked / actual health lost",
                    last.blockedDamage() + " / " + last.actualHealthLost()));
            EssenceCommandUtil.send(source, EssenceCommandUtil.line("Ordinary / block portions",
                    last.ordinaryReflectedDamage() + " / " + last.blockReflectedDamage()));
            EssenceCommandUtil.send(source, EssenceCommandUtil.line("Active block shield native / invested / multiplier",
                    last.nativePercent() + "% / " + last.shieldInvestedPercent() + "% / x" + last.amplification()));
            EssenceCommandUtil.send(source, EssenceCommandUtil.line("Requested retaliation",
                    Float.toString(last.requestedRetaliationDamage()) + " (not the target's actual health loss)"));
        });
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

        if (profile.id().equals(EquipmentProfiles.MAGIC_CASTER.id())) {
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


    private static int showProceduralValuation(
            CommandSourceStack source
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ItemStack stack = player.getMainHandItem();

        EssenceCommandUtil.send(
                source,
                EssenceCommandUtil.title("Procedural Essence Valuation")
        );

        if (stack.isEmpty()) {
            EssenceCommandUtil.send(
                    source,
                    EssenceCommandUtil.muted("Hold an item in your main hand.")
            );
            return 0;
        }

        EssenceCommandUtil.send(source, EssenceCommandUtil.muted(
                "Analyzing current loaded data on demand; this may calculate. Saved mappings are not replaced."));
        ProceduralValuationResult valuation = ProceduralValuationEngine.evaluate(
                source.getServer(),
                stack
        );
        EssenceCommandUtil.send(
                source,
                EssenceCommandUtil.warn("  Current economic analysis may differ from the saved baseline. This command does not change live payouts.")
        );
        var eligibility = ItemEssenceMappingManager.generatedDecision(valuation);
        EssenceCommandUtil.send(source, EssenceCommandUtil.line("Generated eligibility",
                eligibility.status().name() + " — " + eligibility.reason()));
        var live = ItemEssenceMappingRegistry.resolve(stack);
        long liveTotal = 0;
        for (long amount : live.outputs().values()) liveTotal = Math.addExact(liveTotal, amount);
        EssenceCommandUtil.send(source, EssenceCommandUtil.line("Live item mapping",
                ItemEssenceMappingRegistry.source(valuation.itemId()) + " / " + liveTotal
                        + " (Essentium component recovery is separate)"));
        EssenceCommandUtil.send(
                source,
                EssenceCommandUtil.line(
                        "Item",
                        stack.getHoverName().getString() + " [" + valuation.itemId() + "]"
                )
        );
        EssenceCommandUtil.send(
                source,
                EssenceCommandUtil.line(
                        "Internal total",
                        EssenceCommandUtil.format(valuation.totalValue())
                )
        );
        EssenceCommandUtil.send(
                source,
                EssenceCommandUtil.line(
                        "Scale",
                        "FOUNDATIONAL / UNNORMALIZED (no legacy mapping target)"
                )
        );
        EssenceCommandUtil.send(
                source,
                EssenceCommandUtil.line(
                        "Confidence",
                        valuation.confidenceBand().name()
                                + " ("
                                + String.format(Locale.ROOT, "%.0f%%", valuation.confidence() * 100.0)
                                + ")"
                )
        );
        EssenceCommandUtil.send(
                source,
                EssenceCommandUtil.line(
                        "Renewability",
                        "x" + String.format(Locale.ROOT, "%.3f", valuation.renewabilityMultiplier())
                )
        );
        EssenceCommandUtil.send(
                source,
                EssenceCommandUtil.line(
                        "Progression signal",
                        valuation.progressionBand().name()
                )
        );
        EssenceCommandUtil.send(
                source,
                EssenceCommandUtil.line(
                        "Advancement progression",
                        String.format(Locale.ROOT, "%.1f%%", valuation.inferredProgressionScore() * 100.0)
                                + " / "
                                + valuation.progressionEvidenceCount()
                                + " evidence ref(s)"
                )
        );
        EssenceCommandUtil.send(
                source,
                EssenceCommandUtil.line(
                        "Recipe graph",
                        valuation.producingRecipeCount()
                                + " producing / "
                                + valuation.downstreamRecipeCount()
                                + " downstream"
                )
        );
        EssenceCommandUtil.send(
                source,
                EssenceCommandUtil.line(
                        "Downstream demand",
                        valuation.significantDownstreamRecipeCount()
                                + " significant / "
                                + valuation.crossModDownstreamRecipeCount()
                                + " cross-mod | x"
                                + String.format(Locale.ROOT, "%.3f", valuation.downstreamMultiplier())
                )
        );
        EssenceCommandUtil.send(
                source,
                EssenceCommandUtil.line(
                        "Direct sources",
                        valuation.sourceBlockCount()
                                + " block / "
                                + valuation.sourceEntityCount()
                                + " entity / "
                                + valuation.sourceContainerCount()
                                + " container / "
                                + valuation.sourceFishingCount()
                                + " fishing / "
                                + valuation.sourceTradeCount()
                                + " trade"
                )
        );

        valuation.recipeChoice().ifPresent(choice -> {
            EssenceCommandUtil.send(
                    source,
                    EssenceCommandUtil.section("Chosen recipe path")
            );
            EssenceCommandUtil.send(
                    source,
                    EssenceCommandUtil.line("Recipe", choice.recipeId().toString())
            );
            EssenceCommandUtil.send(
                    source,
                    EssenceCommandUtil.line(
                            "Type / value",
                            choice.recipeType()
                                    + " / "
                                    + EssenceCommandUtil.format(choice.valuePerOutput())
                                    + " per output"
                    )
            );
            EssenceCommandUtil.send(
                    source,
                    EssenceCommandUtil.line(
                            "Ingredients",
                            choice.ingredientSlots()
                                    + " slots / "
                                    + choice.uniqueChosenIngredients()
                                    + " unique / "
                                    + choice.easyChosenIngredients()
                                    + " easy / "
                                    + choice.rareChosenIngredients()
                                    + " rare / "
                                    + choice.modSpecificChosenIngredients()
                                    + " mod-specific"
                    )
            );
            EssenceCommandUtil.send(
                    source,
                    EssenceCommandUtil.line(
                            "Depth / transform",
                            choice.depth()
                                    + " / "
                                    + (choice.reversibleTransform() ? "REVERSIBLE" : "PROCESSING")
                    )
            );
        });

        EssenceCommandUtil.send(
                source,
                EssenceCommandUtil.section("Proposed Essence routing")
        );
        valuation.routedEssence().entrySet().stream()
                .sorted(
                        Map.Entry.<EssenceDefinition, Long>comparingByValue()
                                .reversed()
                                .thenComparing(entry -> entry.getKey().id().toString())
                )
                .forEach(entry -> {
                    double fraction = valuation.totalValue() <= 0L
                            ? 0.0
                            : (double) entry.getValue() / valuation.totalValue();
                    EssenceCommandUtil.send(
                            source,
                            EssenceCommandUtil.muted(
                                    "  "
                                            + entry.getKey().id().getPath()
                                            + " = "
                                            + EssenceCommandUtil.format(entry.getValue())
                                            + " ("
                                            + String.format(Locale.ROOT, "%.1f%%", fraction * 100.0)
                                            + ")"
                            )
                    );
                });

        if (!valuation.downstreamExamples().isEmpty()) {
            EssenceCommandUtil.send(
                    source,
                    EssenceCommandUtil.section("Example downstream outputs")
            );
            for (ResourceLocation output : valuation.downstreamExamples()) {
                EssenceCommandUtil.send(
                        source,
                        EssenceCommandUtil.muted("  " + output)
                );
            }
        }

        EssenceCommandUtil.send(
                source,
                EssenceCommandUtil.section("Valuation factors")
        );
        for (String factor : valuation.factors()) {
            EssenceCommandUtil.send(
                    source,
                    EssenceCommandUtil.muted("  " + factor)
            );
        }

        EssenceCommandUtil.send(
                source,
                EssenceCommandUtil.muted(
                        "Installed mapping totals are not economic-analysis targets; /essence debug mapping shows the resolved payout separately."
                )
        );

        return 1;
    }

    private static int rebuildProceduralValuation(
            CommandSourceStack source
    ) {
        ItemEssenceMappingRegistry.ReloadReport report = ItemEssenceMappingManager.rebuild();
        if (!report.successful()) {
            EssenceCommandUtil.send(source, EssenceCommandUtil.bad(
                    "Procedural rebuild rejected; mapping generation " + report.generation()
                            + " is unchanged. The saved cache was not replaced."));
            for (String error : report.errors()) EssenceCommandUtil.send(source, EssenceCommandUtil.bad(error));
            return 0;
        }
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Installed mapping generation", Long.toString(report.generation())));
        if (!ItemEssenceMappingManager.proceduralDefaultsEnabled()) {
            EssenceCommandUtil.send(source, EssenceCommandUtil.muted(
                    "Procedural defaults are disabled: explicit rules reloaded; no calculation and no cache replacement."));
            return 1;
        }
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Saved generated baseline", ItemEssenceMappingManager.generatedCachePath().toString()));
        ProceduralValuationEngine.IndexSummary summary = ProceduralValuationEngine.summary(source.getServer());

        EssenceCommandUtil.send(
                source,
                EssenceCommandUtil.title("Procedural Mapping Rebuild Complete")
        );
        EssenceCommandUtil.send(
                source,
                EssenceCommandUtil.line(
                        "Recipes",
                        summary.recipeCount() + " indexed / " + summary.skippedRecipeCount() + " skipped"
                )
        );
        EssenceCommandUtil.send(
                source,
                EssenceCommandUtil.line(
                        "Recipe links",
                        summary.outputItemCount()
                                + " outputs / "
                                + summary.ingredientLinkCount()
                                + " ingredient links"
                )
        );
        EssenceCommandUtil.send(
                source,
                EssenceCommandUtil.line(
                        "Entity loot",
                        summary.entityLootTableCount()
                                + " tables / "
                                + summary.dropSourceLinkCount()
                                + " item-source links"
                )
        );
        EssenceCommandUtil.send(
                source,
                EssenceCommandUtil.line(
                        "Block loot",
                        summary.blockLootTableCount()
                                + " tables / "
                                + summary.blockDropSourceLinkCount()
                                + " item-source links"
                )
        );
        EssenceCommandUtil.send(
                source,
                EssenceCommandUtil.line(
                        "Container loot",
                        summary.containerLootTableCount()
                                + " tables / "
                                + summary.containerLootSourceLinkCount()
                                + " item-source links"
                )
        );
        EssenceCommandUtil.send(
                source,
                EssenceCommandUtil.line(
                        "Fishing loot",
                        summary.fishingLootTableCount()
                                + " root tables / "
                                + summary.fishingLootSourceLinkCount()
                                + " item-source links"
                )
        );
        EssenceCommandUtil.send(
                source,
                EssenceCommandUtil.line(
                        "Trades",
                        summary.tradeListingCount()
                                + " listings / "
                                + summary.tradeOfferCount()
                                + " sampled offers / "
                                + summary.tradeProfessionTableCount()
                                + " profession-level tables"
                )
        );
        EssenceCommandUtil.send(
                source,
                EssenceCommandUtil.line(
                        "Advancements",
                        summary.consideredAdvancementCount()
                                + "/"
                                + summary.advancementCount()
                                + " considered / "
                                + summary.advancementTreeCount()
                                + " trees / "
                                + summary.advancementReferenceCount()
                                + " progression refs"
                )
        );
        EssenceCommandUtil.send(
                source,
                EssenceCommandUtil.line(
                        "Advancement refs",
                        summary.advancementItemReferenceCount()
                                + " items / "
                                + summary.advancementEntityReferenceCount()
                                + " entities / "
                                + summary.advancementDimensionReferenceCount()
                                + " dimensions"
                )
        );
        EssenceCommandUtil.send(
                source,
                EssenceCommandUtil.muted(
                        "Generated baseline saved; defaults and explicit overrides installed as one new live generation."
                )
        );
        return 1;
    }

    private static int exportProceduralValuation(
            CommandSourceStack source
    ) {
        EssenceCommandUtil.send(
                source,
                EssenceCommandUtil.title("Procedural Valuation CSV Export")
        );
        EssenceCommandUtil.send(
                source,
                EssenceCommandUtil.muted(
                        "Analyzing current loaded data and exporting it with installed live results; this may calculate but does not replace the cache or install a generation..."
                )
        );

        try {
            ProceduralValuationCsvExporter.ExportReport report =
                    ProceduralValuationCsvExporter.export(source.getServer());

            EssenceCommandUtil.send(
                    source,
                    EssenceCommandUtil.line(
                            "Items",
                            Integer.toString(report.itemCount())
                    )
            );
            EssenceCommandUtil.send(
                    source,
                    EssenceCommandUtil.line(
                            "Elapsed",
                            String.format(
                                    Locale.ROOT,
                                    "%.2f sec",
                                    report.elapsedMillis() / 1000.0
                            )
                    )
            );
            EssenceCommandUtil.send(
                    source,
                    EssenceCommandUtil.line(
                            "CSV",
                            report.path().toString()
                    )
            );
            EssenceCommandUtil.send(
                    source,
                    EssenceCommandUtil.muted(
                            "Analysis may differ from the saved baseline; live columns show installed results. Export did not replace the cache."
                    )
            );
            return 1;
        } catch (IOException | RuntimeException exception) {
            EssenceCommandUtil.fail(
                    source,
                    "CSV export failed: " + exception.getMessage()
            );
            return 0;
        }
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
                        "Resolved Essence"
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


    private static int showCrucible(
            CommandSourceStack source
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        HitResult hit = player.pick(8.0D, 0.0F, false);

        if (!(hit instanceof BlockHitResult blockHit)
                || hit.getType() != HitResult.Type.BLOCK
                || !(player.serverLevel().getBlockEntity(blockHit.getBlockPos())
                instanceof EssenceCrucibleBlockEntity crucible)) {
            EssenceCommandUtil.fail(
                    source,
                    "Look directly at an Essence Crucible within 8 blocks."
            );
            return 0;
        }

        EssenceCrucibleStructureStats stats = crucible.structureStats();

        EssenceCommandUtil.send(source, EssenceCommandUtil.title("Essence Crucible Debug"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line("Position", crucible.getBlockPos().getX() + ", " + crucible.getBlockPos().getY() + ", " + crucible.getBlockPos().getZ()));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line("Owner", crucible.ownerDisplayName()));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line("Access", crucible.accessMode().serializedName()));
        EssenceCommandUtil.send(source, EssenceCommandUtil.section("Input Lanes"));
        for (int slot = 0; slot < EssenceCrucibleBlockEntity.MAX_INPUT_SLOTS; slot++) {
            ItemStack input = crucible.getItem(slot);
            boolean active = crucible.isInputSlotActive(slot);
            if (input.isEmpty() && !active) {
                continue;
            }

            ItemEssenceMappingResult mapping = input.isEmpty()
                    ? null
                    : ItemEssenceMappingRegistry.resolve(input);
            String status = active ? "ACTIVE" : "OFFLINE";
            String contents = input.isEmpty()
                    ? "EMPTY"
                    : input.getCount() + "x " + input.getHoverName().getString();
            EssenceCommandUtil.send(
                    source,
                    EssenceCommandUtil.muted(
                            "  #" + (slot + 1) + " [" + status + "] "
                                    + contents + " | " + mappingSummary(mapping)
                    )
            );
        }

        long[] stored = crucible.storedEssenceSnapshot();
        for (int i = 0; i < stored.length; i++) {
            EssenceCommandUtil.send(
                    source,
                    EssenceCommandUtil.line(
                            EssenceCrucibleEssences.shortName(i),
                            EssenceCommandUtil.format(stored[i])
                    )
            );
        }

        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Owner shared reservoir",
                EssenceCommandUtil.format(crucible.totalStoredEssence())
                        + " / " + EssenceCommandUtil.format(crucible.effectiveReservoirCapacity())
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Structure",
                stats.usableItemSlots() + " active input slot(s), "
                        + stats.activePylonCount() + "/" + EssenceConfigManager.get().maxActivePylons() + " active pylon(s), "
                        + stats.automationConnectionPorts() + " automation port(s)"
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Transfer",
                EssenceCommandUtil.format(stats.transferRatePerSecond())
                        + "/sec, range "
                        + EssenceCommandUtil.formatDecimal(stats.transferRange())
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Dissolution",
                stats.simultaneousItemProcesses() + " item(s)/batch, "
                        + EssenceCommandUtil.formatDecimal(
                                20.0D / Math.max(1, stats.dissolutionTicksPerItem())
                        ) + " batch(es)/sec, progress "
                        + crucible.processingTicks() + "/"
                        + stats.dissolutionTicksPerItem()
                        + ", mode " + crucible.dissolutionMode().displayName()
        ));
        EssenceCrucibleStructureSnapshot structureSnapshot = crucible.structureSnapshot();
        if (!structureSnapshot.activePylons().isEmpty()) {
            EssenceCommandUtil.send(source, EssenceCommandUtil.section("Active Pylons"));
            int pylonNumber = 1;
            for (EssenceCrucibleStructureSnapshot.ActivePylon pylon : structureSnapshot.activePylons()) {
                EssencePylonContribution contribution = pylon.contribution();
                EssenceCommandUtil.send(
                        source,
                        EssenceCommandUtil.muted(
                                "  #" + pylonNumber++ + " "
                                        + pylon.pos().getX() + ","
                                        + pylon.pos().getY() + ","
                                        + pylon.pos().getZ()
                                        + " | " + pylon.focusDisplayName()
                                        + " | +" + EssenceCommandUtil.format(contribution.transferRatePerSecondBonus()) + "/sec"
                                        + " | +" + EssenceCommandUtil.formatDecimal(contribution.transferRangeBonus()) + " range"
                                        + " | +" + EssenceCommandUtil.format(contribution.reservoirCapacityBonus()) + " cap/family"
                                        + " | +" + EssenceCommandUtil.formatDecimal(contribution.dissolutionSpeedBonus() * 100.0D) + "% dissolve"
                                        + " | +" + contribution.simultaneousItemProcessesBonus() + " items/batch"
                        )
                );
            }
        }

        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Channel",
                crucible.isChanneling()
                        ? "ACTIVE -> " + crucible.channelingPlayerId()
                        : "INACTIVE"
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Distance",
                EssenceCommandUtil.formatDecimal(crucible.distanceTo(player))
                        + " / " + EssenceCommandUtil.formatDecimal(stats.transferRange())
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Break protection",
                "DISABLED - reservoir is player-owned"
        ));

        return 1;
    }

    private static int showPylon(
            CommandSourceStack source
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        HitResult hit = player.pick(8.0D, 0.0F, false);

        if (!(hit instanceof BlockHitResult blockHit)
                || hit.getType() != HitResult.Type.BLOCK
                || !(player.serverLevel().getBlockEntity(blockHit.getBlockPos())
                        instanceof EssencePylonBlockEntity pylon)) {
            EssenceCommandUtil.fail(
                    source,
                    "Look directly at an Essence Pylon within 8 blocks."
            );
            return 0;
        }

        pylon.refreshLink();
        EssencePylonContribution contribution = pylon.contribution();
        EssenceCommandUtil.send(source, EssenceCommandUtil.title("Essence Pylon Debug"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Position",
                pylon.getBlockPos().getX() + ", " + pylon.getBlockPos().getY() + ", " + pylon.getBlockPos().getZ()
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line("Owner", pylon.ownerDisplayName()));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line("Focus", pylon.focusDisplayName()));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Configured search",
                EssenceCommandUtil.formatDecimal(EssenceConfigManager.get().pylonRadius())
                        + " block radius, max " + EssenceConfigManager.get().maxActivePylons() + " active"
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Contribution",
                "+" + EssenceCommandUtil.format(contribution.transferRatePerSecondBonus()) + "/sec, "
                        + "+" + EssenceCommandUtil.formatDecimal(contribution.transferRangeBonus()) + " range, "
                        + "+" + EssenceCommandUtil.format(contribution.reservoirCapacityBonus()) + " capacity/family"
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Processing contribution",
                "+" + EssenceCommandUtil.formatDecimal(contribution.dissolutionSpeedBonus() * 100.0D)
                        + "% speed, +" + contribution.simultaneousItemProcessesBonus() + " item(s)/batch"
        ));

        if (pylon.linkedCruciblePos() == null) {
            EssenceCommandUtil.send(source, EssenceCommandUtil.line("Link", EssenceCommandUtil.warn("UNLINKED")));
            return 1;
        }

        var linkedPos = pylon.linkedCruciblePos();
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Linked Crucible",
                linkedPos.getX() + ", " + linkedPos.getY() + ", " + linkedPos.getZ()
                        + " (distance " + EssenceCommandUtil.formatDecimal(pylon.distanceToLinkedCrucible()) + ")"
        ));

        if (player.serverLevel().getBlockEntity(linkedPos) instanceof EssenceCrucibleBlockEntity crucible) {
            EssenceCrucibleStructureSnapshot snapshot = crucible.structureSnapshot();
            EssenceCrucibleStructureStats stats = snapshot.stats();
            EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                    "Active",
                    snapshot.containsPylon(pylon.getBlockPos()) ? EssenceCommandUtil.good("YES") : EssenceCommandUtil.warn("NO / PYLON LIMIT")
            ));
            EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                    "Linked structure",
                    stats.activePylonCount() + "/" + EssenceConfigManager.get().maxActivePylons() + " pylons, "
                            + EssenceCommandUtil.format(stats.transferRatePerSecond()) + "/sec, range "
                            + EssenceCommandUtil.formatDecimal(stats.transferRange())
            ));
            EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                    "Linked processing",
                    stats.simultaneousItemProcesses() + " item(s)/batch, "
                            + EssenceCommandUtil.formatDecimal(
                                    20.0D / Math.max(1, stats.dissolutionTicksPerItem())
                            ) + " batch(es)/sec"
            ));
        }

        return 1;
    }


    private static int showInfuser(
            CommandSourceStack source
    ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        HitResult hit = player.pick(8.0D, 0.0F, false);

        if (!(hit instanceof BlockHitResult blockHit)
                || hit.getType() != HitResult.Type.BLOCK
                || !(player.serverLevel().getBlockEntity(blockHit.getBlockPos())
                        instanceof EssenceInfuserBlockEntity infuser)) {
            EssenceCommandUtil.fail(
                    source,
                    "Look directly at an Essence Infuser within 8 blocks."
            );
            return 0;
        }

        infuser.refreshLink();
        EssenceDefinition sourceEssence = infuser.sourceEssence();
        EssenceDefinition targetEssence = infuser.targetEssence();
        var focusTier = infuser.focusTier();
        var profile = infuser.profile();

        EssenceCommandUtil.send(source, EssenceCommandUtil.title("Essence Infuser Debug"));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Position",
                infuser.getBlockPos().getX() + ", " + infuser.getBlockPos().getY() + ", " + infuser.getBlockPos().getZ()
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line("Owner", infuser.ownerDisplayName()));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Processing",
                infuser.processingEnabled() ? "ENABLED" : "STOPPED"
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Player channel",
                infuser.ownerId() != null
                        && EssenceCrucibleChannelService.isChanneling(infuser.ownerId())
                        ? "ACTIVE / INFUSER PAUSED"
                        : "INACTIVE"
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Focus",
                focusTier == null ? "NONE" : focusTier.displayName()
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Infusion Grade",
                profile.grade().displayName()
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Efficiency",
                EssenceCommandUtil.formatDecimal(profile.efficiencyPercent()) + "%"
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Infusion throughput",
                EssenceCommandUtil.format(infuser.infusionThroughputPerSecond()) + " Essence/sec"
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Link range",
                EssenceCommandUtil.formatDecimal(EssenceInfuserBalance.linkRange())
        ));

        BlockPos linked = infuser.linkedCruciblePos();
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Linked Crucible",
                linked == null
                        ? EssenceCommandUtil.warn("UNLINKED")
                        : Component.literal(
                                linked.getX() + ", " + linked.getY() + ", " + linked.getZ()
                                        + (infuser.isCurrentLinkValid() ? " [VALID]" : " [INVALID]")
                        )
        ));
        var currentRecipe = infuser.currentInfusionRecipe().orElse(null);
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Recipe",
                currentRecipe == null ? "NONE" : currentRecipe.id().toString()
        ));
        if (currentRecipe instanceof FocusInfusionRecipe recipe) {
            EssenceCommandUtil.send(source, EssenceCommandUtil.line("Mode", "FOCUS INFUSION"));
            EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                    "Target Focus", recipe.targetTier().displayName()
            ));
            EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                    "Installed requirement",
                    recipe.requiredInstalledTier() == null
                            ? "NONE"
                            : recipe.requiredInstalledTier().displayName()
            ));
            EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                    "Minimum / Essence",
                    EssenceCommandUtil.format(recipe.minimumPerAttributeEssence())
            ));
            EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                    "Progress",
                    EssenceCommandUtil.format(infuser.focusInfusionTotalContributed())
                            + " / " + EssenceCommandUtil.format(recipe.totalEssenceRequired())
            ));
            EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                    "Infusion rate",
                    EssenceCommandUtil.format(infuser.focusInfusionRatePerSecond()) + " Essence/sec"
            ));
            for (EssenceDefinition essence : FocusInfusionRecipe.coreAttributeEssences()) {
                EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                        essence.displayName(),
                        EssenceCommandUtil.format(infuser.focusInfusionContribution(essence))
                                + " / " + EssenceCommandUtil.format(recipe.minimumPerAttributeEssence())
                ));
            }
        } else if (currentRecipe instanceof EssentiumInfusionRecipe) {
            EssenceCommandUtil.send(source, EssenceCommandUtil.line("Mode", "ESSENTIUM"));
            EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                    "Source",
                    sourceEssence == null ? "NONE" : sourceEssence.displayName()
            ));
            EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                    "Target",
                    targetEssence == null ? "NONE" : targetEssence.displayName()
            ));
            EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                    "Source reservoir",
                    EssenceCommandUtil.format(infuser.sourceAmountAvailable())
                            + " / " + EssenceCommandUtil.format(infuser.sourceRequired()) + " required"
            ));
            EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                    "Carrier capacity",
                    EssenceCommandUtil.format(infuser.targetCarrierCapacity())
            ));
            EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                    "Progress",
                    infuser.processingTicks() + " / " + infuser.requiredProcessingTicks() + " ticks"
            ));
        } else if (currentRecipe != null) {
            EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                    "Mode", currentRecipe.workpieceMode().name()
            ));
            EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                    "Progress model", currentRecipe.progressModel().name()
            ));
            EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                    "Installed requirement",
                    currentRecipe.requiredInstalledFocusTier() == null
                            ? "NONE"
                            : currentRecipe.requiredInstalledFocusTier().displayName()
            ));
            EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                    "Progress",
                    infuser.processingTicks() + " / " + infuser.requiredProcessingTicks() + " ticks"
            ));
        } else {
            EssenceCommandUtil.send(source, EssenceCommandUtil.line("Mode", "NONE"));
        }
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "State",
                infuserStatusName(infuser.statusCode())
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Input",
                infuser.getItem(EssenceInfuserBlockEntity.INPUT_SLOT).isEmpty()
                        ? "EMPTY"
                        : infuser.getItem(EssenceInfuserBlockEntity.INPUT_SLOT).getCount() + "x "
                                + infuser.getItem(EssenceInfuserBlockEntity.INPUT_SLOT).getHoverName().getString()
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Output",
                infuser.getItem(EssenceInfuserBlockEntity.OUTPUT_SLOT).isEmpty()
                        ? "EMPTY"
                        : infuser.getItem(EssenceInfuserBlockEntity.OUTPUT_SLOT).getCount() + "x "
                                + infuser.getItem(EssenceInfuserBlockEntity.OUTPUT_SLOT).getHoverName().getString()
        ));
        return 1;
    }

    private static String infuserStatusName(int status) {
        return switch (status) {
            case EssenceInfuserBlockEntity.STATUS_UNLINKED -> "UNLINKED";
            case EssenceInfuserBlockEntity.STATUS_INVALID_SELECTION -> "INVALID SELECTION";
            case EssenceInfuserBlockEntity.STATUS_INSUFFICIENT_SOURCE -> "INSUFFICIENT SOURCE";
            case EssenceInfuserBlockEntity.STATUS_OUTPUT_BLOCKED -> "OUTPUT BLOCKED";
            case EssenceInfuserBlockEntity.STATUS_PROCESSING -> "PROCESSING";
            case EssenceInfuserBlockEntity.STATUS_INVALID_INPUT -> "INVALID INPUT";
            case EssenceInfuserBlockEntity.STATUS_STOPPED -> "STOPPED";
            case EssenceInfuserBlockEntity.STATUS_PLAYER_CHANNELING -> "PAUSED / PLAYER CHANNELING";
            case EssenceInfuserBlockEntity.STATUS_FOCUS_TIER_REQUIRED -> "FOCUS TIER REQUIRED";
            case EssenceInfuserBlockEntity.STATUS_FOCUS_MALFORMED -> "MALFORMED FOCUS DATA";
            default -> "IDLE";
        };
    }


    private static String mappingSummary(
            ItemEssenceMappingResult result
    ) {
        if (result == null || !result.mapped()) {
            return "NONE";
        }
        if (result.outputs().isEmpty()) {
            return "BLOCKED / empty output";
        }

        StringBuilder text = new StringBuilder();
        result.outputs().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(
                        java.util.Comparator.comparing(essence -> essence.id().toString())
                ))
                .forEach(entry -> {
                    if (text.length() > 0) {
                        text.append(", ");
                    }
                    text.append(entry.getKey().id().getPath())
                            .append('=')
                            .append(entry.getValue());
                });
        return text.toString();
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
                        "Generated defaults",
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
        EssenceCommandUtil.send(source, EssenceCommandUtil.title(
                "Equipment Baseline Preset Reference - Player Tier " + data.getTier().displayName()
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.muted(
                "Reference only: real Ascendance equipment uses its own item tier for native stats."
        ));
        EssenceCommandUtil.send(source, EssenceCommandUtil.muted(
                "Use /essence admin itemtier set <tier> while holding an item to test another item tier."
        ));

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
        if (usesProfile(held, EquipmentProfiles.MAGIC_CASTER.id())) {
            EquipmentWeaponService.MagicState magic = EquipmentWeaponService.evaluateMagic(player, held);
            EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                    "Magic",
                    "damage " + EssenceCommandUtil.formatDecimal(magic.finalDamage())
                            + " | cast " + EssenceCommandUtil.formatDecimal(magic.finalCastSpeed()) + "/sec"
            ));
        }

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

        EssenceCommandUtil.send(source, EssenceCommandUtil.line(
                "Damage reflection",
                EssenceCommandUtil.formatDecimal(stats.damageReflectionPercent()) + "%"
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
