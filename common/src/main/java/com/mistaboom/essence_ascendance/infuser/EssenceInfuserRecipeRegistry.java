package com.mistaboom.essence_ascendance.infuser;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.equipment.EquipmentTierData;
import com.mistaboom.essence_ascendance.pylon.EssenceFocusData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * Small central registry of Infuser recipe-family resolvers.
 *
 * Workpiece recognition is intentionally separate from full recipe resolution.
 * The client can therefore choose the correct GUI mode and slot behavior from
 * the item alone without consulting server balance configuration. Full recipe
 * resolution remains available to the authoritative machine logic.
 */
public final class EssenceInfuserRecipeRegistry {

    private static final List<RegisteredResolver> RESOLVERS = new ArrayList<>();

    static {
        register(
                ResourceLocation.fromNamespaceAndPath(
                        EssenceAscendance.MOD_ID,
                        "essentium_carrier"
                ),
                EssenceInfuserWorkpieceMode.ESSENTIUM,
                EssentiumInfusionRecipe::isWorkpiece,
                stack -> Math.max(1, stack.getMaxStackSize()),
                true,
                EssentiumInfusionRecipe::forWorkpiece
        );
        register(
                ResourceLocation.fromNamespaceAndPath(
                        EssenceAscendance.MOD_ID,
                        "focus_upgrade"
                ),
                EssenceInfuserWorkpieceMode.FOCUS,
                EssenceFocusData::isFocusItem,
                stack -> 1,
                false,
                FocusInfusionRecipe::forWorkpiece
        );
        register(
                ResourceLocation.fromNamespaceAndPath(
                        EssenceAscendance.MOD_ID,
                        "equipment_upgrade"
                ),
                EssenceInfuserWorkpieceMode.EQUIPMENT,
                EquipmentTierData::isAscendanceEquipment,
                stack -> 1,
                false,
                EquipmentInfusionRecipe::forWorkpiece
        );
    }

    private EssenceInfuserRecipeRegistry() {
    }

    /**
     * Registers one recipe family. First registered match wins deterministically.
     * Registration is intended for common initialization, not live mutation.
     */
    public static synchronized void register(
            ResourceLocation resolverId,
            EssenceInfuserWorkpieceMode mode,
            Predicate<ItemStack> matcher,
            ToIntFunction<ItemStack> stackLimit,
            boolean allowsAutomationInput,
            Resolver resolver
    ) {
        if (resolverId == null
                || mode == null
                || mode == EssenceInfuserWorkpieceMode.NONE
                || matcher == null
                || stackLimit == null
                || resolver == null) {
            throw new IllegalArgumentException("Invalid Infuser recipe resolver registration");
        }
        for (RegisteredResolver existing : RESOLVERS) {
            if (existing.id().equals(resolverId)) {
                throw new IllegalArgumentException(
                        "Duplicate Infuser recipe resolver id: " + resolverId
                );
            }
        }
        RESOLVERS.add(new RegisteredResolver(
                resolverId,
                mode,
                matcher,
                stackLimit,
                allowsAutomationInput,
                resolver
        ));
    }

    public static Optional<EssenceInfuserRecipe> resolve(ItemStack workpiece) {
        RegisteredResolver registered = matchingResolver(workpiece);
        if (registered == null) {
            return Optional.empty();
        }
        Optional<? extends EssenceInfuserRecipe> resolved =
                registered.resolver().resolve(workpiece);
        if (resolved.isEmpty()) {
            return Optional.empty();
        }
        EssenceInfuserRecipe recipe = resolved.get();
        if (recipe.workpieceMode() != registered.mode()) {
            throw new IllegalStateException(
                    "Infuser recipe " + recipe.id()
                            + " resolved with mode " + recipe.workpieceMode()
                            + " but family " + registered.id()
                            + " is registered as " + registered.mode()
            );
        }
        return Optional.of(recipe);
    }

    public static EssenceInfuserWorkpieceMode modeFor(ItemStack workpiece) {
        RegisteredResolver registered = matchingResolver(workpiece);
        return registered == null ? EssenceInfuserWorkpieceMode.NONE : registered.mode();
    }

    public static boolean isValidWorkpiece(ItemStack workpiece) {
        return matchingResolver(workpiece) != null;
    }

    public static int workpieceStackLimit(ItemStack workpiece) {
        RegisteredResolver registered = matchingResolver(workpiece);
        if (registered == null) {
            return Math.max(1, workpiece == null ? 1 : workpiece.getMaxStackSize());
        }
        return Math.max(1, registered.stackLimit().applyAsInt(workpiece));
    }

    public static boolean allowsAutomationInput(ItemStack workpiece) {
        RegisteredResolver registered = matchingResolver(workpiece);
        return registered != null && registered.allowsAutomationInput();
    }

    private static RegisteredResolver matchingResolver(ItemStack workpiece) {
        if (workpiece == null || workpiece.isEmpty()) {
            return null;
        }
        List<RegisteredResolver> snapshot;
        synchronized (EssenceInfuserRecipeRegistry.class) {
            snapshot = List.copyOf(RESOLVERS);
        }
        for (RegisteredResolver registered : snapshot) {
            if (registered.matcher().test(workpiece)) {
                return registered;
            }
        }
        return null;
    }

    @FunctionalInterface
    public interface Resolver {
        Optional<? extends EssenceInfuserRecipe> resolve(ItemStack workpiece);
    }

    private record RegisteredResolver(
            ResourceLocation id,
            EssenceInfuserWorkpieceMode mode,
            Predicate<ItemStack> matcher,
            ToIntFunction<ItemStack> stackLimit,
            boolean allowsAutomationInput,
            Resolver resolver
    ) {
    }
}
