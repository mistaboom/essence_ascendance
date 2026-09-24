package com.mistaboom.essence_ascendance.client.presentation;

import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import com.mistaboom.essence_ascendance.essence.EssenceRegistry;
import com.mistaboom.essence_ascendance.skill.SkillDefinition;
import com.mistaboom.essence_ascendance.skill.SkillRegistry;
import com.mistaboom.essence_ascendance.stat.EssenceStatRegistry;
import com.mistaboom.essence_ascendance.stat.StatDefinition;
import com.mistaboom.essence_ascendance.text.EssenceText;
import com.mistaboom.essence_ascendance.visual.AscendancePalette;
import com.mistaboom.essence_ascendance.client.ui.content.ItemPresentation;
import com.mistaboom.essence_ascendance.infuser.EssentiumCarrierData;
import com.mistaboom.essence_ascendance.pylon.EssenceFocusTier;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;

import java.util.Comparator;
import java.util.List;

/** Registry-derived Essence identity and relationships shared by reference surfaces. */
public final class EssencePresentationData {
    private EssencePresentationData() { }

    public record Projection(EssenceDefinition essence, Component name, int color,
                             List<StatDefinition> bonuses, List<SkillDefinition> skills) { }

    public static List<Projection> all() {
        return EssenceRegistry.values().stream().map(EssencePresentationData::project).toList();
    }

    public static Projection project(EssenceDefinition essence) {
        List<StatDefinition> bonuses = EssenceStatRegistry.values().stream()
                .filter(stat -> stat.essenceType().id().equals(essence.id()))
                .sorted(Comparator.comparing(stat -> stat.id().toString())).toList();
        List<SkillDefinition> skills = SkillRegistry.values().stream()
                .filter(skill -> skill.essenceId().equals(essence.id()))
                .sorted(Comparator.comparing(skill -> skill.id().toString())).toList();
        Component name = EssenceText.essenceShort(essence).withStyle(style ->
                style.withColor(AscendancePalette.categoryRgb(essence.id())));
        return new Projection(essence, name, AscendancePalette.categoryRgb(essence.id()), bonuses, skills);
    }

    /** Appearance-only variants: one unit identifies a filled carrier, without inventing capacity. */
    public static List<ItemPresentation> carrierModels(EssenceDefinition essence, ResourceLocation form) {
        return java.util.Arrays.stream(EssenceFocusTier.values()).map(grade -> {
            Component tier = EssenceText.focusTier(grade).withColor(AscendancePalette.tierMetalRgb(
                    ResourceLocation.fromNamespaceAndPath(essence.id().getNamespace(), grade.serializedName())));
            Component name = EssenceText.essenceShort(essence).withColor(AscendancePalette.categoryRgb(essence));
            Component item = Component.translatable("item." + form.getNamespace() + "." + form.getPath());
            return new ItemPresentation(form, DataComponentPatch.builder().set(DataComponents.CUSTOM_DATA,
                    EssentiumCarrierData.carrierData(new EssentiumCarrierData.Value(essence, grade, 1))).build(),
                    EssenceText.guide("archive.value.carrier_variant", tier, name, item));
        }).toList();
    }
}
