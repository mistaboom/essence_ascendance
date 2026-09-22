package com.mistaboom.essence_ascendance.pylon;

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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;

public final class EssencePylonContent {

    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(EssenceAscendance.MOD_ID, Registries.BLOCK);

    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(EssenceAscendance.MOD_ID, Registries.ITEM);

    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(EssenceAscendance.MOD_ID, Registries.BLOCK_ENTITY_TYPE);

    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(EssenceAscendance.MOD_ID, Registries.MENU);

    public static final RegistrySupplier<EssencePylonBlock> ESSENCE_PYLON =
            BLOCKS.register(
                    "essence_pylon",
                    () -> new EssencePylonBlock(
                            BlockBehaviour.Properties.of()
                                    .requiresCorrectToolForDrops()
                                    .strength(3.5F)
                                    .lightLevel(state -> state.getValue(EssencePylonBlock.FOCUS_LIT) ? 9 : 0)
                                    .pushReaction(PushReaction.BLOCK)
                    )
            );

    public static final RegistrySupplier<Item> ESSENCE_PYLON_ITEM =
            ITEMS.register(
                    "essence_pylon",
                    () -> new BlockItem(
                            ESSENCE_PYLON.get(),
                            new Item.Properties()
                    )
            );

    public static final RegistrySupplier<Item> ESSENCE_FOCUS =
            ITEMS.register(
                    "essence_focus",
                    () -> new EssenceFocusItem(new Item.Properties())
            );

    public static final RegistrySupplier<BlockEntityType<EssencePylonBlockEntity>> ESSENCE_PYLON_BLOCK_ENTITY =
            BLOCK_ENTITIES.register(
                    "essence_pylon",
                    () -> BlockEntityType.Builder.of(
                            EssencePylonBlockEntity::new,
                            ESSENCE_PYLON.get()
                    ).build(null)
            );

    public static final RegistrySupplier<MenuType<EssencePylonMenu>> ESSENCE_PYLON_MENU =
            MENUS.register(
                    "essence_pylon",
                    () -> new MenuType<>(
                            EssencePylonMenu::new,
                            FeatureFlags.DEFAULT_FLAGS
                    )
            );

    private static boolean initialized = false;

    private EssencePylonContent() {
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
                ESSENCE_PYLON_ITEM,
                ESSENCE_FOCUS
        );

        initialized = true;
    }

    /** Completed upgrade tier; null also describes an installed Latent Focus. */
    public static EssenceFocusTier focusTier(ItemStack stack) {
        return rawFocusTier(stack);
    }

    /** Completed tier identity used when an Essence Focus itself is the Infuser workpiece. */
    public static EssenceFocusTier rawFocusTier(ItemStack stack) {
        return EssenceFocusData.tier(stack);
    }

    public static EssencePylonContribution contribution(ItemStack stack) {
        EssenceFocusTier tier = focusTier(stack);
        return tier == null
                ? EssenceFocusTier.emptyContribution()
                : tier.contribution();
    }

    /** Every Essence Focus, including Latent, may be installed. */
    public static boolean isFocus(ItemStack stack) {
        return EssenceFocusData.isFocusItem(stack);
    }

    /** True for the unified Essence Focus at any completed/Latent infusion state. */
    public static boolean isFocusItem(ItemStack stack) {
        return EssenceFocusData.isFocusItem(stack);
    }

}
