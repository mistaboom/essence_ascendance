package com.mistaboom.essence_ascendance.audio;

import net.minecraft.world.phys.Vec3;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Single admission, playback and ownership path. All calls run on the presentation thread. */
public final class AudioEngine {
    public static final int SOURCE_LIMIT = 256, LOOP_LIMIT = 24, SHOT_LIMIT = 32, HISTORY_LIMIT = 512;
    /** Native owner objects supply incarnation identity; channel separates simultaneous activities. */
    public record Source(Object incarnation,String channel,Object destination) {
        public Source(Object incarnation,String channel) { this(incarnation,channel,null); }
        public Source { Objects.requireNonNull(incarnation); if(channel==null || channel.isBlank()) throw new IllegalArgumentException("Source channel"); }
        // Entity equality/hash codes can follow recycled numeric IDs. Ownership follows the actual object.
        @Override public boolean equals(Object other) { return other instanceof Source source && incarnation==source.incarnation && destination==source.destination && channel.equals(source.channel); }
        @Override public int hashCode() { return 31*(31*System.identityHashCode(incarnation)+channel.hashCode())+System.identityHashCode(destination); }
    }
    public record Context(Object source, Vec3 position, long occurrence, java.util.function.Supplier<Vec3> trackedPosition) {
        public Context(Object source,Vec3 position,long occurrence) { this(source,position,occurrence,null); }
        public Context { Objects.requireNonNull(source); Objects.requireNonNull(position); }
        @Override public Vec3 position() {
            if(trackedPosition==null) return position;
            Vec3 current=trackedPosition.get(); return current!=null && finite(current)?current:position;
        }
    }
    public interface Voice {
        boolean alive();
        void update(Vec3 position, float gain, float pitch);
        void close();
    }
    public interface Sink {
        float gain(AudioCatalog.Cue cue);
        Voice play(AudioCatalog.Cue cue, Context context, boolean loop, float gain);
        void caption(AudioCatalog.Cue cue, Context context, long tick);
    }
    private record Occurrence(Object cue, long sequence) { }
    private record Repeat(Object source, String group, Object cue) { }
    private static final class Active {
        final AudioCatalog.Cue cue;
        Context context;
        Voice voice;
        final boolean loop;
        final AudioCatalog.Lifecycle lifecycle;
        BooleanSupplier valid;
        BooleanSupplier activity;
        Active replacement;
        long expiry = Long.MAX_VALUE;
        long revision, retry, nextCheck, caption = Long.MIN_VALUE / 2;
        int age, release = -1;
        Active(AudioCatalog.Cue cue, Context context, Voice voice, boolean loop, AudioCatalog.Lifecycle lifecycle, BooleanSupplier valid, long revision) {
            this.cue=cue; this.context=context; this.voice=voice; this.loop=loop; this.lifecycle=lifecycle; this.valid=valid; this.revision=revision;
        }
    }
    private final Sink sink;
    private final Map<Occurrence, Long> seen = new HashMap<>();
    private final Map<Repeat, Long> repeated = new HashMap<>();
    private final Map<Object, Active> sources = new LinkedHashMap<>();
    private record Stopped(long revision,long expires) { }
    private final Map<Object,Stopped> stopped = new LinkedHashMap<>();
    private final List<Active> shots = new ArrayList<>();
    private final List<Active> playing = new ArrayList<>();
    private long tick = Long.MIN_VALUE;
    private long sourceClock;
    private int attempts, publicAttempts, publicHistory;
    private Vec3 listener = Vec3.ZERO;
    public AudioEngine(Sink sink) { this.sink=Objects.requireNonNull(sink); }

