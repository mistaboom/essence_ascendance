package com.mistaboom.essence_ascendance.progression;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.attunement.*;
import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.data.*;
import com.mistaboom.essence_ascendance.lifecycle.PlayerRuntimeLifecycleService;
import com.mistaboom.essence_ascendance.tier.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

/** Player Ascension depends exclusively on earned current-chapter category seals. */
public final class AscendanceEngine {
    private AscendanceEngine() {}
    public static AscendanceEvaluationResult evaluate(ServerPlayer player) {
        Objects.requireNonNull(player);
        PlayerEssenceData data = EssenceSavedData.get(player.server).getPlayerData(player.getUUID());
        return evaluate(data, EssenceConfigManager.serverRuntime());
    }
    /** Explicit profile injection supports side-neutral policy checks without consulting a bootstrap. */
    public static AscendanceEvaluationResult evaluate(PlayerEssenceData data,
            com.mistaboom.essence_ascendance.balance.runtime.RuntimeBalanceDefinition authoritativeRuntime) {
        Objects.requireNonNull(data);
        AscendanceTierDefinition current = data.getTier();
        AscendanceTierDefinition next = AscendanceTierRegistry.values().stream()
                .filter(t -> t.order() > current.order()).min(Comparator.comparingInt(AscendanceTierDefinition::order)).orElse(null);
        if (authoritativeRuntime == null) return new AscendanceEvaluationResult(AscendanceEvaluationResult.Status.CONFIGURATION_ERROR,current,next,null);
        if (next == null) return new AscendanceEvaluationResult(AscendanceEvaluationResult.Status.MAX_TIER,current,null,null);
        try {
            AttunementProfile.Chapter chapter = authoritativeRuntime.attunement().chapter(current.id().toString());
            if (chapter == null || !chapter.toTierId().equals(next.id().toString()))
                throw new IllegalStateException("Missing adjacent Attunement chapter; rebuild generated balance");
            data.attunement().chapter(chapter.id());
            int completed = AttunementService.completed(data.attunement(), chapter);
            return new AscendanceEvaluationResult(AscendanceEvaluationResult.Status.AVAILABLE,current,next,
                    new AscendanceProgressSnapshot(current.id(),next.id(),0,0,0,0,completed,chapter.requiredCategories(),
                            new MilestoneProgress(MilestoneRequirement.always(),true,true,List.of())));
        } catch (RuntimeException error) {
            EssenceAscendance.LOGGER.error("Could not evaluate Category Attunement at player tier {}",data.getTierId(),error);
            return new AscendanceEvaluationResult(AscendanceEvaluationResult.Status.CONFIGURATION_ERROR,current,next,null);
        }
    }
    /** Respecs preserve earned progress; projections are validated by the atomic Nexus transaction. */
    public static AscendanceEvaluationResult evaluateProjected(ServerPlayer player, Map<ResourceLocation,Long> investments) {
        validate(investments); return evaluate(player);
    }
    public static AscendanceEvaluationResult evaluateProjected(ServerPlayer player, Map<ResourceLocation,Long> available,
            Map<ResourceLocation,Long> investments, Map<ResourceLocation,SkillPurchase> ownedSkills) {
        validate(available); validate(investments); Map.copyOf(ownedSkills); return evaluate(player);
    }
    private static void validate(Map<ResourceLocation,Long> values) {
        values.forEach((id,value) -> { if(id == null || value == null || value < 0) throw new IllegalArgumentException("Invalid projected investment"); });
    }
    public static AscendanceAttemptResult ascend(ServerPlayer player) {
        var evaluation = evaluate(player);
        if(evaluation.status()==AscendanceEvaluationResult.Status.MAX_TIER)
            return new AscendanceAttemptResult(AscendanceAttemptResult.Status.MAX_TIER,evaluation);
        if(evaluation.status()==AscendanceEvaluationResult.Status.CONFIGURATION_ERROR)
            return new AscendanceAttemptResult(AscendanceAttemptResult.Status.CONFIGURATION_ERROR,evaluation);
        if(!evaluation.progress().readyToAscend()) return new AscendanceAttemptResult(AscendanceAttemptResult.Status.NOT_READY,evaluation);
        EssenceSavedData saved = EssenceSavedData.get(player.server);
        if(!saved.getTier(player.getUUID()).id().equals(evaluation.currentTier().id()))
            return new AscendanceAttemptResult(AscendanceAttemptResult.Status.NOT_READY,evaluate(player));
        saved.setTier(player.getUUID(),evaluation.nextTier());
        AttunementGameplay.forget(player);
        PlayerRuntimeLifecycleService.refreshProgressionState(player);
        return new AscendanceAttemptResult(AscendanceAttemptResult.Status.SUCCESS,evaluation);
    }
}
