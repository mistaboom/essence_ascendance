package com.mistaboom.essence_ascendance.equipment;

import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import com.mistaboom.essence_ascendance.network.EquipmentTooltipPayload;
import com.mistaboom.essence_ascendance.progression.StatScalingService;
import com.mistaboom.essence_ascendance.stat.EssenceStatRegistry;
import com.mistaboom.essence_ascendance.stat.EssenceStats;
import com.mistaboom.essence_ascendance.stat.StatDefinition;
import com.mistaboom.essence_ascendance.stat.StatUnit;
import dev.architectury.networking.NetworkManager;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/*
 * Builds player-aware Ascendance equipment tooltip snapshots.
 *
 * Only current values are displayed. Tier ranges/ceilings remain available
 * through commands/debugging but are intentionally omitted from ordinary item
 * tooltips.
 */
public final class EquipmentTooltipSyncService {

    private static final double EPSILON = 0.0000001;
    private static final int SYNC_INTERVAL_TICKS = 10;

    private static final Map<ServerPlayer, EquipmentTooltipPayload> LAST_SENT =
            new WeakHashMap<>();

    private static boolean initialized = false;

    private EquipmentTooltipSyncService() {
    }

    public static void init() {
        if (initialized) {
            return;
        }

        if (Platform.getEnvironment() == Env.SERVER) {
            NetworkManager.registerS2CPayloadType(
                    EquipmentTooltipPayload.TYPE,
                    EquipmentTooltipPayload.CODEC
            );
        }

        initialized = true;
    }

    public static void sync(ServerPlayer player) {
        if (player.tickCount % SYNC_INTERVAL_TICKS != 0) {
            return;
        }

        if (!NetworkManager.canPlayerReceive(
                player,
                EquipmentTooltipPayload.TYPE
        )) {
            return;
        }

        EquipmentTooltipPayload desired = build(player);
        EquipmentTooltipPayload previous = LAST_SENT.get(player);

        if (desired.equals(previous)) {
            return;
        }

        NetworkManager.sendToPlayer(player, desired);
        LAST_SENT.put(player, desired);
    }

    public static void forget(ServerPlayer player) {
        LAST_SENT.remove(player);
    }

    private static EquipmentTooltipPayload build(ServerPlayer player) {
        PlayerEssenceData data =
                EssenceSavedData
                        .get(player.server)
                        .getPlayerData(player.getUUID());

        ArmorSetState armorSet =
                evaluateArmorSet(player);

        List<EquipmentTooltipPayload.Entry> entries =
                new ArrayList<>(11);

        entries.add(
                armor(
                        data,
                        armorSet,
                        EquipmentSlot.HEAD,
                        "Helmet"
                )
        );
        entries.add(
                armor(
                        data,
                        armorSet,
                        EquipmentSlot.CHEST,
                        "Chestplate"
                )
        );
        entries.add(
                armor(
                        data,
                        armorSet,
                        EquipmentSlot.LEGS,
                        "Leggings"
                )
        );
        entries.add(
                armor(
                        data,
                        armorSet,
                        EquipmentSlot.FEET,
                        "Boots"
                )
        );

        entries.add(melee(data));
        entries.add(ranged(data));
        entries.add(magic(data));

        entries.add(
                tool(
                        data,
                        EquipmentProfiles.PICKAXE,
                        "ascendance_pickaxe",
                        "Pickaxe"
                )
        );
        entries.add(
                tool(
                        data,
                        EquipmentProfiles.AXE,
                        "ascendance_axe",
                        "Axe"
                )
        );
        entries.add(
                tool(
                        data,
                        EquipmentProfiles.SHOVEL,
                        "ascendance_shovel",
                        "Shovel"
                )
        );
        entries.add(
                tool(
                        data,
                        EquipmentProfiles.HOE,
                        "ascendance_hoe",
                        "Hoe"
                )
        );

        return new EquipmentTooltipPayload(entries);
    }

