package com.mistaboom.essence_ascendance.balance.capability;

import com.google.gson.*;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mistaboom.essence_ascendance.balance.engine.*;
import com.mistaboom.essence_ascendance.valuation.GenerationDataSnapshot;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.RegistryOps;
import java.util.*;
import static com.mistaboom.essence_ascendance.balance.engine.CapabilityEvidence.*;
import static com.mistaboom.essence_ascendance.balance.engine.CapabilityAxis.*;

/** Optional fact translators only. All class names are strings; absent mods never load their APIs. */
public final class InstalledCapabilityProviders {
    // Intended adapter domains only. These sets never assert a source, measured effect or attainable access.
    private static final Set<CapabilityAxis> MATERIAL_AXES = Set.of(MELEE_DAMAGE, MAGIC_DAMAGE, RANGED_DAMAGE,
            MINING_SPEED, DURABILITY, ARMOR, TOUGHNESS, PROJECTILE_SPEED);
    private static final Set<CapabilityAxis> AFFIX_AXES = Set.of(ARMOR, TOUGHNESS, MAX_HEALTH, MELEE_DAMAGE,
            RANGED_DAMAGE, MAGIC_DAMAGE, BURST_DAMAGE, SUSTAINED_DAMAGE, ATTACK_RATE, CRITICAL_DAMAGE,
            DROP_YIELD, REACH, MINING_SPEED, GROUND_SPEED, HEALING, REPAIR, DURABILITY, DURABILITY_REDUCTION, STATUS_RESISTANCE);
    private static final Set<CapabilityAxis> MODULE_AXES = Set.of(SHIELD_CAPACITY, EFFECTIVE_HEALTH, DAMAGE_REDUCTION,
            DAMAGE_IMMUNITY, FLIGHT, ABILITIES_FLYING_SPEED, GROUND_SPEED, AREA_MINING, MINING_SPEED, HARVEST_LEVEL,
            MELEE_DAMAGE, RANGED_DAMAGE, BURST_DAMAGE, SUSTAINED_DAMAGE, PROJECTILE_SPEED, REPAIR, INDESTRUCTIBILITY, RESOURCE_CONSUMPTION);
    private static final Set<CapabilityAxis> SPELL_AXES = Set.of(MAGIC_DAMAGE, BURST_DAMAGE, SUSTAINED_DAMAGE,
            AREA_DAMAGE, DAMAGE_OVER_TIME, HEALING, REGENERATION, RECOVERY, FLIGHT, GLIDING, TELEPORTATION,
            JUMP, VERTICAL_MOVEMENT, FALL_CONTROL, GROUND_SPEED, MINING_SPEED, AREA_MINING, VEIN_MINING,
            AUTOMATED_EXTRACTION, AUTOMATED_FARMING, CROP_ACCELERATION, TREE_ACCELERATION, ANIMAL_ACCELERATION,
            AUTOMATION_INTERACTION, INFORMATION, CONVENIENCE, RESOURCE_CONSUMPTION, INVENTORY);
    private InstalledCapabilityProviders() { }
    public static List<PackEvidenceProvider> all() { return List.of(new Torchmaster(), new ComponentRemoval(), new Materials(), new Planters(),
            new SquatGrowCapabilityProvider(), new IronJetpackCapabilityProvider(), new TimeBottleCapabilityProvider(),
            new UnresolvedSystem("apotheosis", "8.8.0", "Affixes/gems/socket rarity/loot-tier configuration needs an attainable composition provider", AFFIX_AXES),
            new UnresolvedSystem("draconicevolution", "3.1.4.633", "Modules/shield/energy/host-grid composition and configured tiers are not normalized", MODULE_AXES),
            new UnresolvedSystem("ars_nouveau", "5.13.1", "Spell/glyph composition, source costs and automation require an attainable configuration provider", SPELL_AXES)); }
    public static ProviderReadiness version(String installed, String audited, boolean supportedLoader, String detail) {
        if (installed == null) return new ProviderReadiness(ProviderReadiness.Status.ABSENT, "unknown", "Optional dependency absent");
        if (!installed.equals(audited) || !supportedLoader) return new ProviderReadiness(ProviderReadiness.Status.UNSUPPORTED, installed,
                "Audited NeoForge version " + audited + " only; " + detail);
        return new ProviderReadiness(ProviderReadiness.Status.PARTIALLY_SUPPORTED, installed, detail, true);
    }
    private abstract static class Optional implements PackEvidenceProvider {
        final String mod, audited;
        Optional(String mod, String audited) { this.mod = mod; this.audited = audited; }
        public String id() { return mod + "_capabilities"; }
        public List<String> dependencyModIds() { return List.of(mod); }
        public boolean requiredForGeneration() { return false; }
        public void collect(PackEvidenceContext context, EvidenceSink sink) { }
        public ProviderReadiness readiness(GenerationDataSnapshot inputs) { return version(inputs.installedVersion(mod), audited,
                inputs.installedVersion("neoforge") != null, "Definition facts only; full configurable mechanics remain partially supported"); }
    }
    private static Object field(String name, String field) {
        try { return Class.forName(name).getField(field).get(null); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException("Audited capability API changed: " + name + "." + field, error); }
    }
    private static Object call(Object target, String name) {
        try { return target.getClass().getMethod(name).invoke(target); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException("Audited capability API changed: " + name, error); }
    }
    private static Object instanceField(Object target, String name) {
        try { return target.getClass().getField(name).get(target); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException("Audited capability field changed: " + name, error); }
    }
    /** Audited passive planter configuration. Capture loaded facts once; never grow/harvest or execute functions.
     * Only one earliest proven seed/soil witness is required for binary relevance, not every cross product. */
    public static final class Planters extends Optional {
        private static final String PREFIX = "net.darkhax.botanypots.common.impl.";
        public Planters() { super("botanypots", "21.1.44"); }
        public Set<CapabilityAxis> capabilityAxes() { return Set.of(AUTOMATED_FARMING); }
        public void collectCapabilities(PackEvidenceContext context, Map<String, ResourceEvidence> resources, CapabilitySink sink) {
            Object gameplay = instanceField(call(field(PREFIX + "BotanyPotsMod", "CONFIG"), "get"), "gameplay");
            double globalGrowth = ((Number)instanceField(gameplay, "global_growth_modifier")).doubleValue();
            JsonObject defaultTool = (JsonObject)instanceField(gameplay, "default_harvest_stack");
            if (!Double.isFinite(globalGrowth) || !defaultTool.isEmpty()) {
                sink.candidate(mod, id(), "Nonempty configured default harvest tool or invalid growth config requires operating proof",
                        capabilityAxes(), CapabilitySink.Reason.ACCESS_UNPROVEN); return;
            }
            var access = new ConfigurationAccess.Resolver(resources, context.inputs().configurationConstrainedItems());
            Map<String, double[]> soils = new TreeMap<>();
            Map<String, Integer> soilMatches = new HashMap<>(), cropMatches = new HashMap<>();
            Class<?> cropApi = recipeApi("crop.Crop"), soilApi = recipeApi("soil.Soil");
            List<Map.Entry<String, Object>> crops = new ArrayList<>();
            for (var recipe : context.inputs().recipes()) {
                String type = recipe.value().getClass().getName();
                if (Set.of(PREFIX + "data.recipe.soil.BasicSoil", PREFIX + "data.recipe.soil.BlockDerivedSoil").contains(type)) {
                    // Invoke the inherited BasicSoil getter: BlockDerivedSoil.getBlockProperties is a different record.
                    Object properties = call(recipe.value(), "getProperties");
                    double growth = ((Number)call(properties, "growthModifier")).doubleValue();
                    double yield = ((Number)call(properties, "yieldModifier")).doubleValue();
                    var input = plainIngredient(call(properties, "input"), context);
                    if (input.isEmpty()) { sink.candidate(recipe.id().toString(), id(), "Opaque/empty soil matching may shadow a configuration",
                            capabilityAxes(), CapabilitySink.Reason.UNKNOWN_BEHAVIOR); return; }
                    for (String item : input) {
                        soilMatches.merge(item, 1, Integer::sum);
                        soils.put(item, new double[]{growth, yield});
                    }
                } else if (Set.of(PREFIX + "data.recipe.crop.BasicCrop", PREFIX + "data.recipe.crop.BlockDerivedCrop").contains(type)) {
                    Object p = call(recipe.value(), "getBasicProperties");
                    var input = plainIngredient(call(p, "input"), context);
                    if (input.isEmpty()) { sink.candidate(recipe.id().toString(), id(), "Opaque/empty crop matching may shadow a configuration",
                            capabilityAxes(), CapabilitySink.Reason.UNKNOWN_BEHAVIOR); return; }
                    for (String item : input) cropMatches.merge(item, 1, Integer::sum);
                    crops.add(Map.entry(recipe.id().toString(), p));
                } else if (cropApi.isInstance(recipe.value()) || soilApi.isInstance(recipe.value())) {
                    sink.candidate(recipe.id().toString(), id(), "Opaque crop/soil matching may shadow supported definitions; no witness certified",
                            capabilityAxes(), CapabilitySink.Reason.UNKNOWN_BEHAVIOR); return;
                }
            }
            ConfigurationAccess.Proof best = null; String bestCrop = null;
            for (var crop : crops) {
                sink.analyzed(); Object p = crop.getValue();
                if (((java.util.Optional<?>)call(p, "functionId")).isPresent() || ((java.util.Optional<?>)call(p, "potPredicate")).isPresent()) {
                    sink.candidate(crop.getKey(), id(), "Custom crop function/pot predicate requires operating proof",
                            capabilityAxes(), CapabilitySink.Reason.UNKNOWN_BEHAVIOR); continue;
                }
                int growTime = ((Number)call(p, "growTime")).intValue();
                double baseYield = ((Number)call(p, "baseYield")).doubleValue();
                double yieldScale = ((Number)call(p, "yieldScale")).doubleValue();
                List<?> drops = (List<?>)call(p, "drops");
                boolean positiveDrop = false, opaqueDrop = false;
                for (Object drop : drops) {
                    if (!drop.getClass().getName().equals(PREFIX + "data.itemdrops.SimpleDropProvider")) { opaqueDrop = true; continue; }
                    for (Object entry : (List<?>)call(drop, "drops")) {
                        var stack = (net.minecraft.world.item.ItemStack)call(entry, "drop");
                        double chance = ((Number)call(entry, "chance")).doubleValue();
                        positiveDrop |= productiveDrop(!stack.isEmpty(), chance);
                    }
                }
                if (!positiveDrop || opaqueDrop) {
                    sink.candidate(crop.getKey(), id(), "Custom/loot/empty/zero-chance crop drops require factual harvest support",
                            capabilityAxes(), CapabilitySink.Reason.UNKNOWN_BEHAVIOR); continue;
                }
                List<String> seed = plainIngredient(call(p, "input"), context).stream().filter(i -> cropMatches.getOrDefault(i, 0) == 1).toList();
                List<String> soil = plainIngredient(call(p, "soil"), context).stream().filter(i -> soilMatches.getOrDefault(i, 0) == 1)
                        .filter(i -> productiveCrop(growTime, globalGrowth, soils.get(i)[0], baseYield, yieldScale, soils.get(i)[1])).toList();
                var proof = access.require(List.of(seed, soil));
                if (!proof.placement().reachable()) continue;
                if (best == null || proof.placement().stage().ordinal() < best.placement().stage().ordinal()
                        || proof.placement().stage() == best.placement().stage() && proof.placement().confidence() > best.placement().confidence()) {
                    best = proof; bestCrop = crop.getKey();
                }
            }
            if (best == null) { sink.candidate(mod, id(), "No independently reachable supported seed/soil configuration; registry membership is not access",
                    capabilityAxes(), CapabilitySink.Reason.ACCESS_UNPROVEN); return; }
            for (var item : context.inputs().items()) {
                if (!(item instanceof net.minecraft.world.item.BlockItem blockItem)
                        || !blockItem.getBlock().getClass().getName().equals(PREFIX + "block.BotanyPotBlock")) continue;
                sink.analyzed();
                if (!(boolean)call(blockItem.getBlock(), "isHopper")) continue;
                String source = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString();
                var requirements = best.selected().stream().map(List::of).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
                requirements.add(List.of(source));
                var proof = access.require(requirements);
                sink.add(planter(source, bestCrop, proof));
            }
            sink.candidate(mod, id(), "Passive farming presence only; output probabilities, loot callbacks, yields, loaded uptime and configured cycle rate unmeasured",
                    capabilityAxes(), CapabilitySink.Reason.UNKNOWN_BEHAVIOR);
        }
        private static Class<?> recipeApi(String suffix) {
            try { return Class.forName("net.darkhax.botanypots.common.api.data.recipes." + suffix); }
            catch (ClassNotFoundException error) { throw new IllegalStateException("Audited planter recipe API changed", error); }
        }
    }
    /** Audited empty-tool/exact base pot projection; no loot or gameplay callback is invoked. */
    public static boolean productiveCrop(int ticks, double globalGrowth, double soilGrowth, double baseYield, double scale, double soilYield) {
        double growth = globalGrowth + soilGrowth, yield = baseYield + scale * soilYield;
        return ticks > 0 && Double.isFinite(growth) && growth > 0 && Double.isFinite(yield) && yield > 0;
    }
    public static boolean productiveDrop(boolean nonempty, double chance) {
        return nonempty && Double.isFinite(chance) && chance > 0 && chance <= 1;
    }
    private static List<String> plainIngredient(Object value, PackEvidenceContext context) {
        var ingredient = (net.minecraft.world.item.crafting.Ingredient)value;
        var ops = RegistryOps.create(JsonOps.INSTANCE, context.server().registryAccess());
        var definition = net.minecraft.world.item.crafting.Ingredient.CODEC.encodeStart(ops, ingredient).getOrThrow();
        if (definition.toString().contains("\"type\"")) return List.of(); // Custom matching is not literal tag membership.
        return Arrays.stream(ingredient.getItems()).filter(s -> !s.isEmpty()).map(s -> net.minecraft.core.registries.BuiltInRegistries.ITEM
                .getKey(s.getItem()).toString()).distinct().sorted().toList();
    }
    public static Functional planter(String source, String crop, ConfigurationAccess.Proof proof) {
        var unknown = new ArrayList<>(proof.unknown());
        unknown.add("Conditional peak; loaded-area uptime/loot yield/configured rate unmeasured; no material conversion or output access certified");
        var m = CompetitiveCapabilities.measurement(CapabilityAxis.AUTOMATED_FARMING, 1.0, "presence", "passive reusable seed/soil planting",
                new Scope("configured_crop", "single", null, 1.0), new Operation(Automation.PASSIVE, Activity.PASSIVE, Renewal.UNKNOWN,
                null, null, null, null, List.of("Reusable seed and soil; output handling required"), proof.selected()),
                "botanypots_capabilities", Origin.TYPED_ADAPTER, unknown);
        return new Functional(new CapabilityEvidence(source, proof.placement().stage(), Map.of(), proof.placement().reachable(),
                Math.min(.85, proof.placement().confidence()), "Audited hopper planter with seed/soil witness " + crop),
                "crop:" + crop, List.of(m), proof.placement().acquisition(), proof.placement().reachable());
    }
    public static final class Torchmaster extends Optional {
        public Torchmaster() { super("torchmaster", "21.1.12"); }
        public Set<CapabilityAxis> capabilityAxes() { return Set.of(SPAWN_SUPPRESSION); }
        public ProviderReadiness readiness(GenerationDataSnapshot inputs) {
            var result = super.readiness(inputs);
            if (!result.collectable()) return result;
            Object spec = field("net.xalcon.torchmaster.TorchmasterNeoforgeConfig", "spec");
            if (!(boolean)call(spec, "isLoaded")) return new ProviderReadiness(ProviderReadiness.Status.NOT_READY, audited, "Effective Torchmaster configuration not loaded");
            return result;
        }
        public void collectCapabilities(PackEvidenceContext context, Map<String, ResourceEvidence> resources, CapabilitySink sink) {
            Object config = field("net.xalcon.torchmaster.TorchmasterNeoforgeConfig", "WRAPPED_CONFIG");
            // Invoke public interface methods (the implementing anonymous class itself is package-private).
            int radius, dreadRadius; boolean natural; boolean siege;
            try {
                Class<?> api = Class.forName("net.xalcon.torchmaster.config.ITorchmasterConfig");
                radius = (int)api.getMethod("getMegaTorchRadius").invoke(config);
                dreadRadius = (int)api.getMethod("getDreadLampRadius").invoke(config);
                natural = (boolean)api.getMethod("getBlockOnlyNaturalSpawns").invoke(config);
                siege = (boolean)api.getMethod("getBlockVillageSieges").invoke(config);
            } catch (ReflectiveOperationException error) { throw new IllegalStateException("Torchmaster configuration API", error); }
            for (String source : List.of("torchmaster:megatorch", "torchmaster:dreadlamp")) {
                sink.analyzed();
                boolean mega = source.endsWith(":megatorch");
                Object filter = field("net.xalcon.torchmaster.Torchmaster", mega ? "MegaTorchFilterRegistry" : "DreadLampFilterRegistry");
                Object[] entities = (Object[])call(filter, "getEntities");
                if (entities.length == 0) {
                    sink.candidate(source, id(), "Effective affected-entity filter empty; loaded service readiness/actual suppression not proven",
                            capabilityAxes(), CapabilitySink.Reason.ACCESS_UNPROVEN);
                    continue;
                }
                var placement = CompetitiveCapabilities.placement(resources, source);
                List<String> entityIds = Arrays.stream(entities).map(Object::toString).sorted().toList();
                Map<String, net.minecraft.world.entity.MobCategory> categories = new TreeMap<>();
                for (String entityId : entityIds) {
                    var key = net.minecraft.resources.ResourceLocation.tryParse(entityId);
                    if (key != null) net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getOptional(key)
                            .ifPresent(entity -> categories.put(entityId, entity.getCategory()));
                }
                sink.add(blockingLight(source, mega ? radius : dreadRadius, natural, mega && siege,
                        entityIds, categories, placement));
            }
        }
    }
    /** Pure adapter projection also used by synthetic/offline verification. */
    public static Functional torch(int radius, boolean natural, boolean siege, List<String> entityTypes, CompetitiveCapabilities.Placement placement) {
        return blockingLight("torchmaster:megatorch", radius, natural, siege, entityTypes, placement);
    }
    public static Functional blockingLight(String source, int radius, boolean natural, boolean siege, List<String> entityTypes,
                                           CompetitiveCapabilities.Placement placement) {
        return blockingLight(source, radius, natural, siege, entityTypes, null, placement);
    }
    /** Category facts are supplied by the loaded registry; unknown filters do not imply hostile coverage.
     * Null retains legacy/general synthetic declarations that supplied no category contract. */
    public static Functional blockingLight(String source, int radius, boolean natural, boolean siege, List<String> entityTypes,
                                           Map<String, net.minecraft.world.entity.MobCategory> categories, CompetitiveCapabilities.Placement placement) {
        if (radius < 1 || entityTypes.isEmpty()) throw new IllegalArgumentException("Blocking light requires a positive radius and affected entity filter");
        String affected = "";
        var unknown = new ArrayList<String>();
        unknown.add("Other spawn/event hooks may override; loaded-area uptime unmeasured");
        if (categories != null) {
            boolean monster = entityTypes.stream().anyMatch(id -> categories.get(id) == net.minecraft.world.entity.MobCategory.MONSTER);
            boolean complete = entityTypes.stream().allMatch(categories::containsKey);
            affected = "; affected_monsters=" + (monster ? "true" : complete ? "false" : "unknown");
            if (!complete) unknown.add("Some affected entity categories unresolved; no unproven general hostile coverage certified");
        }
        var operation = new Operation(Automation.PASSIVE, Activity.PASSIVE, Renewal.RENEWABLE, null, null, null, null,
                List.of("No consumable cost in audited blocking logic"), List.of("Place " + source + "; registered loaded-area blocking light"));
        var m = CompetitiveCapabilities.measurement(CapabilityAxis.SPAWN_SUPPRESSION, (double)radius, "cube_radius_blocks",
                "registered entity filter=" + entityTypes + affected + "; natural_only=" + natural + "; village_sieges=" + siege,
                new Scope("filtered_spawn_attempts", "cube", (double)radius, null), operation, "torchmaster_capabilities", Origin.TYPED_ADAPTER,
                unknown);
        return new Functional(new CapabilityEvidence(source, placement.stage(), Map.of(), placement.reachable(), Math.min(.9, placement.confidence()),
                "Torchmaster 21.1.12 effective config/filter; audited Cubic distance and spawn hooks"), "effective_config", List.of(m), placement.acquisition(), placement.reachable());
    }
    public static final class ComponentRemoval extends Optional {
        public ComponentRemoval() { super("forbidden_arcanus", "2.6.1"); }
        public Set<CapabilityAxis> capabilityAxes() { return Set.of(INDESTRUCTIBILITY); }
        public void collectCapabilities(PackEvidenceContext context, Map<String, ResourceEvidence> resources, CapabilitySink sink) {
            for (var recipe : context.inputs().recipes()) {
                if (!recipe.value().getClass().getName().equals("com.stal111.forbidden_arcanus.common.item.crafting.ApplyModifierRecipe")) continue;
                sink.analyzed(); Object value = recipe.value();
                Object modifier = ((Holder<?>)call(value, "modifier")).value();
                HolderSet<?> removes = (HolderSet<?>)call(modifier, "componentsToRemove");
                if (removes.stream().noneMatch(h -> h.value() == DataComponents.MAX_DAMAGE)) continue;
                var addition = (net.minecraft.world.item.crafting.Ingredient)call(value, "addition");
                var template = (net.minecraft.world.item.crafting.Ingredient)call(value, "template");
                List<String> costs = Arrays.stream(addition.getItems()).map(stack -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()).sorted().toList();
                List<String> setup = Arrays.stream(template.getItems()).map(stack -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()).sorted().toList();
                String subject = costs.isEmpty() ? recipe.id().toString() : costs.getFirst();
                var placement = CompetitiveCapabilities.placement(resources, subject);
                var m = CompetitiveCapabilities.measurement(CapabilityAxis.INDESTRUCTIBILITY, 1.0, "presence", "eligible modifier recipe=" + recipe.id(),
                        Scope.self(), new Operation(Automation.NONE, Activity.PLAYER_ACTIVE, Renewal.UNKNOWN, null, null, null, null,
                        List.of("No native durability component after application; other wear systems unsupported"), List.of("Smithing; addition=" + costs + "; template=" + setup)),
                        id(), Origin.TYPED_ADAPTER, List.of("Eligible base/component predicate and incompatible items/enchantments not resolved; no attainable configuration claimed"));
                sink.add(new Functional(new CapabilityEvidence(subject, placement.stage(), Map.of(), placement.reachable(), .9,
                        "Effective ApplyModifierRecipe removes MAX_DAMAGE; audited ItemModifier.onApplied"), recipe.id().toString(), List.of(m), placement.acquisition(), false));
                sink.candidate(recipe.id().toString(), id(), "Durability-removal behavior measured; target compatibility/template/base acquisition remains unproven",
                        capabilityAxes(), CapabilitySink.Reason.ACCESS_UNPROVEN);
            }
        }
    }
    /** Loaded modular material definitions exceed default-stack evidence, but are not final item stats.
     * Potential component bounds stay outside attainable frontiers until a provider proves composition/grades/traits. */
    public static final class Materials extends Optional {
        public Materials() { super("silentgear", "4.2.1.1"); }
        public Set<CapabilityAxis> capabilityAxes() { return MATERIAL_AXES; }
        public ProviderReadiness readiness(GenerationDataSnapshot inputs) {
            var result = super.readiness(inputs); if (!result.collectable()) return result;
            Object manager = field("net.silentchaos512.gear.setup.SgRegistries", "MATERIAL");
            if ((boolean)call(manager, "isReloading")) return new ProviderReadiness(ProviderReadiness.Status.NOT_READY, audited, "Material manager still reloading");
            return result;
        }
        @SuppressWarnings("unchecked")
        public void collectCapabilities(PackEvidenceContext context, Map<String, ResourceEvidence> resources, CapabilitySink sink) {
            Object manager = field("net.silentchaos512.gear.setup.SgRegistries", "MATERIAL");
            Map<Object, Object> materials = (Map<Object, Object>)call(manager, "copyOfMap");
            Codec<Object> codec = (Codec<Object>)field("net.silentchaos512.gear.gear.material.MaterialSerializers", "DISPATCH_CODEC");
            var ops = RegistryOps.create(JsonOps.INSTANCE, context.server().registryAccess());
            for (var entry : materials.entrySet().stream().sorted(Comparator.comparing(e -> e.getKey().toString())).toList()) {
                sink.analyzed();
                if (!entry.getValue().getClass().getName().equals("net.silentchaos512.gear.gear.material.SimpleMaterial")) {
                    sink.candidate(entry.getKey().toString(), id(), "Compound/processed/custom material composition unsupported",
                            capabilityAxes(), CapabilitySink.Reason.UNKNOWN_BEHAVIOR); continue;
                }
                var encoded = codec.encodeStart(ops, entry.getValue()).result();
                if (encoded.isEmpty()) { sink.candidate(entry.getKey().toString(), id(), "Loaded material definition cannot encode",
                        capabilityAxes(), CapabilitySink.Reason.READ_FAILED); continue; }
                material(entry.getKey().toString(), encoded.get().getAsJsonObject(), sink);
            }
        }
    }
    public static void material(String id, JsonObject definition, CapabilitySink sink) {
        sink.definition("silentgear_capabilities", id, definition);
        JsonObject properties = definition.getAsJsonObject("properties");
        if (properties == null) { sink.candidate(id, "silentgear_capabilities", "Inherited/empty property definition needs resolved composition",
                MATERIAL_AXES, CapabilitySink.Reason.UNKNOWN_BEHAVIOR); return; }
        List<Measurement> measurements = new ArrayList<>();
        for (var part : properties.entrySet()) if (part.getValue().isJsonObject()) for (var stat : part.getValue().getAsJsonObject().entrySet()) {
            CapabilityAxis axis = switch (stat.getKey()) {
                case "attack_damage" -> CapabilityAxis.MELEE_DAMAGE; case "magic_damage" -> CapabilityAxis.MAGIC_DAMAGE;
                case "ranged_damage" -> CapabilityAxis.RANGED_DAMAGE; case "harvest_speed" -> CapabilityAxis.MINING_SPEED;
                case "durability" -> CapabilityAxis.DURABILITY; case "armor" -> CapabilityAxis.ARMOR;
                case "armor_toughness" -> CapabilityAxis.TOUGHNESS; case "projectile_speed" -> CapabilityAxis.PROJECTILE_SPEED;
                default -> null;
            };
            if (axis == null || !stat.getValue().isJsonPrimitive() || !stat.getValue().getAsJsonPrimitive().isNumber()) continue;
            double value = stat.getValue().getAsDouble(); if (!Double.isFinite(value) || value < 0) continue;
            measurements.add(CompetitiveCapabilities.measurement(axis, value, "material_component_stat", "part=" + part.getKey(), Scope.self(), Operation.manual(),
                    "silentgear_capabilities", Origin.TYPED_ADAPTER, List.of("Component value, not final gear; inheritance/mixing/grade/traits/required parts/access unresolved")));
        }
        if (!measurements.isEmpty()) sink.add(new Functional(new CapabilityEvidence(id, ProgressionBand.APEX, Map.of(), false, .85,
                "Loaded Silent Gear 4.2.1.1 material codec definition"), "material_components", measurements, List.of(), false));
        sink.candidate(id, "silentgear_capabilities", "Modular material stats captured; attainable full-gear configuration provider required",
                MATERIAL_AXES, CapabilitySink.Reason.ACCESS_UNPROVEN);
    }
    private static final class UnresolvedSystem extends Optional {
        final String detail; final Set<CapabilityAxis> axes;
        UnresolvedSystem(String mod, String audited, String detail, Set<CapabilityAxis> axes) {
            super(mod, audited); this.detail = detail; this.axes = Set.copyOf(axes);
        }
        public Set<CapabilityAxis> capabilityAxes() { return axes; }
        public void collectCapabilities(PackEvidenceContext context, Map<String, ResourceEvidence> resources, CapabilitySink sink) {
            sink.analyzed(); sink.candidate(mod, id(), detail + "; installed version=" + context.inputs().installedVersion(mod),
                    capabilityAxes(), CapabilitySink.Reason.UNKNOWN_BEHAVIOR);
        }
    }
}
