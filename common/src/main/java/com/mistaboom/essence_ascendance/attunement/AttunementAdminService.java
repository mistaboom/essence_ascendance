package com.mistaboom.essence_ascendance.attunement;

import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import java.util.List;

/** Validates a complete operator request before changing any chapter state. Server callers synchronize it. */
public final class AttunementAdminService {
    private AttunementAdminService() { }

    public static List<String> setPercent(PlayerEssenceData data, AttunementProfile profile, String category, int percent) {
        if (percent < 0 || percent > 100) throw new IllegalArgumentException("Attunement percentage must be 0..100");
        var chapter = profile.chapter(data.getTierId().toString());
        if (chapter == null) throw new IllegalArgumentException("No active Attunement chapter");
        var selected = category == null ? List.copyOf(chapter.categories().keySet()) : List.of(category);
        if (!chapter.categories().keySet().containsAll(selected)) throw new IllegalArgumentException("Unknown chapter category");
        data.attunement().chapter(chapter.id());
        long value = percent * (AttunementLedger.SCALE / 100);
        for (String id : selected) data.attunement().setProgressForAdmin(id, value);
        data.markAttunementChangedForAdmin();
        return selected;
    }
}
