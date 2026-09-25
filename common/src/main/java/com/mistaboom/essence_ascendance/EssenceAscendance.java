package com.mistaboom.essence_ascendance;

import com.mistaboom.essence_ascendance.balance.BalanceProfileRegistry;
import com.mistaboom.essence_ascendance.command.EssenceCommands;
import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleContent;
import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleGameplayEvents;
import com.mistaboom.essence_ascendance.infuser.EssenceInfuserContent;
import com.mistaboom.essence_ascendance.pylon.EssencePylonContent;
import com.mistaboom.essence_ascendance.nexus.AscendanceNexusContent;
import com.mistaboom.essence_ascendance.equipment.ArmorStatWeights;
import com.mistaboom.essence_ascendance.equipment.EquipmentProfileRegistry;
import com.mistaboom.essence_ascendance.equipment.EquipmentGameplayEvents;
import com.mistaboom.essence_ascendance.equipment.EquipmentProfiles;
import com.mistaboom.essence_ascendance.equipment.EquipmentStatProviderRegistry;
import com.mistaboom.essence_ascendance.essence.EssenceRegistry;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import com.mistaboom.essence_ascendance.item.AscendanceArmorMaterials;
import com.mistaboom.essence_ascendance.item.AscendanceItems;
import com.mistaboom.essence_ascendance.progression.AscendanceAdvancementRegistry;
import com.mistaboom.essence_ascendance.progression.AscendanceAdvancements;
import com.mistaboom.essence_ascendance.progression.AscendanceMilestoneEvents;
import com.mistaboom.essence_ascendance.progression.MilestoneProviderRegistry;
import com.mistaboom.essence_ascendance.progression.MilestoneProviders;
import com.mistaboom.essence_ascendance.progression.MilestoneRegistry;
import com.mistaboom.essence_ascendance.progression.Milestones;
import com.mistaboom.essence_ascendance.stat.EssenceStatRegistry;
import com.mistaboom.essence_ascendance.stat.EssenceStats;
import com.mistaboom.essence_ascendance.skill.SkillRegistry;
import com.mistaboom.essence_ascendance.skill.Skills;
import com.mistaboom.essence_ascendance.tier.AscendanceTierRegistry;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import com.mistaboom.essence_ascendance.worldgen.LatentOreWorldgen;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EssenceAscendance {

    public static final String MOD_ID = "essence_ascendance";

    public static final Logger LOGGER =
            LoggerFactory.getLogger(MOD_ID);

    private EssenceAscendance() {
    }

    public static void init() {
        /* Definition dependency order. */
        EssenceTypes.init();
        AscendanceTiers.init();
        EssenceStats.init();

        /* Skill gates depend on these configurable milestone definitions. */
        MilestoneProviders.init();
        Milestones.init();

        Skills.init();

        /* Equipment profiles reference registered stats. */
        EquipmentProfiles.init();
        ArmorStatWeights.init();
        EquipmentStatProviderRegistry.init();

        /* Native item registrations come after equipment definitions. */
        AscendanceArmorMaterials.init();
        AscendanceItems.init();
        com.mistaboom.essence_ascendance.projectile.ProjectileContent.init();
        EssenceCrucibleContent.init();
        EssencePylonContent.init();
        EssenceInfuserContent.init();
        LatentOreWorldgen.init();
        com.mistaboom.essence_ascendance.network.LatentOreCatalogSync.init();
        AscendanceNexusContent.init();
        EssenceCrucibleGameplayEvents.init();

        AscendanceAdvancements.init();

        EssenceConfigManager.load();

        /* Gameplay hooks depend on loaded balance/config values. */
        EquipmentGameplayEvents.init();
        AscendanceMilestoneEvents.init();

        CommandRegistrationEvent.EVENT.register(
                (dispatcher, registry, selection) ->
                        EssenceCommands.register(dispatcher)
        );

        LOGGER.info(
                "Initializing Essence Ascendance with {} essence types, {} stats, {} skills, {} tiers, {} balance profiles, {} equipment profiles, {} equipment stat providers, {} milestone providers, {} milestones, {} Ascendance advancement definitions, using balance profile {}",
                EssenceRegistry.size(),
                EssenceStatRegistry.size(),
                SkillRegistry.size(),
                AscendanceTierRegistry.size(),
                BalanceProfileRegistry.size(),
                EquipmentProfileRegistry.size(),
                EquipmentStatProviderRegistry.providers().size(),
                MilestoneProviderRegistry.size(),
                MilestoneRegistry.size(),
                AscendanceAdvancementRegistry.size(),
                EssenceConfigManager.get().balanceProfile().id()
        );
    }
}
