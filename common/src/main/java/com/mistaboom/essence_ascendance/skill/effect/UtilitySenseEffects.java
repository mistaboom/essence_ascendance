package com.mistaboom.essence_ascendance.skill.effect;

import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudEntry.Text;
import com.mistaboom.essence_ascendance.utility.UtilitySenseService;
import com.mistaboom.essence_ascendance.visual.AscendancePalette;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Sense Focus handlers backed by the shared Utility sensing service. */
public final class UtilitySenseEffects {
    private UtilitySenseEffects() { }

    public static List<SkillEffectHandler> handlers() {
        return List.of(new ThreatSense(), new HuntersLedger(), new Waylight());
    }

    private static Text text(String key, String... args) {
        return Text.translated("hud.essence_ascendance.utility." + key, args);
    }

    private static final class ThreatSense implements SkillEffectHudHandler {
        @Override public ResourceLocation id() { return SkillIds.THREAT_SENSE; }
        @Override public void tick(SkillEffectRuntime.Context context) { UtilitySenseService.tickThreatSense(context); }
        @Override public void deactivate(SkillEffectRuntime.Context context) { context.discardState(id()); }

        @Override public SkillEffectHudEntry hudEntry(SkillEffectRuntime.Context context) {
            var counts = UtilitySenseService.threatCounts(context);
            List<Text> details = new ArrayList<>();
            if (counts.projectilePaths() > 0) details.add(text("projectile_paths", Integer.toString(counts.projectilePaths())));
            if (counts.explosions() > 0 && details.size() < SkillEffectHudEntry.MAX_LINES)
                details.add(text("explosion_zones", Integer.toString(counts.explosions())));
            if (details.size() < 2) details.add(text("sense_range", SkillEffectHudCards.compact(context.settings().utility().threatSense().rangeBlocks())));
            return SkillEffectHudEntry.skill(id(), counts.activeThreats() > 0 || counts.projectilePaths() > 0 || counts.explosions() > 0,
                    AscendancePalette.UTILITY, text("active_threats", Integer.toString(counts.activeThreats())),
                    details, SkillEffectHudEntry.Meter.none());
        }

        @Override public List<String> debugLines(SkillEffectRuntime.Context context) {
            var counts = UtilitySenseService.threatCounts(context);
            return List.of("Range=" + context.settings().utility().threatSense().rangeBlocks()
                            + "; active creatures=" + counts.activeThreats()
                            + "; projectile paths=" + counts.projectilePaths()
                            + "; explosion zones=" + counts.explosions(),
                    "Creature threats require an active native target; projectile previews combine observed flight with generic native-gravity projection and include hostile-owned shots aimed at the player; explosion radii mirror native explosive identity.");
        }
    }

    private static final class HuntersLedger implements SkillEffectHudHandler {
        @Override public ResourceLocation id() { return SkillIds.HUNTERS_LEDGER; }

        @Override public SkillEffectHudEntry hudEntry(SkillEffectRuntime.Context context) {
            var counts = UtilitySenseService.threatCounts(context);
            double seconds = context.settings().utility().huntersLedger().memoryTicks() / 20.0;
            return SkillEffectHudEntry.skill(id(), counts.activeThreats() + counts.rememberedThreats() > 0,
                    AscendancePalette.UTILITY, text("remembered_threats", Integer.toString(counts.rememberedThreats())),
                    List.of(text("ledger_memory", SkillEffectHudCards.compact(seconds)), text("ledger_details")),
                    SkillEffectHudEntry.Meter.none());
        }

        @Override public List<String> debugLines(SkillEffectRuntime.Context context) {
            var counts = UtilitySenseService.threatCounts(context);
            return List.of("Memory=" + context.settings().utility().huntersLedger().memoryTicks() + " ticks; remembered threats=" + counts.rememberedThreats(),
                    "Extends Threat Sense creature tracking and adds a compact above-head health bar plus vanilla-scale armor pips; it does not persist projectile or explosion previews.");
        }
    }

    private static final class Waylight implements SkillEffectHudHandler {
        @Override public ResourceLocation id() { return SkillIds.WAYLIGHT; }
        @Override public void tick(SkillEffectRuntime.Context context) { UtilitySenseService.tickWaylight(context); }
        @Override public void deactivate(SkillEffectRuntime.Context context) { context.discardState(id()); }

        @Override public SkillEffectHudEntry hudEntry(SkillEffectRuntime.Context context) {
            var marker = UtilitySenseService.waylightMarker(context);
            List<Text> details = new ArrayList<>();
            details.add(text("waylight_range", SkillEffectHudCards.compact(context.settings().utility().waylight().searchRadiusBlocks())));
            if (marker == null) {
                details.add(text("waylight_no_candidate"));
            } else {
                details.add(text("waylight_spawnable"));
            }
            details.add(text("waylight_vision"));
            return SkillEffectHudEntry.skill(id(), marker != null, AscendancePalette.UTILITY,
                    marker == null ? text("waylight_searching") : text("waylight_wisp"), details,
                    SkillEffectHudEntry.Meter.none());
        }

        @Override public List<String> debugLines(SkillEffectRuntime.Context context) {
            var marker = UtilitySenseService.waylightMarker(context);
            return List.of("Search radius=" + context.settings().utility().waylight().searchRadiusBlocks()
                            + "; candidate=" + (marker == null ? "none" : marker.feet().toShortString()),
                    "Chooses the nearest naturally spawnable ordinary hostile-mob footing using native placement, collision, and dimension light gates; Night Vision remains client-only presentation, while native Blindness and Darkness applications are rejected server-side and pre-existing instances are purged while Waylight is effective.");
        }
    }
}
