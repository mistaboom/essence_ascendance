package com.mistaboom.essence_ascendance.vitality;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import java.util.UUID;

/** Persisted obligations, not purchased power: refund, rank/config refresh and reconnect cannot forgive debt. */
public final class VitalityDamageLedger {
    public record Source(String damageType, UUID attacker) { }
    public final LinearDamageQueue<Source> delayed = new LinearDamageQueue<>();
    public double traumaFraction;
    public int traumaQuietTicks;
    // Deliberately not serialized: same-tick progression/config refresh must not pay twice.
    public long lastOnlineTick = Long.MIN_VALUE;
    public void clear() {
        delayed.clear(); traumaFraction = 0; traumaQuietTicks = 0;
    }
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("trauma_fraction", traumaFraction);
        tag.putInt("trauma_quiet_ticks", traumaQuietTicks);
        ListTag debts = new ListTag();
        for (var debt : delayed.snapshot()) {
            CompoundTag item = new CompoundTag();
            item.putString("source_type", debt.source().damageType());
            if (debt.source().attacker() != null) item.putUUID("attacker", debt.source().attacker());
            item.putDouble("remaining", debt.remaining()); item.putInt("ticks", debt.ticks());
            debts.add(item);
        }
        tag.put("delayed", debts);
        return tag;
    }
    public static VitalityDamageLedger load(CompoundTag tag) {
        VitalityDamageLedger result = new VitalityDamageLedger();
        result.traumaFraction = unit(tag.getDouble("trauma_fraction"), Math.nextDown(1.0));
        result.traumaQuietTicks = Math.clamp(tag.getInt("trauma_quiet_ticks"), 0, LinearDamageQueue.MAX_TICKS);
        ListTag debts = tag.getList("delayed", Tag.TAG_COMPOUND);
        if (debts.size() > LinearDamageQueue.MAX_ENTRIES) throw new IllegalArgumentException("Delayed damage ledger exceeds safety bound");
        for (int i = 0; i < debts.size(); i++) {
            CompoundTag debt = debts.getCompound(i);
            Source source = new Source(debt.getString("source_type"), debt.hasUUID("attacker") ? debt.getUUID("attacker") : null);
            if (!result.delayed.enqueue(source, debt.getDouble("remaining"), debt.getInt("ticks")))
                throw new IllegalArgumentException("Cannot restore delayed damage obligation");
        }
        return result;
    }
    private static double unit(double value, double maximum) {
        if (!Double.isFinite(value) || value < 0 || value > maximum) throw new IllegalArgumentException("Invalid Vitality resource ledger");
        return value;
    }
}
