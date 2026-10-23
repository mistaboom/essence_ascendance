package com.mistaboom.essence_ascendance.item;

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
    //? if >=26.1.2
    /*public boolean releaseUsing(*/
    //? if <26.1.2
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

            //? if >=26.1.2
            /*return super.releaseUsing(stack, level, user, useDuration - syntheticUseTicks);*/
            //? if <26.1.2
            super.releaseUsing(
                    //? if <26.1.2
                    stack,
                    //? if <26.1.2
                    level,
                    //? if <26.1.2
                    user,
                    //? if <26.1.2
                    useDuration - syntheticUseTicks
            //? if <26.1.2
            );
            //? if <26.1.2
            return;
        }

        EquipmentWeaponService.RangedState state =
                EquipmentWeaponService.evaluateRanged(player, stack);

        int syntheticUseTicks = EquipmentWeaponService.syntheticBowUseTicks(
                state,
                actualUseTicks
        );
        int syntheticTimeLeft = useDuration - syntheticUseTicks;

        //? if >=26.1.2
        /*final boolean[] launched = {false};*/
        EquipmentWeaponService.withRangedShotContext(
                player,
                state,
                actualUseTicks,
                syntheticUseTicks,
                //? if >=26.1.2
                /*() -> launched[0] = super.releaseUsing(stack, level, user, syntheticTimeLeft)*/
                //? if <26.1.2
                () -> super.releaseUsing(
                        //? if <26.1.2
                        stack,
                        //? if <26.1.2
                        level,
                        //? if <26.1.2
                        user,
                        //? if <26.1.2
                        syntheticTimeLeft
                //? if <26.1.2
                )
        );
        //? if >=26.1.2
        /*return launched[0];*/
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



}
