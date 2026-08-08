package com.mistaboom.essence_ascendance;

import com.mistaboom.essence_ascendance.stat.EssenceStatRegistry;
import com.mistaboom.essence_ascendance.stat.EssenceStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EssenceAscendance {

    public static final String MOD_ID = "essence_ascendance";
    public static final Logger LOGGER =
            LoggerFactory.getLogger(MOD_ID);

    private EssenceAscendance() {
    }

    public static void init() {
        EssenceStats.init();

        LOGGER.info(
                "Initializing Essence Ascendance with {} registered stats",
                EssenceStatRegistry.size()
        );
    }
}