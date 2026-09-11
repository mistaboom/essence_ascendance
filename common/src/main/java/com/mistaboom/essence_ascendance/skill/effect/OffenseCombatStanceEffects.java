package com.mistaboom.essence_ascendance.skill.effect;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudEntry.Text;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudEntry.Meter;
import java.util.Locale;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.UUID;

/** The only four gameplay implementations in this batch; no catalog metadata is duplicated. */
final class OffenseCombatStanceEffects {
    private static final ResourceLocation FRENZY_SPEED = id("skill/frenzy/attack_speed");
    private static final ResourceLocation RUSH_SPEED = id("skill/death_rush/attack_speed");

    private OffenseCombatStanceEffects() { }

    static List<SkillEffectHandler> handlers() {
        return List.of(new Frenzy(), new ArmorCrack(), new Desperation(), new DeathRush());
    }

    static double rushSpeed(SkillEffectRuntime.Context context, double percentPerStack) {
        if (!context.isEffective(SkillIds.DEATH_RUSH) || !context.isEffective(SkillIds.DESPERATION)) return 1.0;
        TimedStackState state = context.existingState(SkillIds.DEATH_RUSH);
        return SkillEffectMath.stackMultiplier(state == null ? 0 : state.count(),
                context.settings().deathRush().maxStacks(), percentPerStack);
    }

    static double frenzySpeed(SkillEffectRuntime.Context context, double percentPerStack) {
        if (!context.isEffective(SkillIds.FRENZY)) return 1.0;
        TimedStackState state = context.existingState(SkillIds.FRENZY);
        return SkillEffectMath.stackMultiplier(state == null ? 0 : state.count(),
                context.settings().frenzy().maxStacks(), percentPerStack);
    }

    private static String decimal(double value) { return String.format(Locale.ROOT, "%.2f", value); }

    private static Text stackBadge(int count, int maximum) {
        return Text.translated("hud.essence_ascendance.stacks", Integer.toString(count), Integer.toString(maximum));
    }

    private static double healthFraction(SkillEffectRuntime.Context context) {
        return SkillEffectMath.healthFraction(context.player().getHealth(), context.player().getMaxHealth());
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(EssenceAscendance.MOD_ID, path);
    }

