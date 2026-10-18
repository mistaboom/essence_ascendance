package com.mistaboom.essence_ascendance.equipment;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/** World-space attachment contract, shared by authority and presentation. No texture repeat/range math. */
public final class WeaponActionGeometry {
    private WeaponActionGeometry() { }
    public static final double PIXEL = 1.0 / 32;
    // Live 32px artwork: butt/grip (2,30), blade attachment (17,15), shaft axis (+1,-1).
    // Blade union projected from ALL opaque pixel corners relative to (17,15): axial [0,29], lateral [-5,6].
    public static final double DIAGONAL_PIXEL = PIXEL / Math.sqrt(2);
    public static final double BLADE_LENGTH = 29 * DIAGONAL_PIXEL;
    public static final double BLADE_WIDTH = 11 * DIAGONAL_PIXEL;
    public static final double BLADE_SIDE_OFFSET = .5 * DIAGONAL_PIXEL;
    public static final double THICKNESS = PIXEL;
    public static final double SHAFT_WIDTH = 3 * DIAGONAL_PIXEL;
    // The hand chokes up fourteen projected texels from the authored butt. Range is still hand-to-tip.
    public static final double GRIP_OFFSET = 14 * DIAGONAL_PIXEL;
    public static final double REST_ATTACHMENT = 30 * DIAGONAL_PIXEL - GRIP_OFFSET;
    public static final double REST_TIP = REST_ATTACHMENT + BLADE_LENGTH;
    public static final double CONTACT_TOLERANCE = 1.0 / 4096;
    public static final double BROAD_PHASE_MOTION = .5;
    public record Knot(double fraction,Pose pose) { }

