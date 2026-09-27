package com.mistaboom.essence_ascendance.item;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.equipment.EquipmentCatalogStacks;
import dev.architectury.registry.CreativeTabRegistry;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;

import java.util.ArrayList;
import java.util.List;

public final class AscendanceItems {

    private static final int ARMOR_DURABILITY_FACTOR =
            37;


    /*
     * Static Minecraft bootstrap durability.
     *
     * Item.Properties/Tier require a concrete max-damage value at item
     * construction time. This number is therefore implementation scaffolding,
     * not the authoritative tier balance model. EquipmentBaselineService owns
     * the tier/archetype durability target used by later gameplay hooks.
     */
    private static final int WEAPON_DURABILITY =
            2031;


    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(
                    EssenceAscendance.MOD_ID,
                    Registries.ITEM
            );

    public static final RegistrySupplier<AscendanceArchiveItem> ASCENDANCE_ARCHIVE =
            ITEMS.register("ascendance_archive", () -> new AscendanceArchiveItem(new Item.Properties().stacksTo(1)));


    /*
     * ============================================================
     * ASCENDANCE WEAPONS
     * ============================================================
     */

    public static final RegistrySupplier<AscendanceMeleeWeaponItem>
            ASCENDANCE_MELEE_WEAPON =
            ITEMS.register(
                    "ascendance_melee_weapon",
                    () ->
                            new AscendanceMeleeWeaponItem(
                                    AscendanceToolTier.INSTANCE,
                                    new Item.Properties()
                                            .attributes(
                                                    SwordItem.createAttributes(
                                                            AscendanceToolTier.INSTANCE,
                                                            0,
                                                            0.0F
                                                    )
                                            )
                                            .fireResistant()
                            )
            );


    public static final RegistrySupplier<AscendanceRangedWeaponItem>
            ASCENDANCE_RANGED_WEAPON =
            ITEMS.register(
                    "ascendance_ranged_weapon",
                    () ->
                            new AscendanceRangedWeaponItem(
                                    new Item.Properties()
                                            .durability(
                                                    WEAPON_DURABILITY
                                            )
                                            .fireResistant()
                            )
            );


    public static final RegistrySupplier<AscendanceCasterItem>
            ASCENDANCE_CASTER =
            ITEMS.register(
                    "ascendance_caster",
                    () ->
                            new AscendanceCasterItem(
                                    new Item.Properties()
                                            .durability(
                                                    WEAPON_DURABILITY
                                            )
                                            .fireResistant()
                            )
            );


    /*
     * ============================================================
     * ASCENDANCE TOOLS
     * ============================================================
     *
     * These are real vanilla tool subclasses so stripping, path creation,
     * tilling, mineable-tag behavior, enchantment compatibility, and normal
     * tool interactions remain native Minecraft behavior.
     *
     * Static combat attributes below are deliberately neutral (zero item
     * damage contribution and zero attack-speed modifier). The server-side
     * EquipmentAttributeService supplies the authoritative tier/archetype
     * baseline plus applicable invested melee bonuses as transient player
     * attributes. This prevents the constructor values from becoming a second
     * hidden balance source.
     */

    public static final RegistrySupplier<AscendancePickaxeItem>
            ASCENDANCE_PICKAXE =
            ITEMS.register(
                    "ascendance_pickaxe",
                    () ->
                            new AscendancePickaxeItem(
                                    AscendanceToolTier.INSTANCE,
                                    new Item.Properties()
                                            .attributes(
                                                    PickaxeItem.createAttributes(
                                                            AscendanceToolTier.INSTANCE,
                                                            0.0F,
                                                            0.0F
                                                    )
                                            )
                                            .fireResistant()
                            )
            );


    public static final RegistrySupplier<AscendanceAxeItem>
            ASCENDANCE_AXE =
            ITEMS.register(
                    "ascendance_axe",
                    () ->
                            new AscendanceAxeItem(
                                    AscendanceToolTier.INSTANCE,
                                    new Item.Properties()
                                            .attributes(
                                                    AxeItem.createAttributes(
                                                            AscendanceToolTier.INSTANCE,
                                                            0.0F,
                                                            0.0F
                                                    )
                                            )
                                            .fireResistant()
                            )
            );


    public static final RegistrySupplier<AscendanceShovelItem>
            ASCENDANCE_SHOVEL =
            ITEMS.register(
                    "ascendance_shovel",
                    () ->
                            new AscendanceShovelItem(
                                    AscendanceToolTier.INSTANCE,
                                    new Item.Properties()
                                            .attributes(
                                                    ShovelItem.createAttributes(
                                                            AscendanceToolTier.INSTANCE,
                                                            0.0F,
                                                            0.0F
                                                    )
                                            )
                                            .fireResistant()
                            )
            );


