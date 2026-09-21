package com.mistaboom.essence_ascendance.skill.effect;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

/** Confirmed native outcomes feed the same fixed cards as persistent effects. No ownership-only activation. */
public final class SkillHudEvents {
    public static final long EVENT_TICKS = 20;
    private static final Map<ServerPlayer, Map<ResourceLocation, Event>> EVENTS = new WeakHashMap<>();
    private SkillHudEvents() { }
    public record Event(long at, SkillEffectHudEntry.Text badge, List<SkillEffectHudEntry.Text> details) {
        public Event { details = List.copyOf(details); }
        public boolean active(long now) { return now >= at && now - at < EVENT_TICKS; }
    }
    public static void record(ServerPlayer player, ResourceLocation skill, String metric, double value) {
        record(player, skill, SkillEffectHudEntry.Text.translated("hud.essence_ascendance.event." + metric,
                SkillEffectHudCards.compact(value)), List.of());
    }
    public static void record(ServerPlayer player, ResourceLocation skill, SkillEffectHudEntry.Text badge,
                              List<SkillEffectHudEntry.Text> details) {
        EVENTS.computeIfAbsent(player, ignored -> new HashMap<>()).put(skill,
                new Event(player.level().getGameTime(), badge, details));
    }
    public static SkillEffectHudEntry card(SkillEffectRuntime.Context context, ResourceLocation skill) {
        Event event = EVENTS.getOrDefault(context.player(), Map.of()).get(skill);
        return SkillEffectHudEntry.skill(skill, event != null && event.active(context.now()), 0,
                event == null ? SkillEffectHudEntry.Text.literal("") : event.badge(),
                event == null ? List.of() : event.details(), SkillEffectHudEntry.Meter.none()).asEvent();
    }
    public static boolean active(SkillEffectRuntime.Context context, ResourceLocation skill) {
        Event event = EVENTS.getOrDefault(context.player(), Map.of()).get(skill);
        return event != null && event.active(context.now());
    }
    public static void forget(ServerPlayer player) { EVENTS.remove(player); }
    public static void forget(ServerPlayer player, ResourceLocation skill) {
        var events = EVENTS.get(player); if (events != null) events.remove(skill);
    }
    public static void clear() { EVENTS.clear(); }
}
