package com.mistaboom.essence_ascendance.projectile;

import com.mistaboom.essence_ascendance.balance.runtime.RuntimeBalanceDefinition;
import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleBlockEntity;
import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleChannelService;
import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleContent;
import com.mistaboom.essence_ascendance.data.ReservoirService;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Actual server starts and pool transfers in the existing no-world registered-machine fixture. */
public final class NativeSinglePlayerChannelTest {
    private static int checks;

    public static void run() throws Exception {
        checks = 0;
        var previous = EssenceConfigManager.serverRuntime();
        var json = RuntimeBalanceDefinition.bootstrap().toJson();
        json.getAsJsonObject("crucible").addProperty("transferRatePerSecond", 120);
        EssenceConfigManager.install(RuntimeBalanceDefinition.fromJson(json));
        var f = new ProjectileNativeInterceptionTest.Fixture();
        NativeGuardOutcomeTest.registry(f);
        try {
            f.level.memoryMachines = new HashMap<>();
            f.level.memoryBlocks = new HashMap<>();
            var player = f.player;
            var teammate = f.player(Vec3.ZERO, "single_source_teammate");
            var outsider = f.player(Vec3.ZERO, "single_source_outsider");
            var team = f.saved.nativeMembership().create(player.getUUID(), "Single player source");
            var request = f.saved.nativeMembership().request(teammate.getUUID(), team, true);
            f.saved.nativeMembership().decide(player.getUUID(), request.token(), true);
            f.saved.reservoirs().credit(player.getUUID(), ReservoirService.wholeUnits(
                    Map.of(EssenceTypes.OFFENSE.id(), 100_000L), false), ReservoirService.NONE);
            var sources = new ArrayList<EssenceCrucibleBlockEntity>();
            for (int i = 0; i < 20; i++) {
                var source = place(f, new BlockPos(i % 5 - 2, 0, i / 5 + 1), player);
                sources.add(source);
                check(source.startChanneling(player), "Every valid source can replace the player's previous connection");
                check(activeSources(sources, player) == 1, "Twenty actual Crucibles never retain two connections to one player");
            }
            var current = sources.getLast();
            check(sources.getFirst().startChanneling(teammate) && current.isChanneling(player.getUUID()),
                    "Another member can independently receive from another Crucible");
            long before = available(f, player), teammateBefore = available(f, teammate);
            tick(f, sources, 20);
            check(available(f, player) - before == 120 && available(f, teammate) - teammateBefore == 120,
                    "Real shared-pool transfers allow one full physical-source budget per independent player");

            var foreign = place(f, new BlockPos(3, 0, 0), outsider);
            var distant = place(f, new BlockPos(1000, 0, 0), player);
            check(!foreign.startChanneling(player) && !distant.startChanneling(player)
                            && current.isChanneling(player.getUUID()),
                    "Unauthorized and out-of-range Starts preserve the existing valid connection");
            f.saved.getPlayerData(player.getUUID()).setCrucibleChannelSelection(0);
            check(!sources.getFirst().startChanneling(player) && current.isChanneling(player.getUUID()),
                    "None cannot start a replacement or silently discard the paused connection");
            f.saved.getPlayerData(player.getUUID()).setCrucibleChannelSelection(63);
            sources.getFirst().stopChanneling(player.getUUID());
            check(current.isChanneling(player.getUUID()), "Stopping the previous Crucible leaves its replacement active");

            before = available(f, player);
            for (int i = 0; i < 60; i++) {
                var selected = sources.get(1 + i % (sources.size() - 1));
                check(selected.startChanneling(player) && selected.startChanneling(player)
                                && activeSources(sources, player) == 1,
                        "Rapid switching and duplicate Starts retain one real connection");
                tick(f, sources, 1);
            }
            check(available(f, player) - before == 360,
                    "Changing real Crucibles every tick cannot bypass the configured player channel throughput");
            before = available(f, player); teammateBefore = available(f, teammate);
            check(sources.getFirst().startChanneling(player), "Players can choose the same physical Crucible");
            tick(f, sources, 20);
            check(available(f, player) - before == 60 && available(f, teammate) - teammateBefore == 60,
                    "Two members on one real Crucible still split its physical bandwidth");
            EssenceCrucibleChannelService.stopForPlayer(player);
            check(activeSources(sources, player) == 0 && sources.getFirst().isChanneling(teammate.getUUID()),
                    "Logout removes the player's sole source without affecting another member");
            System.out.println("NativeSinglePlayerChannelTest: " + checks + " assertions passed");
        } finally {
            f.close();
            EssenceConfigManager.install(previous);
        }
    }

    private static EssenceCrucibleBlockEntity place(ProjectileNativeInterceptionTest.Fixture f, BlockPos pos, ServerPlayer player) {
        var machine = new EssenceCrucibleBlockEntity(pos, EssenceCrucibleContent.ESSENCE_CRUCIBLE.get().defaultBlockState());
        machine.setLevel(f.level);
        f.level.memoryMachines.put(pos, machine);
        f.level.memoryBlocks.put(pos, machine.getBlockState());
        machine.bindOnPlacement(player);
        return machine;
    }

    private static long activeSources(List<EssenceCrucibleBlockEntity> sources, ServerPlayer player) {
        return sources.stream().filter(source -> source.isChanneling(player.getUUID())).count();
    }

    private static long available(ProjectileNativeInterceptionTest.Fixture f, ServerPlayer player) {
        return EssenceTypes.ORDERED.stream().mapToLong(type -> f.saved.getPlayerData(player.getUUID()).getAvailable(type)).sum();
    }

    private static void tick(ProjectileNativeInterceptionTest.Fixture f, List<EssenceCrucibleBlockEntity> sources, int ticks) {
        for (int i = 0; i < ticks; i++) {
            f.level.tick++;
            for (var source : sources)
                EssenceCrucibleBlockEntity.serverTick(f.level, source.getBlockPos(), source.getBlockState(), source);
            EssenceCrucibleChannelService.tick(f.server, f.level.getGameTime());
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
