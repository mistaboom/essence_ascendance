package com.mistaboom.essence_ascendance.neoforge.mixin;

import com.mistaboom.essence_ascendance.ore.LatentOreBlockEntity;
import com.mistaboom.essence_ascendance.neoforge.client.LatentOreNeoForgeModels;
import net.minecraft.world.level.block.entity.BlockEntity;
//? if >=26.1.2
/*import net.neoforged.neoforge.model.data.ModelData;*/
//? if <26.1.2
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

    // Both native load APIs and item placement use this shared boundary. Matching the
    // legacy loadAdditional overload alone misses 26's actual ValueInput packet path.
    @Inject(method = "requestClientRenderUpdate", at = @At("HEAD"), remap = false)
    private void essenceAscendance$refreshHostModelData(CallbackInfo callback) {
        essenceAscendance$requestHostModelData();
    }

    @Unique
    private void essenceAscendance$requestHostModelData() {
        BlockEntity entity = (BlockEntity) (Object) this;
        if (entity.getLevel() != null && entity.getLevel().isClientSide) entity.requestModelDataUpdate();
    }
}