    private static EquipmentTooltipPayload.Entry armor(
            PlayerEssenceData data,
            ArmorSetState armorSet,
            EquipmentSlot slot,
            String label
    ) {
        EquipmentBaselineResult baseline =
                EquipmentBaselineService.evaluate(
                        data,
                        EquipmentProfiles.ARMOR.id()
                );

        double pieceStrength =
                ArmorStatWeights.weightFor(slot);

        List<EquipmentTooltipPayload.Line> lines =
                new ArrayList<>();

        lines.add(identity(data, label));

        lines.add(stat(
                "Armor: "
                        + number(
                                baseline.armorForSlot(slot)
                        )
        ));

        lines.add(stat(
                "Toughness: "
                        + number(
                                baseline.toughnessForSlot(slot)
                        )
        ));

        lines.add(
                setBonus(
                        "Set: "
                                + armorSet.piecesWorn()
                                + "/4 worn"
                                + "  •  "
                                + wholePercent(
                                        armorSet.essenceStrength()
                                )
                                + " Essence power"
                )
        );

        addProfileAbilities(
                lines,
                data,
                EquipmentProfiles.ARMOR,
                EquipmentActivationType.WORN,
                pieceStrength
        );

        return entry(
                "ascendance_" + armorPath(slot),
                lines
        );
    }

    private static EquipmentTooltipPayload.Entry melee(
            PlayerEssenceData data
    ) {
        EquipmentBaselineResult base =
                EquipmentBaselineService.evaluate(
                        data,
                        EquipmentProfiles.MELEE_WEAPON.id()
                );

        double damage =
                percentCurrent(
                        data,
                        EssenceStats.MELEE_DAMAGE,
                        1.0,
                        base.meleeDamage()
                );

        double speed =
                percentCurrent(
                        data,
                        EssenceStats.MELEE_ATTACK_SPEED,
                        1.0,
                        base.meleeAttackSpeed()
                );

        List<EquipmentTooltipPayload.Line> lines =
                new ArrayList<>();

        lines.add(identity(data, "Melee Weapon"));
        lines.add(stat("Attack Damage: " + number(damage)));
        lines.add(stat("Attack Speed: " + number(speed) + "/s"));

        addProfileAbilities(
                lines,
                data,
                EquipmentProfiles.MELEE_WEAPON,
                EquipmentActivationType.HELD,
                1.0
        );

        return entry(
                "ascendance_melee_weapon",
                lines
        );
    }

    private static EquipmentTooltipPayload.Entry ranged(
            PlayerEssenceData data
    ) {
        EquipmentBaselineResult base =
                EquipmentBaselineService.evaluate(
                        data,
                        EquipmentProfiles.RANGED_WEAPON.id()
                );

        double damage =
                percentCurrent(
                        data,
                        EssenceStats.RANGED_DAMAGE,
                        1.0,
                        base.rangedDamage()
                );

        double drawSpeed =
                percentCurrent(
                        data,
                        EssenceStats.RANGED_ATTACK_SPEED,
                        1.0,
                        base.rangedAttackSpeed()
                );

        List<EquipmentTooltipPayload.Line> lines =
                new ArrayList<>();

        lines.add(identity(data, "Ranged Weapon"));
        lines.add(stat("Ranged Damage: " + number(damage)));
        lines.add(stat("Draw Speed: " + number(drawSpeed) + "/s"));

        addProfileAbilities(
                lines,
                data,
                EquipmentProfiles.RANGED_WEAPON,
                EquipmentActivationType.HELD,
                1.0
        );

        return entry(
                "ascendance_ranged_weapon",
                lines
        );
    }

    private static EquipmentTooltipPayload.Entry magic(
            PlayerEssenceData data
    ) {
        EquipmentBaselineResult base =
                EquipmentBaselineService.evaluate(
                        data,
                        EquipmentProfiles.MAGIC_FOCUS.id()
                );

        double damage =
                percentCurrent(
                        data,
                        EssenceStats.MAGIC_DAMAGE,
                        1.0,
                        base.magicDamage()
                );

        double castSpeed =
                percentCurrent(
                        data,
                        EssenceStats.MAGIC_CAST_SPEED,
                        1.0,
                        base.magicCastSpeed()
                );

        List<EquipmentTooltipPayload.Line> lines =
                new ArrayList<>();

        lines.add(identity(data, "Magic Focus"));
        lines.add(stat("Magic Damage: " + number(damage)));
        lines.add(stat("Cast Speed: " + number(castSpeed) + "/s"));

        addProfileAbilities(
                lines,
                data,
                EquipmentProfiles.MAGIC_FOCUS,
                EquipmentActivationType.HELD,
                1.0
        );

        return entry(
                "ascendance_magic_weapon",
                lines
        );
    }

