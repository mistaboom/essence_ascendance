package com.mistaboom.essence_ascendance.skill.effect;

import com.google.gson.JsonParser;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import com.mistaboom.essence_ascendance.progression.MilestoneProviders;
import com.mistaboom.essence_ascendance.progression.Milestones;
import com.mistaboom.essence_ascendance.skill.*;
import com.mistaboom.essence_ascendance.stat.EssenceStats;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Authoritative workbook contracts and actual committed choice evaluation, without a world. */
public final class DefenseCompletionTest {
    private static int checks;
    private static final List<ResourceLocation> POSTURES = List.of(SkillIds.EVASIVE_CURRENT,
            SkillIds.BULWARK_STANCE, SkillIds.ADAPTIVE_GUARD);
    private static final List<ResourceLocation> STATUS = List.of(SkillIds.STATUS_MIRROR, SkillIds.PURE_STATE);

    public static void main(String[] args) throws Exception {
        Thread.currentThread().setUncaughtExceptionHandler((thread, failure) -> failure.printStackTrace(
                new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.err))));
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        EssenceTypes.init(); AscendanceTiers.init(); EssenceStats.init();
        MilestoneProviders.init(); Milestones.init(); Skills.init();
        catalog(); choices();
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out))
                .println("DefenseCompletionTest: " + checks + " checks passed");
    }

    private static void catalog() throws Exception {
        check(SkillRegistry.size() == 90 && SkillEffectRegistry.implementedIds().size() == 30,
                "Exactly thirty of ninety curated effects are implemented");
        for (var category : List.of(EssenceTypes.OFFENSE, EssenceTypes.DEFENSE))
            check(SkillRegistry.values(category.id()).stream().filter(s -> SkillEffectRegistry.isImplemented(s.id())).count() == 15,
                    "Completed category has fifteen effects: " + category.id());
        check(Set.copyOf(SkillRegistry.choiceGroup(SkillGroups.DEFENSE_POSTURE).orElseThrow().memberIds()).equals(Set.copyOf(POSTURES)),
                "Workbook posture exclusions");
        check(Set.copyOf(SkillRegistry.choiceGroup(SkillGroups.DEFENSE_STATUS).orElseThrow().memberIds()).equals(Set.copyOf(STATUS)),
                "Workbook status exclusions");
        var expected = Map.of(
                SkillIds.EVASIVE_CURRENT, List.of("Evasive Current", "Continuous intentional movement builds dodge chance; stopping or taking a hit drains the accumulated evasion."),
                SkillIds.BULWARK_STANCE, List.of("Bulwark Stance", "Standing still while facing incoming threats builds damage resistance and knockback immunity; moving or turning drains it."),
                SkillIds.ADAPTIVE_GUARD, List.of("Adaptive Guard", "Repeated hits of the same damage type become progressively less effective; taking a different type begins a new adaptation."),
                SkillIds.STATUS_MIRROR, List.of("Status Mirror", "The first harmful status effect received is immediately removed and copied to its source, followed by a cooldown."),
                SkillIds.PURE_STATE, List.of("Pure State", "The player cannot receive harmful status effects, sacrificing the ability to reflect them with Status Mirror."));
        try (var stream = Objects.requireNonNull(DefenseCompletionTest.class.getResourceAsStream(
                "/assets/essence_ascendance/lang/en_us.json"));
             var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            var language = JsonParser.parseReader(reader).getAsJsonObject();
            for (var entry : expected.entrySet()) {
                var definition = SkillRegistry.require(entry.getKey());
                check(language.get(definition.nameTranslationKey()).getAsString().equals(entry.getValue().get(0)), "Workbook name preserved");
                check(language.get(definition.descriptionTranslationKey()).getAsString().equals(entry.getValue().get(1)), "Workbook description preserved");
                check(definition.prerequisites().isEmpty() && definition.essenceId().equals(EssenceTypes.DEFENSE.id()), "Workbook category and relationships");
                check(definition.maximumRank() == 1 && definition.rankPolicy().projectionRanks() == 5,
                        "Single purchase and provisional five-rank analysis remain separate");
            }
        }
    }

    private static void choices() {
        Map<ResourceLocation, Integer> owned = new HashMap<>();
        POSTURES.forEach(id -> owned.put(id, 1)); STATUS.forEach(id -> owned.put(id, 1));
        for (var posture : POSTURES) for (var status : STATUS) {
            var selected = new HashMap<>(Map.of(SkillGroups.DEFENSE_POSTURE, posture, SkillGroups.DEFENSE_STATUS, status));
            var evaluated = evaluate(owned, selected, AscendanceTiers.TRANSCENDENT.id());
            for (var candidate : POSTURES) check(evaluated.get(candidate).effective() == candidate.equals(posture), "Exactly one selected posture");
            for (var candidate : STATUS) check(evaluated.get(candidate).effective() == candidate.equals(status), "Independent status selection");
            selected.remove(SkillGroups.DEFENSE_POSTURE);
            var postureOff = evaluate(owned, selected, AscendanceTiers.TRANSCENDENT.id());
            check(POSTURES.stream().noneMatch(id -> postureOff.get(id).effective()) && postureOff.get(status).effective(), "Posture opt-out leaves status choice effective");
            selected.put(SkillGroups.DEFENSE_POSTURE, posture);
            selected.remove(SkillGroups.DEFENSE_STATUS);
            var statusOff = evaluate(owned, selected, AscendanceTiers.TRANSCENDENT.id());
            check(STATUS.stream().noneMatch(id -> statusOff.get(id).effective()) && statusOff.get(posture).effective(), "Status opt-out leaves posture effective");
            selected.put(SkillGroups.DEFENSE_STATUS, status);
            var removedReceipt = new HashMap<>(owned);
            removedReceipt.remove(posture); removedReceipt.remove(status);
            var removed = evaluate(removedReceipt, selected, AscendanceTiers.TRANSCENDENT.id());
            check(!removed.get(posture).effective() && !removed.get(status).effective(), "Removed receipt immediately loses effectiveness");
            var missing = evaluate(Map.of(), selected, AscendanceTiers.TRANSCENDENT.id());
            check(owned.keySet().stream().noneMatch(id -> missing.get(id).effective()), "Selection cannot grant unowned effects");
        }
    }

    private static Map<ResourceLocation, SkillEvaluationResult> evaluate(Map<ResourceLocation, Integer> owned,
            Map<ResourceLocation, ResourceLocation> selected, ResourceLocation tier) {
        return SkillStateEvaluator.evaluateAll(SkillEvaluationContext.committed(tier, owned, selected, Set.of(), Set.of(), Map.of()));
    }
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
}
