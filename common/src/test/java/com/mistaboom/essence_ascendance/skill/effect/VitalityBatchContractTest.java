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

/** Reviewed Vitality rows 5–8 and the real committed/projected evaluator; no world required. */
public final class VitalityBatchContractTest {
    private static int checks;
    private static final List<ResourceLocation> RECOVERY = List.of(SkillIds.RISING_RECOVERY, SkillIds.LIFE_STEAL);
    private static final List<ResourceLocation> SUSTENANCE = List.of(SkillIds.FEAST_REFLEX, SkillIds.INNER_SUSTENANCE);
    private static final Map<ResourceLocation, String> DESCRIPTIONS = Map.of(
            SkillIds.RISING_RECOVERY, "Natural regeneration accelerates continuously as health falls, becoming strongest below half health.",
            SkillIds.LIFE_STEAL, "Direct weapon damage restores health, with consecutive hits on the same target increasing the healing until the attack chain breaks.",
            SkillIds.FEAST_REFLEX, "Food and drinks are consumed much faster; when the food bar is below full and health drops, suitable hotbar food is consumed automatically.",
            SkillIds.INNER_SUSTENANCE, "Hunger slowly restores outside combat; at full saturation, passive hunger drain stops, sleep becomes optional, and phantoms ignore the player.");

