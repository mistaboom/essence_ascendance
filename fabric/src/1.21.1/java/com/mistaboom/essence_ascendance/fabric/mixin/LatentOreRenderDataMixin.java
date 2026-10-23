package com.mistaboom.essence_ascendance.fabric.mixin;

import com.mistaboom.essence_ascendance.ore.LatentOreBlockEntity;
import net.fabricmc.fabric.api.blockview.v2.RenderDataBlockEntity;
import org.spongepowered.asm.mixin.Mixin;

/** Snapshot the saved host for Fabric's asynchronous chunk mesher. */
@Mixin(LatentOreBlockEntity.class)
public abstract class LatentOreRenderDataMixin implements RenderDataBlockEntity {
    @Override
    public Object getRenderData() {
        return ((LatentOreBlockEntity) (Object) this).hostState().orElse(null);
    }
}
