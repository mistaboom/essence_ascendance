package com.mistaboom.essence_ascendance.valuation;

import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;
import java.util.List;

/** Native event definitions shared by Animal Kinship's renewable-product gameplay. */
public final class BiologicalSourceEvidenceTest {
    private static int assertions;
    public static void main(String[] args) {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        var sources = BiologicalAcquisitionSources.all();
        check(sources.size() == 5, "Native event definitions retain their complete product set");
        var chicken = BiologicalAcquisitionSources.periodicProducts(EntityType.CHICKEN);
        check(chicken.size() == 1 && chicken.getFirst().output() == Items.EGG && chicken.getFirst().count() == 1,
                "Chicken periodic products retain one native egg");
        var armadillo = BiologicalAcquisitionSources.periodicProducts(EntityType.ARMADILLO);
        check(armadillo.size() == 1 && armadillo.getFirst().output() == Items.ARMADILLO_SCUTE,
                "Armadillo periodic products retain native shedding");
        check(BiologicalAcquisitionSources.periodicProducts(EntityType.TURTLE).isEmpty(), "Growth events are not periodic products");
        check(BiologicalAcquisitionSources.periodicProducts(EntityType.BEE).isEmpty(), "Harvest operations are not free periodic products");
        check(BiologicalAcquisitionSources.periodicProducts(null).isEmpty(), "Missing producer has no periodic product");
        var comb = sources.stream().filter(source -> source.output() == Items.HONEYCOMB).findFirst().orElseThrow();
        check(comb.count() == 3 && comb.requirements().getFirst().item() == Items.SHEARS
                        && !comb.requirements().getFirst().consumed() && comb.requirements().getFirst().durabilityWear() == 1,
                "Honeycomb retains its native harvest count and shears wear");
        var honey = sources.stream().filter(source -> source.output() == Items.HONEY_BOTTLE).findFirst().orElseThrow();
        check(honey.requirements().getFirst().item() == Items.GLASS_BOTTLE && honey.requirements().getFirst().consumed(),
                "Honey harvest consumes an actual bottle");
        var extension = new BiologicalAcquisitionSources.Source(ResourceLocation.parse("fixture:renewable_fiber"),
                EntityType.SHEEP, Items.STRING, 2, BiologicalAcquisitionSources.Event.PERIODIC_PRODUCTION,
                List.of(), "Fixture declares a reusable periodic product");
        BiologicalAcquisitionSources.register(extension);
        check(BiologicalAcquisitionSources.periodicProducts(EntityType.SHEEP).equals(List.of(extension)), "Runtime extension retains its declared product");
        try { BiologicalAcquisitionSources.register(extension); throw new AssertionError("Duplicate accepted"); }
        catch (IllegalArgumentException expected) { assertions++; }
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out)).println("BiologicalSourceEvidenceTest: "
                + assertions + " native event and runtime product checks PASS");
    }
    private static void check(boolean condition, String message) {
        assertions++; if (!condition) throw new AssertionError(message);
    }
}
