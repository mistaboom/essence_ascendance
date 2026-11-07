package com.mistaboom.essence_ascendance.achievement;

import com.mistaboom.essence_ascendance.attunement.AttunementGameplay;
import com.mistaboom.essence_ascendance.gameplay.DissolutionOutcome;
import com.mistaboom.essence_ascendance.gameplay.DissolutionOutcomes;
import com.mistaboom.essence_ascendance.gameplay.ProgressionOutcome;
import com.mistaboom.essence_ascendance.gameplay.ProgressionOutcomes;
import com.mistaboom.essence_ascendance.gameplay.EquipmentSnapshot;
import com.mistaboom.essence_ascendance.gameplay.EquipmentSnapshots;
import com.mistaboom.essence_ascendance.gameplay.EquipmentObservations;
import com.mistaboom.essence_ascendance.gameplay.MachineOutcome;
import com.mistaboom.essence_ascendance.gameplay.MachineOutcomes;
import com.mistaboom.essence_ascendance.gameplay.CombatOutcome;
import com.mistaboom.essence_ascendance.gameplay.CombatOutcomes;
import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import dev.architectury.event.events.common.LifecycleEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.util.IdentityHashMap;
import java.util.Map;

/** Native PlayerAdvancements owns completion, commands, persistence and reload reconciliation. */
public final class AchievementService {
    public static final ResourceLocation EQUIVALENT_EXCHANGE = ResourceLocation.fromNamespaceAndPath("essence_ascendance", "achievements/a022");
    public static final String DISSOLVED = "dissolved";
    private static final Map<MinecraftServer, EquivalentExchangeRule> SESSIONS = new IdentityHashMap<>();
    private static final Map<MinecraftServer, ProgressionAchievementRules> PROGRESSION = new IdentityHashMap<>();
    private static final Map<MinecraftServer, MachineAchievementRules> MACHINES = new IdentityHashMap<>();
    private static final Map<MinecraftServer, CombatAchievementRules> COMBAT = new IdentityHashMap<>();
    private static final Map<MinecraftServer, RodAchievementRules> RODS = new IdentityHashMap<>();
    private static final Map<MinecraftServer, MovementAchievementRules> MOVEMENT = new IdentityHashMap<>();
    private static final Map<MinecraftServer, SurvivalAchievementRules> SURVIVAL = new IdentityHashMap<>();
    private static final Map<MinecraftServer, CompanionAchievementRules> COMPANIONS = new IdentityHashMap<>();
    private static final Map<MinecraftServer, FarmingAchievementRules> FARMING = new IdentityHashMap<>();
    private static final Map<MinecraftServer, GatheringAchievementRules> GATHERING = new IdentityHashMap<>();
    private static boolean initialized;
    private AchievementService() { }

