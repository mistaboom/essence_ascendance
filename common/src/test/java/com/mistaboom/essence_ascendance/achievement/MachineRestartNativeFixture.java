package com.mistaboom.essence_ascendance.achievement;

import com.mistaboom.essence_ascendance.crucible.*;
import com.mistaboom.essence_ascendance.data.*;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import com.mistaboom.essence_ascendance.infuser.*;
import com.mistaboom.essence_ascendance.machine.MachineLink;
import com.mistaboom.essence_ascendance.pylon.*;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

import java.lang.management.ManagementFactory;
import java.math.BigInteger;
import java.nio.file.*;
import java.util.*;

/** Opt-in two-process proof. The marker stores expected scalar values only; native chunks
 * and EssenceSavedData exclusively supply the persisted job, credit and money on resume. */
public final class MachineRestartNativeFixture {
    private static UUID ACTOR;
    private static final BlockPos SOURCE = new BlockPos(96, 120, 96), MACHINE = SOURCE.offset(2, 0, 0);
    private static int checks;
    private MachineRestartNativeFixture() { }

    @SuppressWarnings("unchecked")
    public static void run(ServerLevel level, String stage) throws Exception {
        if (!Boolean.getBoolean("essence.achievement.fixture") || !Set.of("prepare", "resume").contains(stage))
            throw new IllegalStateException("Explicit valid restart fixture stage required");
        checks = 0;
        Path marker = Path.of(Objects.requireNonNull(System.getProperty("essence.achievement.restart.marker"))).toAbsolutePath().normalize();
        // Every prepare owns a fresh fixture identity: old completions or saturated
        // partials from an earlier run cannot hide a broken source hook. Resume reads
        // only identity from the marker; all gameplay data still loads from the world.
        if (stage.equals("prepare")) ACTOR = UUID.randomUUID();
        else {
            check(Files.isRegularFile(marker), "Resume requires a prepare-stage identity marker");
            Properties expected = new Properties();
            try (var input = Files.newInputStream(marker)) { expected.load(input); }
            ACTOR = UUID.fromString(expected.getProperty("actor"));
        }
        var playerList = level.getServer().getPlayerList();
        check(playerList.getPlayers().isEmpty(), "Cold restart fixture requires no real connected players");
        var playersField = PlayerList.class.getDeclaredField("players"); playersField.setAccessible(true);
        var idsField = PlayerList.class.getDeclaredField("playersByUUID"); idsField.setAccessible(true);
        var online = (List<ServerPlayer>) playersField.get(playerList);
        var byId = (Map<UUID, ServerPlayer>) idsField.get(playerList);
        var actor = player(level); actor.setPos(97, 120, 97);
        online.add(actor); byId.put(ACTOR, actor);
        try {
            level.getChunkAt(SOURCE); level.getChunkAt(MACHINE);
            if (stage.equals("prepare")) prepare(level, actor, marker);
            else resume(level, actor, marker);
        } finally {
            actor.containerMenu = actor.inventoryMenu; online.remove(actor); byId.remove(ACTOR);
            EssenceCrucibleChannelService.clear(level.getServer());
        }
        System.out.println("MachineRestartNativeFixture: stage=" + stage + ", " + checks
                + " assertions passed; JVM=" + ProcessHandle.current().pid() + "; native world normal save/shutdown follows");
    }

