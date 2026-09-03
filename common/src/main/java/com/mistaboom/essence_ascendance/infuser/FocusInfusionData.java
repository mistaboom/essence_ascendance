package com.mistaboom.essence_ascendance.infuser;

import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import com.mistaboom.essence_ascendance.pylon.EssenceFocusData;
import com.mistaboom.essence_ascendance.pylon.EssenceFocusTier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Shared persistence/validation boundary for partially infused Focus workpieces. */
public final class FocusInfusionData {

    private static final String ROOT_TAG = "essence_ascendance_focus_infusion";
    private static final String TARGET_TAG = "target_tier";
    private static final String SESSION_TAG = "session";
    private static final String CONTRIBUTIONS_TAG = "contributions";

    private FocusInfusionData() {
    }

    public static Optional<Progress> readRaw(ItemStack stack) {
        if (!EssenceFocusData.isFocusItem(stack)) {
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
        EssenceFocusTier target = parseTier(tag.getString(TARGET_TAG));
        if (target == null) {
            return Optional.empty();
        }
        UUID session = tag.hasUUID(SESSION_TAG) ? tag.getUUID(SESSION_TAG) : null;
        CompoundTag contributions = tag.contains(CONTRIBUTIONS_TAG, Tag.TAG_COMPOUND)
                ? tag.getCompound(CONTRIBUTIONS_TAG)
                : new CompoundTag();
        Map<EssenceDefinition, Long> values = new LinkedHashMap<>();
        long total = 0L;
        try {
            for (EssenceDefinition essence : FocusInfusionRecipe.coreAttributeEssences()) {
                long amount = contributions.getLong(essence.id().getPath());
                if (amount < 0L) {
                    return Optional.empty();
                }
                total = Math.addExact(total, amount);
                values.put(essence, amount);
            }
        } catch (ArithmeticException overflow) {
            return Optional.empty();
        }
        if (total > 0L && session == null) {
            return Optional.empty();
        }
        return Optional.of(new Progress(target, session, Map.copyOf(values)));
    }

    public static Optional<Progress> readValidated(ItemStack stack) {
        Optional<FocusInfusionRecipe> recipeOptional = FocusInfusionRecipe.forWorkpiece(stack);
        if (recipeOptional.isEmpty()) {
            return Optional.empty();
        }
        FocusInfusionRecipe recipe = recipeOptional.get();

        Map<EssenceDefinition, Long> zero = zeroContributions();
        Optional<Progress> raw = readRaw(stack);
        if (raw.isEmpty()) {
            CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
            if (customData != null
                    && customData.copyTag().contains(ROOT_TAG, Tag.TAG_COMPOUND)) {
                return Optional.empty();
            }
            return Optional.of(new Progress(recipe.targetTier(), null, zero));
        }

        Progress progress = raw.get();
        if (progress.targetTier() != recipe.targetTier()
                || progress.totalContributed() > recipe.totalEssenceRequired()) {
            return Optional.empty();
        }
        return Optional.of(progress);
    }

    public static boolean hasInfusionTag(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        return customData != null
                && customData.copyTag().contains(ROOT_TAG, Tag.TAG_COMPOUND);
    }

    public static boolean hasProgress(ItemStack stack) {
        Optional<Progress> progress = readRaw(stack);
        return progress.isPresent() && progress.get().totalContributed() > 0L;
    }

    public static long contribution(ItemStack stack, EssenceDefinition essence) {
        return readValidated(stack)
                .map(progress -> progress.contribution(essence))
                .orElse(0L);
    }

    public static long totalContributed(ItemStack stack) {
        return readValidated(stack)
                .map(Progress::totalContributed)
                .orElse(0L);
    }

    public static long rawContribution(ItemStack stack, EssenceDefinition essence) {
        return readRaw(stack).map(progress -> progress.contribution(essence)).orElse(0L);
    }

    public static long rawTotalContributed(ItemStack stack) {
        return readRaw(stack).map(Progress::totalContributed).orElse(0L);
    }

    public static boolean requirementsMet(ItemStack stack, FocusInfusionRecipe recipe) {
        Optional<Progress> progressOptional = readValidated(stack);
        if (progressOptional.isEmpty()) {
            return false;
        }
        Progress progress = progressOptional.get();
        if (progress.targetTier() != recipe.targetTier()
                || progress.totalContributed() < recipe.totalEssenceRequired()) {
            return false;
        }
        for (EssenceDefinition essence : FocusInfusionRecipe.coreAttributeEssences()) {
            if (progress.contribution(essence) < recipe.minimumPerAttributeEssence()) {
                return false;
            }
        }
        return true;
    }

    public static boolean minimumsMet(ItemStack stack, FocusInfusionRecipe recipe) {
        Optional<Progress> progressOptional = readValidated(stack);
        if (progressOptional.isEmpty()) {
            return false;
        }
        for (EssenceDefinition essence : FocusInfusionRecipe.coreAttributeEssences()) {
            if (progressOptional.get().contribution(essence)
                    < recipe.minimumPerAttributeEssence()) {
                return false;
            }
        }
        return true;
    }

    public static void addContribution(
            ItemStack stack,
            FocusInfusionRecipe recipe,
            EssenceDefinition essence,
            long amount
    ) {
        if (stack == null || stack.isEmpty() || amount <= 0L
                || !FocusInfusionRecipe.coreAttributeEssences().contains(essence)) {
            throw new IllegalArgumentException("Invalid Focus infusion contribution");
        }

        Progress progress = readValidated(stack)
                .orElseThrow(() -> new IllegalArgumentException("Malformed Focus infusion data"));
        long current = progress.contribution(essence);
        long newAmount = Math.addExact(current, amount);
        long newTotal = Math.addExact(progress.totalContributed(), amount);
        if (newTotal > recipe.totalEssenceRequired()) {
            throw new IllegalArgumentException("Focus infusion would exceed total requirement");
        }

        CustomData.update(
                DataComponents.CUSTOM_DATA,
                stack,
                outer -> {
                    CompoundTag tag = outer.contains(ROOT_TAG, Tag.TAG_COMPOUND)
                            ? outer.getCompound(ROOT_TAG)
                            : new CompoundTag();
                    tag.putString(TARGET_TAG, recipe.targetTier().serializedName());
                    if (!tag.hasUUID(SESSION_TAG)) {
                        tag.putUUID(SESSION_TAG, UUID.randomUUID());
                    }
                    CompoundTag contributions = tag.contains(CONTRIBUTIONS_TAG, Tag.TAG_COMPOUND)
                            ? tag.getCompound(CONTRIBUTIONS_TAG)
                            : new CompoundTag();
                    contributions.putLong(essence.id().getPath(), newAmount);
                    tag.put(CONTRIBUTIONS_TAG, contributions);
                    outer.put(ROOT_TAG, tag);
                }
        );
    }

    public static void clear(ItemStack stack) {
        if (!EssenceFocusData.isFocusItem(stack)) {
            return;
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, outer -> outer.remove(ROOT_TAG));
    }

    public static void complete(ItemStack stack, FocusInfusionRecipe recipe) {
        if (!requirementsMet(stack, recipe)) {
            throw new IllegalStateException("Focus infusion is incomplete");
        }
        EssenceFocusData.setTier(stack, recipe.targetTier());
        clear(stack);
    }

    public static void appendTooltip(ItemStack stack, List<Component> tooltip) {
        Optional<Progress> progressOptional = readRaw(stack);
        if (progressOptional.isEmpty() || progressOptional.get().totalContributed() <= 0L) {
            return;
        }

        Progress progress = progressOptional.get();
        tooltip.add(Component.empty());
        tooltip.add(Component.literal("Infusion Progress")
                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
        tooltip.add(Component.literal("  Target: " + progress.targetTier().displayName())
                .withStyle(ChatFormatting.WHITE));
        tooltip.add(Component.literal(
                        "  Infused Essence: " + format(progress.totalContributed())
                )
                .withStyle(ChatFormatting.GRAY));
    }

    private static Map<EssenceDefinition, Long> zeroContributions() {
        Map<EssenceDefinition, Long> values = new LinkedHashMap<>();
        for (EssenceDefinition essence : FocusInfusionRecipe.coreAttributeEssences()) {
            values.put(essence, 0L);
        }
        return Map.copyOf(values);
    }

    private static EssenceFocusTier parseTier(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        for (EssenceFocusTier tier : EssenceFocusTier.values()) {
            if (tier.serializedName().equals(name)) {
                return tier;
            }
        }
        return null;
    }

    private static String format(long value) {
        return String.format(java.util.Locale.ROOT, "%,d", Math.max(0L, value));
    }

    public record Progress(
            EssenceFocusTier targetTier,
            UUID sessionId,
            Map<EssenceDefinition, Long> contributions
    ) {
        public long contribution(EssenceDefinition essence) {
            return contributions.getOrDefault(essence, 0L);
        }

        public long totalContributed() {
            long total = 0L;
            for (long amount : contributions.values()) {
                total = Math.addExact(total, amount);
            }
            return total;
        }
    }
}
