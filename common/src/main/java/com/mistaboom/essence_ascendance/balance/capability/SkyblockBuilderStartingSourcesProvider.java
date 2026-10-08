package com.mistaboom.essence_ascendance.balance.capability;

import com.mistaboom.essence_ascendance.balance.config.BalanceSettings;
import com.mistaboom.essence_ascendance.balance.engine.*;
import com.mistaboom.essence_ascendance.balance.generated.BalanceDocument;
import com.mistaboom.essence_ascendance.valuation.GenerationDataSnapshot;
import com.mistaboom.essence_ascendance.valuation.StartingBlockDrops;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.function.Function;

/** Optional loaded starter-template translator. Exclusive alternatives are intersected, never unioned. */
public final class SkyblockBuilderStartingSourcesProvider implements PackEvidenceProvider {
    private static final String PREFIX = "de.melanx.skyblockbuilder.";
    public String id() { return "skyblockbuilder_starting_sources"; }
    public List<String> dependencyModIds() { return List.of("skyblockbuilder"); }
    public boolean requiredForGeneration() { return false; }
    public ProviderReadiness readiness(GenerationDataSnapshot inputs) {
        return InstalledCapabilityProviders.version(inputs.installedVersion("skyblockbuilder"), "21.1.36",
                inputs.installedVersion("neoforge") != null,
                "Common finite resources from all loaded selectable template/palette alternatives supported; custom blocks, container loot, entities, spreads and renewable cultivation unresolved");
    }
    public void collect(PackEvidenceContext context, EvidenceSink sink) { }
    public void beforeAcquisition(GenerationDataSnapshot inputs, BalanceSettings settings, EvidenceSink sink) {
        String generator = inputs.server().overworld().getChunkSource().getGenerator().getClass().getName();
        if (!supportsGenerator(generator)) {
            sink.warn(id() + ": installed templates do not prove starting access under active generator " + generator);
            return;
        }
        if (!defaultTeamCreationAllowed()) {
            sink.warn(id() + ": default players cannot create a starting team; template contents grant no initial access witness");
            return;
        }
        List<?> loaded = (List<?>) invokeStatic(PREFIX + "template.TemplateLoader", "getConfiguredTemplates");
        if (loaded.isEmpty() || loaded.size() > 128) throw new IllegalStateException("Starting templates unavailable or exceed bounded capture");
        Map<BlockState, Map<String,Double>> drops = new HashMap<>();
        Function<BlockState, Map<String,Double>> proof = state -> drops.computeIfAbsent(state,
                key -> StartingBlockDrops.guaranteedSingleHandDrops(inputs, key));
        List<Bundle> alternatives = new ArrayList<>();
        List<Map<String, Double>> seedAlternatives = new ArrayList<>();
        Map<BlockState, Map<String, StartingBlockDrops.ExpectedDrop>> seedDrops = new HashMap<>();
        int failed = 0;
        for (Object configured : loaded) {
            String name = (String) invoke(configured, "getName");
            var captured = OptionalIntegration.attempt(id(), "template " + name, () -> {
                StructureTemplate template = (StructureTemplate) invoke(configured, "getTemplate");
                var nbt = template.save(new CompoundTag());
                var stocks = captureTemplate(name, nbt, proof);
                var seeds = captureSeedOpportunities(name, nbt, state -> seedDrops.computeIfAbsent(state,
                        key -> StartingBlockDrops.supportedHandDrops(inputs, key)));
                return Map.entry(stocks, seeds);
            });
            if (captured.succeeded()) { alternatives.addAll(captured.value().orElseThrow().getKey()); seedAlternatives.addAll(captured.value().orElseThrow().getValue()); }
            else { alternatives.add(new Bundle(name + "/unresolved", Map.of(), 1)); seedAlternatives.add(Map.of()); failed++; }
        }
        List<ItemStack> starterItems = new ArrayList<>();
        for (Object pair : (List<?>) invokeStatic(PREFIX + "config.StartingInventory", "getStarterItems"))
            starterItems.add(((ItemStack) invoke(pair, "getRight")).copy());
        Map<String,Double> literal = literalStarterItems(starterItems);
        Map<String,Double> common = commonMinimum(alternatives, literal);
        var seedBundles = seedAlternatives.stream().map(seeds -> new Bundle("conditional seed opportunity", seeds, 0)).toList();
        commonMinimum(seedBundles, Map.of()).forEach((seed, chance) -> {
            fact(sink, EvidenceFact.Subject.ITEM, seed, "native_initial_seed_chance", EvidenceFact.Value.number(chance),
                    "Each selectable starter/palette contains a native hand-harvest block with at least this positive sapling-drop probability. Reserve ONE seed conditional on a successful harvest; no guaranteed stock, repeats, additional seeds or rate.");
        });
        fact(sink, EvidenceFact.Subject.PROVIDER, id(), "initial_bundles", EvidenceFact.Value.text(BalanceDocument.GSON.toJson(alternatives)),
                "Exclusive loaded alternatives retained; common minima alone establish shared start access");
        fact(sink, EvidenceFact.Subject.PROVIDER, id(), "starter_inventory", EvidenceFact.Value.text(BalanceDocument.GSON.toJson(literal)),
                "Literal configured starter stacks; components and storage contents do not become free production");
        for (var entry : common.entrySet()) {
            String reason = "Common finite initial item across every loaded starter template/palette; native empty-hand drop proof or literal starter stack; minimum "
                    + entry.getValue() + "; no renewable supply, time rate or economic value override";
            fact(sink, EvidenceFact.Subject.ITEM, entry.getKey(), "attainable", EvidenceFact.Value.flag(true), reason);
            fact(sink, EvidenceFact.Subject.ITEM, entry.getKey(), "stage", EvidenceFact.Value.text("ENTRY"), reason);
            // Source-local finite classification preserves separately proven renewable routes.
            fact(sink, EvidenceFact.Subject.ITEM, entry.getKey(), "initial_source", EvidenceFact.Value.text(id() + ":common_start"), reason);
            fact(sink, EvidenceFact.Subject.ITEM, entry.getKey(), "initial_count", EvidenceFact.Value.number(entry.getValue()), reason);
            fact(sink, EvidenceFact.Subject.ITEM, entry.getKey(), "initial_scope", EvidenceFact.Value.text("TEAM"), reason);
        }
        int excluded = alternatives.stream().mapToInt(Bundle::unresolvedBlocks).sum();
        if (excluded > 0 || failed > 0) sink.warn(id() + ": " + excluded + " custom/container/unproved blocks and " + failed
                + " failed templates excluded; finite initial guarantees contain no callback-generated contents, renewable trees or rates");
    }

