package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import dev.architectury.platform.Platform;

import java.util.concurrent.atomic.AtomicLong;

/*
 * Compatibility revision for JEI's cached ingredient-search index.
 *
 * Dynamic Essence tooltip state arrives from the server. Each completed update
 * that changes tooltip state increments this revision. Identical snapshots
 * and clearing already-empty state do not invalidate the search index.
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

    public static void requestRefresh(String reason, int entries) {
        if (Platform.isModLoaded(
                "jei"
        )) {
            long revision = REVISION.incrementAndGet();
            // One line per changed snapshot/clear, never per ingredient or frame.
            EssenceAscendance.LOGGER.info(
                    "Requested JEI tooltip search revision {}: reason={}, entries={}",
                    revision, reason, entries);
        }
    }

    public static long revision() {
        return REVISION.get();
    }
}
