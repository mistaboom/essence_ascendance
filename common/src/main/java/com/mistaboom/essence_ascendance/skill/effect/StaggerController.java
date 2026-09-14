package com.mistaboom.essence_ascendance.skill.effect;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.WeakHashMap;
import java.lang.ref.WeakReference;
import java.util.function.Predicate;

/** Brief, non-refreshing locomotion penalty. Native AI, attacks, turning, gravity and knockback continue. */
public final class StaggerController {
    private static final ResourceLocation MODIFIER = ResourceLocation.fromNamespaceAndPath("essence_ascendance", "stagger");
    private static final Map<LivingEntity, State> STATES = new WeakHashMap<>();
    private StaggerController() { }
    public record Window(long startedAt, long expiresAt) {
        public boolean active(long now) { return now >= startedAt && now < expiresAt; }
    }
    private static final class State {
        final WeakReference<ServerPlayer> owner;
        final ResourceLocation sourceSkill;
        final Predicate<ServerPlayer> sourceValid;
        final ResourceLocation dimension;
        final Window window;
        final double tolerance;
        Vec3 expected;
        State(ServerPlayer owner, LivingEntity target, ResourceLocation sourceSkill, int duration, double tolerance,
              Predicate<ServerPlayer> sourceValid) {
            this.owner = new WeakReference<>(owner); this.sourceSkill = sourceSkill;
            this.sourceValid = sourceValid;
            dimension = target.level().dimension().location();
            window = new Window(target.level().getGameTime(), target.level().getGameTime() + duration);
            this.tolerance = tolerance; expected = target.position();
        }
    }
    public static boolean apply(ServerPlayer owner, LivingEntity target, ResourceLocation sourceSkill, int duration, double multiplier,
                                double tolerance, Predicate<ServerPlayer> sourceValid) {
        if (sourceSkill == null || sourceValid == null || !CrowdControlEligibility.canControl(owner, target)
                || !sourceValid.test(owner) || duration <= 0 || !Double.isFinite(multiplier)
                || multiplier <= 0 || multiplier >= 1 || !Double.isFinite(tolerance) || tolerance <= 0) return false;
        tick(target);
        if (STATES.containsKey(target)) return false; // No pinning by repeatedly extending the same brief stagger.
        var speed = target.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return false;
        speed.removeModifier(MODIFIER);
        speed.addTransientModifier(new AttributeModifier(MODIFIER, multiplier - 1,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        STATES.put(target, new State(owner, target, sourceSkill, duration, tolerance, sourceValid));
        return true;
    }
    public static void tick(Entity entity) {
        if (!(entity instanceof LivingEntity target) || target.level().isClientSide) return;
        State state = STATES.get(target);
        if (state == null) return;
        ServerPlayer owner = state.owner.get();
        if (owner == null || !owner.isAlive() || owner.isRemoved() || owner.level() != target.level()
                || !com.mistaboom.essence_ascendance.skill.CommittedSkillService.isEffective(owner, state.sourceSkill)
                || !state.sourceValid.test(owner) || !CrowdControlEligibility.canControl(owner, target)
                || !target.isAlive() || target.isRemoved() || target.isPassenger() || target.isVehicle()
                || target.isSpectator() || target.isInvulnerable() || target.getType().is(CrowdControlEligibility.IMMUNE)
                || !state.dimension.equals(target.level().dimension().location())
                || !state.window.active(target.level().getGameTime())
                || state.expected.distanceToSqr(target.position()) > state.tolerance * state.tolerance) remove(target);
    }
    public static void moved(Entity entity) {
        if (entity instanceof LivingEntity target) {
            State state = STATES.get(target);
            if (state != null) state.expected = target.position();
        }
    }
    public static void remove(Entity entity) {
        if (!(entity instanceof LivingEntity target) || STATES.remove(target) == null) return;
        var speed = target.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(MODIFIER);
    }
    public static void clear() { for (LivingEntity target : java.util.List.copyOf(STATES.keySet())) remove(target); }
    public static void removeOwned(ServerPlayer owner) {
        for (var row : java.util.List.copyOf(STATES.entrySet())) if (row.getValue().owner.get() == owner) remove(row.getKey());
    }
}
