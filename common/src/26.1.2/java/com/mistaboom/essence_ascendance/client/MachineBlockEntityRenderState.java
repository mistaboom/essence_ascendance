package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.pylon.PylonLocalFrame;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Extracted static mesh orientation and the source of the dynamic world effects. */
public class MachineBlockEntityRenderState<T extends BlockEntity> extends BlockEntityRenderState {
    T source;
    float partialTick;
    PylonLocalFrame frame;
}
