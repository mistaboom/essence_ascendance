package com.mistaboom.essence_ascendance.balance.engine;

import com.mistaboom.essence_ascendance.balance.config.BalanceOverrides;
import com.mistaboom.essence_ascendance.balance.config.BalanceSettings;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;

import static com.mistaboom.essence_ascendance.balance.engine.EvidenceFact.Subject.ENEMY;
import static com.mistaboom.essence_ascendance.balance.engine.EvidenceFact.Subject.ITEM;

/** Native catalog measurements remain useful without inventing survival access. */
public final class ProviderReferenceTest {
    private static int assertions;
    private static final BalanceSettings SETTINGS = BalanceSettings.defaults();
    public static void main(String[] args) {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        effectiveDefaultEquipment();
        nativeWeaponFamilies();
        independentCapabilityBands();
        nativePayoutEligibility();
        var hand = PackEvidenceCollector.emptyHandMiningReference();
        check(hand.reachable() && hand.usableReference(), "Innate empty hand remains directly usable");
        Map<String, EquipmentReference> measured = new java.util.TreeMap<>();
        Map<String, ResourceEvidence> resources = new java.util.TreeMap<>();
        for (var item : BuiltInRegistries.ITEM) {
            var row = PackEvidenceCollector.measureEquipment(new net.minecraft.world.item.ItemStack(item), ProgressionBand.ENTRY, false);
            if (row == null) continue;
            measured.put(row.itemId(), row);
            resources.put(row.itemId(), new ResourceEvidence(row.itemId(), ProgressionBand.ENTRY, Availability.UNKNOWN,
                    Automation.UNKNOWN, false, true, 1, .6, List.of(), List.of("unexamined access")));
        }
        var rows = PackEvidenceCollector.resolveEquipment(measured, resources, new EvidenceSink(), SETTINGS);
        check(rows.stream().filter(row -> row.itemId().equals("minecraft:netherite_hoe")).findFirst().orElseThrow()
                        .stage().ordinal() >= ProgressionBand.MID.ordinal(),
                "Declared organic-material mining rules keep a high-speed hoe out of the weakest tool band");
        check(rows.stream().filter(EquipmentReference::included).allMatch(row -> !row.reachable() && row.usableReference()
                && row.capabilities().contains(EquipmentReference.ESTIMATED_CATALOG_REFERENCE)), "Catalog eligibility must never become access proof");
        var frontiers = RobustFrontiers.build(rows);
        for (var band : ProgressionBand.values()) for (var axis : List.of(CapabilityAxis.ATTACK_RATE,
                CapabilityAxis.SUSTAINED_DAMAGE, CapabilityAxis.MINING_SPEED, CapabilityAxis.DURABILITY))
            check(frontiers.get(band).getOrDefault(axis, 0.0) > 0, "Native catalog has required " + axis + " reference at " + band);
        var first = rows.stream().filter(EquipmentReference::included).findFirst().orElseThrow();
        var unmarked = new EquipmentReference(first.itemId(), first.slot(), first.stage(), first.axes(), List.of(), false, true, 1, "unknown");
        check(!unmarked.usableReference() && RobustFrontiers.build(List.of(unmarked)).values().stream().allMatch(Map::isEmpty),
                "Unknown access without explicit estimate marker cannot enter frontier");
        var reversed = new java.util.ArrayList<>(rows); java.util.Collections.reverse(reversed);
        check(RobustFrontiers.build(reversed).equals(frontiers), "Catalog order cannot change reference frontier");
        var duplicate = new java.util.ArrayList<>(rows); duplicate.addAll(rows);
        var banded = PackEvidenceCollector.catalogBands(duplicate);
        check(banded.stream().allMatch(row -> rows.stream().filter(prior -> prior.itemId().equals(row.itemId())).findFirst().orElseThrow().stage() == row.stage()),
                "Duplicated registry rows cannot alter strength bands");
        var enemies = PackEvidenceCollector.collectEnemies(new EvidenceSink());
        check(!enemies.isEmpty(), "Registered hostile default attributes supply enemy references");
        check(enemies.stream().allMatch(enemy -> !enemy.axes().containsKey(CapabilityAxis.ATTACK_RATE)
                && !enemy.axes().containsKey(CapabilityAxis.EXPERIENCE)), "Enemy attributes cannot invent cadence or XP");
        new PrintStream(new FileOutputStream(FileDescriptor.out)).println("ProviderReferenceTest: " + assertions + " native catalog checks PASS");
    }
    private static void nativeWeaponFamilies() {
        for (var item : List.of(Items.WOODEN_SWORD, Items.TRIDENT, Items.MACE))
            check(PackEvidenceCollector.measureEquipment(new net.minecraft.world.item.ItemStack(item), ProgressionBand.ENTRY, true)
                            .slot().equals("mainhand_melee"),
                    "Native weapon declarations establish the melee family: " + item);
        for (var item : List.of(Items.WOODEN_PICKAXE, Items.NETHERITE_PICKAXE, Items.WOODEN_SHOVEL, Items.NETHERITE_HOE))
            check(PackEvidenceCollector.measureEquipment(new net.minecraft.world.item.ItemStack(item), ProgressionBand.ENTRY, true)
                            .slot().equals("mainhand_tool"),
                    "Attack attributes do not turn an ordinary mining tool into the sword reference: " + item);
        var hoe = new net.minecraft.world.item.ItemStack(Items.NETHERITE_HOE);
        check(hoe.getDestroySpeed(net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()) == 1
                        && hoe.getDestroySpeed(net.minecraft.world.level.block.Blocks.OAK_LOG.defaultBlockState()) == 1
                        && hoe.getDestroySpeed(net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState()) == 1,
                "Regression fixture: the three broad probes miss the native hoe's actual specialty");
        check(PackEvidenceCollector.measureEquipment(hoe, ProgressionBand.ENTRY, true).axes().get(CapabilityAxis.MINING_SPEED) == 9,
                "Native hoe rules retain the actual material speed on their matching targets");
        var specialized = new net.minecraft.world.item.ItemStack(Items.WOODEN_PICKAXE);
        specialized.set(net.minecraft.core.component.DataComponents.TOOL, new net.minecraft.world.item.component.Tool(
                List.of(net.minecraft.world.item.component.Tool.Rule.overrideSpeed(
                        List.of(net.minecraft.world.level.block.Blocks.OAK_LEAVES), 17.5f)), 1, 1));
        check(PackEvidenceCollector.measureEquipment(specialized, ProgressionBand.ENTRY, true).axes().get(CapabilityAxis.MINING_SPEED) == 17.5,
                "Any native rule target outside the fixed probes can declare its real peak speed");
        var oldTags = new java.util.HashMap<net.minecraft.tags.TagKey<Item>, List<net.minecraft.core.Holder<Item>>>();
        BuiltInRegistries.ITEM.getTags().forEach(tag -> oldTags.put(tag.getFirst(), tag.getSecond().stream().toList()));
        try {
            for (var tag : List.of(net.minecraft.tags.ItemTags.SWORDS, net.minecraft.tags.TagKey.create(
                    net.minecraft.core.registries.Registries.ITEM, ResourceLocation.parse("c:tools/melee_weapon")))) {
                var tags = new java.util.HashMap<>(oldTags);
                tags.put(tag, List.of(Items.DIAMOND_PICKAXE.builtInRegistryHolder()));
                BuiltInRegistries.ITEM.bindTags(tags);
                check(PackEvidenceCollector.measureEquipment(new net.minecraft.world.item.ItemStack(Items.DIAMOND_PICKAXE),
                                ProgressionBand.ENTRY, true).slot().equals("mainhand_melee"),
                        "A loaded functional weapon tag may declare a dual-purpose tool: " + tag.location());
            }
        } finally { BuiltInRegistries.ITEM.bindTags(oldTags); }
        var tools = new java.util.ArrayList<EquipmentReference>();
        for (int i = 0; i < 5; i++) tools.add(new EquipmentReference("test:tool_band_" + i, "mainhand_tool", ProgressionBand.ENTRY,
                Map.of(CapabilityAxis.MINING_SPEED, 2.0 + i * 2, CapabilityAxis.SUSTAINED_DAMAGE, 20.0 - i * 4,
                        CapabilityAxis.ATTACK_RATE, 1.0), List.of(), true, true, 1, "native tool axes"));
        var banded = PackEvidenceCollector.catalogBands(tools);
        for (int i = 0; i < tools.size(); i++) {
            String id = "test:tool_band_" + i;
            check(banded.stream().filter(row -> row.itemId().equals(id)).findFirst().orElseThrow().stage() == ProgressionBand.at(i),
                    "A tool's primary comparison band follows mining speed, not its incidental attack damage");
        }
    }

