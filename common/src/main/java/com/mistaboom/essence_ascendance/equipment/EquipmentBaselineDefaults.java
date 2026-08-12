package com.mistaboom.essence_ascendance.equipment;

import com.mistaboom.essence_ascendance.balance.BalanceProfileDefinition;
import com.mistaboom.essence_ascendance.balance.BalanceProfiles;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/*
 * Built-in tier baselines.
 *
 * Existing armor/weapon chassis MINIMUMS were intentionally reused as the
 * fixed tier baselines. That preserves the old zero-investment physical floor
 * while moving all player specialization back into StatScalingService.
 *
 * Mining speed and durability remain conservative bootstrap placeholders until
 * the actual tool/gameplay effect tranche deliberately balances them.
 */
public final class EquipmentBaselineDefaults {

    private static final double BOOTSTRAP_MINING_SPEED = 1.0;
    private static final int BOOTSTRAP_DURABILITY = 2031;

    private EquipmentBaselineDefaults() {
    }

    public static EquipmentBaselineConfig create(
            BalanceProfileDefinition profile
    ) {
        Objects.requireNonNull(profile, "Balance profile cannot be null");

        if (profile.id().equals(BalanceProfiles.MODDED.id())) {
            return createModded();
        }
        if (profile.id().equals(BalanceProfiles.VANILLA_PLUS.id())) {
            return createVanillaPlus();
        }
        return createVanilla();
    }

    private static EquipmentBaselineConfig createVanilla() {
        return create(
                baseline(7.0, 0.0, 4.0, 1.20, 4.0, 1.00, 4.0, 1.00, 0),
                baseline(12.0, 2.0, 6.0, 1.60, 6.0, 1.25, 6.0, 1.25, 1),
                baseline(15.0, 4.0, 8.0, 1.90, 8.0, 1.50, 8.0, 1.50, 2),
                baseline(20.0, 8.0, 11.0, 2.20, 11.0, 1.75, 11.0, 1.75, 3),
                baseline(24.0, 12.0, 15.0, 2.60, 15.0, 2.00, 15.0, 2.00, 4)
        );
    }

    private static EquipmentBaselineConfig createVanillaPlus() {
        return create(
                baseline(8.0, 0.0, 5.0, 1.25, 5.0, 1.00, 5.0, 1.00, 0),
                baseline(13.0, 3.0, 7.0, 1.65, 7.0, 1.30, 7.0, 1.30, 1),
                baseline(17.0, 6.0, 10.0, 2.00, 10.0, 1.60, 10.0, 1.60, 2),
                baseline(21.0, 10.0, 14.0, 2.35, 14.0, 1.90, 14.0, 1.90, 3),
                baseline(25.0, 14.0, 19.0, 2.80, 19.0, 2.25, 19.0, 2.25, 4)
        );
    }

    private static EquipmentBaselineConfig createModded() {
        return create(
                baseline(10.0, 0.0, 6.0, 1.30, 6.0, 1.10, 6.0, 1.10, 0),
                baseline(14.0, 4.0, 9.0, 1.75, 9.0, 1.40, 9.0, 1.40, 1),
                baseline(18.0, 8.0, 13.0, 2.15, 13.0, 1.75, 13.0, 1.75, 2),
                baseline(22.0, 12.0, 19.0, 2.60, 19.0, 2.15, 19.0, 2.15, 3),
                baseline(26.0, 16.0, 28.0, 3.20, 28.0, 2.65, 28.0, 2.65, 4)
        );
    }

    private static EquipmentBaselineConfig.TierBaseline baseline(
            double armor,
            double toughness,
            double meleeDamage,
            double meleeAttackSpeed,
            double rangedDamage,
            double rangedAttackSpeed,
            double magicDamage,
            double magicCastSpeed,
            int harvestLevel
    ) {
        return new EquipmentBaselineConfig.TierBaseline(
                armor,
                toughness,
                meleeDamage,
                meleeAttackSpeed,
                rangedDamage,
                rangedAttackSpeed,
                magicDamage,
                magicCastSpeed,
                BOOTSTRAP_MINING_SPEED,
                harvestLevel,
                BOOTSTRAP_DURABILITY
        );
    }

    private static EquipmentBaselineConfig create(
            EquipmentBaselineConfig.TierBaseline dormant,
            EquipmentBaselineConfig.TierBaseline awakened,
            EquipmentBaselineConfig.TierBaseline resonant,
            EquipmentBaselineConfig.TierBaseline ascendant,
            EquipmentBaselineConfig.TierBaseline transcendent
    ) {
        Map<ResourceLocation, EquipmentBaselineConfig.TierBaseline> baselines =
                new LinkedHashMap<>();

        baselines.put(AscendanceTiers.DORMANT.id(), dormant);
        baselines.put(AscendanceTiers.AWAKENED.id(), awakened);
        baselines.put(AscendanceTiers.RESONANT.id(), resonant);
        baselines.put(AscendanceTiers.ASCENDANT.id(), ascendant);
        baselines.put(AscendanceTiers.TRANSCENDENT.id(), transcendent);

        return new EquipmentBaselineConfig(baselines);
    }
}
