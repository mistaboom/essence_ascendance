package com.mistaboom.essence_ascendance.balance.runtime;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.balance.BalanceProfileDefinition;
import com.mistaboom.essence_ascendance.progression.AscendanceAdvancementDefinition;
import com.mistaboom.essence_ascendance.progression.HarvestProgressionSafety;
import com.mistaboom.essence_ascendance.progression.MilestoneRequirement;
import com.mistaboom.essence_ascendance.tier.AscendanceTierDefinition;
import com.mistaboom.essence_ascendance.tier.AscendanceTierRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Adjacent chapter topology and generated reachability; no currency or world checklist. */
public final class RuntimeAscensionPolicy {
    private RuntimeAscensionPolicy() { }

    public static Map<ResourceLocation, AscendanceAdvancementDefinition> generate(BalanceProfileDefinition profile) {
        List<AscendanceTierDefinition> tiers = orderedTiers();
        Map<ResourceLocation, AscendanceAdvancementDefinition> result = new LinkedHashMap<>();
        for (int index = 0; index + 1 < tiers.size(); index++) {
            var from = tiers.get(index); var to = tiers.get(index + 1);
            var id = ResourceLocation.fromNamespaceAndPath(EssenceAscendance.MOD_ID,
                    from.id().getPath() + "_to_" + to.id().getPath());
            var definition = new AscendanceAdvancementDefinition(id, from.id(), to.id(),
                    1, 0, 0, 1.0, MilestoneRequirement.always());
            result.put(id, definition);
        }
        return result;
    }

    /** New profiles fail generation/decoding instead of installing a known impossible chain. */
    static void validate(RuntimeBalanceDefinition runtime) {
        var config = runtime.config(); var tiers = orderedTiers();
        if (config.advancements().size() != tiers.size() - 1)
            throw new IllegalArgumentException("Ascension progression must contain every adjacent tier transition exactly once");
        var attunement = runtime.attunement();
        reference(attunement, "movement_region_blocks", 1, 512, true);
        reference(attunement, "movement_max_blocks_per_tick", .01, 64, false);
        reference(attunement, "movement_max_event_blocks", .01, 64, false);
        reference(attunement, "movement_intent_timeout_ticks", 1, 200, true);
        reference(attunement, "exertion_recent_ticks", 1, 1200, true);
        reference(attunement, "exhaustion_per_food", .01, 100, false);
        reference(attunement, "enemy_threat_cap", 1, 4, false);
        var categoryIds = com.mistaboom.essence_ascendance.essence.EssenceRegistry.values().stream()
                .map(category -> category.id().toString()).collect(java.util.stream.Collectors.toSet());
        var methodIds = com.mistaboom.essence_ascendance.attunement.AttunementActivityRegistry.values().stream()
                .map(method -> method.id()).collect(java.util.stream.Collectors.toSet());
        if (!attunement.methods().keySet().equals(methodIds) || attunement.chapters().size() != tiers.size() - 1)
            throw new IllegalArgumentException("Generated Attunement registry changed; explicitly rebuild the profile");
        for (var registered : com.mistaboom.essence_ascendance.attunement.AttunementActivityRegistry.values()) {
            var saved = attunement.methods().get(registered.id());
            if (!saved.categoryId().equals(registered.categoryId()) || !saved.units().equals(registered.units())
                    || !saved.calibrationFamily().equals(registered.calibrationFamily())
                    || saved.baseGameAccessible() != registered.baseGameAccessible()
                    || !saved.labelKey().equals(registered.labelKey()) || !saved.descriptionKey().equals(registered.descriptionKey()))
                throw new IllegalArgumentException("Generated Attunement method metadata changed: " + registered.id() + "; explicitly rebuild the profile");
        }
        for (int index = 0; index + 1 < tiers.size(); index++) {
            var from = tiers.get(index); var to = tiers.get(index + 1);
            reference(attunement, "enemy_damage_" + from.id().getPath(), .01, 1e18, false);
            reference(attunement, "enemy_armor_" + from.id().getPath(), .01, 1e18, false);
            var matches = config.advancements().values().stream().filter(a -> a.fromTierId().equals(from.id())).toList();
            if (matches.size() != 1 || !matches.getFirst().toTierId().equals(to.id()) || to.order() != from.order() + 1)
                throw new IllegalArgumentException("Missing, duplicate or skipped ascension transition from " + from.id());
            var advancement = matches.getFirst();
            var chapter = attunement.chapter(from.id().toString());
            if (chapter == null || !chapter.toTierId().equals(to.id().toString()) || !chapter.categories().keySet().equals(categoryIds))
                throw new IllegalArgumentException("Missing Attunement transition/categories from " + from.id());
            if (!(advancement.worldRequirement() instanceof MilestoneRequirement.Always)
                    || advancement.minimumDevelopedStats() != 0 || advancement.minimumRepresentedCategories() != 0)
                throw new IllegalArgumentException("Player-tier progression is Category Attunement only; use milestones for deliberate skill requirements");
        }
        var miningLocks = HarvestProgressionSafety.evaluate(config);
        if (!miningLocks.isEmpty())
            throw new IllegalArgumentException("Ascension requires mining capability unlocked by a later tier: " + miningLocks);
    }

    private static void reference(com.mistaboom.essence_ascendance.attunement.AttunementProfile profile,
                                  String key, double minimum, double maximum, boolean integer) {
        Double value = profile.references().get(key);
        if (value == null || !Double.isFinite(value) || value < minimum || value > maximum || integer && value != Math.rint(value))
            throw new IllegalArgumentException("Missing or unsafe Attunement adapter reference " + key + "; explicitly rebuild the profile");
    }

    private static List<AscendanceTierDefinition> orderedTiers() {
        return AscendanceTierRegistry.values().stream().sorted(Comparator.comparingInt(AscendanceTierDefinition::order)).toList();
    }
}