    /** Physical grip pivot and converged aim, with independent aimed-plane sweep and stable camera roll. */
    public record Pose(Vec3 pivot, double yaw, double pitch, double tipDistance, double sweepDegrees, double roll) {
        public Pose(Vec3 pivot, double yaw, double pitch, double tipDistance) { this(pivot,yaw,pitch,tipDistance,0,0); }
        public Pose(Vec3 pivot, double yaw, double pitch, double tipDistance,double sweep) { this(pivot,yaw,pitch,tipDistance,sweep,0); }
        private Vec3 aimForward() {
            double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
            return new Vec3(-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p));
        }
        // Yaw supplies roll at vertical aim. No world-up cross product singularity or fallback flip.
        private Vec3 aimRight() {
            double y=Math.toRadians(yaw),r=Math.toRadians(roll);
            Vec3 right=new Vec3(Math.cos(y),0,Math.sin(y));
            return right.scale(Math.cos(r)).add(aimForward().cross(right).scale(Math.sin(r)));
        }
        /** Rotate IN the live aimed plane, rather than adding an offset to world yaw. */
        public Vec3 forward() {
            double angle=Math.toRadians(sweepDegrees);
            return aimForward().scale(Math.cos(angle)).add(aimRight().scale(Math.sin(angle)));
        }
        public Vec3 right() {
            double angle=Math.toRadians(sweepDegrees);
            return aimRight().scale(Math.cos(angle)).subtract(aimForward().scale(Math.sin(angle)));
        }
        public Vec3 up() { return forward().cross(right()); }
        public Vec3 tip() { return pivot.add(forward().scale(tipDistance)); }
        public Vec3 attachment() { return pivot.add(forward().scale(tipDistance - BLADE_LENGTH)); }
        public Pose withDistance(double distance) { return new Pose(pivot, yaw, pitch, Math.max(0, distance),sweepDegrees,roll); }
        public Pose withSweep(double angle) { return new Pose(pivot,yaw,pitch,tipDistance,angle,roll); }
        /** Union of every rigid blade position during radial extension from front distance zero. */
        public Box radialBlade() {
            return new Box(pivot.add(forward().scale((tipDistance-BLADE_LENGTH)/2)).add(right().scale(BLADE_SIDE_OFFSET)),
                    forward(),right(),up(),(tipDistance+BLADE_LENGTH)/2,BLADE_WIDTH/2,THICKNESS/2);
        }
        public Box blade() {
            return new Box(pivot.add(forward().scale(tipDistance - BLADE_LENGTH / 2))
                    .add(right().scale(BLADE_SIDE_OFFSET)), forward(), right(), up(),
                    BLADE_LENGTH / 2, BLADE_WIDTH / 2, THICKNESS / 2);
        }
        /** Terrain occlusion only; entities never receive shaft damage. */
        public Box shaft() {
            double length = Math.max(0, tipDistance - BLADE_LENGTH);
            return new Box(pivot.add(forward().scale(length / 2)), forward(), right(), up(),
                    length / 2, SHAFT_WIDTH / 2, THICKNESS / 2);
        }
    }

    public static double unwrapYaw(double previous, double observed) {
        double delta = (observed - previous) % 360;
        if (delta >= 180) delta -= 360;
        if (delta < -180) delta += 360;
        return previous + delta;
    }
    public static Pose interpolate(Pose a, Pose b, double t) {
        return new Pose(a.pivot.lerp(b.pivot, t), a.yaw + (b.yaw - a.yaw) * t,
                a.pitch + (b.pitch - a.pitch) * t, a.tipDistance + (b.tipDistance - a.tipDistance) * t,
                a.sweepDegrees+(b.sweepDegrees-a.sweepDegrees)*t,a.roll+(b.roll-a.roll)*t);
    }
    /** Lipschitz bound on EVERY blade/shaft point, including translation, extension and both aim rotations. */
    public static double motionBound(Pose a, Pose b) {
        return a.pivot.distanceTo(b.pivot) + Math.abs(a.tipDistance - b.tipDistance)
                + (Math.toRadians(Math.abs(b.yaw - a.yaw) + Math.abs(b.pitch - a.pitch)+Math.abs(b.sweepDegrees-a.sweepDegrees)+Math.abs(b.roll-a.roll)))
                * (Math.max(a.tipDistance, b.tipDistance) + BLADE_LENGTH + BLADE_WIDTH);
    }

    public record Box(Vec3 center, Vec3 forward, Vec3 right, Vec3 up, double length, double width, double thickness) {
        public List<Vec3> axes() { return List.of(forward, right, up); }
        public AABB bounds(double inflation) {
            double x = Math.abs(forward.x)*length + Math.abs(right.x)*width + Math.abs(up.x)*thickness + inflation;
            double y = Math.abs(forward.y)*length + Math.abs(right.y)*width + Math.abs(up.y)*thickness + inflation;
            double z = Math.abs(forward.z)*length + Math.abs(right.z)*width + Math.abs(up.z)*thickness + inflation;
            return new AABB(center.x-x, center.y-y, center.z-z, center.x+x, center.y+y, center.z+z);
        }
        /** Full 15-axis OBB/AABB separating-axis test. Inflation is a conservative Euclidean movement bound. */
        public boolean intersects(AABB target, double inflation) {
            Vec3 delta = target.getCenter().subtract(center);
            var world = List.of(new Vec3(1,0,0), new Vec3(0,1,0), new Vec3(0,0,1));
            for (Vec3 axis : world) if (separates(axis, delta, target, inflation)) return false;
            for (Vec3 axis : axes()) {
                if (separates(axis, delta, target, inflation)) return false;
                for (Vec3 w : world) if (separates(axis.cross(w), delta, target, inflation)) return false;
            }
            return true;
        }
        /** Exact continuous translation SAT. Returns the first distance, never a center/ray approximation. */
        public double firstTranslation(AABB target, Vec3 direction, double maximum, double inflation) {
            double[] interval={0,maximum};
            Vec3 delta=target.getCenter().subtract(center);
            var world=List.of(new Vec3(1,0,0),new Vec3(0,1,0),new Vec3(0,0,1));
            for (Vec3 axis:world) if (!translationAxis(axis,delta,target,direction,inflation,interval)) return Double.NaN;
            for (Vec3 axis:axes()) {
                if (!translationAxis(axis,delta,target,direction,inflation,interval)) return Double.NaN;
                for (Vec3 w:world) if (!translationAxis(axis.cross(w),delta,target,direction,inflation,interval)) return Double.NaN;
            }
            return interval[0];
        }
        private boolean translationAxis(Vec3 axis,Vec3 delta,AABB target,Vec3 direction,double inflation,double[] interval) {
            double magnitude=axis.length(); if (magnitude<1e-10) return true;
            double radius=projectedRadius(axis,target,inflation);
            double position=delta.dot(axis),speed=direction.dot(axis);
            if (Math.abs(speed)<1e-12) return Math.abs(position)<=radius+1e-10;
            double a=(position-radius)/speed,b=(position+radius)/speed;
            interval[0]=Math.max(interval[0],Math.min(a,b)); interval[1]=Math.min(interval[1],Math.max(a,b));
            return interval[0]<=interval[1]+1e-10;
        }
        private boolean separates(Vec3 axis, Vec3 delta, AABB target, double inflation) {
            double magnitude = axis.length();
            if (magnitude < 1e-10) return false;
            return Math.abs(delta.dot(axis)) > projectedRadius(axis,target,inflation) + 1e-10;
        }
        private double projectedRadius(Vec3 axis,AABB target,double inflation) {
            return Math.abs(axis.dot(forward))*length + Math.abs(axis.dot(right))*width
                    + Math.abs(axis.dot(up))*thickness + inflation*axis.length()
                    + Math.abs(axis.x)*target.getXsize()/2 + Math.abs(axis.y)*target.getYsize()/2
                    + Math.abs(axis.z)*target.getZsize()/2;
        }
    }
}
