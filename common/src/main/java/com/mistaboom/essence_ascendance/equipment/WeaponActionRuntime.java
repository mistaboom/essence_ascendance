package com.mistaboom.essence_ascendance.equipment;

//? if >=26.1.2
/*import net.minecraft.world.InteractionResult;*/
//? if <26.1.2
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import static com.mistaboom.essence_ascendance.equipment.WeaponActionGeometry.*;

/** Shared boundary for weapons whose attacks must be handled by an authoritative action runtime. */
public final class WeaponActionRuntime {
    private static final int MAX_OBSERVED_INPUTS_PER_TICK = 16;
    public static final int MAX_ACTION_STARTS_PER_TICK = 16;
    private WeaponActionRuntime() { }
    public enum Mode { STAB, SWIPE }
    public enum Phase { OUTWARD, EXTEND, SWEEP, RETRACT, RECOVERY, CANCELLED, IDLE }
    public record Snapshot(long actionId, Mode mode, Phase phase, long acceptedAt, long readyAt, long sampledAt,
                           int selectedSlot, EquipmentTier tier, double range, Pose previous, Pose current,
                           List<Knot> knots, boolean physicalContact, int contactedEntity) {
        public Snapshot { knots=List.copyOf(knots); }
        public float readiness(double now) {
            return (float)Math.clamp((now-acceptedAt)/Math.max(1,readyAt-acceptedAt),0,1);
        }
    }
    private static final Map<UUID, Gate> GATES = new HashMap<>();
    private static long nextIdentity;
    private static final class Gate {
        long readyAt, lastInputSequence=-1, lastAcceptedInputSentAt=Long.MIN_VALUE;
        long actionStartTick=Long.MIN_VALUE;
        int actionStarts;
        boolean busy;
        Action action;
    }
    /** All durable-per-action state lives here. No ThreadLocal survives a contact call. */
    private static final class Action {
        final long id, acceptedAt;
        final Mode mode;
        final net.minecraft.world.entity.HumanoidArm mainArm;
        final double playerScale;
        final ItemStack stack;
        final int slot;
        final EquipmentTier tier;
        final boolean fractured;
        final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
        final double range;
        final String variant;
        final WeaponActionDamage.Offense offense;
        final GlaiveTuning.ActionPhases timing;
        final Set<UUID> attempted = new HashSet<>();
        long lastTick;
        double outwardEnd, retractDistance, retractSweep;
        boolean primarySpent, wearSpent, finalized, contact;
        int observedInputs;
        int contactedEntity=-1;
        Phase phase=Phase.OUTWARD;
        Pose previous,current,desired;
        List<Knot> knots;
        final List<Pose> observed=new ArrayList<>();
        Action(ServerPlayer player,long now, double range, WeaponActionDamage.Offense offense,Mode mode) {
            id=++nextIdentity; acceptedAt=now; lastTick=now; stack=player.getMainHandItem();
            slot=selectedSlot(player); tier=EquipmentTierData.tier(stack); dimension=player.level().dimension();
            fractured=FracturedEquipmentData.isFractured(stack);
            this.range=range; this.offense=offense; variant=variant(player,stack);
            this.mode=mode; mainArm=player.getMainArm(); playerScale=player.getScale();
            timing=mode==Mode.STAB ? GlaiveTuning.STAB:GlaiveTuning.SWIPE;
            outwardEnd=now+timing.extensionTicks()+timing.outwardTicks();
            phase=mode==Mode.STAB ? Phase.OUTWARD:Phase.EXTEND;
            current=GlaiveGrip.sweep(pose(player,null,REST_TIP,range),mode==Mode.STAB ? 0:GlaiveTuning.swipeAngle(0),mainArm,playerScale);
            desired=current; previous=current; knots=List.of(new Knot(0,current));
        }
    }
    public static long now(ServerPlayer player) { return player.server.overworld().getGameTime(); }
    public static Snapshot snapshot(ServerPlayer player) {
        Gate gate=GATES.get(player.getUUID());
        if (gate==null || gate.action==null) return null;
        Action a=gate.action;
        return new Snapshot(a.id,a.mode,a.phase,a.acceptedAt,gate.readyAt,a.lastTick,a.slot,a.tier,a.range,
                a.previous,a.current,a.knots,a.contact,a.contactedEntity);
    }
    private static Pose pose(ServerPlayer player,Pose previous,double distance,double range) {
        return WeaponActionAim.pose(player,previous,distance,range);
    }
    private static String variant(ServerPlayer player,ItemStack stack) {
        return com.mistaboom.essence_ascendance.equipment.personalized.PersonalizedEquipmentService.active(player,stack)
                .filter(p -> p.perks().containsKey(GlaiveTuning.PERSONALIZED_RANGE)).map(p -> p.id()).orElse("");
    }

