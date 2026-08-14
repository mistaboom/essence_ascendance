package com.mistaboom.essence_ascendance.equipment;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;

/*
 * Datapack-extensible classification tags used by EquipmentDamageService.
 *
 * They deliberately live in the Essence Ascendance namespace rather than
 * hard-coding vanilla DamageType IDs in Java. Built-in JSON tags compose
 * vanilla damage tags/IDs, while a modpack can extend these tags for modded
 * damage types without changing equipment-provider configuration.
 */
public final class AscendanceDamageTypeTags {

    public static final TagKey<DamageType> FALL = create("fall");
    public static final TagKey<DamageType> EXPLOSION = create("explosion");
    public static final TagKey<DamageType> FIRE = create("fire");
    public static final TagKey<DamageType> MAGIC = create("magic");
    public static final TagKey<DamageType> RANGED = create("ranged");

    private AscendanceDamageTypeTags() {
    }

    private static TagKey<DamageType> create(String path) {
        return TagKey.create(
                Registries.DAMAGE_TYPE,
                ResourceLocation.fromNamespaceAndPath(
                        EssenceAscendance.MOD_ID,
                        path
                )
        );
    }
}
