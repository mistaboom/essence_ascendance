package com.mistaboom.essence_ascendance.crucible;

import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.infuser.EssenceInfuserBlockEntity;
import com.mistaboom.essence_ascendance.machine.MachineLink;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

/** One server scheduler shared by real player and Infuser consumers. Each player selects one
 * source at a time; multiple players and Infusers still share a source's physical bandwidth. */
public final class EssenceCrucibleChannelService {
    public record Receiver(UUID player, MachineLink.Source machine) {
        public Receiver {
            if ((player == null) == (machine == null)) throw new IllegalArgumentException("One receiver identity required");
        }
    }
    private static final Map<MinecraftServer, ConcurrentChannelScheduler<MachineLink.Source, Receiver>> SERVERS = new IdentityHashMap<>();
    private static boolean initialized;
    private EssenceCrucibleChannelService() {}
    public static void init() {
        if (initialized) return;
        initialized = true;
        TickEvent.SERVER_POST.register(server -> tick(server, server.getTickCount()));
        LifecycleEvent.SERVER_STOPPED.register(EssenceCrucibleChannelService::clear);
    }
    private static ConcurrentChannelScheduler<MachineLink.Source, Receiver> scheduler(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("Channel scheduling requires the server thread");
        return SERVERS.computeIfAbsent(server, ignored -> new ConcurrentChannelScheduler<>());
    }
    private static MachineLink.Source address(net.minecraft.world.level.block.entity.BlockEntity machine) {
        return new MachineLink.Source(machine.getLevel().dimension().location(), machine.getBlockPos());
    }
    private static boolean loaded(net.minecraft.world.level.block.entity.BlockEntity machine) {
        return machine.getLevel() instanceof ServerLevel level && !machine.isRemoved()
                && level.hasChunkAt(machine.getBlockPos()) && level.getBlockEntity(machine.getBlockPos()) == machine;
    }
    public static void sourceLoaded(EssenceCrucibleBlockEntity source) {
        if (!(source.getLevel() instanceof ServerLevel level)) return;
        scheduler(level.getServer()).source(address(source), source,
                () -> source.fundingActor() == null ? 0 : source.structureStats().transferRatePerSecond(), () -> loaded(source));
    }
    static boolean claim(ServerPlayer player, EssenceCrucibleBlockEntity source) {
        if (!source.validPlayerChannel(player)) return false;
        sourceLoaded(source);
        var scheduler = scheduler(player.server);
        long binding = source.machineBinding().revision();
        return scheduler.registerExclusive(address(source), new Receiver(player.getUUID(), null), player, binding,
                new ConcurrentChannelScheduler.Consumer() {
                    private long moneyRevision = -1, personalRevision = -1, cachedDemand;
                    private int selection = -1;
                    public boolean valid() { return loaded(source) && source.machineBinding().revision() == binding
                            && source.validPlayerChannel(player); }
                    public long demand() {
                        var saved = EssenceSavedData.get(player.server);
                        var data = saved.getPlayerData(player.getUUID());
                        long money = saved.reservoirs().revision(), personal = data.nexusRevision();
                        int mask = data.crucibleChannelSelection();
                        if (money != moneyRevision || personal != personalRevision || mask != selection) {
                            cachedDemand = source.playerChannelDemand(player);
                            moneyRevision = money; personalRevision = personal; selection = mask;
                        }
                        return cachedDemand;
                    }
                    public long ratePerSecond() { return -1; }
                    public long deliver(long grant) { return valid() ? source.deliverPlayer(player, grant) : 0; }
                }) != null;
    }
    public static void registerInfuser(EssenceInfuserBlockEntity infuser, EssenceCrucibleBlockEntity source) {
        if (!(infuser.getLevel() instanceof ServerLevel level)) return;
        sourceLoaded(source);
        var scheduler = scheduler(level.getServer());
        long revision = infuser.channelRevision(), binding = source.machineBinding().revision();
        scheduler.register(address(source), new Receiver(null, address(infuser)), infuser, revision,
                new ConcurrentChannelScheduler.Consumer() {
                    public boolean valid() { return loaded(infuser) && loaded(source) && infuser.channelRevision() == revision
                            && source.machineBinding().revision() == binding && address(source).equals(infuser.linkedSource()); }
                    public long demand() { return infuser.channelDemand(); }
                    public long ratePerSecond() { return infuser.infusionThroughputPerSecond(); }
                    public long deliver(long grant) { return valid() ? infuser.deliverChannelGrant(grant) : 0; }
                });
    }
    public static List<UUID> players(EssenceCrucibleBlockEntity source) {
        if (!(source.getLevel() instanceof ServerLevel level)) return List.of();
        var key = address(source);
        if (!scheduler(level.getServer()).sourceCurrent(key, source)) return List.of();
        return scheduler(level.getServer()).sessions().stream().filter(s -> s.key().source().equals(key))
                .map(s -> s.key().receiver().player()).filter(Objects::nonNull).toList();
    }
    public static boolean supplyingInfuser(EssenceCrucibleBlockEntity source) {
        if (!(source.getLevel() instanceof ServerLevel level)) return false;
        var scheduler = scheduler(level.getServer());
        return scheduler.sessions().stream().anyMatch(s -> s.key().source().equals(address(source))
                && s.key().receiver().machine() != null && scheduler.demand(s) > 0);
    }
    public static boolean isChanneling(EssenceCrucibleBlockEntity source, UUID player) { return players(source).contains(player); }
    public record ChannelFeedback(int registered, int consuming, long allocated, long actual, ChannelReason reason) {}
    public static ChannelFeedback feedback(EssenceCrucibleBlockEntity source, UUID player) {
        if (!(source.getLevel() instanceof ServerLevel level))
            return new ChannelFeedback(0, 0, 0, 0, ChannelReason.SOURCE_UNAVAILABLE);
        var scheduler = scheduler(level.getServer());
        var sessions = scheduler.sessions().stream().filter(s -> s.key().source().equals(address(source))).toList();
        int consuming = (int) sessions.stream().filter(s -> scheduler.feedback(s).consuming()).count();
        var own = sessions.stream().filter(s -> player.equals(s.key().receiver().player())).findFirst().orElse(null);
        if (own == null) return new ChannelFeedback(sessions.size(), consuming, 0, 0, ChannelReason.STOPPED);
        var data = EssenceSavedData.get(level.getServer()).getPlayerData(player);
        var live = level.getServer().getPlayerList().getPlayer(player);
        var f = scheduler.feedback(own);
        ChannelReason reason;
        if (live == null || !source.validPlayerChannel(live)) reason = ChannelReason.AUTHORIZATION_REVOKED;
        else if (data.crucibleChannelSelection() == 0) reason = ChannelReason.NO_SELECTION;
        else if (scheduler.demand(own) == 0) {
            boolean room = false;
            for (int i = 0; i < EssenceCrucibleEssences.ORDERED.size(); i++)
                if (CrucibleChannelSelection.selected(data.crucibleChannelSelection(), i)
                        && data.getAvailable(EssenceCrucibleEssences.ORDERED.get(i)) < Long.MAX_VALUE) room = true;
            reason = room ? ChannelReason.WAITING_ESSENCE : ChannelReason.RECEIVER_CAPPED;
        } else reason = consuming > 1 ? ChannelReason.SHARING_BANDWIDTH : ChannelReason.RECEIVING;
        return new ChannelFeedback(sessions.size(), consuming, f.allocated(), f.transferred(), reason);
    }
    public record PlayerLink(MachineLink.Source source, ChannelFeedback feedback) {}
    public static ChannelFeedback feedback(EssenceInfuserBlockEntity infuser) {
        if (!(infuser.getLevel() instanceof ServerLevel level) || infuser.linkedSource() == null)
            return new ChannelFeedback(0, 0, 0, 0, ChannelReason.SOURCE_UNAVAILABLE);
        var scheduler = scheduler(level.getServer());
        var sessions = scheduler.sessions().stream().filter(s -> s.key().source().equals(infuser.linkedSource())).toList();
        int consuming = (int) sessions.stream().filter(s -> scheduler.feedback(s).consuming()).count();
        var own = sessions.stream().filter(s -> address(infuser).equals(s.key().receiver().machine())).findFirst().orElse(null);
        var f = own == null ? new ConcurrentChannelScheduler.Feedback(0, 0, 0, false) : scheduler.feedback(own);
        var saved = EssenceSavedData.get(level.getServer());
        ChannelReason reason;
        if (!com.mistaboom.essence_ascendance.team.MembershipAuthority.ready(saved.reservoirs().readiness())) reason = ChannelReason.AUTHORIZATION_UNRESOLVED;
        else if (infuser.machineBinding().operator() != null && !infuser.machineBinding().operatorValid(saved)) reason = ChannelReason.AUTHORIZATION_REVOKED;
        else if (infuser.statusCode() == EssenceInfuserBlockEntity.STATUS_PROCESSING && infuser.infusionThroughputPerSecond() == 0) reason = ChannelReason.RECEIVER_CAPPED;
        else reason = switch (infuser.statusCode()) {
            case EssenceInfuserBlockEntity.STATUS_OUTPUT_BLOCKED -> ChannelReason.OUTPUT_BLOCKED;
            case EssenceInfuserBlockEntity.STATUS_RECIPIENT_REQUIRED -> ChannelReason.INVALID_RECIPIENT;
            case EssenceInfuserBlockEntity.STATUS_UNLINKED -> ChannelReason.SOURCE_UNAVAILABLE;
            case EssenceInfuserBlockEntity.STATUS_INSUFFICIENT_SOURCE -> ChannelReason.WAITING_ESSENCE;
            case EssenceInfuserBlockEntity.STATUS_PROCESSING -> consuming > 1 ? ChannelReason.SHARING_BANDWIDTH : ChannelReason.RECEIVING;
            default -> ChannelReason.STOPPED;
        };
        return new ChannelFeedback(sessions.size(), consuming, f.allocated(), f.transferred(), reason);
    }
    public static List<PlayerLink> links(ServerPlayer player) {
        var scheduler = SERVERS.get(player.server);
        if (scheduler == null) return List.of();
        List<PlayerLink> result = new ArrayList<>();
        for (var session : scheduler.sessions()) {
            if (!player.getUUID().equals(session.key().receiver().player())) continue;
            var address = session.key().source();
            var level = player.server.getLevel(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION, address.dimension()));
            if (level != null && level.hasChunkAt(address.pos()) && level.getBlockEntity(address.pos()) instanceof EssenceCrucibleBlockEntity source
                    && source.validPlayerChannel(player)) result.add(new PlayerLink(address, feedback(source, player.getUUID())));
        }
        return List.copyOf(result);
    }
    public static boolean isChanneling(UUID player) {
        return player != null && SERVERS.values().stream().anyMatch(s -> s.sessions().stream().anyMatch(e -> player.equals(e.key().receiver().player())));
    }
    public static void stop(EssenceCrucibleBlockEntity source, UUID player) {
        if (!(source.getLevel() instanceof ServerLevel level)) return;
        var scheduler = scheduler(level.getServer());
        if (!scheduler.sourceCurrent(address(source), source)) return;
        for (var session : scheduler.sessions())
            if (session.key().source().equals(address(source)) && player.equals(session.key().receiver().player())) scheduler.remove(session);
    }
    public static void stopPlayers(EssenceCrucibleBlockEntity source) { for (UUID player : players(source)) stop(source, player); }
    public static void stopForPlayer(ServerPlayer player) {
        var scheduler = SERVERS.get(player.server);
        if (scheduler == null) return;
        scheduler.removeReceiver(new Receiver(player.getUUID(), null), player);
    }
    public static void removeInfuser(EssenceInfuserBlockEntity infuser) {
        if (!(infuser.getLevel() instanceof ServerLevel level)) return;
        var scheduler = SERVERS.get(level.getServer());
        if (scheduler == null) return;
        scheduler.removeReceiver(new Receiver(null, address(infuser)), infuser);
    }
    public static void removeSource(EssenceCrucibleBlockEntity source) {
        if (source.getLevel() instanceof ServerLevel level) scheduler(level.getServer()).removeSource(address(source), source);
    }
    public static void tick(MinecraftServer server, long tick) {
        var scheduler = SERVERS.get(server);
        if (scheduler != null) scheduler.tick(tick);
    }
    public static void clear(MinecraftServer server) { SERVERS.remove(server); }
}
