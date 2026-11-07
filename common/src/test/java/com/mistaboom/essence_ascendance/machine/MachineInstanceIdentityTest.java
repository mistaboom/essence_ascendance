package com.mistaboom.essence_ascendance.machine;

import com.mistaboom.essence_ascendance.data.NbtVersionCompat;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import java.util.UUID;

/** Detached native block entities exercise persisted physical-address validation without a user world. */
public final class MachineInstanceIdentityTest {
    private static int checks;
    public static void main(String[] args) {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        BlockPos original = new BlockPos(3, 77, -9), destination = original.offset(1, 0, 0);
        UUID id = new UUID(7, 11);
        CompoundTag saved = record(id, original);
        var identity = new MachineInstanceIdentity();
        identity.load(saved, machine(original));
        check(!identity.consumeInvalidation(), "A normal same-address reload preserves operation eligibility");
        CompoundTag roundtrip = new CompoundTag(); identity.save(roundtrip);
        check(roundtrip.equals(saved), "Physical instance UUID and immutable address roundtrip exactly");

        identity.load(saved, machine(destination));
        check(identity.consumeInvalidation(), "Native clone to another position invalidates prior operation attribution");
        check(!identity.consumeInvalidation(), "One invalidation is consumed exactly once");
        CompoundTag copied = new CompoundTag(); identity.save(copied);
        check(!copied.contains("physical_machine"), "Invalid identity is cleared before a copied machine can be saved");
        identity.load(copied, machine(original));
        CompoundTag copiedBack = new CompoundTag(); identity.save(copiedBack);
        check(!copiedBack.contains("physical_machine"), "A clone roundtrip cannot resurrect the source instance UUID");

        CompoundTag malformed = saved.copy();
        NbtVersionCompat.getCompound(malformed, "physical_machine").remove("dimension");
        identity.load(malformed, machine(original));
        check(identity.consumeInvalidation(), "Malformed known physical provenance cannot retain operation credit");
        malformed = saved.copy();
        NbtVersionCompat.getCompound(malformed, "physical_machine").remove("id");
        identity.load(malformed, machine(original));
        check(identity.consumeInvalidation(), "Missing UUID in a known physical record invalidates operation attribution");
        malformed = saved.copy();
        NbtVersionCompat.getCompound(malformed, "physical_machine").putString("dimension", "");
        identity.load(malformed, machine(original));
        check(identity.consumeInvalidation(), "An empty dimension cannot pass resource parsing as physical provenance");
        identity.load(new CompoundTag(), machine(original));
        check(!identity.consumeInvalidation(), "Absent legacy identity does not invent copied provenance");

        identity.load(saved, machine(original));
        identity.placed(machine(original));
        CompoundTag replaced = new CompoundTag(); identity.save(replaced);
        check(!replaced.contains("physical_machine"), "New placement discards an old physical instance even at the same address");
        System.out.println("MachineInstanceIdentityTest: " + checks + " assertions passed");
    }
    private static ChestBlockEntity machine(BlockPos position) {
        return new ChestBlockEntity(position, Blocks.CHEST.defaultBlockState());
    }
    private static CompoundTag record(UUID id, BlockPos position) {
        CompoundTag value = new CompoundTag(); NbtVersionCompat.putUUID(value, "id", id);
        value.putString("dimension", "minecraft:overworld"); value.putLong("position", position.asLong());
        CompoundTag root = new CompoundTag(); root.put("physical_machine", value); return root;
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
