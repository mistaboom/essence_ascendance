package com.mistaboom.essence_ascendance.valuation;

import com.mistaboom.essence_ascendance.balance.engine.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import java.util.*;

/** Conditional exploration, backed by collected natural placement and re-evaluated exact
 * native tool drops. Tool supply is paid by the caller's joint ledger, never assumed from
 * a material-tier label. One fresh tool per successful one-item harvest is conservative;
 * no expected count becomes guaranteed stock, and no wear-free renewal is inferred. */
public final class NativeHarvestSupplies {
    private NativeHarvestSupplies() { }
    public record Route(String block, String tool, double chance, double expected, AcquisitionSource placement) { }
    public static Map<String, List<Route>> capture(GenerationDataSnapshot inputs, ValuationEvidenceSnapshot snapshot) {
        var natural = new TreeMap<String, AcquisitionSource>();
        var placement = ProceduralNaturalBlockIndex.startingWorld(inputs);
        for (var block : BuiltInRegistries.BLOCK) {
            var key = BuiltInRegistries.BLOCK.getKey(block);
            if (!placement.contains(key)) continue;
            natural.put(key.toString(), new AcquisitionSource(key.toString(), AcquisitionSource.Kind.WORLD_GENERATION,
                    ProgressionBand.ENTRY, 1, false, false, 0, .76, List.of(),
                    "Actual starting Overworld placement; ordinary harvest with no worn accessories, outside any prior giant-mining action tick: " + String.join("; ", placement.signals(key))));
        }
        var tools = new ArrayList<ItemStack>(); tools.add(ItemStack.EMPTY);
        // Actual native tools only; no custom durability, energy or harvest callbacks.
        for (var item : inputs.items()) if ((item instanceof DiggerItem || item instanceof ShearsItem)
                && item.getClass().getPackageName().equals("net.minecraft.world.item")
                && BuiltInRegistries.ITEM.getKey(item).getNamespace().equals("minecraft")) {
            var stack = item.getDefaultInstance();
            if (stack.isDamageableItem() && stack.getMaxDamage() > 1 && !stack.isEnchanted()) tools.add(stack);
        }
        var result = new TreeMap<String, List<Route>>();
        for (var entry : natural.entrySet()) {
            var key = ResourceLocation.tryParse(entry.getKey());
            var block = key == null ? null : BuiltInRegistries.BLOCK.getOptional(key).orElse(null);
            if (block == null || !key.getNamespace().equals("minecraft")
                    || !block.getClass().getPackageName().equals("net.minecraft.world.level.block")) continue;
            var state = block.defaultBlockState();
            var hand = StartingBlockDrops.supportedHandDrops(inputs, state);
            for (var tool : tools) {
                if (!tool.isEmpty() && !state.requiresCorrectToolForDrops()) continue; // Hand routes are sufficient for this bounded adapter.
                if (state.requiresCorrectToolForDrops() && !tool.isCorrectToolForDrops(state)) continue;
                var drops = tool.isEmpty() ? hand : StartingBlockDrops.supportedToolDrops(inputs, state, tool, "", "", Set.of(), ignored -> { });
                drops.forEach((item, drop) -> result.computeIfAbsent(item, ignored -> new ArrayList<>()).add(new Route(entry.getKey(),
                        tool.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(tool.getItem()).toString(), drop.chance(), drop.expectedCount(), entry.getValue())));
            }
        }
        result.replaceAll((item, routes) -> List.copyOf(routes));
        return Collections.unmodifiableMap(result);
    }
}
