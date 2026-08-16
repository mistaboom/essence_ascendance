package com.mistaboom.essence_ascendance.crucible;

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

public final class EssenceCrucibleContent {

    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(EssenceAscendance.MOD_ID, Registries.BLOCK);

    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(EssenceAscendance.MOD_ID, Registries.ITEM);

    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(EssenceAscendance.MOD_ID, Registries.BLOCK_ENTITY_TYPE);

    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(EssenceAscendance.MOD_ID, Registries.MENU);

    public static final RegistrySupplier<EssenceCrucibleBlock> ESSENCE_CRUCIBLE =
            BLOCKS.register(
                    "essence_crucible",
                    () -> new EssenceCrucibleBlock(
                            BlockBehaviour.Properties.of()
                                    .requiresCorrectToolForDrops()
                                    .strength(3.5F)
                                    .pushReaction(PushReaction.BLOCK)
                    )
            );

    public static final RegistrySupplier<Item> ESSENCE_CRUCIBLE_ITEM =
            ITEMS.register(
                    "essence_crucible",
                    () -> new BlockItem(
                            ESSENCE_CRUCIBLE.get(),
                            new Item.Properties()
                    )
            );

    public static final RegistrySupplier<BlockEntityType<EssenceCrucibleBlockEntity>> ESSENCE_CRUCIBLE_BLOCK_ENTITY =
            BLOCK_ENTITIES.register(
                    "essence_crucible",
                    () -> BlockEntityType.Builder.of(
                            EssenceCrucibleBlockEntity::new,
                            ESSENCE_CRUCIBLE.get()
                    ).build(null)
            );

    public static final RegistrySupplier<MenuType<EssenceCrucibleMenu>> ESSENCE_CRUCIBLE_MENU =
            MENUS.register(
                    "essence_crucible",
                    () -> new MenuType<>(
                            EssenceCrucibleMenu::new,
                            FeatureFlags.DEFAULT_FLAGS
                    )
            );

    private static boolean initialized = false;

    private EssenceCrucibleContent() {
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
                CreativeModeTabs.FUNCTIONAL_BLOCKS,
                ESSENCE_CRUCIBLE_ITEM
        );

        initialized = true;
    }
}
