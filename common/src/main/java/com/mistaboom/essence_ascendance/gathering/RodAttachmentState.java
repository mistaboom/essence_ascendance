package com.mistaboom.essence_ascendance.gathering;

import com.mistaboom.essence_ascendance.equipment.EquipmentFishingRodService;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Transient cast identity and press lifecycle. A release arms the next press until pulling actually starts. */
public final class RodAttachmentState {
    public InteractionHand hand;
    public ItemStack castStack;
    public EquipmentFishingRodService.Context castContext;
    public BlockPos anchorBlock;
    public BlockState anchorGeometry;
    public Vec3 anchor, lastTravelPosition;
    public boolean armed, pulling;
    public int poweredTicks;
    public RodPullMath.OpeningFrame openingFrame;
    public double entityViewScale = 1;
    public double waterTravel;
    public long lastForceTick = Long.MIN_VALUE;
    public long lastHeldInputTick = Long.MIN_VALUE;
    public boolean waterFishing;
    public boolean previousWaterPull;
    public void cast(InteractionHand hand, ItemStack stack, EquipmentFishingRodService.Context context) {
        this.hand = hand; castStack = stack; castContext = context;
    }
    public boolean press() {
        if (!armed || pulling) return false;
        pulling = true; poweredTicks = 0; openingFrame = null; lastTravelPosition = null; waterTravel = 0; return true;
    }
    /** Returns true only when a real pulling press is released. */
    public boolean release() { if (pulling) { pulling = false; return true; } armed = true; return false; }
}
