package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.network.*;
import dev.architectury.networking.NetworkManager;
import net.minecraft.network.chat.Component;
import java.util.UUID;

/** Connection-scoped snapshots only; preference persistence remains in PlayerEssenceData. */
public final class TeamInterfaceClientState {
    private static TeamInterfacePayload snapshot;
    private static boolean initialized;
    private static final UUID EMPTY = new UUID(0, 0);
    public static void init() {
        if (initialized) return; initialized = true;
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, TeamInterfacePayload.TYPE, TeamInterfacePayload.CODEC,
                (p, c) -> ClientPacketDispatch.queue(c, () -> {
                    var mc = net.minecraft.client.Minecraft.getInstance();
                    if (mc.player == null || mc.player.containerMenu.containerId != p.menuId()) return;
                    var menu = mc.player.containerMenu;
                    if (menu instanceof com.mistaboom.essence_ascendance.team.TeamMenu team ? team.view == p.view()
                            : p.view() != 1 && (menu instanceof com.mistaboom.essence_ascendance.crucible.EssenceCrucibleMenu
                            || menu instanceof com.mistaboom.essence_ascendance.infuser.EssenceInfuserMenu
                            || menu instanceof com.mistaboom.essence_ascendance.pylon.EssencePylonMenu)) snapshot = p;
                }));
    }
    public static TeamInterfacePayload snapshot(int menuId) { return snapshot != null && snapshot.menuId() == menuId ? snapshot : null; }
    public static void clear() { snapshot = null; }
    public static Component text(TeamInterfacePayload.Text text) { return Component.translatable(text.key(), text.arguments().toArray()); }
    public static void request(int menuId, int view, int page) { NetworkManager.sendToServer(new TeamInterfaceActionPayload(menuId, EMPTY, TeamInterfaceActionPayload.REQUEST, view, Math.max(0, page), "")); }
    public static void open(int menuId) { clear(); NetworkManager.sendToServer(new TeamInterfaceActionPayload(menuId, EMPTY, TeamInterfaceActionPayload.OPEN, 0, 0, "")); }
    public static void close(int menuId) { clear(); NetworkManager.sendToServer(new TeamInterfaceActionPayload(menuId, EMPTY, TeamInterfaceActionPayload.CLOSE, 0, 0, "")); }
    public static boolean nativeButtonVisible() {
        return !dev.architectury.platform.Platform.isModLoaded("ftbteams");
    }
    public static void action(TeamInterfacePayload snapshot, int index, String input) {
        NetworkManager.sendToServer(new TeamInterfaceActionPayload(snapshot.menuId(), snapshot.receipt(), index, snapshot.view(), snapshot.page(), input));
    }
}
