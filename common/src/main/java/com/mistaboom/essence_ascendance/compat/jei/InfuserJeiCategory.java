package com.mistaboom.essence_ascendance.compat.jei;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.client.MachineScreenUi;
import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import com.mistaboom.essence_ascendance.essence.EssenceRegistry;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import com.mistaboom.essence_ascendance.equipment.EquipmentTierData;
import com.mistaboom.essence_ascendance.infuser.EquipmentInfusionRecipe;
import com.mistaboom.essence_ascendance.infuser.EssenceInfuserBalance;
import com.mistaboom.essence_ascendance.infuser.EssenceInfuserContent;
import com.mistaboom.essence_ascendance.infuser.EssenceInfuserRecipe;
import com.mistaboom.essence_ascendance.infuser.EssenceInfuserRecipeContext;
import com.mistaboom.essence_ascendance.infuser.EssentiumInfusionRecipe;
import com.mistaboom.essence_ascendance.infuser.FocusInfusionRecipe;
import com.mistaboom.essence_ascendance.infuser.RepairInfusionRecipe;
import com.mistaboom.essence_ascendance.pylon.EssenceFocusData;
import com.mistaboom.essence_ascendance.pylon.EssenceFocusTier;
import com.mistaboom.essence_ascendance.pylon.EssencePylonContent;
import com.mistaboom.essence_ascendance.text.EssenceText;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** JEI presentation for all registered Essence Infuser operation families. */
public final class InfuserJeiCategory implements IRecipeCategory<InfuserJeiRecipe> {

    public static final RecipeType<InfuserJeiRecipe> RECIPE_TYPE = RecipeType.create(
            EssenceAscendance.MOD_ID,
            "infuser",
            InfuserJeiRecipe.class
    );

    // Keep the category inside JEI's normal usable recipe pane rather than
    // sizing against the whole screen. The previous 198px width pushed the
    // recipe background noticeably left on common JEI layouts.
    private static final int WIDTH = 176;
    private static final int HEIGHT = 260;
    private static final int SLOT_Y = 10;
    private static final int INPUT_X = 8;
    private static final int AUX_X = 79;
    private static final int OUTPUT_X = 150;
    private static final int TEXT_X = 10;
    private static final int DETAIL_X = 18;
    private static final int TEXT_WIDTH = WIDTH - TEXT_X * 2;
    private static final int DETAIL_WIDTH = WIDTH - DETAIL_X - TEXT_X;
    private static final int LINE_HEIGHT = 10;
    private static final int DETAILS_START_Y = 49;

    // JEI's default recipe background is light gray. Do not reuse the normal
    // machine-screen palette here; those colors are intentionally tuned for
    // Essence Ascendance's dark machine interfaces and become low-contrast in JEI.
    private static final int JEI_TEXT = 0xFF202020;
    private static final int JEI_MUTED = 0xFF555555;
    private static final int JEI_HEADER = 0xFF303030;

    private final IDrawable icon;

