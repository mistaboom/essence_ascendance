package com.mistaboom.essence_ascendance.skill.effect;

import com.mistaboom.essence_ascendance.balance.economy.FractionalRewardService;
import com.mistaboom.essence_ascendance.config.GatheringBalanceSettings;
import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import com.mistaboom.essence_ascendance.gathering.GatheringLootService;
import com.mistaboom.essence_ascendance.network.PlayerEssenceSyncService;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectHudEntry.Text;
import com.mistaboom.essence_ascendance.visual.AscendancePalette;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/** Creature-loot branch: native Looting study or mutually exclusive Essence/XP conversion. */
public final class GatheringLootEffects {
    private static final String BLOOM_ESSENCE_ACCOUNT = "skill/essence_bloom/gathering_essence";
    private static final String BLOOM_EXPERIENCE_ACCOUNT = "skill/essence_bloom/experience";

    private GatheringLootEffects() { }

    public static List<SkillEffectHandler> handlers() {
        return List.of(new HuntersStudy(), new EssenceBloom());
    }

    private static Text text(String key, String... args) {
        return Text.translated("hud.essence_ascendance.gathering." + key, args);
    }

    private static final class HuntersStudy implements SkillEffectHudHandler {
        @Override public ResourceLocation id() { return SkillIds.HUNTERS_STUDY; }
        @Override public void deactivate(SkillEffectRuntime.Context context) { GatheringLootService.clearStudy(context); }
        @Override public SkillEffectHudEntry hudEntry(SkillEffectRuntime.Context context) {
            GatheringLootService.StudySnapshot study = GatheringLootService.studySnapshot(context);
            if (!study.present()) {
                return SkillEffectHudCards.progress(id(), false, AscendancePalette.GATHERING,
                        text("study", "0", Integer.toString(context.settings().gathering().huntersStudy().killsToFullStudy())),
                        List.of(), 0);
            }
            return SkillEffectHudCards.progress(id(), true, AscendancePalette.GATHERING,
                    text("study", Integer.toString(study.kills()), Integer.toString(study.maximumKills())),
                    List.of(Text.translated(study.descriptionKey()),
                            text("study_looting", SkillEffectHudCards.compact(study.expectedVirtualLooting()))),
                    study.fraction());
        }
        @Override public List<String> debugLines(SkillEffectRuntime.Context context) {
            GatheringLootService.StudySnapshot study = GatheringLootService.studySnapshot(context);
            GatheringBalanceSettings.HuntersStudy tuning = context.settings().gathering().huntersStudy();
            return List.of("Kills to full study=" + tuning.killsToFullStudy()
                            + "; maximum virtual Looting=" + tuning.maximumVirtualLootingLevels(),
                    study.present() ? "Last creature=" + study.creature() + "; progress=" + study.kills() + "/"
                            + study.maximumKills() + "; current expected virtual Looting=" + study.expectedVirtualLooting()
                            : "No creature studied yet.",
                    "Expected virtual Looting is realized stochastically per native loot-table evaluation, preserving fractional balance values while stacking with ordinary/equipment Looting through the existing enchantment hook.");
        }
    }

    private static final class EssenceBloom implements SkillEffectHudHandler {
        @Override public ResourceLocation id() { return SkillIds.ESSENCE_BLOOM; }

        @Override public void kill(SkillEffectRuntime.Context context, LivingEntity target) {
            ServerPlayer player = context.player();
            GatheringBalanceSettings.EssenceBloom tuning = context.settings().gathering().essenceBloom();
            int nativeExperience = target.getExperienceReward(player.serverLevel(), player);
            if (nativeExperience <= 0 || tuning.triggerChance() <= 0
                    || player.getRandom().nextDouble() >= tuning.triggerChance()) return;

            double essenceAccrued = nativeExperience * tuning.essencePerExperiencePoint();
            double experienceAccrued = nativeExperience * tuning.bonusExperienceFraction();
            long essence = FractionalRewardService.credit(player.server, player.getUUID(),
                    BLOOM_ESSENCE_ACCOUNT, essenceAccrued).wholeAmount();
            long experience = FractionalRewardService.credit(player.server, player.getUUID(),
                    BLOOM_EXPERIENCE_ACCOUNT, experienceAccrued).wholeAmount();

            if (essence > 0) {
                EssenceSavedData.get(player.server).addEssence(player.getUUID(), EssenceTypes.GATHERING, essence);
                PlayerEssenceSyncService.forceSync(player);
            }
            giveExperience(player, experience);

            BloomState state = context.state(id(), BloomState::new);
            if (!state.recent(context.now())) state.clearRewards();
            state.eventTick = context.now();
            state.essenceAwarded += essence;
            state.experienceAwarded += experience;
        }

        @Override public void deactivate(SkillEffectRuntime.Context context) { context.discardState(id()); }

        @Override public SkillEffectHudEntry hudEntry(SkillEffectRuntime.Context context) {
            BloomState state = context.existingState(id());
            boolean active = state != null && state.eventTick != Long.MIN_VALUE
                    && context.now() >= state.eventTick
                    && context.now() - state.eventTick < CombatHudActivity.WINDOW_TICKS;
            long essence = state == null ? 0 : state.essenceAwarded;
            long experience = state == null ? 0 : state.experienceAwarded;
            return SkillEffectHudEntry.skill(id(), active, AscendancePalette.GATHERING,
                    text("bloom_essence_badge", Long.toString(essence)),
                    List.of(text("bloom_essence", Long.toString(essence)),
                            text("bloom_experience", Long.toString(experience))),
                    SkillEffectHudEntry.Meter.none());
        }

        @Override public List<String> debugLines(SkillEffectRuntime.Context context) {
            GatheringBalanceSettings.EssenceBloom tuning = context.settings().gathering().essenceBloom();
            return List.of("Bloom chance=" + tuning.triggerChance() + "; Gathering Essence/native XP="
                            + tuning.essencePerExperiencePoint() + "; bonus XP fraction=" + tuning.bonusExperienceFraction(),
                    "Victim native XP is the shared creature-value signal; six-decimal persistent carry preserves sub-unit Essence and XP rewards exactly.");
        }
    }

    private static void giveExperience(ServerPlayer player, long amount) {
        long remaining = Math.max(0, amount);
        while (remaining > 0) {
            int step = (int) Math.min(Integer.MAX_VALUE, remaining);
            player.giveExperiencePoints(step);
            remaining -= step;
        }
    }

    private static final class BloomState implements SkillEffectState {
        private long eventTick = Long.MIN_VALUE;
        private long essenceAwarded;
        private long experienceAwarded;

        private boolean recent(long now) {
            return eventTick != Long.MIN_VALUE && now >= eventTick
                    && now - eventTick < CombatHudActivity.WINDOW_TICKS;
        }

        private void clearRewards() {
            essenceAwarded = 0;
            experienceAwarded = 0;
        }

        @Override public void clear() {
            eventTick = Long.MIN_VALUE;
            clearRewards();
        }
    }
}
