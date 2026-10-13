package com.mistaboom.essence_ascendance.machine;

import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleBlockEntity;
import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** Shared, many-to-one stable selection. Links do not reserve a source or inspect other consumers.
 * An unavailable target is retained; only Automatic replaces invalid targets. Channel scheduling is separate.
 */
public final class MachineLink {
    public enum Mode { AUTOMATIC, LOCKED, DISCONNECTED }
    public enum State { VALID, UNAVAILABLE, INVALID, DISCONNECTED }
    public record Source(ResourceLocation dimension, BlockPos pos) {
        public Source { Objects.requireNonNull(dimension); pos = pos.immutable(); }
    }
    private Mode mode = Mode.AUTOMATIC;
    private Source target;
    public Mode mode() { return mode; }
    public Source target() { return target; }
    public static Source nearest(ResourceLocation dimension, BlockPos origin, List<BlockPos> candidates) {
        return candidates.stream().min(Comparator.<BlockPos>comparingDouble(origin::distSqr)
                .thenComparingLong(BlockPos::asLong)).map(p -> new Source(dimension, p)).orElse(null);
    }
    public boolean refresh(State state, Supplier<Source> eligible) {
        Source before = target;
        if (mode == Mode.DISCONNECTED) target = null;
        else if (state == State.INVALID && mode == Mode.AUTOMATIC) target = eligible.get();
        return !Objects.equals(before, target);
    }
    public void configure(Mode mode, Source source) {
        this.mode = Objects.requireNonNull(mode);
        target = mode == Mode.DISCONNECTED ? null : source;
    }
    public State state(ServerLevel level, BlockPos origin, double range, MachineBinding consumer) {
        if (mode == Mode.DISCONNECTED) return State.DISCONNECTED;
        if (target == null || !target.dimension.equals(level.dimension().location())
                || origin.distSqr(target.pos) > range * range) return State.INVALID;
        EssenceSavedData saved = EssenceSavedData.get(level.getServer());
        if (!consumer.operational(saved)) return State.UNAVAILABLE;
        if (!level.hasChunkAt(target.pos)) return State.UNAVAILABLE;
        if (!(level.getBlockEntity(target.pos) instanceof EssenceCrucibleBlockEntity source) || source.isRemoved()) return State.INVALID;
        if (source.machineBinding().refresh(saved)) source.bindingChanged();
        // Paused membership is temporary; a known different identity invalidates immediately.
        if (!Objects.equals(consumer.pool(), source.machineBinding().pool())) return State.INVALID;
        if (source.machineBinding().canAct(saved, MachineAction.SPEND) && consumer.canAct(saved, MachineAction.CONTRIBUTE)) return State.VALID;
        return com.mistaboom.essence_ascendance.team.MembershipAuthority.ready(saved.reservoirs().readiness()) ? State.INVALID : State.UNAVAILABLE;
    }
    public boolean refresh(ServerLevel level, BlockPos origin, double range, MachineBinding consumer) {
        return refresh(state(level, origin, range, consumer), () -> {
            EssenceSavedData saved = EssenceSavedData.get(level.getServer());
            if (!consumer.canAct(saved, MachineAction.LINK)) return null;
            return nearest(level.dimension().location(), origin, LoadedMachineDiscovery.within(level, origin, range,
                    EssenceCrucibleBlockEntity.class).stream().filter(c -> {
                        if (c.machineBinding().refresh(saved)) c.bindingChanged();
                        return consumer.samePool(saved, c.machineBinding());
                    }).map(EssenceCrucibleBlockEntity::getBlockPos).toList());
        });
    }
    public boolean configure(ServerLevel level, BlockPos origin, double range, MachineBinding binding, Mode mode, Source source) {
        if (mode == Mode.LOCKED && source == null) return false;
        MachineLink candidate = new MachineLink(); candidate.configure(mode, source);
        if (mode != Mode.DISCONNECTED && source != null && candidate.state(level, origin, range, binding) != State.VALID) return false;
        configure(mode, source); return true;
    }
    /** Presentation and configuration use the same loaded, range and authorization policy. */
    public static List<Source> eligibleSources(ServerLevel level, BlockPos origin, double range, MachineBinding binding) {
        return LoadedMachineDiscovery.within(level, origin, range, EssenceCrucibleBlockEntity.class).stream()
                .map(c -> new Source(level.dimension().location(), c.getBlockPos()))
                .filter(source -> {
                    MachineLink candidate = new MachineLink(); candidate.configure(Mode.LOCKED, source);
                    return candidate.state(level, origin, range, binding) == State.VALID;
                }).sorted(Comparator.comparingLong(s -> s.pos().asLong())).toList();
    }
    public void save(CompoundTag root) {
        CompoundTag tag = new CompoundTag(); tag.putInt("version", 2); tag.putString("mode", mode.name());
        if (target != null) { tag.putString("dimension", target.dimension.toString()); tag.putLong("pos", target.pos.asLong()); }
        root.put("machine_link", tag);
    }
    public void load(CompoundTag root) {
        CompoundTag tag = root.getCompound("machine_link");
        mode = tag.contains("mode", Tag.TAG_STRING) ? Mode.valueOf(tag.getString("mode")) : Mode.AUTOMATIC;
        target = tag.contains("pos", Tag.TAG_LONG) ? new Source(ResourceLocation.parse(tag.getString("dimension")), BlockPos.of(tag.getLong("pos"))) : null;
        if (mode == Mode.DISCONNECTED && target != null) throw new IllegalStateException("Disconnected machine has a target");
        // The routing popup was removed. Existing devices must not remain silently disconnected
        // or locked by its old settings; keep a valid source as the stable automatic preference.
        if (tag.getInt("version") < 2) mode = Mode.AUTOMATIC;
    }
}
