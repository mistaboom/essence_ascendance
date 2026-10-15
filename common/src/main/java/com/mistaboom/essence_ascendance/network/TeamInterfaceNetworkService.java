package com.mistaboom.essence_ascendance.network;

import com.mistaboom.essence_ascendance.crucible.*;
import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.infuser.*;
import com.mistaboom.essence_ascendance.machine.*;
import com.mistaboom.essence_ascendance.pylon.*;
import com.mistaboom.essence_ascendance.team.*;
import dev.architectury.event.events.common.TickEvent;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.networking.NetworkManager;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.*;
import static com.mistaboom.essence_ascendance.network.TeamInterfacePayload.*;

/** Thin interface projection over existing membership, binding, linking and session policies. */
public final class TeamInterfaceNetworkService {
    private record Choice(String code, String target, Control control) {}
    private record Projection(String context, List<Text> lines, List<Choice> choices) {}
    private record Sent(AbstractContainerMenu menu, String context, List<Choice> choices, TeamInterfacePayload payload) {}
    private static final class TeamPopup {
        final AbstractContainerMenu menu;
        int view, page;
        TeamPopup(AbstractContainerMenu menu) { this.menu = menu; }
    }
    private static final Map<ServerPlayer, Sent> SENT = new WeakHashMap<>();
    private static final Map<ServerPlayer, ChannelSessionsPayload> HUD = new WeakHashMap<>();
    private static final Map<ServerPlayer, TeamPopup> POPUPS = new WeakHashMap<>();
    private static boolean initialized;
    private TeamInterfaceNetworkService() {}
    public static void init() {
        if (initialized) return;
        initialized = true;
        if (Platform.getEnvironment() == Env.SERVER) {
            NetworkManager.registerS2CPayloadType(TeamInterfacePayload.TYPE, TeamInterfacePayload.CODEC);
            NetworkManager.registerS2CPayloadType(ChannelSessionsPayload.TYPE, ChannelSessionsPayload.CODEC);
        }
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, TeamInterfaceActionPayload.TYPE, TeamInterfaceActionPayload.CODEC,
                (payload, context) -> context.queue(() -> { if (context.getPlayer() instanceof ServerPlayer player) handle(player, payload); }));
        TickEvent.PLAYER_POST.register(p -> {
            if (!(p instanceof ServerPlayer player)) return;
            if (player.tickCount % 4 == 0) syncHud(player);
            if (player.tickCount % 4 == 0) sync(player, false);
        });
        LifecycleEvent.SERVER_STOPPED.register(server -> { SENT.keySet().removeIf(p -> p.server == server); HUD.keySet().removeIf(p -> p.server == server); POPUPS.keySet().removeIf(p -> p.server == server); });
        com.mistaboom.essence_ascendance.EssenceAscendance.LOGGER.info("Registered team interface requests, authoritative menu synchronization and incoming-session HUD synchronization");
    }
    private static void syncHud(ServerPlayer player) {
        var links = EssenceCrucibleChannelService.links(player);
        var payload = new ChannelSessionsPayload(player.getId(), player.serverLevel().dimension().location(), links.size(), EssenceSavedData.get(player.server).getPlayerData(player.getUUID()).crucibleChannelSelection(),
                links.stream().limit(ChannelSessionsPayload.MAX_LINKS).map(l -> new ChannelSessionsPayload.Link(l.source().pos().asLong(), l.feedback().reason().name(), l.feedback().allocated(), l.feedback().actual())).toList());
        if ((!payload.equals(HUD.get(player)) || player.tickCount % 16 == 0) && NetworkManager.canPlayerReceive(player, ChannelSessionsPayload.TYPE)) {
            NetworkManager.sendToPlayer(player, payload); HUD.put(player, payload);
        }
    }
    public static BoundMachine machine(AbstractContainerMenu menu) {
        if (menu instanceof TeamMenu team) return team.machine();
        if (menu instanceof EssenceCrucibleMenu c) return c.serverCrucible();
        if (menu instanceof EssenceInfuserMenu i) return i.serverInfuser();
        if (menu instanceof EssencePylonMenu p) return p.serverPylon();
        return null;
    }
    /** Team membership is an overlay on the currently authorized machine inventory. */
    public static void open(ServerPlayer player, BoundMachine machine) {
        if (!nativeInterfaceAvailable(player)) { interfaceGuidance(player); return; }
        var menu = player.containerMenu;
        if (menu instanceof TeamMenu || machine == null || machine(menu) != machine
                || !ServerMenuAccess.isCurrent(player, menu) || !machine.authorize(player, MachineAction.USE)) return;
        POPUPS.put(player, new TeamPopup(menu));
        SENT.remove(player);
        sync(player, true);
    }
    public static void interfaceGuidance(ServerPlayer player) {
        player.displayClientMessage(nativeInterfaceAvailable(player)
                ? Component.translatable("gui.essence_ascendance.team.open_machine_guidance") : providerGuidance(player), false);
    }
    private static boolean nativeInterfaceAvailable(ServerPlayer player) {
        return EssenceSavedData.get(player.server).membershipAuthority() == MembershipAuthority.NATIVE
                && !Platform.isModLoaded("ftbteams");
    }
    public static Component providerGuidance(ServerPlayer player) {
        Text guidance = providerGuidanceText(player);
        return Component.translatable(guidance.key(), guidance.arguments().toArray());
    }
    private static Text providerGuidanceText(ServerPlayer player) {
        var saved = EssenceSavedData.get(player.server);
        MembershipAuthority authority = saved.membershipAuthority();
        // An explicitly selected, installed provider remains authoritative when installed.
        boolean activeInstalled = authority == MembershipAuthority.FTB_TEAMS && Platform.isModLoaded("ftbteams");
        MembershipAuthority installed = activeInstalled ? authority
                : Platform.isModLoaded("ftbteams") ? MembershipAuthority.FTB_TEAMS : authority;
        if (installed != authority)
            return Text.of("provider_setup", installed.displayName(), authority.displayName(), installed.id());
        String readiness = MembershipRuntime.interfaceState(player.server, saved.resolvePool(player.getUUID()));
        if (!readiness.equals("READY_SOLO") && !readiness.equals("READY_TEAM"))
            return Text.of("provider_unavailable", authority.displayName());
        return Text.of("provider_guidance", authority.displayName());
    }
    public static void openRestricted(ServerPlayer player, BoundMachine machine) {
        open(player, machine, TeamMenu.Kind.ACCESS);
    }
    public static void open(ServerPlayer player, BoundMachine machine, TeamMenu.Kind kind) {
        if (kind == TeamMenu.Kind.TEAM) { open(player, machine); return; }
        if (!player.server.isSameThread() || !player.isAlive() || player.isSpectator()
                || machine == null || !ServerMenuAccess.canReach(player, (BlockEntity) machine)) return;
        POPUPS.remove(player); SENT.remove(player);
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new TeamMenu(id, inventory, machine, TeamMenu.Kind.ACCESS),
                Component.translatable("gui.essence_ascendance.team.access_title")));
        sync(player, true);
    }
    private static void handle(ServerPlayer player, TeamInterfaceActionPayload payload) {
        var menu = player.containerMenu;
        if (menu.containerId != payload.menuId()) return;
        if (!ServerMenuAccess.isCurrent(player, menu)) { SENT.remove(player); POPUPS.remove(player); return; }
        if (payload.action() == TeamInterfaceActionPayload.OPEN) { open(player, machine(menu)); return; }
        if (payload.action() == TeamInterfaceActionPayload.CLOSE) {
            POPUPS.remove(player); SENT.remove(player); return;
        }
        if (payload.action() == TeamInterfaceActionPayload.REQUEST) {
            if (payload.page() < 0) return;
            if (menu instanceof TeamMenu team) {
                if (team.kind != TeamMenu.Kind.ACCESS || payload.view() != 1) return;
                team.page = payload.page();
            } else {
                TeamPopup popup = POPUPS.get(player);
                if (popup == null || popup.menu != menu || payload.view() != popup.view) return;
                popup.page = payload.page();
            }
            sync(player, true); return;
        }
        // Refresh permissions, membership, item/device identity and choices before comparing the receipt.
        sync(player, false);
        var sent = SENT.get(player);
        if (sent == null || sent.menu != menu || !sent.payload.receipt().equals(payload.receipt())
                || payload.view() != sent.payload.view() || payload.page() != sent.payload.page()
                || payload.action() < 0 || payload.action() >= sent.choices.size()) { sync(player, true); return; }
        Choice choice = sent.choices.get(payload.action());
        boolean completed = false;
        try { execute(player, menu, choice, payload.input()); completed = true; }
        catch (IllegalArgumentException | IllegalStateException | ArithmeticException denied) {
            player.displayClientMessage(Component.translatable("gui.essence_ascendance.team.action_denied"), false);
        }
        if (player.containerMenu == menu && ServerMenuAccess.isCurrent(player, menu)) sync(player, true);
        else {
            SENT.remove(player); POPUPS.remove(player);
            // A successful create/disband changes the binding receipt. Renew this authorized physical menu
            // instead of making the player reopen their own device; stale controls remain invalidated.
            if (completed && player.containerMenu == menu && !(menu instanceof TeamMenu)) openMachine(player, machine(menu));
        }
    }
    private static boolean openMachine(ServerPlayer player, BoundMachine machine) {
        if (machine == null || !machine.authorize(player, MachineAction.USE)
                || !ServerMenuAccess.canReach(player, (BlockEntity) machine)
                || !(machine instanceof net.minecraft.world.MenuProvider provider)) return false;
        player.openMenu(provider);
        if (machine instanceof EssenceCrucibleBlockEntity) EssenceCrucibleNetworkService.forceSync(player);
        if (machine instanceof EssencePylonBlockEntity) EssencePylonNetworkService.forceSync(player);
        return true;
    }
    private static Choice choice(String code, String target, String key, boolean confirmation, boolean input, String... args) {
        String warning = switch (code) {
            case "create", "request", "approve" -> "sharing";
            case "request_personal", "approve_personal" -> "personal_request";
            case "leave", "remove", "disband" -> "departure";
            case "recover_machine" -> "recovery";
            case "transfer" -> "management";
            default -> "action";
        };
        return new Choice(code, target, new Control(Text.of(key, args), Text.of("confirm." + warning), confirmation, input));
    }
    private static String name(ServerPlayer viewer, UUID id) {
        return MachineOwnerDisplay.playerName(viewer.server, id);
    }
    private static String destinationName(ServerPlayer viewer, NativeMembershipService.Request request) {
        return request.target().kind() == PoolId.Kind.TEAM ? MachineOwnerDisplay.teamName(viewer.server, request.target())
                : name(viewer, EssenceSavedData.get(viewer.server).personalOwner(request.target()));
    }
    private static String bindingContext(BoundMachine machine) {
        var tag = new net.minecraft.nbt.CompoundTag(); machine.machineBinding().save(tag); return tag.toString();
    }
    private static Projection project(ServerPlayer player, AbstractContainerMenu current, int view, int page) {
        var saved = EssenceSavedData.get(player.server); UUID actor = player.getUUID();
        var resolved = saved.resolvePool(actor); var authority = saved.membershipAuthority();
        var own = resolved.snapshot().orElse(null); BoundMachine machine = machine(current);
        List<Text> lines = new ArrayList<>(); List<Choice> choices = new ArrayList<>();
        String context = authority + ":" + resolved + ":" + (machine == null ? "" : bindingContext(machine));
        if (view == 1) {
            lines.add(Text.of("access_denied"));
            if (machine != null && machine.authorize(player, MachineAction.USE)) {
                lines.set(0, Text.of("access_granted"));
                choices.add(choice("open_machine", "", "open_machine", false, false));
            } else if (!nativeInterfaceAvailable(player)) lines.add(providerGuidanceText(player));
            else if (machine != null && machine.machineBinding().pool() != null && own != null) {
                var target = machine.machineBinding().pool();
                var requests = saved.nativeMembership().requests(actor); context += requests;
                var pending = requests.stream().filter(r -> r.applicant().equals(actor)).findFirst();
                if (pending.isPresent()) {
                    var request = pending.get();
                    lines.add(Text.of(request.target().kind() == PoolId.Kind.TEAM ? "pending_team" : "pending_personal", destinationName(player, request)));
                    choices.add(choice("cancel", request.token().toString(), "cancel", false, false));
                } else if (!own.isSoloFor(actor)) lines.add(Text.of("access_other_team"));
                else if (target.authority() == MembershipAuthority.NATIVE && target.kind() == PoolId.Kind.TEAM) {
                    lines.add(Text.of("access_request_team", MachineOwnerDisplay.teamName(player.server, target)));
                    choices.add(choice("request", target.id().toString(), "request_access", true, false));
                } else if (machine.machineBinding().personalOwner() != null) {
                    UUID owner = machine.machineBinding().personalOwner();
                    lines.add(Text.of("access_request_personal", name(player, owner)));
                    choices.add(choice("request_personal", owner.toString(), "request_access", true, false));
                } else lines.add(Text.of("unavailable"));
            } else lines.add(Text.of("machine_help.unbound"));
            if (machine != null && machine.canRecover(player)) choices.add(choice("recover_machine", "", "recover_machine", true, false));
            if (machine != null && machine.machineBinding().pool() != null) {
                var binding = machine.machineBinding();
                lines.add(1, binding.pool().kind() == PoolId.Kind.TEAM
                        ? Text.of("access_team_owner", MachineOwnerDisplay.teamName(player.server, binding.pool()))
                        : Text.of("access_player_owner", name(player, binding.personalOwner())));
            }
            return new Projection(context, lines, choices);
        }
        if (!nativeInterfaceAvailable(player)) {
            lines.add(providerGuidanceText(player)); return new Projection(context, lines, choices);
        }
        if (own == null) { lines.add(Text.of("unavailable")); return new Projection(context, lines, choices); }
        var nativeService = saved.nativeMembership();
        var requests = nativeService.requests(actor);
        context += requests;
        boolean solo = own.isSoloFor(actor), owner = own.allows(actor, PoolCapability.MANAGE);
        if (view == 2) {
            if (requests.isEmpty()) lines.add(Text.of("no_requests"));
            else {
                var request = requests.get(Math.min(page, requests.size() - 1));
                if (request.applicant().equals(actor)) {
                    lines.add(Text.of(request.target().kind() == PoolId.Kind.TEAM ? "pending_team" : "pending_personal", destinationName(player, request)));
                    choices.add(choice("cancel", request.token().toString(), "cancel", false, false));
                } else {
                    boolean personal = request.target().kind() == PoolId.Kind.SOLO;
                    lines.add(Text.of(personal ? "incoming_personal" : "incoming_team", name(player, request.applicant()), MachineOwnerDisplay.teamName(player.server, own.pool())));
                    choices.add(choice(personal ? "approve_personal" : "approve", request.token().toString(), personal ? "create_approve" : "approve_request", true, personal));
                    choices.add(choice("decline", request.token().toString(), "decline_request", false, false));
                }
            }
            choices.add(choice("back", "", "back", false, false));
        } else if (view == 3 && !solo && owner && own.members().size() > 1) {
            var members = own.members().stream().filter(m -> !m.equals(actor)).toList();
            UUID member = members.get(Math.min(page, members.size() - 1));
            lines.add(Text.of("your_team", MachineOwnerDisplay.teamName(player.server, own.pool())));
            lines.add(Text.of("member_member", name(player, member)));
            choices.add(choice("transfer", member.toString(), "transfer", true, false, name(player, member)));
            choices.add(choice("remove", member.toString(), "remove", true, false, name(player, member)));
            choices.add(choice("back", "", "back", false, false));
        } else {
            lines.add(solo ? Text.of("playing_solo") : Text.of("your_team", MachineOwnerDisplay.teamName(player.server, own.pool())));
            if (solo) {
                // A sent request is available through Requests; it never forces a team or blocks solo play.
                choices.add(choice("create", "", "create", true, true));
            } else if (!owner) choices.add(choice("leave", "", "leave", true, false));
            else if (own.members().size() == 1) choices.add(choice("disband", "", "disband", true, false));
            else choices.add(choice("manage_open", "", "manage", false, false));
            if (!requests.isEmpty()) choices.add(choice("requests_open", "", "requests_count", false, false, Integer.toString(requests.size())));
        }
        return new Projection(context, lines, choices);
    }
    public static void sync(ServerPlayer player, boolean force) {
        AbstractContainerMenu menu = player.containerMenu;
        TeamPopup popup = POPUPS.get(player);
        boolean access = menu instanceof TeamMenu team && team.kind == TeamMenu.Kind.ACCESS;
        if (!ServerMenuAccess.isCurrent(player, menu) || !access && (popup == null || popup.menu != menu
                || machine(menu) == null || !machine(menu).authorize(player, MachineAction.USE))) {
            SENT.remove(player); POPUPS.remove(player); return;
        }
        int view = access ? 1 : popup.view;
        int page = access ? ((TeamMenu) menu).page : popup.page;
        int pages = 1;
        if (!access && nativeInterfaceAvailable(player)) {
            var saved = EssenceSavedData.get(player.server);
            var own = saved.resolvePool(player.getUUID()).snapshot().orElse(null);
            if (view == 2 && own != null) pages = Math.max(1, saved.nativeMembership().requests(player.getUUID()).size());
            if (view == 3) {
                if (own == null || !own.allows(player.getUUID(), PoolCapability.MANAGE) || own.members().size() < 2) { popup.view = view = 0; popup.page = page = 0; }
                else pages = own.members().size() - 1;
            }
        }
        page = Math.min(page, pages - 1);
        if (access) ((TeamMenu) menu).page = page; else popup.page = page;
        var projection = project(player, menu, view, page);
        var choices = List.copyOf(projection.choices);
        var old = SENT.get(player);
        UUID receipt = old != null && old.menu == menu && old.context.equals(projection.context) && old.choices.equals(choices)
                && old.payload.view() == view && old.payload.page() == page ? old.payload.receipt() : UUID.randomUUID();
        var data = EssenceSavedData.get(player.server).getPlayerData(player.getUUID());
        var payload = new TeamInterfacePayload(menu.containerId, receipt, view, page, pages,
                projection.lines.stream().limit(MAX_LINES).toList(), choices.stream().map(Choice::control).toList(), data.crucibleChannelSelection(), data.crucibleChannelSelectionRevision());
        SENT.put(player, new Sent(menu, projection.context, choices, payload));
        if ((force || old == null || !payload.equals(old.payload)) && NetworkManager.canPlayerReceive(player, TeamInterfacePayload.TYPE)) NetworkManager.sendToPlayer(player, payload);
    }
    private static void execute(ServerPlayer player, AbstractContainerMenu menu, Choice choice, String input) {
        var saved = EssenceSavedData.get(player.server); UUID actor = player.getUUID(); BoundMachine machine = machine(menu);
        var nativeService = saved.nativeMembership();
        boolean ok = switch (choice.code) {
            case "create" -> { nativeService.create(actor, input); yield true; }
            case "request" -> { nativeService.request(actor, new PoolId(MembershipAuthority.NATIVE, PoolId.Kind.TEAM, UUID.fromString(choice.target)), true); yield true; }
            case "request_personal" -> { nativeService.requestPersonal(actor, UUID.fromString(choice.target), true); yield true; }
            case "cancel" -> { nativeService.cancel(actor, UUID.fromString(choice.target)); yield true; }
            case "approve", "decline" -> { nativeService.decide(actor, UUID.fromString(choice.target), choice.code.equals("approve")); yield true; }
            case "approve_personal" -> { nativeService.decide(actor, UUID.fromString(choice.target), true, input); yield true; }
            case "leave" -> { nativeService.leave(actor); yield true; }
            case "transfer" -> { nativeService.transfer(actor, UUID.fromString(choice.target)); yield true; }
            case "remove" -> { nativeService.remove(actor, UUID.fromString(choice.target)); yield true; }
            case "disband" -> { nativeService.disband(actor); yield true; }
            case "recover_machine" -> machine != null && machine.recover(player, actor);
            case "open_machine" -> openMachine(player, machine);
            case "requests_open", "manage_open", "back" -> {
                TeamPopup popup = POPUPS.get(player);
                if (popup == null || popup.menu != menu) yield false;
                popup.view = choice.code.equals("requests_open") ? 2 : choice.code.equals("manage_open") ? 3 : 0;
                popup.page = 0; yield true;
            }
            default -> false;
        };
        if (!ok) throw new IllegalStateException("Action denied");
    }
}
