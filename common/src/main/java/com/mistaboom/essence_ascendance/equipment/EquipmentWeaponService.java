package com.mistaboom.essence_ascendance.equipment;

import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import com.mistaboom.essence_ascendance.stat.EssenceStats;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/*
 * Server-authoritative gameplay layer for Ascendance ranged and magic weapons.
 *
 * The same architecture used by melee/tools is preserved here:
 *
 *   tier + equipment profile baseline
 *       -> applicable invested stat bonus
 *       -> final gameplay value
 *
 * RANGED
 * ------
 * The native Ascendance bow remains a real BowItem. This service supplies:
 * - resolved nominal ranged damage,
 * - resolved full-draw rate / draw ticks,
 * - projectile-speed multiplier,
 * - arrow base-damage compensation so projectile_speed changes trajectory
 *   without secretly becoming another damage stat.
 *
 * MAGIC
 * -----
 * The native magic focus performs a deliberately simple neutral hitscan cast.
 * It requires no custom entity registration and gives the magic baseline/stat
 * pipeline a real gameplay target before spell families are designed later.
 */
public final class EquipmentWeaponService {

    public static final double MAGIC_RANGE_BLOCKS = 24.0;

    public static final int VANILLA_BOW_FULL_DRAW_TICKS = 20;

    private static final String RANGED_FULL_DRAW_TICKS_TAG =
            "essence_ascendance_ranged_full_draw_ticks";

    private static final double VANILLA_BOW_FULL_SPEED = 3.0;
    private static final double VANILLA_ARROW_BASE_DAMAGE = 2.0;
    private static final double EPSILON = 0.0000001;

    private static final ThreadLocal<RangedShotContext> RANGED_SHOT_CONTEXT =
            new ThreadLocal<>();

    private static final Map<ServerPlayer, RangedShotEvaluation> LAST_RANGED_SHOT =
            new WeakHashMap<>();

    private static final Map<ServerPlayer, MagicCastEvaluation> LAST_MAGIC_CAST =
            new WeakHashMap<>();

    private EquipmentWeaponService() {
    }

    public static RangedState evaluateRanged(
            ServerPlayer player,
            ItemStack stack
    ) {
        PlayerEssenceData playerData = playerData(player);
        requireProfile(stack, EquipmentProfiles.RANGED_WEAPON.id());

        EquipmentStatState held = EquipmentStatResolver.evaluateItem(
                stack,
                EquipmentActivationType.HELD
        );

        EquipmentBaselineResult baseline = EquipmentBaselineService.evaluate(
                playerData,
                EquipmentProfiles.RANGED_WEAPON.id()
        );

        double finalDamage = EquipmentValueService.applyPercentBonus(
                playerData,
                EssenceStats.RANGED_DAMAGE,
                held.strength(EssenceStats.RANGED_DAMAGE),
                baseline.rangedDamage()
        );

        double finalAttackSpeed = EquipmentValueService.applyPercentBonus(
                playerData,
                EssenceStats.RANGED_ATTACK_SPEED,
                held.strength(EssenceStats.RANGED_ATTACK_SPEED),
                baseline.rangedAttackSpeed()
        );

        double projectileSpeedPercent = EquipmentValueService.scaledBonus(
                playerData,
                EssenceStats.PROJECTILE_SPEED,
                held.strength(EssenceStats.PROJECTILE_SPEED)
        );

        double projectileSpeedMultiplier = Math.max(
                EPSILON,
                1.0 + projectileSpeedPercent / 100.0
        );

        return new RangedState(
                baseline.rangedDamage(),
                finalDamage,
                baseline.rangedAttackSpeed(),
                finalAttackSpeed,
                rateToTicks(finalAttackSpeed),
                projectileSpeedPercent,
                projectileSpeedMultiplier
        );
    }

