package com.mistaboom.essence_ascendance.client.transientfx;

import com.mistaboom.essence_ascendance.visual.transientfx.WorldVisualEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

/** A local composition over the existing shared procedural primitives. */
public interface WorldVisualRecipe {
    /** Optional entity attachment affects both distance LOD and lifetime, not just vertex placement. */
    default Vec3 anchor(WorldVisualEvent event, ClientLevel level) { return event.position(); }
    default boolean alive(WorldVisualEvent event, ClientLevel level) { return true; }

    /** Attached recipes must cull at their current owner, rather than the old packet position. */
    interface TargetAttached extends WorldVisualRecipe {
        @Override default Vec3 anchor(WorldVisualEvent event, ClientLevel level) {
            return entityAnchor(event, level, event.targetEntityId());
        }
        @Override default boolean alive(WorldVisualEvent event, ClientLevel level) {
            return entityAlive(level, event.targetEntityId());
        }
    }

    interface SourceAttached extends WorldVisualRecipe {
        @Override default Vec3 anchor(WorldVisualEvent event, ClientLevel level) {
            return entityAnchor(event, level, event.sourceEntityId());
        }
        @Override default boolean alive(WorldVisualEvent event, ClientLevel level) {
            return entityAlive(level, event.sourceEntityId());
        }
    }

    private static Vec3 entityAnchor(WorldVisualEvent event, ClientLevel level, int id) {
        var entity = level.getEntity(id);
        return entity == null ? event.position() : entity.position().add(0, entity.getBbHeight() * 0.5, 0);
    }

    private static boolean entityAlive(ClientLevel level, int id) {
        var entity = level.getEntity(id);
        return entity != null && !entity.isRemoved() && entity.isAlive();
    }
    /** Optional coalescing for repeated entity-attached cues, including multiple nearby emitters. */
    default int repeatIntervalTicks() { return 0; }
    default void renderPrimaryPlanes(WorldVisualRenderContext context) { }
    default void renderPrimaryLines(WorldVisualRenderContext context) { }

    default void renderDetailPlanes(WorldVisualRenderContext context) { }
    default void renderDetailLines(WorldVisualRenderContext context) { }
}
