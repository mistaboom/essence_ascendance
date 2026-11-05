package com.mistaboom.essence_ascendance.audio;

/** Shared semantic envelopes carry explicit server time, independently of native random sound seeds. */
public final class FeedbackTime {
    private FeedbackTime() { }
    public static boolean fresh(long originating,long current) { long age=current-originating; return age>=-20 && age<=100; }
}
