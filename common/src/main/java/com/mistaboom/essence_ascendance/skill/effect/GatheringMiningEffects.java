package com.mistaboom.essence_ascendance.skill.effect;

import com.mistaboom.essence_ascendance.config.GatheringBalanceSettings;
import com.mistaboom.essence_ascendance.gathering.GatheringToolResolver;
import com.mistaboom.essence_ascendance.gathering.HotbarLightPlacementService;
import com.mistaboom.essence_ascendance.gathering.NaturalOreDropService;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.network.MicroVisualFeedback;
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

    /** Called only after positive native block removal, with the scoped tool swap restored. */
    public static void successfulHarvest(ServerPlayer player, BlockPos pos, BlockState state, ItemStack resolvedTool) {
        successfulHarvest(player, pos, state, resolvedTool, HarvestEvidence.UNKNOWN);
    }

    /** Captured before native removal; absence of positive evidence never means natural generation. */
    public record HarvestEvidence(boolean generatedOrigin, boolean correctTool, boolean underwater, boolean aquaticBody) {
        public static final HarvestEvidence UNKNOWN = new HarvestEvidence(false, false, false, false);
    }
    public static HarvestEvidence captureHarvest(ServerPlayer player, BlockPos pos, BlockState state) {
        boolean generated = player.serverLevel().getBlockEntity(pos) instanceof com.mistaboom.essence_ascendance.ore.LatentOreBlockEntity ore
                && ore.hasGeneratedOrigin(player.level().dimension().location().toString(), pos)
                && NaturalOreDropService.isOreType(state);
        generated |= com.mistaboom.essence_ascendance.gathering.GeneratedOreObservations.matches(player.server,
                player.level().dimension().location().toString(),pos,state);
        return new HarvestEvidence(generated, player.hasCorrectToolForDrops(state), player.isUnderWater(),
                SkillEffectRuntime.context(player).isEffective(SkillIds.AQUATIC_BODY));
    }

    public static void successfulHarvest(ServerPlayer player, BlockPos pos, BlockState state, ItemStack resolvedTool, HarvestEvidence evidence) {
        if (player == null || state == null || state.isAir()) return;
        SkillEffectRuntime.Context context = SkillEffectRuntime.context(player);
        com.mistaboom.essence_ascendance.gameplay.GatheringOutcomes.publish(player,
                new com.mistaboom.essence_ascendance.gameplay.GatheringOutcome.Harvest(
                        com.mistaboom.essence_ascendance.gameplay.GatheringOutcomes.context(player, "harvest_success"),
                        BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(), evidence.generatedOrigin(), evidence.correctTool(), evidence.underwater(), evidence.aquaticBody()));
        if (context.isEffective(SkillIds.TOOL_INSTINCT) && GatheringToolResolver.isMiningTool(resolvedTool))
            SkillHudEvents.record(player, SkillIds.TOOL_INSTINCT, Text.translated("hud.essence_ascendance.event.tool"), List.of(Text.literal(resolvedTool.getHoverName().getString())));
        if (context.isEffective(SkillIds.MINING_MOMENTUM)) recordMomentum(context, state);
        if (context.isEffective(SkillIds.NATURES_BOON)) {
            double chance = settings(context).naturesBoon().dropChance();
            if (chance > 0 && NaturalOreDropService.eligibleSource(player.serverLevel(), state)
                    && player.getRandom().nextDouble() < chance) {
                var reward = NaturalOreDropService.dropRandomOreReceipt(player, pos, state, resolvedTool);
                if (reward != null) {
                    com.mistaboom.essence_ascendance.gameplay.GatheringOutcomes.publish(player,
                            new com.mistaboom.essence_ascendance.gameplay.GatheringOutcome.BonusOre(
                                    com.mistaboom.essence_ascendance.gameplay.GatheringOutcomes.context(player, "ore_bonus"), reward.ore(), reward.deliveredItems()));
                    SkillHudEvents.record(player, SkillIds.NATURES_BOON, "loot", 1);
                    MicroVisualFeedback.gathering(player.serverLevel(), net.minecraft.world.phys.Vec3.atCenterOf(pos),
                            pos.asLong() ^ context.now());
                }
            }
        }
        if (context.isEffective(SkillIds.TORCHBEARER) && HotbarLightPlacementService.placeAtFeetIfSpawnDark(player)) {
            SkillHudEvents.record(player, SkillIds.TORCHBEARER, "placed", 1);
            MicroVisualFeedback.gathering(player.serverLevel(), player.position().add(0, 0.18, 0),
                    player.getUUID().getLeastSignificantBits() ^ context.now());
        }
    }

    private static void recordMomentum(SkillEffectRuntime.Context context, BlockState block) {
        MomentumState state = context.state(SkillIds.MINING_MOMENTUM, MomentumState::new);
        GatheringBalanceSettings.MiningMomentum tuning = settings(context).miningMomentum();
        ResourceLocation material = BuiltInRegistries.BLOCK.getKey(block.getBlock());
        if (state.lastBreakAt != Long.MIN_VALUE && context.now() - state.lastBreakAt > tuning.chainTimeoutTicks()) {
            state.reset(); com.mistaboom.essence_ascendance.gameplay.GatheringOutcomes.reset(context.player());
        }
        boolean same = material.equals(state.lastMaterial);
        double before = state.amount;
        double increment = 1.0 / tuning.buildBreaks();
        if (state.lastMaterial != null && !same) increment *= tuning.crossMaterialBuildFraction();
        state.amount = Math.clamp(state.amount + increment, 0, 1);
        state.lastMaterial = material;
        state.lastBreakAt = context.now();
        state.sameMaterial = same;
        state.eligibleOrigin &= com.mistaboom.essence_ascendance.attunement.AttunementGameplay.eligible(context.player());
        applyMomentum(context, state.amount);
        com.mistaboom.essence_ascendance.gameplay.GatheringOutcomes.publish(context.player(),
                new com.mistaboom.essence_ascendance.gameplay.GatheringOutcome.Momentum(
                        com.mistaboom.essence_ascendance.gameplay.GatheringOutcomes.context(context.player(), "mining_rhythm"),
                        state.rhythm, before, state.amount, tuning.maximumSpeedBonus(), state.eligibleOrigin));
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
        String rhythm = java.util.UUID.randomUUID().toString();
        boolean eligibleOrigin = true;
        void reset() { amount = 0; lastBreakAt = Long.MIN_VALUE; lastMaterial = null; sameMaterial = false; rhythm = java.util.UUID.randomUUID().toString(); eligibleOrigin = true; }
        @Override public void clear() { reset(); }
    }

    private static final class ToolInstinct implements SkillEffectHandler {
        @Override public ResourceLocation id() { return SkillIds.TOOL_INSTINCT; }
        @Override public List<String> debugLines(SkillEffectRuntime.Context context) {
            return List.of("Carried native tool resolver active=true; eligible correct-tool speed bonus="
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
                    || context.now() - state.lastBreakAt > settings(context).miningMomentum().chainTimeoutTicks()) {
                state.reset(); com.mistaboom.essence_ascendance.gameplay.GatheringOutcomes.reset(context.player());
            }
            applyMomentum(context, state.amount);
        }
        @Override public void tick(SkillEffectRuntime.Context context) { reconcile(context); }
        @Override public void deactivate(SkillEffectRuntime.Context context) {
            com.mistaboom.essence_ascendance.gameplay.GatheringOutcomes.reset(context.player());
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