    private static EquipmentTooltipPayload.Entry tool(
            PlayerEssenceData data,
            EquipmentProfileDefinition profile,
            String itemPath,
            String label
    ) {
        EquipmentBaselineResult base =
                EquipmentBaselineService.evaluate(
                        data,
                        profile.id()
                );

        double miningStrength =
                profile.statStrength(
                        EquipmentActivationType.HELD,
                        EssenceStats.MINING_SPEED
                );

        double damageStrength =
                profile.statStrength(
                        EquipmentActivationType.HELD,
                        EssenceStats.MELEE_DAMAGE
                );

        double speedStrength =
                profile.statStrength(
                        EquipmentActivationType.HELD,
                        EssenceStats.MELEE_ATTACK_SPEED
                );

        double miningSpeed =
                percentCurrent(
                        data,
                        EssenceStats.MINING_SPEED,
                        miningStrength,
                        base.miningSpeed()
                );

        double damage =
                percentCurrent(
                        data,
                        EssenceStats.MELEE_DAMAGE,
                        damageStrength,
                        base.meleeDamage()
                );

        double attackSpeed =
                percentCurrent(
                        data,
                        EssenceStats.MELEE_ATTACK_SPEED,
                        speedStrength,
                        base.meleeAttackSpeed()
                );

        List<EquipmentTooltipPayload.Line> lines =
                new ArrayList<>();

        lines.add(identity(data, label));
        lines.add(stat("Mining Speed: " + number(miningSpeed)));
        lines.add(stat("Attack Damage: " + number(damage)));
        lines.add(stat("Attack Speed: " + number(attackSpeed) + "/s"));
        lines.add(stat("Harvest Level: " + base.harvestLevel()));

        addProfileAbilities(
                lines,
                data,
                profile,
                EquipmentActivationType.HELD,
                1.0
        );

        return entry(
                itemPath,
                lines
        );
    }

    private static void addProfileAbilities(
            List<EquipmentTooltipPayload.Line> lines,
            PlayerEssenceData data,
            EquipmentProfileDefinition profile,
            EquipmentActivationType activation,
            double outerStrength
    ) {
        for (Map.Entry<ResourceLocation, Double> entry :
                profile.statApplicability(activation).entrySet()) {

            StatDefinition stat =
                    EssenceStatRegistry
                            .get(entry.getKey())
                            .orElse(null);

            if (stat == null) {
                continue;
            }

            double strength =
                    entry.getValue() * outerStrength;

            double resolved =
                    bonus(
                            data,
                            stat,
                            strength
                    );

            if (resolved <= EPSILON) {
                continue;
            }

            lines.add(
                    ability(
                            stat.displayName()
                                    + ": "
                                    + formatAbility(
                                            stat,
                                            resolved
                                    )
                    )
            );
        }
    }

    private static ArmorSetState evaluateArmorSet(
            ServerPlayer player
    ) {
        int pieces = 0;
        double strength = 0.0;

        for (EquipmentSlot slot :
                EquipmentStatResolver.armorSlots()) {

            ItemStack stack =
                    player.getItemBySlot(slot);

            if (stack.isEmpty()
                    || !(stack.getItem()
                    instanceof EquipmentProfileItem profileItem)
                    || !profileItem
                    .equipmentProfileId()
                    .equals(
                            EquipmentProfiles.ARMOR.id()
                    )) {
                continue;
            }

            pieces++;
            strength +=
                    ArmorStatWeights.weightFor(slot);
        }

        return new ArmorSetState(
                pieces,
                clamp01(strength)
        );
    }

    private static double bonus(
            PlayerEssenceData data,
            StatDefinition stat,
            double strength
    ) {
        return StatScalingService
                .evaluate(
                        data,
                        stat
                )
                .scaledBonus()
                * strength;
    }

