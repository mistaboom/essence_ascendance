package com.mistaboom.essence_ascendance.visual;

import net.minecraft.world.phys.Vec3;

/** Public entity presentation state shared by loader sync adapters. It is deliberately not saved. */
public record EntityPresentationState2612(int flightFlags, long boostTick, float arrowScale,
        int rodHand, int rodTier, int attachment, Vec3 anchor) {
    public static final EntityPresentationState2612 DEFAULT =
            new EntityPresentationState2612(0, Long.MIN_VALUE, 1.0F, 0, 0, 0, Vec3.ZERO);

    public EntityPresentationState2612 withFlight(int value) {
        return new EntityPresentationState2612(value, boostTick, arrowScale, rodHand, rodTier, attachment, anchor);
    }
    public EntityPresentationState2612 withBoost(long value) {
        return new EntityPresentationState2612(flightFlags, value, arrowScale, rodHand, rodTier, attachment, anchor);
    }
    public EntityPresentationState2612 withArrowScale(float value) {
        return new EntityPresentationState2612(flightFlags, boostTick, value, rodHand, rodTier, attachment, anchor);
    }
    public EntityPresentationState2612 withRod(int hand, int tier) {
        return new EntityPresentationState2612(flightFlags, boostTick, arrowScale, hand, tier, attachment, anchor);
    }
    public EntityPresentationState2612 withAttachment(int value, Vec3 point) {
        return new EntityPresentationState2612(flightFlags, boostTick, arrowScale, rodHand, rodTier, value, point);
    }
}