    private static void independentCapabilityBands() {
        var rows = new java.util.ArrayList<EquipmentReference>();
        for (int i = 0; i < 5; i++) rows.add(new EquipmentReference("test:weapon_" + i, "mainhand_melee", ProgressionBand.ENTRY,
                Map.of(CapabilityAxis.SUSTAINED_DAMAGE, 4.0 + i * 4, CapabilityAxis.ATTACK_RATE, 1.0,
                        CapabilityAxis.BURST_DAMAGE, 4.0 + i * 4, CapabilityAxis.MINING_SPEED, 1.0),
                List.of(EquipmentReference.ESTIMATED_CATALOG_REFERENCE), false, true, 1, "paired native measurements"));
        rows.add(new EquipmentReference("test:fast_tool", "mainhand_melee", ProgressionBand.ENTRY,
                Map.of(CapabilityAxis.SUSTAINED_DAMAGE, 4.0, CapabilityAxis.ATTACK_RATE, 1.0,
                        CapabilityAxis.BURST_DAMAGE, 4.0, CapabilityAxis.MINING_SPEED, 20.0),
                List.of(EquipmentReference.ESTIMATED_CATALOG_REFERENCE), false, true, 1, "independent mining measurement"));
        var banded = PackEvidenceCollector.catalogBands(rows);
        check(banded.stream().filter(r -> r.itemId().equals("test:weapon_4")).findFirst().orElseThrow().stage() == ProgressionBand.APEX,
                "A strong sword's ordinary speed-one tool component cannot assign it to ENTRY");
        check(banded.stream().filter(r -> r.itemId().equals("test:fast_tool")).findFirst().orElseThrow().stage() == ProgressionBand.ENTRY,
                "Fast mining must not manufacture strong entry combat damage");
        var frontiers = RobustFrontiers.build(banded);
        check(frontiers.get(ProgressionBand.ENTRY).get(CapabilityAxis.SUSTAINED_DAMAGE) == 4
                        && frontiers.get(ProgressionBand.APEX).get(CapabilityAxis.SUSTAINED_DAMAGE) == 20,
                "Combat follows its distinct damage strengths");
        check(frontiers.get(ProgressionBand.ENTRY).get(CapabilityAxis.MINING_SPEED) == 1
                        && frontiers.get(ProgressionBand.APEX).get(CapabilityAxis.MINING_SPEED) == 20,
                "Fast pickaxe mining retains its independent high-strength band");
        var overridden = banded.stream().map(r -> r.itemId().equals("test:fast_tool")
                ? new EquipmentReference(r.itemId(), r.slot(), ProgressionBand.EARLY, r.axes(),
                    List.of(EquipmentReference.ESTIMATED_CATALOG_REFERENCE, EquipmentStrengthBands.EXPLICIT_STAGE),
                    false, true, 1, "explicit author stage") : r).toList();
        check(RobustFrontiers.build(overridden).get(ProgressionBand.EARLY).get(CapabilityAxis.MINING_SPEED) == 20,
                "Explicit author stage controls both measured capabilities");
    }
    private static void nativePayoutEligibility() {
        com.mistaboom.essence_ascendance.essence.EssenceTypes.init();
        var utility = com.mistaboom.essence_ascendance.essence.EssenceTypes.UTILITY;
        List<com.mistaboom.essence_ascendance.valuation.ProceduralValuationResult> estimates = new java.util.ArrayList<>();
        Map<String, Map<String, Long>> routes = new java.util.TreeMap<>();
        for (var item : List.of(Items.BARRIER, Items.PIG_SPAWN_EGG, Items.COAL, Items.COPPER_INGOT)) {
            var id = BuiltInRegistries.ITEM.getKey(item);
            estimates.add(new com.mistaboom.essence_ascendance.valuation.ProceduralValuationResult(id, 24, 24,
                    java.util.Optional.empty(), .3, Map.of(utility, 24L), 0, List.of("property estimate"),
                    new com.mistaboom.essence_ascendance.valuation.ProceduralValuationResult.RoutingDiagnostics(List.of(),
                            com.mistaboom.essence_ascendance.valuation.ProceduralValuationResult.ConfidenceBand.LOW),
                    com.mistaboom.essence_ascendance.valuation.ProceduralValuationResult.ValueEvidence.PROPERTY_RECIPE_ESTIMATE,
                    "PROPERTY_ESTIMATE"));
            routes.put(id.toString(), Map.of(utility.id().toString(), 24L));
        }
        var snapshot = new com.mistaboom.essence_ascendance.valuation.ValuationEvidenceSnapshot(Map.of(), Map.of(), Map.of());
        var resources = PackEvidenceCollector.resolveResources(estimates, snapshot, new EvidenceSink());
        var evidence = new PackEvidence(resources, List.of(), List.of(), Map.of(), List.of(), List.of(), Map.of());
        var graph = new com.mistaboom.essence_ascendance.balance.economy.ProductionGraph(List.of(), List.of());
        var economy = com.mistaboom.essence_ascendance.balance.economy.EconomyGenerator.generate(
                evidence, graph, routes, SETTINGS, BalanceOverrides.empty());
        for (var item : List.of(Items.BARRIER, Items.PIG_SPAWN_EGG, Items.COAL, Items.COPPER_INGOT)) {
            String id = BuiltInRegistries.ITEM.getKey(item).toString();
            boolean excluded = item == Items.BARRIER || item == Items.PIG_SPAWN_EGG;
            check((economy.resources().get(id).dissolutionYield().amount() == 0) == excluded,
                    "Collector-to-economy default native payout eligibility: " + id);
            var resource = resources.get(id);
            check(resource.external() && !resource.reachable() && resource.availability() == Availability.UNKNOWN
                            && resource.automation() == Automation.UNKNOWN && resource.sources().isEmpty(),
                    "Payout eligibility neither changes ownership nor invents source evidence: " + id);
        }
        check(estimates.stream().allMatch(value -> value.totalValue() == 24), "Default payout exclusions do not rewrite internal material estimates");
        var exact = new BalanceOverrides(List.of(), Map.of("/economy/resources/minecraft:barrier/routedYields/" + utility.id(), 5L));
        var allowed = com.mistaboom.essence_ascendance.balance.economy.EconomyGenerator.generate(evidence, graph, routes, SETTINGS, exact);
        check(allowed.resources().get("minecraft:barrier").dissolutionYield().amount() == 5,
                "Explicit exact economy override can opt an administrative item into payout");
    }

