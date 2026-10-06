package com.mistaboom.essence_ascendance.balance.capability;

import com.google.gson.JsonObject;
import com.mistaboom.essence_ascendance.balance.engine.*;
import com.mistaboom.essence_ascendance.gathering.GatheringRuralService;
import com.mistaboom.essence_ascendance.valuation.GenerationDataSnapshot;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;

import java.lang.reflect.InvocationTargetException;
import java.util.*;

import static com.mistaboom.essence_ascendance.balance.engine.CapabilityEvidence.*;

/** Generation-only audited crouch-growth contract. Optional APIs are strings and never execute growth. */
public final class SquatGrowCapabilityProvider implements PackEvidenceProvider {
    public static final String BONE_MEAL_CHANCE_PER_ACTION = "bonemeal_growth_chance_per_action";
    public static final String SOURCE = "squatgrow:crouch_bonemeal";
    private static final String MOD = "squatgrow";
    private static final String PREFIX = "dev.wuffs.squatgrow.";
    public String id() { return "squatgrow_capabilities"; }
    public Set<CapabilityAxis> capabilityAxes() { return Set.of(CapabilityAxis.CROP_ACCELERATION, CapabilityAxis.TREE_ACCELERATION); }
    public List<String> dependencyModIds() { return List.of(MOD); }
    public boolean requiredForGeneration() { return false; }
    public ProviderReadiness readiness(GenerationDataSnapshot inputs) {
        return InstalledCapabilityProviders.version(inputs.installedVersion(MOD), "21.1.4+mc1.21.1",
                inputs.installedVersion("neoforge") != null,
                "Grounded crouch transition/default-enabled free native bone-meal action supported; player cadence, target ownership and other growth actions unmeasured");
    }
    public void collect(PackEvidenceContext context, EvidenceSink sink) { }

    public void collectCapabilities(PackEvidenceContext context, Map<String, ResourceEvidence> resources, CapabilitySink sink) {
        Object config = staticField(PREFIX + "SquatGrow", "config");
        Object requirements = field(config, "requirements");
        var held = strings(field(requirements, "heldItemRequirement"));
        Map<String, String> equipment = new TreeMap<>();
        ((Map<?, ?>) field(requirements, "equipmentRequirement")).forEach((slot, item) -> equipment.put(slot.toString(), item.toString()));
        Object actions = invokeStatic(PREFIX + "actions.Actions", "get");
        boolean boneMeal = ((Collection<?>) invoke(actions, "getActions")).stream()
                .anyMatch(action -> action.getClass().getName().equals(PREFIX + "actions.BoneMealAction"));
        Configuration facts = new Configuration(((Number) field(config, "chance")).doubleValue(),
                ((Number) field(config, "range")).intValue(), (boolean) field(config, "requireHoe"),
                (boolean) field(requirements, "enabled"), held, equipment,
                (boolean) field(config, "hoeTakesDamage"), (boolean) field(requirements, "requiredItemTakesDamage"),
                (boolean) field(config, "useWhitelist"), strings(field(config, "ignoreList")), boneMeal);
        sink.analyzed();
        JsonObject definition = com.mistaboom.essence_ascendance.balance.generated.BalanceDocument.GSON.toJsonTree(facts).getAsJsonObject();
        definition.addProperty("trigger", "grounded not-crouching to crouching transition; held crouch does not repeat");
        definition.addProperty("defaultEnabled", true);
        definition.addProperty("operation", "synthetic bone meal; native target validity and success conditions apply");
        definition.addProperty("verticalHalfRange", 1);
        definition.addProperty("positionsPerAction", 3L * (2L * facts.range() + 1) * (2L * facts.range() + 1));
        sink.definition(id(), SOURCE, definition);
        if (!isFreeUnrestrictedAction(facts)) {
            sink.candidate(SOURCE, id(), "Configured item/hoe requirements, missing action or invalid chance/range prevent independent free-entry proof",
                    capabilityAxes(), CapabilitySink.Reason.ACCESS_UNPROVEN);
            return;
        }
        Map<CapabilityAxis, List<String>> targets = new EnumMap<>(CapabilityAxis.class);
        targets.put(CapabilityAxis.CROP_ACCELERATION, new ArrayList<>());
        targets.put(CapabilityAxis.TREE_ACCELERATION, new ArrayList<>());
        for (var item : context.inputs().items()) {
            if (!(item instanceof BlockItem blockItem)) continue;
            var state = blockItem.getBlock().defaultBlockState();
            if (!GatheringRuralService.isEligibleGrowthTarget(state, null, true)
                    || !(boolean) invokeStatic(PREFIX + "SquatGrow", "allowTwerk", state)) continue;
            CapabilityAxis axis = state.getBlock() instanceof net.minecraft.world.level.block.SaplingBlock
                    || state.is(net.minecraft.tags.BlockTags.SAPLINGS) ? CapabilityAxis.TREE_ACCELERATION : CapabilityAxis.CROP_ACCELERATION;
            var samples = targets.get(axis);
            String block = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
            if (samples.size() < 16 && !samples.contains(block)) samples.add(block);
        }
        List<CapabilityAxis> admitted = targets.entrySet().stream().filter(e -> !e.getValue().isEmpty()).map(Map.Entry::getKey).toList();
        if (admitted.isEmpty()) {
            sink.candidate(SOURCE, id(), "Effective block filter permits no independently identified native crop/sapling target",
                    capabilityAxes(), CapabilitySink.Reason.ACCESS_UNPROVEN);
            return;
        }
        definition.add("targetSamples", com.mistaboom.essence_ascendance.balance.generated.BalanceDocument.GSON.toJsonTree(targets));
        sink.definition(id(), SOURCE, definition);
        sink.add(freeAction(facts, admitted));
        sink.candidate(SOURCE, id(), "Other registered growth actions, Mystical/AE2 behavior and cactus/sugarcane random ticks require separate typed contracts",
                capabilityAxes(), CapabilitySink.Reason.UNKNOWN_BEHAVIOR);
    }

