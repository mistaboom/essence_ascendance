package com.mistaboom.essence_ascendance.equipment;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import java.util.*;
import java.util.function.Predicate;
import static com.mistaboom.essence_ascendance.equipment.WeaponActionGeometry.*;

/** Conservative continuous finite-blade collision. Work is subdivided, never truncated by a sample cap. */
public final class WeaponActionCollision {
    private WeaponActionCollision() { }
    public enum Kind { BLOCK, UNKNOWN_TERRAIN, ENTITY }
    public record Candidate(AABB bounds, Kind kind, Entity entity, boolean shaftBlocks) { }
    public record Contact(double fraction, Pose pose, Candidate obstacle) { }
    public record Trace(List<Knot> knots, Contact contact) { }
    @FunctionalInterface public interface Scene { List<Candidate> candidates(Pose midpoint, double movement); }

    public record Sweep(List<Knot> knots, boolean completed) { }
    private record Radial(Pose pose, Candidate obstacle) { }
    private static final double PRESENTATION_MOTION=1.0/16;
    private static final double EVENT_TOLERANCE=1e-9;

    /**
     * Continuous nearest-obstruction envelope. Every blade point is translated radially by exact SAT;
     * time is conservatively partitioned only where a candidate could change the nearest obstruction.
     * A robust interior witness certifies stable blockers. Thin/grazing transitions refine to CONTACT_TOLERANCE.
     * No ray count, entity count, angular budget or range truncates the remaining path.
     */
    public static Sweep swipe(Pose from,Pose to,Scene scene,Predicate<Contact> visitor) {
        var knots=new ArrayList<Knot>();
        boolean completed=swipeBroad(from,to,0,1,scene,visitor,knots,new HashSet<>());
        return new Sweep(List.copyOf(knots),completed);
    }
    private static boolean swipeBroad(Pose a,Pose b,double start,double end,Scene scene,Predicate<Contact> visitor,
                                      List<Knot> knots,Set<Candidate> reported) {
        double motion=motionBound(a,b); Pose middle=interpolate(a,b,.5);
        if (motion>BROAD_PHASE_MOTION) {
            double time=(start+end)/2;
            return swipeBroad(a,middle,start,time,scene,visitor,knots,reported)
                    && swipeBroad(middle,b,time,end,scene,visitor,knots,reported);
        }
        return swipeInterval(a,b,start,end,scene.candidates(middle,motion/2),visitor,knots,reported);
    }
    private static boolean present(Candidate c) { return c.entity==null || c.entity.isAlive() && !c.entity.isRemoved(); }
    private static double entry(Pose pose,Candidate c,double inflation) {
        return pose.withDistance(0).blade().firstTranslation(c.bounds,pose.forward(),pose.tipDistance(),inflation);
    }
    private static Radial radial(Pose pose,List<Candidate> candidates,double inflation) {
        Candidate nearest=null; double distance=pose.tipDistance();
        for (Candidate c:candidates) {
            if (!present(c)) continue;
            double d=entry(pose,c,inflation);
            if (!Double.isNaN(d) && (nearest==null || d<distance-1e-12 || Math.abs(d-distance)<=1e-12 && compare(c,nearest)<0)) {
                distance=d; nearest=c;
            }
        }
        return new Radial(pose.withDistance(distance),nearest);
    }
    /** A contact with an eroded target contains a blade point that stays inside it over this entire interval. */
    private static boolean stable(Pose middle,double bound,double minimum,List<Candidate> candidates,Candidate winner) {
        if (winner==null) return false;
        AABB box=winner.bounds;
        if (Math.min(box.getXsize(),Math.min(box.getYsize(),box.getZsize()))<=2*bound) return false;
        AABB interior=new AABB(box.minX+bound,box.minY+bound,box.minZ+bound,box.maxX-bound,box.maxY-bound,box.maxZ-bound);
        double guaranteed=middle.withDistance(0).blade().firstTranslation(interior,middle.forward(),minimum,0);
        if (Double.isNaN(guaranteed)) return false;
        for (Candidate other:candidates) {
            if (other==winner || !present(other)) continue;
            double earliest=entry(middle,other,bound);
            if (!Double.isNaN(earliest) && earliest<=guaranteed+1e-10) return false;
        }
        return true;
    }
    private static void knot(List<Knot> knots,double fraction,Pose pose) {
        var next=new Knot(fraction,pose);
        if (knots.isEmpty() || !knots.getLast().equals(next)) knots.add(next);
    }
    private static boolean report(Radial solved,double fraction,Predicate<Contact> visitor,Set<Candidate> reported) {
        return solved.obstacle==null || !reported.add(solved.obstacle)
                || visitor.test(new Contact(fraction,solved.pose,solved.obstacle));
    }
    private static boolean swipeInterval(Pose a,Pose b,double start,double end,List<Candidate> source,Predicate<Contact> visitor,
                                         List<Knot> knots,Set<Candidate> reported) {
        Pose middle=interpolate(a,b,.5); double bound=motionBound(a,b)/2;
        var candidates=new ArrayList<Candidate>();
        for (Candidate c:source) if (present(c) && middle.radialBlade().intersects(c.bounds,bound)) candidates.add(c);
        if (candidates.isEmpty()) { knot(knots,start,a); knot(knots,end,b); return true; }
        Radial first=radial(a,candidates,0),mid=radial(middle,candidates,0),last=radial(b,candidates,0);
        boolean same=first.obstacle==mid.obstacle && mid.obstacle==last.obstacle
                && stable(middle,bound,Math.min(a.tipDistance(),b.tipDistance()),candidates,mid.obstacle);
        if (same && bound<=PRESENTATION_MOTION) {
            knot(knots,start,first.pose);
            if (!report(first,start,visitor,reported)) return false;
            // Native lethal damage removes the body as an obstruction at THIS contact time.
            if (!present(first.obstacle)) return swipeInterval(a,b,start,end,source,visitor,knots,reported);
            knot(knots,end,last.pose);
            return true;
        }
        if (bound<=CONTACT_TOLERANCE) {
            // Conservative transition cell: order actual candidate entry times before radial occlusion.
            var events=new ArrayList<Contact>();
            for (Candidate c:candidates) {
                double time=firstTime(a,b,c,0,1,true);
                if (!Double.isNaN(time)) events.add(new Contact(time,interpolate(a,b,time),c));
                double backwards=firstTime(b,a,c,0,1,true);
                if (!Double.isNaN(backwards)) {
                    // An occluded body may become exposed when the earlier obstacle LEAVES the radial volume.
                    double after=Math.min(1,1-backwards+EVENT_TOLERANCE*4/Math.max(EVENT_TOLERANCE,motionBound(a,b)));
                    events.add(new Contact(after,interpolate(a,b,after),c));
                }
            }
            events.sort((x,y)-> { int order=Double.compare(x.fraction,y.fraction); return order!=0 ? order:compare(x.obstacle,y.obstacle); });
            knot(knots,start,first.pose);
            for (Contact event:events) {
                Radial solved=radial(event.pose,candidates,EVENT_TOLERANCE*2);
                double time=start+(end-start)*event.fraction;
                knot(knots,time,solved.pose);
                if (!report(solved,time,visitor,reported)) return false;
                while (solved.obstacle!=null && !present(solved.obstacle)) {
                    solved=radial(event.pose,candidates,EVENT_TOLERANCE*2);
                    knot(knots,time,solved.pose);
                    if (!report(solved,time,visitor,reported)) return false;
                }
            }
            knot(knots,end,last.pose);
            if (!report(last,end,visitor,reported)) return false;
            return true;
        }
        double time=(start+end)/2;
        return swipeInterval(a,middle,start,time,candidates,visitor,knots,reported)
                && swipeInterval(middle,b,time,end,candidates,visitor,knots,reported);
    }

