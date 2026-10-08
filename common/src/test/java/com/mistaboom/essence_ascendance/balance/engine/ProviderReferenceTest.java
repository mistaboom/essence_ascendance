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

/** Real registries, no server and no spawned entity; exercises optional-provider materialization. */
public final class ProviderReferenceTest {
    private static int assertions;
    private static final BalanceSettings SETTINGS = BalanceSettings.defaults();

    public static void main(String[] args) {
        Thread.currentThread().setUncaughtExceptionHandler((thread, failure) -> failure.printStackTrace(new PrintStream(new FileOutputStream(FileDescriptor.err))));
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        var hand = PackEvidenceCollector.emptyHandMiningReference();
        check(hand.reachable() && hand.included() && hand.stage() == ProgressionBand.ENTRY, "Innate empty-hand action requires no crafted tool");
        check(hand.axes().keySet().equals(java.util.Set.of(CapabilityAxis.MINING_SPEED)), "Empty hands fabricated tool durability or harvest level");
        check(hand.axes().get(CapabilityAxis.MINING_SPEED) == net.minecraft.world.item.ItemStack.EMPTY.getDestroySpeed(net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState()), "Innate mining differs from native empty stack");
        check(RobustFrontiers.build(List.of(hand)).get(ProgressionBand.ENTRY).get(CapabilityAxis.MINING_SPEED).equals(hand.axes().get(CapabilityAxis.MINING_SPEED)), "Tool-free environment lacks native mining frontier");
        effectiveDefaultEquipment();
        equipment();
        enemies();
        new PrintStream(new FileOutputStream(FileDescriptor.out)).println("ProviderReferenceTest: " + assertions + " assertions passed");
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

    private static void equipment() {
        check(Items.STICK.getClass() == Item.class, "Fixture must have no recognized equipment subclass");
        String id = "minecraft:stick";
        EvidenceSink sink = new EvidenceSink();
        add(sink, ITEM, id, "slot", EvidenceFact.Value.text("mainhand_caster"));
        add(sink, ITEM, id, "axis.BURST_DAMAGE", EvidenceFact.Value.number(30));
        add(sink, ITEM, id, "axis.ATTACK_RATE", EvidenceFact.Value.number(2));
        var resolved = equipment(id, sink, true, true);
        check(resolved.included(), "Provider-only plain item becomes a usable equipment reference");
        check(resolved.slot().equals("mainhand_caster"), "Explicit provider slot preserved");
        check(resolved.stage() == ProgressionBand.LATE, "Actual acquisition stage controls the reference");
        check(resolved.axes().get(CapabilityAxis.SUSTAINED_DAMAGE) == 60, "Damage and attack rate derive sustained damage");
        check(resolved.confidence() == .9, "Provider confidence is retained instead of fabricated from a subclass");
        var frontiers = RobustFrontiers.build(List.of(resolved));
        check(!frontiers.get(ProgressionBand.MID).containsKey(CapabilityAxis.BURST_DAMAGE), "Late equipment does not leak into early bands");
        check(frontiers.get(ProgressionBand.LATE).get(CapabilityAxis.BURST_DAMAGE) == 30, "Provider-only gear reaches its frontier");
        check(!equipment(id, sink, false, true).included(), "Provider slot cannot make an unreachable item reachable");
        check(!equipment(id, sink, true, false).included(), "Provider slot cannot include endogenous resources");
        add(sink, ITEM, id, "axis.SUSTAINED_DAMAGE", EvidenceFact.Value.number(12));
        check(equipment(id, sink, true, true).axes().get(CapabilityAxis.SUSTAINED_DAMAGE) == 12,
                "Measured sustained damage respects custom cooldown/reload behavior");
        add(sink, ITEM, id, "administrative", EvidenceFact.Value.flag(true));
        check(!equipment(id, sink, true, true).included(), "Administrative exclusion wins over custom measurements");

        EvidenceSink noSlot = new EvidenceSink();
        add(noSlot, ITEM, id, "axis.BURST_DAMAGE", EvidenceFact.Value.number(20));
        check(!equipment(id, noSlot, true, true).included(), "No slot is guessed from an ordinary item's name");
        check(!noSlot.warnings().isEmpty(), "Missing slot emits actionable diagnostics");
        add(noSlot, ITEM, id, "slot", EvidenceFact.Value.text("body"));
        check(!equipment(id, noSlot, true, true).included(), "Non-player armor never enters the frontier");

        EvidenceSink invalid = new EvidenceSink();
        add(invalid, ITEM, id, "slot", EvidenceFact.Value.text("imaginary_slot"));
        add(invalid, ITEM, id, "axis.BURST_DAMAGE", EvidenceFact.Value.number(20));
        rejected(() -> equipment(id, invalid, true, true), "Malformed provider slot must reject regeneration");
        EvidenceSink invalidAxis = new EvidenceSink();
        add(invalidAxis, ITEM, id, "slot", EvidenceFact.Value.text("mainhand_melee"));
        add(invalidAxis, ITEM, id, "axis.BURST_DAMAGE", EvidenceFact.Value.text("powerful"));
        rejected(() -> equipment(id, invalidAxis, true, true), "Text cannot masquerade as measured damage");

        EvidenceSink absent = new EvidenceSink();
        add(absent, ITEM, "missing:wand", "slot", EvidenceFact.Value.text("mainhand_caster"));
        add(absent, ITEM, "missing:wand", "axis.BURST_DAMAGE", EvidenceFact.Value.number(20));
        check(PackEvidenceCollector.resolveEquipment(Map.of(), Map.of("missing:wand", resource("missing:wand", true, true)), absent, SETTINGS).isEmpty(),
                "A provider cannot invent an unregistered item");
        check(PackEvidenceCollector.resolveEquipment(Map.of(), Map.of(), sink, SETTINGS).isEmpty(),
                "A registered item still requires acquisition evidence");

        String toml = "[[fact]]\nkind=\"item\"\nselector=\"minecraft:stick\"\nslot=\"mainhand_caster\"\ndamage=11\nattack_speed=2\n";
        var override = BalanceOverrides.parse(toml, "provider-test.toml").facts().getFirst();
        EvidenceSink fromOverride = new EvidenceSink();
        override.values().forEach((key, value) -> {
            String property = switch (key) { case "damage" -> "axis.BURST_DAMAGE"; case "attack_speed" -> "axis.ATTACK_RATE"; default -> key; };
            add(fromOverride, ITEM, id, property, value instanceof Number n ? EvidenceFact.Value.number(n.doubleValue()) : EvidenceFact.Value.text(value.toString()));
        });
        check(equipment(id, fromOverride, true, true).included(), "Parsed factual slot/damage corrections support ordinary registered Items");
    }

    private static void enemies() {
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.ARMOR_STAND).toString();
        check(EntityType.ARMOR_STAND.getCategory() != MobCategory.MONSTER, "Fixture must bypass ordinary hostile collection");
        EvidenceSink sink = enemyFacts(id);
        var enemy = enemy(id, sink);
        check(enemy.included(), "Explicit custom encounter facts create a registered non-monster reference");
        check(enemy.stage() == ProgressionBand.LATE, "Explicit custom encounter stage preserved");
        check(enemy.encounter() == EnemyReference.Encounter.UNKNOWN, "No boss/routine classification guessed from registry identity or health");
        check(enemy.axes().get(CapabilityAxis.EFFECTIVE_HEALTH) == 180, "Explicit health preserved");
        check(enemy.axes().get(CapabilityAxis.BURST_DAMAGE) == 30, "Explicit attack damage preserved");
        add(sink, ENEMY, id, "classification", EvidenceFact.Value.text("boss"));
        check(enemy(id, sink).encounter() == EnemyReference.Encounter.BOSS, "Explicit classification activates the requested encounter bucket");

        for (String missing : List.of("stage", "confidence", "include_reference", "axis.EFFECTIVE_HEALTH")) {
            EvidenceSink incomplete = new EvidenceSink();
            enemyFacts(id).facts().stream().filter(fact -> !fact.property().equals(missing)).forEach(incomplete::add);
            check(!enemy(id, incomplete).included(), "Custom enemy requires " + missing);
            check(!incomplete.warnings().isEmpty(), "Missing enemy facts are reported: " + missing);
        }
        EvidenceSink unreachable = enemyFacts(id);
        add(unreachable, ENEMY, id, "attainable", EvidenceFact.Value.flag(false));
        check(!enemy(id, unreachable).included(), "Unattainable encounter facts cannot affect a baseline");
        for (String exclusion : List.of("disabled", "creative_only", "administrative", "joke")) {
            EvidenceSink excluded = enemyFacts(id); add(excluded, ENEMY, id, exclusion, EvidenceFact.Value.flag(true));
            check(!enemy(id, excluded).included(), "Enemy exclusion honored: " + exclusion);
        }
        EvidenceSink noHealth = new EvidenceSink();
        enemyFacts(id).facts().stream().filter(fact -> !fact.property().equals("axis.EFFECTIVE_HEALTH")).forEach(noHealth::add);
        add(noHealth, ENEMY, id, "axis.EFFECTIVE_HEALTH", EvidenceFact.Value.number(0));
        check(!enemy(id, noHealth).included(), "Zero-health references do not become encounters");
        check(PackEvidenceCollector.resolveEnemies(List.of(), enemyFacts("missing:boss"), SETTINGS).isEmpty(),
                "A provider cannot invent an unregistered entity");
        EnemyReference own = new EnemyReference("essence_ascendance:boss", EnemyReference.Encounter.BOSS, ProgressionBand.LATE,
                Map.of(CapabilityAxis.EFFECTIVE_HEALTH, 100.0), true, .9, List.of(), "fixture");
        check(!PackEvidenceCollector.resolveEnemies(List.of(own), enemyFacts(own.entityId()), SETTINGS).getFirst().included(),
                "Own-mod enemy evidence cannot calibrate its own baseline");
        EnemyReference known = new EnemyReference("minecraft:zombie", EnemyReference.Encounter.ROUTINE, ProgressionBand.EARLY,
                Map.of(CapabilityAxis.EFFECTIVE_HEALTH, 20.0), true, .8, List.of(), "base attributes");
        check(PackEvidenceCollector.resolveEnemies(List.of(known), new EvidenceSink(), SETTINGS).getFirst().included(),
                "Ordinary measured enemies retain their existing inclusion semantics");
    }

