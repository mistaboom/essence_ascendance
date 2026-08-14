package com.mistaboom.essence_ascendance.neoforge;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.equipment.EquipmentDamageService;
import com.mistaboom.essence_ascendance.equipment.EquipmentVitalityService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

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
