package com.mistaboom.essence_ascendance.valuation;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Shared factual descriptions of nonlethal production absent from ordinary loot tables.
 * Integrations can register further factual events before generation. Counts describe one
 * completed event, never a measured farm rate or an item price. The registry itself has no
 * side effects; valuation and gameplay consumers decide how an eligible event is used.
 */
public final class BiologicalAcquisitionSources {
    public enum Event { GROWTH, PERIODIC_PRODUCTION, POLLINATION_HARVEST }

    /** Consumed materials and reusable-tool wear are charged by the shared acquisition solver. */
    public record Requirement(Item item, int count, boolean consumed, int durabilityWear) {
        public Requirement {
            if (item == null || item == Items.AIR || count <= 0 || durabilityWear < 0
                    || (consumed && durabilityWear != 0))
                throw new IllegalArgumentException("Invalid biological source requirement");
        }
    }

    public record Source(ResourceLocation id, EntityType<?> producer, Item output, int count,
                         Event event, List<Requirement> requirements, String reason) {
        public Source {
            if (id == null || producer == null || output == null || output == Items.AIR || count <= 0
                    || event == null || reason == null || reason.isBlank())
                throw new IllegalArgumentException("Biological sources need a producer, output and factual event");
            requirements = List.copyOf(requirements);
        }
        public ResourceLocation producerId() { return BuiltInRegistries.ENTITY_TYPE.getKey(producer); }
        public double confidence() { return .65; }
    }

    private static final Map<ResourceLocation, Source> ADDITIONAL = new TreeMap<>();
    private BiologicalAcquisitionSources() { }

    public static synchronized void register(Source source) {
        if (source == null || ADDITIONAL.containsKey(source.id())
                || vanilla().stream().anyMatch(existing -> existing.id().equals(source.id())))
            throw new IllegalArgumentException("Duplicate or missing biological source");
        ADDITIONAL.put(source.id(), source);
    }

    public static synchronized List<Source> all() {
        List<Source> result = new ArrayList<>(vanilla());
        result.addAll(ADDITIONAL.values());
        return result.stream().sorted(java.util.Comparator.comparing(Source::id)).toList();
    }

    /** Renewable products an adult producer creates on its own, without a consumed input or tool. */
    public static synchronized List<Source> periodicProducts(EntityType<?> producer) {
        if (producer == null) return List.of();
        return all().stream()
                .filter(source -> source.producer() == producer)
                .filter(source -> source.event() == Event.PERIODIC_PRODUCTION)
                .filter(source -> source.requirements().isEmpty())
                .toList();
    }

    private static List<Source> vanilla() {
        // These are the actual output quantities of the named vanilla mechanics,
        // not inferred item prices. Custom replacement behavior needs its own provider.
        return List.of(
                source("turtle_growth", EntityType.TURTLE, Items.TURTLE_SCUTE, 1, Event.GROWTH, List.of(),
                        "Turtle.ageBoundaryReached: one scute when a baby becomes adult; breeding, egg incubation and growth constrain production"),
                source("chicken_laying", EntityType.CHICKEN, Items.EGG, 1, Event.PERIODIC_PRODUCTION, List.of(),
                        "Chicken.aiStep: one egg after an adult chicken's laying timer; chicken-jockey production excluded by the engine"),
                source("armadillo_shedding", EntityType.ARMADILLO, Items.ARMADILLO_SCUTE, 1, Event.PERIODIC_PRODUCTION, List.of(),
                        "Armadillo.customServerAiStep: one scute after an adult armadillo's shedding timer; brushing is a separate optional faster route"),
                source("bee_honeycomb_harvest", EntityType.BEE, Items.HONEYCOMB, 3, Event.POLLINATION_HARVEST,
                        List.of(new Requirement(Items.SHEARS, 1, false, 1)),
                        "BeehiveBlock: three honeycomb from a full hive using shears; bees, flowers, a hive and pollination are required"),
                source("bee_honey_bottle_harvest", EntityType.BEE, Items.HONEY_BOTTLE, 1, Event.POLLINATION_HARVEST,
                        List.of(new Requirement(Items.GLASS_BOTTLE, 1, true, 0)),
                        "BeehiveBlock: one bottle of honey from a full hive, consuming one glass bottle; pollination refills the hive")
        );
    }

    private static Source source(String mechanic, EntityType<?> producer, Item output, int count,
                                 Event event, List<Requirement> requirements, String reason) {
        return new Source(ResourceLocation.fromNamespaceAndPath("essence_ascendance", "biological/" + mechanic),
                producer, output, count, event, requirements,
                reason + "; qualitative source evidence: setup, age and farm throughput are not measured");
    }
}
