package com.mistaboom.essence_ascendance.balance.engine;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.balance.config.BalanceOverrides;
import com.mistaboom.essence_ascendance.balance.config.BalanceSettings;
import com.mistaboom.essence_ascendance.balance.generated.BalancePerformance;
import com.mistaboom.essence_ascendance.valuation.ProceduralValuationEngine;
import com.mistaboom.essence_ascendance.valuation.ProceduralValuationResult;
import com.mistaboom.essence_ascendance.valuation.ValuationEvidenceSnapshot;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import static com.mistaboom.essence_ascendance.balance.engine.EvidenceFact.Subject.ITEM;
import static com.mistaboom.essence_ascendance.balance.engine.EvidenceFact.Subject.ENEMY;

/** Orchestrates one generation; ordinary profile loading never invokes this collector. */
public final class PackEvidenceCollector {
    private PackEvidenceCollector() { }

    public static PackEvidence collect(MinecraftServer server, BalanceSettings settings, BalanceOverrides overrides,
                                       com.mistaboom.essence_ascendance.valuation.GenerationDataSnapshot inputs,
                                       GenerationProviders runs) {
        inputs.requireCurrent(server);
        BalancePerformance.increment("evidence_collection_runs");
        BalancePerformance.flag("environment_analysis_rescanned", true);
        com.mistaboom.essence_ascendance.balance.quest.FtbQuestProvider.capture(inputs, runs, overrides);
        com.mistaboom.essence_ascendance.valuation.LootrProvider.capture(inputs, runs, overrides);
        Map<String, Integer> priorities = new TreeMap<>();
        overrides.facts().stream().filter(f -> f.kind() == BalanceOverrides.SubjectKind.PROVIDER)
                .sorted(Comparator.comparingInt(BalanceOverrides.FactOverride::priority).reversed()
                        .thenComparing(BalanceOverrides.FactOverride::id))
                .forEach(f -> priorities.putIfAbsent(f.selector(), f.priority()));
        List<PackEvidenceProvider> providers = PackEvidenceProviders.all().stream()
                .filter(provider -> runs.prepare("evidence", provider, GenerationProviders.disabled(provider.id(), overrides))).toList();
        providers.forEach(provider -> priorities.putIfAbsent(provider.id(), provider.priority()));
        EvidenceSink sink = new EvidenceSink(priorities);
        for (PackEvidenceProvider provider : providers) {
            int start = sink.size();
            runs.collect("evidence", provider, "before_acquisition", sink::staged,
                    staged -> provider.beforeAcquisition(inputs, settings, staged), sink::merge);
            var emitted = sink.emittedSince(start);
            runs.emitted("evidence", provider, emitted.facts(), emitted.sources(), emitted.minimumConfidence());
        }
        ProceduralValuationEngine.prepareGeneration(acquisitionInputs(overrides, sink), inputs);
        com.mistaboom.essence_ascendance.balance.economy.EconomyGenerator.prepareProduction(inputs, overrides, runs);
        List<ProceduralValuationResult> valuations;
        try (var phase = BalancePerformance.phase("procedural_valuation")) {
            valuations = ProceduralValuationEngine.evaluateAll(server);
        }
        try (var phase = BalancePerformance.phase("routing_diagnostics")) {
            inputs.recordRoutingDiagnostics(valuations);
            BalancePerformance.count("routing_diagnostic_items", valuations.size());
        }
        ValuationEvidenceSnapshot snapshot;
        try (var phase = BalancePerformance.phase("valuation_evidence_snapshot")) {
            snapshot = ValuationEvidenceSnapshot.collect(server, valuations);
        }
        com.mistaboom.essence_ascendance.valuation.LootrProvider.recordSources(inputs, runs, snapshot);
        QuestProjection questProjection = projectQuests(inputs, valuations, snapshot);
        snapshot = questProjection.snapshot();
        PackEvidenceContext context = new PackEvidenceContext(server, settings, overrides, valuations, snapshot, inputs);
        try (var phase = BalancePerformance.phase("acquisition_facts")) {
            collectAcquisition(context, sink, questProjection.placements());
            collectCropEligibility(context, sink);
            collectVanillaMechanics(context, sink);
        }
        Map<String, EquipmentReference> baseEquipment;
        try (var phase = BalancePerformance.phase("equipment_measurement")) {
            baseEquipment = collectEquipment(context, sink);
        }
        List<EnemyReference> baseEnemies;
        try (var phase = BalancePerformance.phase("enemy_measurement")) {
            baseEnemies = collectEnemies(context, sink);
        }
        for (PackEvidenceProvider provider : providers) {
            int start = sink.size();
            runs.collect("evidence", provider, "collect", sink::staged, staged -> provider.collect(context, staged), sink::merge);
            var emitted = sink.emittedSince(start);
            runs.emitted("evidence", provider, emitted.facts(), emitted.sources(), emitted.minimumConfidence());
        }
        try (var phase = BalancePerformance.phase("resolve_evidence_frontiers")) {
        inputs.limitations().forEach(sink::warn);
        runs.warnings().forEach(sink::warn);
        applyOverrides(context, sink);
        Map<String, ResourceEvidence> resources = com.mistaboom.essence_ascendance.valuation.NativeTreeRenewal.enrich(context, resolveResources(context, sink), sink);
        resources = com.mistaboom.essence_ascendance.valuation.NativeCropRenewal.enrich(context, resources, sink);
        resources = NativeStructureBlockLoot.enrich(context, resources, sink);
        resources = com.mistaboom.essence_ascendance.valuation.ExDeorumManualAccess.enrich(context, resources, sink);
        resources = com.mistaboom.essence_ascendance.valuation.NativePassiveDrops.enrich(context, resources, sink);
        inputs.configurationConstrainedItems(ConfigurationAccess.remainingFiniteClaims(resources, inputs.configurationConstrainedItems()));
        List<EquipmentReference> equipment = new ArrayList<>(resolveEquipment(baseEquipment, resources, sink, settings));
        equipment.add(emptyHandMiningReference());
        explainOutliers(equipment, sink, settings);
        List<EnemyReference> enemies = resolveEnemies(baseEnemies, sink, settings);
        Map<ProgressionBand, Map<CapabilityAxis, Double>> frontiers = RobustFrontiers.build(equipment, settings.outlierPolicy().name());
        List<CapabilityEvidence> capabilities = resolveCapabilities(sink, resources);
        inputs.competitiveCapabilities(CompetitiveCapabilities.collect(context, resources, equipment, enemies, capabilities, providers, runs, sink));
        // Capability adapters are probed above, after the earlier acquisition warning snapshot.
        runs.warnings().forEach(sink::warn);
        for (ProgressionBand band : ProgressionBand.values()) {
            Map<CapabilityAxis, Double> axes = new EnumMap<>(CapabilityAxis.class); axes.putAll(frontiers.getOrDefault(band, Map.of()));
            for (CapabilityEvidence capability : capabilities) if (capability.reachable() && capability.stage().ordinal() <= band.ordinal())
                capability.axes().forEach((axis, value) -> axes.merge(axis, value, Math::max));
            frontiers.put(band, axes);
        }
        if (snapshot.summary().getOrDefault("unsupported_recipes", 0L) > 0)
            sink.warn("Unsupported recipes: " + snapshot.summary().get("unsupported_recipes") + "; use recipe-family providers for dynamic or machine outputs");
        sink.warn("Custom spells, affixes, set bonuses, dynamic attributes, quest gates and machine rates require providers or factual overrides when they are not exposed by loaded data");
        BalancePerformance.count("evidence_conflicts", sink.conflicts());
        BalancePerformance.count("evidence_resources", resources.size());
        BalancePerformance.count("evidence_equipment", equipment.size());
        BalancePerformance.count("evidence_enemies", enemies.size());
        BalancePerformance.count("evidence_providers", providers.size());
        snapshot.summary().forEach((key, count) -> BalancePerformance.count("acquisition_" + key, count));
        return new PackEvidence(resources, equipment, enemies, frontiers, sink.facts(), sink.warnings(), snapshot.summary(), capabilities);
        }
    }

