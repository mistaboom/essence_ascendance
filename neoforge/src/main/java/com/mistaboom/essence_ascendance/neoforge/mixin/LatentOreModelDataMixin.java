package com.mistaboom.essence_ascendance.neoforge.mixin;

import com.mistaboom.essence_ascendance.ore.LatentOreBlockEntity;
import com.mistaboom.essence_ascendance.neoforge.client.LatentOreNeoForgeModels;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.common.extensions.IBlockEntityExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Publish a main-thread immutable snapshot, then invalidate it when client host data changes. */
@Mixin(LatentOreBlockEntity.class)
public abstract class LatentOreModelDataMixin implements IBlockEntityExtension {
    @Override
    public ModelData getModelData() {
        return LatentOreNeoForgeModels.hostData(((LatentOreBlockEntity) (Object) this).hostState().orElse(null));
    }

    @Inject(method = "loadAdditional", at = @At("RETURN"))
    private void essenceAscendance$refreshHostModelData(CompoundTag tag, HolderLookup.Provider registries,
                                                       CallbackInfo callback) {
        essenceAscendance$requestHostModelData();
    }

    @Inject(method = "setHost", at = @At("RETURN"), remap = false)
    private void essenceAscendance$refreshPlacedHostModelData(BlockState host, CallbackInfo callback) {
        essenceAscendance$requestHostModelData();
    }

    @Unique
    private void essenceAscendance$requestHostModelData() {
        BlockEntity entity = (BlockEntity) (Object) this;
        if (entity.getLevel() != null && entity.getLevel().isClientSide) entity.requestModelDataUpdate();
    }
}
