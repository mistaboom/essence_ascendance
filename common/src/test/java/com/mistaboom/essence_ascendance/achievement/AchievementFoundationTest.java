package com.mistaboom.essence_ascendance.achievement;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mistaboom.essence_ascendance.gameplay.CommittedReceiptWindow;
import com.mistaboom.essence_ascendance.gameplay.DissolutionOutcome;
import net.minecraft.resources.ResourceLocation;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Executable rule/resource invariants. Actual native transactions/save files are covered by AchievementNativeFixture. */
public final class AchievementFoundationTest {
    private static int checks;
    private static final UUID ALICE = new UUID(1, 1), BOB = new UUID(2, 2);
    private static final ResourceLocation DIMENSION = ResourceLocation.parse("minecraft:overworld");
    private static final Set<Integer> IMPLEMENTED = Set.of(19, 22, 26, 28, 30, 31, 32, 33, 34,
            35, 36, 61, 72, 139, 166, 169, 205, 208,
            24, 46, 49, 51, 56, 57, 63, 73, 190, 197, 199,
            76, 77, 85, 120, 136, 138, 184, 206, 207, 215,
            1, 2, 3, 4, 5, 7, 95, 99, 100, 101, 102, 145, 201, 216,
            107, 110, 114, 176, 177, 193, 200, 209,
            118, 119, 122, 126, 128, 131, 182, 204);

    public static void main(String[] args) throws Exception {
        ruleOutcomes();
        receiptBoundaries();
        repeatedReceiptWorkload();
        resources();
        System.out.println("Achievement foundation: " + checks + " checks passed");
    }

    private static DissolutionOutcome receipt(String action, UUID actor, long tick) {
        return new DissolutionOutcome(action, actor, DIMENSION, 0, 1, BigInteger.ONE, tick);
    }

    private static void ruleOutcomes() {
        var rule = new EquivalentExchangeRule();
        Set<UUID> completed = new HashSet<>();
        var first = receipt("first", ALICE, 10);
        check(rule.consume(first, 10, actor -> true, completed::add), "Positive fractional yield earns completion");
        check(completed.equals(Set.of(ALICE)), "Nearby/other player is not credited");
        check(!rule.consume(first, 10, actor -> true, completed::add), "Duplicate delivery is ignored");
        completed.remove(ALICE); // Native command revoke: replay must not undo it.
        check(!rule.consume(first, 11, actor -> true, completed::add), "Revoke is not reversed by duplicate receipt");
        check(rule.consume(receipt("second", ALICE, 11), 11, actor -> true, completed::add), "A fresh successful action re-earns after revoke");
        check(!rule.consume(receipt("third", ALICE, 12), 12, actor -> true, completed::add), "Completed award is idempotent");
        check(rule.consume(receipt("first", BOB, 12), 12, actor -> true, completed::add), "Receipt identity is scoped to actor");
        check(!rule.consume(receipt("creative", ALICE, 13), 13, actor -> false, actor -> { throw new AssertionError("Ineligible award"); }), "Creative/offline/canceled eligibility fails");
        check(!rule.consume(null, 13, actor -> true, completed::add), "No committed outcome cannot earn");
        check(!rule.consume(receipt("stale", ALICE, 0), 200, actor -> true, actor -> { throw new AssertionError("Stale award"); }), "Expired facts cannot regrant");
        rejects(() -> new DissolutionOutcome("zero", ALICE, DIMENSION, 0, 1, BigInteger.ZERO, 0));
        rejects(() -> new DissolutionOutcome("negative", ALICE, DIMENSION, 0, 1, BigInteger.ONE.negate(), 0));
        rejects(() -> new DissolutionOutcome("unconsumed", ALICE, DIMENSION, 0, 0, BigInteger.ONE, 0));
        rejects(() -> new DissolutionOutcome("missing", null, DIMENSION, 0, 1, BigInteger.ONE, 0));
        var otherWorld = new EquivalentExchangeRule();
        check(otherWorld.consume(first, 10, actor -> true, actor -> true), "Other server session has independent receipt history");
    }

