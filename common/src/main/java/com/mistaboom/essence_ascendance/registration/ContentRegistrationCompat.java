package com.mistaboom.essence_ascendance.registration;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** Applies the registry identity before 26.1.2 constructors inspect properties. */
public final class ContentRegistrationCompat {
    private ContentRegistrationCompat() { }

    public static BlockBehaviour.Properties block(String path, BlockBehaviour.Properties properties) {
        //? if >=26.1.2
        /*return properties.setId(ResourceKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(EssenceAscendance.MOD_ID, path)));*/
        //? if <26.1.2
        return properties;
    }

    public static Item.Properties item(String path, Item.Properties properties) {
        //? if >=26.1.2
        /*return properties.setId(ResourceKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(EssenceAscendance.MOD_ID, path)));*/
        //? if <26.1.2
        return properties;
    }

    public static Item.Properties blockItem(String path, Item.Properties properties) {
        //? if >=26.1.2
        /*return item(path, properties.useBlockDescriptionPrefix());*/
        //? if <26.1.2
        return item(path, properties);
    }

    public static <T extends BlockEntity> BlockEntityType<T> blockEntityType(
            BlockEntityType.BlockEntitySupplier<T> factory, Block... blocks) {
        //? if >=26.1.2
        /*return ContentRegistrationPlatform.createBlockEntityType(factory, blocks);*/
        //? if <26.1.2
        return BlockEntityType.Builder.of(factory, blocks).build(null);
    }
}
