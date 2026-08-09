package com.mistaboom.essence_ascendance;

import com.mistaboom.essence_ascendance.balance.BalanceProfileRegistry;
import com.mistaboom.essence_ascendance.balance.BalanceProfiles;
import com.mistaboom.essence_ascendance.command.EssenceCommands;
import com.mistaboom.essence_ascendance.essence.EssenceRegistry;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import com.mistaboom.essence_ascendance.stat.EssenceStatRegistry;
import com.mistaboom.essence_ascendance.stat.EssenceStats;
import com.mistaboom.essence_ascendance.tier.AscendanceTierRegistry;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
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
         * Registration order matters where definitions reference
         * one another.
         */
        EssenceTypes.init();

        AscendanceTiers.init();

        EssenceStats.init();

        BalanceProfiles.init();


        CommandRegistrationEvent.EVENT.register(
                (dispatcher, registry, selection) ->
                        EssenceCommands.register(
                                dispatcher
                        )
        );


        LOGGER.info(
                "Initializing Essence Ascendance with {} essence types, {} registered stats, {} Ascendance tiers, and {} balance profiles",
                EssenceRegistry.size(),
                EssenceStatRegistry.size(),
                AscendanceTierRegistry.size(),
                BalanceProfileRegistry.size()
        );
    }
}