    private static double percentCurrent(
            PlayerEssenceData data,
            StatDefinition stat,
            double strength,
            double base
    ) {
        return base
                * (
                1.0
                        + bonus(
                        data,
                        stat,
                        strength
                ) / 100.0
        );
    }

    private static String formatAbility(
            StatDefinition stat,
            double value
    ) {
        StatUnit unit =
                stat.unit();

        return switch (unit) {
            case PERCENT ->
                    "+" + number(value) + "%";

            case HEARTS ->
                    "+" + number(value) + " \u2665";

            case HEARTS_PER_SECOND ->
                    "+" + number(value) + " \u2665/s";

            case SECONDS ->
                    "+" + number(value) + " s";

            case BLOCKS ->
                    "+" + number(value) + " blocks";

            case LEVELS ->
                    "+" + roman(
                            toGameplayLevel(value)
                    );

            case FLAT ->
                    "+" + number(value);

            default ->
                    "+" + number(value);
        };
    }

    private static int toGameplayLevel(
            double value
    ) {
        if (!Double.isFinite(value)
                || value <= 0.0) {
            return 0;
        }

        long rounded =
                (long) Math.floor(
                        value + 0.5D
                );

        return (int) Math.max(
                0L,
                Math.min(
                        255L,
                        rounded
                )
        );
    }

    private static EquipmentTooltipPayload.Line identity(
            PlayerEssenceData data,
            String itemLabel
    ) {
        /*
         * The vanilla item name already identifies the equipment. Repeating
         * "Helmet", "Pickaxe", etc. here adds noise, so the identity line is
         * intentionally just the player's current Ascendance tier.
         */
        return new EquipmentTooltipPayload.Line(
                EquipmentTooltipPayload.Group.IDENTITY,
                EquipmentTooltipPayload.Tone.TIER,
                data.getTier().displayName()
        );
    }

    private static EquipmentTooltipPayload.Line stat(
            String text
    ) {
        return new EquipmentTooltipPayload.Line(
                EquipmentTooltipPayload.Group.STATS,
                EquipmentTooltipPayload.Tone.PRIMARY,
                text
        );
    }

    private static EquipmentTooltipPayload.Line ability(
            String text
    ) {
        return new EquipmentTooltipPayload.Line(
                EquipmentTooltipPayload.Group.ESSENCE,
                EquipmentTooltipPayload.Tone.ABILITY,
                text
        );
    }

    private static EquipmentTooltipPayload.Line setBonus(
            String text
    ) {
        return new EquipmentTooltipPayload.Line(
                EquipmentTooltipPayload.Group.ESSENCE,
                EquipmentTooltipPayload.Tone.SET_BONUS,
                text
        );
    }

    private static EquipmentTooltipPayload.Entry entry(
            String itemPath,
            List<EquipmentTooltipPayload.Line> lines
    ) {
        return new EquipmentTooltipPayload.Entry(
                "essence_ascendance:" + itemPath,
                lines
        );
    }

    private static String armorPath(
            EquipmentSlot slot
    ) {
        return switch (slot) {
            case HEAD -> "helmet";
            case CHEST -> "chestplate";
            case LEGS -> "leggings";
            case FEET -> "boots";
            default -> throw new IllegalArgumentException(
                    "Not an Ascendance armor slot: " + slot
            );
        };
    }

    private static String number(
            double value
    ) {
        if (Math.abs(
                value - Math.rint(value)
        ) < 0.005) {
            return Long.toString(
                    Math.round(value)
            );
        }

        return String.format(
                Locale.ROOT,
                "%.1f",
                value
        );
    }

    private static String wholePercent(
            double fraction
    ) {
        return Math.round(
                clamp01(fraction) * 100.0
        ) + "%";
    }

    private static double clamp01(
            double value
    ) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }

        return Math.max(
                0.0,
                Math.min(
                        1.0,
                        value
                )
        );
    }

    private static String roman(
            int level
    ) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> Integer.toString(level);
        };
    }

    private record ArmorSetState(
            int piecesWorn,
            double essenceStrength
    ) {
    }
}
