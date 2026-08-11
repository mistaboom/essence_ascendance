package com.mistaboom.essence_ascendance.item;

import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;

/*
 * Shared structural Tier implementation used by the Ascendance melee
 * weapon now and intended for Ascendance tools in Issue 9.7.
 *
 * IMPORTANT:
 *
 * This is NOT an Ascendance progression tier and it is NOT the source of
 * dynamic combat power.
 *
 * Primary weapon properties such as attack damage and attack speed are
 * driven by WeaponChassisService. getAttackDamageBonus() therefore stays
 * zero and AscendanceItems does not install SwordItem attribute modifiers.
 *
 * Mining speed/harvest behavior is intentionally provisional until the
 * tool chassis is designed in Issue 9.7.
 */
public final class AscendanceToolTier
        implements Tier {

    public static final AscendanceToolTier INSTANCE =
            new AscendanceToolTier();


    private static final int USES =
            2031;

    /*
     * Provisional tool-side value. Do not use this as the final mining
     * chassis implementation; Issue 9.7 will decide how mining_speed and
     * mining_level map to actual tool behavior.
     */
    private static final float SPEED =
            9.0F;

    private static final float ATTACK_DAMAGE_BONUS =
            0.0F;

    private static final int ENCHANTMENT_VALUE =
            15;


    private AscendanceToolTier() {
    }


    @Override
    public int getUses() {

        return USES;
    }


    @Override
    public float getSpeed() {

        return SPEED;
    }


    @Override
    public float getAttackDamageBonus() {

        return ATTACK_DAMAGE_BONUS;
    }


    @Override
    public TagKey<Block> getIncorrectBlocksForDrops() {

        return BlockTags.INCORRECT_FOR_NETHERITE_TOOL;
    }


    @Override
    public int getEnchantmentValue() {

        return ENCHANTMENT_VALUE;
    }


    @Override
    public Ingredient getRepairIngredient() {

        return Ingredient.EMPTY;
    }
}
