package com.mistaboom.essence_ascendance.nexus;

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
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;

/** Registration holder for the stateless Ascendance Nexus interface block. */
public final class AscendanceNexusContent {

    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(EssenceAscendance.MOD_ID, Registries.BLOCK);

    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(EssenceAscendance.MOD_ID, Registries.ITEM);

    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(EssenceAscendance.MOD_ID, Registries.MENU);

    public static final RegistrySupplier<AscendanceNexusBlock> ASCENDANCE_NEXUS =
            BLOCKS.register(
                    "ascendance_nexus",
                    () -> new AscendanceNexusBlock(
                            BlockBehaviour.Properties.of()
                                    .requiresCorrectToolForDrops()
                                    .strength(3.5F)
                                    .pushReaction(PushReaction.BLOCK)
                    )
            );

    public static final RegistrySupplier<Item> ASCENDANCE_NEXUS_ITEM =
            ITEMS.register(
                    "ascendance_nexus",
                    () -> new BlockItem(
                            ASCENDANCE_NEXUS.get(),
                            new Item.Properties()
                    )
            );

    public static final RegistrySupplier<MenuType<AscendanceNexusMenu>> ASCENDANCE_NEXUS_MENU =
            MENUS.register(
                    "ascendance_nexus",
                    () -> new MenuType<>(
                            AscendanceNexusMenu::new,
                            FeatureFlags.DEFAULT_FLAGS
                    )
            );

    private static boolean initialized = false;

    private AscendanceNexusContent() {
    }

    public static void init() {
        if (initialized) {
            return;
        }

        BLOCKS.register();
        ITEMS.register();
        MENUS.register();

        CreativeTabRegistry.append(
                CreativeModeTabs.FUNCTIONAL_BLOCKS,
                ASCENDANCE_NEXUS_ITEM
        );

        initialized = true;
    }
}
