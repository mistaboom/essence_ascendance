package com.mistaboom.essence_ascendance.item;

import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;

/*
 * Neutral vanilla bootstrap tier shared by Ascendance weapons
 * and tools.
 *
 * IMPORTANT:
 *
 * This is NOT the player's actual Ascendance equipment power.
 *
 * Primary equipment behavior is calculated separately through the
 * tier/archetype equipment baseline plus invested stat bonuses, and later
 * applied by the gameplay-effect layer.
 *
 * The Tier exists because vanilla SwordItem / DiggerItem classes
 * require one for their normal Minecraft behavior.
 */
public final class AscendanceToolTier
        implements Tier {

    public static final AscendanceToolTier INSTANCE =
            new AscendanceToolTier();


    /*
     * Durability remains a normal Minecraft mechanic.
     *
     * durability_efficiency will modify durability consumption
     * later in the gameplay-effect layer.
     */
    private static final int USES =
            2031;


    /*
     * Neutral mining baseline.
     *
     * We deliberately do NOT grant netherite mining speed here.
     *
     * Ascendance tool mining speed will be tier/archetype baseline power with
     * the player's mining_speed investment applied on top.
     */
    private static final float SPEED =
            1.0F;


    /*
     * Tool and weapon combat values are baseline/stat driven.
     *
     * Do not place a permanent attack-damage bonus here because
     * doing so would double-count melee progression.
     */
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


    /*
     * Lowest normal vanilla harvest baseline.
     *
     * Player Ascendance tier is dynamic while Minecraft's Tier object is
     * static per Item, so harvest capability cannot safely live here. The
     * later block-harvest gameplay layer must consult EquipmentBaselineService
     * for the player's current tier-derived harvest level.
     */
    @Override
    public TagKey<Block> getIncorrectBlocksForDrops() {

        return BlockTags.INCORRECT_FOR_WOODEN_TOOL;
    }


    @Override
    public int getEnchantmentValue() {

        return ENCHANTMENT_VALUE;
    }


    /*
     * Ascendance equipment currently has no vanilla-material
     * repair ingredient.
     */
    @Override
    public Ingredient getRepairIngredient() {

        return Ingredient.EMPTY;
    }
}