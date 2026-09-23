package com.mistaboom.essence_ascendance.gathering;

import com.mistaboom.essence_ascendance.config.GatheringBalanceSettings;
import com.mistaboom.essence_ascendance.equipment.EquipmentDamageService;
import com.mistaboom.essence_ascendance.equipment.EquipmentGatheringService;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.effect.KeyedProgressState;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime;
import com.mistaboom.essence_ascendance.network.MicroVisualFeedback;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

/**
 * Shared ordinary-loot hook for creature-study effects. A scoped virtual
 * Looting level is visible only while vanilla evaluates the victim's native
 * loot table; no drops are copied, replaced, or synthesized here.
 */
public final class GatheringLootService {
    private static final ThreadLocal<LootScope> ACTIVE_SCOPE = new ThreadLocal<>();

    private GatheringLootService() { }

    public static void dropWithStudy(LivingEntity victim, DamageSource source,
                                     boolean causedByPlayer, Runnable nativeDrop) {
        if (!(victim.level() instanceof net.minecraft.server.level.ServerLevel)
                || !causedByPlayer) {
            nativeDrop.run();
            return;
        }
        ServerPlayer player = EquipmentDamageService.skillDamagePlayer(victim, source);
        if (player == null || !player.isAlive()) {
            nativeDrop.run();
            return;
        }
        SkillEffectRuntime.Context context = SkillEffectRuntime.context(player);
        if (!context.isEffective(SkillIds.HUNTERS_STUDY)) {
            nativeDrop.run();
            return;
        }

        GatheringBalanceSettings.HuntersStudy tuning = context.settings().gathering().huntersStudy();
        ResourceLocation creature = BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType());
        KeyedProgressState<ResourceLocation> progress = studyState(context);
        double fraction = progress.fraction(creature, tuning.killsToFullStudy());
        double expectedVirtualLooting = tuning.maximumVirtualLootingLevels() * fraction;
        int virtualLooting = EquipmentGatheringService.stochasticVirtualEnchantmentLevel(
                expectedVirtualLooting,
                player.getRandom()
        );

        LootScope previous = ACTIVE_SCOPE.get();
        ACTIVE_SCOPE.set(new LootScope(player.getUUID(), virtualLooting));
        boolean completed = false;
        try {
            nativeDrop.run();
            completed = true;
        } finally {
            if (previous == null) ACTIVE_SCOPE.remove();
            else ACTIVE_SCOPE.set(previous);
            if (completed) {
                progress.advance(creature, tuning.killsToFullStudy());
                if (virtualLooting > 0) MicroVisualFeedback.gathering(player.serverLevel(),
                        victim.getBoundingBox().getCenter(), victim.getId() * 31L ^ context.now());
            }
        }
    }

    /** Item-level loot queries occur inside the tightly bounded native drop scope. */
    public static int currentVirtualLootingBonus() {
        LootScope scope = ACTIVE_SCOPE.get();
        return scope == null ? 0 : scope.virtualLooting();
    }

    /** Entity-level enchantment queries additionally verify the scoped attacker. */
    public static int currentVirtualLootingBonus(LivingEntity entity) {
        LootScope scope = ACTIVE_SCOPE.get();
        return scope != null && entity != null && scope.owner().equals(entity.getUUID())
                ? scope.virtualLooting() : 0;
    }

    public static StudySnapshot studySnapshot(SkillEffectRuntime.Context context) {
        KeyedProgressState<ResourceLocation> state = context.existingState(SkillIds.HUNTERS_STUDY);
        if (state == null || state.lastKey() == null) return StudySnapshot.EMPTY;
        GatheringBalanceSettings.HuntersStudy tuning = context.settings().gathering().huntersStudy();
        ResourceLocation key = state.lastKey();
        int kills = Math.min(tuning.killsToFullStudy(), state.value(key));
        double fraction = state.fraction(key, tuning.killsToFullStudy());
        double expectedVirtualLooting = tuning.maximumVirtualLootingLevels() * fraction;
        String descriptionKey = BuiltInRegistries.ENTITY_TYPE.getOptional(key)
                .map(EntityType::getDescriptionId)
                .orElse("entity.minecraft.pig");
        return new StudySnapshot(key, descriptionKey, kills, tuning.killsToFullStudy(),
                fraction, expectedVirtualLooting);
    }

    public static void clearStudy(SkillEffectRuntime.Context context) {
        context.discardState(SkillIds.HUNTERS_STUDY);
    }

    private static KeyedProgressState<ResourceLocation> studyState(SkillEffectRuntime.Context context) {
        return context.state(SkillIds.HUNTERS_STUDY, KeyedProgressState::new);
    }

    private record LootScope(java.util.UUID owner, int virtualLooting) { }

    public record StudySnapshot(ResourceLocation creature, String descriptionKey, int kills,
                                int maximumKills, double fraction, double expectedVirtualLooting) {
        public static final StudySnapshot EMPTY = new StudySnapshot(null, "", 0, 1, 0, 0);
        public boolean present() { return creature != null; }
    }
}
