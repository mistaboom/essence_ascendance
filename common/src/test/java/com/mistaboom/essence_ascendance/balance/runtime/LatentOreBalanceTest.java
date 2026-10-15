package com.mistaboom.essence_ascendance.balance.runtime;

import com.mistaboom.essence_ascendance.config.LatentOreWorldgenSettings;
import com.mistaboom.essence_ascendance.config.LatentOreWorldgenSettings.DimensionOverride;
import com.mistaboom.essence_ascendance.config.LatentOreWorldgenSettings.DimensionSettings;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

/** Placement policy has no acquisition or source inference dependencies. */
public final class LatentOreBalanceTest {
    private static int assertions;
    public static void main(String[] args) {
        var defaults = LatentOreWorldgenSettings.defaults();
        check(generate(defaults).equals(defaults), "Default configured sixteen attempts must remain sixteen");
        var low = new DimensionSettings(true, 1, 1, -17, 39, .25);
        var disabled = new DimensionSettings(false, 1, 3, 5, 12, .75);
        var zero = new DimensionSettings(true, 2, 0, 7, 13, .5);
        var dimension = ResourceLocation.parse("fixture:exact");
        var exact = new DimensionSettings(true, 3, 2, 20, 40, .8);
        var override = new DimensionOverride(true, List.of(ResourceLocation.parse("minecraft:stone")), exact);
        var inputs = new LatentOreWorldgenSettings(low, disabled, zero, false, Map.of(dimension, override));
        var output = generate(inputs);
        check(output.overworld().veinSize() == 9, "Enabled baseline retains native vein size minimum");
        check(output.overworld().veinsPerChunk() == 12, "Enabled baseline retains placement attempt minimum");
        check(output.overworld().minY() == -17 && output.overworld().maxY() == 39,
                "Configured baseline heights are preserved");
        check(output.overworld().discardChanceOnAirExposure() == .25, "Configured air exposure is preserved");
        check(output.nether().equals(disabled), "Disabled distributions do not gain placement");
        check(output.end().equals(zero), "Zero attempts do not gain placement");
        check(!output.automaticDimensions(), "Automatic dimension setting is preserved");
        check(output.dimensions().equals(inputs.dimensions()), "Exact dimension rules are preserved");
        check(output.distribution(dimension, -64, 320).equals(exact), "Exact distribution bypasses automatic minimums");
        check(generate(output).equals(output), "Applying baseline policy twice is stable");
        var maximum = new DimensionSettings(true, 64, 128, -2048, 2048, 1);
        var maximums = new LatentOreWorldgenSettings(maximum, maximum, maximum, true, Map.of());
        check(generate(maximums).equals(maximums), "Large configured distributions are not scaled");
        var diagnostics = LatentOreBalanceGenerator.diagnostics(null, null, inputs, output);
        check(diagnostics.get("policy").getAsString().equals("configured_baseline"), "Diagnostics name actual policy");
        check(!diagnostics.get("acquisitionEvaluated").getAsBoolean(), "Diagnostics do not claim measured acquisition");
        check(!diagnostics.has("earlyConversionFuelAvailable") && !diagnostics.has("categories"),
                "Diagnostics do not invent fuel availability or source coverage");
        check(diagnostics.getAsJsonObject("inputs").equals(RuntimeBalanceDefinition.worldgenJson(inputs)),
                "Diagnostics retain exact configured inputs");
        check(diagnostics.getAsJsonObject("resolved").equals(RuntimeBalanceDefinition.worldgenJson(output)),
                "Diagnostics retain exact resolved placement");
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out))
                .println("LatentOreBalanceTest: " + assertions + " checks PASS");
    }
    private static LatentOreWorldgenSettings generate(LatentOreWorldgenSettings input) {
        return LatentOreBalanceGenerator.generate(null, null, input);
    }
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
