package com.mistaboom.essence_ascendance.balance.runtime;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mistaboom.essence_ascendance.balance.config.*;
import com.mistaboom.essence_ascendance.balance.economy.*;
import com.mistaboom.essence_ascendance.balance.engine.*;
import com.mistaboom.essence_ascendance.config.SkillEffectBalanceSettings;
import com.mistaboom.essence_ascendance.equipment.*;
import com.mistaboom.essence_ascendance.essence.*;
import com.mistaboom.essence_ascendance.progression.*;
import com.mistaboom.essence_ascendance.skill.Skills;
import com.mistaboom.essence_ascendance.stat.*;
import com.mistaboom.essence_ascendance.tier.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import java.util.*;

/** Two calibrated catalogs isolate stronger melee from unchanged projectile/caster observations. */
public final class PackCombatScalingTest {
    private static int checks;
    private static final List<StatDefinition> DAMAGE = List.of(EssenceStats.MELEE_DAMAGE, EssenceStats.RANGED_DAMAGE, EssenceStats.MAGIC_DAMAGE);

    public static void main(String[] args) {
        Thread.currentThread().setUncaughtExceptionHandler((thread, failure) -> failure.printStackTrace(
                new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.err))));
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        EssenceTypes.init(); AscendanceTiers.init(); EssenceStats.init();
        MilestoneProviders.init(); Milestones.init(); Skills.init(); AscendanceAdvancements.init(); EquipmentProfiles.init();
        requestUnits();
        var settings = BalanceSettings.defaults();
        var economy = economy();
        var ordinaryEvidence = catalog(1);
        var strongerEvidence = catalog(6);
        for (String family : List.of("ranged", "caster")) for (var band : ProgressionBand.values())
            check(RuntimeReferencePolicy.weapon(ordinaryEvidence, band, family, settings.outlierPolicy().name())
                            .equals(RuntimeReferencePolicy.weapon(strongerEvidence, band, family, settings.outlierPolicy().name())),
                    "Fixture must leave every projectile/caster observation unchanged");
        var ordinary = RuntimeBalanceDefinition.generate(ordinaryEvidence, economy, settings, BalanceOverrides.empty());
        var stronger = RuntimeBalanceDefinition.generate(strongerEvidence, economy, settings, BalanceOverrides.empty());
        ordinary.generationAnalysis().requireSafe(); stronger.generationAnalysis().requireSafe();
        double before = ordinary.config().statMaxBonus(EssenceStats.MELEE_DAMAGE);
        double after = stronger.config().statMaxBonus(EssenceStats.MELEE_DAMAGE);
        check(after > before && after > stronger.config().statMaxBonus(EssenceStats.MAGIC_DAMAGE),
                "A stronger melee catalog must receive more native melee Bonus despite unchanged caster evidence; before="
                        + before + ", after=" + after + ", caster=" + stronger.config().statMaxBonus(EssenceStats.MAGIC_DAMAGE));
        parity(ordinary, ordinaryEvidence); parity(stronger, strongerEvidence);
        for (var tier : AscendanceTierRegistry.values()) {
            var a = ordinary.config().equipmentBaselineConfig().baselineFor(tier);
            var b = stronger.config().equipmentBaselineConfig().baselineFor(tier);
            check(a.rangedDamage() == b.rangedDamage() && a.rangedAttackSpeed() == b.rangedAttackSpeed()
                            && a.magicDamage() == b.magicDamage() && a.magicCastSpeed() == b.magicCastSpeed(),
                    "Melee allocation cannot fund itself by changing another family's physical equipment: " + tier.id());
        }
        nativeDamageStates(ordinary); nativeDamageStates(stronger);
        authorControls(stronger);
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out)).println(
                "PackCombatScalingTest: " + checks + " request/family/native/config checks PASS; melee Bonus " + before + " -> " + after);
    }

    private static void requestUnits() {
        var ordinary = baseline(8, 9, 6);
        var meleeOnly = baseline(48, 9, 6);
        var allStronger = baseline(48, 54, 36);
        check(RuntimeCombatRequests.bonus(EssenceStats.MELEE_DAMAGE, 5, ordinary) == 5,
                "Small native catalogs retain the authored damage request");
        check(RuntimeCombatRequests.bonus(EssenceStats.MELEE_DAMAGE, 5, meleeOnly) == 24,
                "Large melee request uses the shared half-hit policy");
        for (var stat : List.of(EssenceStats.RANGED_DAMAGE, EssenceStats.MAGIC_DAMAGE))
            check(RuntimeCombatRequests.bonus(stat, 5, ordinary) == RuntimeCombatRequests.bonus(stat, 5, meleeOnly),
                    "A stronger sword does not invent stronger evidence for " + stat.id());
        for (var stat : DAMAGE)
            check(RuntimeCombatRequests.bonus(stat, 5, allStronger) > RuntimeCombatRequests.bonus(stat, 5, ordinary),
                    "Every stronger observed family can request more native flat damage: " + stat.id());
        for (var stat : EssenceStatRegistry.values()) if (!DAMAGE.contains(stat)) {
            double authored = StatScalingDefaults.get(stat.id()).orElseThrow();
            check(RuntimeCombatRequests.bonus(stat, authored, allStronger) == authored,
                    "Weapon-hit scaling must not multiply another mechanic's units: " + stat.id());
        }
        JsonObject effects = new Gson().toJsonTree(SkillEffectBalanceSettings.defaults()).getAsJsonObject();
        var unchanged = effects.deepCopy();
        RuntimeCombatRequests.scaleFlatSkillDamage(unchanged, meleeOnly);
        check(unchanged.equals(effects), "One strong family cannot inflate fixed procs shared with weaker families");
        var stronger = effects.deepCopy();
        RuntimeCombatRequests.scaleFlatSkillDamage(stronger, allStronger);
        var expected = effects.deepCopy();
        for (String path : List.of("combustion.damage", "shatter.shardDamage", "staticCharge.lightningDamage")) {
            String[] parts = path.split("\\.");
            var field = expected.getAsJsonObject(parts[0]);
            field.addProperty(parts[1], field.get(parts[1]).getAsDouble() * 3.6);
        }
        check(stronger.equals(expected), "All-family scaling changes only native flat proc damage, preserving percentages and timings");
        RuntimeCombatRequests.scaleFlatSkillDamage(stronger, baseline(100_000, 100_000, 100_000));
        for (String section : List.of("combustion", "shatter", "staticCharge")) {
            String field = section.equals("combustion") ? "damage" : section.equals("shatter") ? "shardDamage" : "lightningDamage";
            check(stronger.getAsJsonObject(section).get(field).getAsDouble() == 1024,
                    "Extreme requests preserve the existing supported flat-proc parameter limit");
        }
    }

    private static EquipmentBaselineConfig.TierBaseline baseline(double melee, double ranged, double magic) {
        return new EquipmentBaselineConfig.TierBaseline(20, 8, melee, 1.2, ranged, .8, magic, 1.2, 12, 3, 2000);
    }

    private static PackEvidence catalog(double meleeFactor) {
        var rows = new ArrayList<EquipmentReference>();
        double[] melee = {6, 6, 7, 7, 8}, ranged = {7, 7, 7, 8, 9}, magic = {4, 4, 5, 5, 6};
        for (var band : ProgressionBand.values()) {
            int i = band.ordinal();
            rows.add(weapon("melee", "mainhand_melee", band, melee[i] * meleeFactor, 1.2));
            rows.add(weapon("ranged", "mainhand_bow", band, ranged[i], .8));
            rows.add(weapon("caster", "mainhand_caster", band, magic[i], 1.2));
            rows.add(new EquipmentReference("test:tool_" + i, "mainhand_pickaxe", band,
                    Map.of(CapabilityAxis.MINING_SPEED, 6.0 + i * 1.5, CapabilityAxis.DURABILITY, 400.0 + i * 400,
                            CapabilityAxis.HARVEST_LEVEL, i < 2 ? 2.0 : 3.0), List.of(), true, true, 1, "native fixture measurements"));
        }
        var references = new EnumMap<ProgressionBand, Map<CapabilityAxis, Double>>(ProgressionBand.class);
        RobustFrontiers.build(rows).forEach((band, axes) -> {
            var measured = new EnumMap<CapabilityAxis, Double>(CapabilityAxis.class);
            measured.putAll(axes); measured.put(CapabilityAxis.ARMOR, 8.0 + band.ordinal() * 3);
            measured.put(CapabilityAxis.TOUGHNESS, band.ordinal() * 2.0); references.put(band, measured);
        });
        var enemy = new EnemyReference("test:fixed_enemy", EnemyReference.Encounter.ROUTINE, ProgressionBand.ENTRY,
                Map.of(CapabilityAxis.EFFECTIVE_HEALTH, 20.0, CapabilityAxis.BURST_DAMAGE, 5.0), true, 1, List.of(), "unchanged native fixture");
        return new PackEvidence(Map.of(), rows, List.of(enemy), references, List.of(), List.of(), Map.of());
    }

    private static EquipmentReference weapon(String name, String slot, ProgressionBand band, double hit, double rate) {
        return new EquipmentReference("test:" + name + "_" + band.ordinal(), slot, band,
                Map.of(CapabilityAxis.SUSTAINED_DAMAGE, hit * rate, CapabilityAxis.BURST_DAMAGE, hit, CapabilityAxis.ATTACK_RATE, rate),
                List.of(), true, true, 1, "paired hit/cadence fixture");
    }

    private static EconomyProfile economy() {
        var values = new TreeMap<String, EconomyProfile.ResourceValue>();
        for (var essence : EssenceRegistry.values()) values.put("test:" + essence.id().getPath(),
                new EconomyProfile.ResourceValue(new EconomicValue(24), DissolutionYield.of(24),
                        Map.of(essence.id().toString(), 24.0), List.of()));
        return new EconomyProfile(values, List.of(), List.of(), List.of(), 0, EconomyProcessingPolicy.defaults());
    }

    private static void parity(RuntimeBalanceDefinition runtime, PackEvidence evidence) {
        var apex = runtime.config().equipmentBaselineConfig().baselineFor(AscendanceTiers.TRANSCENDENT);
        for (String family : List.of("melee_shield", "ranged", "caster")) {
            var observed = RuntimeReferencePolicy.weapon(evidence, ProgressionBand.APEX, family, BalanceSettings.defaults().outlierPolicy().name());
            double hit = family.equals("melee_shield") ? apex.meleeDamage() : family.equals("ranged") ? apex.rangedDamage() : apex.magicDamage();
            double rate = family.equals("melee_shield") ? apex.meleeAttackSpeed() : family.equals("ranged") ? apex.rangedAttackSpeed() : apex.magicCastSpeed();
            check(hit == observed.damage() && rate == observed.rate(), "Bonus calibration changed observed equipment parity for " + family);
        }
    }

    private static void nativeDamageStates(RuntimeBalanceDefinition runtime) {
        for (var stat : DAMAGE) {
            var track = runtime.config().balanceProfile().bonusTracks().get(stat.id());
            var contexts = NativeDamageBonusFloor.contexts(runtime, stat);
            double previous = 0;
            for (var point : track.checkpoints()) if (point.purchasable()) {
                double value = track.maximumEffect() * point.effectFraction();
                check(value - previous >= 1 - 1e-8, "Published damage state retains the authored increment floor");
                for (var context : contexts) {
                    check(value <= context.maximumValue() + 1e-8, "Published damage respects every infused physical gear tier's headroom");
                    check(context.damage(value) - context.damage(previous) >= 1 - 1e-8,
                            "Each paid increment adds native damage on unchanged activating equipment");
                }
                previous = value;
            }
            check(previous == track.maximumEffect(), "Published maximum must be a real purchasable state");
        }
    }

    private static void authorControls(RuntimeBalanceDefinition runtime) {
        var entries = new TreeMap<String, BonusProgressionConfig.Entry>();
        runtime.config().balanceProfile().bonusTracks().values().stream()
                .filter(track -> track.applicability() != BonusTrackDefinition.Applicability.UNAVAILABLE)
                .forEach(track -> entries.put(track.statId().toString(), BonusProgressionConfig.generatedEntry(track)));
        var replay = BonusTrackGenerator.publishMeaningful(runtime, Map.of(), new BonusProgressionConfig(entries));
        check(replay.config().balanceProfile().bonusTracks().equals(runtime.config().balanceProfile().bonusTracks()),
                "Stronger catalog's generated controls must replay every complete track exactly");
        entries.put(EssenceStats.MELEE_DAMAGE.id().toString(), new BonusProgressionConfig.Entry(
                AscendanceTiers.DORMANT.id().toString(), AscendanceTiers.AWAKENED.id().toString(), 2.6, 1));
        var bounded = BonusTrackGenerator.publishMeaningful(runtime, Map.of(), new BonusProgressionConfig(entries));
        var track = bounded.config().balanceProfile().bonusTracks().get(EssenceStats.MELEE_DAMAGE.id());
        check(track.startTier().equals(AscendanceTiers.DORMANT.id()) && track.completionTier().equals(AscendanceTiers.AWAKENED.id())
                        && track.maximumEffect() == 2.6 && track.activeValues().equals(List.of(1.0, 2.6)),
                "A stronger request must not override the author's exact span or maximum");
        nativeDamageStates(bounded);
        double ceiling = NativeDamageBonusFloor.contexts(runtime, EssenceStats.MELEE_DAMAGE).stream()
                .mapToDouble(NativeDamageBonusFloor.Context::maximumValue).min().orElseThrow();
        entries.put(EssenceStats.MELEE_DAMAGE.id().toString(), new BonusProgressionConfig.Entry(
                AscendanceTiers.TRANSCENDENT.id().toString(), AscendanceTiers.TRANSCENDENT.id().toString(), Math.floor(ceiling) + 1, 1));
        try {
            BonusTrackGenerator.publishMeaningful(runtime, Map.of(), new BonusProgressionConfig(entries));
            throw new AssertionError("Author maximum bypassed the native damage ceiling");
        } catch (IllegalArgumentException expected) {
            check(expected.getMessage().contains("native ceiling"), "Impossible author bound must report the actual native limit");
        }
    }

    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