    public static void init() {
        if (initialized) return;
        initialized = true;
        DissolutionOutcomes.register(AchievementService::onDissolution);
        ProgressionOutcomes.register(AchievementService::onProgression);
        EquipmentObservations.register(AchievementService::onEquipment);
        MachineOutcomes.register(AchievementService::onMachine);
        CombatOutcomes.register(AchievementService::onCombat);
        com.mistaboom.essence_ascendance.gameplay.SurvivalOutcomes.register(AchievementService::onSurvival);
        com.mistaboom.essence_ascendance.gameplay.CompanionOutcomes.register(AchievementService::onCompanion);
        com.mistaboom.essence_ascendance.gameplay.FarmingOutcomes.register(AchievementService::onFarming);
        com.mistaboom.essence_ascendance.gameplay.GatheringOutcomes.register(AchievementService::onGathering);
        com.mistaboom.essence_ascendance.gameplay.GatheringOutcomes.registerReset((server, actor) -> {
            var rules = GATHERING.get(server);
            if (rules != null) rules.forget(actor);
        });
        com.mistaboom.essence_ascendance.gameplay.RodOutcomes.register(AchievementService::onRod);
        com.mistaboom.essence_ascendance.gameplay.MovementOutcomes.register(AchievementService::onMovement);
        com.mistaboom.essence_ascendance.gameplay.MovementOutcomes.registerReset((server, actor) -> {
            var rules = MOVEMENT.get(server);
            if (rules != null) rules.forget(actor);
        });
        com.mistaboom.essence_ascendance.gameplay.WorldClockObservations.register((server, previous, current) -> {
            if (current < previous) EssenceSavedData.get(server).visitPlayerData(data -> data.achievementProgress().invalidateMachineDay());
        });
        LifecycleEvent.SERVER_STOPPED.register(server -> {
            SESSIONS.remove(server); PROGRESSION.remove(server); MACHINES.remove(server); COMBAT.remove(server);
            RODS.remove(server); MOVEMENT.remove(server); SURVIVAL.remove(server);
            COMPANIONS.remove(server); FARMING.remove(server); GATHERING.remove(server);
            com.mistaboom.essence_ascendance.gameplay.CompanionOutcomes.clear(server);
            com.mistaboom.essence_ascendance.gameplay.RodOutcomes.clear(server);
            com.mistaboom.essence_ascendance.gameplay.MovementOutcomes.clear(server);
            EquipmentObservations.clear();
        });
    }

    public static void onDissolution(MinecraftServer server, DissolutionOutcome outcome) {
        if (!server.isSameThread() || server.overworld() == null) return;
        SESSIONS.computeIfAbsent(server, ignored -> new EquivalentExchangeRule()).consume(outcome,
                server.overworld().getGameTime(), actor -> AttunementGameplay.eligible(server.getPlayerList().getPlayer(actor)),
                actor -> award(server.getPlayerList().getPlayer(actor)));
        var player = server.getPlayerList().getPlayer(outcome.operator());
        if (!AttunementGameplay.eligible(player)) return;
        var saved = EssenceSavedData.get(server);
        if (MACHINES.computeIfAbsent(server, ignored -> new MachineAchievementRules()).consume(outcome,
                server.overworld().getGameTime(), true, saved.getPlayerData(outcome.operator()).achievementProgress(), id -> award(player, id))) saved.setDirty();
    }

    public static void onMachine(MinecraftServer server, MachineOutcome fact) {
        if (!server.isSameThread() || server.overworld() == null || fact == null) return;
        var player = server.getPlayerList().getPlayer(fact.actor());
        if (!AttunementGameplay.eligible(player)) return;
        var saved = EssenceSavedData.get(server);
        if (MACHINES.computeIfAbsent(server, ignored -> new MachineAchievementRules()).consume(fact,
                server.overworld().getGameTime(), true, saved.getPlayerData(fact.actor()).achievementProgress(), id -> award(player, id))) saved.setDirty();
    }

    public static void onCombat(MinecraftServer server, CombatOutcome fact) {
        if (!server.isSameThread() || server.overworld() == null || fact == null) return;
        var player = server.getPlayerList().getPlayer(fact.context().actor());
        if (!AttunementGameplay.eligible(player)) return;
        var saved = EssenceSavedData.get(server);
        if (COMBAT.computeIfAbsent(server, ignored -> new CombatAchievementRules()).consume(fact,
                server.overworld().getGameTime(), true, saved.getPlayerData(player.getUUID()).achievementProgress(), id -> award(player, id))) saved.setDirty();
    }

    public static void onRod(MinecraftServer server, com.mistaboom.essence_ascendance.gameplay.RodOutcome fact) {
        if (!server.isSameThread() || server.overworld() == null || fact == null) return;
        var player = server.getPlayerList().getPlayer(fact.actor());
        if (!AttunementGameplay.eligible(player)) return;
        RODS.computeIfAbsent(server, ignored -> new RodAchievementRules()).consume(fact,
                server.overworld().getGameTime(), true, id -> award(player, id));
    }

