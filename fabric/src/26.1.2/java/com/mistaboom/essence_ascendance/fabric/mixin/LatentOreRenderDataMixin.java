package com.mistaboom.essence_ascendance.fabric.mixin;

import com.mistaboom.essence_ascendance.ore.LatentOreBlockEntity;
import net.fabricmc.fabric.api.blockgetter.v2.RenderDataBlockEntity;
import org.spongepowered.asm.mixin.Mixin;

/** Supply the host as an immutable section snapshot for asynchronous model building. */
@Mixin(LatentOreBlockEntity.class)
public abstract class LatentOreRenderDataMixin implements RenderDataBlockEntity {
    @Override public Object getRenderData() {
        return ((LatentOreBlockEntity) (Object) this).hostState().orElse(null);
    }
}
