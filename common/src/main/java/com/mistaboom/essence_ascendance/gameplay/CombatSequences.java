package com.mistaboom.essence_ascendance.gameplay;

import java.util.*;

/** Bounded actor/action grouping and ordered kill queries, independent of awards and Minecraft entities. */
public final class CombatSequences {
    public static final int MAX_ACTORS = 256, MAX_ACTIONS = 64, MAX_VICTIMS = 64, MAX_KILLS = 32;
    public static final long EXPIRATION_TICKS = 200;
    public record Kill(long order, CombatOutcome.Context context) { }
    public record Accepted(boolean accepted, int hostileKills) { }
    private static final class Action {
        final long started;
        final Set<UUID> victims = new HashSet<>();
        int hostile;
        Action(long started) { this.started = started; }
    }
    private static final class Actor {
        final Map<String, Action> actions = new HashMap<>();
        final Deque<Kill> kills = new ArrayDeque<>();
        String dimension;
        long lastTick, order;
    }
    private final Map<UUID, Actor> actors = new HashMap<>();

    public Accepted killed(CombatOutcome.Context context, long now) {
        if (context == null || context.actor() == null || context.victim() == null
                || context.actionId() == null || context.actionId().isBlank()
                || now < context.committedTick() || now - context.committedTick() >= EXPIRATION_TICKS)
            return new Accepted(false, 0);
        prune(now);
        Actor actor = actors.get(context.actor());
        if (actor == null) {
            if (actors.size() >= MAX_ACTORS) return new Accepted(false, 0);
            actor = new Actor(); actors.put(context.actor(), actor);
        }
        if (!Objects.equals(actor.dimension, context.dimension()) || now < actor.lastTick) {
            actor.actions.clear(); actor.kills.clear();
        }
        actor.dimension = context.dimension(); actor.lastTick = now;
        Action action = actor.actions.get(context.actionId());
        // A rejected duplicate must not allocate an empty group and consume another action slot.
        for (Action existing : actor.actions.values())
            if (existing.victims.contains(context.victim())) return new Accepted(false, action == null ? 0 : action.hostile);
        if (action == null) {
            if (actor.actions.size() >= MAX_ACTIONS) return new Accepted(false, 0);
            action = new Action(now); actor.actions.put(context.actionId(), action);
        }
        // Victim deduplication is distinct from sequence order and spans all actions in the window.
        if (action.victims.size() >= MAX_VICTIMS) return new Accepted(false, action.hostile);
        action.victims.add(context.victim());
        if (context.hostile()) action.hostile++;
        if (actor.kills.size() == MAX_KILLS) actor.kills.removeFirst();
        actor.kills.addLast(new Kill(++actor.order, context));
        return new Accepted(true, action.hostile);
    }
    public List<Kill> recent(UUID actor, long now) {
        prune(now); Actor state = actors.get(actor);
        return state == null ? List.of() : List.copyOf(state.kills);
    }
    public boolean repeatedType(UUID actor, String type, int count, long now) {
        if (count < 1 || count > MAX_KILLS) return false;
        List<Kill> kills = recent(actor, now);
        return kills.size() >= count && kills.subList(kills.size()-count,kills.size()).stream()
                .allMatch(kill -> kill.context().victimType().equals(type));
    }
    public boolean distinctTypes(UUID actor, int count, long now) {
        if (count < 1 || count > MAX_KILLS) return false;
        List<Kill> kills = recent(actor, now);
        return kills.size() >= count && kills.subList(kills.size()-count,kills.size()).stream()
                .map(kill -> kill.context().victimType()).distinct().count() == count;
    }
    public void forget(UUID actor) { actors.remove(actor); }
    public void clear() { actors.clear(); }
    private void prune(long now) {
        actors.values().removeIf(actor -> now < actor.lastTick || now - actor.lastTick >= EXPIRATION_TICKS);
        for (Actor actor : actors.values()) {
            actor.actions.values().removeIf(action -> now < action.started || now - action.started >= EXPIRATION_TICKS);
            actor.kills.removeIf(kill -> now < kill.context().committedTick()
                    || now - kill.context().committedTick() >= EXPIRATION_TICKS);
        }
    }
}
