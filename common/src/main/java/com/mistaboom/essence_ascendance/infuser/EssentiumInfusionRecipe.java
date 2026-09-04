package com.mistaboom.essence_ascendance.infuser;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.Optional;

/** Resolved Latent carrier -> standardized Essentium conversion recipe. */
public record EssentiumInfusionRecipe(
        ResourceLocation id,
        Item inputItem,
        EssentiumItem outputItem
) implements EssenceInfuserRecipe {

    private static final ResourceLocation NUGGET_ID = ResourceLocation.fromNamespaceAndPath(
            EssenceAscendance.MOD_ID,
            "infuser/essentium_nugget"
    );
    private static final ResourceLocation INGOT_ID = ResourceLocation.fromNamespaceAndPath(
            EssenceAscendance.MOD_ID,
            "infuser/essentium_ingot"
    );
    private static final ResourceLocation BLOCK_ID = ResourceLocation.fromNamespaceAndPath(
            EssenceAscendance.MOD_ID,
            "infuser/essentium_block"
    );

    public static boolean isWorkpiece(ItemStack stack) {
        return stack != null
                && !stack.isEmpty()
                && (stack.is(EssenceInfuserContent.LATENT_NUGGET.get())
                || stack.is(EssenceInfuserContent.LATENT_INGOT.get())
                || stack.is(EssenceInfuserContent.LATENT_BLOCK_ITEM.get()));
    }

    public static Optional<EssentiumInfusionRecipe> forWorkpiece(ItemStack stack) {
        if (!isWorkpiece(stack)) {
            return Optional.empty();
        }
        if (stack.is(EssenceInfuserContent.LATENT_NUGGET.get())) {
            return create(
                    NUGGET_ID,
                    EssenceInfuserContent.LATENT_NUGGET.get(),
                    EssenceInfuserContent.ESSENTIUM_NUGGET.get()
            );
        }
        if (stack.is(EssenceInfuserContent.LATENT_INGOT.get())) {
            return create(
                    INGOT_ID,
                    EssenceInfuserContent.LATENT_INGOT.get(),
                    EssenceInfuserContent.ESSENTIUM_INGOT.get()
            );
        }
        if (stack.is(EssenceInfuserContent.LATENT_BLOCK_ITEM.get())) {
            return create(
                    BLOCK_ID,
                    EssenceInfuserContent.LATENT_BLOCK_ITEM.get(),
                    EssenceInfuserContent.ESSENTIUM_BLOCK.get()
            );
        }
        return Optional.empty();
    }

    private static Optional<EssentiumInfusionRecipe> create(
            ResourceLocation id,
            Item input,
            Item output
    ) {
        if (!(output instanceof EssentiumItem essentium)) {
            return Optional.empty();
        }
        return Optional.of(new EssentiumInfusionRecipe(id, input, essentium));
    }

    @Override
    public EssenceInfuserWorkpieceMode workpieceMode() {
        return EssenceInfuserWorkpieceMode.ESSENTIUM;
    }

    @Override
    public EssenceInfuserProgressModel progressModel() {
        return EssenceInfuserProgressModel.TIMED_ATOMIC;
    }

    @Override
    public boolean allowsAutomationInput() {
        return true;
    }

    @Override
    public EssenceInfusionRequirements essenceRequirements(EssenceInfuserRecipeContext context) {
        EssenceDefinition source = context.sourceEssence();
        long targetCapacity = targetCapacity(context);
        long sourceRequired = EssenceInfuserBalance.requiredSource(
                targetCapacity,
                context.profile().efficiencyBasisPoints()
        );
        if (source == null || sourceRequired <= 0L) {
            return EssenceInfusionRequirements.none();
        }
        return new EssenceInfusionRequirements(
                Map.of(source.id(), sourceRequired),
                sourceRequired
        );
    }

    @Override
    public long infusionWork(EssenceInfuserRecipeContext context) {
        return targetCapacity(context);
    }

    @Override
    public ItemStack createOutput(EssenceInfuserRecipeContext context) {
        EssenceDefinition target = context.targetEssence();
        if (target == null) {
            return ItemStack.EMPTY;
        }
        return EssentiumCarrierData.createFull(
                outputItem,
                target,
                context.profile().grade()
        );
    }

    public long targetCapacity(EssenceInfuserRecipeContext context) {
        return EssentiumCarrierData.capacityFor(outputItem, context.profile().grade());
    }
}
