package com.mistaboom.essence_ascendance.movement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import static com.mistaboom.essence_ascendance.movement.TraversalCapabilities.Capability.*;

/** Native collision/fluid policy shared by Fabric, NeoForge and local prediction. No velocity
 * replacement, block placement, position correction, potion, permanent fire immunity or custom timer. */
public final class TraversalService {
    public static final TagKey<Block> DRAG_BLOCKS = TagKey.create(Registries.BLOCK, id("traversal_drag"));
    public static final TagKey<Block> INSIDE_BLOCKS = TagKey.create(Registries.BLOCK, id("traversal_inside"));
    public static final TagKey<Block> FIRM_BLOCKS = TagKey.create(Registries.BLOCK, id("traversal_firm_footing"));
    public static final TagKey<Fluid> SURFACE_FLUIDS = TagKey.create(Registries.FLUID, id("traversal_surfaces"));
    public static final TagKey<DamageType> TERRAIN_DAMAGE = TagKey.create(Registries.DAMAGE_TYPE, id("terrain_contact"));
    public static final TagKey<DamageType> IMMERSION_DAMAGE = TagKey.create(Registries.DAMAGE_TYPE, id("lava_immersion"));
    private TraversalService() { }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("essence_ascendance", path); }

    public static boolean nativeMovement(Entity entity) {
        return entity instanceof Player player && player.isAlive() && !player.isRemoved() && !player.isSpectator()
                && !player.isSleeping() && !player.isPassenger() && !player.isFallFlying() && !player.getAbilities().flying;
    }
    public static float terrainFactor(Entity entity, float nativeFactor) {
        float result = TraversalCapabilities.has(entity, TERRAIN_DRAG) ? TraversalRules.withoutPenalty(nativeFactor) : nativeFactor;
        if (result != nativeFactor && entity instanceof net.minecraft.server.level.ServerPlayer player)
            com.mistaboom.essence_ascendance.skill.effect.SkillHudEvents.record(player, com.mistaboom.essence_ascendance.skill.SkillIds.TERRAIN_FREEDOM, "terrain", 1);
        return result;
    }
    public static boolean ignoresDrag(Entity entity, BlockState block) {
        return block.is(DRAG_BLOCKS) && TraversalCapabilities.has(entity, TERRAIN_DRAG);
    }
    public static boolean ignoresInside(Entity entity, BlockState block) {
        // Only explicitly opted-in terrain callbacks: never suppress portals, fluids, triggers or arbitrary blocks.
        return block.is(INSIDE_BLOCKS) && TraversalCapabilities.has(entity, TERRAIN_DRAG);
    }
    public static boolean preventsFreezing(Entity entity) { return TraversalCapabilities.has(entity, TERRAIN_CONTACT); }
    public static boolean protectsDamage(LivingEntity entity, DamageSource source) {
        if (source == null || !(entity instanceof Player)) return false;
        boolean protectedContact = TraversalRules.protectsContact(TraversalCapabilities.has(entity, TERRAIN_CONTACT),
                TraversalCapabilities.has(entity, LAVA_PROTECTION), entity.isInLava(),
                source.is(TERRAIN_DAMAGE), source.is(IMMERSION_DAMAGE),
                source.getEntity() != null || source.getDirectEntity() != null, source.is(DamageTypeTags.BYPASSES_INVULNERABILITY));
        if (protectedContact && entity instanceof net.minecraft.server.level.ServerPlayer player)
            com.mistaboom.essence_ascendance.skill.effect.SkillHudEvents.record(player, source.is(TERRAIN_DAMAGE) ? com.mistaboom.essence_ascendance.skill.SkillIds.TERRAIN_FREEDOM : com.mistaboom.essence_ascendance.skill.SkillIds.LAVABORN, "terrain", 1);
        return protectedContact;
    }
    public static boolean lavaBody(Entity entity) {
        return nativeMovement(entity) && entity.isInLava() && TraversalCapabilities.has(entity, LAVA_BODY);
    }
    public static boolean fluidBody(Entity entity) {
        return nativeMovement(entity) && (entity.isInWater() && TraversalCapabilities.has(entity, WATER_BODY) || lavaBody(entity));
    }
    public static boolean swimmingPose(Entity entity, boolean original) {
        return original || lavaBody(entity) && entity.isSprinting()
                && (entity.isSwimming() || entity.isEyeInFluid(FluidTags.LAVA));
    }
    /** Scoped query for native swimming only. Actual water/eye state remains untouched everywhere else. */
    public static boolean swimmingWater(Entity entity, boolean nativeWater) { return nativeWater || lavaBody(entity); }
    public static boolean swimmingUnderwater(Entity entity, boolean nativeUnderwater) {
        return nativeUnderwater || lavaBody(entity) && entity.isEyeInFluid(FluidTags.LAVA);
    }
    public static boolean surfaceActive(Entity entity) {
        return entity instanceof Player player && TraversalRules.surfaceEnabled(
                TraversalCapabilities.has(player, LIQUID_SURFACE), nativeMovement(player),
                player.isSprinting(), com.mistaboom.essence_ascendance.equipment.EquipmentFishingRodService.holdsRod(player), player.isShiftKeyDown());
    }
    public static boolean surfaceBuoyancyEligible(Player player) { return Double.isFinite(buoyancySurfaceY(player)); }
    private static double buoyancySurfaceY(Player player) {
        boolean rod = com.mistaboom.essence_ascendance.equipment.EquipmentFishingRodService.holdsRod(player);
        if (!rod || !surfaceActive(player) || player.isEyeInFluid(FluidTags.WATER) || player.isEyeInFluid(FluidTags.LAVA)) return Double.NaN;
        BlockPos feet = player.blockPosition();
        double surfaceY = Double.NaN;
        for (int dy = 0; dy <= 1; dy++) {
            BlockPos pos = feet.above(dy);
            var shape = fluidSurface(player.level().getBlockState(pos), player.level(), pos);
            if (!shape.isEmpty()) surfaceY = pos.getY() + shape.max(net.minecraft.core.Direction.Axis.Y);
        }
        return TraversalRules.rodBuoyancy(TraversalCapabilities.has(player, HEAD_ABOVE_LIQUID_BUOYANCY), nativeMovement(player), rod,
                player.isShiftKeyDown(), false, player.getY(), player.getEyeY(), surfaceY) ? surfaceY : Double.NaN;
    }
    /** Server-only limited surface buoyancy. Collision support remains in the shared prediction seam. */
    public static void tickSurfaceBuoyancy(net.minecraft.server.level.ServerPlayer player) {
        if (!com.mistaboom.essence_ascendance.config.EssenceConfigManager.authoritativeReady()) return;
        double surfaceY = buoyancySurfaceY(player);
        if (!Double.isFinite(surfaceY)) return;
        PlayerMotionTracker.track(player, com.mistaboom.essence_ascendance.config.EssenceConfigManager.skillEffects().posture().movement());
        double speed = Math.min(com.mistaboom.essence_ascendance.config.EssenceConfigManager.get().fishingRodBalance().buoyancySpeed(), surfaceY - player.getY());
        if (!player.level().noCollision(player, player.getBoundingBox().move(0, speed, 0))) return;
        // Counter gravity and gently close only the remaining surface gap. Do not raise a submerged head.
        var prior = PlayerMotionTracker.velocity(player);
        double gravity = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.GRAVITY);
        double addition = Math.min(speed - prior.y, gravity + speed);
        if (addition <= 0) return;
        player.setDeltaMovement(prior.add(0, addition, 0));
        //? if <26.1.2
        player.hasImpulse = true;
        player.hurtMarked = true;
        PlayerMotionTracker.forced(player);
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(player));
    }
    private static VoxelShape fluidSurface(BlockState block, BlockGetter level, BlockPos pos) {
        if (!(block.getBlock() instanceof LiquidBlock)) return Shapes.empty(); // No waterlogged-solid replacement.
        FluidState fluid = block.getFluidState();
        if (!fluid.is(SURFACE_FLUIDS)) return Shapes.empty();
        double height = fluid.getHeight(level, pos);
        return TraversalRules.exposedSurface(height, level.getFluidState(pos.above()).isEmpty())
                ? Shapes.box(0, 0, 0, 1, height, 1) : Shapes.empty();
    }
    public static VoxelShape collision(BlockState block, BlockGetter level, BlockPos pos,
                                       CollisionContext context, VoxelShape original) {
        if (!(context instanceof EntityCollisionContext entityContext)) return original;
        Entity entity = entityContext.getEntity();
        if (!(entity instanceof Player)) return original;
        VoxelShape added = Shapes.empty();
        if (block.is(FIRM_BLOCKS) && TraversalCapabilities.has(entity, FIRM_FOOTING)) added = Shapes.block();
        else if (block.getBlock() instanceof LiquidBlock && surfaceActive(entity)) added = fluidSurface(block, level, pos);
        // Native isAbove includes the native collision tolerance. Never lift or entomb an already submerged player.
        if (added.isEmpty()) return original;
        boolean fromAbove = context.isAbove(added, pos, true);
        // A neighboring higher surface may participate in native stepping, but never boost an
        // immersed or airborne entity out of liquid. Native collision still decides headroom.
        boolean nativeStep = !fromAbove && !entity.getBoundingBox().intersects(added.bounds().move(pos.getX(), pos.getY(), pos.getZ()))
                && TraversalRules.canStepOntoSurface(entity.getY(),
                pos.getY() + added.max(net.minecraft.core.Direction.Axis.Y), entity.maxUpStep(),
                entity.onGround(), entity.isInWater() || entity.isInLava());
        return fromAbove || nativeStep ? Shapes.or(original, added) : original;
    }
    public static boolean canStandOnFluid(LivingEntity entity, FluidState fluid) {
        if (!fluid.is(SURFACE_FLUIDS) || !surfaceActive(entity)) return false;
        BlockPos feet = entity.blockPosition();
        return exposedAtFeet(entity, feet) || exposedAtFeet(entity, feet.below());
    }
    private static boolean exposedAtFeet(Entity entity, BlockPos pos) {
        var surface = fluidSurface(entity.level().getBlockState(pos), entity.level(), pos);
        return !surface.isEmpty() && CollisionContext.of(entity).isAbove(surface, pos, true);
    }
    public static boolean clearWaterVision(Entity entity) { return TraversalCapabilities.has(entity, WATER_VISION); }
    public static boolean clearLavaVision(Entity entity) { return TraversalCapabilities.has(entity, LAVA_VISION); }
    public static boolean protectedLavaImmersion(Entity entity) {
        return entity != null && entity.isInLava() && TraversalCapabilities.has(entity, LAVA_PROTECTION);
    }
    public static boolean hideImmersionFire(Entity entity) { return protectedLavaImmersion(entity); }
}
