package com.mistaboom.essence_ascendance.item;

import com.mistaboom.essence_ascendance.client.EquipmentTooltipClientState;
import com.mistaboom.essence_ascendance.equipment.EquipmentProfileItem;
import com.mistaboom.essence_ascendance.equipment.EquipmentProfiles;
import com.mistaboom.essence_ascendance.equipment.EquipmentWeaponService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;
import java.util.List;

/*
 * Native Ascendance ranged weapon.
 *
 * BowItem remains responsible for ammo selection, enchantments, durability,
 * vanilla projectile creation and normal bow interaction. We only translate
 * Ascendance's resolved draw speed into BowItem's 20-tick charge model and
 * adjust each spawned projectile with the resolved damage/speed values.
 */
public final class AscendanceRangedWeaponItem
        extends BowItem
        implements EquipmentProfileItem, EmissiveAccentItem {

    public AscendanceRangedWeaponItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public ResourceLocation equipmentProfileId() {
        return EquipmentProfiles.RANGED_WEAPON.id();
    }

    @Override
    public void releaseUsing(
            ItemStack stack,
            Level level,
            LivingEntity user,
            int timeLeft
    ) {
        int useDuration = getUseDuration(stack, user);
        int actualUseTicks = Math.max(0, useDuration - timeLeft);

        if (!(user instanceof ServerPlayer player)) {
            /*
             * The client receives the resolved full-draw duration on the
             * ItemStack. Feed the same synthetic vanilla charge duration into
             * BowItem on both logical sides so the release frame agrees with
             * the server-authoritative shot.
             */
            int syntheticUseTicks = EquipmentWeaponService.syntheticBowUseTicks(
                    EquipmentWeaponService.syncedRangedFullDrawTicks(stack),
                    actualUseTicks
            );

            super.releaseUsing(
                    stack,
                    level,
                    user,
                    useDuration - syntheticUseTicks
            );
            return;
        }

        EquipmentWeaponService.RangedState state =
                EquipmentWeaponService.evaluateRanged(player, stack);

        int syntheticUseTicks = EquipmentWeaponService.syntheticBowUseTicks(
                state,
                actualUseTicks
        );
        int syntheticTimeLeft = useDuration - syntheticUseTicks;

        EquipmentWeaponService.withRangedShotContext(
                player,
                state,
                actualUseTicks,
                syntheticUseTicks,
                () -> super.releaseUsing(
                        stack,
                        level,
                        user,
                        syntheticTimeLeft
                )
        );
    }

    @Override
    protected void shootProjectile(
            LivingEntity shooter,
            Projectile projectile,
            int index,
            float velocity,
            float inaccuracy,
            float angle,
            LivingEntity target
    ) {
        /*
         * Let vanilla establish the projectile's spawn/facing/motion first.
         * Ascendance then scales the finished motion vector and compensates
         * arrow base damage.
         */
        super.shootProjectile(
                shooter,
                projectile,
                index,
                velocity,
                inaccuracy,
                angle,
                target
        );

        EquipmentWeaponService.configureRangedProjectile(
                shooter,
                projectile,
                velocity
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
