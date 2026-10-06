package com.mistaboom.essence_ascendance.valuation;

import com.google.gson.*;
import com.mistaboom.essence_ascendance.balance.economy.ProductionGraph;
import com.mistaboom.essence_ascendance.balance.engine.OptionalIntegration;
import com.mistaboom.essence_ascendance.balance.generated.BalancePerformance;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import java.util.*;

/** Reads published recipe objects, never resource files or script text. One scan per generation. */
public final class EffectiveProduction {
    private EffectiveProduction() { }
    private static final Set<String> CREATE = Set.of("create:crushing", "create:milling", "create:cutting",
            "create:pressing", "create:mixing", "create:compacting", "create:splashing", "create:haunting",
            "create:filling", "create:emptying");
    private static final Set<String> MEKANISM = Set.of("mekanism:crushing", "mekanism:enriching", "mekanism:smelting");

    static ProductionGraph collect(GenerationDataSnapshot data, HolderLookup.Provider registries) {
        return collect(data.recipes(), registries, data::installedVersion, data::unsupportedRecipe);
    }

    static ProductionGraph collect(List<RecipeHolder<?>> recipes, HolderLookup.Provider registries,
                                   java.util.function.Function<String, String> versions,
                                   java.util.function.BiConsumer<String, String> unsupported) {
        List<ProductionGraph.Process> processes = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Set<String> families = new TreeSet<>();
        Map<String, Long> adapterNanos = new TreeMap<>();
        int emptyResults = 0, failedRecipes = 0;
        try (var phase = BalancePerformance.phase("effective_production_normalization")) {
            for (RecipeHolder<?> holder : recipes) {
                BalancePerformance.increment("production_recipes_inspected");
                Recipe<?> recipe = holder.value();
                String[] observedFamily = { "unresolved_type" };
                var attempt = OptionalIntegration.attempt("effective_production_recipe:" + holder.id(),
                        "normalize published " + recipe.getClass().getName(),
                        () -> evaluate(holder, registries, versions, observedFamily));
                String family = observedFamily[0];
                families.add(family);
                if (attempt.succeeded()) {
                    RecipeEvaluation evaluated = attempt.value().orElseThrow();
                    if (!evaluated.allowed()) continue;
                    if (evaluated.supported()) {
                        JsonObject definition = evaluated.definition();
                        ProductionGraph.Process process = evaluated.process();
                        if (process != null) {
                            processes.add(process);
                            if (!process.acquisitionComplete()) unsupported.accept(family,
                                    "partial normalized evidence: resource/predicate/source-access or custom matching/assembly unresolved; no known acquisition seed");
                        } else if (definition.has("nominal_result_empty")) {
                            unsupported.accept(family, "empty nominal result; dynamic/tag output unresolved; diagnostic-only, no acquisition or conservation claim");
                            // Save bounded effective evidence without inventing a product or quantity.
                            if (emptyResults++ < 16) {
                                String evidence = definition.toString();
                                warnings.add("Unresolved empty nominal result " + holder.id() + ": "
                                        + evidence.substring(0, Math.min(8192, evidence.length()))
                                        + (evidence.length() > 8192 ? " [definition truncated at 8192 characters]" : ""));
                            }
                            BalancePerformance.increment("production_empty_nominal_results");
                        } else unsupported.accept(family, "no item output; non-item evidence requires resource valuation");
                        adapterNanos.merge(evaluated.adapter() == null ? "vanilla" : evaluated.adapter(), evaluated.nanos(), Long::sum);
                    } else {
                        // A public generic view is advisory: unknown counts/operating costs never prove conservation.
                        ProductionGraph.Process generic = evaluated.process();
                        if (generic != null) processes.add(generic);
                        unsupported.accept(family, "unsupported machine/addon semantics; generic view is advisory only");
                    }
                } else {
                    // An optional recipe object's getters/codecs may differ or be broken. Never publish
                    // its partial inputs/outputs, and continue collecting healthy native recipes.
                    unsupported.accept(family, "normalization failed: " + recipe.getClass().getName()
                            + "; excluded from acquisition, valuation and conservation");
                    String diagnostic = "Excluded incompatible effective production recipe " + holder.id()
                            + " (" + family + ", " + recipe.getClass().getName() + "): " + attempt.failure()
                            + "; no acquisition, valuation or conservation evidence retained";
                    if (failedRecipes++ < 16) {
                        warnings.add(diagnostic);
                    }
                    BalancePerformance.increment("production_recipe_normalization_failures");
                }
            }
        }
        if (emptyResults > 16) warnings.add((emptyResults - 16) + " additional empty nominal results; representative evidence limited to 16 recipes.");
        if (failedRecipes > 16) {
            String summary = (failedRecipes - 16) + " additional incompatible effective production recipes excluded; representative evidence limited to 16 recipes.";
            warnings.add(summary);
        }
        warnings.add("KubeJS core added/removed/replaced recipes and effective tags are observed after publication. "
                + "Addon families require an audited adapter. Custom callbacks, event generation, scripted loot and non-recipe automation are not inferred or executed.");
        long incomplete = processes.stream().filter(p -> !p.conservationComplete()).count();
        warnings.add(incomplete + " processes have incomplete conservation evidence; no hard material constraint is imposed on them.");
        BalancePerformance.count("normalized_production_nodes", processes.size());
        BalancePerformance.count("production_recipe_families", families.size());
        adapterNanos.forEach((adapter, nanos) -> BalancePerformance.count("production_adapter_nanos/" + adapter, nanos));
        BalancePerformance.count("normalized_production_edges", processes.stream().mapToLong(p -> p.inputs().size() + p.outputs().size()).sum());
        return new ProductionGraph(processes, warnings);
    }

