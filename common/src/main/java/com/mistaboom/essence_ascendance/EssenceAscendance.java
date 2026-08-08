package com.mistaboom.essence_ascendance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EssenceAscendance {
    public static final String MOD_ID = "essence_ascendance";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private EssenceAscendance() {
    }

    public static void init() {
        LOGGER.info("Initializing Essence Ascendance");
    }
}