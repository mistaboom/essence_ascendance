package com.mistaboom.essence_ascendance.compat.jei;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import com.mistaboom.essence_ascendance.essence.EssenceFamily;
import com.mistaboom.essence_ascendance.essence.EssenceRegistry;
import com.mistaboom.essence_ascendance.equipment.EquipmentTier;
import com.mistaboom.essence_ascendance.equipment.EquipmentTierData;
import com.mistaboom.essence_ascendance.equipment.FracturedEquipmentData;
import com.mistaboom.essence_ascendance.infuser.EssenceInfuserContent;
import com.mistaboom.essence_ascendance.infuser.EssenceInfuserRecipe;
import com.mistaboom.essence_ascendance.infuser.EssenceInfuserRecipeRegistry;
import com.mistaboom.essence_ascendance.infuser.EssentiumItem;
import com.mistaboom.essence_ascendance.item.AscendanceItems;
import com.mistaboom.essence_ascendance.pylon.EssenceFocusData;
import com.mistaboom.essence_ascendance.pylon.EssenceFocusTier;
import com.mistaboom.essence_ascendance.pylon.EssencePylonContent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Stable JEI-facing representative for one Infuser operation.
 *
 * <p>This deliberately stores only identity and representative ItemStacks.
 * Numeric requirements are resolved through {@link EssenceInfuserRecipeRegistry}
 * when JEI lays out/draws the recipe, so JEI does not become a second Infuser
 * balance definition system.</p>
 */
public final class InfuserJeiRecipe {

    public enum Kind {
        ESSENTIUM,
        FOCUS,
        EQUIPMENT,
        REPAIR
    }

    private final ResourceLocation id;
    private final Kind kind;
    private final ItemStack resolverWorkpiece;
    private final List<ItemStack> workpieceVariants;
    @Nullable
    private final EssenceDefinition targetEssence;
    @Nullable
    private final EssenceFocusTier installedFocusTier;
    private final boolean fracturedRepair;

    private InfuserJeiRecipe(
            ResourceLocation id,
            Kind kind,
            ItemStack resolverWorkpiece,
            List<ItemStack> workpieceVariants,
            @Nullable EssenceDefinition targetEssence,
            @Nullable EssenceFocusTier installedFocusTier,
            boolean fracturedRepair
    ) {
        this.id = id;
        this.kind = kind;
        this.resolverWorkpiece = resolverWorkpiece.copy();
        this.workpieceVariants = copyStacks(workpieceVariants);
        this.targetEssence = targetEssence;
        this.installedFocusTier = installedFocusTier;
        this.fracturedRepair = fracturedRepair;
    }

    public static List<InfuserJeiRecipe> createAll() {
        List<InfuserJeiRecipe> result = new ArrayList<>();
        addEssentiumRecipes(result);
        addFocusRecipes(result);
        addEquipmentRecipes(result);
        addRepairRecipes(result);
        return List.copyOf(result);
    }

    private static void addEssentiumRecipes(List<InfuserJeiRecipe> result) {
        addEssentiumForm(
                result,
                EssentiumItem.CarrierForm.NUGGET,
                new ItemStack(EssenceInfuserContent.LATENT_NUGGET.get())
        );
        addEssentiumForm(
                result,
                EssentiumItem.CarrierForm.INGOT,
                new ItemStack(EssenceInfuserContent.LATENT_INGOT.get())
        );
        addEssentiumForm(
                result,
                EssentiumItem.CarrierForm.BLOCK,
                new ItemStack(EssenceInfuserContent.LATENT_BLOCK_ITEM.get())
        );
    }

    private static void addEssentiumForm(
            List<InfuserJeiRecipe> result,
            EssentiumItem.CarrierForm form,
            ItemStack workpiece
    ) {
        String formPath = form.name().toLowerCase(Locale.ROOT);
        for (EssenceDefinition target : EssenceRegistry.values()) {
            // No Focus is a real, distinct Dormant-grade conversion profile and
            // may have different efficiency from a Dormant Focus. Keep both
            // visible as separate JEI recipes instead of merging their costs.
            result.add(new InfuserJeiRecipe(
                    id("essentium/" + formPath + "/" + target.id().getPath() + "/no_focus"),
                    Kind.ESSENTIUM,
                    workpiece,
                    List.of(workpiece),
                    target,
                    null,
                    false
            ));

            for (EssenceFocusTier focusTier : EssenceFocusTier.values()) {
                result.add(new InfuserJeiRecipe(
                        id("essentium/" + formPath + "/" + target.id().getPath()
                                + "/" + focusTier.serializedName()),
                        Kind.ESSENTIUM,
                        workpiece,
                        List.of(workpiece),
                        target,
                        focusTier,
                        false
                ));
            }
        }
    }

