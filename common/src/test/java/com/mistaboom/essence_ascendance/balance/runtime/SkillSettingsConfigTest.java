package com.mistaboom.essence_ascendance.balance.runtime;

import com.mistaboom.essence_ascendance.balance.config.*;
import com.mistaboom.essence_ascendance.skill.*;
import com.mistaboom.essence_ascendance.skill.balance.SkillBalanceGenerator;
import com.mistaboom.essence_ascendance.skill.balance.SkillBalanceRuntime;
import com.mistaboom.essence_ascendance.tier.AscendanceTierRegistry;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import net.minecraft.resources.ResourceLocation;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

/** Generated defaults, prerequisite-safe placement and native rank/stat contracts. */
public final class SkillSettingsConfigTest {
    private static final String TOOL = "gathering/toolInstinct/maximumLowerTierSpeedBonus";
    private static final String ENDURANCE = "mobility/fatigueFlight/enduranceTicks";
    private static final String RECHARGE = "mobility/fatigueFlight/groundRechargeTicks";
    private static int checks;

    public static void main(String[] args) throws Exception {
        syntaxBeforeCatalog();
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        com.mistaboom.essence_ascendance.essence.EssenceTypes.init();
        AscendanceTiers.init();
        com.mistaboom.essence_ascendance.stat.EssenceStats.init();
        com.mistaboom.essence_ascendance.progression.MilestoneProviders.init();
        com.mistaboom.essence_ascendance.progression.Milestones.init();
        Skills.init();
        com.mistaboom.essence_ascendance.progression.AscendanceAdvancements.init();
        tierPlacement();
        var evidence = RuntimeReferencePolicy.bootstrapEvidence();
        var runtime = RuntimeBalanceGenerator.generate(evidence, BalanceSettings.defaults(), BalanceOverrides.empty());
        var emitted = generatedDefaults(runtime);
        var rebuilt = RuntimeBalanceGenerator.generate(evidence, null, BalanceSettings.defaults(),
                BalanceOverrides.empty(), emitted, BonusSettingsConfig.automatic());
        check(rebuilt.toJson().equals(runtime.toJson()),
                "Untouched emitted controls changed fresh runtime generation, middle ranks or prices");
        explicitCurves(runtime);
        invalidCurves(runtime);
        preservedFiles(runtime.skillCurves());
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out))
                .println("SkillSettingsConfigTest: " + checks + " checks PASS");
    }

    private static void syntaxBeforeCatalog() throws Exception {
        check(SkillSettingsConfig.parse("# omitted entries retain authored defaults", "empty.toml")
                .equals(SkillSettingsConfig.defaults()), "Partial input lost authored defaults");
        rejects("Unknown skill ID", () -> parse(skill("typo", "dormant", null)));
        rejects("powered Ascendance tier", () -> parse(skill("frenzy", "latent", null)));
        rejects("schema_version", () -> parse("schema_version=2"));
        rejects("Expected [[skill]]", () -> parse("[skill]"));
        rejects("Expected [[skill]]", () -> parse("[skills]\n\"essence_ascendance:frenzy\"='essence_ascendance:dormant'"));
        rejects("preceding [[skill]]", () -> parse(stat(TOOL, .1, .5)));
        rejects("Duplicate skill ID", () -> parse(skill("tool_instinct", null, 3) + skill("tool_instinct", null, 3)));
        rejects("Duplicate stat", () -> parse(skill("tool_instinct", null, 3) + stat(TOOL, .1, .5) + stat(TOOL, .1, .5)));
        for (String ranks : new String[] {"0", "65", "2.5"})
            rejects("ranks must be an integer", () -> parse("[[skill]]\nid='essence_ascendance:tool_instinct'\nranks=" + ranks));
        rejects("finite number", () -> parse(skill("tool_instinct", null, 3)
                + "[[skill.stat]]\npath='" + TOOL + "'\nat_first_rank='ten'\nat_max_rank=0.5\n"));
        var directory = Files.createTempDirectory("essence-skill-missing-");
        try {
            check(SkillSettingsConfig.read(directory).equals(SkillSettingsConfig.defaults()), "Missing file did not use authored defaults");
            check(!Files.exists(SkillSettingsConfig.path(directory)), "Read scaffolded settings before a valid profile");
            check(!Files.exists(SkillSettingsConfig.path(directory).getParent()), "Read created a premature config directory");
        } finally { Files.deleteIfExists(directory); }
    }

    private static void tierPlacement() {
        var defaults = SkillSettingsConfig.defaults().requiredTiers();
        check(defaults.size() == SkillRegistry.size(), "Bundled tiers no longer cover the complete catalog");
        for (var skill : SkillRegistry.values()) check(defaults.get(skill.id()).equals(skill.catalogRequiredTierId()),
                "Bundled tier differs from the authored catalog: " + skill.id());
        var changed = parse(skill("fatigue_flight", "dormant", null));
        check(changed.requiredTiers().get(SkillIds.FATIGUE_FLIGHT).equals(AscendanceTiers.DORMANT.id()), "Unlock edit ignored");
        rejects("precedes required prerequisite", () -> parse(skill("vector_jump", "dormant", null)).requiredTiers());
        check(parse(skill("armor_crack", "dormant", null)).requiredTiers().get(SkillIds.ARMOR_CRACK)
                .equals(AscendanceTiers.DORMANT.id()), "A prerequisite may share its child's tier");
        var costs = new TreeMap<ResourceLocation, Long>();
        AscendanceTierRegistry.powerTiers().forEach(t -> costs.put(t.id(), 100L * t.order()));
        var original = SkillBalanceRuntime.withRequiredTiers(defaults, () -> SkillBalanceGenerator.generate(costs, 1));
        var custom = SkillBalanceRuntime.withRequiredTiers(changed.requiredTiers(), () -> SkillBalanceGenerator.generate(costs, 1));
        String flight = SkillIds.FATIGUE_FLIGHT.toString();
        check(custom.get(flight).requiredTierId().equals(AscendanceTiers.DORMANT.id()), "Generation lost configured unlock");
        check(custom.get(flight).ranks().getFirst().cost() < original.get(flight).ranks().getFirst().cost(),
                "Procedural price does not follow the configured tier budget");
        SkillBalanceRuntime.install(custom);
        try {
            check(SkillSettingsConfig.defaults().requiredTiers().equals(defaults), "Installed profile contaminated authored defaults");
            check(SkillBalanceRuntime.snapshot().equals(custom), "Reading settings changed installed rank authority");
            check(SkillRegistry.require(SkillIds.FATIGUE_FLIGHT).catalogRequiredTierId().equals(AscendanceTiers.RESONANT.id()),
                    "Explicit edit mutated catalog defaults");
            try { changed.requiredTiers().put(SkillIds.FRENZY, AscendanceTiers.TRANSCENDENT.id());
                throw new AssertionError("Mutable configured tier map");
            } catch (UnsupportedOperationException expected) { checks++; }
        } finally { SkillBalanceRuntime.clear(); }
    }

    private static SkillSettingsConfig generatedDefaults(RuntimeBalanceDefinition runtime) throws Exception {
        var directory = Files.createTempDirectory("essence-skill-generated-");
        var file = SkillSettingsConfig.path(directory);
        try {
            SkillSettingsConfig.writeGeneratedDefaults(directory, runtime.skillCurves());
            var emitted = SkillSettingsConfig.read(directory);
            check(emitted.entries().size() == SkillRegistry.size(), "Generated settings omit registered skills");
            check(Files.readString(file).contains("[[skill.stat]]"), "Generated file has no native stat controls");
            check(siblings(file) == 1, "Successful publication left an intermediate file");
            var replay = emitted.apply(runtime);
            int rankCount = 0;
            for (var entry : runtime.skillCurves().entrySet()) {
                var before = entry.getValue();
                var after = replay.skillCurves().get(entry.getKey());
                var controls = emitted.entries().get(entry.getKey());
                check(controls.ranks() == before.maximumRank() && controls.tier().equals(before.requiredTierId().toString()),
                        "Generated rank/tier controls differ: " + entry.getKey());
                check(controls.stats().keySet().equals(before.ranks().getFirst().parameters().keySet()),
                        "Generated native controls are incomplete: " + entry.getKey());
                for (var parameter : controls.stats().entrySet())
                    check(parameter.getValue().atFirstRank() == before.ranks().getFirst().parameters().get(parameter.getKey())
                                    && parameter.getValue().atMaxRank() == before.ranks().getLast().parameters().get(parameter.getKey()),
                            "Generated endpoints lost exact native values: " + entry.getKey() + "/" + parameter.getKey());
                check(before.equals(after), "Unchanged controls changed the complete curve: " + entry.getKey());
                for (int rank = 0; rank < before.maximumRank(); rank++) {
                    rankCount++;
                    check(before.ranks().get(rank).equals(after.ranks().get(rank)),
                            "Unchanged controls changed rank parameters, cost or power: " + entry.getKey() + "/" + (rank + 1));
                }
            }
            check(rankCount > SkillRegistry.size(), "Roundtrip fixture did not exercise repeat/middle ranks");
            check(replay.toJson().equals(runtime.toJson()), "Default replay altered another runtime section");
            check(RuntimeBalanceDefinition.fromJson(replay.toJson()).toJson().equals(runtime.toJson()),
                    "Saved-profile decode altered the settings replay");
            return emitted;
        } finally { clean(directory); }
    }

    private static void explicitCurves(RuntimeBalanceDefinition runtime) {
        var changed = parse(skill("tool_instinct", null, 3) + stat(TOOL, .1, .5)).apply(runtime);
        var tool = changed.skillCurves().get(SkillIds.TOOL_INSTINCT.toString());
        check(tool.maximumRank() == 3 && tool.ranks().size() == 3, "Explicit Tool Instinct rank count ignored");
        near(tool.ranks().getFirst().parameters().get(TOOL), .1, "Tool Instinct first endpoint");
        near(tool.ranks().getLast().parameters().get(TOOL), .5, "Tool Instinct last endpoint");
        for (int i = 1; i < tool.ranks().size(); i++)
            check(tool.ranks().get(i).parameters().get(TOOL) - tool.ranks().get(i - 1).parameters().get(TOOL) >= .05 - 1e-9,
                    "Configured Tool Instinct has a sub-floor paid rank");
        check(tool.requiredTierId().equals(runtime.skillCurves().get(SkillIds.TOOL_INSTINCT.toString()).requiredTierId()),
                "Omitting tier changed placement");
        near(ProgressionRequirements.read(changed.toJson().getAsJsonObject("effects"), TOOL), .1,
                "First-rank effects mirror configured authority");
        runtime.skillCurves().forEach((id, curve) -> {
            if (!id.equals(SkillIds.TOOL_INSTINCT.toString()))
                check(changed.skillCurves().get(id).equals(curve), "Explicit edit changed a sibling: " + id);
        });
        var flightRuntime = parse(skill("fatigue_flight", null, 3) + stat(ENDURANCE, 60, 660) + stat(RECHARGE, 600, 60)).apply(runtime);
        var flight = flightRuntime.skillCurves().get(SkillIds.FATIGUE_FLIGHT.toString());
        check(flight.maximumRank() == 3, "Explicit Fatigue Flight ranks ignored");
        near(flight.ranks().getFirst().parameters().get(ENDURANCE), 60, "Endurance start");
        near(flight.ranks().getLast().parameters().get(ENDURANCE), 660, "Endurance end");
        near(flight.ranks().getFirst().parameters().get(RECHARGE), 600, "Recharge start");
        near(flight.ranks().getLast().parameters().get(RECHARGE), 60, "Recharge end");
        for (int i = 1; i < flight.ranks().size(); i++) {
            var before = flight.ranks().get(i - 1).parameters();
            var after = flight.ranks().get(i).parameters();
            check(after.get(ENDURANCE) >= before.get(ENDURANCE) && after.get(RECHARGE) <= before.get(RECHARGE),
                    "Configured flight rank regressed a native outcome");
            check(after.get(ENDURANCE) - before.get(ENDURANCE) >= 20 || before.get(RECHARGE) - after.get(RECHARGE) >= 20,
                    "Configured flight rank misses both authored improvement alternatives");
            check(after.get(ENDURANCE) == Math.rint(after.get(ENDURANCE)) && after.get(RECHARGE) == Math.rint(after.get(RECHARGE)),
                    "Configured native tick values are fractional");
        }
    }

    private static void invalidCurves(RuntimeBalanceDefinition runtime) {
        rejects("unknown or non-rank stat", () -> parse(skill("tool_instinct", null, 3) + stat("gathering/unknown/value", .1, .5)).apply(runtime));
        rejects("unknown or non-rank stat", () -> parse(skill("tool_instinct", null, 3) + stat(ENDURANCE, 60, 660)).apply(runtime));
        rejects("floor", () -> parse(skill("tool_instinct", null, 3) + stat(TOOL, .05, .5)).apply(runtime));
        rejects("floor", () -> parse(skill("tool_instinct", null, 3) + stat(TOOL, .1, .15)).apply(runtime));
        rejects("native gameplay step", () -> parse(skill("tool_instinct", null, 3) + stat(TOOL, .1000005, .5)).apply(runtime));
        rejects("single rank", () -> parse(skill("tool_instinct", null, 1) + stat(TOOL, .1, .5)).apply(runtime));
        rejects("native floor, cap or prerequisite", () -> parse(skill("tool_instinct", null, 3) + stat(TOOL, .1, 17)).apply(runtime));
        rejects("native floor, cap or prerequisite", () -> parse(skill("untethered_flight", null, 2)).apply(runtime));
        rejects("native gameplay step", () -> parse(skill("fatigue_flight", null, 3)
                + stat(ENDURANCE, 60.5, 660) + stat(RECHARGE, 600, 60)).apply(runtime));
        rejects("native floor, cap or prerequisite", () -> parse(skill("fatigue_flight", null, 3)
                + stat(ENDURANCE, 660, 60) + stat(RECHARGE, 600, 60)).apply(runtime));
    }

    private static void preservedFiles(Map<String, SkillBalanceRuntime.ResolvedSkill> curves) throws Exception {
        var directory = Files.createTempDirectory("essence-skill-preserve-");
        var file = SkillSettingsConfig.path(directory);
        try {
            Files.createDirectories(file.getParent());
            String pending = "# Human edits awaiting explicit rebuild\n" + skill("tool_instinct", "dormant", 3) + stat(TOOL, .1, .5);
            Files.writeString(file, pending);
            SkillSettingsConfig.writeGeneratedDefaults(directory, curves);
            check(Files.readString(file).equals(pending), "Publication overwrote pending human edits");
            check(SkillSettingsConfig.read(directory).entries().get(SkillIds.TOOL_INSTINCT.toString()).ranks() == 3,
                    "Preserved pending controls did not parse");
            Files.writeString(file, "[invalid");
            SkillSettingsConfig.writeGeneratedDefaults(directory, curves);
            check(Files.readString(file).equals("[invalid"), "Publication overwrote an invalid human file");
            check(siblings(file) == 1, "Preserving an existing file created intermediate output");
            Files.delete(file);
            Files.delete(file.getParent());
            Files.writeString(file.getParent(), "Parent path is an existing human file");
            try { SkillSettingsConfig.writeGeneratedDefaults(directory, curves); throw new AssertionError("Expected publication I/O failure"); }
            catch (IOException expected) { checks++; }
            check(!Files.exists(file), "Failed publication exposed partial settings");
            check(Files.readString(file.getParent()).equals("Parent path is an existing human file"), "Failed publication damaged its parent");
        } finally { clean(directory); }
    }

    private static long siblings(Path file) throws IOException {
        try (var files = Files.list(file.getParent())) { return files.count(); }
    }
    private static void clean(Path directory) throws IOException {
        var file = SkillSettingsConfig.path(directory);
        if (Files.isDirectory(file.getParent())) Files.deleteIfExists(file);
        Files.deleteIfExists(file.getParent());
        Files.deleteIfExists(directory);
    }
    private static String skill(String id, String tier, Integer ranks) {
        return "\n[[skill]]\nid='essence_ascendance:" + id + "'\n"
                + (tier == null ? "" : "tier='essence_ascendance:" + tier + "'\n")
                + (ranks == null ? "" : "ranks=" + ranks + "\n");
    }
    private static String stat(String path, double first, double last) {
        return "\n[[skill.stat]]\npath='" + path + "'\nat_first_rank=" + first + "\nat_max_rank=" + last + "\n";
    }
    private static SkillSettingsConfig parse(String text) { return SkillSettingsConfig.parse(text, "skills-test.toml"); }
    private static void rejects(String message, Runnable action) {
        try { action.run(); } catch (BalanceConfigException expected) {
            check(expected.getMessage().contains(message), "Wrong config diagnostic: " + expected.getMessage());
            return;
        }
        throw new AssertionError("Expected config error: " + message);
    }
    private static void near(double actual, double expected, String message) {
        check(Math.abs(actual - expected) < 1e-9, message + ": actual=" + actual + ", expected=" + expected);
    }
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
}
