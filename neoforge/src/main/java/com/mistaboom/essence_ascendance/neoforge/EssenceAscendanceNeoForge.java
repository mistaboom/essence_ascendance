package com.mistaboom.essence_ascendance.neoforge;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.equipment.EquipmentDamageService;
import com.mistaboom.essence_ascendance.equipment.EquipmentGatheringService;
import com.mistaboom.essence_ascendance.equipment.EquipmentVitalityService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.enchanting.GetEnchantmentLevelEvent;

@Mod(EssenceAscendance.MOD_ID)
public final class EssenceAscendanceNeoForge {

    public EssenceAscendanceNeoForge() {
        EssenceAscendance.init();

        /*
         * NeoForge exposes mutable incoming damage, post-damage health loss,
         * and mutable healing events. These listeners therefore remain thin
         * loader adapters into the common gameplay services.
         */
        NeoForge.EVENT_BUS.addListener(
                EssenceAscendanceNeoForge::onIncomingDamage
        );

        NeoForge.EVENT_BUS.addListener(
                EssenceAscendanceNeoForge::onDamagePost
        );

        NeoForge.EVENT_BUS.addListener(
                EssenceAscendanceNeoForge::onHealing
        );

        NeoForge.EVENT_BUS.addListener(
                EssenceAscendanceNeoForge::onGetEnchantmentLevel
        );

    }

    private static void onIncomingDamage(
            LivingIncomingDamageEvent event
    ) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        event.setAmount(
                EquipmentDamageService.modifyIncomingDamage(
                        player,
                        event.getSource(),
                        event.getAmount()
                )
        );
    }

    private static void onDamagePost(
            LivingDamageEvent.Post event
    ) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        EquipmentDamageService.reflectAfterDamage(
                player,
                event.getSource(),
                event.getNewDamage()
        );
    }

    private static void onGetEnchantmentLevel(
            GetEnchantmentLevelEvent event
    ) {
        applyVirtualLevel(event, Enchantments.FORTUNE);
        applyVirtualLevel(event, Enchantments.LOOTING);
    }

    private static void applyVirtualLevel(
            GetEnchantmentLevelEvent event,
            net.minecraft.resources.ResourceKey<net.minecraft.world.item.enchantment.Enchantment> key
    ) {
        if (!event.isTargetting(key)) {
            return;
        }

        event.getHolder(key).ifPresent(holder -> {
            int current = event.getEnchantments().getLevel(holder);
            int resolved = EquipmentGatheringService.resolveVirtualEnchantmentLevel(
                    event.getStack(),
                    holder,
                    current
            );

            if (resolved > current) {
                event.getEnchantments().set(holder, resolved);
            }
        });
    }

    private static void onHealing(
            LivingHealEvent event
    ) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        event.setAmount(
                EquipmentVitalityService.modifyExternalHealing(
                        player,
                        event.getAmount()
                )
        );
    }
}