    public static MagicState evaluateMagic(
            ServerPlayer player,
            ItemStack stack
    ) {
        PlayerEssenceData playerData = playerData(player);
        requireProfile(stack, EquipmentProfiles.MAGIC_FOCUS.id());

        EquipmentStatState held = EquipmentStatResolver.evaluateItem(
                stack,
                EquipmentActivationType.HELD
        );

        EquipmentBaselineResult baseline = EquipmentBaselineService.evaluate(
                playerData,
                EquipmentProfiles.MAGIC_FOCUS.id()
        );

        double finalDamage = EquipmentValueService.applyPercentBonus(
                playerData,
                EssenceStats.MAGIC_DAMAGE,
                held.strength(EssenceStats.MAGIC_DAMAGE),
                baseline.magicDamage()
        );

        double finalCastSpeed = EquipmentValueService.applyPercentBonus(
                playerData,
                EssenceStats.MAGIC_CAST_SPEED,
                held.strength(EssenceStats.MAGIC_CAST_SPEED),
                baseline.magicCastSpeed()
        );

        return new MagicState(
                baseline.magicDamage(),
                finalDamage,
                baseline.magicCastSpeed(),
                finalCastSpeed,
                rateToTicks(finalCastSpeed)
        );
    }

    /*
     * Keep the resolved full-draw duration on the concrete held ItemStack.
     * ItemStack components are synchronized by Minecraft, which gives the
     * client-side model predicate the one small piece of authoritative state
     * it needs without introducing a separate networking protocol.
     *
     * CUSTOM_DATA is updated rather than replaced so unrelated namespaced
     * custom data remains intact.
     */
    public static void syncRangedVisualState(ServerPlayer player) {
        syncRangedVisualState(player, player.getMainHandItem());
        syncRangedVisualState(player, player.getOffhandItem());
    }

    private static void syncRangedVisualState(
            ServerPlayer player,
            ItemStack stack
    ) {
        if (stack.isEmpty()
                || !(stack.getItem() instanceof EquipmentProfileItem profileItem)
                || !profileItem.equipmentProfileId().equals(
                EquipmentProfiles.RANGED_WEAPON.id()
        )) {
            return;
        }

        int resolvedTicks = evaluateRanged(player, stack).fullDrawTicks();
        int currentTicks = syncedRangedFullDrawTicks(stack);

        if (currentTicks == resolvedTicks) {
            return;
        }

        CustomData.update(
                DataComponents.CUSTOM_DATA,
                stack,
                tag -> tag.putInt(
                        RANGED_FULL_DRAW_TICKS_TAG,
                        resolvedTicks
                )
        );
    }

    /*
     * Client-safe accessor. If the stack has not received its first server
     * synchronization yet, vanilla's 20-tick full-draw visual is a safe
     * fallback for that brief period.
     */
    public static int syncedRangedFullDrawTicks(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);

        if (customData == null) {
            return VANILLA_BOW_FULL_DRAW_TICKS;
        }

        CompoundTag tag = customData.copyTag();
        int ticks = tag.getInt(RANGED_FULL_DRAW_TICKS_TAG);

