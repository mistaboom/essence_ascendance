package com.mistaboom.essence_ascendance.infuser;

import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import com.mistaboom.essence_ascendance.essence.EssenceFamily;
import com.mistaboom.essence_ascendance.essence.EssenceRegistry;
import com.mistaboom.essence_ascendance.pylon.EssenceFocusTier;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.Optional;

/**
 * One shared interpretation boundary for data-bearing Essentium stacks.
 * Tooltip rendering, Infuser output, and Crucible dissolution all use this
 * helper so carrier data cannot drift between subsystems.
 */
public final class EssentiumCarrierData {

    private static final String ROOT_TAG = "essence_ascendance_essentium";
    private static final String ESSENCE_TAG = "essence";
    private static final String GRADE_TAG = "grade";
    private static final String AMOUNT_TAG = "amount";

    private EssentiumCarrierData() {
    }

    public static Optional<Value> read(ItemStack stack) {
        if (stack == null
                || stack.isEmpty()
                || !(stack.getItem() instanceof EssentiumItem)) {
            return Optional.empty();
        }

        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return Optional.empty();
        }

        CompoundTag outer = customData.copyTag();
        if (!outer.contains(ROOT_TAG, Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }

        CompoundTag tag = outer.getCompound(ROOT_TAG);
        ResourceLocation essenceId = ResourceLocation.tryParse(
                tag.getString(ESSENCE_TAG)
        );
        if (essenceId == null) {
            return Optional.empty();
        }

        EssenceDefinition essence = EssenceRegistry.get(essenceId).orElse(null);
        EssenceFocusTier grade = parseGrade(tag.getString(GRADE_TAG));
        long amount = tag.getLong(AMOUNT_TAG);
        if (essence == null || grade == null || amount <= 0L) {
            return Optional.empty();
        }

        return Optional.of(new Value(essence, grade, amount));
    }

    /**
     * Strict server-side read. Rejects carrier data that exceeds the currently
     * configured capacity for its registered carrier form/grade.
     */
    public static Optional<Value> readValidated(ItemStack stack) {
        Optional<Value> raw = read(stack);
        if (raw.isEmpty()) {
            return Optional.empty();
        }

        long capacity = capacityFor(stack, raw.get().grade());
        if (capacity <= 0L || raw.get().amount() > capacity) {
            return Optional.empty();
        }
        return raw;
    }

    public static boolean isEssentium(ItemStack stack) {
        return stack != null
                && !stack.isEmpty()
                && stack.getItem() instanceof EssentiumItem;
    }

    public static boolean isEnabled(Value value) {
        return value.essence().family() != EssenceFamily.SKILL
                || EssenceConfigManager.get().skillEssencesEnabled();
    }

    public static long capacityFor(
            ItemStack stack,
            EssenceFocusTier grade
    ) {
        if (stack == null
                || stack.isEmpty()
                || !(stack.getItem() instanceof EssentiumItem item)) {
            return 0L;
        }
        return capacityFor(item, grade);
    }

    public static long capacityFor(
            EssentiumItem item,
            EssenceFocusTier grade
    ) {
        if (item == null || grade == null) {
            return 0L;
        }

        long ingotCapacity = EssenceConfigManager.get()
                .infuserBalance()
                .grade(grade.serializedName())
                .ingotCapacity();
        try {
            long scaled = Math.multiplyExact(
                    ingotCapacity,
                    item.form().capacityNumerator()
            );
            long denominator = item.form().capacityDenominator();
            if (denominator <= 0L || scaled % denominator != 0L) {
                return 0L;
            }
            return scaled / denominator;
        } catch (ArithmeticException overflow) {
            return 0L;
        }
    }

    public static ItemStack createFull(
            EssentiumItem item,
            EssenceDefinition essence,
            EssenceFocusTier grade
    ) {
        long capacity = capacityFor(item, grade);
        if (capacity <= 0L) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = new ItemStack(item);
        write(stack, new Value(essence, grade, capacity));
        return stack;
    }

    public static void write(ItemStack stack, Value value) {
        if (stack == null
                || stack.isEmpty()
                || !(stack.getItem() instanceof EssentiumItem)
                || value == null
                || value.amount() <= 0L) {
            throw new IllegalArgumentException("Invalid Essentium carrier data");
        }

        CustomData.update(
                DataComponents.CUSTOM_DATA,
                stack,
                outer -> {
                    CompoundTag carrier = new CompoundTag();
                    carrier.putString(ESSENCE_TAG, value.essence().id().toString());
                    carrier.putString(GRADE_TAG, value.grade().serializedName());
                    carrier.putLong(AMOUNT_TAG, value.amount());
                    outer.put(ROOT_TAG, carrier);
                }
        );
    }

    private static EssenceFocusTier parseGrade(String serializedName) {
        if (serializedName == null || serializedName.isBlank()) {
            return null;
        }
        for (EssenceFocusTier tier : EssenceFocusTier.values()) {
            if (tier.serializedName().equals(serializedName)) {
                return tier;
            }
        }
        return null;
    }

    public record Value(
            EssenceDefinition essence,
            EssenceFocusTier grade,
            long amount
    ) {
    }
}
