package com.mistaboom.essence_ascendance.archive;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;

/** Domain subject shared by distinct Guide and Reference documents. */
public record ArchiveSubject(ResourceLocation id, Component name, List<Component> keywords) {
    public ArchiveSubject {
        Objects.requireNonNull(id); Objects.requireNonNull(name); keywords = List.copyOf(keywords);
    }
}
