package com.mistaboom.essence_ascendance.client.nexus;

import com.mistaboom.essence_ascendance.essence.EssenceDefinition;

import java.util.List;
import java.util.Objects;

/** Data-driven category page model consumed by the fullscreen Nexus shell. */
public record NexusCategoryView(
        EssenceDefinition essence,
        PresentationType presentationType,
        long availableEssence,
        List<NexusProgressionTrack> tracks
) {
    public NexusCategoryView {
        Objects.requireNonNull(essence, "Nexus category Essence cannot be null");
        Objects.requireNonNull(presentationType, "Nexus presentation type cannot be null");
        if (availableEssence < 0L) {
            throw new IllegalArgumentException("Available Essence cannot be negative");
        }
        tracks = List.copyOf(tracks);
    }

    public String shortDisplayName() {
        String displayName = essence.displayName();
        String suffix = " Essence";
        if (displayName.endsWith(suffix)
                && displayName.length() > suffix.length()) {
            return displayName.substring(
                    0,
                    displayName.length() - suffix.length()
            );
        }
        return displayName;
    }

    public enum PresentationType {
        ATTRIBUTE_SLIDERS,
        PLACEHOLDER
    }
}
