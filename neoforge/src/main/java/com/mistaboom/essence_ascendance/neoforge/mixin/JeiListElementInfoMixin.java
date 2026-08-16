package com.mistaboom.essence_ascendance.neoforge.mixin;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.client.JeiTooltipSearchTerms;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/*
 * Optional JEI 19.x compatibility.
 *
 * JEI's "$" tooltip search is sourced from:
 *   ListElementInfo#getTooltipStrings(...)
 *
 * Injecting the Essence tooltip words here means we are feeding the exact
 * search-data API JEI consumes instead of hoping a presentation tooltip hook is
 * visible to JEI's indexing path.
 *
 * @Pseudo + string target means JEI remains optional.
 */
@Pseudo
@Mixin(
        targets = "mezz.jei.gui.ingredients.ListElementInfo",
        remap = false
)
public abstract class JeiListElementInfoMixin {

    @Unique
    private static boolean essenceAscendance$reflectionWarningLogged =
            false;

    @Inject(
            method = "getTooltipStrings",
            at = @At("RETURN"),
            cancellable = true,
            require = 0,
            remap = false
    )
    private void essenceAscendance$addTooltipSearchWords(
            CallbackInfoReturnable<Set<String>> callback
    ) {
        ItemStack stack =
                essenceAscendance$getItemStack();

        if (stack.isEmpty()) {
            return;
        }

        Set<String> additions =
                JeiTooltipSearchTerms.forStack(
                        stack
                );

        if (additions.isEmpty()) {
            return;
        }

        Set<String> merged =
                new HashSet<>(
                        callback.getReturnValue()
                );

        merged.addAll(
                additions
        );

        callback.setReturnValue(
                merged
        );
    }

    @Unique
    private ItemStack essenceAscendance$getItemStack() {
        try {
            /*
             * ListElementInfo has public getTypedIngredient().
             */
            Method getTypedIngredient =
                    this.getClass()
                            .getMethod(
                                    "getTypedIngredient"
                            );

            Object typedIngredient =
                    getTypedIngredient.invoke(
                            this
                    );

            if (typedIngredient == null) {
                return ItemStack.EMPTY;
            }

            /*
             * ITypedIngredient has public getItemStack(), returning
             * Optional<ItemStack>.
             */
            Method getItemStack =
                    typedIngredient.getClass()
                            .getMethod(
                                    "getItemStack"
                            );

            Object optionalValue =
                    getItemStack.invoke(
                            typedIngredient
                    );

            if (optionalValue instanceof Optional<?> optional
                    && optional.orElse(
                            null
                    ) instanceof ItemStack stack) {

                return stack;
            }

        } catch (NoSuchMethodException
                 | IllegalAccessException
                 | InvocationTargetException
                 | RuntimeException exception) {

            if (!essenceAscendance$reflectionWarningLogged) {
                essenceAscendance$reflectionWarningLogged =
                        true;

                EssenceAscendance.LOGGER.warn(
                        "JEI tooltip-search compatibility could not read an ItemStack from ListElementInfo.",
                        exception
                );
            }
        }

        return ItemStack.EMPTY;
    }
}
