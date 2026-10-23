package com.mistaboom.essence_ascendance.registration;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** The two 26.1.2 loaders expose different block entity type constructors. */
public final class ContentRegistrationPlatform {
    private ContentRegistrationPlatform() { }

    @ExpectPlatform
    public static <T extends BlockEntity> BlockEntityType<T> createBlockEntityType(
            BlockEntityType.BlockEntitySupplier<T> factory, Block... blocks) {
        throw new AssertionError("Architectury must supply a loader implementation");
    }
}
