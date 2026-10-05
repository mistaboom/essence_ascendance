package com.mistaboom.essence_ascendance.valuation;

import com.mistaboom.essence_ascendance.balance.config.BalanceOverrides;
import com.mistaboom.essence_ascendance.balance.engine.PackEvidenceCollector;
import com.mistaboom.essence_ascendance.balance.engine.ProgressionBand;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Tool;
import java.lang.reflect.*;
import java.util.List;
import java.util.Map;

/** Native component routing and actual economic/equipment isolation, without a server/world. */
public final class RoutingEvidenceTest {
    public static void main(String[] args) throws Exception {
        Thread.currentThread().setUncaughtExceptionHandler((thread, failure) -> failure.printStackTrace(
                new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.err))));
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); EssenceTypes.init();
        // Test JVM only: open the frozen vanilla item registry for native fixtures.
        // No server, live pack registry or saved profile is involved.
        Field frozen = net.minecraft.core.MappedRegistry.class.getDeclaredField("frozen"); frozen.setAccessible(true);
        Field intrusive = net.minecraft.core.MappedRegistry.class.getDeclaredField("unregisteredIntrusiveHolders"); intrusive.setAccessible(true);
        frozen.set(net.minecraft.core.registries.BuiltInRegistries.ITEM, false);
        intrusive.set(net.minecraft.core.registries.BuiltInRegistries.ITEM, new java.util.IdentityHashMap<>());
        ValuationGenerationInputs.configure(new BalanceOverrides(List.of(), Map.of()));
        Constructor<?> indexConstructor = ProceduralValuationIndex.class.getDeclaredConstructors()[0];
        indexConstructor.setAccessible(true);
        var index = (ProceduralValuationIndex) indexConstructor.newInstance(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                Map.of(), Map.of(), Map.of(), Map.of(), null, null, null, null);
        Class<?> contextClass = Class.forName(ProceduralValuationEngine.class.getName() + "$EvaluationContext");
        Constructor<?> contextConstructor = contextClass.getDeclaredConstructor(ProceduralValuationIndex.class);
        contextConstructor.setAccessible(true);
        Method evaluate = ProceduralValuationEngine.class.getDeclaredMethod("evaluateItem", ProceduralValuationIndex.class, Item.class, contextClass);
        evaluate.setAccessible(true);
        // Opaque fixture registry paths exercise standard description-key fallback
        // and missing native tags without depending on translated display text.
        Item plain = named("opaque", new Item.Properties());
        Item sword = named("ultimate_supreme_sword", new Item.Properties());
        var p = value(evaluate, index, plain, contextConstructor.newInstance(index));
        var s = value(evaluate, index, sword, contextConstructor.newInstance(index));
        check(p.totalValue() == s.totalValue() && p.intrinsicValue() == s.intrinsicValue(), "name cannot change economic value");
        check(p.progressionBand() == s.progressionBand() && p.modeledAcquisition() == s.modeledAcquisition(), "name cannot change acquisition stage/reachability");
        Method stage = PackEvidenceCollector.class.getDeclaredMethod("inferStage", ProceduralValuationResult.class, Item.class, List.class);
        stage.setAccessible(true);
        check(stage.invoke(null, p, plain, List.of()).equals(stage.invoke(null, s, sword, List.of())), "final acquisition stage ignores marketing/name hints");
        check(s.routingDiagnostics().nameHints().contains("weapon:sword"), "missing tags get weak function hint");
        Method measure = PackEvidenceCollector.class.getDeclaredMethod("measureEquipment", ItemStack.class, ProgressionBand.class, boolean.class);
        measure.setAccessible(true);
        check(measure.invoke(null, new ItemStack(sword), ProgressionBand.ENTRY, false) == null, "keywords create no equipment/capability power");
        var renamed = new ItemStack(Items.DIAMOND_SWORD);
        Object nativeMeasure = measure.invoke(null, renamed, ProgressionBand.LATE, true);
        renamed.set(DataComponents.CUSTOM_NAME, Component.literal("Supreme flying mining cannon"));
        check(nativeMeasure.equals(measure.invoke(null, renamed, ProgressionBand.LATE, true)), "anvil/display naming cannot alter measured power");
        Item foodSword = named("sword", new Item.Properties().food(new FoodProperties.Builder().nutrition(4).saturationModifier(.3f).build()));
        var food = value(evaluate, index, foodSword, contextConstructor.newInstance(index));
        check(food.routedEssence().containsKey(EssenceTypes.VITALITY) && !food.routedEssence().containsKey(EssenceTypes.OFFENSE), "food component overrides weak sword hint");
        check(food.routingDiagnostics().evidence().contains("name_structured_conflict"), "disjoint evidence conflict reported");
        Item componentTool = named("sword_statue", new Item.Properties().component(DataComponents.TOOL, new Tool(List.of(), 2, 1)));
        var tool = value(evaluate, index, componentTool, contextConstructor.newInstance(index));
        check(tool.routingDiagnostics().structuredSignals().contains("tool_component") && tool.routedEssence().containsKey(EssenceTypes.GATHERING), "native tool component overrides decorative hint");
        var diagnostics = RoutingGenerationDiagnostics.collect(List.of(p, s, food, tool));
        check(diagnostics.getAsJsonObject("counts").get("conflicts").getAsInt() == 2, "generation diagnostics count conflicts");
        check(diagnostics.getAsJsonObject("counts").get("fallback_only_function").getAsInt() == 1, "fallback function counted separately");
        check(diagnostics.getAsJsonObject("ruleCounts").has("form:depiction"), "matched safeguards retained");
        Item module = named("module_jetpack_unit", new Item.Properties());
        var recipe = new ProceduralValuationIndex.RecipeModel(net.minecraft.resources.ResourceLocation.parse("fixture:flight"),
                net.minecraft.world.item.crafting.RecipeType.CRAFTING, Items.ELYTRA, 1,
                List.of(new ProceduralValuationIndex.IngredientChoice(List.of(module))));
        var componentIndex = (ProceduralValuationIndex) indexConstructor.newInstance(Map.of(Items.ELYTRA, List.of(recipe)),
                Map.of(module, List.of(new ProceduralValuationIndex.RecipeUse(recipe))), Map.of(), Map.of(), Map.of(),
                Map.of(), Map.of(), Map.of(), Map.of(), null, null, null, null);
        var component = value(evaluate, componentIndex, module, contextConstructor.newInstance(componentIndex));
        check(!component.routedEssence().containsKey(EssenceTypes.MOBILITY), "machine part cannot inherit whole flight-item function");
        check(measure.invoke(null, new ItemStack(module), ProgressionBand.ENTRY, false) == null, "flight module name gives no numeric capability");
        var modifiers = net.minecraft.world.item.component.ItemAttributeModifiers.builder()
                .add(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE,
                        new net.minecraft.world.entity.ai.attributes.AttributeModifier(net.minecraft.resources.ResourceLocation.parse("fixture:damage"),
                                5, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE),
                        net.minecraft.world.entity.EquipmentSlotGroup.MAINHAND).build();
        Item attributed = named("wand", new Item.Properties().component(DataComponents.ATTRIBUTE_MODIFIERS, modifiers));
        var attributeValue = value(evaluate, index, attributed, contextConstructor.newInstance(index));
        check(attributeValue.routingDiagnostics().structuredSignals().contains("positive_mainhand_attack_attribute")
                && attributeValue.routedEssence().containsKey(EssenceTypes.OFFENSE), "native slot attribute wins over weak utility implement hint");
        long diagnosticStarted = System.nanoTime();
        var largeDiagnostics = RoutingGenerationDiagnostics.collect(java.util.Collections.nCopies(64341, food));
        long diagnosticMillis = (System.nanoTime() - diagnosticStarted) / 1_000_000;
        check(largeDiagnostics.getAsJsonObject("counts").get("conflicts").getAsInt() == 64341, "counts cover complete input");
        check(largeDiagnostics.getAsJsonObject("representatives").getAsJsonArray("conflicts").size() == 24, "retained representatives remain bounded");
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out)).println(
                "Synthetic routing diagnostic projection: items=64341 elapsedMs=" + diagnosticMillis + " retainedJsonChars=" + largeDiagnostics.toString().length());
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out)).println("RoutingEvidenceTest PASS: native precedence, naming/economic/stage/power isolation and generation diagnostics");
    }
    private static int fixtureId;
    private static Item named(String key, Item.Properties properties) {
        Item item = new Item(properties) { @Override public String getDescriptionId() { return "item.fixture." + key; } };
        return net.minecraft.core.Registry.register(net.minecraft.core.registries.BuiltInRegistries.ITEM,
                net.minecraft.resources.ResourceLocation.parse("fixture:opaque_" + fixtureId++), item);
    }
    private static ProceduralValuationResult value(Method method, ProceduralValuationIndex index, Item item, Object context) throws Exception {
        return (ProceduralValuationResult) method.invoke(null, index, item, context);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
