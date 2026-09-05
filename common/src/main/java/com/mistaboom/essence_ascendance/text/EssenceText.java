package com.mistaboom.essence_ascendance.text;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleDissolutionMode;
import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import com.mistaboom.essence_ascendance.equipment.EquipmentTier;
import com.mistaboom.essence_ascendance.pylon.EssenceFocusTier;
import com.mistaboom.essence_ascendance.stat.StatCategory;
import com.mistaboom.essence_ascendance.stat.StatDefinition;
import com.mistaboom.essence_ascendance.tier.AscendanceTierDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

/**
 * Central translation-key vocabulary for Essence Ascendance.
 *
 * <p>Short machine labels, tooltips, commands, and future guidebook pages
 * should reference semantic keys instead of embedding English in Java. Long
 * guidebook prose can live under {@code guide.essence_ascendance.*} while
 * reusing the canonical Essence/stat/tier names exposed here.</p>
 */
public final class EssenceText {

    private static final String MOD_ID = EssenceAscendance.MOD_ID;

    private EssenceText() {
    }

    public static MutableComponent gui(String path, Object... args) {
        return Component.translatable("gui." + MOD_ID + "." + path, args);
    }

    public static MutableComponent tooltip(String path, Object... args) {
        return Component.translatable("tooltip." + MOD_ID + "." + path, args);
    }

    public static MutableComponent command(String path, Object... args) {
        return Component.translatable("command." + MOD_ID + "." + path, args);
    }

    public static MutableComponent jei(String path, Object... args) {
        return Component.translatable("jei." + MOD_ID + "." + path, args);
    }

    public static MutableComponent term(String path, Object... args) {
        return Component.translatable("term." + MOD_ID + "." + path, args);
    }

    public static MutableComponent guide(String path, Object... args) {
        return Component.translatable("guide." + MOD_ID + "." + path, args);
    }

    public static MutableComponent essence(EssenceDefinition essence) {
        return named(
                "essence",
                essence.id(),
                essence.displayName()
        );
    }

    public static MutableComponent essenceShort(EssenceDefinition essence) {
        String fallback = essence.displayName();
        String suffix = " Essence";
        if (fallback.endsWith(suffix) && fallback.length() > suffix.length()) {
            fallback = fallback.substring(0, fallback.length() - suffix.length());
        }
        return named("essence_short", essence.id(), fallback);
    }

    public static MutableComponent stat(StatDefinition stat) {
        return named("stat", stat.id(), stat.displayName());
    }

    public static MutableComponent ascendanceTier(AscendanceTierDefinition tier) {
        return named("tier", tier.id(), tier.displayName());
    }

    public static MutableComponent equipmentTier(EquipmentTier tier) {
        return Component.translatableWithFallback(
                "equipment_tier." + MOD_ID + "." + tier.serializedName(),
                tier.displayName()
        );
    }

    public static MutableComponent focusTier(EssenceFocusTier tier) {
        return Component.translatableWithFallback(
                "focus_tier." + MOD_ID + "." + tier.serializedName(),
                tier.displayName()
        );
    }

    public static MutableComponent category(StatCategory category) {
        String path = category.name().toLowerCase(Locale.ROOT);
        String fallback = Character.toUpperCase(path.charAt(0)) + path.substring(1);
        return Component.translatableWithFallback(
                "stat_category." + MOD_ID + "." + path,
                fallback
        );
    }

    public static MutableComponent dissolutionMode(EssenceCrucibleDissolutionMode mode) {
        return Component.translatableWithFallback(
                "dissolution_mode." + MOD_ID + "." + mode.serializedName(),
                mode.displayName()
        );
    }

    public static MutableComponent literalOrTranslated(
            String key,
            String fallback,
            Object... args
    ) {
        if (args == null || args.length == 0) {
            return Component.translatableWithFallback(key, fallback);
        }
        /*
         * Minecraft's fallback overload does not accept format arguments. All
         * keys created by this mod are guaranteed in en_us.json; external or
         * config-defined names should use named(...) instead.
         */
        return Component.translatable(key, args);
    }

    private static MutableComponent named(
            String family,
            ResourceLocation id,
            String fallback
    ) {
        return Component.translatableWithFallback(
                family + "." + id.getNamespace() + "." + id.getPath(),
                fallback
        );
    }
}
