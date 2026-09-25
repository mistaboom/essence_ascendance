package com.mistaboom.essence_ascendance.compat.jei;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.client.AscendanceNexusScreen;
import com.mistaboom.essence_ascendance.client.EssenceCrucibleScreen;
import com.mistaboom.essence_ascendance.client.EssenceInfuserScreen;
import com.mistaboom.essence_ascendance.client.EssencePylonScreen;
import com.mistaboom.essence_ascendance.client.ore.LatentOreClientCatalog;
import com.mistaboom.essence_ascendance.ore.LatentOreHost;
import com.mistaboom.essence_ascendance.infuser.EssenceInfuserContent;
import com.mistaboom.essence_ascendance.infuser.EssentiumBlockCompactingRecipe;
import com.mistaboom.essence_ascendance.infuser.EssentiumBlockUncompactingRecipe;
import com.mistaboom.essence_ascendance.infuser.EssentiumCarrierData;
import com.mistaboom.essence_ascendance.infuser.EssentiumItem;
import com.mistaboom.essence_ascendance.infuser.EssentiumNuggetCompactingRecipe;
import com.mistaboom.essence_ascendance.infuser.EssentiumNuggetUncompactingRecipe;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.gui.ingredient.ICraftingGridHelper;
import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.extensions.vanilla.crafting.ICraftingCategoryExtension;
import mezz.jei.api.registration.IExtraIngredientRegistration;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.api.registration.IVanillaCategoryExtensionRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.List;

/**
 * Optional JEI integration.
 *
 * Normal machine popups intentionally render in front of JEI. While one is
 * open, its exact dynamic bounds are also reported as GUI-owned exclusion
 * space so JEI cannot consume mouse clicks through the foreground popup.
 * The Nexus remains the full-screen exception.
 *
 * Essentium carriers are data-bearing ItemStacks. JEI therefore needs explicit
 * subtype/ingredient/category-extension information so it can distinguish and
 * display the real Essence + tier variants used by dynamic crafting recipes.
 */
@JeiPlugin
@Environment(EnvType.CLIENT)
public final class EssenceAscendanceJeiPlugin implements IModPlugin {

    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(
                    EssenceAscendance.MOD_ID,
                    "gui_layout"
            );

    // JEI exposes vanilla registry tags as browse-only recipe categories. The
    // public runtime visibility API operates at category scope, so suppressing
    // these removes the Item Tags / Block Tags browse tabs globally while this
    // JEI plugin is active. It does not remove or alter any underlying Minecraft
    // item/block tags, mining rules, enchantability rules, or equipment rules.
    private static final ResourceLocation JEI_ITEM_TAG_RECIPES =
            ResourceLocation.fromNamespaceAndPath(
                    "minecraft",
                    "tag_recipes/item"
            );
    private static final ResourceLocation JEI_BLOCK_TAG_RECIPES =
            ResourceLocation.fromNamespaceAndPath(
                    "minecraft",
                    "tag_recipes/block"
            );

    private static final ISubtypeInterpreter<ItemStack> ESSENTIUM_SUBTYPE =
            new ISubtypeInterpreter<>() {
                @Override
                public Object getSubtypeData(ItemStack stack, UidContext context) {
                    return EssentiumCarrierData.read(stack)
                            .map(value -> new CarrierSubtype(
                                    value.essence().id(),
                                    value.grade().serializedName(),
                                    value.amount()
                            ))
                            .orElse(null);
                }

                @Override
                @SuppressWarnings("removal")
                public String getLegacyStringSubtypeInfo(ItemStack stack, UidContext context) {
                    return EssentiumCarrierData.read(stack)
                            .map(value -> value.essence().id()
                                    + "|" + value.grade().serializedName()
                                    + "|" + value.amount())
                            .orElse("");
                }
            };

    // Hosts distinguish browsing/bookmarks, but every host uses the same tag-based recipes.
    static final ISubtypeInterpreter<ItemStack> LATENT_ORE_SUBTYPE = new ISubtypeInterpreter<>() {
        @Override
        public Object getSubtypeData(ItemStack stack, UidContext context) {
            return context == UidContext.Ingredient ? LatentOreHost.read(stack).orElse(null) : null;
        }

        @Override
        @SuppressWarnings("removal")
        public String getLegacyStringSubtypeInfo(ItemStack stack, UidContext context) {
            return "";
        }
    };

