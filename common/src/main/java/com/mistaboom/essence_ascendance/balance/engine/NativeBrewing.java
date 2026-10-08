package com.mistaboom.essence_ascendance.balance.engine;

import com.google.gson.JsonObject;
import com.mistaboom.essence_ascendance.balance.generated.BalanceDocument;
import com.mistaboom.essence_ascendance.mixin.PotionBrewingAccess;
import com.mistaboom.essence_ascendance.mixin.PotionMixAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.*;
import java.util.*;
import java.util.function.Function;

/** Effective native drinkable-potion chains. A bounded joint bill accounts for the
 * bottle, station, each transformation ingredient and whole blaze-powder fuel charges. */
public final class NativeBrewing {
    private static final String PROVIDER = "native_brewing";
    private static final String WATER = "minecraft:water";
    public record Mix(String from, String to, List<String> ingredients) {
        public Mix { ingredients = ingredients.stream().distinct().sorted().toList(); }
    }
    private NativeBrewing() { }

    public static void collect(PackEvidenceContext context, ConfiguredRecipeAccess access, CapabilitySink sink) {
        var attempt = OptionalIntegration.attempt(PROVIDER, "effective native brewing recipes", () -> {
            var staged = new CapabilitySink(); var mixes = new ArrayList<Mix>();
            var seen = new HashSet<String>(); var opaqueInputs = new HashSet<String>();
            for (var value : ((PotionBrewingAccess)(Object)context.server().potionBrewing()).essenceAscendance$potionMixes()) {
                var mix = (PotionMixAccess)value;
                String from = mix.essenceAscendance$from().unwrapKey().orElseThrow().location().toString();
                String to = mix.essenceAscendance$to().unwrapKey().orElseThrow().location().toString();
                var ingredient = mix.essenceAscendance$ingredient();
                if (ingredient.getClass() != Ingredient.class) {
                    opaqueInputs.add(from);
                    staged.candidate(to, PROVIDER, "Custom brewing ingredient predicate excluded", Set.of(), CapabilitySink.Reason.UNKNOWN_BEHAVIOR); continue;
                }
                // Native mixing picks the first matching entry. Later duplicate inputs cannot establish another output.
                var choices = Arrays.stream(ingredient.getItems()).map(s -> BuiltInRegistries.ITEM.getKey(s.getItem()).toString())
                        .filter(item -> seen.add(from + "/" + item)).toList();
                mixes.add(new Mix(from, to, choices));
            }
            // An opaque predicate could shadow a known route; exclude the affected input potion conservatively.
            mixes.removeIf(m -> opaqueInputs.contains(m.from()) || m.ingredients().isEmpty());
            mixes.sort(Comparator.comparing(Mix::to).thenComparing(Mix::from).thenComparing(m -> m.ingredients().toString()));
            boolean water = surfaceWater(context.server().overworld().getChunkSource().getGenerator());
            var waterProof = water ? new ConfigurationAccess.Proof(new CompetitiveCapabilities.Placement(ProgressionBand.ENTRY, true, .8, List.of()),
                    List.of("Effective Overworld native terrain has source water; bottle filling does not consume the water block"), List.of())
                    : unknown("No supported Overworld source-water terrain; water bottles need a separate access adapter");
            staged.definition(PROVIDER, "effective_mixes", BalanceDocument.GSON.toJsonTree(mixes));
            staged.definition(PROVIDER, "water_setup", BalanceDocument.GSON.toJsonTree(waterProof));
            var targets = new TreeSet<String>(); mixes.forEach(m -> targets.add(m.to()));
            for (var id : targets) {
                var target = OptionalIntegration.attempt(PROVIDER, id, () -> {
                    var result = new CapabilitySink();
                    var holder = BuiltInRegistries.POTION.getHolder(net.minecraft.resources.ResourceLocation.parse(id)).orElseThrow();
                    var proof = solve(id, mixes, bill -> access.requireExploration(bill).access(), waterProof);
                    result.definition(PROVIDER, id + "/access", BalanceDocument.GSON.toJsonTree(proof));
                    NativeConsumables.readStack(PotionContents.createItemStack(Items.POTION, holder), "minecraft:potion", id,
                            proof.placement(), result);
                    if (!proof.placement().reachable()) result.candidate(id, PROVIDER, String.join("; ", proof.unknown()),
                            Set.of(), CapabilitySink.Reason.ACCESS_UNPROVEN);
                    return result;
                });
                target.value().ifPresentOrElse(staged::merge, () -> staged.candidate(id, PROVIDER, target.failure(),
                        Set.of(), CapabilitySink.Reason.READ_FAILED));
            }
            return staged;
        });
        attempt.value().ifPresentOrElse(sink::merge, () -> sink.candidate(PROVIDER, PROVIDER,
                attempt.failure(), Set.of(), CapabilitySink.Reason.READ_FAILED));
    }

