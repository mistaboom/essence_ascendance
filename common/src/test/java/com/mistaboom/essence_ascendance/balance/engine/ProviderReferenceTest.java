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
        double armor = 0, toughness = 0;
        for (var item : List.of(Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS)) {
            var piece = PackEvidenceCollector.measureEquipment(new net.minecraft.world.item.ItemStack(item), ProgressionBand.APEX, true);
            armor += piece.axes().get(CapabilityAxis.ARMOR); toughness += piece.axes().get(CapabilityAxis.TOUGHNESS);
        }
        check(armor == 20 && toughness == 12, "Real netherite item defaults produce the actual full-set defenses");
        chest.set(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS,
                net.minecraft.world.item.component.ItemAttributeModifiers.builder()
                        .add(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR,
                                new net.minecraft.world.entity.ai.attributes.AttributeModifier(ResourceLocation.parse("test:armor"), 11,
                                        net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE),
                                net.minecraft.world.entity.EquipmentSlotGroup.CHEST)
                        .add(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR,
                                new net.minecraft.world.entity.ai.attributes.AttributeModifier(ResourceLocation.parse("test:wrong_slot"), 100,
                                        net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE),
                                net.minecraft.world.entity.EquipmentSlotGroup.HEAD).build());
        check(PackEvidenceCollector.measureEquipment(chest, ProgressionBand.LATE, true).axes().get(CapabilityAxis.ARMOR) == 11,
                "Explicit components replace defaults and unrelated equipment slots remain excluded");
    }

    private static void check(boolean condition, String message) {
        assertions++; if (!condition) throw new AssertionError(message);
    }
}
