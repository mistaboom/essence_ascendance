package com.mistaboom.essence_ascendance.verification;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;

/** Runs each isolated 26.1 invariant after the same built-in item binding done by a loader. */
public final class NativeTestLauncher2612 {
    private NativeTestLauncher2612() { }

    public static void main(String[] args) throws Throwable {
        if (args.length == 0) throw new IllegalArgumentException("Fixture main class is required");
        NativeTestBootstrap2612.bindBuiltInComponents();
        try {
            Class.forName(args[0]).getMethod("main", String[].class)
                    .invoke(null, (Object) Arrays.copyOfRange(args, 1, args.length));
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
