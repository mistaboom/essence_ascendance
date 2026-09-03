package com.mistaboom.essence_ascendance.infuser;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.config.InfuserBalanceSettings;
import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import com.mistaboom.essence_ascendance.pylon.EssenceFocusData;
import com.mistaboom.essence_ascendance.pylon.EssencePylonContent;
import com.mistaboom.essence_ascendance.pylon.EssenceFocusTier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resolved Focus-upgrade recipe for the Infuser.
 *
 * Focus infusion is a STREAMED_PERSISTENT recipe: contribution progress lives
 * on the Focus workpiece itself rather than in the machine's timed progress.
 */
public record FocusInfusionRecipe(
        EssenceFocusTier targetTier,
        @Nullable EssenceFocusTier requiredInstalledTier,
        long minimumPerAttributeEssence,
        long totalEssenceRequired
) implements EssenceInfuserRecipe {

    private static final List<EssenceDefinition> CORE_ATTRIBUTE_ESSENCES = List.of(
            EssenceTypes.OFFENSE,
            EssenceTypes.DEFENSE,
            EssenceTypes.VITALITY,
            EssenceTypes.MOBILITY,
            EssenceTypes.GATHERING,
            EssenceTypes.UTILITY
    );

    public FocusInfusionRecipe {
        if (targetTier == null) {
            throw new IllegalArgumentException("Focus infusion target tier cannot be null");
        }
        if (minimumPerAttributeEssence <= 0L || totalEssenceRequired <= 0L) {
            throw new IllegalArgumentException("Focus infusion requirements must be positive");
        }
        long requiredMinimumTotal = Math.multiplyExact(
                minimumPerAttributeEssence,
                CORE_ATTRIBUTE_ESSENCES.size()
        );
        if (totalEssenceRequired < requiredMinimumTotal) {
            throw new IllegalArgumentException(
                    "Focus infusion total must cover every Attribute Essence minimum"
            );
        }
    }

    public static List<EssenceDefinition> coreAttributeEssences() {
        return CORE_ATTRIBUTE_ESSENCES;
    }

    public static boolean isWorkpiece(ItemStack stack) {
        if (!EssenceFocusData.isFocusItem(stack)) {
            return false;
        }
        EssenceFocusTier tier = EssencePylonContent.rawFocusTier(stack);
        return tier != EssenceFocusTier.TRANSCENDENT;
    }

    public static Optional<FocusInfusionRecipe> forWorkpiece(ItemStack stack) {
        if (!isWorkpiece(stack)) {
            return Optional.empty();
        }

        EssenceFocusTier current = EssencePylonContent.rawFocusTier(stack);
        EssenceFocusTier target;
        EssenceFocusTier requiredInstalled;

        if (current == null) {
            target = EssenceFocusTier.DORMANT;
            requiredInstalled = null;
        } else {
            if (current == EssenceFocusTier.TRANSCENDENT) {
                return Optional.empty();
            }
            target = EssenceFocusTier.values()[current.ordinal() + 1];
            requiredInstalled = current;
        }

        InfuserBalanceSettings.FocusUpgradeSettings settings =
                EssenceConfigManager.get()
                        .infuserBalance()
                        .focusUpgrade(target.serializedName());

        return Optional.of(new FocusInfusionRecipe(
                target,
                requiredInstalled,
                settings.minimumPerAttributeEssence(),
                settings.totalEssenceRequired()
        ));
    }

    @Override
    public ResourceLocation id() {
        return ResourceLocation.fromNamespaceAndPath(
                EssenceAscendance.MOD_ID,
                "infuser/focus_" + targetTier.serializedName()
        );
    }

    @Override
    public EssenceInfuserWorkpieceMode workpieceMode() {
        return EssenceInfuserWorkpieceMode.FOCUS;
    }

    @Override
    public EssenceInfuserProgressModel progressModel() {
        return EssenceInfuserProgressModel.STREAMED_PERSISTENT;
    }

    @Override
    public int workpieceStackLimit(ItemStack workpiece) {
        return 1;
    }

    @Override
    public EssenceFocusTier requiredInstalledFocusTier() {
        return requiredInstalledTier;
    }

    @Override
    public EssenceInfusionRequirements essenceRequirements(EssenceInfuserRecipeContext context) {
        Map<ResourceLocation, Long> minimums = new LinkedHashMap<>();
        for (EssenceDefinition essence : CORE_ATTRIBUTE_ESSENCES) {
            minimums.put(essence.id(), minimumPerAttributeEssence);
        }
        return new EssenceInfusionRequirements(minimums, totalEssenceRequired);
    }

    @Override
    public int processingTicks(EssenceInfuserRecipeContext context) {
        return 0;
    }

    @Override
    public ItemStack createOutput(EssenceInfuserRecipeContext context) {
        return createOutput();
    }

    public boolean installedFocusAllows(@Nullable EssenceFocusTier installed) {
        return requiredInstalledTier == null
                || (installed != null && installed.ordinal() >= requiredInstalledTier.ordinal());
    }

    public ItemStack createOutput() {
        ItemStack result = new ItemStack(EssencePylonContent.ESSENCE_FOCUS.get());
        EssenceFocusData.setTier(result, targetTier);
        return result;
    }

    public String workpieceName(ItemStack stack) {
        EssenceFocusTier current = EssencePylonContent.rawFocusTier(stack);
        return current == null ? "Latent" : current.displayName();
    }
}
