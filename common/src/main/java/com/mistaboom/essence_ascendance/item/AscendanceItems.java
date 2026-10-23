package com.mistaboom.essence_ascendance.item;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.equipment.EquipmentCatalogStacks;
import com.mistaboom.essence_ascendance.registration.ContentRegistrationCompat;
import dev.architectury.registry.CreativeTabRegistry;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EquipmentSlot;
//? if <26.1.2
import net.minecraft.world.item.ArmorItem;
//? if >=26.1.2
/*import net.minecraft.world.item.equipment.ArmorType;*/
import net.minecraft.world.item.CreativeModeTabs;
//? if <26.1.2
import net.minecraft.world.item.AxeItem;
//? if <26.1.2
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
//? if <26.1.2
import net.minecraft.world.item.PickaxeItem;
//? if <26.1.2
import net.minecraft.world.item.ShovelItem;
//? if <26.1.2
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
            ITEMS.register("ascendance_archive", () -> new AscendanceArchiveItem(
                    ContentRegistrationCompat.item("ascendance_archive", new Item.Properties().stacksTo(1))));


    /*
     * ============================================================
     * ASCENDANCE WEAPONS
     * ============================================================
     */

    public static final RegistrySupplier<AscendanceGlaiveItem> ASCENDANCE_GLAIVE =
            ITEMS.register("ascendance_glaive", () -> new AscendanceGlaiveItem(
                    glaiveProperties()));

    public static final RegistrySupplier<AscendanceSwordItem>
            ASCENDANCE_SWORD =
            ITEMS.register(
                    "ascendance_sword",
                    () ->
                            new AscendanceSwordItem(
                                    AscendanceToolTier.INSTANCE,
                                    swordProperties()
                            )
            );


    public static final RegistrySupplier<AscendanceRangedWeaponItem>
            ASCENDANCE_RANGED_WEAPON =
            ITEMS.register(
                    "ascendance_ranged_weapon",
                    () ->
                            new AscendanceRangedWeaponItem(
                                    ContentRegistrationCompat.item("ascendance_ranged_weapon", new Item.Properties()
                                            .durability(
                                                    WEAPON_DURABILITY
                                            )
                                            .fireResistant())
                            )
            );


    public static final RegistrySupplier<AscendanceCasterItem>
            ASCENDANCE_CASTER =
            ITEMS.register(
                    "ascendance_caster",
                    () ->
                            new AscendanceCasterItem(
                                    casterProperties()
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
                                    pickaxeProperties()
                            )
            );


    public static final RegistrySupplier<AscendanceAxeItem>
            ASCENDANCE_AXE =
            ITEMS.register(
                    "ascendance_axe",
                    () ->
                            new AscendanceAxeItem(
                                    AscendanceToolTier.INSTANCE,
                                    axeProperties()
                            )
            );


    public static final RegistrySupplier<AscendanceShovelItem>
            ASCENDANCE_SHOVEL =
            ITEMS.register(
                    "ascendance_shovel",
                    () ->
                            new AscendanceShovelItem(
                                    AscendanceToolTier.INSTANCE,
                                    shovelProperties()
                            )
            );


    public static final RegistrySupplier<AscendanceHoeItem>
            ASCENDANCE_HOE =
            ITEMS.register(
                    "ascendance_hoe",
                    () ->
                            new AscendanceHoeItem(
                                    AscendanceToolTier.INSTANCE,
                                    hoeProperties()
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
                    //? if >=26.1.2
                    /*ArmorType.HELMET,*/
                    //? if <26.1.2
                    ArmorItem.Type.HELMET,
                    EquipmentSlot.HEAD
            );


    public static final RegistrySupplier<AscendanceArmorItem>
            ASCENDANCE_CHESTPLATE =
            registerArmor(
                    "ascendance_chestplate",
                    //? if >=26.1.2
                    /*ArmorType.CHESTPLATE,*/
                    //? if <26.1.2
                    ArmorItem.Type.CHESTPLATE,
                    EquipmentSlot.CHEST
            );


    public static final RegistrySupplier<AscendanceArmorItem>
            ASCENDANCE_LEGGINGS =
            registerArmor(
                    "ascendance_leggings",
                    //? if >=26.1.2
                    /*ArmorType.LEGGINGS,*/
                    //? if <26.1.2
                    ArmorItem.Type.LEGGINGS,
                    EquipmentSlot.LEGS
            );


    public static final RegistrySupplier<AscendanceArmorItem>
            ASCENDANCE_BOOTS =
            registerArmor(
                    "ascendance_boots",
                    //? if >=26.1.2
                    /*ArmorType.BOOTS,*/
                    //? if <26.1.2
                    ArmorItem.Type.BOOTS,
                    EquipmentSlot.FEET
            );


    public static final RegistrySupplier<AscendanceShieldItem> ASCENDANCE_SHIELD =
            ITEMS.register("ascendance_shield", () -> new AscendanceShieldItem(
                    shieldProperties()));

    public static final RegistrySupplier<AscendanceFishingRodItem> ASCENDANCE_FISHING_ROD =
            ITEMS.register("ascendance_fishing_rod", () -> new AscendanceFishingRodItem(
                    ContentRegistrationCompat.item("ascendance_fishing_rod", new Item.Properties().durability(64))));

    private static Item.Properties swordProperties() {
        //? if >=26.1.2
        /*return ContentRegistrationCompat.item("ascendance_sword", new Item.Properties().sword(AscendanceToolTier.INSTANCE, 0.0F, 0.0F).fireResistant());*/
        //? if <26.1.2
        return ContentRegistrationCompat.item("ascendance_sword", new Item.Properties().attributes(SwordItem.createAttributes(AscendanceToolTier.INSTANCE, 0, 0.0F)).fireResistant());
    }

    private static Item.Properties pickaxeProperties() {
        //? if >=26.1.2
        /*return ContentRegistrationCompat.item("ascendance_pickaxe", new Item.Properties().pickaxe(AscendanceToolTier.INSTANCE, 0.0F, 0.0F).fireResistant());*/
        //? if <26.1.2
        return ContentRegistrationCompat.item("ascendance_pickaxe", new Item.Properties().attributes(PickaxeItem.createAttributes(AscendanceToolTier.INSTANCE, 0.0F, 0.0F)).fireResistant());
    }

    private static Item.Properties axeProperties() {
        //? if >=26.1.2
        /*return ContentRegistrationCompat.item("ascendance_axe", new Item.Properties().axe(AscendanceToolTier.INSTANCE, 0.0F, 0.0F).fireResistant());*/
        //? if <26.1.2
        return ContentRegistrationCompat.item("ascendance_axe", new Item.Properties().attributes(AxeItem.createAttributes(AscendanceToolTier.INSTANCE, 0.0F, 0.0F)).fireResistant());
    }

    private static Item.Properties shovelProperties() {
        //? if >=26.1.2
        /*return ContentRegistrationCompat.item("ascendance_shovel", new Item.Properties().shovel(AscendanceToolTier.INSTANCE, 0.0F, 0.0F).fireResistant());*/
        //? if <26.1.2
        return ContentRegistrationCompat.item("ascendance_shovel", new Item.Properties().attributes(ShovelItem.createAttributes(AscendanceToolTier.INSTANCE, 0.0F, 0.0F)).fireResistant());
    }

    private static Item.Properties hoeProperties() {
        //? if >=26.1.2
        /*return ContentRegistrationCompat.item("ascendance_hoe", new Item.Properties().hoe(AscendanceToolTier.INSTANCE, 0.0F, 0.0F).fireResistant());*/
        //? if <26.1.2
        return ContentRegistrationCompat.item("ascendance_hoe", new Item.Properties().attributes(HoeItem.createAttributes(AscendanceToolTier.INSTANCE, 0.0F, 0.0F)).fireResistant());
    }

    private AscendanceItems() {
    }

    private static Item.Properties glaiveProperties() {
        //? if >=26.1.2
        /*return ContentRegistrationCompat.item("ascendance_glaive", new Item.Properties().durability(WEAPON_DURABILITY).enchantable(15).fireResistant());*/
        //? if <26.1.2
        return ContentRegistrationCompat.item("ascendance_glaive", new Item.Properties().durability(WEAPON_DURABILITY).fireResistant());
    }

    private static Item.Properties casterProperties() {
        //? if >=26.1.2
        /*return ContentRegistrationCompat.item("ascendance_caster", new Item.Properties().durability(WEAPON_DURABILITY).enchantable(15).fireResistant());*/
        //? if <26.1.2
        return ContentRegistrationCompat.item("ascendance_caster", new Item.Properties().durability(WEAPON_DURABILITY).fireResistant());
    }

    private static Item.Properties shieldProperties() {
        //? if >=26.1.2 {
        /*return ContentRegistrationCompat.item("ascendance_shield", new Item.Properties()
                .durability(336).fireResistant().enchantable(15)
                .equippableUnswappable(EquipmentSlot.OFFHAND)
                .repairable(net.minecraft.tags.ItemTags.WOODEN_TOOL_MATERIALS)
                .component(net.minecraft.core.component.DataComponents.BANNER_PATTERNS,
                        net.minecraft.world.level.block.entity.BannerPatternLayers.EMPTY)
                .delayedComponent(net.minecraft.core.component.DataComponents.BLOCKS_ATTACKS,
                        lookup -> new net.minecraft.world.item.component.BlocksAttacks(
                                0.25F, 1.0F,
                                java.util.List.of(new net.minecraft.world.item.component.BlocksAttacks.DamageReduction(
                                        90.0F, java.util.Optional.empty(), 0.0F, 1.0F)),
                                new net.minecraft.world.item.component.BlocksAttacks.ItemDamageFunction(
                                        3.0F, 1.0F, 1.0F),
                                java.util.Optional.of(lookup.getOrThrow(net.minecraft.tags.DamageTypeTags.BYPASSES_SHIELD)),
                                java.util.Optional.of(net.minecraft.sounds.SoundEvents.SHIELD_BLOCK),
                                java.util.Optional.of(net.minecraft.sounds.SoundEvents.SHIELD_BREAK)))
                .component(net.minecraft.core.component.DataComponents.BREAK_SOUND,
                        net.minecraft.sounds.SoundEvents.SHIELD_BREAK));
        *///? }
        //? if <26.1.2
        return ContentRegistrationCompat.item("ascendance_shield", new Item.Properties().durability(336).fireResistant());
    }


    private static RegistrySupplier<AscendanceArmorItem>
    registerArmor(
            String id,
            //? if >=26.1.2
            /*ArmorType type,*/
            //? if <26.1.2
            ArmorItem.Type type,
            EquipmentSlot slot
    ) {
        //? if >=26.1.2
        /*return ITEMS.register(id, () -> new AscendanceArmorItem(AscendanceArmorMaterials.ASCENDANCE, type, slot, ContentRegistrationCompat.item(id, new Item.Properties().humanoidArmor(AscendanceArmorMaterials.ASCENDANCE, type))));*/
        //? if <26.1.2
        return ITEMS.register(id, () -> new AscendanceArmorItem(AscendanceArmorMaterials.ASCENDANCE, type, slot, ContentRegistrationCompat.item(id, new Item.Properties().durability(type.getDurability(ARMOR_DURABILITY_FACTOR)))));
    }


    public static void init() {

        ITEMS.register();

        CreativeTabRegistry.append(
                CreativeModeTabs.TOOLS_AND_UTILITIES,
                ASCENDANCE_ARCHIVE
        );


        // Equipment exposes all six tiers. These are
        // ordinary zero-investment stacks: tier data is present where needed,
        // but ownership remains absent until real Infuser progress begins.
        CreativeVariantRegistry.register(
                CreativeModeTabs.COMBAT,
                () -> EquipmentCatalogStacks.allTiers(tieredCombatItems())
        );
        CreativeVariantRegistry.register(
                CreativeModeTabs.TOOLS_AND_UTILITIES,
                () -> EquipmentCatalogStacks.allTiers(tieredToolInventoryArtItems())
        );
    }

    /** Equipment whose authored inventory sprites support all tier tints. */
    public static List<Item> tieredInventoryArtItems() {
        List<Item> items = new ArrayList<>(tieredCombatInventoryArtItems());
        items.addAll(tieredToolInventoryArtItems());
        return List.copyOf(items);
    }

    /** Full browsing catalog, including equipment with a custom renderer. */
    public static List<Item> tieredCatalogItems() {
        List<Item> items = new ArrayList<>(tieredCombatItems());
        items.addAll(tieredToolInventoryArtItems());
        return List.copyOf(items);
    }

    private static List<Item> tieredCombatItems() {
        List<Item> items = new ArrayList<>(tieredCombatInventoryArtItems());
        items.add(ASCENDANCE_SHIELD.get());
        return List.copyOf(items);
    }

    private static List<Item> tieredCombatInventoryArtItems() {
        return List.of(
                ASCENDANCE_SWORD.get(),
                ASCENDANCE_GLAIVE.get(),
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
                ASCENDANCE_HOE.get(),
                ASCENDANCE_FISHING_ROD.get()
        );
    }
}
