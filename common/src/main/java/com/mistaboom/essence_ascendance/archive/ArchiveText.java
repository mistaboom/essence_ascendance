package com.mistaboom.essence_ascendance.archive;

import com.mistaboom.essence_ascendance.client.presentation.BonusPresentationData;
import com.mistaboom.essence_ascendance.client.presentation.SkillPresentationData;
import com.mistaboom.essence_ascendance.client.ui.StyledTextLayout;
import com.mistaboom.essence_ascendance.stat.EssenceStatRegistry;
import com.mistaboom.essence_ascendance.text.EssenceText;
import com.mistaboom.essence_ascendance.visual.AscendanceUiPalette;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Authored localized semantic arguments. Runtime never strips or searches translated names. */
final class ArchiveText {
    private ArchiveText() { }
    static Component guide(String path, Object... args) {
        return switch (path) {
            case "archive.guide.infusion.repair.detail" -> phrase(path, args, 2, "fractured");
            case "archive.guide.infusion.durability_efficiency" -> phrase(path, args, 0, "bonus:durability_efficiency");
            case "archive.guide.infusion.durability_efficiency.detail" -> phrase(path, args, 0, "bonus:durability_efficiency");
            case "archive.entry.reference.equipment.lifecycle.summary" -> phrase(path, args, 0, "soulbound");
            case "archive.entry.reference.mechanics.death_retention.summary" -> phrase(path, args, 0, "soulbound");
            case "archive.reference.equipment.infusion.soulbound" -> phrase(path, args, 0, "soulbound");
            case "archive.reference.equipment.lifecycle.body" -> phrase(path, args, 0, "fractured");
            case "archive.reference.equipment.fractured_material" -> phrase(path, args, 0, "fractured");
            case "archive.reference.equipment.lifecycle.fractured" -> phrase(path, args, 0, "fractured");
            case "archive.reference.equipment.lifecycle.repair" -> phrase(path, args, 0, "fractured");
            case "archive.reference.equipment.lifecycle.durability" -> phrase(path, args, 0, "fractured");
            case "archive.reference.equipment.lifecycle.soulbound" -> phrase(path, args, 0, "soulbound");
            case "archive.reference.equipment.lifecycle.death" -> phrase(path, args, 0, "soulbound");
            case "archive.reference.equipment.lifecycle.exceptions" -> phrase(path, args, 0, "fractured", "skill:masterwork_tempering");
            case "archive.reference.mechanics.repair_durability.body" -> phrase(path, args, 0, "bonus:durability_efficiency");
            case "archive.reference.mechanics.repair_durability.artifacts" -> phrase(path, args, 0, "fractured");
            case "archive.reference.mechanics.death_retention.body" -> phrase(path, args, 0, "soulbound");
            case "archive.reference.mechanics.death_retention.artifacts" -> phrase(path, args, 0, "soulbound");
            case "archive.reference.equipment.armor.shares" -> phrase(path, args, 0, "bonus:durability_efficiency");
            case "archive.reference.equipment.shield.passive" -> phrase(path, args, 0, "bonus:damage_reflection");
            case "archive.reference.equipment.shield.block" -> phrase(path, args, 0, "bonus:damage_reflection");
            case "archive.reference.equipment.shield.guarding" -> phrase(path, args, 0, "bonus:guard_readiness", "bonus:guarded_movement", "bonus:knockback_resistance", "fractured");
            default -> EssenceText.guide(path, args);
        };
    }
    private static Component phrase(String path, Object[] args, int count, String... references) {
        Object[] expanded = java.util.Arrays.copyOf(args, Math.max(args.length, count + references.length));
        for (int index = 0; index < references.length; index++) expanded[count + index] = term(references[index]);
        return EssenceText.guide(path, expanded);
    }
    static Component term(String reference) {
        if (reference.equals("soulbound") || reference.equals("fractured"))
            return EssenceText.guide("archive.reference.label." + reference).withColor(reference.equals("soulbound")
                    ? AscendanceUiPalette.SOULBOUND : AscendanceUiPalette.FRACTURED);
        String[] parts = reference.split(":", 2);
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("essence_ascendance", parts[1]);
        Component name = parts[0].equals("skill") ? SkillPresentationData.skillName(id)
                : BonusPresentationData.name(EssenceStatRegistry.get(id).orElseThrow());
        return StyledTextLayout.link(name, "essence_ascendance:reference/"
                + (parts[0].equals("skill") ? "skills/" : "bonuses/") + parts[1]);
    }
    static Component decorate(Component component) {
        net.minecraft.network.chat.MutableComponent result;
        if (component.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents translated) {
            Object[] args = java.util.Arrays.stream(translated.getArgs()).map(value ->
                    value instanceof Component nested ? decorate(nested) : value).toArray();
            Component localized = switch (translated.getKey()) {
                case "skill.essence_ascendance.aquatic_body.description" -> phrase("archive.semantic.skill.essence_ascendance.aquatic_body.description", args, 0, "bonus:swim_speed");
                case "skill.essence_ascendance.aquatic_body.description.resolved.1" -> phrase("archive.semantic.skill.essence_ascendance.aquatic_body.description.resolved.1", args, 0, "bonus:swim_speed");
                case "skill.essence_ascendance.armor_crack.description" -> phrase("archive.semantic.skill.essence_ascendance.armor_crack.description", args, 0, "skill:frenzy");
                case "skill.essence_ascendance.armor_crack.description.resolved" -> phrase("archive.semantic.skill.essence_ascendance.armor_crack.description.resolved", args, 3, "skill:frenzy");
                case "skill.essence_ascendance.armor_crack.description.resolved.1" -> phrase("archive.semantic.skill.essence_ascendance.armor_crack.description.resolved.1", args, 1, "skill:frenzy");
                case "skill.essence_ascendance.deep_ward.description" -> phrase("archive.semantic.skill.essence_ascendance.deep_ward.description", args, 0, "skill:soul_ward");
                case "skill.essence_ascendance.deep_ward.description.resolved" -> phrase("archive.semantic.skill.essence_ascendance.deep_ward.description.resolved", args, 1, "skill:soul_ward");
                case "skill.essence_ascendance.fatigue_flight.description.resolved.1" -> phrase("archive.semantic.skill.essence_ascendance.fatigue_flight.description.resolved.1", args, 1, "bonus:flight_speed");
                case "skill.essence_ascendance.feast_reflex.description.resolved.1" -> phrase("archive.semantic.skill.essence_ascendance.feast_reflex.description.resolved.1", args, 0, "skill:metabolic_conversion", "skill:metabolic_mending");
                case "skill.essence_ascendance.fishing_instinct.description.resolved.1" -> phrase("archive.semantic.skill.essence_ascendance.fishing_instinct.description.resolved.1", args, 1, "bonus:luck");
                case "skill.essence_ascendance.hunters_ledger.description" -> phrase("archive.semantic.skill.essence_ascendance.hunters_ledger.description", args, 0, "skill:threat_sense");
                case "skill.essence_ascendance.hunters_ledger.description.resolved" -> phrase("archive.semantic.skill.essence_ascendance.hunters_ledger.description.resolved", args, 1, "skill:threat_sense");
                case "skill.essence_ascendance.impact_control.description.resolved.1" -> phrase("archive.semantic.skill.essence_ascendance.impact_control.description.resolved.1", args, 0, "bonus:fall_resistance");
                case "skill.essence_ascendance.metabolic_conversion.description.resolved.1" -> phrase("archive.semantic.skill.essence_ascendance.metabolic_conversion.description.resolved.1", args, 0, "skill:feast_reflex");
                case "skill.essence_ascendance.metabolic_mending.description" -> phrase("archive.semantic.skill.essence_ascendance.metabolic_mending.description", args, 0, "skill:feast_reflex");
                case "skill.essence_ascendance.pain_purge.description.resolved.1" -> phrase("archive.semantic.skill.essence_ascendance.pain_purge.description.resolved.1", args, 0, "skill:life_steal");
                case "skill.essence_ascendance.pocket_nets.description.resolved.1" -> phrase("archive.semantic.skill.essence_ascendance.pocket_nets.description.resolved.1", args, 0, "bonus:luck");
                case "skill.essence_ascendance.pure_state.description" -> phrase("archive.semantic.skill.essence_ascendance.pure_state.description", args, 0, "skill:status_mirror");
                case "skill.essence_ascendance.pure_state.description.resolved" -> phrase("archive.semantic.skill.essence_ascendance.pure_state.description.resolved", args, 0, "skill:status_mirror");
                case "skill.essence_ascendance.rush.description" -> phrase("archive.semantic.skill.essence_ascendance.rush.description", args, 0, "skill:running_momentum");
                case "skill.essence_ascendance.rush.description.resolved" -> phrase("archive.semantic.skill.essence_ascendance.rush.description.resolved", args, 1, "skill:running_momentum");
                case "skill.essence_ascendance.second_wind.description.resolved.1" -> phrase("archive.semantic.skill.essence_ascendance.second_wind.description.resolved.1", args, 1, "skill:spirit_walk");
                case "skill.essence_ascendance.shattering_ward.description" -> phrase("archive.semantic.skill.essence_ascendance.shattering_ward.description", args, 0, "skill:soul_ward");
                case "skill.essence_ascendance.shattering_ward.description.resolved" -> phrase("archive.semantic.skill.essence_ascendance.shattering_ward.description.resolved", args, 3, "skill:soul_ward");
                case "skill.essence_ascendance.spirit_walk.description.resolved.1" -> phrase("archive.semantic.skill.essence_ascendance.spirit_walk.description.resolved.1", args, 2, "skill:second_wind");
                case "skill.essence_ascendance.trajectory_theft.description" -> phrase("archive.semantic.skill.essence_ascendance.trajectory_theft.description", args, 0, "skill:interceptor");
                case "skill.essence_ascendance.untethered_flight.description" -> phrase("archive.semantic.skill.essence_ascendance.untethered_flight.description", args, 0, "skill:fatigue_flight");
                case "skill.essence_ascendance.untethered_flight.description.resolved" -> phrase("archive.semantic.skill.essence_ascendance.untethered_flight.description.resolved", args, 0, "bonus:flight_speed");
                case "skill.essence_ascendance.vector_boost.description" -> phrase("archive.semantic.skill.essence_ascendance.vector_boost.description", args, 0, "skill:essence_wings");
                case "skill.essence_ascendance.vector_boost.description.resolved" -> phrase("archive.semantic.skill.essence_ascendance.vector_boost.description.resolved", args, 1, "skill:essence_wings");
                case "skill.essence_ascendance.vector_boost.description.resolved.1" -> phrase("archive.semantic.skill.essence_ascendance.vector_boost.description.resolved.1", args, 1, "skill:essence_wings");
                case "skill.essence_ascendance.vector_jump.description.resolved" -> phrase("archive.semantic.skill.essence_ascendance.vector_jump.description.resolved", args, 1, "skill:double_jump");
                case "skill.essence_ascendance.vector_jump.description.resolved.2" -> phrase("archive.semantic.skill.essence_ascendance.vector_jump.description.resolved.2", args, 0, "skill:double_jump");
                case "stat.essence_ascendance.fall_resistance.description" -> phrase("archive.semantic.stat.essence_ascendance.fall_resistance.description", args, 1, "skill:impact_control");
                default -> Component.translatableWithFallback(translated.getKey(), translated.getFallback(), args);
            };
            result = localized.copy();
        } else result = net.minecraft.network.chat.MutableComponent.create(component.getContents());
        result.setStyle(component.getStyle());
        component.getSiblings().forEach(sibling -> result.append(decorate(sibling)));
        return result;
    }
}
