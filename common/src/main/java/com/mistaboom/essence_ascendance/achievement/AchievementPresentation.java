package com.mistaboom.essence_ascendance.achievement;

import net.minecraft.advancements.AdvancementNode;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.advancements.AdvancementVisibilityEvaluator;

import java.util.Set;
import java.util.stream.Collectors;

/** Discovery only: reveal the selected native tree without changing criteria or completed progress. */
public final class AchievementPresentation {
    public static final ResourceLocation ROOT = ResourceLocation.parse("essence_ascendance:achievements/a022");
    private static final Set<ResourceLocation> SELECTED = Set.of(
            1, 2, 3, 4, 5, 7, 19, 22, 24, 26, 28, 30, 31, 32, 33, 34, 35, 36,
            46, 49, 51, 56, 57, 61, 63, 72, 73, 76, 77, 85, 95, 99, 100, 101, 102,
            107, 110, 114, 118, 119, 120, 122, 126, 128, 131, 136, 138, 139, 145, 166,
            169, 176, 177, 182, 184, 190, 193, 197, 199, 200, 201, 204, 205, 206, 207,
            208, 209, 215, 216).stream().map(id -> ResourceLocation.parse(
                    String.format(java.util.Locale.ROOT, "essence_ascendance:achievements/a%03d", id)))
            .collect(Collectors.toUnmodifiableSet());

    private AchievementPresentation() { }

    public static Set<ResourceLocation> selectedIds() { return SELECTED; }

    public static AdvancementVisibilityEvaluator.Output revealing(AdvancementVisibilityEvaluator.Output output) {
        return (node, nativeVisible) -> output.accept(node, nativeVisible || discoverable(node));
    }

    /** Explicit datapack hiding, removed displays and reparenting outside this tab retain native discovery. */
    public static boolean discoverable(AdvancementNode node) {
        if (!SELECTED.contains(node.holder().id()) || !node.root().holder().id().equals(ROOT)) return false;
        for (var ancestor = node; ancestor != null; ancestor = ancestor.parent()) {
            if (!SELECTED.contains(ancestor.holder().id()) || ancestor.advancement().display().isEmpty()
                    || ancestor.advancement().display().orElseThrow().isHidden()) return false;
        }
        return true;
    }
}