    /** Call once per tick even with no renderer, then publish/reconcile desired state. */
    public void tick(long now, Vec3 position) {
        tick(now,position,false);
    }
    /** UI time continues during pause; native gameplay fades and source recovery stay paused. */
    public void tick(long now,Vec3 position,boolean paused) {
        if (now < tick) clear();
        if (now == tick) return;
        if(!paused && tick!=Long.MIN_VALUE) sourceClock+=now-tick;
        tick=now; attempts=publicAttempts=0; listener=position;
        stopped.values().removeIf(s -> s.expires()<sourceClock);
        if(sources.isEmpty() && shots.isEmpty() && seen.isEmpty() && repeated.isEmpty()) return;
        seen.entrySet().removeIf(e -> { if(e.getValue()>=now) return false; if(e.getKey().cue() instanceof AudioCatalog.Cue cue && cue.audience().scope()==AudioCatalog.Scope.WORLD) publicHistory--; return true; });
        repeated.values().removeIf(deadline -> deadline < now);
        if(paused) return;
        shots.removeIf(a -> { if (a.voice == null || !a.voice.alive()) { stop(a); return true; } if (++a.age > 600 || sink.gain(a.cue) <= 0) { stop(a); return true; } a.voice.update(a.context.position(),sink.gain(a.cue),a.cue.playback().pitch()); return false; });
        for (var it=sources.entrySet().iterator(); it.hasNext();) {
            var entry=it.next(); var a=entry.getValue();
            if(sourceClock>=a.expiry) { rememberStop(entry.getKey(),a.revision); stop(a); it.remove(); continue; }
            if(a.voice==null && a.release<0 && now<a.nextCheck) continue;
            if(a.voice==null) a.nextCheck=now+10;
            if (!a.valid.getAsBoolean()) { stop(a); it.remove(); continue; }
            if(a.activity!=null) {
                if(!a.activity.getAsBoolean()) { if(a.release<0) a.release=a.lifecycle.releaseTicks(); }
                else if(a.replacement==null) a.release=-1;
            }
            if (a.release >= 0) {
                if(sink.gain(a.cue)<=0 || !inRange(a.cue,a.context)) stop(a);
                if (a.release == 0) {
                    stop(a);
                    if(a.replacement==null) it.remove(); else entry.setValue(a.replacement);
                    continue;
                }
                if (a.voice != null) a.voice.update(a.context.position(), sink.gain(a.cue)*a.release/Math.max(1,a.lifecycle.releaseTicks()),a.cue.playback().pitch());
                a.release--; continue;
            }
            boolean eligible = inRange(a.cue,a.context);
            if (!eligible || sink.gain(a.cue)<=0) stop(a);
            if (eligible && now-a.caption>=100) { sink.caption(a.cue,a.context,now); a.caption=now; }
            if (!eligible || sink.gain(a.cue)<=0) continue;
            if (a.voice != null && !a.voice.alive()) { stop(a); a.retry=now+a.lifecycle.restartBackoffTicks(); }
            if (a.voice == null && now>=a.retry && admit(a)) {
                a.age=0; start(a,a.lifecycle.attackTicks()>0 ? 0 : sink.gain(a.cue));
                a.retry=now+a.lifecycle.restartBackoffTicks();
            }
            if (a.voice != null) a.voice.update(a.context.position(),sink.gain(a.cue)*Math.min(1F,(float)++a.age/Math.max(1,a.lifecycle.attackTicks())),a.cue.playback().pitch());
        }
    }

    public boolean oneShot(AudioCatalog.Cue cue, Context context) {
        if (cue==null || !finite(context.position()) || !inRange(cue,context)
                || cue.audience().scope()==AudioCatalog.Scope.WORLD && publicAttempts++>=56 || attempts++>=64) return false;
        var occurrence=new Occurrence(cue,context.occurrence());
        if (seen.containsKey(occurrence) || seen.size()>=HISTORY_LIMIT || cue.audience().scope()==AudioCatalog.Scope.WORLD && publicHistory>=HISTORY_LIMIT-64) return false;
        seen.put(occurrence,tick+100);
        if(cue.audience().scope()==AudioCatalog.Scope.WORLD) publicHistory++;
        if(cue.admission().repeatTicks()>0) {
            var repeat=new Repeat(context.source(),cue.admission().group(),cue.id());
            if (repeated.getOrDefault(repeat,Long.MIN_VALUE)>tick) return false;
            if (!repeated.containsKey(repeat) && repeated.size()>=HISTORY_LIMIT) return false;
            repeated.put(repeat,tick+cue.admission().repeatTicks());
        }
        sink.caption(cue,context,tick);
        var active=new Active(cue,context,null,false,null,()->true,0);
        if (sink.gain(cue)>0 && admit(active)) { start(active,sink.gain(cue)); if(active.voice!=null) shots.add(active); }
        return true;
    }

