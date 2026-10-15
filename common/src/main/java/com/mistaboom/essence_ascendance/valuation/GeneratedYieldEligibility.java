package com.mistaboom.essence_ascendance.valuation;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.infuser.EssentiumItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;

import java.util.Set;

/** Permission to generate a default payout, separate from internal economic value. */
public final class GeneratedYieldEligibility {
    private static final Set<String> ADMIN_ITEMS = Set.of("air", "barrier", "light", "debug_stick",
            "knowledge_book", "command_block", "chain_command_block", "repeating_command_block",
            "command_block_minecart", "structure_block", "structure_void", "jigsaw");
    private static final TagKey<net.minecraft.world.item.Item> EXCLUDED = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(EssenceAscendance.MOD_ID, "generated_yield_excluded"));
    private static final TagKey<net.minecraft.world.item.Item> INTERNAL_ONLY = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(EssenceAscendance.MOD_ID, "generated_yield_internal_only"));

    private GeneratedYieldEligibility() { }

    public enum Status { ELIGIBLE, INTERNAL_ONLY, EXCLUDED, COMPONENT_ONLY }
    public record Decision(Status status, String reason) {
        public boolean eligible() { return status == Status.ELIGIBLE; }
    }

    public static Decision decide(ItemStack stack, ProceduralValuationResult result,
                                  boolean allowByConfig, boolean denyByConfig) {
        return decide(stack, result.itemId(), result.totalValue(), allowByConfig, denyByConfig);
    }

    /** Estimate eligibility is separate from survival-source evidence. */
    public static Decision decide(ItemStack stack, ResourceLocation itemId, long totalValue,
                                  boolean allowByConfig, boolean denyByConfig) {
        if (stack.isEmpty()) return new Decision(Status.EXCLUDED, "empty_item");
        // No override can add a generated base payout on top of stored Essence.
        if (stack.getItem() instanceof EssentiumItem)
            return new Decision(Status.COMPONENT_ONLY, "exact_stored_essence_recovery_only");
        if (totalValue <= 0 || totalValue > ProceduralValuationSettings.MAX_VALUE)
            return new Decision(Status.EXCLUDED, "unsafe_or_unrepresentable_payout");
        if (denyByConfig) return new Decision(Status.EXCLUDED, "deny_generated_setting");
        if (!allowByConfig) {
            if (stack.is(EXCLUDED)) return new Decision(Status.EXCLUDED, "generated_yield_excluded_tag");
            if (itemId.getNamespace().equals(EssenceAscendance.MOD_ID) || stack.is(INTERNAL_ONLY))
                return new Decision(Status.INTERNAL_ONLY, "internal_recipe_value_not_automatic_dissolution");
            if (itemId.getNamespace().equals("minecraft") && ADMIN_ITEMS.contains(itemId.getPath()))
                return new Decision(Status.EXCLUDED, "administrative_item");
            if (stack.getItem() instanceof SpawnEggItem)
                return new Decision(Status.EXCLUDED, "spawn_egg_requires_explicit_allowance");
        }
        return new Decision(Status.ELIGIBLE, allowByConfig ? "explicitly_allowed_estimate" : "property_recipe_estimate_not_source_proof");
    }
}
