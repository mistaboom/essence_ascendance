package com.mistaboom.essence_ascendance.network;

import com.mistaboom.essence_ascendance.attunement.AttunementSnapshot;
import com.mistaboom.essence_ascendance.balance.runtime.BonusTrackDefinition;
import com.mistaboom.essence_ascendance.client.ClientEssenceState;
import com.mistaboom.essence_ascendance.stat.StatUnit;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Real registered codec, deterministic bytes, transport bounds and lossless client-facing facts. */
public final class BonusTrackSnapshotCodecTest {
    private static int checks;
    public static final List<ResourceLocation> TIERS = List.of("latent", "dormant", "awakened", "resonant", "ascendant", "transcendent")
            .stream().map(path -> ResourceLocation.parse("essence_ascendance:" + path)).toList();

    public static void main(String[] args) {
        for (var track : List.of(track(1, 5, false), track(3, 5, false), track(4, 4, false), track(1, 2, true), unavailable())) {
            var encoded = buffer();
            BonusTrackSnapshot.write(encoded, track);
            byte[] bytes = bytes(encoded);
            check(BonusTrackSnapshot.read(encoded).equals(track) && encoded.readableBytes() == 0,
                    "Resolved checkpoint, segment, snap and geometry facts round-trip exactly");
            for (int i = 1; i < track.tierPositions().size(); i++) {
                check(Math.abs(track.tierPositions().get(i) - track.tierPositions().get(i - 1) - .2) < 1e-12,
                        "Every purchasable tier position, beginning at Dormant, retains an equal visible band");
            }
            encoded.release();
            var repeated = buffer();
            BonusTrackSnapshot.write(repeated, track);
            check(Arrays.equals(bytes, bytes(repeated)), "Snapshot encoding is deterministic");
            repeated.release();
            var truncated = buffer();
            truncated.writeBytes(Arrays.copyOf(bytes, bytes.length - 1));
            rejects(() -> BonusTrackSnapshot.read(truncated));
            truncated.release();
        }
        var resolved = track(1, 2, true);
        var stat = new PlayerEssenceSyncPayload.StatState("essence_ascendance:step_height", 600, 600, 600,
                1, .9, .9, .9, resolved);
        var progress = new PlayerEssenceSyncPayload.ProgressState(PlayerEssenceSyncPayload.ProgressStatus.MAX_TIER,
                "", 0, 0, 0, 0, 0, 0, 0, List.of(), true, false);
        var payload = new PlayerEssenceSyncPayload(PlayerEssenceSyncPayload.CURRENT_SCHEMA_VERSION, 84,
                TIERS.getLast().toString(), "essence_ascendance:generated_test", List.of(), List.of(stat), List.of(),
                List.of(), List.of(), List.of(), AttunementSnapshot.empty(), progress);
        var full = buffer();
        PlayerEssenceSyncPayload.CODEC.encode(full, payload);
        var decoded = PlayerEssenceSyncPayload.CODEC.decode(full);
        check(decoded.equals(payload) && full.readableBytes() == 0, "Complete player payload preserves independent track and revision");
        full.release();
        var view = ClientEssenceState.StatSnapshot.from(decoded.stats().getFirst());
        check(view.track().equals(resolved) && view.storedInvestment() == 600 && view.scaledBonus() == .9,
                "Client view retains exactly the server's facts without registry-name inference");
        for (int count : new int[] {-1, 33, Integer.MAX_VALUE}) {
            var invalid = header(); invalid.writeVarInt(count);
            rejects(() -> BonusTrackSnapshot.read(invalid)); invalid.release();
        }
        var invalidEnum = buffer(); invalidEnum.writeVarInt(500);
        rejects(() -> BonusTrackSnapshot.read(invalidEnum)); invalidEnum.release();
        rejects(() -> new BonusTrackSnapshot(resolved.unit(), resolved.startTier(), resolved.completionTier(),
                resolved.checkpoints(), List.of(0.0, .1, .09, .4, .7, 1.0), .7, resolved.purchaseStyle(), resolved.snapPoints(), resolved.applicability()));
        rejects(() -> new BonusTrackSnapshot(resolved.unit(), resolved.startTier(), resolved.completionTier(),
                resolved.checkpoints(), resolved.tierPositions(), Double.NaN, resolved.purchaseStyle(), resolved.snapPoints(), resolved.applicability()));
        rejects(() -> new BonusTrackSnapshot(resolved.unit(), resolved.startTier(), resolved.completionTier(),
                resolved.checkpoints(), resolved.tierPositions(), .7, resolved.purchaseStyle(), List.of(0.0, .5, .5, 1.0), resolved.applicability()));
        rejects(() -> new BonusTrackSnapshot(resolved.unit(), resolved.startTier(), resolved.completionTier(),
                resolved.checkpoints(), resolved.tierPositions(), .7, resolved.purchaseStyle(), resolved.snapPoints(), BonusTrackDefinition.Applicability.UNAVAILABLE));
        System.out.println("BonusTrackSnapshotCodecTest: " + checks + " checks PASS");
    }