    /** Source identity includes native object incarnation and channel; a stale owner cannot stop its replacement. */
    public void reconcile(Object key, AudioCatalog.Profile profile, Context context, boolean active, long revision, BooleanSupplier valid) {
        reconcile(key,profile,context,active,revision,valid,0);
    }
    /** Loaded facts may change between observer snapshots; activity fades, invalid incarnation removes immediately. */
    public void reconcile(Object key, AudioCatalog.Profile profile, Context context, boolean active, long revision, BooleanSupplier valid,BooleanSupplier activity) {
        reconcile(key,profile,context,active,revision,valid,0,activity);
    }
    /** Optional lease for network-only projections. Zero means native loaded/tracked validity owns expiry. */
    public void reconcile(Object key, AudioCatalog.Profile profile, Context context, boolean active, long revision, BooleanSupplier valid,int leaseTicks) {
        reconcile(key,profile,context,active,revision,valid,leaseTicks,null);
    }
    private void reconcile(Object key, AudioCatalog.Profile profile, Context context, boolean active, long revision, BooleanSupplier valid,int leaseTicks,BooleanSupplier activity) {
        if (profile==null || !finite(context.position())) return;
        var old=sources.get(key);
        if (old!=null && revision<old.revision) return;
        var terminal=stopped.get(key);
        if (terminal!=null && revision<=terminal.revision()) return;
        if (!active) { rememberStop(key,revision); if (old!=null) { old.revision=revision; old.replacement=null; old.activity=null; if(old.release<0) old.release=old.lifecycle.releaseTicks(); } return; }
        long expiry=leaseTicks>0?sourceClock+leaseTicks:Long.MAX_VALUE;
        if (old!=null && matches(old,profile)) { update(old,context,revision,valid,activity,expiry); old.release=-1; old.replacement=null; return; }
        if (old!=null) {
            // Any source profile change uses its existing release followed by the new attack.
            // Keep one native voice per identity and retain only the newest desired replacement.
            if(old.replacement==null || !matches(old.replacement,profile))
                old.replacement=new Active(profile.body(),context,null,true,profile.lifecycle(),valid,revision);
            update(old.replacement,context,revision,valid,activity,expiry);
            old.revision=revision; old.expiry=expiry; old.activity=activity;
            if(old.release<0) old.release=old.lifecycle.releaseTicks();
            return;
        }
        if (old==null && sources.size()>=SOURCE_LIMIT) {
            Active victim=null;
            int wanted=profile.body().playback().priority();
            double incomingDistance=profile.body().audience().scope()==AudioCatalog.Scope.WORLD?context.position().distanceToSqr(listener):0;
            for(var candidate:sources.values()) {
                int priority=candidate.cue.playback().priority();
                if(priority>wanted || priority==wanted && distance(candidate)<=incomingDistance+4) continue;
                if(victim==null || priority<victim.cue.playback().priority() || priority==victim.cue.playback().priority() && distance(candidate)>distance(victim)) victim=candidate;
            }
            if(victim==null) return;
            stop(victim); sources.values().remove(victim);
        }
        var desired=new Active(profile.body(),context,null,true,profile.lifecycle(),valid,revision);
        desired.expiry=expiry; desired.activity=activity;
        sources.put(key,desired);
    }
    public void remove(Object key) { var a=sources.remove(key); if(a!=null) { rememberStop(key,a.revision); stop(a); } }
    /** A linked activity dies when either native endpoint incarnation is removed. */
    public void removeIncarnation(Object owner) {
        for(Object key:new ArrayList<>(sources.keySet()))
            if(key==owner || key instanceof Source source && (source.incarnation()==owner || source.destination()==owner)) remove(key);
    }
    private void rememberStop(Object key,long revision) {
        stopped.put(key,new Stopped(revision,sourceClock+100));
        while(stopped.size()>HISTORY_LIMIT) stopped.remove(stopped.keySet().iterator().next());
    }
    /** Retain desired valid sources through reload/device recovery; never replay past accents. */
    public void invalidate() {
        shots.forEach(this::stop); shots.clear();
        for(var entry:sources.entrySet()) {
            var a=entry.getValue(); stop(a);
            if(a.replacement!=null) { a=a.replacement; entry.setValue(a); }
            a.nextCheck=0; a.caption=Long.MIN_VALUE/2; a.retry=tick+a.lifecycle.restartBackoffTicks();
        }
    }
    public void clear() { invalidate(); sources.clear(); stopped.clear(); seen.clear(); repeated.clear(); tick=Long.MIN_VALUE; sourceClock=0; attempts=publicAttempts=0; publicHistory=0; }
    public int sources() { return sources.size(); }
    /** Tracked adapters can reuse desired state, including muted/evicted bodies, without rebuilding it each tick. */
    public boolean containsSource(Object key) { return sources.containsKey(key); }
    public int voices() { return playing.size(); }
    public int history() { return seen.size(); }
    private static boolean matches(Active active,AudioCatalog.Profile profile) { return active.cue==profile.body() && active.lifecycle.equals(profile.lifecycle()); }
    private static void update(Active active,Context context,long revision,BooleanSupplier valid,BooleanSupplier activity,long expiry) {
        active.context=context; active.revision=revision; active.valid=valid; active.activity=activity; active.expiry=expiry;
    }
    private boolean inRange(AudioCatalog.Cue cue, Context c) { return cue.audience().scope()!=AudioCatalog.Scope.WORLD || listener.distanceToSqr(c.position())<=cue.audience().radius()*cue.audience().radius(); }
    private static boolean finite(Vec3 p) { return Double.isFinite(p.x)&&Double.isFinite(p.y)&&Double.isFinite(p.z); }
    private void start(Active a,float gain) { a.voice=sink.play(a.cue,a.context,a.loop,gain); if(a.voice!=null) playing.add(a); }
    private void stop(Active a) { if(a.voice!=null) { a.voice.close(); a.voice=null; playing.remove(a); } }
    private double distance(Active a) { return a.cue.audience().scope()==AudioCatalog.Scope.WORLD ? a.context.position().distanceToSqr(listener):0; }
    private boolean admit(Active incoming) {
        int total=0,group=0,source=0;
        for(var a:playing) {
            if(a.loop==incoming.loop) total++;
            boolean sameGroup=a.cue.admission().group().equals(incoming.cue.admission().group());
            if(sameGroup) { group++; if(a.context.source().equals(incoming.context.source())) source++; }
        }
        var policy=incoming.cue.admission();
        if(source>=policy.perSource()) return false;
        boolean fullType=total>=(incoming.loop?LOOP_LIMIT:SHOT_LIMIT), fullGroup=group>=policy.perGroup();
        if(!fullType && !fullGroup) return true;
        Active victim=null;
        for(var a:playing) {
            if(fullType && a.loop!=incoming.loop || fullGroup && !a.cue.admission().group().equals(policy.group())) continue;
            int priority=a.cue.playback().priority(), wanted=incoming.cue.playback().priority();
            if(priority>wanted || priority==wanted && distance(a)<=distance(incoming)+4) continue;
            if(victim==null || priority<victim.cue.playback().priority() || priority==victim.cue.playback().priority() && distance(a)>distance(victim)) victim=a;
        }
        if(victim==null) return false;
        stop(victim);
        if(victim.loop) victim.retry=tick+victim.lifecycle.restartBackoffTicks(); else shots.remove(victim);
        return true;
    }
}
