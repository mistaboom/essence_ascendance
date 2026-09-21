package com.mistaboom.essence_ascendance.skill.effect;

import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.vitality.VitalityDamageService;
import com.mistaboom.essence_ascendance.visual.AscendancePalette;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.food.FoodConstants;
import java.util.List;
import static com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudEntry.Text;
import static com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudCards.compact;

/** Catalog skills share routing, persistence, timed stacks, native modifiers and the established card templates. */
public final class VitalityDamageEffects {
    private static final Adrenaline ADRENALINE = new Adrenaline();
    private VitalityDamageEffects() { }

    /** Called after the native health write with actual HP loss and the pre-hit current maximum.
     * No original-attack, absorption, prevented-damage, lethal-save or post-Trauma denominator. */
    public static void onHealthDamage(SkillEffectRuntime.Context context, double lost, double maximumBefore) {
        if (!context.isEffective(SkillIds.ADRENALINE) || !Double.isFinite(maximumBefore) || maximumBefore <= 0) return;
        var tuning = context.settings().vitality().damage().adrenaline();
        if (context.state(SkillIds.ADRENALINE, ThresholdBuffState::new).observeAbove(context.now(), lost,
                maximumBefore * tuning.triggerHealthLossFraction(), tuning.durationTicks())) ADRENALINE.reconcile(context);
    }

