package com.mistaboom.essence_ascendance.verification;

import com.mistaboom.essence_ascendance.balance.economy.FractionalLedgerSavedData;
import net.minecraft.nbt.CompoundTag;

/** Reads the same ledger serializer across the SavedData API change. */
public final class FractionalLedgerTestCompat {
    private FractionalLedgerTestCompat() { }

    public static CompoundTag save(FractionalLedgerSavedData ledger) {
        //? if >=26.1.2
        /*try {*/
            //? if >=26.1.2
            /*var method = FractionalLedgerSavedData.class.getDeclaredMethod("saveTag", CompoundTag.class);*/
            //? if >=26.1.2
            /*method.setAccessible(true);*/
            //? if >=26.1.2
            /*return (CompoundTag) method.invoke(ledger, new CompoundTag());*/
        //? if >=26.1.2
        /*} catch (ReflectiveOperationException exception) { throw new AssertionError("Native ledger serialization unavailable", exception); }*/
        //? if <26.1.2
        return ledger.save(new CompoundTag(), null);
    }
}