    /** Empty configured requirements are proven access to the action, never proof of free target resources. */
    public static boolean isFreeUnrestrictedAction(Configuration facts) {
        return facts.boneMealActionPresent() && Double.isFinite(facts.chance()) && facts.chance() > 0 && facts.chance() <= 1
                && facts.range() >= 0 && facts.range() <= 128 && !facts.requireHoe()
                && (!facts.requirementsEnabled() || facts.heldItems().isEmpty() && facts.equipment().isEmpty());
    }

    public record Configuration(double chance, int range, boolean requireHoe, boolean requirementsEnabled,
                                List<String> heldItems, Map<String, String> equipment, boolean hoeTakesDamage,
                                boolean requiredItemTakesDamage, boolean whitelist, List<String> filters,
                                boolean boneMealActionPresent) {
        public Configuration { heldItems = List.copyOf(heldItems); equipment = Collections.unmodifiableMap(new TreeMap<>(equipment)); filters = List.copyOf(filters); }
    }

    public static Functional freeAction(Configuration facts, List<CapabilityAxis> admitted) {
        if (!isFreeUnrestrictedAction(facts)) throw new IllegalArgumentException("Free growth action not proven");
        var unknown = List.of("Player crouch cadence and loaded-area uptime unmeasured; no actions-per-second claim",
                "Targets must be acquired/planted and satisfy native bone-meal environmental/success conditions; no output access certified",
                "Per-player enable toggle/server protection may suppress action; applicability is the default-enabled native contract");
        Operation operation = new Operation(Automation.NONE, Activity.PLAYER_ACTIVE, Renewal.RENEWABLE,
                null, null, null, null, List.of(), List.of("on ground; fresh crouch transition; nearby eligible planted target"));
        List<Measurement> measurements = new ArrayList<>();
        Map<CapabilityAxis, Double> axes = new EnumMap<>(CapabilityAxis.class);
        for (CapabilityAxis axis : admitted.stream().distinct().sorted().toList()) {
            if (axis != CapabilityAxis.CROP_ACCELERATION && axis != CapabilityAxis.TREE_ACCELERATION)
                throw new IllegalArgumentException("Bone-meal target axis required");
            axes.put(axis, facts.chance());
            measurements.add(CompetitiveCapabilities.measurement(axis, facts.chance(), BONE_MEAL_CHANCE_PER_ACTION,
                    "one native bone-meal attempt per eligible block per grounded crouch transition",
                    new Scope(axis == CapabilityAxis.CROP_ACCELERATION ? "growable_crop" : "growable_sapling",
                            "horizontal_square_vertical_3", (double) facts.range(), null), operation, "squatgrow_capabilities", Origin.TYPED_ADAPTER, unknown));
        }
        String reason = "Audited default-enabled reusable crouch action has no configured item requirement or consumable bone-meal cost";
        var witness = new AcquisitionSource(SOURCE, AcquisitionSource.Kind.PLAYER_ACTION, ProgressionBand.ENTRY,
                1, true, false, 0, .95, List.of(), reason);
        return new Functional(new CapabilityEvidence(SOURCE, ProgressionBand.ENTRY, axes, true, .95, reason),
                "effective_free_grounded_crouch", measurements, List.of(witness), true);
    }

    private static List<String> strings(Object value) { return ((List<?>) value).stream().map(Object::toString).toList(); }
    private static Object staticField(String type, String name) {
        try { return Class.forName(type).getField(name).get(null); }
        catch (ReflectiveOperationException error) { throw changed(error); }
    }
    private static Object field(Object target, String name) {
        try { return target.getClass().getField(name).get(target); }
        catch (ReflectiveOperationException error) { throw changed(error); }
    }
    private static Object invoke(Object target, String name) {
        try { return target.getClass().getMethod(name).invoke(target); }
        catch (ReflectiveOperationException error) { throw changed(error); }
    }
    private static Object invokeStatic(String type, String name, Object... arguments) {
        try {
            Class<?>[] parameters = Arrays.stream(arguments).map(value -> value instanceof net.minecraft.world.level.block.state.BlockState
                    ? net.minecraft.world.level.block.state.BlockState.class : value.getClass()).toArray(Class<?>[]::new);
            return Class.forName(type).getMethod(name, parameters).invoke(null, arguments);
        } catch (ReflectiveOperationException error) { throw changed(error); }
    }
    private static IllegalStateException changed(ReflectiveOperationException error) {
        return new IllegalStateException("Audited optional crouch-growth API changed", error instanceof InvocationTargetException invocation ? invocation.getCause() : error);
    }
}
