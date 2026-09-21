package com.mistaboom.essence_ascendance.verification;

import com.google.gson.JsonParser;
import com.mistaboom.essence_ascendance.config.SkillEffectBalanceSettings;
import com.mistaboom.essence_ascendance.essence.EssenceRegistry;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import com.mistaboom.essence_ascendance.skill.Skills;
import com.mistaboom.essence_ascendance.skill.SkillRegistry;
import com.mistaboom.essence_ascendance.skill.tooltip.SkillTooltipRegistry;
import com.mistaboom.essence_ascendance.stat.EssenceStats;
import com.mistaboom.essence_ascendance.stat.EssenceStatRegistry;
import com.mistaboom.essence_ascendance.stat.StatCategory;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;

/** Catalog-wide baseline. Future HUD definitions and bonus prose are planned, not falsely asserted here. */
public final class RegistryIntegrityTest {
    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        EssenceTypes.init(); EssenceStats.init(); AscendanceTiers.init(); Skills.init();
        Set<String> expected = Set.of("offense", "defense", "mobility", "vitality", "gathering", "utility");
        var essences = EssenceRegistry.values().stream().map(e -> e.id()).collect(Collectors.toSet());
        check(essences.equals(expected.stream().map(s -> ResourceLocation.fromNamespaceAndPath("essence_ascendance", s))
                .collect(Collectors.toSet())), "Exactly the six intended Essences must be registered");
        var ids = SkillRegistry.values().stream().map(s -> s.id()).collect(Collectors.toSet());
        check(SkillRegistry.values().size() == 90 && ids.size() == 90, "Exactly 90 unique registered skills");
        check(com.mistaboom.essence_ascendance.skill.effect.SkillEffectRegistry.implementedIds().equals(ids),
                "Every registered skill must retain its effect handler, with no orphan handlers");
        for (var essence : essences) {
            check(SkillRegistry.values().stream().filter(s -> s.essenceId().equals(essence)).count() == 15,
                    "Expected 15 skills in " + essence);
        }
        check(EssenceStatRegistry.size() == 40, "Expected 40 registered bonuses in the current catalog");
        check(SkillRegistry.choiceGroups().size() == 19, "Preserve all intentional choice groups");
        check(SkillRegistry.choiceGroups().stream().mapToInt(g -> g.memberIds().size() * (g.memberIds().size()-1) / 2).sum() == 31,
                "Preserve all 31 unordered exclusion pairs");
        check(SkillRegistry.values().stream().filter(s -> s.isReplacement()).count() == 3, "Preserve replacement edges");
        for (var skill : SkillRegistry.values()) {
            var requirements = skill.progressionRequirements();
            check(requirements.home().equalsIgnoreCase(skill.essenceId().getPath()), "Approved skill home: " + skill.id());
            check(!requirements.firstStateFloor().isBlank() && !requirements.additionalImprovementFloor().isBlank(), "Missing outcome floor");
            check(!requirements.bonusCompatibility().isBlank(), "Missing zero-bonus contract");
        }
        for (var stat : EssenceStatRegistry.values()) {
            var requirements = com.mistaboom.essence_ascendance.skill.ProgressionRequirements.bonus(stat.id());
            check(requirements.home().equalsIgnoreCase(stat.essenceType().id().getPath()), "Approved bonus home");
            check(requirements.meetsFirstFloor(requirements.firstStateFloor() * 2), "First floor must not cap stronger benefits");
            check(requirements.meetsImprovementFloor(requirements.tierImprovementFloor() * 2), "Improvement floor must not cap stronger benefits");
            check(com.mistaboom.essence_ascendance.balance.runtime.BonusSemantics.require(stat).neutralMultiplier() == 1, "Neutral multiplier");
            check(essences.contains(stat.essenceType().id()), "Bonus has an unknown Essence: " + stat.id());
            check(stat.category().name().equalsIgnoreCase(stat.essenceType().id().getPath()),
                    "Bonus category/Essence mismatch: " + stat.id());
            check(EssenceStatRegistry.get(stat.id()).orElseThrow().equals(stat), "Bonus lookup mismatch: " + stat.id());
        }
        for (var category : StatCategory.values()) check(EssenceStatRegistry.values().stream().anyMatch(s -> s.category() == category),
                "Missing bonus category: " + category);
        check(SkillTooltipRegistry.supportedIds().equals(ids), "Tooltip definitions must match all 90 skills exactly");
        var settings = SkillEffectBalanceSettings.defaults();
        try (var stream = RegistryIntegrityTest.class.getResourceAsStream("/assets/essence_ascendance/lang/en_us.json")) {
            check(stream != null, "Missing English localization resource");
            var language = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var id : ids) {
                check(!SkillTooltipRegistry.lines(id).isEmpty(), "Empty tooltip: " + id);
                for (var line : SkillTooltipRegistry.lines(id)) {
                    check(language.has(line.key()), "Missing tooltip translation: " + line.key());
                    String template = language.get(line.key()).getAsString();
                    var tokens = java.util.regex.Pattern.compile("%(?:(\\d+)\\$)?([A-Za-z%]|$)").matcher(template);
                    int sequential = 0, required = 0;
                    while (tokens.find()) if (!tokens.group().equals("%%")) {
                        check(tokens.group(2).equals("s"), "Unsupported tooltip format token: " + line.key());
                        int index = tokens.group(1) == null ? ++sequential : Integer.parseInt(tokens.group(1));
                        check(index > 0, "Invalid tooltip argument index: " + line.key());
                        required = Math.max(required, index);
                    }
                    check(required == line.values().size(), "Tooltip argument count mismatch: " + line.key());
                    for (String value : line.arguments(settings)) check(!value.isBlank() && !value.contains("NaN") && !value.contains("Infinity"),
                            "Invalid resolved tooltip argument: " + line.key());
                }
            }
        }
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out))
                .println("RegistryIntegrityTest: 90 skills, six groups of 15, 40 bonuses, exact tooltip coverage and tokens PASS");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