    private static void receiptBoundaries() {
        var window = new CommittedReceiptWindow();
        check(!window.accept(ALICE, "future", 11, 10), "Future receipt rejected");
        check(window.accept(ALICE, "last-tick", 0, 199), "Last live receipt tick accepted");
        check(!window.accept(ALICE, "expired", 0, 200), "Exact expiry rejected");
        for (int i = 0; i < CommittedReceiptWindow.MAX_RECEIPTS; i++)
            check(window.accept(ALICE, "capacity-" + i, 200, 200), "Bounded live slot accepted");
        check(window.size() == CommittedReceiptWindow.MAX_RECEIPTS, "Expired records cleaned on next delivery");
        check(!window.accept(ALICE, "overflow", 200, 200), "Overflow fails closed without evicting a live receipt");
        check(!window.accept(ALICE, "capacity-0", 200, 200), "Overflow cannot reopen earlier receipt");
        check(window.accept(ALICE, "after-expiry", 400, 400), "Expired capacity becomes available");
        check(window.size() == 1, "History stays bounded and expires");
    }

    private static void repeatedReceiptWorkload() {
        var window = new CommittedReceiptWindow();
        check(window.accept(ALICE, "newer", 100, 100), "Newer commit accepted first");
        check(window.accept(ALICE, "older", 50, 100), "Late delivery keeps its original commit clock");
        check(window.accept(ALICE, "prune", 250, 250), "Late commit expires independently");
        check(window.size() == 2, "Out-of-order expiry retains the newer live receipt");
        check(!window.accept(ALICE, "newer", 100, 250), "Live newer key remains deduplicated");
        check(window.accept(ALICE, "rewind", 80, 80), "Clock rewind accepts new present action");
        check(window.size() == 1, "Clock rewind removes previous future entries from both indexes");
        check(window.accept(ALICE, "newer", 80, 80), "Invalidated future identity can be reused");
        check(!window.accept(ALICE, "newer", 80, 80), "Reused identity is still deduplicated");

        window = new CommittedReceiptWindow();
        long started = System.nanoTime();
        int peak = 0;
        for (int tick = 0; tick < 10_000; tick++) {
            for (int event = 0; event < 10; event++) {
                String action = tick + ":" + event;
                check(window.accept(ALICE, action, tick, tick), "Repeated workload accepts fresh operation");
                check(!window.accept(ALICE, action, tick, tick), "Repeated workload rejects duplicate operation");
            }
            peak = Math.max(peak, window.size());
            check(window.size() <= 2000, "Ten events/tick retain only the 200-tick live window");
        }
        check(peak == 2000, "Representative workload reaches steady bounded history");
        check(window.accept(BOB, "after-idle", 10_200, 10_200), "Delivery after idle expires all prior receipts");
        check(window.size() == 1, "Idle expiry leaves only the new receipt");
        System.out.println("Receipt workload: 100000 commits + 100000 duplicate deliveries; peak=" + peak
                + ", remaining=" + window.size() + ", elapsedMs=" + (System.nanoTime() - started) / 1_000_000);
    }