    /** Persist the gameplay selector so saved-evidence calibration needs no current block registry. */
    private static void collectCropEligibility(PackEvidenceContext context, EvidenceSink sink) {
        var ids = context.acquisition().sources().values().stream().flatMap(List::stream)
                .filter(source -> source.kind() == AcquisitionSource.Kind.PLAYER_ACTION
                        || source.kind() == AcquisitionSource.Kind.FARMING)
                .map(AcquisitionSource::id).distinct().sorted().toList();
        for (String id : ids) {
            var key = ResourceLocation.tryParse(id);
            if (key == null) continue;
            var block = BuiltInRegistries.BLOCK.getOptional(key);
            if (block.isEmpty()) continue;
            var attempt = OptionalIntegration.attempt("crop_eligibility", id,
                    () -> com.mistaboom.essence_ascendance.equipment.PlayerAttributedBlockHarvestService.isEligibleCropBlock(block.get()));
            if (!attempt.succeeded()) {
                sink.warn("Crop eligibility " + id + " excluded: " + attempt.failure());
                continue;
            }
            sink.add(new EvidenceFact(EvidenceFact.Subject.BLOCK, id,
                    com.mistaboom.essence_ascendance.equipment.PlayerAttributedBlockHarvestService.CROP_ELIGIBILITY_PROPERTY,
                    EvidenceFact.Value.flag(attempt.value().orElseThrow()), "crop_eligibility", EvidenceFact.Origin.OBSERVED,
                    1, 0, ProgressionBand.ENTRY, List.of(),
                    "Shared player harvest state selector evaluated at maximum age; tags, exclusions and native crop behavior preserved. This proves state eligibility, not planting access, block-entity inventory behavior or a positive payout."));
        }
    }

    private static BalanceOverrides acquisitionInputs(BalanceOverrides human, EvidenceSink initial) {
        List<BalanceOverrides.FactOverride> facts = new ArrayList<>(human.facts());
        for (EvidenceFact fact : initial.facts()) {
            BalanceOverrides.SubjectKind kind = switch (fact.subject()) {
                case ITEM -> BalanceOverrides.SubjectKind.ITEM; case BLOCK -> BalanceOverrides.SubjectKind.BLOCK;
                case RECIPE -> BalanceOverrides.SubjectKind.RECIPE; case TAG -> BalanceOverrides.SubjectKind.ITEM_TAG;
                case SOURCE -> BalanceOverrides.SubjectKind.SOURCE; default -> null;
            };
            if (kind == null || fact.property().startsWith("axis.")) continue;
            Object value = switch (fact.value().type()) {
                case NUMBER -> fact.value().number(); case FLAG -> fact.value().flag(); case TEXT -> fact.value().text();
            };
            if (value instanceof String text && List.of("stage", "availability", "automation").contains(fact.property())) value = text.toLowerCase(Locale.ROOT);
            Map<String, Object> fields = new TreeMap<>(); fields.put(fact.property(), value); fields.put("confidence", fact.confidence());
            fields.put("reason", fact.reason());
            facts.add(new BalanceOverrides.FactOverride("provider:" + fact.provider() + ":" + fact.key(), kind,
                    fact.subjectId(), fact.priority(), fields, fact.provider(), 0));
        }
        return new BalanceOverrides(facts, human.exactValues());
    }

    private static void collectAcquisition(PackEvidenceContext context, EvidenceSink sink, Map<String, AcquisitionProgressionGraph.Placement> placements) {
        for (ProceduralValuationResult value : context.valuations()) {
            String id = value.itemId().toString();
            List<AcquisitionSource> sources = context.acquisition().sources().getOrDefault(id, List.of());
            ProgressionBand stage = inferStage(value, BuiltInRegistries.ITEM.get(value.itemId()), sources);
            var placement = placements.get(id);
            if (placement != null) stage = placement.stage();
            boolean attainable = value.modeledAcquisition() || placement != null;
            boolean renewable = value.renewabilityMultiplier() < 1 || sources.stream().anyMatch(AcquisitionSource::renewable);
            Availability availability = !attainable ? Availability.UNKNOWN
                    : renewable ? Availability.RENEWABLE_MANUAL : Availability.FINITE;
            Automation automation = renewable ? Automation.PLAYER_GATED : Automation.NONE;
            if (sources.stream().anyMatch(s -> s.kind() == AcquisitionSource.Kind.MOB_DROP && s.renewable())) automation = Automation.SCALABLE;
            fact(sink, ITEM, id, "attainable", EvidenceFact.Value.flag(attainable), "acquisition", value.confidence(), stage,
                    "Bounded acquisition solver requires an external starting source; recipe-only cycles do not seed reachability");
            fact(sink, ITEM, id, "stage", EvidenceFact.Value.text(stage.name()), "acquisition", value.confidence(), stage,
                    "Cheapest modeled acquisition, direct sources, recipe depth and typed tool tier; unrelated late loot does not force a gate");
            fact(sink, ITEM, id, "resource_value", EvidenceFact.Value.number(value.totalValue()), "acquisition", value.confidence(), stage,
                    "Opportunity value from shared valuation graph; dissolution pressure is applied separately");
            fact(sink, ITEM, id, "availability", EvidenceFact.Value.text(availability.name()), "acquisition", .62, stage, "Loaded sources and renewability evidence");
            fact(sink, ITEM, id, "automation", EvidenceFact.Value.text(automation.name()), "acquisition", .5, stage, "Broad source class; production rates remain unknown");
        }
    }