    public static void onMovement(MinecraftServer server, com.mistaboom.essence_ascendance.gameplay.MovementOutcome fact) {
        if (!server.isSameThread() || server.overworld() == null || fact == null) return;
        var player = server.getPlayerList().getPlayer(fact.context().actor());
        if (!AttunementGameplay.eligible(player)) return;
        MOVEMENT.computeIfAbsent(server, ignored -> new MovementAchievementRules()).consume(fact,
                server.overworld().getGameTime(), true, id -> award(player, id));
    }

    public static void onSurvival(MinecraftServer server, com.mistaboom.essence_ascendance.gameplay.SurvivalOutcome fact) {
        if (!server.isSameThread() || server.overworld() == null || fact == null) return;
        var player = server.getPlayerList().getPlayer(fact.context().actor());
        boolean eligible = AttunementGameplay.eligible(player);
        // A committed death is a life boundary even if the actor died under an ineligible mode/admin scope.
        if (player == null || (!eligible && !(fact instanceof com.mistaboom.essence_ascendance.gameplay.SurvivalOutcome.Death)
                && !(fact instanceof com.mistaboom.essence_ascendance.gameplay.SurvivalOutcome.CeilingDebt))) return;
        var saved = EssenceSavedData.get(server);
        if (SURVIVAL.computeIfAbsent(server, ignored -> new SurvivalAchievementRules()).consume(fact,
                server.overworld().getGameTime(), eligible, saved.getPlayerData(fact.context().actor()).achievementProgress(),
                id -> award(player, id))) saved.setDirty();
    }

    public static void onProgression(MinecraftServer server, ProgressionOutcome fact) {
        if (!server.isSameThread() || server.overworld() == null || fact == null) return;
        var player = server.getPlayerList().getPlayer(fact.actor());
        if (!AttunementGameplay.eligible(player)) return;
        var saved = EssenceSavedData.get(server);
        if (PROGRESSION.computeIfAbsent(server, ignored -> new ProgressionAchievementRules()).consume(fact,
                server.overworld().getGameTime(), true, saved.getPlayerData(fact.actor()).achievementProgress(), id -> award(player, id))) saved.setDirty();
    }

    public static void onCompanion(MinecraftServer server, com.mistaboom.essence_ascendance.gameplay.CompanionOutcome fact) {
        if (!server.isSameThread() || server.overworld() == null || fact == null) return;
        var actor = fact.context().actor();
        var player = server.getPlayerList().getPlayer(actor);
        boolean eligible = AttunementGameplay.eligible(player);
        // Loaded cats can be damaged or change owners while their former owner is offline.
        if (!eligible && !(fact instanceof com.mistaboom.essence_ascendance.gameplay.CompanionOutcome.CatInvalidated)) return;
        var saved = EssenceSavedData.get(server);
        if (COMPANIONS.computeIfAbsent(server, ignored -> new CompanionAchievementRules()).consume(fact,
                server.overworld().getGameTime(), eligible, saved.getPlayerData(actor).achievementProgress(),
                id -> award(player, id))) saved.setDirty();
    }

    public static void onFarming(MinecraftServer server, com.mistaboom.essence_ascendance.gameplay.FarmingOutcome fact) {
        if (!server.isSameThread() || server.overworld() == null || fact == null) return;
        var player = server.getPlayerList().getPlayer(fact.context().actor());
        if (!AttunementGameplay.eligible(player)) return;
        var saved = EssenceSavedData.get(server);
        if (FARMING.computeIfAbsent(server, ignored -> new FarmingAchievementRules()).consume(fact,
                server.overworld().getGameTime(), true, saved.getPlayerData(player.getUUID()).achievementProgress(),
                id -> award(player, id))) saved.setDirty();
    }