    public static final RegistrySupplier<AscendanceHoeItem>
            ASCENDANCE_HOE =
            ITEMS.register(
                    "ascendance_hoe",
                    () ->
                            new AscendanceHoeItem(
                                    AscendanceToolTier.INSTANCE,
                                    new Item.Properties()
                                            .attributes(
                                                    HoeItem.createAttributes(
                                                            AscendanceToolTier.INSTANCE,
                                                            0.0F,
                                                            0.0F
                                                    )
                                            )
                                            .fireResistant()
                            )
            );


    /*
     * ============================================================
     * ASCENDANCE ARMOR
     * ============================================================
     */

    public static final RegistrySupplier<AscendanceArmorItem>
            ASCENDANCE_HELMET =
            registerArmor(
                    "ascendance_helmet",
                    ArmorItem.Type.HELMET,
                    EquipmentSlot.HEAD
            );


    public static final RegistrySupplier<AscendanceArmorItem>
            ASCENDANCE_CHESTPLATE =
            registerArmor(
                    "ascendance_chestplate",
                    ArmorItem.Type.CHESTPLATE,
                    EquipmentSlot.CHEST
            );


    public static final RegistrySupplier<AscendanceArmorItem>
            ASCENDANCE_LEGGINGS =
            registerArmor(
                    "ascendance_leggings",
                    ArmorItem.Type.LEGGINGS,
                    EquipmentSlot.LEGS
            );


    public static final RegistrySupplier<AscendanceArmorItem>
            ASCENDANCE_BOOTS =
            registerArmor(
                    "ascendance_boots",
                    ArmorItem.Type.BOOTS,
                    EquipmentSlot.FEET
            );


    public static final RegistrySupplier<AscendanceShieldItem> ASCENDANCE_SHIELD =
            ITEMS.register("ascendance_shield", () -> new AscendanceShieldItem(
                    new Item.Properties().durability(336).fireResistant()));

    private AscendanceItems() {
    }


    private static RegistrySupplier<AscendanceArmorItem>
    registerArmor(
            String id,
            ArmorItem.Type type,
            EquipmentSlot slot
    ) {

        return ITEMS.register(
                id,
                () ->
                        new AscendanceArmorItem(
                                AscendanceArmorMaterials.ASCENDANCE,
                                type,
                                slot,
                                new Item.Properties()
                                        .durability(
                                                type.getDurability(
                                                        ARMOR_DURABILITY_FACTOR
                                                )
                                        )
                        )
        );
    }


    public static void init() {

        ITEMS.register();
        CreativeTabRegistry.append(CreativeModeTabs.COMBAT, ASCENDANCE_SHIELD);

        CreativeTabRegistry.append(
                CreativeModeTabs.TOOLS_AND_UTILITIES,
                ASCENDANCE_ARCHIVE
        );


        // Inventory-art equipment exposes all six visual tiers. These are
        // ordinary zero-investment stacks: tier data is present where needed,
        // but ownership remains absent until real Infuser progress begins.
        CreativeTabRegistry.modify(
                CreativeTabRegistry.defer(CreativeModeTabs.COMBAT),
                (flags, output, canUseGameMasterBlocks) ->
                        EquipmentCatalogStacks.allTiers(tieredCombatInventoryArtItems())
                                .forEach(output::accept)
        );
        CreativeTabRegistry.modify(
                CreativeTabRegistry.defer(CreativeModeTabs.TOOLS_AND_UTILITIES),
                (flags, output, canUseGameMasterBlocks) ->
                        EquipmentCatalogStacks.allTiers(tieredToolInventoryArtItems())
                                .forEach(output::accept)
        );
    }

    /** Equipment whose authored inventory sprites support all tier tints. */
    public static List<Item> tieredInventoryArtItems() {
        List<Item> items = new ArrayList<>(tieredCombatInventoryArtItems());
        items.addAll(tieredToolInventoryArtItems());
        return List.copyOf(items);
    }

    private static List<Item> tieredCombatInventoryArtItems() {
        return List.of(
                ASCENDANCE_MELEE_WEAPON.get(),
                ASCENDANCE_RANGED_WEAPON.get(),
                ASCENDANCE_CASTER.get(),
                ASCENDANCE_HELMET.get(),
                ASCENDANCE_CHESTPLATE.get(),
                ASCENDANCE_LEGGINGS.get(),
                ASCENDANCE_BOOTS.get()
        );
    }

    private static List<Item> tieredToolInventoryArtItems() {
        return List.of(
                ASCENDANCE_PICKAXE.get(),
                ASCENDANCE_AXE.get(),
                ASCENDANCE_SHOVEL.get(),
                ASCENDANCE_HOE.get()
        );
    }
}
