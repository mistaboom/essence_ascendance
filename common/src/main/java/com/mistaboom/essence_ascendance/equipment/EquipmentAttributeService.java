package com.mistaboom.essence_ascendance.equipment;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import com.mistaboom.essence_ascendance.stat.EssenceStats;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.WeakHashMap;

/*
 * Server-authoritative application layer for the first attribute-backed
 * Ascendance gameplay effects.
 *
 * No MobEffect/potion effects are used here. Everything is applied as a
 * transient vanilla attribute modifier, so there are no status-effect icons,
 * inventory effect tiles, or persisted modifier data.
 *
 * The calculation order is deliberately the architecture established by the
 * equipment refactor:
 *
 *   player tier
 *       -> equipment archetype/profile
 *       -> physical baseline (when the equipment owns one)
 *       -> applicable invested stat bonus
 *       -> transient Minecraft attribute
 *
 * Physical Armor/Toughness have no investable stat multiplier. They come only
 * from tier + armor coverage. Melee Damage/Attack Speed begin with a
 * tier/archetype baseline, then their corresponding invested stats modify that
 * baseline. The remaining effects in this tranche are direct stat-to-attribute
 * mappings gated by active equipment applicability.
 */
public final class EquipmentAttributeService {

    /*
     * Minecraft 1.21.1 neutral player combat values before a held item's
     * attribute modifiers are applied. Ascendance melee-capable items are
     * registered with zero bootstrap ATTACK_DAMAGE / ATTACK_SPEED contribution,
     * so these constants let the transient modifier target our resolved total
     * weapon values without overwriting unrelated effects from other systems.
     */
    private static final double NEUTRAL_ATTACK_DAMAGE = 1.0;
    private static final double NEUTRAL_ATTACK_SPEED = 4.0;

    private static final double HEALTH_POINTS_PER_HEART = 2.0;
    private static final double EPSILON = 0.0000001;

    private static final ResourceLocation ARMOR_ID = id("equipment_armor");
    private static final ResourceLocation TOUGHNESS_ID = id("equipment_toughness");
    private static final ResourceLocation MELEE_DAMAGE_ID = id("melee_damage");
    private static final ResourceLocation MELEE_ATTACK_SPEED_ID = id("melee_attack_speed");
    private static final ResourceLocation ATTACK_KNOCKBACK_ID = id("attack_knockback");
    private static final ResourceLocation MINING_SPEED_ID = id("mining_speed");
    private static final ResourceLocation MAX_HEALTH_ID = id("max_health");
    private static final ResourceLocation MOVEMENT_SPEED_ID = id("movement_speed");
    private static final ResourceLocation KNOCKBACK_RESISTANCE_ID = id("knockback_resistance");
    private static final ResourceLocation SNEAK_SPEED_ID = id("sneak_speed");
    private static final ResourceLocation STEP_HEIGHT_ID = id("step_height");
    private static final ResourceLocation BLOCK_REACH_ID = id("block_reach");
    private static final ResourceLocation ENTITY_REACH_ID = id("entity_reach");

    /*
     * Prevents us from removing/re-adding identical modifiers every tick. That
     * matters because attribute changes are synchronized to clients. A weak-key
     * cache naturally disappears with old ServerPlayer instances after logout
     * or respawn.
     */
    private static final Map<ServerPlayer, AppliedState> LAST_APPLIED =
            new WeakHashMap<>();

    private EquipmentAttributeService() {
    }

