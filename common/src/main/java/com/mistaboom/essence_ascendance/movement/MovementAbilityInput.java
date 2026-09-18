package com.mistaboom.essence_ascendance.movement;

/** Bounded ordinary-key intent. AIR_JUMP requests an extra jump; the server still
 * validates airborne support, the key edge, ownership and its own landing resource. */
public record MovementAbilityInput(int flags) {
    public static final int ENABLED = 1, JUMP = 2, FORWARD = 4, BACK = 8, LEFT = 16, RIGHT = 32;
    /** Only set for a fresh press the client routed to the air-jump skill, never for native takeoff. */
    public static final int AIR_JUMP = 64;
    public static final int MASK = ENABLED | JUMP | FORWARD | BACK | LEFT | RIGHT | AIR_JUMP;
    /** Transport heartbeat; shorter than the existing movement-intent expiry, not gameplay tuning. */
    public static final int HEARTBEAT_TICKS = 2;
    public static final MovementAbilityInput DISABLED = new MovementAbilityInput(0);
    public MovementAbilityInput { flags &= MASK; }
    public boolean enabled() { return (flags & ENABLED) != 0; }
    public boolean jump() { return enabled() && (flags & JUMP) != 0; }
    public boolean requestsAirJump() { return jump() && (flags & AIR_JUMP) != 0; }
    public int forward() { return ((flags & FORWARD) != 0 ? 1 : 0) - ((flags & BACK) != 0 ? 1 : 0); }
    public int left() { return ((flags & LEFT) != 0 ? 1 : 0) - ((flags & RIGHT) != 0 ? 1 : 0); }
}