    private record RecipeEvaluation(boolean allowed, boolean supported, String adapter,
                                    ProductionGraph.Process process, JsonObject definition, long nanos) { }

    /** Build privately before publishing anything to the shared graph. A failed getter, encoder or
     * normalizer leaves only diagnostic identity; none of that recipe's partial facts can leak out. */
    private static RecipeEvaluation evaluate(RecipeHolder<?> holder, HolderLookup.Provider registries,
                                             java.util.function.Function<String, String> versions, String[] observedFamily) {
        Recipe<?> recipe = holder.value();
        RecipeType<?> type = recipe.getType();
        String family = ValuationGenerationInputs.recipeFamily(type);
        observedFamily[0] = family;
        if (!ValuationGenerationInputs.recipeAllowed(holder.id(), type)) return new RecipeEvaluation(false, false, null, null, null, 0);
        boolean vanilla = vanilla(type);
        String adapter = adapter(family, recipe.getClass().getName(), versions);
        if (vanilla || adapter != null) {
            long started = System.nanoTime();
            JsonObject definition = definition(holder, registries, versions);
            ProductionGraph.Process process = normalize(holder.id().toString(), family, adapter, definition, registries, vanilla ? recipe : null);
            return new RecipeEvaluation(true, true, adapter, process, definition, System.nanoTime() - started);
        }
        return new RecipeEvaluation(true, false, null, generic(holder, registries), null, 0);
    }

