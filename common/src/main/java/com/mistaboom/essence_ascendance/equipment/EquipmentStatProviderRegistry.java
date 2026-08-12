package com.mistaboom.essence_ascendance.equipment;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/*
 * ItemStack -> per-stat equipment profile registry.
 *
 * Multiple providers are intentionally allowed. Their results merge by MAX
 * for each stat in each activation context so native declarations, tags,
 * enchantments, data components, and integrations cannot double-dip merely
 * because several sources describe the same capability.
 */
public final class EquipmentStatProviderRegistry {

    private static final List<EquipmentStatProvider> PROVIDERS =
            new ArrayList<>();

    private static boolean initialized = false;

    private EquipmentStatProviderRegistry() {
    }

    public static void init() {
        if (initialized) {
            return;
        }

        register(NativeEquipmentStatProvider.INSTANCE);
        initialized = true;
    }

    public static void register(EquipmentStatProvider provider) {
        Objects.requireNonNull(provider, "Equipment stat provider cannot be null");
        if (!PROVIDERS.contains(provider)) {
            PROVIDERS.add(provider);
        }
    }

    public static List<EquipmentStatProvider> providers() {
        return List.copyOf(PROVIDERS);
    }

    public static EquipmentStatProfile evaluate(ItemStack stack) {
        Objects.requireNonNull(stack, "Item stack cannot be null");

        if (!initialized) {
            init();
        }

        if (stack.isEmpty()) {
            return EquipmentStatProfile.none();
        }

        EquipmentStatProfile merged = EquipmentStatProfile.none();

        for (EquipmentStatProvider provider : PROVIDERS) {
            EquipmentStatProfile result = Objects.requireNonNull(
                    provider.evaluate(stack),
                    "Equipment stat provider returned null: "
                            + provider.getClass().getName()
            );
            merged = merged.mergeMax(result);
        }

        return merged;
    }
}
