package com.mistaboom.essence_ascendance.item;

import com.mistaboom.essence_ascendance.equipment.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Profile-backed action weapon. Native sword combat is never a temporary implementation. */
public final class AscendanceGlaiveItem extends Item implements EquipmentProfileItem, EmissiveAccentItem {
    public AscendanceGlaiveItem(Properties properties) { super(properties); }

    @Override public ResourceLocation equipmentProfileId() { return EquipmentProfiles.GLAIVE.id(); }
    @Override public int getEnchantmentValue() { return AscendanceToolTier.INSTANCE.getEnchantmentValue(); }

    /** Separate blade and butt accent masks share the established accent tint/emission material. */
    @Override public int materialTintIndex(int modelTintIndex) { return modelTintIndex == 3 ? 1 : modelTintIndex; }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return WeaponActionRuntime.tryUse(player, hand);
    }
}
