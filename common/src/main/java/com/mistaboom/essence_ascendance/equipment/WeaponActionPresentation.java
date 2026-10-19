package com.mistaboom.essence_ascendance.equipment;

import java.util.ArrayList;
import java.util.List;
import static com.mistaboom.essence_ascendance.equipment.WeaponActionGeometry.*;

/** Bounded wire history and chronological playback of server-solved geometry. No collision prediction. */
public final class WeaponActionPresentation {
    public static final int MAX_KNOTS=256;
    private WeaponActionPresentation() { }
    /** One action clock and a short history buffer; packet arrival never restarts an in-progress interval. */
    public static final class Timeline {
        public static final double DELAY=2;
        private final java.util.ArrayDeque<WeaponActionRuntime.Snapshot> history=new java.util.ArrayDeque<>();
        private final double offset;
        private final double prepareAt;
        private double lastTime=Double.NEGATIVE_INFINITY;
        private double retractAt=Double.POSITIVE_INFINITY;
        private double recoveredAt=Double.POSITIVE_INFINITY;
        public Timeline(WeaponActionRuntime.Snapshot first,long receivedAt) { this(first,receivedAt,Double.NEGATIVE_INFINITY); }
        public Timeline(WeaponActionRuntime.Snapshot first,long receivedAt,double previousReturnAt) {
            offset=first.sampledAt()-receivedAt;
            prepareAt=Math.max(first.acceptedAt()-DELAY,Math.min(first.acceptedAt(),previousReturnAt));
            add(first);
        }
        public void add(WeaponActionRuntime.Snapshot state) {
            if (state.phase()==WeaponActionRuntime.Phase.RECOVERY || state.phase()==WeaponActionRuntime.Phase.IDLE)
                recoveredAt=Math.min(recoveredAt,state.sampledAt());
            if(state.phase()==WeaponActionRuntime.Phase.RETRACT && !Double.isFinite(retractAt))
                retractAt=state.sampledAt()-1+(state.knots().isEmpty() ? 1:state.knots().getLast().fraction());
            if((state.phase()==WeaponActionRuntime.Phase.RECOVERY || state.phase()==WeaponActionRuntime.Phase.IDLE)
                    && !Double.isFinite(retractAt)) {
                var timing=state.mode()==WeaponActionRuntime.Mode.STAB ? GlaiveTuning.STAB:GlaiveTuning.SWIPE;
                retractAt=state.sampledAt()-timing.retractionTicks();
            }
            if (!history.isEmpty() && history.getLast().sampledAt()==state.sampledAt()) history.removeLast();
            history.addLast(state);
            while (history.size()>16) history.removeFirst();
        }
        public Frame frame(long now,float partial) {
            return frameAt(now+Math.clamp(partial,0,1)+offset-DELAY);
        }
        public Frame frameAt(double playedAt) {
            var latest=history.getLast();
            double time=Math.min(latest.sampledAt(),Math.max(lastTime,playedAt));
            lastTime=time;
            var selected=latest;
            for (var state:history) if (state.sampledAt()>=time) { selected=state; break; }
            var phase=selected.phase();
            if (latest.phase()==WeaponActionRuntime.Phase.CANCELLED) phase=latest.phase();
            else if ((phase==WeaponActionRuntime.Phase.RECOVERY || phase==WeaponActionRuntime.Phase.IDLE)
                    && selected.sampledAt()==recoveredAt && selected.knots().size()>1
                    && time<selected.sampledAt()) phase=WeaponActionRuntime.Phase.RETRACT;
            var timing=selected.mode()==WeaponActionRuntime.Mode.STAB ? GlaiveTuning.STAB:GlaiveTuning.SWIPE;
            // Use the existing playback buffer for the short preparation. Damage playback starts at full weight.
            double enter=smooth((time-prepareAt)/Math.max(1e-6,selected.acceptedAt()-prepareAt));
            double leave=smooth((time-retractAt)/timing.retractionTicks());
            float blend=extended(phase) ? (float)(enter*(1-leave)):0;
            return new Frame(sample(selected,time-selected.sampledAt()+1),phase,time,blend);
        }
        public int size() { return history.size(); }
        public double retractAt() { return retractAt; }
        public double prepareAt() { return prepareAt; }
        public double returnAt() {
            var timing=history.getLast().mode()==WeaponActionRuntime.Mode.STAB ? GlaiveTuning.STAB:GlaiveTuning.SWIPE;
            return retractAt+timing.retractionTicks();
        }
    }
    private static double smooth(double value) { double t=Math.clamp(value,0,1);return t*t*(3-2*t); }
    public record Frame(Pose pose,WeaponActionRuntime.Phase phase,double time,float blend) { }
    public static boolean extended(WeaponActionRuntime.Phase phase) {
        return switch (phase) { case EXTEND, OUTWARD, SWEEP, RETRACT -> true; default -> false; };
    }
    public static Pose sample(WeaponActionRuntime.Snapshot state,double fraction) {
        var knots=state.knots();
        if (knots.isEmpty()) return interpolate(state.previous(),state.current(),Math.clamp(fraction,0,1));
        var before=knots.getFirst();
        for (int i=1;i<knots.size();i++) {
            var after=knots.get(i);
            if (after.fraction()>fraction) {
                double t=Math.clamp((fraction-before.fraction())/(after.fraction()-before.fraction()),0,1);
                return interpolate(before.pose(),after.pose(),t);
            }
            // Last knot at a duplicate timestamp wins, preserving instantaneous shortening.
            before=after;
        }
        return before.pose();
    }
    public static List<Knot> wireKnots(List<Knot> source) {
        if (source.size()<3) return source;
        var result=new ArrayList<Knot>();
        result.add(source.getFirst());
        for (int i=1;i<source.size()-1;i++) {
            var a=result.getLast(); var b=source.get(i); var c=source.get(i+1);
            double span=c.fraction()-a.fraction();
            // Only remove redundant numerical subdivisions. Never remove collision minima or timed jumps.
            boolean redundant=span>0 && b.fraction()>a.fraction() && c.fraction()>b.fraction()
                    && motionBound(b.pose(),interpolate(a.pose(),c.pose(),(b.fraction()-a.fraction())/span))<1e-8;
            if (!redundant) result.add(b);
        }
        result.add(source.getLast());
        if (result.size()<=MAX_KNOTS) return List.copyOf(result);
        // Pathological input can contain arbitrarily many turns in one tick. Bound traffic conservatively:
        // every retained pose uses the smallest solved length, so no discarded shortening becomes a long chord.
        double minimum=source.stream().mapToDouble(k -> k.pose().tipDistance()).min().orElse(0);
        var bounded=new ArrayList<Knot>(MAX_KNOTS);
        for (int i=0;i<MAX_KNOTS;i++) {
            var knot=source.get((int)((long)i*(source.size()-1)/(MAX_KNOTS-1)));
            bounded.add(new Knot(knot.fraction(),knot.pose().withDistance(minimum)));
        }
        return List.copyOf(bounded);
    }
}
