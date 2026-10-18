package com.mistaboom.essence_ascendance.compat.jei;

import com.mistaboom.essence_ascendance.item.CreativeVariantRegistry;
import com.mistaboom.essence_ascendance.equipment.EquipmentTierData;
import com.mistaboom.essence_ascendance.pylon.EssenceFocusData;
import com.mistaboom.essence_ascendance.pylon.EssenceFocusItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.IdentityHashMap;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.function.Function;

/** Preserve the creative position of our variant families when more subtypes arrive after startup. */
public final class JeiCatalogOrder {
    public static final JeiCatalogOrder INSTANCE = new JeiCatalogOrder(
            CreativeVariantRegistry::variantItems, CreativeVariantRegistry::revision);
    private final Supplier<Set<Item>> items;
    private final LongSupplier revision;
    private final Map<Item, Integer> firstIndices = new IdentityHashMap<>();
    private Set<Item> variantItems = Set.of();
    private long indexedRevision = Long.MIN_VALUE;
    private boolean runtimeReady;
    private boolean sortPending;

    JeiCatalogOrder(Supplier<Set<Item>> items, LongSupplier revision) {
        this.items = items;
        this.revision = revision;
    }

    /** Optional JEI bridge; recipe subtype identities and native creative positions stay intact. */
    public interface IngredientAccess { ItemStack essenceAscendance$catalogStack(); }

    public static <T> Comparator<T> creativeComparator(Comparator<T> nativeOrder, Function<T, ItemStack> stack) {
        return nativeOrder.thenComparingInt(value -> tierOrder(stack.apply(value)));
    }

    public static int tierOrder(ItemStack stack) {
        if (EquipmentTierData.isAscendanceEquipment(stack)) return EquipmentTierData.tier(stack).ordinal();
        if (!stack.isEmpty() && stack.getItem() instanceof EssenceFocusItem) {
            var tier = EssenceFocusData.tierFromData(stack.get(DataComponents.CUSTOM_DATA));
            return tier == null ? 0 : tier.ordinal() + 1;
        }
        return 0;
    }

    /** Called at element creation, never from a comparator: ordering keys remain stable during sorting. */
    public int createdIndex(ItemStack stack, int nativeIndex) {
        if (stack.isEmpty()) return nativeIndex;
        long current = revision.getAsLong();
        if (indexedRevision != current) {
            variantItems = Set.copyOf(items.get());
            indexedRevision = current;
        }
        if (!variantItems.contains(stack.getItem())) return nativeIndex;
        Integer first = firstIndices.putIfAbsent(stack.getItem(), nativeIndex);
        if (runtimeReady && first != null) sortPending = true;
        return first == null ? nativeIndex : first;
    }

    public void runtimeAvailable() { runtimeReady = true; sortPending = false; }
    public boolean takePendingSort() {
        boolean pending = sortPending;
        sortPending = false;
        return pending;
    }
    public void clear() {
        firstIndices.clear();
        variantItems = Set.of();
        indexedRevision = Long.MIN_VALUE;
        runtimeReady = false;
        sortPending = false;
    }
}
