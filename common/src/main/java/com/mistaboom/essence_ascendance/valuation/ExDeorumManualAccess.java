package com.mistaboom.essence_ascendance.valuation;

import com.google.gson.*;
import com.mistaboom.essence_ascendance.balance.engine.*;
import com.mistaboom.essence_ascendance.balance.generated.BalanceDocument;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.providers.number.*;
import java.util.*;

/** Audited Ex Deorum 3.12 manual consumers. Loaded recipes, actual cache selection,
 * independently renewable inputs/tools, and a finite station bill are all required.
 * This never authorizes machine automation, invents FE, samples RNG or mutates a world. */
public final class ExDeorumManualAccess {
    private ExDeorumManualAccess() { }
    private static final String API = "thedarkcolour.exdeorum.", PROVIDER = "exdeorum_manual_access";
    record Yield(double chance, double expected) { }
    private record Rule(String id, String kind, Object nativeRecipe, List<ItemStack> inputs, List<ItemStack> meshes,
                        ItemStack result, Yield yield, int volume) { }

    public static Map<String, ResourceEvidence> enrich(PackEvidenceContext context, Map<String, ResourceEvidence> original, EvidenceSink sink) {
        String version = context.inputs().installedVersion("exdeorum"); if (version == null) return original;
        if (!version.equals("3.12")) { sink.warn(PROVIDER + ": unsupported loaded version " + version); return original; }
        var staged = sink.staged();
        var attempt = OptionalIntegration.attempt(PROVIDER, "loaded manual recipes", () -> collect(context, original, staged));
        if (!attempt.succeeded()) { sink.warn(PROVIDER + " READ_FAILED: " + attempt.failure()); return original; }
        sink.merge(staged); return attempt.value().orElseThrow();
    }
    private static Map<String, ResourceEvidence> collect(PackEvidenceContext context, Map<String, ResourceEvidence> original, EvidenceSink sink) {
        var result = new TreeMap<>(original);
        Object caches = invokeStatic(API + "recipe.RecipeUtil", "getCaches", new Class<?>[]{net.minecraft.world.level.Level.class}, context.server().overworld());
        var rules = rules(context, sink);
        var barrels = blocks(context, API + "block.BarrelBlock");
        var sieves = blocks(context, API + "block.SieveBlock");
        var hammers = context.inputs().items().stream().filter(i -> i.getClass().getName().equals(API + "item.HammerItem"))
                .map(Item::getDefaultInstance).filter(s -> s.isDamageableItem() && s.getMaxDamage() > 2 && !s.isEnchanted()).toList();
        double compostStep = config("barrelProgressStep").doubleValue();
        int sieveInterval = config("sieveIntervalTicks").intValue();
        var installed = new HashSet<String>();
        var unresolved = new TreeMap<String, String>();
        for (int wave = 0; wave < 12; wave++) {
            var access = ConfiguredRecipeAccess.nativeCrafting(context, Collections.unmodifiableMap(new TreeMap<>(result)));
            var renewable = new HashMap<String, ConfigurationAccess.Proof>();
            var setup = new HashMap<String, ConfigurationAccess.Proof>();
            int before = installed.size();
            crookCultivation(context, sink, result, access, caches, renewable, installed);
            for (var rule : rules) {
                if (installed.contains(rule.id())) continue;
                for (var input : rule.inputs()) {
                    var supply = renewable.computeIfAbsent(id(input), access::requireItem);
                    if (!supply.placement().reachable()) { unresolved.putIfAbsent(rule.id(), "No independently renewable input; " + id(input) + ": " + supply.unknown()); continue; }
                    unresolved.put(rule.id(), "Renewable input " + id(input) + "; native operation/station gates unproven");
                    ConfigurationAccess.Proof station = null; String operation = "";
                    if (rule.kind().equals("compost")) {
                        if (!(compostStep > 0) || !Double.isFinite(compostStep)
                                || invoke(caches, "getBarrelCompostRecipe", new Class<?>[]{ItemStack.class}, input) != rule.nativeRecipe()) continue;
                        station = firstSetup(barrels, access, setup);
                        operation = "Manual empty barrel: consume " + ((999L + rule.volume()) / rule.volume()) + " " + id(input)
                                + " per 1000-volume fill; loaded progress step=" + compostStep + "; loaded ticking, collect dirt and repeat; no energy or measured rate";
                    } else if (rule.kind().equals("sieve")) {
                        if (sieveInterval < 0) continue;
                        for (var mesh : rule.meshes()) {
                            var selected = (List<?>)invoke(caches, "getSieveRecipes", new Class<?>[]{Item.class, ItemStack.class}, mesh.getItem(), input);
                            if (!selected.contains(rule.nativeRecipe())) continue;
                            for (var sieve : sieves) {
                                String key = id(sieve) + "/" + id(mesh);
                                var next = setup.computeIfAbsent(key, ignored -> access.requireFinite(List.of(stack(sieve, context), stack(mesh, context))).access());
                                if (next.placement().reachable()) { station = next; break; }
                            }
                            if (station != null) break;
                        }
                        operation = "Manual unenchanted mesh/sieve: consume one " + id(input)
                                + " per completed cycle; base 0.1 progress/use, minimum loaded interval=" + sieveInterval
                                + " ticks; mesh preserved, no FE; by-hand-only recipes are valid here; no automation or measured rate";
                    } else if (rule.kind().equals("hammer")) {
                        if (!(input.getItem() instanceof BlockItem block) || !block.getBlock().getClass().getPackageName().equals("net.minecraft.world.level.block")
                                || invoke(caches, "getHammerRecipe", new Class<?>[]{Item.class}, input.getItem()) != rule.nativeRecipe()) continue;
                        var state = block.getBlock().defaultBlockState();
                        if (state.getDestroySpeed(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO) < 0) continue;
                        for (var hammer : hammers) {
                            if (state.requiresCorrectToolForDrops() && !hammer.isCorrectToolForDrops(state)) {
                                unresolved.put(rule.id(), "Native tool predicate rejects " + id(hammer) + " for " + id(input)); continue;
                            }
                            var blocked = modifierBlockers(context, "HammerLootModifier", state, hammer, id(rule.result()));
                            if (!blocked.isEmpty()) { unresolved.put(rule.id(), "Native modifier scope for " + id(input) + "/" + id(hammer) + ": " + blocked); continue; }
                            var tool = renewable.computeIfAbsent("exact:" + id(hammer), ignored -> access.requireStack(stack(hammer, context)));
                            if (tool.placement().reachable()) { station = tool; break; }
                            unresolved.put(rule.id(), "Fresh exact hammer " + id(hammer) + ": " + tool.unknown());
                        }
                        operation = "Manual hammer: place and break one renewable input block; conservatively supply a fresh unenchanted hammer per operation, so wear is paid; loaded selected recipe and global loot conditions; no measured rate";
                    }
                    if (station == null || !station.placement().reachable()) continue;
                    var proof = ConfiguredRecipeAccess.combine(List.of(supply, station), rule.id(), .9);
                    if (publish(result, sink, id(rule.result()), rule.id(), rule.yield(), proof, operation)) { installed.add(rule.id()); break; }
                }
            }
            // Cultivation/crook bootstrap is added independently of recipe-rule closure.
            if (installed.size() == before) break;
        }
        installed.forEach(unresolved::remove);
        var diagnostics = new JsonObject(); unresolved.forEach((id, reason) -> diagnostics.addProperty(id, reason.length() > 1600 ? reason.substring(0, 1600) : reason));
        sink.add(new EvidenceFact(EvidenceFact.Subject.PROVIDER, PROVIDER, "unproven_manual_rules", EvidenceFact.Value.text(diagnostics.toString()),
                PROVIDER, EvidenceFact.Origin.OBSERVED, 1, 0, ProgressionBand.ENTRY, List.of(), "Bounded native rule census; exclusions remain unproven, never absence"));
        if (installed.isEmpty()) sink.warn(PROVIDER + ": no supported manual chain; loaded rules=" + rules.size()
                + ", barrels=" + barrels.size() + ", sieves=" + sieves.size()
                + "; independently renewable inputs, native modifier gates and joint station access remain required");
        return Collections.unmodifiableMap(result);
    }
    private static List<Rule> rules(PackEvidenceContext context, EvidenceSink sink) {
        var rules = new ArrayList<Rule>();
        for (var holder : context.inputs().recipes()) {
            Object recipe = holder.value(); String type = recipe.getClass().getName();
            String kind = type.equals(API + "recipe.barrel.BarrelCompostRecipe") ? "compost"
                    : type.equals(API + "recipe.sieve.SieveRecipe") ? "sieve" : type.equals(API + "recipe.hammer.HammerRecipe") ? "hammer" : "";
            if (kind.isEmpty()) continue;
            var attempt = OptionalIntegration.attempt(PROVIDER, holder.id().toString(), () -> {
                var inputs = simple((Ingredient)field(recipe, "ingredient"), context);
                if (inputs.isEmpty()) return Optional.<Rule>empty();
                int volume = kind.equals("compost") ? ((Number)call(recipe, "getVolume")).intValue() : 0;
                if (kind.equals("compost") && (volume <= 0 || volume > 1000)) return Optional.<Rule>empty();
                var output = kind.equals("compost") ? Items.DIRT.getDefaultInstance() : (ItemStack)field(recipe, "result");
                if (output.isEmpty() || !output.getComponents().equals(output.getItem().getDefaultInstance().getComponents())) return Optional.<Rule>empty();
                Yield yield = kind.equals("compost") ? new Yield(1, 1) : nativeYield((NumberProvider)field(recipe, "resultAmount"));
                if (yield == null) return Optional.<Rule>empty();
                var meshes = kind.equals("sieve") ? simple((Ingredient)field(recipe, "mesh"), context) : List.<ItemStack>of();
                return Optional.of(new Rule(holder.id().toString(), kind, recipe, inputs, meshes, output.copy(), yield, volume));
            });
            if (!attempt.succeeded()) sink.warn(PROVIDER + " recipe READ_FAILED " + holder.id() + ": " + attempt.failure());
            else attempt.value().orElseThrow().ifPresent(rules::add);
        }
        rules.sort(Comparator.comparing(Rule::id)); return List.copyOf(rules);
    }
    private static List<ItemStack> simple(Ingredient ingredient, PackEvidenceContext context) {
        return simple(ingredient, context, 0);
    }
    private static List<ItemStack> simple(Ingredient ingredient, PackEvidenceContext context, int depth) {
        if (depth >= 16 || ingredient.getClass() != Ingredient.class) return List.of();
        Object custom = call(ingredient, "getCustomIngredient");
        if (custom != null) {
            // NeoForge represents JSON OR-arrays as CompoundIngredient. Its audited
            // predicate is any child; a supported child is a valid narrower witness.
            if (!Set.of("21.1.248", "21.1.250", "21.1.251").contains(context.inputs().installedVersion("neoforge"))
                    || !custom.getClass().getName().equals("net.neoforged.neoforge.common.crafting.CompoundIngredient")) return List.of();
            var choices = new TreeMap<String, ItemStack>();
            for (var child : (List<?>)call(custom, "children"))
                for (var item : simple((Ingredient)child, context, depth + 1)) choices.put(id(item), item);
            return List.copyOf(choices.values());
        }
        var encoded = Ingredient.CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE, context.server().registryAccess()), ingredient).getOrThrow();
        if (!simpleDefinition(encoded)) return List.of();
        return Arrays.stream(ingredient.getItems()).filter(s -> !s.isEmpty())
                .map(s -> s.getItem().getDefaultInstance()).sorted(Comparator.comparing(ExDeorumManualAccess::id)).toList();
    }
    static boolean simpleDefinition(JsonElement element) {
        if (element.isJsonArray()) return !element.getAsJsonArray().isEmpty() && element.getAsJsonArray().asList().stream().allMatch(ExDeorumManualAccess::simpleDefinition);
        if (!element.isJsonObject()) return false;
        var object = element.getAsJsonObject();
        return object.size() == 1 && (object.has("item") || object.has("tag")) && object.entrySet().iterator().next().getValue().isJsonPrimitive();
    }
    private static Yield nativeYield(NumberProvider provider) {
        if (provider.getClass() != ConstantValue.class && provider.getClass() != UniformGenerator.class && provider.getClass() != BinomialDistributionGenerator.class) return null;
        return numberYield(NumberProviders.CODEC.encodeStart(JsonOps.INSTANCE, provider).getOrThrow());
    }
    static Yield numberYield(JsonElement value) {
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
            double count = Math.floor(value.getAsDouble()); return count > 0 && count <= 256 ? new Yield(1, count) : null;
        }
        if (!value.isJsonObject()) return null;
        var object = value.getAsJsonObject(); String type = object.has("type") ? object.get("type").getAsString() : "";
        if (type.equals("minecraft:constant")) return numberYield(object.get("value"));
        if (type.equals("minecraft:binomial") && numeric(object, "n") && numeric(object, "p")) {
            double n = Math.floor(object.get("n").getAsDouble()), p = object.get("p").getAsDouble();
            return n > 0 && n <= 256 && p > 0 && p <= 1 ? new Yield(1 - Math.pow(1 - p, n), n * p) : null;
        }
        if (type.equals("minecraft:uniform") && numeric(object, "min") && numeric(object, "max")) {
            double min = Math.floor(object.get("min").getAsDouble()), max = Math.floor(object.get("max").getAsDouble());
            if (min < 0 || max < min || max > 256 || max <= 0) return null;
            return new Yield(min > 0 ? 1 : max / (max + 1), (min + max) / 2);
        }
        return null;
    }
    private static boolean numeric(JsonObject o, String k) { return o.has(k) && o.get(k).isJsonPrimitive() && o.getAsJsonPrimitive(k).isNumber(); }
    private static void crookCultivation(PackEvidenceContext context, EvidenceSink sink, Map<String, ResourceEvidence> result,
            ConfiguredRecipeAccess access, Object caches, Map<String, ConfigurationAccess.Proof> renewable, Set<String> installed) {
        var crooks = context.inputs().items().stream().filter(i -> i.getClass().getName().equals(API + "item.CrookItem"))
                .map(Item::getDefaultInstance).filter(s -> s.isDamageableItem() && s.getMaxDamage() > 2 && !s.isEnchanted()).toList();
        var states = new LinkedHashMap<BlockState, ConfigurationAccess.Proof>();
        for (var fact : List.copyOf(sink.facts())) {
            if (fact.subject() != EvidenceFact.Subject.BLOCK || !fact.property().equals("native_renewable_canopy")
                    || !fact.provider().equals("native_tree_renewal") || fact.origin() != EvidenceFact.Origin.OBSERVED) continue;
            var block = BuiltInRegistries.BLOCK.getOptional(net.minecraft.resources.ResourceLocation.parse(fact.subjectId())).orElse(null);
            if (block == null || !block.defaultBlockState().is(net.minecraft.tags.BlockTags.LEAVES)) continue;
            var seed = renewable.computeIfAbsent(fact.value().text(), access::requireItem);
            if (seed.placement().reachable()) states.put(block.defaultBlockState(), seed);
        }
        if (states.isEmpty()) return;
        // SilkwormItem 3.12 consumes one worm to replace a native leaves block.
        // Its native ticker advances 80/16000 per tick to fully_infested. We pay
        // one independent worm per leaf; spreading and replanting gains are unused.
        var worms = context.inputs().items().stream().filter(i -> i.getClass().getName().equals(API + "item.SilkwormItem")).toList();
        var infestation = BuiltInRegistries.BLOCK.getOptional(net.minecraft.resources.ResourceLocation.parse("exdeorum:infested_leaves")).orElse(null);
        if (infestation != null && infestation.getClass().getName().equals(API + "block.InfestedLeavesBlock")) {
            var mature = infestation.getStateDefinition().getProperty("fully_infested");
            if (mature instanceof net.minecraft.world.level.block.state.properties.BooleanProperty property) {
                for (var worm : worms) {
                    var supply = renewable.computeIfAbsent(BuiltInRegistries.ITEM.getKey(worm).toString(), access::requireItem);
                    if (supply.placement().reachable()) {
                        var canopy = states.values().iterator().next();
                        states.put(infestation.defaultBlockState().setValue(property, true), ConfiguredRecipeAccess.combine(List.of(canopy, supply),
                                "Consume one independently renewable silkworm and one surplus-seed canopy leaf; allow 200 loaded native ticks; no spread bonus", .9));
                        break;
                    }
                }
            }
        }
        var identities = new IdentityHashMap<Object, String>();
        for (var holder : context.inputs().recipes()) if (holder.value().getClass().getName().equals(API + "recipe.crook.CrookRecipe"))
            identities.put(holder.value(), holder.id().toString());
        for (var entry : states.entrySet()) {
            var recipes = (List<?>)invoke(caches, "getCrookRecipes", new Class<?>[]{BlockState.class}, entry.getKey());
            for (var recipe : recipes) {
                String name = identities.get(recipe); if (name == null || installed.contains(name)) continue;
                Object predicate = call(recipe, "blockPredicate");
                if (!Set.of(API + "recipe.BlockPredicate$SingleBlockPredicate", API + "recipe.BlockPredicate$BlockStatePredicate",
                        API + "recipe.BlockPredicate$TagPredicate").contains(predicate.getClass().getName())) continue;
                float chance = ((Number)call(recipe, "chance")).floatValue();
                var output = (ItemStack)call(recipe, "result");
                if (!(chance > 0 && chance <= 1) || output.isEmpty() || !output.getComponents().equals(output.getItem().getDefaultInstance().getComponents())) continue;
                for (var crook : crooks) {
                    if (!modifierBlockers(context, "CrookLootModifier", entry.getKey(), crook, id(output)).isEmpty()) continue;
                    var tool = renewable.computeIfAbsent("exact:" + id(crook), ignored -> access.requireStack(stack(crook, context))); if (!tool.placement().reachable()) continue;
                    var proof = ConfiguredRecipeAccess.combine(List.of(entry.getValue(), tool), name, .9);
                    if (publish(result, sink, id(output), name, new Yield(chance, chance * output.getCount()), proof,
                            "Manual crook harvest of " + entry.getKey() + "; fresh unenchanted crook paid per harvest, using independently renewable surplus-seed canopies; loaded cache/predicate and global modifier; extra rerolled drops excluded")) installed.add(name);
                    break;
                }
            }
        }
    }
    private static List<ItemStack> blocks(PackEvidenceContext context, String type) {
        return context.inputs().items().stream().filter(i -> i instanceof BlockItem b && b.getBlock().getClass().getName().equals(type))
                .map(Item::getDefaultInstance).sorted(Comparator.comparing(ExDeorumManualAccess::id)).toList();
    }
    private static ConfigurationAccess.Proof firstSetup(List<ItemStack> stations, ConfiguredRecipeAccess access, Map<String, ConfigurationAccess.Proof> cache) {
        for (var station : stations) {
            var proof = cache.computeIfAbsent(id(station), ignored -> access.requireFinite(List.of(NativeConsumables.request(id(station), 1))).access());
            if (proof.placement().reachable()) return proof;
        }
        return null;
    }
    private static List<String> modifierBlockers(PackEvidenceContext context, String suffix, BlockState state, ItemStack tool, String output) {
        var properties = new TreeMap<String, String>(); state.getValues().forEach((p,v) -> properties.put(p.getName(), v.toString()));
        var tags = tool.getTags().map(t -> t.location().toString()).collect(java.util.stream.Collectors.toSet());
        var scenario = new ProceduralBlockLoot.Context(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(), properties, properties.keySet(), id(tool), tags, Map.of()).withToolFacts(NativeToolFacts.capture(context.inputs(), tool));
        String implementation = API + "loot." + suffix;
        boolean active = context.inputs().runtimeLootModifiers().stream().anyMatch(m -> m.implementation().equals(implementation)
                && m.conditionsEnforced() && ProceduralBlockLoot.conditionsProven(ProceduralBlockHarvest.plain(m.definition().get("conditions")), scenario));
        if (!active) return List.of("Required native modifier or its exact tool predicate is unproven: " + implementation);
        return context.inputs().nativeBlockLootAudit()
                .applicable(state.getBlock().getLootTable().location().toString(), scenario, Set.of(output)).stream()
                .filter(m -> !m.implementation().equals(implementation)).filter(m -> BlockLootModifierAudit.couldChangeOutput(m, output))
                .map(RuntimeLootAudit.Modifier::implementation).distinct().sorted().toList();
    }
    private static boolean publish(Map<String, ResourceEvidence> result, EvidenceSink sink, String item, String recipe, Yield yield,
            ConfigurationAccess.Proof proof, String operation) {
        var resource = result.get(item); if (resource == null || !resource.external()) return false;
        for (String property : List.of("renewable", "attainable")) {
            var override = sink.get(EvidenceFact.Subject.ITEM, item, property);
            if (override != null && override.origin() == EvidenceFact.Origin.OVERRIDE && !override.value().flag()) return false;
        }
        String sourceId = PROVIDER + ":" + recipe;
        if (resource.sources().stream().anyMatch(s -> s.id().equals(sourceId))) return true;
        var stage = proof.placement().stage(); var override = sink.get(EvidenceFact.Subject.ITEM, item, "stage");
        if (override != null && override.origin() == EvidenceFact.Origin.OVERRIDE) stage = resource.stage();
        var conditions = List.of(operation, "Actual loaded recipe " + recipe + "; positive output probability=" + yield.chance()
                + "; expected output=" + yield.expected() + "; independently renewable consumed inputs and tools; finite station setup proved; conditional stochastic supply, never guaranteed finite stock or a measured rate");
        var off = new SourceAvailability.Timer(SourceAvailability.Applicability.OFF, 0, List.of());
        var availability = new SourceAvailability(sourceId, SourceAvailability.Category.CONDITIONAL_RENEWABLE, SourceAvailability.Scope.SHARED,
                List.of(), List.of(), conditions, off, off, true, yield.chance(), yield.expected(), List.of());
        var source = new AcquisitionSource(sourceId, AcquisitionSource.Kind.PLAYER_ACTION, stage, yield.expected(), true, false, 0,
                proof.placement().confidence(), proof.selected(), String.join("; ", conditions), availability);
        var sources = new ArrayList<>(resource.sources()); sources.add(source);
        result.put(item, new ResourceEvidence(item, resource.reachable() && resource.stage().ordinal() < stage.ordinal() ? resource.stage() : stage,
                Availability.RENEWABLE_MANUAL, Automation.PLAYER_GATED, true, true, resource.economicValue(), Math.max(resource.confidence(), source.confidence()), sources, resource.warnings()));
        sink.add(new EvidenceFact(EvidenceFact.Subject.SOURCE, sourceId, "conditional_renewal", EvidenceFact.Value.text(BalanceDocument.GSON.toJson(source)),
                PROVIDER, EvidenceFact.Origin.OBSERVED, source.confidence(), 0, stage, proof.selected(), source.reason()));
        return true;
    }
    private static JsonObject stack(ItemStack stack, PackEvidenceContext context) {
        return ItemStack.CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE, context.server().registryAccess()), stack).getOrThrow().getAsJsonObject();
    }
    private static String id(ItemStack stack) { return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(); }
    private static Number config(String name) {
        Object config = staticField(API + "config.EConfig", "SERVER"); return (Number)call(field(config, name), "get");
    }
    private static Object call(Object target, String name) { return invoke(target, name, new Class<?>[]{}); }
    private static Object invoke(Object target, String name, Class<?>[] types, Object... args) {
        try { return target.getClass().getMethod(name, types).invoke(target, args); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException("Audited manual API changed: " + name, e); }
    }
    private static Object field(Object target, String name) {
        try { return target.getClass().getField(name).get(target); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException("Audited manual field changed: " + name, e); }
    }
    private static Object staticField(String type, String name) {
        try { return Class.forName(type).getField(name).get(null); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException("Audited manual configuration changed", e); }
    }
    private static Object invokeStatic(String type, String name, Class<?>[] types, Object... args) {
        try { return Class.forName(type).getMethod(name, types).invoke(null, args); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException("Audited manual cache changed", e); }
    }
}
