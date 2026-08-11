package com.mistaboom.essence_ascendance.equipment;

import net.minecraft.world.item.ItemStack;

/*
 * Resolves conduit capabilities supplied by an ItemStack.
 *
 * Providers describe what the item COULD contribute.
 *
 * EquipmentConduitResolver decides whether those capabilities are
 * valid in the current context:
 *
 * - held item
 * - equipped armor slot
 * - action hand
 *
 * Providers may return multiple conduit strengths.
 *
 * Example future hybrid artifact:
 *
 * MELEE_WEAPON = 1.0
 * MAGIC_WEAPON = 0.5
 */
@FunctionalInterface
public interface EquipmentConduitProvider {

    EquipmentConduitState evaluate(
            ItemStack stack
    );
}