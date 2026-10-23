package com.mistaboom.essence_ascendance.item;

//? if <26.1.2
import com.mistaboom.essence_ascendance.EssenceAscendance;
//? if <26.1.2
import dev.architectury.registry.registries.DeferredRegister;
//? if <26.1.2
import dev.architectury.registry.registries.RegistrySupplier;
//? if <26.1.2
import net.minecraft.core.registries.Registries;
//? if <26.1.2
import net.minecraft.resources.ResourceLocation;
//? if <26.1.2
import net.minecraft.sounds.SoundEvents;
//? if <26.1.2
import net.minecraft.world.item.ArmorItem;
//? if <26.1.2
import net.minecraft.world.item.ArmorMaterial;
//? if <26.1.2
import net.minecraft.world.item.crafting.Ingredient;

//? if <26.1.2
import java.util.EnumMap;
//? if <26.1.2
import java.util.List;
//? if <26.1.2
import java.util.Map;

//? if <26.1.2
public final class AscendanceArmorMaterials {

//? if <26.1.2
    private static final DeferredRegister<ArmorMaterial>
//? if <26.1.2
            ARMOR_MATERIALS =
//? if <26.1.2
            DeferredRegister.create(
//? if <26.1.2
                    EssenceAscendance.MOD_ID,
//? if <26.1.2
                    Registries.ARMOR_MATERIAL
//? if <26.1.2
            );


//? if <26.1.2
    public static final RegistrySupplier<ArmorMaterial>
//? if <26.1.2
            ASCENDANCE =
//? if <26.1.2
            ARMOR_MATERIALS.register(
//? if <26.1.2
                    "ascendance",
//? if <26.1.2
                    AscendanceArmorMaterials::createMaterial
//? if <26.1.2
            );


//? if <26.1.2
    private AscendanceArmorMaterials() {
//? if <26.1.2
    }


//? if <26.1.2
    private static ArmorMaterial createMaterial() {

//? if <26.1.2
        Map<ArmorItem.Type, Integer> defense =
//? if <26.1.2
                new EnumMap<>(
//? if <26.1.2
                        ArmorItem.Type.class
//? if <26.1.2
                );


//? if <26.1.2
        /*
//? if <26.1.2
         * IMPORTANT:
//? if <26.1.2
         *
//? if <26.1.2
         * Ascendance physical Armor/Toughness are tier-derived through
//? if <26.1.2
         * EquipmentBaselineService and will be applied by the gameplay-effect
//? if <26.1.2
         * layer. Static ArmorMaterial protection therefore remains zero so the
//? if <26.1.2
         * vanilla material never becomes a second hidden balance source.
//? if <26.1.2
         */

//? if <26.1.2
        for (ArmorItem.Type type :
//? if <26.1.2
                ArmorItem.Type.values()) {

//? if <26.1.2
            defense.put(
//? if <26.1.2
                    type,
//? if <26.1.2
                    0
//? if <26.1.2
            );
//? if <26.1.2
        }


//? if <26.1.2
        return new ArmorMaterial(
//? if <26.1.2
                defense,

//? if <26.1.2
                /*
//? if <26.1.2
                 * Enchantment value.
//? if <26.1.2
                 *
//? if <26.1.2
                 * This is independent of our progression system.
//? if <26.1.2
                 */
//? if <26.1.2
                15,

//? if <26.1.2
                /*
//? if <26.1.2
                 * Placeholder equip sound.
//? if <26.1.2
                 */
//? if <26.1.2
                SoundEvents.ARMOR_EQUIP_NETHERITE,

//? if <26.1.2
                /*
//? if <26.1.2
                 * No repair ingredient yet.
//? if <26.1.2
                 *
//? if <26.1.2
                 * Repair/crafting economy should be designed with
//? if <26.1.2
                 * the actual armor acquisition system rather than
//? if <26.1.2
                 * accidentally inheriting vanilla repair rules.
//? if <26.1.2
                 */
//? if <26.1.2
                () -> Ingredient.EMPTY,

//? if <26.1.2
                /*
//? if <26.1.2
                 * Placeholder worn appearance.
//? if <26.1.2
                 *
//? if <26.1.2
                 * Point at vanilla iron armor until custom
//? if <26.1.2
                 * Ascendance textures are created.
//? if <26.1.2
                 */
//? if <26.1.2
                List.of(
//? if <26.1.2
                        new ArmorMaterial.Layer(
//? if <26.1.2
                                ResourceLocation.fromNamespaceAndPath(
//? if <26.1.2
                                        "minecraft",
//? if <26.1.2
                                        "iron"
//? if <26.1.2
                                )
//? if <26.1.2
                        )
//? if <26.1.2
                ),

//? if <26.1.2
                /*
//? if <26.1.2
                 * Static toughness stays zero; the tier baseline owns it.
//? if <26.1.2
                 */
//? if <26.1.2
                0.0F,

//? if <26.1.2
                /*
//? if <26.1.2
                 * Static knockback resistance.
//? if <26.1.2
                 *
//? if <26.1.2
                 * The invested Knockback Resistance stat owns this.
//? if <26.1.2
                 */
//? if <26.1.2
                0.0F
//? if <26.1.2
        );
//? if <26.1.2
    }


//? if <26.1.2
    public static void init() {

//? if <26.1.2
        ARMOR_MATERIALS.register();
//? if <26.1.2
    }
//? if <26.1.2
}

//? if >=26.1.2
/*public final class AscendanceArmorMaterials { public static final net.minecraft.world.item.equipment.ArmorMaterial ASCENDANCE = new net.minecraft.world.item.equipment.ArmorMaterial(37, java.util.Map.of(net.minecraft.world.item.equipment.ArmorType.HELMET, 0, net.minecraft.world.item.equipment.ArmorType.CHESTPLATE, 0, net.minecraft.world.item.equipment.ArmorType.LEGGINGS, 0, net.minecraft.world.item.equipment.ArmorType.BOOTS, 0, net.minecraft.world.item.equipment.ArmorType.BODY, 0), 15, net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_NETHERITE, 0.0F, 0.0F, net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("essence_ascendance", "no_vanilla_repair")), net.minecraft.world.item.equipment.EquipmentAssets.IRON); private AscendanceArmorMaterials() { } public static void init() { } }*/
