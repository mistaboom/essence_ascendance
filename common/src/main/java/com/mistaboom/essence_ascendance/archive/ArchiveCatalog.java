package com.mistaboom.essence_ascendance.archive;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.client.ui.content.SemanticDocument;
import com.mistaboom.essence_ascendance.text.EssenceText;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable Archive curriculum and canonical entry registry. */
public final class ArchiveCatalog {
    public static final ArchiveCatalog DEFAULT = createDefault();

    private final List<ArchiveSection> sections;
    private final List<ArchiveSubject> subjects;
    private final List<ArchiveEntry> entries;
    private final Map<ResourceLocation, ArchiveEntry> byId;

    public ArchiveCatalog(List<ArchiveSection> sections, List<ArchiveSubject> subjects, List<ArchiveEntry> entries) {
        this.sections = List.copyOf(sections);
        this.subjects = List.copyOf(subjects);
        this.entries = List.copyOf(entries);
        Map<ResourceLocation, ArchiveSection> sectionIds = new LinkedHashMap<>();
        for (ArchiveSection section : this.sections) {
            if (section.mode() == ArchiveMode.SEARCH || sectionIds.put(section.id(), section) != null)
                throw new IllegalArgumentException("Invalid or duplicate Archive section " + section.id());
        }
        HashSet<ResourceLocation> subjectIds = new HashSet<>();
        for (ArchiveSubject subject : this.subjects) if (!subjectIds.add(subject.id()))
            throw new IllegalArgumentException("Duplicate Archive subject " + subject.id());
        Map<ResourceLocation, ArchiveEntry> indexed = new LinkedHashMap<>();
        for (ArchiveEntry entry : this.entries) {
            ArchiveSection section = sectionIds.get(entry.section());
            if (section == null || section.mode() != entry.mode())
                throw new IllegalArgumentException("Entry has an invalid section " + entry.id());
            if (!subjectIds.contains(entry.subject())) throw new IllegalArgumentException("Entry has an invalid subject " + entry.id());
            if (indexed.put(entry.id(), entry) != null) throw new IllegalArgumentException("Duplicate Archive entry " + entry.id());
        }
        byId = Map.copyOf(indexed);
    }

    public List<ArchiveSection> sections(ArchiveMode mode) {
        return sections.stream().filter(section -> section.mode() == mode).toList();
    }
    public List<ArchiveEntry> entries(ArchiveMode mode, ResourceLocation section) {
        return entries.stream().filter(entry -> entry.mode() == mode && entry.section().equals(section)).toList();
    }
    public List<ArchiveEntry> entries() { return entries; }
    public ArchiveEntry entry(ResourceLocation id) { return id == null ? null : byId.get(id); }
    public ArchiveEntry first(ArchiveMode mode, ResourceLocation section) {
        return entries(mode, section).stream().findFirst().orElse(null);
    }
    public ArchiveSection section(ResourceLocation id) {
        return sections.stream().filter(section -> section.id().equals(id)).findFirst().orElse(null);
    }

    /** Components come directly from catalog metadata and semantic providers; no second article index exists. */
    public List<Component> searchableText(ArchiveEntry entry) {
        List<Component> text = new ArrayList<>();
        text.add(entry.title()); text.add(entry.summary());
        subjects.stream().filter(subject -> subject.id().equals(entry.subject())).findFirst().ifPresent(subject -> {
            text.add(subject.name()); text.addAll(subject.keywords());
        });
        text.addAll(entry.content().get().searchableText());
        return List.copyOf(text);
    }

    private static ArchiveCatalog createDefault() {
        List<ArchiveSection> sections = new ArrayList<>();
        sections.addAll(ArchiveSection.GUIDE); sections.addAll(ArchiveSection.REFERENCE);
        List<ArchiveSubject> subjects = List.of(
                subject("beginning"), subject("essence"), subject("machines"), subject("infusion"),
                subject("ascendance"), subject("skills"), subject("equipment"), subject("bonuses"), subject("mechanics"));
        List<ArchiveEntry> entries = List.of(
                entry("guide/beginning/welcome", ArchiveSection.GUIDE_BEGINNING, "beginning", ArchiveDocuments::beginningGuide),
                entry("guide/essence/collecting", ArchiveSection.GUIDE_ESSENCE, "essence", ArchiveDocuments::essenceGuide),
                entry("guide/machines/first_network", ArchiveSection.GUIDE_MACHINES, "machines", () -> ArchiveDocuments.illustrated("guide.machines.first_network", "essence_crucible")),
                entry("guide/infusion/first_focus", ArchiveSection.GUIDE_INFUSION, "infusion", ArchiveDocuments::infusionGuide),
                entry("guide/ascendance/nexus", ArchiveSection.GUIDE_ASCENDANCE, "ascendance", () -> ArchiveDocuments.illustrated("guide.ascendance.nexus", "ascendance_nexus")),
                entry("guide/skills/choosing", ArchiveSection.GUIDE_SKILLS, "skills", ArchiveDocuments::skillsGuide),
                entry("reference/essences/overview", ArchiveSection.REFERENCE_ESSENCES, "essence", () -> ArchiveDocuments.reference("reference.essences.overview", "essentium_nugget")),
                entry("reference/machines/overview", ArchiveSection.REFERENCE_MACHINES, "machines", () -> ArchiveDocuments.reference("reference.machines.overview", "essence_pylon")),
                entry("reference/equipment/overview", ArchiveSection.REFERENCE_EQUIPMENT, "equipment", () -> ArchiveDocuments.reference("reference.equipment.overview", "ascendance_melee_weapon")),
                entry("reference/bonuses/overview", ArchiveSection.REFERENCE_BONUSES, "bonuses", ArchiveDocuments::bonusesReference),
                entry("reference/skills/ranks", ArchiveSection.REFERENCE_SKILLS, "skills", ArchiveDocuments::skillRanksReference),
                entry("reference/mechanics/status", ArchiveSection.REFERENCE_MECHANICS, "mechanics", ArchiveDocuments::mechanicsReference));
        return new ArchiveCatalog(sections, subjects, entries);
    }

    private static ArchiveSubject subject(String id) {
        ResourceLocation resource = id("subject/" + id);
        return new ArchiveSubject(resource, g("archive.subject." + id), List.of(g("archive.subject." + id + ".keywords")));
    }

    private static ArchiveEntry entry(String id, ArchiveSection section, String subject,
                                      java.util.function.Supplier<SemanticDocument> provider) {
        String key = "archive.entry." + id.replace('/', '.');
        return new ArchiveEntry(id(id), section.mode(), section.id(), id("subject/" + subject),
                g(key + ".title"), g(key + ".summary"), provider);
    }

    private static Component g(String path, Object... args) { return EssenceText.guide(path, args); }
    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(EssenceAscendance.MOD_ID, path);
    }
}