    public static BonusTrackSnapshot track(int start, int completion, boolean threshold) {
        List<BonusTrackDefinition.Checkpoint> checkpoints = new ArrayList<>();
        long cap = 0;
        double fraction = 0;
        long[] segments = {0, 100, 500, 3_000, 16_000, 100_000};
        for (int i = 0; i < TIERS.size(); i++) {
            long segment = i >= start && i <= completion ? segments[i] : 0;
            cap += segment;
            if (segment > 0) fraction = (i - start + 1.0) / (completion - start + 1.0);
            checkpoints.add(new BonusTrackDefinition.Checkpoint(TIERS.get(i), cap, segment, fraction, i >= start, segment > 0));
        }
        return new BonusTrackSnapshot(threshold ? StatUnit.BLOCKS : StatUnit.PERCENT, TIERS.get(start), TIERS.get(completion),
                checkpoints, List.of(0.0, .2, .4, .6, .8, 1.0), .72,
                BonusTrackDefinition.PurchaseStyle.FUNDED_STATES,
                java.util.stream.Stream.concat(java.util.stream.Stream.of(0.0), checkpoints.stream().filter(BonusTrackDefinition.Checkpoint::purchasable).map(BonusTrackDefinition.Checkpoint::effectFraction)).toList(), BonusTrackDefinition.Applicability.AVAILABLE);
    }

    public static BonusTrackSnapshot unavailable() {
        return new BonusTrackSnapshot(StatUnit.PERCENT, TIERS.get(1), TIERS.get(5),
                TIERS.stream().map(tier -> new BonusTrackDefinition.Checkpoint(tier, 0, 0, 0, false, false)).toList(),
                List.of(0.0, .2, .4, .6, .8, 1.0), .72, BonusTrackDefinition.PurchaseStyle.CONTINUOUS, List.of(),
                BonusTrackDefinition.Applicability.UNAVAILABLE);
    }

    private static RegistryFriendlyByteBuf header() {
        var buffer = buffer(); buffer.writeEnum(StatUnit.PERCENT); buffer.writeResourceLocation(TIERS.get(1));
        buffer.writeResourceLocation(TIERS.get(5)); buffer.writeDouble(.72);
        buffer.writeEnum(BonusTrackDefinition.PurchaseStyle.CONTINUOUS); buffer.writeEnum(BonusTrackDefinition.Applicability.AVAILABLE);
        return buffer;
    }
    private static RegistryFriendlyByteBuf buffer() { return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY); }
    private static byte[] bytes(RegistryFriendlyByteBuf buffer) {
        byte[] data = new byte[buffer.readableBytes()]; buffer.getBytes(buffer.readerIndex(), data); return data;
    }
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (RuntimeException expected) { checks++; return; }
        throw new AssertionError("Malformed Bonus snapshot accepted");
    }
}
