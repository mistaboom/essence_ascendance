package com.mistaboom.essence_ascendance.utility;

import com.mistaboom.essence_ascendance.equipment.MobEffectDurationAccess;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectState;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.alchemy.PotionContents;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Shared server-authoritative potion application service.
 *
 * <p>Drinking enters a narrow item-use frame so unrelated effects applied during the same tick are not
 * mistaken for potion effects. Splash and lingering effects enter through their native potion/cloud
 * dispatch points, after vanilla has already calculated impact falloff. Relay applications are marked
 * secondary so they cannot recursively trigger amplification/relay behavior.</p>
 */
public final class UtilityPotionService {
    private static final ThreadLocal<Frame> ACTIVE = new ThreadLocal<>();
    private static final ThreadLocal<Integer> RELAY_DEPTH = ThreadLocal.withInitial(() -> 0);

    private UtilityPotionService() { }

    public static ItemStack complete(ServerPlayer player, ItemStack source, Supplier<ItemStack> nativeUse) {
        PotionContents contents = source.get(DataComponents.POTION_CONTENTS);
        if (contents == null || source.getUseAnimation() != UseAnim.DRINK) return nativeUse.get();
        Frame previous = ACTIVE.get();
        ACTIVE.set(new Frame(player, contents));
        try {
            return nativeUse.get();
        } finally {
            if (previous == null) ACTIVE.remove();
            else ACTIVE.set(previous);
        }
    }

    @FunctionalInterface
    public interface EffectApplication {
        boolean apply(MobEffectInstance effect, Entity source);
    }

    /** Handles timed effects emitted while a drink-potion frame is active. */
    public static boolean apply(LivingEntity target, MobEffectInstance effect, Entity source, EffectApplication nativeApply) {
        Frame frame = ACTIVE.get();
        if (RELAY_DEPTH.get() > 0 || frame == null || target != frame.player || !frame.claim(effect)) {
            return nativeApply.apply(effect, source);
        }
        return applyPotionEffect(target, effect, source, nativeApply);
    }

    /** Handles timed effects from native splash/lingering potion paths after vanilla impact scaling. */
    public static boolean applyExternalPotion(LivingEntity target, MobEffectInstance effect, Entity source,
                                              EffectApplication nativeApply) {
        if (RELAY_DEPTH.get() > 0) return nativeApply.apply(effect, source);
        return applyPotionEffect(target, effect, source, nativeApply);
    }

    /** Resolves an instantaneous effect emitted while a drink-potion frame is active. */
    public static double consumedInstantPotency(MobEffect effect, LivingEntity target, int amplifier, double potency) {
        Frame frame = ACTIVE.get();
        if (RELAY_DEPTH.get() > 0 || frame == null || target != frame.player || !frame.claim(effect, amplifier)) return potency;
        return instantPotionPotency(effect, target, potency);
    }

    /** Resolves an instantaneous effect coming directly from a splash or lingering potion path. */
    public static double externalInstantPotency(MobEffect effect, LivingEntity target, double potency) {
        if (RELAY_DEPTH.get() > 0) return potency;
        return instantPotionPotency(effect, target, potency);
    }

    private static boolean applyPotionEffect(LivingEntity target, MobEffectInstance effect, Entity source,
                                             EffectApplication nativeApply) {
        if (!(target instanceof ServerPlayer player) || !compatibleTimed(effect)) {
            return nativeApply.apply(effect, source);
        }

        SkillEffectRuntime.Context context = SkillEffectRuntime.context(player);
        int originalDuration = effect.getDuration();
        MobEffectInstance resolved = effect;
        if (context.isEffective(SkillIds.ALCHEMICAL_AMPLIFICATION)) {
            int extended = extendedDuration(originalDuration, context.settings().utility().alchemicalAmplification());
            if (extended != originalDuration) resolved = withDuration(effect, extended);
        }

        boolean applied = nativeApply.apply(resolved, source);
        if (!applied) return false;

        if (context.isEffective(SkillIds.ALCHEMICAL_AMPLIFICATION) && resolved.getDuration() > originalDuration) {
            MobEffectInstance active = player.getEffect(resolved.getEffect());
            int remaining = active == null ? resolved.getDuration() : active.getDuration();
            AmplificationState state = context.state(SkillIds.ALCHEMICAL_AMPLIFICATION, AmplificationState::new);
            state.effectKey = resolved.getEffect().value().getDescriptionId();
            state.originalDuration = originalDuration;
            state.appliedDuration = resolved.getDuration();
            state.expiresAt = context.now() + Math.max(0, remaining);
        }

        if (context.isEffective(SkillIds.POTION_RELAY)) relay(context, resolved);
        return true;
    }

    private static double instantPotionPotency(MobEffect effect, LivingEntity target, double potency) {
        if (!(target instanceof ServerPlayer player) || !Double.isFinite(potency)
                || !effect.isBeneficial() || !effect.isInstantenous()) return potency;
        SkillEffectRuntime.Context context = SkillEffectRuntime.context(player);
        if (!context.isEffective(SkillIds.ALCHEMICAL_AMPLIFICATION)) return potency;
        double bonus = context.settings().utility().alchemicalAmplification().maximumBonusFraction();
        return bonus <= 0 ? potency : potency * (1.0 + bonus);
    }