    private static void resources() throws Exception {
        Path root = Path.of(System.getProperty("essence.repository.root"));
        JsonObject catalog = JsonParser.parseString(Files.readString(root.resolve("docs/achievements/catalog.json"))).getAsJsonObject();
        check(catalog.get("schemaVersion").getAsInt() == 1, "Design catalog schema");
        check(catalog.getAsJsonObject("provenance").get("sha256").getAsString().matches("[0-9a-f]{64}"), "Workbook provenance is content-addressed");
        check(catalog.getAsJsonArray("achievements").size() == 69, "Approved roster only");
        Set<Integer> ids = new HashSet<>(); Set<String> categories = new HashSet<>();
        JsonObject selected = null;
        for (var entry : catalog.getAsJsonArray("achievements")) {
            var achievement = entry.getAsJsonObject();
            int id = achievement.get("id").getAsInt();
            check(ids.add(id), "Stable ID unique");
            check(achievement.get("resourceId").getAsString().equals("essence_ascendance:achievements/a%03d".formatted(id)), "Resource independent of mutable display name");
            check(!achievement.has("status"), "Internal status is separate from approved design");
            achievement.getAsJsonArray("capabilities").forEach(category -> categories.add(category.getAsString()));
            if (id == 22) selected = achievement;
        }
        check(ids.equals(IMPLEMENTED), "Every approved stable ID has an implementation/resource, with no extra ID");
        check(categories.size() == 16 && selected != null, "Shared planning categories and slice present");
        var advancement = resource("data/essence_ascendance/advancement/achievements/a022.json");
        var translations = resource("assets/essence_ascendance/lang/en_us.json");
        Set<String> expectedLanguage = new HashSet<>();
        for (int id : IMPLEMENTED) {
            String title = "advancement.essence_ascendance.a%03d".formatted(id);
            expectedLanguage.add(title); expectedLanguage.add(title + ".description");
        }
        Set<String> selectedLanguage = new HashSet<>();
        translations.keySet().stream().filter(key -> key.matches("advancement\\.essence_ascendance\\.a[0-9]+(?:\\..*)?"))
                .forEach(selectedLanguage::add);
        check(selectedLanguage.equals(expectedLanguage), "No orphan, extra or missing selected achievement language keys");
        var presentation = JsonParser.parseString(Files.readString(root.resolve("docs/achievements/presentation.json"))).getAsJsonObject();
        java.util.Map<Integer, String> descriptions = new java.util.HashMap<>();
        presentation.getAsJsonArray("entries").forEach(entry -> descriptions.put(entry.getAsJsonObject().get("id").getAsInt(),
                entry.getAsJsonObject().get("description").getAsString()));
        check(descriptions.keySet().equals(IMPLEMENTED), "Every implemented ID has a reviewed final description");
        for (var entry : catalog.getAsJsonArray("achievements")) {
            var approved = entry.getAsJsonObject();
            int id = approved.get("id").getAsInt();
            if (!IMPLEMENTED.contains(id)) continue;
            var implemented = resource("data/essence_ascendance/advancement/achievements/a%03d.json".formatted(id));
            var implementedDisplay = implemented.getAsJsonObject("display");
            check(translations.get(implementedDisplay.getAsJsonObject("title").get("translate").getAsString())
                    .getAsString().equals(approved.get("name").getAsString()), "Implemented title exactly matches Pool: " + id);
            String expectedDescription = descriptions.get(id);
            check(translations.get(implementedDisplay.getAsJsonObject("description").get("translate").getAsString())
                    .getAsString().equals(expectedDescription), "Implemented action matches approved condition/explicit clarification: " + id);
            check(!implemented.has("rewards"), "No gameplay reward or progression gate: " + id);
            var criterion = implemented.getAsJsonObject("criteria");
            String expectedCriterion = id == 22 ? "dissolved" : "achieved";
            check(criterion.size() == 1 && criterion.getAsJsonObject(expectedCriterion).get("trigger").getAsString()
                    .equals("minecraft:impossible"), "Only committed server award criterion: " + id);
            check(implemented.getAsJsonArray("requirements").toString().equals("[[\"" + expectedCriterion + "\"]]"),
                    "Stable sole completion criterion: " + id);
            check(implementedDisplay.getAsJsonObject("icon").get("id").getAsString()
                    .matches("(?:essence_ascendance|minecraft):[a-z0-9_]+"), "Existing item icon identity: " + id);
        }
        var display = advancement.getAsJsonObject("display");
        check(translations.get(display.getAsJsonObject("title").get("translate").getAsString()).getAsString().equals(selected.get("name").getAsString()), "Translated title exactly matches Pool");
        check(translations.get(display.getAsJsonObject("description").get("translate").getAsString()).getAsString().equals(selected.get("action").getAsString()), "Translated action exactly matches Pool");
        check(display.getAsJsonObject("icon").get("id").getAsString().equals("essence_ascendance:essence_crucible"), "Existing Crucible ItemStack icon");
        check(!advancement.has("rewards"), "No invented gameplay reward");
        check(advancement.getAsJsonObject("criteria").getAsJsonObject("dissolved").get("trigger").getAsString().equals("minecraft:impossible"), "Only authoritative server award path");
        var legacy = resource("data/essence_ascendance/advancement/water_skiing.json");
        check(legacy.getAsJsonObject("criteria").has("travel"), "Legacy save criterion preserved");
        check(!legacy.has("rewards"), "Legacy cannot grant selected successor");
        try (var paths = Files.list(root.resolve("common/src/main/resources/data/essence_ascendance/advancement/achievements"))) {
            Set<String> registered = new HashSet<>();
            paths.filter(path -> path.toString().endsWith(".json")).forEach(path -> registered.add(path.getFileName().toString()));
            Set<String> expected = new HashSet<>();
            IMPLEMENTED.forEach(id -> expected.add("a%03d.json".formatted(id)));
            check(registered.equals(expected), "Exactly the 69 completed phase-1 through phase-7 achievements are registered");
        }
    }

    private static JsonObject resource(String path) throws Exception {
        try (var stream = AchievementFoundationTest.class.getClassLoader().getResourceAsStream(path)) {
            check(stream != null, "Processed target resource exists: " + path);
            return JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static void rejects(Runnable action) {
        try { action.run(); throw new AssertionError("Invalid outcome accepted"); }
        catch (IllegalArgumentException | NullPointerException expected) { checks++; }
    }
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
}