    public InfuserJeiCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemLike(
                EssenceInfuserContent.ESSENCE_INFUSER_ITEM.get()
        );
    }

    @Override
    public RecipeType<InfuserJeiRecipe> getRecipeType() {
        return RECIPE_TYPE;
    }

    @Override
    public Component getTitle() {
        return EssenceText.jei("infuser.title");
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public ResourceLocation getRegistryName(InfuserJeiRecipe recipe) {
        return recipe.id();
    }

    @Override
    public void setRecipe(
            IRecipeLayoutBuilder builder,
            InfuserJeiRecipe recipe,
            IFocusGroup focuses
    ) {
        EssenceInfuserRecipe resolved = recipe.resolve().orElse(null);
        if (resolved == null) {
            return;
        }

        builder.addInputSlot(INPUT_X, SLOT_Y)
                .setStandardSlotBackground()
                .setSlotName("workpiece")
                .addItemStacks(recipe.workpieceVariants());

        switch (recipe.kind()) {
            case ESSENTIUM -> setEssentiumSlots(builder, recipe, resolved);
            case FOCUS -> setFocusSlots(builder, resolved);
            case EQUIPMENT -> setEquipmentSlots(builder, recipe, resolved);
            case REPAIR -> setRepairSlots(builder, recipe, resolved);
        }
    }

    private static void setEssentiumSlots(
            IRecipeLayoutBuilder builder,
            InfuserJeiRecipe recipe,
            EssenceInfuserRecipe resolved
    ) {
        if (!(resolved instanceof EssentiumInfusionRecipe essentium)
                || recipe.targetEssence() == null) {
            return;
        }

        EssenceFocusTier installedFocus = recipe.installedFocusTier();
        if (installedFocus != null) {
            ItemStack focus = new ItemStack(EssencePylonContent.ESSENCE_FOCUS.get());
            EssenceFocusData.setTier(focus, installedFocus);
            builder.addSlot(RecipeIngredientRole.CATALYST, AUX_X, SLOT_Y)
                    .setStandardSlotBackground()
                    .setSlotName("installed_focus")
                    .addItemStack(focus);
        }

        EssenceDefinition source = representativeSource(recipe.targetEssence());
        EssenceInfuserBalance.Profile profile = EssenceInfuserBalance.profile(installedFocus);
        EssenceInfuserRecipeContext context = new EssenceInfuserRecipeContext(
                recipe.resolverWorkpiece(),
                source,
                recipe.targetEssence(),
                installedFocus,
                profile
        );
        ItemStack outputStack = essentium.createOutput(context);
        if (!outputStack.isEmpty()) {
            builder.addOutputSlot(OUTPUT_X, SLOT_Y)
                    .setOutputSlotBackground()
                    .setSlotName("output")
                    .addItemStack(outputStack);
        }
    }

    private static void setFocusSlots(
            IRecipeLayoutBuilder builder,
            EssenceInfuserRecipe resolved
    ) {
        if (!(resolved instanceof FocusInfusionRecipe focusRecipe)) {
            return;
        }

        EssenceFocusTier required = focusRecipe.requiredInstalledFocusTier();
        if (required != null) {
            List<ItemStack> installedVariants = new ArrayList<>();
            for (EssenceFocusTier tier : EssenceFocusTier.values()) {
                if (tier.ordinal() < required.ordinal()) {
                    continue;
                }
                ItemStack installed = new ItemStack(EssencePylonContent.ESSENCE_FOCUS.get());
                EssenceFocusData.setTier(installed, tier);
                installedVariants.add(installed);
            }
            builder.addSlot(RecipeIngredientRole.CATALYST, AUX_X, SLOT_Y)
                    .setStandardSlotBackground()
                    .setSlotName("installed_focus")
                    .addItemStacks(installedVariants);
        }

        builder.addOutputSlot(OUTPUT_X, SLOT_Y)
                .setOutputSlotBackground()
                .setSlotName("output")
                .addItemStack(focusRecipe.createOutput());
    }

    private static void setEquipmentSlots(
            IRecipeLayoutBuilder builder,
            InfuserJeiRecipe recipe,
            EssenceInfuserRecipe resolved
    ) {
        if (!(resolved instanceof EquipmentInfusionRecipe equipmentRecipe)) {
            return;
        }

        ItemStack matrix = new ItemStack(
                EssenceInfuserContent.ASCENDANCE_MATRIX.get(),
                equipmentRecipe.matrixCount()
        );
        builder.addInputSlot(AUX_X, SLOT_Y)
                .setStandardSlotBackground()
                .setSlotName("matrix")
                .addItemStack(matrix);

        ItemStack output = recipe.resolverWorkpiece();
        EquipmentTierData.setTier(output, equipmentRecipe.targetTier());
        builder.addOutputSlot(OUTPUT_X, SLOT_Y)
                .setOutputSlotBackground()
                .setSlotName("output")
                .addItemStack(output);
    }

    private static void setRepairSlots(
            IRecipeLayoutBuilder builder,
            InfuserJeiRecipe recipe,
            EssenceInfuserRecipe resolved
    ) {
        if (!(resolved instanceof RepairInfusionRecipe repairRecipe)) {
            return;
        }

        if (repairRecipe.latentIngotCount() > 0) {
            builder.addInputSlot(AUX_X, SLOT_Y)
                    .setStandardSlotBackground()
                    .setSlotName("repair_material")
                    .addItemStack(new ItemStack(
                            EssenceInfuserContent.LATENT_INGOT.get(),
                            repairRecipe.latentIngotCount()
                    ));
        }

        List<ItemStack> outputs = new ArrayList<>();
        for (ItemStack workpiece : recipe.workpieceVariants()) {
            RepairInfusionRecipe.forWorkpiece(workpiece).ifPresent(variantRecipe -> {
                EssenceInfuserRecipeContext context = new EssenceInfuserRecipeContext(
                        workpiece,
                        EssenceTypes.OFFENSE,
                        null,
                        null,
                        EssenceInfuserBalance.profile((EssenceFocusTier) null)
                );
                ItemStack output = variantRecipe.createOutput(context);
                if (!output.isEmpty()) {
                    outputs.add(output);
                }
            });
        }

        builder.addOutputSlot(OUTPUT_X, SLOT_Y)
                .setOutputSlotBackground()
                .setSlotName("output")
                .addItemStacks(outputs);
    }

    @Override
    public void draw(
            InfuserJeiRecipe recipe,
            IRecipeSlotsView recipeSlotsView,
            GuiGraphics graphics,
            double mouseX,
            double mouseY
    ) {
        EssenceInfuserRecipe resolved = recipe.resolve().orElse(null);
        if (resolved == null) {
            return;
        }

        Font font = Minecraft.getInstance().font;
        drawSlotLabels(graphics, font, recipe, resolved);

        int y = DETAILS_START_Y;
        switch (recipe.kind()) {
            case ESSENTIUM -> drawEssentium(recipe, resolved, graphics, font, y);
            case FOCUS -> drawFocus(resolved, graphics, font, y);
            case EQUIPMENT -> drawEquipment(recipe, resolved, graphics, font, y);
            case REPAIR -> drawRepair(resolved, graphics, font, y);
        }
    }

    private static void drawSlotLabels(
            GuiGraphics graphics,
            Font font,
            InfuserJeiRecipe recipe,
            EssenceInfuserRecipe resolved
    ) {
        drawSlotLabel(
                graphics,
                font,
                EssenceText.term("workpiece"),
                INPUT_X,
                29
        );

        Component auxiliary = switch (recipe.kind()) {
            case ESSENTIUM -> recipe.installedFocusTier() == null
                    ? Component.empty()
                    : EssenceText.term("focus");
            case FOCUS -> resolved.requiredInstalledFocusTier() == null
                    ? Component.empty()
                    : EssenceText.term("focus");
            case EQUIPMENT -> EssenceText.term("matrix");
            case REPAIR -> resolved instanceof RepairInfusionRecipe repair
                    && repair.latentIngotCount() > 0
                    ? EssenceText.term("latent_ingot")
                    : Component.empty();
        };
        if (!auxiliary.getString().isEmpty()) {
            drawSlotLabel(graphics, font, auxiliary, AUX_X, 29);
        }

        drawSlotLabel(
                graphics,
                font,
                EssenceText.term("output"),
                OUTPUT_X,
                29
        );
    }

    private static void drawEssentium(
            InfuserJeiRecipe recipe,
            EssenceInfuserRecipe resolved,
            GuiGraphics graphics,
            Font font,
            int startY
    ) {
        if (!(resolved instanceof EssentiumInfusionRecipe essentium)
                || recipe.targetEssence() == null) {
            return;
        }

        EssenceDefinition target = recipe.targetEssence();
        EssenceFocusTier installedFocus = recipe.installedFocusTier();
        EssenceInfuserBalance.Profile profile = EssenceInfuserBalance.profile(installedFocus);
        EssenceDefinition source = representativeSource(target);
        EssenceInfuserRecipeContext context = new EssenceInfuserRecipeContext(
                recipe.resolverWorkpiece(),
                source,
                target,
                installedFocus,
                profile
        );

        long cost = essentium.essenceRequirements(context).totalRequired();
        long yield = essentium.targetCapacity(context);
        double loss = Math.max(0.0D, 100.0D - profile.efficiencyPercent());

        int y = header(
                graphics,
                font,
                EssenceText.jei("infuser.essentium_header", EssenceText.essenceShort(target)),
                startY
        );
        Component requiredFocus = installedFocus == null
                ? EssenceText.term("none")
                : EssenceText.focusTier(installedFocus);
        y = detail(
                graphics,
                font,
                EssenceText.gui("required_focus_value", requiredFocus),
                y,
                JEI_TEXT,
                1
        );
        y = detail(
                graphics,
                font,
                EssenceText.jei("infuser.cost", number(cost)),
                y,
                JEI_TEXT,
                1
        );
        y = detail(
                graphics,
                font,
                EssenceText.jei("infuser.loss", percent(loss)),
                y,
                JEI_TEXT,
                1
        );
        detail(
                graphics,
                font,
                EssenceText.jei("infuser.yield", number(yield)),
                y,
                JEI_TEXT,
                1
        );
    }

    private static void drawFocus(
            EssenceInfuserRecipe resolved,
            GuiGraphics graphics,
            Font font,
            int startY
    ) {
        if (!(resolved instanceof FocusInfusionRecipe focusRecipe)) {
            return;
        }

        int y = header(
                graphics,
                font,
                EssenceText.term("focus_infusion"),
                startY
        );

        Component requiredFocus = focusRecipe.requiredInstalledTier() == null
                ? EssenceText.term("none")
                : EssenceText.jei(
                        "infuser.focus_tier_plus",
                        EssenceText.focusTier(focusRecipe.requiredInstalledTier())
                );
        y = detail(
                graphics,
                font,
                EssenceText.gui("required_focus_value", requiredFocus),
                y,
                JEI_TEXT,
                1
        );

        List<EssenceDefinition> core = FocusInfusionRecipe.coreAttributeEssences();
        for (EssenceDefinition essence : core) {
            y = detail(
                    graphics,
                    font,
                    EssenceText.jei(
                            "infuser.requirement",
                            EssenceText.essenceShort(essence),
                            number(focusRecipe.minimumPerAttributeEssence())
                    ),
                    y,
                    JEI_TEXT,
                    1
            );
        }

        long minimumTotal = Math.multiplyExact(
                focusRecipe.minimumPerAttributeEssence(),
                core.size()
        );
        long flexible = Math.max(0L, focusRecipe.totalEssenceRequired() - minimumTotal);
        y = detail(
                graphics,
                font,
                EssenceText.jei("infuser.flexible", number(flexible)),
                y,
                JEI_TEXT,
                1
        );
        detail(
                graphics,
                font,
                EssenceText.jei("infuser.total", number(focusRecipe.totalEssenceRequired())),
                y,
                JEI_TEXT,
                1
        );
    }

    private static void drawEquipment(
            InfuserJeiRecipe recipe,
            EssenceInfuserRecipe resolved,
            GuiGraphics graphics,
            Font font,
            int startY
    ) {
        if (!(resolved instanceof EquipmentInfusionRecipe equipmentRecipe)) {
            return;
        }

        int y = header(
                graphics,
                font,
                EssenceText.jei(
                        "infuser.equipment_header",
                        recipe.resolverWorkpiece().getHoverName()
                ),
                startY
        );
        y = detail(
                graphics,
                font,
                EssenceText.gui(
                        "tier_transition",
                        EssenceText.equipmentTier(equipmentRecipe.currentTier()),
                        EssenceText.equipmentTier(equipmentRecipe.targetTier())
                ),
                y,
                JEI_TEXT,
                1
        );
        y = detail(
                graphics,
                font,
                EssenceText.gui("matrices_value", equipmentRecipe.matrixCount()),
                y,
                JEI_TEXT,
                1
        );

        for (Map.Entry<ResourceLocation, Long> entry : equipmentRecipe.requirements().entrySet()) {
            EssenceDefinition essence = EssenceRegistry.get(entry.getKey()).orElse(null);
            if (essence == null) {
                continue;
            }
            y = detail(
                    graphics,
                    font,
                    EssenceText.jei(
                            "infuser.requirement",
                            EssenceText.essenceShort(essence),
                            number(entry.getValue())
                    ),
                    y,
                    JEI_TEXT,
                    1
            );
        }
    }

    private static void drawRepair(
            EssenceInfuserRecipe resolved,
            GuiGraphics graphics,
            Font font,
            int startY
    ) {
        if (!(resolved instanceof RepairInfusionRecipe repairRecipe)) {
            return;
        }

        int y = header(
                graphics,
                font,
                EssenceText.jei(
                        repairRecipe.fractured()
                                ? "infuser.repair_fractured_header"
                                : "infuser.repair_damaged_header"
                ),
                startY
        );
        y = wrapped(
                graphics,
                font,
                EssenceText.jei("infuser.repair_representative"),
                y,
                JEI_MUTED,
                2
        );
        y = wrapped(
                graphics,
                font,
                EssenceText.jei("infuser.repair_essence"),
                y,
                JEI_TEXT,
                2
        );

        long essencePerDurability = Math.max(
                1L,
                repairRecipe.essenceRequired() / Math.max(1, repairRecipe.missingDurability())
        );
        y = wrapped(
                graphics,
                font,
                EssenceText.jei("infuser.repair_formula", number(essencePerDurability)),
                y,
                JEI_TEXT,
                2
        );

        if (repairRecipe.fractured()) {
            y = wrapped(
                    graphics,
                    font,
                    EssenceText.jei(
                            "infuser.repair_fractured_material",
                            repairRecipe.latentIngotCount()
                    ),
                    y,
                    JEI_TEXT,
                    2
            );
            y = wrapped(
                    graphics,
                    font,
                    EssenceText.jei("infuser.repair_clears_fractured"),
                    y,
                    JEI_MUTED,
                    2
            );
        } else {
            y = wrapped(
                    graphics,
                    font,
                    EssenceText.jei("infuser.repair_ordinary_material"),
                    y,
                    JEI_MUTED,
                    2
            );
        }

        wrapped(
                graphics,
                font,
                EssenceText.jei("infuser.repair_same_artifact"),
                y,
                JEI_MUTED,
                2
        );
    }

    private static void drawSlotLabel(
            GuiGraphics graphics,
            Font font,
            Component text,
            int slotX,
            int y
    ) {
        int centerX = slotX + 9;
        int x = centerX - font.width(text) / 2;
        x = Math.max(TEXT_X, Math.min(x, WIDTH - TEXT_X - font.width(text)));
        graphics.drawString(font, text, x, y, JEI_MUTED, false);
    }

    private static int header(
            GuiGraphics graphics,
            Font font,
            Component text,
            int y
    ) {
        int lines = MachineScreenUi.wrapped(
                graphics,
                font,
                text,
                TEXT_X,
                y,
                TEXT_WIDTH,
                JEI_HEADER,
                LINE_HEIGHT,
                2
        );
        return y + Math.max(1, lines) * LINE_HEIGHT + 2;
    }

    private static int wrapped(
            GuiGraphics graphics,
            Font font,
            Component text,
            int y,
            int color,
            int maxLines
    ) {
        int lines = MachineScreenUi.wrapped(
                graphics,
                font,
                text,
                TEXT_X,
                y,
                TEXT_WIDTH,
                color,
                LINE_HEIGHT,
                maxLines
        );
        return y + Math.max(1, lines) * LINE_HEIGHT;
    }

    private static int detail(
            GuiGraphics graphics,
            Font font,
            Component text,
            int y,
            int color,
            int maxLines
    ) {
        int lines = MachineScreenUi.wrapped(
                graphics,
                font,
                text,
                DETAIL_X,
                y,
                DETAIL_WIDTH,
                color,
                LINE_HEIGHT,
                maxLines
        );
        return y + Math.max(1, lines) * LINE_HEIGHT;
    }

    private static EssenceDefinition representativeSource(EssenceDefinition target) {
        for (EssenceDefinition essence : EssenceRegistry.values()) {
            if (!essence.id().equals(target.id())) {
                return essence;
            }
        }
        return EssenceTypes.OFFENSE;
    }

    private static String number(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    private static String percent(double value) {
        if (Math.rint(value) == value) {
            return String.format(Locale.ROOT, "%.0f%%", value);
        }
        return String.format(Locale.ROOT, "%.2f%%", value);
    }
}
