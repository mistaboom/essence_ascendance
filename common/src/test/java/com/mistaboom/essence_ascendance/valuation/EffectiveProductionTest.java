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
    private static RecipeSerializer<TagCookingFixture> tagCookingSerializer;
    public static void main(String[] args) throws Exception {
        Thread.currentThread().setUncaughtExceptionHandler((thread, failure) -> failure.printStackTrace(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.err))));
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); registerChargeFixture(); EssenceTypes.init();
        registries = VanillaRegistries.createLookup();
        ValuationGenerationInputs.configure(BalanceOverrides.empty());
        if (args.length == 2 && args[0].equals("--benchmark")) { benchmark(java.nio.file.Path.of(args[1])); return; }
        effectiveInventory(); unpackedShapedRecipes(); emptyNominalResults(); customCrafting(); unsupportedCustomEncoding(); unresolvedRuntimeIngredients(); customCooking(); recipeFailureIsolation(); normalizedFacts(); machineAcquisition(); kubeCraftingSemantics();
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
    /** Mirrors only the audited public getter protocol; no optional KubeJS dependency or script execution. */
    public static final class PublishedCraftingHooks {
        private final List<?> actions;
        private final String callback;
        private int reads;
        public PublishedCraftingHooks(List<?> actions, String callback) { this.actions = actions; this.callback = callback; }
        public List<?> kjs$getIngredientActions() { reads++; return actions; }
        public String kjs$getModifyResult() { reads++; return callback; }
    }
    private static void kubeCraftingSemantics() throws Exception {
        String version = "2101.7.2-build.374";
        for (String form : List.of("Shaped", "Shapeless")) {
            String runtime = "dev.latvian.mods.kubejs.recipe.special." + form + "KubeJSRecipe";
            var hooks = new PublishedCraftingHooks(List.of(), "");
            var observed = KubeCraftingSemantics.inspect(runtime, version, hooks);
            check(observed.audited() && observed.nativeBehavior() && hooks.reads == 2,
                    "Exact audited " + form + " class reads published empty hooks without executing behavior");
            JsonObject root = JsonParser.parseString("{\"type\":\"kubejs:" + form.toLowerCase(Locale.ROOT)
                    + "\",\"projection\":\"native_" + form.toLowerCase(Locale.ROOT)
                    + "_public_fields\",\"runtime_class\":\"" + runtime
                    + "\",\"ingredients\":[{\"item\":\"minecraft:paper\"},{\"item\":\"minecraft:paper\"},{\"item\":\"minecraft:paper\"},{\"item\":\"minecraft:leather\"}],\"result\":{\"id\":\"minecraft:book\",\"count\":1}}").getAsJsonObject();
            if (form.equals("Shaped")) { root.addProperty("width", 2); root.addProperty("height", 2); }
            KubeCraftingSemantics.annotate(root, observed, version);
            var process = EffectiveProduction.normalize("fixture:hookless_" + form.toLowerCase(Locale.ROOT), "minecraft:crafting", null, root, registries, null);
            check(process.acquisitionComplete() && process.conservationComplete() && process.inputs().size() == 4
                    && process.outputs().getFirst().itemId().equals("minecraft:book") && process.outputs().getFirst().count() == 1,
                    "Hookless " + form + " retains exact consumed paper/leather inputs and static book output");
            check(process.metadata().get("behavior_adapter").equals("kubejs:published_crafting_hooks")
                    && process.metadata().get("behavior_api_version").equals(version), "Audited native behavior is saved as evidence");
            var copied = BalanceDocument.GSON.fromJson(BalanceDocument.GSON.toJson(process), ProductionGraph.Process.class);
            check(copied.equals(process), "Hookless behavior evidence survives direct saved graph decode");
            for (var configured : List.of(new PublishedCraftingHooks(List.of("opaque remainder action"), ""),
                    new PublishedCraftingHooks(List.of(), "result_callback"))) {
                var custom = KubeCraftingSemantics.inspect(runtime, version, configured);
                var definition = root.deepCopy(); KubeCraftingSemantics.annotate(definition, custom, version);
                var unresolved = EffectiveProduction.normalize("fixture:callback_" + form.toLowerCase(Locale.ROOT), "minecraft:crafting", null, definition, registries, null);
                check(custom.audited() && !custom.nativeBehavior() && !unresolved.acquisitionComplete()
                        && !unresolved.conservationComplete(), "Configured " + form + " result/remainder hooks stay advisory");
            }
            var unaudited = new PublishedCraftingHooks(List.of(), "");
            check(!KubeCraftingSemantics.inspect(runtime, "2101.7.3-build.999", unaudited).nativeBehavior() && unaudited.reads == 0,
                    "Unknown optional API version does not inspect or certify crafting behavior");
            check(!KubeCraftingSemantics.inspect(runtime + "Subclass", version, unaudited).nativeBehavior() && unaudited.reads == 0,
                    "Arbitrary inherited subclasses do not bypass custom behavior uncertainty");
        }
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
        isolatedFailure("fixture:broken", broken, "fixture thrown getter");
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
        isolatedFailure("fixture:broken_shapeless", broken, "fixture getter failure");
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
            tagCookingSerializer = Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, id("fixture:tag_blasting"), new RecipeSerializer<TagCookingFixture>() {
                public com.mojang.serialization.MapCodec<TagCookingFixture> codec() {
                    return com.mojang.serialization.codecs.RecordCodecBuilder.mapCodec(instance -> instance.group(
                            Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter((TagCookingFixture value) -> value.inputIngredient),
                            Ingredient.CODEC_NONEMPTY.fieldOf("result").forGetter((TagCookingFixture value) -> value.resultIngredient),
                            com.mojang.serialization.Codec.INT.fieldOf("cookingtime").forGetter(TagCookingFixture::getCookingTime)
                    ).apply(instance, TagCookingFixture::new));
                }
                public net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, TagCookingFixture> streamCodec() {
                    throw new AssertionError("Generation must not execute fixture cooking network serialization");
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
        isolatedFailure("fixture:broken_custom", broken, "fixture custom getter failed");
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
            isolatedFailure("fixture:broken_encoder", broken, failure.getMessage());
        }
        var brokenGetter = new ChargeFixture(Items.GOLD_INGOT, Ingredient.of(Items.STONE), 1) {
            @Override public ItemStack getResultItem(HolderLookup.Provider lookup) { throw new UnsupportedOperationException("fixture required getter unsupported"); }
        };
        isolatedFailure("fixture:unsupported_getter", brokenGetter, "fixture required getter unsupported");
    }

    private static void unresolvedRuntimeIngredients() throws Exception {
        // A runtime ingredient with no encodable value mirrors optional loader ingredients whose
        // public predicate exists but whose codec type is unavailable. Never invoke its matching/items.
        Class<?> valueType = Class.forName(Ingredient.class.getName() + "$Value");
        Object values = Array.newInstance(valueType, 1);
        Constructor<Ingredient> constructor = Ingredient.class.getDeclaredConstructor(values.getClass());
        constructor.setAccessible(true);
        Ingredient opaque = constructor.newInstance(values);
        var recipe = new ChargeFixture(Items.GOLD_INGOT, Ingredient.of(Items.STONE), 5) {
            @Override public NonNullList<Ingredient> getIngredients() { return NonNullList.of(Ingredient.EMPTY, charge, opaque); }
            @Override public ItemStack getResultItem(HolderLookup.Provider lookup) { return new ItemStack(Items.DIAMOND, 3); }
        };
        List<String> limitations = new ArrayList<>();
        ProductionGraph graph;
        try (var operation = BalancePerformance.begin("fixture_unresolved_runtime_ingredient", "isolated public ingredient projection")) {
            graph = EffectiveProduction.collect(List.of(new RecipeHolder<>(id("fixture:runtime_ingredient"), recipe),
                    holder("fixture:healthy_after_ingredient", Items.IRON_INGOT, 2)), registries, mod -> null,
                    (family, reason) -> limitations.add(reason));
            operation.complete("advisory_ingredient_projection");
        }
        var process = graph.processes().stream().filter(p -> p.id().equals("fixture:runtime_ingredient")).findFirst().orElseThrow();
        JsonObject marker = process.effectiveDefinition().getAsJsonArray("ingredients").get(1).getAsJsonObject();
        check(graph.processes().size() == 2 && process.inputs().size() == 2 && process.outputs().getFirst().count() == 3,
                "Unencodable runtime ingredient retains its slot, public nominal result and healthy subsequent recipes");
        check(marker.get("unresolved_ingredient").getAsBoolean() && marker.has("serialization_failure")
                && process.inputs().get(1).alternatives().getFirst().startsWith("predicate:"),
                "Unavailable ingredient codec is saved as an unresolved predicate without fabricated item alternatives");
        check(!process.acquisitionComplete() && !process.conservationComplete()
                && ProceduralValuationIndex.productionModels(process, RecipeType.CRAFTING).isEmpty(),
                "Unresolved ingredient projection cannot seed valuation/acquisition or certify conservation");
        check(process.effectiveDefinition().getAsJsonObject("serializer_definition").get("charges_per_item").getAsInt() == 5
                && limitations.size() == 1,
                "Independent recipe serializer parameters remain available as advisory evidence");
        check(BalancePerformance.lastSnapshot().counts().get("production_unrepresentable_ingredients") == 1
                && !BalancePerformance.lastSnapshot().counts().containsKey("production_recipe_normalization_failures"),
                "Diagnostics distinguish an unavailable ingredient codec from a completely failed recipe");
        check(BalanceDocument.GSON.fromJson(BalanceDocument.GSON.toJson(graph), ProductionGraph.class).equals(graph),
                "Unresolved runtime predicate diagnostics survive saved production graph serialization");
        var ordinary = new ShapelessRecipe("", CraftingBookCategory.MISC, new ItemStack(Items.GOLD_INGOT, 2),
                NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.STONE), opaque));
        var ordinaryProcess = EffectiveProduction.collect(List.of(new RecipeHolder<>(id("fixture:ordinary_runtime_ingredient"), ordinary)),
                registries, mod -> null, (family, reason) -> {}).processes().getFirst();
        check(!ordinaryProcess.acquisitionComplete() && !ordinaryProcess.conservationComplete()
                && ordinaryProcess.inputs().size() == 2 && !ordinaryProcess.effectiveDefinition().has("custom_behavior_unresolved"),
                "Unresolved ingredient semantics stay incomplete even on an otherwise ordinary vanilla recipe object");
    }

    /** Mirrors tag cooking's public boundary, including a serializer whose result is an Ingredient. */
    private static class TagCookingFixture extends BlastingRecipe {
        final Ingredient inputIngredient, resultIngredient;
        TagCookingFixture(Ingredient input, Ingredient result, int ticks) {
            super("fixture:cooking", CookingBookCategory.MISC, input, ItemStack.EMPTY, .7F, ticks);
            inputIngredient = input; resultIngredient = result;
        }
        @Override public ItemStack getResultItem(HolderLookup.Provider lookup) { return resultIngredient.getItems()[0]; }
        @Override public ItemStack assemble(SingleRecipeInput input, HolderLookup.Provider lookup) {
            throw new AssertionError("Generation must not execute custom cooking assembly");
        }
        @Override public RecipeSerializer<?> getSerializer() { return tagCookingSerializer; }
    }

    private static void customCooking() throws Exception {
        var tag = net.minecraft.tags.TagKey.create(Registries.ITEM, id("fixture:cooking_result"));
        BuiltInRegistries.ITEM.bindTags(Map.of(tag, List.of(Items.GOLD_INGOT.builtInRegistryHolder(), Items.IRON_INGOT.builtInRegistryHolder())));
        var tagged = new TagCookingFixture(Ingredient.of(Items.STONE), Ingredient.of(tag), 123);
        var encoded = Recipe.CODEC.encodeStart(net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE, registries), tagged)
                .getOrThrow().getAsJsonObject();
        check(encoded.getAsJsonObject("result").has("tag") && !encoded.getAsJsonObject("result").has("id"),
                "Tag cooking fixture reproduces a serializer result predicate without a stack identity");
        List<String> limitations = new ArrayList<>();
        var ordinary = new SmeltingRecipe("", CookingBookCategory.MISC, Ingredient.of(Items.STONE), new ItemStack(Items.DIAMOND, 2), .1F, 200);
        var graph = EffectiveProduction.collect(List.of(new RecipeHolder<>(id("fixture:tagged_cooking"), tagged),
                new RecipeHolder<>(id("fixture:ordinary_cooking"), ordinary)), registries, mod -> null, (family, reason) -> limitations.add(reason));
        var process = graph.processes().stream().filter(p -> p.id().equals("fixture:tagged_cooking")).findFirst().orElseThrow();
        check(process.outputs().size() == 1 && process.outputs().getFirst().itemId().equals("minecraft:gold_ingot")
                && process.effectiveDefinition().getAsJsonObject("result").get("id").getAsString().equals("minecraft:gold_ingot"),
                "Custom cooking records the effective public nominal stack without converting its tag to a product");
        check(!process.acquisitionComplete() && !process.conservationComplete() && process.duration() == 123
                && process.effectiveDefinition().getAsJsonObject("serializer_definition").equals(encoded)
                && process.effectiveDefinition().get("runtime_class").getAsString().equals(TagCookingFixture.class.getName()),
                "Custom cooking preserves exact tag, duration and class while rejecting unproved access/conservation");
        ValuationGenerationInputs.configure(new BalanceOverrides(List.of(new BalanceOverrides.FactOverride("fixture:cooking_input_source",
                BalanceOverrides.SubjectKind.ITEM, "minecraft:stone", 1000, Map.of("attainable", true, "resource_value", 80.0), "fixture", 1)), Map.of()));
        check(!evaluateProcess(process, Items.GOLD_INGOT, RecipeType.BLASTING).modeledAcquisition() && limitations.size() == 1,
                "Actual valuation solver cannot mark a custom nominal cooking product attainable even with a known input");
        ValuationGenerationInputs.configure(BalanceOverrides.empty());
        var normal = graph.processes().stream().filter(p -> p.id().equals("fixture:ordinary_cooking")).findFirst().orElseThrow();
        check(normal.acquisitionComplete() && !normal.conservationComplete() && normal.outputs().getFirst().count() == 2,
                "Ordinary vanilla cooking keeps supported acquisition with unknown fuel conservation");
        check(BalanceDocument.GSON.fromJson(BalanceDocument.GSON.toJson(graph), ProductionGraph.class).equals(graph),
                "Cooking nominal projection and serializer predicate survive saved graph serialization");
        // Omitting a tag from bindTags retains its old HolderSet; explicitly bind it empty.
        BuiltInRegistries.ITEM.bindTags(Map.of(tag, List.of()));
        isolatedFailure("fixture:empty_cooking_tag", new TagCookingFixture(Ingredient.of(Items.STONE), Ingredient.of(tag), 100), "ArrayIndexOutOfBoundsException");
        var unknown = JsonParser.parseString("{\"ingredients\":[{\"item\":\"minecraft:stone\"}],\"result\":{\"tag\":\"fixture:unknown\"}}").getAsJsonObject();
        try {
            EffectiveProduction.normalize("fixture:unknown_output", "minecraft:blasting", null, unknown, registries, null);
            throw new AssertionError("Unknown output predicate accepted as an item stack");
        } catch (IllegalArgumentException expected) {
            check(expected.getMessage().contains("no concrete item identity") && expected.getMessage().contains("fixture:unknown"),
                    "Unknown output schemas fail with a useful diagnostic instead of dereferencing a missing id/item");
        }
    }

    private static void isolatedFailure(String recipeId, Recipe<?> broken, String detail) {
        List<String> limitations = new ArrayList<>();
        var graph = EffectiveProduction.collect(List.of(new RecipeHolder<>(id(recipeId), broken), holder("fixture:healthy_after_failure", Items.DIAMOND, 2)),
                registries, mod -> null, (family, reason) -> limitations.add(family + ": " + reason));
        check(graph.processes().size() == 1 && graph.processes().getFirst().id().equals("fixture:healthy_after_failure"),
                "A failing recipe is excluded completely and healthy recipes continue: " + recipeId);
        check(limitations.size() == 1 && limitations.getFirst().contains("excluded from acquisition, valuation and conservation")
                && graph.warnings().stream().anyMatch(w -> w.contains(recipeId) && w.contains(broken.getClass().getName()) && w.contains(detail)),
                "Runtime/linkage exclusion retains recipe identity, class, family and cause: " + recipeId);
    }

    private static void recipeFailureIsolation() {
        Recipe<RecipeInput> generic = new Recipe<>() {
            public boolean matches(RecipeInput input, net.minecraft.world.level.Level level) { throw new AssertionError("Do not match generic recipe"); }
            public ItemStack assemble(RecipeInput input, HolderLookup.Provider lookup) { throw new AssertionError("Do not assemble generic recipe"); }
            public boolean canCraftInDimensions(int x, int y) { return true; }
            public ItemStack getResultItem(HolderLookup.Provider lookup) { throw new NoClassDefFoundError("fixture generic broken linkage"); }
            public RecipeSerializer<?> getSerializer() { return RecipeSerializer.SHAPELESS_RECIPE; }
            public RecipeType<?> getType() { return new RecipeType<Recipe<RecipeInput>>() {}; }
        };
        isolatedFailure("fixture:broken_generic", generic, "fixture generic broken linkage");
        var brokenType = new ShapelessRecipe("", CraftingBookCategory.MISC, new ItemStack(Items.GOLD_INGOT),
                NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.STONE))) {
            @Override public RecipeType<?> getType() { throw new NoClassDefFoundError("fixture type broken linkage"); }
        };
        isolatedFailure("fixture:broken_type", brokenType, "fixture type broken linkage");
        List<RecipeHolder<?>> many = new ArrayList<>();
        for (int i = 0; i < 40; i++) many.add(new RecipeHolder<>(id("fixture:failed_" + i), generic));
        var bounded = EffectiveProduction.collect(many, registries, mod -> null, (family, reason) -> {});
        check(bounded.processes().isEmpty() && bounded.warnings().stream().filter(w -> w.startsWith("Excluded incompatible effective production recipe")).count() == 16
                && bounded.warnings().stream().anyMatch(w -> w.startsWith("24 additional incompatible")),
                "Many broken recipe objects keep bounded representative diagnostics and a complete exclusion count");
        check(BalanceDocument.GSON.fromJson(BalanceDocument.GSON.toJson(bounded), ProductionGraph.class).equals(bounded),
                "Failure diagnostics survive saved graph serialization without admitting failed evidence");
    }

    private static ProceduralValuationResult evaluateProcess(ProductionGraph.Process process, Item output, RecipeType<?> type) throws Exception {
        var models = ProceduralValuationIndex.productionModels(process, type);
        Constructor<?> constructor = ProceduralValuationIndex.class.getDeclaredConstructors()[0]; constructor.setAccessible(true);
        var index = (ProceduralValuationIndex) constructor.newInstance(Map.of(output, models), Map.of(), Map.of(), Map.of(), Map.of(),
                Map.of(), Map.of(), Map.of(), Map.of(), null, null, null, null);
        Class<?> contextType = Class.forName(ProceduralValuationEngine.class.getName() + "$EvaluationContext");
        Constructor<?> contextConstructor = contextType.getDeclaredConstructor(ProceduralValuationIndex.class); contextConstructor.setAccessible(true);
        Method evaluate = ProceduralValuationEngine.class.getDeclaredMethod("evaluateItem", ProceduralValuationIndex.class, Item.class, contextType); evaluate.setAccessible(true);
        return (ProceduralValuationResult) evaluate.invoke(null, index, output, contextConstructor.newInstance(index));
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
