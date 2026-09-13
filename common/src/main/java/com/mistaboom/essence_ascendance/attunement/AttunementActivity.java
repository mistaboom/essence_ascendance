package com.mistaboom.essence_ascendance.attunement;

/** A meaningful observed outcome; registration never requires ownership of a skill. */
public record AttunementActivity(String id, String categoryId, String units,
                                  String calibrationFamily, boolean baseGameAccessible, String accessItem) {
    public AttunementActivity(String id, String categoryId, String units, String family, boolean accessible) {
        this(id, categoryId, units, family, accessible, "");
    }
    public AttunementActivity {
        if (id == null || id.length() > 128 || !id.matches("[a-z0-9_.:-]+") || categoryId == null || categoryId.isBlank() || categoryId.length() > 128
                || units == null || units.isBlank() || calibrationFamily == null || calibrationFamily.isBlank()
                || accessItem == null || accessItem.length() > 256)
            throw new IllegalArgumentException("Invalid Attunement activity metadata");
    }
    public String labelKey() { return "attunement.essence_ascendance.method." + id; }
    public String descriptionKey() { return labelKey() + ".description"; }
}
