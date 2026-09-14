package com.mistaboom.essence_ascendance.posture;

import com.mistaboom.essence_ascendance.attunement.AttunementGameplay;
import com.mistaboom.essence_ascendance.config.PostureBalanceSettings;
import com.mistaboom.essence_ascendance.equipment.EquipmentDamageService;
import com.mistaboom.essence_ascendance.projectile.ProjectileOwnership;
import com.mistaboom.essence_ascendance.projectile.ProjectileRuntime;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.effect.CombatHudActivity;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** One transient authoritative posture per player lifecycle, never persisted and never fed by HUD retention. */
public final class PostureService {
    private static final Map<ServerPlayer, State> STATES = new WeakHashMap<>();
    private static long nextLifecycle;
    private PostureService() { }
    public record Incoming(long event, long tick, long lifecycle, String damageType, ResourceLocation posture, UUID source, boolean eligible,
                           double chance, double roll, boolean dodged, double resistance,
                           double requestedPrevention, double confirmedPrevention, boolean accepted,
                           String knockback, String decision) {
        public static Incoming empty() { return new Incoming(0, 0, 0, "", null, null, false, 0, -1, false, 0, 0, 0, false, "none", "no_incoming_event"); }
        public Incoming force(String decision) { return new Incoming(event,tick,lifecycle,damageType,posture,source,eligible,chance,roll,dodged,resistance,
                requestedPrevention,confirmedPrevention,accepted,decision,this.decision); }
        public Incoming prevention(double measured) { return new Incoming(event,tick,lifecycle,damageType,posture,source,eligible,chance,roll,dodged,resistance,
                Math.max(0,measured),confirmedPrevention,accepted,knockback,decision); }
    }
    public record Snapshot(UUID player, int entityId, long lifecycle, String dimension, ResourceLocation selected, boolean effective,
                           double meter, double maximum, String reason, long tick, long expiresAt,
                           boolean movementIntent, double displacement, double turnDegrees, String movement,
                           ThreatFacingResolver.Result threat, int stacks, String damageType, Incoming incoming) { }
    private static final class State {
        final PostureMeter meter = new PostureMeter();
        final long lifecycle = ++nextLifecycle;
        final String dimension;
        ResourceLocation selected;
        Vec3 lastPosition;
        float yaw, pitch;
        long sampleTick = Long.MIN_VALUE, forcedUntil, lastTick = Long.MIN_VALUE;
        double packetDistance, packetTurn, displacement, turn;
        boolean intent;
        String motion = "no_server_movement", pendingMotion = "no_server_movement", forceReason = "none";
        ThreatFacingResolver.Result threat = ThreatFacingResolver.Result.none("inactive");
        Incoming incoming = Incoming.empty();
        State(ServerPlayer player) {
            dimension = player.level().dimension().location().toString(); lastPosition = player.position();
            yaw = player.getYRot(); pitch = player.getXRot();
        }
    }
    public static ResourceLocation selected(SkillEffectRuntime.Context context) {
        ResourceLocation found = null;
        for (ResourceLocation id : List.of(SkillIds.EVASIVE_CURRENT, SkillIds.BULWARK_STANCE, SkillIds.ADAPTIVE_GUARD))
            if (context.isEffective(id)) { if (found != null) return null; found = id; }
        return found;
    }
    private static PostureMeter.Choice choice(ResourceLocation id) {
        return SkillIds.EVASIVE_CURRENT.equals(id) ? PostureMeter.Choice.EVASIVE : SkillIds.BULWARK_STANCE.equals(id)
                ? PostureMeter.Choice.BULWARK : SkillIds.ADAPTIVE_GUARD.equals(id) ? PostureMeter.Choice.ADAPTIVE : PostureMeter.Choice.NONE;
    }
    private static State state(SkillEffectRuntime.Context context) {
        ServerPlayer player = context.player(); ResourceLocation selected = selected(context);
        if (!ProjectileOwnership.validDefender(player) || selected == null) { STATES.remove(player); return null; }
        State state = STATES.get(player);
        if (state == null || !state.dimension.equals(player.level().dimension().location().toString())) {
            state = new State(player); STATES.put(player, state);
        }
        if (!Objects.equals(state.selected, selected)) {
            state = new State(player); state.selected = selected; state.meter.select(choice(selected)); STATES.put(player,state);
        }
        state.meter.expire(context.now()); return state;
    }
    public static void reconcile(SkillEffectRuntime.Context context) { state(context); }
    public static void tick(SkillEffectRuntime.Context context) {
        State state = state(context); if (state == null || state.lastTick == context.now()) return;
        var player = context.player(); var settings = context.settings().posture(); var movement = settings.movement();
        double actual = player.position().distanceTo(state.lastPosition);
        // Native packet samples may precede or follow the gameplay tick. Consume each sample only once.
        state.displacement = state.packetDistance;
        state.turn = Math.max(state.packetTurn, angle(state.yaw, player.getYRot()) + angle(state.pitch, player.getXRot()));
        boolean unknownMotion = actual > movement.stillExitDisplacement() && state.packetDistance < movement.minimumDisplacement();
        if (actual > movement.maximumDisplacement()) {
            state.meter.clear("teleport_or_discontinuity"); state.forcedUntil = context.now() + movement.forcedMotionQuietTicks();
            state.pendingMotion = "teleport_or_discontinuity"; state.forceReason = state.pendingMotion;
        }
        state.intent = AttunementGameplay.movementIntent(player, movement.intentTimeoutTicks());
        boolean forced = context.now() < state.forcedUntil || unknownMotion || player.hurtTime > 0;
        boolean supported = movementMode(player);
        state.motion = forced ? (context.now() < state.forcedUntil ? state.forceReason : player.hurtTime > 0 ? "recent_native_hit" : "uncorroborated_displacement")
                : !supported ? "unsupported_movement_mode" : state.pendingMotion;
        state.threat = state.meter.choice() == PostureMeter.Choice.BULWARK
                ? ThreatFacingResolver.resolve(player, settings.bulwark()) : ThreatFacingResolver.Result.none("not_bulwark");
        state.meter.tick(context.now(), Math.max(state.displacement, actual), state.turn,
                state.intent, forced, supported, state.threat.facing(), settings);
        state.lastTick = context.now(); state.lastPosition = player.position(); state.yaw = player.getYRot(); state.pitch = player.getXRot();
        state.packetDistance = 0; state.packetTurn = 0; state.pendingMotion = "no_server_movement";
    }
    /** Reuses the existing input packet only as evidence; all motion and lifecycle checks remain server-side. */
    public static void moved(ServerPlayer player, Vec3 before, String dimension, float yaw, float pitch) {
        State state = STATES.get(player); if (state == null) return;
        var movement = SkillEffectRuntime.resolvedSettings(player).posture().movement();
        if (!dimension.equals(player.level().dimension().location().toString())) { forget(player); return; }
        Vec3 delta = player.position().subtract(before); double distance = delta.length();
        if (!Double.isFinite(distance) || distance > movement.maximumDisplacement()) {
            state.meter.clear("packet_discontinuity"); forced(player, "packet_discontinuity"); return;
        }
        state.packetDistance = Math.min(movement.maximumDisplacement() + 1, state.packetDistance + distance);
        state.packetTurn = Math.min(360, state.packetTurn + angle(yaw, player.getYRot()) + angle(pitch, player.getXRot()));
        state.sampleTick = player.level().getGameTime();
        state.pendingMotion = player.isSwimming() ? "native_swimming" : player.onClimbable() ? "native_climbing"
                : player.getAbilities().flying ? "native_flight" : player.isCrouching() ? "native_crouching" : "native_player_translation";
    }
    /** Non-player native displacement (pistons, conveyors, knockback and collision correction) cannot charge. */
    public static void externalMove(Entity entity, MoverType type, Vec3 before) {
        if (!(entity instanceof ServerPlayer player) || type == MoverType.PLAYER || !STATES.containsKey(player)) return;
        var settings = SkillEffectRuntime.resolvedSettings(player).posture().movement();
        if (player.position().distanceTo(before) > settings.stillExitDisplacement()) forced(player, "native_" + type.name().toLowerCase(Locale.ROOT));
    }
    public static void forced(ServerPlayer player, String reason) {
        State state = STATES.get(player); if (state == null) return;
        state.forcedUntil = player.level().getGameTime() + SkillEffectRuntime.resolvedSettings(player).posture().movement().forcedMotionQuietTicks();
        state.pendingMotion = reason; state.forceReason = reason.substring(0,Math.min(reason.length(),96));
    }
    /** Explicit server velocity accelerations conservatively invalidate accumulated evasion evidence. */
    public static void velocity(Entity entity, Vec3 proposed) {
        if (!(entity instanceof ServerPlayer player) || !STATES.containsKey(player)) return;
        var settings = SkillEffectRuntime.resolvedSettings(player).posture().movement();
        Vec3 current = entity.getDeltaMovement();
        // Ordinary drag/gravity do not create horizontal acceleration. Force or a changed horizontal
        // direction does. Ground collision cancels downward gravity after native move sets onGround;
        // only acceleration into positive upward motion counts as grounded vertical force.
        if (!Double.isFinite(proposed.lengthSqr()) || proposed.horizontalDistance() > current.horizontalDistance() + settings.minimumDisplacement()
                || current.horizontalDistance() > settings.minimumDisplacement() && proposed.horizontalDistance() > settings.minimumDisplacement()
                && current.normalize().dot(proposed.normalize()) < 0
                || player.onGround() && proposed.y > Math.max(0, current.y) + settings.minimumDisplacement()) forced(player,"server_velocity_acceleration");
    }
    private static boolean movementMode(ServerPlayer player) {
        if (player.isPassenger() || player.isSleeping() || player.isFallFlying() || player.isInLava()) return false;
        if (player.isInWater()) return player.isSwimming()
                && player.level().getFluidState(player.blockPosition()).getFlow(player.level(), player.blockPosition()).lengthSqr() == 0;
        return player.onGround() || player.onClimbable() || player.getAbilities().flying;
    }
    public static double angle(float before, float after) {
        if (!Float.isFinite(before) || !Float.isFinite(after)) return 360;
        return Math.abs(net.minecraft.util.Mth.wrapDegrees(after - before));
    }
    public static String damageIdentity(DamageSource source) {
        return source.typeHolder().unwrapKey().map(key -> key.location().toString()).orElse("");
    }
    public static boolean eligible(ServerPlayer player, DamageSource source, float amount, boolean secondary) {
        if (secondary || !Float.isFinite(amount) || amount <= 0 || !ProjectileOwnership.validDefender(player)
                || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.is(DamageTypeTags.BYPASSES_EFFECTS)
                || damageIdentity(source).isBlank()) return false;
        if (source.getDirectEntity() instanceof Projectile projectile && ProjectileRuntime.secondary(projectile)) return false;
        if (source.getEntity() != null) return ProjectileOwnership.hostileDamageSource(player, ProjectileOwnership.damageSource(source, player));
        return source.getDirectEntity() == null;
    }
    public static Incoming prepare(ServerPlayer player, DamageSource source, float amount, long event, boolean secondary) {
        var context = SkillEffectRuntime.context(player); State state = state(context); long now = context.now();
        boolean eligible = state != null && eligible(player, source, amount, secondary);
        var responsible = ProjectileOwnership.damageSource(source, player);
        double resistance = 0, chance = 0; String reason = eligible ? "eligible_primary_native_hit" : "invalid_secondary_or_unavoidable";
        if (eligible && state.meter.choice() == PostureMeter.Choice.ADAPTIVE)
            resistance = state.meter.adaptation(damageIdentity(source), now, context.settings().posture().adaptive());
        else if (eligible && state.meter.choice() == PostureMeter.Choice.BULWARK) {
            // Recheck current geometry: meter alone is never frontal defense after a turn or teleport.
            boolean frontal = stableNow(player, state, context.settings().posture())
                    && ThreatFacingResolver.incoming(player, responsible, context.settings().posture().bulwark());
            resistance = frontal ? state.meter.meter() * context.settings().posture().bulwark().maximumResistance() : 0;
            if (!frontal) reason = "not_still_frontal_living_threat";
        } else if (eligible && state.meter.choice() == PostureMeter.Choice.EVASIVE) {
            // Dodge only attributable direct melee and ordinary nonexplosive projectiles. Environment never rolls.
            boolean avoidable = responsible != null && !source.is(DamageTypeTags.IS_EXPLOSION)
                    && !source.is(DamageTypeTags.IS_FIRE) && (source.is(DamageTypeTags.IS_PROJECTILE)
                    || source.isDirect() && !source.is(DamageTypeTags.BYPASSES_ARMOR) && !source.is(DamageTypeTags.WITCH_RESISTANT_TO));
            chance = avoidable ? state.meter.meter() * context.settings().posture().evasive().maximumDodgeChance() : 0;
            if (!avoidable) reason = "damage_class_not_dodgeable";
        }
        Incoming result = new Incoming(event, now, state == null ? 0 : state.lifecycle, damageIdentity(source), state == null ? null : state.selected, responsible == null ? null : responsible.getUUID(),
                eligible, chance, -1, false, resistance, Float.isFinite(amount) ? Math.max(0, amount * resistance) : 0, 0, false, "not_attempted", reason);
        if (state != null) state.incoming = result; return result;
    }
    private static boolean stableNow(ServerPlayer player, State state, PostureBalanceSettings settings) {
        return state.meter.still() && state.forcedUntil <= player.level().getGameTime() && movementMode(player)
                && player.position().distanceTo(state.lastPosition) <= settings.movement().stillExitDisplacement()
                && state.packetDistance <= settings.movement().stillExitDisplacement()
                && state.packetTurn <= settings.movement().turnExitDegrees()
                && angle(state.yaw,player.getYRot()) + angle(state.pitch,player.getXRot()) <= settings.movement().turnExitDegrees();
    }
    public static Incoming roll(ServerPlayer player, Incoming event, double roll, float amount) {
        if (!Double.isFinite(roll) || roll < 0 || roll >= 1) roll = -1;
        boolean dodge = canRoll(player,event) && PostureMeter.dodge(roll, event.chance());
        Incoming result = new Incoming(event.event(),event.tick(),event.lifecycle(),event.damageType(),event.posture(),event.source(),event.eligible(),event.chance(),roll,dodge,
                event.resistance(), dodge && Float.isFinite(amount) ? Math.max(0,amount) : event.requestedPrevention(),0,false,event.knockback(),dodge ? "native_dodge" : "dodge_failed");
        State state = STATES.get(player); if (state != null) state.incoming = result; return result;
    }
    public static boolean canRoll(ServerPlayer player, Incoming event) {
        State state=state(SkillEffectRuntime.context(player));
        return state != null && SkillIds.EVASIVE_CURRENT.equals(event.posture()) && state.selected.equals(event.posture())
                && state.lifecycle==event.lifecycle()
                && event.tick()==player.level().getGameTime() && event.chance()>0;
    }
    public static void finish(ServerPlayer player, Incoming event, boolean accepted, boolean nativeBlock, double loss, double confirmed) {
        if (event == null) return;
        var context = SkillEffectRuntime.context(player); State state = state(context); if (state == null) return;
        if (!Objects.equals(state.selected,event.posture()) || state.lifecycle!=event.lifecycle()) return;
        boolean actual = event.dodged() || accepted;
        boolean changed = state.meter.hit(event.event(),context.now(),event.damageType(),event.eligible(),accepted && loss > 0,event.dodged(),context.settings().posture());
        state.incoming = new Incoming(event.event(),event.tick(),event.lifecycle(),event.damageType(),event.posture(),event.source(),event.eligible(),event.chance(),event.roll(),event.dodged(),
                event.resistance(),event.requestedPrevention(),actual || nativeBlock ? Math.max(0,confirmed) : 0,accepted,event.knockback(),
                event.dodged() ? "confirmed_dodge" : !accepted && !nativeBlock ? "native_rejected" : event.decision());
        if (changed && (event.dodged() || state.meter.choice() == PostureMeter.Choice.ADAPTIVE))
            feedback(player);
    }
    public static void knockback(ServerPlayer player, Incoming event, String decision) {
        State state = STATES.get(player);
        if (state != null && state.incoming.event() == event.event()) state.incoming = state.incoming.force(decision);
        if (decision.contains("rejected")) feedback(player);
    }
    private static void feedback(ServerPlayer player) {
        int particles = SkillEffectRuntime.resolvedSettings(player).guard().reprisal().particleCount();
        if (particles > 0) player.serverLevel().sendParticles(ParticleTypes.ENCHANT,player.getX(),player.getY()+1,player.getZ(),particles,.2,.2,.2,0);
    }
    public static boolean suppressKnockback(ServerPlayer player, Incoming event) {
        if (event == null || !event.eligible() || event.resistance() <= 0) return false;
        var context = SkillEffectRuntime.context(player); State state = state(context);
        return state != null && state.meter.choice() == PostureMeter.Choice.BULWARK
                && Objects.equals(state.selected,event.posture())
                && state.lifecycle==event.lifecycle()
                && state.meter.meter() >= context.settings().posture().bulwark().knockbackThreshold()
                && stableNow(player,state,context.settings().posture());
    }
    public static Snapshot snapshot(ServerPlayer player) {
        var context = SkillEffectRuntime.context(player); State state = state(context);
        if (state == null) return new Snapshot(player.getUUID(),player.getId(),0,player.level().dimension().location().toString(),null,false,0,1,"ineffective",context.now(),0,
                false,0,0,"inactive",ThreatFacingResolver.Result.none("inactive"),0,"",Incoming.empty());
        return new Snapshot(player.getUUID(),player.getId(),state.lifecycle,state.dimension,state.selected,true,state.meter.meter(),1,state.meter.reason(),context.now(),state.meter.expiresAt(),
                state.intent,state.displacement,state.turn,state.motion,state.threat,state.meter.stacks(),state.meter.damageType(),state.incoming);
    }
    public static List<String> diagnostics(ServerPlayer player) {
        Snapshot s=snapshot(player);
        return List.of("Posture: player="+s.player()+" entity="+s.entityId()+" lifecycle="+s.lifecycle()+" dimension="+s.dimension()+" selected="+s.selected()+" effective="+s.effective(),
                "  meter="+s.meter()+"/"+s.maximum()+" reason="+s.reason()+" tick="+s.tick()+" expires="+s.expiresAt(),
                "  HUD combatRemainingTicks="+CombatHudActivity.remainingTicks(player)+" windowTicks="+CombatHudActivity.WINDOW_TICKS+" (presentation only)",
                "  intent="+s.movementIntent()+" displacement="+s.displacement()+" turn="+s.turnDegrees()+" movement="+s.movement(),
                "  threat="+s.threat(), "  adaptation="+s.damageType()+" stacks="+s.stacks(), "  incoming="+s.incoming());
    }
    public static void forget(ServerPlayer player) { STATES.remove(player); }
    public static void clear() { STATES.clear(); nextLifecycle=0; }
}
