package com.mistaboom.essence_ascendance.projectile;

import com.mistaboom.essence_ascendance.balance.runtime.RuntimeBalanceDefinition;
import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.crucible.*;
import com.mistaboom.essence_ascendance.data.*;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import com.mistaboom.essence_ascendance.infuser.*;
import com.mistaboom.essence_ascendance.machine.*;
import com.mistaboom.essence_ascendance.pylon.*;
import com.mistaboom.essence_ascendance.team.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import java.math.BigInteger;
import java.util.*;

/** Real registered machines, menus, structure caches and source discovery in the existing no-world storage fixture. */
public final class NativeMachineBindingTest {
    private static int checks;
    public static void run() throws Exception {
        var runtime = EssenceConfigManager.serverRuntime();
        EssenceConfigManager.install(RuntimeBalanceDefinition.bootstrap());
        try {
            var f = new ProjectileNativeInterceptionTest.Fixture(); NativeGuardOutcomeTest.registry(f);
            f.level.memoryMachines = new HashMap<>(); f.level.memoryBlocks = new HashMap<>();
            var a = f.player; var b = f.player(Vec3.ZERO, "teammate"); var neighbor = f.player(Vec3.ZERO, "neighbor");
            PoolId pool = f.saved.nativeMembership().create(a.getUUID(), "Factory");
            var request = f.saved.nativeMembership().request(b.getUUID(), pool, true);
            f.saved.nativeMembership().decide(a.getUUID(), request.token(), true);
            f.saved.nativeMembership().create(neighbor.getUUID(), "Neighbor");
            var source = crucible(f, new BlockPos(5,0,0), a);
            var foreign = crucible(f, new BlockPos(1,0,0), neighbor);
            var infuser = new EssenceInfuserBlockEntity(new BlockPos(0,0,1), EssenceInfuserContent.ESSENCE_INFUSER.get().defaultBlockState());
            var pylon = new EssencePylonBlockEntity(new BlockPos(0,0,2), EssencePylonContent.ESSENCE_PYLON.get().defaultBlockState());
            install(f, infuser); install(f, pylon); infuser.bindOnPlacement(b); pylon.bindOnPlacement(b);
            infuser.refreshLink(); pylon.refreshLink();
            check(infuser.linkedCruciblePos().equals(source.getBlockPos()) && pylon.linkedCruciblePos().equals(source.getBlockPos()), "Both real callers exclude a closer neighboring team");
            var focus = new ItemStack(EssencePylonContent.ESSENCE_FOCUS.get()); EssenceFocusData.setTier(focus, EssenceFocusTier.values()[0]);
            pylon.setItem(0, focus);
            check(source.structureSnapshot().containsPylon(pylon.getBlockPos()) && !foreign.structureSnapshot().containsPylon(pylon.getBlockPos()), "Pylon contributes only to its valid linked station");
            long structureCapacity = source.structureStats().reservoirCapacity();
            check(source.localFillLimit().equals(BigInteger.valueOf(structureCapacity).multiply(BigInteger.TWO)), "Real station scales capacity, not throughput");
            var carrier = EssentiumCarrierData.createFull((EssentiumItem) EssenceInfuserContent.ESSENTIUM_NUGGET.get(), EssenceTypes.OFFENSE, EssenceFocusTier.DORMANT);
            source.setItem(0, carrier.copy());
            f.saved.reservoirs().administer(a.getUUID(), Map.of(EssenceTypes.OFFENSE.id(), source.localFillLimit().multiply(ReservoirService.SCALE)));
            for (int tick = 0; tick < source.structureStats().dissolutionTicksPerItem() + 1; tick++) {
                f.level.tick++; EssenceCrucibleBlockEntity.serverTick(f.level, source.getBlockPos(), source.getBlockState(), source);
            }
            check(ItemStack.matches(source.getItem(0), carrier), "Full local aggregate capacity rejects additions before consuming actual input");
            check(source.visualState().reservoirFillBasisPoints() == 10000, "Real station visual fill agrees with aggregate capacity");
            f.saved.reservoirs().channel(b.getUUID(), Map.of(EssenceTypes.OFFENSE.id(), 1L));
            check(source.totalStoredEssence().equals(source.localFillLimit().subtract(BigInteger.ONE)), "Actual team member withdraws from the shared reservoir");
            infuser.setItem(EssenceInfuserBlockEntity.FOCUS_SLOT, focus.copy()); infuser.setProcessingEnabled(b, true);
            check(infuser.processingEnabled(), "Team member arms another placer's Infuser");
            var entrant = f.player(Vec3.ZERO, "entrant");
            var beforePendingCapacity = source.localFillLimit();
            var pending = f.saved.nativeMembership().request(entrant.getUUID(), pool, true);
            check(source.localFillLimit().equals(beforePendingCapacity), "Pending requests never increase actual station capacity");
            f.saved.nativeMembership().decide(a.getUUID(), pending.token(), true);
            EssenceInfuserBlockEntity.serverTick(f.level, infuser.getBlockPos(), infuser.getBlockState(), infuser);
            check(infuser.processingEnabled(), "Ordinary settled roster changes retain actual machine armed state");
            f.saved.reservoirs().pause(new com.mistaboom.essence_ascendance.balance.engine.ProviderReadiness(com.mistaboom.essence_ascendance.balance.engine.ProviderReadiness.Status.NOT_READY, "fixture", "Pending role event"));
            EssenceInfuserBlockEntity.serverTick(f.level, infuser.getBlockPos(), infuser.getBlockState(), infuser);
            check(infuser.processingEnabled(), "Temporary membership unavailability pauses instead of disarming factory");
            MembershipPublication.publish(f.saved, NativeMembershipService.READY, f.saved.reservoirs().settledTeams().stream().map(MembershipRoster::from).toList());
            f.saved.nativeMembership().remove(a.getUUID(), entrant.getUUID());
            source.setItem(0, ItemStack.EMPTY);
            var closer = crucible(f, new BlockPos(2,0,0), b);
            infuser.refreshLink(); pylon.refreshLink();
            check(infuser.linkedCruciblePos().equals(source.getBlockPos()) && pylon.linkedCruciblePos().equals(source.getBlockPos()), "Closer same-team placement preserves both existing links");
            var target = new MachineLink.Source(f.level.dimension().location(), closer.getBlockPos());
            check(pylon.setLink(b, MachineLink.Mode.LOCKED, target), "Authorized explicit Pylon reassignment");
            check(!source.structureSnapshot().containsPylon(pylon.getBlockPos()) && closer.structureSnapshot().containsPylon(pylon.getBlockPos()), "Reassignment invalidates both source structure caches immediately");
            pylon.setItem(0, ItemStack.EMPTY);
            check(!closer.structureSnapshot().containsPylon(pylon.getBlockPos()), "Focus removal invalidates cached contribution");
            check(!infuser.setLink(neighbor, MachineLink.Mode.LOCKED, target), "Foreign actor cannot change source");
            check(!pylon.setLink(b, MachineLink.Mode.LOCKED, new MachineLink.Source(f.level.dimension().location(), foreign.getBlockPos())), "Proximity does not grant source authorization");
            check(infuser.setLink(b, MachineLink.Mode.LOCKED, target), "Infuser uses the same explicit source policy");
            f.level.unloadedMachineChunks = new HashSet<>(Set.of(net.minecraft.world.level.ChunkPos.asLong(0,0)));
            infuser.refreshLink(); pylon.refreshLink();
            check(infuser.linkState() == MachineLink.State.UNAVAILABLE && pylon.linkState() == MachineLink.State.UNAVAILABLE
                    && infuser.linkedCruciblePos().equals(closer.getBlockPos()) && pylon.linkedCruciblePos().equals(closer.getBlockPos()), "Unloaded sources retain both targets with no force loading");
            f.level.unloadedMachineChunks.clear();
            f.level.memoryMachines.remove(closer.getBlockPos()); infuser.refreshLink(); pylon.refreshLink();
            check(!infuser.isCurrentLinkValid() && !pylon.isCurrentLinkValid()
                    && infuser.linkedCruciblePos().equals(closer.getBlockPos()), "Locked targets remain recorded after confirmed removal without fallback");
            infuser.setLink(b, MachineLink.Mode.AUTOMATIC, null); pylon.setLink(b, MachineLink.Mode.AUTOMATIC, null);
            check(infuser.linkedCruciblePos().equals(source.getBlockPos()) && pylon.linkedCruciblePos().equals(source.getBlockPos()), "Automatic replaces confirmed invalid links");
            infuser.setLink(b, MachineLink.Mode.DISCONNECTED, null); pylon.setLink(b, MachineLink.Mode.DISCONNECTED, null);
            check(infuser.linkedCruciblePos() == null && pylon.linkedCruciblePos() == null
                    && !source.structureSnapshot().containsPylon(pylon.getBlockPos()), "Disconnected has no source or Pylon contribution");
            var oldMenu = new EssenceInfuserMenu(1, b.getInventory(), infuser);
            check(oldMenu.stillValid(b), "Team member can open real menu despite different placer");
            var sourceMenu = new EssenceCrucibleMenu(2, a.getInventory(), source);
            f.saved.nativeMembership().transfer(a.getUUID(), b.getUUID()); f.saved.nativeMembership().leave(a.getUUID());
            check(source.machineBinding().pool().equals(pool) && source.canPlayerUse(b) && !sourceMenu.stillValid(a), "Placer departure preserves infrastructure and rejects stale departed-player menu");
            check(oldMenu.stillValid(b), "Remaining team member retains inventory access after another member leaves");
            // Existing personal machines automatically join their owner's team.
            var solo = f.player(Vec3.ZERO, "personal"); var personal = crucible(f, new BlockPos(0,0,3), solo);
            f.saved.addCrucibleStored(solo.getUUID(), EssenceTypes.OFFENSE, 100);
            var join = f.saved.nativeMembership().request(solo.getUUID(), pool, true); f.saved.nativeMembership().decide(b.getUUID(), join.token(), true);
            check(personal.canPlayerUse(b) && personal.fundingActor() != null && personal.machineBinding().pool().equals(pool), "Joining automatically shares existing personal station access and funding");
            f.saved.nativeMembership().transfer(b.getUUID(), solo.getUUID());
            f.saved.nativeMembership().leave(b.getUUID());
            var savedMachine = source.saveWithFullMetadata(f.level.registryAccess());
            f.saved.nativeMembership().disband(solo.getUUID());
            var loaded = new EssenceCrucibleBlockEntity(source.getBlockPos(), source.getBlockState()); install(f, loaded);
            loaded.loadWithComponents(savedMachine, f.level.registryAccess());
            EssenceCrucibleBlockEntity.serverTick(f.level, loaded.getBlockPos(), loaded.getBlockState(), loaded);
            check(loaded.machineBinding().personalOwner().equals(solo.getUUID()) && !loaded.machineBinding().paused() && loaded.canPlayerUse(solo)
                    && loaded.fundingActor() != null, "Actual unloaded machine resolves final owner and resumes ordinary personal operation after disband");
            sharedSourceLinkContract();
            channelSelectionContract();
            concurrentChannelContract();
            NativeSinglePlayerChannelTest.run();
            stageSevenContinuity();
            stageEightInterfaces();
            stageEightAccessAndScreenScopes();
            NativeFishingRodTest.infusionTransactions(f); // Reuse the real streamed-payment and exact rollback fixture.
            f.close();
            System.out.println("NativeMachineBindingTest: " + checks + " actual machine assertions passed");
        } finally { if (runtime == null) EssenceConfigManager.reset(); else EssenceConfigManager.install(runtime); }
    }
    private static com.mistaboom.essence_ascendance.network.TeamInterfacePayload interfaceSnapshot(net.minecraft.server.level.ServerPlayer player) throws Exception {
        com.mistaboom.essence_ascendance.network.TeamInterfaceNetworkService.sync(player, false);
        var field = com.mistaboom.essence_ascendance.network.TeamInterfaceNetworkService.class.getDeclaredField("SENT"); field.setAccessible(true);
        var sent = ((Map<?, ?>) field.get(null)).get(player);
        var getter = sent.getClass().getDeclaredMethod("payload"); getter.setAccessible(true);
        return (com.mistaboom.essence_ascendance.network.TeamInterfacePayload) getter.invoke(sent);
    }
    private static int control(com.mistaboom.essence_ascendance.network.TeamInterfacePayload snapshot, String key) {
        for (int i = 0; i < snapshot.controls().size(); i++) if (snapshot.controls().get(i).label().key().equals("gui.essence_ascendance.team." + key)) return i;
        throw new AssertionError("Missing control: " + key + " in " + snapshot);
    }
    private static void interfaceAction(net.minecraft.server.level.ServerPlayer player, com.mistaboom.essence_ascendance.network.TeamInterfacePayload snapshot, int index, String input) throws Exception {
        var handler = com.mistaboom.essence_ascendance.network.TeamInterfaceNetworkService.class.getDeclaredMethod("handle", net.minecraft.server.level.ServerPlayer.class, com.mistaboom.essence_ascendance.network.TeamInterfaceActionPayload.class); handler.setAccessible(true);
        handler.invoke(null, player, new com.mistaboom.essence_ascendance.network.TeamInterfaceActionPayload(snapshot.menuId(), snapshot.receipt(), index, snapshot.view(), snapshot.page(), input));
    }
    private static void requestInterface(net.minecraft.server.level.ServerPlayer player, int action, int view, int page) throws Exception {
        var handler = com.mistaboom.essence_ascendance.network.TeamInterfaceNetworkService.class.getDeclaredMethod("handle", net.minecraft.server.level.ServerPlayer.class, com.mistaboom.essence_ascendance.network.TeamInterfaceActionPayload.class); handler.setAccessible(true);
        handler.invoke(null, player, new com.mistaboom.essence_ascendance.network.TeamInterfaceActionPayload(player.containerMenu.containerId, new UUID(0,0), action, view, page, ""));
    }
    private static boolean externalInstalled() { return dev.architectury.platform.Platform.isModLoaded("ftbteams") || dev.architectury.platform.Platform.isModLoaded("openpartiesandclaims"); }
    private static void openTeamPopup(net.minecraft.server.level.ServerPlayer player, EssenceCrucibleBlockEntity source, int id) throws Exception {
        initializeMenuLifecycle(player);
        player.containerMenu = new EssenceCrucibleMenu(id, player.getInventory(), source);
        var inventory = player.containerMenu;
        requestInterface(player, -1, 0, 0);
        check(player.containerMenu == inventory, "M opens a popup without replacing the actual machine menu");
    }
    /** Complete the constructor-skipped player boundary before exercising real openMenu/closeContainer. */
    private static void initializeMenuLifecycle(net.minecraft.server.level.ServerPlayer player) throws Exception {
        if (player.inventoryMenu != null) return;
        ProjectileNativeInterceptionTest.set(net.minecraft.world.entity.player.Player.class, player, "inventoryMenu",
                new net.minecraft.world.inventory.InventoryMenu(player.getInventory(), true, player));
        for (var entry : Map.of("containerSynchronizer", "1", "containerListener", "2").entrySet()) {
            var constructor = Class.forName("net.minecraft.server.level.ServerPlayer$" + entry.getValue())
                    .getDeclaredConstructor(net.minecraft.server.level.ServerPlayer.class);
            constructor.setAccessible(true);
            ProjectileNativeInterceptionTest.set(net.minecraft.server.level.ServerPlayer.class, player, entry.getKey(), constructor.newInstance(player));
        }
    }
    private static void stageEightInterfaces() throws Exception {
        int initial = checks;
        var f = new ProjectileNativeInterceptionTest.Fixture(); NativeGuardOutcomeTest.registry(f);
        try {
            f.level.memoryMachines = new HashMap<>(); f.level.memoryBlocks = new HashMap<>();
            var a = f.player; var b = f.player(Vec3.ZERO, "interface_applicant");
            var team = f.saved.nativeMembership().create(a.getUUID(), "Interface team");
            var source = crucible(f, new BlockPos(2,0,0), a);
            check(source.ownerDisplayName().equals("Interface team"), "Owner info resolves native team name rather than UUID");
            if (externalInstalled()) {
                var before = a.containerMenu;
                com.mistaboom.essence_ascendance.network.TeamInterfaceNetworkService.open(a, source);
                check(a.containerMenu == before, "Installed external provider never opens a standalone native membership screen");
            } else {
                source.setItem(0, new ItemStack(EssenceInfuserContent.LATENT_NUGGET.get(), 37));
                var outsider = new TeamMenu(82, b.getInventory(), source, TeamMenu.Kind.ACCESS); b.containerMenu = outsider;
                var snapshot = interfaceSnapshot(b);
                check(outsider.slots.isEmpty() && !source.canPlayerUse(b), "Outsider access prompt has no machine/player slots or inventory authority");
                check(snapshot.lines().stream().anyMatch(t -> t.key().endsWith("access_denied"))
                        && snapshot.lines().stream().noneMatch(t -> t.arguments().contains("37") || t.key().contains("stored")), "Access denial exposes no inventory or reservoir data");
                var selection = com.mistaboom.essence_ascendance.network.EssenceCrucibleNetworkService.class.getDeclaredMethod("handleSelectionRequest", net.minecraft.server.level.ServerPlayer.class, com.mistaboom.essence_ascendance.network.EssenceCrucibleSelectionPayload.class); selection.setAccessible(true);
                selection.invoke(null, b, new com.mistaboom.essence_ascendance.network.EssenceCrucibleSelectionPayload(82, CrucibleChannelSelection.SELECT_NONE, 0));
                check(f.saved.getPlayerData(b.getUUID()).crucibleChannelSelection() == 63, "Access menus cannot mutate the Crucible's personal selection");
                requestInterface(b, -3, 2, 0);
                check(interfaceSnapshot(b).view() == 1, "Retired settings request cannot retarget a denied access menu");
                interfaceAction(b, snapshot, control(snapshot, "request_access"), "");
                check(f.saved.nativeMembership().requests(b.getUUID()).size() == 1 && f.saved.resolvePool(b.getUUID()).requireReady().isSoloFor(b.getUUID()), "Access request never auto-enrolls applicant");
                snapshot = interfaceSnapshot(b);
                check(snapshot.lines().stream().anyMatch(t -> t.key().endsWith("pending_team") && t.arguments().contains("Interface team")), "Pending access names its actual destination");
                interfaceAction(b, snapshot, control(snapshot, "cancel"), "");
                check(f.saved.nativeMembership().requests(b.getUUID()).isEmpty(), "Pending access request can be cancelled");
                snapshot = interfaceSnapshot(b); interfaceAction(b, snapshot, control(snapshot, "request_access"), "");
                openTeamPopup(a, source, 83); var manager = interfaceSnapshot(a);
                check(manager.controls().size() == 2 && manager.controls().stream().noneMatch(c -> c.input()), "Owner home has disband and conditional requests, no unrelated settings");
                interfaceAction(a, manager, control(manager, "requests_count"), "");
                manager = interfaceSnapshot(a);
                check(manager.view() == 2 && manager.pages() == 1, "Incoming requests open within the same popup");
                interfaceAction(a, manager, control(manager, "approve_request"), "");
                check(f.saved.resolvePool(b.getUUID()).requireReady().pool().equals(team) && source.canPlayerUse(b), "Explicit owner approval grants team access");
                openTeamPopup(a, source, 84); manager = interfaceSnapshot(a);
                check(manager.controls().size() == 1 && manager.controls().getFirst().label().key().endsWith("manage"), "Owner with members has one Manage button");
                check(manager.lines().stream().noneMatch(t -> t.key().contains("authority") || t.key().contains("readiness") || t.arguments().contains(team.id().toString())), "Popup has no authority, readiness or UUID noise");
                requestInterface(a, -3, 2, 0);
                check(interfaceSnapshot(a).view() == 0, "Removed settings action cannot open routing controls");
                var closed = interfaceSnapshot(a); requestInterface(a, -4, 0, 0);
                interfaceAction(a, closed, control(closed, "manage"), "");
                var sentField = com.mistaboom.essence_ascendance.network.TeamInterfaceNetworkService.class.getDeclaredField("SENT"); sentField.setAccessible(true);
                check(!((Map<?,?>)sentField.get(null)).containsKey(a), "Closing popup revokes stale action receipts");
                var c = f.player(Vec3.ZERO, "next_applicant"); f.saved.nativeMembership().request(c.getUUID(), team, true);
                openTeamPopup(b, source, 85); var member = interfaceSnapshot(b);
                check(member.controls().size() == 1 && member.controls().getFirst().label().key().endsWith("leave"), "Ordinary member sees no owner-only incoming approvals");
                openTeamPopup(a, source, 86); var stale = interfaceSnapshot(a);
                f.saved.nativeMembership().transfer(a.getUUID(), b.getUUID()); f.saved.nativeMembership().leave(a.getUUID());
                interfaceAction(a, stale, control(stale, "manage"), "");
                check(!a.containerMenu.stillValid(a) && !((Map<?,?>)sentField.get(null)).containsKey(a), "Departure revokes popup and machine menu authorization");
                openTeamPopup(b, source, 89); requestInterface(b, -2, 0, Integer.MAX_VALUE);
                snapshot = interfaceSnapshot(b); check(snapshot.page() == 0, "Hostile popup page offset clamps without overflow");
                interfaceAction(b, snapshot, control(snapshot, "disband"), "");
                check(f.saved.resolvePool(b.getUUID()).requireReady().isSoloFor(b.getUUID()) && source.canPlayerUse(b), "Final owner disbands and continues using personal Crucible");
            }
        } finally { f.close(); }
        for (var authority : List.of(MembershipAuthority.FTB_TEAMS, MembershipAuthority.OPENPAC)) {
            f = new ProjectileNativeInterceptionTest.Fixture(); NativeGuardOutcomeTest.registry(f);
            try {
                f.level.memoryMachines = new HashMap<>(); f.level.memoryBlocks = new HashMap<>();
                var a = f.player; var pool = new PoolId(authority, PoolId.Kind.TEAM, UUID.randomUUID());
                f.saved.activateMembershipAuthority(authority, NativeMembershipService.READY, List.of(new MembershipSnapshot(pool, List.of(a.getUUID()), MembershipPolicy.grants(List.of(a.getUUID()), Set.of(a.getUUID())), 1, 1)), false);
                var station = crucible(f, new BlockPos(1,0,0), a); var outsider = f.player(Vec3.ZERO, "provider_outsider");
                outsider.containerMenu = new TeamMenu(85, outsider.getInventory(), station, TeamMenu.Kind.ACCESS);
                var current = interfaceSnapshot(outsider);
                check(current.controls().isEmpty() && current.lines().stream().anyMatch(l -> l.key().endsWith("provider_guidance") || l.key().endsWith("provider_setup") || l.key().endsWith("provider_unavailable")), "External authority presents provider guidance");
                check(outsider.containerMenu.slots.isEmpty() && !station.canPlayerUse(outsider), "External denied access has no duplicate enrollment or inventory authority");
            } finally { f.close(); }
        }
        System.out.println("Compact team popup/access: " + (checks-initial) + " actual assertions passed");
    }
    private static void stageEightAccessAndScreenScopes() throws Exception {
        int initial = checks;
        var f = new ProjectileNativeInterceptionTest.Fixture(); NativeGuardOutcomeTest.registry(f);
        try {
            f.level.memoryMachines = new HashMap<>(); f.level.memoryBlocks = new HashMap<>();
            var owner = f.player; var applicant = f.player(Vec3.ZERO, "personal_access");
            initializeMenuLifecycle(applicant);
            var personal = crucible(f, new BlockPos(2,0,0), owner);
            var soloPylon = new EssencePylonBlockEntity(new BlockPos(0,0,1), EssencePylonContent.ESSENCE_PYLON.get().defaultBlockState()); install(f, soloPylon); soloPylon.bindOnPlacement(owner);
            check(personal.canPlayerUse(owner) && personal.fundingActor() != null && personal.accessState(owner) == BoundMachine.AccessState.AVAILABLE
                    && soloPylon.linkedCruciblePos().equals(personal.getBlockPos()), "Solo placement provides normal access, usable funding and immediate automatic Pylon link without a team");
            if (!externalInstalled()) {
                applicant.containerMenu = new TeamMenu(90, applicant.getInventory(), personal, TeamMenu.Kind.ACCESS);
                var request = interfaceSnapshot(applicant); interfaceAction(applicant, request, control(request, "request_access"), "");
                check(f.saved.resolvePool(owner.getUUID()).requireReady().isSoloFor(owner.getUUID()) && !personal.canPlayerUse(applicant), "Requesting access to personal machine never forces owner into team");
                openTeamPopup(owner, personal, 91); var approval = interfaceSnapshot(owner);
                interfaceAction(owner, approval, control(approval, "requests_count"), ""); approval = interfaceSnapshot(owner);
                check(approval.controls().stream().anyMatch(c -> c.input()), "Solo-to-solo approval requests a team name");
                interfaceAction(owner, approval, control(approval, "create_approve"), "Shared workshop");
            } else {
                var request = f.saved.nativeMembership().requestPersonal(applicant.getUUID(), owner.getUUID(), true);
                f.saved.nativeMembership().decide(owner.getUUID(), request.token(), true, "Shared workshop");
            }
            var pool = f.saved.resolvePool(owner.getUUID()).requireReady().pool();
            check(personal.canPlayerUse(applicant) && personal.machineBinding().pool().equals(pool) && personal.fundingActor() != null
                    && personal.accessState(owner) == BoundMachine.AccessState.AVAILABLE, "Joining makes existing personal Crucible team infrastructure with no adoption state");
            if (!externalInstalled()) {
                check(applicant.containerMenu.stillValid(applicant), "Personal request approval keeps the slot-free applicant prompt alive");
                var granted = interfaceSnapshot(applicant);
                check(granted.lines().getFirst().key().endsWith("access_granted"), "Approved applicant immediately sees Access granted");
                interfaceAction(applicant, granted, control(granted, "open_machine"), "");
                check(applicant.containerMenu instanceof EssenceCrucibleMenu, "Approved prompt opens actual authorized inventory");
            }
            soloPylon.refreshLink();
            check(soloPylon.machineBinding().pool().equals(pool) && soloPylon.linkedCruciblePos().equals(personal.getBlockPos()), "Existing personal Pylon follows membership and retains same-team link");
            f.saved.nativeMembership().leave(applicant.getUUID());
            check(!personal.canPlayerUse(applicant) && personal.canPlayerUse(owner), "Departed member loses direct team inventory/settings access");
            f.saved.nativeMembership().disband(owner.getUUID());
            check(personal.canPlayerUse(owner) && personal.machineBinding().personalOwner().equals(owner.getUUID())
                    && personal.fundingActor() != null && personal.accessState(owner) == BoundMachine.AccessState.AVAILABLE, "Final disband restores ordinary usable personal ownership without adoption");
            if (!externalInstalled()) {
                openTeamPopup(owner, personal, 97);
                var home = interfaceSnapshot(owner);
                check(home.controls().size() == 1 && home.controls().getFirst().input()
                        && home.lines().getFirst().key().endsWith("playing_solo"), "Solo home has only name field and Create, without pending/adoption wording");
                requestInterface(owner, -2, 2, 0);
                check(interfaceSnapshot(owner).view() == 0, "Unrequested popup view cannot expose another page");
            }
            var access = new TeamMenu(98, applicant.getInventory(), personal, TeamMenu.Kind.ACCESS);
            check(access.slots.isEmpty(), "Access prompt remains a separate slot-free menu");
        } finally { f.close(); }
        System.out.println("Solo/team automatic machine access: " + (checks-initial) + " actual assertions passed");
    }