    public static void sync(ServerPlayer player) {
        AppliedState desired = calculate(player);

        AppliedState previous = LAST_APPLIED.get(player);
        if (desired.equals(previous) && modifierPresenceMatches(player, desired)) {
            return;
        }

        apply(
                player,
                Attributes.ARMOR,
                ARMOR_ID,
                desired.armor(),
                AttributeModifier.Operation.ADD_VALUE
        );

        apply(
                player,
                Attributes.ARMOR_TOUGHNESS,
                TOUGHNESS_ID,
                desired.toughness(),
                AttributeModifier.Operation.ADD_VALUE
        );

        apply(
                player,
                Attributes.ATTACK_DAMAGE,
                MELEE_DAMAGE_ID,
                desired.meleeDamageModifier(),
                AttributeModifier.Operation.ADD_VALUE
        );

        apply(
                player,
                Attributes.ATTACK_SPEED,
                MELEE_ATTACK_SPEED_ID,
                desired.meleeAttackSpeedModifier(),
                AttributeModifier.Operation.ADD_VALUE
        );

        apply(
                player,
                Attributes.ATTACK_KNOCKBACK,
                ATTACK_KNOCKBACK_ID,
                desired.attackKnockback(),
                AttributeModifier.Operation.ADD_VALUE
        );

        apply(
                player,
                Attributes.BLOCK_BREAK_SPEED,
                MINING_SPEED_ID,
                desired.miningSpeedFraction(),
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE
        );

        apply(
                player,
                Attributes.MAX_HEALTH,
                MAX_HEALTH_ID,
                desired.maxHealthPoints(),
                AttributeModifier.Operation.ADD_VALUE
        );

        apply(
                player,
                Attributes.MOVEMENT_SPEED,
                MOVEMENT_SPEED_ID,
                desired.movementSpeedFraction(),
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE
        );

        apply(
                player,
                Attributes.KNOCKBACK_RESISTANCE,
                KNOCKBACK_RESISTANCE_ID,
                desired.knockbackResistance(),
                AttributeModifier.Operation.ADD_VALUE
        );

        apply(
                player,
                Attributes.SNEAKING_SPEED,
                SNEAK_SPEED_ID,
                desired.sneakSpeedFraction(),
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE
        );

        apply(
                player,
                Attributes.STEP_HEIGHT,
                STEP_HEIGHT_ID,
                desired.stepHeightBlocks(),
                AttributeModifier.Operation.ADD_VALUE
        );

        apply(
                player,
                Attributes.BLOCK_INTERACTION_RANGE,
                BLOCK_REACH_ID,
                desired.reachBlocks(),
                AttributeModifier.Operation.ADD_VALUE
        );

        apply(
                player,
                Attributes.ENTITY_INTERACTION_RANGE,
                ENTITY_REACH_ID,
                desired.reachBlocks(),
                AttributeModifier.Operation.ADD_VALUE
        );

        LAST_APPLIED.put(player, desired);

        /*
         * Removing Max Health equipment must never leave current health above
         * the newly resolved maximum.
         */
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    public static AppliedState evaluate(ServerPlayer player) {
        return calculate(player);
    }

    private static AppliedState calculate(ServerPlayer player) {
        PlayerEssenceData playerData =
                EssenceSavedData
                        .get(player.server)
                        .getPlayerData(player.getUUID());

        EquipmentStatState worn =
                EquipmentStatResolver.evaluateWornArmor(player);

        EquipmentStatState held =
                EquipmentStatResolver.evaluateHeldOnly(
                        player,
                        InteractionHand.MAIN_HAND
                );

        /*
         * Passive worn effects plus the active main hand merge by MAX for the
         * same stat. This is especially important for Reach: a full Ascendance
         * armor set and an Ascendance tool must not double-dip one investment.
         */
        EquipmentStatState active = worn.mergeMax(held);

        ArmorBaseline armorBaseline =
                resolveArmorBaseline(player, playerData);

        MeleeBaseline meleeBaseline =
                resolveMeleeBaseline(player, playerData, held);

        return new AppliedState(
                armorBaseline.armor(),
                armorBaseline.toughness(),
                meleeBaseline.attackDamageModifier(),
                meleeBaseline.attackSpeedModifier(),
                fractionBonus(playerData, EssenceStats.ATTACK_KNOCKBACK, held.strength(EssenceStats.ATTACK_KNOCKBACK)),
                fractionBonus(playerData, EssenceStats.MINING_SPEED, held.strength(EssenceStats.MINING_SPEED)),
                EquipmentValueService.scaledBonus(playerData, EssenceStats.MAX_HEALTH, worn.strength(EssenceStats.MAX_HEALTH)) * HEALTH_POINTS_PER_HEART,
                fractionBonus(playerData, EssenceStats.MOVEMENT_SPEED, worn.strength(EssenceStats.MOVEMENT_SPEED)),
                fractionBonus(playerData, EssenceStats.KNOCKBACK_RESISTANCE, worn.strength(EssenceStats.KNOCKBACK_RESISTANCE)),
                fractionBonus(playerData, EssenceStats.SNEAK_SPEED, worn.strength(EssenceStats.SNEAK_SPEED)),
                EquipmentValueService.scaledBonus(playerData, EssenceStats.STEP_HEIGHT, worn.strength(EssenceStats.STEP_HEIGHT)),
                EquipmentValueService.scaledBonus(playerData, EssenceStats.REACH, active.strength(EssenceStats.REACH))
        );
    }

    public static void forget(ServerPlayer player) {
        LAST_APPLIED.remove(player);
    }

    private static ArmorBaseline resolveArmorBaseline(
            ServerPlayer player,
            PlayerEssenceData playerData
    ) {
        double coverage = 0.0;

        for (EquipmentSlot slot : EquipmentStatResolver.armorSlots()) {
            ItemStack stack = player.getItemBySlot(slot);
            if (!isFirstPartyProfile(stack, EquipmentProfiles.ARMOR.id())) {
                continue;
            }

            coverage += ArmorStatWeights.weightFor(slot);
        }

        if (coverage <= 0.0) {
            return ArmorBaseline.NONE;
        }

        EquipmentBaselineResult baseline =
                EquipmentBaselineService.evaluate(
                        playerData,
                        EquipmentProfiles.ARMOR.id()
                );

        return new ArmorBaseline(
                baseline.armor() * coverage,
                baseline.toughness() * coverage
        );
    }

    private static MeleeBaseline resolveMeleeBaseline(
            ServerPlayer player,
            PlayerEssenceData playerData,
            EquipmentStatState held
    ) {
        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.isEmpty()
                || !(mainHand.getItem() instanceof EquipmentProfileItem profileItem)) {
            return MeleeBaseline.NONE;
        }

        ResourceLocation profileId = profileItem.equipmentProfileId();
        EquipmentProfileDefinition profile =
                EquipmentProfileRegistry.get(profileId).orElse(null);

        if (profile == null
                || profile.baselineMultiplier(EquipmentBaselineProperty.MELEE_DAMAGE) <= 0.0
                || profile.baselineMultiplier(EquipmentBaselineProperty.MELEE_ATTACK_SPEED) <= 0.0) {
            return MeleeBaseline.NONE;
        }

        EquipmentBaselineResult baseline =
                EquipmentBaselineService.evaluate(playerData, profileId);

        double finalDamage = EquipmentValueService.applyPercentBonus(
                playerData,
                EssenceStats.MELEE_DAMAGE,
                held.strength(EssenceStats.MELEE_DAMAGE),
                baseline.meleeDamage()
        );

        double finalAttackSpeed = EquipmentValueService.applyPercentBonus(
                playerData,
                EssenceStats.MELEE_ATTACK_SPEED,
                held.strength(EssenceStats.MELEE_ATTACK_SPEED),
                baseline.meleeAttackSpeed()
        );

        return new MeleeBaseline(
                finalDamage - NEUTRAL_ATTACK_DAMAGE,
                finalAttackSpeed - NEUTRAL_ATTACK_SPEED
        );
    }

