package com.mistaboom.essence_ascendance.valuation;

import com.google.gson.GsonBuilder;
import com.mistaboom.essence_ascendance.balance.config.BalanceOverrides;
import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Differential snapshot captured before the generation-cache optimization. No world is opened. */
public final class ValuationPerformanceTest {
    public static void main(String[] args) throws Exception {
        Thread.currentThread().setUncaughtExceptionHandler((thread, failure) -> failure.printStackTrace(
                new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.err))));
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); EssenceTypes.init();
        ValuationGenerationInputs.configure(new BalanceOverrides(List.of(new BalanceOverrides.FactOverride(
                "fixture_source", BalanceOverrides.SubjectKind.ITEM, "minecraft:clay_ball", 1000,
                Map.of("resource_value", 8.0, "attainable", true), "fixture", 1)), Map.of()));
        List<Item> items = List.of(Items.BRICK, Items.CLAY_BALL, Items.PAPER, Items.FLINT, Items.CHARCOAL,
                Items.ECHO_SHARD, Items.PRISMARINE_SHARD, Items.POPPED_CHORUS_FRUIT, Items.NAUTILUS_SHELL,
                Items.GUNPOWDER, Items.DIAMOND_SWORD, Items.BOW, Items.SHIELD, Items.APPLE, Items.IRON_PICKAXE,
                Items.EMERALD, Items.IRON_INGOT, Items.IRON_NUGGET, Items.IRON_BLOCK);
        Map<Item, List<ProceduralValuationIndex.RecipeModel>> outputs = new IdentityHashMap<>();
        Map<Item, List<ProceduralValuationIndex.RecipeUse>> uses = new IdentityHashMap<>();
        // Cycles, repeated slots, alternative ordering/ties, self outputs and the 96-recipe boundary.
        for (int n = 0; n < 125; n++) {
            List<Item> alternatives = new ArrayList<>(items.subList(0, 10));
            Collections.rotate(alternatives, n % alternatives.size());
            alternatives.add(alternatives.getFirst());
            var recipe = new ProceduralValuationIndex.RecipeModel(ResourceLocation.parse("fixture:r" + String.format(Locale.ROOT, "%03d", n)),
                    RecipeType.CRAFTING, items.get(n % items.size()), 1 + n % 3,
                    List.of(new ProceduralValuationIndex.IngredientChoice(alternatives),
                            new ProceduralValuationIndex.IngredientChoice(List.of(items.get((n + 1) % 10)))));
            outputs.computeIfAbsent(recipe.outputItem(), k -> new ArrayList<>()).add(recipe);
            for (var ingredient : recipe.ingredients()) for (Item item : ingredient.alternatives())
                uses.computeIfAbsent(item, k -> new ArrayList<>()).add(new ProceduralValuationIndex.RecipeUse(recipe));
        }
        var constructor = ProceduralValuationIndex.class.getDeclaredConstructors()[0]; constructor.setAccessible(true);
        var index = (ProceduralValuationIndex) constructor.newInstance(outputs, uses, Map.of(), Map.of(), Map.of(),
                Map.of(), Map.of(), Map.of(), Map.of(), null, null, null, null);
        Class<?> contextClass = Class.forName(ProceduralValuationEngine.class.getName() + "$EvaluationContext");
        Constructor<?> contextConstructor = contextClass.getDeclaredConstructor(ProceduralValuationIndex.class);
        contextConstructor.setAccessible(true);
        Method solve = ProceduralValuationEngine.class.getDeclaredMethod("solveAcquisitionGraph", List.class, contextClass);
        Method evaluate = ProceduralValuationEngine.class.getDeclaredMethod("evaluateItem", ProceduralValuationIndex.class, Item.class, contextClass);
        solve.setAccessible(true); evaluate.setAccessible(true);
        Map<String, Object> result = new TreeMap<>();
        long start = System.nanoTime();
        for (boolean reversed : List.of(false, true)) {
            Object context = contextConstructor.newInstance(index);
            List<Item> order = new ArrayList<>(items); if (reversed) Collections.reverse(order);
            solve.invoke(null, order, context);
            Map<String, Object> values = new TreeMap<>();
            for (Item item : order) values.put(BuiltInRegistries.ITEM.getKey(item).toString(), canonical(evaluate.invoke(null, index, item, context)));
            result.put(reversed ? "reverse" : "forward", values);
        }
        String snapshot = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(result) + "\n";
        Path observed = Path.of("build/valuation-performance-observed.json"); Files.createDirectories(observed.getParent());
        Files.writeString(observed, snapshot);
        var out = new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out));
        out.println("Valuation fixture elapsed ms=" + (System.nanoTime() - start) / 1_000_000);
        if (args.length == 1 && args[0].equals("--capture-reference")) {
            out.println("Reference capture only: " + observed.toAbsolutePath()); return;
        }
        try (var stream = ValuationPerformanceTest.class.getResourceAsStream("/valuation/valuation-reference.json")) {
            if (stream == null) throw new AssertionError("Missing pre-optimization reference");
            if (!snapshot.equals(new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)))
                throw new AssertionError("Valuation changed from pre-optimization results; inspect " + observed);
        }
        out.println("ValuationPerformanceTest PASS: 38 complete valuations match pre-optimization snapshots, including routing, acquisition, choices, confidence and provenance");
    }

    private static Object canonical(Object value) throws ReflectiveOperationException {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) return value;
        if (value instanceof ResourceLocation || value instanceof Enum<?>) return value.toString();
        if (value instanceof EssenceDefinition essence) return essence.id().toString();
        if (value instanceof Optional<?> optional) return canonical(optional.orElse(null));
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new TreeMap<>();
            for (var entry : map.entrySet()) result.put(String.valueOf(canonical(entry.getKey())), canonical(entry.getValue()));
            return result;
        }
        if (value instanceof Collection<?> list) {
            List<Object> result = new ArrayList<>(); for (Object element : list) result.add(canonical(element)); return result;
        }
        if (value.getClass().isRecord()) {
            Map<String, Object> result = new TreeMap<>();
            for (var component : value.getClass().getRecordComponents()) result.put(component.getName(), canonical(component.getAccessor().invoke(value)));
            return result;
        }
        throw new IllegalArgumentException("No canonical representation for " + value.getClass());
    }
}
