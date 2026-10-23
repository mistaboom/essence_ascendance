package com.mistaboom.essence_ascendance.verification;

import com.mistaboom.essence_ascendance.visual.EntityPresentationBridge2612;
import com.mistaboom.essence_ascendance.visual.EntityPresentationState2612;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.Map;

/** Runs synthetic-entity cleanup without asking NeoForge to track an unregistered test world. */
public final class FixtureEntityPresentationCompat {
    private FixtureEntityPresentationCompat() { }

    public static void withDetachedEntities(Level detachedLevel, Runnable cleanup) {
        try {
            Field field = EntityPresentationBridge2612.class.getDeclaredField("backend");
            field.setAccessible(true);
            var original = (EntityPresentationBridge2612.Backend) field.get(null);
            if (original == null) throw new IllegalStateException("Loader presentation backend was not installed");
            Map<Entity, EntityPresentationState2612> detached = new IdentityHashMap<>();
            EntityPresentationBridge2612.install(new EntityPresentationBridge2612.Backend() {
                @Override public EntityPresentationState2612 get(Entity entity) {
                    return entity.level() == detachedLevel
                            ? detached.getOrDefault(entity, EntityPresentationState2612.DEFAULT)
                            : original.get(entity);
                }

                @Override public void set(Entity entity, EntityPresentationState2612 state) {
                    if (entity.level() == detachedLevel) detached.put(entity, state);
                    else original.set(entity, state);
                }
            });
            try { cleanup.run(); }
            finally { EntityPresentationBridge2612.install(original); }
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Cannot scope synthetic presentation cleanup", failure);
        }
    }
}
