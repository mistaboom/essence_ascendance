package com.mistaboom.essence_ascendance.gathering;

import com.mistaboom.essence_ascendance.config.GatheringBalanceSettings;
import com.mistaboom.essence_ascendance.equipment.EquipmentGatheringService;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.FishingHook;

/** Native-hook adapter for Fishing Instinct. No replacement fishing/loot simulation is maintained here. */
public final class GatheringFishingService {
    private GatheringFishingService() { }

    public enum HudPhase { NONE, CAST, WAITING, APPROACHING, BITE }

    /** Server-authoritative glance state for the shared skill HUD; it never changes fishing gameplay. */
    public record HudState(HudPhase phase, int remainingTicks) {
        public HudState {
            if (phase == null) throw new IllegalArgumentException("Fishing HUD phase cannot be null");
            remainingTicks = Math.max(0, remainingTicks);
        }
        public boolean active() { return phase != HudPhase.NONE; }
    }

    public static HudState hudState(ServerPlayer player) {
        FishingHook fishing = player.fishing;
        if (fishing == null || !(fishing instanceof GatheringFishingHookAccess hook)) {
            return new HudState(HudPhase.NONE, 0);
        }
        int nibble = hook.essenceAscendance$nibble();
        if (nibble > 0) return new HudState(HudPhase.BITE, nibble);
        int approaching = hook.essenceAscendance$timeUntilHooked();
        if (approaching > 0) return new HudState(HudPhase.APPROACHING, approaching);
        int waiting = hook.essenceAscendance$timeUntilLured();
        if (waiting > 0) return new HudState(HudPhase.WAITING, waiting);
        return new HudState(HudPhase.CAST, 0);
    }

    /** Captures native countdown state and, while a fish is biting, stretches the native reel window. */
    public static void beforeFishingLogic(ServerPlayer player, GatheringFishingHookAccess hook) {
        GatheringBalanceSettings.FishingInstinct tuning = tuning(player);
        if (tuning == null) {
            resetCarries(hook);
            return;
        }
        hook.essenceAscendance$setLureBefore(hook.essenceAscendance$timeUntilLured());
        hook.essenceAscendance$setHookBefore(hook.essenceAscendance$timeUntilHooked());

        int nibble = hook.essenceAscendance$nibble();
        if (nibble <= 0) {
            hook.essenceAscendance$setReelCarry(0);
            return;
        }

        // Vanilla removes one live-window tick per fishing update. Retaining (1 - 1/M) stretches it by M.
        double retainedPerTick = 1.0D - 1.0D / tuning.reelWindowMultiplier();
        double carry = hook.essenceAscendance$reelCarry() + retainedPerTick;
        int retained = (int) Math.floor(carry);
        hook.essenceAscendance$setReelCarry(carry - retained);
        if (retained > 0) hook.essenceAscendance$setNibble(nibble + retained);
    }

    /** Multiplies whatever progress vanilla actually made this tick, preserving weather/sky timing behavior. */
    public static void afterFishingLogic(ServerPlayer player, GatheringFishingHookAccess hook) {
        GatheringBalanceSettings.FishingInstinct tuning = tuning(player);
        if (tuning == null) {
            resetCarries(hook);
            return;
        }
        hook.essenceAscendance$setLureCarry(accelerateNativeProgress(
                hook.essenceAscendance$lureBefore(), hook.essenceAscendance$timeUntilLured(),
                hook.essenceAscendance$lureCarry(), tuning.biteSpeedMultiplier(),
                hook::essenceAscendance$setTimeUntilLured));
        hook.essenceAscendance$setHookCarry(accelerateNativeProgress(
                hook.essenceAscendance$hookBefore(), hook.essenceAscendance$timeUntilHooked(),
                hook.essenceAscendance$hookCarry(), tuning.biteSpeedMultiplier(),
                hook::essenceAscendance$setTimeUntilHooked));
    }

    /** Extra native Luck level applied only while the hook executes its ordinary retrieval path. */
    public static int virtualLuck(ServerPlayer player) {
        GatheringBalanceSettings.FishingInstinct tuning = tuning(player);
        if (tuning == null) return 0;
        return EquipmentGatheringService.stochasticVirtualEnchantmentLevel(
                tuning.virtualLuckLevels(), player.getRandom());
    }

    private static GatheringBalanceSettings.FishingInstinct tuning(ServerPlayer player) {
        SkillEffectRuntime.Context context = SkillEffectRuntime.context(player);
        return context.isEffective(SkillIds.FISHING_INSTINCT)
                ? context.settings().gathering().fishingInstinct() : null;
    }

    private static double accelerateNativeProgress(int before, int after, double carry, double multiplier,
                                                   java.util.function.IntConsumer setter) {
        if (before <= 0 || after <= 0 || after >= before || multiplier <= 1) {
            return after <= 0 || after > before ? 0 : carry;
        }
        int nativeAdvance = before - after;
        double accrued = carry + nativeAdvance * (multiplier - 1.0D);
        int extraTicks = (int) Math.floor(accrued);
        double remainder = accrued - extraTicks;
        if (extraTicks <= 0) return remainder;
        int accelerated = Math.max(0, after - extraTicks);
        setter.accept(accelerated);
        return accelerated <= 0 ? 0 : remainder;
    }

    private static void resetCarries(GatheringFishingHookAccess hook) {
        hook.essenceAscendance$setLureBefore(0);
        hook.essenceAscendance$setHookBefore(0);
        hook.essenceAscendance$setLureCarry(0);
        hook.essenceAscendance$setHookCarry(0);
        hook.essenceAscendance$setReelCarry(0);
    }
}
