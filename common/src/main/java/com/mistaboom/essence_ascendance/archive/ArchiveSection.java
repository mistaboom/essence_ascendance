package com.mistaboom.essence_ascendance.archive;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.text.EssenceText;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;

/** Localized stable identity for one Guide or Reference curriculum section. */
public record ArchiveSection(ResourceLocation id, ArchiveMode mode, Component label) {
    public ArchiveSection { Objects.requireNonNull(id); Objects.requireNonNull(mode); Objects.requireNonNull(label); }

    public static final ArchiveSection GUIDE_BEGINNING = guide("beginning");
    public static final ArchiveSection GUIDE_ESSENCE = guide("essence");
    public static final ArchiveSection GUIDE_MACHINES = guide("machines");
    public static final ArchiveSection GUIDE_INFUSION = guide("infusion");
    public static final ArchiveSection GUIDE_ASCENDANCE = guide("ascendance");
    public static final ArchiveSection GUIDE_SKILLS = guide("skills");
    public static final ArchiveSection REFERENCE_ESSENCES = reference("essences");
    public static final ArchiveSection REFERENCE_MACHINES = reference("machines");
    public static final ArchiveSection REFERENCE_EQUIPMENT = reference("equipment");
    public static final ArchiveSection REFERENCE_BONUSES = reference("bonuses");
    public static final ArchiveSection REFERENCE_SKILLS = reference("skills");
    public static final ArchiveSection REFERENCE_MECHANICS = reference("mechanics");

    public static final List<ArchiveSection> GUIDE = List.of(GUIDE_BEGINNING, GUIDE_ESSENCE, GUIDE_MACHINES,
            GUIDE_INFUSION, GUIDE_ASCENDANCE, GUIDE_SKILLS);
    public static final List<ArchiveSection> REFERENCE = List.of(REFERENCE_ESSENCES, REFERENCE_MACHINES,
            REFERENCE_EQUIPMENT, REFERENCE_BONUSES, REFERENCE_SKILLS, REFERENCE_MECHANICS);

    private static ArchiveSection guide(String id) { return create(ArchiveMode.GUIDE, id); }
    private static ArchiveSection reference(String id) { return create(ArchiveMode.REFERENCE, id); }
    private static ArchiveSection create(ArchiveMode mode, String path) {
        return new ArchiveSection(ResourceLocation.fromNamespaceAndPath(EssenceAscendance.MOD_ID,
                mode.id() + "/" + path), mode, EssenceText.guide("archive.section." + mode.id() + "." + path));
    }
}