    private static EquipmentReference equipment(String id, EvidenceSink sink, boolean reachable, boolean external) {
        return PackEvidenceCollector.resolveEquipment(Map.of(), Map.of(id, resource(id, reachable, external)), sink, SETTINGS).getFirst();
    }
    private static ResourceEvidence resource(String id, boolean reachable, boolean external) {
        return new ResourceEvidence(id, ProgressionBand.LATE, Availability.FINITE, Automation.NONE, reachable, external, 100, .8, List.of(), List.of());
    }
    private static EnemyReference enemy(String id, EvidenceSink sink) {
        return PackEvidenceCollector.resolveEnemies(List.of(), sink, SETTINGS).stream().filter(ref -> ref.entityId().equals(id)).findFirst().orElseThrow();
    }
    private static EvidenceSink enemyFacts(String id) {
        EvidenceSink sink = new EvidenceSink();
        add(sink, ENEMY, id, "include_reference", EvidenceFact.Value.flag(true));
        add(sink, ENEMY, id, "stage", EvidenceFact.Value.text("late"));
        add(sink, ENEMY, id, "confidence", EvidenceFact.Value.number(.9));
        add(sink, ENEMY, id, "axis.EFFECTIVE_HEALTH", EvidenceFact.Value.number(180));
        add(sink, ENEMY, id, "axis.BURST_DAMAGE", EvidenceFact.Value.number(30));
        return sink;
    }
    private static void add(EvidenceSink sink, EvidenceFact.Subject subject, String id, String property, EvidenceFact.Value value) {
        sink.add(new EvidenceFact(subject, id, property, value, "test:pack_adapter", EvidenceFact.Origin.OBSERVED, .9, 100,
                ProgressionBand.LATE, List.of(), "Measured custom behavior"));
    }
    private static void rejected(Runnable action, String message) {
        try { action.run(); } catch (IllegalArgumentException expected) { assertions++; return; }
        throw new AssertionError(message);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); assertions++; }
}
