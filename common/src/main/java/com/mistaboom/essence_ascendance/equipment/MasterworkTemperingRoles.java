package com.mistaboom.essence_ascendance.equipment;

import com.mistaboom.essence_ascendance.stat.EssenceStats;
import com.mistaboom.essence_ascendance.stat.StatDefinition;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.BrushItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;

/**
 * Shared equipment-role classification for Masterwork Tempering gameplay and presentation.
 *
 * <p>The centralized equipment-stat provider registry is consulted first so first-party Ascendance equipment and
 * future third-party integrations classify through the same extension surface. Stable vanilla classes/tags and the
 * cross-loader c: mining-tool convention remain the fallback for ordinary/modded items that do not register an
 * Essence equipment provider.</p>
 */
public final class MasterworkTemperingRoles {
    private static final TagKey<Item> CONVENTIONAL_MINING_TOOLS = TagKey.create(
            Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("c", "tools/mining_tool")
    );

    private MasterworkTemperingRoles() { }

    public static boolean melee(ItemStack stack) {
        if (heldStrength(stack, EssenceStats.MELEE_DAMAGE) > 0) return true;
        return nativePositiveAdditive(stack, Attributes.ATTACK_DAMAGE, EquipmentSlot.MAINHAND) > 0;
    }

    /**
     * True for tools whose Masterwork performance expression is harvesting/mining speed.
     *
     * <p>Do not use the raw TOOL data component here: swords also expose tool rules for special blocks such as
     * cobwebs, which previously made a sword look like a mining tool. Digger classes plus standard item tags are a
     * more faithful semantic signal and are also what normal modded tools conventionally participate in.</p>
     */
    public static boolean mining(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (heldStrength(stack, EssenceStats.MINING_SPEED) > 0) return true;
        Item item = stack.getItem();
        return item instanceof DiggerItem
                || item instanceof ShearsItem
                || item instanceof BrushItem
                || stack.is(CONVENTIONAL_MINING_TOOLS)
                || stack.is(ItemTags.PICKAXES)
                || stack.is(ItemTags.AXES)
                || stack.is(ItemTags.SHOVELS)
                || stack.is(ItemTags.HOES);
    }

    /** Dedicated melee gear gets attack speed; dual-purpose harvesting tools keep mining speed instead. */
    public static boolean attackSpeed(ItemStack stack) {
        return melee(stack) && !mining(stack);
    }

    public static boolean armor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.getItem() instanceof ArmorItem) return true;
        EquipmentProfileDefinition profile = firstPartyProfile(stack);
        return profile != null && (profile.baselineMultiplier(EquipmentBaselineProperty.ARMOR) > 0
                || profile.baselineMultiplier(EquipmentBaselineProperty.TOUGHNESS) > 0);
    }

    public static boolean ranged(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (heldStrength(stack, EssenceStats.RANGED_DAMAGE) > 0) return true;
        return stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem;
    }

    public static boolean caster(ItemStack stack) {
        return heldStrength(stack, EssenceStats.MAGIC_DAMAGE) > 0;
    }

    public static EquipmentProfileDefinition firstPartyProfile(ItemStack stack) {
        if (stack == null || stack.isEmpty()
                || !(stack.getItem() instanceof EquipmentProfileItem profileItem)) return null;
        return EquipmentProfileRegistry.get(profileItem.equipmentProfileId()).orElse(null);
    }

    public static double nativePositiveAdditive(ItemStack stack, Holder<Attribute> attribute, EquipmentSlot slot) {
        if (stack == null || stack.isEmpty()) return 0;
        double[] amount = {0};
        stack.forEachModifier(slot, (holder, modifier) -> {
            if (holder.equals(attribute) && modifier.operation() == AttributeModifier.Operation.ADD_VALUE
                    && modifier.amount() > 0) amount[0] += modifier.amount();
        });
        return amount[0];
    }

    private static double heldStrength(ItemStack stack, StatDefinition stat) {
        if (stack == null || stack.isEmpty()) return 0;
        return EquipmentStatProviderRegistry.evaluate(stack).strength(EquipmentActivationType.HELD, stat);
    }
}
