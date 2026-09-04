package com.mistaboom.essence_ascendance.equipment;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistent soulbinding identity stored directly on Ascendance equipment.
 *
 * A binding is permanent during ordinary gameplay.  It is intentionally
 * independent from completed equipment tier and partial infusion progress so
 * later tier changes, repairs, enchantments, names, and other ItemStack state
 * cannot accidentally erase ownership.
 */
public final class SoulboundEquipmentData {

    private static final String ROOT_TAG = "essence_ascendance_soulbound";
    private static final String OWNER_TAG = "owner";
    private static final String OWNER_NAME_TAG = "owner_name";
    private static final String BINDING_ID_TAG = "binding_id";

    private SoulboundEquipmentData() {
    }

    public static boolean isSoulbound(ItemStack stack) {
        return read(stack).isPresent();
    }

    public static Optional<Binding> read(ItemStack stack) {
        if (!EquipmentTierData.isAscendanceEquipment(stack)) {
            return Optional.empty();
        }

        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return Optional.empty();
        }

        CompoundTag outer = customData.copyTag();
        if (!outer.contains(ROOT_TAG, Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }

        CompoundTag root = outer.getCompound(ROOT_TAG);
        if (!root.hasUUID(OWNER_TAG) || !root.hasUUID(BINDING_ID_TAG)) {
            return Optional.empty();
        }

        UUID ownerId = root.getUUID(OWNER_TAG);
        UUID bindingId = root.getUUID(BINDING_ID_TAG);
        String ownerName = root.getString(OWNER_NAME_TAG);
        return Optional.of(new Binding(ownerId, ownerName, bindingId));
    }

    /**
     * Bind an unbound Ascendance artifact. Existing bindings are never
     * overwritten by ordinary gameplay, even if another player's Infuser later
     * contributes Essence to the item.
     */
    public static Binding bind(ItemStack stack, UUID ownerId, String ownerName) {
        if (!EquipmentTierData.isAscendanceEquipment(stack)) {
            throw new IllegalArgumentException("Only Ascendance equipment may be soulbound");
        }
        if (ownerId == null) {
            throw new IllegalArgumentException("Soulbound owner may not be null");
        }

        Optional<Binding> existing = read(stack);
        if (existing.isPresent()) {
            Binding binding = existing.get();

            /*
             * Refresh only the cosmetic last-known player name when the UUID
             * still matches. Never use a name change as a rebinding mechanism.
             */
            String normalizedName = normalizeName(ownerName);
            if (binding.ownerId().equals(ownerId)
                    && !normalizedName.isBlank()
                    && !normalizedName.equals(binding.ownerName())) {
                CustomData.update(DataComponents.CUSTOM_DATA, stack, outer -> {
                    CompoundTag root = outer.getCompound(ROOT_TAG);
                    root.putString(OWNER_NAME_TAG, normalizedName);
                    outer.put(ROOT_TAG, root);
                });
                return read(stack).orElse(binding);
            }
            return binding;
        }

        UUID bindingId = UUID.randomUUID();
        String normalizedName = normalizeName(ownerName);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, outer -> {
            CompoundTag root = new CompoundTag();
            root.putUUID(OWNER_TAG, ownerId);
            root.putUUID(BINDING_ID_TAG, bindingId);
            if (!normalizedName.isBlank()) {
                root.putString(OWNER_NAME_TAG, normalizedName);
            }
            outer.put(ROOT_TAG, root);
        });

        return read(stack).orElseThrow(
                () -> new IllegalStateException("Failed to persist Ascendance equipment soulbinding")
        );
    }

    /** Admin/testing escape hatch. Ordinary gameplay never calls this. */
    public static void clear(ItemStack stack) {
        if (!EquipmentTierData.isAscendanceEquipment(stack)) {
            return;
        }
        CustomData.update(
                DataComponents.CUSTOM_DATA,
                stack,
                outer -> outer.remove(ROOT_TAG)
        );
    }

    public static boolean isBoundTo(ItemStack stack, UUID playerId) {
        return playerId != null
                && read(stack)
                .map(binding -> binding.ownerId().equals(playerId))
                .orElse(false);
    }

    private static String normalizeName(String ownerName) {
        return ownerName == null ? "" : ownerName.trim();
    }

    public record Binding(UUID ownerId, String ownerName, UUID bindingId) {
        public String displayOwner() {
            return ownerName == null || ownerName.isBlank()
                    ? ownerId.toString()
                    : ownerName;
        }
    }
}
