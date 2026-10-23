package com.mistaboom.essence_ascendance.equipment;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;
import java.util.*;

/** Mutation-time classification only. Never called by attack, damage, or active-action validation. */
public final class TwoHandedEquipmentPolicy {
    public static final TagKey<Item> PASSIVE_OFFHAND = tag("essence_ascendance", "two_handed/passive_offhand");
    private static final List<TagKey<Item>> ACTIVE_TOOLS = List.of(tag("c", "tools"),
            tag("c", "tools/shield"), tag("c", "tools/shields"), tag("c", "shields"),
            tag("c", "tools/melee_weapons"), tag("c", "tools/bow"), tag("c", "tools/crossbow"));
    private static long generation;
    private static long classifications;
    private static Set<DataComponentType<?>> extraDependencies = Set.of();

    private TwoHandedEquipmentPolicy() { }
    private static TagKey<Item> tag(String namespace, String path) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(namespace, path));
    }
    public static long generation() { return generation; }
    public static long classifications() { return classifications; }
    public static void rulesChanged() {
        generation++;
        var dependencies = new HashSet<DataComponentType<?>>();
        for (var provider : EquipmentStatProviderRegistry.providers()) dependencies.addAll(provider.equipmentPolicyComponents());
        extraDependencies = Set.copyOf(dependencies);
        TwoHandedEquipmentService.rulesChanged();
    }

    public static EquipmentWeaponCapabilities capabilities(ItemStack stack) {
        if (stack.isEmpty()) return EquipmentWeaponCapabilities.DEFAULT;
        boolean twoHands = false, action = false;
        for (var provider : EquipmentStatProviderRegistry.providers()) {
            var c = provider.weaponCapabilities(stack);
            twoHands |= c.hands() == EquipmentWeaponCapabilities.HandRequirement.MAIN_HAND_TWO_HANDED;
            action |= c.melee() == EquipmentWeaponCapabilities.MeleeExecution.ACTION;
        }
        return new EquipmentWeaponCapabilities(twoHands ? EquipmentWeaponCapabilities.HandRequirement.MAIN_HAND_TWO_HANDED
                : EquipmentWeaponCapabilities.HandRequirement.UNRESTRICTED,
                action ? EquipmentWeaponCapabilities.MeleeExecution.ACTION : EquipmentWeaponCapabilities.MeleeExecution.NATIVE);
    }

    public static boolean compatible(ItemStack stack, net.minecraft.world.entity.LivingEntity holder) {
        if (stack.isEmpty()) return true;
        classifications++;
        // A positive passive declaration cannot turn a weapon, shield, or active hand tool into a charm.
        if (MasterworkTemperingRoles.activeHandEquipment(stack)) return false;
        if (stack.is(ItemTags.SWORDS) || ACTIVE_TOOLS.stream().anyMatch(stack::is)) return false;
        if (
                //? if >=26.1.2
                /*stack.getUseAnimation() != ItemUseAnimation.NONE*/
                //? if <26.1.2
                stack.getUseAnimation() != UseAnim.NONE
                || stack.getUseDuration(holder) > 0
                || stack.has(DataComponents.FOOD) || stack.has(DataComponents.TOOL)) return false;
        return stack.is(PASSIVE_OFFHAND) || EquipmentStatProviderRegistry.providers().stream()
                .anyMatch(provider -> provider.passiveOffhand(stack));
    }

    public static boolean relevant(DataComponentType<?> type) {
        return type == DataComponents.ATTRIBUTE_MODIFIERS || type == DataComponents.ENCHANTMENTS
                || type == DataComponents.TOOL || type == DataComponents.FOOD || extraDependencies.contains(type);
    }

    /** Component values, never stack serialization. Providers project only declared policy fields. */
    public static Object semanticKey(ItemStack stack) {
        var key = new ArrayList<Object>();
        for (var slot : List.of(net.minecraft.world.entity.EquipmentSlot.MAINHAND, net.minecraft.world.entity.EquipmentSlot.OFFHAND)) {
            key.add(MasterworkTemperingRoles.nativePositiveAdditive(stack, net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE, slot));
            key.add(MasterworkTemperingRoles.nativePositiveAdditive(stack, net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED, slot));
        }
        key.add(stack.get(DataComponents.TOOL));
        key.add(stack.get(DataComponents.FOOD));
        for (var provider : EquipmentStatProviderRegistry.providers()) key.add(provider.equipmentPolicyKey(stack));
        return key;
    }
}
