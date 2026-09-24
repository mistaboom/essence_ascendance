package com.mistaboom.essence_ascendance.archive;

import java.util.Objects;

/** Common-side indirection keeps the real Archive item safe to load on a dedicated server. */
public final class ArchiveClientBridge {
    private static volatile Runnable opener = () -> { };
    private ArchiveClientBridge() { }
    public static void install(Runnable clientOpener) { opener = Objects.requireNonNull(clientOpener); }
    public static void open() { opener.run(); }
}