    /** Intent freshness is checked against server time and cached equipment revision, never client permission. */
    public static boolean input(ServerPlayer player,boolean stab,long sequence,long sentAt,long policyRevision) {
        // Old entity work can still be queued across death/dimension transitions. It must not
        // advance the UUID-owned sequence before accept() rejects the removed/dead actor.
        if (!player.isAlive() || player.isRemoved() || player.isSpectator()) return false;
        Gate gate=GATES.computeIfAbsent(player.getUUID(),id -> new Gate());
        if (sequence<=gate.lastInputSequence) return false;
        gate.lastInputSequence=sequence;
        long age=player.level().getGameTime()-sentAt;
        if (age < -2 || age>10 || sentAt<gate.lastAcceptedInputSentAt
                || TwoHandedEquipmentService.decision(player).revision()!=policyRevision) return false;
        boolean accepted=attempt(player,stab);
        if (accepted) gate.lastAcceptedInputSentAt=sentAt;
        return accepted;
    }

    public static boolean requiresActionRuntime(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof EquipmentProfileItem item
                && EquipmentProfileRegistry.get(item.equipmentProfileId())
                .map(profile -> profile.weaponCapabilities().melee()
                        == EquipmentWeaponCapabilities.MeleeExecution.ACTION).orElse(false);
    }

    public static boolean allowsNativeAttack(ItemStack stack) {
        return !requiresActionRuntime(stack);
    }

    public static boolean consumesWorldInput(ServerPlayer player) {
        var d = TwoHandedEquipmentService.decision(player);
        return d.actionWeapon() || d.status() == TwoHandedEquipmentService.Status.PENDING;
    }

    /** Main-hand-only acceptance; each press replaces the current action and restarts native readiness. */
    public static boolean attempt(ServerPlayer player, boolean stab) {
        TwoHandedEquipmentService.enterCombat();
        try { return accept(player,stab); }
        finally { TwoHandedEquipmentService.exitCombat(); }
    }
    private static boolean accept(ServerPlayer player,boolean stab) {
        var d = TwoHandedEquipmentService.decision(player);
        if (!player.isAlive() || player.isRemoved() || player.isSpectator() || !validAim(player) || !d.actionWeapon()
                || !TwoHandedEquipmentService.validateAttack(player)) return false;
        Gate gate=GATES.computeIfAbsent(player.getUUID(),id -> new Gate());
        if (gate.busy || player.isUsingItem() || player.isAutoSpinAttack() || !d.mayAttack()) return false;
        gate.busy=true;
        try {
            long now=now(player);
            if (gate.actionStartTick!=now) { gate.actionStartTick=now; gate.actionStarts=0; }
            // Native fallback packets have no custom sequence. Bound same-tick restarts
            // without denying ordinary distinct presses before the strength meter fills.
            if (gate.actionStarts>=MAX_ACTION_STARTS_PER_TICK) return false;
            Action previous=gate.action;
            if (previous!=null && previous.phase!=Phase.IDLE && previous.phase!=Phase.CANCELLED) {
                previous.phase=Phase.CANCELLED;
                finalizeOutcome(player,previous);
            }
            // The native 0.2 + 0.8 * readiness^2 hit formula uses strength at the press,
            // before this new action resets the player's attack ticker and indicator.
            var offense=WeaponActionDamage.snapshot(player);
            double range=GlaiveTuning.maximumRange(player,player.getMainHandItem());
            Action action=new Action(player,now,range,offense,stab ? Mode.STAB:Mode.SWIPE);
            gate.action=action;
            gate.actionStarts++;
            gate.readyAt=now+action.timing.extensionTicks()+action.timing.outwardTicks()+action.timing.retractionTicks()+action.timing.recoveryTicks(offense.speed());
            // 26.1.2 also resets the item-swap animation in resetAttackStrengthTicker;
            // a native attack resets only combat strength there.
            //? if >=26.1.2
            /*player.resetOnlyAttackStrengthTicker();*/
            //? if <26.1.2
            player.resetAttackStrengthTicker();
            com.mistaboom.essence_ascendance.network.WeaponActionStatePayload.sync(player);
            return true;
        } finally { gate.busy=false; }
    }

