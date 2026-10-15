package com.mistaboom.essence_ascendance.balance.runtime;

import com.mistaboom.essence_ascendance.balance.config.BonusProgressionConfig;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import com.mistaboom.essence_ascendance.progression.*;
import com.mistaboom.essence_ascendance.skill.Skills;
import com.mistaboom.essence_ascendance.stat.*;
import com.mistaboom.essence_ascendance.tier.*;
import java.nio.file.Files;
import java.util.Map;

/** Generated editable defaults, exact human bounds and native incompatibility rejection. */
public final class BonusProgressionConfigTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        var directory = Files.createTempDirectory("essence-bonus-config-");
        var file = BonusProgressionConfig.path(directory);
        try {
            check(BonusProgressionConfig.read(directory).equals(BonusProgressionConfig.automatic()), "Absent file must request automatic generation");
            check(!Files.exists(file), "Reading absent input created defaults before a valid generation");
            rejects("ascending order", () -> BonusProgressionConfig.parse(row("movement_speed", "ascendant", "dormant", 40, 10), "test.toml"));
            rejects("Missing Bonus setting", () -> BonusProgressionConfig.parse("[[bonus]]\nid='essence_ascendance:movement_speed'", "test.toml"));
            rejects("minimum_bonus_at_lowest", () -> BonusProgressionConfig.parse(row("movement_speed", "dormant", "awakened", 10, 20), "test.toml"));
            String valid = row("movement_speed", "dormant", "awakened", 40, 20);
            rejects("Duplicate Bonus ID", () -> BonusProgressionConfig.parse(valid + valid, "test.toml"));
            rejects("schema_version", () -> BonusProgressionConfig.parse("schema_version=2", "test.toml"));

            net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
            EssenceTypes.init(); EssenceStats.init(); AscendanceTiers.init();
            MilestoneProviders.init(); Milestones.init(); Skills.init(); AscendanceAdvancements.init();
            // Use bound Player attributes for both generations. The pre-world bootstrap placeholder
            // intentionally uses Attribute defaults (movement 0.7), unlike the real player (0.1).
            var evidence = RuntimeReferencePolicy.bootstrapEvidence();
            var runtime = RuntimeBalanceGenerator.generate(evidence,
                    com.mistaboom.essence_ascendance.balance.config.BalanceSettings.defaults(),
                    com.mistaboom.essence_ascendance.balance.config.BalanceOverrides.empty());
            var original = runtime.config().balanceProfile().bonusTracks();
            BonusProgressionConfig.writeGeneratedDefaults(directory, original);
            var emitted = BonusProgressionConfig.read(directory);
            try (var siblings = Files.list(file.getParent())) {
                check(siblings.count() == 1, "Successful defaults publication left a temporary file");
            }
            check(emitted.entries().size() == EssenceStatRegistry.values().size(), "Every generated Bonus must have editable controls");
            var replay = BonusTrackGenerator.publishMeaningful(runtime, Map.of(), emitted);
            for (var track : replay.config().balanceProfile().bonusTracks().values()) {
                var before = original.get(track.statId());
                check(track.startTier().equals(before.startTier()) && track.completionTier().equals(before.completionTier()),
                        "Generated config changed its own tier boundaries: " + track.statId());
                check(Math.abs(track.maximumEffect() - before.maximumEffect()) < 1e-7, "Generated config changed its own completion target: " + track.statId());
                check(track.activeValues().equals(before.activeValues()), "Untouched generated config changed an endpoint: " + track.statId()
                        + "; before=" + before.activeValues() + "; after=" + track.activeValues());
                check(track.checkpoints().equals(before.checkpoints()), "Untouched generated config changed checkpoint prices or flags: " + track.statId());
                check(track.equals(before), "Untouched generated config changed the complete published track: " + track.statId()
                        + "\nbefore=" + before + "\nafter=" + track);
            }
            var rebuilt = RuntimeBalanceGenerator.generate(evidence, null,
                    com.mistaboom.essence_ascendance.balance.config.BalanceSettings.defaults(),
                    com.mistaboom.essence_ascendance.balance.config.BalanceOverrides.empty(),
                    com.mistaboom.essence_ascendance.balance.config.SkillLayoutConfig.defaults(), emitted);
            check(rebuilt.toJson().equals(runtime.toJson()), "Untouched emitted controls changed a fresh complete runtime generation");
            check(RuntimeValueQuantization.reconstructedGridValue(.7000000000000001, .1) == .7,
                    "Derived fractional endpoint retained multiplication noise");
            check(RuntimeValueQuantization.reconstructedGridValue(.700001, .1) == .700001,
                    "Grid reconstruction changed a real off-grid value");
            check(MeaningfulProgression.first(.025, .4, .000001, 10) == .4,
                    "Low-budget Step Height first floor rounded below its authoritative minimum");
            check(RuntimeValueQuantization.up(.4000001, .000001) == .400001,
                    "Upward floor quantization hid a real increase in the declared minimum");
            var step = original.get(EssenceStats.STEP_HEIGHT.id());
            double nativeBoundary = .8999999761581421;
            var singlePoints = step.checkpoints().stream().map(point -> {
                boolean active = point.tierId().equals(AscendanceTiers.TRANSCENDENT.id());
                return new BonusTrackDefinition.Checkpoint(point.tierId(), active ? 1 : 0, active ? 1 : 0,
                        active ? 1 : 0, active, active);
            }).toList();
            var nativeSingle = new BonusTrackDefinition(step.statId(), step.category(), step.unit(), nativeBoundary,
                    AscendanceTiers.TRANSCENDENT.id(), AscendanceTiers.TRANSCENDENT.id(), singlePoints,
                    step.investmentExponent(), BonusTrackDefinition.PurchaseStyle.CONTINUOUS, java.util.List.of(),
                    step.applicability(), step.compatibilityRequirements(), step.confidence(), step.evidence(), step.source(), step.inputs());
            var nativeControls = BonusProgressionConfig.generatedEntry(nativeSingle);
            check(nativeControls.bonusAtMax() == nativeBoundary && nativeControls.minimumBonusAtLowest() == nativeBoundary,
                    "Generated controls altered a real off-grid native Step Height endpoint");
            var changed = BonusTrackGenerator.publishMeaningful(runtime, Map.of(), BonusProgressionConfig.parse(valid, "test.toml"))
                    .config().balanceProfile().bonusTracks().get(EssenceStats.MOVEMENT_SPEED.id());
            check(changed.startTier().equals(AscendanceTiers.DORMANT.id()) && changed.completionTier().equals(AscendanceTiers.AWAKENED.id()),
                    "Explicit first and completion tiers were silently moved");
            check(changed.maximumEffect() == 40 && changed.activeValues().getFirst() >= 20 && changed.activeStateCount() == 2,
                    "Explicit maximum/first floor did not govern meaningful native values");
            rejects("first floor", () -> BonusTrackGenerator.publishMeaningful(runtime, Map.of(),
                    BonusProgressionConfig.parse(row("movement_speed", "dormant", "dormant", 1, 1), "test.toml")));
            rejects("native ceiling", () -> BonusTrackGenerator.publishMeaningful(runtime, Map.of(),
                    BonusProgressionConfig.parse(row("max_health", "dormant", "transcendent", 1000, 1), "test.toml")));
            rejects("meaningful native increment", () -> BonusTrackGenerator.publishMeaningful(runtime, Map.of(),
                    BonusProgressionConfig.parse(row("movement_speed", "dormant", "transcendent", 20, 10), "test.toml")));
            rejects("Unknown Bonus ID", () -> BonusTrackGenerator.publishMeaningful(runtime, Map.of(),
                    BonusProgressionConfig.parse(row("typo", "dormant", "awakened", 40, 20), "test.toml")));
            Files.writeString(file, valid + "\n# human edit\n");
            BonusProgressionConfig.writeGeneratedDefaults(directory, original);
            check(Files.readString(file).equals(valid + "\n# human edit\n"), "Generation overwrote a human file");
            Files.writeString(file, "[invalid");
            BonusProgressionConfig.writeGeneratedDefaults(directory, original);
            check(Files.readString(file).equals("[invalid"), "Generation overwrote an invalid human file");
            var obstructed = directory.resolve("blocked-config-root");
            Files.writeString(obstructed, "existing file");
            try {
                try {
                    BonusProgressionConfig.writeGeneratedDefaults(obstructed, original);
                    throw new AssertionError("Expected defaults publication to report an unwritable parent");
                } catch (java.io.IOException expected) {
                    check(!Files.exists(BonusProgressionConfig.path(obstructed)), "Failed defaults publication left a destination file");
                    check(Files.readString(obstructed).equals("existing file"), "Failed defaults publication changed its obstructing file");
                    check(Files.readString(file).equals("[invalid"), "Failed defaults publication changed an existing config");
                }
            } finally { Files.deleteIfExists(obstructed); }
        } finally {
            Files.deleteIfExists(file); Files.deleteIfExists(file.getParent()); Files.deleteIfExists(directory);
        }
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out))
                .println("BonusProgressionConfigTest: " + checks + " checks PASS");
    }
    private static String row(String id, String first, String last, double maximum, double minimum) {
        return "\n[[bonus]]\nid='essence_ascendance:" + id + "'\nmin_tier='essence_ascendance:" + first
                + "'\nmax_tier='essence_ascendance:" + last + "'\nbonus_at_max=" + maximum + "\nminimum_bonus_at_lowest=" + minimum + "\n";
    }
    private static void rejects(String part, Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) {
            check(expected.getMessage().contains(part), "Wrong rejection: " + expected.getMessage()); return;
        }
        throw new AssertionError("Expected rejection containing " + part);
    }
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
