package com.mistaboom.essence_ascendance.skill.effect;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.network.SkillEffectHudPayload;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Invoked by the existing in-mod diagnostic, never adds a fake gameplay skill or changes a player. */
final class SkillEffectHudDiagnostics {
    private SkillEffectHudDiagnostics() { }

    static void validate(List<String> failures) {
        ResourceLocation first = ResourceLocation.fromNamespaceAndPath(EssenceAscendance.MOD_ID, "diagnostic/first_buff");
        ResourceLocation second = ResourceLocation.fromNamespaceAndPath(EssenceAscendance.MOD_ID, "diagnostic/second_buff");
        ResourceLocation dimension = ResourceLocation.withDefaultNamespace("overworld");
        // Multiple cards from one source, plus an independent implemented catalog skill.
        SkillEffectHudEntry one = new SkillEffectHudEntry(first, SkillIds.FRENZY, true, 0xFFE87929,
                SkillEffectHudEntry.Text.literal("Test one"), SkillEffectHudEntry.Text.literal("1/5"),
                List.of(SkillEffectHudEntry.Text.translated("hud.essence_ascendance.damage", "1.04")),
                SkillEffectHudEntry.Meter.timer("hud.essence_ascendance.chain", 160L));
        SkillEffectHudEntry two = new SkillEffectHudEntry(second, SkillIds.FRENZY, false, 0xFFEA4E4E,
                SkillEffectHudEntry.Text.literal("Test two"), SkillEffectHudEntry.Text.literal("50%"),
                List.of(), SkillEffectHudEntry.Meter.progress(0.5));
        SkillEffectHudEntry independent = SkillEffectHudEntry.skill(SkillIds.NATURES_BOON, true, 0xFF00FF00,
                SkillEffectHudEntry.Text.literal("TEST"), List.of(), SkillEffectHudEntry.Meter.none()).asEvent();
        SkillEffectHudEntry heat = SkillEffectHudEntry.skill(SkillIds.KINDLING, true, 0xFFFF6A2B,
                SkillEffectHudEntry.Text.translated("hud.essence_ascendance.heat", "3", "5"),
                List.of(SkillEffectHudEntry.Text.literal("Zombie")), SkillEffectHudEntry.Meter.progress(0.6));
        SkillEffectHudEntry chill = SkillEffectHudEntry.skill(SkillIds.FROSTBITE, true, 0xFF72D9FF,
                SkillEffectHudEntry.Text.translated("hud.essence_ascendance.chill", "4", "5"),
                List.of(SkillEffectHudEntry.Text.literal("Skeleton")), SkillEffectHudEntry.Meter.progress(0.8));
        SkillEffectHudEntry charge = SkillEffectHudEntry.skill(SkillIds.STATIC_CHARGE, true, 0xFFFFE45C,
                SkillEffectHudEntry.Text.literal("100.0/100.0"),
                List.of(SkillEffectHudEntry.Text.translated("hud.essence_ascendance.next_confirmed_attack")),
                SkillEffectHudEntry.Meter.progress(1.0));
        SkillEffectHudSnapshot source = new SkillEffectHudSnapshot(100L, 12, dimension,
                List.of(one, two, independent, heat, chill, charge), .75, 180L);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            SkillEffectHudPayload.CODEC.encode(buffer, new SkillEffectHudPayload(source));
            SkillEffectHudSnapshot decoded = SkillEffectHudPayload.CODEC.decode(buffer).snapshot();
            if (!source.equals(decoded) || buffer.isReadable()) failures.add("Generic HUD packet round-trip differs.");
            if (!source.sameState(new SkillEffectHudSnapshot(101L, 12, dimension, source.entries(), .75, 180L))) {
                failures.add("HUD clock changes must not count as a content change.");
            }
            if (source.sameState(new SkillEffectHudSnapshot(100L, 13, dimension, source.entries(), .75, 180L))) {
                failures.add("HUD snapshots must distinguish player respawn identity.");
            }
        } catch (RuntimeException exception) {
            failures.add("Generic HUD round-trip failed: " + exception.getMessage());
        } finally {
            buffer.release();
        }
        try {
            new SkillEffectHudSnapshot(100L, 12, dimension, List.of(one, one), 0, 0L);
            failures.add("HUD must reject duplicate card IDs.");
        } catch (IllegalArgumentException expected) { }
        try {
            new SkillEffectHudEntry.Meter(SkillEffectHudEntry.MeterKind.PROGRESS,
                    SkillEffectHudEntry.Text.literal(""), 0L, Double.NaN);
            failures.add("HUD must reject non-finite progress.");
        } catch (IllegalArgumentException expected) { }
    }
}
