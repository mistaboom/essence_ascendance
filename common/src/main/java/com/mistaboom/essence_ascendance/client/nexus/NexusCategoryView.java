package com.mistaboom.essence_ascendance.client.nexus;

import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import com.mistaboom.essence_ascendance.text.EssenceText;

import java.util.List;
import java.util.Objects;

/** Data-driven category page model consumed by the fullscreen Nexus shell. */
public record NexusCategoryView(
        EssenceDefinition essence,
        long availableEssence,
        List<NexusProgressionTrack> tracks
) {
    public NexusCategoryView {
        Objects.requireNonNull(essence, "Nexus category Essence cannot be null");
        if (availableEssence < 0L) {
            throw new IllegalArgumentException("Available Essence cannot be negative");
        }
        tracks = List.copyOf(tracks);
    }

    public String shortDisplayName() {
        return EssenceText.essenceShort(essence).getString();
    }
}
