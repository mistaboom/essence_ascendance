package com.mistaboom.essence_ascendance.team;

/** One scheduler for all external adapters: events settle promptly; unavailable recovery is bounded. */
public final class MembershipCaptureSchedule {
    public static final int RECOVERY_TICKS = 100;
    private int remaining;
    public void invalidate() { remaining = 0; }
    public boolean due() { if (remaining > 0) { remaining--; return false; } return true; }
    public void captured(boolean ready, boolean pending) { remaining = ready && pending ? 0 : RECOVERY_TICKS - 1; }
}