    /** Positive native terrain evidence, not a water-bucket loot guess. Custom/void
     * generators are not assumed to have water merely because the water block is registered. */
    public static boolean surfaceWater(ChunkGenerator generator) {
        if (generator.getClass() == NoiseBasedChunkGenerator.class) {
            var settings = ((NoiseBasedChunkGenerator)generator).generatorSettings().value();
            return settings.defaultFluid().is(Blocks.WATER) && settings.defaultFluid().getFluidState().isSource()
                    && settings.seaLevel() > settings.noiseSettings().minY();
        }
        if (generator.getClass() == FlatLevelSource.class)
            return ((FlatLevelSource)generator).settings().getLayers().stream().filter(Objects::nonNull)
                    .anyMatch(s -> s.is(Blocks.WATER) && s.getFluidState().isSource());
        return false;
    }

    public static ConfigurationAccess.Proof solve(String target, List<Mix> mixes,
            Function<List<JsonObject>, ConfigurationAccess.Proof> bill, ConfigurationAccess.Proof water) {
        if (!water.placement().reachable()) return water;
        var routes = new ArrayList<List<String>>(); int[] work = {0};
        paths(target, mixes, new HashSet<>(), new ArrayList<>(), routes, work);
        if (work[0] > 4096) return unknown("Brewing graph exceeds bounded search; no optimal access claim");
        ConfigurationAccess.Proof best = null;
        var failures = new TreeSet<String>();
        for (var route : routes) {
            var counts = new TreeMap<String, Integer>();
            counts.put("minecraft:glass_bottle", 1);
            if (!route.isEmpty()) {
                counts.put("minecraft:brewing_stand", 1);
                counts.put("minecraft:blaze_powder", (route.size() + 19) / 20);
            }
            route.forEach(id -> counts.merge(id, 1, Integer::sum));
            var request = counts.entrySet().stream().map(e -> NativeConsumables.request(e.getKey(), e.getValue())).toList();
            var acquired = bill.apply(request);
            var proof = ConfiguredRecipeAccess.combine(List.of(water, acquired),
                    "Native drinkable brewing; steps=" + route.size() + "; 20 seconds per step; reusable stand; one bottle; ingredients=" + route,
                    .9);
            if (!proof.placement().reachable()) { failures.addAll(proof.unknown()); continue; }
            if (best == null || proof.placement().stage().ordinal() < best.placement().stage().ordinal()
                    || proof.placement().stage() == best.placement().stage() && proof.placement().confidence() > best.placement().confidence()) best = proof;
        }
        return best != null ? best : unknown(routes.isEmpty() ? "No native water-to-potion path"
                : "No complete joint brewing bill: " + failures.stream().limit(8).toList());
    }

    private static void paths(String target, List<Mix> mixes, Set<String> path, List<String> ingredients,
            List<List<String>> routes, int[] work) {
        if (++work[0] > 4096) return;
        if (target.equals(WATER)) { routes.add(List.copyOf(ingredients)); return; }
        if (!path.add(target)) return;
        for (var mix : mixes) if (mix.to().equals(target)) for (var item : mix.ingredients()) {
            ingredients.add(item); paths(mix.from(), mixes, path, ingredients, routes, work); ingredients.removeLast();
            if (work[0] > 4096) break;
        }
        path.remove(target);
    }
    private static ConfigurationAccess.Proof unknown(String reason) {
        return new ConfigurationAccess.Proof(new CompetitiveCapabilities.Placement(ProgressionBand.APEX, false, 0, List.of()), List.of(), List.of(reason));
    }
}
