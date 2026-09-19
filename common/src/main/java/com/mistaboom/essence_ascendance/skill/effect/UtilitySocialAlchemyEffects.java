package com.mistaboom.essence_ascendance.skill.effect;

import com.mistaboom.essence_ascendance.config.UtilityBalanceSettings;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudEntry.Text;
import com.mistaboom.essence_ascendance.utility.BondedCompanionService;
import com.mistaboom.essence_ascendance.utility.UtilityPotionService;
import com.mistaboom.essence_ascendance.utility.VillagePatronService;
import com.mistaboom.essence_ascendance.visual.AscendancePalette;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

import static com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudCards.compact;

/** Utility social and alchemy handlers backed by shared villager, companion, ally, and potion services. */
public final class UtilitySocialAlchemyEffects {
    private UtilitySocialAlchemyEffects() { }

    public static List<SkillEffectHandler> handlers() {
        return List.of(new VillagePatron(), new BondedCompanion(), new AlchemicalAmplification(), new PotionRelay());
    }

    private static UtilityBalanceSettings settings(SkillEffectRuntime.Context context) {
        return context.settings().utility();
    }

    private static Text text(String key, String... args) {
        return Text.translated("hud.essence_ascendance.utility." + key, args);
    }

    private static final class VillagePatron implements SkillEffectHudHandler {
        @Override public ResourceLocation id() { return SkillIds.VILLAGE_PATRON; }
        @Override public void tick(SkillEffectRuntime.Context context) { VillagePatronService.tick(context); }

        @Override public SkillEffectHudEntry hudEntry(SkillEffectRuntime.Context context) {
            var state = VillagePatronService.snapshot(context);
            var tuning = settings(context).villagePatron();
            List<Text> details = new ArrayList<>();
            details.add(text("patron_villagers", Integer.toString(state.nearbyVillagers())));
            if (state.refreshedOffers() > 0) details.add(text("patron_refreshed", Integer.toString(state.refreshedOffers())));
            else details.add(text("patron_exhausted", Integer.toString(state.exhaustedOffers())));
            details.add(text("patron_radius", compact(tuning.restockRadiusBlocks())));
            var meter = state.exhaustedOffers() > 0 && state.nextRestockAt() > context.now()
                    ? SkillEffectHudEntry.Meter.timer("hud.essence_ascendance.utility.patron_restock", state.nextRestockAt())
                    : SkillEffectHudEntry.Meter.none();
            return SkillEffectHudEntry.skill(id(), state.nearbyVillagers() > 0, AscendancePalette.UTILITY,
                    text("patron_discount", compact(tuning.discountFraction() * 100.0)), details, meter);
        }

        @Override public List<String> debugLines(SkillEffectRuntime.Context context) {
            var tuning = settings(context).villagePatron();
            var state = VillagePatronService.snapshot(context);
            return List.of("Discount=" + tuning.discountFraction() + "; restock radius=" + tuning.restockRadiusBlocks()
                            + "; restock interval=" + tuning.restockIntervalTicks() + " ticks",
                    "Nearby villagers=" + state.nearbyVillagers() + "; exhausted offers=" + state.exhaustedOffers()
                            + "; refreshed this tick=" + state.refreshedOffers() + "; vanilla demand still updates when the skill restores an exhausted offer.");
        }
    }

    private static final class BondedCompanion implements SkillEffectHudHandler {
        @Override public ResourceLocation id() { return SkillIds.BONDED_COMPANION; }

        @Override public SkillEffectHudEntry hudEntry(SkillEffectRuntime.Context context) {
            var state = BondedCompanionService.snapshot(context);
            var tuning = settings(context).bondedCompanion();
            List<Text> details = state == null ? List.of() : List.of(
                    Text.literal(state.name()),
                    text("bonded_stats", compact(tuning.statBonusFraction() * 100.0)),
                    state.teleportedThisTick() ? text("bonded_caught_up")
                            : text("bonded_catchup", compact(tuning.catchupDistanceBlocks())));
            return SkillEffectHudEntry.skill(id(), state != null, AscendancePalette.UTILITY,
                    text("bonded_bonus", compact(tuning.statBonusFraction() * 100.0)), details,
                    SkillEffectHudEntry.Meter.none());
        }

