package com.mistaboom.essence_ascendance.item;

import com.mistaboom.essence_ascendance.equipment.EquipmentProfileItem;
import com.mistaboom.essence_ascendance.equipment.EquipmentProfiles;
import com.mistaboom.essence_ascendance.equipment.EquipmentWeaponService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
//? if >=26.1.2
/*import net.minecraft.world.InteractionResult;*/
//? if <26.1.2
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Semi-automatic magic projectile weapon. Native use/release packets latch each press. */
public final class AscendanceCasterItem
        extends Item
        implements EquipmentProfileItem, EmissiveAccentItem {

    private static final int ENCHANTMENT_VALUE = 15;

    public AscendanceCasterItem(Item.Properties properties) {
        super(properties);
    }

    /*
     * The caster is not a vanilla TieredItem, so it does not inherit an
     * enchantment value from AscendanceToolTier. Match the rest of Ascendance
     * equipment so the enchanting table can offer its supported enchantments.
     */
    //? if <26.1.2
    @Override
    //? if <26.1.2
    public int getEnchantmentValue() {
        //? if <26.1.2
        return ENCHANTMENT_VALUE;
    //? if <26.1.2
    }

    @Override
    public ResourceLocation equipmentProfileId() {
        return EquipmentProfiles.MAGIC_CASTER.id();
    }

    @Override
    //? if >=26.1.2
    /*public InteractionResult use(*/
    //? if <26.1.2
    public InteractionResultHolder<ItemStack> use(
            Level level,
            Player player,
            InteractionHand hand
    ) {
        ItemStack stack = player.getItemInHand(hand);

        // Latch even a cooldown-rejected press. Holding the button never queues another cast.
        player.startUsingItem(hand);
        //? if >=26.1.2
        /*if (player.getCooldowns().isOnCooldown(stack)) return InteractionResult.CONSUME;*/
        //? if <26.1.2
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
            //? if >=26.1.2
            /*if (ticks > 0) player.getCooldowns().addCooldown(stack, ticks);*/
            //? if <26.1.2
            if (ticks > 0) player.getCooldowns().addCooldown(this, ticks);
        }

        //? if >=26.1.2
        /*return InteractionResult.CONSUME;*/
        //? if <26.1.2
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack, net.minecraft.world.entity.LivingEntity entity) { return 72000; }

    @Override
    //? if >=26.1.2
    /*public net.minecraft.world.item.ItemUseAnimation getUseAnimation(ItemStack stack) { return net.minecraft.world.item.ItemUseAnimation.NONE; }*/
    //? if <26.1.2
    public net.minecraft.world.item.UseAnim getUseAnimation(ItemStack stack) { return net.minecraft.world.item.UseAnim.NONE; }



}