    public static Trace trace(Pose from, Pose to, Scene scene) {
        Contact contact = trace(from, to, 0, 1, scene);
        // Within an observed interval yaw/pitch/pivot/extension interpolate parametrically.
        // Thousands of redundant subdivision poses are not needed to render that SAME curve.
        // A shortening impact retains both the approached and shortened pose at the contact time.
        return new Trace(contact==null ? List.of(new Knot(0,from),new Knot(1,to))
                : List.of(new Knot(0,from),new Knot(contact.fraction,interpolate(from,to,contact.fraction)),
                    new Knot(contact.fraction,contact.pose)),contact);
    }
    private static Contact trace(Pose a, Pose b, double start, double end, Scene scene) {
        double motion = motionBound(a,b);
        Pose middle = interpolate(a,b,.5);
        if (motion > BROAD_PHASE_MOTION) {
            Contact first = trace(a,middle,start,(start+end)/2,scene);
            return first != null ? first : trace(middle,b,(start+end)/2,end,scene);
        }
        Contact first = null;
        for (Candidate candidate : scene.candidates(middle, motion/2)) {
            double fraction = firstTime(a,b,candidate,0,1);
            if (Double.isNaN(fraction)) continue;
            double global = start+(end-start)*fraction;
            // Terrain wins coincident contacts, then stable UUID. Never let iteration order shoot through walls.
            if (first == null || global < first.fraction-1e-12 || Math.abs(global-first.fraction)<1e-12
                    && compare(candidate,first.obstacle)<0) {
                Pose pose = interpolate(a,b,fraction);
                if (candidate.shaftBlocks && pose.shaft().intersects(candidate.bounds, CONTACT_TOLERANCE*2)) {
                    // A live turn can put terrain inside the shaft before the distant blade reaches it.
                    // Retain the collision-shortened pose for rendering; the shaft never damages anything.
                    Vec3 center = candidate.bounds.getCenter().subtract(pose.pivot());
                    Vec3 direction = pose.forward();
                    double extent = Math.abs(direction.x)*candidate.bounds.getXsize()/2
                            + Math.abs(direction.y)*candidate.bounds.getYsize()/2 + Math.abs(direction.z)*candidate.bounds.getZsize()/2;
                    pose = pose.withDistance(Math.min(pose.tipDistance(), Math.max(0,center.dot(direction)-extent-CONTACT_TOLERANCE)));
                }
                first = new Contact(global,pose,candidate);
            }
        }
        return first;
    }
    private static int compare(Candidate a, Candidate b) {
        int kind = a.kind.compareTo(b.kind);
        if (kind!=0) return kind;
        if (a.entity!=null && b.entity!=null) return a.entity.getUUID().compareTo(b.entity.getUUID());
        int x=Double.compare(a.bounds.minX,b.bounds.minX),y=Double.compare(a.bounds.minY,b.bounds.minY);
        return x!=0 ? x : y!=0 ? y : Double.compare(a.bounds.minZ,b.bounds.minZ);
    }
    /** Branch-and-bound against curved poses, not a tip chord. Terminal uncertainty <= 1/4096 block. */
    private static double firstTime(Pose a, Pose b, Candidate c, double low, double high) {
        return firstTime(a,b,c,low,high,false);
    }
    private static double firstTime(Pose a, Pose b, Candidate c, double low, double high,boolean radial) {
        Pose mid = interpolate(a,b,.5);
        double bound = motionBound(a,b)/2;
        if (radial ? !mid.radialBlade().intersects(c.bounds,bound)
                : !mid.blade().intersects(c.bounds,bound) && !(c.shaftBlocks && mid.shaft().intersects(c.bounds,bound))) return Double.NaN;
        // At the world border a coordinate ULP can exceed the event tolerance. Once a midpoint
        // rounds to an endpoint, this interval cannot be split further in the shared pose representation.
        if (bound <= (radial ? EVENT_TOLERANCE:CONTACT_TOLERANCE) || mid.equals(a) || mid.equals(b)) return low;
        double left = firstTime(a,mid,c,low,(low+high)/2,radial);
        return Double.isNaN(left) ? firstTime(mid,b,c,(low+high)/2,high,radial) : left;
    }

