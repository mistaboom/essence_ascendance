package com.mistaboom.essence_ascendance.equipment;

import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/*
 * Built-in provider for first-party Essence Ascendance equipment.
 *
 * The native item exposes only a profile ID. The profile itself remains
 * centralized in EquipmentProfiles/EquipmentProfileRegistry.
 */
public final class NativeEquipmentStatProvider
        implements EquipmentStatProvider {

    public static final NativeEquipmentStatProvider INSTANCE =
            new NativeEquipmentStatProvider();

    private NativeEquipmentStatProvider() {
    }

    @Override
    public EquipmentWeaponCapabilities weaponCapabilities(ItemStack stack) {
        var profile = MasterworkTemperingRoles.firstPartyProfile(stack);
        return profile == null ? EquipmentWeaponCapabilities.DEFAULT : profile.weaponCapabilities();
    }

    @Override
    public EquipmentStatProfile evaluate(ItemStack stack) {
        Objects.requireNonNull(stack, "Item stack cannot be null");

        if (stack.isEmpty()
                || !(stack.getItem() instanceof EquipmentProfileItem profiledItem)) {
            return EquipmentStatProfile.none();
        }

        return EquipmentProfileRegistry
                .get(profiledItem.equipmentProfileId())
                .map(EquipmentStatProfile::fromDefinition)
                .orElseGet(EquipmentStatProfile::none);
    }
}
