package com.mistaboom.essence_ascendance.pylon;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.infuser.FocusInfusionData;
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

    public static final RegistrySupplier<Item> DORMANT_PYLON_FOCUS =
            focus("dormant_pylon_focus", EssencePylonFocusTier.DORMANT);
    public static final RegistrySupplier<Item> AWAKENED_PYLON_FOCUS =
            focus("awakened_pylon_focus", EssencePylonFocusTier.AWAKENED);
    public static final RegistrySupplier<Item> RESONANT_PYLON_FOCUS =
            focus("resonant_pylon_focus", EssencePylonFocusTier.RESONANT);
    public static final RegistrySupplier<Item> ASCENDANT_PYLON_FOCUS =
            focus("ascendant_pylon_focus", EssencePylonFocusTier.ASCENDANT);
    public static final RegistrySupplier<Item> TRANSCENDENT_PYLON_FOCUS =
            focus("transcendent_pylon_focus", EssencePylonFocusTier.TRANSCENDENT);

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

    private static RegistrySupplier<Item> focus(
            String id,
            EssencePylonFocusTier tier
    ) {
        return ITEMS.register(
                id,
                () -> new EssencePylonFocusItem(
                        tier,
                        new Item.Properties()
                )
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
                CreativeModeTabs.FUNCTIONAL_BLOCKS,
                ESSENCE_PYLON_ITEM
        );
        CreativeTabRegistry.append(
                CreativeModeTabs.INGREDIENTS,
                DORMANT_PYLON_FOCUS,
                AWAKENED_PYLON_FOCUS,
                RESONANT_PYLON_FOCUS,
                ASCENDANT_PYLON_FOCUS,
                TRANSCENDENT_PYLON_FOCUS
        );

        initialized = true;
    }

    /** Operational tier for an installed Focus. Partially infused workpieces cannot power machines. */
    public static EssencePylonFocusTier focusTier(ItemStack stack) {
        EssencePylonFocusTier raw = rawFocusTier(stack);
        return raw != null && !FocusInfusionData.hasInfusionTag(stack) ? raw : null;
    }

    /** Raw tier identity used when a Focus item itself is the Infuser workpiece. */
    public static EssencePylonFocusTier rawFocusTier(ItemStack stack) {
        if (stack != null && !stack.isEmpty()
                && stack.getItem() instanceof EssencePylonFocusItem focusItem) {
            return focusItem.tier();
        }
        return null;
    }

    public static Item itemForTier(EssencePylonFocusTier tier) {
        return switch (tier) {
            case DORMANT -> DORMANT_PYLON_FOCUS.get();
            case AWAKENED -> AWAKENED_PYLON_FOCUS.get();
            case RESONANT -> RESONANT_PYLON_FOCUS.get();
            case ASCENDANT -> ASCENDANT_PYLON_FOCUS.get();
            case TRANSCENDENT -> TRANSCENDENT_PYLON_FOCUS.get();
        };
    }

    public static EssencePylonContribution contribution(ItemStack stack) {
        EssencePylonFocusTier tier = focusTier(stack);
        return tier == null
                ? EssencePylonFocusTier.EMPTY_PYLON
                : tier.contribution();
    }

    public static boolean isFocus(ItemStack stack) {
        return focusTier(stack) != null;
    }

    public static boolean isFocusItem(ItemStack stack) {
        return rawFocusTier(stack) != null;
    }
}