    /** Runtime-created shaped patterns need not retain datapack key/pattern data. Public crafting fields
     * remain useful for subclasses, but cannot certify their custom matching, assembly or remainders.
     * Custom representation errors/explicit unsupported encoding remain partial; thrown failures are
     * isolated by the per-recipe collector and exclude that recipe. */
    private static JsonObject definition(RecipeHolder<?> holder, HolderLookup.Provider registries,
                                         java.util.function.Function<String, String> versions) {
        Recipe<?> recipe = holder.value();
        var ops = RegistryOps.create(JsonOps.INSTANCE, registries);
        if (recipe instanceof AbstractCookingRecipe cooking) return cookingDefinition(holder, cooking, ops, registries);
        if (!(recipe instanceof ShapedRecipe) && !(recipe instanceof ShapelessRecipe) && recipe.getType() != RecipeType.CRAFTING)
            return GenerationDataSnapshot.encodeDefinition(Recipe.CODEC, recipe, ops, "effective recipe " + holder.id());
        try {
            ShapedRecipe shaped = recipe instanceof ShapedRecipe value ? value : null;
            String form = shaped != null ? "shaped" : recipe instanceof ShapelessRecipe ? "shapeless" : "crafting";
            if (shaped != null && (shaped.getWidth() <= 0 || shaped.getHeight() <= 0
                    || (long) shaped.getWidth() * shaped.getHeight() != shaped.getIngredients().size()))
                throw new IllegalStateException("Invalid effective shaped dimensions/slots");
            JsonObject root = new JsonObject();
            ResourceLocation serializerId = BuiltInRegistries.RECIPE_SERIALIZER.getKey(recipe.getSerializer());
            if (serializerId == null) throw new IllegalStateException("Unregistered crafting recipe serializer");
            root.addProperty("type", serializerId.toString());
            root.addProperty("projection", "native_" + form + "_public_fields");
            root.addProperty("runtime_class", recipe.getClass().getName());
            if (shaped != null) {
                root.addProperty("width", shaped.getWidth()); root.addProperty("height", shaped.getHeight());
            }
            root.addProperty("group", recipe.getGroup());
            if (recipe instanceof CraftingRecipe crafting) root.addProperty("category", crafting.category().getSerializedName());
            root.addProperty("show_notification", recipe.showNotification());
            JsonArray slots = new JsonArray();
            for (Ingredient ingredient : recipe.getIngredients())
                slots.add(ingredient == Ingredient.EMPTY ? JsonNull.INSTANCE : ingredientDefinition(holder.id(), slots.size(), ingredient, ops));
            root.add("ingredients", slots);
            ItemStack nominal = recipe.getResultItem(registries);
            if (nominal.isEmpty()) root.addProperty("nominal_result_empty", true);
            else root.add("result", ItemStack.CODEC.encodeStart(ops, nominal).getOrThrow());
            if (recipe.getClass() != ShapedRecipe.class && recipe.getClass() != ShapelessRecipe.class) {
                var behavior = KubeCraftingSemantics.inspect(recipe, versions.apply("kubejs"));
                // An exact audited subclass without configured hooks has native matching,
                // static copied output and native remainders. Every other subclass remains advisory.
                KubeCraftingSemantics.annotate(root, behavior, versions.apply("kubejs"));
                try {
                    var encoded = Recipe.CODEC.encodeStart(ops, recipe);
                    if (encoded.result().isPresent()) root.add("serializer_definition", encoded.result().orElseThrow());
                    else serializationLimitation(root, form, "codec_representation_error", encoded.error().orElseThrow().message());
                } catch (UnsupportedOperationException unsupported) {
                    // This optional encoder explicitly lacks the operation; required public codecs above remain strict.
                    serializationLimitation(root, form, "unsupported_serializer_operation", unsupported.toString());
                    BalancePerformance.increment("production_custom_serializer_unsupported_operations");
                }
                BalancePerformance.increment(behavior.nativeBehavior() ? "production_kubejs_hookless_" + form + "_projections"
                        : "production_custom_" + form + "_advisory_projections");
            }
            BalancePerformance.increment("production_public_" + form + "_projections");
            return root;
        } catch (RuntimeException | LinkageError failure) {
            throw new IllegalStateException("Cannot normalize effective crafting recipe " + holder.id() + ": " + failure, failure);
        }
    }

    /** Cooking subclasses may encode result tags/predicates rather than ItemStack.CODEC. Observe the
     * published nominal stack without interpreting their private serializer schema or executing assembly. */
    private static JsonObject cookingDefinition(RecipeHolder<?> holder, AbstractCookingRecipe recipe,
                                                RegistryOps<JsonElement> ops, HolderLookup.Provider registries) {
        JsonObject root = new JsonObject();
        ResourceLocation serializer = BuiltInRegistries.RECIPE_SERIALIZER.getKey(recipe.getSerializer());
        if (serializer == null) throw new IllegalStateException("Unregistered cooking recipe serializer " + holder.id());
        root.addProperty("type", serializer.toString());
        root.addProperty("projection", "native_cooking_public_fields");
        root.addProperty("runtime_class", recipe.getClass().getName());
        root.addProperty("group", recipe.getGroup());
        root.addProperty("category", recipe.category().getSerializedName());
        root.addProperty("experience", recipe.getExperience());
        root.addProperty("cookingtime", recipe.getCookingTime());
        JsonArray slots = new JsonArray();
        for (Ingredient ingredient : recipe.getIngredients())
            if (ingredient != Ingredient.EMPTY) slots.add(ingredientDefinition(holder.id(), slots.size(), ingredient, ops));
        root.add("ingredients", slots);
        ItemStack nominal = recipe.getResultItem(registries);
        if (nominal.isEmpty()) root.addProperty("nominal_result_empty", true);
        else root.add("result", ItemStack.CODEC.encodeStart(ops, nominal).getOrThrow());
        if (recipe.getClass() != SmeltingRecipe.class && recipe.getClass() != BlastingRecipe.class
                && recipe.getClass() != SmokingRecipe.class && recipe.getClass() != CampfireCookingRecipe.class) {
            root.addProperty("custom_behavior_unresolved", true);
            try {
                var encoded = Recipe.CODEC.encodeStart(ops, recipe);
                if (encoded.result().isPresent()) root.add("serializer_definition", encoded.result().orElseThrow());
                else serializationLimitation(root, "cooking", "codec_representation_error", encoded.error().orElseThrow().message());
            } catch (UnsupportedOperationException unsupported) {
                serializationLimitation(root, "cooking", "unsupported_serializer_operation", unsupported.toString());
                BalancePerformance.increment("production_custom_serializer_unsupported_operations");
            }
            BalancePerformance.increment("production_custom_cooking_advisory_projections");
        }
        BalancePerformance.increment("production_public_cooking_projections");
        return root;
    }

