package com.mistaboom.essence_ascendance.visual;

import com.mistaboom.essence_ascendance.pylon.EssenceFocusTier;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Small, observer-visible snapshots. Gameplay always reads the owning server objects. */
public final class MachineVisualState {
    public static final String TAG = "machine_visual";
    public static final Crucible IDLE_CRUCIBLE = new Crucible(false, List.of(), 0L, -1, 0,
            List.of(), List.of(), false);
    public static final Pylon IDLE_PYLON = new Pylon(null,
            new Focus(Host.PYLON, false, null, false, 0L));
    public static final Infuser IDLE_INFUSER = new Infuser(null, false, 11, 0, 1, 0L,
            null, 0, new Focus(Host.INFUSER, false, null, false, 0L));
    public static final Nexus IDLE_NEXUS = new Nexus(false);

    private MachineVisualState() {}

    public enum Host { PYLON, INFUSER }

    /** Presence is separate from completed tier: an installed Latent Focus has a null tier. */
    public record Focus(Host host, boolean installed, @Nullable EssenceFocusTier tier,
                        boolean active, long ratePerSecond) { }

    public record PlayerStream(UUID player, int essenceMask, long rate) {}
    public record Crucible(boolean dissolving, List<PlayerStream> playerStreams,
                           long transferRatePerSecond, int predominantEssenceIndex,
                           int reservoirFillBasisPoints, List<BlockPos> activePylons,
                           List<ResourceLocation> inputItems, boolean supplyingInfuser) {
        public Crucible {
            playerStreams = List.copyOf(playerStreams);
            if (playerStreams.size() > 32) throw new IllegalArgumentException("Too many observer streams");
            activePylons = List.copyOf(activePylons);
            inputItems = List.copyOf(inputItems);
        }

        public List<UUID> channelingPlayers() { return playerStreams.stream().map(PlayerStream::player).toList(); }
        public boolean channeling() { return !playerStreams.isEmpty(); }
        public boolean active() { return dissolving || channeling(); }
        public boolean needsPylonSupport() { return active() || supplyingInfuser; }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putBoolean("dissolving", dissolving);
            tag.putBoolean("supplying_infuser", supplyingInfuser);
            net.minecraft.nbt.ListTag players = new net.minecraft.nbt.ListTag();
            for (var stream : playerStreams) {
                var entry = new CompoundTag(); com.mistaboom.essence_ascendance.data.NbtVersionCompat.putUUID(entry, "player", stream.player()); entry.putInt("types", stream.essenceMask()); entry.putLong("rate", stream.rate()); players.add(entry);
            }
            tag.put("channelers", players);
            tag.putLong("rate", transferRatePerSecond);
            tag.putInt("predominant", predominantEssenceIndex);
            tag.putInt("fill", reservoirFillBasisPoints);
            tag.putLongArray("pylons", activePylons.stream().mapToLong(BlockPos::asLong).toArray());
            tag.putString("inputs", String.join(";", inputItems.stream().map(ResourceLocation::toString).toList()));
            return tag;
        }

        public static Crucible read(CompoundTag tag) {
            List<BlockPos> pylons = new ArrayList<>();
            for (long pos : com.mistaboom.essence_ascendance.data.NbtVersionCompat.getLongArray(tag, "pylons")) pylons.add(BlockPos.of(pos));
            List<ResourceLocation> inputs = new ArrayList<>();
            for (String id : tag.getString("inputs").split(";")) {
                ResourceLocation parsed = ResourceLocation.tryParse(id);
                if (!id.isBlank() && parsed != null) inputs.add(parsed);
            }
            List<PlayerStream> players = new ArrayList<>();
            var channelers = tag.getList("channelers", Tag.TAG_COMPOUND);
            if (channelers.size() > 32) throw new IllegalArgumentException("Too many observer streams");
            for (int i = 0; i < channelers.size(); i++) { var entry = channelers.getCompound(i);
                int mask = entry.getInt("types"); long rate = entry.getLong("rate");
                if (!entry.hasUUID("player") || !com.mistaboom.essence_ascendance.crucible.CrucibleChannelSelection.valid(mask) || rate < 0) throw new IllegalArgumentException("Invalid observer stream");
                players.add(new PlayerStream(entry.getUUID("player"), mask, rate));
            }
            return new Crucible(tag.getBoolean("dissolving"), players,
                    tag.getLong("rate"), tag.getInt("predominant"), tag.getInt("fill"),
                    pylons, inputs, tag.getBoolean("supplying_infuser"));
        }
    }

    public record Pylon(@Nullable BlockPos linkedCrucible, Focus focus) {
        public boolean linked() { return linkedCrucible != null; }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            if (linkedCrucible != null) tag.putLong("link", linkedCrucible.asLong());
            writeFocus(tag, focus);
            return tag;
        }

        public static Pylon read(CompoundTag tag) {
            return new Pylon(tag.contains("link", Tag.TAG_LONG) ? BlockPos.of(tag.getLong("link")) : null,
                    readFocus(tag, Host.PYLON));
        }
    }

    public record Infuser(@Nullable BlockPos linkedCrucible, boolean enabled, int status,
                          int processingTicks, int requiredTicks, long throughputPerSecond,
                          @Nullable ResourceLocation workpiece, int transferredTypes, Focus focus) {
        public boolean linked() { return linkedCrucible != null; }
        public boolean processing() { return status == 5; }
        public boolean transferring() {
            return linked() && focus.installed() && processing() && throughputPerSecond > 0L;
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            if (linkedCrucible != null) tag.putLong("link", linkedCrucible.asLong());
            tag.putBoolean("enabled", enabled);
            tag.putInt("status", status);
            tag.putInt("ticks", processingTicks);
            tag.putInt("required", requiredTicks);
            tag.putLong("rate", throughputPerSecond);
            if (workpiece != null) tag.putString("workpiece", workpiece.toString());
            tag.putInt("transferred_types", transferredTypes);
            writeFocus(tag, focus);
            return tag;
        }

        public static Infuser read(CompoundTag tag) {
            return new Infuser(tag.contains("link", Tag.TAG_LONG) ? BlockPos.of(tag.getLong("link")) : null,
                    tag.getBoolean("enabled"), tag.getInt("status"), tag.getInt("ticks"),
                    tag.getInt("required"), tag.getLong("rate"),
                    ResourceLocation.tryParse(tag.getString("workpiece")), tag.getInt("transferred_types"), readFocus(tag, Host.INFUSER));
        }
    }

    public record Nexus(boolean inUse) {
        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putBoolean("in_use", inUse);
            return tag;
        }

        public static Nexus read(CompoundTag tag) { return new Nexus(tag.getBoolean("in_use")); }
    }

    private static void writeFocus(CompoundTag tag, Focus focus) {
        tag.putBoolean("focus_installed", focus.installed());
        tag.putInt("focus_tier", focus.tier() == null ? -1 : focus.tier().ordinal());
        tag.putBoolean("focus_active", focus.active());
        tag.putLong("focus_rate", focus.ratePerSecond());
    }

    private static Focus readFocus(CompoundTag tag, Host host) {
        int index = tag.getInt("focus_tier");
        EssenceFocusTier[] tiers = EssenceFocusTier.values();
        boolean installed = tag.contains("focus_installed", Tag.TAG_BYTE)
                ? tag.getBoolean("focus_installed") : index >= 0;
        return new Focus(host, installed, index >= 0 && index < tiers.length ? tiers[index] : null,
                tag.getBoolean("focus_active"), tag.getLong("focus_rate"));
    }
}
