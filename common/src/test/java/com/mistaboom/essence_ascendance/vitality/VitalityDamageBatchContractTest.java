package com.mistaboom.essence_ascendance.vitality;

import com.google.gson.JsonParser;
import com.mistaboom.essence_ascendance.config.SkillEffectBalanceSettings;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import com.mistaboom.essence_ascendance.progression.MilestoneProviders;
import com.mistaboom.essence_ascendance.progression.Milestones;
import com.mistaboom.essence_ascendance.skill.*;
import com.mistaboom.essence_ascendance.skill.balance.SkillRankEffectScaling;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRegistry;
import com.mistaboom.essence_ascendance.stat.EssenceStats;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import net.minecraft.resources.ResourceLocation;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Opt-in real mapped registry/evaluator/NBT/resource contracts; does not simulate a running server. */
public final class VitalityDamageBatchContractTest {
    private static int checks;
    private static final Map<ResourceLocation, ResourceLocation> CHILDREN = Map.of(
            SkillIds.HUNGER_WARD, SkillIds.METABOLIC_CONVERSION,
            SkillIds.STAGGERED_PAIN, SkillIds.PAIN_PURGE,
            SkillIds.DAMAGE_CEILING, SkillIds.ADRENALINE);
    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        EssenceTypes.init(); AscendanceTiers.init(); EssenceStats.init();
        MilestoneProviders.init(); Milestones.init(); Skills.init();
        Set<ResourceLocation> all = new HashSet<>(CHILDREN.keySet()); all.addAll(CHILDREN.values());
        check(SkillRegistry.size() == 90, "No catalog additions or deleted skills");
        check(SkillEffectRegistry.implementedIds().containsAll(all), "All six catalog effects registered");
        check(Set.copyOf(SkillRegistry.choiceGroup(SkillGroups.VITALITY_DAMAGE_HANDLING).orElseThrow().memberIds())
                .equals(CHILDREN.keySet()), "The three damage branches remain exclusive");
        Map<ResourceLocation, Integer> owned = new HashMap<>(); all.forEach(id -> owned.put(id, 1));
        for (var entry : CHILDREN.entrySet()) {
            var parent = SkillRegistry.require(entry.getKey()); var child = SkillRegistry.require(entry.getValue());
            check(parent.requiredTierId().equals(AscendanceTiers.RESONANT.id())
                    && child.requiredTierId().equals(AscendanceTiers.ASCENDANT.id()), "Reviewed unlock tiers");
            check(child.prerequisites().equals(List.of(parent.id())), "Reviewed parent/child dependency");
            var selected = Map.of(SkillGroups.VITALITY_DAMAGE_HANDLING, parent.id());
            var committed = SkillStateEvaluator.evaluateAll(SkillEvaluationContext.committed(
                    AscendanceTiers.ASCENDANT.id(), owned, selected, Set.of(), Set.of(), Map.of()));
            for (var candidate : all) check(committed.get(candidate).effective()
                    == (candidate.equals(parent.id()) || candidate.equals(child.id())), "Only the committed branch and child are effective");
            var staged = SkillStateEvaluator.evaluateAll(new SkillEvaluationContext(AscendanceTiers.ASCENDANT.id(),
                    Map.of(), owned, Map.of(), selected, Set.of(), Set.of(), Map.of(), Map.of()));
            for (var candidate : all) check(!staged.get(candidate).effective(), "Draft purchases never authorize gameplay");
            var refunded = new HashMap<>(owned); refunded.remove(parent.id());
            var removed = SkillStateEvaluator.evaluateAll(SkillEvaluationContext.committed(
                    AscendanceTiers.ASCENDANT.id(), refunded, selected, Set.of(), Set.of(), Map.of()));
            check(!removed.get(child.id()).effective(), "Refund disables the child, even with a stale selection");
        }
        for (var id : all) {
            var skill = SkillRegistry.require(id);
            check(skill.rankPolicy().maximumRank() == 0, "Catalog delegates purchasable rank count to the balance engine");
        }
        check(!SkillRankEffectScaling.supports(SkillIds.STAGGERED_PAIN), "Time shifting is not fictitious permanent mitigation/rank growth");
        var effects = SkillEffectBalanceSettings.defaults();
        check(SkillRankEffectScaling.apply(effects, Map.of(SkillIds.FRENZY, 2), (id, rank) -> 1.1).vitality().damage().equals(effects.vitality().damage()),
                "Typed damage parameters survive common rank reconstruction");
        var ranked = SkillRankEffectScaling.apply(effects,
                Map.of(SkillIds.DAMAGE_CEILING, 2, SkillIds.ADRENALINE, 2), (id, rank) -> 2);
        check(Math.abs(ranked.vitality().damage().damageCeiling().damageTakenFraction() - .6) < 1e-9,
                "Rank scales proportional bonus survivability, not a maximum-HP hit threshold");
        check(ranked.vitality().damage().adrenaline().triggerHealthLossFraction() == .25,
                "Rank scaling does not change the actual-loss trigger");
        check(Arrays.stream(com.mistaboom.essence_ascendance.config.VitalityDamageBalanceSettings.DamageCeiling.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName).collect(java.util.stream.Collectors.toSet())
                .equals(Set.of("damageTakenFraction", "combatTimeoutTicks")),
                "Only one generated fraction; no independently tunable Trauma ratio, bank or hit cap");
        persistence(); resources(all); tooltipValues(); hudPriority();
        System.out.println("VitalityDamageBatchContractTest: " + checks + " checks passed");
    }
    private static void persistence() {
        var ledger = new VitalityDamageLedger();
        var source = new VitalityDamageLedger.Source("minecraft:mob_attack", UUID.randomUUID());
        ledger.delayed.enqueue(source, 18, 200); ledger.delayed.advance(); ledger.delayed.purge(.25);
        ledger.traumaFraction = .2; ledger.traumaQuietTicks = 143; ledger.lastOnlineTick = 99;
        var copy = VitalityDamageLedger.load(ledger.save());
        check(copy.delayed.snapshot().equals(ledger.delayed.snapshot()), "NBT preserves unpaid amounts, source and remaining online ticks");
        check(copy.traumaFraction == .2 && copy.traumaQuietTicks == 143, "Trauma persists through reconnect");
        check(copy.lastOnlineTick == Long.MIN_VALUE, "Tick deduplication is session-local, not a cross-world timestamp");
        ledger.traumaFraction = .995;
        check(VitalityDamageLedger.load(ledger.save()).traumaFraction == .995,
                "Expanded health pools retain penalties above the retired arbitrary 99 percent ceiling");
        copy.clear();
        check(copy.delayed.isEmpty() && copy.traumaFraction == 0 && copy.traumaQuietTicks == 0, "Death clears obligations");
        check(VitalityDamageLedger.load(new net.minecraft.nbt.CompoundTag()).delayed.isEmpty(), "Pre-batch saves start without debt");
        var player = new com.mistaboom.essence_ascendance.data.PlayerEssenceData();
        player.setFractionalResourceCostCarry(SkillIds.HUNGER_WARD, .25);
        player.setFractionalResourceCostCarry(SkillIds.METABOLIC_CONVERSION, .75);
        var saved = com.mistaboom.essence_ascendance.data.PlayerEssenceData.load(player.save());
        check(saved.getFractionalResourceCostCarry(SkillIds.HUNGER_WARD) == .25
                && saved.getFractionalResourceCostCarry(SkillIds.METABOLIC_CONVERSION) == .75,
                "Both resource fractions use the existing persistent cost store");
    }
    private static void resources(Set<ResourceLocation> all) throws Exception {
        try (var input = Objects.requireNonNull(VitalityDamageBatchContractTest.class.getResourceAsStream(
                "/assets/essence_ascendance/lang/en_us.json")); var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            var language = JsonParser.parseReader(reader).getAsJsonObject();
            for (var id : all) {
                var skill = SkillRegistry.require(id);
                check(language.has(skill.nameTranslationKey()) && language.has(skill.descriptionTranslationKey()), "Names and descriptions localized");
            }
            for (String key : List.of("reserve", "food", "ward_rate", "owed", "payment", "debt_persists", "payment_time",
                    "trauma", "hit_limit", "trauma_capacity", "quiet_time", "surge", "movement", "attack", "knockback", "surge_time", "ready", "redirect", "cap_bank", "last_hit", "speed_bonuses", "trigger", "inactive", "next_cap", "max_health_cost", "lethal_save_trigger", "last_lethal_hit", "last_prevented", "max_hp_lost"))
                check(language.has("hud.essence_ascendance.vitality." + key), "HUD key exists: " + key);
            check(language.get(SkillRegistry.require(SkillIds.DAMAGE_CEILING).descriptionTranslationKey()).getAsString()
                    .contains("generated percentage"), "The replacement percentage behavior is disclosed in player-facing text");
        }
        try (var input = Objects.requireNonNull(VitalityDamageBatchContractTest.class.getResourceAsStream(
                "/data/essence_ascendance/tags/damage_type/quiet_feedback.json"));
             var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            var tag = JsonParser.parseReader(reader).getAsJsonObject();
            check(!tag.get("replace").getAsBoolean(), "quiet policy is an extensible damage-type tag");
            check(tag.getAsJsonArray("values").asList().stream().anyMatch(v -> v.getAsString().equals("essence_ascendance:delayed_pain")),
                    "delayed payments opt into quiet feedback");
        }
        for (String tag : List.of("bypasses_armor", "bypasses_cooldown", "bypasses_effects", "bypasses_enchantments",
                "bypasses_resistance", "bypasses_shield", "no_impact", "no_knockback", "no_anger")) {
            try (var input = Objects.requireNonNull(VitalityDamageBatchContractTest.class.getResourceAsStream(
                    "/data/minecraft/tags/damage_type/" + tag + ".json")); var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                var data = JsonParser.parseReader(reader).getAsJsonObject();
                check(!data.get("replace").getAsBoolean(), "Vanilla/other mod tags are preserved");
                check(data.getAsJsonArray("values").asList().stream().anyMatch(e -> e.getAsString().equals("essence_ascendance:delayed_pain")),
                        "Deferred native damage excludes already-paid mitigation: " + tag);
            }
        }
    }
    private static void tooltipValues() throws Exception {
        var implemented = SkillEffectRegistry.implementedIds();
        var supported = com.mistaboom.essence_ascendance.skill.tooltip.SkillTooltipRegistry.supportedIds();
        check(supported.equals(implemented), "Every implemented skill has a typed generated description, no fictional planned effects");
        var base = SkillEffectBalanceSettings.defaults();
        try (var input = Objects.requireNonNull(VitalityDamageBatchContractTest.class.getResourceAsStream(
                "/assets/essence_ascendance/lang/en_us.json")); var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            var language = JsonParser.parseReader(reader).getAsJsonObject();
            for (var id : implemented) {
                var descriptions = com.mistaboom.essence_ascendance.skill.tooltip.SkillTooltipRegistry.lines(id);
                check(!descriptions.isEmpty(), "Implemented skill has at least one resolved line");
                for (var line : descriptions) {
                    check(language.has(line.key()), "Resolved description key localized: " + line.key());
                    var arguments = line.arguments(base);
                    String template = language.get(line.key()).getAsString();
                    check(java.util.regex.Pattern.compile("(?<!%)%s").matcher(template.replace("%%", "")).results().count()
                            == arguments.size(), "Every numeric placeholder has one typed generated value");
                    String formatted = String.format(Locale.ROOT, template, arguments.toArray());
                    check(!formatted.contains("%s") && !formatted.contains("NaN") && !formatted.contains("Infinity"), "Resolved player text is complete");
                }
                if (SkillRankEffectScaling.supports(id)) {
                    var ranked = SkillRankEffectScaling.apply(base, Map.of(id, 2), (skill, rank) -> 1.1);
                    ranked.validate();
                    for (var line : descriptions) for (String value : line.arguments(ranked))
                        check(Double.isFinite(Double.parseDouble(value)), "Projected rank descriptions use valid generated values");
                }
            }
            check(language.get("hud.essence_ascendance.vitality.redirect").getAsString().equals("%s%%"), "Ward badge is just its percentage");
        }
        var ward = com.mistaboom.essence_ascendance.skill.tooltip.SkillTooltipRegistry.lines(SkillIds.HUNGER_WARD).getFirst();
        check(ward.arguments(base).equals(List.of("30")), "Hunger Ward first sentence exposes generated percent");
        var changed = SkillRankEffectScaling.apply(base, Map.of(SkillIds.HUNGER_WARD, 2), (id, rank) -> 1.5);
        check(!ward.arguments(base).equals(ward.arguments(changed)), "Tooltip is not a static copy of the reference number");
        var purge = com.mistaboom.essence_ascendance.skill.tooltip.SkillTooltipRegistry.lines(SkillIds.PAIN_PURGE).getFirst();
        check(purge.arguments(base).equals(List.of("100")), "Reference fixture mirrors one healing into one debt recovery");
    }
    private static void hudPriority() {
        var text = com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudEntry.Text.literal("");
        var ready = new com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudEntry(SkillIds.HUNGER_WARD, SkillIds.HUNGER_WARD,
                true, 0, text, text, List.of(), com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudEntry.Meter.progress(1));
        var longTimer = new com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudEntry(SkillIds.DAMAGE_CEILING, SkillIds.DAMAGE_CEILING,
                true, 0, text, text, List.of(), com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudEntry.Meter.timer("", 200));
        var shortTimer = new com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudEntry(SkillIds.ADRENALINE, SkillIds.ADRENALINE,
                true, 0, text, text, List.of(), com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudEntry.Meter.timer("", 100));
        check(com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudPriority.order(List.of(ready, longTimer, shortTimer), 1)
                .equals(List.of(shortTimer, longTimer, ready)), "Short active buffs precede resource/ready cards under HUD overflow");
    }
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
