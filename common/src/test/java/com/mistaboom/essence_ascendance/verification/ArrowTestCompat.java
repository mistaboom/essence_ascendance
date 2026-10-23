package com.mistaboom.essence_ascendance.verification;

//? if >=26.1.2
/*import net.minecraft.world.entity.projectile.arrow.AbstractArrow;*/
//? if <26.1.2
import net.minecraft.world.entity.projectile.AbstractArrow;

/** Reads the native arrow damage value after its 26.1.2 getter was removed. */
public final class ArrowTestCompat {
    private ArrowTestCompat() { }

    public static double baseDamage(AbstractArrow arrow) {
        //? if >=26.1.2
        /*try {*/
            //? if >=26.1.2
            /*var field = AbstractArrow.class.getDeclaredField("baseDamage");*/
            //? if >=26.1.2
            /*field.setAccessible(true);*/
            //? if >=26.1.2
            /*return ((Number) field.get(arrow)).doubleValue();*/
        //? if >=26.1.2
        /*} catch (ReflectiveOperationException exception) { throw new AssertionError("Native arrow base damage unavailable", exception); }*/
        //? if <26.1.2
        return arrow.getBaseDamage();
    }
}
