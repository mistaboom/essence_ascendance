package com.mistaboom.essence_ascendance.equipment;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/*
 * Central ItemStack -> conduit capability registry.
 *
 * Multiple providers may recognize the same item.
 *
 * Provider results are merged using MAXIMUM strength rather than
 * addition.
 *
 * Example:
 *
 * Native weapon:
 *     MELEE_WEAPON = 1.0
 *
 * Future conduit enchantment:
 *     MELEE_WEAPON = 1.0
 *
 * Combined result:
 *     MELEE_WEAPON = 1.0
 *
 * NOT:
 *     MELEE_WEAPON = 2.0
 *
 * This prevents tags, enchantments, and native declarations from
 * accidentally double-dipping.
 */
public final class EquipmentConduitRegistry {

    private static final List<EquipmentConduitProvider> PROVIDERS =
            new ArrayList<>();


    private static boolean initialized =
            false;


    private EquipmentConduitRegistry() {
    }


    /*
     * ============================================================
     * INITIALIZATION
     * ============================================================
     */

    public static void init() {

        if (initialized) {

            return;
        }


        register(
                NativeEquipmentConduitProvider.INSTANCE
        );


        initialized =
                true;
    }


    /*
     * ============================================================
     * PROVIDER REGISTRATION
     * ============================================================
     */

    public static void register(
            EquipmentConduitProvider provider
    ) {

        Objects.requireNonNull(
                provider,
                "Equipment conduit provider cannot be null"
        );


        /*
         * Avoid registering the same provider instance twice.
         */

        if (!PROVIDERS.contains(
                provider
        )) {

            PROVIDERS.add(
                    provider
            );
        }
    }


    public static List<EquipmentConduitProvider> providers() {

        return List.copyOf(
                PROVIDERS
        );
    }


    /*
     * ============================================================
     * ITEM RESOLUTION
     * ============================================================
     */

    public static EquipmentConduitState evaluate(
            ItemStack stack
    ) {

        Objects.requireNonNull(
                stack,
                "Item stack cannot be null"
        );


        /*
         * Defensive initialization.
         *
         * Normal startup explicitly calls init(), but this keeps the
         * registry safe if some integration queries it unusually
         * early.
         */

        if (!initialized) {

            init();
        }


        if (stack.isEmpty()) {

            return EquipmentConduitState.none();
        }


        Map<EquipmentConduitType, Double> merged =
                new EnumMap<>(
                        EquipmentConduitType.class
                );


        for (EquipmentConduitProvider provider :
                PROVIDERS) {

            EquipmentConduitState result =
                    Objects.requireNonNull(
                            provider.evaluate(
                                    stack
                            ),
                            "Equipment conduit provider returned null: "
                                    + provider.getClass().getName()
                    );


            for (EquipmentConduitType conduit :
                    EquipmentConduitType.values()) {

                double strength =
                        result.strength(
                                conduit
                        );


                if (strength <= 0.0) {

                    continue;
                }


                /*
                 * Maximum merge prevents conduit sources from
                 * stacking with themselves.
                 */

                merged.merge(
                        conduit,
                        strength,
                        Math::max
                );
            }
        }


        return new EquipmentConduitState(
                merged
        );
    }
}