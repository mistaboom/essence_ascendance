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

/** Optional fact translators only. All class names are strings; absent mods never load their APIs. */
public final class InstalledCapabilityProviders {
    private InstalledCapabilityProviders() { }
    public static List<PackEvidenceProvider> all() { return List.of(new Torchmaster(), new ComponentRemoval(), new Materials(),
            new UnresolvedSystem("apotheosis", "8.8.0", "Affixes/gems/socket rarity/loot-tier configuration needs an attainable composition provider"),
            new UnresolvedSystem("draconicevolution", "3.1.4.633", "Modules/shield/energy/host-grid composition and configured tiers are not normalized"),
            new UnresolvedSystem("ars_nouveau", "5.13.1", "Spell/glyph composition, source costs and automation require an attainable configuration provider")); }
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
    public static final class Torchmaster extends Optional {
        public Torchmaster() { super("torchmaster", "21.1.12"); }
        public ProviderReadiness readiness(GenerationDataSnapshot inputs) {
            var result = super.readiness(inputs);
            if (!result.collectable()) return result;
            Object spec = field("net.xalcon.torchmaster.TorchmasterNeoforgeConfig", "spec");
            if (!(boolean)call(spec, "isLoaded")) return new ProviderReadiness(ProviderReadiness.Status.NOT_READY, audited, "Effective Torchmaster configuration not loaded");
            return result;
        }
        public void collectCapabilities(PackEvidenceContext context, Map<String, ResourceEvidence> resources, CapabilitySink sink) {
            sink.analyzed();
            Object config = field("net.xalcon.torchmaster.TorchmasterNeoforgeConfig", "WRAPPED_CONFIG");
            // Invoke public interface methods (the implementing anonymous class itself is package-private).
            int radius; boolean natural; boolean siege;
            try {
                Class<?> api = Class.forName("net.xalcon.torchmaster.config.ITorchmasterConfig");
                radius = (int)api.getMethod("getMegaTorchRadius").invoke(config);
                natural = (boolean)api.getMethod("getBlockOnlyNaturalSpawns").invoke(config);
                siege = (boolean)api.getMethod("getBlockVillageSieges").invoke(config);
            } catch (ReflectiveOperationException error) { throw new IllegalStateException("Torchmaster configuration API", error); }
            Object filter = field("net.xalcon.torchmaster.Torchmaster", "MegaTorchFilterRegistry");
            Object[] entities = (Object[])call(filter, "getEntities");
            if (entities.length == 0) {
                sink.candidate("torchmaster:megatorch", id(), "Effective affected-entity filter empty; loaded service readiness/actual suppression not proven");
                return;
            }
            var placement = CompetitiveCapabilities.placement(resources, "torchmaster:megatorch");
            sink.add(torch(radius, natural, siege, Arrays.stream(entities).map(Object::toString).sorted().toList(), placement));
        }
    }
    /** Pure adapter projection also used by synthetic/offline verification. */
    public static Functional torch(int radius, boolean natural, boolean siege, List<String> entityTypes, CompetitiveCapabilities.Placement placement) {
        var operation = new Operation(Automation.PASSIVE, Activity.PASSIVE, Renewal.RENEWABLE, null, null, null, null,
                List.of("No consumable cost in audited blocking logic"), List.of("Place mega torch; registered loaded-area blocking light"));
        var m = CompetitiveCapabilities.measurement(CapabilityAxis.SPAWN_SUPPRESSION, (double)radius, "cube_radius_blocks",
                "registered entity filter=" + entityTypes + "; natural_only=" + natural + "; village_sieges=" + siege,
                new Scope("filtered_spawn_attempts", "cube", (double)radius, null), operation, "torchmaster_capabilities", Origin.TYPED_ADAPTER,
                List.of("Other spawn/event hooks may override; loaded-area uptime unmeasured"));
        return new Functional(new CapabilityEvidence("torchmaster:megatorch", placement.stage(), Map.of(), placement.reachable(), Math.min(.9, placement.confidence()),
                "Torchmaster 21.1.12 effective config/filter; audited Cubic distance and spawn hooks"), "effective_config", List.of(m), placement.acquisition(), placement.reachable());
    }
    public static final class ComponentRemoval extends Optional {
        public ComponentRemoval() { super("forbidden_arcanus", "2.6.1"); }
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
                sink.candidate(recipe.id().toString(), id(), "Durability-removal behavior measured; target compatibility/template/base acquisition remains unproven");
            }
        }
    }
    /** Loaded modular material definitions exceed default-stack evidence, but are not final item stats.
     * Potential component bounds stay outside attainable frontiers until a provider proves composition/grades/traits. */
    public static final class Materials extends Optional {
        public Materials() { super("silentgear", "4.2.1.1"); }
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
                    sink.candidate(entry.getKey().toString(), id(), "Compound/processed/custom material composition unsupported"); continue;
                }
                var encoded = codec.encodeStart(ops, entry.getValue()).result();
                if (encoded.isEmpty()) { sink.candidate(entry.getKey().toString(), id(), "Loaded material definition cannot encode"); continue; }
                material(entry.getKey().toString(), encoded.get().getAsJsonObject(), sink);
            }
        }
    }
    public static void material(String id, JsonObject definition, CapabilitySink sink) {
        sink.definition("silentgear_capabilities", id, definition);
        JsonObject properties = definition.getAsJsonObject("properties");
        if (properties == null) { sink.candidate(id, "silentgear_capabilities", "Inherited/empty property definition needs resolved composition"); return; }
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
        sink.candidate(id, "silentgear_capabilities", "Modular material stats captured; attainable full-gear configuration provider required");
    }
    private static final class UnresolvedSystem extends Optional {
        final String detail;
        UnresolvedSystem(String mod, String audited, String detail) { super(mod, audited); this.detail = detail; }
        public void collectCapabilities(PackEvidenceContext context, Map<String, ResourceEvidence> resources, CapabilitySink sink) {
            sink.analyzed(); sink.candidate(mod, id(), detail + "; installed version=" + context.inputs().installedVersion(mod));
        }
    }
}
