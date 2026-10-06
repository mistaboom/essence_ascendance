package com.mistaboom.essence_ascendance.gathering;

import com.mistaboom.essence_ascendance.config.GatheringBalanceSettings;
import com.mistaboom.essence_ascendance.equipment.PlayerAttributedBlockHarvestService;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectState;
import com.mistaboom.essence_ascendance.network.MicroVisualFeedback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.tags.BlockTags;
import com.mistaboom.essence_ascendance.balance.engine.OptionalIntegration;

import java.util.List;
import com.mistaboom.essence_ascendance.utility.SharedTargetWork;
import com.mistaboom.essence_ascendance.utility.UtilityAuraService;

/** Shared nearby-growth and animal scheduler for the Gathering rural branch. */
public final class GatheringRuralService {
    private GatheringRuralService() { }

    public static void tickVerdantStride(SkillEffectRuntime.Context context) {
        GatheringBalanceSettings.VerdantStride tuning = context.settings().gathering().verdantStride();
        PulseState pulse = pulseState(context, SkillIds.VERDANT_STRIDE);
        if (!pulseDue(context, pulse, tuning.growthPulseTicks())) return;

        ServerPlayer player = context.player();
        ServerLevel level = player.serverLevel();
        BlockPos center = player.blockPosition();
        int range = (int) Math.ceil(tuning.radiusBlocks());
        double rangeSqr = tuning.radiusBlocks() * tuning.radiusBlocks();
        int eligible = 0;
        int extraTicks = 0;
        int visualEvents = 0;

        int side = range * 2 + 1;
        int volume = side * side * side;
        for (int visits = 0; visits < Math.min(volume, 1024); visits++) {
            if (!SharedTargetWork.visit(level, context.now())) break;
            int index = pulse.cursor;
            pulse.cursor = (pulse.cursor + 1) % volume;
            BlockPos mutable = center.offset(index % side - range, (index / side) % side - range, index / (side * side) - range);
            if (center.distSqr(mutable) > rangeSqr) continue;
            if (!level.hasChunkAt(mutable)) continue;
            BlockState state = level.getBlockState(mutable);
            if (!isEligibleGrowthTarget(state, level.getBlockEntity(mutable), tuning.usesBoneMealGrowth())) continue;
            var match = UtilityAuraService.strongest(level, net.minecraft.world.phys.Vec3.atCenterOf(mutable), SkillIds.VERDANT_STRIDE,
                    c -> c.settings().gathering().verdantStride().radiusBlocks(),
                    c -> c.settings().gathering().verdantStride().growthChance() / c.settings().gathering().verdantStride().growthPulseTicks());
            if (match == null || match.player() != player || !SharedTargetWork.claimPosition(level, mutable.asLong(),
                    "crop_growth", context.now(), tuning.growthPulseTicks())) continue;
            eligible++;
            if (player.getRandom().nextDouble() >= tuning.growthChance()) continue;
            OptionalIntegration.attempt("verdant_stride", "native_growth", () -> {
                if (tuning.usesBoneMealGrowth()) {
                    BonemealableBlock growth = (BonemealableBlock) state.getBlock();
                    if (growth.isValidBonemealTarget(level, mutable, state)
                            && growth.isBonemealSuccess(level, level.random, mutable, state)) {
                        growth.performBonemeal(level, level.random, mutable, state);
                    }
                } else {
                    state.randomTick(level, mutable, level.random);
                }
                return Boolean.TRUE;
            });
            if (state.equals(level.getBlockState(mutable))) continue;
            extraTicks++;
            // One pulse may visit a large farm. Keep the acknowledgement sampled and bounded.
            if (visualEvents++ < 6) {
                MicroVisualFeedback.gathering(level, net.minecraft.world.phys.Vec3.atCenterOf(mutable),
                        mutable.asLong() ^ context.now());
            }
        }
        pulse.lastEligibleTargets = eligible;
        pulse.lastSuccessfulEvents = extraTicks;
    }

