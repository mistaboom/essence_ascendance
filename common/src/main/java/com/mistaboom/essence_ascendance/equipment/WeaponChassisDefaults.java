package com.mistaboom.essence_ascendance.equipment;

import com.mistaboom.essence_ascendance.balance.BalanceProfileDefinition;
import com.mistaboom.essence_ascendance.balance.BalanceProfiles;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class WeaponChassisDefaults {

    private WeaponChassisDefaults() {
    }


    /*
     * ============================================================
     * PROFILE SELECTION
     * ============================================================
     *
     * Weapon chassis defaults follow the same profile pattern as
     * ArmorChassisDefaults.
     *
     * Unknown/custom profiles intentionally fall back to Vanilla
     * chassis power unless their values are overridden in config.
     */

    public static WeaponChassisConfig create(
            BalanceProfileDefinition profile
    ) {

        Objects.requireNonNull(
                profile,
                "Balance profile cannot be null"
        );


        if (profile.id().equals(
                BalanceProfiles.MODDED.id()
        )) {

            return createModded();
        }


        if (profile.id().equals(
                BalanceProfiles.VANILLA_PLUS.id()
        )) {

            return createVanillaPlus();
        }


        return createVanilla();
    }


    /*
     * ============================================================
     * BUILT-IN PROFILE RANGES
     * ============================================================
     *
     * Damage values are absolute chassis targets.
     *
     * Melee attack speed is attacks per second.
     * Ranged attack speed is full draws per second.
     * Magic cast speed is casts per second.
     *
     * The next tier's minimum equals the previous tier's maximum,
     * so reaching a new tier never introduces a lower chassis floor.
     *
     * These are initial balance values and remain config-overridable.
     */

    private static WeaponChassisConfig createVanilla() {

        return create(
                range(
                        4.0,
                        6.0,
                        1.20,
                        1.60,
                        1.00,
                        1.25
                ),
                range(
                        6.0,
                        8.0,
                        1.60,
                        1.90,
                        1.25,
                        1.50
                ),
                range(
                        8.0,
                        11.0,
                        1.90,
                        2.20,
                        1.50,
                        1.75
                ),
                range(
                        11.0,
                        15.0,
                        2.20,
                        2.60,
                        1.75,
                        2.00
                ),
                range(
                        15.0,
                        20.0,
                        2.60,
                        3.00,
                        2.00,
                        2.50
                )
        );
    }


    private static WeaponChassisConfig createVanillaPlus() {

        return create(
                range(
                        5.0,
                        7.0,
                        1.25,
                        1.65,
                        1.00,
                        1.30
                ),
                range(
                        7.0,
                        10.0,
                        1.65,
                        2.00,
                        1.30,
                        1.60
                ),
                range(
                        10.0,
                        14.0,
                        2.00,
                        2.35,
                        1.60,
                        1.90
                ),
                range(
                        14.0,
                        19.0,
                        2.35,
                        2.80,
                        1.90,
                        2.25
                ),
                range(
                        19.0,
                        26.0,
                        2.80,
                        3.30,
                        2.25,
                        2.75
                )
        );
    }


    private static WeaponChassisConfig createModded() {

        return create(
                range(
                        6.0,
                        9.0,
                        1.30,
                        1.75,
                        1.10,
                        1.40
                ),
                range(
                        9.0,
                        13.0,
                        1.75,
                        2.15,
                        1.40,
                        1.75
                ),
                range(
                        13.0,
                        19.0,
                        2.15,
                        2.60,
                        1.75,
                        2.15
                ),
                range(
                        19.0,
                        28.0,
                        2.60,
                        3.20,
                        2.15,
                        2.65
                ),
                range(
                        28.0,
                        40.0,
                        3.20,
                        4.00,
                        2.65,
                        3.25
                )
        );
    }


    /*
     * Builds a symmetrical initial range for ranged and magic
     * weapons. They remain separate fields in WeaponChassisConfig,
     * so config authors can tune them independently.
     */

    private static WeaponChassisConfig.TierRange range(
            double minDamage,
            double maxDamage,
            double minMeleeAttackSpeed,
            double maxMeleeAttackSpeed,
            double minRangedAndMagicSpeed,
            double maxRangedAndMagicSpeed
    ) {

        return new WeaponChassisConfig.TierRange(
                minDamage,
                maxDamage,

                minMeleeAttackSpeed,
                maxMeleeAttackSpeed,

                minDamage,
                maxDamage,

                minRangedAndMagicSpeed,
                maxRangedAndMagicSpeed,

                minDamage,
                maxDamage,

                minRangedAndMagicSpeed,
                maxRangedAndMagicSpeed
        );
    }


    /*
     * ============================================================
     * CONFIG CONSTRUCTION
     * ============================================================
     *
     * This shape intentionally mirrors ArmorChassisDefaults and
     * should be copied by future chassis systems such as tools.
     */

    private static WeaponChassisConfig create(
            WeaponChassisConfig.TierRange dormant,
            WeaponChassisConfig.TierRange awakened,
            WeaponChassisConfig.TierRange resonant,
            WeaponChassisConfig.TierRange ascendant,
            WeaponChassisConfig.TierRange transcendent
    ) {

        Map<ResourceLocation, WeaponChassisConfig.TierRange> ranges =
                new LinkedHashMap<>();


        ranges.put(
                AscendanceTiers.DORMANT.id(),
                dormant
        );

        ranges.put(
                AscendanceTiers.AWAKENED.id(),
                awakened
        );

        ranges.put(
                AscendanceTiers.RESONANT.id(),
                resonant
        );

        ranges.put(
                AscendanceTiers.ASCENDANT.id(),
                ascendant
        );

        ranges.put(
                AscendanceTiers.TRANSCENDENT.id(),
                transcendent
        );


        return new WeaponChassisConfig(
                ranges
        );
    }
}
