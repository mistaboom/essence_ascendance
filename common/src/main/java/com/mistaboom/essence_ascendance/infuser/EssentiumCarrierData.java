package com.mistaboom.essence_ascendance.infuser;

import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import com.mistaboom.essence_ascendance.essence.EssenceRegistry;
import com.mistaboom.essence_ascendance.pylon.EssenceFocusTier;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.List;
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
    private static final String SCHEMA_TAG = "schema";
    private static final String DENOMINATION_TAG = "ingot_denomination";
    /** Fixed accounting bound, independent of the installed balance profile. */
    public static final long MAX_STORED_AMOUNT = Long.MAX_VALUE
            / com.mistaboom.essence_ascendance.balance.economy.FractionalAmountService.SCALE;

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
        if (essence == null || grade == null || !tag.contains(AMOUNT_TAG, Tag.TAG_LONG) || amount <= 0L) {
            return Optional.empty();
        }
        if (!tag.contains(SCHEMA_TAG, Tag.TAG_INT) || tag.getInt(SCHEMA_TAG) != 1
                || !tag.contains(DENOMINATION_TAG, Tag.TAG_LONG)) return Optional.empty();
        long denomination = tag.getLong(DENOMINATION_TAG);
        if (!validDenomination(denomination)
                || amount > capacityAt((EssentiumItem) stack.getItem(), denomination)) return Optional.empty();
        return Optional.of(new Value(essence, grade, amount, denomination));
    }

    /**
     * Stored face value is financial authority, not a reference to today's mint
     * capacity. The saved denomination and exact amount are required, and the
     * fixed bound keeps extraction representable.
     */
    public static Optional<Value> readValidated(ItemStack stack) {
        Optional<Value> raw = read(stack);
        if (raw.isEmpty()) {
            return Optional.empty();
        }

        if (raw.get().amount() > MAX_STORED_AMOUNT) {
            return Optional.empty();
        }
        return raw;
    }

    public static boolean isEssentium(ItemStack stack) {
        return stack != null
                && !stack.isEmpty()
                && stack.getItem() instanceof EssentiumItem;
    }

    /** Face-value storage survives compression; only extraction pays this distinct loss. */
    public static long extractionYieldMicroUnits(Value carrier) {
        return com.mistaboom.essence_ascendance.balance.economy.FractionalAmountService.yieldForEfficiencyUnits(
                carrier.amount(), EssenceConfigManager.get().infuserBalance().carrierExtractionEfficiencyBasisPoints());
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
        if (item == null || grade == null) return ItemStack.EMPTY;
        return createAtDenomination(item, essence, grade, EssenceConfigManager.get()
                .infuserBalance().grade(grade.serializedName()).ingotCapacity());
    }

    /** Mint or transform a full carrier without repricing any saved amount. */
    public static ItemStack createAtDenomination(EssentiumItem item, EssenceDefinition essence,
                                                 EssenceFocusTier grade, long ingotDenomination) {
        if (item == null || grade == null || !isSupportedEssence(essence)
                || !validDenomination(ingotDenomination)) return ItemStack.EMPTY;
        var stack = new ItemStack(item);
        write(stack, new Value(essence, grade, capacityAt(item, ingotDenomination), ingotDenomination));
        return stack;
    }

    /** Only full carriers of their declared denomination may change form. */
    static long fullDenomination(ItemStack stack, Value value) {
        if (!(stack.getItem() instanceof EssentiumItem item)) return 0L;
        long denomination = value.ingotDenomination();
        return validDenomination(denomination) && capacityAt(item, denomination) == value.amount()
                ? denomination : 0L;
    }

    private static boolean validDenomination(long denomination) {
        return denomination > 0L && denomination % 9L == 0L && denomination <= MAX_STORED_AMOUNT / 9L;
    }

    private static long capacityAt(EssentiumItem item, long denomination) {
        return denomination * item.form().capacityNumerator() / item.form().capacityDenominator();
    }

    /** Shared Essence/tier browse order for JEI and the creative menu. */
    public static List<ItemStack> createFullVariants(EssentiumItem item, int stackCount) {
        List<ItemStack> variants = new ArrayList<>();
        for (EssenceDefinition essence : EssenceRegistry.values()) {
            for (EssenceFocusTier grade : EssenceFocusTier.values()) {
                ItemStack stack = createFull(item, essence, grade);
                if (!stack.isEmpty()) {
                    stack.setCount(stackCount);
                    variants.add(stack);
                }
            }
        }
        return List.copyOf(variants);
    }

    public static void write(ItemStack stack, Value value) {
        if (stack == null
                || stack.isEmpty()
                || !(stack.getItem() instanceof EssentiumItem)
                || value == null
                || !isSupportedEssence(value.essence())
                || value.amount() <= 0L || value.amount() > MAX_STORED_AMOUNT
                || !validDenomination(value.ingotDenomination())
                || value.amount() > capacityAt((EssentiumItem) stack.getItem(), value.ingotDenomination())) {
            throw new IllegalArgumentException("Invalid Essentium carrier data");
        }

        CustomData.update(
                DataComponents.CUSTOM_DATA,
                stack,
                outer -> outer.put(ROOT_TAG, carrierData(value).copyTag().getCompound(ROOT_TAG))
        );
    }

    /** Pure component encoding; presentation does not need to install a global balance profile. */
    public static CustomData carrierData(Value value) {
        if (value == null || !isSupportedEssence(value.essence()) || value.grade() == null
                || value.amount() <= 0 || value.amount() > MAX_STORED_AMOUNT || !validDenomination(value.ingotDenomination()))
            throw new IllegalArgumentException("Invalid Essentium carrier data");
        CompoundTag carrier = new CompoundTag();
        carrier.putString(ESSENCE_TAG, value.essence().id().toString());
        carrier.putString(GRADE_TAG, value.grade().serializedName());
        carrier.putLong(AMOUNT_TAG, value.amount());
        carrier.putInt(SCHEMA_TAG, 1);
        carrier.putLong(DENOMINATION_TAG, value.ingotDenomination());
        CompoundTag outer = new CompoundTag();
        outer.put(ROOT_TAG, carrier);
        return CustomData.of(outer);
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

    private static boolean isSupportedEssence(EssenceDefinition essence) {
        return essence != null
                && EssenceRegistry.get(essence.id())
                .filter(essence::equals)
                .isPresent();
    }

    public record Value(
            EssenceDefinition essence,
            EssenceFocusTier grade,
            long amount,
            long ingotDenomination
    ) { }
}
