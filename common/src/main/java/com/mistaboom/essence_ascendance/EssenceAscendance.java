package com.mistaboom.essence_ascendance;

import com.mistaboom.essence_ascendance.essence.EssenceRegistry;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import com.mistaboom.essence_ascendance.stat.EssenceStatRegistry;
import com.mistaboom.essence_ascendance.stat.EssenceStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.mistaboom.essence_ascendance.command.EssenceCommands;
import dev.architectury.event.events.common.CommandRegistrationEvent;

public final class EssenceAscendance {

    public static final String MOD_ID = "essence_ascendance";

    public static final Logger LOGGER =
            LoggerFactory.getLogger(MOD_ID);

    private EssenceAscendance() {
    }

    public static void init() {
        EssenceTypes.init();
        EssenceStats.init();

        CommandRegistrationEvent.EVENT.register(
                (dispatcher, registry, selection) ->
                        EssenceCommands.register(dispatcher)
        );

        LOGGER.info(
                "Initializing Essence Ascendance with {} essence types and {} registered stats",
                EssenceRegistry.size(),
                EssenceStatRegistry.size()
        );
    }
}