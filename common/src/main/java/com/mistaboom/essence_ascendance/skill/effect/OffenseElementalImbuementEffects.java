package com.mistaboom.essence_ascendance.skill.effect;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Six elemental skills composed from shared attack, buildup, condition, targeting, and proc primitives. */
final class OffenseElementalImbuementEffects {
    private OffenseElementalImbuementEffects() { }

    static List<SkillEffectHandler> handlers() {
        return List.of(new Kindling(), new Combustion(), new Frostbite(), new Shatter(),
                new StaticCharge(), new ChainStrike());
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(EssenceAscendance.MOD_ID, path);
    }

    private static String decimal(double value) {
        return SkillEffectHudCards.decimal(value);
    }

    private static SkillEffectHudEntry.Text targets(int count) {
        return SkillEffectHudEntry.Text.translated("hud.essence_ascendance.targets", Integer.toString(count));
    }

    private static int uniqueTargets(Set<UUID> buildup, Set<UUID> condition) {
        Set<UUID> ids = new HashSet<>(buildup);
        ids.addAll(condition);
        return ids.size();
    }

    private static final class Kindling implements SkillEffectHudHandler {
        private static final int BURN_PULSE_TICKS = 20;

        @Override public ResourceLocation id() { return SkillIds.KINDLING; }

        @Override public void successfulAttack(SkillEffectRuntime.Context context, AttackResultContext result) {
            if (!result.primary() || !result.target().isAlive() || result.target().isRemoved()) return;
            KindlingState state = context.state(id(), () -> new KindlingState(context.player().getUUID()));
            var settings = context.settings().kindling();
            if (state.burning.active(result.target(), context.now())) {
                state.ignite(result.target(), context.now(), settings.ignitionDurationTicks(),
                        result.damageDealt(), settings.burningDamagePercentPerSecond());
                return;
            }
            int heat = state.heat.add(result.target(), context.now(), settings.maxHeat(),
                    settings.heatPerHit(), settings.heatExpiryTicks());
            if (settings.maxHeat() > 0 && heat >= settings.maxHeat()) {
                state.heat.remove(result.target());
                state.ignite(result.target(), context.now(), settings.ignitionDurationTicks(),
                        result.damageDealt(), settings.burningDamagePercentPerSecond());
            }
        }