    private IJeiRuntime runtime;
    private List<ItemStack> listedOreVariants = List.of();

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        registration.registerSubtypeInterpreter(EssenceInfuserContent.LATENT_ORE_ITEM.get(), LATENT_ORE_SUBTYPE);
        registration.registerSubtypeInterpreter(
                EssenceInfuserContent.ESSENTIUM_NUGGET.get(),
                ESSENTIUM_SUBTYPE
        );
        registration.registerSubtypeInterpreter(
                EssenceInfuserContent.ESSENTIUM_INGOT.get(),
                ESSENTIUM_SUBTYPE
        );
        registration.registerSubtypeInterpreter(
                EssenceInfuserContent.ESSENTIUM_BLOCK.get(),
                ESSENTIUM_SUBTYPE
        );
    }

    @Override
    public void registerExtraIngredients(IExtraIngredientRegistration registration) {
        List<ItemStack> variants = new ArrayList<>();
        variants.addAll(carrierVariants(EssentiumItem.CarrierForm.NUGGET, 1));
        variants.addAll(carrierVariants(EssentiumItem.CarrierForm.INGOT, 1));
        variants.addAll(carrierVariants(EssentiumItem.CarrierForm.BLOCK, 1));
        variants.addAll(LatentOreHost.creativeStacks());
        registration.addExtraItemStacks(variants);
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(
                new InfuserJeiCategory(
                        registration.getJeiHelpers().getGuiHelper()
                )
        );
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(
                InfuserJeiCategory.RECIPE_TYPE,
                InfuserJeiRecipe.createAll()
        );
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalysts(
                InfuserJeiCategory.RECIPE_TYPE,
                EssenceInfuserContent.ESSENCE_INFUSER_ITEM.get()
        );
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        hideTagInformationCategories(jeiRuntime);
        runtime = jeiRuntime;
        listedOreVariants = jeiRuntime.getIngredientManager().getAllItemStacks().stream()
                .filter(stack -> stack.is(EssenceInfuserContent.LATENT_ORE_ITEM.get()))
                .map(ItemStack::copy).toList();
        LatentOreClientCatalog.setIngredientRefreshListener(this::refreshLatentOreIngredients);
        refreshLatentOreIngredients();
    }

    @Override
    public void onRuntimeUnavailable() {
        LatentOreClientCatalog.setIngredientRefreshListener(null);
        runtime = null;
        listedOreVariants = List.of();
    }

    /** Reconcile either startup order: JEI may start before or after the server catalog arrives. */
    private void refreshLatentOreIngredients() {
        if (runtime == null) return;
        var manager = runtime.getIngredientManager();
        var desired = LatentOreHost.creativeStacks();
        var existing = listedOreVariants;
        var removed = existing.stream().filter(stack -> desired.stream()
                .noneMatch(wanted -> ItemStack.isSameItemSameComponents(stack, wanted))).toList();
        var added = desired.stream().filter(stack -> existing.stream()
                .noneMatch(known -> ItemStack.isSameItemSameComponents(stack, known))).toList();
        if (!removed.isEmpty()) manager.removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, removed);
        if (!added.isEmpty()) manager.addIngredientsAtRuntime(VanillaTypes.ITEM_STACK, added);
        listedOreVariants = desired;
    }

    @Override
    public void registerVanillaCategoryExtensions(
            IVanillaCategoryExtensionRegistration registration
    ) {
        var crafting = registration.getCraftingCategory();

        crafting.addExtension(
                EssentiumNuggetCompactingRecipe.class,
                new EssentiumCarrierCraftingExtension(
                        EssentiumItem.CarrierForm.NUGGET,
                        9,
                        EssentiumItem.CarrierForm.INGOT,
                        1,
                        3,
                        3
                )
        );
        crafting.addExtension(
                EssentiumNuggetUncompactingRecipe.class,
                new EssentiumCarrierCraftingExtension(
                        EssentiumItem.CarrierForm.INGOT,
                        1,
                        EssentiumItem.CarrierForm.NUGGET,
                        9,
                        1,
                        1
                )
        );
        crafting.addExtension(
                EssentiumBlockCompactingRecipe.class,
                new EssentiumCarrierCraftingExtension(
                        EssentiumItem.CarrierForm.INGOT,
                        9,
                        EssentiumItem.CarrierForm.BLOCK,
                        1,
                        3,
                        3
                )
        );
        crafting.addExtension(
                EssentiumBlockUncompactingRecipe.class,
                new EssentiumCarrierCraftingExtension(
                        EssentiumItem.CarrierForm.BLOCK,
                        1,
                        EssentiumItem.CarrierForm.INGOT,
                        9,
                        1,
                        1
                )
        );
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        /*
         * The Nexus consumes the full logical screen. Mark that complete area
         * as GUI-owned so JEI does not place its ingredient list over the
         * progression tracks while remaining an optional compile-time bridge.
         */
        registration.addGuiContainerHandler(
                AscendanceNexusScreen.class,
                new IGuiContainerHandler<AscendanceNexusScreen>() {
                    @Override
                    public List<Rect2i> getGuiExtraAreas(
                            AscendanceNexusScreen screen
                    ) {
                        return List.of(
                                new Rect2i(
                                        0,
                                        0,
                                        screen.width,
                                        screen.height
                                )
                        );
                    }
                }
        );

        // These areas exist only while the foreground popup is open. Reporting
        // them through JEI's supported GUI handler prevents ingredient/bookmark
        // input from stealing the click before the screen's popup controls see it.
        registration.addGuiContainerHandler(
                EssenceCrucibleScreen.class,
                new IGuiContainerHandler<EssenceCrucibleScreen>() {
                    @Override
                    public List<Rect2i> getGuiExtraAreas(EssenceCrucibleScreen screen) {
                        return screen.overlayInteractionAreas();
                    }
                }
        );
        registration.addGuiContainerHandler(
                EssencePylonScreen.class,
                new IGuiContainerHandler<EssencePylonScreen>() {
                    @Override
                    public List<Rect2i> getGuiExtraAreas(EssencePylonScreen screen) {
                        return screen.overlayInteractionAreas();
                    }
                }
        );
        registration.addGuiContainerHandler(
                EssenceInfuserScreen.class,
                new IGuiContainerHandler<EssenceInfuserScreen>() {
                    @Override
                    public List<Rect2i> getGuiExtraAreas(EssenceInfuserScreen screen) {
                        return screen.overlayInteractionAreas();
                    }
                }
        );
    }

    private static List<ItemStack> carrierVariants(
            EssentiumItem.CarrierForm form,
            int stackCount
    ) {
        EssentiumItem item = itemFor(form);
        if (item == null) {
            return List.of();
        }

        return EssentiumCarrierData.createFullVariants(item, stackCount);
    }

    private static void hideTagInformationCategories(IJeiRuntime jeiRuntime) {
        hideRecipeCategoryIfPresent(jeiRuntime, JEI_ITEM_TAG_RECIPES);
        hideRecipeCategoryIfPresent(jeiRuntime, JEI_BLOCK_TAG_RECIPES);
    }

    private static void hideRecipeCategoryIfPresent(
            IJeiRuntime jeiRuntime,
            ResourceLocation recipeTypeId
    ) {
        jeiRuntime.getRecipeManager()
                .getRecipeType(recipeTypeId)
                .ifPresent(recipeType ->
                        jeiRuntime.getRecipeManager().hideRecipeCategory(recipeType)
                );
    }

    private static EssentiumItem itemFor(EssentiumItem.CarrierForm form) {
        return switch (form) {
            case NUGGET -> (EssentiumItem) EssenceInfuserContent.ESSENTIUM_NUGGET.get();
            case INGOT -> (EssentiumItem) EssenceInfuserContent.ESSENTIUM_INGOT.get();
            case BLOCK -> (EssentiumItem) EssenceInfuserContent.ESSENTIUM_BLOCK.get();
        };
    }

    private record CarrierSubtype(
            ResourceLocation essenceId,
            String grade,
            long amount
    ) {
    }

    /**
     * JEI view of the four dynamic data-preserving Essentium crafting recipes.
     * Every input/output list is generated in the same Essence/tier order, so
     * JEI cycles matching carrier variants together instead of showing bare,
     * data-less item forms that the real recipe correctly rejects.
     */
    private static final class EssentiumCarrierCraftingExtension
            implements ICraftingCategoryExtension<CraftingRecipe> {

        private final EssentiumItem.CarrierForm inputForm;
        private final int inputSlots;
        private final EssentiumItem.CarrierForm outputForm;
        private final int outputCount;
        private final int width;
        private final int height;

        private EssentiumCarrierCraftingExtension(
                EssentiumItem.CarrierForm inputForm,
                int inputSlots,
                EssentiumItem.CarrierForm outputForm,
                int outputCount,
                int width,
                int height
        ) {
            this.inputForm = inputForm;
            this.inputSlots = inputSlots;
            this.outputForm = outputForm;
            this.outputCount = outputCount;
            this.width = width;
            this.height = height;
        }

        @Override
        public void setRecipe(
                RecipeHolder<CraftingRecipe> recipeHolder,
                IRecipeLayoutBuilder builder,
                ICraftingGridHelper craftingGridHelper,
                IFocusGroup focuses
        ) {
            List<ItemStack> inputVariants = carrierVariants(inputForm, 1);
            List<List<ItemStack>> inputs = new ArrayList<>();
            for (int slot = 0; slot < inputSlots; slot++) {
                inputs.add(inputVariants);
            }

            craftingGridHelper.createAndSetInputs(
                    builder,
                    inputs,
                    width,
                    height
            );
            craftingGridHelper.createAndSetOutputs(
                    builder,
                    carrierVariants(outputForm, outputCount)
            );
        }

        @Override
        public int getWidth(RecipeHolder<CraftingRecipe> recipeHolder) {
            return width;
        }

        @Override
        public int getHeight(RecipeHolder<CraftingRecipe> recipeHolder) {
            return height;
        }
    }
}
