package com.mistaboom.essence_ascendance.equipment;

import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import com.mistaboom.essence_ascendance.stat.EssenceStats;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Abilities;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/*
 * Server-authoritative application layer for the remaining mobility/utility
 * gameplay stats:
 *
 *   swim_speed
 *   jump_height
 *   flight_speed
 *   luck
 *
 * All four remain ordinary bonuses layered on top of vanilla mechanics.
 * Nothing here replaces enchantments, potion effects, or other attribute
 * modifiers.
 *
 * Swim Speed
 * ----------
 * Minecraft 1.21.1 exposes WATER_MOVEMENT_EFFICIENCY specifically for water
 * movement. Essence contributes an additive value to that attribute, so
 * Depth Strider and other vanilla/modded modifiers remain part of the same
 * resolved attribute value. Minecraft's own attribute limits remain
 * authoritative.
 *
 * Jump Height
 * -----------
 * Essence multiplies the base JUMP_STRENGTH attribute. Jump Boost and any
 * other vanilla/modded jump modifiers are not removed or replaced.
 *
 * Flight Speed
 * ------------
 * Vanilla player ability flight uses Abilities#flyingSpeed rather than the
 * generic FLYING_SPEED entity attribute. We therefore multiply the current
 * ability speed and remember the pre-Essence baseline. If another system
 * changes the underlying fly speed while Essence is active, that new value is
 * treated as the new baseline and the Essence multiplier is reapplied once.
 * Removing the armor/stat restores the baseline instead of hard-coding the
 * vanilla default.
 *
 * Luck
 * ----
 * Essence adds directly to the vanilla LUCK attribute, so vanilla Luck and
 * other attribute sources stack naturally.
 */
public final class EquipmentMobilityService {

    private static final double EPSILON = 0.0000001;
    private static final float FLOAT_EPSILON = 0.000001F;

    private static final net.minecraft.resources.ResourceLocation SWIM_SPEED_ID =
            id("swim_speed");
    private static final net.minecraft.resources.ResourceLocation JUMP_HEIGHT_ID =
            id("jump_height");
    private static final net.minecraft.resources.ResourceLocation LUCK_ID =
            id("luck");

    /*
     * UUID-keyed so the remembered pre-Essence flight baseline survives a
     * ServerPlayer object replacement during respawn. This prevents a copied
     * already-boosted flyingSpeed from becoming the next baseline and being
     * multiplied a second time.
     *
     * Entries are explicitly removed on normal logout by forget().
     */
    private static final Map<UUID, FlightRuntime> FLIGHT_RUNTIME =
            new ConcurrentHashMap<>();

    private EquipmentMobilityService() {
    }

    public static void sync(ServerPlayer player) {
        PlayerEssenceData playerData = playerData(player);
        EquipmentStatState worn = EquipmentStatResolver.evaluateWornArmor(player);

        double swimSpeedPercent = value(
                playerData,
                EssenceStats.SWIM_SPEED,
                worn.strength(EssenceStats.SWIM_SPEED)
        );

        double jumpHeightPercent = value(
                playerData,
                EssenceStats.JUMP_HEIGHT,
                worn.strength(EssenceStats.JUMP_HEIGHT)
        );

        double flightSpeedPercent = value(
                playerData,
                EssenceStats.FLIGHT_SPEED,
                worn.strength(EssenceStats.FLIGHT_SPEED)
        );

        double luckBonus = value(
                playerData,
                EssenceStats.LUCK,
                worn.strength(EssenceStats.LUCK)
        );

        apply(
                player,
                Attributes.WATER_MOVEMENT_EFFICIENCY,
                SWIM_SPEED_ID,
                swimSpeedPercent / 100.0,
                AttributeModifier.Operation.ADD_VALUE
        );

        apply(
                player,
                Attributes.JUMP_STRENGTH,
                JUMP_HEIGHT_ID,
                jumpHeightPercent / 100.0,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE
        );

        apply(
                player,
                Attributes.LUCK,
                LUCK_ID,
                luckBonus,
                AttributeModifier.Operation.ADD_VALUE
        );

        syncFlightSpeed(player, flightSpeedPercent);
    }

    public static MobilityState evaluate(ServerPlayer player) {
        PlayerEssenceData playerData = playerData(player);
        EquipmentStatState worn = EquipmentStatResolver.evaluateWornArmor(player);

        double swimSpeedPercent = value(
                playerData,
                EssenceStats.SWIM_SPEED,
                worn.strength(EssenceStats.SWIM_SPEED)
        );

        double jumpHeightPercent = value(
                playerData,
                EssenceStats.JUMP_HEIGHT,
                worn.strength(EssenceStats.JUMP_HEIGHT)
        );

        double flightSpeedPercent = value(
                playerData,
                EssenceStats.FLIGHT_SPEED,
                worn.strength(EssenceStats.FLIGHT_SPEED)
        );

        double luckBonus = value(
                playerData,
                EssenceStats.LUCK,
                worn.strength(EssenceStats.LUCK)
        );

        Abilities abilities = player.getAbilities();
        float currentFlightSpeed = abilities.getFlyingSpeed();
        FlightRuntime runtime = FLIGHT_RUNTIME.get(player.getUUID());

        float baselineFlightSpeed = runtime != null
                && approximately(currentFlightSpeed, runtime.appliedSpeed())
                ? runtime.baselineSpeed()
                : currentFlightSpeed;

        return new MobilityState(
                swimSpeedPercent,
                jumpHeightPercent,
                flightSpeedPercent,
                luckBonus,
                player.getAttributeValue(Attributes.WATER_MOVEMENT_EFFICIENCY),
                player.getAttributeValue(Attributes.JUMP_STRENGTH),
                player.getAttributeValue(Attributes.LUCK),
                baselineFlightSpeed,
                currentFlightSpeed,
                abilities.mayfly,
                abilities.flying
        );
    }

