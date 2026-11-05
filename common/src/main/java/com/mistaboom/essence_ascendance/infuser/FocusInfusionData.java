package com.mistaboom.essence_ascendance.infuser;

import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import com.mistaboom.essence_ascendance.data.NbtVersionCompat;
import com.mistaboom.essence_ascendance.data.CustomDataReader;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapDecoder;
import com.mojang.serialization.MapLike;
import com.mistaboom.essence_ascendance.equipment.EquipmentTier;
import com.mistaboom.essence_ascendance.pylon.EssenceFocusData;
import com.mistaboom.essence_ascendance.pylon.EssenceFocusTier;
import com.mistaboom.essence_ascendance.text.EssenceText;
import com.mistaboom.essence_ascendance.visual.AscendancePalette;
import com.mistaboom.essence_ascendance.visual.AscendanceUiPalette;
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
import java.util.stream.Stream;

/** Shared persistence/validation boundary for partially infused Focus workpieces. */
public final class FocusInfusionData {

    private static final String ROOT_TAG = "essence_ascendance_focus_infusion";
    private static final String TARGET_TAG = "target_tier";
    private static final String SESSION_TAG = "session";
    private static final String CONTRIBUTIONS_TAG = "contributions";

    // The native reader supplies NbtOps values. Only these immutable projections leave the decoder;
    // funding histories and unrelated components are neither traversed nor copied.
    private static final MapDecoder<Parsed> PROGRESS_READER = new MapDecoder.Implementation<>() {
        @Override public <T> DataResult<Parsed> decode(DynamicOps<T> ops, MapLike<T> input) {
            return DataResult.success(input.get(ROOT_TAG) instanceof CompoundTag tag ? parse(tag) : Parsed.ABSENT);
        }
        @Override public <T> Stream<T> keys(DynamicOps<T> ops) { return Stream.of(ops.createString(ROOT_TAG)); }
    };
    private static final MapDecoder<Boolean> PRESENCE_READER = new MapDecoder.Implementation<>() {
        @Override public <T> DataResult<Boolean> decode(DynamicOps<T> ops, MapLike<T> input) {
            return DataResult.success(input.get(ROOT_TAG) instanceof CompoundTag);
        }
        @Override public <T> Stream<T> keys(DynamicOps<T> ops) { return Stream.of(ops.createString(ROOT_TAG)); }
    };
    private record Parsed(boolean present, Optional<Progress> progress) {
        private static final Parsed ABSENT = new Parsed(false, Optional.empty());
        private static final Parsed MALFORMED = new Parsed(true, Optional.empty());
    }

    private FocusInfusionData() {
    }
    public static void recordFunding(ItemStack stack, com.mistaboom.essence_ascendance.team.PoolId pool,
                                     Map<net.minecraft.resources.ResourceLocation, Long> costs) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, outer -> {
            CompoundTag progress = NbtVersionCompat.getCompound(outer, ROOT_TAG);
            InfusionPayments.attribute(progress, pool, costs); outer.put(ROOT_TAG, progress);
        });
    }

    public static Optional<Progress> readRaw(ItemStack stack) {
        return EssenceFocusData.isFocusItem(stack) ? parsed(stack).progress() : Optional.empty();
    }

    private static Parsed parsed(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        return customData == null ? Parsed.ABSENT : CustomDataReader.read(customData, PROGRESS_READER).getOrThrow();
    }

    private static Parsed parse(CompoundTag tag) {
        EssenceFocusTier target = parseTier(NbtVersionCompat.getString(tag, TARGET_TAG));
        if (target == null) return Parsed.MALFORMED;
        UUID session = NbtVersionCompat.hasUUID(tag, SESSION_TAG) ? NbtVersionCompat.getUUID(tag, SESSION_TAG) : null;
        CompoundTag contributions = tag.get(CONTRIBUTIONS_TAG) instanceof CompoundTag compound ? compound : null;
        Map<EssenceDefinition, Long> values = new LinkedHashMap<>();
        long total = 0L;
        try {
            for (EssenceDefinition essence : FocusInfusionRecipe.coreAttributeEssences()) {
                long amount = contributions == null ? 0L : NbtVersionCompat.getLong(contributions, essence.id().getPath());
                if (amount < 0L) return Parsed.MALFORMED;
                total = Math.addExact(total, amount);
                values.put(essence, amount);
            }
        } catch (ArithmeticException overflow) {
            return Parsed.MALFORMED;
        }
        if (total > 0L && session == null) return Parsed.MALFORMED;
        return new Parsed(true, Optional.of(new Progress(target, session, Map.copyOf(values))));
    }

    public static Optional<Progress> readValidated(ItemStack stack) {
        Optional<FocusInfusionRecipe> recipeOptional = FocusInfusionRecipe.forWorkpiece(stack);
        if (recipeOptional.isEmpty()) {
            return Optional.empty();
        }
        FocusInfusionRecipe recipe = recipeOptional.get();

        Parsed parsed = parsed(stack);
        if (parsed.progress().isEmpty()) {
            if (parsed.present()) return Optional.empty();
            return Optional.of(new Progress(recipe.targetTier(), null, zeroContributions()));
        }

        Progress progress = parsed.progress().get();
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
        return customData != null && CustomDataReader.read(customData, PRESENCE_READER).getOrThrow();
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
                    CompoundTag tag = NbtVersionCompat.contains(outer, ROOT_TAG, Tag.TAG_COMPOUND)
                            ? NbtVersionCompat.getCompound(outer, ROOT_TAG)
                            : new CompoundTag();
                    tag.putString(TARGET_TAG, recipe.targetTier().serializedName());
                    if (!NbtVersionCompat.hasUUID(tag, SESSION_TAG)) {
                        NbtVersionCompat.putUUID(tag, SESSION_TAG, UUID.randomUUID());
                    }
                    CompoundTag contributions = NbtVersionCompat.contains(tag, CONTRIBUTIONS_TAG, Tag.TAG_COMPOUND)
                            ? NbtVersionCompat.getCompound(tag, CONTRIBUTIONS_TAG)
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
        tooltip.add(EssenceText.tooltip("infusion_progress")
                .withStyle(style -> style
                        .withColor(AscendancePalette.tierMetalRgb(
                                EquipmentTier.fromSerializedName(progress.targetTier().serializedName())
                        ))
                        .withBold(true)));
        tooltip.add(Component.literal("  ")
                .append(EssenceText.tooltip("target", EssenceText.focusTier(progress.targetTier())))
                .withStyle(style -> style.withColor(AscendanceUiPalette.PRIMARY_TEXT)));
        tooltip.add(Component.literal("  ")
                .append(EssenceText.tooltip("infused_essence", format(progress.totalContributed())))
                .withStyle(style -> style.withColor(AscendanceUiPalette.MUTED_TEXT)));
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
