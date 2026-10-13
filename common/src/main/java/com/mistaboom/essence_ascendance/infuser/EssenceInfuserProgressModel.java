package com.mistaboom.essence_ascendance.infuser;

/** Describes how an Infuser recipe accumulates work before completion. */
public enum EssenceInfuserProgressModel {
    /** Accumulate exact scheduled input and honor the processing duration before atomic output. */
    TIMED_ATOMIC,

    /** Persist transferred Essence directly on the workpiece as it is supplied. */
    STREAMED_PERSISTENT
}