    private record QuestProjection(ValuationEvidenceSnapshot snapshot, Map<String, AcquisitionProgressionGraph.Placement> placements) { }
    private static QuestProjection projectQuests(com.mistaboom.essence_ascendance.valuation.GenerationDataSnapshot inputs,
            List<ProceduralValuationResult> values, ValuationEvidenceSnapshot snapshot) {
        if (inputs.quests() == com.mistaboom.essence_ascendance.balance.quest.QuestEvidence.EMPTY) return new QuestProjection(snapshot, Map.of());
        try (var phase = BalancePerformance.phase("shared_quest_acquisition_progression")) {
            Map<String, AcquisitionProgressionGraph.Placement> seeds = new TreeMap<>();
            for (var value : values) if (value.modeledAcquisition()) seeds.put(value.itemId().toString(), AcquisitionProgressionGraph.Placement.ordinary(
                    inferStage(value, BuiltInRegistries.ITEM.get(value.itemId()), snapshot.sources().getOrDefault(value.itemId().toString(), List.of())),
                    value.renewabilityMultiplier() < 1 || snapshot.sources().getOrDefault(value.itemId().toString(), List.of()).stream().anyMatch(AcquisitionSource::renewable)));
            var solved = AcquisitionProgressionGraph.solve(seeds, inputs.production(), inputs.quests());
            // Preserve finite/exclusive quest lineage for joint equipment/automation setup proofs.
            // A crafted descendant must not masquerade as an unconstrained recipe source.
            inputs.configurationConstrainedItems(solved.items().entrySet().stream()
                    .filter(entry -> entry.getValue().finite() || !entry.getValue().exclusiveClaims().isEmpty())
                    .map(Map.Entry::getKey).collect(java.util.stream.Collectors.toSet()));
            List<com.mistaboom.essence_ascendance.balance.economy.ProductionGraph.Process> resolved = new ArrayList<>();
            for (var process : inputs.production().processes()) {
                if (!process.metadata().containsKey("quest_id")) { resolved.add(process); continue; }
                Map<String, String> metadata = new TreeMap<>(process.metadata());
                var placement = solved.quests().get(metadata.get("quest_id"));
                boolean supported = placement != null && process.confidence() >= .85;
                metadata.put("acquisition_complete", Boolean.toString(supported));
                if (!supported) metadata.put("conservation_complete", "false");
                metadata.put("repeat_supply_proven", Boolean.toString(supported && placement.renewable()));
                if ("renewable".equals(metadata.get("renewability")) && (placement == null || !placement.renewable()))
                    metadata.put("renewability", placement != null && placement.finite() ? "finite" : "unknown");
                metadata.put("availability_stage", supported ? placement.stage().name() : "unresolved");
                metadata.put("acquisition_semantics", "Marginal reward opportunity, not guaranteed stock or unrestricted material conversion");
                resolved.add(new com.mistaboom.essence_ascendance.balance.economy.ProductionGraph.Process(process.id(), process.family(), process.inputs(),
                        process.outputs(), process.processingTicks(), process.externalCost(), process.provider(), process.confidence(), metadata));
            }
            inputs.production(new com.mistaboom.essence_ascendance.balance.economy.ProductionGraph(resolved, inputs.production().warnings()));
            Map<String, List<AcquisitionSource>> sources = new TreeMap<>(snapshot.sources());
            for (var q : inputs.quests().quests()) {
                var placement = solved.quests().get(q.id());
                if (placement == null) continue;
                for (var r : q.rewards()) if (r.supported()) {
                    List<AcquisitionSource> entries = new ArrayList<>(sources.getOrDefault(r.item(), List.of()));
                    entries.add(new AcquisitionSource("essence_ascendance:quest/" + q.id() + "/" + r.id(), AcquisitionSource.Kind.QUEST_REWARD,
                            placement.stage(), r.count() * r.probability(), q.repeatable() && placement.renewable(), false, 0, .85,
                            q.tasks().stream().flatMap(t -> t.items().stream()).distinct().toList(),
                            "Reward acquisition opportunity; " + r.type() + "; scope=" + r.scope() + "; group=" + r.group()
                                    + "; repeatable=" + q.repeatable() + "; cooldown_seconds=" + q.cooldownSeconds()
                                    + "; dependencies=" + q.prerequisites() + "; enforced=" + q.enforced()
                                    + "; consumes=" + q.tasks().stream().filter(t -> t.consumed()).map(t -> t.id() + ":" + t.count()).toList()
                                    + "; player activity required; no passive rate, population multiplier or capability-strength inference"));
                    sources.put(r.item(), List.copyOf(entries));
                }
            }
            var diagnostics = new com.google.gson.Gson().toJsonTree(solved).getAsJsonObject();
            inputs.questProgression(diagnostics);
            BalancePerformance.count("quest_progression_rule_evaluations", solved.ruleEvaluations());
            BalancePerformance.count("quest_reachable_completions", solved.quests().size());
            return new QuestProjection(new ValuationEvidenceSnapshot(java.util.Collections.unmodifiableMap(sources), snapshot.recipeInputs(),
                    snapshot.entityProgression(), snapshot.summary()), solved.items());
        }
    }

    private static ProgressionBand inferStage(ProceduralValuationResult value, Item item, List<AcquisitionSource> sources) {
        if (item instanceof ArmorItem armor) {
            var material = armor.getMaterial();
            if (material.equals(ArmorMaterials.LEATHER)) return ProgressionBand.ENTRY;
            if (material.equals(ArmorMaterials.CHAIN)) return ProgressionBand.EARLY;
            if (material.equals(ArmorMaterials.IRON) || material.equals(ArmorMaterials.GOLD) || material.equals(ArmorMaterials.TURTLE)) return ProgressionBand.MID;
            if (material.equals(ArmorMaterials.DIAMOND)) return ProgressionBand.LATE;
            if (material.equals(ArmorMaterials.NETHERITE)) return ProgressionBand.APEX;
        }
        if (item instanceof TieredItem tool && tool.getTier() instanceof Tiers tier) {
            return switch (tier) {
                case WOOD -> ProgressionBand.ENTRY;
                case STONE -> ProgressionBand.EARLY;
                case IRON, GOLD -> ProgressionBand.MID;
                case DIAMOND -> ProgressionBand.LATE;
                case NETHERITE -> ProgressionBand.APEX;
            };
        }
        int depth = value.recipeChoice().map(ProceduralValuationResult.RecipeChoice::depth).orElse(0);
        int direct = sources.stream().filter(s -> s.kind() != AcquisitionSource.Kind.RECIPE)
                .mapToInt(s -> s.stage().ordinal()).min().orElse(0);
        int derived = value.recipeChoice().isPresent() ? Math.min(3, Math.max(0, depth - 1) / 2) : direct;
        // Semantic advancement evidence implies access; this remains an inference, never a mandatory gameplay gate.
        derived = Math.max(derived, (int) Math.floor(value.inferredProgressionScore() * 4));
        if (item instanceof ArmorItem) {
            // Opportunity cost differentiates rare materials without comparing Ascendance's own armor values.
            derived = Math.max(derived, value.totalValue() >= 2000 ? 4 : value.totalValue() >= 700 ? 3 : value.totalValue() >= 300 ? 2 : 1);
        }
        return ProgressionBand.at(derived);
    }

    private static void collectVanillaMechanics(PackEvidenceContext context, EvidenceSink sink) {
        // Explicit vanilla adapter: these are known liquid-interaction outputs, not a price exception in gameplay.
        for (var block : List.of(Blocks.COBBLESTONE, Blocks.STONE, Blocks.BASALT)) {
            String id = BuiltInRegistries.ITEM.getKey(block.asItem()).toString();
            fact(sink, ITEM, id, "availability", EvidenceFact.Value.text(Availability.EFFECTIVELY_INFINITE.name()),
                    "vanilla_liquid_generation", .99, ProgressionBand.EARLY, "Non-consumed fluid source plus player breaking enables unbounded generation");
            fact(sink, ITEM, id, "automation", EvidenceFact.Value.text(Automation.SCALABLE.name()),
                    "vanilla_liquid_generation", .95, ProgressionBand.EARLY, "Parallel generators are scalable; vanilla requires harvesting setup and no exact rate is assumed");
        }
    }

    private static Map<String, EquipmentReference> collectEquipment(PackEvidenceContext context, EvidenceSink sink) {
        Map<String, EquipmentReference> result = new TreeMap<>();
        for (ProceduralValuationResult value : context.valuations()) {
            String id = value.itemId().toString();
            var attempt = OptionalIntegration.attempt("equipment_components", id, () -> {
                EvidenceSink staged = sink.staged();
                Item item = BuiltInRegistries.ITEM.get(value.itemId());
                ProgressionBand stage = inferStage(value, item, context.acquisition().sources().getOrDefault(id, List.of()));
                EquipmentReference reference = measureEquipment(new ItemStack(item), stage, value.modeledAcquisition());
                if (reference != null) measuredAxisFacts(staged, ITEM, id, reference.axes(), "equipment_components", reference.confidence(), stage,
                        "Effective slot-valid stack modifiers, including item defaults; default tool behavior; conditional effects require a provider");
                return new EquipmentMeasurement(reference, staged);
            });
            if (attempt.succeeded()) {
                var completed = attempt.value().orElseThrow();
                sink.merge(completed.facts());
                if (completed.reference() != null) result.put(id, completed.reference());
            } else {
                sink.warn("Unsupported equipment measurement " + id + ": " + attempt.failure() + "; affected axes excluded; supply corrected attributes through a provider");
                result.put(id, new EquipmentReference(id, "unknown", ProgressionBand.ENTRY, Map.of(),
                        List.of("invalid_component_measurement"), value.modeledAcquisition(), false, 0, "Invalid or unsupported default components"));
            }
        }
        return result;
    }
    private record EquipmentMeasurement(EquipmentReference reference, EvidenceSink facts) { }

