package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.equipment.EquipmentWeaponService;
import com.mistaboom.essence_ascendance.item.AscendanceItems;
import net.minecraft.client.renderer.item.ClampedItemPropertyFunction;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

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

    private static boolean initialized = false;

    private EssenceAscendanceClient() {
    }

    /*
     * ============================================================
     * CLIENT INITIALIZATION
     * ============================================================
     *
     * IMPORTANT:
     * BowItem behavior does NOT automatically copy the vanilla BOW item's
     * model-property registrations to a custom BowItem.  The JSON override
     * predicates only work after minecraft:pulling and minecraft:pull are
     * explicitly registered for ASCENDANCE_RANGED_WEAPON.
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

        Item rangedWeapon = AscendanceItems
                .ASCENDANCE_RANGED_WEAPON
                .get();

        /*
         * minecraft:pulling
         * -----------------
         * Controls whether ascendance_ranged_weapon.json switches from its
         * resting model into pulling_0.
         */
        registrar.register(
                rangedWeapon,
                PULLING,
                (stack, level, entity, seed) -> {
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
         * minecraft:pull
         * --------------
         * Controls progression through pulling_0 -> pulling_1 -> pulling_2.
         *
         * The server continuously synchronizes the resolved Ascendance full
         * draw time onto the concrete bow ItemStack.  Using that same value
         * here means the rendered bow reaches full draw at exactly the same
         * time gameplay does instead of always using vanilla's 20 ticks.
         */
        registrar.register(
                rangedWeapon,
                PULL,
                (stack, level, entity, seed) -> {
                    if (entity == null
                            || !entity.isUsingItem()
                            || entity.getUseItem() != stack) {
                        return 0.0F;
                    }

                    int usedTicks =
                            stack.getUseDuration(entity)
                                    - entity.getUseItemRemainingTicks();

                    int fullDrawTicks =
                            EquipmentWeaponService
                                    .syncedRangedFullDrawTicks(stack);

                    return Math.min(
                            1.0F,
                            usedTicks / (float) Math.max(1, fullDrawTicks)
                    );
                }
        );

        /*
         * Fail loudly during development if a loader bridge silently failed
         * to attach the predicates.  This prevents us from debugging JSON or
         * projectile code when the real problem is client registration.
         */
        ItemStack probe = new ItemStack(rangedWeapon);

        if (ItemProperties.getProperty(probe, PULLING) == null
                || ItemProperties.getProperty(probe, PULL) == null) {
            throw new IllegalStateException(
                    "Essence Ascendance failed to register bow model properties "
                            + "minecraft:pulling and minecraft:pull for "
                            + rangedWeapon
            );
        }

        initialized = true;

        EssenceAscendance.LOGGER.info(
                "Registered Ascendance bow model properties: minecraft:pulling and minecraft:pull"
        );
    }

    @FunctionalInterface
    public interface ItemPropertyRegistrar {
        void register(
                Item item,
                ResourceLocation propertyId,
                ClampedItemPropertyFunction property
        );
    }
}
