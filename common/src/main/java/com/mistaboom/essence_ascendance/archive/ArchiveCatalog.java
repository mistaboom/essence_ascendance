package com.mistaboom.essence_ascendance.archive;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.client.presentation.BonusPresentationData;
import com.mistaboom.essence_ascendance.client.presentation.PresentationContext;
import com.mistaboom.essence_ascendance.client.presentation.SkillPresentationData;
import com.mistaboom.essence_ascendance.client.ui.content.SemanticDocument;
import com.mistaboom.essence_ascendance.essence.EssenceRegistry;
import com.mistaboom.essence_ascendance.equipment.EquipmentProfileDefinition;
import com.mistaboom.essence_ascendance.equipment.EquipmentProfiles;
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
        for (var essence : EssenceRegistry.values()) {
            entries.add(customEntry("reference/essences/" + essence.id().getPath(), ArchiveSection.REFERENCE_ESSENCES,
                    "essence", EssenceText.essenceShort(essence).withStyle(style -> style.withColor(
                            AscendancePalette.categoryRgb(essence.id()))),
                    g("archive.entry.reference.essences.category.summary", EssenceText.essenceShort(essence)),
                    g("archive.nav.essences"),
                    () -> ReferenceDocuments.essence(essence)));
        }
        entries.add(entry("reference/essences/item_yields", ArchiveSection.REFERENCE_ESSENCES, "essence",
                ReferenceDocuments::itemYieldsDestination));

        entries.add(entry("reference/machines/crucible", ArchiveSection.REFERENCE_MACHINES, "machines",
                () -> ReferenceDocuments.crucible(PresentationContext.capture())));
        entries.add(entry("reference/machines/pylon", ArchiveSection.REFERENCE_MACHINES, "machines",
                () -> ReferenceDocuments.pylon(PresentationContext.capture())));
        entries.add(entry("reference/machines/infuser", ArchiveSection.REFERENCE_MACHINES, "infusion",
                () -> ReferenceDocuments.infuser(PresentationContext.capture())));
        entries.add(entry("reference/machines/focus", ArchiveSection.REFERENCE_MACHINES, "infusion",
                () -> ReferenceDocuments.focus(PresentationContext.capture())));
        entries.add(entry("reference/machines/nexus", ArchiveSection.REFERENCE_MACHINES, "ascendance",
                ReferenceDocuments::nexus));
        entries.add(entry("reference/machines/channelstone", ArchiveSection.REFERENCE_MACHINES, "machines",
                ReferenceDocuments::channelstone));

        EquipmentProfiles.init();
        addEquipment(entries, EquipmentProfiles.ARMOR, "ascendance_helmet");
        addEquipment(entries, EquipmentProfiles.MELEE_WEAPON, "ascendance_melee_weapon");
        addEquipment(entries, EquipmentProfiles.RANGED_WEAPON, "ascendance_ranged_weapon");
        addEquipment(entries, EquipmentProfiles.MAGIC_CASTER, "ascendance_caster");
        addEquipment(entries, EquipmentProfiles.PICKAXE, "ascendance_pickaxe");
        addEquipment(entries, EquipmentProfiles.AXE, "ascendance_axe");
        addEquipment(entries, EquipmentProfiles.SHOVEL, "ascendance_shovel");
        addEquipment(entries, EquipmentProfiles.HOE, "ascendance_hoe");
        addEquipment(entries, EquipmentProfiles.SHIELD, "ascendance_shield");
        entries.add(entry("reference/equipment/infusion", ArchiveSection.REFERENCE_EQUIPMENT, "infusion",
                () -> ReferenceDocuments.equipmentInfusion(PresentationContext.capture())));
        entries.add(entry("reference/equipment/lifecycle", ArchiveSection.REFERENCE_EQUIPMENT, "equipment",
                () -> ReferenceDocuments.equipmentLifecycle(PresentationContext.capture())));
        for (var stat : EssenceStatRegistry.values()) {
            entries.add(customEntry("reference/bonuses/" + stat.id().getPath(), ArchiveSection.REFERENCE_BONUSES,
                    "bonuses", BonusPresentationData.name(stat), g("archive.entry.reference.bonuses.stat.summary",
                            EssenceText.category(stat.category()).withStyle(style -> style.withColor(
                                    AscendancePalette.categoryRgb(stat.category())))),
                    EssenceText.category(stat.category()).withStyle(style -> style.withColor(AscendancePalette.categoryRgb(stat.category()))),
                    () -> ArchiveDocuments.bonusReference(stat, PresentationContext.capture())));
        }
        for (var skill : SkillRegistry.values()) {
            Component category = EssenceRegistry.get(skill.essenceId()).map(EssenceText::essenceShort)
                    .map(Component.class::cast).orElseGet(() -> Component.literal(skill.essenceId().getPath()))
                    .copy().withStyle(style -> style.withColor(AscendancePalette.categoryRgb(skill.essenceId())));
            entries.add(customEntry("reference/skills/" + skill.id().getPath(), ArchiveSection.REFERENCE_SKILLS,
                    "skills", SkillPresentationData.skillName(skill),
                    g("archive.entry.reference.skills.skill.summary", category),
                    category,
                    () -> ArchiveDocuments.skillReference(skill, PresentationContext.capture())));
        }
        for (String topic : List.of("status", "allocation_storage", "attunement",
                "repair_durability", "death_retention", "overcap")) {
            entries.add(entry("reference/mechanics/" + topic, ArchiveSection.REFERENCE_MECHANICS, "mechanics",
                    () -> ReferenceDocuments.mechanics(topic, PresentationContext.capture())));
        }
        return new ArchiveCatalog(sections, subjects, entries);
    }

    private static void addEquipment(List<ArchiveEntry> entries, EquipmentProfileDefinition profile, String item) {
        String path = profile.id().getPath();
        entries.add(customEntry("reference/equipment/" + path, ArchiveSection.REFERENCE_EQUIPMENT, "equipment",
                ReferenceDocuments.profileTitle(profile.id()),
                g("archive.entry.reference.equipment.profile.summary"),
                g("archive.nav.equipment"),
                () -> ReferenceDocuments.equipmentProfile(profile.id(), item, PresentationContext.capture())));
    }

    private static ArchiveSubject subject(String id) {
        ResourceLocation resource = id("subject/" + id);
        return new ArchiveSubject(resource, g("archive.subject." + id), List.of(g("archive.subject." + id + ".keywords")));
    }

    private static ArchiveEntry entry(String id, ArchiveSection section, String subject,
                                      java.util.function.Supplier<SemanticDocument> provider) {
        String key = "archive.entry." + id.replace('/', '.');
        return new ArchiveEntry(id(id), section.mode(), section.id(), id("subject/" + subject),
                g(key + ".title"), g(key + ".summary"), g(key + ".nav"), provider);
    }

    private static ArchiveEntry customEntry(String id, ArchiveSection section, String subject,
                                            Component title, Component summary, Component navigationSummary,
                                            java.util.function.Supplier<SemanticDocument> provider) {
        return new ArchiveEntry(id(id), section.mode(), section.id(), id("subject/" + subject),
                title, summary, navigationSummary, provider);
    }

    private static Component g(String path, Object... args) { return EssenceText.guide(path, args); }
    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(EssenceAscendance.MOD_ID, path);
    }
}