    private static void prepare(ServerLevel level, ServerPlayer actor, Path marker) throws Exception {
        level.setBlock(MACHINE, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(SOURCE, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(SOURCE, EssenceCrucibleContent.ESSENCE_CRUCIBLE.get().defaultBlockState(), 3);
        level.setBlock(MACHINE, EssenceInfuserContent.ESSENCE_INFUSER.get().defaultBlockState(), 3);
        var source = (EssenceCrucibleBlockEntity) level.getBlockEntity(SOURCE);
        var machine = (EssenceInfuserBlockEntity) level.getBlockEntity(MACHINE);
        check(source != null && machine != null && source.bindOnPlacement(actor) && machine.bindOnPlacement(actor),
                "Prepare places and binds real native Crucible and Infuser");
        check(!actor.getAdvancements().getOrStartProgress(level.getServer().getAdvancements()
                        .get(AchievementService.EQUIVALENT_EXCHANGE)).isDone()
                        && EssenceSavedData.get(level.getServer()).getPlayerData(ACTOR).achievementProgress().empty(),
                "Fresh prepare actor has neither inherited completion nor durable partials");
        // A real menu insertion and committed dissolution supply both native completion
        // and lifetime partial facts. The marker only compares them after cold loading.
        var dissolution = new EssenceCrucibleMenu(93, actor.getInventory(), source);
        actor.containerMenu = dissolution;
        dissolution.setCarried(new ItemStack(net.minecraft.world.item.Items.IRON_INGOT));
        dissolution.clicked(0, 0, ClickType.PICKUP, actor);
        check(dissolution.getCarried().isEmpty() && ACTOR.equals(source.machineBinding().operator()),
                "Prepare inserts supported input through the real survival menu");
        source.setProcessingEnabled(actor, true);
        for (int i = 0; i <= source.structureStats().dissolutionTicksPerItem(); i++)
            EssenceCrucibleBlockEntity.serverTick(level, SOURCE, source.getBlockState(), source);
        var exchange = level.getServer().getAdvancements().get(AchievementService.EQUIVALENT_EXCHANGE);
        check(source.getItem(0).isEmpty() && source.totalStoredEssence().signum() > 0
                        && actor.getAdvancements().getOrStartProgress(exchange).isDone(),
                "Prepare commits actual dissolution before saving completed and partial progress");
        var partial = EssenceSavedData.get(level.getServer()).getPlayerData(ACTOR).achievementProgress().save();
        check(partial.toString().contains("minecraft:iron_ingot"),
                "Actual dissolution creates the durable consumed-item fact");
        var legacy = level.getServer().getAdvancements().get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("essence_ascendance", "water_skiing"));
        // This deliberate legacy grant seeds an old-world compatibility state only.
        actor.getAdvancements().award(legacy, "travel");
        actor.getAdvancements().save();
        var focus = new ItemStack(EssencePylonContent.ESSENCE_FOCUS.get());
        EssenceFocusData.setTier(focus, EssenceFocusTier.TRANSCENDENT); machine.setItem(EssenceInfuserBlockEntity.FOCUS_SLOT, focus);
        check(machine.setLink(actor, MachineLink.Mode.LOCKED, new MachineLink.Source(level.dimension().location(), SOURCE)),
                "Prepare locks real physical Crucible source");
        EssenceInfuserBlockEntity.serverTick(level, MACHINE, machine.getBlockState(), machine);
        var menu = new EssenceInfuserMenu(94, actor.getInventory(), machine); actor.containerMenu = menu;
        menu.setCarried(new ItemStack(EssenceInfuserContent.LATENT_NUGGET.get()));
        menu.clicked(EssenceInfuserBlockEntity.INPUT_SLOT, 0, ClickType.PICKUP, actor);
        machine.setProcessingEnabled(actor, true);
        check(menu.getCarried().isEmpty() && ACTOR.equals(machine.machineBinding().operator()), "Prepare uses successful native survival input and Start");
        var advancement = actor.serverLevel().getServer().getAdvancements().get(AchievementService.resource(49));
        check(advancement != null, "Native working Infuser advancement exists");
        actor.getAdvancements().revoke(advancement, "achieved"); actor.getAdvancements().save();
        long required = machine.sourceRequired(); check(required > 1, "Restart job has a real nontrivial payment requirement");
        var values = new LinkedHashMap<net.minecraft.resources.ResourceLocation, BigInteger>();
        for (var essence : EssenceTypes.ORDERED) values.put(essence.id(), BigInteger.valueOf(required).multiply(ReservoirService.SCALE));
        EssenceSavedData.get(level.getServer()).reservoirs().administer(ACTOR, values);
        check(machine.deliverChannelGrant(required - 1) == required - 1, "Prepare commits all but final Essence through actual production debit");
        var job = job(machine);
        check(job != null && !job.complete() && job.credit() != null && ACTOR.equals(job.credit().actor())
                        && source.physicalMachineId().equals(job.credit().source()) && !done(actor),
                "Prepare has unpaid remainder, captured actor/source and no completed achievement");
        // Keep the job armed but remove unspent fixture stock, so no scheduler tick during
        // normal shutdown or startup can complete the missing payment before resume asserts it.
        values.replaceAll((essence, amount) -> BigInteger.ZERO);
        EssenceSavedData.get(level.getServer()).reservoirs().administer(ACTOR, values);
        check(machine.sourceAmountAvailable() == 0, "Prepare drains only unused fixture stock while preserving committed partial job");
        Properties expected = new Properties(); expected.setProperty("version", "1");
        expected.setProperty("source", source.physicalMachineId().toString()); expected.setProperty("machine", machine.physicalMachineId().toString());
        expected.setProperty("action", job.identity().toString()); expected.setProperty("actor", ACTOR.toString());
        expected.setProperty("paid", Long.toString(total(job.paid()))); expected.setProperty("required", Long.toString(required));
        expected.setProperty("process", Long.toString(ProcessHandle.current().pid()));
        expected.setProperty("process_start", Long.toString(ManagementFactory.getRuntimeMXBean().getStartTime()));
        expected.setProperty("stage", "prepared");
        expected.setProperty("achievement_partial", partial.toString());
        Files.createDirectories(marker.getParent());
        try (var output = Files.newOutputStream(marker)) { expected.store(output, "Expected values only; native world owns the job and credit"); }
        machine.setChanged(); source.setChanged(); EssenceSavedData.get(level.getServer()).setDirty();
        check(!machine.getItem(0).isEmpty() && machine.getItem(1).isEmpty(), "Prepare leaves real unfinished input in the native chunk for normal shutdown save");
    }

    private static void resume(ServerLevel level, ServerPlayer actor, Path marker) throws Exception {
        check(Files.isRegularFile(marker), "Resume requires the prepare-stage expected-values marker");
        Properties expected = new Properties(); try (var input = Files.newInputStream(marker)) { expected.load(input); }
        check(expected.getProperty("version").equals("1") && expected.getProperty("stage").equals("prepared")
                        && expected.getProperty("actor").equals(ACTOR.toString()), "Resume validates expected actor and prepared marker schema");
        check(!expected.getProperty("process").equals(Long.toString(ProcessHandle.current().pid()))
                        || !expected.getProperty("process_start").equals(Long.toString(ManagementFactory.getRuntimeMXBean().getStartTime())),
                "Resume runs in a different real Java process from prepare");
        check(level.getBlockEntity(SOURCE) instanceof EssenceCrucibleBlockEntity
                        && level.getBlockEntity(MACHINE) instanceof EssenceInfuserBlockEntity,
                "Real saved native chunk restores both blocks without replacement or marker-driven reconstruction");
        var source = (EssenceCrucibleBlockEntity) level.getBlockEntity(SOURCE);
        var machine = (EssenceInfuserBlockEntity) level.getBlockEntity(MACHINE);
        var job = job(machine);
        check(source.physicalMachineId().toString().equals(expected.getProperty("source"))
                        && machine.physicalMachineId().toString().equals(expected.getProperty("machine")),
                "Cold process preserves physical machine and supply UUIDs from native block persistence");
        check(job != null && !job.complete() && job.identity().toString().equals(expected.getProperty("action"))
                        && total(job.paid()) == Long.parseLong(expected.getProperty("paid"))
                        && total(job.required()) == Long.parseLong(expected.getProperty("required")),
                "Cold process restores same committed receipt identity and exact partial financial progress");
        check(job.credit() != null && job.credit().actionId().equals(job.identity()) && ACTOR.equals(job.credit().actor())
                        && source.physicalMachineId().equals(job.credit().source()) && ACTOR.equals(machine.machineBinding().operator()),
                "Cold process restores original operation actor, receipt, physical source and binding operator");
        check(!done(actor), "Loading an unfinished saved job does not manufacture native completion");
        var savedPartial = EssenceSavedData.get(level.getServer()).getPlayerData(ACTOR).achievementProgress().save();
        check(savedPartial.toString().equals(expected.getProperty("achievement_partial")),
                "Cold process restores exact durable achievement partials from world SavedData");
        var manager = level.getServer().getAdvancements();
        var exchange = manager.get(AchievementService.EQUIVALENT_EXCHANGE);
        var legacy = manager.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("essence_ascendance", "water_skiing"));
        check(actor.getAdvancements().getOrStartProgress(exchange).isDone()
                        && actor.getAdvancements().getOrStartProgress(legacy).isDone()
                        && !actor.getAdvancements().getOrStartProgress(manager.get(AchievementService.resource(1))).isDone(),
                "Cold process loads native earned completion and legacy travel without fabricating selected rod achievement");
        actor.getAdvancements().revoke(exchange, AchievementService.DISSOLVED);
        actor.getAdvancements().save();
        actor.getAdvancements().reload(manager);
        check(!actor.getAdvancements().getOrStartProgress(exchange).isDone()
                        && actor.getAdvancements().getOrStartProgress(legacy).isDone()
                        && savedPartial.equals(EssenceSavedData.get(level.getServer()).getPlayerData(ACTOR).achievementProgress().save()),
                "Native revoke/save/reload preserves legacy and partial facts without regranting old completion");
        machine.refreshLink();
        check(machine.sourceAmountAvailable() == 0, "Cold process also restores deliberately empty unspent source stock");
        EssenceSavedData.get(level.getServer()).reservoirs().administer(ACTOR,
                Map.of(machine.sourceEssence().id(), ReservoirService.SCALE));
        check(machine.processingEnabled() && machine.deliverChannelGrant(1) == 1, "Resume commits only the final missing Essence without new Start or credit reassignment");
        for (int i = 0; i <= machine.requiredProcessingTicks(); i++)
            EssenceInfuserBlockEntity.serverTick(level, MACHINE, machine.getBlockState(), machine);
        check(machine.getItem(0).isEmpty() && !machine.getItem(1).isEmpty()
                        && EssentiumCarrierData.readValidated(machine.getItem(1)).isPresent() && done(actor),
                "Actual post-restart production completion emits filled output and earns original actor's achievement");
        var advancement = actor.serverLevel().getServer().getAdvancements().get(AchievementService.resource(49));
        actor.getAdvancements().revoke(advancement, "achieved");
        EssenceInfuserBlockEntity.serverTick(level, MACHINE, machine.getBlockState(), machine);
        machine.removeItemNoUpdate(EssenceInfuserBlockEntity.OUTPUT_SLOT);
        check(!done(actor), "Post-restart duplicate tick and output retrieval cannot regrant a revoked completion");
        actor.getAdvancements().save();
        level.setBlock(MACHINE, Blocks.AIR.defaultBlockState(), 3); level.setBlock(SOURCE, Blocks.AIR.defaultBlockState(), 3);
        expected.setProperty("stage", "completed");
        try (var output = Files.newOutputStream(marker)) { expected.store(output, "Cold restart native assertions completed; expected values retained"); }
    }

    private static InfusionJobProgress job(EssenceInfuserBlockEntity machine) throws Exception {
        var field = EssenceInfuserBlockEntity.class.getDeclaredField("paidJob"); field.setAccessible(true);
        return (InfusionJobProgress) field.get(machine);
    }
    private static long total(Map<?, Long> amounts) { return amounts.values().stream().mapToLong(Long::longValue).sum(); }
    private static boolean done(ServerPlayer actor) {
        var advancement = actor.serverLevel().getServer().getAdvancements().get(AchievementService.resource(49));
        return advancement != null && actor.getAdvancements().getOrStartProgress(advancement).isDone();
    }
    private static ServerPlayer player(ServerLevel level) {
        var profile = new GameProfile(ACTOR, "AchColdRestart"); var client = ClientInformation.createDefault();
        var actor = new ServerPlayer(level.getServer(), level, profile, client);
        var transport = new Connection(PacketFlow.SERVERBOUND);
        new ServerGamePacketListenerImpl(level.getServer(), transport, actor, new CommonListenerCookie(profile, 0, client, false));
        var channel = new EmbeddedChannel(transport); channel.close().syncUninterruptibly(); return actor;
    }
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