    private static Text lastHit(SkillEffectRuntime.Context context, ResourceLocation skill) {
        DamageRoutingState state = context.existingState(skill);
        return state != null && state.recorded()
                ? text("last_hit", compact(state.incoming()), compact(state.immediate())) : null;
    }
    public static List<SkillEffectHandler> handlers() {
        return List.of(new HungerWard(), new StaggeredPain(), new DamageCeiling(),
                new Passive(SkillIds.METABOLIC_CONVERSION), new Passive(SkillIds.PAIN_PURGE), ADRENALINE);
    }
    private static Text text(String key, String... args) { return Text.translated("hud.essence_ascendance.vitality." + key, args); }
    private static final class HungerWard implements SkillEffectHudHandler {
        @Override public ResourceLocation id() { return SkillIds.HUNGER_WARD; }
        @Override public SkillEffectHudEntry hudEntry(SkillEffectRuntime.Context context) {
            var food = context.player().getFoodData();
            double points = food.getFoodLevel() + food.getSaturationLevel() + VitalityDamageService.carry(context.player(), SkillIds.HUNGER_WARD);
            var tuning = context.settings().vitality().damage().hungerWard();
            var details = new java.util.ArrayList<Text>();
            details.add(text("reserve", compact(points * tuning.healthPerFoodPoint())));
            Text hit = lastHit(context, id());
            if (hit != null) details.add(hit);
            return SkillEffectHudCards.progress(id(), CombatHudActivity.active(context.player()), AscendancePalette.VITALITY,
                    text("redirect", compact(tuning.damageShare() * 100)), details,
                    points / (FoodConstants.MAX_FOOD * 2.0));
        }
        @Override public List<String> debugLines(SkillEffectRuntime.Context context) {
            return List.of("Redirected share=" + context.settings().vitality().damage().hungerWard().damageShare(),
                    "Post-absorption health per food point=" + context.settings().vitality().damage().hungerWard().healthPerFoodPoint(),
                    "Order=saturation, prepaid fractional hunger, hunger, health. Spent fractional hunger is persisted.");
        }
    }
    private static final class StaggeredPain implements SkillEffectHudHandler {
        @Override public ResourceLocation id() { return SkillIds.STAGGERED_PAIN; }
        @Override public SkillEffectHudEntry hudEntry(SkillEffectRuntime.Context context) {
            var debt = VitalityDamageService.ledger(context.player()).delayed;
            return SkillEffectHudCards.timed(id(), !debt.isEmpty(), AscendancePalette.VITALITY,
                    text("owed", compact(debt.total())), List.of(text("payment", compact(debt.nextPayment() * 20))), "hud.essence_ascendance.vitality.payment_time", context.now() + debt.ticksRemaining());
        }
        @Override public List<String> debugLines(SkillEffectRuntime.Context context) {
            var debt = VitalityDamageService.ledger(context.player()).delayed;
            return List.of("Queued HP=" + debt.total() + "; online payment ticks=" + debt.ticksRemaining(),
                    "New hit duration=" + context.settings().vitality().damage().staggeredPain().paymentTicks()
                            + " ticks; old obligations keep their captured duration through settings/rank changes.");
        }
    }
    private static final class DamageCeiling implements SkillEffectHudHandler {
        @Override public ResourceLocation id() { return SkillIds.DAMAGE_CEILING; }
        @Override public SkillEffectHudEntry hudEntry(SkillEffectRuntime.Context context) {
            var debt = VitalityDamageService.ledger(context.player());
            boolean trauma = debt.traumaFraction > 0;
            DamageRoutingState state = context.existingState(id());
            // Mere combat must not open a template with no routed hit. Environmental hits use
            // this same recorded-outcome window instead of depending on a hostile mob source.
            boolean visible = trauma || (state != null && state.recent(context.now(), CombatHudActivity.WINDOW_TICKS));
            double prevented = state != null && state.recorded() ? state.lastReduction() : 0;
            double lostMaxHealth = Math.max(0, VitalityDamageService.ordinaryMaximumHealth(context.player())
                    - context.player().getMaxHealth());
            // Standard one-detail-row card: name/penalty header, latest prevented DMG, timer footer.
            // With no penalty there is no recovery timer to label as unknown or invent.
            var meter = trauma ? SkillEffectHudEntry.Meter.timer("hud.essence_ascendance.vitality.quiet_time",
                    context.now() + debt.traumaQuietTicks) : SkillEffectHudEntry.Meter.none();
            return SkillEffectHudEntry.skill(id(), visible, AscendancePalette.VITALITY,
                    text("max_hp_lost", compact(lostMaxHealth)), List.of(text("last_prevented", compact(prevented))), meter);
        }
        @Override public List<String> debugLines(SkillEffectRuntime.Context context) {
            var s = context.settings().vitality().damage().damageCeiling();
            var l = VitalityDamageService.ledger(context.player());
            DamageRoutingState routed = context.existingState(id());
            return List.of("Recorded routed hit=" + (routed != null && routed.recorded())
                            + "; incoming HP DMG=" + (routed == null ? 0 : routed.incoming())
                            + "; applied HP DMG=" + (routed == null ? 0 : routed.immediate())
                            + "; prevented=" + (routed == null ? 0 : routed.lastReduction()),
                    "Incoming fraction taken=" + s.damageTakenFraction() + "; max HP lost per prevented HP=" + s.damageTakenFraction(),
                    "Lost maximum fraction=" + l.traumaFraction + "; quiet ticks=" + l.traumaQuietTicks,
                    "Every eligible hit uses the same generated percentage. Capacity loss is not extra damage; no max-HP hit cap or Trauma bank.",
                    "Live maximum=" + context.player().getMaxHealth() + "; before Trauma=" + VitalityDamageService.ordinaryMaximumHealth(context.player())
                            + "; recovery restores capacity without calling heal or increasing current HP.");
        }
    }
    private record Passive(ResourceLocation id) implements SkillEffectHandler {
        @Override public List<String> debugLines(SkillEffectRuntime.Context context) {
            var settings = context.settings().vitality().damage();
            if (id.equals(SkillIds.PAIN_PURGE)) return List.of("Delayed damage recovered per accepted healing point=" + settings.painPurge().queuePerHealing(),
                    "All accepted native healing, including full-HP overflow; queue cancellation never emits a heal event.");
            return List.of("Accepted overflow food points/HP=" + settings.metabolicConversion().foodPointsPerOverflowHealth(),
                    "Full-hunger food health/nutrition=" + settings.metabolicConversion().healthPerNutrition(),
                    "Food-generated healing never converts back to food; native completion/cancellation and committed-state eligibility apply.");
        }
    }
    private static final class Adrenaline implements SkillEffectHudHandler {
        private static final ResourceLocation SPEED = key("adrenaline_speed"), ATTACK = key("adrenaline_attack"), KNOCKBACK = key("adrenaline_knockback");
        @Override public ResourceLocation id() { return SkillIds.ADRENALINE; }
        private boolean active(SkillEffectRuntime.Context context) {
            ThresholdBuffState state = context.existingState(id());
            return state != null && state.active(context.now());
        }
        @Override public void reconcile(SkillEffectRuntime.Context context) {
            double factor = active(context) ? 1 : 0;
            var s = context.settings().vitality().damage().adrenaline();
            SkillEffectAttributes.apply(context.player(), Attributes.MOVEMENT_SPEED, SPEED, s.movementSpeedBonus() * factor, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            SkillEffectAttributes.apply(context.player(), Attributes.ATTACK_SPEED, ATTACK, s.attackSpeedBonus() * factor, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            SkillEffectAttributes.apply(context.player(), Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK, s.knockbackResistance() * factor, AttributeModifier.Operation.ADD_VALUE);
        }
        @Override public void tick(SkillEffectRuntime.Context context) { reconcile(context); }
        @Override public void deactivate(SkillEffectRuntime.Context context) {
            context.discardState(id()); reconcile(context);
        }
        @Override public double bowDrawSpeedMultiplier(SkillEffectRuntime.Context context) {
            return 1 + (active(context) ? context.settings().vitality().damage().adrenaline().attackSpeedBonus() : 0);
        }
        @Override public double casterSpeedMultiplier(SkillEffectRuntime.Context context) { return bowDrawSpeedMultiplier(context); }
        @Override public SkillEffectHudEntry hudEntry(SkillEffectRuntime.Context context) {
            boolean active = active(context);
            ThresholdBuffState state = context.existingState(id());
            var s = context.settings().vitality().damage().adrenaline();
            // Always emit the same card shape. Inactive snapshots let the shared HUD retain the
            // last active buffs for its normal closing grace; combat/Trauma never keep this card open.
            return SkillEffectHudCards.timed(id(), active, AscendancePalette.VITALITY, text("surge"),
                    List.of(text("speed_bonuses", compact(s.movementSpeedBonus() * 100), compact(s.attackSpeedBonus() * 100)),
                            text("knockback", compact(s.knockbackResistance() * 100))),
                    "hud.essence_ascendance.vitality.surge_time", state == null ? 0 : state.expiresAt());
        }
        @Override public List<String> debugLines(SkillEffectRuntime.Context context) {
            var s = context.settings().vitality().damage().adrenaline();
            ThresholdBuffState state = context.existingState(id());
            return List.of("Observed health write=" + (state != null && state.recorded())
                            + "; buff remaining ticks=" + (state == null ? 0 : Math.max(0, state.expiresAt() - context.now())),
                    "Active=" + active(context) + "; actual HP lost=" + (state == null ? 0 : state.measured())
                            + "; required HP loss (strictly more)=" + (state == null ? 0 : state.required()) + "; threshold exceeded=" + (state != null && state.triggered()),
                    "Trigger=actual HP lost > pre-hit current max HP * " + s.triggerHealthLossFraction()
                            + "; ignores absorption, prevented damage and this hit's max-HP penalty.",
                    "Duration=" + s.durationTicks() + "; movement=" + s.movementSpeedBonus() + "; attack=" + s.attackSpeedBonus()
                            + "; knockback resistance=" + s.knockbackResistance(),
                    "Attack speed applies to melee, bow draw and caster recovery; one refreshable timed buff, never stacking.");
        }
        private static ResourceLocation key(String path) { return ResourceLocation.fromNamespaceAndPath("essence_ascendance", path); }
    }
}
