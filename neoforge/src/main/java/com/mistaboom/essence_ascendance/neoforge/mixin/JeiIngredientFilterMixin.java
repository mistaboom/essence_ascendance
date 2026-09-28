package com.mistaboom.essence_ascendance.neoforge.mixin;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.client.JeiTooltipSearchRefreshBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

/*
 * Optional JEI 19.x compatibility.
 *
 * JEI caches its tooltip-search strings. Essence Ascendance's values arrive
 * from the server after login and can change after an in-game mapping reload.
 *
 * Right before JEI returns the ingredient/search list, compare the last indexed
 * Essence revision to the current one. If it changed, rebuild JEI's index
 * synchronously first.
 * JEI's own initial indexing and resource-reload rebuilds also satisfy that
 * revision, so opening the inventory does not repeat work JEI already did.
 *
 * This is intentionally lazy:
 * - no JEI-startup race;
 * - no client-tick polling;
 * - no assumption about when JEI publishes its runtime;
 * - the search that needs fresh data triggers the refresh itself.
 */
@Pseudo
@Mixin(
        targets = "mezz.jei.gui.ingredients.IngredientFilter",
        remap = false
)
public abstract class JeiIngredientFilterMixin {

    @Unique
    private long essenceAscendance$indexedRevision =
            -1L;

    @Unique
    private long essenceAscendance$rebuildRevision;

    @Unique
    private long essenceAscendance$indexStartedNanos;

    @Unique
    private boolean essenceAscendance$rebuilding =
            false;

    @Unique
    private static boolean essenceAscendance$reflectionWarningLogged =
            false;

    @Inject(method = "<init>", at = @At("RETURN"), require = 0, remap = false)
    private void essenceAscendance$initialIndexComplete(CallbackInfo callback) {
        // Initial indexing and queued tooltip updates both run on the client thread.
        essenceAscendance$indexedRevision = JeiTooltipSearchRefreshBridge.revision();
        EssenceAscendance.LOGGER.info(
                "JEI initial index complete: indexed Essence revision={}",
                essenceAscendance$indexedRevision);
    }

    @Inject(method = "rebuildItemFilter", at = @At("HEAD"), require = 0, remap = false)
    private void essenceAscendance$indexRebuildStarted(CallbackInfo callback) {
        essenceAscendance$rebuildRevision = JeiTooltipSearchRefreshBridge.revision();
        essenceAscendance$indexStartedNanos = System.nanoTime();
        EssenceAscendance.LOGGER.info(
                "JEI index rebuild started: cause={}, indexed Essence revision={}, current={}",
                essenceAscendance$rebuilding ? "essence-tooltip-change" : "jei",
                essenceAscendance$indexedRevision, essenceAscendance$rebuildRevision);
    }

    @Inject(method = "rebuildItemFilter", at = @At("RETURN"), require = 0, remap = false)
    private void essenceAscendance$indexRebuildComplete(CallbackInfo callback) {
        // A later revision still needs indexing; a failed rebuild never reaches RETURN.
        essenceAscendance$indexedRevision = essenceAscendance$rebuildRevision;
        EssenceAscendance.LOGGER.info(
                "JEI index rebuild complete: cause={}, indexed Essence revision={}, current={}, elapsed={} ms",
                essenceAscendance$rebuilding ? "essence-tooltip-change" : "jei",
                essenceAscendance$indexedRevision, JeiTooltipSearchRefreshBridge.revision(),
                (System.nanoTime() - essenceAscendance$indexStartedNanos) / 1_000_000L);
    }

    @Inject(
            method = "getElements",
            at = @At("HEAD"),
            require = 0,
            remap = false
    )
    private void essenceAscendance$refreshSearchIndexIfNeeded(
            CallbackInfoReturnable<List<?>> callback
    ) {
        if (essenceAscendance$rebuilding) {
            return;
        }

        long currentRevision =
                JeiTooltipSearchRefreshBridge.revision();

        if (essenceAscendance$indexedRevision
                == currentRevision) {
            return;
        }

        try {
            essenceAscendance$rebuilding =
                    true;
            long started = System.nanoTime();

            Method rebuild =
                    this.getClass()
                            .getMethod(
                                    "rebuildItemFilter"
                            );

            rebuild.invoke(
                    this
            );

            essenceAscendance$indexedRevision =
                    currentRevision;

            EssenceAscendance.LOGGER.info(
                    "Rebuilt JEI search index for Essence tooltip revision {} in {} ms",
                    currentRevision, (System.nanoTime() - started) / 1_000_000L
            );

        } catch (NoSuchMethodException
                 | IllegalAccessException
                 | InvocationTargetException
                 | RuntimeException exception) {

            /*
             * Avoid trying every frame/search if JEI internals are incompatible.
             */
            essenceAscendance$indexedRevision =
                    currentRevision;

            if (!essenceAscendance$reflectionWarningLogged) {
                essenceAscendance$reflectionWarningLogged =
                        true;

                EssenceAscendance.LOGGER.warn(
                        "JEI tooltip-search compatibility could not invoke IngredientFilter.rebuildItemFilter().",
                        exception
                );
            }

        } finally {
            essenceAscendance$rebuilding =
                    false;
        }
    }
}
