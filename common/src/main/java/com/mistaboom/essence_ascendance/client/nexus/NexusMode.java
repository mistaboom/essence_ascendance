package com.mistaboom.essence_ascendance.client.nexus;

/** The three explicit, independently selectable Nexus presentations. */
public enum NexusMode {
    ASCENDANCE("nexus.mode.ascendance"),
    BONUSES("nexus.mode.bonuses"),
    SKILLS("nexus.mode.skills");

    private final String translationPath;

    NexusMode(String translationPath) {
        this.translationPath = translationPath;
    }

    public String translationPath() {
        return translationPath;
    }
}