    /** Runtime ingredients can expose usable recipe objects without a registered codec type. Keep
     * the missing predicate explicit instead of replacing it with displayed items or dropping its slot. */
    private static JsonElement ingredientDefinition(ResourceLocation recipe, int slot, Ingredient ingredient,
                                                     RegistryOps<JsonElement> ops) {
        var attempt = OptionalIntegration.attempt("effective_production_recipe:" + recipe,
                "encode published ingredient slot " + slot,
                () -> Ingredient.CODEC.encodeStart(ops, ingredient).getOrThrow());
        if (attempt.succeeded()) return attempt.value().orElseThrow();
        JsonObject unresolved = new JsonObject();
        unresolved.addProperty("unresolved_ingredient", true);
        unresolved.addProperty("runtime_class", ingredient.getClass().getName());
        unresolved.addProperty("serialization_failure", attempt.failure());
        BalancePerformance.increment("production_unrepresentable_ingredients");
        return unresolved;
    }

    private static void serializationLimitation(JsonObject root, String form, String kind, String error) {
        root.addProperty("serialization_limitation_kind", kind);
        root.addProperty("serialization_limitation", error.substring(0, Math.min(1024, error.length())));
        BalancePerformance.increment("production_custom_" + form + "_unrepresentable_codecs");
    }

    /** Version and runtime class guards protect the audited public codec boundary. No optional classes load here. */
    static String adapter(String family, String className, java.util.function.Function<String, String> versions) {
        if (CREATE.contains(family) && className.startsWith("com.simibubi.create.content.") && supported(versions.apply("create"), "6.0.10")) return "create_processing";
        if (family.startsWith("modern_industrialization:") && className.equals("aztech.modern_industrialization.machines.recipe.MachineRecipe")
                && supported(versions.apply("modern_industrialization"), "2.5.8")) return "mi_machine";
        if (MEKANISM.contains(family) && className.startsWith("mekanism.api.recipes.basic.Basic") && supported(versions.apply("mekanism"), "10.7.19")) return "mekanism_item";
        if (List.of("mekanism:crystallizing", "mekanism:oxidizing", "mekanism:pigment_extracting", "mekanism:chemical_conversion",
                    "mekanism:activating", "mekanism:centrifuging").contains(family)
                && className.startsWith("mekanism.api.recipes.basic.Basic") && supported(versions.apply("mekanism"), "10.7.19")) return "mekanism_chemical";
        if (family.equals("occultism:miner") && className.equals("com.klikli_dev.occultism.crafting.recipe.MinerRecipe")
                && supported(versions.apply("occultism"), "1.224.4")) return "weighted_extraction";
        return null;
    }
    private static boolean supported(String version, String audited) { return audited.equals(version); }
    static boolean vanilla(RecipeType<?> type) {
        return type == RecipeType.CRAFTING || type == RecipeType.SMITHING || type == RecipeType.STONECUTTING
                || type == RecipeType.SMELTING || type == RecipeType.BLASTING || type == RecipeType.SMOKING || type == RecipeType.CAMPFIRE_COOKING;
    }

