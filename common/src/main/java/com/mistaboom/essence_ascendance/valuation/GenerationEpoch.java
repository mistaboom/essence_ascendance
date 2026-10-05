package com.mistaboom.essence_ascendance.valuation;

/** Identity guard for an in-flight operation only; never persisted or compared with saved profiles. */
final class GenerationEpoch {
    private Object server, resources, recipes, registries, reloadable;
    GenerationEpoch(Object server, Object resources, Object recipes, Object registries, Object reloadable) {
        this.server = java.util.Objects.requireNonNull(server); this.resources = java.util.Objects.requireNonNull(resources);
        this.recipes = java.util.Objects.requireNonNull(recipes); this.registries = java.util.Objects.requireNonNull(registries);
        this.reloadable = java.util.Objects.requireNonNull(reloadable);
    }
    void require(Object server, Object resources, Object recipes, Object registries, Object reloadable) {
        if (this.server == null || this.server != server || this.resources != resources || this.recipes != recipes
                || this.registries != registries || this.reloadable != reloadable)
            throw new IllegalStateException("Generation snapshot does not belong to this server/resource operation");
    }
    void clear() { server = null; resources = null; recipes = null; registries = null; reloadable = null; }
}
