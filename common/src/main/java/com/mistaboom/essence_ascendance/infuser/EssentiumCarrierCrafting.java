package com.mistaboom.essence_ascendance.infuser;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;

/** Exact 9:1 carrier exchange; the installed mint profile never reprices inputs. */
final class EssentiumCarrierCrafting {
    private EssentiumCarrierCrafting() { }

    static ItemStack convert(CraftingInput input, Item source, Item target, boolean compact) {
        if (!(source instanceof EssentiumItem sourceCarrier) || !(target instanceof EssentiumItem targetCarrier))
            return ItemStack.EMPTY;
        EssentiumCarrierData.Value common = null;
        long denomination = 0;
        int occupied = 0;
        for (int slot = 0; slot < input.size(); slot++) {
            var stack = input.getItem(slot);
            if (stack.isEmpty()) continue;
            if (!stack.is(source)) return ItemStack.EMPTY;
            var value = EssentiumCarrierData.readValidated(stack).orElse(null);
            if (value == null) return ItemStack.EMPTY;
            long candidate = EssentiumCarrierData.fullDenomination(stack, value);
            if (candidate == 0) return ItemStack.EMPTY;
            if (common == null) { common = value; denomination = candidate; }
            else if (!common.essence().equals(value.essence()) || common.grade() != value.grade()
                    || denomination != candidate || common.amount() != value.amount()) return ItemStack.EMPTY;
            occupied++;
        }
        if (common == null || occupied != (compact ? 9 : 1)) return ItemStack.EMPTY;
        var result = EssentiumCarrierData.createAtDenomination(targetCarrier, common.essence(), common.grade(), denomination);
        if (result.isEmpty()) return result;
        result.setCount(compact ? 1 : 9);
        // Keep the form relationship explicit even if another caller uses this helper later.
        try {
            long inputValue = Math.multiplyExact(common.amount(), occupied);
            long outputValue = Math.multiplyExact(EssentiumCarrierData.readValidated(result).orElseThrow().amount(), result.getCount());
            return inputValue == outputValue ? result : ItemStack.EMPTY;
        } catch (ArithmeticException overflow) { return ItemStack.EMPTY; }
    }
}
