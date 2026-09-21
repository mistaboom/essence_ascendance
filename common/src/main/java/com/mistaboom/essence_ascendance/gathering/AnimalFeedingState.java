package com.mistaboom.essence_ascendance.gathering;

/** Native consumed-feed state, independent of the breeding age accelerated by Herdkeeper. */
public interface AnimalFeedingState {
    long essenceAscendance$fedAt();
    void essenceAscendance$fedAt(long now);
    int essenceAscendance$feedWindow();
    default boolean essenceAscendance$fed(long now) {
        long at = essenceAscendance$fedAt();
        return at != Long.MIN_VALUE && now >= at && now - at < essenceAscendance$feedWindow();
    }
}
