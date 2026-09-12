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

/** Semi-automatic magic projectile weapon. Native use/release packets latch each press. */
public final class AscendanceCasterItem
        extends Item
        implements EquipmentProfileItem {

    private static final int ENCHANTMENT_VALUE = 15;

    public AscendanceCasterItem(Item.Properties properties) {
        super(properties);
    }

    /*
     * The caster is not a vanilla TieredItem, so it does not inherit an
     * enchantment value from AscendanceToolTier. Match the rest of Ascendance
     * equipment so the enchanting table can offer its supported enchantments.
     */
    @Override
    public int getEnchantmentValue() {
        return ENCHANTMENT_VALUE;
    }

    @Override
    public ResourceLocation equipmentProfileId() {
        return EquipmentProfiles.MAGIC_CASTER.id();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(
            Level level,
            Player player,
            InteractionHand hand
    ) {
        ItemStack stack = player.getItemInHand(hand);

        // Latch even a cooldown-rejected press. Holding the button never queues another cast.
        player.startUsingItem(hand);
        if (player.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.consume(stack);

        if (player instanceof ServerPlayer serverPlayer) {
            EquipmentWeaponService.castMagic(
                    serverPlayer,
                    hand,
                    stack
            );
        } else if (level.isClientSide) {
            // Predict only the native recovery meter, never a bolt, particle or successful hit.
            // Server cooldown packets reconcile this with the actual accepted launch.
            int ticks = EquipmentWeaponService.syncedMagicCastTicks(stack);
            if (ticks > 0) player.getCooldowns().addCooldown(this, ticks);
        }

        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack, net.minecraft.world.entity.LivingEntity entity) { return 72000; }

    @Override
    public net.minecraft.world.item.UseAnim getUseAnimation(ItemStack stack) { return net.minecraft.world.item.UseAnim.NONE; }

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
