package com.mistaboom.essence_ascendance.balance.runtime;

import com.google.gson.JsonObject;
import com.mistaboom.essence_ascendance.balance.config.BalanceOverrides;
import com.mistaboom.essence_ascendance.balance.config.BalanceSettings;
import com.mistaboom.essence_ascendance.balance.engine.*;
import com.mistaboom.essence_ascendance.balance.economy.*;
import com.mistaboom.essence_ascendance.attunement.AttunementProfile;
import com.mistaboom.essence_ascendance.attunement.AttunementActivity;
import com.mistaboom.essence_ascendance.attunement.AttunementActivityRegistry;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import com.mistaboom.essence_ascendance.progression.*;
import com.mistaboom.essence_ascendance.skill.Skills;
import com.mistaboom.essence_ascendance.stat.EssenceStats;
import com.mistaboom.essence_ascendance.tier.AscendanceTierRegistry;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Generated unlocks must be reachable before the next tier, with no prescribed build. */
public final class RuntimeAscensionPolicyTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        Thread.currentThread().setUncaughtExceptionHandler((t,e) -> e.printStackTrace(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.err))));
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        EssenceTypes.init(); AscendanceTiers.init(); EssenceStats.init();
        MilestoneProviders.init(); Milestones.init(); Skills.init(); AscendanceAdvancements.init();
        com.mistaboom.essence_ascendance.equipment.EquipmentProfiles.init();
        if (args.length > 0) {
            replaySavedEvidence(java.nio.file.Path.of(args[0]), args.length > 1 ? java.nio.file.Path.of(args[1])
                    : java.nio.file.Path.of("build/attunement-real-calibration.txt"));
        }
        var evidence = RuntimeReferencePolicy.bootstrapEvidence();
        var defaults = RuntimeBalanceDefinition.generate(evidence, BalanceSettings.defaults(), BalanceOverrides.empty());
        check(defaults.attunement() != null, "Generated Category Attunement profile missing");
        check(defaults.config().balanceProfile().getDefaultInvestmentCap(AscendanceTiers.LATENT) == 0
                        && defaults.config().balanceProfile().tierFractions().get(AscendanceTiers.LATENT.id()) == 0
                        && !AscendanceTiers.LATENT.grantsPower(),
                "Latent tier grants player power");
        breadth();
        verify(defaults);
        sourceReachability(evidence, defaults);
        scarcePackRoutes(evidence, defaults);
        for (double length : new double[]{0.1, 1, 10}) for (double pressure : new double[]{0.1, 1, 10}) {
            var settings = BalanceSettings.parse("[progression]\nlength=" + length + "\ncost_pressure=" + pressure, "policy-fixture");
            var generated = RuntimeBalanceDefinition.generate(evidence, settings, BalanceOverrides.empty());
            verify(generated);
            check(generated.toJson().equals(RuntimeBalanceDefinition.generate(evidence, settings, BalanceOverrides.empty()).toJson()),
                    "Progression generation changed with identical inputs");
        }
        String first = AscendanceAdvancements.DORMANT_TO_AWAKENED.id().toString();
        String second = AscendanceAdvancements.AWAKENED_TO_RESONANT.id().toString();
        reject(defaults, j -> j.getAsJsonObject("advancements").remove(first), "Missing transition accepted");
        reject(defaults, j -> j.getAsJsonObject("advancements").getAsJsonObject(first)
                .addProperty("toTierId", AscendanceTiers.RESONANT.id().toString()), "Skipped tier accepted");
        reject(defaults, j -> j.getAsJsonObject("advancements").getAsJsonObject(second)
                .addProperty("fromTierId", AscendanceTiers.DORMANT.id().toString()), "Duplicate origin accepted");
        reject(defaults, j -> j.getAsJsonObject("advancements").getAsJsonObject(first)
                .addProperty("minimumDevelopedStats", Integer.MAX_VALUE), "Impossible stat breadth accepted");
        reject(defaults, j -> j.getAsJsonObject("advancements").getAsJsonObject(first)
                .addProperty("minimumRepresentedCategories", Integer.MAX_VALUE), "Impossible category breadth accepted");
        reject(defaults, j -> j.getAsJsonObject("advancements").getAsJsonObject(first)
                .addProperty("totalInvestmentMultiplier", Long.MAX_VALUE), "Overflowing threshold accepted");
        reject(defaults, j -> j.remove("attunement"), "Old saved profile silently acquired new policy without explicit rebuild");
        String source = AscendanceTiers.DORMANT.id().toString();
        reject(defaults, j -> j.getAsJsonObject("attunement").getAsJsonObject("chapters").remove(source), "Missing chapter accepted");
        reject(defaults, j -> j.getAsJsonObject("attunement").getAsJsonObject("chapters").getAsJsonObject(source)
                .addProperty("requiredCategories", 7), "Impossible category breadth accepted");
        reject(defaults, j -> j.getAsJsonObject("attunement").getAsJsonObject("policy").addProperty("repetitionFloor", 0), "Zero repeated farm contribution accepted");
        reject(defaults, j -> j.getAsJsonObject("attunement").getAsJsonObject("methods").remove("run"), "Incomplete methods accepted");
        reject(defaults, j -> j.getAsJsonObject("attunement").getAsJsonObject("chapters").getAsJsonObject(source)
                .getAsJsonObject("activities").getAsJsonObject("run").addProperty("contributionPerUnit", 0), "Unreachable method accepted");
        reject(defaults, j -> j.getAsJsonObject("advancements").getAsJsonObject(first)
                .add("worldRequirement", milestoneGate(Milestones.OBTAIN_DIAMONDS.id().toString())), "Future-tier mining gate accepted");
        var faster = RuntimeBalanceDefinition.generate(evidence, BalanceSettings.parse("[attunement]\npace=2.0", "faster"), BalanceOverrides.empty());
        var defaultChapter = defaults.attunement().chapter(source);
        var fastChapter = faster.attunement().chapter(source);
        check(fastChapter.categories().values().iterator().next().target() < defaultChapter.categories().values().iterator().next().target(), "Faster pace did not reduce effort");
        check(defaults.toJson().get("equipment").equals(faster.toJson().get("equipment")), "Attunement tuning changed accepted equipment curves");
        check(defaults.toJson().get("skillCurves").equals(faster.toJson().get("skillCurves")), "Attunement tuning repriced skill receipts");
        check(defaults.toJson().get("infuser").equals(faster.toJson().get("infuser")), "Attunement tuning changed unrelated economy");
        var fullEarly = RuntimeBalanceDefinition.generate(evidence,
                BalanceSettings.parse("[attunement]\nearly_effort_fraction=1.0\nonboarding_effort_fraction=1.0", "full-effort"),
                BalanceOverrides.empty());
        check(defaults.attunement().chapter(AscendanceTiers.DORMANT.id().toString()).categories().values().iterator().next().target()
                        < fullEarly.attunement().chapter(AscendanceTiers.DORMANT.id().toString()).categories().values().iterator().next().target(),
                "Opening powered chapter did not receive its configured effort reduction");
        check(defaults.attunement().chapter(AscendanceTiers.LATENT.id().toString()).categories().values().iterator().next().target()
                        < defaults.attunement().chapter(AscendanceTiers.DORMANT.id().toString()).categories().values().iterator().next().target(),
                "Latent onboarding is not shorter than the first powered chapter");
        AttunementActivityRegistry.register(new AttunementActivity("future_test_action", "essence_ascendance:offense", "health", "damage", false));
        var extended = RuntimeBalanceDefinition.generate(evidence, BalanceSettings.defaults(), BalanceOverrides.empty());
        check(extended.attunement().methods().containsKey("future_test_action"), "Generic future activity missing from generated profile");
        check(extended.attunement().chapter(source).activities().get("future_test_action").contributionPerUnit()
                == defaultChapter.activities().get("deal_damage").contributionPerUnit(), "Future method did not inherit family calibration");
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out)).println(
                "RuntimeAscensionPolicyTest: " + checks + " chapter generation, breadth, calibration, zero-skill reachability, strict rebuild and unrelated-curve preservation checks PASS");
    }
    private static JsonObject milestoneGate(String id) {
        var gate = new JsonObject(); gate.addProperty("kind", "milestone"); gate.addProperty("id", id); return gate;
    }
    private static void verify(RuntimeBalanceDefinition runtime) {
        var tiers = AscendanceTierRegistry.values().stream().sorted(Comparator.comparingInt(t -> t.order())).toList();
        var config = runtime.config(); var profile = config.balanceProfile();
        check(config.advancements().size() == tiers.size() - 1, "Transition count differs from tier chain");
        long previous = 0;
        for (int i = 0; i + 1 < tiers.size(); i++) {
            var from = tiers.get(i); var to = tiers.get(i + 1);
            var transition = config.advancements().values().stream().filter(a -> a.fromTierId().equals(from.id())).findFirst().orElseThrow();
            check(transition.toTierId().equals(to.id()), "Transition does not advance exactly one tier");
            var chapter = runtime.attunement().chapter(from.id().toString());
            check(chapter.requiredCategories() == i + 1, "Default breadth differs from procedural 1/2/3/4/5");
            check(chapter.toTierId().equals(to.id().toString()), "Chapter transition skipped tier");
            for (var category : chapter.categories().values()) {
                check(category.target() > 0 && category.investmentReference() > 0, "Missing generated target/reference");
                check(runtime.attunement().methods().values().stream().filter(method -> method.categoryId().equals(category.categoryId()) && method.baseGameAccessible()).count() >= 2,
                        "Category lacks multiple no-skill base methods");
            }
            long target = chapter.categories().values().iterator().next().target();
            check(target >= previous, "Chapter effort decreases across generated growth");
            previous = target;
            check(chapter.activities().get("run").contributionPerUnit() < chapter.activities().get("swim").contributionPerUnit(), "Running raw distance is not slowest");
            check(chapter.activities().get("run").contributionPerUnit() < chapter.activities().get("fly").contributionPerUnit(), "Flying lacks separate faster distance calibration");
            check(chapter.activities().get("explore_biome").contributionPerUnit() > 100 * chapter.activities().get("run").contributionPerUnit(), "Exploration is not substantially faster per event");
            var references = runtime.attunement().references();
            double healing = chapter.activities().get("heal_health").referenceUnits();
            double food = chapter.activities().get("consume_hunger").referenceUnits();
            check(healing == references.get("health_recovery_" + from.id().getPath()) && healing <= references.get("player_health"), "Healing does not match stage damage/recovery evidence");
            check(chapter.activities().get("restore_hunger").referenceUnits() >= food, "Batched food restoration was valued as instantaneous exertion");
            check(food == references.get("hunger_consumption_per_window") && food < references.get("food_capacity"), "Exertion was calibrated against a whole food bar instead of native consumption");
            double nativeFoodFromRunning = chapter.activities().get("run").referenceUnits()
                    * references.get("native_sprint_exhaustion_per_block") / references.get("exhaustion_per_food");
            check(Math.abs(nativeFoodFromRunning / food - references.get("run_accessibility_pressure")) < 1e-9,
                    "Running and actual native hunger no longer share dimensionally consistent calibration windows");
            check(transition.minimumDevelopedStats() == 0 && transition.minimumRepresentedCategories() == 0, "Generated unlock forces a Nexus build");
            check(transition.worldRequirement() instanceof MilestoneRequirement.Always, "Generated unlock requires a specific world/boss milestone");
        }
        check(HarvestProgressionSafety.evaluate(config).isEmpty(), "Generated unlock has a harvest progression cycle");
        check(config.milestones().containsKey(Milestones.SKY_LIMIT.id()), "Skill-specific acquisition milestone removed");
    }
    private static void breadth() {
        for (int categories = 1; categories <= 64; categories++) for (int transitions = 1; transitions <= 17; transitions++) {
            int previous = 0;
            for (int position = 1; position <= transitions; position++) {
                int actual = AttunementProfile.requiredCategories(categories, position, transitions, 1);
                check(actual >= previous && actual >= 1 && actual <= Math.max(1, categories - 1), "Unusual registry breadth outside safe monotonic bounds");
                check(actual == Math.max(1, (int) Math.ceil((categories - 1) * position / (double) transitions)), "Breadth differs from ratio formula");
                previous = actual;
            }
            check(previous == Math.max(1, categories - 1), "Final transition must leave one optional category where possible");
        }
    }
    private static void sourceReachability(PackEvidence base, RuntimeBalanceDefinition runtime) {
        check(runtime.attunement().assumptions().stream().anyMatch(text -> text.contains("no positive FARMING")), "Missing family evidence lost its explicit fallback diagnostic");
        for (var kind : List.of(AcquisitionSource.Kind.FARMING, AcquisitionSource.Kind.FISHING)) {
            var source = new AcquisitionSource("test:" + kind.name().toLowerCase(java.util.Locale.ROOT), kind, ProgressionBand.ENTRY, 1,
                    true, false, 0, 1, List.of(), "Declared ordinary player-operated source fixture");
            var resource = new ResourceEvidence("test:output", ProgressionBand.ENTRY, Availability.RENEWABLE_MANUAL,
                    Automation.PLAYER_GATED, true, true, 1, 1, List.of(source), List.of());
            var evidence = new PackEvidence(Map.of(resource.itemId(), resource), base.equipment(), base.enemies(), base.frontiers(), base.facts(), base.warnings(), base.graphSummary(), base.capabilities());
            var suppressed = new EconomyProfile(Map.of(resource.itemId(), new EconomyProfile.ResourceValue(new EconomicValue(1),
                    DissolutionYield.of(0), Map.of(), List.of())), List.of(), List.of(), List.of(), 1, EconomyProcessingPolicy.defaults());
            try {
                AttunementGenerator.generate(evidence, suppressed, BalanceSettings.defaults(), runtime.config().balanceProfile());
                throw new AssertionError("Known entirely suppressed " + kind + " route was silently declared reachable");
            } catch (IllegalArgumentException expected) {
                check(expected.getMessage().contains(kind.name()) && expected.getMessage().contains("zero generated yield"), "Unreachable source family lacks actionable diagnostic");
            }
            var productive = new EconomyProfile(Map.of(resource.itemId(), new EconomyProfile.ResourceValue(new EconomicValue(1),
                    DissolutionYield.of(2), Map.of("essence_ascendance:gathering", 2.0), List.of())), List.of(), List.of(), List.of(), 1, EconomyProcessingPolicy.defaults());
            var generated = AttunementGenerator.generate(evidence, productive, BalanceSettings.defaults(), runtime.config().balanceProfile());
            var chapter = generated.chapter(AscendanceTiers.DORMANT.id().toString());
            String activity = kind == AcquisitionSource.Kind.FARMING ? "harvest_crops" : "catch_fish";
            check(chapter.activities().get(activity).referenceUnits() == 2, "Productive family did not use its installed source calibration");
        }
    }
    /** Optional local acceptance fixture: reads real saved evidence, never rewrites or installs the user's profile. */
    private static void replaySavedEvidence(java.nio.file.Path source, java.nio.file.Path report) throws Exception {
        String original = java.nio.file.Files.readString(source);
        var document = com.mistaboom.essence_ascendance.balance.generated.BalanceDocument.parse(original);
        var gson = com.mistaboom.essence_ascendance.balance.generated.BalanceDocument.GSON;
        var evidence = gson.fromJson(document.section("evidence"), PackEvidence.class);
        var economy = gson.fromJson(document.section("economy"), EconomyProfile.class);
        var settings = gson.fromJson(document.section("settings"), BalanceSettings.class);
        var overrides = gson.fromJson(document.section("overrides"), BalanceOverrides.class);
        var old = document.section("runtime");
        var generated = RuntimeBalanceDefinition.generate(evidence, economy, settings, overrides);
        var current = generated.toJson();
        StringBuilder out = new StringBuilder("READ-ONLY SAVED-EVIDENCE ATTUNEMENT REPLAY\nSource: ").append(source.toAbsolutePath())
                .append("\nSource integrity: ").append(document.integrity()).append("\nEquipment references: ").append(evidence.equipment().size())
                .append("; enemy references: ").append(evidence.enemies().size()).append("; valued resources: ").append(economy.resources().size()).append('\n');
        boolean unchanged = true;
        for (String field : List.of("equipment", "statMaxBonuses", "skillCurves", "infuser", "shield", "effects", "pylons", "crucible")) {
            // Vitality is the intentionally evolving runtime section: its
            // recovery projection now includes the Nexus passive source.
            // Keep the replay invariant strict for every unrelated effect.
            boolean same = field.equals("effects")
                    ? withoutVitality(old.getAsJsonObject(field)).equals(withoutVitality(current.getAsJsonObject(field)))
                    : old.get(field).equals(current.get(field));
            unchanged &= same;
            out.append("Unrelated runtime section ").append(field.equals("effects") ? "effects (except vitality)" : field)
                    .append(": ").append(same ? "UNCHANGED" : "DIFFERS").append('\n');
        }
        for (String field : List.of("defaultTierCaps", "statOverrides", "tierFractions", "investmentExponent")) {
            boolean same = old.getAsJsonObject("balanceProfile").get(field).equals(current.getAsJsonObject("balanceProfile").get(field)); unchanged &= same;
            out.append("Unrelated balance curve ").append(field).append(": ").append(same ? "UNCHANGED" : "DIFFERS").append('\n');
        }
        out.append("\nCHAPTER CALIBRATION (zero investment; raw references before repetition and variety)\n");
        var profile = generated.attunement();
        out.append("Per-root cap=none; repeated-source floor=")
                .append(profile.policy().repetitionFloor()).append("; maximum added investment multiplier=").append(profile.policy().maximumAcceleration()).append('\n');
        for (var tier : AscendanceTierRegistry.values().stream().sorted(Comparator.comparingInt(value -> value.order())).toList()) {
            var chapter = profile.chapter(tier.id().toString()); if (chapter == null) continue;
            out.append(chapter.id()).append(" categories=").append(chapter.requiredCategories()).append('/').append(chapter.categories().size()).append('\n');
            for (var category : chapter.categories().values()) out.append("  ").append(category.categoryId()).append(" target=").append(category.target())
                    .append(" investment_reference=").append(category.investmentReference()).append('\n');
            for (var rate : chapter.activities().values()) out.append("  method=").append(rate.activityId()).append(" units=").append(rate.units())
                    .append(" reference=").append(rate.referenceUnits()).append(" gain_per_unit=").append(rate.contributionPerUnit())
                    .append(" raw_units_per_seal=").append(chapter.categories().get(rate.categoryId()).target()/rate.contributionPerUnit()).append('\n');
        }
        out.append("\nDECLARED ASSUMPTIONS / REACHABILITY BOUNDARIES\n");
        pacingScenarios(profile, out);
        profile.assumptions().forEach(assumption -> out.append(assumption).append('\n'));
        out.append("\nNo server/world execution or gameplay pacing acceptance is implied. The original generated profile remains unchanged; explicitly rebuild in game to install the new calibration.\n");
        java.nio.file.Files.createDirectories(report.toAbsolutePath().getParent());
        java.nio.file.Files.writeString(report, out.toString());
        check(java.nio.file.Files.readString(source).equals(original), "Read-only replay changed the user's generated profile");
        check(generated.toJson().equals(RuntimeBalanceDefinition.fromJson(generated.toJson()).toJson()), "Real evidence generated profile failed strict round trip");
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out)).println("Saved evidence replay written to " + report + "; unrelated runtime curves unchanged=" + unchanged);
        check(unchanged, "Attunement replay changed an unrelated saved runtime curve; inspect " + report);
    }
    private static com.google.gson.JsonObject withoutVitality(com.google.gson.JsonObject effects) {
        var copy = effects.deepCopy();
        copy.remove("vitality");
        return copy;
    }
    private static void scarcePackRoutes(PackEvidence empty, RuntimeBalanceDefinition defaults) {
        var nativeXp = new EnemyReference("fixture:pack_enemy", EnemyReference.Encounter.ROUTINE, ProgressionBand.EARLY,
                Map.of(CapabilityAxis.EFFECTIVE_HEALTH, 40.0, CapabilityAxis.BURST_DAMAGE, 7.0, CapabilityAxis.EXPERIENCE, 3.0),
                true, 1, List.of(), "Observed pack-defined reward");
        var scarce = new PackEvidence(Map.of(), empty.equipment(), List.of(nativeXp), empty.frontiers(), empty.facts(), empty.warnings(), empty.graphSummary());
        var generated = RuntimeBalanceDefinition.generate(scarce, BalanceSettings.defaults(), BalanceOverrides.empty()).attunement();
        var chapter = generated.chapter(AscendanceTiers.LATENT.id().toString());
        check(chapter.activities().get("gain_experience").referenceUnits()==3, "Pack XP reward was replaced by a vanilla or health-derived constant");
        check(!chapter.activities().get("brew_potions").stageAccessible(), "Missing workstation was declared stage accessible");
        check(chapter.activities().get("gain_experience").stageAccessible(), "Scarce workstations locked ordinary XP");
        check(generated.references().get("accessibility_gain_utility_latent")>1, "Scarce route evidence did not compensate Utility progress");
        check(generated.references().get("enemy_damage_latent")==7, "Entry Attunement fell back to player attack despite pack enemy evidence");
        check(defaults.attunement().chapter(AscendanceTiers.LATENT.id().toString()).activities().values().stream()
                .allMatch(r -> Double.isFinite(r.contributionPerUnit()) && r.contributionPerUnit()>0), "Unknown pack evidence disabled fallback routes");
    }

    private static void pacingScenarios(AttunementProfile profile, StringBuilder out) {
        var chapter=profile.chapter(AscendanceTiers.LATENT.id().toString());
        var ledger=new com.mistaboom.essence_ascendance.attunement.AttunementLedger(); ledger.chapter(chapter.id());
        double distance=profile.references().get("run_blocks_per_second") * 100;
        int packets=100*SharedConstants.TICKS_PER_SECOND;
        com.mistaboom.essence_ascendance.attunement.AttunementContribution last=null;
        for(int i=0;i<packets;i++) last=ledger.contribute("run"+i,
                com.mistaboom.essence_ascendance.attunement.AttunementEvent.Outcome.eligible("run","same_region",distance/packets),chapter,profile.policy(),0);
        check(last.repetitionMultiplier()>.8,"Brief ordinary running hit severe repetition despite low reference work");
        out.append("\nPACING SCENARIOS (Latent, zero investment, fixed test inputs; not generated requirements)\n")
                .append("100 seconds same-region running: progress_percent=").append(ledger.progress("essence_ascendance:mobility")/10_000_000.0)
                .append("; ending_efficiency=").append(last.repetitionMultiplier()).append('\n');
        var food=new com.mistaboom.essence_ascendance.attunement.AttunementLedger(); food.chapter(chapter.id());
        // Two full vanilla steak restorations and fourteen health are intentionally generous actual-outcome fixtures.
        var steak=net.minecraft.world.item.Items.COOKED_BEEF.components().get(net.minecraft.core.component.DataComponents.FOOD);
        for(int i=0;i<2;i++) food.contribute("food"+i,
                com.mistaboom.essence_ascendance.attunement.AttunementEvent.Outcome.eligible("restore_hunger","steak",steak.nutrition()+steak.saturation()),chapter,profile.policy(),0);
        food.contribute("heal",com.mistaboom.essence_ascendance.attunement.AttunementEvent.Outcome.eligible("heal_health","natural",14),chapter,profile.policy(),0);
        double percent=food.progress("essence_ascendance:vitality")/10_000_000.0;
        check(percent<30,"Two food items still dominate the onboarding Vitality seal");
        out.append("Two maximum steak restorations plus 14 health: vitality_percent=").append(percent).append('\n');
        for(String method:List.of("deal_damage","take_damage","run","restore_hunger","gain_experience")) {
            var rate=chapter.activities().get(method); var category=chapter.categories().get(rate.categoryId());
            double raw=com.mistaboom.essence_ascendance.attunement.AttunementPacing.rawUnits(rate,category);
            double repeated=com.mistaboom.essence_ascendance.attunement.AttunementPacing.repeatedUnits(rate,category,profile.policy());
            check(repeated>=raw&&repeated<=raw/profile.policy().repetitionFloor(),"Repeated source pacing has an unreachable wall");
            out.append("Single-source route ").append(method).append(": raw_units=").append(raw).append("; repeated_units=").append(repeated).append('\n');
        }
    }
    private static void reject(RuntimeBalanceDefinition base, Consumer<JsonObject> mutation, String message) {
        JsonObject json = base.toJson(); mutation.accept(json);
        try { RuntimeBalanceDefinition.fromJson(json); } catch (RuntimeException expected) { checks++; return; }
        throw new AssertionError(message);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); checks++; }
}
