package com.mistaboom.essence_ascendance.network;

import com.mistaboom.essence_ascendance.crucible.*;
import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import com.mistaboom.essence_ascendance.visual.MachineVisualState;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Actual state, framework paging and bounded payload fixtures, including malicious/stale snapshots. */
public final class MultiplayerInterfaceTest {
    private static int checks;
    public static void main(String[] args) {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); EssenceTypes.init(); AscendanceTiers.init();
        var a = new PlayerEssenceData(); var b = new PlayerEssenceData();
        for (int mask = 0; mask < 64; mask++) {
            a.setCrucibleChannelSelection(mask); long revision = a.crucibleChannelSelectionRevision();
            int action = mask % 6;
            check(a.updateCrucibleChannelSelection(revision, action), "Current individual action accepted");
            int accepted = a.crucibleChannelSelection();
            check(!a.updateCrucibleChannelSelection(revision, CrucibleChannelSelection.SELECT_ALL)
                    && a.crucibleChannelSelection() == accepted, "Stale other-screen update cannot overwrite newer selection");
            var loaded = PlayerEssenceData.load(a.save());
            check(loaded.crucibleChannelSelection() == accepted && loaded.crucibleChannelSelectionRevision() == a.crucibleChannelSelectionRevision(), "Restart preserves mask AND receipt revision");
            check(b.crucibleChannelSelection() == 63 && b.crucibleChannelSelectionRevision() == 0, "Different viewer untouched");
        }
        a.updateCrucibleChannelSelection(a.crucibleChannelSelectionRevision(), CrucibleChannelSelection.SELECT_NONE);
        check(a.crucibleChannelSelection() == 0 && PlayerEssenceData.load(a.save()).crucibleChannelSelection() == 0, "None remains visible and persistent");
        check(!a.updateCrucibleChannelSelection(a.crucibleChannelSelectionRevision(), 100), "Malformed selection denied");
        check(PresentationPage.start(257, Integer.MAX_VALUE, 6) == 257 && PresentationPage.pages(Integer.MAX_VALUE, 6) == 357913942, "Paging cannot overflow hostile or large offsets");
        for (int count : new int[]{0, 1, 6, 7, 32, 257, 10000}) {
            int visited = 0;
            for (int page = 0; page < PresentationPage.pages(count, 6); page++)
                visited += Math.min(6, count - PresentationPage.start(count, page, 6));
            check(visited == count, "Pagination covers every receiver without a scheduler cap");
        }
        var payload = new TeamInterfacePayload(17, UUID.randomUUID(), 2, 42, 43,
                List.of(TeamInterfacePayload.Text.of("requests_count", "257")), Collections.nCopies(6, new TeamInterfacePayload.Control(TeamInterfacePayload.Text.of("approve_request"), TeamInterfacePayload.Text.of("confirm.action"), false, false)), 0, 147);
        var buffer = buffer();
        try { TeamInterfacePayload.CODEC.encode(buffer, payload); check(TeamInterfacePayload.CODEC.decode(buffer).equals(payload), "Real interface codec preserves None, revisions, page and receipt"); } finally { buffer.release(); }
        var action = new TeamInterfaceActionPayload(17, payload.receipt(), 5, 2, 42, "Create team"); buffer = buffer();
        try { TeamInterfaceActionPayload.CODEC.encode(buffer, action); check(TeamInterfaceActionPayload.CODEC.decode(buffer).equals(action), "Actor-free action codec retains execution receipt"); } finally { buffer.release(); }
        rejects(() -> new TeamInterfacePayload(0, UUID.randomUUID(), 2, 0, 1, List.of(), Collections.nCopies(7, payload.controls().getFirst()), 63, 0));
        rejects(() -> new TeamInterfacePayload.Text("x", List.of("x".repeat(193))));
        rejects(() -> new TeamInterfacePayload(0, UUID.randomUUID(), 4, 0, 1, List.of(), List.of(), 63, 0));
        var management = new TeamInterfacePayload(17, UUID.randomUUID(), 3, 0, 1, List.of(TeamInterfacePayload.Text.of("your_team", "Workshop")), List.of(), 63, 0);
        buffer = buffer();
        try { TeamInterfacePayload.CODEC.encode(buffer, management); check(TeamInterfacePayload.CODEC.decode(buffer).equals(management), "Management popup view round-trips through the real codec"); } finally { buffer.release(); }
        var hud = new ChannelSessionsPayload(7, ResourceLocation.parse("minecraft:overworld"), 257, 0,
                Collections.nCopies(16, new ChannelSessionsPayload.Link(0, "NO_SELECTION", 0, 0)));
        buffer = buffer();
        try { ChannelSessionsPayload.CODEC.encode(buffer, hud); check(ChannelSessionsPayload.CODEC.decode(buffer).equals(hud), "HUD bounded projection retains complete registration count"); } finally { buffer.release(); }
        rejects(() -> new ChannelSessionsPayload(7, hud.dimension(), 17, 0, Collections.nCopies(17, hud.links().getFirst())));
        buffer = buffer();
        try { buffer.writeVarInt(7); buffer.writeResourceLocation(hud.dimension()); buffer.writeVarInt(17); buffer.writeVarInt(0); buffer.writeVarInt(17);
            var hostile = buffer; rejects(() -> ChannelSessionsPayload.CODEC.decode(hostile)); } finally { buffer.release(); }
        var stream = new MachineVisualState.Crucible(false, List.of(new MachineVisualState.PlayerStream(UUID.randomUUID(), 32, 30), new MachineVisualState.PlayerStream(UUID.randomUUID(), 1, 30)), 120, 2, 5000, List.of(), List.of(), true);
        check(MachineVisualState.Crucible.read(stream.save()).equals(stream), "Multiple observer endpoints retain their actual transferred types and rates");
        System.out.println("MultiplayerInterfaceTest: " + checks + " assertions passed");
    }
    private static RegistryFriendlyByteBuf buffer() { return new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), RegistryAccess.EMPTY); }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); checks++; }
    private static void rejects(Runnable action) { try { action.run(); throw new AssertionError("Invalid presentation accepted"); } catch (IllegalArgumentException expected) { checks++; } }
}
