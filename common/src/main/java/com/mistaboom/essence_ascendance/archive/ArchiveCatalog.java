package com.mistaboom.essence_ascendance.archive;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.client.presentation.BonusPresentationData;
import com.mistaboom.essence_ascendance.client.presentation.PresentationContext;
import com.mistaboom.essence_ascendance.client.presentation.SkillPresentationData;
import com.mistaboom.essence_ascendance.client.ui.content.SemanticDocument;
import com.mistaboom.essence_ascendance.essence.EssenceRegistry;
import com.mistaboom.essence_ascendance.skill.SkillRegistry;
import com.mistaboom.essence_ascendance.stat.EssenceStatRegistry;
import com.mistaboom.essence_ascendance.text.EssenceText;
import com.mistaboom.essence_ascendance.visual.AscendancePalette;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable Archive curriculum and canonical entry registry. */
public final class ArchiveCatalog {
    public static final ArchiveCatalog DEFAULT = createDefault();

    private final List<ArchiveSection> sections;
    private final List<ArchiveSubject> subjects;
    private final List<ArchiveEntry> entries;
    private final Map<ResourceLocation, ArchiveEntry> byId;
    private final Map<ResourceLocation, List<Component>> searchableCache = new LinkedHashMap<>();
    private PresentationContext.Revision searchableRevision;

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
        var matching = entries.stream().filter(entry -> entry.mode() == mode && entry.section().equals(section));
        if (section.equals(ArchiveSection.REFERENCE_SKILLS.id())
                || section.equals(ArchiveSection.REFERENCE_BONUSES.id())) {
            matching = matching.sorted(Comparator
                    .comparing((ArchiveEntry entry) -> entry.title().getString(), String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(entry -> entry.id().toString()));
        }
        return matching.toList();
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
    public synchronized List<Component> searchableText(ArchiveEntry entry) {
        PresentationContext.Revision revision = PresentationContext.capture().revision();
        if (!Objects.equals(searchableRevision, revision)) {
            searchableRevision = revision;
            searchableCache.clear();
        }
        List<Component> cached = searchableCache.get(entry.id());
        if (cached != null) return cached;
        List<Component> text = new ArrayList<>();
        text.add(entry.title()); text.add(entry.summary());
        subjects.stream().filter(subject -> subject.id().equals(entry.subject())).findFirst().ifPresent(subject -> {
            text.add(subject.name()); text.addAll(subject.keywords());
        });
        text.addAll(entry.content().get().searchableText());
        List<Component> result = List.copyOf(text);
        searchableCache.put(entry.id(), result);
        return result;
    }

    private static ArchiveCatalog createDefault() {
        List<ArchiveSection> sections = new ArrayList<>();
        sections.addAll(ArchiveSection.GUIDE); sections.addAll(ArchiveSection.REFERENCE);
        List<ArchiveSubject> subjects = List.of(
                subject("beginning"), subject("essence"), subject("machines"), subject("infusion"),
                subject("ascendance"), subject("skills"), subject("equipment"), subject("bonuses"), subject("mechanics"));
        List<ArchiveEntry> entries = new ArrayList<>();
        entries.add(entry("guide/beginning/welcome", ArchiveSection.GUIDE_BEGINNING, "beginning", ArchiveDocuments::beginningGuide));
        entries.add(entry("guide/essence/collecting", ArchiveSection.GUIDE_ESSENCE, "essence", ArchiveDocuments::essenceGuide));
        entries.add(entry("guide/machines/first_network", ArchiveSection.GUIDE_MACHINES, "machines", () -> ArchiveDocuments.illustrated("guide.machines.first_network", "essence_crucible")));
        entries.add(entry("guide/infusion/first_focus", ArchiveSection.GUIDE_INFUSION, "infusion", ArchiveDocuments::infusionGuide));
        entries.add(entry("guide/ascendance/nexus", ArchiveSection.GUIDE_ASCENDANCE, "ascendance", () -> ArchiveDocuments.illustrated("guide.ascendance.nexus", "ascendance_nexus")));
        entries.add(entry("guide/skills/choosing", ArchiveSection.GUIDE_SKILLS, "skills", ArchiveDocuments::skillsGuide));
        entries.add(entry("reference/essences/overview", ArchiveSection.REFERENCE_ESSENCES, "essence", () -> ArchiveDocuments.reference("reference.essences.overview", "essentium_nugget")));
        entries.add(entry("reference/machines/overview", ArchiveSection.REFERENCE_MACHINES, "machines", () -> ArchiveDocuments.reference("reference.machines.overview", "essence_pylon")));
        entries.add(entry("reference/equipment/overview", ArchiveSection.REFERENCE_EQUIPMENT, "equipment", () -> ArchiveDocuments.reference("reference.equipment.overview", "ascendance_melee_weapon")));
        for (var stat : EssenceStatRegistry.values()) {
            entries.add(customEntry("reference/bonuses/" + stat.id().getPath(), ArchiveSection.REFERENCE_BONUSES,
                    "bonuses", BonusPresentationData.name(stat), g("archive.entry.reference.bonuses.stat.summary",
                            EssenceText.category(stat.category()).withStyle(style -> style.withColor(
                                    AscendancePalette.categoryRgb(stat.category())))),
                    () -> ArchiveDocuments.bonusReference(stat, PresentationContext.capture())));
        }
        for (var skill : SkillRegistry.values()) {
            Component category = EssenceRegistry.get(skill.essenceId()).map(EssenceText::essenceShort)
                    .map(Component.class::cast).orElseGet(() -> Component.literal(skill.essenceId().getPath()))
                    .copy().withStyle(style -> style.withColor(AscendancePalette.categoryRgb(skill.essenceId())));
            entries.add(customEntry("reference/skills/" + skill.id().getPath(), ArchiveSection.REFERENCE_SKILLS,
                    "skills", SkillPresentationData.skillName(skill),
                    g("archive.entry.reference.skills.skill.summary", category),
                    () -> ArchiveDocuments.skillReference(skill, PresentationContext.capture())));
        }
        entries.add(entry("reference/mechanics/status", ArchiveSection.REFERENCE_MECHANICS, "mechanics", ArchiveDocuments::mechanicsReference));
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

    private static ArchiveEntry customEntry(String id, ArchiveSection section, String subject,
                                            Component title, Component summary,
                                            java.util.function.Supplier<SemanticDocument> provider) {
        return new ArchiveEntry(id(id), section.mode(), section.id(), id("subject/" + subject),
                title, summary, provider);
    }

    private static Component g(String path, Object... args) { return EssenceText.guide(path, args); }
    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(EssenceAscendance.MOD_ID, path);
    }
}
