package com.mistaboom.essence_ascendance.gameplay;

import java.util.*;
import java.util.function.Consumer;

/** Bounded rod sessions. Identity is actor + accepted pull, never item lifetime identity. */
public final class RodTrajectoryTracker {
    public static final int MAX_SESSIONS = 256;
    public static final long LIFETIME_TICKS = 200;
    public record Point(double x, double y, double z) {
        public double horizontal(Point other) { return Math.hypot(x-other.x,z-other.z); }
        public boolean finite() { return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z); }
    }
    public record Origin(String action, UUID actor, UUID hook, UUID target, String dimension, long tick,
                         Point position, Point eye, boolean anchor, boolean hostile, boolean item, double range,
                         boolean exposedStart, boolean upwardLaunch, long entityTick) {
        public Origin(String action,UUID actor,UUID hook,UUID target,String dimension,long tick,Point position,Point eye,
                      boolean anchor,boolean hostile,boolean item,double range,boolean exposedStart,boolean upwardLaunch) {
            this(action,actor,hook,target,dimension,tick,position,eye,anchor,hostile,item,range,exposedStart,upwardLaunch,-1);
        }
    }
    private static final class Session {
        final Origin origin;
        final MovementSessionTracker motion = new MovementSessionTracker();
        Point last, waterStart;
        long lastTick;
        boolean released, airborne, waterPrevious, launchCausal=true;
        Point release;
        Session(Origin origin) {
            this.origin=origin; last=origin.position; lastTick=origin.tick;
            motion.begin(origin.action,origin.dimension,origin.tick,last.x,last.y,last.z);
            waterPrevious=origin.exposedStart; waterStart=waterPrevious ? last : null;
        }
    }
    private final Map<UUID,Session> anchors = new HashMap<>();
    private final Map<UUID,Session> targets = new HashMap<>();
    public boolean start(Origin origin, boolean eligible) {
        if (origin == null || origin.actor == null || origin.hook == null || origin.action == null || origin.dimension == null
                || origin.action.isBlank() || origin.action.length()>180 || origin.dimension.isBlank() || origin.dimension.length()>256
                || origin.position == null || !origin.position.finite() || origin.eye == null || !origin.eye.finite()
                || origin.tick < 0 || !Double.isFinite(origin.range) || origin.range < 0
                || origin.anchor && origin.target!=null || origin.hostile && origin.item) return false;
        // A competing accepted pull invalidates earlier ownership even when its new actor is ineligible.
        if (origin.target != null) targets.remove(origin.target);
        if (origin.anchor) anchors.remove(origin.actor);
        prune(origin.tick);
        if (!eligible || anchors.size()+targets.size() >= MAX_SESSIONS) return false;
        if (origin.anchor) anchors.put(origin.actor,new Session(origin));
        else if (origin.target != null && (origin.hostile || origin.item)) targets.put(origin.target,new Session(origin));
        else return false;
        return true;
    }
    public Origin anchor(UUID actor) { Session session=anchors.get(actor); return session==null ? null : session.origin; }
    public List<Origin> targets(UUID actor) {
        return targets.values().stream().filter(s->s.origin.actor.equals(actor)).map(s->s.origin).toList();
    }
    public Point last(UUID identity, boolean anchor) {
        Session session=(anchor ? anchors : targets).get(identity); return session==null ? null : session.last;
    }
    public boolean released(UUID identity, boolean anchor) {
        Session session=(anchor ? anchors : targets).get(identity); return session!=null && session.released;
    }
    public void release(UUID actor, UUID hook, long now, Point targetPosition) {
        Session anchor=anchors.get(actor);
        if (anchor!=null && anchor.origin.hook.equals(hook) && alive(anchor,now)) anchor.released=true;
        for (Session session:targets.values()) if (session.origin.actor.equals(actor) && session.origin.hook.equals(hook) && alive(session,now)) {
            session.released=true;
            // Engineering defines launch displacement from release, separately from the preceding reel.
            session.release=targetPosition!=null && targetPosition.finite() ? targetPosition : session.last;
        }
    }
    public void hookRemoved(UUID hook) {
        anchors.values().removeIf(s->s.origin.hook.equals(hook) && !s.released);
        targets.values().removeIf(s->s.origin.hook.equals(hook) && !s.released);
    }
    public void sampleAnchor(UUID actor, String dimension, long now, Point position, double maxStep,
                             boolean forbidden, boolean exposedRoute, boolean supportedDry, Consumer<RodOutcome> emit) {
        sampleAnchor(actor,dimension,now,position,maxStep,forbidden,exposedRoute,supportedDry,false,emit);
    }
    public void sampleAnchor(UUID actor, String dimension, long now, Point position, double maxStep,
                             boolean forbidden, boolean exposedRoute, boolean supportedDry, boolean checkpoint, Consumer<RodOutcome> emit) {
        Session session=anchors.get(actor);
        if (session==null) return;
        long previous=session.lastTick;
        Point before=session.last;
        if (!alive(session,now) || forbidden || !sample(session,dimension,now,position,maxStep,checkpoint)) { anchors.remove(actor); return; }
        if (previous==now && !checkpoint) return;
        double rise=position.y-session.origin.position.y;
        if (!supportedDry && rise>.1) session.airborne=true;
        if (!session.released && exposedRoute) {
            if (!session.waterPrevious) session.waterStart=before;
            if (session.waterStart!=null) emit.accept(fact(session,now,RodOutcome.Kind.ANCHORED_WATER_DISPLACEMENT,position.horizontal(session.waterStart)));
            session.waterPrevious=true;
        } else { session.waterPrevious=false; session.waterStart=null; }
        if (supportedDry && session.airborne && rise>=10) {
            emit.accept(fact(session,now,RodOutcome.Kind.SUPPORTED_GRAPPLE_LANDING,rise)); anchors.remove(actor);
        } else if (supportedDry && session.airborne) anchors.remove(actor);
    }
    public void sampleTarget(UUID target, String dimension, long now, Point position, double maxStep,
                             boolean forbidden, boolean grounded, boolean water, boolean causalArrival,
                             Consumer<RodOutcome> emit) {
        sampleTarget(target,dimension,now,position,maxStep,forbidden,grounded,water,causalArrival,false,emit);
    }
    public void sampleTarget(UUID target, String dimension, long now, Point position, double maxStep,
                             boolean forbidden, boolean grounded, boolean water, boolean causalArrival, boolean checkpoint,
                             Consumer<RodOutcome> emit) {
        Session session=targets.get(target);
        if (session==null) return;
        long previous=session.lastTick;
        if (!alive(session,now) || forbidden || !sample(session,dimension,now,position,maxStep,checkpoint)) { targets.remove(target); return; }
        if (previous==now && !checkpoint) return;
        if (!grounded && (session.origin.position.horizontal(position)>.1 || Math.abs(position.y-session.origin.position.y)>.1)) session.airborne=true;
        if (causalArrival && meaningfulToward(session,position)) {
            emit.accept(fact(session,now,session.origin.item ? RodOutcome.Kind.ITEM_REELED : RodOutcome.Kind.HOSTILE_REELED,session.origin.position.horizontal(position)));
        }
        if (session.origin.hostile && session.launchCausal && session.released && session.airborne && session.release!=null)
            emit.accept(fact(session,now,RodOutcome.Kind.RELEASED_HOSTILE_DISPLACEMENT,position.horizontal(session.release)));
        // A surviving landing/water entry ends the launch. Native fatal-fall callback runs during landing first.
        if (session.origin.hostile && (water || grounded)) {
            session.launchCausal=false;
            if (session.released) targets.remove(target);
        }
    }
    public void died(UUID target, String dimension, long now, boolean fall, Point position, double maxStep, Consumer<RodOutcome> emit) {
        Session session=targets.remove(target);
        if (session!=null && session.origin.hostile && alive(session,now) && session.origin.dimension.equals(dimension)
                && fall && session.launchCausal && (session.airborne || session.origin.upwardLaunch)
                && position!=null && position.finite() && sample(session,dimension,now,position,maxStep))
            emit.accept(fact(session,now,RodOutcome.Kind.LAUNCHED_HOSTILE_FATAL_FALL,session.origin.position.horizontal(position)));
    }
    public void damaged(UUID target, boolean fall) { if (!fall) targets.remove(target); }
    public void landed(UUID target) { var session=targets.get(target); if(session!=null) session.launchCausal=false; }
    public Origin target(UUID target) { var session=targets.get(target); return session==null ? null : session.origin; }
    /** Native item touch may run after the actor's tick and before the item disappears. */
    public void arrived(UUID target,UUID actor,String dimension,long now,Point position,double maxStep,Consumer<RodOutcome> emit) {
        Session session=targets.get(target);
        if (session==null || !session.origin.item || !session.origin.actor.equals(actor)) return;
        if (!alive(session,now) || !sample(session,dimension,now,position,maxStep)) { targets.remove(target); return; }
        if (meaningfulToward(session,position)) {
            emit.accept(fact(session,now,RodOutcome.Kind.ITEM_REELED,session.origin.position.horizontal(position)));
            targets.remove(target);
        }
    }
    public void removed(UUID target) { targets.remove(target); }
    /** Every successful force must retain survival provenance, including a continuing pull. */
    public boolean eligibleForce(UUID actor,UUID target,boolean eligible) {
        if (eligible) return true;
        forget(actor);
        if (target!=null) removed(target);
        return false;
    }
    /** A changed native merge destroys attribution for both physical participants, including the survivor. */
    public boolean merged(UUID first,UUID second,int firstBefore,int secondBefore,int firstAfter,int secondAfter) {
        if (firstBefore==firstAfter && secondBefore==secondAfter) return false;
        removed(first); removed(second); return true;
    }
    public boolean pickedUp(UUID target,UUID picker,int before,int after) {
        var session=targets.get(target);
        if (session==null || before<=after || session.origin.actor.equals(picker)) return false;
        removed(target); return true;
    }
    public void forget(UUID actor) { anchors.remove(actor); targets.values().removeIf(s->s.origin.actor.equals(actor)); }
    public void prune(long now) { anchors.values().removeIf(s->!alive(s,now)); targets.values().removeIf(s->!alive(s,now)); }
    public int size() { return anchors.size()+targets.size(); }
    private static boolean alive(Session session,long now) { return now>=session.origin.tick && now-session.origin.tick<LIFETIME_TICKS; }
    private static boolean sample(Session session,String dimension,long now,Point position,double maxStep) {
        return sample(session,dimension,now,position,maxStep,false);
    }
    private static boolean sample(Session session,String dimension,long now,Point position,double maxStep,boolean checkpoint) {
        if (position==null || !position.finite() || now>session.lastTick+1) return false;
        var sample=session.motion.sample(dimension,now,position.x,position.y,position.z,maxStep,true);
        if (sample.discontinuity()) return false;
        if (checkpoint && now==session.lastTick) {
            // Endpoint checkpoints observe a later accepted position in this tick. Rod rules use displacement,
            // so rebasing validation here adds no duplicate path distance or extra physics step.
            session.motion.begin(session.origin.action,dimension,now,position.x,position.y,position.z);
            session.last=position; return true;
        }
        if (!sample.accepted()) return true;
        session.last=position; session.lastTick=now; return true;
    }
    private static RodOutcome fact(Session session,long now,RodOutcome.Kind kind,double metric) {
        return new RodOutcome(session.origin.action,session.origin.actor,session.origin.target,session.origin.dimension,now,kind,metric,session.origin.range);
    }
    private static boolean meaningfulToward(Session session,Point position) {
        Point start=session.origin.position, eye=session.origin.eye;
        double dx=eye.x-start.x,dy=eye.y-start.y,dz=eye.z-start.z;
        double length=Math.sqrt(dx*dx+dy*dy+dz*dz);
        return length>0 && ((position.x-start.x)*dx+(position.y-start.y)*dy+(position.z-start.z)*dz)/length>.1;
    }
}
