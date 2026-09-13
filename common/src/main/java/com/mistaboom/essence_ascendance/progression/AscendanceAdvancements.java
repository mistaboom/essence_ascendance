package com.mistaboom.essence_ascendance.progression;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.tier.AscendanceTierDefinition;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import net.minecraft.resources.ResourceLocation;

/** Stable transition identifiers. RuntimeAscensionPolicy supplies generated qualification amounts. */
public final class AscendanceAdvancements {
    public static final AscendanceAdvancementDefinition DORMANT_TO_AWAKENED =
            register(AscendanceTiers.DORMANT, AscendanceTiers.AWAKENED);
    public static final AscendanceAdvancementDefinition AWAKENED_TO_RESONANT =
            register(AscendanceTiers.AWAKENED, AscendanceTiers.RESONANT);
    public static final AscendanceAdvancementDefinition RESONANT_TO_ASCENDANT =
            register(AscendanceTiers.RESONANT, AscendanceTiers.ASCENDANT);
    public static final AscendanceAdvancementDefinition ASCENDANT_TO_TRANSCENDENT =
            register(AscendanceTiers.ASCENDANT, AscendanceTiers.TRANSCENDENT);

    private AscendanceAdvancements() { }

    private static AscendanceAdvancementDefinition register(AscendanceTierDefinition from, AscendanceTierDefinition to) {
        return AscendanceAdvancementRegistry.register(new AscendanceAdvancementDefinition(
                ResourceLocation.fromNamespaceAndPath(EssenceAscendance.MOD_ID,
                        from.id().getPath() + "_to_" + to.id().getPath()),
                from.id(), to.id(), 1, 0, 0, 1.0, MilestoneRequirement.always()));
    }

    public static void init() {
        // Forces registration. No hard-coded mineral/boss gates or per-tier cost table.
    }
}