    public record Bundle(String identity, Map<String,Double> items, int unresolvedBlocks) {
        public Bundle { items = Collections.unmodifiableMap(new TreeMap<>(items)); }
    }
    /** Reuse the strict native template walk. Only one positive seed event is needed;
     * a lower bound across witnessed blocks avoids inventing independent rolls or quantity. */
    public static List<Map<String, Double>> captureSeedOpportunities(String name, CompoundTag template,
            Function<BlockState, Map<String, StartingBlockDrops.ExpectedDrop>> harvest) {
        // captureTemplate adds per-block amounts. Encode presence first, then retain
        // the minimum positive chance of qualifying blocks as a conservative witness.
        var chances = new TreeMap<String, Double>();
        var bundles = captureTemplate(name, template, state -> {
            var present = new TreeMap<String, Double>();
            harvest.apply(state).forEach((id, drop) -> {
                var item = BuiltInRegistries.ITEM.getOptional(net.minecraft.resources.ResourceLocation.parse(id)).orElse(null);
                if (item instanceof net.minecraft.world.item.BlockItem block && block.getBlock().getClass() == net.minecraft.world.level.block.SaplingBlock.class
                        && drop.chance() > 0 && drop.chance() <= 1 && drop.expectedCount() + 1e-12 >= drop.chance()) {
                    present.put(id, 1.0); chances.merge(id, drop.chance(), Math::min);
                }
            });
            return present;
        });
        return bundles.stream().map(bundle -> {
            var result = new TreeMap<String, Double>(); bundle.items().keySet().forEach(id -> result.put(id, chances.get(id)));
            return Collections.unmodifiableMap(result);
        }).toList();
    }
    public static boolean supportsGenerator(String type) {
        return (PREFIX + "world.chunkgenerators.SkyblockNoiseBasedChunkGenerator").equals(type);
    }
    public static List<Bundle> captureTemplate(String name, CompoundTag template, Function<BlockState, Map<String,Double>> drops) {
        ListTag blocks = template.getList("blocks", Tag.TAG_COMPOUND);
        if (blocks.size() > 1_000_000) throw new IllegalArgumentException("Starting structure exceeds bounded block census");
        List<ListTag> palettes = new ArrayList<>();
        if (template.contains("palettes", Tag.TAG_LIST)) {
            ListTag all = template.getList("palettes", Tag.TAG_LIST);
            if (all.size() > 32) throw new IllegalArgumentException("Starting structure exceeds bounded palette alternatives");
            for (int i = 0; i < all.size(); i++) palettes.add((ListTag) all.get(i));
        } else if (template.contains("palette", Tag.TAG_LIST)) palettes.add(template.getList("palette", Tag.TAG_COMPOUND));
        if (palettes.isEmpty()) return List.of(new Bundle(name + "/unresolved", Map.of(), blocks.size()));
        List<Bundle> result = new ArrayList<>();
        for (int paletteIndex = 0; paletteIndex < palettes.size(); paletteIndex++) {
            var palette = palettes.get(paletteIndex);
            Map<String,Double> resources = new TreeMap<>(); int unknown = 0;
            for (int i = 0; i < blocks.size(); i++) {
                CompoundTag placed = blocks.getCompound(i);
                int index = placed.getInt("state");
                if (index < 0 || index >= palette.size() || placed.contains("nbt")) { unknown++; continue; }
                CompoundTag definition = palette.getCompound(index);
                String blockId = definition.getString("Name");
                var location = net.minecraft.resources.ResourceLocation.tryParse(blockId);
                if (location == null || !BuiltInRegistries.BLOCK.containsKey(location)) { unknown++; continue; }
                BlockState state = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), definition);
                if (!isPlainNativeState(state)) { if (!state.isAir()) unknown++; continue; }
                Map<String,Double> outputs = drops.apply(state);
                if (outputs.isEmpty() && !state.isAir()) unknown++;
                outputs.forEach((item, count) -> { if (Double.isFinite(count) && count > 0) resources.merge(item, count, Double::sum); });
            }
            result.add(new Bundle(name + "/palette:" + paletteIndex, resources, unknown));
        }
        return List.copyOf(result);
    }
    public static boolean isPlainNativeState(BlockState state) {
        return state != null && !state.hasBlockEntity() && !state.requiresCorrectToolForDrops()
                && BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals("minecraft")
                && state.getBlock().getClass().getPackageName().equals("net.minecraft.world.level.block");
    }
    public static Map<String,Double> literalStarterItems(List<ItemStack> stacks) {
        Map<String,Double> items = new TreeMap<>();
        for (ItemStack stack : stacks) if (!stack.isEmpty())
            items.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), (double) stack.getCount(), Double::sum);
        return Collections.unmodifiableMap(items);
    }
    public static Map<String,Double> commonMinimum(List<Bundle> alternatives, Map<String,Double> starterItems) {
        Map<String,Double> common = new TreeMap<>();
        if (!alternatives.isEmpty()) {
            common.putAll(alternatives.getFirst().items());
            for (Bundle bundle : alternatives) common.replaceAll((item,count) -> Math.min(count, bundle.items().getOrDefault(item, 0.0)));
            common.entrySet().removeIf(entry -> !Double.isFinite(entry.getValue()) || entry.getValue() <= 0);
        }
        starterItems.forEach((item,count) -> { if (Double.isFinite(count) && count > 0) common.merge(item,count,Double::sum); });
        return Collections.unmodifiableMap(common);
    }
    private void fact(EvidenceSink sink, EvidenceFact.Subject subject, String target, String property, EvidenceFact.Value value, String reason) {
        sink.add(new EvidenceFact(subject, target, property, value, id(), EvidenceFact.Origin.OBSERVED,
                .95, 0, ProgressionBand.ENTRY, List.of(), reason));
    }
    private static Object invokeStatic(String type, String method) {
        try { return Class.forName(type).getMethod(method).invoke(null); }
        catch (ReflectiveOperationException error) { throw changed(error); }
    }
    private static boolean defaultTeamCreationAllowed() {
        try {
            var permissions = (List<?>) Class.forName(PREFIX + "config.common.PermissionsConfig").getField("permissions").get(null);
            return permissions.stream().anyMatch(value -> value instanceof Enum<?> permission && permission.name().equals("TEAM_CREATE"));
        } catch (ReflectiveOperationException error) { throw changed(error); }
    }
    private static Object invoke(Object target, String method) {
        try { return target.getClass().getMethod(method).invoke(target); }
        catch (ReflectiveOperationException error) { throw changed(error); }
    }
    private static IllegalStateException changed(ReflectiveOperationException error) {
        return new IllegalStateException("Audited optional starter-template API changed", error instanceof InvocationTargetException invocation ? invocation.getCause() : error);
    }
}
