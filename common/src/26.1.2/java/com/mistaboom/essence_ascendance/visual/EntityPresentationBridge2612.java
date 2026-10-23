package com.mistaboom.essence_ascendance.visual;

import net.minecraft.world.entity.Entity;
import java.util.Objects;

/** Loader boundary for tracking-visible state on players, arrows and custom fishing hooks. */
public final class EntityPresentationBridge2612 {
    public interface Backend {
        EntityPresentationState2612 get(Entity entity);
        void set(Entity entity, EntityPresentationState2612 state);
    }
    private static Backend backend;
    private EntityPresentationBridge2612() { }
    public static void install(Backend value) { backend = Objects.requireNonNull(value); }
    public static EntityPresentationState2612 get(Entity entity) {
        if (backend == null) throw new IllegalStateException("Entity presentation sync backend not installed");
        return backend.get(entity);
    }
    public static void set(Entity entity, EntityPresentationState2612 state) {
        if (backend == null) throw new IllegalStateException("Entity presentation sync backend not installed");
        if (!backend.get(entity).equals(state)) backend.set(entity, state);
    }
}
