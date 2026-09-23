package com.mistaboom.essence_ascendance.infuser;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import dev.architectury.registry.CreativeTabRegistry;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
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
    private static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(EssenceAscendance.MOD_ID, Registries.RECIPE_SERIALIZER);

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
    public static final RegistrySupplier<EssentiumBlock> ESSENTIUM_BLOCK_BLOCK = BLOCKS.register(
            "essentium_block",
            () -> new EssentiumBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(net.minecraft.world.level.material.MapColor.COLOR_GRAY)
                            .requiresCorrectToolForDrops()
                            .strength(5.0F)
            )
    );
    public static final RegistrySupplier<RotatedPillarBlock> CHANNELSTONE = BLOCKS.register(
            "channelstone",
            () -> new RotatedPillarBlock(
                    BlockBehaviour.Properties.of()
                            .requiresCorrectToolForDrops()
                            .strength(3.5F, 6.0F)
            )
    );
    public static final RegistrySupplier<EssenceInfuserBlock> ESSENCE_INFUSER = BLOCKS.register(
            "essence_infuser",
            () -> new EssenceInfuserBlock(
                    BlockBehaviour.Properties.of()
                            .requiresCorrectToolForDrops()
                            .strength(3.5F)
                            .lightLevel(state -> state.getValue(EssenceInfuserBlock.FOCUS_LIT) ? 9 : 0)
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
    public static final RegistrySupplier<Item> LATENT_NUGGET = ITEMS.register(
            "latent_nugget",
            () -> new Item(new Item.Properties())
    );
    public static final RegistrySupplier<Item> LATENT_INGOT = ITEMS.register(
            "latent_ingot",
            () -> new Item(new Item.Properties())
    );
    public static final RegistrySupplier<Item> LATENT_BLOCK_ITEM = blockItem(
            "latent_block",
            LATENT_BLOCK
    );
    public static final RegistrySupplier<Item> CHANNELSTONE_ITEM = blockItem(
            "channelstone",
            CHANNELSTONE
    );
    public static final RegistrySupplier<Item> ASCENDANCE_MATRIX = ITEMS.register(
            "ascendance_matrix",
            () -> new Item(new Item.Properties())
    );
    public static final RegistrySupplier<Item> ESSENTIUM_NUGGET = ITEMS.register(
            "essentium_nugget",
            () -> new EssentiumItem(
                    EssentiumItem.CarrierForm.NUGGET,
                    new Item.Properties()
            )
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

    public static final RegistrySupplier<BlockEntityType<EssentiumBlockEntity>> ESSENTIUM_BLOCK_BLOCK_ENTITY =
            BLOCK_ENTITIES.register(
                    "essentium_block",
                    () -> BlockEntityType.Builder.of(
                            EssentiumBlockEntity::new,
                            ESSENTIUM_BLOCK_BLOCK.get()
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


    public static final RegistrySupplier<RecipeSerializer<EssentiumNuggetCompactingRecipe>> ESSENTIUM_NUGGET_COMPACTING_SERIALIZER =
            RECIPE_SERIALIZERS.register(
                    "essentium_nugget_compacting",
                    () -> new SimpleCraftingRecipeSerializer<>(EssentiumNuggetCompactingRecipe::new)
            );

    public static final RegistrySupplier<RecipeSerializer<EssentiumNuggetUncompactingRecipe>> ESSENTIUM_NUGGET_UNCOMPACTING_SERIALIZER =
            RECIPE_SERIALIZERS.register(
                    "essentium_nugget_uncompacting",
                    () -> new SimpleCraftingRecipeSerializer<>(EssentiumNuggetUncompactingRecipe::new)
            );

    public static final RegistrySupplier<RecipeSerializer<EssentiumBlockCompactingRecipe>> ESSENTIUM_BLOCK_COMPACTING_SERIALIZER =
            RECIPE_SERIALIZERS.register(
                    "essentium_block_compacting",
                    () -> new SimpleCraftingRecipeSerializer<>(EssentiumBlockCompactingRecipe::new)
            );

    public static final RegistrySupplier<RecipeSerializer<EssentiumBlockUncompactingRecipe>> ESSENTIUM_BLOCK_UNCOMPACTING_SERIALIZER =
            RECIPE_SERIALIZERS.register(
                    "essentium_block_uncompacting",
                    () -> new SimpleCraftingRecipeSerializer<>(EssentiumBlockUncompactingRecipe::new)
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
        RECIPE_SERIALIZERS.register();

        CreativeTabRegistry.append(
                CreativeModeTabs.NATURAL_BLOCKS,
                LATENT_ORE_ITEM,
                DEEPSLATE_LATENT_ORE_ITEM,
                NETHERRACK_LATENT_ORE_ITEM,
                END_STONE_LATENT_ORE_ITEM,
                RAW_LATENT_ORE,
                RAW_LATENT_ORE_BLOCK_ITEM
        );
        CreativeTabRegistry.append(
                CreativeModeTabs.INGREDIENTS,
                ASCENDANCE_MATRIX,
                LATENT_NUGGET,
                LATENT_INGOT,
                LATENT_BLOCK_ITEM
        );
        // Resolve full carrier stacks when the tab is rebuilt, not during
        // registry initialization. Empty/uninfused carriers stay hidden.
        CreativeTabRegistry.modify(
                CreativeTabRegistry.defer(CreativeModeTabs.INGREDIENTS),
                (flags, output, canUseGameMasterBlocks) -> {
                    for (Item item : new Item[]{
                            ESSENTIUM_NUGGET.get(),
                            ESSENTIUM_INGOT.get(),
                            ESSENTIUM_BLOCK.get()
                    }) {
                        EssentiumCarrierData.createFullVariants((EssentiumItem) item, 1)
                                .forEach(output::accept);
                    }
                }
        );
        CreativeTabRegistry.append(
                CreativeModeTabs.BUILDING_BLOCKS,
                CHANNELSTONE_ITEM
        );
        CreativeTabRegistry.append(
                CreativeModeTabs.FUNCTIONAL_BLOCKS,
                ESSENCE_INFUSER_ITEM
        );

        initialized = true;
    }
}
