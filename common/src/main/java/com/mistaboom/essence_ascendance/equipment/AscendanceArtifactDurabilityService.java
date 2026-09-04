package com.mistaboom.essence_ascendance.equipment;

import net.minecraft.world.item.ItemStack;

/**
 * Makes Ascendance artifacts permanent at durability exhaustion.
 *
 * The final durability hit is intercepted after vanilla enchantments have
 * calculated the real durability loss, but before ItemStack reaches its
 * shrink/remove break path. The artifact is marked Fractured and retained at
 * the last non-breaking damage value.
 */
public final class AscendanceArtifactDurabilityService {

    private AscendanceArtifactDurabilityService() {}

    public static int preventDamageWhileFractured(
            ItemStack stack,
            int requestedDamage
    ) {
        return FracturedEquipmentData.isFractured(stack)
                ? 0
                : requestedDamage;
    }

    /**
     * Receives the post-enchantment durability loss. If applying it would
     * make vanilla break the artifact, persist Fractured first and cap the
     * returned loss so the stack remains at maxDamage - 1.
     *
     * Keeping one internal durability point is intentional: Minecraft 1.21.1
     * removes a stack once damage reaches maxDamage. Fractured is the actual
     * gameplay state, and further durability loss is blocked until Repair.
     */
    public static int fractureBeforeVanillaBreak(
            ItemStack stack,
            int actualDamage
    ) {
        if (actualDamage <= 0
                || !FracturedEquipmentData.isAscendanceArtifact(stack)) {
            return actualDamage;
        }

        if (FracturedEquipmentData.isFractured(stack)) {
            return 0;
        }

        int maxDamage = stack.getMaxDamage();
        if (maxDamage <= 0) {
            return actualDamage;
        }

        int currentDamage = Math.max(0, stack.getDamageValue());
        long resultingDamage = (long) currentDamage + actualDamage;
        if (resultingDamage < maxDamage) {
            return actualDamage;
        }

        FracturedEquipmentData.markFractured(stack);

        int lastSafeDamage = Math.max(0, maxDamage - 1);
        int remainingSafeLoss = Math.max(0, lastSafeDamage - currentDamage);
        return Math.min(actualDamage, remainingSafeLoss);
    }
}
