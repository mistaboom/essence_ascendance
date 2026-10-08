package com.mistaboom.essence_ascendance.balance.engine;

import com.mistaboom.essence_ascendance.balance.runtime.*;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import com.mistaboom.essence_ascendance.progression.*;
import com.mistaboom.essence_ascendance.skill.*;
import com.mistaboom.essence_ascendance.skill.balance.SkillFunctionalScope;
import com.mistaboom.essence_ascendance.stat.EssenceStats;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.*;
import java.util.*;

public final class NativeConsumablesTest {
    private static int checks;
    private static final ConfigurationAccess.Proof ACCESS = new ConfigurationAccess.Proof(
            new CompetitiveCapabilities.Placement(ProgressionBand.ENTRY, true, .9, List.of()), List.of("fixture supply"), List.of());
    public static void main(String[] args) {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        EssenceTypes.init(); EssenceStats.init(); AscendanceTiers.init(); MilestoneProviders.init(); Milestones.init(); Skills.init();
        brewingBills(); servingEffects(); naturalRegeneration();
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out)).println("NativeConsumablesTest: " + checks + " checks PASS");
    }
    private static void brewingBills() {
        var mixes = List.of(new NativeBrewing.Mix("minecraft:water", "test:base", List.of("minecraft:nether_wart")),
                new NativeBrewing.Mix("test:base", "test:strength", List.of("minecraft:blaze_powder")),
                new NativeBrewing.Mix("test:strength", "test:base", List.of("minecraft:sugar")));
        var proof = NativeBrewing.solve("test:strength", mixes, bill -> {
            var counts = new TreeMap<String, Integer>(); bill.forEach(s -> counts.put(s.get("id").getAsString(), s.get("count").getAsInt()));
            check(counts.equals(Map.of("minecraft:brewing_stand", 1, "minecraft:glass_bottle", 1,
                    "minecraft:blaze_powder", 2, "minecraft:nether_wart", 1)), "Fuel and reagent must share a counted bill; reserve station/bottle once");
            return new ConfigurationAccess.Proof(new CompetitiveCapabilities.Placement(ProgressionBand.LATE, true, .7, List.of()), List.of("late ingredients"), List.of());
        }, ACCESS);
        check(proof.placement().reachable() && proof.placement().stage() == ProgressionBand.LATE, "Brewing ignored actual ingredient access tier");
        check(!NativeBrewing.solve("test:missing", mixes, bill -> { throw new AssertionError("Cycle proved access"); }, ACCESS).placement().reachable(), "Disconnected brew accepted");
        var absent = new ConfigurationAccess.Proof(new CompetitiveCapabilities.Placement(ProgressionBand.APEX, false, 0, List.of()), List.of(), List.of("absent water"));
        check(!NativeBrewing.solve("test:strength", mixes, bill -> { throw new AssertionError("Unproven water admitted"); }, absent).placement().reachable(), "Missing water setup ignored");
        check(!NativeBrewing.solve("test:strength", mixes, bill -> absent, ACCESS).placement().reachable(), "Missing stand/fuel/ingredients ignored");
        var many = new ArrayList<NativeBrewing.Mix>(); String previous = "minecraft:water";
        for (int i = 0; i < 21; i++) { String next = "test:step" + i; many.add(new NativeBrewing.Mix(previous, next, List.of("minecraft:sugar"))); previous = next; }
        check(NativeBrewing.solve(previous, many, bill -> {
            check(bill.stream().anyMatch(s -> s.get("id").getAsString().equals("minecraft:blaze_powder") && s.get("count").getAsInt() == 2), "Whole fuel charges rounded down");
            check(bill.stream().anyMatch(s -> s.get("id").getAsString().equals("minecraft:sugar") && s.get("count").getAsInt() == 21), "Repeated ingredients were collapsed");
            return ACCESS;
        }, ACCESS).placement().reachable(), "Bounded long brew chain rejected");
    }
    private static CapabilitySink read(ItemStack stack) {
        var sink = new CapabilitySink(); NativeConsumables.readStack(stack, "test:serving", "fixture", ACCESS.placement(), sink); return sink;
    }
    private static void servingEffects() {
        var food = read(new ItemStack(Items.BREAD));
        check(food.evidence().isEmpty() && !food.definitions().isEmpty(), "Food nutrition fabricated unconditional healing");
        var apple = read(new ItemStack(Items.GOLDEN_APPLE));
        check(apple.evidence().stream().flatMap(f -> f.measurements().stream()).anyMatch(m -> m.axis() == CapabilityAxis.REGENERATION), "Food's real regeneration effect was missed");
        var heal = read(PotionContents.createItemStack(Items.POTION, Potions.STRONG_HEALING));
        var healing = heal.evidence().stream().flatMap(f -> f.measurements().stream()).filter(m -> m.unit().equals("health_points")).findFirst().orElseThrow();
        check(healing.magnitude() == 8 && healing.operation().renewal() == CapabilityEvidence.Renewal.FINITE
                && healing.operation().uptimeBound() == null, "Instant heal became continuous or renewable healing");
        var fire = read(PotionContents.createItemStack(Items.POTION, Potions.FIRE_RESISTANCE));
        var resistance = fire.evidence().getFirst().measurements().getFirst();
        check(SkillFunctionalScope.compatible(SkillIds.LAVABORN, resistance)
                && !SkillFunctionalScope.compatible(SkillIds.PURE_STATE, resistance), "Fire protection became general status immunity");
        var jump = read(PotionContents.createItemStack(Items.POTION, Potions.LEAPING)).evidence().getFirst().measurements().getFirst();
        check(SkillFunctionalScope.compatible(SkillIds.CHARGED_JUMP, jump)
                && !SkillFunctionalScope.compatible(SkillIds.DOUBLE_JUMP, jump), "Ground jump effect proved an air jump");
        var slow = read(PotionContents.createItemStack(Items.POTION, Potions.SLOW_FALLING));
        check(slow.evidence().stream().flatMap(f -> f.measurements().stream()).noneMatch(m -> m.axis() == CapabilityAxis.FLIGHT), "Slow falling became flight");
        check(read(PotionContents.createItemStack(Items.SPLASH_POTION, Potions.HEALING)).evidence().isEmpty(), "Splash attenuation ignored");
        var expired = new ItemStack(Items.POTION); expired.set(DataComponents.POTION_CONTENTS, PotionContents.EMPTY.withEffectAdded(new MobEffectInstance(MobEffects.REGENERATION, 2)));
        check(read(expired).evidence().isEmpty(), "Regeneration shorter than one native pulse fabricated healing");
        var blocked = new CapabilitySink(); NativeConsumables.readStack(PotionContents.createItemStack(Items.POTION, Potions.HEALING),
                "test:unavailable", "fixture", new CompetitiveCapabilities.Placement(ProgressionBand.ENTRY, false, 0, List.of()), blocked);
        check(CompetitiveCapabilities.report(blocked, "WINSORIZE").getAsJsonArray("frontiers").isEmpty(), "Unobtainable potion admitted to competition");
        var baseline = new PackEvidence(Map.of(), List.of(), List.of(), Map.of(), List.of(), List.of(), Map.of());
        var empty = new AdaptiveCompetitionCalibration(baseline, CompetitiveCapabilities.report(new CapabilitySink(), "WINSORIZE"));
        var withFire = new AdaptiveCompetitionCalibration(baseline, CompetitiveCapabilities.report(fire, "WINSORIZE"));
        check(empty.skillTiers().get(SkillIds.PURE_STATE).equals(withFire.skillTiers().get(SkillIds.PURE_STATE)), "Potion changed an incompatible skill tier");
        var strong = new ItemStack(Items.POTION); strong.set(DataComponents.POTION_CONTENTS,
                PotionContents.EMPTY.withEffectAdded(new MobEffectInstance(MobEffects.REGENERATION, 200, 4)));
        var policy = new AdaptiveCompetitionCalibration(baseline, CompetitiveCapabilities.report(read(strong), "WINSORIZE"));
        check(policy.factor("skill.rising_recovery", ProgressionBand.ENTRY) > empty.factor("skill.rising_recovery", ProgressionBand.ENTRY), "Supported regeneration did not affect matching strength pressure");
    }
    private static void naturalRegeneration() {
        var bread = new ItemStack(Items.BREAD);
        var meal = NativeConsumables.meal(bread, true).orElseThrow();
        check(meal.servings() == 4 && meal.foodLevel() == 20 && meal.saturation() == 20,
                "Counted meal must use native food/saturation clamping from zero, not initial player reserves");
        check(meal.pulseTicks() == 10 && meal.pulseHealth() == 1 && meal.pulseExhaustion() == 6,
                "Saturated natural regeneration pulse differs from native FoodData");
        check(NativeConsumables.meal(bread, false).isEmpty(), "Disabled natural regeneration admitted");
        check(NativeConsumables.meal(new ItemStack(Items.ROTTEN_FLESH), true).isEmpty(), "Food with adverse effects treated as ordinary safe nutrition");
        check(NativeConsumables.meal(new ItemStack(Items.POTION), true).isEmpty(), "Nonfood supplies a meal");
        var dry = new ItemStack(Items.BREAD); dry.set(DataComponents.FOOD,
                new net.minecraft.world.food.FoodProperties.Builder().nutrition(1).saturationModifier(0).build());
        var slow = NativeConsumables.meal(dry, true).orElseThrow();
        check(slow.servings() == 20 && slow.saturation() == 0 && slow.pulseTicks() == 80 && slow.pulseHealth() == 1,
                "Zero-saturation meal must use the slow native hunger pulse");
        var sink = new CapabilitySink();
        NativeConsumables.naturalRegeneration(bread, "minecraft:bread", true, bill -> {
            check(bill.size() == 1 && bill.getFirst().get("count").getAsInt() == 4, "Single serving incorrectly certifies complete meal");
            return ACCESS;
        }, sink);
        var pulse = sink.evidence().getFirst().measurements().getFirst();
        check(pulse.magnitude() == 2 && pulse.operation().renewal() == CapabilityEvidence.Renewal.FINITE
                && pulse.operation().uptimeBound() == null, "First native pulse became unlimited or sustained regeneration");
        var missing = new CapabilitySink();
        NativeConsumables.naturalRegeneration(bread, "minecraft:bread", true, bill -> new ConfigurationAccess.Proof(
                new CompetitiveCapabilities.Placement(ProgressionBand.APEX, false, 0, List.of()), List.of(), List.of("insufficient food")), missing);
        check(CompetitiveCapabilities.report(missing, "WINSORIZE").getAsJsonArray("frontiers").isEmpty(), "Unproven meal supplied competition");
        NativeConsumables.naturalRegeneration(bread, "minecraft:bread", false, bill -> { throw new AssertionError("Disabled rule queried supply"); }, new CapabilitySink());
    }
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
