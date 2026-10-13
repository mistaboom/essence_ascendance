package com.mistaboom.essence_ascendance.infuser;

import net.minecraft.resources.ResourceLocation;
import com.mistaboom.essence_ascendance.team.PoolId;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import java.util.*;
import java.util.function.ToLongFunction;

/** Exact fixed-requirement planning shared by equipment workpieces and timed jobs. */
public final class InfusionPayments {
    private InfusionPayments() {}
    public static long next(long required, long current, long addition) {
        long next = Math.addExact(current, addition);
        if (required <= 0 || current < 0 || addition <= 0 || next > required)
            throw new IllegalArgumentException("Infusion payment exceeds exact requirement");
        return next;
    }
    public static Map<ResourceLocation, Long> plan(Map<ResourceLocation, Long> requirements,
            ToLongFunction<ResourceLocation> paid, ToLongFunction<ResourceLocation> stock, long budget) {
        Map<ResourceLocation, Long> result = new LinkedHashMap<>();
        for (var entry : requirements.entrySet()) {
            long room = entry.getValue() - paid.applyAsLong(entry.getKey());
            long amount = Math.min(budget, Math.min(Math.max(0, room), stock.applyAsLong(entry.getKey())));
            if (amount <= 0) continue;
            result.put(entry.getKey(), amount); budget -= amount;
            if (budget == 0) break;
        }
        return result;
    }
    public static long total(Map<ResourceLocation, Long> amounts) {
        long total = 0;
        for (long amount : amounts.values()) total = Math.addExact(total, amount);
        return total;
    }
    /** Attribution of committed work, never another spendable balance or refund ledger. */
    public static Map<PoolId, Map<ResourceLocation, Long>> attribute(
            Map<PoolId, Map<ResourceLocation, Long>> prior, PoolId pool, Map<ResourceLocation, Long> costs) {
        Objects.requireNonNull(pool);
        Map<PoolId, Map<ResourceLocation, Long>> next = new LinkedHashMap<>(prior);
        Map<ResourceLocation, Long> values = new LinkedHashMap<>(next.getOrDefault(pool, Map.of()));
        costs.forEach((type, amount) -> {
            if (amount <= 0) throw new IllegalArgumentException("Invalid committed work attribution");
            values.merge(type, amount, Math::addExact);
        });
        if (!values.isEmpty()) next.put(pool, Map.copyOf(values));
        return Map.copyOf(next);
    }
    public static ListTag saveFunding(Map<PoolId, Map<ResourceLocation, Long>> funding) {
        ListTag list = new ListTag();
        funding.forEach((pool, amounts) -> {
            CompoundTag entry = new CompoundTag(), values = new CompoundTag();
            entry.put("pool", pool.save()); amounts.forEach((type, amount) -> values.putLong(type.toString(), amount));
            entry.put("amounts", values); list.add(entry);
        });
        return list;
    }
    public static Map<PoolId, Map<ResourceLocation, Long>> loadFunding(ListTag list) {
        Map<PoolId, Map<ResourceLocation, Long>> result = new LinkedHashMap<>();
        for (Tag value : list) {
            CompoundTag entry = (CompoundTag) value, amounts = entry.getCompound("amounts");
            Map<ResourceLocation, Long> values = new LinkedHashMap<>();
            for (String type : amounts.getAllKeys()) {
                long amount = amounts.getLong(type);
                if (amount <= 0) throw new IllegalArgumentException("Invalid funding provenance");
                values.put(ResourceLocation.parse(type), amount);
            }
            if (result.put(PoolId.load(entry.getCompound("pool")), Map.copyOf(values)) != null)
                throw new IllegalArgumentException("Duplicate funding provenance");
        }
        return Map.copyOf(result);
    }
    /** Updates the existing portable progress record inside the same inventory/payment participant. */
    public static void attribute(CompoundTag progress, PoolId pool, Map<ResourceLocation, Long> costs) {
        progress.put("funding", saveFunding(attribute(loadFunding(progress.getList("funding", Tag.TAG_COMPOUND)), pool, costs)));
    }
}
