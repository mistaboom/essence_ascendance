package com.mistaboom.essence_ascendance.archive;

import com.mistaboom.essence_ascendance.text.EssenceText;
import net.minecraft.network.chat.Component;

/** Stable top-level Archive modes. */
public enum ArchiveMode {
    GUIDE("guide"), REFERENCE("reference"), SEARCH("search");

    private final String id;
    ArchiveMode(String id) { this.id = id; }
    public String id() { return id; }
    public Component label() { return EssenceText.guide("archive.mode." + id); }
}
