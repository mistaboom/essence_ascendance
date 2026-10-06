package com.mistaboom.essence_ascendance.balance.capability;

import com.google.gson.JsonObject;
import com.mistaboom.essence_ascendance.balance.engine.*;
import com.mistaboom.essence_ascendance.valuation.GenerationDataSnapshot;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import static com.mistaboom.essence_ascendance.balance.engine.CapabilityEvidence.*;

/** Loaded Iron Jetpacks definitions and exact craft witnesses; optional APIs remain reflection-only.
 * This velocity-based flight is not an Abilities#flyingSpeed compatibility declaration. */
public final class IronJetpackCapabilityProvider implements PackEvidenceProvider {
    private static final String API = "com.blakebr0.ironjetpacks.";
    public String id() { return "ironjetpacks_capabilities"; }
    public Set<CapabilityAxis> capabilityAxes() { return Set.of(CapabilityAxis.FLIGHT); }
    public List<String> dependencyModIds() { return List.of("ironjetpacks"); }
    public boolean requiredForGeneration() { return false; }
    public ProviderReadiness readiness(GenerationDataSnapshot inputs) {
        return InstalledCapabilityProviders.version(inputs.installedVersion("ironjetpacks"), "8.0.11",
                inputs.installedVersion("neoforge") != null,
                "Loaded configuration, static exact-component craft access and audited FE/velocity operation; custom upgrade transforms remain unknown");
    }
    public void collect(PackEvidenceContext context, EvidenceSink sink) { }
    public void collectCapabilities(PackEvidenceContext context, Map<String, ResourceEvidence> resources, CapabilitySink sink) {
        var access = ConfiguredRecipeAccess.nativeCrafting(context, resources);
        ConfigurationAccess.Proof charger = null;
        boolean chargingAudited = "1.14.1".equals(context.inputs().installedVersion("charginggadgets"));
        if (chargingAudited) charger = access.requireFuel();
        Object registry = staticCall(API + "registry.JetpackRegistry", "getInstance");
        List<?> definitions = (List<?>)call(registry, "getJetpacks");
        if (definitions.size() > 4096) throw new IllegalArgumentException("Jetpack registry exceeds bounded configuration census");
        final var operatingProof = charger;
        int index = 0;
        for (Object jetpack : definitions) {
            String operation = "configuration-" + index++;
            var attempt = OptionalIntegration.attempt(id(), operation, () -> {
                var staged = new CapabilitySink();
                collectConfiguration(context, access, operatingProof, jetpack, staged);
                return staged;
            });
            attempt.value().ifPresentOrElse(sink::merge,
                    () -> sink.candidate(operation, id(), "Configured compatibility read failed: " + attempt.failure(),
                            capabilityAxes(), CapabilitySink.Reason.READ_FAILED));
        }
    }
    private void collectConfiguration(PackEvidenceContext context, ConfiguredRecipeAccess access,
            ConfigurationAccess.Proof charger, Object jetpack, CapabilitySink sink) {
            var ops = RegistryOps.create(JsonOps.INSTANCE, context.server().registryAccess());
            sink.analyzed();
            String configuration = call(jetpack, "getId").toString();
            var definition = (JsonObject)call(jetpack, "toJson");
            sink.definition(id(), configuration, definition);
            if (bool(jetpack, "disabled")) {
                sink.candidate(configuration, id(), "Disabled loaded flight configuration", capabilityAxes(), CapabilitySink.Reason.CONFIGURATION_DISABLED);
                return;
            }
            boolean creative = bool(jetpack, "creative");
            int capacity = number(jetpack, "capacity").intValue(), usage = number(jetpack, "usage").intValue();
            double vertical = number(jetpack, "speedVert").doubleValue(), acceleration = number(jetpack, "accelVert").doubleValue();
            if ((!creative && (capacity <= usage || usage <= 0)) || !Double.isFinite(vertical) || vertical <= 0
                    || !Double.isFinite(acceleration) || acceleration <= 0) {
                sink.candidate(configuration, id(), "Configured flight has no positive bounded thrust/energy contract",
                        capabilityAxes(), CapabilitySink.Reason.NO_SUPPORTED_OPERATION); return;
            }
            ItemStack stack = (ItemStack)staticCall(API + "util.JetpackUtils", "getItemForJetpack", jetpack);
            JsonObject serialized = ItemStack.CODEC.encodeStart(ops, stack).getOrThrow().getAsJsonObject();
            var setup = new ArrayList<JsonObject>(); setup.add(serialized);
            if (!creative) {
                var station = new JsonObject(); station.addProperty("id", "charginggadgets:charging_station"); setup.add(station);
            }
            var craft = access.requireFinite(setup).access();
            List<ConfigurationAccess.Proof> requirements = new ArrayList<>(); requirements.add(craft);
            if (!creative) {
                if (charger == null) {
                    sink.candidate(configuration, id(), "No audited independently accessible FE charging/fuel witness",
                            capabilityAxes(), CapabilitySink.Reason.UNSUPPORTED_API); return;
                }
                requirements.add(charger);
            }
            var proof = ConfiguredRecipeAccess.combine(requirements, configuration, .9);
            if (!proof.placement().reachable()) {
                sink.candidate(configuration, id(), "Exact craft/operating configuration access unproven: " + String.join("; ", proof.unknown()),
                        capabilityAxes(), CapabilitySink.Reason.ACCESS_UNPROVEN); return;
            }
            String item = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            var costs = creative ? List.of("Creative configuration; its own exact craft access remains required")
                    : List.of(usage + " FE per active native tick", "Station burns standard smelting fuel: floor(burnTicks)/50 ticks at 625 FE/tick",
                    "Charging transfers up to 2500 FE/tick; charge exhaustion ends thrust/hover");
            var unsupported = List.of("FLIGHT presence only; velocity fields are native impulse controls, not measured travel throughput",
                    "Loaded-area/player duty cycle unmeasured; no permanent standard-flight permission or Abilities#flyingSpeed response",
                    "Fall-distance reset applies only while native powered flight operation runs; no unconditional void rescue");
            var operation = new Operation(Automation.NONE, Activity.PLAYER_ACTIVE, Renewal.UNKNOWN, null,
                    creative ? null : Math.max(0, (capacity - 1) / usage) / 20.0, null, null, costs, proof.selected());
            var measurement = CompetitiveCapabilities.measurement(CapabilityAxis.FLIGHT, 1.0, "presence", "configured powered directional thrust and hover",
                    Scope.self(), operation, id(), Origin.TYPED_ADAPTER, unsupported);
            sink.add(new Functional(new CapabilityEvidence(item, proof.placement().stage(), Map.of(CapabilityAxis.FLIGHT, 1.0), true,
                    proof.placement().confidence(), "Loaded Iron Jetpacks configuration " + configuration + "; exact static craft and operating witnesses"),
                    configuration, List.of(measurement), proof.placement().acquisition(), true));
    }
    private static Object call(Object target, String method) {
        try { return target.getClass().getMethod(method).invoke(target); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException("Audited jetpack API changed: " + method, failure); }
    }
    private static Object staticCall(String type, String method, Object... arguments) {
        try {
            Class<?> api = Class.forName(type);
            if (arguments.length == 0) return api.getMethod(method).invoke(null);
            return api.getMethod(method, Class.forName(API + "registry.Jetpack")).invoke(null, arguments);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Audited jetpack API changed: " + type + "." + method, failure); }
    }
    private static Number number(Object target, String field) { return (Number)field(target, field); }
    private static boolean bool(Object target, String field) { return (Boolean)field(target, field); }
    private static Object field(Object target, String field) {
        try { return target.getClass().getField(field).get(target); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException("Audited jetpack definition changed: " + field, failure); }
    }
}
