package com.mistaboom.essence_ascendance.valuation;

import net.minecraft.util.RandomSource;
import java.util.function.Supplier;

/** Hypothetical trade/experience evidence uses private loot/entity streams, never a world's persistent RNG.
 * Explicitly seeded loot retains its own seed. This scope neither changes the factory's
 * prices/stock nor claims its sampled variants exhaust the underlying distribution. */
public final class TradeSamplingScope {
    private TradeSamplingScope() { }
    private static final ThreadLocal<RandomSource> LOOT = new ThreadLocal<>();
    private static final ThreadLocal<RandomSource> ENTITIES = new ThreadLocal<>();

    public static <T> T sample(long seed, Supplier<T> action) {
        RandomSource previous = LOOT.get();
        RandomSource priorEntities = ENTITIES.get();
        LOOT.set(RandomSource.create(seed ^ 0x64A0761D6478BD2FL));
        ENTITIES.set(RandomSource.create(seed ^ 0x1D8E4E27C47D124FL));
        try { return action.get(); }
        finally {
            if (previous == null) LOOT.remove(); else LOOT.set(previous);
            if (priorEntities == null) ENTITIES.remove(); else ENTITIES.set(priorEntities);
        }
    }

    /** Called at loot-context construction; outside a sample this is a no-op. */
    public static RandomSource isolatedLootRandom(RandomSource explicit) {
        return explicit == null ? LOOT.get() : explicit;
    }
    /** Only hypothetical entities constructed inside this scope receive a private native RNG.
     * They are never added to a level. Constructors and reward code consume their ordinary
     * distribution; gameplay entities and the world's random streams remain untouched. */
    public static RandomSource entityRandom(Supplier<RandomSource> ordinary) {
        var seeds = ENTITIES.get();
        return seeds == null ? ordinary.get() : RandomSource.create(seeds.nextLong());
    }

    /** Preserve the native reward distribution while a hypothetical animal reads
     * its level's RNG. The original object is neither reseeded nor consumed. */
    public static RandomSource rewardRandom(RandomSource ordinary) {
        var isolated = ENTITIES.get();
        return isolated == null ? ordinary : isolated;
    }
}