    public static void forget(ServerPlayer player) {
        restoreFlightBaseline(player);
        remove(player, Attributes.WATER_MOVEMENT_EFFICIENCY, SWIM_SPEED_ID);
        remove(player, Attributes.JUMP_STRENGTH, JUMP_HEIGHT_ID);
        remove(player, Attributes.LUCK, LUCK_ID);
    }

    private static void syncFlightSpeed(
            ServerPlayer player,
            double flightSpeedPercent
    ) {
        Abilities abilities = player.getAbilities();
        float currentSpeed = abilities.getFlyingSpeed();
        FlightRuntime previous = FLIGHT_RUNTIME.get(player.getUUID());

        /*
         * If the speed still equals what we applied last tick, recover the
         * original baseline. Otherwise another system changed flySpeed; treat
         * that current value as the new baseline so the bonuses compose.
         */
        float baselineSpeed = previous != null
                && approximately(currentSpeed, previous.appliedSpeed())
                ? previous.baselineSpeed()
                : currentSpeed;

        if (flightSpeedPercent <= EPSILON) {
            if (previous != null
                    && approximately(currentSpeed, previous.appliedSpeed())) {
                setFlyingSpeed(player, previous.baselineSpeed());
            }

            FLIGHT_RUNTIME.remove(player.getUUID());
            return;
        }

        double multiplier = 1.0 + flightSpeedPercent / 100.0;
        float targetSpeed = finiteFloat(
                baselineSpeed * multiplier
        );

        if (!approximately(currentSpeed, targetSpeed)) {
            setFlyingSpeed(player, targetSpeed);
        }

        FLIGHT_RUNTIME.put(
                player.getUUID(),
                new FlightRuntime(
                        baselineSpeed,
                        targetSpeed,
                        flightSpeedPercent
                )
        );
    }

    private static void restoreFlightBaseline(ServerPlayer player) {
        FlightRuntime runtime = FLIGHT_RUNTIME.remove(player.getUUID());
        if (runtime == null) {
            return;
        }

        float currentSpeed = player.getAbilities().getFlyingSpeed();
        if (approximately(currentSpeed, runtime.appliedSpeed())) {
            setFlyingSpeed(player, runtime.baselineSpeed());
        }
    }

    private static void setFlyingSpeed(
            ServerPlayer player,
            float speed
    ) {
        player.getAbilities().setFlyingSpeed(speed);
        player.onUpdateAbilities();
    }

    private static double value(
            PlayerEssenceData playerData,
            com.mistaboom.essence_ascendance.stat.StatDefinition stat,
            double applicability
    ) {
        return EquipmentValueService.scaledBonus(
                playerData,
                stat,
                applicability
        );
    }

    private static void apply(
            ServerPlayer player,
            Holder<Attribute> attribute,
            net.minecraft.resources.ResourceLocation modifierId,
            double amount,
            AttributeModifier.Operation operation
    ) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }

        if (!Double.isFinite(amount)) {
            throw new IllegalArgumentException(
                    "Mobility attribute amount must be finite"
            );
        }

        if (Math.abs(amount) <= EPSILON) {
            instance.removeModifier(modifierId);
            return;
        }

        instance.addOrUpdateTransientModifier(
                new AttributeModifier(
                        modifierId,
                        amount,
                        operation
                )
        );
    }

    private static void remove(
            ServerPlayer player,
            Holder<Attribute> attribute,
            net.minecraft.resources.ResourceLocation modifierId
    ) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) {
            instance.removeModifier(modifierId);
        }
    }

    private static PlayerEssenceData playerData(ServerPlayer player) {
        return EssenceSavedData
                .get(player.server)
                .getPlayerData(player.getUUID());
    }

    private static boolean approximately(float left, float right) {
        return Math.abs(left - right) <= FLOAT_EPSILON;
    }

    private static float finiteFloat(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(
                    "Resolved flight speed must be finite"
            );
        }

        if (value >= Float.MAX_VALUE) {
            return Float.MAX_VALUE;
        }

        if (value <= 0.0) {
            return 0.0F;
        }

        return (float) value;
    }

    private static net.minecraft.resources.ResourceLocation id(String path) {
        return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                com.mistaboom.essence_ascendance.EssenceAscendance.MOD_ID,
                path
        );
    }

    private record FlightRuntime(
            float baselineSpeed,
            float appliedSpeed,
            double bonusPercent
    ) {
    }

    public record MobilityState(
            double swimSpeedPercent,
            double jumpHeightPercent,
            double flightSpeedPercent,
            double luckBonus,
            double waterMovementEfficiency,
            double jumpStrength,
            double actualLuck,
            float baselineFlightSpeed,
            float resolvedFlightSpeed,
            boolean mayFly,
            boolean flying
    ) {
    }
}
