package com.mistaboom.essence_ascendance.fabric.mixin;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.client.JeiTooltipSearchRefreshBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
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
    private boolean essenceAscendance$rebuilding =
            false;

    @Unique
    private static boolean essenceAscendance$reflectionWarningLogged =
            false;

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
                    "Rebuilt JEI search index for Essence tooltip revision {}",
                    currentRevision
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
