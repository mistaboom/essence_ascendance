package com.mistaboom.essence_ascendance.projectile;

import com.mistaboom.essence_ascendance.equipment.ConstructionMaterialResolver;
import com.mistaboom.essence_ascendance.equipment.ConstructionMaterialResolver.MaterialAmount;
import com.mistaboom.essence_ascendance.gathering.GatheringSalvageService;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapelessRecipe;

import java.util.List;

/** Real native recipe classes and sampled runtime reward arithmetic; no copied recipe or salvage model. */
public final class NativeSalvageTest {
    private static int checks;
    private NativeSalvageTest() { }
    public static void main(String[] args) {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        run();
    }
    public static void run() {
        var rich = recipe("rich", 1, Items.DIAMOND, Items.DIAMOND, Items.STICK);
        var cheap = recipe("cheap", 1, Items.COBBLESTONE, Items.COBBLESTONE, Items.STICK);
        check(resolve(rich).size() == 2, "A single proven sword bill still recovers materials");
        check(resolve(rich, cheap).isEmpty() && resolve(cheap, rich).isEmpty(),
                "Crafting the cheap alternate cannot recycle the expensive alternate's diamonds, independent of recipe order");

        var sharedRich = recipe("shared_rich", 1, Items.DIAMOND, Items.DIAMOND, Items.STICK, Items.STICK);
        var sharedCheap = recipe("shared_cheap", 1, Items.COBBLESTONE, Items.STICK, Items.STICK);
        var shared = resolve(sharedRich, sharedCheap);
        check(shared.size() == 1 && shared.getFirst().item() == Items.STICK && shared.getFirst().amount() == 2,
                "Distinct routes keep proven shared consumed materials and a tangible one-item salvage");
        check(total(GatheringSalvageService.materialRecovery(shared, .1, RandomSource.create(1))) == 1,
                "A conservative supported bill retains its minimum recovered item");

        var alternative = new RecipeHolder<>(ResourceLocation.parse("fixture:alternative"), new ShapelessRecipe("",
                CraftingBookCategory.MISC, new ItemStack(Items.DIAMOND_SWORD), NonNullList.of(Ingredient.EMPTY,
                Ingredient.of(Items.DIAMOND, Items.COBBLESTONE), Ingredient.of(Items.STICK), Ingredient.of(Items.STICK))));
        var alternatives = resolve(alternative);
        check(alternatives.size() == 1 && alternatives.getFirst().item() == Items.STICK,
                "An ingredient alternative cannot manufacture the arbitrarily preferred expensive material");
        customIngredientEvidence();
        customRemainderEvidence();
        var containers = resolve(recipe("containers", 1, Items.MILK_BUCKET, Items.MILK_BUCKET, Items.STICK, Items.STICK));
        check(containers.size() == 1 && containers.getFirst().item() == Items.STICK,
                "A returned bucket is not consumed material recoverable as another filled bucket");
        check(resolve(recipe("multiple_outputs", 4, Items.DIAMOND, Items.STICK)).isEmpty(),
                "Multi-output recipes cannot pay a full material for each cheap fractional output");
        check(resolve(rich, recipe("duplicates_target", 2, Items.DIAMOND_SWORD, Items.STICK)).isEmpty(),
                "A recipe that consumes one target but creates two is an acquisition route, not a harmless upgrade");
        check(resolve(rich, recipe("upgrade", 1, Items.DIAMOND_SWORD, Items.STICK)).equals(resolve(rich)),
                "A one-for-one upgrade does not erase proven original construction");

        var opaque = new ShapelessRecipe("", CraftingBookCategory.MISC, new ItemStack(Items.DIAMOND_SWORD),
                NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.DIAMOND), Ingredient.of(Items.DIAMOND))) { };
        check(resolve(rich, new RecipeHolder<>(ResourceLocation.parse("fixture:opaque"), opaque)).isEmpty(),
                "An opaque alternate recipe cannot be treated as a proven native consumption bill");

        // Before the fix .25 diamond + 1 stick at 90% recovery returned ~.2405 diamonds
        // per salvage despite a one-item total cap. Correct proportional recovery is .2.
        var fractional = List.of(new MaterialAmount(Items.DIAMOND, .25), new MaterialAmount(Items.STICK, 1));
        RandomSource random = RandomSource.create(148090L);
        int diamonds = 0, sticks = 0;
        for (int i = 0; i < 100_000; i++) {
            var recovered = GatheringSalvageService.materialRecovery(fractional, .9, random);
            check(total(recovered) == 1, "Destructive fractional bill pays exactly the meaningful bounded total");
            diamonds += recovered.getOrDefault(Items.DIAMOND, 0);
            sticks += recovered.getOrDefault(Items.STICK, 0);
        }
        check(Math.abs(diamonds / 100_000.0 - .2) < .005 && Math.abs(sticks / 100_000.0 - .8) < .005,
                "Forced minimum and rounding preserve each material's proportional expectation: diamonds=" + diamonds);
        var larger = List.of(new MaterialAmount(Items.DIAMOND, 3), new MaterialAmount(Items.STICK, 2));
        double first = average(larger, .2), later = average(larger, .4);
        check(later - first >= .99, "Higher recovery materially improves a supported unsaturated native bill");
        for (double invalid : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY})
            check(GatheringSalvageService.materialRecovery(larger, invalid, random).isEmpty(), "Invalid recovery fails closed");
        check(GatheringSalvageService.materialRecovery(List.of(new MaterialAmount(Items.DIAMOND, 1)), .9, random).isEmpty(),
                "A one-input construction cannot guarantee an item while also losing a material unit");
        coverageLabels(sharedRich, sharedCheap);
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out))
                .println("Native salvage conservation passed: " + checks + " checks (recipe alternatives, consumed inputs, output counts, bounded expectation)");
    }
    private static void customRemainderEvidence() {
        try {
            var proof = ConstructionMaterialResolver.class.getDeclaredMethod("knownRemainderContract", Item.class);
            proof.setAccessible(true);
            check((boolean)proof.invoke(null, Items.STICK), "Ordinary item remainder contract remains supported");
            // No new registry entry is created after registry freeze. This proof concerns the
            // runtime method contract only, so uninitialized item storage is never inspected.
            var custom = ProjectileNativeInterceptionTest.instance(StackRemainderItem.class);
            check(!(boolean)proof.invoke(null, custom), "Stack-sensitive remainder overrides cannot be certified from a default stack");
            var sample = new ItemStack(Items.DIAMOND);
            check(custom.getCraftingRemainingItem(sample).is(Items.DIAMOND)
                            && custom.getRecipeRemainder(sample).is(Items.DIAMOND),
                    "The adversarial loader remainder returns the material instead of consuming it");
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    public static final class StackRemainderItem extends Item {
        private StackRemainderItem() { super(new Item.Properties()); }
        public ItemStack getCraftingRemainingItem(ItemStack stack) { return stack.copy(); }
        public boolean hasCraftingRemainingItem(ItemStack stack) { return true; }
        public ItemStack getRecipeRemainder(ItemStack stack) { return stack.copy(); }
    }

    private static void customIngredientEvidence() {
        Ingredient displayOnly = customIngredient(false);
        if (displayOnly == null) return; // Plain common fixture: actual wrappers are exercised on both loaders.
        check(displayOnly.test(new ItemStack(Items.COBBLESTONE)) && displayOnly.getItems().length == 1
                        && displayOnly.getItems()[0].is(Items.DIAMOND),
                "Custom predicate really accepts cheap cobblestone despite displaying only diamond");
        var recipe = new RecipeHolder<>(ResourceLocation.parse("fixture:display_only"), new ShapelessRecipe("",
                CraftingBookCategory.MISC, new ItemStack(Items.DIAMOND_SWORD), NonNullList.of(Ingredient.EMPTY,
                displayOnly, displayOnly, Ingredient.of(Items.STICK), Ingredient.of(Items.STICK))));
        var materials = resolve(recipe);
        check(materials.size() == 1 && materials.getFirst().item() == Items.STICK && materials.getFirst().amount() == 2,
                "Display-only custom ingredient cannot pay diamond; guaranteed ordinary sticks remain usable");
        Ingredient complete = customIngredient(true);
        if (complete != null) {
            var simple = new RecipeHolder<>(ResourceLocation.parse("fixture:complete_custom"), new ShapelessRecipe("",
                    CraftingBookCategory.MISC, new ItemStack(Items.DIAMOND_SWORD), NonNullList.of(Ingredient.EMPTY,
                    complete, complete, Ingredient.of(Items.STICK))));
            check(resolve(simple).stream().anyMatch(m -> m.item() == Items.DIAMOND && m.amount() == 2),
                    "A loader custom ingredient explicitly promising complete simple alternatives remains supported");
        }
    }

    /** Uses the actual installed loader custom-ingredient wrapper when available. */
    private static Ingredient customIngredient(boolean complete) {
        for (String name : List.of("net.neoforged.neoforge.common.crafting.ICustomIngredient",
                "net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredient")) {
            try {
                Class<?> type = Class.forName(name);
                Object custom = java.lang.reflect.Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                        (proxy, method, args) -> switch (method.getName()) {
                            case "test" -> ((ItemStack)args[0]).is(Items.DIAMOND)
                                    || !complete && ((ItemStack)args[0]).is(Items.COBBLESTONE);
                            case "getItems" -> java.util.stream.Stream.of(new ItemStack(Items.DIAMOND));
                            case "getMatchingStacks" -> List.of(new ItemStack(Items.DIAMOND));
                            case "isSimple" -> complete;
                            case "requiresTesting" -> !complete;
                            case "toVanilla" -> java.lang.reflect.InvocationHandler.invokeDefault(proxy, method, args);
                            case "equals" -> proxy == args[0];
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "toString" -> "NativeSalvageIngredient[complete=" + complete + "]";
                            default -> null;
                        });
                return (Ingredient)type.getMethod("toVanilla").invoke(custom);
            } catch (ClassNotFoundException absent) {
                // Standalone common regression has neither loader API; transformed fixtures do.
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError("Installed custom ingredient API failed", failure);
            }
        }
        return null;
    }

    /** These native recipe fixtures verify mechanical coverage without claiming item acquisition. */
    private static void coverageLabels(RecipeHolder<?> rich, RecipeHolder<?> cheap) {
        com.mistaboom.essence_ascendance.essence.EssenceTypes.init();
        com.mistaboom.essence_ascendance.stat.EssenceStats.init();
        com.mistaboom.essence_ascendance.tier.AscendanceTiers.init();
        // Unlike the catalog initializers below, this method registers on every call.
        // Standalone common starts empty; native loader fixtures already own these providers.
        if (com.mistaboom.essence_ascendance.progression.MilestoneProviderRegistry.values().isEmpty())
            com.mistaboom.essence_ascendance.progression.MilestoneProviders.init();
        com.mistaboom.essence_ascendance.progression.Milestones.init();
        com.mistaboom.essence_ascendance.skill.Skills.init();
        com.mistaboom.essence_ascendance.progression.AscendanceAdvancements.init();
        com.mistaboom.essence_ascendance.equipment.EquipmentProfiles.init();
        var runtime = com.mistaboom.essence_ascendance.balance.runtime.RuntimeBalanceDefinition.bootstrap();
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var saturated = com.mistaboom.essence_ascendance.gathering.SalvageCoverageDiagnostics.collect(
                List.of(rich, cheap), registries, runtime, java.util.Map.of());
        var first = saturated.getAsJsonArray("ranks").get(0).getAsJsonObject();
        check(!first.has("requiredAcquisitionBand") && first.has("requiredTier"), "Skill tier remains a gate without inventing an item acquisition band");
        check(first.get("status").getAsString().equals("PASS_NATIVE_MATERIAL_RECOVERY"), "First rank passes its actual native bill without source inference");
        check(saturated.getAsJsonObject("summary").get("materialBills").getAsInt() == 1, "Output index retains the actual common resolver bill");
        for (int i = 1; i < saturated.getAsJsonArray("ranks").size(); i++) {
            var row = saturated.getAsJsonArray("ranks").get(i).getAsJsonObject();
            check(row.get("status").getAsString().startsWith("FAIL_") && row.get("maskedRows").getAsInt() == 1,
                    "A confirmed masked native rank is not hidden by missing source evidence");
        }
        var mechanical = com.mistaboom.essence_ascendance.gathering.SalvageCoverageDiagnostics.collect(
                List.of(rich), registries, runtime, java.util.Map.of());
        check(mechanical.getAsJsonObject("summary").get("passedRanks").getAsInt() > 0,
                "A loaded four-unit recipe establishes mechanical recovery independently of source evidence");
        check(mechanical.getAsJsonObject("summary").get("unknownRanks").getAsInt() == 0,
                "Missing catalog/source evidence cannot create a blanket unknown mechanical result");
        check(mechanical.get("catalogPolicy").getAsString().contains("not inferred"),
                "Diagnostic explicitly separates catalog estimates from survival acquisition");
        var itemRow = java.util.stream.StreamSupport.stream(mechanical.getAsJsonArray("items").spliterator(), false)
                .map(com.google.gson.JsonElement::getAsJsonObject)
                .filter(row -> row.get("item").getAsString().equals("minecraft:diamond_sword")).findFirst().orElseThrow();
        check(itemRow.get("catalogEstimateStatus").getAsString().equals("MISSING_CATALOG_ESTIMATE")
                        && !itemRow.has("savedAccessEvidence") && !itemRow.getAsJsonArray("ranks").get(0).getAsJsonObject().has("accessStatus"),
                "Missing estimate stays explicit without restoring retired source or stage assertions");
        double initial = GatheringSalvageService.expectedMaterialRecovery(3, .215686) / 3;
        double upgraded = GatheringSalvageService.expectedMaterialRecovery(3, .360511) / 3;
        check(upgraded > initial && upgraded - initial < .1, "The guaranteed minimum can mask most of a nominal ten-point increase");
    }
    @SafeVarargs private static List<MaterialAmount> resolve(RecipeHolder<?>... recipes) {
        return ConstructionMaterialResolver.resolveRecipes(Items.DIAMOND_SWORD, List.of(recipes),
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }
    static RecipeHolder<?> recipe(String id, int count, Item... inputs) {
        var ingredients = NonNullList.<Ingredient>create();
        for (Item item : inputs) ingredients.add(Ingredient.of(item));
        return new RecipeHolder<>(ResourceLocation.parse("fixture:" + id),
                new ShapelessRecipe("", CraftingBookCategory.MISC, new ItemStack(Items.DIAMOND_SWORD, count), ingredients));
    }
    private static int total(java.util.Map<Item, Integer> recovered) { return recovered.values().stream().mapToInt(Integer::intValue).sum(); }
    private static double average(List<MaterialAmount> materials, double fraction) {
        RandomSource random = RandomSource.create(72);
        int total = 0;
        for (int i = 0; i < 1000; i++) total += total(GatheringSalvageService.materialRecovery(materials, fraction, random));
        return total / 1000.0;
    }
    private static void check(boolean pass, String message) { checks++; if (!pass) throw new AssertionError(message); }
}