    private static void attackSpeed(SkillEffectRuntime.Context context, ResourceLocation id, double multiplier) {
        SkillEffectAttributes.apply(context.player(), Attributes.ATTACK_SPEED, id,
                multiplier - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    private static void clearFrenzy(SkillEffectRuntime.Context context) {
        context.discardState(SkillIds.FRENZY);
        attackSpeed(context, FRENZY_SPEED, 1.0);
        context.discardState(SkillIds.ARMOR_CRACK);
    }

    private static final class Frenzy implements SkillEffectHandler {
        @Override public ResourceLocation id() { return SkillIds.FRENZY; }

        @Override public double bowDrawSpeedMultiplier(SkillEffectRuntime.Context context) {
            return frenzySpeed(context, context.settings().frenzy().attackSpeedBonusPercentPerStack());
        }

        @Override public double casterSpeedMultiplier(SkillEffectRuntime.Context context) {
            return bowDrawSpeedMultiplier(context);
        }

        @Override public List<SkillEffectHudEntry> hudEntries(SkillEffectRuntime.Context context) {
            TimedStackState state = context.existingState(id());
            int count = state == null ? 0 : state.count();
            var settings = context.settings().frenzy();
            return List.of(SkillEffectHudEntry.skill(id(), count > 0, 0xFFE87929,
                    stackBadge(count, settings.maxStacks()),
                    List.of(Text.translated("hud.essence_ascendance.damage_speed",
                            decimal(SkillEffectMath.stackMultiplier(count, settings.maxStacks(), settings.damageBonusPercentPerStack())),
                            decimal(frenzySpeed(context, settings.attackSpeedBonusPercentPerStack())))),
                    Meter.timer("hud.essence_ascendance.chain", state == null ? 0L : state.nextExpiry())));
        }

        @Override public void reconcile(SkillEffectRuntime.Context context) {
            TimedStackState state = context.existingState(id());
            if (state != null) state.reconcile(context.now(), context.settings().frenzy().maxStacks());
            if (state == null || state.count() <= 0) {
                clearFrenzy(context);
                return;
            }
            var settings = context.settings().frenzy();
            attackSpeed(context, FRENZY_SPEED, SkillEffectMath.stackMultiplier(state.count(),
                    settings.maxStacks(), settings.attackSpeedBonusPercentPerStack()));
        }

        @Override public void primaryHit(SkillEffectRuntime.Context context, LivingEntity target) {
            TimedStackState state = context.state(id(), TimedStackState::shared);
            var settings = context.settings().frenzy();
            state.grant(context.now(), settings.maxStacks(), settings.chainTimeoutTicks(), true);
            reconcile(context);
        }

        @Override public double damageMultiplier(SkillEffectRuntime.Context context, LivingEntity target,
                                                 DamageSource source, boolean primaryMelee) {
            // Stacks still come only from accepted primary melee hits. Once
            // earned, the damage bonus applies to melee/tools, bow shots, and
            // the Ascendance Caster's first-party damage route.
            if (primaryMelee && !context.matchesPrimaryTarget(target)) return 1.0;
            TimedStackState state = context.existingState(id());
            var settings = context.settings().frenzy();
            return SkillEffectMath.stackMultiplier(state == null ? 0 : state.count(),
                    settings.maxStacks(), settings.damageBonusPercentPerStack());
        }

        @Override public void primaryMiss(SkillEffectRuntime.Context context) { clearFrenzy(context); }
        @Override public void deactivate(SkillEffectRuntime.Context context) { clearFrenzy(context); }

        @Override public List<String> debugLines(SkillEffectRuntime.Context context) {
            TimedStackState state = context.existingState(id());
            var settings = context.settings().frenzy();
            int stacks = state == null ? 0 : state.count();
            return List.of("Frenzy stacks=" + stacks + "/" + settings.maxStacks()
                            + "; timeout remaining=" + (state == null ? 0 : SkillEffectMath.remaining(state.nextExpiry(), context.now())) + " ticks",
                    "All-weapon damage multiplier=" + SkillEffectMath.stackMultiplier(stacks, settings.maxStacks(), settings.damageBonusPercentPerStack())
                            + "; melee/bow/Caster speed multiplier=" + SkillEffectMath.stackMultiplier(stacks, settings.maxStacks(), settings.attackSpeedBonusPercentPerStack()),
                    "Config: timeout=" + settings.chainTimeoutTicks() + " ticks; damage/stack="
                            + settings.damageBonusPercentPerStack() + "%; attack speed/stack=" + settings.attackSpeedBonusPercentPerStack() + "%");
        }
    }

    private static final class ArmorCrack implements SkillEffectHandler {
        @Override public ResourceLocation id() { return SkillIds.ARMOR_CRACK; }

        @Override public List<SkillEffectHudEntry> hudEntries(SkillEffectRuntime.Context context) {
            CrackState state = context.existingState(id());
            int count = state == null ? 0 : state.stacks.count();
            var settings = context.settings().armorCrack();
            LivingEntity target = state == null ? null : state.target.get();
            Text detail;
            if (target == null) detail = Text.translated("hud.essence_ascendance.no_target");
            else {
                String name = target.getName().getString();
                name = name.substring(0, Math.min(96, name.length()));
                detail = Text.translated("hud.essence_ascendance.armor_reduction", name,
                        decimal(count * settings.armorReductionPerStack()));
            }
            return List.of(SkillEffectHudEntry.skill(id(), count > 0, 0xFFF2C94C,
                    stackBadge(count, settings.maxStacks()), List.of(detail),
                    Meter.timer("hud.essence_ascendance.exposed", state == null ? 0L : state.stacks.nextExpiry())));
        }

        @Override public void reconcile(SkillEffectRuntime.Context context) {
            CrackState state = context.existingState(id());
            if (state == null) return;
            state.stacks.reconcile(context.now(), context.settings().armorCrack().maxStacks());
            LivingEntity target = state.target.get();
            TimedStackState frenzy = context.existingState(SkillIds.FRENZY);
            if (!context.isEffective(SkillIds.FRENZY) || frenzy == null || frenzy.count() <= 0 || state.stacks.count() <= 0
                    || target == null || !target.isAlive() || target.isRemoved()
                    || target.level() != context.player().level()
                    || context.player().serverLevel().getEntity(target.getUUID()) != target) {
                context.discardState(id());
                return;
            }
            var settings = context.settings().armorCrack();
            SkillEffectAttributes.reduction(target, Attributes.ARMOR, state.armorId,
                    state.stacks.count() * settings.armorReductionPerStack());
            SkillEffectAttributes.reduction(target, Attributes.ARMOR_TOUGHNESS, state.toughnessId,
                    state.stacks.count() * settings.toughnessReductionPerStack());
        }

        @Override public void primaryHit(SkillEffectRuntime.Context context, LivingEntity target) {
            CrackState state = context.existingState(id());
            // A successful target switch clears the previous victim even when
            // the new victim dies on that first hit and cannot retain a debuff.
            if (state != null && state.target.get() != target) {
                context.discardState(id());
                state = null;
            }
            if (!context.isEffective(SkillIds.FRENZY) || !target.isAlive() || target.isRemoved()) return;
            TimedStackState frenzy = context.existingState(SkillIds.FRENZY);
            if (frenzy == null || frenzy.count() <= 0) return;
            if (state == null) {
                state = context.state(id(), () -> new CrackState(context.player().getUUID(), target));
            }
            var settings = context.settings().armorCrack();
            state.stacks.grant(context.now(), settings.maxStacks(), settings.stackTimeoutTicks(), true);
            reconcile(context);
        }

        @Override public void primaryMiss(SkillEffectRuntime.Context context) { context.discardState(id()); }

        @Override public void targetRemoved(SkillEffectRuntime.Context context, Entity target) {
            CrackState state = context.existingState(id());
            if (state != null && state.target.get() == target) context.discardState(id());
        }

        @Override public List<String> debugLines(SkillEffectRuntime.Context context) {
            CrackState state = context.existingState(id());
            LivingEntity target = state == null ? null : state.target.get();
            var settings = context.settings().armorCrack();
            return List.of("Armor Crack target=" + (target == null ? "none" : target.getName().getString() + " (" + target.getUUID() + ")")
                            + "; stacks=" + (state == null ? 0 : state.stacks.count()) + "/" + settings.maxStacks()
                            + "; timeout remaining=" + (state == null ? 0 : SkillEffectMath.remaining(state.stacks.nextExpiry(), context.now())) + " ticks",
                    "Config: timeout=" + settings.stackTimeoutTicks() + " ticks; armor reduction/stack="
                            + settings.armorReductionPerStack() + "; toughness reduction/stack=" + settings.toughnessReductionPerStack());
        }
    }

    private static final class Desperation implements SkillEffectHandler {
        @Override public ResourceLocation id() { return SkillIds.DESPERATION; }

        @Override public List<SkillEffectHudEntry> hudEntries(SkillEffectRuntime.Context context) {
            double missing = 1.0 - healthFraction(context);
            double multiplier = SkillEffectMath.desperationMultiplier(context.player().getHealth(),
                    context.player().getMaxHealth(), context.settings().desperation().maxDamageBonusPercent());
            return List.of(SkillEffectHudEntry.skill(id(), multiplier > 1.000001, 0xFFEA4E4E,
                    Text.translated("hud.essence_ascendance.percent", Long.toString(Math.round(missing * 100.0))),
                    List.of(Text.translated("hud.essence_ascendance.damage", decimal(multiplier))),
                    Meter.progress(missing)));
        }

        @Override public double damageMultiplier(SkillEffectRuntime.Context context, LivingEntity target,
                                                 DamageSource source, boolean primaryMelee) {
            return SkillEffectMath.desperationMultiplier(context.player().getHealth(), context.player().getMaxHealth(),
                    context.settings().desperation().maxDamageBonusPercent());
        }

        @Override public List<String> debugLines(SkillEffectRuntime.Context context) {
            double multiplier = context.isEffective(id())
                    ? SkillEffectMath.desperationMultiplier(context.player().getHealth(), context.player().getMaxHealth(),
                    context.settings().desperation().maxDamageBonusPercent()) : 1.0;
            return List.of("Desperation normal-health fraction=" + healthFraction(context)
                            + "; resolved outgoing damage multiplier=" + multiplier + "; absorption excluded",
                    "Config: maximum missing-health damage bonus=" + context.settings().desperation().maxDamageBonusPercent() + "% (linear)");
        }
    }

    private static final class DeathRush implements SkillEffectHandler {
        @Override public ResourceLocation id() { return SkillIds.DEATH_RUSH; }

        @Override public double bowDrawSpeedMultiplier(SkillEffectRuntime.Context context) {
            return rushSpeed(context, context.settings().deathRush().bowDrawSpeedBonusPercentPerStack());
        }

        @Override public double casterSpeedMultiplier(SkillEffectRuntime.Context context) {
            return rushSpeed(context, context.settings().deathRush().castSpeedBonusPercentPerStack());
        }

        @Override public List<SkillEffectHudEntry> hudEntries(SkillEffectRuntime.Context context) {
            TimedStackState state = context.existingState(id());
            int count = state == null ? 0 : state.count();
            var settings = context.settings().deathRush();
            return List.of(SkillEffectHudEntry.skill(id(), count > 0, 0xFFB17BFF,
                    stackBadge(count, settings.maxStacks()),
                    List.of(Text.translated("hud.essence_ascendance.attack_bow_speed",
                            decimal(rushSpeed(context, settings.attackSpeedBonusPercentPerStack())),
                            decimal(bowDrawSpeedMultiplier(context)))),
                    Meter.timer("hud.essence_ascendance.next_stack", state == null ? 0L : state.nextExpiry())));
        }

        @Override public void reconcile(SkillEffectRuntime.Context context) {
            if (!context.isEffective(SkillIds.DESPERATION)) {
                deactivate(context);
                return;
            }
            TimedStackState state = context.existingState(id());
            if (state != null) {
                state.reconcile(context.now(), context.settings().deathRush().maxStacks());
                if (state.count() == 0) context.discardState(id());
            }
            attackSpeed(context, RUSH_SPEED, rushSpeed(context,
                    context.settings().deathRush().attackSpeedBonusPercentPerStack()));
        }

        @Override public void kill(SkillEffectRuntime.Context context, LivingEntity target) {
            if (!context.isEffective(SkillIds.DESPERATION)) return;
            var settings = context.settings().deathRush();
            double fraction = healthFraction(context);
            if (!SkillEffectMath.rushEligible(fraction, settings.killHealthThreshold())) return;
            TimedStackState state = context.state(id(), TimedStackState::independent);
            state.grant(context.now(), settings.maxStacks(), settings.stackDurationTicks(),
                    SkillEffectMath.rushRefreshes(fraction, settings.killHealthThreshold(), settings.nearDeathHealthThreshold()));
            reconcile(context);
        }

        @Override public void deactivate(SkillEffectRuntime.Context context) {
            context.discardState(id());
            attackSpeed(context, RUSH_SPEED, 1.0);
        }

        @Override public List<String> debugLines(SkillEffectRuntime.Context context) {
            TimedStackState state = context.existingState(id());
            var settings = context.settings().deathRush();
            List<Long> remaining = state == null ? List.of() : state.expiries().stream()
                    .map(expiry -> SkillEffectMath.remaining(expiry, context.now())).sorted().toList();
            return List.of("Death Rush stacks=" + remaining.size() + "/" + settings.maxStacks()
                            + "; next expiry=" + (remaining.isEmpty() ? "none" : remaining.getFirst() + " ticks")
                            + "; per-stack remaining ticks=" + remaining,
                    "Speed multipliers: attack=" + rushSpeed(context, settings.attackSpeedBonusPercentPerStack())
                            + "; bow draw=" + rushSpeed(context, settings.bowDrawSpeedBonusPercentPerStack())
                            + "; Caster=" + rushSpeed(context, settings.castSpeedBonusPercentPerStack()),
                    "Config: kill health below=" + settings.killHealthThreshold() + "; full refresh at/below="
                            + settings.nearDeathHealthThreshold() + "; duration/stack=" + settings.stackDurationTicks() + " ticks",
                    "Config: attack speed/stack=" + settings.attackSpeedBonusPercentPerStack()
                            + "%; bow draw speed/stack=" + settings.bowDrawSpeedBonusPercentPerStack()
                            + "%; Caster speed/stack=" + settings.castSpeedBonusPercentPerStack() + "%");
        }
    }

    private static final class CrackState implements SkillEffectState {
        final WeakReference<LivingEntity> target;
        final ResourceLocation armorId;
        final ResourceLocation toughnessId;
        final TimedStackState stacks = TimedStackState.shared();

        CrackState(UUID attacker, LivingEntity target) {
            this.target = new WeakReference<>(target);
            this.armorId = id("skill/armor_crack/armor/" + attacker);
            this.toughnessId = id("skill/armor_crack/toughness/" + attacker);
        }

        @Override public void clear() {
            LivingEntity entity = target.get();
            if (entity != null) {
                SkillEffectAttributes.apply(entity, Attributes.ARMOR, armorId, 0.0, AttributeModifier.Operation.ADD_VALUE);
                SkillEffectAttributes.apply(entity, Attributes.ARMOR_TOUGHNESS, toughnessId, 0.0, AttributeModifier.Operation.ADD_VALUE);
            }
            target.clear();
            stacks.clear();
        }
    }
}
