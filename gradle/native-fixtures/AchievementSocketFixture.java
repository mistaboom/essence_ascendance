package com.mistaboom.essence_ascendance.achievementfixture;

import com.mistaboom.essence_ascendance.achievement.AchievementService;
import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleBlockEntity;
import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleContent;
import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleMenu;
import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import dev.architectury.event.events.common.TickEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import java.nio.file.*;
import java.util.*;

/** External opt-in fixture: actual production server, two sockets, native menu packet and toast. */
public final class AchievementSocketFixture {
    private static final Path ROOT = fixtureDirectory();
    private static final BlockPos POS = new BlockPos(0, 99, 2);
    private static final long START = System.nanoTime();
    private static int phase, ticks, checks;
    private static EssenceCrucibleBlockEntity machine;
    private static ServerPlayer firstOperator;
    private static Object oldManager, oldHolder;
    private AchievementSocketFixture() { }
    private static Path fixtureDirectory() {
        String configured = System.getProperty("essence.achievement.socketDirectory");
        if (configured == null || configured.isBlank())
            throw new IllegalStateException("Explicit disposable achievement socket directory required");
        var directory = Path.of(configured).normalize();
        if (!directory.isAbsolute() || !Files.isDirectory(directory))
            throw new IllegalStateException("Existing absolute disposable achievement socket directory required");
        return directory;
    }
    public static void server() {
        TickEvent.SERVER_POST.register(server -> { try { tick(server); } catch (Throwable error) { fail(error); } });
    }
    public static void client() { Client.install(); }
    public static void toastRendered(Object toast) { Client.toastRendered(toast); }
    private static void tick(MinecraftServer server) throws Exception {
        timeout(); ticks++;
        var a = server.getPlayerList().getPlayerByName("EA_A");
        var b = server.getPlayerList().getPlayerByName("EA_B");
        if (phase == 3) {
            if (b == null && exists("EA_B.disconnected")) { write("reconnect", "ready"); phase = 4; }
            return;
        }
        if (a == null || b == null) return;
        if ((phase == 1 || phase == 7) && b.containerMenu instanceof EssenceCrucibleMenu
                && b.containerMenu.getCarried().is(Items.IRON_INGOT) && b.getInventory().getItem(0).isEmpty()) {
            String acknowledgement = "pickup-ack-" + (phase == 1 ? 0 : 1);
            if (!exists(acknowledgement)) write(acknowledgement, Integer.toString(b.containerMenu.getStateId()));
        }
        if (phase == 0 && exists("EA_A.joined-0") && exists("EA_B.joined-0")) {
            check(!a.getUUID().equals(b.getUUID()) && a.connection != b.connection, "Two distinct native socket players");
            var level = server.overworld();
            for (int x = -3; x < 5; x++) for (int z = -3; z < 6; z++) level.setBlockAndUpdate(new BlockPos(x, 98, z), Blocks.STONE.defaultBlockState());
            command(server, "tp EA_A 0.5 99 0.5"); command(server, "tp EA_B 2.5 99 0.5");
            a.setGameMode(GameType.SURVIVAL); b.setGameMode(GameType.SURVIVAL);
            var saved = EssenceSavedData.get(server);
            var team = saved.nativeMembership().create(a.getUUID(), "Achievement socket fixture");
            var request = saved.nativeMembership().request(b.getUUID(), team, true);
            saved.nativeMembership().decide(a.getUUID(), request.token(), true);
            saved.getPlayerData(b.getUUID()).setTier(AscendanceTiers.TRANSCENDENT); AchievementService.baseline(b);
            level.setBlockAndUpdate(POS, EssenceCrucibleContent.ESSENCE_CRUCIBLE.get().defaultBlockState());
            machine = (EssenceCrucibleBlockEntity) level.getBlockEntity(POS);
            check(machine != null && machine.bindOnPlacement(a), "Native machine belongs to other team member");
            machine.setProcessingEnabled(b, false);
            phase = 10; ticks = 0;
        } else if (phase == 10 && exists("EA_A.positioned") && exists("EA_B.positioned")) {
            b.getInventory().setItem(0, new ItemStack(Items.IRON_INGOT));
            b.openMenu(machine);
            check(b.containerMenu instanceof EssenceCrucibleMenu, "Server opens actual native Crucible menu");
            check(b.containerMenu.getCarried().isEmpty(), "Native menu begins with an empty cursor and synchronized ordinary inventory");
            write("phase", "operate"); phase = 1; ticks = 0;
        } else if (phase == 1 && !machine.getItem(0).isEmpty()) {
            check(b.getUUID().equals(machine.machineBinding().operator()), "Real remote menu packet records capped operator: operator="
                    + machine.machineBinding().operator() + " actor=" + b.getUUID() + " eligible="
                    + com.mistaboom.essence_ascendance.attunement.AttunementGameplay.eligible(b)
                    + " use=" + machine.canPlayerUse(b) + " position=" + b.position() + " alive=" + b.isAlive()
                    + " creative=" + b.hasInfiniteMaterials());
            check(!done(a) && !done(b), "Input alone does not award either player");
            machine.setProcessingEnabled(b, true); phase = 2; ticks = 0;
        } else if (phase == 2 && done(b) && exists("EA_B.toast")) {
            check(machine.getItem(0).isEmpty() && machine.totalStoredEssence().signum() > 0, "Actual server ticks commit consumption and positive Essence");
            check(!done(a) && !exists("EA_A.toast"), "Machine owner/team observer receives neither completion nor toast");
            check(exists("EA_B.clicked"), "Survival operation began at a real client inventory click");
            b.closeContainer(); b.getAdvancements().save(); a.getAdvancements().save();
            firstOperator = b;
            write("disconnect", "operator"); phase = 3;
        } else if (phase == 4 && b != firstOperator && exists("EA_B.joined-1")) {
            check(b.getUUID().equals(firstOperator.getUUID()) && b.connection != firstOperator.connection,
                    "Reconnect retains UUID with a new socket and native player body");
            check(done(b) && !done(a), "Native achievement completion survives genuine socket reconnect with actor isolation");
            var nether = server.getLevel(net.minecraft.world.level.Level.NETHER);
            nether.getChunkAt(new BlockPos(0, 98, 0));
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
                nether.setBlockAndUpdate(new BlockPos(x, 98, z), Blocks.STONE.defaultBlockState());
                for (int y = 99; y <= 103; y++) nether.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
            }
            command(server, "execute in minecraft:the_nether run tp EA_B 0 100 0");
            phase = 5; ticks = 0;
        } else if (phase == 5 && b.level() != server.overworld() && ticks > 40) {
            check(done(b) && !done(a), "Native dimension transfer preserves only the operator completion");
            command(server, "execute in minecraft:overworld run tp EA_B 2 99 0");
            oldManager = server.getAdvancements(); oldHolder = server.getAdvancements().get(AchievementService.EQUIVALENT_EXCHANGE);
            command(server, "reload"); phase = 6; ticks = 0;
        } else if (phase == 6 && server.getAdvancements() != oldManager && ticks > 40) {
            check(server.getAdvancements().get(AchievementService.EQUIVALENT_EXCHANGE) != oldHolder,
                    "Actual ReloadCommand installs new advancement manager and holder");
            check(done(b) && !done(a), "Full datapack reload preserves earned operator completion and owner isolation");
            command(server, "advancement revoke EA_B only essence_ascendance:achievements/a022");
            check(!done(b), "Native revoke after full datapack reload is authoritative");
            machine.setProcessingEnabled(b, false);
            b.getInventory().setItem(0, new ItemStack(Items.IRON_INGOT)); b.openMenu(machine);
            write("phase", "operate-again"); phase = 7;
        } else if (phase == 7 && !machine.getItem(0).isEmpty()) {
            check(b.getUUID().equals(machine.machineBinding().operator()) && !done(b),
                    "Fresh remote input after reload/revoke is pending actual commit");
            machine.setProcessingEnabled(b, true); phase = 8;
        } else if (phase == 8 && done(b)) {
            check(machine.getItem(0).isEmpty() && !done(a), "Fresh actual commit re-earns using current datapack holder without owner award");
            b.closeContainer();
            // Separate intentional administrator display wiring checks. These are
            // never described as earned survival actions or emitted gameplay facts.
            write("display-wiring", "synthetic native administrator grant/revoke");
            command(server, "advancement grant EA_A only essence_ascendance:water_skiing");
            command(server, "advancement grant EA_A from essence_ascendance:achievements/a022");
            int completed = 0;
            for (var id : com.mistaboom.essence_ascendance.achievement.AchievementPresentation.selectedIds())
                if (a.getAdvancements().getOrStartProgress(server.getAdvancements().get(id)).isDone()) completed++;
            check(completed == 69, "Native grant-from command completes all 69 selections for display wiring");
            command(server, "advancement revoke EA_A from essence_ascendance:achievements/a022");
            for (var id : com.mistaboom.essence_ascendance.achievement.AchievementPresentation.selectedIds())
                check(!a.getAdvancements().getOrStartProgress(server.getAdvancements().get(id)).isDone(), "Native revoke-from clears selection " + id);
            // Resolve legacy through the actual manager without version-specific identifier APIs.
            var legacy = server.getAdvancements().getAllAdvancements().stream().filter(h -> h.id().toString().equals("essence_ascendance:water_skiing")).findFirst().orElseThrow();
            check(a.getAdvancements().getOrStartProgress(legacy).isDone() && done(b), "Selected subtree revoke preserves legacy and the other player");
            write("complete", "PASS " + checks); phase = 9;
            System.out.println("AchievementSocketFixture: SERVER PASS " + checks + " assertions; real menu C2S, committed dissolution, capped operator isolation, toast, reconnect, dimension transfer, full datapack reload and fresh re-earn; separate synthetic 69-selection admin wiring");
        }
    }
    private static boolean done(ServerPlayer p) {
        var server = ((net.minecraft.server.level.ServerLevel)p.level()).getServer();
        return p.getAdvancements().getOrStartProgress(server.getAdvancements().get(AchievementService.EQUIVALENT_EXCHANGE)).isDone();
    }
    private static void command(MinecraftServer server, String command) { server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command); }
    private static boolean exists(String name) { return Files.isRegularFile(ROOT.resolve(name)); }
    private static void write(String name, String value) throws Exception { Files.writeString(ROOT.resolve(name), value + "\n"); }
    private static void check(boolean result, String message) { checks++; if (!result) throw new AssertionError(message); }
    private static void timeout() { if (System.nanoTime() - START > 600_000_000_000L) throw new AssertionError("Achievement socket fixture timed out phase=" + phase); }
    private static void fail(Throwable error) { error.printStackTrace(); try { write("failed-" + ProcessHandle.current().pid(), error.toString()); } catch (Exception ignored) { } Runtime.getRuntime().halt(1); }
    private static final class Client {
        private static boolean joined, clicked, disconnecting;
        private static int clickStage, pickupTicks;
        private static String name;
        private static final int GENERATION = Integer.getInteger("essence.achievement.socketGeneration", 0);
        static void install() { ClientTickEvent.CLIENT_POST.register(client -> { try { tick(client); } catch (Throwable error) { fail(error); } }); }
        static void tick(net.minecraft.client.Minecraft client) throws Exception {
            timeout(); client.options.pauseOnLostFocus = false;
            if (disconnecting) {
                if (client.player != null || client.level != null) return;
                write(name + (GENERATION == 0 && name.equals("EA_B") ? ".disconnected" : ".finished"), "native socket disconnected");
                System.out.println("AchievementSocketFixture: CLIENT PASS " + name + " generation=" + GENERATION);
                client.stop(); return;
            }
            if (client.player == null || client.level == null || client.getConnection() == null) return;
            name = client.player.getName().getString();
            if (!joined) {
                if (client.screen != null) return;
                check(client.getSingleplayerServer() == null, "Actual dedicated-server socket client");
                write(name + ".joined-" + GENERATION, client.player.getUUID().toString()); joined = true;
            }
            if (GENERATION == 0 && client.player.position().distanceToSqr(new net.minecraft.world.phys.Vec3(name.equals("EA_A") ? .5 : 2.5, 99, .5)) < .1
                    && !exists(name + ".positioned")) write(name + ".positioned", "native teleport received at safe platform");
            if (name.equals("EA_B") && !clicked && exists("phase")
                    && client.player.containerMenu instanceof EssenceCrucibleMenu menu) {
                // The enum was renamed in 26.1. Select the native five-argument click
                // API by its types, not mapped/obfuscated field or method names.
                var method = Arrays.stream(client.gameMode.getClass().getMethods()).filter(m -> {
                    var t = m.getParameterTypes(); return t.length == 5 && t[0] == int.class && t[1] == int.class
                            && t[2] == int.class && t[3].isEnum() && net.minecraft.world.entity.player.Player.class.isAssignableFrom(t[4]);
                }).findFirst().orElseThrow();
                Object pickup = Arrays.stream(method.getParameterTypes()[3].getEnumConstants()).filter(v -> ((Enum<?>)v).name().equals("PICKUP")).findFirst().orElseThrow();
                int hotbar = EssenceCrucibleMenu.PLAYER_INVENTORY_END - 9;
                if (clickStage == 0 && menu.getCarried().isEmpty() && menu.getSlot(hotbar).getItem().is(Items.IRON_INGOT)) {
                    method.invoke(client.gameMode, menu.containerId, hotbar, 0, pickup, client.player);
                    clickStage = 1; pickupTicks = 0;
                    write(name + ".picked", "native inventory-to-cursor click sent");
                } else if (clickStage == 1 && ++pickupTicks >= 5 && exists("pickup-ack-" + GENERATION)
                        && menu.getStateId() == Integer.parseInt(Files.readString(ROOT.resolve("pickup-ack-" + GENERATION)).trim())
                        && menu.getCarried().is(Items.IRON_INGOT)) {
                    method.invoke(client.gameMode, menu.containerId, 0, 0, pickup, client.player);
                    clicked = true; write(name + ".clicked", "native cursor-to-machine click sent");
                }
            }
            if (exists("complete") || name.equals("EA_B") && GENERATION == 0 && exists("disconnect")) {
                client.getConnection().getConnection().disconnect(net.minecraft.network.chat.Component.literal("Achievement fixture complete"));
                disconnecting = true;
            }
        }
        static void toastRendered(Object toast) {
            if (name == null) return;
            try {
                for (var field : toast.getClass().getDeclaredFields()) {
                    if (field.getType() != net.minecraft.advancements.AdvancementHolder.class) continue;
                    field.setAccessible(true);
                    var holder = (net.minecraft.advancements.AdvancementHolder) field.get(toast);
                    if (holder.id().equals(AchievementService.EQUIVALENT_EXCHANGE) && !exists("display-wiring") && !exists(name + ".toast")) {
                        write(name + ".toast", "native advancement toast rendered " + holder.id());
                        System.out.println("AchievementSocketFixture: TOAST RENDER PASS " + name + " " + holder.id());
                    }
                }
            } catch (Throwable error) { fail(error); }
        }
    }
}
