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
    private static final String TOOLTIP_KEY = "tooltip.essence_ascendance.equipment.";
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
        send(
                player,
                false
        );
    }

    /*
     * Lifecycle/config transitions should not wait for the normal 10-tick
     * presentation refresh interval.
     */
    public static void forceSync(ServerPlayer player) {
        send(
                player,
                true
        );
    }

    public static void forget(ServerPlayer player) {
        LAST_SENT.remove(player);
    }

    private static void send(
            ServerPlayer player,
            boolean force
    ) {
        if (!force
                && player.tickCount % SYNC_INTERVAL_TICKS != 0) {
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

        if (!force
                && desired.equals(previous)) {
            return;
        }

        NetworkManager.sendToPlayer(player, desired);
        LAST_SENT.put(player, desired);
    }

    private static EquipmentTooltipPayload build(ServerPlayer player) {
        PlayerEssenceData data =
                EssenceSavedData.get(player.server).getPlayerData(player.getUUID());

        ArmorSetState armorSet = evaluateArmorSet(player);
        List<EquipmentTooltipPayload.Entry> entries = new ArrayList<>(80);

        // Tooltip presentation is stack-tier aware. A single registered item can
        // exist at six completed equipment tiers, so synchronize one resolved
        // snapshot per item+tier instead of caching one player-tier snapshot per
        // registered item ID.
        for (EquipmentTier itemTier : EquipmentTier.values()) {
            entries.add(armor(data, armorSet, EquipmentSlot.HEAD, "Helmet", itemTier));
            entries.add(armor(data, armorSet, EquipmentSlot.CHEST, "Chestplate", itemTier));
            entries.add(armor(data, armorSet, EquipmentSlot.LEGS, "Leggings", itemTier));
            entries.add(armor(data, armorSet, EquipmentSlot.FEET, "Boots", itemTier));
            entries.add(melee(data, itemTier));
            entries.add(ranged(data, itemTier));
            entries.add(magic(data, itemTier));
            entries.add(tool(data, EquipmentProfiles.PICKAXE, "ascendance_pickaxe", "Pickaxe", itemTier));
            entries.add(tool(data, EquipmentProfiles.AXE, "ascendance_axe", "Axe", itemTier));
            entries.add(tool(data, EquipmentProfiles.SHOVEL, "ascendance_shovel", "Shovel", itemTier));
            entries.add(tool(data, EquipmentProfiles.HOE, "ascendance_hoe", "Hoe", itemTier));
        }

        // Fractured state is stack-specific, but its active gameplay baseline is
        // always the mundane Latent baseline with no Essence abilities. One
        // extra snapshot per equipment type is enough; the client keeps the
        // real completed tier identity line from the normal tier snapshot.
        entries.add(fractured(armor(data, armorSet, EquipmentSlot.HEAD, "Helmet", EquipmentTier.LATENT)));
        entries.add(fractured(armor(data, armorSet, EquipmentSlot.CHEST, "Chestplate", EquipmentTier.LATENT)));
        entries.add(fractured(armor(data, armorSet, EquipmentSlot.LEGS, "Leggings", EquipmentTier.LATENT)));
        entries.add(fractured(armor(data, armorSet, EquipmentSlot.FEET, "Boots", EquipmentTier.LATENT)));
        entries.add(fractured(melee(data, EquipmentTier.LATENT)));
        entries.add(fractured(ranged(data, EquipmentTier.LATENT)));
        entries.add(fractured(magic(data, EquipmentTier.LATENT)));
        entries.add(fractured(tool(data, EquipmentProfiles.PICKAXE, "ascendance_pickaxe", "Pickaxe", EquipmentTier.LATENT)));
        entries.add(fractured(tool(data, EquipmentProfiles.AXE, "ascendance_axe", "Axe", EquipmentTier.LATENT)));
        entries.add(fractured(tool(data, EquipmentProfiles.SHOVEL, "ascendance_shovel", "Shovel", EquipmentTier.LATENT)));
        entries.add(fractured(tool(data, EquipmentProfiles.HOE, "ascendance_hoe", "Hoe", EquipmentTier.LATENT)));

        return new EquipmentTooltipPayload(entries);
    }

    private static EquipmentTooltipPayload.Entry armor(
            PlayerEssenceData data,
            ArmorSetState armorSet,
            EquipmentSlot slot,
            String label,
            EquipmentTier itemTier
    ) {
        EquipmentBaselineResult baseline =
                EquipmentBaselineService.evaluateForEquipmentTier(
                        data,
                        EquipmentProfiles.ARMOR.id(),
                        itemTier
                );

        double pieceStrength =
                ArmorStatWeights.weightFor(slot);

        List<EquipmentTooltipPayload.Line> lines =
                new ArrayList<>();

        lines.add(identity(itemTier, label));

        lines.add(stat("stat.armor", number(baseline.armorForSlot(slot))));

        lines.add(stat("stat.toughness", number(baseline.toughnessForSlot(slot))));

        if (itemTier != EquipmentTier.LATENT) {
            lines.add(
                    setBonus(
                            Integer.toString(armorSet.piecesWorn()),
                            wholePercent(armorSet.essenceStrength())
                    )
            );
        }

        addProfileAbilities(
                lines,
                data,
                itemTier,
                EquipmentProfiles.ARMOR,
                EquipmentActivationType.WORN,
                pieceStrength
        );

        return entry(
                "ascendance_" + armorPath(slot),
                itemTier,
                lines
        );
    }

    private static EquipmentTooltipPayload.Entry melee(
            PlayerEssenceData data,
            EquipmentTier itemTier
    ) {
        EquipmentBaselineResult base =
                EquipmentBaselineService.evaluateForEquipmentTier(
                        data,
                        EquipmentProfiles.MELEE_WEAPON.id(),
                        itemTier
                );

        double damage =
                percentCurrent(
                        data,
                        itemTier,
                        EssenceStats.MELEE_DAMAGE,
                        1.0,
                        base.meleeDamage()
                );

        double speed =
                percentCurrent(
                        data,
                        itemTier,
                        EssenceStats.MELEE_ATTACK_SPEED,
                        1.0,
                        base.meleeAttackSpeed()
                );

        List<EquipmentTooltipPayload.Line> lines =
                new ArrayList<>();

        lines.add(identity(itemTier, "Melee Weapon"));
        lines.add(stat("stat.attack_damage", number(damage)));
        lines.add(stat("stat.attack_speed", number(speed)));

        addProfileAbilities(
                lines,
                data,
                itemTier,
                EquipmentProfiles.MELEE_WEAPON,
                EquipmentActivationType.HELD,
                1.0
        );

        return entry(
                "ascendance_melee_weapon",
                itemTier,
                lines
        );
    }

    private static EquipmentTooltipPayload.Entry ranged(
            PlayerEssenceData data,
            EquipmentTier itemTier
    ) {
        EquipmentBaselineResult base =
                EquipmentBaselineService.evaluateForEquipmentTier(
                        data,
                        EquipmentProfiles.RANGED_WEAPON.id(),
                        itemTier
                );

        double damage =
                percentCurrent(
                        data,
                        itemTier,
                        EssenceStats.RANGED_DAMAGE,
                        1.0,
                        base.rangedDamage()
                );

        double drawSpeed =
                percentCurrent(
                        data,
                        itemTier,
                        EssenceStats.RANGED_ATTACK_SPEED,
                        1.0,
                        base.rangedAttackSpeed()
                );

        List<EquipmentTooltipPayload.Line> lines =
                new ArrayList<>();

        lines.add(identity(itemTier, "Ranged Weapon"));
        lines.add(stat("stat.ranged_damage", number(damage)));
        lines.add(stat("stat.draw_speed", number(drawSpeed)));

        addProfileAbilities(
                lines,
                data,
                itemTier,
                EquipmentProfiles.RANGED_WEAPON,
                EquipmentActivationType.HELD,
                1.0
        );

        return entry(
                "ascendance_ranged_weapon",
                itemTier,
                lines
        );
    }

    private static EquipmentTooltipPayload.Entry magic(
            PlayerEssenceData data,
            EquipmentTier itemTier
    ) {
        EquipmentBaselineResult base =
                EquipmentBaselineService.evaluateForEquipmentTier(
                        data,
                        EquipmentProfiles.MAGIC_CASTER.id(),
                        itemTier
                );

        double damage =
                percentCurrent(
                        data,
                        itemTier,
                        EssenceStats.MAGIC_DAMAGE,
                        1.0,
                        base.magicDamage()
                );

        double castSpeed =
                percentCurrent(
                        data,
                        itemTier,
                        EssenceStats.MAGIC_CAST_SPEED,
                        1.0,
                        base.magicCastSpeed()
                );

        List<EquipmentTooltipPayload.Line> lines =
                new ArrayList<>();

        lines.add(identity(itemTier, "Caster"));
        lines.add(stat("stat.magic_damage", number(damage)));
        lines.add(stat("stat.cast_speed", number(castSpeed)));

        addProfileAbilities(
                lines,
                data,
                itemTier,
                EquipmentProfiles.MAGIC_CASTER,
                EquipmentActivationType.HELD,
                1.0
        );

        return entry(
                "ascendance_caster",
                itemTier,
                lines
        );
    }

    private static EquipmentTooltipPayload.Entry tool(
            PlayerEssenceData data,
            EquipmentProfileDefinition profile,
            String itemPath,
            String label,
            EquipmentTier itemTier
    ) {
        EquipmentBaselineResult base =
                EquipmentBaselineService.evaluateForEquipmentTier(
                        data,
                        profile.id(),
                        itemTier
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
                        itemTier,
                        EssenceStats.MINING_SPEED,
                        miningStrength,
                        base.miningSpeed()
                );

        double damage =
                percentCurrent(
                        data,
                        itemTier,
                        EssenceStats.MELEE_DAMAGE,
                        damageStrength,
                        base.meleeDamage()
                );

        double attackSpeed =
                percentCurrent(
                        data,
                        itemTier,
                        EssenceStats.MELEE_ATTACK_SPEED,
                        speedStrength,
                        base.meleeAttackSpeed()
                );

        List<EquipmentTooltipPayload.Line> lines =
                new ArrayList<>();

        lines.add(identity(itemTier, label));
        lines.add(stat("stat.mining_speed", number(miningSpeed)));
        lines.add(stat("stat.attack_damage", number(damage)));
        lines.add(stat("stat.attack_speed", number(attackSpeed)));
        lines.add(stat("stat.harvest_level", Integer.toString(base.harvestLevel())));

        addProfileAbilities(
                lines,
                data,
                itemTier,
                profile,
                EquipmentActivationType.HELD,
                1.0
        );

        return entry(
                itemPath,
                itemTier,
                lines
        );
    }

    private static void addProfileAbilities(
            List<EquipmentTooltipPayload.Line> lines,
            PlayerEssenceData data,
            EquipmentTier itemTier,
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
                            itemTier,
                            stat,
                            strength
                    );

            if (resolved <= EPSILON) {
                continue;
            }

            lines.add(ability(stat, resolved));
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
            if (!FracturedEquipmentData.isFractured(stack)) {
                strength +=
                        ArmorStatWeights.weightFor(slot);
            }
        }

        return new ArmorSetState(
                pieces,
                clamp01(strength)
        );
    }

    private static double bonus(
            PlayerEssenceData data,
            EquipmentTier itemTier,
            StatDefinition stat,
            double strength
    ) {
        if (itemTier == EquipmentTier.LATENT) {
            return 0.0D;
        }

        EquipmentTier playerTier = EquipmentTier.fromAscendanceTier(data.getTier());
        EquipmentTier effective = itemTier.order() <= playerTier.order() ? itemTier : playerTier;
        return StatScalingService.scaledBonusForTier(
                data,
                stat,
                effective.ascendanceTier()
        ) * strength;
    }

    private static double percentCurrent(
            PlayerEssenceData data,
            EquipmentTier itemTier,
            StatDefinition stat,
            double strength,
            double base
    ) {
        return base * (1.0D + bonus(data, itemTier, stat, strength) / 100.0D);
    }

    private static EquipmentTooltipPayload.Line identity(
            EquipmentTier itemTier,
            String itemLabel
    ) {
        return translated(
                EquipmentTooltipPayload.Group.IDENTITY,
                EquipmentTooltipPayload.Tone.TIER,
                "tier",
                "@equipment_tier:" + itemTier.serializedName()
        );
    }

    private static EquipmentTooltipPayload.Line stat(
            String key,
            String value
    ) {
        return translated(
                EquipmentTooltipPayload.Group.STATS,
                EquipmentTooltipPayload.Tone.PRIMARY,
                key,
                value
        );
    }

    private static EquipmentTooltipPayload.Line ability(
            StatDefinition stat,
            double value
    ) {
        String key;
        String formatted;
        switch (stat.unit()) {
            case PERCENT -> {
                key = "ability.percent";
                formatted = number(value);
            }
            case HEARTS -> {
                key = "ability.hearts";
                formatted = number(value);
            }
            case HEARTS_PER_SECOND -> {
                key = "ability.hearts_per_second";
                formatted = number(value);
            }
            case SECONDS -> {
                key = "ability.seconds";
                formatted = number(value);
            }
            case BLOCKS -> {
                key = "ability.blocks";
                formatted = number(value);
            }
            case LEVELS -> {
                key = "ability.levels";
                formatted = roman(toGameplayLevel(value));
            }
            case FLAT -> {
                key = "ability.flat";
                formatted = number(value);
            }
            default -> {
                key = "ability.flat";
                formatted = number(value);
            }
        }

        return translated(
                EquipmentTooltipPayload.Group.ESSENCE,
                EquipmentTooltipPayload.Tone.ABILITY,
                key,
                "@stat:" + stat.id(),
                formatted
        );
    }

    private static EquipmentTooltipPayload.Line setBonus(
            String pieces,
            String essenceStrengthPercent
    ) {
        return translated(
                EquipmentTooltipPayload.Group.ESSENCE,
                EquipmentTooltipPayload.Tone.SET_BONUS,
                "set_bonus",
                pieces,
                essenceStrengthPercent
        );
    }

    private static EquipmentTooltipPayload.Line translated(
            EquipmentTooltipPayload.Group group,
            EquipmentTooltipPayload.Tone tone,
            String key,
            String... arguments
    ) {
        return new EquipmentTooltipPayload.Line(
                group,
                tone,
                TOOLTIP_KEY + key,
                List.of(arguments)
        );
    }

    private static EquipmentTooltipPayload.Entry entry(
            String itemPath,
            EquipmentTier itemTier,
            List<EquipmentTooltipPayload.Line> lines
    ) {
        return new EquipmentTooltipPayload.Entry(
                "essence_ascendance:" + itemPath + "#" + itemTier.serializedName(),
                lines
        );
    }

    private static EquipmentTooltipPayload.Entry fractured(
            EquipmentTooltipPayload.Entry latentEntry
    ) {
        int tierSeparator = latentEntry.itemId().lastIndexOf('#');
        String baseId = tierSeparator >= 0
                ? latentEntry.itemId().substring(0, tierSeparator)
                : latentEntry.itemId();

        List<EquipmentTooltipPayload.Line> activeLines = latentEntry.lines()
                .stream()
                .filter(line -> line.group() == EquipmentTooltipPayload.Group.STATS)
                .toList();

        return new EquipmentTooltipPayload.Entry(
                baseId + "#fractured",
                activeLines
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

    private static int toGameplayLevel(
            double value
    ) {
        if (!Double.isFinite(value) || value <= 0.0) {
            return 0;
        }
        long rounded = (long) Math.floor(value + 0.5D);
        return (int) Math.max(0L, Math.min(255L, rounded));
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