    /** Empty hands have native destroy speed even when this environment proves no early tool recipe. */
    static EquipmentReference emptyHandMiningReference() {
        double speed = ItemStack.EMPTY.getDestroySpeed(Blocks.DIRT.defaultBlockState());
        if (!Double.isFinite(speed) || speed <= 0) throw new IllegalStateException("Invalid native empty-hand destroy speed");
        return new EquipmentReference("minecraft:air", "mainhand_tool", ProgressionBand.ENTRY,
                Map.of(CapabilityAxis.MINING_SPEED, speed), List.of("innate_empty_hand; hand-breakable targets only"),
                true, true, 1, "Loaded native empty-stack destroy speed on a hand-breakable block; no tool acquisition, harvest level, durability, throughput or scripted interaction access claimed");
    }

    /** Uses the same stack attribute fallback and slot filtering as equipped gameplay items. */
    static EquipmentReference measureEquipment(ItemStack stack, ProgressionBand stage, boolean reachable) {
        Item item = stack.getItem();
        ItemAttributeModifiers attributes = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        if (attributes.modifiers().isEmpty()) attributes = item.getDefaultAttributeModifiers();
        Tool tool = stack.get(DataComponents.TOOL);
        boolean known = item instanceof ArmorItem || item instanceof TieredItem || item instanceof BowItem || item instanceof CrossbowItem
                || item instanceof TridentItem || item instanceof MaceItem || item instanceof ShieldItem || item instanceof ElytraItem;
        if (!known && attributes.modifiers().isEmpty() && tool == null) return null;
        String id = BuiltInRegistries.ITEM.getKey(item).toString();
        List<String> capabilities = new ArrayList<>();
        Map<CapabilityAxis, Double> axes = new EnumMap<>(CapabilityAxis.class);
        String slot = "mainhand_melee"; EquipmentSlot equipmentSlot = EquipmentSlot.MAINHAND;
        if (item instanceof ArmorItem armor) {
            equipmentSlot = armor.getEquipmentSlot(); slot = equipmentSlot.getName();
            axes.put(CapabilityAxis.ARMOR, attribute(stack, Attributes.ARMOR, equipmentSlot, 0));
            axes.put(CapabilityAxis.TOUGHNESS, attribute(stack, Attributes.ARMOR_TOUGHNESS, equipmentSlot, 0));
        } else if (item instanceof ElytraItem) {
            slot = "chest"; axes.put(CapabilityAxis.GLIDING, 1.0); capabilities.add("gliding");
        } else if (item instanceof ShieldItem) {
            slot = "offhand"; axes.put(CapabilityAxis.BLOCKING, 1.0); capabilities.add("directional_blocking");
        } else {
            double damage = attribute(stack, Attributes.ATTACK_DAMAGE, equipmentSlot, 1);
            double rate = attribute(stack, Attributes.ATTACK_SPEED, equipmentSlot, 4);
            if (item instanceof BowItem || item instanceof CrossbowItem) {
                slot = item instanceof BowItem ? "mainhand_bow" : "mainhand_crossbow";
                damage = item instanceof BowItem ? 6 : 9; rate = item instanceof BowItem ? 1 : .8;
                axes.put(CapabilityAxis.RANGE, 32.0); capabilities.add("projectile_ammunition");
                capabilities.add("vanilla_projectile_estimate_requires_provider_for_custom_behavior");
            }
            axes.put(CapabilityAxis.BURST_DAMAGE, damage); axes.put(CapabilityAxis.ATTACK_RATE, Math.max(.05, rate));
            axes.put(CapabilityAxis.SUSTAINED_DAMAGE, damage * Math.max(.05, rate));
        }
        if (tool != null) {
            double mining = Math.max(tool.defaultMiningSpeed(), stack.getDestroySpeed(Blocks.STONE.defaultBlockState()));
            mining = Math.max(mining, stack.getDestroySpeed(Blocks.OAK_LOG.defaultBlockState()));
            mining = Math.max(mining, stack.getDestroySpeed(Blocks.DIRT.defaultBlockState()));
            axes.put(CapabilityAxis.MINING_SPEED, mining);
            int harvest = stack.isCorrectToolForDrops(Blocks.OBSIDIAN.defaultBlockState()) ? 3
                    : stack.isCorrectToolForDrops(Blocks.DIAMOND_ORE.defaultBlockState()) ? 2
                    : stack.isCorrectToolForDrops(Blocks.IRON_ORE.defaultBlockState()) ? 1 : 0;
            axes.put(CapabilityAxis.HARVEST_LEVEL, (double) harvest);
            capabilities.add("tool_component");
        }
        axes.put(CapabilityAxis.DURABILITY, (double) stack.getMaxDamage());
        if (item.getEnchantmentValue() > 0) capabilities.add("enchantable:" + item.getEnchantmentValue());
        axes.put(CapabilityAxis.REACH, attribute(stack, Attributes.BLOCK_INTERACTION_RANGE, equipmentSlot, 4.5));
        double confidence = known ? .85 : .48;
        if (!BuiltInRegistries.ITEM.getKey(item).getNamespace().equals("minecraft")) {
            confidence = Math.min(confidence, .58); capabilities.add("unknown_dynamic_behavior");
        }
        return new EquipmentReference(id, slot, stage, axes, capabilities, reachable, false, confidence, "effective default stack behavior");
    }

    private static double attribute(ItemStack stack, Holder<Attribute> attribute, EquipmentSlot slot, double base) {
        double[] operations = {0, 0, 1};
        stack.forEachModifier(slot, (holder, modifier) -> {
            if (!holder.equals(attribute)) return;
            double amount = modifier.amount();
            switch (modifier.operation()) {
                case ADD_VALUE -> operations[0] += amount;
                case ADD_MULTIPLIED_BASE -> operations[1] += amount;
                case ADD_MULTIPLIED_TOTAL -> operations[2] *= 1 + amount;
            }
        });
        double value = (base + operations[0]) * (1 + operations[1]) * operations[2];
        if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("Invalid equipment attribute result: " + value);
        return value;
    }

