package com.mistaboom.essence_ascendance.gameplay;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.attunement.AttunementGameplay;
import com.mistaboom.essence_ascendance.skill.effect.RecentHostileCombat;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.util.Objects;
import java.util.function.BiConsumer;

/** Direct post-commit dispatch. No timer loop, pending durable receipt or retained entity history. */
public final class SurvivalOutcomes {
    private static BiConsumer<MinecraftServer, SurvivalOutcome> listener;
    private SurvivalOutcomes() { }
    public static void register(BiConsumer<MinecraftServer, SurvivalOutcome> consumer) {
        if (listener != null) throw new IllegalStateException("Survival consumer already registered");
        listener = Objects.requireNonNull(consumer);
    }
    public static SurvivalOutcome.Context context(ServerPlayer player, String kind) {
        return new SurvivalOutcome.Context(AttunementGameplay.action(kind), player.getUUID(),
                player.level().dimension().location().toString(), player.server.overworld().getGameTime());
    }
    public static void publish(ServerPlayer player, SurvivalOutcome fact) {
        // Own-debt transitions also establish an ineligible baseline, so a stale earlier episode cannot be laundered.
        if ((!AttunementGameplay.eligible(player) && !(fact instanceof SurvivalOutcome.CeilingDebt))
                || !fact.context().actor().equals(player.getUUID())) return;
        deliver(player.server, fact);
    }
    private static void deliver(MinecraftServer server, SurvivalOutcome fact) {
        if (listener != null) try { listener.accept(server, fact); }
        catch (RuntimeException failure) { EssenceAscendance.LOGGER.error("Survival outcome delivery failed", failure); }
    }
    public static void automaticMeal(ServerPlayer player) {
        var skill = SkillEffectRuntime.context(player);
        if (!AttunementGameplay.eligible(player)) return;
        String encounter = RecentHostileCombat.encounter(skill, skill.settings().vitality().innerSustenance().combatTimeoutTicks());
        int meals = RecentHostileCombat.completedAutomaticMeal(skill, skill.settings().vitality().innerSustenance().combatTimeoutTicks());
        if (encounter != null && meals > 0)
            publish(player, new SurvivalOutcome.AutomaticMeal(context(player, "automatic_meal"), encounter, meals));
    }
    /** Native committed death is processed even when the dying player is no longer award-eligible. */
    public static void death(ServerPlayer player) {
        if (!player.server.isSameThread()) return;
        deliver(player.server, new SurvivalOutcome.Death(context(player, "life_end")));
    }
}