        @Override public List<String> debugLines(SkillEffectRuntime.Context context) {
            var tuning = settings(context).bondedCompanion();
            var state = BondedCompanionService.snapshot(context);
            return List.of("Bonded stat fraction=" + tuning.statBonusFraction() + "; catch-up threshold="
                            + tuning.catchupDistanceBlocks() + " blocks",
                    "Last loaded owned creature=" + (state == null ? "none" : state.name())
                            + "; transient attack-damage, movement-speed and max-health modifiers share one generated bond strength; health percentage is preserved when the modifier changes.");
        }
    }

    private static final class AlchemicalAmplification implements SkillEffectHudHandler {
        @Override public ResourceLocation id() { return SkillIds.ALCHEMICAL_AMPLIFICATION; }

        @Override public SkillEffectHudEntry hudEntry(SkillEffectRuntime.Context context) {
            var state = UtilityPotionService.amplificationSnapshot(context);
            var tuning = settings(context).alchemicalAmplification();
            if (state == null) return SkillEffectHudEntry.skill(id(), false, AscendancePalette.UTILITY,
                    text("alchemical_amplification_bonus", "0"), List.of(), SkillEffectHudEntry.Meter.none());
            double gainedSeconds = (state.appliedDuration() - state.originalDuration()) / 20.0;
            return SkillEffectHudCards.timed(id(), true, AscendancePalette.UTILITY,
                    text("alchemical_amplification_bonus", compact(gainedSeconds)),
                    List.of(Text.translated(state.effectKey()),
                            text("alchemical_amplification_cap", compact(tuning.maximumBonusFraction() * 100.0)),
                            text("alchemical_amplification_window", compact(tuning.diminishingWindowTicks() / 20.0))),
                    "hud.essence_ascendance.utility.potion_remaining", state.expiresAt());
        }

        @Override public List<String> debugLines(SkillEffectRuntime.Context context) {
            var tuning = settings(context).alchemicalAmplification();
            var state = UtilityPotionService.amplificationSnapshot(context);
            return List.of("Maximum timed-duration/instant-potency bonus=" + tuning.maximumBonusFraction()
                            + "; diminishing duration window=" + tuning.diminishingWindowTicks() + " ticks",
                    "Last compatible timed potion effect=" + (state == null ? "none" : state.effectKey() + " "
                            + state.originalDuration() + "->" + state.appliedDuration() + " ticks")
                            + "; drink, splash and lingering potion paths are supported; harmful and non-potion sources are untouched.");
        }
    }

    private static final class PotionRelay implements SkillEffectHudHandler {
        @Override public ResourceLocation id() { return SkillIds.POTION_RELAY; }

        @Override public SkillEffectHudEntry hudEntry(SkillEffectRuntime.Context context) {
            var state = UtilityPotionService.relaySnapshot(context);
            var tuning = settings(context).potionRelay();
            if (state == null) return SkillEffectHudEntry.skill(id(), false, AscendancePalette.UTILITY,
                    text("potion_relay_allies", "0"), List.of(), SkillEffectHudEntry.Meter.none());
            return SkillEffectHudCards.timed(id(), true, AscendancePalette.UTILITY,
                    text("potion_relay_allies", Integer.toString(state.targets())),
                    List.of(Text.translated(state.effectKey()),
                            text("potion_relay_duration", compact(tuning.durationFraction() * 100.0)),
                            text("potion_relay_radius", compact(tuning.radiusBlocks()))),
                    "hud.essence_ascendance.utility.potion_remaining", state.expiresAt());
        }

        @Override public List<String> debugLines(SkillEffectRuntime.Context context) {
            var tuning = settings(context).potionRelay();
            var state = UtilityPotionService.relaySnapshot(context);
            return List.of("Relay radius=" + tuning.radiusBlocks() + "; duration fraction=" + tuning.durationFraction()
                            + "; target budget=" + tuning.maximumTargets(),
                    "Last relay=" + (state == null ? "none" : state.effectKey() + " to " + state.targets()
                            + " allies for " + state.durationTicks() + " ticks")
                            + "; deterministic nearest allied players/owned allied creatures are selected and relay applications cannot chain.");
        }
    }
}