    public static void onGathering(MinecraftServer server, com.mistaboom.essence_ascendance.gameplay.GatheringOutcome fact) {
        if (!server.isSameThread() || server.overworld() == null || fact == null) return;
        var player = server.getPlayerList().getPlayer(fact.context().actor());
        if (!AttunementGameplay.eligible(player)) return;
        var saved = EssenceSavedData.get(server);
        if (GATHERING.computeIfAbsent(server, ignored -> new GatheringAchievementRules()).consume(fact,
                server.overworld().getGameTime(), true, saved.getPlayerData(player.getUUID()).achievementProgress(),
                id -> award(player, id))) saved.setDirty();
    }

    private static void onEquipment(ServerPlayer player, EquipmentSnapshot equipment) {
        // Loading an offline player's inventory is not an observed gameplay state or a creative baseline.
        if (player.server.getPlayerList().getPlayer(player.getUUID()) != player
                || !com.mistaboom.essence_ascendance.config.EssenceConfigManager.authoritativeReady()) return;
        observeEquipment(player, equipment, AttunementGameplay.eligible(player));
    }
    private static void observeEquipment(ServerPlayer player, EquipmentSnapshot equipment, boolean eligible) {
        var saved = EssenceSavedData.get(player.server);
        var progress = saved.getPlayerData(player.getUUID()).achievementProgress();
        int[] ids = {61, 72, 139, 166};
        boolean[] matches = {EquipmentRules.fullArmor(equipment), EquipmentRules.transcendentSetAndWeapon(equipment),
                EquipmentRules.transcendentGlaive(equipment), EquipmentRules.swordAndShield(equipment)};
        for (int i = 0; i < ids.length; i++) if (progress.observeEquipment(key(ids[i]), matches[i], eligible)) award(player, ids[i]);
        saved.setDirty();
    }

    /** One first-install saved-tier backfill. Every later tier award needs a committed Ascension outcome. */
    public static void reconcile(ServerPlayer player) {
        if (!player.server.isSameThread() || player.server.getPlayerList().getPlayer(player.getUUID()) != player
                || !com.mistaboom.essence_ascendance.config.EssenceConfigManager.authoritativeReady()) return;
        if (!AttunementGameplay.eligible(player)) { baseline(player); return; }
        var saved = EssenceSavedData.get(player.server);
        var data = saved.getPlayerData(player.getUUID());
        data.achievementProgress().chapter(data.attunement().chapterId());
        boolean initial = !data.achievementProgress().tierObserved();
        data.achievementProgress().observeTier(Math.min(5, data.getTier().order()));
        if (initial) ProgressionAchievementRules.tierMilestones(data.getTier().order(), id -> award(player, id));
        EquipmentObservations.refresh(player);
        saved.setDirty();
    }

    /** Explicit admin mutations establish state checkpoints without inventing gameplay outcomes. */
    public static void baseline(ServerPlayer player) {
        var saved = EssenceSavedData.get(player.server);
        var data = saved.getPlayerData(player.getUUID());
        data.achievementProgress().chapter(data.attunement().chapterId());
        data.achievementProgress().observeTier(Math.min(5, data.getTier().order()));
        observeEquipment(player, EquipmentSnapshots.capture(player), false);
        saved.setDirty();
    }
    private static String key(int id) { return String.format(java.util.Locale.ROOT, "a%03d", id); }
    public static ResourceLocation resource(int id) {
        return ResourceLocation.fromNamespaceAndPath("essence_ascendance", "achievements/" + key(id));
    }
    private static boolean award(ServerPlayer player, int id) {
        var manager = player.server.getAdvancements();
        var advancement = manager == null ? null : manager.get(resource(id));
        if (advancement == null || player.getAdvancements().getOrStartProgress(advancement).isDone()) return false;
        return player.getAdvancements().award(advancement, "achieved");
    }

    private static boolean award(ServerPlayer player) {
        // Resolve each time: datapack reload replaces holders; never retain stale advancement objects.
        var manager = player.server.getAdvancements();
        var advancement = manager == null ? null : manager.get(EQUIVALENT_EXCHANGE);
        if (advancement == null || player.getAdvancements().getOrStartProgress(advancement).isDone()) return false;
        return player.getAdvancements().award(advancement, DISSOLVED);
    }
}