    private static boolean isFirstPartyProfile(
            ItemStack stack,
            ResourceLocation profileId
    ) {
        return !stack.isEmpty()
                && stack.getItem() instanceof EquipmentProfileItem profileItem
                && profileItem.equipmentProfileId().equals(profileId);
    }

    private static double fractionBonus(
            PlayerEssenceData playerData,
            com.mistaboom.essence_ascendance.stat.StatDefinition stat,
            double applicability
    ) {
        return EquipmentValueService.scaledBonus(
                playerData,
                stat,
                applicability
        ) / 100.0;
    }

    private static void apply(
            ServerPlayer player,
            Holder<Attribute> attribute,
            ResourceLocation modifierId,
            double amount,
            AttributeModifier.Operation operation
    ) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
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

    private static boolean modifierPresenceMatches(
            ServerPlayer player,
            AppliedState state
    ) {
        return presenceMatches(player, Attributes.ARMOR, ARMOR_ID, state.armor())
                && presenceMatches(player, Attributes.ARMOR_TOUGHNESS, TOUGHNESS_ID, state.toughness())
                && presenceMatches(player, Attributes.ATTACK_DAMAGE, MELEE_DAMAGE_ID, state.meleeDamageModifier())
                && presenceMatches(player, Attributes.ATTACK_SPEED, MELEE_ATTACK_SPEED_ID, state.meleeAttackSpeedModifier())
                && presenceMatches(player, Attributes.ATTACK_KNOCKBACK, ATTACK_KNOCKBACK_ID, state.attackKnockback())
                && presenceMatches(player, Attributes.BLOCK_BREAK_SPEED, MINING_SPEED_ID, state.miningSpeedFraction())
                && presenceMatches(player, Attributes.MAX_HEALTH, MAX_HEALTH_ID, state.maxHealthPoints())
                && presenceMatches(player, Attributes.MOVEMENT_SPEED, MOVEMENT_SPEED_ID, state.movementSpeedFraction())
                && presenceMatches(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RESISTANCE_ID, state.knockbackResistance())
                && presenceMatches(player, Attributes.SNEAKING_SPEED, SNEAK_SPEED_ID, state.sneakSpeedFraction())
                && presenceMatches(player, Attributes.STEP_HEIGHT, STEP_HEIGHT_ID, state.stepHeightBlocks())
                && presenceMatches(player, Attributes.BLOCK_INTERACTION_RANGE, BLOCK_REACH_ID, state.reachBlocks())
                && presenceMatches(player, Attributes.ENTITY_INTERACTION_RANGE, ENTITY_REACH_ID, state.reachBlocks());
    }

    private static boolean presenceMatches(
            ServerPlayer player,
            Holder<Attribute> attribute,
            ResourceLocation modifierId,
            double desiredAmount
    ) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return Math.abs(desiredAmount) <= EPSILON;
        }

        boolean shouldExist = Math.abs(desiredAmount) > EPSILON;
        return instance.hasModifier(modifierId) == shouldExist;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                EssenceAscendance.MOD_ID,
                path
        );
    }

    private record ArmorBaseline(
            double armor,
            double toughness
    ) {
        private static final ArmorBaseline NONE =
                new ArmorBaseline(0.0, 0.0);
    }

    private record MeleeBaseline(
            double attackDamageModifier,
            double attackSpeedModifier
    ) {
        private static final MeleeBaseline NONE =
                new MeleeBaseline(0.0, 0.0);
    }

    public record AppliedState(
            double armor,
            double toughness,
            double meleeDamageModifier,
            double meleeAttackSpeedModifier,
            double attackKnockback,
            double miningSpeedFraction,
            double maxHealthPoints,
            double movementSpeedFraction,
            double knockbackResistance,
            double sneakSpeedFraction,
            double stepHeightBlocks,
            double reachBlocks
    ) {
    }
}
