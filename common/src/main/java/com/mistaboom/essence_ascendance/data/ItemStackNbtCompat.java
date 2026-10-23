package com.mistaboom.essence_ascendance.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
//? if >=26.1.2
/*import net.minecraft.nbt.NbtOps;*/
//? if >=26.1.2
/*import net.minecraft.resources.RegistryOps;*/

import java.util.Optional;

/** Registry-aware stack serialization for portable workpieces and machine jobs. */
public final class ItemStackNbtCompat {
    private ItemStackNbtCompat() { }

    public static CompoundTag save(ItemStack stack, HolderLookup.Provider registries) {
        //? if >=26.1.2
        /*return (CompoundTag) ItemStack.CODEC.encodeStart(RegistryOps.create(NbtOps.INSTANCE, registries), stack).getOrThrow();*/
        //? if <26.1.2
        return (CompoundTag) stack.save(registries);
    }

    public static CompoundTag saveOptional(ItemStack stack, HolderLookup.Provider registries) {
        //? if >=26.1.2
        /*return (CompoundTag) ItemStack.OPTIONAL_CODEC.encodeStart(RegistryOps.create(NbtOps.INSTANCE, registries), stack).getOrThrow();*/
        //? if <26.1.2
        return (CompoundTag) stack.saveOptional(registries);
    }

    public static Optional<ItemStack> parse(HolderLookup.Provider registries, CompoundTag tag) {
        //? if >=26.1.2
        /*return ItemStack.CODEC.parse(RegistryOps.create(NbtOps.INSTANCE, registries), tag).result();*/
        //? if <26.1.2
        return ItemStack.parse(registries, tag);
    }

    public static ItemStack parseOptional(HolderLookup.Provider registries, CompoundTag tag) {
        //? if >=26.1.2
        /*return ItemStack.OPTIONAL_CODEC.parse(RegistryOps.create(NbtOps.INSTANCE, registries), tag).result().orElse(ItemStack.EMPTY);*/
        //? if <26.1.2
        return ItemStack.parseOptional(registries, tag);
    }
}