    /** Growth needs immature crops; harvest maturity cannot decide whether a plant may grow.
     * Native crops/saplings and their tags keep spreading terrain or production blocks outside the aura.
     * Opaque storage without native inventory/menu interfaces still requires an exclusion tag/adapter. */
    public static boolean isEligibleGrowthTarget(BlockState state, BlockEntity blockEntity, boolean boneMealGrowth) {
        if (state == null || state.is(PlayerAttributedBlockHarvestService.CROP_YIELD_EXCLUDED)
                || blockEntity instanceof Container || blockEntity instanceof MenuProvider) return false;
        boolean crop = PlayerAttributedBlockHarvestService.isCrop(state);
        boolean sapling = state.getBlock() instanceof SaplingBlock || state.is(BlockTags.SAPLINGS);
        if (!crop && !(boneMealGrowth && sapling)) return false;
        return boneMealGrowth ? state.getBlock() instanceof BonemealableBlock : state.isRandomlyTicking();
    }

    public static void tickHerdkeeper(SkillEffectRuntime.Context context) {
        GatheringBalanceSettings.Herdkeeper tuning = context.settings().gathering().herdkeeper();
        int visualEvents = 0;
        for (Animal animal : nearbyLivestock(context.player(), tuning.radiusBlocks())) {
            if (!SharedTargetWork.visit(animal.level(), context.now())) break;
            var match = UtilityAuraService.strongest(context.player().serverLevel(), animal.position(), SkillIds.HERDKEEPER,
                    c -> c.settings().gathering().herdkeeper().radiusBlocks(),
                    c -> c.settings().gathering().herdkeeper().breedingRecoveryMultiplier());
            if (match == null || match.player() != context.player()
                    || !SharedTargetWork.claim(animal.level(), animal, "herdkeeper", context.now(), 1)) continue;
            // One is vanilla's neutral navigation speed factor; skill power is kept in generated cooldown recovery.
            animal.getNavigation().moveTo(context.player(), 1.0D);
            int age = animal.getAge();
            if (age <= 0) continue;
            int extraRecovery = stochasticWhole(tuning.breedingRecoveryMultiplier() - 1.0D,
                    context.player().getRandom().nextDouble());
            if (extraRecovery > 0) {
                int resolvedAge = Math.max(0, age - extraRecovery);
                animal.setAge(resolvedAge);
                if (resolvedAge == 0 && visualEvents++ < 4) {
                    MicroVisualFeedback.gathering(context.player().serverLevel(), animal.getBoundingBox().getCenter(),
                            animal.getId() * 31L ^ context.now());
                }
            }
        }
    }

    public static void tickAnimalGift(SkillEffectRuntime.Context context) {
        GatheringBalanceSettings.AnimalGift tuning = context.settings().gathering().animalGift();
        PulseState pulse = pulseState(context, SkillIds.ANIMAL_GIFT);
        if (!pulseDue(context, pulse, tuning.giftPulseTicks())) return;

        ServerPlayer player = context.player();
        int eligible = 0;
        int gifts = 0;
        int visualEvents = 0;
        for (Animal animal : nearbyLivestock(player, tuning.radiusBlocks())) {
            if (!SharedTargetWork.visit(animal.level(), context.now())) break;
            if (!giftEligible(player, animal)) continue;
            var match = UtilityAuraService.strongest(player.serverLevel(), animal.position(), SkillIds.ANIMAL_GIFT,
                    c -> c.settings().gathering().animalGift().radiusBlocks(),
                    c -> c.settings().gathering().animalGift().giftChance() / c.settings().gathering().animalGift().giftPulseTicks());
            if (match == null || match.player() != player
                    || !SharedTargetWork.claim(animal.level(), animal, "animal_gift", context.now(), tuning.giftPulseTicks())) continue;
            eligible++;
            if (player.getRandom().nextDouble() >= tuning.giftChance()) continue;
            if (RenewableAnimalProductRegistry.provideOne(player, animal, player.getRandom())) {
                gifts++;
                if (visualEvents++ < 4) MicroVisualFeedback.gathering(player.serverLevel(),
                        animal.getBoundingBox().getCenter(), animal.getId() * 31L ^ context.now());
            }
        }
        pulse.lastEligibleTargets = eligible;
        pulse.lastSuccessfulEvents = gifts;
    }

    /** Native fragile-ground hooks call this without duplicating activation logic. */
    public static boolean protectsFragileGround(Entity entity) {
        return entity instanceof ServerPlayer player
                && SkillEffectRuntime.context(player).isEffective(SkillIds.VERDANT_STRIDE);
    }

    public static int nearbyLivestockCount(ServerPlayer player, double radiusBlocks) {
        return nearbyLivestock(player, radiusBlocks).size();
    }

