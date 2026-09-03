package com.mistaboom.essence_ascendance.infuser;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import dev.architectury.registry.CreativeTabRegistry;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;

/** Registration holder for the Latent/Essentium material family and Infuser. */
public final class EssenceInfuserContent {

    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(EssenceAscendance.MOD_ID, Registries.BLOCK);
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(EssenceAscendance.MOD_ID, Registries.ITEM);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(EssenceAscendance.MOD_ID, Registries.BLOCK_ENTITY_TYPE);
    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(EssenceAscendance.MOD_ID, Registries.MENU);

    public static final RegistrySupplier<Block> LATENT_ORE = BLOCKS.register(
            "latent_ore",
            () -> new Block(
                    BlockBehaviour.Properties.of()
                            .requiresCorrectToolForDrops()
                            .strength(3.0F)
            )
    );
    public static final RegistrySupplier<Block> DEEPSLATE_LATENT_ORE = BLOCKS.register(
            "deepslate_latent_ore",
            () -> new Block(
                    BlockBehaviour.Properties.of()
                            .requiresCorrectToolForDrops()
                            .strength(4.5F)
            )
    );
    public static final RegistrySupplier<Block> NETHERRACK_LATENT_ORE = BLOCKS.register(
            "netherrack_latent_ore",
            () -> new Block(
                    BlockBehaviour.Properties.of()
                            .requiresCorrectToolForDrops()
                            .strength(3.0F)
            )
    );
    public static final RegistrySupplier<Block> END_STONE_LATENT_ORE = BLOCKS.register(
            "end_stone_latent_ore",
            () -> new Block(
                    BlockBehaviour.Properties.of()
                            .requiresCorrectToolForDrops()
                            .strength(3.0F)
            )
    );
    public static final RegistrySupplier<Block> RAW_LATENT_ORE_BLOCK = BLOCKS.register(
            "raw_latent_ore_block",
            () -> new Block(
                    BlockBehaviour.Properties.of()
                            .requiresCorrectToolForDrops()
                            .strength(5.0F)
            )
    );
    public static final RegistrySupplier<Block> LATENT_BLOCK = BLOCKS.register(
            "latent_block",
            () -> new Block(
                    BlockBehaviour.Properties.of()
                            .requiresCorrectToolForDrops()
                            .strength(5.0F)
            )
    );
    public static final RegistrySupplier<EssenceInfuserBlock> ESSENCE_INFUSER = BLOCKS.register(
            "essence_infuser",
            () -> new EssenceInfuserBlock(
                    BlockBehaviour.Properties.of()
                            .requiresCorrectToolForDrops()
                            .strength(3.5F)
                            .pushReaction(PushReaction.BLOCK)
            )
    );

    public static final RegistrySupplier<Item> LATENT_ORE_ITEM = blockItem(
            "latent_ore",
            LATENT_ORE
    );
    public static final RegistrySupplier<Item> DEEPSLATE_LATENT_ORE_ITEM = blockItem(
            "deepslate_latent_ore",
            DEEPSLATE_LATENT_ORE
    );
    public static final RegistrySupplier<Item> NETHERRACK_LATENT_ORE_ITEM = blockItem(
            "netherrack_latent_ore",
            NETHERRACK_LATENT_ORE
    );
    public static final RegistrySupplier<Item> END_STONE_LATENT_ORE_ITEM = blockItem(
            "end_stone_latent_ore",
            END_STONE_LATENT_ORE
    );
    public static final RegistrySupplier<Item> RAW_LATENT_ORE = ITEMS.register(
            "raw_latent_ore",
            () -> new Item(new Item.Properties())
    );
    public static final RegistrySupplier<Item> RAW_LATENT_ORE_BLOCK_ITEM = blockItem(
            "raw_latent_ore_block",
            RAW_LATENT_ORE_BLOCK
    );
    public static final RegistrySupplier<Item> LATENT_INGOT = ITEMS.register(
            "latent_ingot",
            () -> new Item(new Item.Properties())
    );
    public static final RegistrySupplier<Item> LATENT_BLOCK_ITEM = blockItem(
            "latent_block",
            LATENT_BLOCK
    );
    public static final RegistrySupplier<Item> LATENT_FOCUS = ITEMS.register(
            "latent_focus",
            () -> new LatentFocusItem(new Item.Properties())
    );
    public static final RegistrySupplier<Item> ESSENTIUM_INGOT = ITEMS.register(
            "essentium_ingot",
            () -> new EssentiumItem(
                    EssentiumItem.CarrierForm.INGOT,
                    new Item.Properties()
            )
    );
    public static final RegistrySupplier<Item> ESSENTIUM_BLOCK = ITEMS.register(
            "essentium_block",
            () -> new EssentiumItem(
                    EssentiumItem.CarrierForm.BLOCK,
                    new Item.Properties()
            )
    );
    public static final RegistrySupplier<Item> ESSENCE_INFUSER_ITEM = blockItem(
            "essence_infuser",
            ESSENCE_INFUSER
    );

    public static final RegistrySupplier<BlockEntityType<EssenceInfuserBlockEntity>> ESSENCE_INFUSER_BLOCK_ENTITY =
            BLOCK_ENTITIES.register(
                    "essence_infuser",
                    () -> BlockEntityType.Builder.of(
                            EssenceInfuserBlockEntity::new,
                            ESSENCE_INFUSER.get()
                    ).build(null)
            );

    public static final RegistrySupplier<MenuType<EssenceInfuserMenu>> ESSENCE_INFUSER_MENU =
            MENUS.register(
                    "essence_infuser",
                    () -> new MenuType<>(
                            EssenceInfuserMenu::new,
                            FeatureFlags.DEFAULT_FLAGS
                    )
            );

    private static boolean initialized = false;

    private EssenceInfuserContent() {
    }

    private static RegistrySupplier<Item> blockItem(
            String id,
            RegistrySupplier<? extends Block> block
    ) {
        return ITEMS.register(
                id,
                () -> new BlockItem(block.get(), new Item.Properties())
        );
    }

    public static void init() {
        if (initialized) {
            return;
        }

        BLOCKS.register();
        ITEMS.register();
        BLOCK_ENTITIES.register();
        MENUS.register();

        CreativeTabRegistry.append(
                CreativeModeTabs.NATURAL_BLOCKS,
                LATENT_ORE_ITEM,
                DEEPSLATE_LATENT_ORE_ITEM,
                NETHERRACK_LATENT_ORE_ITEM,
                END_STONE_LATENT_ORE_ITEM
        );
        CreativeTabRegistry.append(
                CreativeModeTabs.INGREDIENTS,
                RAW_LATENT_ORE,
                LATENT_INGOT,
                LATENT_FOCUS,
                ESSENTIUM_INGOT,
                ESSENTIUM_BLOCK
        );
        CreativeTabRegistry.append(
                CreativeModeTabs.BUILDING_BLOCKS,
                RAW_LATENT_ORE_BLOCK_ITEM,
                LATENT_BLOCK_ITEM
        );
        CreativeTabRegistry.append(
                CreativeModeTabs.FUNCTIONAL_BLOCKS,
                ESSENCE_INFUSER_ITEM
        );

        initialized = true;
    }
}
