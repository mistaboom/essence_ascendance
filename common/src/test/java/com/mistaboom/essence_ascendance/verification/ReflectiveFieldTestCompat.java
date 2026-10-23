package com.mistaboom.essence_ascendance.verification;

import java.lang.reflect.Field;

/** Keeps native fixture reflection separate from projected NBT accessor names. */
public final class ReflectiveFieldTestCompat {
    private ReflectiveFieldTestCompat() { }

    public static int readInt(Field field, Object receiver) throws IllegalAccessException {
        return ((Number) field.get(receiver)).intValue();
    }

    public static boolean readBoolean(Field field, Object receiver) throws IllegalAccessException {
        return (Boolean) field.get(receiver);
    }

    public static long readLong(Field field, Object receiver) throws IllegalAccessException {
        return ((Number) field.get(receiver)).longValue();
    }

    public static double readDouble(Field field, Object receiver) throws IllegalAccessException {
        return ((Number) field.get(receiver)).doubleValue();
    }
}
