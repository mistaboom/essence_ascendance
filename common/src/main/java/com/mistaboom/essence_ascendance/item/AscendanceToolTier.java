package com.mistaboom.essence_ascendance.item;

import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
//? if >=26.1.2
/*import net.minecraft.core.registries.Registries;*/
//? if >=26.1.2
/*import net.minecraft.resources.ResourceLocation;*/
//? if >=26.1.2
/*import net.minecraft.world.item.ToolMaterial;*/
//? if <26.1.2
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
 * tier/archetype equipment baseline plus invested stat bonuses and applied by
 * the equipment gameplay layer.
 *
 * The Tier exists because vanilla SwordItem / DiggerItem classes
 * require one for their normal Minecraft behavior.
 */
public final class AscendanceToolTier
        //? if <26.1.2
        implements Tier
{

    //? if >=26.1.2
    /*public static final ToolMaterial INSTANCE = new ToolMaterial(BlockTags.INCORRECT_FOR_IRON_TOOL, 2031, 6.0F, 0.0F, 15, TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("essence_ascendance", "no_vanilla_repair")));*/
    //? if <26.1.2
    public static final AscendanceToolTier INSTANCE = new AscendanceToolTier();


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
            6.0F;


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


    //? if <26.1.2
    @Override
    public int getUses() {

        return USES;
    }


    //? if <26.1.2
    @Override
    public float getSpeed() {

        return SPEED;
    }


    //? if <26.1.2
    @Override
    public float getAttackDamageBonus() {

        return ATTACK_DAMAGE_BONUS;
    }


    /*
     * Safe construction-time fallback matching built-in Dormant capability.
     *
     * Player Ascendance tier is dynamic while Minecraft's Tier object is
     * static per Item, so this tag is only the bootstrap component used before
     * AscendanceToolMiningService synchronizes the ItemStack. The authoritative
     * harvest level still comes from EquipmentBaselineService/config.
     */
    //? if <26.1.2
    @Override
    public TagKey<Block> getIncorrectBlocksForDrops() {

        return BlockTags.INCORRECT_FOR_IRON_TOOL;
    }


    //? if <26.1.2
    @Override
    public int getEnchantmentValue() {

        return ENCHANTMENT_VALUE;
    }


    /*
     * Ascendance equipment currently has no vanilla-material
     * repair ingredient.
     */
    //? if <26.1.2
    @Override
    public Ingredient getRepairIngredient() {

        //? if >=26.1.2
        /*return Ingredient.of();*/
        //? if <26.1.2
        return Ingredient.EMPTY;
    }
}
