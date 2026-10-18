package com.mistaboom.essence_ascendance.equipment;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import static com.mistaboom.essence_ascendance.equipment.WeaponActionGeometry.*;

/** Authoritative crosshair focus. This guides orientation only; finite blade collision still decides contact. */
public final class WeaponActionAim {
    private WeaponActionAim() { }
    public static Pose pose(Player player,Pose previous,double distance,double range) {
        Pose view=GlaiveGrip.frame(player.getEyePosition(),player.getYRot(),Math.clamp(player.getXRot(),-90,90));
        return GlaiveGrip.rigidAim(view,player.yBodyRot,player.getMainArm(),player.getScale(),target(player,view,range),distance,previous);
    }
    public static Vec3 target(Player player,Pose view,double range) {
        var level=player.level(); Vec3 start=view.pivot(),end=start.add(view.forward().scale(range));
        var context=CollisionContext.of(player);
        Vec3 block=BlockGetter.traverseBlocks(start,end,player,(ignored,pos)-> {
            // Never load terrain just to aim a long weapon. Unknown cells terminate the focus ray.
            if (!level.hasChunkAt(pos) || level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos))
                return new AABB(pos).clip(start,end).orElse(start);
            var hit=level.getBlockState(pos).getCollisionShape(level,pos,context).clip(start,end,pos);
            return hit==null ? null:hit.getLocation();
        },ignored->null);
        Vec3 nearest=block; double limit=block==null ? range*range:start.distanceToSqr(block);
        for (var entity:level.getEntities(player,new AABB(start,end).inflate(1),e->e.isAlive() && !e.isSpectator() && e.isPickable())) {
            var bounds=entity.getBoundingBox().inflate(entity.getPickRadius());
            var hit=bounds.contains(start) ? start:bounds.clip(start,end).orElse(null);
            if (hit!=null && start.distanceToSqr(hit)<limit) { nearest=hit; limit=start.distanceToSqr(hit); }
        }
        return nearest;
    }
}
