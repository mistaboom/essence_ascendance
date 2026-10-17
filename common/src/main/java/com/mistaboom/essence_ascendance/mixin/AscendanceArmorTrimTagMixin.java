package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.equipment.EquipmentProfileItem;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagLoader;
import net.minecraft.world.item.ArmorItem;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Armor-slot tags imply vanilla trim eligibility; opt our authored armor out after references resolve. */
@Mixin(TagLoader.class)
public abstract class AscendanceArmorTrimTagMixin {
    @Shadow @Final private String directory;

    @Inject(method = "build(Ljava/util/Map;)Ljava/util/Map;", at = @At("RETURN"))
    private void essenceAscendance$excludeAuthoredArmorTrims(
            Map<ResourceLocation, List<TagLoader.EntryWithSource>> entries,
            CallbackInfoReturnable<Map<ResourceLocation, Collection<Object>>> callback) {
        if (!Registries.tagsDirPath(Registries.ITEM).equals(directory)) return;
        // Change only this resolved membership. Slot/enchantment tags and other armor are preserved.
        callback.getReturnValue().computeIfPresent(ItemTags.TRIMMABLE_ARMOR.location(), (id, values) ->
                values.stream().filter(entry -> {
                    Object value = entry instanceof Holder<?> holder ? holder.value() : entry;
                    return !(value instanceof ArmorItem && value instanceof EquipmentProfileItem);
                }).toList());
    }
}