    private static List<EnemyReference> collectEnemies(PackEvidenceContext context, EvidenceSink sink) {
        List<EnemyReference> enemies = new ArrayList<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE.stream().sorted(Comparator.comparing(t -> BuiltInRegistries.ENTITY_TYPE.getKey(t).toString())).toList()) {
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            var attempt = OptionalIntegration.attempt("entity_attributes", id.toString(), () -> {
            EvidenceSink staged = sink.staged();
            if (!DefaultAttributes.hasSupplier(type)) return new EnemyMeasurement(null, staged);
            if (type.getCategory() != MobCategory.MONSTER && type != EntityType.ENDER_DRAGON && type != EntityType.WITHER) return new EnemyMeasurement(null, staged);
            @SuppressWarnings("unchecked") EntityType<? extends LivingEntity> living = (EntityType<? extends LivingEntity>) type;
            AttributeSupplier attributes = DefaultAttributes.getSupplier(living);
            if (attributes == null) return new EnemyMeasurement(null, staged);
            double health = base(attributes, Attributes.MAX_HEALTH, 20), armor = base(attributes, Attributes.ARMOR, 0);
            double damage = base(attributes, Attributes.ATTACK_DAMAGE, 0), toughness = base(attributes, Attributes.ARMOR_TOUGHNESS, 0);
            EnemyReference.Encounter encounter = type == EntityType.ENDER_DRAGON || type == EntityType.WITHER ? EnemyReference.Encounter.BOSS
                    : health >= 150 ? EnemyReference.Encounter.APEX : health >= 50 ? EnemyReference.Encounter.ELITE : EnemyReference.Encounter.ROUTINE;
            ProgressionBand stage = encounter == EnemyReference.Encounter.BOSS || encounter == EnemyReference.Encounter.APEX ? ProgressionBand.APEX
                    : ProgressionBand.at(Math.max(1, (int) Math.floor(context.acquisition().entityProgression().getOrDefault(id.toString(), 0.0) * 4)));
            Map<CapabilityAxis, Double> axes = new EnumMap<>(CapabilityAxis.class);
            axes.put(CapabilityAxis.EFFECTIVE_HEALTH, health); axes.put(CapabilityAxis.ARMOR, armor); axes.put(CapabilityAxis.TOUGHNESS, toughness);
            axes.put(CapabilityAxis.BURST_DAMAGE, damage); axes.put(CapabilityAxis.GROUND_SPEED, base(attributes, Attributes.MOVEMENT_SPEED, .25));
            // A disposable entity is never added to the world or ticked. Query the native reward
            // instead of inventing an XP-to-health exchange rate for progression calibration.
            if (context.server() != null) {
                var experience = OptionalIntegration.attempt("entity_experience", id.toString(), () ->
                        com.mistaboom.essence_ascendance.valuation.TradeSamplingScope.sample(0x4E41544956455850L ^ id.toString().hashCode(), () -> {
                    var sample = type.create(context.server().overworld());
                    double reward = sample instanceof LivingEntity sampled
                            ? sampled.getExperienceReward(context.server().overworld(), null) : 0;
                    if (!Double.isFinite(reward) || reward < 0) throw new IllegalArgumentException("Invalid native experience reward " + reward);
                    return reward;
                }));
                experience.value().filter(reward -> reward > 0).ifPresent(reward -> axes.put(CapabilityAxis.EXPERIENCE, reward));
                if (!experience.succeeded()) staged.warn("No native XP observation for " + id + ": " + experience.failure()
                        + "; supported entity attributes remain available; Attunement reports the generated fallback");
            }
            boolean vanilla = id.getNamespace().equals("minecraft");
            List<String> unknown = vanilla ? List.of("Attack cadence, equipment rolls and encounter frequency not inferred from base attributes")
                    : List.of("Dynamic phases, shields, immunities, regeneration and attack cadence require a provider");
            measuredAxisFacts(staged, ENEMY, id.toString(), axes, "entity_attributes", vanilla ? .8 : .45, stage,
                    "Registered default attributes; entity is never spawned for measurement");
            return new EnemyMeasurement(new EnemyReference(id.toString(), encounter, stage, axes, true, vanilla ? .8 : .45, unknown,
                    "Hostile category or native boss; health-based elite/apex classification is an inference"), staged);
            });
            if (attempt.succeeded()) {
                var completed = attempt.value().orElseThrow();
                sink.merge(completed.facts());
                if (completed.reference() != null) enemies.add(completed.reference());
            } else {
                sink.warn("Unsupported entity attributes " + id + ": " + attempt.failure() + "; affected axes excluded until a provider supplies measurable values");
                enemies.add(new EnemyReference(id.toString(), EnemyReference.Encounter.UNKNOWN, ProgressionBand.APEX, Map.of(), false, 0,
                        List.of("invalid_default_attributes"), "Invalid or unsupported default attributes"));
            }
        }
        return enemies;
    }
    private record EnemyMeasurement(EnemyReference reference, EvidenceSink facts) { }

    /** Validate every native axis before its record is merged into the shared generation evidence. */
    private static void measuredAxisFacts(EvidenceSink staged, EvidenceFact.Subject subject, String id,
                                          Map<CapabilityAxis, Double> axes, String provider, double confidence,
                                          ProgressionBand stage, String reason) {
        for (var entry : axes.entrySet()) {
            if (!Double.isFinite(entry.getValue()) || entry.getValue() < 0)
                throw new IllegalArgumentException("Invalid measured " + entry.getKey() + " for " + id + ": " + entry.getValue());
            fact(staged, subject, id, "axis." + entry.getKey().name(), EvidenceFact.Value.number(entry.getValue()),
                    provider, confidence, stage, reason);
        }
    }

    private static double base(AttributeSupplier supplier, Holder<Attribute> attribute, double fallback) {
        return supplier.hasAttribute(attribute) ? supplier.getBaseValue(attribute) : fallback;
    }

    private static void fact(EvidenceSink sink, EvidenceFact.Subject subject, String id, String property,
                             EvidenceFact.Value value, String provider, double confidence, ProgressionBand stage, String reason) {
        EvidenceFact.Origin origin = provider.equals("equipment_components") || provider.equals("entity_attributes")
                || provider.equals("vanilla_liquid_generation") ? EvidenceFact.Origin.OBSERVED : EvidenceFact.Origin.INFERRED;
        sink.add(new EvidenceFact(subject, id, property, value, provider, origin,
                confidence, 0, stage, List.of(), reason));
    }

    private static void applyOverrides(PackEvidenceContext context, EvidenceSink sink) {
        for (var override : context.overrides().facts()) {
            if (override.kind() == BalanceOverrides.SubjectKind.PROVIDER || override.kind() == BalanceOverrides.SubjectKind.RECIPE
                    || override.kind() == BalanceOverrides.SubjectKind.RECIPE_FAMILY) continue;
            if (override.kind() == BalanceOverrides.SubjectKind.SOURCE) {
                for (String key : List.of("disabled", "attainable")) override.flag(key).ifPresent(flag ->
                        sink.add(new EvidenceFact(EvidenceFact.Subject.SOURCE, override.selector(), key, EvidenceFact.Value.flag(flag),
                                "override:" + override.id(), EvidenceFact.Origin.OVERRIDE, override.number("confidence").orElse(1.0),
                                override.priority(), null, List.of(), override.source() + ":" + override.line())));
            }
            List<String> targets = new ArrayList<>(); EvidenceFact.Subject subject = ITEM;
            if (override.kind() == BalanceOverrides.SubjectKind.CAPABILITY) {
                subject = EvidenceFact.Subject.CAPABILITY; targets.add(override.selector());
            } else if (override.kind() == BalanceOverrides.SubjectKind.ENEMY) {
                subject = ENEMY;
                BuiltInRegistries.ENTITY_TYPE.keySet().stream().map(Object::toString).filter(id -> matches(id, override.selector())).sorted().forEach(targets::add);
            } else {
                for (Item item : BuiltInRegistries.ITEM) {
                    String id = BuiltInRegistries.ITEM.getKey(item).toString(); boolean match;
                    if (override.kind() == BalanceOverrides.SubjectKind.ITEM_TAG) {
                        ResourceLocation tag = ResourceLocation.tryParse(override.selector().replaceFirst("^#", ""));
                        match = tag != null && new ItemStack(item).is(TagKey.create(Registries.ITEM, tag));
                    } else if (override.kind() == BalanceOverrides.SubjectKind.BLOCK_TAG) {
                        ResourceLocation tag = ResourceLocation.tryParse(override.selector().replaceFirst("^#", ""));
                        match = item instanceof BlockItem block && tag != null && block.getBlock().defaultBlockState().is(TagKey.create(Registries.BLOCK, tag));
                    } else if (override.kind() == BalanceOverrides.SubjectKind.BLOCK) {
                        match = item instanceof BlockItem block && matches(BuiltInRegistries.BLOCK.getKey(block.getBlock()).toString(), override.selector());
                    } else if (override.kind() == BalanceOverrides.SubjectKind.SOURCE) {
                        match = matches(id, override.selector()) || context.acquisition().sources().getOrDefault(id, List.of()).stream()
                                .anyMatch(source -> matches(source.id(), override.selector()));
                    } else match = matches(id, override.selector());
                    if (match) targets.add(id);
                }
            }
            if (targets.isEmpty()) sink.warn("Override " + override.id() + " at " + override.source() + ":" + override.line()
                    + " matched no registered subjects: " + override.selector());
            for (String target : targets.stream().sorted().toList()) {
                for (var entry : override.values().entrySet()) {
                    if (override.kind() == BalanceOverrides.SubjectKind.SOURCE && !matches(target, override.selector())
                            && List.of("disabled", "attainable").contains(entry.getKey())) continue;
                    EvidenceFact.Value value;
                    if (entry.getValue() instanceof Number number) value = EvidenceFact.Value.number(number.doubleValue());
                    else if (entry.getValue() instanceof Boolean flag) value = EvidenceFact.Value.flag(flag);
                    else if (entry.getValue() instanceof List<?> list) value = EvidenceFact.Value.text(String.join(",", list.stream().map(Object::toString).toList()));
                    else value = EvidenceFact.Value.text(entry.getValue().toString());
                    sink.add(new EvidenceFact(subject, target, canonicalProperty(entry.getKey()), value, "override:" + override.id(),
                            EvidenceFact.Origin.OVERRIDE, override.number("confidence").orElse(1.0), override.priority(),
                            null, override.strings("dependencies"), override.text("reason").orElse(override.source() + ":" + override.line())));
                }
            }
        }
    }

    private static String canonicalProperty(String key) {
        return switch (key) {
            case "health" -> "axis.EFFECTIVE_HEALTH"; case "armor" -> "axis.ARMOR"; case "toughness" -> "axis.TOUGHNESS";
            case "damage" -> "axis.BURST_DAMAGE"; case "attack_speed" -> "axis.ATTACK_RATE";
            case "mining_speed" -> "axis.MINING_SPEED"; case "harvest_level" -> "axis.HARVEST_LEVEL";
            case "durability" -> "axis.DURABILITY";
            case "flight" -> "axis.FLIGHT"; case "flying_speed_compatible" -> "axis.ABILITIES_FLYING_SPEED";
            case "area_mining" -> "axis.AREA_MINING"; case "vein_mining" -> "axis.VEIN_MINING";
            default -> key;
        };
    }

    private static Map<String, ResourceEvidence> resolveResources(PackEvidenceContext context, EvidenceSink sink) {
        Map<String, ResourceEvidence> resources = new TreeMap<>();
        for (var value : context.valuations()) {
            String id = value.itemId().toString(); List<String> warnings = new ArrayList<>();
            boolean reachable = sink.flag(ITEM, id, "attainable", value.modeledAcquisition()) && !excluded(sink, ITEM, id);
            boolean external = !value.itemId().getNamespace().equals(EssenceAscendance.MOD_ID);
            ProgressionBand stage = enumeration(ProgressionBand.class, sink.text(ITEM, id, "stage", "ENTRY"));
            Availability availability = enumeration(Availability.class, sink.text(ITEM, id, "availability", "UNKNOWN"));
            Automation automation = enumeration(Automation.class, sink.text(ITEM, id, "automation", "UNKNOWN"));
            if (sink.get(ITEM, id, "renewable") != null) {
                boolean renewable = sink.flag(ITEM, id, "renewable", false);
                if (renewable && availability == Availability.FINITE) availability = Availability.RENEWABLE_MANUAL;
                if (!renewable && (availability == Availability.RENEWABLE_MANUAL || availability == Availability.RENEWABLE_AUTOMATED)) availability = Availability.FINITE;
            }
            double confidence = sink.number(ITEM, id, "confidence", value.confidence());
            double economics = sink.number(ITEM, id, "resource_value", value.totalValue());
            List<AcquisitionSource> sources = new ArrayList<>(context.acquisition().sources().getOrDefault(id, List.of()));
            if (reachable) initialAcquisition(sink, id).ifPresent(sources::add);
            if (availability == Availability.EFFECTIVELY_INFINITE || sink.flag(ITEM, id, "passive_generation", false)) {
                boolean passive = sink.flag(ITEM, id, "passive_generation", false);
                if (passive) { automation = Automation.PASSIVE; availability = Availability.RENEWABLE_AUTOMATED; }
                var declaration = sink.get(ITEM, id, passive ? "passive_generation" : "availability");
                boolean explicit = declaration != null && declaration.origin() == EvidenceFact.Origin.OVERRIDE;
                var off = new SourceAvailability.Timer(SourceAvailability.Applicability.OFF, 0, List.of());
                var access = new SourceAvailability("classified:" + id,
                        explicit ? SourceAvailability.Category.CONDITIONAL_RENEWABLE : SourceAvailability.Category.UNKNOWN,
                        SourceAvailability.Scope.SHARED, List.of(), List.of(),
                        List.of(explicit ? "Explicit author renewable-source declaration" : "Renewability classification only; operating setup is not proved"),
                        off, off, explicit, 1, 1, explicit ? List.of() : List.of("Native seed/fluid/tool/station acquisition not established by classification"));
                sources.add(new AcquisitionSource("classified:" + id, passive ? AcquisitionSource.Kind.PASSIVE_GENERATION : AcquisitionSource.Kind.INFINITE_BULK,
                        stage, 1, true, sink.get(ITEM, id, "throughput") != null, sink.number(ITEM, id, "throughput", 0),
                        confidence, List.of(), "Provider or factual override classified renewable generation; setup and output rate may be unknown", access));
            }
            if (!reachable) warnings.add("unreachable_or_excluded");
            if (!external) warnings.add("endogenous_excluded_from_external_baselines");
            if (confidence < context.settings().warningConfidence()) warnings.add("low_confidence");
            if (sink.flag(ITEM, id, "quest_gated", false) && sink.get(ITEM, id, "stage").origin() != EvidenceFact.Origin.OVERRIDE)
                warnings.add("quest_gate_without_stage_override");
            resources.put(id, new ResourceEvidence(id, stage, availability, automation, reachable, external,
                    economics, confidence, sources, warnings));
        }
        return resources;
    }

    /** Typed finite starting witness supplied before acquisition. It never supplies renewal, rates or prices. */
    static java.util.Optional<AcquisitionSource> initialAcquisition(EvidenceSink sink, String item) {
        EvidenceFact source = sink.get(ITEM, item, "initial_source");
        EvidenceFact count = sink.get(ITEM, item, "initial_count");
        if (source == null && count == null) return java.util.Optional.empty();
        if (source == null || count == null || source.value().type() != EvidenceFact.ValueType.TEXT
                || count.value().type() != EvidenceFact.ValueType.NUMBER || source.value().text().isBlank()
                || source.value().text().length() > 512 || !source.provider().equals(count.provider())
                || source.stage() == null || source.stage() != count.stage()
                || source.confidence() < .5 || count.confidence() < .5
                || count.value().number() <= 0 || count.value().number() != Math.rint(count.value().number())
                || count.value().number() > 9_007_199_254_740_991d) {
            sink.warn("Incomplete/invalid finite initial-source witness excluded for " + item);
            return java.util.Optional.empty();
        }
        SourceAvailability.Scope scope = SourceAvailability.Scope.SHARED;
        EvidenceFact scopeFact = sink.get(ITEM, item, "initial_scope");
        if (scopeFact != null) {
            try {
                if (scopeFact.value().type() != EvidenceFact.ValueType.TEXT || !scopeFact.provider().equals(source.provider())
                        || scopeFact.confidence() < .5) throw new IllegalArgumentException("Invalid scope provenance");
                scope = SourceAvailability.Scope.valueOf(scopeFact.value().text().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException invalid) {
                sink.warn("Invalid finite initial-source scope excluded for " + item);
                return java.util.Optional.empty();
            }
        }
        String identity = source.value().text() + ":" + item;
        var off = new SourceAvailability.Timer(SourceAvailability.Applicability.OFF, 0, List.of());
        var availability = new SourceAvailability(identity, SourceAvailability.Category.FINITE_SHARED, scope,
                List.of(), List.of(), List.of("finite starting bundle"), off, off, true, 1, count.value().number(), List.of());
        return java.util.Optional.of(new AcquisitionSource(identity,
                AcquisitionSource.Kind.WORLD_GENERATION, source.stage(), count.value().number(),
                false, false, 0, Math.min(source.confidence(), count.confidence()), source.dependencies(),
                source.reason() + "; finite initial bundle; no renewal or measured throughput", availability));
    }

    /** Package seam exercises provider-only subjects without building a server or spawning entities. */
    static List<EquipmentReference> resolveEquipment(Map<String, EquipmentReference> base, Map<String, ResourceEvidence> resources,
                                                     EvidenceSink sink, BalanceSettings settings) {
        Map<String, EquipmentReference> candidates = new TreeMap<>(base);
        for (EvidenceFact fact : sink.facts()) {
            if (fact.subject() != ITEM || !(fact.property().startsWith("axis.") || fact.property().equals("slot"))) continue;
            String id = fact.subjectId();
            if (candidates.containsKey(id)) continue;
            ResourceLocation location = ResourceLocation.tryParse(id);
            ResourceEvidence resource = resources.get(id);
            if (location == null || !BuiltInRegistries.ITEM.containsKey(location) || resource == null) {
                sink.warn("Equipment facts ignored for " + id + ": no registered item with acquisition evidence");
                continue;
            }
            // A plain registered Item may expose all its behavior through a provider. Its name proves no slot or access.
            candidates.put(id, new EquipmentReference(id, "unknown", resource.stage(), Map.of(),
                    List.of("provider_supplied_measurements"), resource.reachable(), false, 0, "Provider-only equipment"));
        }
        List<EquipmentReference> resolved = new ArrayList<>();
        for (var ref : candidates.values()) {
            ResourceEvidence resource = resources.get(ref.itemId());
            if (resource == null) {
                sink.warn("Equipment reference ignored for " + ref.itemId() + ": no acquisition evidence");
                continue;
            }
            String slot = sink.text(ITEM, ref.itemId(), "slot", ref.slot());
            EvidenceFact slotFact = sink.get(ITEM, ref.itemId(), "slot");
            if (slotFact != null && (slotFact.value().type() != EvidenceFact.ValueType.TEXT || !BalanceOverrides.EQUIPMENT_SLOTS.contains(slot)))
                throw new IllegalArgumentException("Invalid equipment slot fact for " + ref.itemId() + " from " + slotFact.provider()
                        + "; use a documented reference slot");
            Map<CapabilityAxis, Double> axes = resolvedAxes(sink, ITEM, ref.itemId(), ref.axes());
            boolean knownSlot = BalanceOverrides.EQUIPMENT_SLOTS.contains(slot) && !slot.equals("body");
            boolean include = resource.reachable() && resource.external() && knownSlot && !axes.isEmpty()
                    && !excluded(sink, ITEM, ref.itemId()) && sink.flag(ITEM, ref.itemId(), "include_reference", true);
            // An explicit sustained estimate may model reload/uptime; only derive it when no provider supplied one.
            if (axes.containsKey(CapabilityAxis.BURST_DAMAGE) && axes.containsKey(CapabilityAxis.ATTACK_RATE)
                    && sink.get(ITEM, ref.itemId(), "axis.SUSTAINED_DAMAGE") == null)
                axes.put(CapabilityAxis.SUSTAINED_DAMAGE, finiteProduct(axes.get(CapabilityAxis.BURST_DAMAGE), axes.get(CapabilityAxis.ATTACK_RATE), ref.itemId()));
            else if (axes.containsKey(CapabilityAxis.BURST_DAMAGE) && axes.containsKey(CapabilityAxis.ATTACK_RATE)
                    && sink.get(ITEM, ref.itemId(), "axis.SUSTAINED_DAMAGE").provider().equals("equipment_components"))
                axes.put(CapabilityAxis.SUSTAINED_DAMAGE, finiteProduct(axes.get(CapabilityAxis.BURST_DAMAGE), axes.get(CapabilityAxis.ATTACK_RATE), ref.itemId()));
            double confidence = referenceConfidence(sink, ITEM, ref.itemId(), ref.confidence());
            String reason = !resource.external() ? "Endogenous Ascendance output" : !resource.reachable() ? "No reachable acquisition route or excluded by override"
                    : !knownSlot ? "Missing explicit supported player equipment slot; names do not establish a slot"
                    : axes.isEmpty() ? "No measurable equipment axes" : include ? "Attainable external equipment; robust frontier limits unsupported extremes" : "Excluded by factual override";
            if (!knownSlot && !slot.equals("body")) sink.warn("Equipment " + ref.itemId() + " excluded: provide a supported explicit slot fact");
            if (include && confidence < settings.warningConfidence()) sink.warn("Low-confidence equipment " + ref.itemId() + ": custom behavior needs a provider");
            List<String> capabilities = new ArrayList<>(ref.capabilities());
            if (sink.get(ITEM, ref.itemId(), "enchantability") != null) {
                capabilities.removeIf(flag -> flag.startsWith("enchantable:"));
                capabilities.add("enchantable:" + sink.number(ITEM, ref.itemId(), "enchantability", 0));
            }
            String added = sink.text(ITEM, ref.itemId(), "capabilities", ""); if (!added.isBlank()) capabilities.addAll(List.of(added.split(",")));
            resolved.add(new EquipmentReference(ref.itemId(), slot, resource.stage(), axes, capabilities,
                    resource.reachable(), include, confidence, reason));
        }
        return resolved;
    }

    static List<EnemyReference> resolveEnemies(List<EnemyReference> base, EvidenceSink sink, BalanceSettings settings) {
        Map<String, EnemyReference> candidates = new TreeMap<>();
        base.forEach(ref -> candidates.put(ref.entityId(), ref));
        for (EvidenceFact fact : sink.facts()) {
            if (fact.subject() != ENEMY || candidates.containsKey(fact.subjectId())) continue;
            String id = fact.subjectId(); ResourceLocation location = ResourceLocation.tryParse(id);
            if (location == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(location)) {
                sink.warn("Enemy facts ignored for " + id + ": no registered entity type");
                continue;
            }
            candidates.put(id, new EnemyReference(id, EnemyReference.Encounter.UNKNOWN, ProgressionBand.APEX, Map.of(), false, 0,
                    List.of("Only supplied measurements are known; missing attack cadence, phases and defenses remain unknown"), "Provider-only encounter"));
        }
        List<EnemyReference> resolved = new ArrayList<>();
        for (var ref : candidates.values()) {
            Map<CapabilityAxis, Double> axes = resolvedAxes(sink, ENEMY, ref.entityId(), ref.axes());
            boolean needsExplicitEvidence = ref.axes().isEmpty();
            EvidenceFact stageFact = sink.get(ENEMY, ref.entityId(), "stage");
            EvidenceFact confidenceFact = sink.get(ENEMY, ref.entityId(), "confidence");
            boolean explicitEvidence = !needsExplicitEvidence || (stageFact != null && stageFact.value().type() == EvidenceFact.ValueType.TEXT
                    && confidenceFact != null && confidenceFact.value().type() == EvidenceFact.ValueType.NUMBER && confidenceFact.value().number() > 0
                    && sink.flag(ENEMY, ref.entityId(), "include_reference", false));
            boolean included = !ref.entityId().startsWith(EssenceAscendance.MOD_ID + ":") && !excluded(sink, ENEMY, ref.entityId())
                    && sink.flag(ENEMY, ref.entityId(), "attainable", true) && explicitEvidence
                    && axes.getOrDefault(CapabilityAxis.EFFECTIVE_HEALTH, 0.0) > 0
                    && sink.flag(ENEMY, ref.entityId(), "include_reference", ref.included());
            double confidence = referenceConfidence(sink, ENEMY, ref.entityId(), ref.confidence());
            if (included && confidence < settings.warningConfidence()) sink.warn("Low-confidence enemy " + ref.entityId() + ": dynamic combat mechanics unmodeled");
            EnemyReference.Encounter encounter = enumeration(EnemyReference.Encounter.class, sink.text(ENEMY, ref.entityId(), "classification", ref.encounter().name()));
            ProgressionBand stage = enumeration(ProgressionBand.class, sink.text(ENEMY, ref.entityId(), "stage", ref.stage().name()));
            String reason = ref.reason();
            if (needsExplicitEvidence && (!explicitEvidence || axes.getOrDefault(CapabilityAxis.EFFECTIVE_HEALTH, 0.0) <= 0)) {
                reason = "Provider-only enemy requires explicit include_reference=true, stage, confidence>0 and positive health";
                sink.warn("Enemy " + ref.entityId() + " excluded: " + reason);
            } else if (!included) reason = "Endogenous, unattainable or excluded by factual override";
            resolved.add(new EnemyReference(ref.entityId(), encounter, stage, axes, included, confidence, ref.unknownMechanics(), reason));
        }
        return resolved;
    }

    private static double referenceConfidence(EvidenceSink sink, EvidenceFact.Subject subject, String id, double fallback) {
        double derived = fallback > 0 ? fallback : 1;
        boolean measured = false;
        for (CapabilityAxis axis : CapabilityAxis.values()) {
            EvidenceFact fact = sink.get(subject, id, "axis." + axis);
            if (fact != null) { derived = Math.min(derived, fact.confidence()); measured = true; }
        }
        EvidenceFact slot = sink.get(subject, id, "slot");
        if (slot != null) derived = Math.min(derived, slot.confidence());
        double confidence = sink.number(subject, id, "confidence", measured ? derived : fallback);
        if (!Double.isFinite(confidence) || confidence < 0 || confidence > 1)
            throw new IllegalArgumentException("Reference confidence outside 0..1 for " + id);
        return confidence;
    }

    private static double finiteProduct(double left, double right, String id) {
        double value = left * right;
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Equipment damage-rate product overflow for " + id);
        return value;
    }

    private static Map<CapabilityAxis, Double> resolvedAxes(EvidenceSink sink, EvidenceFact.Subject subject, String id, Map<CapabilityAxis, Double> base) {
        Map<CapabilityAxis, Double> axes = new EnumMap<>(CapabilityAxis.class); axes.putAll(base);
        for (CapabilityAxis axis : CapabilityAxis.values()) {
            var fact = sink.get(subject, id, "axis." + axis);
            if (fact == null) continue;
            if (fact.value().type() == EvidenceFact.ValueType.TEXT || fact.value().number() < 0)
                throw new IllegalArgumentException("Invalid nonnegative numeric/flag axis " + axis + " for " + id + " from " + fact.provider());
            axes.put(axis, fact.value().type() == EvidenceFact.ValueType.FLAG ? fact.value().flag() ? 1.0 : 0.0 : fact.value().number());
        }
        return axes;
    }
    private static boolean excluded(EvidenceSink sink, EvidenceFact.Subject subject, String id) {
        return List.of("disabled", "creative_only", "administrative", "joke").stream().anyMatch(key -> sink.flag(subject, id, key, false));
    }
    private static List<CapabilityEvidence> resolveCapabilities(EvidenceSink sink, Map<String, ResourceEvidence> resources) {
        Map<String, EvidenceFact.Subject> subjects = new TreeMap<>();
        for (EvidenceFact fact : sink.facts()) {
            if (fact.subject() == EvidenceFact.Subject.CAPABILITY || (fact.subject() == ITEM
                    && List.of("axis.FLIGHT", "axis.GLIDING", "axis.AREA_MINING", "axis.VEIN_MINING", "axis.TELEPORTATION").contains(fact.property())))
                subjects.put(fact.subjectId(), fact.subject());
        }
        List<CapabilityEvidence> capabilities = new ArrayList<>();
        subjects.forEach((id, subject) -> {
            ResourceEvidence resource = resources.get(id);
            boolean reachable = subject == ITEM ? resource != null && resource.reachable() && resource.external()
                    : sink.flag(subject, id, "attainable", true);
            reachable &= !excluded(sink, subject, id);
            ProgressionBand stage = enumeration(ProgressionBand.class, sink.text(subject, id, "stage",
                    resource == null ? "APEX" : resource.stage().name()));
            Map<CapabilityAxis, Double> axes = resolvedAxes(sink, subject, id, Map.of());
            double confidence = sink.number(subject, id, "confidence", resource == null ? .6 : resource.confidence());
            // Both flight and its speed contract must be reliable; a confident acquisition fact
            // cannot launder an uncertain provider mechanism claim.
            for (var axis : List.of(CapabilityAxis.FLIGHT, CapabilityAxis.ABILITIES_FLYING_SPEED)) {
                EvidenceFact claim = sink.get(subject, id, "axis." + axis.name());
                if (claim != null) confidence = Math.min(confidence, claim.confidence());
            }
            capabilities.add(new CapabilityEvidence(id, stage, axes, reachable, confidence,
                    sink.text(subject, id, "reason", "Transformative capability from registered evidence; default unknown global access is apex")));
        });
        return capabilities;
    }
    private static void explainOutliers(List<EquipmentReference> equipment, EvidenceSink sink, BalanceSettings settings) {
        for (ProgressionBand band : ProgressionBand.values()) {
            Map<String, List<Double>> populations = new TreeMap<>();
            for (EquipmentReference reference : equipment) if (reference.included() && reference.reachable() && reference.stage() == band)
                reference.axes().forEach((axis, value) -> { if (value > 0) populations.computeIfAbsent(reference.slot() + ":" + axis, ignored -> new ArrayList<>()).add(value); });
            Map<String, Double> ceilings = new TreeMap<>();
            populations.forEach((key, values) -> {
                if (values.size() >= 3) ceilings.put(key, Math.max(RobustFrontiers.percentile(values, .5, "INCLUDE_ATTAINABLE") * 4,
                        RobustFrontiers.percentile(values, .25, "INCLUDE_ATTAINABLE") * 8));
            });
            for (EquipmentReference reference : equipment) {
                if (!reference.included() || !reference.reachable() || reference.stage() != band) continue;
                for (var axis : reference.axes().entrySet()) {
                    double ceiling = ceilings.getOrDefault(reference.slot() + ":" + axis.getKey(), 0.0);
                    if (ceiling > 0 && axis.getValue() > ceiling) {
                        sink.warn("Suspicious outlier " + reference.itemId() + "/" + axis.getKey() + " in " + band
                                + ": value " + axis.getValue() + ", robust ceiling " + ceiling + ", policy " + settings.outlierPolicy());
                    }
                }
            }
        }
    }
    private static boolean matches(String id, String selector) {
        return id.matches(java.util.regex.Pattern.quote(selector).replace("*", "\\E.*\\Q"));
    }
    private static <E extends Enum<E>> E enumeration(Class<E> type, String text) { return Enum.valueOf(type, text.toUpperCase(Locale.ROOT)); }
}
