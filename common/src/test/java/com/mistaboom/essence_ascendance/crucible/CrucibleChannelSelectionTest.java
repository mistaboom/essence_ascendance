package com.mistaboom.essence_ascendance.crucible;

import com.mistaboom.essence_ascendance.data.*;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import com.mistaboom.essence_ascendance.network.EssenceCrucibleSelectionPayload;
import com.mistaboom.essence_ascendance.team.NativeMembershipService;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.Bootstrap;
import java.util.UUID;

/** Real persistence and transport invariants; channel transfers are covered by the native machine fixture. */
public final class CrucibleChannelSelectionTest {
    private static int checks;
    public static void main(String[] args) {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); EssenceTypes.init(); AscendanceTiers.init();
        var player = new PlayerEssenceData();
        check(player.crucibleChannelSelection() == CrucibleChannelSelection.ALL, "New players default to all six selected");
        for (int mask = 0; mask <= CrucibleChannelSelection.ALL; mask++) {
            player.setCrucibleChannelSelection(mask);
            check(PlayerEssenceData.load(player.save()).crucibleChannelSelection() == mask, "Every personal selection persists, including None");
        }
        var withoutPreference = player.save(); withoutPreference.remove("crucible_channel_selection");
        check(PlayerEssenceData.load(withoutPreference).crucibleChannelSelection() == CrucibleChannelSelection.ALL,
                "An existing schema-10 player without an optional preference defaults to All");
        var malformed = player.save(); malformed.putInt("crucible_channel_selection", 64);
        rejects(() -> PlayerEssenceData.load(malformed));
        malformed.putString("crucible_channel_selection", "all"); rejects(() -> PlayerEssenceData.load(malformed));
        rejects(() -> player.setCrucibleChannelSelection(-1)); rejects(() -> CrucibleChannelSelection.apply(0, 8));
        check(CrucibleChannelSelection.apply(CrucibleChannelSelection.apply(0, 5), 5) == 0, "Consecutive toggles compose on authoritative state without lost updates");
        check(CrucibleChannelSelection.apply(0, CrucibleChannelSelection.SELECT_ALL) == 63
                && CrucibleChannelSelection.apply(63, CrucibleChannelSelection.SELECT_NONE) == 0, "All and None are explicit actions");
        UUID a = new UUID(0, 1), b = new UUID(0, 2);
        var saved = new EssenceSavedData(); saved.addCrucibleStored(a, EssenceTypes.OFFENSE, 200);
        var team = saved.nativeMembership().create(a, "Channel selection fixture");
        var request = saved.nativeMembership().request(b, team, true); saved.nativeMembership().decide(a, request.token(), true);
        var before = saved.reservoirs().read(a); long progression = saved.getPlayerData(a).nexusRevision();
        saved.getPlayerData(a).setCrucibleChannelSelection(1 << 5);
        saved.getPlayerData(b).setCrucibleChannelSelection(1 << 0);
        check(saved.reservoirs().read(a) == before && saved.getPlayerData(a).nexusRevision() == progression,
                "Channel preferences do not alter communal funds, cached views or progression revisions");
        var record = saved.getPlayerData(a);
        long preparedRevision = record.crucibleChannelSelectionRevision();
        var preparedFunds = saved.reservoirs().read(a);
        record.setCrucibleChannelSelection(0);
        var staleSelection = new ReservoirService.Mutation() {
            public boolean apply() { return record.crucibleChannelSelectionRevision() == preparedRevision; }
            public void rollback() { }
        };
        check(!saved.reservoirs().channel(a, java.util.Map.of(EssenceTypes.OFFENSE.id(),10L), staleSelection), "Accepted disable rejects previously prepared incoming payment");
        record.setCrucibleChannelSelection(32);
        check(!saved.reservoirs().channel(a, java.util.Map.of(EssenceTypes.OFFENSE.id(),10L), staleSelection)
                && saved.reservoirs().read(a) == preparedFunds && record.getAvailable(EssenceTypes.OFFENSE) == 0,
                "Disable then re-enable cannot resurrect a stale selection grant or mutate communal accounting");
        var loaded = EssenceSavedData.load(saved.save(new CompoundTag(), null), null);
        check(loaded.getPlayerData(a).crucibleChannelSelection() == 32 && loaded.getPlayerData(b).crucibleChannelSelection() == 1,
                "Team members keep distinct choices across a world save/restart");
        player.setCrucibleChannelSelection(32); player.resetProgressionForAdmin();
        check(player.crucibleChannelSelection() == 32, "Personal channel preferences survive progression reset");
        for (int action = 0; action <= CrucibleChannelSelection.SELECT_NONE; action++) {
            var packet = new EssenceCrucibleSelectionPayload(123, action, 19);
            var buffer = new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), RegistryAccess.EMPTY);
            try {
                EssenceCrucibleSelectionPayload.CODEC.encode(buffer, packet);
                check(EssenceCrucibleSelectionPayload.CODEC.decode(buffer).equals(packet), "Selection actions roundtrip through the actual packet codec");
            } finally { buffer.release(); }
        }
        System.out.println("CrucibleChannelSelectionTest: " + checks + " assertions passed");
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); checks++; }
    private static void rejects(Runnable action) {
        try { action.run(); throw new AssertionError("Malformed preference/action accepted"); }
        catch (IllegalArgumentException | IllegalStateException expected) { checks++; }
    }
}
