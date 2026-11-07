package com.mistaboom.essence_ascendance.machine;

import com.mistaboom.essence_ascendance.data.NbtVersionCompat;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
//? if >=26.1.2
/*import net.minecraft.world.level.storage.ValueInput;*/
//? if >=26.1.2
/*import net.minecraft.world.level.storage.ValueOutput;*/

import java.util.UUID;

/** Physical infrastructure generation, independent of ownership and shared financial pools. */
public final class MachineInstanceIdentity {
    private static final String KEY = "physical_machine";
    private UUID id;
    private ResourceLocation dimension;
    private long position;
    private boolean invalidated;

    /** Lazily identifies a legitimate old loaded machine without fabricating an operation. */
    public UUID get(BlockEntity machine) {
        if (machine.getLevel() == null || machine.getLevel().isClientSide) return null;
        ResourceLocation currentDimension = machine.getLevel().dimension().location();
        long currentPosition = machine.getBlockPos().asLong();
        if (id == null || !currentDimension.equals(dimension) || position != currentPosition) {
            if (id != null) invalidated = true;
            id = UUID.randomUUID(); dimension = currentDimension; position = currentPosition;
            machine.setChanged();
        }
        return id;
    }

    /** Portable pickup/replacement and fresh physical placement always begin a new generation. */
    public void placed(BlockEntity machine) {
        id = null; dimension = null; position = 0; invalidated = false;
        get(machine);
    }

    /** A copied/malformed physical record cannot keep an earlier instance's operation attribution. */
    public boolean consumeInvalidation() {
        boolean result = invalidated;
        invalidated = false;
        return result;
    }

    public void save(CompoundTag root) {
        if (id == null || dimension == null) return;
        CompoundTag value = new CompoundTag();
        NbtVersionCompat.putUUID(value, "id", id);
        value.putString("dimension", dimension.toString());
        value.putLong("position", position);
        root.put(KEY, value);
    }

    public void load(CompoundTag root, BlockEntity machine) {
        CompoundTag value = NbtVersionCompat.getCompound(root, KEY);
        invalidated = false;
        id = NbtVersionCompat.hasUUID(value, "id") ? NbtVersionCompat.getUUID(value, "id") : null;
        dimension = ResourceLocation.tryParse(NbtVersionCompat.getString(value, "dimension"));
        position = NbtVersionCompat.getLong(value, "position");
        // Invalidate immediately, including before a cloned machine can be copied back to its old address.
        if (id == null || dimension == null || !NbtVersionCompat.contains(value, "dimension", Tag.TAG_STRING)
                || NbtVersionCompat.getString(value, "dimension").isBlank()
                || !NbtVersionCompat.contains(value, "position", Tag.TAG_LONG)
                || position != machine.getBlockPos().asLong()
                || machine.getLevel() != null && !dimension.equals(machine.getLevel().dimension().location())) {
            invalidated = root.get(KEY) != null;
            id = null; dimension = null; position = 0;
        }
    }

    //? if >=26.1.2
    /*public void save(ValueOutput output) { CompoundTag root = new CompoundTag(); save(root); if (root.get(KEY) != null) output.store(KEY, CompoundTag.CODEC, NbtVersionCompat.getCompound(root, KEY)); }*/
    //? if >=26.1.2
    /*public void load(ValueInput input, BlockEntity machine) { CompoundTag root = new CompoundTag(); input.read(KEY, CompoundTag.CODEC).ifPresent(value -> root.put(KEY, value)); load(root, machine); }*/
}
