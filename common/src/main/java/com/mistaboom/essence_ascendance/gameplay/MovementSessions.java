package com.mistaboom.essence_ascendance.gameplay;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Pure, active-session movement facts. Bounds are storage limits, independent of award thresholds. */
public final class MovementSessions {
    public record Position(double x,double y,double z) { }
    public record Frame(String dimension,long tick,Position before,Position after,boolean observed,
                        double horizontal,double spatial,double maximumStep,boolean eligible,
                        boolean airborneAllowed,boolean supported,boolean wings,boolean sprint,
                        double momentum,double momentumMaximum,boolean surfaceSupported,boolean momentumMovement,boolean rushProtected) { }
    private final UUID actor;
    private final Supplier<String> identities;
    private final Consumer<MovementOutcome> sink;
    private final MovementSessionTracker glide=new MovementSessionTracker(), sprint=new MovementSessionTracker(), surface=new MovementSessionTracker();
    private final Set<Long> obstacles=new HashSet<>();
    private final Set<Long> pendingObstacles=new HashSet<>();
    private String airborne="",dimension="";
    private int boosts,vaults;
    private long rushExpires=Long.MIN_VALUE,lastTick=Long.MIN_VALUE;
    private long lastBoost=Long.MIN_VALUE;
    private boolean maximum,airborneHit;
    public MovementSessions(UUID actor,Supplier<String> identities,Consumer<MovementOutcome> sink) {
        this.actor=Objects.requireNonNull(actor);this.identities=Objects.requireNonNull(identities);this.sink=Objects.requireNonNull(sink);
    }
    private MovementOutcome.Context context(String dimension,long tick) {
        return new MovementOutcome.Context(identities.get(),actor,dimension,tick);
    }
    public void beginGlide(String dimension,long tick,Position position) {
        begin(glide,dimension,tick,position);this.dimension=dimension;boosts=0;
        airborne=glide.sessionId();airborneHit=false;lastBoost=Long.MIN_VALUE;
    }
    public void boost(String dimension,long tick) {
        if(!glide.active()||!this.dimension.equals(dimension)||tick<=lastBoost)return;
        lastBoost=tick;
        boosts=Math.min(1024,boosts+1);
        var value=glide.sample(dimension,tick,positionX,positionY,positionZ,Double.MAX_VALUE,false);
        sink.accept(new MovementOutcome.Glide(context(dimension,tick),glide.sessionId(),boosts,value.spatialPath()));
    }
    // The last accepted endpoint is kept as scalars only, for boost checkpoints between samples.
    private double positionX,positionY,positionZ;
    public void rush(long expires) { rushExpires=expires; }
    /** Ineligible gameplay can still change the real skill reservoir. Baseline that state without
     * inventing a survival transition, while discarding every challenge and causal activation. */
    public void baselineMomentum(String dimension,long tick,double amount,double maximum) {
        stop();this.dimension=dimension;lastTick=tick;
        baselineMaximum(amount,maximum);
    }
    /** Current state after lost capacity is unknown history, not an observed maximum transition. */
    public void baselineMaximum(double amount,double maximum) {
        this.maximum=Double.isFinite(amount)&&Double.isFinite(maximum)&&maximum>0&&amount>=maximum;
    }
    /** The successful fill owner supplies affirmative evidence that its prior amount was below cap. */
    public void maximumWasBelow() { maximum=false; }
    public void vault(long obstacle) {
        if(pendingObstacles.size()<64)pendingObstacles.add(obstacle);
    }
    public void hit(String dimension,long tick,String combatAction) {
        if(airborne.isEmpty()||airborneHit||!this.dimension.equals(dimension))return;
        airborneHit=true;
        sink.accept(new MovementOutcome.AirborneHit(context(dimension,tick),airborne,combatAction));
    }
    public void sample(Frame f) {
        if(f.tick()==lastTick)return;
        if(f.tick()<lastTick||!f.eligible()||!dimension.isEmpty()&&!dimension.equals(f.dimension())) {baseline(f);return;}
        lastTick=f.tick();dimension=f.dimension();
        if(!f.airborneAllowed()||f.supported()){airborne="";airborneHit=false;}
        if(!f.wings())glide.stop();
        if(f.observed()&&(!Double.isFinite(f.horizontal())||!Double.isFinite(f.spatial())||f.horizontal()<0
                ||f.spatial()<f.horizontal()||f.spatial()>f.maximumStep())){baseline(f);return;}
        if(f.observed()&&f.spatial()+1e-7<distance(f.before(),f.after())){baseline(f);return;}
        if(glide.active()){
            var sample=sample(glide,f);
            if(sample.discontinuity()){baseline(f);return;}
            if(sample.accepted())sink.accept(new MovementOutcome.Glide(context(f.dimension(),f.tick()),glide.sessionId(),boosts,sample.spatialPath()));
        }
        boolean buff=Double.isFinite(f.momentum())&&Double.isFinite(f.momentumMaximum())&&f.momentum()>0&&f.momentumMaximum()>0;
        boolean nowMaximum=buff&&f.momentum()>=f.momentumMaximum();
        if(nowMaximum!=maximum&&f.momentumMaximum()>=0){
            sink.accept(new MovementOutcome.Momentum(context(f.dimension(),f.tick()),Math.max(0,Math.min(f.momentum(),f.momentumMaximum())),f.momentumMaximum()));maximum=nowMaximum;
        }
        boolean validSprint=f.sprint()&&buff&&f.momentumMovement();
        if(!validSprint||f.observed()&&f.horizontal()==0){sprint.stop();obstacles.clear();vaults=0;}
        else if(f.observed()){
            if(!sprint.active()){begin(sprint,f.dimension(),Math.max(0,f.tick()-1),f.before());obstacles.clear();vaults=0;}
            var sample=sample(sprint,f);
            if(sample.discontinuity()){baseline(f);return;}
            for(long obstacle:pendingObstacles)if(obstacles.size()<1024&&obstacles.add(obstacle))vaults=Math.min(1024,vaults+1);
            if(sample.accepted())sink.accept(new MovementOutcome.Sprint(context(f.dimension(),f.tick()),sprint.sessionId(),sample.horizontalPath(),vaults,
                    f.rushProtected()&&f.tick()<rushExpires));
        }
        if(!f.sprint()||!f.surfaceSupported()||f.observed()&&f.horizontal()==0)surface.stop();
        else if(f.observed()){
            if(!surface.active())begin(surface,f.dimension(),Math.max(0,f.tick()-1),f.before());
            var sample=sample(surface,f);
            if(sample.discontinuity()){baseline(f);return;}
            if(sample.accepted())sink.accept(new MovementOutcome.Surface(context(f.dimension(),f.tick()),surface.sessionId(),sample.horizontalPath()));
        }
        positionX=f.after().x();positionY=f.after().y();positionZ=f.after().z();
        pendingObstacles.clear();
    }
    private void begin(MovementSessionTracker target,String dimension,long tick,Position p) {
        target.begin(identities.get(),dimension,tick,p.x(),p.y(),p.z());positionX=p.x();positionY=p.y();positionZ=p.z();
    }
    /** Losing a route cannot turn the unchanged live reservoir into a newly observed maximum. */
    private void baseline(Frame frame) {
        baselineMomentum(frame.dimension(),frame.tick(),frame.momentum(),frame.momentumMaximum());
    }
    private static MovementSessionTracker.Sample sample(MovementSessionTracker target,Frame f) {
        return target.sample(f.dimension(),f.tick(),f.after().x(),f.after().y(),f.after().z(),f.maximumStep(),f.observed(),f.horizontal(),f.spatial());
    }
    private static double distance(Position a,Position b){return Math.hypot(Math.hypot(a.x()-b.x(),a.z()-b.z()),a.y()-b.y());}
    public boolean active(){return glide.active()||sprint.active()||surface.active()||!airborne.isEmpty()||rushExpires>lastTick;}
    public void stop(){glide.stop();sprint.stop();surface.stop();obstacles.clear();pendingObstacles.clear();boosts=vaults=0;airborne="";airborneHit=false;rushExpires=Long.MIN_VALUE;maximum=false;dimension="";}
}
