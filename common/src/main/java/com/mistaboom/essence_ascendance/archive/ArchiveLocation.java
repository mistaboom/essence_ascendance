package com.mistaboom.essence_ascendance.archive;

import net.minecraft.resources.ResourceLocation;

/** Canonical history value. No Screen, player, menu or runtime object is retained. */
public sealed interface ArchiveLocation permits ArchiveLocation.Article, ArchiveLocation.Search, ArchiveLocation.YieldBrowser {
    ArchiveMode mode();

    record Article(ArchiveMode mode, ResourceLocation section, ResourceLocation entry) implements ArchiveLocation {
        public Article {
            if (mode == ArchiveMode.SEARCH) throw new IllegalArgumentException("Search is not an article location");
        }
    }
    record Search(String query, ResourceLocation selectedResult) implements ArchiveLocation {
        public Search { query = query == null ? "" : query; }
        @Override public ArchiveMode mode() { return ArchiveMode.SEARCH; }
    }
    /** Reserved typed destination for the later read-only yield browser. */
    record YieldBrowser(ResourceLocation subject, ResourceLocation selectedRow) implements ArchiveLocation {
        @Override public ArchiveMode mode() { return ArchiveMode.REFERENCE; }
    }
}
