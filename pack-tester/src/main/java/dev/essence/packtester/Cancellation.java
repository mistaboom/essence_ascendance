package dev.essence.packtester;

import java.util.concurrent.CancellationException;

final class Cancellation {
    private volatile boolean cancelled;
    synchronized void cancel() { cancelled = true; }
    void check() { if (cancelled) throw new CancellationException("Cancelled. Deployment was not committed; any replacement in progress was rolled back."); }
    synchronized void launchIfActive(CheckedAction action) throws Exception { check(); action.run(); }
    @FunctionalInterface interface CheckedAction { void run() throws Exception; }
}
