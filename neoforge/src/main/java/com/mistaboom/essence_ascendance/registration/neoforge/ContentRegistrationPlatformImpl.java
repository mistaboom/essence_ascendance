package com.mistaboom.essence_ascendance.registration.neoforge;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ContentRegistrationPlatformImpl {
    private ContentRegistrationPlatformImpl() { }

    public static <T extends BlockEntity> BlockEntityType<T> createBlockEntityType(
            BlockEntityType.BlockEntitySupplier<T> factory, Block... blocks) {
        //? if >=26.1.2
        /*return new BlockEntityType<>(factory, blocks);*/
        //? if <26.1.2
        return BlockEntityType.Builder.of(factory, blocks).build(null);
    }
}
