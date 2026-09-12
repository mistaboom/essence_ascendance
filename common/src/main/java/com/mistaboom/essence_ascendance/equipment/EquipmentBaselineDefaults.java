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
 * Tool mining speed is now a real tier-owned baseline. Harvest capability is
 * deliberately progression-safe: the current tier can harvest any material
 * required to unlock the next tier. In the built-in progression that means
 * Dormant is iron-equivalent (Diamond-capable) and Awakened is
 * diamond-equivalent (Ancient-Debris-capable).
 *
 * Durability remains a conservative bootstrap value until dynamic max damage
 * is applied by the durability gameplay tranche.
 */
public final class EquipmentBaselineDefaults {

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
                baseline(7.0, 0.0, 4.0, 1.20, 4.0, 1.00, 2.4000, 1.666667, 6.0, 2),
                baseline(12.0, 2.0, 6.0, 1.60, 6.0, 1.25, 3.6000, 2.083333, 8.0, 3),
                baseline(15.0, 4.0, 8.0, 1.90, 8.0, 1.50, 4.8000, 2.500000, 9.0, 4),
                baseline(20.0, 8.0, 11.0, 2.20, 11.0, 1.75, 6.6000, 2.916667, 11.0, 5),
                baseline(24.0, 12.0, 15.0, 2.60, 15.0, 2.00, 9.0000, 3.333333, 14.0, 6)
        );
    }

    private static EquipmentBaselineConfig createVanillaPlus() {
        return create(
                baseline(8.0, 0.0, 5.0, 1.25, 5.0, 1.00, 3.0000, 1.666667, 7.0, 2),
                baseline(13.0, 3.0, 7.0, 1.65, 7.0, 1.30, 4.2000, 2.166667, 9.0, 3),
                baseline(17.0, 6.0, 10.0, 2.00, 10.0, 1.60, 6.0000, 2.666667, 11.0, 4),
                baseline(21.0, 10.0, 14.0, 2.35, 14.0, 1.90, 8.4000, 3.166667, 14.0, 5),
                baseline(25.0, 14.0, 19.0, 2.80, 19.0, 2.25, 11.4000, 3.750000, 18.0, 6)
        );
    }

    private static EquipmentBaselineConfig createModded() {
        return create(
                baseline(10.0, 0.0, 6.0, 1.30, 6.0, 1.10, 3.6000, 1.833333, 8.0, 2),
                baseline(14.0, 4.0, 9.0, 1.75, 9.0, 1.40, 5.4000, 2.333333, 10.0, 3),
                baseline(18.0, 8.0, 13.0, 2.15, 13.0, 1.75, 7.8000, 2.916667, 13.0, 4),
                baseline(22.0, 12.0, 19.0, 2.60, 19.0, 2.15, 11.4000, 3.583333, 17.0, 5),
                baseline(26.0, 16.0, 28.0, 3.20, 28.0, 2.65, 16.8000, 4.416667, 22.0, 6)
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
            double miningSpeed,
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
                miningSpeed,
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
