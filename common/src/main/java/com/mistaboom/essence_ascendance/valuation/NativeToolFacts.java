package com.mistaboom.essence_ascendance.valuation;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import java.util.function.Function;

/** Exact stack facts for conservative negative predicates; no loot, traits, affixes,
 * socket events or other gameplay callbacks are executed. Missing components can
 * disprove a necessary condition; present components do not authorize custom effects. */
record NativeToolFacts(boolean exactComponents, Set<String> components, Set<String> inactiveModifiers,
                       Set<String> falseConditions, Set<String> falseComponentPredicates) {
    static final NativeToolFacts UNKNOWN = new NativeToolFacts(false, Set.of(), Set.of(), Set.of(), Set.of());
    NativeToolFacts {
        components = Set.copyOf(components); inactiveModifiers = Set.copyOf(inactiveModifiers);
        falseConditions = Set.copyOf(falseConditions); falseComponentPredicates = Set.copyOf(falseComponentPredicates);
    }
    static NativeToolFacts capture(GenerationDataSnapshot inputs, ItemStack tool) {
        var components = new TreeSet<String>();
        for (var entry : tool.getComponents()) components.add(BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(entry.type()).toString());
        var inactive = new TreeSet<String>(); var conditions = new TreeSet<String>(); var predicates = new TreeSet<String>();
        if ("8.8.0".equals(inputs.installedVersion("apotheosis"))) {
            // An empty affix component and no stored gems leave both callback loops
            // empty. Socket counts alone cannot supply a gem or affix.
            var affixes = component("dev.shadowsoffire.apotheosis.Apoth$Components", "AFFIXES", false);
            var gems = component("dev.shadowsoffire.apotheosis.Apoth$Components", "SOCKETED_GEMS", false);
            if (!tool.has(affixes) && !tool.has(gems)) inactive.add("dev.shadowsoffire.apotheosis.loot.modifiers.AffixHookLootModifier");
        }
        if ("4.2.1.1".equals(inputs.installedVersion("silentgear"))) {
            try {
                if (!Class.forName("net.silentchaos512.gear.api.item.GearItem").isInstance(tool.getItem())) {
                    inactive.add("net.silentchaos512.gear.loot.modifier.BonusDropsTraitLootModifier");
                    conditions.add("silentgear:has_trait");
                }
            } catch (ClassNotFoundException e) { throw new IllegalStateException("Audited GearItem contract unavailable", e); }
        }
        if ("2.6.1".equals(inputs.installedVersion("forbidden_arcanus"))) {
            var modifier = component("com.stal111.forbidden_arcanus.core.init.ModDataComponents", "ITEM_MODIFIER", true);
            if (!tool.has(modifier)) predicates.add("forbidden_arcanus:modifier");
        }
        return new NativeToolFacts(true, components, inactive, conditions, predicates);
    }
    private static DataComponentType<?> component(String type, String field, boolean supplied) {
        try {
            Object value = Class.forName(type).getField(field).get(null);
            return (DataComponentType<?>)(supplied ? value.getClass().getMethod("get").invoke(value) : value);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("Audited component contract changed: " + type + "." + field, e); }
    }
    /** getNestHost returns the outermost class, losing the declaring nested
     * class of a method-reference lambda. Keep that owner without its VM suffix. */
    static String lambdaOwner(Object value) {
        Class<?> type = value.getClass(); String name = type.getName(); int marker = name.indexOf("$$Lambda");
        return type.isHidden() && type.isSynthetic() && marker > 0 ? name.substring(0, marker) : name;
    }
}