    /** Many-to-one link eligibility remains independent of other active consumers. */
    private static void sharedSourceLinkContract() throws Exception {
        var f = new ProjectileNativeInterceptionTest.Fixture(); NativeGuardOutcomeTest.registry(f);
        f.level.memoryMachines = new HashMap<>(); f.level.memoryBlocks = new HashMap<>();
        var a = f.player; var b = f.player(Vec3.ZERO, "shared_source_member"); var outsider = f.player(Vec3.ZERO, "shared_source_neighbor");
        var pool = f.saved.nativeMembership().create(a.getUUID(), "Shared source links");
        var join = f.saved.nativeMembership().request(b.getUUID(),pool,true); f.saved.nativeMembership().decide(a.getUUID(),join.token(),true);
        f.saved.nativeMembership().create(outsider.getUUID(),"Neighbor");
        var source = crucible(f,new BlockPos(5,0,0),a);
        var amounts = new LinkedHashMap<net.minecraft.resources.ResourceLocation,Long>();
        EssenceTypes.ORDERED.forEach(type -> amounts.put(type.id(),1_000_000L));
        f.saved.reservoirs().credit(a.getUUID(),ReservoirService.wholeUnits(amounts,false),ReservoirService.NONE);
        var first = sharedSourceInfuser(f,new BlockPos(0,0,1),a);
        first.refreshLink();
        var focus = new ItemStack(EssencePylonContent.ESSENCE_FOCUS.get()); EssenceFocusData.setTier(focus,EssenceFocusTier.DORMANT);
        first.setItem(EssenceInfuserBlockEntity.FOCUS_SLOT,focus.copy());
        first.setItem(EssenceInfuserBlockEntity.INPUT_SLOT,new ItemStack(EssenceInfuserContent.LATENT_NUGGET.get(),2));
        first.setProcessingEnabled(a,true);
        EssenceInfuserBlockEntity.serverTick(f.level,first.getBlockPos(),first.getBlockState(),first);
        check(first.statusCode()==EssenceInfuserBlockEntity.STATUS_PROCESSING,"First Infuser is actively using the shared source before a second link is requested");
        var second = sharedSourceInfuser(f,new BlockPos(0,0,2),b); second.refreshLink();
        check(first.isCurrentLinkValid() && second.isCurrentLinkValid() && first.linkedSource().equals(second.linkedSource()),
                "Two actual Automatic Infusers may select the same occupied-by-processing source");
        var selected = new MachineLink.Source(f.level.dimension().location(),source.getBlockPos());
        check(second.setLink(b,MachineLink.Mode.LOCKED,selected) && first.isCurrentLinkValid() && second.isCurrentLinkValid(),
                "Locked linking shares an already-used source without replacing another link");
        second.setItem(EssenceInfuserBlockEntity.FOCUS_SLOT,focus.copy());
        second.setItem(EssenceInfuserBlockEntity.INPUT_SLOT,new ItemStack(EssenceInfuserContent.LATENT_NUGGET.get(),2)); second.setProcessingEnabled(b,true);
        EssenceInfuserBlockEntity.serverTick(f.level,second.getBlockPos(),second.getBlockState(),second);
        check(first.statusCode()==EssenceInfuserBlockEntity.STATUS_PROCESSING && second.statusCode()==EssenceInfuserBlockEntity.STATUS_PROCESSING,
                "Another active Infuser is not a source-occupancy or link-validation gate");
        var closer = crucible(f,new BlockPos(2,0,0),b); first.refreshLink(); second.refreshLink();
        check(first.linkedSource().equals(selected) && second.linkedSource().equals(selected),"Both shared-source links remain stable after closer placement");
        check(source.startChanneling(a) && first.linkState()==MachineLink.State.VALID && second.linkState()==MachineLink.State.VALID,
                "An active player session does not reserve the source against Infuser links");
        check(first.statusCode()==EssenceInfuserBlockEntity.STATUS_PROCESSING && first.isCurrentLinkValid(),
                "Player channeling and Infuser processing coexist on the shared source");
        f.level.unloadedMachineChunks = new HashSet<>(Set.of(net.minecraft.world.level.ChunkPos.asLong(0,0)));
        first.refreshLink();second.refreshLink();
        check(first.linkState()==MachineLink.State.UNAVAILABLE && second.linkState()==MachineLink.State.UNAVAILABLE
                && first.linkedSource().equals(selected) && second.linkedSource().equals(selected),"Both shared links retain an unloaded target without rerouting or forcing a load");
        f.level.unloadedMachineChunks.clear();
        check(!second.setLink(b,MachineLink.Mode.LOCKED,new MachineLink.Source(net.minecraft.resources.ResourceLocation.parse("minecraft:the_nether"),source.getBlockPos()))
                && second.linkedSource().equals(selected),"Shared-source linking still rejects another dimension");
        check(!second.setLink(b,MachineLink.Mode.LOCKED,new MachineLink.Source(f.level.dimension().location(),new BlockPos(1000,0,0)))
                && second.linkedSource().equals(selected),"Shared-source linking still enforces range");
        var foreign = sharedSourceInfuser(f,new BlockPos(0,0,3),outsider);foreign.refreshLink();
        check(foreign.linkedSource()==null && !foreign.setLink(outsider,MachineLink.Mode.LOCKED,selected),
                "Proximity and another consumer's link do not grant an outsider access");
        check(second.setLink(b,MachineLink.Mode.DISCONNECTED,null) && second.linkedSource()==null && first.linkedSource().equals(selected)
                && first.isCurrentLinkValid(),"Disconnecting one consumer does not alter another consumer's source");
        second.setLink(b,MachineLink.Mode.LOCKED,selected);source.stopChanneling();
        f.level.memoryMachines.remove(source.getBlockPos());first.refreshLink();second.refreshLink();
        check(first.linkedCruciblePos().equals(closer.getBlockPos()) && first.isCurrentLinkValid()
                && second.linkedSource().equals(selected) && second.linkState()==MachineLink.State.INVALID,
                "Confirmed removal replaces only Automatic; Locked retains an invalid recorded target without fallback");
        f.close();
    }
    private static EssenceInfuserBlockEntity sharedSourceInfuser(ProjectileNativeInterceptionTest.Fixture f,BlockPos pos,
                                                                net.minecraft.server.level.ServerPlayer owner) {
        var machine = new EssenceInfuserBlockEntity(pos,EssenceInfuserContent.ESSENCE_INFUSER.get().defaultBlockState());
        install(f,machine);machine.bindOnPlacement(owner);return machine;
    }

