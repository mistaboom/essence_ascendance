package com.mistaboom.essence_ascendance.guard;

import com.mistaboom.essence_ascendance.equipment.EquipmentShieldService;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.effect.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import java.util.List;

/** Three catalog bindings share the existing runtime/effective-choice lifecycle. */
public final class GuardReflectionEffects {
    private GuardReflectionEffects() { }
    public static List<SkillEffectHandler> handlers() {
        return List.of(new Effect(SkillIds.REFLEXIVE_WARD), new AmplifierEffect(), new Effect(SkillIds.CROWD_REPRISAL));
    }
    private static final class Amplifier implements SkillEffectState {
        final GuardAmplifierState state = new GuardAmplifierState();
        ItemStack shield;
        net.minecraft.world.InteractionHand hand;
        Object level;
        @Override public void clear() { state.clear(); shield = null; level = null; }
    }
    public static void onBlock(ServerPlayer player, long event, double prevented, boolean perfect) {
        var context = SkillEffectRuntime.context(player);
        if (!context.isEffective(SkillIds.GUARD_AMPLIFIER) || !EquipmentShieldService.isUsingShield(player)) return;
        Amplifier active = context.state(SkillIds.GUARD_AMPLIFIER, Amplifier::new);
        active.shield = player.getUseItem(); active.hand = player.getUsedItemHand(); active.level = player.level();
        active.state.block(event, context.now(), prevented, perfect, context.settings().guard().amplifier());
    }
    public static double multiplier(SkillEffectRuntime.Context context) {
        Amplifier active = context.existingState(SkillIds.GUARD_AMPLIFIER);
        return !context.isEffective(SkillIds.GUARD_AMPLIFIER) || active == null ? 1 : active.state.multiplier(context.now());
    }
    private static class Effect implements SkillEffectHandler {
        final ResourceLocation id;
        Effect(ResourceLocation id) { this.id = id; }
        @Override public ResourceLocation id() { return id; }
        @Override public void reconcile(SkillEffectRuntime.Context context) {
            if (!id.equals(SkillIds.GUARD_AMPLIFIER)) return;
            Amplifier active = context.existingState(id);
            if (active != null && (active.level != context.player().level()
                    || active.hand == null || active.shield != context.player().getItemInHand(active.hand)
                    || !EquipmentShieldService.canGuard(context.player(), active.shield)
                    || active.state.multiplier(context.now()) <= 1)) context.discardState(id);
        }
        @Override public void deactivate(SkillEffectRuntime.Context context) {
            context.discardState(id); GuardLifecycle.invalidateTiming(context.player());
        }
        @Override public List<String> debugLines(SkillEffectRuntime.Context context) {
            Amplifier active = context.existingState(SkillIds.GUARD_AMPLIFIER);
            return List.of("Guard equipment policy: functional held Ascendance Shield for block rewards; native reflection remains equipment-based.",
                    "Amplifier: multiplier=" + multiplier(context) + "; maximum=" + context.settings().guard().amplifier().maximumMultiplier()
                            + "; expiry=" + (active == null ? 0 : active.state.expiresAt()) + "; source=" + (active == null ? 0 : active.state.lastEvent())
                            + "; positive blocks add/refresh; perfect=max; earning block uses growth.");
        }
    }
    private static final class AmplifierEffect extends Effect implements SkillEffectHudHandler {
        AmplifierEffect() { super(SkillIds.GUARD_AMPLIFIER); }
        @Override public SkillEffectHudEntry hudEntry(SkillEffectRuntime.Context context) {
            Amplifier state = context.existingState(id);
            double multiplier = state == null ? 1 : state.state.multiplier(context.now());
            double maximum = context.settings().guard().amplifier().maximumMultiplier();
            double fraction = maximum <= 1 ? 0 : SkillEffectMath.clamp((multiplier - 1) / (maximum - 1), 0, 1);
            return SkillEffectHudCards.timed(id, multiplier > 1, com.mistaboom.essence_ascendance.visual.AscendancePalette.DEFENSE,
                    SkillEffectHudEntry.Text.translated("hud.essence_ascendance.percent", Long.toString(Math.round(fraction * 100))),
                    List.of(SkillEffectHudEntry.Text.translated("hud.essence_ascendance.guard.amplifier", SkillEffectHudCards.decimal(multiplier))),
                    "hud.essence_ascendance.guard.remaining", state == null ? 0 : state.state.expiresAt());
        }
    }
}
