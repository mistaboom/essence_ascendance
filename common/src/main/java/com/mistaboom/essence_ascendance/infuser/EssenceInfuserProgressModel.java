package com.mistaboom.essence_ascendance.infuser;

/** Describes how an Infuser recipe accumulates work before completion. */
public enum EssenceInfuserProgressModel {
    /** Wait a fixed processing duration, then perform one atomic transaction. */
    TIMED_ATOMIC,

    /** Persist transferred Essence directly on the workpiece as it is supplied. */
    STREAMED_PERSISTENT
}
