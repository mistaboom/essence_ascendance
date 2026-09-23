package com.mistaboom.essence_ascendance.utility;

import com.mistaboom.essence_ascendance.skill.CommittedSkillService;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectAttributes;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectState;
import com.mistaboom.essence_ascendance.network.MicroVisualFeedback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Owner-authoritative stat and catch-up bridge for loaded bonded creatures. */
public final class BondedCompanionService {
    private static final double EPSILON = 1.0E-7;

    private BondedCompanionService() { }

    public static void tick(LivingEntity companion) {
        if (!(companion instanceof OwnableEntity ownable)) return;
        LivingEntity ownerEntity = ownable.getOwner();
        if (!(ownerEntity instanceof ServerPlayer owner) || !owner.isAlive() || owner.isRemoved()) {
            remove(companion);
            return;
        }

        if (!CommittedSkillService.isEffective(owner, SkillIds.BONDED_COMPANION)) {
            remove(companion);
            return;
        }
        SkillEffectRuntime.Context context = SkillEffectRuntime.context(owner);
        if (!context.isEffective(SkillIds.BONDED_COMPANION)) {
            remove(companion);
            return;
        }

        var tuning = context.settings().utility().bondedCompanion();
        apply(companion, tuning.statBonusFraction());
        boolean teleported = SafeTeleportService.catchUp(companion, owner, tuning.catchupDistanceBlocks());
        if (teleported) MicroVisualFeedback.utility(owner.serverLevel(), companion.getBoundingBox().getCenter(),
                companion.getId() * 31L ^ context.now());
        State state = context.state(SkillIds.BONDED_COMPANION, State::new);
        state.lastSeenAt = context.now();
        state.lastName = companion.getName().getString();
        state.lastTeleportedAt = teleported ? context.now() : state.lastTeleportedAt;
    }

    public static Snapshot snapshot(SkillEffectRuntime.Context context) {
        State state = context.existingState(SkillIds.BONDED_COMPANION);
        if (state == null || state.lastSeenAt == Long.MIN_VALUE || context.now() < state.lastSeenAt
                || context.now() - state.lastSeenAt > 1) return null;
        return new Snapshot(state.lastName, state.lastTeleportedAt == context.now());
    }

    private static void apply(LivingEntity companion, double bonus) {
        reconcileHealth(companion, bonus);
        SkillEffectAttributes.apply(companion, Attributes.ATTACK_DAMAGE, SkillIds.BONDED_COMPANION,
                bonus, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        SkillEffectAttributes.apply(companion, Attributes.MOVEMENT_SPEED, SkillIds.BONDED_COMPANION,
                bonus, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
    }

    private static void remove(LivingEntity companion) {
        reconcileHealth(companion, 0);
        SkillEffectAttributes.apply(companion, Attributes.ATTACK_DAMAGE, SkillIds.BONDED_COMPANION,
                0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        SkillEffectAttributes.apply(companion, Attributes.MOVEMENT_SPEED, SkillIds.BONDED_COMPANION,
                0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
    }

    private static void reconcileHealth(LivingEntity companion, double bonus) {
        AttributeInstance instance = companion.getAttribute(Attributes.MAX_HEALTH);
        if (instance == null) return;
        AttributeModifier previous = instance.getModifier(SkillIds.BONDED_COMPANION);
        double previousAmount = previous == null ? 0 : previous.amount();
        if ((previous != null && previous.operation() != AttributeModifier.Operation.ADD_MULTIPLIED_BASE)
                || Math.abs(previousAmount - bonus) > EPSILON) {
            double oldMaximum = companion.getMaxHealth();
            double healthFraction = oldMaximum <= 0 ? 1 : companion.getHealth() / oldMaximum;
            SkillEffectAttributes.apply(companion, Attributes.MAX_HEALTH, SkillIds.BONDED_COMPANION,
                    bonus, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
            companion.setHealth((float) Math.clamp(companion.getMaxHealth() * healthFraction, 0, companion.getMaxHealth()));
        }
    }

    public record Snapshot(String name, boolean teleportedThisTick) { }

    private static final class State implements SkillEffectState {
        String lastName = "";
        long lastSeenAt = Long.MIN_VALUE;
        long lastTeleportedAt = Long.MIN_VALUE;
        @Override public void clear() { lastName = ""; lastSeenAt = Long.MIN_VALUE; lastTeleportedAt = Long.MIN_VALUE; }
    }
}
