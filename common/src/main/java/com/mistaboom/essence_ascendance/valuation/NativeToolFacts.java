package com.mistaboom.essence_ascendance.valuation;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

/** Exact stack facts and audited native ability sets; no loot, traits, affixes,
 * socket events or other gameplay callbacks are executed. Missing components can
 * disprove a necessary condition; present components do not authorize custom effects. */
record NativeToolFacts(boolean exactComponents, Set<String> components, Set<String> inactiveModifiers,
                       Set<String> falseConditions, Set<String> falseComponentPredicates,
                       Map<String, Boolean> abilities) {
    static final NativeToolFacts UNKNOWN = new NativeToolFacts(false, Set.of(), Set.of(), Set.of(), Set.of());
    private static final Set<String> NATIVE_ABILITY_TOOL_CLASSES = Set.of(
            "net.minecraft.world.item.Item", "net.minecraft.world.item.AirItem",
            "net.minecraft.world.item.ShearsItem", "net.minecraft.world.item.AxeItem",
            "net.minecraft.world.item.HoeItem", "net.minecraft.world.item.PickaxeItem",
            "net.minecraft.world.item.ShovelItem", "net.minecraft.world.item.SwordItem");
    NativeToolFacts(boolean exactComponents, Set<String> components, Set<String> inactiveModifiers,
                    Set<String> falseConditions, Set<String> falseComponentPredicates) {
        this(exactComponents, components, inactiveModifiers, falseConditions, falseComponentPredicates, Map.of());
    }
    NativeToolFacts {
        components = Set.copyOf(components); inactiveModifiers = Set.copyOf(inactiveModifiers);
        falseConditions = Set.copyOf(falseConditions); falseComponentPredicates = Set.copyOf(falseComponentPredicates);
        abilities = Map.copyOf(abilities);
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
        return new NativeToolFacts(true, components, inactive, conditions, predicates).withNativeAbilities(tool);
    }
    /** NeoForge's native shears_dig predicate delegates to an item method. Read the
     * audited native implementation's actual action set without invoking that method.
     * Custom runtime classes are excluded BEFORE reflection: merely asking for one
     * method can link their unrelated client-only method signatures on a server. */
    NativeToolFacts withNativeAbilities(ItemStack tool) {
        Class<?> runtimeType = tool.getItem().getClass();
        Map<String, Boolean> known = readNativeAbilities(runtimeType.getName(), () -> inspectNativeAbilities(runtimeType));
        return new NativeToolFacts(exactComponents, components, inactiveModifiers, falseConditions, falseComponentPredicates, known);
    }
    /** The reader must not inspect a custom class, including a native-tool subclass.
     * Loader rejection is optional evidence failure, not a failed generation. */
    static Map<String, Boolean> readNativeAbilities(String runtimeClass, Supplier<Map<String, Boolean>> reader) {
        if (!NATIVE_ABILITY_TOOL_CLASSES.contains(runtimeClass)) return Map.of();
        try {
            return Map.copyOf(reader.get());
        } catch (RuntimeException | LinkageError ignored) {
            return Map.of();
        }
    }
    private static Map<String, Boolean> inspectNativeAbilities(Class<?> runtimeType) {
        try {
            Class<?> ability = Class.forName("net.neoforged.neoforge.common.ItemAbility");
            Class<?> actions = Class.forName("net.neoforged.neoforge.common.ItemAbilities");
            Class.forName("net.neoforged.neoforge.common.loot.CanItemPerformAbility");
            var method = runtimeType.getMethod("canPerformAction", ItemStack.class, ability);
            String field = nativeActionSet(method.getDeclaringClass().getName());
            if (field != null) {
                Object shearsDig = actions.getField("SHEARS_DIG").get(null);
                boolean supported = !field.isEmpty() && ((Set<?>) actions.getField(field).get(null)).contains(shearsDig);
                return Map.of("shears_dig", supported);
            }
        } catch (ReflectiveOperationException ignored) { }
        return Map.of();
    }
    static String nativeActionSet(String declaringClass) {
        return switch (declaringClass) {
            case "net.minecraft.world.item.ShearsItem" -> "DEFAULT_SHEARS_ACTIONS";
            case "net.minecraft.world.item.AxeItem" -> "DEFAULT_AXE_ACTIONS";
            case "net.minecraft.world.item.HoeItem" -> "DEFAULT_HOE_ACTIONS";
            case "net.minecraft.world.item.PickaxeItem" -> "DEFAULT_PICKAXE_ACTIONS";
            case "net.minecraft.world.item.ShovelItem" -> "DEFAULT_SHOVEL_ACTIONS";
            case "net.minecraft.world.item.SwordItem" -> "DEFAULT_SWORD_ACTIONS";
            case "net.neoforged.neoforge.common.extensions.IItemExtension" -> "";
            default -> null;
        };
    }
    private static DataComponentType<?> component(String type, String field, boolean supplied) {
        try {
            Object value = Class.forName(type).getField(field).get(null);
            return (DataComponentType<?>)(supplied ? value.getClass().getMethod("get").invoke(value) : value);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("Audited component contract changed: " + type + "." + field, e); }
    }
    /** Additional scenario fact, never inferred from an empty hand. In Relics
     * 0.12.8 the destructive Greed branch requires an equipped Curio; its loop
     * is empty for a player wearing no accessories. Other releases stay unknown. */
    NativeToolFacts withoutAccessories(Function<String, String> versions) {
        var inactive = new TreeSet<>(inactiveModifiers);
        if ("0.12.8".equals(versions.apply("relics")))
            inactive.add("it.hurts.sskirillss.relics.level.GreedLootModifier");
        return new NativeToolFacts(exactComponents, components, inactive, falseConditions, falseComponentPredicates, abilities);
    }
    /** Explicit ordinary-action scenario, not a fact inferred from held equipment.
     * The audited condition requires this player's recorded giant-mining game tick
     * to equal the current tick. Waiting until a later tick and using native tools
     * keeps that condition false even if the attachment retains a conversion count. */
    NativeToolFacts outsideGiantMiningTick(Function<String, String> versions) {
        var conditions = new TreeSet<>(falseConditions);
        if ("4.8.3345".equals(versions.apply("twilightforest"))) conditions.add("twilightforest:giant_pick_used/this");
        return new NativeToolFacts(exactComponents, components, inactiveModifiers, conditions, falseComponentPredicates, abilities);
    }
    /** getNestHost returns the outermost class, losing the declaring nested
     * class of a method-reference lambda. Keep that owner without its VM suffix. */
    static String lambdaOwner(Object value) {
        Class<?> type = value.getClass(); String name = type.getName(); int marker = name.indexOf("$$Lambda");
        return type.isHidden() && type.isSynthetic() && marker > 0 ? name.substring(0, marker) : name;
    }
}
