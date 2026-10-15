package com.mistaboom.essence_ascendance.balance.config;

import com.mistaboom.essence_ascendance.skill.*;
import com.mistaboom.essence_ascendance.skill.balance.SkillBalanceGenerator;
import com.mistaboom.essence_ascendance.skill.balance.SkillBalanceRuntime;
import com.mistaboom.essence_ascendance.tier.AscendanceTierRegistry;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Files;
import java.util.Map;
import java.util.TreeMap;

/** Original authored defaults, preserved human files and explicit prerequisite-safe unlock edits. */
public final class SkillLayoutConfigTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        // These operations must remain safe before the gameplay catalog is initialized.
        check(SkillLayoutConfig.parse("# omitted entries retain authored tiers", "empty.toml")
                .equals(SkillLayoutConfig.defaults()), "Partial input lost authored defaults");
        rejects("bad.toml:2", () -> SkillLayoutConfig.parse("[skills]\n\"essence_ascendance:typo\"='essence_ascendance:dormant'", "bad.toml"));
        rejects("powered Ascendance tier", () -> SkillLayoutConfig.parse("[skills]\n\"essence_ascendance:frenzy\"='essence_ascendance:latent'", "bad.toml"));
        rejects("schema_version", () -> SkillLayoutConfig.parse("schema_version=2", "bad.toml"));
        rejects("Unknown skill layout table", () -> SkillLayoutConfig.parse("[skill]", "bad.toml"));
        preservedFiles();

        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        com.mistaboom.essence_ascendance.essence.EssenceTypes.init();
        AscendanceTiers.init();
        com.mistaboom.essence_ascendance.stat.EssenceStats.init();
        com.mistaboom.essence_ascendance.progression.MilestoneProviders.init();
        com.mistaboom.essence_ascendance.progression.Milestones.init();
        Skills.init();

        var defaults = SkillLayoutConfig.defaults().requiredTiers();
        check(defaults.size() == SkillRegistry.size(), "Bundled authored layout no longer matches the complete catalog");
        for (var skill : SkillRegistry.values()) check(defaults.get(skill.id()).equals(skill.catalogRequiredTierId()),
                "Bundled tier differs from original authored catalog: " + skill.id());
        var changed = SkillLayoutConfig.parse("[skills]\n\"essence_ascendance:fatigue_flight\"='essence_ascendance:dormant'", "custom.toml");
        check(changed.requiredTiers().get(SkillIds.FATIGUE_FLIGHT).equals(AscendanceTiers.DORMANT.id()), "Explicit unlock edit ignored");
        rejects("precedes required prerequisite", () -> SkillLayoutConfig.parse(
                "[skills]\n\"essence_ascendance:vector_jump\"='essence_ascendance:dormant'", "custom.toml").requiredTiers());
        var sameTier = SkillLayoutConfig.parse("[skills]\n\"essence_ascendance:armor_crack\"='essence_ascendance:dormant'", "custom.toml");
        check(sameTier.requiredTiers().get(SkillIds.ARMOR_CRACK).equals(AscendanceTiers.DORMANT.id()),
                "A prerequisite may share the same tier; configuration must not invent an extra tier gate");

        var costs = new TreeMap<ResourceLocation, Long>();
        AscendanceTierRegistry.powerTiers().forEach(t -> costs.put(t.id(), 100L * t.order()));
        var original = SkillBalanceRuntime.withRequiredTiers(defaults, () -> SkillBalanceGenerator.generate(costs, 1));
        var custom = SkillBalanceRuntime.withRequiredTiers(changed.requiredTiers(), () -> SkillBalanceGenerator.generate(costs, 1));
        String flight = SkillIds.FATIGUE_FLIGHT.toString();
        check(custom.get(flight).requiredTierId().equals(AscendanceTiers.DORMANT.id()), "Generated profile lost configured unlock");
        check(custom.get(flight).ranks().getFirst().cost() < original.get(flight).ranks().getFirst().cost(),
                "Procedural rank price does not follow the configured tier budget");
        SkillBalanceRuntime.install(custom);
        try {
            check(SkillLayoutConfig.defaults().requiredTiers().equals(defaults), "Installed procedural profile contaminated authored defaults");
            check(SkillBalanceRuntime.snapshot().equals(custom), "Reading layout changed installed rank authority");
            check(SkillRegistry.require(SkillIds.FATIGUE_FLIGHT).catalogRequiredTierId().equals(AscendanceTiers.RESONANT.id()),
                    "Explicit edit mutated catalog defaults");
            try { changed.requiredTiers().put(SkillIds.FRENZY, AscendanceTiers.TRANSCENDENT.id());
                throw new AssertionError("Mutable configured layout");
            } catch (UnsupportedOperationException expected) { checks++; }
        } finally { SkillBalanceRuntime.clear(); }
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out))
                .println("SkillLayoutConfigTest: " + checks + " checks PASS");
    }

    private static void preservedFiles() throws Exception {
        var directory = Files.createTempDirectory("essence-skill-layout-");
        var file = SkillLayoutConfig.path(directory);
        try {
            check(SkillLayoutConfig.read(directory).equals(SkillLayoutConfig.defaults()), "First read did not scaffold authored defaults");
            String edited = "# Human file; intentionally partial\n[skills]\n\"essence_ascendance:fatigue_flight\"='essence_ascendance:dormant'\n";
            Files.writeString(file, edited);
            SkillLayoutConfig.read(directory);
            check(Files.readString(file).equals(edited), "Scaffold overwrote a human file");
            Files.writeString(file, "[invalid");
            SkillLayoutConfig.scaffold(directory);
            check(Files.readString(file).equals("[invalid"), "Scaffold overwrote an invalid human file");
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(file.getParent());
            Files.deleteIfExists(directory);
        }
    }

    private static void rejects(String message, Runnable action) {
        try { action.run(); } catch (BalanceConfigException expected) {
            check(expected.getMessage().contains(message), "Wrong config diagnostic: " + expected.getMessage());
            return;
        }
        throw new AssertionError("Expected config error: " + message);
    }
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
}
