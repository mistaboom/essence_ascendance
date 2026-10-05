package com.mistaboom.essence_ascendance.valuation;

import com.google.gson.*;
import com.mistaboom.essence_ascendance.balance.config.*;
import com.mistaboom.essence_ascendance.balance.economy.*;
import com.mistaboom.essence_ascendance.balance.engine.*;
import com.mistaboom.essence_ascendance.balance.generated.*;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import net.minecraft.SharedConstants;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import java.lang.reflect.*;
import java.util.*;

/** Actual shared projection/solver/economy/profile seams. No script execution, world or installed-profile writes. */
public final class EffectiveProductionTest {
    private static int checks;
    private static HolderLookup.Provider registries;
    private static RecipeSerializer<ChargeFixture> chargeSerializer;
    public static void main(String[] args) throws Exception {
        Thread.currentThread().setUncaughtExceptionHandler((thread, failure) -> failure.printStackTrace(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.err))));
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); registerChargeFixture(); EssenceTypes.init();
        registries = VanillaRegistries.createLookup();
        ValuationGenerationInputs.configure(BalanceOverrides.empty());
        if (args.length == 2 && args[0].equals("--benchmark")) { benchmark(java.nio.file.Path.of(args[1])); return; }
        effectiveInventory(); unpackedShapedRecipes(); emptyNominalResults(); customCrafting(); unsupportedCustomEncoding(); normalizedFacts(); machineAcquisition();
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out)).println("EffectiveProductionTest: " + checks + " checks PASS");
    }
    private static void benchmark(java.nio.file.Path output) throws Exception {
        java.nio.file.Files.createDirectories(output);
        var value = holder("fixture:template", Items.GOLD_INGOT, 2).value();
        List<RecipeHolder<?>> recipes = new ArrayList<>();
        for (int i = 0; i < 96_184; i++) recipes.add(new RecipeHolder<>(id("fixture:synthetic_" + i), value));
        for (int trial = 0; trial < 3; trial++) {
            try (var operation = BalancePerformance.begin("offline_synthetic_production", "96184 native vanilla objects; no pack scan")) {
                var graph = EffectiveProduction.collect(recipes, registries, mod -> null, (family, reason) -> { throw new AssertionError(reason); });
                if (graph.processes().size() != recipes.size()) throw new AssertionError("Normalization coverage lost");
                operation.complete("normalized_synthetic");
            }
            java.nio.file.Files.writeString(output.resolve("trial-" + trial + ".json"), BalanceDocument.GSON.toJson(BalancePerformance.lastSnapshot()));
        }
        java.nio.file.Files.writeString(output.resolve("identity.json"), BalanceDocument.GSON.toJson(Map.of("java", System.getProperty("java.runtime.version"),
                "heapBytes", Runtime.getRuntime().maxMemory(), "recipes", recipes.size(), "boundary", "Synthetic repeated vanilla recipe normalization only; not ATM10 or full generation/rebuild")));
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out)).println("Production normalization scalability replay PASS: 3 synthetic trials; no native pack timing claim");
    }
    private static void effectiveInventory() {
        var first = holder("fixture:same", Items.GOLD_INGOT, 2);
        var addition = holder("fixture:added", Items.IRON_INGOT, 3);
        List<String> unsupported = new ArrayList<>();
        var original = EffectiveProduction.collect(List.of(first), registries, id -> null, (family, reason) -> unsupported.add(family + reason));
        var changed = EffectiveProduction.collect(List.of(holder("fixture:same", Items.DIAMOND, 5), addition), registries, id -> null, (family, reason) -> unsupported.add(family + reason));
        check(original.processes().getFirst().outputs().getFirst().itemId().equals("minecraft:gold_ingot"), "original effective result");
        check(changed.processes().size() == 2 && changed.processes().stream().anyMatch(p -> p.id().equals("fixture:added")), "added recipe observed");
        var replacement = changed.processes().stream().filter(p -> p.id().equals("fixture:same")).findFirst().orElseThrow();
        check(replacement.outputs().getFirst().itemId().equals("minecraft:diamond") && replacement.outputs().getFirst().count() == 5, "same-ID replacement uses effective object");
        check(changed.processes().stream().noneMatch(p -> p.outputs().getFirst().itemId().equals("minecraft:gold_ingot")), "old same-ID result absent");
        check(EffectiveProduction.collect(List.of(), registries, id -> null, (family, reason) -> {}).processes().isEmpty(), "removed recipes cannot resurrect from raw resources");
        check(unsupported.isEmpty(), "supported ordinary core recipes normalized");
        var smithing = new RecipeHolder<>(id("fixture:smithing"), new SmithingTransformRecipe(Ingredient.of(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE),
                Ingredient.of(Items.DIAMOND_SWORD), Ingredient.of(Items.NETHERITE_INGOT), new ItemStack(Items.NETHERITE_SWORD)));
        var smithingGraph = EffectiveProduction.collect(List.of(smithing), registries, mod -> null, (family, reason) -> {});
        check(smithingGraph.processes().getFirst().inputs().size() == 3, "smithing ingredients recovered from effective native codec");
        check(smithingGraph.processes().getFirst().metadata().containsKey("variant_transform"), "smithing base component-copy behavior retained");
        check(EffectiveProduction.adapter("create:crushing", "com.simibubi.create.content.kinetics.crusher.CrushingRecipe", id -> "6.0.10") != null, "installed Create API range accepted");
        check(EffectiveProduction.adapter("create:crushing", "com.simibubi.create.content.kinetics.crusher.CrushingRecipe", id -> "7.0.0") == null, "unaudited API range unsupported");
        check(EffectiveProduction.adapter("mekanism:crystallizing", "mekanism.api.recipes.basic.BasicChemicalCrystallizerRecipe", id -> "10.7.19") != null, "installed crystallizing registry family accepted");
        check(EffectiveProduction.adapter("create:sequenced_assembly", "com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe", id -> "6.0.10") == null, "sequenced joint probabilities unsupported");
        var dynamic = new Recipe<RecipeInput>() {
            public boolean matches(RecipeInput input, net.minecraft.world.level.Level level) { return false; }
            public ItemStack assemble(RecipeInput input, HolderLookup.Provider provider) { return ItemStack.EMPTY; }
            public boolean canCraftInDimensions(int x, int y) { return true; }
            public ItemStack getResultItem(HolderLookup.Provider provider) { return ItemStack.EMPTY; }
            public RecipeSerializer<?> getSerializer() { return RecipeSerializer.SHAPELESS_RECIPE; }
            public RecipeType<?> getType() { return new RecipeType<Recipe<RecipeInput>>() {}; }
        };
        EffectiveProduction.collect(List.of(new RecipeHolder<>(id("fixture:dynamic"), dynamic)), registries, mod -> null,
                (family, reason) -> unsupported.add(family + ":" + reason));
        check(unsupported.size() == 1 && unsupported.getFirst().contains("unsupported machine/addon semantics"), "unsupported family explicitly reported");
    }
    private static RecipeHolder<?> holder(String id, Item output, int count) {
        return new RecipeHolder<>(id(id), new ShapelessRecipe("", CraftingBookCategory.MISC, new ItemStack(output, count),
                NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.STONE))));
    }
    private static void unpackedShapedRecipes() {
        var slots = NonNullList.of(Ingredient.EMPTY, Ingredient.EMPTY, Ingredient.of(Items.MILK_BUCKET), Ingredient.EMPTY,
                Ingredient.of(Items.STONE, Items.DIRT), Ingredient.EMPTY, Ingredient.of(Items.STONE, Items.DIRT));
        var pattern = new ShapedRecipePattern(3, 2, slots, Optional.empty());
        ItemStack result = new ItemStack(Items.GOLD_INGOT, 3);
        result.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Effective component result"));
        var shaped = new ShapedRecipe("fixture:runtime", CraftingBookCategory.BUILDING, pattern, result, false);
        var ops = net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE, registries);
        check(Recipe.CODEC.encodeStart(ops, shaped).error().orElseThrow().message().contains("Cannot encode unpacked recipe"),
                "Native runtime-created shaped pattern reproduces the actual rejection");
        var graph = EffectiveProduction.collect(List.of(new RecipeHolder<>(id("fixture:runtime_shaped"), shaped)), registries,
                mod -> null, (family, reason) -> {});
        var process = graph.processes().getFirst();
        var definition = process.effectiveDefinition();
        check(definition.get("width").getAsInt() == 3 && definition.get("height").getAsInt() == 2
                && definition.getAsJsonArray("ingredients").size() == 6 && definition.getAsJsonArray("ingredients").get(0).isJsonNull()
                && definition.getAsJsonArray("ingredients").get(4).isJsonNull(), "Runtime shape retains dimensions, padding and empty slots without shrinking");
        check(process.inputs().size() == 3 && process.inputs().get(1).alternatives().size() == 2
                && process.inputs().get(2).alternatives().size() == 2, "Runtime shape preserves repeated exact ingredient alternatives");
        check(definition.get("group").getAsString().equals("fixture:runtime") && definition.get("category").getAsString().equals("building")
                && !definition.get("show_notification").getAsBoolean(), "Public shaped presentation fields retained");
        check(process.outputs().stream().anyMatch(output -> output.itemId().equals("minecraft:gold_ingot") && output.count() == 3)
                && definition.getAsJsonObject("result").has("components") && !process.conservationComplete(), "Runtime exact count/components preserve variant uncertainty");
        check(process.outputs().stream().anyMatch(output -> output.itemId().equals("minecraft:bucket") && output.byproduct()),
                "Runtime shaped projection retains returned containers");
        check(Recipe.CODEC.encodeStart(ops, shaped).error().isPresent() && shaped.getIngredients().equals(slots)
                && shaped.getResultItem(registries).equals(result), "Normalization never rewrites native recipe/pattern/result objects");
        check(BalanceDocument.GSON.fromJson(BalanceDocument.GSON.toJson(process), ProductionGraph.Process.class).equals(process),
                "Runtime shaped evidence survives saved process roundtrip");
        var custom = new ShapedRecipe("fixture:custom", CraftingBookCategory.MISC, pattern, new ItemStack(Items.GOLD_INGOT)) {
            @Override public ItemStack assemble(CraftingInput input, HolderLookup.Provider lookup) {
                throw new AssertionError("Generation must not execute custom assembly");
            }
        };
        List<String> limitations = new ArrayList<>();
        var customProcess = EffectiveProduction.collect(List.of(new RecipeHolder<>(id("fixture:custom"), custom)), registries,
                mod -> null, (family, reason) -> limitations.add(reason)).processes().getFirst();
        check(customProcess.inputs().equals(process.inputs()) && customProcess.effectiveDefinition().get("width").getAsInt() == 3,
                "Custom subclass retains inherited effective shape and nominal ingredients");
        check(!customProcess.acquisitionComplete() && !customProcess.conservationComplete()
                && customProcess.metadata().get("custom_behavior").contains("component transfer"), "Custom runtime assembly cannot become a known route or hard constraint");
        check(customProcess.metadata().get("serialization_limitation").contains("Cannot encode unpacked recipe")
                && !customProcess.effectiveDefinition().has("serializer_definition"), "Unrepresentable custom codec is explicit, without partial serializer JSON");
        check(limitations.size() == 1 && limitations.getFirst().contains("custom matching/assembly"), "Custom partial behavior is reported during generation");
        check(BalanceDocument.GSON.fromJson(BalanceDocument.GSON.toJson(customProcess), ProductionGraph.Process.class).equals(customProcess),
                "Saved subclass projection retains class, serializer, codec error and incompleteness");
        var encodable = new ShapedRecipe("fixture:custom_encodable", CraftingBookCategory.MISC,
                ShapedRecipePattern.of(Map.of('X', Ingredient.of(Items.STONE)), "X"), new ItemStack(Items.GOLD_INGOT)) { };
        var encodableProcess = EffectiveProduction.collect(List.of(new RecipeHolder<>(id("fixture:custom_encodable"), encodable)), registries,
                mod -> null, (family, reason) -> {}).processes().getFirst();
        check(encodableProcess.effectiveDefinition().getAsJsonObject("serializer_definition").has("pattern")
                && !encodableProcess.acquisitionComplete() && !encodableProcess.conservationComplete(), "Encodable custom parameters retained without certifying unobserved behavior");
        var broken = new ShapedRecipe("fixture:broken", CraftingBookCategory.MISC, pattern, new ItemStack(Items.GOLD_INGOT)) {
            @Override public ItemStack getResultItem(HolderLookup.Provider lookup) { throw new IllegalStateException("fixture thrown getter"); }
        };
        try {
            EffectiveProduction.collect(List.of(new RecipeHolder<>(id("fixture:broken"), broken)), registries, mod -> null, (family, reason) -> {});
            throw new AssertionError("Thrown native field failure bypassed");
        } catch (IllegalStateException expected) {
            check(expected.getMessage().contains("fixture:broken") && expected.getMessage().contains("fixture thrown getter"),
                    "Thrown normalization failures still abort with recipe provenance");
        }
    }
    private static void emptyNominalResults() {
        var pattern = ShapedRecipePattern.of(Map.of('X', Ingredient.of(Items.MILK_BUCKET)), "X");
        var emptyShaped = new ShapedRecipe("", CraftingBookCategory.MISC, pattern, ItemStack.EMPTY);
        var emptyShapeless = new ShapelessRecipe("", CraftingBookCategory.MISC, ItemStack.EMPTY,
                NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.MILK_BUCKET)));
        var dynamicShaped = new ShapedRecipe("", CraftingBookCategory.MISC, pattern, new ItemStack(Items.GOLD_INGOT)) {
            @Override public ItemStack getResultItem(HolderLookup.Provider lookup) { return ItemStack.EMPTY; }
            @Override public ItemStack assemble(CraftingInput input, HolderLookup.Provider lookup) {
                throw new AssertionError("Do not execute dynamic assembly");
            }
        };
        var dynamicShapeless = new ShapelessRecipe("", CraftingBookCategory.MISC, new ItemStack(Items.GOLD_INGOT),
                NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.MILK_BUCKET))) {
            @Override public ItemStack getResultItem(HolderLookup.Provider lookup) { return ItemStack.EMPTY; }
            @Override public ItemStack assemble(CraftingInput input, HolderLookup.Provider lookup) {
                throw new AssertionError("Do not execute dynamic assembly");
            }
        };
        List<String> limitations = new ArrayList<>();
        var graph = EffectiveProduction.collect(List.of(new RecipeHolder<>(id("fixture:empty_shaped"), emptyShaped),
                new RecipeHolder<>(id("fixture:empty_shapeless"), emptyShapeless),
                new RecipeHolder<>(id("fixture:dynamic_shaped"), dynamicShaped),
                new RecipeHolder<>(id("fixture:dynamic_shapeless"), dynamicShapeless), holder("fixture:healthy", Items.DIAMOND, 2)),
                registries, mod -> null, (family, reason) -> limitations.add(reason));
        check(graph.processes().size() == 1 && graph.processes().getFirst().id().equals("fixture:healthy"),
                "Empty nominal products do not abort generation or fabricate air/bucket/serializer-only products");
        check(limitations.size() == 4 && limitations.stream().allMatch(reason -> reason.contains("empty nominal result")),
                "Each empty result is explicitly diagnostic-only");
        check(graph.warnings().stream().filter(w -> w.startsWith("Unresolved empty nominal result")).count() == 4
                && graph.warnings().stream().anyMatch(w -> w.contains("fixture:empty_shapeless") && w.contains("native_shapeless_public_fields")
                        && w.contains("nominal_result_empty")), "Saved diagnostics retain effective input, runtime class, serializer and empty observation");
        check(graph.warnings().stream().anyMatch(w -> w.contains("fixture:dynamic_shaped") && w.contains("serializer_definition"))
                && graph.warnings().stream().anyMatch(w -> w.contains("fixture:dynamic_shapeless") && w.contains("serializer_definition")),
                "Representable custom parameters survive an empty nominal result without overriding its observation");
        check(emptyShaped.getResultItem(registries).isEmpty() && emptyShapeless.getResultItem(registries).isEmpty()
                && dynamicShaped.getIngredients().getFirst().test(new ItemStack(Items.MILK_BUCKET)), "Native empty results and ingredients remain unchanged");
        check(BalanceDocument.GSON.fromJson(BalanceDocument.GSON.toJson(graph), ProductionGraph.class).equals(graph),
                "Diagnostic-only effective evidence survives graph serialization");
        List<RecipeHolder<?>> many = new ArrayList<>();
        for (int i = 0; i < 40; i++) many.add(new RecipeHolder<>(id("fixture:empty_" + i), emptyShaped));
        var bounded = EffectiveProduction.collect(many, registries, mod -> null, (family, reason) -> {});
        check(bounded.warnings().stream().filter(w -> w.startsWith("Unresolved empty nominal result")).count() == 16
                && bounded.warnings().stream().anyMatch(w -> w.startsWith("24 additional empty nominal results")),
                "Diagnostic evidence remains bounded while recording omitted representative count");
        var broken = new ShapelessRecipe("", CraftingBookCategory.MISC, ItemStack.EMPTY,
                NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.STONE))) {
            @Override public ItemStack getResultItem(HolderLookup.Provider lookup) { throw new IllegalStateException("fixture getter failure"); }
        };
        try {
            EffectiveProduction.collect(List.of(new RecipeHolder<>(id("fixture:broken_shapeless"), broken)), registries, mod -> null, (family, reason) -> {});
            throw new AssertionError("Thrown shapeless getter failure bypassed");
        } catch (IllegalStateException expected) {
            check(expected.getMessage().contains("fixture:broken_shapeless") && expected.getMessage().contains("fixture getter failure"),
                    "Empty observation handling does not swallow thrown native failures");
        }
    }
    /** Reopen only this isolated fixture JVM's serializer registry; never part of mod/runtime code. */
    private static void registerChargeFixture() throws Exception {
        var registry = (MappedRegistry<RecipeSerializer<?>>) BuiltInRegistries.RECIPE_SERIALIZER;
        Field frozen = MappedRegistry.class.getDeclaredField("frozen");
        frozen.setAccessible(true);
        frozen.setBoolean(registry, false);
        try {
            chargeSerializer = Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, id("fixture:charging"), new RecipeSerializer<ChargeFixture>() {
                public com.mojang.serialization.MapCodec<ChargeFixture> codec() {
                    return com.mojang.serialization.codecs.RecordCodecBuilder.mapCodec(instance -> instance.group(
                            BuiltInRegistries.ITEM.byNameCodec().fieldOf("result").forGetter(ChargeFixture::serializedTarget),
                            Ingredient.CODEC.fieldOf("charge").forGetter((ChargeFixture value) -> value.charge),
                            com.mojang.serialization.Codec.INT.fieldOf("charges_per_item").forGetter((ChargeFixture value) -> value.charges)
                    ).apply(instance, ChargeFixture::new));
                }
                public net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, ChargeFixture> streamCodec() {
                    throw new AssertionError("Generation must not execute fixture network serialization");
                }
            });
        } finally { registry.freeze(); }
    }
    private static class ChargeFixture extends CustomRecipe {
        final Item target;
        final Ingredient charge;
        final int charges;
        ChargeFixture(Item target, Ingredient charge, int charges) {
            super(CraftingBookCategory.MISC); this.target = target; this.charge = charge; this.charges = charges;
        }
        public boolean matches(CraftingInput input, net.minecraft.world.level.Level level) { throw new AssertionError("Do not execute custom matching"); }
        public ItemStack assemble(CraftingInput input, HolderLookup.Provider lookup) { throw new AssertionError("Do not execute custom assembly"); }
        public boolean canCraftInDimensions(int x, int y) { return true; }
        public RecipeSerializer<?> getSerializer() { return chargeSerializer; }
        Item serializedTarget() { return target; }
    }
    private static void customCrafting() {
        var recipe = new ChargeFixture(Items.GOLD_INGOT, Ingredient.of(Items.MILK_BUCKET), 7);
        var ops = net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE, registries);
        JsonObject encoded = Recipe.CODEC.encodeStart(ops, recipe).getOrThrow().getAsJsonObject();
        check(encoded.get("result").isJsonPrimitive() && encoded.get("result").getAsString().equals("minecraft:gold_ingot"),
                "Native custom serializer reproduces bare-item result rather than stack encoding");
        check(recipe.getResultItem(registries).isEmpty() && recipe.getIngredients().isEmpty(),
                "CustomRecipe default public fields do not promise a product or counted charge inputs");
        List<String> limitations = new ArrayList<>();
        var graph = EffectiveProduction.collect(List.of(new RecipeHolder<>(id("fixture:charging"), recipe), holder("fixture:ordinary", Items.DIAMOND, 3)),
                registries, mod -> null, (family, reason) -> limitations.add(reason));
        check(graph.processes().size() == 1 && graph.processes().getFirst().id().equals("fixture:ordinary")
                && limitations.size() == 1 && limitations.getFirst().contains("empty nominal result"),
                "Unresolved custom charging cannot become a free gold/bucket producer or abort healthy recipes");
        String warning = graph.warnings().stream().filter(w -> w.contains("fixture:charging")).findFirst().orElseThrow();
        JsonObject definition = JsonParser.parseString(warning.substring(warning.indexOf('{'))).getAsJsonObject();
        check(definition.get("nominal_result_empty").getAsBoolean() && definition.get("custom_behavior_unresolved").getAsBoolean()
                && definition.getAsJsonObject("serializer_definition").equals(encoded),
                "Effective custom serializer preserves exact bare item, charge predicate and charge count as unresolved evidence");
        check(BalanceDocument.GSON.fromJson(BalanceDocument.GSON.toJson(graph), ProductionGraph.class).equals(graph),
                "Custom charging evidence survives serialization for later semantic adapters");
        var nominal = new ChargeFixture(Items.GOLD_INGOT, Ingredient.of(Items.MILK_BUCKET), 7) {
            @Override public ItemStack getResultItem(HolderLookup.Provider lookup) {
                ItemStack stack = new ItemStack(Items.DIAMOND, 4);
                stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Nominal observation"));
                return stack;
            }
            @Override public NonNullList<Ingredient> getIngredients() { return NonNullList.of(Ingredient.EMPTY, charge); }
        };
        var process = EffectiveProduction.collect(List.of(new RecipeHolder<>(id("fixture:custom_nominal"), nominal)), registries,
                mod -> null, (family, reason) -> {}).processes().getFirst();
        check(process.outputs().size() == 1 && process.outputs().getFirst().itemId().equals("minecraft:diamond") && process.outputs().getFirst().count() == 4
                && process.effectiveDefinition().getAsJsonObject("result").has("components"),
                "Positive public nominal count/components survive without substituting serializer identity or guessing custom containers");
        check(!process.acquisitionComplete() && !process.conservationComplete()
                && process.effectiveDefinition().getAsJsonObject("serializer_definition").get("result").getAsString().equals("minecraft:gold_ingot"),
                "Custom positive nominal evidence stays advisory with complete separate serializer parameters");
        var broken = new ChargeFixture(Items.GOLD_INGOT, Ingredient.of(Items.STONE), 1) {
            @Override public ItemStack getResultItem(HolderLookup.Provider lookup) { throw new IllegalStateException("fixture custom getter failed"); }
        };
        try {
            EffectiveProduction.collect(List.of(new RecipeHolder<>(id("fixture:broken_custom"), broken)), registries, mod -> null, (family, reason) -> {});
            throw new AssertionError("Thrown custom getter failure bypassed");
        } catch (IllegalStateException expected) {
            check(expected.getMessage().contains("fixture:broken_custom") && expected.getMessage().contains("fixture custom getter failed"),
                    "Custom crafting projection still rejects thrown failures with recipe provenance");
        }
    }
    private static void unsupportedCustomEncoding() {
        var recipe = new ChargeFixture(Items.GOLD_INGOT, Ingredient.of(Items.STONE), 5) {
            @Override Item serializedTarget() { throw new org.apache.commons.lang3.NotImplementedException("fixture encoding not implemented"); }
            @Override public NonNullList<Ingredient> getIngredients() { return NonNullList.of(Ingredient.EMPTY, charge, charge); }
            @Override public ItemStack getResultItem(HolderLookup.Provider lookup) { return new ItemStack(Items.DIAMOND, 3); }
        };
        var ops = net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE, registries);
        try {
            Recipe.CODEC.encodeStart(ops, recipe);
            throw new AssertionError("Fixture did not reproduce unsupported serializer operation");
        } catch (UnsupportedOperationException expected) {
            check(expected instanceof org.apache.commons.lang3.NotImplementedException, "Native custom encoder reproduces the actual unsupported-operation subtype");
        }
        List<String> limitations = new ArrayList<>();
        var graph = EffectiveProduction.collect(List.of(new RecipeHolder<>(id("fixture:unsupported_encoder"), recipe), holder("fixture:healthy_encoder", Items.IRON_INGOT, 2)),
                registries, mod -> null, (family, reason) -> limitations.add(reason));
        var process = graph.processes().stream().filter(p -> p.id().equals("fixture:unsupported_encoder")).findFirst().orElseThrow();
        check(graph.processes().size() == 2 && process.inputs().size() == 2 && process.outputs().getFirst().count() == 3
                && process.outputs().getFirst().itemId().equals("minecraft:diamond"), "Unsupported optional encoding retains public nominal counts/inputs and healthy processes");
        check(!process.acquisitionComplete() && !process.conservationComplete() && limitations.size() == 1,
                "Unsupported custom encoding cannot certify acquisition/conservation");
        check(process.metadata().get("serialization_limitation_kind").equals("unsupported_serializer_operation")
                && process.metadata().get("serialization_limitation").contains("NotImplementedException")
                && !process.effectiveDefinition().has("serializer_definition"), "Unsupported serializer provenance is explicit without partial invented definition");
        check(BalanceDocument.GSON.fromJson(BalanceDocument.GSON.toJson(process), ProductionGraph.Process.class).equals(process),
                "Unsupported-operation evidence survives saved process serialization");
        var empty = new ChargeFixture(Items.GOLD_INGOT, Ingredient.of(Items.STONE), 5) {
            @Override Item serializedTarget() { throw new UnsupportedOperationException("fixture base unsupported operation"); }
        };
        var emptyGraph = EffectiveProduction.collect(List.of(new RecipeHolder<>(id("fixture:empty_unsupported_encoder"), empty)), registries,
                mod -> null, (family, reason) -> {});
        check(emptyGraph.processes().isEmpty() && emptyGraph.warnings().stream().anyMatch(w -> w.contains("unsupported_serializer_operation")
                && w.contains("nominal_result_empty")), "Base unsupported operation with empty nominal output stays diagnostic-only");
        var failures = List.<Throwable>of(new IllegalStateException("fixture unexpected encoder failure"), new NoClassDefFoundError("fixture broken encoder linkage"));
        for (Throwable failure : failures) {
            var broken = new ChargeFixture(Items.GOLD_INGOT, Ingredient.of(Items.STONE), 1) {
                @Override Item serializedTarget() { if (failure instanceof RuntimeException runtime) throw runtime; throw (LinkageError) failure; }
            };
            try {
                EffectiveProduction.collect(List.of(new RecipeHolder<>(id("fixture:broken_encoder"), broken)), registries, mod -> null, (family, reason) -> {});
                throw new AssertionError("Unexpected encoder failure bypassed");
            } catch (IllegalStateException expected) {
                check(expected.getMessage().contains("fixture:broken_encoder") && expected.getMessage().contains(failure.getMessage()),
                        "Unexpected encoder runtime/linkage failures remain strict with recipe provenance");
            }
        }
        var brokenGetter = new ChargeFixture(Items.GOLD_INGOT, Ingredient.of(Items.STONE), 1) {
            @Override public ItemStack getResultItem(HolderLookup.Provider lookup) { throw new UnsupportedOperationException("fixture required getter unsupported"); }
        };
        try {
            EffectiveProduction.collect(List.of(new RecipeHolder<>(id("fixture:unsupported_getter"), brokenGetter)), registries, mod -> null, (family, reason) -> {});
            throw new AssertionError("Required getter unsupported failure bypassed");
        } catch (IllegalStateException expected) {
            check(expected.getMessage().contains("fixture:unsupported_getter") && expected.getMessage().contains("fixture required getter unsupported"),
                    "Unsupported-operation handling is confined to optional custom serializer encoding");
        }
    }
    public static ProductionGraph currentEffectiveFixture(int revision) {
        var lookup = VanillaRegistries.createLookup();
        return EffectiveProduction.collect(List.of(holder("fixture:script_modified", revision == 1 ? Items.GOLD_INGOT : Items.DIAMOND, revision)),
                lookup, mod -> null, (family, reason) -> { throw new AssertionError(reason); });
    }
    private static ProductionGraph.Process normalized(String family, String adapter, String json) {
        return EffectiveProduction.normalize("fixture:process", family, adapter, JsonParser.parseString(json).getAsJsonObject(), registries, null);
    }
    private static void normalizedFacts() {
        var alternatives = normalized("minecraft:crafting", null, """
            {"ingredients":[[{"item":"minecraft:stone"},{"item":"minecraft:dirt"}]],"result":{"id":"minecraft:gold_ingot","count":2}}
            """);
        check(alternatives.inputs().getFirst().alternatives().size() == 2, "exact alternatives preserved");
        var tag = net.minecraft.tags.TagKey.create(Registries.ITEM, id("fixture:effective"));
        BuiltInRegistries.ITEM.bindTags(Map.of(tag, List.of(BuiltInRegistries.ITEM.getHolderOrThrow(Items.GOLD_INGOT.builtInRegistryHolder().key()),
                BuiltInRegistries.ITEM.getHolderOrThrow(Items.IRON_INGOT.builtInRegistryHolder().key()))));
        var tagged = normalized("minecraft:crafting", null, """
            {"ingredients":[{"tag":"fixture:effective"}],"result":{"id":"minecraft:sugar"}}
            """);
        check(tagged.acquisitionComplete() && tagged.inputs().getFirst().alternatives().size() == 2, "effective tag membership becomes exact acquisition alternatives");
        BuiltInRegistries.ITEM.bindTags(Map.of(tag, List.of(Items.DIAMOND.builtInRegistryHolder())));
        var retagged = normalized("minecraft:crafting", null, tagged.effectiveDefinition().toString());
        check(retagged.inputs().getFirst().alternatives().equals(List.of("minecraft:diamond")), "explicit fresh normalization observes changed effective tags");
        BuiltInRegistries.ITEM.bindTags(Map.of());
        var unresolved = normalized("minecraft:crafting", null, """
            {"ingredients":[{"tag":"fixture:unresolved"}],"result":{"id":"minecraft:gold_ingot"}}
            """);
        check(!unresolved.acquisitionComplete() && unresolved.inputs().getFirst().alternatives().getFirst().startsWith("predicate:"), "unresolved effective tag stays explicit");
        check(ProceduralValuationIndex.productionModels(unresolved, RecipeType.CRAFTING).isEmpty(), "symbolic predicate never parses as an item ID or seeds acquisition");
        var milk = normalized("minecraft:crafting", null, """
            {"ingredients":[{"item":"minecraft:milk_bucket"}],"result":{"id":"minecraft:sugar"}}
            """);
        check(milk.outputs().stream().anyMatch(o -> o.itemId().equals("minecraft:bucket") && o.byproduct()), "returned container retained");
        var conditional = normalized("minecraft:crafting", null, """
            {"ingredients":[[{"item":"minecraft:milk_bucket"},{"item":"minecraft:stone"}]],"result":{"id":"minecraft:sugar"}}
            """);
        check(!conditional.conservationComplete() && conditional.outputs().size() == 1, "alternative-dependent returns do not fabricate simultaneous outputs");
        var mi = normalized("modern_industrialization:macerator", "mi_machine", """
            {"eu":8,"duration":120,"item_inputs":[{"item":"minecraft:diamond","amount":1,"probability":0},{"item":"minecraft:gold_ingot","amount":3}],
             "item_outputs":[{"item":"minecraft:iron_ingot","amount":6},{"item":"minecraft:redstone","amount":2,"probability":0.25}]}
            """);
        check(!mi.inputs().getFirst().consumed() && mi.inputs().get(1).count() == 3, "reusable catalyst separate from exact consumed quantity");
        check(mi.outputs().size() == 2 && mi.outputs().getLast().probability() == .25 && mi.outputs().getLast().expectedCount() == .5, "multiple/chance outputs exact");
        check(mi.duration() == 120 && mi.metadata().get("energy").equals("modern_industrialization:EU/tick=8.0"), "native duration and distinct EU system retained");
        check(!mi.conservationComplete(), "unvalued native energy prevents hard material conservation");
        var source = normalized("modern_industrialization:producer", "mi_machine", """
            {"eu":16,"duration":200,"item_outputs":[{"item":"minecraft:iron_ingot","amount":2}]}
            """);
        check(source.sourceProducer() && !source.acquisitionComplete(), "zero consumed-input production preserved without inventing setup access");
        var fluid = normalized("create:mixing", "create_processing", """
            {"ingredients":[{"fluid":"minecraft:water","amount":250},{"item":"minecraft:stone"}],
             "results":[{"item":"minecraft:gold_ingot","count":3},{"id":"minecraft:water","amount":500}],"processing_time":40}
            """);
        check(fluid.resources().size() == 2 && fluid.resources().getFirst().count() == 250 && fluid.resources().getLast().count() == 500, "native fluid quantities and output retained");
        check(fluid.resources().getFirst().unit().equals("neoforge:mB") && !fluid.acquisitionComplete(), "fluid route stays unresolved until its resource inputs are accessible");
        var resourceOnly = normalized("create:mixing", "create_processing", """
            {"ingredients":[{"item":"minecraft:stone"}],"results":[{"id":"minecraft:water","amount":500}]}
            """);
        var resourceOverride = new BalanceOverrides(List.of(new BalanceOverrides.FactOverride("fixture:resource_fact", BalanceOverrides.SubjectKind.RECIPE,
                resourceOnly.id(), 1, Map.of("confidence", .95, "processing_time", 40.0), "fixture", 1)), Map.of());
        var resourceEconomy = EconomyGenerator.generate(new PackEvidence(Map.of("minecraft:stone", new ResourceEvidence("minecraft:stone", ProgressionBand.ENTRY, Availability.FINITE, Automation.NONE, true, true, 1, .9, List.of(), List.of())), List.of(), List.of(), Map.of(), List.of(), List.of(), Map.of()),
                new ProductionGraph(List.of(resourceOnly), List.of()), Map.of(), BalanceSettings.defaults(), resourceOverride);
        check(resourceEconomy.processes().size() == 1 && resourceEconomy.processes().getFirst().resources().getFirst().count() == 500,
                "factual overrides retain native resource-only processes");
        check(resourceEconomy.processes().getFirst().duration() == 40 && resourceEconomy.processes().getFirst().confidence() == .95,
                "factual processing time and confidence precede all consumers");
        var component = normalized("create:crushing", "create_processing", """
            {"ingredients":[{"item":"minecraft:stone"}],"results":[{"item":"minecraft:diamond","components":{"minecraft:custom_data":{"fixture":1}}}]}
            """);
        check(component.effectiveDefinition().getAsJsonArray("results").get(0).getAsJsonObject().has("components"), "components never erased");
        check(component.acquisitionComplete() && !component.conservationComplete(), "component result proves item existence while variant conservation remains unresolved");
        var predicate = normalized("create:crushing", "create_processing", """
            {"ingredients":[{"item":"minecraft:stone","components":{"minecraft:custom_data":{"fixture":1}}}],"results":[{"item":"minecraft:diamond"}]}
            """);
        check(!predicate.acquisitionComplete() && predicate.effectiveDefinition().getAsJsonArray("ingredients").get(0).getAsJsonObject().has("components"), "unproven input component predicate preserved without ordinary-item reachability");
        var miner = normalized("occultism:miner", "weighted_extraction", """
            {"ingredient":{"item":"minecraft:diamond_pickaxe"},"result":{"stack":{"id":"minecraft:gold_ingot","count":2},"weight":20}}
            """);
        check(miner.metadata().get("function").equals("automated_resource_extraction") && miner.metadata().get("player_activity").equals("passive_while_loaded"), "authoritative extraction/passive function retained without Essence rules");
        check(!miner.inputs().getFirst().consumed() && miner.metadata().containsKey("durability_cost"), "durability-consuming tool is not consumed construction");
        check(miner.metadata().get("selection_weight").equals("20") && miner.duration() == null && !miner.acquisitionComplete(), "weight is not invented probability or throughput");
        check(BalanceDocument.GSON.fromJson(BalanceDocument.GSON.toJson(fluid), ProductionGraph.Process.class).equals(fluid), "resource/component provenance survives saved process codec");
        var constrained = new ProductionGraph(List.of(mi), List.of());
        var conserved = EconomyConservationSolver.solveWholeUnits(constrained, Map.of("minecraft:iron_ingot", DissolutionYield.of(10)));
        check(conserved.yields().get("minecraft:iron_ingot").amount() == 10 && conserved.invariants().isEmpty(), "incomplete operating evidence cannot zero supported machine outputs");
        check(conserved.warnings().stream().anyMatch(w -> w.contains("incomplete")), "excluded constraints diagnosed");
        var chemical = normalized("mekanism:crystallizing", "mekanism_chemical", """
            {"input":{"ingredient":{"chemical":"fixture:chemical"},"amount":200},"output":{"id":"minecraft:gold_ingot","count":2}}
            """);
        check(chemical.resources().getFirst().form().equals("chemical") && chemical.resources().getFirst().unit().equals("mekanism:chemical_amount")
                && chemical.resources().getFirst().count() == 200, "chemical quantity and system preserved without fluid/energy conflation");
        check(!chemical.acquisitionComplete(), "chemical inputs not silently free");
        var zero = new ProductionGraph.Process("fixture:resource_source", "fixture:extraction", List.of(),
                List.of(new ProductionGraph.Output("minecraft:gold_ingot", 1, 1, false)), 0, 0, "fixture:source", .9,
                Map.of("production_constraint", BoundedProductionPolicy.NATIVE_RESOURCE_SOURCE, "source_provenance", "fixture observed source mechanic",
                        "renewability", "renewable", "operation", "automated", "acquisition_complete", "true", "conservation_complete", "true"));
        check(BoundedProductionPolicy.isBoundedSource(zero), "native zero-material resource source distinguished from arbitrary free conversion");
        check(zero.duration() == null, "unknown production time is not zero throughput");
        try {
            BoundedProductionPolicy.isBoundedSource(new ProductionGraph.Process(zero.id(), zero.family(), zero.inputs(), zero.outputs(), 0, 0, zero.provider(), .9,
                    Map.of("production_constraint", BoundedProductionPolicy.NATIVE_RESOURCE_SOURCE)));
            throw new AssertionError("unsupported resource source claim accepted");
        } catch (IllegalArgumentException expected) { checks++; }
    }

    /** Exposed to the full-profile integration suite, which attaches these real solver results to its complete profile. */
    public record MachineCase(PackEvidence evidence, EconomyProfile economy) { }
    public static MachineCase machineCase() throws Exception {
        return machineAcquisition();
    }
    private static MachineCase machineAcquisition() throws Exception {
        // Vanilla item IDs provide stable codec identity; only fixture source facts seed the opaque barrier result.
        ValuationGenerationInputs.configure(new BalanceOverrides(List.of(
                new BalanceOverrides.FactOverride("fixture:source", BalanceOverrides.SubjectKind.ITEM, "minecraft:gold_ingot", 1000,
                        Map.of("attainable", true, "resource_value", 80.0), "fixture", 1),
                new BalanceOverrides.FactOverride("fixture:setup", BalanceOverrides.SubjectKind.ITEM, "minecraft:diamond", 1000,
                        Map.of("attainable", true, "resource_value", 5000.0), "fixture", 2)), Map.of()));
        var process = new ProductionGraph.Process("fixture:machine_only", "fixture:machine", List.of(
                new ProductionGraph.Input(List.of("minecraft:gold_ingot"), 2, true),
                new ProductionGraph.Input(List.of("minecraft:diamond"), 1, false)),
                List.of(new ProductionGraph.Output("minecraft:barrier", 4, 1, false)), 80, 0, "fixture:authoritative", .95,
                Map.of("acquisition_complete", "true", "conservation_complete", "true", "duration_known", "true",
                        "operation", "automated", "renewability", "finite", "energy", "observed_no_energy", "setup", "diamond infrastructure"));
        var models = ProceduralValuationIndex.productionModels(process, null);
        Constructor<?> constructor = ProceduralValuationIndex.class.getDeclaredConstructors()[0]; constructor.setAccessible(true);
        var index = (ProceduralValuationIndex) constructor.newInstance(Map.of(Items.BARRIER, models), Map.of(), Map.of(), Map.of(), Map.of(),
                Map.of(), Map.of(), Map.of(), Map.of(), null, null, null, null);
        var absent = (ProceduralValuationIndex) constructor.newInstance(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), null, null, null, null);
        Class<?> contextType = Class.forName(ProceduralValuationEngine.class.getName() + "$EvaluationContext");
        Constructor<?> contextConstructor = contextType.getDeclaredConstructor(ProceduralValuationIndex.class); contextConstructor.setAccessible(true);
        Method evaluate = ProceduralValuationEngine.class.getDeclaredMethod("evaluateItem", ProceduralValuationIndex.class, Item.class, contextType); evaluate.setAccessible(true);
        var before = (ProceduralValuationResult) evaluate.invoke(null, absent, Items.BARRIER, contextConstructor.newInstance(absent));
        var after = (ProceduralValuationResult) evaluate.invoke(null, index, Items.BARRIER, contextConstructor.newInstance(index));
        check(!before.modeledAcquisition() && after.modeledAcquisition(), "machine-only route establishes reachability in actual valuation solver");
        check(after.recipeChoice().isPresent() && after.totalValue() < 500, "operating cost affects upstream valuation; setup not charged per output");
        check(models.getFirst().ingredients().get(1).count() == 1 && !models.getFirst().ingredients().get(1).consumed(), "shared acquisition projection keeps infrastructure role");
        Map<String, ResourceEvidence> resources = new TreeMap<>();
        resources.put("minecraft:gold_ingot", new ResourceEvidence("minecraft:gold_ingot", ProgressionBand.ENTRY, Availability.FINITE, Automation.NONE, true, true, 80, .95, List.of(), List.of()));
        resources.put("minecraft:barrier", new ResourceEvidence("minecraft:barrier", ProgressionBand.ENTRY, Availability.FINITE, Automation.NONE, after.modeledAcquisition(), true, after.totalValue(), after.confidence(), List.of(), List.of()));
        var evidence = new PackEvidence(resources, List.of(), List.of(), Map.of(), List.of(), List.of(), Map.of("recipes", 1L));
        var economy = EconomyGenerator.generate(evidence, new ProductionGraph(List.of(process), List.of()),
                Map.of("minecraft:gold_ingot", Map.of(EssenceTypes.UTILITY.id().toString(), 1L), "minecraft:barrier", Map.of(EssenceTypes.UTILITY.id().toString(), 1L)),
                BalanceSettings.defaults(), BalanceOverrides.empty());
        check(economy.resources().get("minecraft:barrier").dissolutionYield().amount() > 0, "reachable machine output receives generated whole payout");
        check(economy.invariants().size() == 1 && economy.invariants().getFirst().passed(), "same machine route participates in conservation");
        check(economy.resources().get("minecraft:barrier").dissolutionYield().amount() * 4
                <= economy.resources().get("minecraft:gold_ingot").dissolutionYield().amount() * 2, "construction grants no recurring conservation credit");
        EconomyGenerator.validateWhole(economy);
        var decoded = BalanceDocument.GSON.fromJson(BalanceDocument.GSON.toJson(economy), EconomyProfile.class);
        check(decoded.equals(economy), "normalized machine evidence survives generated economy serialization");
        ValuationGenerationInputs.clear();
        return new MachineCase(evidence, economy);
    }
    private static ResourceLocation id(String value) { return ResourceLocation.parse(value); }
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