    public static int renewableProductCount(ServerPlayer player, double radiusBlocks) {
        int count = 0;
        for (Animal animal : nearbyLivestock(player, radiusBlocks)) {
            if (RenewableAnimalProductRegistry.hasAvailableProduct(player, animal)) count++;
        }
        return count;
    }

    /** Current loaded crops for the Verdant Stride badge, without scheduler claims or random ticks. */
    public static int nearbyGrowingCropCount(ServerPlayer player, double radiusBlocks) {
        return nearbyGrowingCropCount(player, radiusBlocks, false);
    }

    public static int nearbyGrowingCropCount(ServerPlayer player, double radiusBlocks, boolean boneMealGrowth) {
        ServerLevel level = player.serverLevel();
        BlockPos center = player.blockPosition();
        int range = (int) Math.ceil(radiusBlocks);
        double rangeSqr = radiusBlocks * radiusBlocks;
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-range, -range, -range),
                center.offset(range, range, range))) {
            if (center.distSqr(pos) > rangeSqr || !level.hasChunkAt(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (isEligibleGrowthTarget(state, level.getBlockEntity(pos), boneMealGrowth)) count++;
        }
        return count;
    }

    /** Current Animal Gift eligibility, independent of the last scheduler pulse. */
    public static int giftReadyCount(ServerPlayer player, double radiusBlocks) {
        int count = 0;
        for (Animal animal : nearbyLivestock(player, radiusBlocks)) {
            if (giftEligible(player, animal)) count++;
        }
        return count;
    }

    public static HerdkeeperHudState herdkeeperHudState(ServerPlayer player, double radiusBlocks) {
        int nearby = 0;
        int recovering = 0;
        for (Animal animal : nearbyLivestock(player, radiusBlocks)) {
            nearby++;
            if (animal.getAge() > 0) recovering++;
        }
        return new HerdkeeperHudState(nearby, recovering);
    }

    public static PulseHudState pulseHudState(SkillEffectRuntime.Context context,
                                               net.minecraft.resources.ResourceLocation skill) {
        PulseState state = context.existingState(skill);
        if (state == null) return new PulseHudState(0, 0, 0L);
        return new PulseHudState(state.lastEligibleTargets, state.lastSuccessfulEvents, state.nextPulseTick);
    }

    public static void clear(SkillEffectRuntime.Context context, net.minecraft.resources.ResourceLocation skill) {
        context.discardState(skill);
    }

    private static List<Animal> nearbyLivestock(ServerPlayer player, double radiusBlocks) {
        double radiusSqr = radiusBlocks * radiusBlocks;
        List<Animal> animals = new java.util.ArrayList<>();
        player.serverLevel().getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(Animal.class),
                player.getBoundingBox().inflate(radiusBlocks),
                animal -> animal.isAlive() && !animal.isRemoved()
                        && !(animal instanceof TamableAnimal)
                        && animal.distanceToSqr(player) <= radiusSqr, animals, 256);
        return animals;
    }

    private static PulseState pulseState(SkillEffectRuntime.Context context,
                                         net.minecraft.resources.ResourceLocation skill) {
        return context.state(skill, PulseState::new);
    }

    private static boolean pulseDue(SkillEffectRuntime.Context context, PulseState state, int periodTicks) {
        if (state.nextPulseTick != Long.MIN_VALUE && context.now() < state.nextPulseTick) return false;
        state.nextPulseTick = context.now() + periodTicks;
        return true;
    }

    private static boolean giftEligible(ServerPlayer player, Animal animal) {
        return !animal.isBaby()
                && animal instanceof AnimalFeedingState fed && fed.essenceAscendance$fed(player.level().getGameTime())
                && RenewableAnimalProductRegistry.hasAvailableProduct(player, animal);
    }

    private static int stochasticWhole(double expected, double roll) {
        if (!Double.isFinite(expected) || expected <= 0) return 0;
        int whole = (int) Math.floor(expected);
        return whole + (roll < expected - whole ? 1 : 0);
    }

    public record PulseHudState(int eligibleTargets, int successfulEvents, long nextPulseTick) { }
    public record HerdkeeperHudState(int nearbyAnimals, int recoveringCooldowns) { }

    private static final class PulseState implements SkillEffectState {
        private long nextPulseTick = Long.MIN_VALUE;
        private int lastEligibleTargets;
        private int lastSuccessfulEvents;
        private int cursor;

        @Override public void clear() {
            nextPulseTick = Long.MIN_VALUE;
            lastEligibleTargets = 0;
            lastSuccessfulEvents = 0;
        }
    }
}
