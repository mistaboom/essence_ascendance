package com.mistaboom.essence_ascendance.verification;

import java.lang.reflect.InvocationTargetException;

/** Only explicitly reviewed, lightweight suites may share this JVM. Registry installers stay isolated. */
public final class InvariantSuite {
    public static void main(String[] classes) throws Throwable {
        if (classes.length == 0) throw new IllegalArgumentException("Supply invariant main classes");
        for (String name : classes) {
            long started = System.nanoTime();
            System.out.println("INVARIANT START " + name);
            try {
                Class.forName(name).getMethod("main", String[].class).invoke(null, (Object) new String[0]);
            } catch (InvocationTargetException failure) {
                System.err.println("INVARIANT FAILED " + name);
                throw new AssertionError("Invariant failed: " + name, failure.getCause());
            }
            System.out.printf(java.util.Locale.ROOT, "INVARIANT PASS %s (%.3f s)%n", name,
                    (System.nanoTime() - started) / 1_000_000_000.0);
        }
    }
}
