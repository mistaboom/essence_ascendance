package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.equipment.EquipmentTierVisuals;
import com.mistaboom.essence_ascendance.equipment.EquipmentTierData;
import com.mistaboom.essence_ascendance.equipment.personalized.*;
import com.mistaboom.essence_ascendance.gathering.RodHookAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import java.util.Optional;

/** Owner-held presentation visible to every observer. Dropped items and catalog copies stay ordinary. */
public final class PersonalizedEquipmentClient {
    private PersonalizedEquipmentClient() { }
    public static Optional<PersonalizedEquipmentDefinition> active(ItemStack stack) {
        var client = Minecraft.getInstance();
        if (client == null || !EquipmentTierData.isAscendanceEquipment(stack)) return Optional.empty();
        var local = PersonalizedEquipmentService.active(client.player, stack);
        return local.isPresent() || client.level == null ? local : active(client.level.players(), stack);
    }
    public static Optional<PersonalizedEquipmentDefinition> active(Iterable<? extends Player> holders, ItemStack stack) {
        if (!EquipmentTierData.isAscendanceEquipment(stack)) return Optional.empty();
        for (var holder : holders) {
            if (holder.getMainHandItem() != stack && holder.getOffhandItem() != stack) continue;
            var profile = PersonalizedEquipmentService.active(holder, stack);
            if (profile.isPresent()) return profile;
        }
        return Optional.empty();
    }
    public static int itemTint(ItemStack stack, int tintIndex) {
        return itemTint(active(stack), stack, tintIndex);
    }
    public static int itemTint(Iterable<? extends Player> holders, ItemStack stack, int tintIndex) {
        return itemTint(active(holders, stack), stack, tintIndex);
    }
    private static int itemTint(Optional<PersonalizedEquipmentDefinition> active, ItemStack stack, int tintIndex) {
        return active.map(profile -> switch (tintIndex) {
            case 0 -> 0xFF000000 | profile.baseRgb();
            case 1 -> 0xFF000000 | profile.accentRgb();
            default -> EquipmentTierVisuals.CLEAR_TINT;
        }).orElseGet(() -> EquipmentTierVisuals.itemTint(stack, tintIndex));
    }
    public static Component name(ItemStack stack) {
        return active(stack).map(profile -> Component.literal(profile.name())).orElse(null);
    }
    public static ResourceLocation hookTexture(FishingHook hook) {
        var owner = hook.getPlayerOwner();
        if (owner == null || !(hook instanceof RodHookAccess access)
                || access.essenceAscendance$rodHand() == 0) return null;
        var stack = owner.getItemInHand(access.essenceAscendance$rodHand() == 1 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
        return PersonalizedEquipmentService.active(owner, stack).filter(profile -> profile.tier().equals(access.essenceAscendance$rodTier().serializedName()))
                .map(profile -> ResourceLocation.fromNamespaceAndPath("essence_ascendance",
                        "textures/entity/ascendance_fishing_hook/personalized/" + profile.id() + ".png")).orElse(null);
    }
}
