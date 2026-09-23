package com.mistaboom.essence_ascendance.gathering;

import com.mistaboom.essence_ascendance.config.GatheringBalanceSettings;
import com.mistaboom.essence_ascendance.equipment.EquipmentGatheringService;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectState;
import com.mistaboom.essence_ascendance.skill.effect.TimedStackState;
import com.mistaboom.essence_ascendance.network.MicroVisualFeedback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Native fishing integration shared by Fishing Instinct, Fisher's Call and Pocket Nets. */
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

    public record ShoalHudState(int stacks, int maximumStacks, long expiresAt,
                                double biteSpeedMultiplier, double extraCatchChance) {
        public boolean active() { return stacks > 0; }
    }

    public record PocketNetHudState(boolean swimming, long nextPulseTick, int lastDrops) { }

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

    /** Captures native countdown state and lets Fishing Instinct stretch only the native live reel window. */
    public static void beforeFishingLogic(ServerPlayer player, GatheringFishingHookAccess hook) {
        SkillEffectRuntime.Context context = SkillEffectRuntime.context(player);
        GatheringBalanceSettings.FishingInstinct instinct = fishingInstinct(context);
        GatheringBalanceSettings.FishersCall shoal = fishersCall(context);
        if (instinct == null && shoal == null) {
            resetCarries(hook);
            return;
        }
        hook.essenceAscendance$setLureBefore(hook.essenceAscendance$timeUntilLured());
        hook.essenceAscendance$setHookBefore(hook.essenceAscendance$timeUntilHooked());
        hook.essenceAscendance$setNibbleBefore(hook.essenceAscendance$nibble());

        int nibble = hook.essenceAscendance$nibble();
        if (nibble <= 0 || instinct == null) {
            hook.essenceAscendance$setReelCarry(0);
            return;
        }

        // Vanilla removes one live-window tick per fishing update. Retaining (1 - 1/M) stretches it by M.
        double retainedPerTick = 1.0D - 1.0D / instinct.reelWindowMultiplier();
        double carry = hook.essenceAscendance$reelCarry() + retainedPerTick;
        int retained = (int) Math.floor(carry);
        hook.essenceAscendance$setReelCarry(carry - retained);
        if (retained > 0) hook.essenceAscendance$setNibble(nibble + retained);
    }

    /** Multiplies whatever progress vanilla actually made, allowing Fisher's Call to compound with Fishing Instinct. */
    public static void afterFishingLogic(ServerPlayer player, GatheringFishingHookAccess hook) {
        SkillEffectRuntime.Context context = SkillEffectRuntime.context(player);
        GatheringBalanceSettings.FishingInstinct instinct = fishingInstinct(context);
        GatheringBalanceSettings.FishersCall shoal = fishersCall(context);
        if (instinct == null && shoal == null) {
            resetCarries(hook);
            return;
        }
        if (shoal != null && hook.essenceAscendance$nibbleBefore() > 0
                && hook.essenceAscendance$nibble() <= 0) {
            context.discardState(SkillIds.FISHERS_CALL);
        }
        double multiplier = instinct == null ? 1.0D : instinct.biteSpeedMultiplier();
        multiplier *= shoalBiteMultiplier(context, shoal);
        hook.essenceAscendance$setLureCarry(accelerateNativeProgress(
                hook.essenceAscendance$lureBefore(), hook.essenceAscendance$timeUntilLured(),
                hook.essenceAscendance$lureCarry(), multiplier,
                hook::essenceAscendance$setTimeUntilLured));
        hook.essenceAscendance$setHookCarry(accelerateNativeProgress(
                hook.essenceAscendance$hookBefore(), hook.essenceAscendance$timeUntilHooked(),
                hook.essenceAscendance$hookCarry(), multiplier,
                hook::essenceAscendance$setTimeUntilHooked));
    }

    /** Extra native Luck level applied only while the hook executes its ordinary retrieval path. */
    public static int virtualLuck(ServerPlayer player) {
        SkillEffectRuntime.Context context = SkillEffectRuntime.context(player);
        GatheringBalanceSettings.FishingInstinct tuning = fishingInstinct(context);
        if (tuning == null) return 0;
        return EquipmentGatheringService.stochasticVirtualEnchantmentLevel(
                tuning.virtualLuckLevels(), player.getRandom());
    }

    /** Commits Fisher's Call only after native retrieval completed, so canceled/failed retrieval cannot build a streak. */
    public static void afterRetrieve(ServerPlayer player, FishingHook hook, ItemStack rod,
                                     boolean hadFishBite, int effectiveHookLuck, int virtualLuckBonus) {
        SkillEffectRuntime.Context context = SkillEffectRuntime.context(player);
        if (!context.isEffective(SkillIds.FISHERS_CALL)) {
            context.discardState(SkillIds.FISHERS_CALL);
            if (hadFishBite && virtualLuckBonus > 0) MicroVisualFeedback.gathering(
                    player.serverLevel(), hook.position(), hook.getId() * 31L ^ context.now());
            return;
        }
        if (!hadFishBite) {
            context.discardState(SkillIds.FISHERS_CALL);
            return;
        }

        GatheringBalanceSettings.FishersCall tuning = context.settings().gathering().fishersCall();
        TimedStackState state = context.state(SkillIds.FISHERS_CALL, TimedStackState::shared);
        state.grant(context.now(), tuning.catchesToFullShoal(), tuning.chainTimeoutTicks(), true);
        double fraction = state.count() / (double) tuning.catchesToFullShoal();
        double chance = Math.clamp(tuning.maximumExtraCatchChance() * fraction, 0.0D, 1.0D);
        int extraDrops = 0;
        if (player.getRandom().nextDouble() < chance) {
            extraDrops = FishingLootService.giveOrDrop(player, FishingLootService.roll(player, hook, hook.position(), rod,
                    effectiveHookLuck + player.getLuck()));
        }
        if (virtualLuckBonus > 0 || extraDrops > 0) MicroVisualFeedback.gathering(player.serverLevel(), hook.position(),
                hook.getId() * 31L ^ context.now());
    }

    public static void reconcileShoal(SkillEffectRuntime.Context context) {
        TimedStackState state = context.existingState(SkillIds.FISHERS_CALL);
        if (state == null) return;
        GatheringBalanceSettings.FishersCall tuning = context.settings().gathering().fishersCall();
        state.reconcile(context.now(), tuning.catchesToFullShoal());
        if (state.count() == 0) context.discardState(SkillIds.FISHERS_CALL);
    }

    public static ShoalHudState shoalHudState(SkillEffectRuntime.Context context) {
        GatheringBalanceSettings.FishersCall tuning = context.settings().gathering().fishersCall();
        TimedStackState state = context.existingState(SkillIds.FISHERS_CALL);
        if (state == null) return new ShoalHudState(0, tuning.catchesToFullShoal(), 0, 1.0D, 0.0D);
        state.reconcile(context.now(), tuning.catchesToFullShoal());
        int stacks = state.count();
        double fraction = stacks / (double) tuning.catchesToFullShoal();
        return new ShoalHudState(stacks, tuning.catchesToFullShoal(), state.nextExpiry(),
                1.0D + (tuning.maximumBiteSpeedMultiplier() - 1.0D) * fraction,
                tuning.maximumExtraCatchChance() * fraction);
    }

    public static void tickPocketNets(SkillEffectRuntime.Context context) {
        GatheringBalanceSettings.PocketNets tuning = context.settings().gathering().pocketNets();
        PocketNetState state = context.state(SkillIds.POCKET_NETS, PocketNetState::new);
        // Ctrl/sprint swimming is the intended activation. The sprint flag is more stable than the
        // transient swimming pose, which can flicker while vanilla changes pose around the surface.
        if (!isActivelySwimming(context.player())) {
            state.clear();
            return;
        }
        if (state.nextPulseTick == Long.MIN_VALUE) {
            state.nextPulseTick = context.now() + tuning.pulseTicks();
            return;
        }
        if (context.now() < state.nextPulseTick) return;
        state.nextPulseTick = context.now() + tuning.pulseTicks();
        state.lastDrops = 0;
        if (context.player().getRandom().nextDouble() >= tuning.dropChance()) return;

        ItemStack contextRod = Items.FISHING_ROD.getDefaultInstance();
        ItemStack reward = FishingLootService.rollOneGuaranteedReward(
                context.player(), context.player(), context.player().position(), contextRod, context.player().getLuck());
        if (!reward.isEmpty()) {
            state.lastDrops = FishingLootService.giveOrDrop(context.player(), java.util.List.of(reward));
            if (state.lastDrops > 0) MicroVisualFeedback.gathering(context.player().serverLevel(),
                    context.player().position().add(0, 0.6, 0),
                    context.player().getUUID().getLeastSignificantBits() ^ context.now());
        }
    }

    public static PocketNetHudState pocketNetHudState(SkillEffectRuntime.Context context) {
        PocketNetState state = context.existingState(SkillIds.POCKET_NETS);
        return new PocketNetHudState(isActivelySwimming(context.player()),
                state == null ? 0 : state.nextPulseTick, state == null ? 0 : state.lastDrops);
    }

    private static boolean isActivelySwimming(ServerPlayer player) {
        return player != null && player.isInWater() && player.isSprinting();
    }

    private static GatheringBalanceSettings.FishingInstinct fishingInstinct(SkillEffectRuntime.Context context) {
        return context.isEffective(SkillIds.FISHING_INSTINCT)
                ? context.settings().gathering().fishingInstinct() : null;
    }

    private static GatheringBalanceSettings.FishersCall fishersCall(SkillEffectRuntime.Context context) {
        return context.isEffective(SkillIds.FISHERS_CALL)
                ? context.settings().gathering().fishersCall() : null;
    }

    private static double shoalBiteMultiplier(SkillEffectRuntime.Context context,
                                              GatheringBalanceSettings.FishersCall tuning) {
        if (tuning == null) return 1.0D;
        TimedStackState state = context.existingState(SkillIds.FISHERS_CALL);
        if (state == null) return 1.0D;
        state.reconcile(context.now(), tuning.catchesToFullShoal());
        if (state.count() <= 0) return 1.0D;
        double fraction = state.count() / (double) tuning.catchesToFullShoal();
        return 1.0D + (tuning.maximumBiteSpeedMultiplier() - 1.0D) * fraction;
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
        // Do not cross a native phase boundary from a TAIL hook. Vanilla must observe the
        // countdown at 1 and perform the 1 -> 0 transition itself on the next fishing tick;
        // forcing it to 0 here skips the transition body and can make an approaching fish vanish.
        int accelerated = Math.max(1, after - extraTicks);
        setter.accept(accelerated);
        return accelerated <= 1 ? 0 : remainder;
    }

    private static void resetCarries(GatheringFishingHookAccess hook) {
        hook.essenceAscendance$setLureBefore(0);
        hook.essenceAscendance$setHookBefore(0);
        hook.essenceAscendance$setNibbleBefore(0);
        hook.essenceAscendance$setLureCarry(0);
        hook.essenceAscendance$setHookCarry(0);
        hook.essenceAscendance$setReelCarry(0);
    }

    private static final class PocketNetState implements SkillEffectState {
        private long nextPulseTick = Long.MIN_VALUE;
        private int lastDrops;

        @Override public void clear() {
            nextPulseTick = Long.MIN_VALUE;
            lastDrops = 0;
        }
    }
}
