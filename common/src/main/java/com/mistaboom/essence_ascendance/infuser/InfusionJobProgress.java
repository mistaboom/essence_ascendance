package com.mistaboom.essence_ascendance.infuser;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import com.mistaboom.essence_ascendance.team.PoolId;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.nbt.Tag;

/** One exact paid timed job, attached to its recipe/input/output rather than a reusable essence tank.
 * Removing/changing input pauses this job; restoring its context resumes it. Input stacks cannot
 * copy this machine-owned progress. Focus/equipment retain their existing portable workpieces. */
public record InfusionJobProgress(ResourceLocation recipe, ItemStack workpiece, ItemStack result,
                                 Map<ResourceLocation, Long> required, Map<ResourceLocation, Long> paid,
                                 UUID identity, UUID recipient, Map<PoolId, Map<ResourceLocation, Long>> funding) {
    private static final String PORTABLE_TAG = "essence_ascendance_paid_job";
    public InfusionJobProgress {
        workpiece = workpiece.copy(); result = result.copy(); required = Map.copyOf(required); paid = Map.copyOf(paid);
        Objects.requireNonNull(identity);
        Map<PoolId, Map<ResourceLocation, Long>> frozen = new LinkedHashMap<>();
        funding.forEach((pool, values) -> frozen.put(pool, Map.copyOf(values))); funding = Map.copyOf(frozen);
        for (var entry : required.entrySet())
            if (entry.getValue() <= 0 || com.mistaboom.essence_ascendance.essence.EssenceTypes.ORDERED.stream().noneMatch(type -> type.id().equals(entry.getKey()))) throw new IllegalArgumentException("Invalid job requirement");
        if (required.isEmpty() || InfusionPayments.total(required) <= 0) throw new IllegalArgumentException("Empty job");
        for (var entry : paid.entrySet())
            if (entry.getValue() <= 0 || entry.getValue() > required.getOrDefault(entry.getKey(), 0L)) throw new IllegalArgumentException("Invalid paid job");
        Map<ResourceLocation, Long> attributed = new LinkedHashMap<>();
        funding.values().forEach(values -> values.forEach((type, amount) -> attributed.merge(type, amount, Math::addExact)));
        if (!attributed.equals(paid)) throw new IllegalArgumentException("Paid job attribution does not match committed progress");
    }
    @Override public ItemStack workpiece() { return workpiece.copy(); }
    @Override public ItemStack result() { return result.copy(); }
    public static InfusionJobProgress begin(EssenceInfuserRecipe recipe, EssenceInfuserRecipeContext context, UUID recipient) {
        ItemStack input = context.workpiece().copy(); input.setCount(recipe.inputCount());
        return new InfusionJobProgress(recipe.id(), input, recipe.createOutput(context),
                recipe.essenceRequirements(context).minimumByEssence(), Map.of(), UUID.randomUUID(), recipient, Map.of());
    }
    public boolean matches(EssenceInfuserRecipe current, EssenceInfuserRecipeContext context) {
        ItemStack input = context.workpiece().copy(); input.setCount(current.inputCount());
        return recipe.equals(current.id()) && ItemStack.matches(workpiece, input)
                && ItemStack.matches(result, current.createOutput(context))
                && required.equals(current.essenceRequirements(context).minimumByEssence());
    }
    public InfusionJobProgress add(Map<ResourceLocation, Long> amounts, PoolId pool) {
        Map<ResourceLocation, Long> next = new LinkedHashMap<>(paid);
        amounts.forEach((type, amount) -> next.put(type, InfusionPayments.next(required.getOrDefault(type, 0L), paid.getOrDefault(type, 0L), amount)));
        return new InfusionJobProgress(recipe, workpiece, result, required, next, identity, recipient,
                InfusionPayments.attribute(funding, pool, amounts));
    }
    public boolean complete() { return required.equals(paid); }
    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("version", 2);
        tag.putString("recipe", recipe.toString()); tag.put("workpiece", workpiece.save(registries)); tag.put("result", result.save(registries));
        tag.put("required", amounts(required)); tag.put("paid", amounts(paid)); tag.putUUID("identity", identity);
        if (recipient != null) tag.putUUID("recipient", recipient);
        tag.put("funding", InfusionPayments.saveFunding(funding)); return tag;
    }
    public static InfusionJobProgress load(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.getInt("version") != 2) throw new IllegalStateException("Unsupported paid Infuser job format; preserve this world and use a new development world");
        return new InfusionJobProgress(ResourceLocation.parse(tag.getString("recipe")),
                ItemStack.parse(registries, tag.getCompound("workpiece")).orElseThrow(),
                ItemStack.parse(registries, tag.getCompound("result")).orElseThrow(),
                amounts(tag.getCompound("required")), amounts(tag.getCompound("paid")), tag.getUUID("identity"),
                tag.hasUUID("recipient") ? tag.getUUID("recipient") : null,
                InfusionPayments.loadFunding(tag.getList("funding", Tag.TAG_COMPOUND)));
    }
    /** Move the SAME timed progress record with a unique repaired artifact; no copy remains in the machine. */
    public ItemStack carry(HolderLookup.Provider registries, int ticks) {
        ItemStack stack = workpiece();
        CompoundTag tag = save(registries); tag.putInt("processing_ticks", ticks);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, outer -> outer.put(PORTABLE_TAG, tag));
        return stack;
    }
    public static CompoundTag carried(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? new CompoundTag() : data.copyTag().getCompound(PORTABLE_TAG);
    }
    public static ItemStack withoutCarried(ItemStack stack) {
        ItemStack clean = stack.copy();
        CustomData.update(DataComponents.CUSTOM_DATA, clean, outer -> outer.remove(PORTABLE_TAG));
        return clean;
    }
    private static CompoundTag amounts(Map<ResourceLocation, Long> values) {
        CompoundTag tag = new CompoundTag(); values.forEach((type, amount) -> tag.putLong(type.toString(), amount)); return tag;
    }
    private static Map<ResourceLocation, Long> amounts(CompoundTag tag) {
        Map<ResourceLocation, Long> values = new LinkedHashMap<>();
        for (String key : tag.getAllKeys()) values.put(ResourceLocation.parse(key), tag.getLong(key)); return values;
    }
}