    public static void tick(ServerPlayer player) {
        TwoHandedEquipmentService.enterCombat();
        try { update(player); }
        finally { TwoHandedEquipmentService.exitCombat(); }
    }
    private static void update(ServerPlayer player) {
        Gate gate=GATES.get(player.getUUID());
        if (gate==null || gate.action==null || gate.busy) return;
        Action a=gate.action;
        if (a.phase==Phase.IDLE) return;
        long now=now(player);
        if (now<=a.lastTick) return;
        gate.busy=true;
        try {
        if (a.phase!=Phase.CANCELLED && a.phase!=Phase.IDLE && !valid(player,a)) cancel(player);
        a.previous=a.current; a.knots=List.of(new Knot(0,a.current));
        if (a.phase==Phase.EXTEND || a.phase==Phase.SWEEP) {
            updateSwipe(player,a,now);
        } else if (a.phase==Phase.OUTWARD) {
            double progress=Math.clamp((double)(now-a.acceptedAt)/a.timing.outwardTicks(),0,1);
            Pose next=pose(player,a.observed.isEmpty() ? a.current : a.observed.getLast(),GlaiveTuning.stabTipDistance(a.range,now-a.acceptedAt),a.range);
            // Teleports are discontinuities, not observed physical blade movement. Fail closed for the whole action.
            if (a.current.pivot().distanceTo(next.pivot())>GlaiveTuning.MAXIMUM_PIVOT_STEP) cancel(player);
            else {
                remember(a,next);
                var solved=new ArrayList<Knot>(); solved.add(new Knot(0,a.current));
                double initialDistance=a.current.tipDistance();
                int count=a.observed.size();
                var scene=WeaponActionCollision.world(player);
                for (int i=0;i<count;i++) {
                    Pose observed=a.observed.get(i).withDistance(initialDistance+(next.tipDistance()-initialDistance)*(i+1)/count);
                    var trace=a.range>10 ? WeaponActionFastCollision.stab(a.current,observed,player)
                            : WeaponActionCollision.trace(a.current,observed,scene);
                    for (var knot:trace.knots().subList(1,trace.knots().size()))
                        solved.add(new Knot((i+knot.fraction())/count,knot.pose()));
                    a.current=trace.contact()==null ? observed : trace.contact().pose();
                    if (trace.contact()!=null) {
                        a.contact=true;
                        Entity entity=trace.contact().obstacle().entity();
                        a.contactedEntity=entity==null ? -1 : entity.getId();
                        if (entity!=null) contact(player,a,entity);
                        a.outwardEnd=now-1+(i+trace.contact().fraction())/count;
                        break;
                    }
                }
                a.observed.clear(); a.knots=List.copyOf(solved);
                if (a.contact || progress>=1) endOutward(player,a);
            }
        } else if (a.phase==Phase.RETRACT) {
            double progress=Math.clamp((now-a.outwardEnd)/a.timing.retractionTicks(),0,1);
            a.current=pose(player,a.current,a.retractDistance+(REST_TIP-a.retractDistance)*progress,a.range)
                    .withSweep(a.retractSweep);
            var rest=GlaiveGrip.rest(player,1,GlaiveGrip.frame(player.getEyePosition(),player.getYRot(),player.getXRot()));
            rest=new Pose(rest.pivot(),unwrapYaw(a.current.yaw(),rest.yaw()),rest.pitch(),a.current.tipDistance(),0,
                    unwrapYaw(a.current.roll(),rest.roll()));
            a.current=interpolate(a.current,rest,progress);
            a.knots=List.of(new Knot(0,a.previous),new Knot(1,a.current));
            if (progress>=1) a.phase=Phase.RECOVERY;
        }
        if (now>=gate.readyAt && a.phase!=Phase.OUTWARD && a.phase!=Phase.EXTEND && a.phase!=Phase.SWEEP && a.phase!=Phase.RETRACT) a.phase=Phase.IDLE;
        a.observedInputs=0;
        a.lastTick=now;
        com.mistaboom.essence_ascendance.network.WeaponActionStatePayload.sync(player);
        } finally { gate.busy=false; }
    }
    private static Pose swipePose(Pose aim,Action a,double elapsed) {
        return GlaiveGrip.sweep(aim.withDistance(GlaiveTuning.swipeTipDistance(a.range,elapsed)),GlaiveTuning.swipeAngle(elapsed),a.mainArm,a.playerScale);
    }
    private static void updateSwipe(ServerPlayer player,Action a,long now) {
        Pose next=pose(player,a.observed.isEmpty() ? a.desired:a.observed.getLast(),a.range,a.range);
        if (a.desired.pivot().distanceTo(next.pivot())>GlaiveTuning.MAXIMUM_PIVOT_STEP) { cancel(player); return; }
        remember(a,next);
        var solved=new ArrayList<Knot>(); solved.add(new Knot(0,a.previous));
        var scene=WeaponActionCollision.world(player,true);
        double duration=now-a.lastTick,elapsed=a.lastTick-a.acceptedAt;
        int count=a.observed.size();
        for (int i=0;i<count && a.phase!=Phase.CANCELLED;i++) {
            double start=elapsed+duration*i/count,end=Math.min(elapsed+duration*(i+1)/count,a.timing.extensionTicks()+a.timing.outwardTicks());
            if (end<start) break;
            Pose aim=a.observed.get(i),from=a.desired,to=swipePose(aim,a,end);
            // Preserve the explicit non-damaging/damaging boundary even when an update spans it.
            if (start<a.timing.extensionTicks() && end>a.timing.extensionTicks()) {
                double fraction=(a.timing.extensionTicks()-start)/(end-start);
                Pose boundary=swipePose(interpolate(from,to,fraction),a,a.timing.extensionTicks());
                if (!swipeSegment(player,a,from,boundary,start,a.timing.extensionTicks(),elapsed,duration,scene,solved,false)) break;
                from=boundary; start=a.timing.extensionTicks();
            }
            if (!swipeSegment(player,a,from,to,start,end,elapsed,duration,scene,solved,start>=a.timing.extensionTicks())) break;
            a.desired=to;
        }
        a.observed.clear(); a.knots=List.copyOf(solved);
        if (a.phase!=Phase.CANCELLED) {
            a.phase=now-a.acceptedAt<a.timing.extensionTicks() ? Phase.EXTEND:Phase.SWEEP;
            if (now>=a.outwardEnd) endOutward(player,a);
        }
    }
    private static boolean swipeSegment(ServerPlayer player,Action a,Pose from,Pose to,double start,double end,
                                        double elapsed,double duration,WeaponActionCollision.Scene scene,List<Knot> knots,boolean damaging) {
        java.util.function.Predicate<WeaponActionCollision.Contact> visitor=contact -> {
            a.current=contact.pose(); a.contact=true;
            Entity entity=contact.obstacle().entity(); a.contactedEntity=entity==null ? -1:entity.getId();
            if (damaging && entity!=null) contact(player,a,entity);
            return a.phase!=Phase.CANCELLED;
        };
        var result=a.range>10 ? WeaponActionFastCollision.swipe(from,to,player,visitor)
                : WeaponActionCollision.swipe(from,to,scene,visitor);
        for (Knot knot:result.knots()) knots.add(new Knot((start-elapsed+(end-start)*knot.fraction())/duration,knot.pose()));
        if (result.completed() && !result.knots().isEmpty()) a.current=result.knots().getLast().pose();
        return result.completed();
    }
    private static boolean valid(ServerPlayer player,Action a) {
        var d=TwoHandedEquipmentService.decision(player);
        return player.isAlive() && !player.isRemoved() && !player.isSpectator() && validAim(player) && player.level().dimension()==a.dimension
                && player.getMainArm()==a.mainArm && player.getScale()==a.playerScale
                && player.getMainHandItem()==a.stack && selectedSlot(player)==a.slot
                && d.actionWeapon() && d.mayAttack() && FracturedEquipmentData.isFractured(a.stack)==a.fractured
                && EquipmentTierData.tier(a.stack)==a.tier && variant(player,a.stack).equals(a.variant);
    }
    private static boolean validAim(ServerPlayer player) {
        return Float.isFinite(player.getYRot()) && Float.isFinite(player.getXRot())
                && com.mistaboom.essence_ascendance.projectile.ProjectileControlMath.finite(player.getEyePosition());
    }
    private static void contact(ServerPlayer player,Action a,Entity entity) {
        if (!valid(player,a)) { cancel(player); return; }
        if (!a.attempted.add(entity.getUUID())) return;
        var result=WeaponActionDamage.hit(player,entity,a.offense,a.timing.damageScalar(),!a.primarySpent,a.current.forward());
        if (result.accepted() && result.loss()>0) {
            a.primarySpent=true;
            if (!a.wearSpent) { a.wearSpent=true; WeaponActionDamage.wear(player,a.stack,a.slot); }
        }
        if (!valid(player,a)) cancel(player);
    }
    private static void endOutward(ServerPlayer player,Action a) {
        if (a.phase==Phase.CANCELLED) return;
        finalizeOutcome(player,a);
        a.retractDistance=a.current.tipDistance(); a.retractSweep=a.current.sweepDegrees(); a.phase=Phase.RETRACT;
    }
    private static void finalizeOutcome(ServerPlayer player,Action a) {
        if (a.finalized) return;
        a.finalized=true;
        if (!a.primarySpent && a.offense.skills().runtimeIdentity()==com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime.attackRuntimeIdentity(player))
            com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime.onAirSwing(player);
    }
    public static void cancel(ServerPlayer player) {
        Gate gate=GATES.get(player.getUUID());
        if (gate==null || gate.action==null) return;
        Action a=gate.action;
        if (a.phase==Phase.IDLE || a.phase==Phase.CANCELLED) return;
        boolean ownBusy=!gate.busy;
        if (ownBusy) gate.busy=true;
        try {
            a.phase=Phase.CANCELLED;
            finalizeOutcome(player,a);
            com.mistaboom.essence_ascendance.network.WeaponActionStatePayload.sync(player);
        } finally { if (ownBusy) gate.busy=false; }
    }
    /** Completed inventory mutations invalidate even a swap away and back between gameplay ticks. */
    public static void equipmentChanged(ServerPlayer player) {
        Gate gate=GATES.get(player.getUUID());
        if (gate!=null && gate.action!=null && (player.getMainHandItem()!=gate.action.stack
                || selectedSlot(player)!=gate.action.slot)) cancel(player);
    }
    /** Retain actual accepted native move/rotation packets, including a turn and return within one tick. */
    public static void observeAim(ServerPlayer player) {
        Gate gate=GATES.get(player.getUUID());
        if (gate==null || gate.action==null || gate.action.phase!=Phase.OUTWARD
                && gate.action.phase!=Phase.EXTEND && gate.action.phase!=Phase.SWEEP) return;
        Action a=gate.action;
        if (!validAim(player)) { cancel(player); return; }
        // A delayed/native packet burst must not become unbounded collision work on the next tick.
        // Cancel rather than collapsing an arbitrary path into an invented damaging sweep.
        if (++a.observedInputs>MAX_OBSERVED_INPUTS_PER_TICK) { cancel(player); return; }
        Pose previous=a.observed.isEmpty() ? (a.mode==Mode.STAB ? a.current:a.desired) : a.observed.getLast();
        Pose current=pose(player,previous,a.current.tipDistance(),a.range);
        if (!current.equals(previous)) {
            if (previous.pivot().distanceTo(current.pivot())>GlaiveTuning.MAXIMUM_PIVOT_STEP) cancel(player);
            else remember(a,current);
        }
    }
    private static void remember(Action action,Pose pose) {
        // Long-range collision has a fixed per-segment budget. Bound packet bursts as well,
        // retaining the earliest turns and the latest accepted aim within this server tick.
        if (action.range>10 && action.observed.size()>=4) action.observed.set(3,pose);
        else action.observed.add(pose);
    }
    public static void disconnected(ServerPlayer player) {
        cancel(player);
        Gate gate=GATES.get(player.getUUID());
        if (gate!=null) {
            gate.action=null; gate.lastInputSequence=-1; gate.lastAcceptedInputSentAt=Long.MIN_VALUE;
            gate.actionStartTick=Long.MIN_VALUE; gate.actionStarts=0;
        }
        com.mistaboom.essence_ascendance.network.WeaponActionStatePayload.forget(player);
    }
    public static void clear() { GATES.clear(); nextIdentity=0; }

    private static int selectedSlot(ServerPlayer player) {
        //? if >=26.1.2
        /*return player.getInventory().getSelectedSlot();*/
        //? if <26.1.2
        return player.getInventory().selected;
    }

    //? if >=26.1.2
    /*public static InteractionResult tryUse(Player player, InteractionHand hand) {*/
    //? if <26.1.2
    public static InteractionResultHolder<ItemStack> tryUse(Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer server) attempt(server, true);
        return tryUse(stack);
    }

    /** Consume vanilla use after the authoritative executor has received the press. */
    //? if >=26.1.2
    /*public static InteractionResult tryUse(ItemStack stack) {*/
    //? if <26.1.2
    public static InteractionResultHolder<ItemStack> tryUse(ItemStack stack) {
        //? if >=26.1.2
        /*return requiresActionRuntime(stack) ? InteractionResult.FAIL : InteractionResult.PASS;*/
        //? if <26.1.2
        return requiresActionRuntime(stack) ? InteractionResultHolder.fail(stack)
                //? if <26.1.2
                : InteractionResultHolder.pass(stack);
    }
}