        return ticks > 0
                ? ticks
                : VANILLA_BOW_FULL_DRAW_TICKS;
    }

    /*
     * BowItem internally treats 20 use ticks as a full vanilla draw. Convert
     * the player's real held time into an equivalent vanilla held time so the
     * existing BowItem release logic, ammo handling, enchantments and crit
     * behavior remain native.
     */
    public static int syntheticBowUseTicks(
            RangedState state,
            int actualUseTicks
    ) {
        if (actualUseTicks <= 0) {
            return 0;
        }

        return syntheticBowUseTicks(
                state.fullDrawTicks(),
                actualUseTicks
        );
    }

    public static int syntheticBowUseTicks(
            int fullDrawTicks,
            int actualUseTicks
    ) {
        if (actualUseTicks <= 0) {
            return 0;
        }

        int safeFullDrawTicks = Math.max(1, fullDrawTicks);
        double scaled = actualUseTicks
                * (double) VANILLA_BOW_FULL_DRAW_TICKS
                / safeFullDrawTicks;
        return Math.max(0, (int) Math.round(scaled));
    }

    public static void withRangedShotContext(
            ServerPlayer player,
            RangedState state,
            int actualUseTicks,
            int syntheticUseTicks,
            Runnable action
    ) {
        RangedShotContext previous = RANGED_SHOT_CONTEXT.get();

        RANGED_SHOT_CONTEXT.set(
                new RangedShotContext(
                        player,
                        state,
                        actualUseTicks,
                        syntheticUseTicks
                )
        );

        try {
            action.run();
        } finally {
            if (previous == null) {
                RANGED_SHOT_CONTEXT.remove();
            } else {
                RANGED_SHOT_CONTEXT.set(previous);
            }
        }
    }

    /*
     * Called from AscendanceRangedWeaponItem#shootProjectile while vanilla is
     * creating each projectile for the release currently wrapped above.
     */
    public static float configureRangedProjectile(
            LivingEntity shooter,
            Projectile projectile,
            float vanillaVelocity
    ) {
        RangedShotContext context = RANGED_SHOT_CONTEXT.get();

        if (!(shooter instanceof ServerPlayer player)
                || context == null
                || context.player() != player) {
            return vanillaVelocity;
        }

        RangedState state = context.state();
        double speedMultiplier = state.projectileSpeedMultiplier();
        float resolvedVelocity = (float) (vanillaVelocity * speedMultiplier);

        /*
         * IMPORTANT: AscendanceRangedWeaponItem calls this AFTER vanilla's
         * BowItem launch setup. Keep vanilla responsible for the projectile's
         * initial position, facing and normal shoot-from-rotation setup, then
         * scale only the already-correct motion vector. This avoids a
         * first-render frame using an atypical launch speed during vanilla's
         * setup while preserving the exact trajectory direction.
         */
        projectile.setDeltaMovement(
                projectile.getDeltaMovement().scale(speedMultiplier)
        );

        double resolvedArrowBaseDamage = -1.0;

        if (projectile instanceof AbstractArrow arrow) {
            /*
             * Vanilla arrows begin at 2.0 base damage and BowItem gives a
             * fully drawn shot speed 3.0. Preserve any extra base damage that
             * enchantments/modifiers have already supplied, replace only the
             * vanilla core with our resolved Ascendance damage, then divide by
             * projectile-speed scaling so PROJECTILE_SPEED does not also raise
             * impact damage.
             */
            double existingBaseDamage = arrow.getBaseDamage();
            double extraBaseDamage = Math.max(
                    0.0,
                    existingBaseDamage - VANILLA_ARROW_BASE_DAMAGE
            );

            resolvedArrowBaseDamage = (
                    state.finalDamage() / VANILLA_BOW_FULL_SPEED
                            + extraBaseDamage
            ) / speedMultiplier;

            arrow.setBaseDamage(resolvedArrowBaseDamage);
        }

        LAST_RANGED_SHOT.put(
                player,
                new RangedShotEvaluation(
                        context.actualUseTicks(),
                        context.syntheticUseTicks(),
                        state.fullDrawTicks(),
                        vanillaVelocity,
                        resolvedVelocity,
                        resolvedArrowBaseDamage,
                        state.finalDamage(),
                        state.projectileSpeedPercent()
                )
        );

        return resolvedVelocity;
    }

    public static MagicCastEvaluation castMagic(
            ServerPlayer player,
            InteractionHand hand,
            ItemStack stack
    ) {
        MagicState state = evaluateMagic(player, stack);

        if (player.getCooldowns().isOnCooldown(stack.getItem())) {
            MagicCastEvaluation evaluation = new MagicCastEvaluation(
                    false,
                    false,
                    "COOLDOWN",
                    0.0,
                    state.finalDamage(),
                    state.castTicks()
            );
            LAST_MAGIC_CAST.put(player, evaluation);
            return evaluation;
        }

        Vec3 start = player.getEyePosition();
        Vec3 look = player.getLookAngle();

        HitResult blockHit = player.pick(
                MAGIC_RANGE_BLOCKS,
                0.0F,
                false
        );

        double rayDistance = blockHit.getType() == HitResult.Type.MISS
                ? MAGIC_RANGE_BLOCKS
                : Math.min(
                        MAGIC_RANGE_BLOCKS,
                        start.distanceTo(blockHit.getLocation())
                );

        Vec3 direction = look.scale(rayDistance);
        Vec3 end = start.add(direction);

        AABB searchBox = player
                .getBoundingBox()
                .expandTowards(direction)
                .inflate(1.0D);

        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                player,
                start,
                end,
                searchBox,
                entity -> entity != player
                        && entity.isAlive()
                        && entity.isPickable()
                        && !entity.isSpectator(),
                rayDistance * rayDistance
        );

        Entity target = entityHit == null
                ? null
                : entityHit.getEntity();

        boolean damaged = false;
        String targetName = "MISS";
        double targetDistance = rayDistance;

        if (target != null) {
            targetName = target.getDisplayName().getString();
            targetDistance = start.distanceTo(target.position());

            damaged = target.hurt(
                    player.damageSources().indirectMagic(player, player),
                    (float) state.finalDamage()
            );
        }

        player.getCooldowns().addCooldown(
                stack.getItem(),
                state.castTicks()
        );

        stack.hurtAndBreak(
                1,
                player,
                LivingEntity.getSlotForHand(hand)
        );

        MagicCastEvaluation evaluation = new MagicCastEvaluation(
                true,
                damaged,
                targetName,
                targetDistance,
                state.finalDamage(),
                state.castTicks()
        );

        LAST_MAGIC_CAST.put(player, evaluation);
        return evaluation;
    }

    public static Optional<RangedShotEvaluation> lastRangedShot(
            ServerPlayer player
    ) {
        return Optional.ofNullable(LAST_RANGED_SHOT.get(player));
    }

    public static Optional<MagicCastEvaluation> lastMagicCast(
            ServerPlayer player
    ) {
        return Optional.ofNullable(LAST_MAGIC_CAST.get(player));
    }

    public static void forget(ServerPlayer player) {
        LAST_RANGED_SHOT.remove(player);
        LAST_MAGIC_CAST.remove(player);
    }

    private static PlayerEssenceData playerData(ServerPlayer player) {
        return EssenceSavedData
                .get(player.server)
                .getPlayerData(player.getUUID());
    }

    private static void requireProfile(
            ItemStack stack,
            net.minecraft.resources.ResourceLocation expectedProfileId
    ) {
        if (stack.isEmpty()
                || !(stack.getItem() instanceof EquipmentProfileItem profileItem)
                || !profileItem.equipmentProfileId().equals(expectedProfileId)) {
            throw new IllegalArgumentException(
                    "Item stack does not use required equipment profile "
                            + expectedProfileId
            );
        }
    }

    private static int rateToTicks(double actionsPerSecond) {
        if (!Double.isFinite(actionsPerSecond) || actionsPerSecond <= 0.0) {
            return Integer.MAX_VALUE;
        }

        return Math.max(
                1,
                (int) Math.round(20.0 / actionsPerSecond)
        );
    }

    private record RangedShotContext(
            ServerPlayer player,
            RangedState state,
            int actualUseTicks,
            int syntheticUseTicks
    ) {
    }

    public record RangedState(
            double baselineDamage,
            double finalDamage,
            double baselineAttackSpeed,
            double finalAttackSpeed,
            int fullDrawTicks,
            double projectileSpeedPercent,
            double projectileSpeedMultiplier
    ) {
    }

    public record MagicState(
            double baselineDamage,
            double finalDamage,
            double baselineCastSpeed,
            double finalCastSpeed,
            int castTicks
    ) {
    }

    public record RangedShotEvaluation(
            int actualUseTicks,
            int syntheticUseTicks,
            int resolvedFullDrawTicks,
            float vanillaVelocity,
            float resolvedVelocity,
            double resolvedArrowBaseDamage,
            double nominalFullDrawDamage,
            double projectileSpeedPercent
    ) {
    }

    public record MagicCastEvaluation(
            boolean castPerformed,
            boolean damageApplied,
            String targetName,
            double targetDistance,
            double attemptedDamage,
            int cooldownTicks
    ) {
    }
}
