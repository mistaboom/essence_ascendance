package com.mistaboom.essence_ascendance.skill.effect;

import com.mistaboom.essence_ascendance.equipment.EquipmentShieldService;
import com.mistaboom.essence_ascendance.equipment.ShieldMath;
import com.mistaboom.essence_ascendance.projectile.ProjectileTargeting;
import com.mistaboom.essence_ascendance.skill.CommittedSkillService;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** One permission/slowdown policy used by local prediction and the native authoritative move boundary. */
public final class GuardMobilityController {
    private static final String SYNC = "essence_ascendance_guard_mobility";
    private static final Map<ServerPlayer, CollisionAttackService.Ledger> RAMS = new WeakHashMap<>();
    private static final Map<ServerPlayer, String> RECENT = new WeakHashMap<>();
    private GuardMobilityController() { }

    public static boolean active(Player player) {
        if (!EquipmentShieldService.isGuarding(player) || player.isPassenger() || player.isFallFlying()) return false;
        if (player instanceof ServerPlayer server) return CommittedSkillService.effectiveIds(server).contains(SkillIds.GUARDED_ADVANCE);
        var data = player.getUseItem().get(DataComponents.CUSTOM_DATA);
        if (data == null) return false;
        var tag = data.copyTag().getCompound(SYNC);
        return tag.hasUUID("holder") && player.getUUID().equals(tag.getUUID("holder")) && tag.getBoolean("enabled");
    }
    /** Mirror native food/fall-flying/blindness/crouch limits; input, water and collision remain native caller decisions. */
    public static boolean sprintAllowed(Player player) {
        return active(player) && !player.isShiftKeyDown() && !player.hasEffect(MobEffects.BLINDNESS)
                && (player.getFoodData().getFoodLevel() > 6 || player.getAbilities().mayfly);
    }
    public static float movementMultiplier(Player player, float vanilla, double investedPercent) {
        if (!EquipmentShieldService.isGuarding(player)) return vanilla;
        double removal = 0;
        if (active(player)) {
            if (player instanceof ServerPlayer server) removal = SkillEffectRuntime.resolvedSettings(server).guard().mobility().slowdownRemoval();
            else removal = player.getUseItem().get(DataComponents.CUSTOM_DATA).copyTag().getCompound(SYNC).getDouble("removal");
        }
        return resolveMultiplier(vanilla, investedPercent, removal);
    }
    /** The stronger removal wins; equipment and skill do not double-apply the item-use multiplier. */
    public static float resolveMultiplier(float vanilla, double investedPercent, double removal) {
        return ShieldMath.movementMultiplier(vanilla, Math.max(investedPercent, Math.clamp(removal, 0, 1) * 100));
    }
    public static float stepHeight(LivingEntity holder, float vanilla) {
        if (!(holder instanceof Player player) || !active(player) || player.isShiftKeyDown()
                || player.isInWater() || player.isInLava() || player.onClimbable()
                || player.getAbilities().flying || player.hasEffect(MobEffects.LEVITATION)) return vanilla;
        double step = player instanceof ServerPlayer server
                ? SkillEffectRuntime.resolvedSettings(server).guard().mobility().stepHeight()
                : player.getUseItem().get(DataComponents.CUSTOM_DATA).copyTag().getCompound(SYNC).getDouble("step");
        return (float) Math.max(vanilla, Math.clamp(step, 0, 1.1));
    }
    public static void sync(ServerPlayer player) {
        boolean effective = player.isAlive() && !player.isRemoved() && !player.isSpectator()
                && CommittedSkillService.effectiveIds(player).contains(SkillIds.GUARDED_ADVANCE);
        sync(player, player.getMainHandItem(), effective);
        sync(player, player.getOffhandItem(), effective);
    }
    private static void sync(ServerPlayer player, ItemStack stack, boolean effective) {
        if (!EquipmentShieldService.isShield(stack)) return;
        var tuning = SkillEffectRuntime.resolvedSettings(player).guard().mobility();
        var next = new net.minecraft.nbt.CompoundTag();
        next.putUUID("holder", player.getUUID());
        next.putBoolean("enabled", effective && EquipmentShieldService.canGuard(player, stack));
        next.putDouble("removal", tuning.slowdownRemoval()); next.putDouble("step", tuning.stepHeight());
        var old = stack.get(DataComponents.CUSTOM_DATA);
        if (old != null && old.copyTag().getCompound(SYNC).equals(next)) return;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.put(SYNC, next));
    }
    public static void tick(ServerPlayer player) {
        sync(player);
        if (EquipmentShieldService.isUsingShield(player) && !sprintAllowed(player)) player.setSprinting(false);
        if (!EquipmentShieldService.canGuard(player, player.getMainHandItem())
                && !EquipmentShieldService.canGuard(player, player.getOffhandItem())) StaggerController.removeOwned(player);
        ledger(player).posture(ramPosture(player), player.level().getGameTime());
    }
    private static CollisionAttackService.Ledger ledger(ServerPlayer player) {
        return RAMS.computeIfAbsent(player, ignored -> new CollisionAttackService.Ledger());
    }
    private static boolean ramPosture(ServerPlayer player) {
        return sprintAllowed(player) && player.isSprinting() && !player.isInWater() && !player.isInLava()
                && !player.onClimbable() && !player.getAbilities().flying && !player.hasEffect(MobEffects.LEVITATION)
                && CommittedSkillService.effectiveIds(player).contains(SkillIds.SHIELD_RAM);
    }
    /** Capture only intentional native player translation; pistons/riding/teleport never start contact attacks. */
    public static AABB beginMove(Entity entity, MoverType type) {
        if (!(entity instanceof ServerPlayer player) || type != MoverType.PLAYER || !ramPosture(player)) return null;
        return player.getBoundingBox();
    }
    public static void completedMove(Entity entity, AABB initial) {
        if (!(entity instanceof ServerPlayer player) || initial == null) return;
        long now = player.level().getGameTime();
        var ledger = ledger(player);
        ledger.posture(ramPosture(player), now);
        var tuning = SkillEffectRuntime.resolvedSettings(player).guard().ram();
        Vec3 movement = player.getBoundingBox().getCenter().subtract(initial.getCenter());
        double speed = movement.horizontalDistance();
        if (!ramPosture(player) || movement.x * player.getLookAngle().x + movement.z * player.getLookAngle().z <= 0) {
            RECENT.put(player, "Shield Ram: rejected=not-forward-guarded-sprint; speed=" + speed); return;
        }
        List<CollisionAttackService.Contact> contacts = CollisionAttackService.sweep(player,
                new CollisionAttackService.Shape(initial, movement), tuning.minimumSpeed(), tuning.maximumSweep(),
                ledger.remaining(tuning.contactLimit()), target -> target != player
                        && CrowdControlEligibility.canControl(player, target) && ProjectileTargeting.hostile(player, target)
                        && ledger.eligible(target.getUUID(), now));
        String reason = speed < tuning.minimumSpeed() ? "below-minimum-speed" : movement.length() > tuning.maximumSweep()
                ? "teleport-or-excess-sweep" : ledger.remaining(tuning.contactLimit()) == 0 ? "contact-budget" : "no-forward-visible-contact";
        int accepted = 0;
        for (var contact : contacts) {
            Entity found = player.serverLevel().getEntity(contact.id());
            if (!(found instanceof LivingEntity target) || !CrowdControlEligibility.canControl(player, target)
                    || !ProjectileTargeting.hostile(player, target)
                    || !ledger.eligible(contact.id(), now)) continue;
            if (!StaggerController.apply(player, target, SkillIds.SHIELD_RAM, tuning.staggerTicks(), tuning.staggerMovementMultiplier(), tuning.maximumSweep(), staggerSource(player))) {
                reason = "already-staggered-or-control-ineligible"; continue;
            }
            ledger.accept(contact.id(), now, tuning.repeatCooldownTicks(), tuning.contactLimit());
            Vec3 direction = CollisionAttackService.pushDirection(movement, target.position().subtract(player.position()), contact.id());
            target.knockback(tuning.knockback(), -direction.x, -direction.z);
            target.hurtMarked = true;
            com.mistaboom.essence_ascendance.network.CombatVisualFeedback.shieldRam(
                    player.serverLevel(), player, target, direction);
            player.serverLevel().playSound(null, target.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, .35F, 1.15F);
            accepted++; reason = "accepted target=" + contact.id() + "; fraction=" + contact.fraction() + "; push=" + direction;
        }
        if (accepted > 0) SkillHudEvents.record(player, SkillIds.SHIELD_RAM, "targets", accepted);
        RECENT.put(player, "Shield Ram: " + reason + "; speed=" + speed + "; sweep=" + movement
                + "; candidates=" + contacts.size() + "; accepted=" + accepted + "; stagger=" + tuning.staggerTicks()
                + "; knockback=" + tuning.knockback() + "; remaining=" + ledger.remaining(tuning.contactLimit())
                + "; repeat=" + tuning.repeatCooldownTicks() + "; damage=none");
    }
    /** Accepted stagger survives lowering, but cannot outlive its exact functional held source equipment. */
    public static java.util.function.Predicate<ServerPlayer> staggerSource(ServerPlayer player) {
        ItemStack shield = player.getUseItem();
        var hand = player.getUsedItemHand();
        return holder -> holder.getItemInHand(hand) == shield && EquipmentShieldService.canGuard(holder, shield);
    }
    public static List<String> diagnostics(ServerPlayer player) {
        var context = EquipmentShieldService.blockingContext(player);
        float multiplier = movementMultiplier(player, .2F, context == null ? 0 : context.guardedMovementPercent());
        return List.of("Guard mobility: effective=" + CommittedSkillService.effectiveIds(player).contains(SkillIds.GUARDED_ADVANCE)
                        + "; functional=" + EquipmentShieldService.isUsingShield(player) + "; active=" + active(player)
                        + "; sprint=" + sprintAllowed(player) + "; jump=native; step=" + stepHeight(player, .6F)
                        + "; multiplier=" + multiplier + "; contract=AscendanceShieldItem",
                RECENT.getOrDefault(player, "Shield Ram: no native movement contact recorded"));
    }
    public static void remove(ServerPlayer player) { RAMS.remove(player); RECENT.remove(player); StaggerController.removeOwned(player); }
    public static void clear() { RAMS.clear(); RECENT.clear(); StaggerController.clear(); }
}
