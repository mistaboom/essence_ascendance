package com.mistaboom.essence_ascendance.archive;

import com.mistaboom.essence_ascendance.client.ui.content.SemanticDocument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.function.Supplier;

/** Canonical catalog record: identity and curriculum stay separate from semantic content provision. */
public record ArchiveEntry(ResourceLocation id, ArchiveMode mode, ResourceLocation section,
                           ResourceLocation subject, Component title, Component summary, Component navigationSummary,
                           Supplier<SemanticDocument> content) {
    public ArchiveEntry(ResourceLocation id, ArchiveMode mode, ResourceLocation section, ResourceLocation subject,
                        Component title, Component summary, Supplier<SemanticDocument> content) {
        this(id, mode, section, subject, title, summary, summary, content);
    }
    public ArchiveEntry {
        Objects.requireNonNull(id); Objects.requireNonNull(mode); Objects.requireNonNull(section);
        Objects.requireNonNull(subject); Objects.requireNonNull(title); Objects.requireNonNull(summary);
        Objects.requireNonNull(content);
        Objects.requireNonNull(navigationSummary);
        if (mode == ArchiveMode.SEARCH) throw new IllegalArgumentException("Search results point to Guide or Reference entries");
    }
    public ArchiveLocation.Article location() { return new ArchiveLocation.Article(mode, section, id); }
}
