package com.mistaboom.essence_ascendance.item;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class AscendanceArmorMaterials {

    private static final DeferredRegister<ArmorMaterial>
            ARMOR_MATERIALS =
            DeferredRegister.create(
                    EssenceAscendance.MOD_ID,
                    Registries.ARMOR_MATERIAL
            );


    public static final RegistrySupplier<ArmorMaterial>
            ASCENDANCE =
            ARMOR_MATERIALS.register(
                    "ascendance",
                    AscendanceArmorMaterials::createMaterial
            );


    private AscendanceArmorMaterials() {
    }


    private static ArmorMaterial createMaterial() {

        Map<ArmorItem.Type, Integer> defense =
                new EnumMap<>(
                        ArmorItem.Type.class
                );


        /*
         * IMPORTANT:
         *
         * The Ascendance chassis is player-dependent.
         *
         * Static vanilla ArmorMaterial protection therefore remains
         * zero so it cannot double-dip with the dynamic Armor and
         * Toughness values that will be applied by the progression
         * effect layer.
         */

        for (ArmorItem.Type type :
                ArmorItem.Type.values()) {

            defense.put(
                    type,
                    0
            );
        }


        return new ArmorMaterial(
                defense,

                /*
                 * Enchantment value.
                 *
                 * This is independent of our progression system.
                 */
                15,

                /*
                 * Placeholder equip sound.
                 */
                SoundEvents.ARMOR_EQUIP_NETHERITE,

                /*
                 * No repair ingredient yet.
                 *
                 * Repair/crafting economy should be designed with
                 * the actual armor acquisition system rather than
                 * accidentally inheriting vanilla repair rules.
                 */
                () -> Ingredient.EMPTY,

                /*
                 * Placeholder worn appearance.
                 *
                 * Point at vanilla iron armor until custom
                 * Ascendance textures are created.
                 */
                List.of(
                        new ArmorMaterial.Layer(
                                ResourceLocation.fromNamespaceAndPath(
                                        "minecraft",
                                        "iron"
                                )
                        )
                ),

                /*
                 * Static toughness.
                 *
                 * Dynamic chassis owns this later.
                 */
                0.0F,

                /*
                 * Static knockback resistance.
                 *
                 * The invested Knockback Resistance stat owns this.
                 */
                0.0F
        );
    }


    public static void init() {

        ARMOR_MATERIALS.register();
    }
}