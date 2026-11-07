package com.mistaboom.essence_ascendance.gameplay;

import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.equipment.*;
import com.mistaboom.essence_ascendance.item.AscendanceArmorItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import static com.mistaboom.essence_ascendance.gameplay.EquipmentSnapshot.Kind;
import static com.mistaboom.essence_ascendance.gameplay.EquipmentSnapshot.Piece;

/** Small native adapter using the same physical, fracture and hand policy as equipment gameplay. */
public final class EquipmentSnapshots {
    private EquipmentSnapshots() { }

    public static EquipmentSnapshot capture(ServerPlayer player) {
        return new EquipmentSnapshot(
                EquipmentTier.fromAscendanceTier(EssenceSavedData.get(player.server).getPlayerData(player.getUUID()).getTier()),
                armor(player, EquipmentSlot.HEAD), armor(player, EquipmentSlot.CHEST),
                armor(player, EquipmentSlot.LEGS), armor(player, EquipmentSlot.FEET),
                piece(player.getMainHandItem()), piece(player.getOffhandItem()),
                TwoHandedEquipmentService.decision(player).mayAttack());
    }

    private static Piece armor(ServerPlayer player, EquipmentSlot slot) {
        ItemStack stack = player.getItemBySlot(slot);
        // A helmet command-inserted into the chest slot is not a complete armor piece.
        return stack.getItem() instanceof AscendanceArmorItem armor && armor.ascendanceSlot() == slot
                ? piece(stack) : Piece.EMPTY;
    }

    private static Piece piece(ItemStack stack) {
        if (!EquipmentTierData.isAscendanceEquipment(stack)) return Piece.EMPTY;
        var profile = ((EquipmentProfileItem) stack.getItem()).equipmentProfileId();
        Kind kind = profile.equals(EquipmentProfiles.ARMOR.id()) ? Kind.ARMOR
                : profile.equals(EquipmentProfiles.SWORD.id()) ? Kind.SWORD
                : profile.equals(EquipmentProfiles.GLAIVE.id()) ? Kind.GLAIVE
                : profile.equals(EquipmentProfiles.RANGED_WEAPON.id()) ? Kind.RANGED_WEAPON
                : profile.equals(EquipmentProfiles.MAGIC_CASTER.id()) ? Kind.MAGIC_CASTER
                : profile.equals(EquipmentProfiles.SHIELD.id()) ? Kind.SHIELD : Kind.OTHER;
        // Soulbinding affects recovery and machine acceptance, not wield/wear applicability.
        // Latent equipment has a mundane physical baseline and remains functional while intact.
        boolean functional = !FracturedEquipmentData.isFractured(stack)
                && (!stack.isDamageableItem() || stack.getDamageValue() < stack.getMaxDamage());
        return new Piece(kind, EquipmentTierData.tier(stack), functional);
    }
}
