package com.mistaboom.essence_ascendance.balance.config;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Dependency-free executable tests; also run by the common balance invariant task. */
public final class BalanceConfigTest {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        defaultsAndPolicies();
        actionableFailures();
        factsAndExactValues();
        scaffoldAndParsedInputs();
        System.out.println("BalanceConfigTest: " + assertions + " assertions passed");
    }

    private static void defaultsAndPolicies() throws Exception {
        check(BalanceSettings.parse(resource("essence_ascendance.toml"), "defaults").equals(BalanceSettings.defaults()),
                "Documented defaults must match centralized defaults");
        check(BalanceOverrides.parse(resource("balance_overrides.toml"), "overrides").equals(BalanceOverrides.empty()),
                "Bundled examples must not accidentally change the vanilla-like environment");
        BalanceSettings settings = BalanceSettings.parse("""
                # Reduced cost while keeping all omitted defaults
                [progression]
                cost_pressure = 7.5e-1
                [policies]
                flight = 'match_pack'
                mining = "restrict" # comment after string
                resources = "abundance_aware"
                outliers = "winsorize"
                """, "policy.toml");
        check(settings.costPressure() == 0.75, "Decimal exponent supported");
        check(settings.flightPolicy() == BalanceSettings.FlightPolicy.MATCH_PACK, "Literal policy string supported");
        check(settings.miningPolicy() == BalanceSettings.MiningPolicy.RESTRICT, "Policy parsed");
        check(settings.equipmentShare() == BalanceSettings.defaults().equipmentShare(), "Omitted controls keep declared defaults");
        BalanceSettings attunement = BalanceSettings.parse("[attunement]\npace=1.5\nrepetition_floor=0.2\nhistory_window=128", "attunement.toml");
        check(attunement.attunement().pace() == 1.5 && attunement.attunement().repetitionFloor() == .2
                && attunement.attunement().historyWindow() == 128, "Attunement controls use existing TOML parser");
        check(attunement.attunement().earlyEffortFraction() == BalanceSettings.defaults().attunement().earlyEffortFraction()
                && attunement.attunement().onboardingEffortFraction() == BalanceSettings.defaults().attunement().onboardingEffortFraction(),
                "Omitted Attunement effort controls lost deterministic defaults");
    }

    private static void actionableFailures() {
        rejected("typo.toml:2", () -> BalanceSettings.parse("[power]\noveralll=1.0", "typo.toml"));
        rejected("Unknown settings table", () -> BalanceSettings.parse("[powre]\noverall=1.0", "bad.toml"));
        rejected("must equal 1.0", () -> BalanceSettings.parse("[budget]\nequipment=0.5", "bad.toml"));
        rejected("bad.toml:2", () -> BalanceSettings.parse("[power]\noverall=-1", "bad.toml"));
        rejected("Duplicate key", () -> BalanceSettings.parse("[power]\noverall=1.0\noverall=1.1", "bad.toml"));
        rejected("Duplicate or conflicting table", () -> BalanceSettings.parse("[power]\noverall=1.0\n[power]", "bad.toml"));
        rejected("must be a number", () -> BalanceSettings.parse("[power]\noverall=\"1\"", "bad.toml"));
        rejected("Unsupported value", () -> BalanceSettings.parse("[power]\noverall=nan", "bad.toml"));
        rejected("must be one of", () -> BalanceSettings.parse("[policies]\nflight=\"matc_pack\"", "bad.toml"));
        rejected("schema_version", () -> BalanceSettings.parse("schema_version=2", "bad.toml"));
        rejected("Quote keys", () -> BalanceSettings.parse("power.overall=1.0", "bad.toml"));
        rejected("Unsupported value", () -> BalanceSettings.parse("[power]\noverall=0x2", "bad.toml"));
        rejected("Unsupported value", () -> BalanceSettings.parse("[power]\noverall=01", "bad.toml"));
        rejected("bad.toml:2", () -> BalanceSettings.parse("[attunement]\nrepetition_floor=0", "bad.toml"));
        rejected("Unknown key", () -> BalanceSettings.parse("[attunement]\nevent_cap_fraction=1", "bad.toml"));
        rejected("must be between", () -> BalanceSettings.parse("[attunement]\nearly_effort_fraction=1.1", "bad.toml"));
        rejected("must be between", () -> BalanceSettings.parse("[attunement]\nonboarding_effort_fraction=0", "bad.toml"));
        rejected("must be between", () -> BalanceSettings.parse("[attunement]\nhistory_window=100000", "bad.toml"));
        rejected("whole integer", () -> BalanceSettings.parse("[attunement]\nhistory_window=64.0", "bad.toml"));
        rejected("must be between", () -> BalanceSettings.parse("[attunement]\nmaximum_acceleration=-1", "bad.toml"));
    }

    private static void factsAndExactValues() {
        String valid = """
                schema_version = 1
                [[fact]]
                id = "bulk"
                kind = "item"
                selector = "minecraft:cobblestone"
                availability = "effectively_infinite"
                renewable = true
                confidence = 1.0
                priority = 2_000
                reason = "A # symbol inside a string is preserved."
                [[fact]]
                id = 'area'
                kind = "item_tag"
                selector = "example:area_tools"
                area_mining = true
                capabilities = [
                  "area_mining", # multiline primitive arrays
                  "vein_mining",
                ]
                [exact]
                "/runtime/cost" = 12.5
                "/runtime/enabled" = false
                "/runtime/gates" = ["example:gate", "literal~1slash"]
                "/runtime/text" = "escaped \\"quote\\" and \\u00E9"
                """;
        BalanceOverrides result = BalanceOverrides.parse(valid, "facts.toml");
        check(result.facts().get(0).id().equals("area"), "Stable id order independent of TOML table order");
        check(result.facts().get(0).strings("capabilities").equals(List.of("area_mining", "vein_mining")), "Multiline array values preserved");
        check(result.facts().get(1).priority() == 2000, "Underscores in decimal integers supported");
        check(result.facts().get(1).text("reason").orElseThrow().contains("#"), "Hash in string not a comment");
        check(result.exactValues().get("/runtime/cost").equals(12.5), "Exact numeric overrides separate from facts");
        check(result.exactValues().get("/runtime/enabled").equals(false), "Exact flag supported");
        check(result.exactValues().get("/runtime/text").equals("escaped \"quote\" and é"), "String escaping supported");
        String prefix = "[[fact]]\nkind=\"item\"\nselector=\"example:test\"\n";
        rejected("Unknown key", () -> BalanceOverrides.parse(prefix + "attainble=true", "facts.toml"));
        rejected("contradicts", () -> BalanceOverrides.parse(prefix + "disabled=true\nattainable=true", "facts.toml"));
        rejected("contradicts", () -> BalanceOverrides.parse(prefix + "creative_only=true\ninclude_reference=true", "facts.toml"));
        rejected("contradicts", () -> BalanceOverrides.parse(prefix + "availability=\"effectively_infinite\"\nrenewable=false", "facts.toml"));
        rejected("between 0 and 1", () -> BalanceOverrides.parse(prefix + "confidence=1.5", "facts.toml"));
        rejected("output_count must be a positive whole number", () -> BalanceOverrides.parse(prefix + "output_count=0.5", "facts.toml"));
        rejected("output_count must be a positive whole number", () -> BalanceOverrides.parse(prefix + "output_count=0", "facts.toml"));
        rejected("array of nonempty strings", () -> BalanceOverrides.parse(prefix + "capabilities=[3]", "facts.toml"));
        rejected("must be one of", () -> BalanceOverrides.parse(prefix + "stage=\"endgme\"", "facts.toml"));
        check(BalanceOverrides.parse(prefix + "slot=\"mainhand_caster\"", "facts.toml").facts().getFirst()
                .text("slot").orElseThrow().equals("mainhand_caster"), "Explicit custom equipment family accepted");
        rejected("slot must be one of", () -> BalanceOverrides.parse(prefix + "slot=\"weapon\"", "facts.toml"));
        rejected("slot must be a nonempty string", () -> BalanceOverrides.parse(prefix + "slot=1", "facts.toml"));
        rejected("Duplicate fact id", () -> BalanceOverrides.parse(prefix + "disabled=true\n" + prefix + "disabled=false", "facts.toml"));
        rejected("JSON Pointer", () -> BalanceOverrides.parse("[exact]\n\"runtime.cost\"=1", "facts.toml"));
        rejected("JSON Pointer", () -> BalanceOverrides.parse("[exact]\n\"/runtime/bad~2escape\"=1", "facts.toml"));
        rejected("Unknown override table", () -> BalanceOverrides.parse("[[facts]]", "facts.toml"));
        check(BalanceOverrides.parse("[[fact]]\nkind=\"enemy\"\nselector=\"example:test\"\nclassification=\"unknown\"", "enemy.toml")
                .facts().get(0).text("classification").orElseThrow().equals("unknown"), "Unknown enemy encounter remains an explicit classification");
        rejected("Unclosed array", () -> BalanceOverrides.parse(prefix + "capabilities=[\"flight\",", "facts.toml"));
        rejected("Nested arrays", () -> BalanceOverrides.parse("[exact]\n\"/runtime/x\"=[[1]]", "facts.toml"));
        try {
            result.exactValues().put("/new", 1);
            throw new AssertionError("Exact values must be immutable");
        } catch (UnsupportedOperationException expected) { assertions++; }
    }

    private static void scaffoldAndParsedInputs() throws Exception {
        Path directory = Files.createTempDirectory("essence-balance-config-test-");
        try {
            BalanceInputs original = BalanceInputs.read(directory);
            check(original.settings().equals(BalanceSettings.defaults()), "First read creates parseable defaults");
            Path settings = BalanceInputs.settingsPath(directory);
            String changed = Files.readString(settings) + "\n# deliberate pack-maker edit\n";
            Files.writeString(settings, changed);
            BalanceInputs next = BalanceInputs.read(directory);
            check(original.equals(next), "Comments preserve the same parsed generation inputs");
            check(Files.readString(settings).equals(changed), "Scaffolding never overwrites a user's file");
            Files.writeString(settings, "[invalid TOML");
            BalanceInputs.scaffold(directory);
            check(Files.readString(settings).equals("[invalid TOML"), "Scaffolding preserves edited inputs without parsing them");
            try {
                BalanceInputs.read(directory);
                throw new AssertionError("Explicit generation input read accepted invalid TOML");
            } catch (BalanceConfigException expected) { assertions++; }
            Files.writeString(settings, "#" + "x".repeat(4_000_000));
            try {
                BalanceInputs.read(directory);
                throw new AssertionError("Oversized generation input accepted");
            } catch (java.io.IOException expected) {
                check(expected.getMessage().contains("exceeds 4 MB"), "Oversized generation input retains an actionable bounded-read failure");
            }
        } finally {
            Files.deleteIfExists(BalanceInputs.settingsPath(directory));
            Files.deleteIfExists(BalanceInputs.overridesPath(directory));
            Files.deleteIfExists(directory.resolve("essence_ascendance"));
            Files.deleteIfExists(directory);
        }
    }

    private static String resource(String name) throws Exception {
        try (InputStream input = BalanceConfigTest.class.getResourceAsStream("/balance/" + name)) {
            if (input == null) throw new AssertionError("Missing default resource " + name);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void rejected(String expected, Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected config rejection containing: " + expected);
        } catch (BalanceConfigException exception) {
            check(exception.getMessage().contains(expected), "Actionable error expected '" + expected + "', got '" + exception.getMessage() + "'");
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
    }
}
