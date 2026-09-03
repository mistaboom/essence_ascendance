package com.mistaboom.essence_ascendance.infuser;

import com.mistaboom.essence_ascendance.equipment.EquipmentTier;
import com.mistaboom.essence_ascendance.equipment.EquipmentTierData;
import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Persistent streamed progress stored directly on an Ascendance equipment stack. */
public final class EquipmentInfusionData {
    private static final String ROOT_TAG = "essence_ascendance_equipment_infusion";
    private static final String TARGET_TAG = "target_tier";
    private static final String SESSION_TAG = "session";
    private static final String CONTRIBUTIONS_TAG = "contributions";

    private EquipmentInfusionData() {}

    public static Optional<Progress> read(ItemStack stack) {
        if (!EquipmentTierData.isAscendanceEquipment(stack)) return Optional.empty();
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return Optional.empty();
        CompoundTag outer = customData.copyTag();
        if (!outer.contains(ROOT_TAG, Tag.TAG_COMPOUND)) return Optional.empty();
        CompoundTag root = outer.getCompound(ROOT_TAG);
        EquipmentTier target = EquipmentTier.fromSerializedName(root.getString(TARGET_TAG));
        if (target == EquipmentTier.LATENT || !root.hasUUID(SESSION_TAG)) return Optional.empty();
        Map<String, Long> contributions = new LinkedHashMap<>();
        if (root.contains(CONTRIBUTIONS_TAG, Tag.TAG_COMPOUND)) {
            CompoundTag tag = root.getCompound(CONTRIBUTIONS_TAG);
            for (String key : tag.getAllKeys()) {
                long amount = tag.getLong(key);
                if (amount < 0L) return Optional.empty();
                contributions.put(key, amount);
            }
        }
        return Optional.of(new Progress(target, root.getUUID(SESSION_TAG), Map.copyOf(contributions)));
    }

    public static Progress ensure(ItemStack stack, EquipmentInfusionRecipe recipe) {
        Optional<Progress> existing = read(stack);
        if (existing.isPresent()) {
            if (existing.get().targetTier() != recipe.targetTier()) {
                throw new IllegalArgumentException("Equipment carries infusion progress for a different target tier");
            }
            return existing.get();
        }
        UUID session = UUID.randomUUID();
        CustomData.update(DataComponents.CUSTOM_DATA, stack, outer -> {
            CompoundTag root = new CompoundTag();
            root.putString(TARGET_TAG, recipe.targetTier().serializedName());
            root.putUUID(SESSION_TAG, session);
            root.put(CONTRIBUTIONS_TAG, new CompoundTag());
            outer.put(ROOT_TAG, root);
        });
        return read(stack).orElseThrow();
    }

    public static long contribution(ItemStack stack, EssenceDefinition essence) {
        return read(stack).map(progress -> progress.contribution(essence)).orElse(0L);
    }

    public static long totalContributed(ItemStack stack) {
        return read(stack).map(Progress::totalContributed).orElse(0L);
    }

    public static void addContribution(ItemStack stack, EquipmentInfusionRecipe recipe, EssenceDefinition essence, long amount) {
        if (amount <= 0L) throw new IllegalArgumentException("Contribution must be positive");
        Progress progress = ensure(stack, recipe);
        long required = recipe.requirementFor(essence);
        long current = progress.contribution(essence);
        long next = Math.addExact(current, amount);
        if (required <= 0L || next > required) {
            throw new IllegalArgumentException("Equipment infusion contribution exceeds exact requirement");
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, outer -> {
            CompoundTag root = outer.getCompound(ROOT_TAG);
            CompoundTag contributions = root.getCompound(CONTRIBUTIONS_TAG);
            contributions.putLong(essence.id().toString(), next);
            root.put(CONTRIBUTIONS_TAG, contributions);
            outer.put(ROOT_TAG, root);
        });
    }

    public static boolean requirementsMet(ItemStack stack, EquipmentInfusionRecipe recipe) {
        Progress progress = read(stack).orElse(null);
        if (progress == null || progress.targetTier() != recipe.targetTier()) return false;
        for (Map.Entry<net.minecraft.resources.ResourceLocation, Long> entry : recipe.requirements().entrySet()) {
            if (progress.contributions().getOrDefault(entry.getKey().toString(), 0L) < entry.getValue()) return false;
        }
        return true;
    }

    public static void complete(ItemStack stack, EquipmentInfusionRecipe recipe) {
        if (!requirementsMet(stack, recipe)) throw new IllegalStateException("Equipment infusion is incomplete");
        EquipmentTierData.setTier(stack, recipe.targetTier());
        clear(stack);
    }

    /** Clears only streamed upgrade progress; completed equipment tier is preserved. */
    public static void clear(ItemStack stack) {
        if (!EquipmentTierData.isAscendanceEquipment(stack)) return;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, outer -> outer.remove(ROOT_TAG));
    }

    public static void appendTooltip(ItemStack stack, List<Component> tooltip) {
        if (!EquipmentTierData.isAscendanceEquipment(stack)) return;
        EquipmentTier tier = EquipmentTierData.tier(stack);
        if (tier == EquipmentTier.LATENT) {
            tooltip.add(Component.literal("Essence Channeling: Inactive").withStyle(ChatFormatting.DARK_GRAY));
        }
        read(stack).ifPresent(progress -> tooltip.add(Component.literal(
                "Infusing toward " + progress.targetTier().displayName() + ": " + format(progress.totalContributed())
        ).withStyle(ChatFormatting.DARK_PURPLE)));
    }

    private static String format(long value) { return String.format("%,d", value); }

    public record Progress(EquipmentTier targetTier, UUID sessionId, Map<String, Long> contributions) {
        public long contribution(EssenceDefinition essence) { return contributions.getOrDefault(essence.id().toString(), 0L); }
        public long totalContributed() {
            long total = 0L;
            for (long amount : contributions.values()) total = Math.addExact(total, amount);
            return total;
        }
    }
}
