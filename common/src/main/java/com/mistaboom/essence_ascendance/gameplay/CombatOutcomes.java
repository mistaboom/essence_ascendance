package com.mistaboom.essence_ascendance.gameplay;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.attunement.AttunementGameplay;
import com.mistaboom.essence_ascendance.equipment.*;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime;
import com.mistaboom.essence_ascendance.skill.effect.SkillProcDamageService;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

/** Observes native commits without participating in damage resolution. No persistent entity/stack history. */
public final class CombatOutcomes {
    private CombatOutcomes() { }
    private static final ThreadLocal<Deque<Frame>> FRAMES = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<Contact> CONTACT = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> DISABLED = ThreadLocal.withInitial(() -> false);
    private static final Map<MinecraftServer, CombatSequences> SEQUENCES = new WeakHashMap<>();
    private static BiConsumer<MinecraftServer, CombatOutcome> listener;
    private record Contact(ServerPlayer actor, CombatOutcome.Cause cause, double distance) { }
    /** Live references exist only on this synchronous native call stack. */
    private static final class Frame {
        final LivingEntity target; final DamageSource source; final ServerPlayer actor;
        final CombatOutcome.Context context; final CombatOutcome.Cause cause;
        final double distance;
        final CompanionOutcomes.DamageSnapshot companion;
        double victimY, actorY, healthFraction;
        final boolean eligible;
        boolean noBoots, desperation, ledger;
        double loss; boolean death;
        CombatOutcome.Freeze freeze;
        Frame(LivingEntity target, DamageSource source, String action) {
            this.target = target; this.source = source;
            companion = CompanionOutcomes.capture(target, source, action);
            var proc = SkillProcDamageService.current();
            actor = proc != null ? proc.owner() : source.getEntity() instanceof ServerPlayer p ? p : null;
            Contact contact = CONTACT.get();
            boolean direct = actor != null && source.getEntity() == actor && source.getDirectEntity() == actor
                    && proc == null && !EquipmentDamageService.isReflectionInProgress() && !EquipmentDamageService.isSecondarySkillDamage();
            CombatOutcome.Cause origin = proc == null ? null : proc.root().combatCause();
            if (origin != null && !origin.actor().equals(actor.getUUID())) origin = null;
            if (origin == null && direct && contact != null && contact.actor == actor) origin = contact.cause;
            if (origin == null && proc == null && source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile projectile) {
                var state = com.mistaboom.essence_ascendance.projectile.ProjectileRuntime.state(projectile);
                if (state != null && actor != null && state.owner.equals(actor.getUUID()) && !state.redirected
                        && com.mistaboom.essence_ascendance.projectile.ProjectileRuntime.validOwnership(projectile,state))
                    origin = state.combatCause;
            }
            cause = origin != null ? qualified(origin,actor) : new CombatOutcome.Cause(action, actor == null ? new UUID(0,0) : actor.getUUID(),
                    direct ? weapon(actor.getMainHandItem(), "MELEE") : CombatOutcome.Weapon.UNKNOWN,
                    AttunementGameplay.eligible(actor) && !(source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile)
                            && (proc == null || proc.kind().reflectedOutcome()));
            distance = direct && contact != null && contact.actor == actor ? contact.distance : Double.NaN;
            context = actor == null ? null : context(actor, target, cause);
            eligible = actor != target && cause.eligibleOrigin() && AttunementGameplay.eligible(actor) && actor.level() == target.level();
            victimY = target.getY(); actorY = actor == null ? Double.NaN : actor.getY();
            noBoots = actor != null && actor.getItemBySlot(EquipmentSlot.FEET).isEmpty();
            desperation = actor != null && SkillEffectRuntime.desperationActive(actor);
            healthFraction = actor == null || !(actor.getMaxHealth() > 0) ? Double.NaN : actor.getHealth() / (double)actor.getMaxHealth();
            ledger = eligible && com.mistaboom.essence_ascendance.utility.UtilitySenseService.ledgerEligible(actor,target);
        }
    }
    public static void register(BiConsumer<MinecraftServer,CombatOutcome> consumer) {
        if (listener != null) throw new IllegalStateException("Combat consumer already registered");
        listener = Objects.requireNonNull(consumer);
    }
    public static CombatOutcome.Weapon weapon(ItemStack stack, String mode) {
        String profile = stack.getItem() instanceof EquipmentProfileItem item ? item.equipmentProfileId().toString() : "";
        return new CombatOutcome.Weapon(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), profile,
                EquipmentTierData.tier(stack).ordinal(), mode);
    }
    public static CombatOutcome.Cause currentCause(ServerPlayer player) {
        if (DISABLED.get()) return null;
        Frame frame = FRAMES.get().peek();
        if (frame != null && frame.actor == player) return qualified(frame.cause,player);
        Contact contact = CONTACT.get();
        return contact != null && contact.actor == player ? qualified(contact.cause,player) : null;
    }
    private static CombatOutcome.Cause qualified(CombatOutcome.Cause cause,ServerPlayer actor) {
        return cause.eligibleOrigin() && !AttunementGameplay.eligible(actor)
                ? new CombatOutcome.Cause(cause.actionId(),cause.actor(),cause.weapon(),false) : cause;
    }
    public static <T> T contact(ServerPlayer actor, CombatOutcome.Cause cause, double distance, java.util.function.Supplier<T> original) {
        Contact previous = CONTACT.get(); CONTACT.set(new Contact(actor,cause,distance));
        try { return original.get(); } finally { if (previous == null) CONTACT.remove(); else CONTACT.set(previous); }
    }
    public static ContactScope openContact(ServerPlayer actor, CombatOutcome.Cause cause, double distance) {
        return new ContactScope(new Contact(actor,cause,distance));
    }
    public static final class ContactScope implements AutoCloseable {
        private final Contact previous = CONTACT.get();
        private boolean closed;
        private ContactScope(Contact contact) { CONTACT.set(contact); }
        public void close() {
            if (closed) return; closed = true;
            if (previous == null) CONTACT.remove(); else CONTACT.set(previous);
        }
    }
    public static boolean damage(LivingEntity target, DamageSource source, String action, BooleanSupplier original) {
        if (DISABLED.get()) return original.getAsBoolean();
        Frame frame;
        try { frame = new Frame(target,source,action); }
        catch (RuntimeException failure) {
            EssenceAscendance.LOGGER.error("Combat observation capture failed",failure);
            DISABLED.set(true);
            try { return original.getAsBoolean(); } finally { DISABLED.remove(); }
        }
        Deque<Frame> frames = FRAMES.get(); frames.push(frame);
        try {
            boolean accepted = original.getAsBoolean();
            try {
            if (accepted && frame.loss > 0) RodOutcomes.committedDamage(target,source,frame.loss,frame.death);
            if (accepted && frame.loss > 0) CompanionOutcomes.committed(target,source,frame.companion,frame.loss,frame.death);
            if (accepted && frame.loss > 0 && frame.actor != null) {
                com.mistaboom.essence_ascendance.skill.effect.RecentHostileCombat.acceptedEncounterDamage(frame.actor,target);
                // Encounter rejection also observes ineligible players, preventing creative damage then mode switching.
                com.mistaboom.essence_ascendance.utility.UtilitySanctuaryService.acceptedDamage(frame.actor,target);
                if (frame.eligible && AttunementGameplay.eligible(frame.actor)) {
                    publish(server(frame.actor),new CombatOutcome.Damage(frame.context,frame.distance,frame.loss));
                    if (frame.freeze != null) publish(server(frame.actor),frame.freeze);
                    if (frame.death) {
                        var result = sequences(server(frame.actor)).killed(frame.context,now(frame.actor));
                        if (result.accepted()) publish(server(frame.actor),new CombatOutcome.Kill(frame.context,frame.victimY,frame.actorY,
                                frame.noBoots,frame.desperation,frame.healthFraction,frame.ledger,result.hostileKills()));
                    }
                }
            }
            } catch (RuntimeException failure) { EssenceAscendance.LOGGER.error("Combat observation commit failed",failure); }
            return accepted;
        } finally { frames.pop(); if (frames.isEmpty()) FRAMES.remove(); }
    }
    public static void measured(LivingEntity target, DamageSource source, double loss) {
        if (DISABLED.get()) return;
        Frame frame = FRAMES.get().peek();
        if (frame != null && frame.target == target && frame.source == source && Double.isFinite(loss)) frame.loss += Math.max(0,loss);
    }
    /** Native committed death only. A prior hit, attack request or standalone administrative die cannot satisfy this. */
    public static void death(LivingEntity target, DamageSource source) {
        if (DISABLED.get()) return;
        Frame frame = FRAMES.get().peek();
        if (frame != null && frame.target == target && frame.source == source) frame.death = true;
    }
    /** Snapshot before the actual mitigated health write, not an earlier attack request. */
    public static void beforeHealthWrite(LivingEntity target, DamageSource source) {
        if (DISABLED.get()) return;
        Frame frame = FRAMES.get().peek();
        if (frame == null || frame.target != target || frame.source != source || frame.actor == null) return;
        try {
            ServerPlayer actor = frame.actor;
            frame.victimY = target.getY();
            frame.actorY = actor.getY();
            frame.noBoots = actor.getItemBySlot(EquipmentSlot.FEET).isEmpty();
            frame.desperation = SkillEffectRuntime.desperationActive(actor);
            frame.healthFraction = actor.getMaxHealth() > 0 ? actor.getHealth() / (double)actor.getMaxHealth() : Double.NaN;
            frame.ledger = frame.eligible && com.mistaboom.essence_ascendance.utility.UtilitySenseService.ledgerEligible(actor,target);
        } catch (RuntimeException failure) {
            frame.desperation = false; frame.ledger = false;
            EssenceAscendance.LOGGER.error("Combat pre-damage observation failed",failure);
        }
    }
    public static void frozen(ServerPlayer player, LivingEntity target) {
        if (DISABLED.get() || !AttunementGameplay.eligible(player) || !target.isAlive()) return;
        var cause = currentCause(player);
        if (cause != null && !cause.eligibleOrigin()) return;
        var outcome = new CombatOutcome.Freeze(context(player,target,new CombatOutcome.Cause(
                AttunementGameplay.action("full_freeze"),player.getUUID(),CombatOutcome.Weapon.UNKNOWN)));
        Frame frame = FRAMES.get().peek();
        if (frame != null && frame.target == target && frame.actor == player) frame.freeze = outcome;
        else publish(player.server,outcome);
    }
    public static void pacified(ServerPlayer player, LivingEntity target, boolean targetedActor, boolean undamagedEncounter) {
        if (!DISABLED.get() && AttunementGameplay.eligible(player) && target.isAlive())
            publish(player.server,new CombatOutcome.Pacified(context(player,target,new CombatOutcome.Cause(
                    AttunementGameplay.action("pacification"),player.getUUID(),CombatOutcome.Weapon.UNKNOWN)),targetedActor,undamagedEncounter));
    }
    private static CombatOutcome.Context context(ServerPlayer player, LivingEntity target, CombatOutcome.Cause cause) {
        return new CombatOutcome.Context(cause.actionId(),player.getUUID(),target.getUUID(),
                BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString(),target instanceof Enemy && !player.isAlliedTo(target),
                target.level().dimension().location().toString(),now(player),cause.weapon());
    }
    private static long now(ServerPlayer player) { return player.server.overworld().getGameTime(); }
    private static MinecraftServer server(ServerPlayer player) { return player.server; }
    public static CombatSequences sequences(MinecraftServer server) { return SEQUENCES.computeIfAbsent(server,ignored -> new CombatSequences()); }
    public static void forget(ServerPlayer player) {
        CombatSequences sequences = SEQUENCES.get(player.server); if (sequences != null) sequences.forget(player.getUUID());
    }
    public static void clear(MinecraftServer server) { SEQUENCES.remove(server); CompanionOutcomes.clear(server); }
    private static void publish(MinecraftServer server, CombatOutcome outcome) {
        var player = server.getPlayerList().getPlayer(outcome.context().actor());
        if (server.isSameThread() && AttunementGameplay.eligible(player)) {
            if (outcome instanceof CombatOutcome.Kill kill) {
                try { CompanionOutcomes.playerKilled(player, kill); }
                catch (RuntimeException failure) { EssenceAscendance.LOGGER.error("Companion kill observation failed", failure); }
            }
            try { MovementOutcomes.combat(player, outcome); }
            catch (RuntimeException failure) { EssenceAscendance.LOGGER.error("Movement combat observation failed", failure); }
        }
        if (listener != null) try { listener.accept(server,outcome); }
        catch (RuntimeException failure) { EssenceAscendance.LOGGER.error("Combat outcome delivery failed",failure); }
    }
}
