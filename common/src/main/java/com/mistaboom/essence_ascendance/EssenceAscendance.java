package com.mistaboom.essence_ascendance;

import com.mistaboom.essence_ascendance.balance.BalanceProfileRegistry;
import com.mistaboom.essence_ascendance.balance.BalanceProfiles;
import com.mistaboom.essence_ascendance.command.EssenceCommands;
import com.mistaboom.essence_ascendance.essence.EssenceRegistry;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import com.mistaboom.essence_ascendance.progression.AscendanceAdvancementRegistry;
import com.mistaboom.essence_ascendance.progression.AscendanceAdvancements;
import com.mistaboom.essence_ascendance.progression.MilestoneProviderRegistry;
import com.mistaboom.essence_ascendance.progression.MilestoneProviders;
import com.mistaboom.essence_ascendance.progression.MilestoneRegistry;
import com.mistaboom.essence_ascendance.progression.Milestones;
import com.mistaboom.essence_ascendance.stat.EssenceStatRegistry;
import com.mistaboom.essence_ascendance.stat.EssenceStats;
import com.mistaboom.essence_ascendance.tier.AscendanceTierRegistry;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.progression.AscendanceMilestoneEvents;
import com.mistaboom.essence_ascendance.equipment.StatConduits;
import com.mistaboom.essence_ascendance.equipment.ArmorConduitWeights;
import com.mistaboom.essence_ascendance.item.AscendanceArmorMaterials;
import com.mistaboom.essence_ascendance.item.AscendanceItems;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EssenceAscendance {

    public static final String MOD_ID =
            "essence_ascendance";

    public static final Logger LOGGER =
            LoggerFactory.getLogger(
                    MOD_ID
            );


    private EssenceAscendance() {
    }


    public static void init() {

        /*
         * Definitions are initialized in dependency order.
         */

        EssenceTypes.init();

        AscendanceTiers.init();

        EssenceStats.init();

        StatConduits.init();

        ArmorConduitWeights.init();

        /*
         * Game content.
         *
         * Armor material must be registered before the armor items
         * that reference it.
         */

        AscendanceArmorMaterials.init();

        AscendanceItems.init();

        BalanceProfiles.init();

        MilestoneProviders.init();

        Milestones.init();

        AscendanceAdvancements.init();

        EssenceConfigManager.load();

        AscendanceMilestoneEvents.init();

        CommandRegistrationEvent.EVENT.register(
                (dispatcher, registry, selection) ->
                        EssenceCommands.register(
                                dispatcher
                        )
        );

        LOGGER.info(
                "Initializing Essence Ascendance with {} essence types, {} registered stats, {} Ascendance tiers, {} balance profiles, {} milestone providers, {} milestones, {} Ascendance advancement definitions, using balance profile {}",
                EssenceRegistry.size(),
                EssenceStatRegistry.size(),
                AscendanceTierRegistry.size(),
                BalanceProfileRegistry.size(),
                MilestoneProviderRegistry.size(),
                MilestoneRegistry.size(),
                AscendanceAdvancementRegistry.size(),
                EssenceConfigManager
                        .get()
                        .balanceProfile()
                        .id()
        );
    }
}