    public static Scene world(ServerPlayer player) {
        return world(player,false);
    }
    public static Scene world(ServerPlayer player,boolean radial) {
        Map<BlockPos,List<Candidate>> terrain=new HashMap<>();
        return (pose, movement) -> {
            var level = player.serverLevel();
            var result = new ArrayList<Candidate>();
            var context = CollisionContext.of(player);
            // Enumerate small overlapping neighborhoods ALONG the weapon, never its large diagonal bounding cube.
            Set<BlockPos> visited = new HashSet<>();
            Set<BlockPos> centers = new HashSet<>();
            Vec3 end = pose.tip(), start = pose.pivot();
            int steps = Math.max(1,(int)Math.ceil(start.distanceTo(end)*2));
            int radius = (int)Math.ceil(BLADE_WIDTH+movement)+1;
            for (int i=0;i<=steps;i++) {
                BlockPos center = BlockPos.containing(start.lerp(end,(double)i/steps));
                if (!centers.add(center)) continue;
                for (int x=-radius;x<=radius;x++) for (int y=-radius;y<=radius;y++) for (int z=-radius;z<=radius;z++) {
                    BlockPos pos = center.offset(x,y,z);
                    if (!visited.add(pos)) continue;
                    var cached=terrain.get(pos);
                    if (cached!=null && cached.isEmpty()) continue;
                    AABB unit = new AABB(pos);
                    if (radial ? !pose.radialBlade().intersects(unit.inflate(1),movement)
                            : !pose.blade().intersects(unit.inflate(1),movement) && !pose.shaft().intersects(unit.inflate(1),movement)) continue;
                    if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos) || level.isOutsideBuildHeight(pos)) {
                        result.add(new Candidate(unit,Kind.UNKNOWN_TERRAIN,null,true)); continue;
                    }
                    var boxes=terrain.get(pos);
                    if (boxes==null) {
                        BlockState state=level.getBlockState(pos); // Guarded: never requests a missing chunk.
                        boxes=state.isAir() ? List.of() : state.getCollisionShape(level,pos,context).toAabbs().stream()
                                .map(box->new Candidate(box.move(pos),Kind.BLOCK,null,true)).toList();
                        terrain.put(pos,boxes);
                    }
                    result.addAll(boxes);
                }
            }
            for (Entity entity : level.getEntities(player,(radial ? pose.radialBlade():pose.blade()).bounds(movement),
                    e -> !e.isSpectator() && e.isAlive() && e.isPickable()))
                result.add(new Candidate(entity.getBoundingBox(),Kind.ENTITY,entity,false));
            return mergeTerrain(result);
        };
    }

    /** Exact union simplification: a tiled flat floor must not create thousands of artificial contact transitions.
     * Merge only touching/overlapping boxes with IDENTICAL extents on the other two axes. Holes and partial
     * collision shapes remain holes; entities retain their identity and ordinary contact order. */
    public static List<Candidate> mergeTerrain(List<Candidate> source) {
        var terrain=new ArrayList<Candidate>(); var entities=new ArrayList<Candidate>();
        for (var c:source) { if (c.kind==Kind.ENTITY) entities.add(c); else terrain.add(c); }
        for (int axis:new int[]{0,2,1}) {
            int u=(axis+1)%3,v=(axis+2)%3;
            terrain.sort(Comparator.comparing((Candidate c)->c.kind).thenComparing(c->c.shaftBlocks)
                    .thenComparingDouble(c->min(c.bounds,u)).thenComparingDouble(c->max(c.bounds,u))
                    .thenComparingDouble(c->min(c.bounds,v)).thenComparingDouble(c->max(c.bounds,v))
                    .thenComparingDouble(c->min(c.bounds,axis)));
            var merged=new ArrayList<Candidate>();
            for (var c:terrain) {
                if (!merged.isEmpty()) {
                    var last=merged.getLast(); var a=last.bounds; var b=c.bounds;
                    if (last.kind==c.kind && last.shaftBlocks==c.shaftBlocks
                            && min(a,u)==min(b,u) && max(a,u)==max(b,u) && min(a,v)==min(b,v) && max(a,v)==max(b,v)
                            && min(b,axis)<=max(a,axis)) {
                        merged.set(merged.size()-1,new Candidate(a.minmax(b),last.kind,null,last.shaftBlocks));
                        continue;
                    }
                }
                merged.add(c);
            }
            terrain=merged;
        }
        terrain.addAll(entities); return terrain;
    }
    private static double min(AABB b,int axis) { return axis==0 ? b.minX:axis==1 ? b.minY:b.minZ; }
    private static double max(AABB b,int axis) { return axis==0 ? b.maxX:axis==1 ? b.maxY:b.maxZ; }
}