    public static void main(String[] args) throws Exception {
        Thread.currentThread().setUncaughtExceptionHandler((thread, failure) -> failure.printStackTrace(
                new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.err))));
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        EssenceTypes.init(); AscendanceTiers.init(); EssenceStats.init();
        MilestoneProviders.init(); Milestones.init(); Skills.init();
        catalog(); choicesAndAuthority();
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out))
                .println("VitalityBatchContractTest: " + checks + " checks passed");
    }

    private static void catalog() throws Exception {
        check(SkillRegistry.size() == 90 && SkillEffectRegistry.implementedIds().size() == 34,
                "The complete batch implements exactly thirty-four of ninety effects");
        check(SkillRegistry.values(EssenceTypes.VITALITY.id()).stream()
                .filter(skill -> SkillEffectRegistry.isImplemented(skill.id())).map(SkillDefinition::id)
                .collect(java.util.stream.Collectors.toSet()).equals(DESCRIPTIONS.keySet()),
                "Only the four requested Vitality effects enter the runtime");
        check(Set.copyOf(SkillRegistry.choiceGroup(SkillGroups.VITALITY_RECOVERY).orElseThrow().memberIds()).equals(Set.copyOf(RECOVERY)),
                "Reviewed recovery exclusions remain exact");
        check(Set.copyOf(SkillRegistry.choiceGroup(SkillGroups.VITALITY_SUSTENANCE).orElseThrow().memberIds()).equals(Set.copyOf(SUSTENANCE)),
                "Reviewed sustenance exclusions remain exact");
        try (var stream = Objects.requireNonNull(VitalityBatchContractTest.class.getResourceAsStream(
                "/assets/essence_ascendance/lang/en_us.json"));
             var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            var language = JsonParser.parseReader(reader).getAsJsonObject();
            for (var entry : DESCRIPTIONS.entrySet()) {
                var skill = SkillRegistry.require(entry.getKey());
                check(language.get(skill.descriptionTranslationKey()).getAsString().equals(entry.getValue()),
                        "Reviewed catalog description preserved: " + skill.id());
                check(skill.essenceId().equals(EssenceTypes.VITALITY.id()) && skill.prerequisites().isEmpty()
                        && skill.replacementTargetId().isEmpty(), "Category and prerequisite relationships preserved");
                check(skill.requiredTierId().equals(RECOVERY.contains(skill.id())
                        ? AscendanceTiers.DORMANT.id() : AscendanceTiers.AWAKENED.id()), "Registered tier preserved");
                check(skill.maximumRank() == 1 && skill.rankPolicy().projectionRanks() == 5,
                        "Future five-rank balance headroom never becomes a purchase");
            }
        }
        for (var skill : SkillRegistry.values()) check(skill.maximumRank() == 1, "Every existing skill still has one purchasable rank");
    }

    private static void choicesAndAuthority() {
        Map<ResourceLocation, Integer> owned = new HashMap<>();
        DESCRIPTIONS.keySet().forEach(id -> owned.put(id, 1));
        for (var recovery : RECOVERY) for (var sustenance : SUSTENANCE) {
            var selected = new HashMap<>(Map.of(SkillGroups.VITALITY_RECOVERY, recovery, SkillGroups.VITALITY_SUSTENANCE, sustenance));
            var active = evaluate(owned, selected, AscendanceTiers.AWAKENED.id());
            for (var candidate : DESCRIPTIONS.keySet()) check(active.get(candidate).effective()
                    == (candidate.equals(recovery) || candidate.equals(sustenance)), "Exactly one branch in each independent group");
            var dormant = SkillEvaluationContext.committed(AscendanceTiers.DORMANT.id(), Map.of(), selected, Set.of(), Set.of(), Map.of());
            check(SkillStateEvaluator.evaluatePurchaseEligibility(SkillRegistry.require(recovery), dormant).tierSatisfied()
                    && !SkillStateEvaluator.evaluatePurchaseEligibility(SkillRegistry.require(sustenance), dormant).tierSatisfied(),
                    "Dormant unlocks recovery purchases while sustenance retains its Awakened purchase gate");
            var latent = SkillEvaluationContext.committed(AscendanceTiers.LATENT.id(), Map.of(), selected, Set.of(), Set.of(), Map.of());
            check(DESCRIPTIONS.keySet().stream().noneMatch(id ->
                    SkillStateEvaluator.evaluatePurchaseEligibility(SkillRegistry.require(id), latent).tierSatisfied()),
                    "Latent cannot purchase any skill in this batch");
            var unowned = evaluate(Map.of(), selected, AscendanceTiers.TRANSCENDENT.id());
            check(DESCRIPTIONS.keySet().stream().noneMatch(id -> unowned.get(id).effective()), "Selection never grants ownership");
            var pendingPurchase = SkillStateEvaluator.evaluateAll(new SkillEvaluationContext(AscendanceTiers.AWAKENED.id(),
                    Map.of(), owned, Map.of(), selected, Set.of(), Set.of(), Map.of(), Map.of()));
            check(DESCRIPTIONS.keySet().stream().noneMatch(id -> pendingPurchase.get(id).effective())
                    && pendingPurchase.get(recovery).projectedEffective() && pendingPurchase.get(sustenance).projectedEffective(),
                    "A purchasable Nexus draft displays a projection without gameplay authority");
            var opposite = Map.of(SkillGroups.VITALITY_RECOVERY, RECOVERY.get(1 - RECOVERY.indexOf(recovery)),
                    SkillGroups.VITALITY_SUSTENANCE, SUSTENANCE.get(1 - SUSTENANCE.indexOf(sustenance)));
            var pendingSwitch = SkillStateEvaluator.evaluateAll(new SkillEvaluationContext(AscendanceTiers.AWAKENED.id(),
                    owned, owned, selected, opposite, Set.of(), Set.of(), Map.of(), Map.of()));
            check(pendingSwitch.get(recovery).effective() && !pendingSwitch.get(recovery).projectedEffective()
                    && pendingSwitch.get(sustenance).effective() && !pendingSwitch.get(sustenance).projectedEffective(),
                    "A staged branch switch cannot remove or grant committed gameplay");
            var switched = evaluate(owned, opposite, AscendanceTiers.AWAKENED.id());
            check(!switched.get(recovery).effective() && !switched.get(sustenance).effective()
                    && opposite.values().stream().allMatch(id -> switched.get(id).effective()), "Committed switch removes both former branches immediately");
            var receiptsRemoved = new HashMap<>(owned);
            receiptsRemoved.remove(recovery); receiptsRemoved.remove(sustenance);
            var removed = evaluate(receiptsRemoved, selected, AscendanceTiers.AWAKENED.id());
            check(DESCRIPTIONS.keySet().stream().noneMatch(id -> removed.get(id).effective()), "Removing receipts disables stale selections without enabling the other branch");
            selected.remove(SkillGroups.VITALITY_RECOVERY);
            var recoveryOff = evaluate(owned, selected, AscendanceTiers.AWAKENED.id());
            check(RECOVERY.stream().noneMatch(id -> recoveryOff.get(id).effective()) && recoveryOff.get(sustenance).effective(),
                    "Recovery opt-out preserves sustenance selection");
            selected.put(SkillGroups.VITALITY_RECOVERY, recovery); selected.remove(SkillGroups.VITALITY_SUSTENANCE);
            var sustenanceOff = evaluate(owned, selected, AscendanceTiers.AWAKENED.id());
            check(SUSTENANCE.stream().noneMatch(id -> sustenanceOff.get(id).effective()) && sustenanceOff.get(recovery).effective(),
                    "Sustenance opt-out preserves recovery selection");
        }
    }

    private static Map<ResourceLocation, SkillEvaluationResult> evaluate(Map<ResourceLocation, Integer> owned,
            Map<ResourceLocation, ResourceLocation> selected, ResourceLocation tier) {
        return SkillStateEvaluator.evaluateAll(SkillEvaluationContext.committed(tier, owned, selected, Set.of(), Set.of(), Map.of()));
    }
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