    private static void channelSelectionContract() throws Exception {
        var f = new ProjectileNativeInterceptionTest.Fixture(); NativeGuardOutcomeTest.registry(f);
        f.level.memoryMachines = new HashMap<>(); f.level.memoryBlocks = new HashMap<>();
        var a = f.player; var b = f.player(Vec3.ZERO, "channel_teammate");
        var pool = f.saved.nativeMembership().create(a.getUUID(), "Selective channeling");
        var join = f.saved.nativeMembership().request(b.getUUID(), pool, true); f.saved.nativeMembership().decide(a.getUUID(), join.token(), true);
        var source = crucible(f, new BlockPos(0,0,2), a);
        var aData = f.saved.getPlayerData(a.getUUID()); var bData = f.saved.getPlayerData(b.getUUID());
        aData.setCrucibleChannelSelection(1 << EssenceCrucibleEssences.indexOf(EssenceTypes.UTILITY));
        bData.setCrucibleChannelSelection(1 << EssenceCrucibleEssences.indexOf(EssenceTypes.OFFENSE));
        var deposits = new LinkedHashMap<net.minecraft.resources.ResourceLocation, Long>();
        for (var essence : EssenceTypes.ORDERED) deposits.put(essence.id(),100L);
        f.saved.reservoirs().credit(a.getUUID(), ReservoirService.wholeUnits(deposits,false), ReservoirService.NONE);
        var transfer = EssenceCrucibleBlockEntity.class.getDeclaredMethod("transferFairly", net.minecraft.server.level.ServerPlayer.class, long.class); transfer.setAccessible(true);
        check(source.startChanneling(a), "Selected-only receiving starts on a real team source");
        check((long) transfer.invoke(source,a,25L)==25 && aData.getAvailable(EssenceTypes.UTILITY)==25, "Single selected type gets the entire existing budget");
        for (var essence : EssenceTypes.ORDERED) if (!essence.equals(EssenceTypes.UTILITY))
            check(source.storedEssence(essence)==100 && aData.getAvailable(essence)==0, "Unselected type remains available for automation: "+essence.id());
        check(source.startChanneling(b), "Second selected player starts concurrently");
        check((long) transfer.invoke(source,b,70L)==70 && bData.getAvailable(EssenceTypes.OFFENSE)==70 && source.storedEssence(EssenceTypes.OFFENSE)==30,
                "Another member's independent selection can receive more than their underlying individual share");
        check((long) transfer.invoke(source,a,100L)==75 && source.storedEssence(EssenceTypes.UTILITY)==0, "A selected category empties without withdrawing others");
        source.stopChanneling(b.getUUID());
        f.level.tick++; EssenceCrucibleBlockEntity.serverTick(f.level,source.getBlockPos(),source.getBlockState(),source);
        check(source.isChanneling() && source.channelingPaused() && EssenceCrucibleChannelService.isChanneling(a.getUUID())
                , "Exhaustion retains the receiving session with zero demand");
        check(!source.visualState().channeling(), "Waiting channel renders no transfer stream");
        check((long) transfer.invoke(source,a,100L)==0 && source.storedEssence(EssenceTypes.OFFENSE)==30, "No empty-selected fallback drains conversion input");
        f.saved.reservoirs().credit(a.getUUID(),ReservoirService.wholeUnits(Map.of(EssenceTypes.UTILITY.id(),9L),false),ReservoirService.NONE);
        long before = aData.getAvailable(EssenceTypes.UTILITY);
        for(int tick=0;tick<20;tick++){f.level.tick++;EssenceCrucibleBlockEntity.serverTick(f.level,source.getBlockPos(),source.getBlockState(),source);EssenceCrucibleChannelService.tick(f.server,f.level.getGameTime());}
        check(aData.getAvailable(EssenceTypes.UTILITY)>before && source.isChanneling() && source.storedEssence(EssenceTypes.OFFENSE)==30,
                "Refill resumes the real active channel without a restart or unselected withdrawal");
        aData.setCrucibleChannelSelection(0); before=aData.getAvailable(EssenceTypes.OFFENSE);
        f.level.tick++; EssenceCrucibleBlockEntity.serverTick(f.level,source.getBlockPos(),source.getBlockState(),source);
        check(source.isChanneling() && source.channelingPaused() && (long) transfer.invoke(source,a,100L)==0 && aData.getAvailable(EssenceTypes.OFFENSE)==before,
                "None pauses an existing channel without consuming any type");
        bData.setCrucibleChannelSelection(0);
        check(!source.startChanneling(b) && source.isChanneling(a.getUUID()), "None cannot start or replace another member's channel");
        aData.setCrucibleChannelSelection(1 << EssenceCrucibleEssences.indexOf(EssenceTypes.OFFENSE));
        check((long) transfer.invoke(source,a,2L)==2 && source.isChanneling(), "Selection changes apply immediately without restarting");
        bData.setCrucibleChannelSelection(1);
        check(source.startChanneling(b) && EssenceCrucibleChannelService.isChanneling(a.getUUID())
                && EssenceCrucibleChannelService.isChanneling(b.getUUID()), "Starting a second receiver retains both player sessions");
        EssenceCrucibleChannelService.stopForPlayer(a);
        check(source.isChanneling(b.getUUID()) && !source.isChanneling(a.getUUID()), "One receiver logout does not stop another receiver");
        // The actual server handler cannot edit preferences through a malformed or stale menu grant.
        var menu = new EssenceCrucibleMenu(73,a.getInventory(),source); a.containerMenu=menu;
        var handler = com.mistaboom.essence_ascendance.network.EssenceCrucibleNetworkService.class.getDeclaredMethod("handleSelectionRequest",
                net.minecraft.server.level.ServerPlayer.class, com.mistaboom.essence_ascendance.network.EssenceCrucibleSelectionPayload.class); handler.setAccessible(true);
        int selection=aData.crucibleChannelSelection();
        handler.invoke(null,a,new com.mistaboom.essence_ascendance.network.EssenceCrucibleSelectionPayload(74,CrucibleChannelSelection.SELECT_NONE,f.saved.getPlayerData(a.getUUID()).crucibleChannelSelectionRevision()));
        handler.invoke(null,a,new com.mistaboom.essence_ascendance.network.EssenceCrucibleSelectionPayload(73,100,f.saved.getPlayerData(a.getUUID()).crucibleChannelSelectionRevision()));
        check(aData.crucibleChannelSelection()==selection,"Wrong-menu and malformed selection packets cannot mutate preferences");
        source.bindOnPlacement(a);
        handler.invoke(null,a,new com.mistaboom.essence_ascendance.network.EssenceCrucibleSelectionPayload(73,CrucibleChannelSelection.SELECT_NONE,f.saved.getPlayerData(a.getUUID()).crucibleChannelSelectionRevision()));
        check(aData.crucibleChannelSelection()==selection && !menu.stillValid(a),"Binding changes reject the real stale selection-menu request");
        source.stopChanneling(); f.close();
    }

