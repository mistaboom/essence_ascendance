package com.mistaboom.essence_ascendance.item;

import com.mistaboom.essence_ascendance.client.EquipmentTooltipClientState;
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
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;
import java.util.List;

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

    private static final int ENCHANTMENT_VALUE = 15;

    public AscendanceMagicWeaponItem(Item.Properties properties) {
        super(properties);
    }

    /*
     * The magic focus is not a vanilla TieredItem, so it does not inherit an
     * enchantment value from AscendanceToolTier. Match the rest of Ascendance
     * equipment so the enchanting table can offer its supported enchantments.
     */
    @Override
    public int getEnchantmentValue() {
        return ENCHANTMENT_VALUE;
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

    @Override
    public void appendHoverText(
            ItemStack stack,
            Item.TooltipContext context,
            List<Component> tooltipComponents,
            TooltipFlag tooltipFlag
    ) {
        super.appendHoverText(
                stack,
                context,
                tooltipComponents,
                tooltipFlag
        );
        EquipmentTooltipClientState.append(
                stack,
                tooltipComponents
        );
    }

}
