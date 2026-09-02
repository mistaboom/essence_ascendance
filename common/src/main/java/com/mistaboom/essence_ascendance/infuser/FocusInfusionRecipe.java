package com.mistaboom.essence_ascendance.infuser;

import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.config.InfuserBalanceSettings;
import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import com.mistaboom.essence_ascendance.pylon.EssencePylonContent;
import com.mistaboom.essence_ascendance.pylon.EssencePylonFocusTier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Small, explicit Focus-upgrade recipe model for the Infuser.
 *
 * Focus infusion is deliberately separate from Source -> Target Essentium
 * conversion: all six core Attribute Essences participate automatically and
 * Skill Essences are never considered.
 */
public record FocusInfusionRecipe(
        EssencePylonFocusTier targetTier,
        @Nullable EssencePylonFocusTier requiredInstalledTier,
        long minimumPerAttributeEssence,
        long totalEssenceRequired
) {

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

    public static Optional<FocusInfusionRecipe> forWorkpiece(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }

        EssencePylonFocusTier target;
        EssencePylonFocusTier requiredInstalled;

        if (stack.is(EssenceInfuserContent.LATENT_FOCUS.get())) {
            target = EssencePylonFocusTier.DORMANT;
            requiredInstalled = null;
        } else {
            EssencePylonFocusTier current = EssencePylonContent.rawFocusTier(stack);
            if (current == null || current == EssencePylonFocusTier.TRANSCENDENT) {
                return Optional.empty();
            }
            target = EssencePylonFocusTier.values()[current.ordinal() + 1];
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

    public boolean installedFocusAllows(@Nullable EssencePylonFocusTier installed) {
        return requiredInstalledTier == null
                || (installed != null && installed.ordinal() >= requiredInstalledTier.ordinal());
    }

    public ItemStack createOutput() {
        return new ItemStack(EssencePylonContent.itemForTier(targetTier));
    }

    public String workpieceName(ItemStack stack) {
        if (stack != null && stack.is(EssenceInfuserContent.LATENT_FOCUS.get())) {
            return "Latent";
        }
        EssencePylonFocusTier current = EssencePylonContent.rawFocusTier(stack);
        return current == null ? "Unknown" : current.displayName();
    }
}