    private static void concurrentChannelContract() throws Exception {
        var previous = EssenceConfigManager.serverRuntime();
        var json = previous.toJson();
        json.getAsJsonObject("crucible").addProperty("transferRatePerSecond", 120);
        var infuserConfig = json.getAsJsonObject("infuser");
        infuserConfig.addProperty("noFocusInfusionThroughputPerSecond",120);
        for (var grade : infuserConfig.getAsJsonObject("grades").entrySet()) {
            grade.getValue().getAsJsonObject().addProperty("infusionThroughputPerSecond",120);
            grade.getValue().getAsJsonObject().addProperty("ingotCapacity",5400);
        }
        EssenceConfigManager.install(RuntimeBalanceDefinition.fromJson(json));
        var f = new ProjectileNativeInterceptionTest.Fixture(); NativeGuardOutcomeTest.registry(f);
        try {
            f.level.memoryMachines = new HashMap<>(); f.level.memoryBlocks = new HashMap<>(); f.level.unloadedMachineChunks = new HashSet<>();
            var a = f.player; var b = f.player(Vec3.ZERO,"concurrent_member");
            var team = f.saved.nativeMembership().create(a.getUUID(),"Concurrent channels");
            var request = f.saved.nativeMembership().request(b.getUUID(),team,true);
            f.saved.nativeMembership().decide(a.getUUID(),request.token(),true);
            var source = crucible(f,new BlockPos(5,0,0),a);
            var secondSource = crucible(f,new BlockPos(6,0,0),b);
            Map<net.minecraft.resources.ResourceLocation,Long> initial = new LinkedHashMap<>();
            for (var type : EssenceTypes.ORDERED) initial.put(type.id(),5_000_000L);
            f.saved.reservoirs().credit(a.getUUID(),ReservoirService.wholeUnits(initial,false),ReservoirService.NONE);
            List<EssenceInfuserBlockEntity> machines = new ArrayList<>();
            for (int i=0;i<2;i++) {
                var infuser = sharedSourceInfuser(f,new BlockPos(0,0,i+1),i==0?a:b);
                var focus = new ItemStack(EssencePylonContent.ESSENCE_FOCUS.get());
                EssenceFocusData.setTier(focus,EssenceFocusTier.DORMANT);
                infuser.setItem(EssenceInfuserBlockEntity.FOCUS_SLOT,focus);
                infuser.setItem(EssenceInfuserBlockEntity.INPUT_SLOT,new ItemStack(EssenceInfuserContent.LATENT_NUGGET.get(),2));
                infuser.setLink(i==0?a:b,MachineLink.Mode.LOCKED,new MachineLink.Source(f.level.dimension().location(),source.getBlockPos()));
                infuser.setProcessingEnabled(i==0?a:b,true); machines.add(infuser);
            }
            var jobField=EssenceInfuserBlockEntity.class.getDeclaredField("paidJob");jobField.setAccessible(true);
            tickChannels(f,source,machines,20);
            check(jobPaid(jobField,machines.get(0))==60 && jobPaid(jobField,machines.get(1))==60,"Two actual Essentium jobs share 120 as 60 / 60");
            long totalBefore=poolWhole(f,a); long pBefore=availableTotal(f,a);
            check(source.startChanneling(a) && source.startChanneling(a) && source.channelingPlayers().size()==1,"Duplicate Start does not add a share");
            tickChannels(f,source,machines,20);
            check(jobPaid(jobField,machines.get(0))==100 && jobPaid(jobField,machines.get(1))==100 && availableTotal(f,a)-pBefore==40,"Mixed actual jobs and one player get 40 each");
            check(totalBefore-poolWhole(f,a)==120,"Mixed actual transaction conservation");
            check(source.startChanneling(b),"Second member starts without replacing first");
            pBefore=availableTotal(f,a); long bBefore=availableTotal(f,b);
            tickChannels(f,source,machines,20);
            check(jobPaid(jobField,machines.get(0))==130 && jobPaid(jobField,machines.get(1))==130
                    && availableTotal(f,a)-pBefore==30 && availableTotal(f,b)-bBefore==30,"Two Infusers and two players get 30 each");
            f.saved.getPlayerData(a.getUUID()).setCrucibleChannelSelection(1<<5);
            bBefore=availableTotal(f,b); long utility=f.saved.getPlayerData(a.getUUID()).getAvailable(EssenceTypes.UTILITY);
            tickChannels(f,source,machines,20);
            check(f.saved.getPlayerData(a.getUUID()).getAvailable(EssenceTypes.UTILITY)-utility==30
                    && availableTotal(f,b)-bBefore==30,"One selected type gets the same channel share as All");
            f.saved.getPlayerData(a.getUUID()).setCrucibleChannelSelection(0);pBefore=availableTotal(f,a);
            tickChannels(f,source,machines,20);
            check(availableTotal(f,a)==pBefore && jobPaid(jobField,machines.get(0))==200 && jobPaid(jobField,machines.get(1))==200,"None frees bandwidth without filtering either Infuser recipe");
            check(source.isChanneling(a.getUUID()) && source.channelingPaused(a.getUUID()) && machines.stream().allMatch(EssenceInfuserBlockEntity::processingEnabled),"Toggle preserves explicit sessions and armed jobs");
            source.stopChanneling(a.getUUID()); f.saved.getPlayerData(a.getUUID()).setCrucibleChannelSelection(63);
            tickChannels(f,source,machines,4);check(!source.isChanneling(a.getUUID()),"Changing preferences cannot auto-start a stopped channel");
            source.stopChanneling(b.getUUID()); source.startChanneling(a); secondSource.startChanneling(a);
            check(!source.isChanneling(a.getUUID()) && secondSource.isChanneling(a.getUUID()),
                    "Starting at another real Crucible immediately replaces the player's previous source");
            pBefore=availableTotal(f,a);
            for(int t=0;t<20;t++) {
                f.level.tick++; EssenceCrucibleBlockEntity.serverTick(f.level,secondSource.getBlockPos(),secondSource.getBlockState(),secondSource);
                EssenceCrucibleBlockEntity.serverTick(f.level,source.getBlockPos(),source.getBlockState(),source);
                for(var machine:machines) EssenceInfuserBlockEntity.serverTick(f.level,machine.getBlockPos(),machine.getBlockState(),machine);
                EssenceCrucibleChannelService.tick(f.server,f.level.getGameTime());
            }
            check(availableTotal(f,a)-pBefore==120 && !source.isChanneling(a.getUUID()) && secondSource.isChanneling(a.getUUID()),
                    "A player receives only the new physical-source budget while old-source Infusers continue");
            source.stopChanneling(a.getUUID());
            check(secondSource.isChanneling(a.getUUID()), "Stopping an old source does not cancel its replacement player connection");
            EssenceCrucibleChannelService.stopForPlayer(a);
            check(!secondSource.isChanneling(a.getUUID()) && machines.stream().allMatch(EssenceInfuserBlockEntity::processingEnabled),"Logout cleanup removes player links without disarming automation");
            var savedInfuser=machines.get(0).saveWithFullMetadata(f.level.registryAccess()); long paid=jobPaid(jobField,machines.get(0));
            machines.get(0).setRemoved();var loaded=sharedSourceInfuser(f,machines.get(0).getBlockPos(),a);
            loaded.loadWithComponents(savedInfuser,f.level.registryAccess());machines.set(0,loaded);
            check(jobPaid(jobField,loaded)==paid && loaded.processingEnabled(),"Chunk save/reload preserves bounded exact timed payment and armed state");
            var beforeStock=poolWhole(f,a);
            f.level.unloadedMachineChunks.add(net.minecraft.world.level.ChunkPos.asLong(0,0));
            tickChannels(f,source,machines,20);
            check(poolWhole(f,a)==beforeStock && jobPaid(jobField,loaded)==paid,"Unavailable source reserves and spends no bandwidth");
            f.level.unloadedMachineChunks.clear();
            tickChannels(f,source,machines,20);
            check(jobPaid(jobField,loaded)==paid+60,"Reload resumes paid jobs without idle credit burst");
            // Block one output: its entire share goes to the other job.
            machines.get(1).setItem(EssenceInfuserBlockEntity.OUTPUT_SLOT,new ItemStack(net.minecraft.world.item.Items.DIRT));
            paid=jobPaid(jobField,loaded);tickChannels(f,source,machines,20);
            check(jobPaid(jobField,loaded)==paid+120,"Blocked output consumes no share");
            machines.get(1).setItem(EssenceInfuserBlockEntity.OUTPUT_SLOT,ItemStack.EMPTY);
            // Complete actual Essentium while Start/Stop/All/None continue; output waits for full payment.
            int outputBefore=loaded.getItem(EssenceInfuserBlockEntity.OUTPUT_SLOT).getCount();
            int ticks=0;
            while(loaded.getItem(EssenceInfuserBlockEntity.OUTPUT_SLOT).getCount()==outputBefore && ticks++<3000) {
                if(ticks%23==0)source.startChanneling(a);
                if(ticks%31==0)source.stopChanneling(a.getUUID());
                if(ticks%17==0)f.saved.getPlayerData(a.getUUID()).setCrucibleChannelSelection(ticks%34==0?0:63);
                tickChannels(f,source,machines,1);
            }
            check(ticks<3000 && loaded.getItem(EssenceInfuserBlockEntity.OUTPUT_SLOT).is(EssenceInfuserContent.ESSENTIUM_NUGGET.get()),"Actual Essentium completes under concurrent Start/Stop/toggle activity");
            check(loaded.getItem(EssenceInfuserBlockEntity.INPUT_SLOT).getCount()==1,"Timed job consumes one carrier from a stack exactly once");
            check(jobPaid(jobField,loaded)==0,"Paid timed job clears at completion without leaving a reusable tank");
            // Input substitution cannot spend already-paid progress on another recipe.
            tickChannels(f,source,machines,8);var prior=jobField.get(loaded);
            loaded.setItem(EssenceInfuserBlockEntity.INPUT_SLOT,new ItemStack(EssenceInfuserContent.LATENT_INGOT.get()));
            check(loaded.statusCode()==EssenceInfuserBlockEntity.STATUS_JOB_CONTEXT_REQUIRED && jobField.get(loaded)==prior,"Changing workpiece pauses the exact paid job without duplicating/discarding payment");
            source.startChanneling(b); source.setRemoved();
            check(!source.isChanneling() && machines.get(1).processingEnabled(),"Source removal invalidates channels without stopping team jobs");
            var active=secondSource.startChanneling(b);EssenceCrucibleChannelService.clear(f.server);
            check(active && !secondSource.isChanneling(),"Server stop/restart clears runtime sessions");
            manyInfusersAndRecipePaths();
        } finally { EssenceCrucibleChannelService.clear(f.server);f.close();EssenceConfigManager.install(previous); }
    }
    private static void manyInfusersAndRecipePaths() throws Exception {
        var jobField=EssenceInfuserBlockEntity.class.getDeclaredField("paidJob");jobField.setAccessible(true);
        var f=new ProjectileNativeInterceptionTest.Fixture();NativeGuardOutcomeTest.registry(f);
        f.level.memoryMachines=new HashMap<>();f.level.memoryBlocks=new HashMap<>();
        var source=crucible(f,new BlockPos(5,0,0),f.player);
        for(var type:EssenceTypes.ORDERED)f.saved.addCrucibleStored(f.player.getUUID(),type,5_000_000L);
        List<EssenceInfuserBlockEntity> many=new ArrayList<>();
        for(int i=0;i<8;i++) {
            var machine=preparedInfuser(f,new BlockPos(i/4,0,i%4+1),source,new ItemStack(EssenceInfuserContent.LATENT_NUGGET.get(),2));
            many.add(machine);
        }
        tickChannels(f,source,many,20);
        for(var machine:many)check(jobPaid(jobField,machine)==15 && machine.isCurrentLinkValid(),"Eight actual Infusers share one source without a count limit: "+jobPaid(jobField,machine)+" status "+machine.statusCode());
        long before=jobPaid(jobField,many.get(0));
        for(int i=4;i<8;i++)many.get(i).setProcessingEnabled(f.player,false);
        tickChannels(f,source,many,20);
        check(jobPaid(jobField,many.get(0))==before+30,"Stopped Infusers release shares immediately");
        source.startChanneling(f.player);f.player.setHealth(0);tickChannels(f,source,many,1);
        check(!source.isChanneling() && many.get(0).processingEnabled(),"Death invalidates player sessions without disarming automation");
        f.close();
        f=new ProjectileNativeInterceptionTest.Fixture();NativeGuardOutcomeTest.registry(f);
        f.level.memoryMachines=new HashMap<>();f.level.memoryBlocks=new HashMap<>();
        source=crucible(f,new BlockPos(5,0,0),f.player);
        for(var type:EssenceTypes.ORDERED)f.saved.addCrucibleStored(f.player.getUUID(),type,5_000_000L);
        var equipment=new ItemStack(com.mistaboom.essence_ascendance.item.AscendanceItems.ASCENDANCE_FISHING_ROD.get());
        com.mistaboom.essence_ascendance.equipment.EquipmentFishingRodService.refreshNativeDurability(equipment);
        var equipmentRecipe=EquipmentInfusionRecipe.forWorkpiece(equipment).orElseThrow();
        var equipmentMachine=preparedInfuser(f,new BlockPos(0,0,1),source,equipment);
        equipmentMachine.setItem(EssenceInfuserBlockEntity.COMPONENT_SLOT,new ItemStack(EssenceInfuserContent.ASCENDANCE_MATRIX.get(),equipmentRecipe.matrixCount()));
        var focusMachine=preparedInfuser(f,new BlockPos(0,0,2),source,new ItemStack(EssencePylonContent.ESSENCE_FOCUS.get()));
        var repairInput=equipment.copy();repairInput.setDamageValue(Math.max(1,repairInput.getMaxDamage()/2));
        var repairRecipe=RepairInfusionRecipe.forWorkpiece(repairInput).orElseThrow();
        var repairMachine=preparedInfuser(f,new BlockPos(0,0,3),source,repairInput);
        var essentiumMachine=preparedInfuser(f,new BlockPos(0,0,4),source,new ItemStack(EssenceInfuserContent.LATENT_NUGGET.get(),2));
        List<EssenceInfuserBlockEntity> all=List.of(equipmentMachine,focusMachine,repairMachine,essentiumMachine);
        source.startChanneling(f.player);long funds=poolWhole(f,f.player);
        tickChannels(f,source,all,20);
        check(EquipmentInfusionData.totalContributed(equipmentMachine.getItem(EssenceInfuserBlockEntity.INPUT_SLOT))==24,"Actual equipment workpiece uses shared grant");
        check(FocusInfusionData.totalContributed(focusMachine.getItem(EssenceInfuserBlockEntity.INPUT_SLOT))==24,"Actual flexible Focus workpiece uses shared grant");
        check(jobPaid(jobField,repairMachine)==24 && repairRecipe.essenceRequired()>24,"Actual repair accumulates partial grant without repairing early");
        check(jobPaid(jobField,essentiumMachine)==24 && availableTotal(f,f.player)==24 && funds-poolWhole(f,f.player)==120,"All four recipe paths plus player conserve one physical budget");
        source.stopChanneling(f.player.getUUID());
        long repairCost=repairRecipe.essenceRequired();
        int t=0;while(repairMachine.getItem(EssenceInfuserBlockEntity.OUTPUT_SLOT).isEmpty() && t++<3000)tickChannels(f,source,all,1);
        check(t<3000 && repairMachine.getItem(EssenceInfuserBlockEntity.OUTPUT_SLOT).getDamageValue()==0 && jobPaid(jobField,repairMachine)==0,"Repair completes only after exact payment and its processing interval");
        check(repairCost>24 && !repairMachine.processingEnabled(),"Completed repair releases its channel");
        f.close();
    }
    private static void stageSevenContinuity() throws Exception {
        int initial = checks;
        var f = new ProjectileNativeInterceptionTest.Fixture(); NativeGuardOutcomeTest.registry(f);
        f.level.memoryMachines = new HashMap<>(); f.level.memoryBlocks = new HashMap<>();
        try {
            var a = f.player; var b = f.player(Vec3.ZERO, "job_recipient"); var outsider = f.player(Vec3.ZERO, "outsider");
            var pool = f.saved.nativeMembership().create(a.getUUID(), "Continuity");
            var join = f.saved.nativeMembership().request(b.getUUID(), pool, true);
            f.saved.nativeMembership().decide(a.getUUID(), join.token(), true);
            var source = crucible(f, new BlockPos(5,0,0), a);
            for (var type : EssenceTypes.ORDERED) f.saved.reservoirs().credit(a.getUUID(),
                    Map.of(type.id(), BigInteger.valueOf(5_000_000).multiply(ReservoirService.SCALE)), ReservoirService.NONE);
            var paidField = EssenceInfuserBlockEntity.class.getDeclaredField("paidJob"); paidField.setAccessible(true);
            var tickField = EssenceInfuserBlockEntity.class.getDeclaredField("processingTicks"); tickField.setAccessible(true);
            var machine = preparedInfuser(f, new BlockPos(0,0,1), source,
                    new ItemStack(EssenceInfuserContent.LATENT_NUGGET.get(), 3));
            int ticks = 0;
            while (machine.getItem(EssenceInfuserBlockEntity.OUTPUT_SLOT).getCount() < 3 && ticks++ < 5000)
                tickChannels(f, source, List.of(machine), 1);
            check(ticks < 5000 && machine.processingEnabled() && machine.getItem(EssenceInfuserBlockEntity.INPUT_SLOT).isEmpty(),
                    "Three consecutive actual Essentium jobs complete with no unrelated monetary mutation or player channel");
            check(!machine.machineBinding().recipientValid(f.saved) && paidField.get(machine) == null,
                    "Essentium has no artificial recipient or residual paid bank");
            var manual = sharedSourceInfuser(f, new BlockPos(1,0,2), a);
            var manualFocus = new ItemStack(EssencePylonContent.ESSENCE_FOCUS.get()); EssenceFocusData.setTier(manualFocus, EssenceFocusTier.DORMANT);
            manual.setItem(2, manualFocus);
            var manualArtifact = new ItemStack(com.mistaboom.essence_ascendance.item.AscendanceItems.ASCENDANCE_FISHING_ROD.get());
            com.mistaboom.essence_ascendance.equipment.EquipmentFishingRodService.refreshNativeDurability(manualArtifact);
            manual.setItem(0, manualArtifact); NativeInfuserStartTest.start(manual, b);
            check(com.mistaboom.essence_ascendance.equipment.SoulboundEquipmentData.isBoundTo(manual.getItem(0), b.getUUID()),
                    "Confirmed unbound equipment belongs to the actual starter, independently of the placer");
            var artifact = new ItemStack(com.mistaboom.essence_ascendance.item.AscendanceItems.ASCENDANCE_FISHING_ROD.get());
            com.mistaboom.essence_ascendance.equipment.EquipmentFishingRodService.refreshNativeDurability(artifact);
            artifact.setDamageValue(Math.max(1, artifact.getMaxDamage()/2));
            var repair = preparedInfuser(f, new BlockPos(0,0,2), source, artifact);
            repair.setProcessingEnabled(b, true);
            // The starter was a in preparedInfuser, so it is already claimed; a teammate cannot retarget it.
            check(com.mistaboom.essence_ascendance.equipment.SoulboundEquipmentData.isBoundTo(repair.getItem(0), a.getUUID()),
                    "Repeated Start preserves the initial manual starter's recipient");
            tickChannels(f, source, List.of(repair), 8);
            var job = (InfusionJobProgress) paidField.get(repair); int elapsed = tickField.getInt(repair);
            check(job != null && !job.paid().isEmpty() && job.funding().keySet().equals(Set.of(pool)) && job.recipient().equals(a.getUUID()),
                    "Actual repair records exact committed funding and recipient in the existing paid job");
            repair.setProcessingEnabled(a, false);
            check(paidField.get(repair) == job && tickField.getInt(repair) == elapsed, "Explicit Stop preserves paid work and elapsed timer");
            repair.setProcessingEnabled(b, true);
            BigInteger beforeOffense = f.saved.reservoirs().read(a.getUUID()).units(EssenceTypes.OFFENSE.id());
            var saved = repair.saveWithFullMetadata(f.level.registryAccess());
            repair.setRemoved();
            var reloaded = sharedSourceInfuser(f, repair.getBlockPos(), a); reloaded.loadWithComponents(saved, f.level.registryAccess());
            check(((InfusionJobProgress)paidField.get(reloaded)).save(f.level.registryAccess()).equals(job.save(f.level.registryAccess()))
                    && tickField.getInt(reloaded) == elapsed, "Restart retains job identity, attribution, recipient and timer exactly");
            check(!reloaded.recoverWorkpiece(outsider, 0), "Foreign recovery is denied by actual soulbound owner");
            f.saved.nativeMembership().transfer(a.getUUID(), b.getUUID()); f.saved.nativeMembership().leave(a.getUUID());
            check(!reloaded.canPlayerUse(a), "Former member has no general inventory or spending access");
            check(reloaded.recoverWorkpiece(a, 0), "Former member recovers only their actual soulbound paid workpiece");
            ItemStack recovered = ItemStack.EMPTY;
            for (int slot = 0; slot < a.getInventory().getContainerSize(); slot++) {
                if (!InfusionJobProgress.carried(a.getInventory().getItem(slot)).isEmpty()) {
                    recovered = a.getInventory().removeItemNoUpdate(slot); break;
                }
            }
            check(!recovered.isEmpty() && paidField.get(reloaded) == null && reloaded.getItem(0).isEmpty()
                    && !reloaded.recoverWorkpiece(a, 0), "Paid repair moves once with no second machine credit or duplicate recovery");
            check(f.saved.reservoirs().read(a.getUUID()).units(EssenceTypes.OFFENSE.id())
                    .add(f.saved.reservoirs().read(b.getUUID()).units(EssenceTypes.OFFENSE.id())).equals(beforeOffense),
                    "Departure and recovery do not refund committed repair value, including exact fractional shares");
            ItemStack ownOutput = artifact.copy();
            com.mistaboom.essence_ascendance.equipment.SoulboundEquipmentData.bind(ownOutput, a.getUUID(), "");
            reloaded.setItem(1, ownOutput);
            check(reloaded.recoverWorkpiece(a, 1) && reloaded.getItem(1).isEmpty() && !reloaded.canPlayerUse(a),
                    "Former member's output recovery does not restore general team inventory access");
            reloaded.setItem(1, new ItemStack(net.minecraft.world.item.Items.DIAMOND));
            check(!reloaded.recoverWorkpiece(a, 1), "Own-item recovery cannot take unbound/shared output");
            var personalSource = crucible(f, new BlockPos(4,0,0), a);
            var destination = sharedSourceInfuser(f, new BlockPos(0,0,3), a);
            var focus = new ItemStack(EssencePylonContent.ESSENCE_FOCUS.get()); EssenceFocusData.setTier(focus, EssenceFocusTier.DORMANT);
            destination.setItem(2, focus); destination.setItem(0, recovered);
            check(((InfusionJobProgress)paidField.get(destination)).identity().equals(job.identity())
                    && tickField.getInt(destination) == elapsed && !destination.processingEnabled(),
                    "The same repair progress and timer travel to another funding identity, requiring explicit restart");
            destination.setLink(a, MachineLink.Mode.LOCKED, new MachineLink.Source(f.level.dimension().location(), personalSource.getBlockPos()));
            destination.setProcessingEnabled(a, true); tickChannels(f, personalSource, List.of(destination), 4);
            var funded = (InfusionJobProgress)paidField.get(destination);
            check(funded.identity().equals(job.identity()) && funded.funding().size() == 2,
                    "Explicit resumption preserves prior payment and attributes only additional work to the new pool");
            int beforeJoinTicks = tickField.getInt(destination);
            var rejoin = f.saved.nativeMembership().request(a.getUUID(), pool, true);
            f.saved.nativeMembership().decide(b.getUUID(), rejoin.token(), true);
            check(destination.canPlayerUse(b) && destination.machineBinding().pool().equals(pool)
                    && !destination.processingEnabled() && paidField.get(destination) == funded
                    && tickField.getInt(destination) == beforeJoinTicks,
                    "Joining a team automatically changes personal machine funding without discarding paid work or elapsed time");
            check(MachineProtection.canBreak(reloaded, b) && MachineProtection.canBreak(reloaded, outsider),
                    "Team ownership permits vanilla physical theft by an opposing player");
            var snapshotJob = funded.save(f.level.registryAccess()); int snapshotTicks = tickField.getInt(destination);
            ItemStack pickup = MachineProtection.takePickup(destination);
            check(!pickup.isEmpty() && MachineProtection.takePickup(destination).isEmpty() && destination.isEmpty(),
                    "Physical pickup transfers all custody once and prevents secondary inventory drops");
            var replaced = new EssenceInfuserBlockEntity(destination.getBlockPos(), destination.getBlockState());
            install(f, replaced); // Vanilla applies item data BEFORE setPlacedBy initializes a fresh binding.
            check(net.minecraft.world.item.BlockItem.updateCustomBlockEntityTag(f.level, outsider, replaced.getBlockPos(), pickup),
                    "Actual vanilla BlockItem placement applies the contents and paid-work snapshot");
            replaced.getBlockState().getBlock().setPlacedBy(f.level, replaced.getBlockPos(), replaced.getBlockState(), outsider, pickup);
            check(replaced.machineBinding().pool().equals(f.saved.resolvePool(outsider.getUUID()).requireReady().pool())
                    && replaced.machineBinding().placer().equals(outsider.getUUID()) && !replaced.processingEnabled()
                    && replaced.canPlayerUse(outsider) && !replaced.canPlayerUse(b) && tickField.getInt(replaced) == snapshotTicks
                    && ((InfusionJobProgress)paidField.get(replaced)).save(f.level.registryAccess()).equals(snapshotJob),
                    "Stolen-machine placement gives the new placer ownership while retaining contents, paid provenance and timer");
            check(replaced.getBlockState().getPistonPushReaction() == net.minecraft.world.level.material.PushReaction.BLOCK,
                    "Infuser piston policy blocks unauthenticated movement");
            var port = sharedSourceInfuser(f, new BlockPos(1,0,1), b);
            port.setItem(1, new ItemStack(net.minecraft.world.item.Items.DIAMOND, 3));
            check(port.canTakeItemThroughFace(1, port.getItem(1), net.minecraft.core.Direction.DOWN), "Common output extraction is always available to physical automation");
            var unbound = new ItemStack(com.mistaboom.essence_ascendance.item.AscendanceItems.ASCENDANCE_FISHING_ROD.get());
            com.mistaboom.essence_ascendance.equipment.EquipmentFishingRodService.refreshNativeDurability(unbound);
            check(port.canPlaceItemThroughFace(0, unbound, null)
                    && !com.mistaboom.essence_ascendance.equipment.SoulboundEquipmentData.isSoulbound(unbound),
                    "Automation accepts unbound equipment without inferring or claiming its recipient");
            platformPorts(port, b);
            var automatic = sharedSourceInfuser(f, new BlockPos(1,0,3), b);
            automatic.setItem(2, manualFocus.copy()); automatic.refreshLink(); automatic.setProcessingEnabled(b, true);
            automatic.setItem(0, unbound.copy());
            check(!automatic.processingEnabled() && automatic.statusCode() == EssenceInfuserBlockEntity.STATUS_STOPPED,
                    "Equipment arrival disarms an otherwise armed factory until explicit Start");
            automatic.setItem(3, new ItemStack(EssenceInfuserContent.ASCENDANCE_MATRIX.get(), EquipmentInfusionRecipe.forWorkpiece(unbound).orElseThrow().matrixCount()));
            var beforeUnstarted = f.saved.save(new net.minecraft.nbt.CompoundTag(), f.level.registryAccess());
            tickChannels(f, source, List.of(automatic), 4);
            check(EquipmentInfusionData.totalContributed(automatic.getItem(0)) == 0
                    && !com.mistaboom.essence_ascendance.equipment.SoulboundEquipmentData.isSoulbound(automatic.getItem(0))
                    && f.saved.save(new net.minecraft.nbt.CompoundTag(), f.level.registryAccess()).equals(beforeUnstarted),
                    "Waiting equipment neither binds nor pays when materials are inserted");
            NativeInfuserStartTest.start(automatic, b);
            tickChannels(f, source, List.of(automatic), 4);
            check(EquipmentInfusionData.totalContributed(automatic.getItem(0)) > 0
                    && com.mistaboom.essence_ascendance.equipment.SoulboundEquipmentData.isBoundTo(automatic.getItem(0), b.getUUID()),
                    "Confirmed equipment work uses the actual starter and common scheduler");
            ItemStack malformed = automatic.getItem(0).copy();
            net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, malformed, outer -> {
                var progress = outer.getCompound("essence_ascendance_equipment_infusion");
                var contributions = progress.getCompound("contributions"); contributions.putLong(EssenceTypes.OFFENSE.id().toString(), -1);
                progress.put("contributions", contributions); outer.put("essence_ascendance_equipment_infusion", progress);
            });
            automatic.setItem(0, malformed.copy());
            automatic.setProcessingEnabled(b, true);
            var moneyBeforeInvalid = f.saved.save(new net.minecraft.nbt.CompoundTag(), f.level.registryAccess());
            tickChannels(f, source, List.of(automatic), 4);
            check(automatic.statusCode() == EssenceInfuserBlockEntity.STATUS_INVALID_INPUT && ItemStack.matches(automatic.getItem(0), malformed)
                    && f.saved.save(new net.minecraft.nbt.CompoundTag(), f.level.registryAccess()).equals(moneyBeforeInvalid),
                    "Invalid portable equipment progress is preserved and cannot be overwritten, paid again or completed");
            System.out.println("Stage 7 continuity/ports: " + (checks-initial) + " assertions passed");
        } finally { f.close(); }
    }
    /** Actual platform handler mechanics; no claim of third-party pipe interoperability. */
    private static void platformPorts(EssenceInfuserBlockEntity port, net.minecraft.server.level.ServerPlayer actor) throws Exception {
        var funds = EssenceSavedData.get(actor.server).save(new net.minecraft.nbt.CompoundTag(), actor.registryAccess());
        boolean fabric = dev.architectury.platform.Platform.isModLoaded("fabricloader");
        String adapter = "com.mistaboom.essence_ascendance." + (fabric ? "fabric.EssenceInfuserFabricItemStorage" : "neoforge.EssenceInfuserNeoForgeItemHandler");
        var constructor = Class.forName(adapter).getDeclaredConstructor(EssenceInfuserBlockEntity.class); constructor.setAccessible(true);
        Object handler = constructor.newInstance(port);
        var original = port.saveWithFullMetadata(port.getLevel().registryAccess());
        if (fabric) {
            var variantType = Class.forName("net.fabricmc.fabric.api.transfer.v1.item.ItemVariant");
            var txType = Class.forName("net.fabricmc.fabric.api.transfer.v1.transaction.Transaction");
            var txContext = Class.forName("net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext");
            Object diamond = variantType.getMethod("of", ItemStack.class).invoke(null, port.getItem(1));
            var extract = handler.getClass().getMethod("extract", variantType, long.class, txContext); extract.setAccessible(true);
            var insert = handler.getClass().getMethod("insert", variantType, long.class, txContext); insert.setAccessible(true);
            var open = txType.getMethod("openOuter");
            try (var tx = (AutoCloseable)open.invoke(null)) { check((long)extract.invoke(handler, diamond, 1L, tx) == 1, "Fabric output extraction is available without an enable toggle"); }
            check(port.saveWithFullMetadata(port.getLevel().registryAccess()).equals(original), "Aborted Fabric extraction restores exact inventory and job state");
            try (var tx = (AutoCloseable)open.invoke(null)) { extract.invoke(handler, diamond, 1L, tx); txType.getMethod("commit").invoke(tx); }
            check(port.getItem(1).getCount() == 2, "Committed Fabric extraction removes exactly once");
            Object carrier = variantType.getMethod("of", ItemStack.class).invoke(null, new ItemStack(EssenceInfuserContent.LATENT_NUGGET.get()));
            original = port.saveWithFullMetadata(port.getLevel().registryAccess());
            try (var tx = (AutoCloseable)open.invoke(null)) { check((long)insert.invoke(handler, carrier, 2L, tx) == 2, "Fabric can provisionally feed the Essentium pipeline"); }
            check(port.saveWithFullMetadata(port.getLevel().registryAccess()).equals(original), "Aborted insertion publishes no context, timer or recipient effects");
            try (var tx = (AutoCloseable)open.invoke(null)) { insert.invoke(handler, carrier, 2L, tx); txType.getMethod("commit").invoke(tx); }
        } else {
            var extract = handler.getClass().getMethod("extractItem", int.class, int.class, boolean.class); extract.setAccessible(true);
            var insert = handler.getClass().getMethod("insertItem", int.class, ItemStack.class, boolean.class); insert.setAccessible(true);
            check(((ItemStack)extract.invoke(handler, 1, 1, true)).getCount() == 1, "NeoForge output extraction is available without an enable toggle");
            check(port.saveWithFullMetadata(port.getLevel().registryAccess()).equals(original), "NeoForge extraction simulation preserves exact inventory and job state");
            extract.invoke(handler, 1, 1, false); check(port.getItem(1).getCount() == 2, "Executed NeoForge extraction removes exactly once");
            original = port.saveWithFullMetadata(port.getLevel().registryAccess());
            check(((ItemStack)insert.invoke(handler, 0, new ItemStack(EssenceInfuserContent.LATENT_NUGGET.get(),2), true)).isEmpty(), "NeoForge can simulate feeding Essentium");
            check(port.saveWithFullMetadata(port.getLevel().registryAccess()).equals(original), "Simulated insertion publishes no context, timer or recipient effects");
            insert.invoke(handler, 0, new ItemStack(EssenceInfuserContent.LATENT_NUGGET.get(),2), false);
            var getter = handler.getClass().getMethod("getStackInSlot", int.class); getter.setAccessible(true);
            ((ItemStack)getter.invoke(handler,1)).setCount(0);
            check(port.getItem(1).getCount()==2, "NeoForge stack views cannot mutate machine contents");
        }
        check(port.getItem(0).getCount()==2, "Successful platform insertion feeds the existing input slot");
        var stale = new EssenceInfuserMenu(17, actor.getInventory(), port); actor.containerMenu = stale;
        port.bindOnPlacement(actor);
        check(!stale.stillValid(actor) && !stale.clickMenuButton(actor, EssenceInfuserMenu.BUTTON_TOGGLE_PROCESSING),
                "A new physical ownership revision revokes stale menus and requests");
        check(port.canPlaceItemThroughFace(0, new ItemStack(EssenceInfuserContent.LATENT_NUGGET.get()),null)
                && port.canTakeItemThroughFace(1,port.getItem(1),null)
                && !port.canTakeItemThroughFace(0,port.getItem(0),null)
                && !port.canPlaceItemThroughFace(1,new ItemStack(EssenceInfuserContent.LATENT_NUGGET.get()),null),
                "Always-open automation retains ordinary input and output slot rules");
        check(net.minecraft.world.level.block.entity.HopperBlockEntity.addItem(null, port,
                new ItemStack(net.minecraft.world.item.Items.DIAMOND), net.minecraft.core.Direction.UP).getCount()==1,
                "Actual vanilla hopper insertion still rejects items without an input recipe");
        check(net.minecraft.world.level.block.entity.HopperBlockEntity.addItem(null, port,
                new ItemStack(EssenceInfuserContent.LATENT_NUGGET.get()), net.minecraft.core.Direction.UP).isEmpty()
                && port.getItem(0).getCount()==3, "Actual vanilla hopper insertion feeds the existing Essentium slot automatically");
        var hopper = new net.minecraft.world.level.block.entity.HopperBlockEntity(new BlockPos(2,0,1), net.minecraft.world.level.block.Blocks.HOPPER.defaultBlockState());
        hopper.setLevel(port.getLevel());
        var pull = net.minecraft.world.level.block.entity.HopperBlockEntity.class.getDeclaredMethod("tryTakeInItemFromSlot",
                net.minecraft.world.level.block.entity.Hopper.class, net.minecraft.world.Container.class, int.class, net.minecraft.core.Direction.class);
        pull.setAccessible(true);
        check((boolean)pull.invoke(null, hopper, port, 1, net.minecraft.core.Direction.DOWN)
                && port.getItem(1).getCount()==1 && hopper.getItem(0).getCount()==1,
                "Actual vanilla hopper extraction takes one item without machine ownership or enable settings");
        var restored = new EssenceInfuserBlockEntity(port.getBlockPos(), port.getBlockState()); restored.setLevel(port.getLevel());
        restored.loadWithComponents(port.saveWithFullMetadata(port.getLevel().registryAccess()), port.getLevel().registryAccess());
        check(restored.machineBinding().pool().equals(port.machineBinding().pool())
                && ItemStack.matches(restored.getItem(0), port.getItem(0))
                && ItemStack.matches(restored.getItem(1), port.getItem(1)),
                "Restart preserves automatic material input and output with their machine ownership");
        check(EssenceSavedData.get(actor.server).save(new net.minecraft.nbt.CompoundTag(), actor.registryAccess()).equals(funds),
                "Simulations, aborts and real item transfers cannot spend or publish essence");
    }
    private static EssenceInfuserBlockEntity preparedInfuser(ProjectileNativeInterceptionTest.Fixture f,BlockPos pos,
                                                            EssenceCrucibleBlockEntity source,ItemStack input) throws Exception {
        var machine=sharedSourceInfuser(f,pos,f.player);
        var focus=new ItemStack(EssencePylonContent.ESSENCE_FOCUS.get());EssenceFocusData.setTier(focus,EssenceFocusTier.DORMANT);
        machine.setItem(EssenceInfuserBlockEntity.FOCUS_SLOT,focus);machine.setItem(EssenceInfuserBlockEntity.INPUT_SLOT,input);
        machine.setLink(f.player,MachineLink.Mode.LOCKED,new MachineLink.Source(f.level.dimension().location(),source.getBlockPos()));
        NativeInfuserStartTest.start(machine,f.player);return machine;
    }
    private static long jobPaid(java.lang.reflect.Field field,EssenceInfuserBlockEntity machine) throws Exception {
        var progress=(InfusionJobProgress)field.get(machine);return progress==null?0:InfusionPayments.total(progress.paid());
    }
    private static long poolWhole(ProjectileNativeInterceptionTest.Fixture f,net.minecraft.server.level.ServerPlayer actor) {
        return f.saved.reservoirs().read(actor.getUUID()).units().values().stream().reduce(BigInteger.ZERO,BigInteger::add).divide(ReservoirService.SCALE).longValueExact();
    }
    private static long availableTotal(ProjectileNativeInterceptionTest.Fixture f,net.minecraft.server.level.ServerPlayer actor) {
        return EssenceTypes.ORDERED.stream().mapToLong(type->f.saved.getPlayerData(actor.getUUID()).getAvailable(type)).sum();
    }
    private static void tickChannels(ProjectileNativeInterceptionTest.Fixture f,EssenceCrucibleBlockEntity source,List<EssenceInfuserBlockEntity> machines,int ticks) {
        for(int t=0;t<ticks;t++) {
            f.level.tick++;EssenceCrucibleBlockEntity.serverTick(f.level,source.getBlockPos(),source.getBlockState(),source);
            for(var machine:machines)EssenceInfuserBlockEntity.serverTick(f.level,machine.getBlockPos(),machine.getBlockState(),machine);
            EssenceCrucibleChannelService.tick(f.server,f.level.getGameTime());
        }
    }

    private static EssenceCrucibleBlockEntity crucible(ProjectileNativeInterceptionTest.Fixture f, BlockPos pos, net.minecraft.server.level.ServerPlayer owner) {
        var machine = new EssenceCrucibleBlockEntity(pos, EssenceCrucibleContent.ESSENCE_CRUCIBLE.get().defaultBlockState());
        install(f, machine); machine.bindOnPlacement(owner); return machine;
    }
    private static void install(ProjectileNativeInterceptionTest.Fixture f, net.minecraft.world.level.block.entity.BlockEntity machine) {
        machine.setLevel(f.level); f.level.memoryMachines.put(machine.getBlockPos(), machine); f.level.memoryBlocks.put(machine.getBlockPos(), machine.getBlockState());
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); checks++; }
}
