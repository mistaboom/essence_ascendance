package com.mistaboom.essence_ascendance.skill.effect;

import com.mistaboom.essence_ascendance.equipment.EquipmentDamageService;
import com.mistaboom.essence_ascendance.equipment.EquipmentShieldService;
import com.mistaboom.essence_ascendance.guard.CounterattackLedger;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;

/** Shared primary-attack reservation for Stored Force and Riposte. No secondary hurt calls. */
public final class GuardCounterattackService {
    private static final ThreadLocal<Resolution> RESOLUTION = new ThreadLocal<>();

    private GuardCounterattackService() { }

    public static List<SkillEffectHandler> handlers() {
        return List.of(new CounterEffect(SkillIds.STORED_FORCE), new CounterEffect(SkillIds.RIPOSTE));
    }

    /** Called once after the shared native outcome commits a positive functional shield block. */
    public static void onBlock(ServerPlayer player, long event, double prevented, boolean perfect) {
        if (!Double.isFinite(prevented) || prevented <= 0 || !EquipmentShieldService.isUsingShield(player)) return;
        SkillEffectRuntime.Context context = SkillEffectRuntime.context(player);
        ItemStack shield = player.getUseItem();
        InteractionHand hand = player.getUsedItemHand();
        if (player.getItemInHand(hand) != shield || !EquipmentShieldService.canGuard(player, shield)) return;
        if (context.isEffective(SkillIds.STORED_FORCE)) {
            BoundReward state = reward(context, SkillIds.STORED_FORCE, shield, hand);
            var tuning = context.settings().guard().storedForce();
            state.ledger.grant(event, context.now(), prevented * tuning.conversion(),
                    tuning.capacity(), tuning.durationTicks(), false);
        }
        if (perfect && context.isEffective(SkillIds.RIPOSTE)) {
            BoundReward state = reward(context, SkillIds.RIPOSTE, shield, hand);
            if (state.ledger.grant(event, context.now(), 1, 1,
                    context.settings().guard().riposte().durationTicks(), true)) {
                player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_CHIME.value(),
                        SoundSource.PLAYERS, .35F, 1.5F);
            }
        }
    }

    private static BoundReward reward(SkillEffectRuntime.Context context, ResourceLocation id,
                                       ItemStack shield, InteractionHand hand) {
        BoundReward existing = context.existingState(id);
        if (existing != null && (existing.shield != shield || existing.hand != hand)) context.discardState(id);
        return context.state(id, () -> new BoundReward(shield, hand, context.player().level().dimension().location()));
    }

    /** Native attack input is already scoped by EquipmentDamageService, before cooldown/crit resolution. */
    static void begin(SkillEffectRuntime.Context context, long token, Entity target) {
        if (RESOLUTION.get() != null || !(target instanceof LivingEntity living) || !living.isAlive()
                || !EquipmentDamageService.canSkillHarm(context.player(), living)) return;
        double reach = bonusReach(context);
        if (!withinReach(context.player(), living.getBoundingBox(), context.player().entityInteractionRange() + reach)
                || !visible(context.player(), living.getBoundingBox())) return;
        BoundReward stored = context.isEffective(SkillIds.STORED_FORCE) ? context.existingState(SkillIds.STORED_FORCE) : null;
        BoundReward riposte = context.isEffective(SkillIds.RIPOSTE) ? context.existingState(SkillIds.RIPOSTE) : null;
        var force = stored == null ? null : stored.ledger.reserve(token, context.now());
        var riposteCharge = riposte == null ? null : riposte.ledger.reserve(token, context.now());
        if (force == null && riposteCharge == null) return;
        RESOLUTION.set(new Resolution(context.player(), living, token, context.now(), stored, riposte, force,
                riposteCharge, context.settings().guard().storedForce().damageScale(),
                context.settings().guard().storedForce().knockbackScale(),
                context.settings().guard().ward().knockbackEchoCap(),
                context.settings().guard().riposte().damageScale(),
                context.settings().guard().riposte().protectionTicks(), context.player().getLookAngle()));
    }

    /** Existing outgoing modification invokes this once after ordinary multipliers/flat bonuses. */
    static float modify(SkillEffectRuntime.Context context, LivingEntity target, AttackCategory category, float amount) {
        Resolution state = RESOLUTION.get();
        if (state == null || state.player != context.player() || state.target != target
                || category != AttackCategory.MELEE || !context.matchesPrimaryTarget(target)) return amount;
        boolean riposte = valid(context, SkillIds.RIPOSTE, state.riposte) && state.riposteCharge != null;
        boolean stored = valid(context, SkillIds.STORED_FORCE, state.stored) && state.force != null;
        state.riposteBonus = riposte ? amount * state.riposteScale : 0;
        state.storedBonus = stored ? state.force.value() * state.forceScale : 0;
        state.modified = true;
        return (float) Math.min(Float.MAX_VALUE, amount + state.riposteBonus + state.storedBonus);
    }

    private static boolean valid(SkillEffectRuntime.Context context, ResourceLocation skill, BoundReward reward) {
        return reward != null && context.isEffective(skill) && context.existingState(skill) == reward
                && reward.valid(context.player()) && reward.ledger.value() > 0;
    }

    /** Called before ordinary on-hit procs; the native primary hurt/knockback/sweep have all completed. */
    static void finish(SkillEffectRuntime.Context context, long token, boolean accepted, double loss) {
        Resolution state = RESOLUTION.get();
        if (state == null || state.token != token || state.player != context.player()) return;
        // Close protection before reward knockback and every secondary skill callback.
        RESOLUTION.remove();
        boolean confirmed = accepted && state.modified && Double.isFinite(loss) && loss > 0;
        double spent = 0;
        if (valid(context, SkillIds.STORED_FORCE, state.stored)) {
            spent = state.stored.ledger.finish(state.force, confirmed, loss, false);
            state.stored.lastTarget = state.target.getUUID().toString();
            state.stored.lastRequestedBonus = state.storedBonus;
        }
        if (valid(context, SkillIds.RIPOSTE, state.riposte)) {
            state.riposte.ledger.finish(state.riposteCharge, confirmed, loss, true);
            state.riposte.lastTarget = state.target.getUUID().toString();
            state.riposte.lastRequestedBonus = state.riposteBonus;
        }
        if (confirmed && spent > 0 && state.target.isAlive() && !state.target.isRemoved()
                && EquipmentDamageService.canSkillHarm(state.player, state.target)
                && CrowdControlEligibility.canControl(state.player, state.target)) {
            double strength = Math.min(state.knockbackCap, spent * state.knockbackScale);
            if (strength > 0 && state.direction.horizontalDistanceSqr() > 1.0E-8) {
                EquipmentDamageService.withSecondarySkillDamage(() -> state.target.knockback(strength,
                        -state.direction.x, -state.direction.z));
            }
        }
        if (confirmed && (state.riposteBonus > 0 || state.storedBonus > 0)) {
            com.mistaboom.essence_ascendance.network.CombatVisualFeedback.defenseImpact(state.player.serverLevel(), state.target);
        }
    }

    static void close(ServerPlayer player, long token) {
        Resolution state = RESOLUTION.get();
        if (state != null && state.player == player && state.token == token) RESOLUTION.remove();
    }

    static void reset(ServerPlayer player) {
        Resolution state = RESOLUTION.get();
        if (state != null && state.player == player) RESOLUTION.remove();
    }

    static void clearAll() { RESOLUTION.remove(); }

    /** Damage and normal cooldowns remain untouched. Only native displacement during this call is suppressed. */
    public static boolean suppressDisplacement(Entity entity) {
        Resolution state = RESOLUTION.get();
        if (state == null || state.player != entity || state.riposteCharge == null || !state.player.isAlive()
                || state.player.isRemoved() || !state.riposte.valid(state.player)
                || state.riposte.ledger.value() <= 0
                || state.player.level().getGameTime() - state.startedAt >= state.protectionTicks) return false;
        return com.mistaboom.essence_ascendance.skill.CommittedSkillService.isEffective(state.player, SkillIds.RIPOSTE);
    }

    public static double bonusReach(ServerPlayer player) { return bonusReach(SkillEffectRuntime.context(player)); }

    static double bonusReach(SkillEffectRuntime.Context context) {
        BoundReward state = context.existingState(SkillIds.RIPOSTE);
        return valid(context, SkillIds.RIPOSTE, state) ? context.settings().guard().riposte().bonusReach() : 0;
    }

    static long reachExpiresAt(SkillEffectRuntime.Context context) {
        BoundReward state = context.existingState(SkillIds.RIPOSTE);
        return valid(context, SkillIds.RIPOSTE, state) ? state.ledger.expiresAt() : 0;
    }

    /** Decode the native packet's action without executing any game interaction. */
    public static boolean isAttack(ServerboundInteractPacket packet) {
        boolean[] attack = {false};
        packet.dispatch(new ServerboundInteractPacket.Handler() {
            @Override public void onInteraction(InteractionHand hand) { }
            @Override public void onInteraction(InteractionHand hand, Vec3 location) { }
            @Override public void onAttack() { attack[0] = true; }
        });
        return attack[0];
    }

    public static boolean canReach(ServerPlayer player, Entity target) {
        double bonus = bonusReach(player);
        return bonus > 0 && target instanceof LivingEntity living && living.isAlive()
                && EquipmentDamageService.canSkillHarm(player, target)
                && player.level().getWorldBorder().isWithinBounds(target.blockPosition())
                && withinReach(player, target.getBoundingBox(), player.entityInteractionRange() + bonus)
                && visible(player, target.getBoundingBox());
    }

    public static boolean withinReach(ServerPlayer player, AABB bounds, double reach) {
        return Double.isFinite(reach) && reach >= 0
                && player.getEyePosition().distanceToSqr(closest(player.getEyePosition(), bounds)) <= reach * reach;
    }

    private static boolean visible(ServerPlayer player, AABB bounds) {
        Vec3 eye = player.getEyePosition();
        Vec3 hit = closest(eye, bounds);
        return player.level().clip(new ClipContext(eye, hit, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS;
    }

    static Vec3 closest(Vec3 origin, AABB bounds) {
        return new Vec3(Math.clamp(origin.x, bounds.minX, bounds.maxX),
                Math.clamp(origin.y, bounds.minY, bounds.maxY), Math.clamp(origin.z, bounds.minZ, bounds.maxZ));
    }

    private static String decimal(double value) { return SkillEffectHudCards.decimal(value); }

    private record CounterEffect(ResourceLocation id) implements SkillEffectHudHandler {
        @Override public void deactivate(SkillEffectRuntime.Context context) {
            context.discardState(id);
            com.mistaboom.essence_ascendance.guard.GuardLifecycle.invalidateTiming(context.player());
        }

        @Override public void reconcile(SkillEffectRuntime.Context context) {
            BoundReward state = context.existingState(id);
            if (state == null) return;
            if (!state.valid(context.player())) context.discardState(id);
            else state.ledger.expire(context.now());
        }

        @Override public SkillEffectHudEntry hudEntry(SkillEffectRuntime.Context context) {
            BoundReward state = context.existingState(id);
            double value = state == null ? 0 : state.ledger.value();
            boolean force = id.equals(SkillIds.STORED_FORCE);
            var settings = context.settings().guard();
            return SkillEffectHudCards.timed(id, value > 0, force ? com.mistaboom.essence_ascendance.visual.AscendancePalette.DEFENSE : com.mistaboom.essence_ascendance.visual.AscendancePalette.DEFENSE,
                    force ? SkillEffectHudEntry.Text.literal(decimal(value) + "/" + decimal(settings.storedForce().capacity()))
                            : SkillEffectHudEntry.Text.translated("hud.essence_ascendance.armed"),
                    List.of(force ? SkillEffectHudEntry.Text.translated("hud.essence_ascendance.next_confirmed_melee")
                            : SkillEffectHudEntry.Text.translated("hud.essence_ascendance.damage_reach",
                                    decimal(1 + settings.riposte().damageScale()), decimal(settings.riposte().bonusReach()))),
                    "hud.essence_ascendance.counterattack", value > 0 ? state.ledger.expiresAt() : 0);
        }

        @Override public List<String> debugLines(SkillEffectRuntime.Context context) {
            BoundReward state = context.existingState(id);
            var settings = context.settings().guard();
            return List.of("Guard reward=" + (state == null ? 0 : state.ledger.value())
                            + "; expires=" + (state == null ? 0 : state.ledger.expiresAt())
                            + "; age=" + (state == null ? 0 : context.now() - state.ledger.refreshedAt())
                            + "; source outcome=" + (state == null ? 0 : state.ledger.lastEvent()),
                    "Shield/hand=" + (state == null ? "none" : state.hand + "/" + state.valid(context.player()))
                            + "; decision=" + (state == null ? "empty_or_invalidated" : state.ledger.decision())
                            + "; last target=" + (state == null ? "none" : state.lastTarget)
                            + "; requested bonus=" + (state == null ? 0 : state.lastRequestedBonus),
                    id.equals(SkillIds.STORED_FORCE)
                            ? "Add/refresh; cap=" + settings.storedForce().capacity() + "; flat scale=" + settings.storedForce().damageScale()
                            + "; knockback scale=" + settings.storedForce().knockbackScale()
                            : "Armed reach=" + bonusReach(context) + "; damage bonus ratio=" + settings.riposte().damageScale()
                            + "; protection=" + suppressDisplacement(context.player()) + "; maximum=" + settings.riposte().protectionTicks() + " ticks, ends at attack return",
                    "Spend: accepted primary melee with positive health/absorption loss only; misses/cancel/zero retain. Riposte multiplies native outgoing, then Stored Force adds flat; target mitigation remains native.");
        }
    }

    private static final class BoundReward implements SkillEffectState {
        final ItemStack shield;
        final InteractionHand hand;
        final ResourceLocation dimension;
        final CounterattackLedger ledger = new CounterattackLedger();
        String lastTarget = "none";
        double lastRequestedBonus;
        BoundReward(ItemStack shield, InteractionHand hand, ResourceLocation dimension) {
            this.shield = shield; this.hand = hand; this.dimension = dimension;
        }
        boolean valid(ServerPlayer player) {
            return player.isAlive() && !player.isRemoved() && !player.isSpectator()
                    && player.level().dimension().location().equals(dimension)
                    && player.getItemInHand(hand) == shield && EquipmentShieldService.canGuard(player, shield);
        }
        @Override public void clear() { ledger.clear(); }
    }

    private static final class Resolution {
        final ServerPlayer player; final LivingEntity target; final long token; final long startedAt;
        final BoundReward stored; final BoundReward riposte;
        final CounterattackLedger.Reservation force; final CounterattackLedger.Reservation riposteCharge;
        final double forceScale; final double knockbackScale; final double knockbackCap; final double riposteScale;
        final int protectionTicks; final Vec3 direction;
        boolean modified; double storedBonus; double riposteBonus;
        Resolution(ServerPlayer player, LivingEntity target, long token, long startedAt, BoundReward stored,
                   BoundReward riposte, CounterattackLedger.Reservation force, CounterattackLedger.Reservation riposteCharge,
                   double forceScale, double knockbackScale, double knockbackCap, double riposteScale, int protectionTicks,
                   Vec3 direction) {
            this.player = player; this.target = target; this.token = token; this.startedAt = startedAt;
            this.stored = stored; this.riposte = riposte; this.force = force; this.riposteCharge = riposteCharge;
            this.forceScale = forceScale; this.knockbackScale = knockbackScale; this.knockbackCap = knockbackCap;
            this.riposteScale = riposteScale; this.protectionTicks = protectionTicks; this.direction = direction;
        }
    }
}