    private static void addFocusRecipes(List<InfuserJeiRecipe> result) {
        for (EssenceFocusTier target : EssenceFocusTier.values()) {
            ItemStack workpiece = new ItemStack(EssencePylonContent.ESSENCE_FOCUS.get());
            int targetIndex = target.ordinal();
            if (targetIndex == 0) {
                EssenceFocusData.setLatent(workpiece);
            } else {
                EssenceFocusData.setTier(workpiece, EssenceFocusTier.values()[targetIndex - 1]);
            }

            result.add(new InfuserJeiRecipe(
                    id("focus/" + target.serializedName()),
                    Kind.FOCUS,
                    workpiece,
                    List.of(workpiece),
                    null,
                    null,
                    false
            ));
        }
    }

    private static void addEquipmentRecipes(List<InfuserJeiRecipe> result) {
        for (Item item : equipmentItems()) {
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
            for (EquipmentTier current : EquipmentTier.values()) {
                EquipmentTier target = current.next();
                if (target == null) {
                    continue;
                }

                ItemStack workpiece = new ItemStack(item);
                EquipmentTierData.setTier(workpiece, current);
                result.add(new InfuserJeiRecipe(
                        id("equipment/" + itemId.getPath() + "/" + target.serializedName()),
                        Kind.EQUIPMENT,
                        workpiece,
                        List.of(workpiece),
                        null,
                        null,
                        false
                ));
            }
        }
    }

    private static void addRepairRecipes(List<InfuserJeiRecipe> result) {
        List<ItemStack> damaged = new ArrayList<>();
        List<ItemStack> fractured = new ArrayList<>();

        for (Item item : equipmentItems()) {
            ItemStack damagedStack = new ItemStack(item);
            damagedStack.setDamageValue(1);
            damaged.add(damagedStack);

            ItemStack fracturedStack = new ItemStack(item);
            FracturedEquipmentData.markFractured(fracturedStack);
            fractured.add(fracturedStack);
        }

        result.add(new InfuserJeiRecipe(
                id("repair/damaged"),
                Kind.REPAIR,
                damaged.get(0),
                damaged,
                null,
                null,
                false
        ));
        result.add(new InfuserJeiRecipe(
                id("repair/fractured"),
                Kind.REPAIR,
                fractured.get(0),
                fractured,
                null,
                null,
                true
        ));
    }

    public ResourceLocation id() {
        return id;
    }

    public Kind kind() {
        return kind;
    }

    public ItemStack resolverWorkpiece() {
        return resolverWorkpiece.copy();
    }

    public List<ItemStack> workpieceVariants() {
        return copyStacks(workpieceVariants);
    }

    @Nullable
    public EssenceDefinition targetEssence() {
        return targetEssence;
    }

    @Nullable
    public EssenceFocusTier installedFocusTier() {
        return installedFocusTier;
    }

    public boolean fracturedRepair() {
        return fracturedRepair;
    }

    public boolean isSkillEssentiumRecipe() {
        return kind == Kind.ESSENTIUM
                && targetEssence != null
                && targetEssence.family() == EssenceFamily.SKILL;
    }

    public Optional<EssenceInfuserRecipe> resolve() {
        return EssenceInfuserRecipeRegistry.resolve(resolverWorkpiece.copy());
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                EssenceAscendance.MOD_ID,
                "jei/infuser/" + path
        );
    }

    private static List<Item> equipmentItems() {
        return List.of(
                AscendanceItems.ASCENDANCE_HELMET.get(),
                AscendanceItems.ASCENDANCE_CHESTPLATE.get(),
                AscendanceItems.ASCENDANCE_LEGGINGS.get(),
                AscendanceItems.ASCENDANCE_BOOTS.get(),
                AscendanceItems.ASCENDANCE_MELEE_WEAPON.get(),
                AscendanceItems.ASCENDANCE_RANGED_WEAPON.get(),
                AscendanceItems.ASCENDANCE_CASTER.get(),
                AscendanceItems.ASCENDANCE_PICKAXE.get(),
                AscendanceItems.ASCENDANCE_AXE.get(),
                AscendanceItems.ASCENDANCE_SHOVEL.get(),
                AscendanceItems.ASCENDANCE_HOE.get(),
                AscendanceItems.ASCENDANCE_SHIELD.get()
        );
    }

    private static List<ItemStack> copyStacks(List<ItemStack> stacks) {
        List<ItemStack> copies = new ArrayList<>(stacks.size());
        for (ItemStack stack : stacks) {
            copies.add(stack.copy());
        }
        return List.copyOf(copies);
    }
}
