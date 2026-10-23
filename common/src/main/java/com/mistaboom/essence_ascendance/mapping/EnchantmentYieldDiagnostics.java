package com.mistaboom.essence_ascendance.mapping;

import com.google.gson.JsonObject;
import com.mistaboom.essence_ascendance.balance.engine.EnchantmentSemantics;
import com.mistaboom.essence_ascendance.balance.generated.BalanceDocument;
import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.*;

/** Loaded-registry, headless-safe receipt. No player, world blocks, inventory or progression is mutated. */
public final class EnchantmentYieldDiagnostics {
    private EnchantmentYieldDiagnostics() { }
    public static int run(CommandSourceStack source) {
        //? if >=26.1.2
        /*var registry = source.getServer().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);*/
        //? if <26.1.2
        var registry = source.getServer().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        JsonObject report = new JsonObject();
        report.addProperty("policy", EnchantedStackYields.POLICY);
        report.addProperty("generation", ItemEssenceMappingRegistry.generation());
        report.addProperty("halfSaturation", EnchantedStackYields.HALF_SATURATION);
        JsonObject cases = new JsonObject();
        long cap = total(ItemEssenceMappingRegistry.resolveBaseDissolution(new ItemStack(Items.ENCHANTED_BOOK)));
        long carrier = total(ItemEssenceMappingRegistry.resolveDissolution(new ItemStack(Items.ENCHANTED_BOOK)));
        cases.add("blank", receipt(new ItemStack(Items.ENCHANTED_BOOK)));
        List<String> failures = new ArrayList<>();
        var retained = new TreeMap<String, ItemStack>();
        for (String id : List.of("sharpness", "protection", "efficiency", "mending", "unbreaking", "binding_curse")) {
            //? if >=26.1.2
            /*var holder = registry.get(ResourceLocation.withDefaultNamespace(id));*/
            //? if <26.1.2
            var holder = registry.getHolder(ResourceLocation.withDefaultNamespace(id));
            if (holder.isEmpty()) continue;
            for (int level : List.of(1, 2, 5)) {
                if (level > holder.get().value().getMaxLevel()) continue;
                var stack = book(Map.of(holder.get(), level));
                retained.put(id + "/" + level, stack);
                cases.add(id + "/" + level, receipt(stack));
                long value = total(ItemEssenceMappingRegistry.resolveDissolution(stack));
                if (value < carrier || value > cap) failures.add(id + " level " + level + " outside generated budget");
                if (level > 1) {
                    var previous = book(Map.of(holder.get(), level - 1));
                    if (value > 2 * total(ItemEssenceMappingRegistry.resolveDissolution(previous)))
                        failures.add(id + " level " + level + " exceeds equal-level anvil inputs");
                }
            }
        }
        //? if >=26.1.2
        /*var sharpness = registry.get(ResourceLocation.withDefaultNamespace("sharpness"));*/
        //? if <26.1.2
        var sharpness = registry.getHolder(ResourceLocation.withDefaultNamespace("sharpness"));
        //? if >=26.1.2
        /*var protection = registry.get(ResourceLocation.withDefaultNamespace("protection"));*/
        //? if <26.1.2
        var protection = registry.getHolder(ResourceLocation.withDefaultNamespace("protection"));
        if (sharpness.isPresent() && protection.isPresent()) {
            var mixed = book(Map.of(sharpness.get(), 1, protection.get(), 1));
            cases.add("mixed_sharpness_protection", receipt(mixed));
            long input = total(ItemEssenceMappingRegistry.resolveDissolution(book(Map.of(sharpness.get(), 1))))
                    + total(ItemEssenceMappingRegistry.resolveDissolution(book(Map.of(protection.get(), 1))));
            if (total(ItemEssenceMappingRegistry.resolveDissolution(mixed)) > input) failures.add("Mixed book exceeds its two inputs");
            var sword = new ItemStack(Items.IRON_SWORD); sword.enchant(sharpness.get(), 1);
            cases.add("applied_iron_sword", receipt(sword));
            if (total(ItemEssenceMappingRegistry.resolveDissolution(sword))
                    != total(ItemEssenceMappingRegistry.resolveDissolution(new ItemStack(Items.IRON_SWORD))))
                failures.add("Equipment enchantment changes conserved material total");
        }
        JsonObject coverage = new JsonObject();
        List<String> unsupported = new ArrayList<>();
        List<String> decreasing = new ArrayList<>();
        List<String> lateOnsetPositive = new ArrayList<>();
        //? if >=26.1.2
        /*registry.listElements().sorted(Comparator.comparing(holder -> holder.key().identifier())).forEach(holder -> {*/
        //? if <26.1.2
        registry.holders().sorted(Comparator.comparing(holder -> holder.key().location())).forEach(holder -> {
            JsonObject levels = new JsonObject();
            long previous = -1;
            boolean recognizedPositive = false;
            for (int level = 1; level <= Math.min(255, holder.value().getMaxLevel()); level++) {
                var stack = book(Map.of(holder, level));
                levels.add(Integer.toString(level), receipt(stack));
                long value = total(ItemEssenceMappingRegistry.resolveDissolution(stack));
                String identity = holder.key().location() + "/" + level;
                if (EnchantmentSemantics.analyze(holder, level).strength() > 0) {
                    if (!recognizedPositive && level > 1) {
                        lateOnsetPositive.add(identity);
                        long unit = com.mistaboom.essence_ascendance.balance.economy.FractionalAmountService.SCALE;
                        if (carrier >= unit && cap - carrier >= unit && value <= carrier)
                            failures.add(identity + " has recognized later-onset effects but no funded positive premium");
                    }
                    recognizedPositive = true;
                }
                if (value < carrier || value > cap) failures.add(identity + " outside generated component bounds");
                if (previous >= 0 && value > 2 * previous) failures.add(identity + " exceeds equal-level anvil input budget");
                if (previous >= 0 && value < previous) decreasing.add(identity + " decreased; inspect effective level curve/condition/effort");
                previous = value;
            }
            coverage.add(holder.key().location().toString(), levels);
            if (!recognizedPositive
                    && !holder.is(net.minecraft.tags.EnchantmentTags.CURSE)) unsupported.add(holder.key().location().toString());
        });
        if (sharpness.isPresent()) {
            var holder = sharpness.get();
            long first = total(ItemEssenceMappingRegistry.resolveDissolution(book(Map.of(holder, 1))));
            long second = total(ItemEssenceMappingRegistry.resolveDissolution(book(Map.of(holder, 2))));
            if (cap - carrier >= 20 * com.mistaboom.essence_ascendance.balance.economy.FractionalAmountService.SCALE
                    && EnchantmentSemantics.analyze(holder, 2).strength() > EnchantmentSemantics.analyze(holder, 1).strength()
                    && second <= first) failures.add("Sharpness II failed to exceed I with sufficient native budget");
            for (int level : List.of(6, 10)) {
                var stack = book(Map.of(holder, level));
                cases.add("sharpness/" + level + "_actual_stored", receipt(stack));
                long value = total(ItemEssenceMappingRegistry.resolveDissolution(stack));
                long prior = total(ItemEssenceMappingRegistry.resolveDissolution(book(Map.of(holder, level - 1))));
                if (value < carrier || value > cap || value > 2 * prior)
                    failures.add("Sharpness actual stored level " + level + " violates budget/merge bound");
            }
        }
        report.add("registryCoverage", coverage);
        report.add("decreasingEffectiveLevels", BalanceDocument.GSON.toJsonTree(decreasing));
        report.add("lateOnsetPositiveDefinitions", BalanceDocument.GSON.toJsonTree(lateOnsetPositive));
        report.add("unsupportedPositiveDefinitions", BalanceDocument.GSON.toJsonTree(unsupported));
        unsupported.stream().filter(id -> id.startsWith("minecraft:")).forEach(id ->
                failures.add("Standard native positive definition has no admitted effect/equipment semantics: " + id));
        report.addProperty("registryEnchantments", coverage.size());
        if (!ItemEssenceMappingRegistry.lastReload().successful()) failures.add("No validated generated mapping is installed");
        report.add("cases", cases);
        report.add("failures", BalanceDocument.GSON.toJsonTree(failures));
        report.addProperty("passed", failures.isEmpty());
        try {
            var path = ItemEssenceMappingManager.configDirectory().resolve("diagnostics/enchantment_yields.json");
            java.nio.file.Files.createDirectories(path.getParent());
            java.nio.file.Files.writeString(path, BalanceDocument.GSON.toJson(report));
        } catch (java.io.IOException error) { failures.add("Cannot write receipt: " + error.getMessage()); }
        source.sendSuccess(() -> Component.literal("Enchantment yield diagnostics: " + (failures.isEmpty() ? "PASS" : "FAIL")
                + "; " + cases.size() + " cases; " + coverage.size() + " registry definitions; "
                + unsupported.size() + " unsupported positive definitions; " + failures), false);
        return failures.isEmpty() ? 1 : 0;
    }
    private static JsonObject receipt(ItemStack stack) {
        var result = new JsonObject();
        Map<String, Long> outputs = new TreeMap<>();
        ItemEssenceMappingRegistry.resolveDissolution(stack).forEach((essence, value) -> outputs.put(essence.id().toString(), value));
        result.add("microYields", BalanceDocument.GSON.toJsonTree(outputs));
        JsonObject effects = new JsonObject();
        var enchantments = stack.getOrDefault(stack.is(Items.ENCHANTED_BOOK) ? DataComponents.STORED_ENCHANTMENTS : DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        enchantments.entrySet().forEach(entry -> effects.add(entry.getKey().unwrapKey().map(key -> key.location().toString()).orElse("unregistered"),
                BalanceDocument.GSON.toJsonTree(EnchantmentSemantics.analyze(entry.getKey(), entry.getIntValue()))));
        result.add("effects", effects);
        return result;
    }
    public static ItemStack book(Map<? extends Holder<Enchantment>, Integer> contents) {
        ItemStack stack = new ItemStack(Items.ENCHANTED_BOOK);
        var enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        contents.forEach(enchantments::set);
        stack.set(DataComponents.STORED_ENCHANTMENTS, enchantments.toImmutable());
        return stack;
    }
    private static long total(Map<EssenceDefinition, Long> values) {
        long total = 0; for (long value : values.values()) total = Math.addExact(total, value); return total;
    }
}