    /** Public codec seam also used by fixtures; unconsumed inputs are prerequisites, never per-output construction cost. */
    static ProductionGraph.Process normalize(String id, String family, String adapter, JsonObject root,
                                              HolderLookup.Provider registries, Recipe<?> nativeVanilla) {
        List<ProductionGraph.Input> inputs = new ArrayList<>();
        List<ProductionGraph.Output> outputs = new ArrayList<>();
        Map<String, String> facts = new TreeMap<>();
        facts.put("effective_definition", root.toString());
        facts.put("provenance", root.has("projection") ? "published RecipeManager object / native public fields and ingredient/stack codecs"
                : "published RecipeManager object / public serializer codec");
        facts.put("adapter", adapter == null ? "vanilla" : adapter);
        facts.put("operation", adapter == null ? "manual_or_machine_unknown" : "automated_processing");
        facts.put("player_activity", "unknown"); facts.put("renewability", "unknown");
        facts.put("setup", adapter == null ? "recipe_station" : "machine_family:" + family);
        facts.put("access", "machine construction/configuration access unobserved");
        facts.put("energy", adapter == null ? "unobserved_fuel" : "unobserved_native_operating_cost");
        facts.put("duration_known", "false");
        if (root.has("behavior_adapter")) {
            facts.put("behavior_adapter", root.get("behavior_adapter").getAsString());
            facts.put("behavior_api_version", root.get("behavior_api_version").getAsString());
            facts.put("ingredient_action_count", root.get("ingredient_action_count").getAsString());
            facts.put("modify_result_present", root.get("modify_result_present").getAsString());
        }
        if (root.has("custom_behavior_unresolved")) {
            facts.put("conditions_unresolved", "true");
            facts.put("custom_behavior", "Inherited nominal public fields only; custom matching, assembly, component transfer and remainders unresolved");
            facts.put("runtime_class", root.get("runtime_class").getAsString());
            facts.put("serializer", root.get("type").getAsString());
            if (root.has("serialization_limitation")) facts.put("serialization_limitation", root.get("serialization_limitation").getAsString());
            if (root.has("serialization_limitation_kind")) facts.put("serialization_limitation_kind", root.get("serialization_limitation_kind").getAsString());
        }
        List<ProductionGraph.ResourceFlow> resources = new ArrayList<>();
        boolean resourceInputs = false, stochasticInputs = false;
        if ("weighted_extraction".equals(adapter)) {
            addInput(inputs, root.get("ingredient"), 1, false, registries);
            JsonObject result = root.getAsJsonObject("result");
            // The result codec carries selection weight, not an independent Bernoulli probability.
            if (result.has("stack")) addOutput(outputs, result.get("stack"), "probability", "count");
            else if (result.has("tag")) resources.add(new ProductionGraph.ResourceFlow("output", "product", "item_tag", "items",
                    List.of("#" + result.get("tag").getAsString()), number(result, "count", 1), 1, result.toString()));
            else throw new IllegalArgumentException("Unsupported weighted extraction result " + result);
            facts.put("selection_weight", result.get("weight").toString());
            facts.put("probability_basis", "conditional selected result; overlapping eligible weight pools/configured rolls unresolved");
            facts.put("function", "automated_resource_extraction");
            facts.put("operation", "automated"); facts.put("player_activity", "passive_while_loaded");
            facts.put("renewability", "unknown");
            facts.put("durability_cost", "MinerSpirit stack hurtAndBreak per mining operation; native configuration/enchantment/roll dependence unresolved");
            facts.put("setup", "occultism:dimensional_mineshaft plus compatible miner spirit");
            facts.put("energy", "not an energy recipe; spirit durability recurring cost unresolved");
            facts.put("conditions_unresolved", "true");
        } else if ("mi_machine".equals(adapter)) {
            for (JsonElement element : list(root, "item_inputs")) {
                JsonObject value = element.getAsJsonObject();
                double probability = number(value, "probability", 1);
                double count = number(value, "amount", number(value, "count", 1));
                addInput(inputs, value, count, probability != 0, registries);
                stochasticInputs |= probability > 0 && probability < 1;
            }
            for (JsonElement element : list(root, "item_outputs")) addOutput(outputs, element, "probability", "amount");
            resourceInputs = !list(root, "fluid_inputs").isEmpty();
            facts.put("energy", "modern_industrialization:EU/tick=" + number(root, "eu", 0));
            facts.put("duration_known", "true");
            facts.put("recurring_resources", list(root, "fluid_inputs").toString());
            facts.put("resource_outputs", list(root, "fluid_outputs").toString());
            for (JsonElement element : list(root, "fluid_inputs")) resources.add(fluidFlow(element, "input", "modern_industrialization:fluid_unit"));
            for (JsonElement element : list(root, "fluid_outputs")) resources.add(fluidFlow(element, "output", "modern_industrialization:fluid_unit"));
            if (!list(root, "process_conditions").isEmpty()) facts.put("conditions_unresolved", "true");
        } else if ("create_processing".equals(adapter)) {
            JsonArray fluids = new JsonArray(), fluidOutputs = new JsonArray();
            for (JsonElement element : list(root, "ingredients")) {
                if (isFluid(element)) { fluids.add(element); resourceInputs = true; }
                else addInput(inputs, element, 1, true, registries);
            }
            for (JsonElement element : list(root, "results")) {
                if (isFluid(element)) fluidOutputs.add(element); else addOutput(outputs, element, "chance", "count");
            }
            facts.put("recurring_resources", fluids.toString()); facts.put("resource_outputs", fluidOutputs.toString());
            for (JsonElement element : fluids) resources.add(fluidFlow(element, "input", "neoforge:mB"));
            for (JsonElement element : fluidOutputs) resources.add(fluidFlow(element, "output", "neoforge:mB"));
            facts.put("heat_requirement", root.has("heat_requirement") ? root.get("heat_requirement").toString() : "none");
            facts.put("energy", "create:kinetic_speed/stress or fan/heat conditions unobserved");
            // Codec default 0 is not measured instantaneous throughput.
            facts.put("duration_known", Boolean.toString(number(root, "processing_time", 0) > 0));
        } else if ("mekanism_chemical".equals(adapter)) {
            if (family.equals("mekanism:crystallizing") || family.equals("mekanism:activating") || family.equals("mekanism:centrifuging")) {
                resources.add(chemicalFlow(root.getAsJsonObject("input"), "input")); resourceInputs = true;
            } else {
                JsonObject input = root.getAsJsonObject("input");
                addInput(inputs, input, number(input, "count", 1), true, registries);
            }
            if (family.equals("mekanism:crystallizing")) addOutput(outputs, root.get("output"), "probability", "count");
            else resources.add(chemicalFlow(root.getAsJsonObject("output"), "output"));
            facts.put("energy", "mekanism:energy/tick and configured processing duration unobserved");
        } else if ("mekanism_item".equals(adapter)) {
            JsonObject input = root.getAsJsonObject("input");
            addInput(inputs, input, number(input, "count", 1), true, registries);
            addOutput(outputs, root.get("output"), "probability", "count");
            facts.put("energy", "mekanism:energy/tick and configured processing duration unobserved");
        } else {
            if (family.equals("minecraft:smithing")) {
                for (String key : List.of("template", "base", "addition")) if (root.has(key)) addInput(inputs, root.get(key), 1, true, registries);
                // Native smithing copies components from its base; static result cannot model every variant.
                facts.put("variant_transform", "base_components_copied");
            } else if (root.has("projection") && root.has("ingredients")) {
                // Reuse the staged public projection: a failed ingredient codec is an unresolved
                // predicate, and retrying that codec here would discard otherwise useful diagnostics.
                for (JsonElement element : list(root, "ingredients"))
                    if (!element.isJsonNull()) addInput(inputs, element, 1, true, registries);
            } else if (nativeVanilla != null) {
                for (Ingredient ingredient : nativeVanilla.getIngredients()) {
                    if (ingredient == Ingredient.EMPTY) continue;
                    JsonElement value = Ingredient.CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE, registries), ingredient).getOrThrow();
                    addInput(inputs, value, 1, true, registries);
                }
            } else {
                for (JsonElement element : list(root, "ingredients")) addInput(inputs, element, 1, true, registries);
                if (root.has("ingredient")) addInput(inputs, root.get("ingredient"), 1, true, registries);
            }
            if (root.has("result")) addOutput(outputs, root.get("result"), "probability", "count");
            facts.put("duration_known", Boolean.toString(root.has("cookingtime")));
            // Custom remainder behavior is unresolved; containers also require an observed primary product.
            if (family.equals("minecraft:crafting") && !outputs.isEmpty() && !root.has("custom_behavior_unresolved"))
                returnedContainers(inputs, outputs, facts);
        }
        if (outputs.isEmpty() && resources.stream().noneMatch(flow -> flow.direction().equals("output"))) return null;
        if (!resources.isEmpty()) facts.put("native_resources", new Gson().toJson(resources));
        boolean predicates = hasPredicates(root);
        boolean inputPredicates = false;
        for (String key : List.of("ingredients", "ingredient", "item_inputs", "input", "template", "base", "addition", "key"))
            if (root.has(key)) inputPredicates |= hasPredicates(root.get(key));
        boolean conditions = facts.containsKey("conditions_unresolved");
        facts.put("component_sensitive", Boolean.toString(predicates));
        boolean unobservedSourceAccess = adapter != null && inputs.stream().noneMatch(ProductionGraph.Input::consumed);
        facts.put("acquisition_complete", Boolean.toString(!resourceInputs && !inputPredicates && !conditions && !unobservedSourceAccess
                && inputs.stream().noneMatch(input -> input.alternatives().stream().anyMatch(choice -> choice.startsWith("predicate:")))));
        // Unknown native energy/fuel, variants, probabilistic inputs or joint source output invalidate hard conservation.
        boolean complete = adapter == null && !resourceInputs && !predicates && !conditions && !stochasticInputs
                && (family.equals("minecraft:crafting") || family.equals("minecraft:stonecutting"));
        facts.put("conservation_complete", Boolean.toString(complete && !facts.containsKey("conditional_returns")));
        double ticks = number(root, "duration", number(root, "processing_time", number(root, "cookingtime", 0)));
        return new ProductionGraph.Process(id, family, inputs, outputs, ticks, 0,
                "essence_ascendance:effective_" + facts.get("adapter"), adapter == null ? .9 : .85, facts);
    }

    private static void addInput(List<ProductionGraph.Input> inputs, JsonElement value, double count, boolean consumed, HolderLookup.Provider registries) {
        if (value.isJsonObject() && value.getAsJsonObject().has("ingredient")) value = value.getAsJsonObject().get("ingredient");
        if (value.isJsonObject() && value.getAsJsonObject().has("unresolved_ingredient")) {
            inputs.add(new ProductionGraph.Input(List.of("predicate:" + value), count, consumed));
            return;
        }
        Ingredient ingredient = Ingredient.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, registries), value).getOrThrow();
        List<String> alternatives = Arrays.stream(ingredient.getItems()).filter(s -> !s.isEmpty()).map(s -> BuiltInRegistries.ITEM.getKey(s.getItem()).toString()).distinct().sorted().toList();
        if (alternatives.isEmpty()) alternatives = List.of("predicate:" + value);
        inputs.add(new ProductionGraph.Input(alternatives, count, consumed));
    }
    private static void addOutput(List<ProductionGraph.Output> outputs, JsonElement value, String probabilityKey, String countKey) {
        if (value == null || !value.isJsonObject()) throw new IllegalArgumentException("Production output is not an item-stack object: " + value);
        JsonObject output = value.getAsJsonObject();
        if ((!output.has("id") || !output.get("id").isJsonPrimitive())
                && (!output.has("item") || !output.get("item").isJsonPrimitive()))
            throw new IllegalArgumentException("Production output has no concrete item identity: " + output);
        String id = output.has("id") ? output.get("id").getAsString() : output.get("item").getAsString();
        double chance = number(output, probabilityKey, 1);
        if (chance == 0) return;
        if (!BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(id))) throw new IllegalArgumentException("Unknown production item " + id);
        outputs.add(new ProductionGraph.Output(id, number(output, countKey, number(output, "count", 1)), chance, !outputs.isEmpty()));
    }
    private static boolean isFluid(JsonElement value) {
        if (!value.isJsonObject()) return false;
        var object = value.getAsJsonObject();
        return object.has("fluid") || object.has("fluid_tag") || (object.has("amount") && !object.has("item"));
    }
    private static ProductionGraph.ResourceFlow fluidFlow(JsonElement element, String direction, String unit) {
        JsonObject value = element.getAsJsonObject();
        double probability = number(value, "probability", 1);
        return new ProductionGraph.ResourceFlow(direction, direction.equals("output") ? "product" : probability == 0 ? "catalyst" : "consumed",
                "fluid", unit, List.of(fluidIdentity(value)), number(value, "amount", 1), probability, value.toString());
    }
    private static boolean hasPredicates(JsonElement value) {
        if (value.isJsonArray()) return value.getAsJsonArray().asList().stream().anyMatch(EffectiveProduction::hasPredicates);
        if (!value.isJsonObject()) return false;
        JsonObject object = value.getAsJsonObject();
        if (object.has("components") || object.has("unresolved_ingredient")) return true;
        if (object.has("type") && object.get("type").isJsonPrimitive()) {
            String type = object.get("type").getAsString();
            if (type.startsWith("neoforge:") || type.startsWith("fabric:")) return true;
        }
        return object.entrySet().stream().anyMatch(entry -> hasPredicates(entry.getValue()));
    }
    private static ProductionGraph.ResourceFlow chemicalFlow(JsonObject value, String direction) {
        JsonObject predicate = value.has("ingredient") && value.get("ingredient").isJsonObject() ? value.getAsJsonObject("ingredient") : value;
        String identity = "predicate:" + predicate;
        for (String key : List.of("chemical", "id", "tag")) if (predicate.has(key) && predicate.get(key).isJsonPrimitive())
            identity = (key.equals("tag") ? "#" : "") + predicate.get(key).getAsString();
        return new ProductionGraph.ResourceFlow(direction, direction.equals("output") ? "product" : "consumed", "chemical", "mekanism:chemical_amount",
                List.of(identity), number(value, "amount", 1), 1, value.toString());
    }
    private static String fluidIdentity(JsonObject value) {
        for (String key : List.of("fluid", "id", "tag", "fluid_tag"))
            if (value.has(key) && value.get(key).isJsonPrimitive()) return (key.contains("tag") ? "#" : "") + value.get(key).getAsString();
        if (value.has("ingredient") && value.get("ingredient").isJsonObject()) return fluidIdentity(value.getAsJsonObject("ingredient"));
        return "predicate:" + value;
    }
    private static void returnedContainers(List<ProductionGraph.Input> inputs, List<ProductionGraph.Output> outputs, Map<String, String> facts) {
        for (int slot = 0; slot < inputs.size(); slot++) {
            ProductionGraph.Input input = inputs.get(slot);
            Set<String> returns = new TreeSet<>(); boolean allReturn = true, allSelf = true;
            for (String id : input.alternatives()) {
                ResourceLocation itemId = ResourceLocation.tryParse(id);
                if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId)) {
                    allReturn = false; allSelf = false; continue;
                }
                Item item = BuiltInRegistries.ITEM.get(itemId), remaining = item.getCraftingRemainingItem();
                if (remaining == null) { allReturn = false; allSelf = false; }
                else { returns.add(BuiltInRegistries.ITEM.getKey(remaining).toString()); allSelf &= remaining == item; }
            }
            if (allSelf) inputs.set(slot, new ProductionGraph.Input(input.alternatives(), input.count(), false));
            else if (allReturn && returns.size() == 1) outputs.add(new ProductionGraph.Output(returns.iterator().next(), input.count(), 1, true));
            else if (!returns.isEmpty()) facts.put("conditional_returns", "input-dependent containers retained in effective ingredients; joint outputs unresolved");
        }
    }
    private static ProductionGraph.Process generic(RecipeHolder<?> holder, HolderLookup.Provider registries) {
        Recipe<?> recipe = holder.value(); ItemStack output = recipe.getResultItem(registries);
        if (output == null || output.isEmpty()) return null;
        List<ProductionGraph.Input> inputs = new ArrayList<>();
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient == Ingredient.EMPTY) continue;
            List<String> choices = Arrays.stream(ingredient.getItems()).filter(s -> !s.isEmpty()).map(s -> BuiltInRegistries.ITEM.getKey(s.getItem()).toString()).distinct().toList();
            if (choices.isEmpty()) return null;
            inputs.add(new ProductionGraph.Input(choices, 1, true));
        }
        return new ProductionGraph.Process(holder.id().toString(), ValuationGenerationInputs.recipeFamily(recipe.getType()), inputs,
                List.of(new ProductionGraph.Output(BuiltInRegistries.ITEM.getKey(output.getItem()).toString(), output.getCount(), 1, false)),
                0, 0, "essence_ascendance:generic_advisory", .35,
                Map.of("acquisition_complete", "false", "conservation_complete", "false", "duration_known", "false", "energy", "unknown", "quantities", "generic estimates only"));
    }
    private static double number(JsonObject root, String key, double defaultValue) { return root.has(key) ? root.get(key).getAsDouble() : defaultValue; }
    private static JsonArray list(JsonObject root, String key) {
        if (!root.has(key)) return new JsonArray();
        if (root.get(key).isJsonArray()) return root.getAsJsonArray(key);
        JsonArray array = new JsonArray(); array.add(root.get(key)); return array;
    }
}
