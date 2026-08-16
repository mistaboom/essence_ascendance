package com.mistaboom.essence_ascendance.client;

import dev.architectury.platform.Platform;

import java.util.concurrent.atomic.AtomicLong;

/*
 * Compatibility revision for JEI's cached ingredient-search index.
 *
 * Dynamic Essence tooltip state arrives from the server. Each completed tooltip
 * state update increments this revision.
 *
 * Optional JEI mixins compare this revision with the last revision they indexed
 * and rebuild JEI lazily before its ingredient/search list is returned.
 *
 * This removes startup timing assumptions completely.
 */
public final class JeiTooltipSearchRefreshBridge {

    private static final AtomicLong REVISION =
            new AtomicLong();

    private JeiTooltipSearchRefreshBridge() {
    }

    public static void requestRefresh() {
        if (Platform.isModLoaded(
                "jei"
        )) {
            REVISION.incrementAndGet();
        }
    }

    public static long revision() {
        return REVISION.get();
    }
}
