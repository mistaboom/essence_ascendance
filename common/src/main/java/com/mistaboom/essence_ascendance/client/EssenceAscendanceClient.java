package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.item.AscendanceItems;
import net.minecraft.client.renderer.item.ClampedItemPropertyFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.Objects;

public final class EssenceAscendanceClient {

    private static final ResourceLocation PULLING =
            ResourceLocation.fromNamespaceAndPath(
                    "minecraft",
                    "pulling"
            );

    private static final ResourceLocation PULL =
            ResourceLocation.fromNamespaceAndPath(
                    "minecraft",
                    "pull"
            );


    private static boolean initialized =
            false;


    private EssenceAscendanceClient() {
    }


    /*
     * ============================================================
     * CLIENT INITIALIZATION
     * ============================================================
     *
     * Common code defines WHAT client properties we need.
     *
     * Loader-specific code defines HOW those properties are
     * registered.
     *
     * This keeps vanilla-private / loader-specific registration
     * details out of the common module.
     */

    public static void init(
            ItemPropertyRegistrar registrar
    ) {

        Objects.requireNonNull(
                registrar,
                "Item property registrar cannot be null"
        );


        if (initialized) {

            return;
        }


        initialized =
                true;


        /*
         * ========================================================
         * ASCENDANCE BOW - PULLING STATE
         * ========================================================
         */

        registrar.register(
                AscendanceItems
                        .ASCENDANCE_RANGED_WEAPON
                        .get(),

                PULLING,

                (
                        stack,
                        level,
                        entity,
                        seed
                ) -> {

                    if (entity == null) {

                        return 0.0F;
                    }


                    return entity.isUsingItem()
                            && entity.getUseItem() == stack
                            ? 1.0F
                            : 0.0F;
                }
        );


        /*
         * ========================================================
         * ASCENDANCE BOW - DRAW PROGRESS
         * ========================================================
         *
         * 0.0 = just started drawing
         * 1.0 = vanilla full-draw visual state
         *
         * The 20 tick visual baseline will later be tied to the
         * authoritative ranged attack-speed chassis when client
         * progression synchronization is implemented.
         */

        registrar.register(
                AscendanceItems
                        .ASCENDANCE_RANGED_WEAPON
                        .get(),

                PULL,

                (
                        stack,
                        level,
                        entity,
                        seed
                ) -> {

                    if (entity == null
                            || !entity.isUsingItem()
                            || entity.getUseItem() != stack) {

                        return 0.0F;
                    }


                    int usedTicks =
                            stack.getUseDuration(
                                    entity
                            )
                                    - entity.getUseItemRemainingTicks();


                    return Math.min(
                            1.0F,
                            usedTicks
                                    / 20.0F
                    );
                }
        );
    }


    /*
     * ============================================================
     * LOADER REGISTRATION BRIDGE
     * ============================================================
     */

    @FunctionalInterface
    public interface ItemPropertyRegistrar {

        void register(
                Item item,
                ResourceLocation propertyId,
                ClampedItemPropertyFunction property
        );
    }
}