    public static AmplificationSnapshot amplificationSnapshot(SkillEffectRuntime.Context context) {
        AmplificationState state = context.existingState(SkillIds.ALCHEMICAL_AMPLIFICATION);
        if (state == null || state.expiresAt <= context.now()) return null;
        return new AmplificationSnapshot(state.effectKey, state.originalDuration, state.appliedDuration, state.expiresAt);
    }

    public static RelaySnapshot relaySnapshot(SkillEffectRuntime.Context context) {
        RelayState state = context.existingState(SkillIds.POTION_RELAY);
        if (state == null || state.expiresAt <= context.now()) return null;
        return new RelaySnapshot(state.effectKey, state.targets, state.durationTicks, state.expiresAt);
    }

    private static void relay(SkillEffectRuntime.Context context, MobEffectInstance effect) {
        var tuning = context.settings().utility().potionRelay();
        if (tuning.durationFraction() <= 0 || tuning.radiusBlocks() <= 0 || tuning.maximumTargets() <= 0) return;
        int relayDuration = Math.max(1, (int) Math.floor(effect.getDuration() * tuning.durationFraction()));
        int depth = RELAY_DEPTH.get();
        int accepted = 0;
        RELAY_DEPTH.set(depth + 1);
        try {
            for (LivingEntity ally : AllyTargetingService.nearby(context.player(), tuning.radiusBlocks(), tuning.maximumTargets())) {
                if (ally.addEffect(withDuration(effect, relayDuration), context.player())) accepted++;
            }
        } finally {
            if (depth == 0) RELAY_DEPTH.remove();
            else RELAY_DEPTH.set(depth);
        }
        if (accepted <= 0) return;
        RelayState state = context.state(SkillIds.POTION_RELAY, RelayState::new);
        state.effectKey = effect.getEffect().value().getDescriptionId();
        state.targets = accepted;
        state.durationTicks = relayDuration;
        state.expiresAt = context.now() + relayDuration;
    }

    private static int extendedDuration(int baseDuration,
                                        com.mistaboom.essence_ascendance.config.UtilityBalanceSettings.AlchemicalAmplification tuning) {
        if (baseDuration <= 0 || tuning.maximumBonusFraction() <= 0) return baseDuration;
        double window = Math.max(1, tuning.diminishingWindowTicks());
        double bonus = baseDuration * tuning.maximumBonusFraction() / (1.0 + baseDuration / window);
        long extended = (long) baseDuration + Math.max(0L, (long) Math.ceil(bonus));
        return (int) Math.min(Integer.MAX_VALUE, extended);
    }

    private static MobEffectInstance withDuration(MobEffectInstance source, int duration) {
        MobEffectInstance copy = new MobEffectInstance(source);
        if (!(copy instanceof MobEffectDurationAccess access)) {
            throw new IllegalStateException(
                    "MobEffectInstance duration bridge was not applied. Check the loader vitality mixin configuration.");
        }
        access.essenceAscendance$setDurationTicks(duration);
        if (copy.getDuration() != duration) {
            throw new IllegalStateException("Failed to set copied potion effect duration for "
                    + copy.getDescriptionId() + ": requested " + duration + " ticks but copy reports "
                    + copy.getDuration());
        }
        return copy;
    }

    private static boolean compatibleTimed(MobEffectInstance effect) {
        return effect.getDuration() > 0 && !effect.isInfiniteDuration()
                && effect.getEffect().value().isBeneficial() && !effect.getEffect().value().isInstantenous();
    }

    private record Key(Holder<MobEffect> effect, int amplifier) { }

    private static final class Frame {
        final ServerPlayer player;
        final Map<Key, Integer> remaining = new HashMap<>();
        Frame(ServerPlayer player, PotionContents contents) {
            this.player = player;
            for (MobEffectInstance effect : contents.getAllEffects()) {
                remaining.merge(new Key(effect.getEffect(), effect.getAmplifier()), 1, Integer::sum);
            }
        }
        boolean claim(MobEffectInstance effect) {
            return claim(new Key(effect.getEffect(), effect.getAmplifier()));
        }
        boolean claim(MobEffect effect, int amplifier) {
            Iterator<Map.Entry<Key, Integer>> iterator = remaining.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Key, Integer> entry = iterator.next();
                if (entry.getKey().amplifier() != amplifier || entry.getKey().effect().value() != effect) continue;
                int count = entry.getValue();
                if (count <= 1) iterator.remove();
                else entry.setValue(count - 1);
                return true;
            }
            return false;
        }
        private boolean claim(Key key) {
            Integer count = remaining.get(key);
            if (count == null || count <= 0) return false;
            if (count == 1) remaining.remove(key);
            else remaining.put(key, count - 1);
            return true;
        }
    }

    private static final class AmplificationState implements SkillEffectState {
        String effectKey = "";
        int originalDuration;
        int appliedDuration;
        long expiresAt;
        @Override public void clear() { effectKey = ""; originalDuration = appliedDuration = 0; expiresAt = 0; }
    }

    private static final class RelayState implements SkillEffectState {
        String effectKey = "";
        int targets;
        int durationTicks;
        long expiresAt;
        @Override public void clear() { effectKey = ""; targets = durationTicks = 0; expiresAt = 0; }
    }

    public record AmplificationSnapshot(String effectKey, int originalDuration, int appliedDuration, long expiresAt) { }
    public record RelaySnapshot(String effectKey, int targets, int durationTicks, long expiresAt) { }
}
