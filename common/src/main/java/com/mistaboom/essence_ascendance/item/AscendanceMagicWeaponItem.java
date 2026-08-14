package com.mistaboom.essence_ascendance.item;

import com.mistaboom.essence_ascendance.equipment.EquipmentProfileItem;
import com.mistaboom.essence_ascendance.equipment.EquipmentProfiles;
import com.mistaboom.essence_ascendance.equipment.EquipmentWeaponService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/*
 * Native Ascendance magic focus.
 *
 * This tranche intentionally starts with one neutral, universal magic action:
 * right-click performs a server-authoritative hitscan cast. Later spell/Essence
 * systems can replace or expand the presentation while continuing to consume
 * the same authoritative magic damage/cast-speed values.
 */
public final class AscendanceMagicWeaponItem
        extends Item
        implements EquipmentProfileItem {

    public AscendanceMagicWeaponItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public ResourceLocation equipmentProfileId() {
        return EquipmentProfiles.MAGIC_FOCUS.id();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(
            Level level,
            Player player,
            InteractionHand hand
    ) {
        ItemStack stack = player.getItemInHand(hand);

        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }

        if (player instanceof ServerPlayer serverPlayer) {
            EquipmentWeaponService.castMagic(
                    serverPlayer,
                    hand,
                    stack
            );
        }

        return InteractionResultHolder.sidedSuccess(
                stack,
                level.isClientSide
        );
    }
}