        @Override public void tick(SkillEffectRuntime.Context context) {
            KindlingState state = context.existingState(id());
            if (state == null) return;
            for (LivingEntity target : state.burning.activeTargets(context.now())) {
                // Keep the vanilla fire visual/status without allowing its fixed one-point tick
                // to replace this source-owned, attack-scaled periodic damage.
                target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(), 2));
            }
            for (TargetConditionState.Pulse pulse : state.burning.drainDuePulses(context.now())) {
                if (pulse.magnitude() <= 0.0) continue;
                var lineage = pulse.lineage();
                if (lineage != null && !context.isEffective(lineage.sourceSkill())) continue;
                var combustion = context.settings().combustion();
                PropagationBudget root = lineage == null
                        ? new PropagationBudget(combustion.maximumGeneration(), combustion.rootTargetBudget()) : lineage.root();
                root.seed(pulse.target().getUUID());
                SkillProcDamageService.hurt(context.player(), pulse.target(), (float) pulse.magnitude(),
                        SkillProcDamageService.DamageKind.BURNING, lineage == null ? id() : lineage.sourceSkill(), root,
                        lineage == null ? 0 : lineage.generation());
            }
        }

        @Override public void reconcile(SkillEffectRuntime.Context context) {
            KindlingState state = context.existingState(id());
            if (state == null) return;
            state.heat.reconcile(context.player(), context.now(), context.settings().kindling().maxHeat());
            state.burning.reconcile(context.player(), context.now());
            state.burning.discardInactiveLineage(context::isEffective);
            if (state.empty()) context.discardState(id());
        }

        @Override public double damageMultiplier(SkillEffectRuntime.Context context, LivingEntity target,
                                                 DamageSource source, AttackCategory primaryCategory) {
            if (primaryCategory == null) return 1.0;
            KindlingState state = context.existingState(id());
            return state != null && state.burning.active(target, context.now())
                    ? 1.0 + context.settings().kindling().burningDamageAmplificationPercent() / 100.0
                    : 1.0;
        }

        @Override public SkillEffectHudEntry hudEntry(SkillEffectRuntime.Context context) {
            KindlingState state = context.existingState(id());
            var settings = context.settings().kindling();
            SourceOwnedBuildupState.Snapshot top = state == null ? null
                    : state.heat.mostDeveloped(context.now(), settings.maxHeat());
            int burning = state == null ? 0 : state.burning.size();
            int tracked = state == null ? 0
                    : uniqueTargets(state.heat.targetIds(), state.burning.targetIds());
            int heat = top == null ? 0 : top.stacks();
            double fraction = top == null && burning > 0 ? 1.0
                    : settings.maxHeat() <= 0 ? 0.0 : (double) heat / settings.maxHeat();
            SkillEffectHudEntry.Text badge = top == null
                    ? SkillEffectHudEntry.Text.translated("hud.essence_ascendance.burning_count", Integer.toString(burning))
                    : SkillEffectHudEntry.Text.translated("hud.essence_ascendance.heat", Integer.toString(heat),
                    Integer.toString(settings.maxHeat()));
            List<SkillEffectHudEntry.Text> lines = top == null
                    ? List.of(targets(burning))
                    : List.of(SkillEffectHudEntry.Text.literal(top.target().getName().getString()), targets(tracked));
            return SkillEffectHudEntry.skill(id(), top != null || burning > 0, com.mistaboom.essence_ascendance.visual.AscendancePalette.OFFENSE,
                    badge, top == null ? List.of() : lines,
                    top == null ? SkillEffectHudEntry.Meter.none() : SkillEffectHudEntry.Meter.progress(fraction));
        }

        @Override public void targetRemoved(SkillEffectRuntime.Context context, Entity target) {
            KindlingState state = context.existingState(id());
            if (state != null) state.remove(target);
        }

        @Override public List<String> debugLines(SkillEffectRuntime.Context context) {
            KindlingState state = context.existingState(id());
            var settings = context.settings().kindling();
            return List.of("Source-owned Heat targets=" + (state == null ? 0 : state.heat.size())
                            + "; burning conditions=" + (state == null ? 0 : state.burning.size()),
                    "Config: Heat " + settings.heatPerHit() + "/hit to " + settings.maxHeat()
                            + "; expiry=" + settings.heatExpiryTicks() + " ticks; ignition="
                            + settings.ignitionDurationTicks() + " ticks; burn damage/second="
                            + settings.burningDamagePercentPerSecond() + "% of latest confirmed hit; owner primary damage amplification="
                            + settings.burningDamageAmplificationPercent() + "%");
        }
    }

    private static final class Combustion implements SkillEffectHandler {
        @Override public ResourceLocation id() { return SkillIds.COMBUSTION; }

        @Override public void deathObserved(SkillEffectRuntime.Context context, SkillDeathContext death) {
            KindlingState state = context.existingState(SkillIds.KINDLING);
            SkillProcDamageService.ProcContext previous = death.proc();
            boolean burningKill = previous != null
                    && previous.owner() == context.player()
                    && previous.kind() == SkillProcDamageService.DamageKind.BURNING;
            if (previous != null && !burningKill && (previous.owner() != context.player()
                    || previous.kind() != SkillProcDamageService.DamageKind.COMBUSTION)) return;
            if (state == null || !state.burning.consume(death.victim(), context.now())) return;
            var settings = context.settings().combustion();
            PropagationBudget root = previous == null
                    ? new PropagationBudget(settings.maximumGeneration(), settings.rootTargetBudget())
                    : previous.root();
            int generation = previous == null || burningKill && previous.sourceSkill().equals(SkillIds.KINDLING)
                    ? 0 : previous.generation() + 1;
            root.seed(death.victim().getUUID());
            if (!root.canContinue(generation)) return;
            double damage = settings.damage() * Math.pow(settings.generationDamageFalloff(), generation);
            SafeCreatureAreaService.burst(context.player(), death.victim().getBoundingBox().getCenter(),
                    settings.radius(), settings.targetsPerBurst(), (float) damage, id(),
                    SkillProcDamageService.DamageKind.COMBUSTION, root, generation, target -> {
                state.ignite(target, context.now(), settings.seededIgnitionTicks(), damage,
                        context.settings().kindling().burningDamagePercentPerSecond(),
                        new TargetConditionState.Lineage(root, generation, id()));
                SkillProcDamageService.particles(context.player(), target, ParticleTypes.FLAME, 10, 0.35, 0.02);
            });
        }

        @Override public List<String> debugLines(SkillEffectRuntime.Context context) {
            var settings = context.settings().combustion();
            return List.of("Terrain-safe entity burst; distance then UUID ordering; secondary kills may continue only their bounded root.",
                    "Config: damage=" + settings.damage() + "; radius=" + settings.radius()
                            + "; targets/burst=" + settings.targetsPerBurst() + "; max generation="
                            + settings.maximumGeneration() + "; root budget=" + settings.rootTargetBudget()
                            + "; falloff=" + settings.generationDamageFalloff() + "; seeded burn="
                            + settings.seededIgnitionTicks() + " ticks");
        }
    }

    private static final class Frostbite implements SkillEffectHudHandler {
        @Override public ResourceLocation id() { return SkillIds.FROSTBITE; }

        @Override public void successfulAttack(SkillEffectRuntime.Context context, AttackResultContext result) {
            if (!result.primary() || !result.target().isAlive() || result.target().isRemoved()) return;
            FrostbiteState state = context.state(id(), () -> new FrostbiteState(context.player().getUUID()));
            var settings = context.settings().frostbite();
            if (state.frozen.active(result.target(), context.now())) {
                state.frozen.apply(result.target(), context.now(), settings.freezeDurationTicks());
                state.slow(result.target(), settings.frozenSlow());
                return;
            }
            int chill = state.chill.add(result.target(), context.now(), settings.maxChill(),
                    settings.chillPerHit(), settings.chillExpiryTicks());
            if (settings.maxChill() > 0 && chill >= settings.maxChill()) {
                state.chill.remove(result.target());
                state.frozen.apply(result.target(), context.now(), settings.freezeDurationTicks());
                state.slow(result.target(), settings.frozenSlow());
            } else {
                state.slow(result.target(), Math.min(settings.maximumProgressiveSlow(),
                        chill * settings.slowPerStack()));
            }
        }

        @Override public void reconcile(SkillEffectRuntime.Context context) {
            FrostbiteState state = context.existingState(id());
            if (state == null) return;
            var settings = context.settings().frostbite();
            state.chill.reconcile(context.player(), context.now(), settings.maxChill());
            state.frozen.reconcile(context.player(), context.now());
            for (SourceOwnedBuildupState.Snapshot snapshot : state.chill.snapshots(context.now())) {
                state.slow(snapshot.target(), Math.min(settings.maximumProgressiveSlow(),
                        snapshot.stacks() * settings.slowPerStack()));
            }
            if (state.empty()) context.discardState(id());
        }

        @Override public SkillEffectHudEntry hudEntry(SkillEffectRuntime.Context context) {
            FrostbiteState state = context.existingState(id());
            var settings = context.settings().frostbite();
            SourceOwnedBuildupState.Snapshot top = state == null ? null
                    : state.chill.mostDeveloped(context.now(), settings.maxChill());
            int frozen = state == null ? 0 : state.frozen.size();
            int tracked = state == null ? 0
                    : uniqueTargets(state.chill.targetIds(), state.frozen.targetIds());
            int chill = top == null ? 0 : top.stacks();
            double fraction = top == null && frozen > 0 ? 1.0
                    : settings.maxChill() <= 0 ? 0.0 : (double) chill / settings.maxChill();
            SkillEffectHudEntry.Text badge = top == null
                    ? SkillEffectHudEntry.Text.translated("hud.essence_ascendance.frozen_count", Integer.toString(frozen))
                    : SkillEffectHudEntry.Text.translated("hud.essence_ascendance.chill", Integer.toString(chill),
                    Integer.toString(settings.maxChill()));
            List<SkillEffectHudEntry.Text> lines = top == null
                    ? List.of(targets(frozen))
                    : List.of(SkillEffectHudEntry.Text.literal(top.target().getName().getString()), targets(tracked));
            return SkillEffectHudEntry.skill(id(), top != null || frozen > 0, com.mistaboom.essence_ascendance.visual.AscendancePalette.OFFENSE,
                    badge, top == null ? List.of() : lines,
                    top == null ? SkillEffectHudEntry.Meter.none() : SkillEffectHudEntry.Meter.progress(fraction));
        }

        @Override public void targetRemoved(SkillEffectRuntime.Context context, Entity target) {
            FrostbiteState state = context.existingState(id());
            if (state != null) state.remove(target);
        }

        @Override public List<String> debugLines(SkillEffectRuntime.Context context) {
            FrostbiteState state = context.existingState(id());
            var settings = context.settings().frostbite();
            return List.of("Source-owned Chill targets=" + (state == null ? 0 : state.chill.size())
                            + "; frozen conditions=" + (state == null ? 0 : state.frozen.size()),
                    "Config: Chill " + settings.chillPerHit() + "/hit to " + settings.maxChill()
                            + "; expiry=" + settings.chillExpiryTicks() + " ticks; slow/stack="
                            + settings.slowPerStack() + "; progressive cap=" + settings.maximumProgressiveSlow()
                            + "; freeze=" + settings.freezeDurationTicks() + " ticks at " + settings.frozenSlow());
        }
    }

    private static final class Shatter implements SkillEffectHandler {
        @Override public ResourceLocation id() { return SkillIds.SHATTER; }

        @Override public void kill(SkillEffectRuntime.Context context, LivingEntity target) {
            FrostbiteState state = context.existingState(SkillIds.FROSTBITE);
            if (state == null || !state.frozen.consume(target, context.now())) return;
            var settings = context.settings().shatter();
            PropagationBudget root = new PropagationBudget(0, settings.maximumTargets());
            root.seed(target.getUUID());
            for (LivingEntity candidate : SkillTargetingService.nearby(context.player(), target,
                    settings.radius(), root.visitedIds(), settings.maximumTargets())) {
                if (!root.tryVisit(candidate.getUUID(), 0)) continue;
                SkillProcDamageService.particles(context.player(), candidate, ParticleTypes.SNOWFLAKE, 8, 0.25, 0.01);
                SkillProcDamageService.hurt(context.player(), candidate, (float) settings.shardDamage(),
                        SkillProcDamageService.DamageKind.ICE_SHARD, id(), root, 0);
            }
        }

        @Override public List<String> debugLines(SkillEffectRuntime.Context context) {
            var settings = context.settings().shatter();
            return List.of("Consumes only this owner's explicit frozen condition on an ordinary attributed kill; shards are secondary.",
                    "Config: shard damage=" + settings.shardDamage() + "; radius=" + settings.radius()
                            + "; maximum targets=" + settings.maximumTargets());
        }
    }

    private static final class StaticCharge implements SkillEffectHudHandler {
        @Override public ResourceLocation id() { return SkillIds.STATIC_CHARGE; }

        @Override public void tick(SkillEffectRuntime.Context context) {
            StaticChargeState state = context.state(id(), StaticChargeState::new);
            var settings = context.settings().staticCharge();
            com.mistaboom.essence_ascendance.movement.PlayerMotionTracker.track(context.player(), context.settings().posture().movement());
            var motion = com.mistaboom.essence_ascendance.movement.PlayerMotionTracker.sample(context.player());
            double vertical = context.player().getDeltaMovement().y;
            boolean onGround = context.player().onGround();
            if (state.dischargeVisible(context.now())) {
                // Preserve one authoritative empty snapshot after release. Updating the
                // movement baseline prevents a synthetic jump grant when charging resumes.
                state.initialized = true;
                state.previousOnGround = onGround;
                return;
            }
            double gain = 0.0;
            boolean validMotion = motion.qualifiedSpatialMovement(context.settings().posture().movement().minimumDisplacement());
            if (validMotion && motion.intentional() && state.initialized && state.previousOnGround && !onGround && vertical > 0.10) {
                gain += settings.jumpCharge();
            }
            if (validMotion) {
                if (motion.intentional() && context.player().isFallFlying()) gain += settings.glidePerTick();
                else if (motion.intentional() && context.player().getAbilities().flying) gain += settings.flightPerTick();
                else if (!onGround && vertical < -0.08) gain += settings.fallPerTick();
                else if (motion.intentional() && motion.moving() && context.player().isSprinting()) gain += settings.sprintPerTick();
            }
            state.initialized = true;
            state.previousOnGround = onGround;
            if (gain > 0.0) state.add(gain, settings.maximumCharge(), context.now());
            else state.decay(context.now(), settings.maximumCharge(), settings.idleGraceTicks(), settings.decayPerTick());
        }

        @Override public double flatPrimaryDamageBonus(SkillEffectRuntime.Context context, LivingEntity target,
                                                       DamageSource source, AttackCategory primaryCategory) {
            StaticChargeState state = context.existingState(id());
            return primaryCategory != null && com.mistaboom.essence_ascendance.projectile.ProjectileRuntime.canDischarge(source) && state != null
                    && state.full(context.settings().staticCharge().maximumCharge())
                    ? context.settings().staticCharge().lightningDamage() : 0.0;
        }

        @Override public void successfulAttack(SkillEffectRuntime.Context context, AttackResultContext result) {
            StaticChargeState state = context.existingState(id());
            var settings = context.settings().staticCharge();
            if (state == null || !com.mistaboom.essence_ascendance.projectile.ProjectileRuntime.canDischarge(result.source())
                    || !state.consumeIfFull(settings.maximumCharge(), context.now())) return;
            com.mistaboom.essence_ascendance.projectile.ProjectileRuntime.discharged(result.source());
            SkillProcDamageService.particles(context.player(), result.target(), ParticleTypes.ELECTRIC_SPARK,
                    14, 0.30, 0.03);
            if (context.isEffective(SkillIds.CHAIN_STRIKE)) {
                arc(context, result.target(), settings.lightningDamage());
            }
        }

        private void arc(SkillEffectRuntime.Context context, LivingEntity first, double releaseDamage) {
            var settings = context.settings().chainStrike();
            PropagationBudget root = new PropagationBudget(settings.maximumJumps(), settings.maximumJumps());
            root.seed(first.getUUID());
            Entity origin = first;
            for (int jump = 1; jump <= settings.maximumJumps(); jump++) {
                List<LivingEntity> candidates = SkillTargetingService.nearby(context.player(), origin,
                        settings.radius(), root.visitedIds(), 1);
                if (candidates.isEmpty()) break;
                LivingEntity target = candidates.getFirst();
                if (!root.tryVisit(target.getUUID(), jump)) break;
                double damage = releaseDamage * Math.pow(settings.damageFalloff(), jump);
                SkillProcDamageService.particles(context.player(), target, ParticleTypes.ELECTRIC_SPARK,
                        10, 0.25, 0.03);
                SkillProcDamageService.hurt(context.player(), target, (float) damage,
                        SkillProcDamageService.DamageKind.LIGHTNING_ARC, SkillIds.CHAIN_STRIKE, root, jump);
                origin = target;
            }
        }

        @Override public SkillEffectHudEntry hudEntry(SkillEffectRuntime.Context context) {
            StaticChargeState state = context.existingState(id());
            double maximum = context.settings().staticCharge().maximumCharge();
            double charge = state == null ? 0.0 : state.charge;
            double fraction = maximum <= 0.0 ? 0.0 : Math.min(1.0, charge / maximum);
            SkillEffectHudEntry.Text badge = state != null && state.full(maximum)
                    ? SkillEffectHudEntry.Text.translated("hud.essence_ascendance.full")
                    : SkillEffectHudEntry.Text.literal(decimal(charge) + "/" + decimal(maximum));
            boolean active = charge > 0.0001
                    || (state != null && state.dischargeVisible(context.now()));
            if (charge <= 0.0001 && active)
                return SkillEffectHudEntry.skill(id(), true, 0,
                        SkillEffectHudEntry.Text.translated("hud.essence_ascendance.discharged"),
                        List.of(), SkillEffectHudEntry.Meter.none());
            return SkillEffectHudCards.progress(id(), active, com.mistaboom.essence_ascendance.visual.AscendancePalette.OFFENSE,
                    badge,
                    state != null && state.full(maximum)
                            ? List.of(SkillEffectHudEntry.Text.translated("hud.essence_ascendance.next_confirmed_attack")) : List.of(),
                    fraction);
        }

        @Override public List<String> debugLines(SkillEffectRuntime.Context context) {
            StaticChargeState state = context.existingState(id());
            var settings = context.settings().staticCharge();
            return List.of("Charge=" + decimal(state == null ? 0.0 : state.charge) + "/" + settings.maximumCharge()
                            + "; full charge is preserved and only a confirmed primary melee/ranged/Caster hit consumes it.",
                    "Config: sprint/tick=" + settings.sprintPerTick() + "; jump/takeoff=" + settings.jumpCharge()
                            + "; fall/tick=" + settings.fallPerTick() + "; fly/tick=" + settings.flightPerTick()
                            + "; glide/tick=" + settings.glidePerTick() + "; idle grace="
                            + settings.idleGraceTicks() + "; decay/tick=" + settings.decayPerTick()
                            + "; lightning bonus=" + settings.lightningDamage());
        }
    }

    private static final class ChainStrike implements SkillEffectHandler {
        @Override public ResourceLocation id() { return SkillIds.CHAIN_STRIKE; }

        @Override public List<String> debugLines(SkillEffectRuntime.Context context) {
            var settings = context.settings().chainStrike();
            return List.of("Augments only a consumed Static Charge release; nearest valid unvisited target wins each jump.",
                    "Config: jumps=" + settings.maximumJumps() + "; radius=" + settings.radius()
                            + "; per-jump damage falloff=" + settings.damageFalloff());
        }
    }

    private static final class KindlingState implements SkillEffectState {
        final SourceOwnedBuildupState heat;
        final TargetConditionState burning;

        KindlingState(UUID owner) {
            heat = new SourceOwnedBuildupState(owner);
            burning = new TargetConditionState(owner);
        }

        void ignite(LivingEntity target, long now, int durationTicks,
                    double triggeringDamage, double damagePercentPerSecond) {
            ignite(target, now, durationTicks, triggeringDamage, damagePercentPerSecond, null);
        }
        void ignite(LivingEntity target, long now, int durationTicks,
                    double triggeringDamage, double damagePercentPerSecond, TargetConditionState.Lineage lineage) {
            double pulseDamage = SkillEffectMath.clamp(
                    triggeringDamage * damagePercentPerSecond / 100.0, 0.0, Float.MAX_VALUE);
            burning.apply(target, now, durationTicks, pulseDamage, Kindling.BURN_PULSE_TICKS, lineage);
            target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(), 2));
        }

        boolean empty() { return heat.size() == 0 && burning.size() == 0; }
        void remove(Entity target) { heat.remove(target); burning.remove(target); }
        @Override public void clear() { heat.clear(); burning.clear(); }
    }

    private static final class FrostbiteState implements SkillEffectState {
        final ResourceLocation slowId;
        final SourceOwnedBuildupState chill;
        final TargetConditionState frozen;

        FrostbiteState(UUID owner) {
            slowId = id("skill/frostbite/movement/" + owner);
            chill = new SourceOwnedBuildupState(owner, this::clearSlow);
            frozen = new TargetConditionState(owner, this::clearSlow);
        }

        void slow(LivingEntity target, double amount) {
            SkillEffectAttributes.apply(target, Attributes.MOVEMENT_SPEED, slowId,
                    -Math.min(0.99, Math.max(0.0, amount)), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        }

        void clearSlow(LivingEntity target) {
            SkillEffectAttributes.apply(target, Attributes.MOVEMENT_SPEED, slowId,
                    0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        }

        boolean empty() { return chill.size() == 0 && frozen.size() == 0; }
        void remove(Entity target) { chill.remove(target); frozen.remove(target); }
        @Override public void clear() { chill.clear(); frozen.clear(); }
    }

    private static final class StaticChargeState implements SkillEffectState {
        double charge;
        long lastGainTick;
        long dischargeVisibleThrough = Long.MIN_VALUE;
        boolean initialized;
        boolean previousOnGround;

        void add(double amount, double maximum, long now) {
            if (!Double.isFinite(amount) || amount <= 0.0 || maximum <= 0.0) return;
            charge = Math.min(maximum, charge + amount);
            lastGainTick = now;
        }

        void decay(long now, double maximum, int graceTicks, double amount) {
            if (full(maximum) || charge <= 0.0 || now - lastGainTick <= graceTicks) return;
            charge = Math.max(0.0, charge - Math.max(0.0, amount));
        }

        boolean full(double maximum) {
            return maximum > 0.0 && charge + 0.0000001 >= maximum;
        }

        boolean consumeIfFull(double maximum, long now) {
            if (!full(maximum)) return false;
            charge = 0.0;
            dischargeVisibleThrough = now >= Long.MAX_VALUE - 1L ? Long.MAX_VALUE : now + 1L;
            return true;
        }

        boolean dischargeVisible(long now) {
            return now <= dischargeVisibleThrough;
        }

        @Override public void clear() {
            charge = 0.0;
            initialized = false;
            previousOnGround = false;
            lastGainTick = 0L;
            dischargeVisibleThrough = Long.MIN_VALUE;
        }
    }
}
