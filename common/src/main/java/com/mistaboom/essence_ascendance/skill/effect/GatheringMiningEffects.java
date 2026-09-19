package com.mistaboom.essence_ascendance.skill.effect;

import com.mistaboom.essence_ascendance.config.GatheringBalanceSettings;
import com.mistaboom.essence_ascendance.gathering.GatheringToolResolver;
import com.mistaboom.essence_ascendance.gathering.HotbarLightPlacementService;
import com.mistaboom.essence_ascendance.gathering.NaturalOreDropService;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudEntry.Text;
import com.mistaboom.essence_ascendance.visual.AscendancePalette;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

import static com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudCards.compact;

/** First Gathering branch: shared mining resolver plus rhythm, procedural ore and lighting consumers. */
public final class GatheringMiningEffects {
    private GatheringMiningEffects() { }

    public static List<SkillEffectHandler> handlers() {
        return List.of(new ToolInstinct(), new MiningMomentum(), new NaturesBoon(), new Torchbearer());
    }

    private static GatheringBalanceSettings settings(SkillEffectRuntime.Context context) { return context.settings().gathering(); }
    private static Text text(String key, String... args) { return Text.translated("hud.essence_ascendance.gathering." + key, args); }

    /** Called only after vanilla has confirmed a successful destroyBlock and the scoped tool swap is restored. */
    public static void successfulHarvest(ServerPlayer player, BlockPos pos, BlockState state, ItemStack resolvedTool) {
        if (player == null || state == null || state.isAir()) return;
        SkillEffectRuntime.Context context = SkillEffectRuntime.context(player);
        if (context.isEffective(SkillIds.MINING_MOMENTUM)) recordMomentum(context, state);
        if (context.isEffective(SkillIds.NATURES_BOON)) {
            double chance = settings(context).naturesBoon().dropChance();
            if (chance > 0 && NaturalOreDropService.eligibleSource(player.serverLevel(), state)
                    && player.getRandom().nextDouble() < chance) {
                NaturalOreDropService.dropRandomOre(player, pos, state, resolvedTool);
            }
        }
        if (context.isEffective(SkillIds.TORCHBEARER)) HotbarLightPlacementService.placeAtFeetIfSpawnDark(player);
    }

    private static void recordMomentum(SkillEffectRuntime.Context context, BlockState block) {
        MomentumState state = context.state(SkillIds.MINING_MOMENTUM, MomentumState::new);
        GatheringBalanceSettings.MiningMomentum tuning = settings(context).miningMomentum();
        ResourceLocation material = BuiltInRegistries.BLOCK.getKey(block.getBlock());
        if (state.lastBreakAt != Long.MIN_VALUE && context.now() - state.lastBreakAt > tuning.chainTimeoutTicks()) state.reset();
        boolean same = material.equals(state.lastMaterial);
        double increment = 1.0 / tuning.buildBreaks();
        if (state.lastMaterial != null && !same) increment *= tuning.crossMaterialBuildFraction();
        state.amount = Math.clamp(state.amount + increment, 0, 1);
        state.lastMaterial = material;
        state.lastBreakAt = context.now();
        state.sameMaterial = same;
        applyMomentum(context, state.amount);
    }

    private static void applyMomentum(SkillEffectRuntime.Context context, double amount) {
        double speed = Math.clamp(amount, 0, 1) * settings(context).miningMomentum().maximumSpeedBonus();
        SkillEffectAttributes.apply(context.player(), Attributes.BLOCK_BREAK_SPEED, SkillIds.MINING_MOMENTUM,
                speed, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    private static final class MomentumState implements SkillEffectState {
        double amount;
        long lastBreakAt = Long.MIN_VALUE;
        ResourceLocation lastMaterial;
        boolean sameMaterial;
        void reset() { amount = 0; lastBreakAt = Long.MIN_VALUE; lastMaterial = null; sameMaterial = false; }
        @Override public void clear() { reset(); }
    }

    private static final class ToolInstinct implements SkillEffectHandler {
        @Override public ResourceLocation id() { return SkillIds.TOOL_INSTINCT; }
        @Override public List<String> debugLines(SkillEffectRuntime.Context context) {
            return List.of("Carried Ascendance tool resolver active=true; lower-tier maximum speed bonus="
                            + settings(context).toolInstinct().maximumLowerTierSpeedBonus(),
                    "Break-speed prediction and vanilla destroyBlock share one resolver; native drops, enchantments and durability use the resolved tool.");
        }
    }

    private static final class MiningMomentum implements SkillEffectHudHandler {
        @Override public ResourceLocation id() { return SkillIds.MINING_MOMENTUM; }
        @Override public void reconcile(SkillEffectRuntime.Context context) {
            MomentumState state = context.existingState(id());
            if (state == null) { applyMomentum(context, 0); return; }
            if (state.lastBreakAt == Long.MIN_VALUE
                    || context.now() - state.lastBreakAt > settings(context).miningMomentum().chainTimeoutTicks()) state.reset();
            applyMomentum(context, state.amount);
        }
        @Override public void tick(SkillEffectRuntime.Context context) { reconcile(context); }
        @Override public void deactivate(SkillEffectRuntime.Context context) {
            context.discardState(id());
            SkillEffectAttributes.apply(context.player(), Attributes.BLOCK_BREAK_SPEED, id(), 0,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        }
        @Override public SkillEffectHudEntry hudEntry(SkillEffectRuntime.Context context) {
            MomentumState state = context.existingState(id());
            double amount = state == null ? 0 : state.amount;
            double speed = amount * settings(context).miningMomentum().maximumSpeedBonus();
            return SkillEffectHudCards.progress(id(), amount > 0, AscendancePalette.GATHERING,
                    text("mining_speed", compact(speed * 100)),
                    List.of(text("rhythm", compact(amount * 100))), amount);
        }
        @Override public List<String> debugLines(SkillEffectRuntime.Context context) {
            MomentumState state = context.existingState(id());
            var tuning = settings(context).miningMomentum();
            return List.of("Momentum=" + (state == null ? 0 : state.amount) + "; maximum speed bonus=" + tuning.maximumSpeedBonus(),
                    "Build breaks=" + tuning.buildBreaks() + "; cross-material build fraction=" + tuning.crossMaterialBuildFraction()
                            + "; chain timeout ticks=" + tuning.chainTimeoutTicks(),
                    "Last material=" + (state == null || state.lastMaterial == null ? "none" : state.lastMaterial)
                            + "; same-material last transition=" + (state != null && state.sameMaterial));
        }
    }

    private static final class NaturesBoon implements SkillEffectHandler {
        @Override public ResourceLocation id() { return SkillIds.NATURES_BOON; }
        @Override public List<String> debugLines(SkillEffectRuntime.Context context) {
            return List.of("Eligible-substrate drop chance=" + settings(context).naturesBoon().dropChance()
                            + "; current dimension candidate ores=" + NaturalOreDropService.candidateCount(context.player()),
                    "Dimension convention/custom tags choose candidates; procedural item valuation provides inverse-value selection weights; selected ore uses native loot with the tool that performed the harvest.");
        }
    }

    private static final class Torchbearer implements SkillEffectHandler {
        @Override public ResourceLocation id() { return SkillIds.TORCHBEARER; }
        @Override public List<String> debugLines(SkillEffectRuntime.Context context) {
            return List.of("Binary convenience effect; only hostile-spawn-capable lighting at the player's feet triggers native item placement.",
                    "Supports vanilla torches, c:torches, and essence_ascendance:torchbearer_lights without a parallel placement/consumption system.");
        }
    }
}