    private static void effectiveDefaultEquipment() {
        var chest = new net.minecraft.world.item.ItemStack(Items.DIAMOND_CHESTPLATE);
        check(chest.getOrDefault(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS,
                        net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY).modifiers().isEmpty(),
                "Regression fixture: default vanilla armor lives outside the stack component");
        var measured = PackEvidenceCollector.measureEquipment(chest, ProgressionBand.LATE, true);
        check(measured.axes().get(CapabilityAxis.ARMOR) == 8, "Effective item defaults restore diamond chestplate armor");
        check(measured.axes().get(CapabilityAxis.TOUGHNESS) == 2, "Effective item defaults restore diamond chestplate toughness");
        check(measured.slot().equals("chest"), "Armor modifiers use their actual equipped slot");
        String healthKey = net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH.unwrapKey().orElseThrow().location().toString();
        check(measured.attributeModifiers().containsKey(healthKey)
                        && measured.attributeModifiers().get(healthKey).equals(NativeAttributeContribution.EMPTY),
                "Measured absence of native health modifiers is explicit, never a scalar player-health estimate");
        double armor = 0, toughness = 0;
        var outfitHealth = NativeAttributeContribution.EMPTY;
        for (var item : List.of(Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS)) {
            var piece = PackEvidenceCollector.measureEquipment(new net.minecraft.world.item.ItemStack(item), ProgressionBand.APEX, true);
            armor += piece.axes().get(CapabilityAxis.ARMOR); toughness += piece.axes().get(CapabilityAxis.TOUGHNESS);
            check(piece.attributeModifiers().containsKey(healthKey), "Every measured armor slot retains native health absence");
            outfitHealth = outfitHealth.combine(piece.attributeModifiers().get(healthKey));
        }
        check(armor == 20 && toughness == 12, "Real netherite item defaults produce the actual full-set defenses");
        for (double playerBase : new double[]{10, 40, 80}) {
            var nativeHealth = new net.minecraft.world.entity.ai.attributes.AttributeInstance(
                    net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH, ignored -> {});
            nativeHealth.setBaseValue(playerBase);
            check(outfitHealth.apply(playerBase) == nativeHealth.getValue(),
                    "Ordinary armor preserves a native player base different from the attribute default: " + playerBase);
        }
        chest.set(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS,
                net.minecraft.world.item.component.ItemAttributeModifiers.builder()
                        .add(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR,
                                new net.minecraft.world.entity.ai.attributes.AttributeModifier(ResourceLocation.parse("test:armor"), 11,
                                        net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE),
                                net.minecraft.world.entity.EquipmentSlotGroup.CHEST)
                        .add(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR,
                                new net.minecraft.world.entity.ai.attributes.AttributeModifier(ResourceLocation.parse("test:wrong_slot"), 100,
                                        net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE),
                                net.minecraft.world.entity.EquipmentSlotGroup.HEAD)
                        .add(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH,
                                new net.minecraft.world.entity.ai.attributes.AttributeModifier(ResourceLocation.parse("test:health"), .5,
                                        net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
                                net.minecraft.world.entity.EquipmentSlotGroup.CHEST)
                        .add(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS,
                                new net.minecraft.world.entity.ai.attributes.AttributeModifier(ResourceLocation.parse("test:toughness"), 3,
                                        net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE),
                                net.minecraft.world.entity.EquipmentSlotGroup.CHEST)
                        .add(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH,
                                new net.minecraft.world.entity.ai.attributes.AttributeModifier(ResourceLocation.parse("test:wrong_health_slot"), 100,
                                        net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE),
                                net.minecraft.world.entity.EquipmentSlotGroup.HEAD).build());
        check(PackEvidenceCollector.measureEquipment(chest, ProgressionBand.LATE, true).axes().get(CapabilityAxis.ARMOR) == 11,
                "Explicit components replace defaults and unrelated equipment slots remain excluded");
        var health = PackEvidenceCollector.measureEquipment(chest, ProgressionBand.LATE, true).attributeModifiers()
                .get(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH.unwrapKey().orElseThrow().location().toString());
        check(health.modifiers().size() == 1 && health.apply(20) == 30
                        && health.modifiers().get("test:health").operation()
                            == net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
                "Native health collection preserves the real slot, identity and multiplication operation");
        check(PackEvidenceCollector.measureEquipment(chest, ProgressionBand.LATE, true).axes().get(CapabilityAxis.MAX_HEALTH) == 30,
                "Measured native health also enters the shared whole-slot outlier policy");
        var nativeRow = PackEvidenceCollector.measureEquipment(chest, ProgressionBand.LATE, true);
        var resource = new ResourceEvidence(nativeRow.itemId(), ProgressionBand.LATE, Availability.UNKNOWN,
                Automation.UNKNOWN, false, true, 1, 1, List.of(), List.of());
        Map.of(CapabilityAxis.ARMOR, net.minecraft.world.entity.ai.attributes.Attributes.ARMOR,
                CapabilityAxis.TOUGHNESS, net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS,
                CapabilityAxis.MAX_HEALTH, net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).forEach((axis, attribute) -> {
            var sink = new EvidenceSink();
            sink.add(new EvidenceFact(ITEM, nativeRow.itemId(), "axis." + axis.name(), EvidenceFact.Value.number(7),
                    "human_override", EvidenceFact.Origin.OVERRIDE, 1, 1000, ProgressionBand.LATE, List.of(), "explicit scalar fact"));
            var resolved = PackEvidenceCollector.resolveEquipment(Map.of(nativeRow.itemId(), nativeRow),
                    Map.of(nativeRow.itemId(), resource), sink, SETTINGS).getFirst();
            String attributeId = attribute.unwrapKey().orElseThrow().location().toString();
            check(resolved.axes().get(axis) == 7 && !resolved.attributeModifiers().containsKey(attributeId),
                    "An explicit scalar fact overrides the native modifier bundle for " + axis);
            check(nativeRow.attributeModifiers().entrySet().stream().filter(entry -> !entry.getKey().equals(attributeId))
                            .allMatch(entry -> entry.getValue().equals(resolved.attributeModifiers().get(entry.getKey()))),
                    "An explicit axis override preserves all unrelated native modifier bundles");
        });
    }

    private static void check(boolean condition, String message) {
        assertions++; if (!condition) throw new AssertionError(message);
    }
}
