package com.mistaboom.essence_ascendance.equipment;

import com.mistaboom.essence_ascendance.balance.BalanceProfileDefinition;
import com.mistaboom.essence_ascendance.balance.BalanceProfiles;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class ArmorChassisDefaults {

    private ArmorChassisDefaults() {
    }


    /*
     * ============================================================
     * PROFILE SELECTION
     * ============================================================
     *
     * Chassis defaults follow the selected balance profile.
     *
     * Unknown/custom profiles intentionally fall back to Vanilla
     * chassis power unless their values are overridden in config.
     */

    public static ArmorChassisConfig create(
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
     */

    private static ArmorChassisConfig createVanilla() {

        return create(
                new ArmorChassisConfig.TierRange(
                        7.0,
                        12.0,
                        0.0,
                        2.0
                ),
                new ArmorChassisConfig.TierRange(
                        12.0,
                        15.0,
                        2.0,
                        4.0
                ),
                new ArmorChassisConfig.TierRange(
                        15.0,
                        20.0,
                        4.0,
                        8.0
                ),
                new ArmorChassisConfig.TierRange(
                        20.0,
                        24.0,
                        8.0,
                        12.0
                ),
                new ArmorChassisConfig.TierRange(
                        24.0,
                        30.0,
                        12.0,
                        16.0
                )
        );
    }


    private static ArmorChassisConfig createVanillaPlus() {

        return create(
                new ArmorChassisConfig.TierRange(
                        8.0,
                        13.0,
                        0.0,
                        3.0
                ),
                new ArmorChassisConfig.TierRange(
                        13.0,
                        17.0,
                        3.0,
                        6.0
                ),
                new ArmorChassisConfig.TierRange(
                        17.0,
                        21.0,
                        6.0,
                        10.0
                ),
                new ArmorChassisConfig.TierRange(
                        21.0,
                        25.0,
                        10.0,
                        14.0
                ),
                new ArmorChassisConfig.TierRange(
                        25.0,
                        30.0,
                        14.0,
                        18.0
                )
        );
    }


    private static ArmorChassisConfig createModded() {

        return create(
                new ArmorChassisConfig.TierRange(
                        10.0,
                        14.0,
                        0.0,
                        4.0
                ),
                new ArmorChassisConfig.TierRange(
                        14.0,
                        18.0,
                        4.0,
                        8.0
                ),
                new ArmorChassisConfig.TierRange(
                        18.0,
                        22.0,
                        8.0,
                        12.0
                ),
                new ArmorChassisConfig.TierRange(
                        22.0,
                        26.0,
                        12.0,
                        16.0
                ),
                new ArmorChassisConfig.TierRange(
                        26.0,
                        30.0,
                        16.0,
                        20.0
                )
        );
    }


    /*
     * ============================================================
     * CONFIG CONSTRUCTION
     * ============================================================
     *
     * This shape is intentionally mirrored by
     * WeaponChassisDefaults and should be copied by future chassis
     * systems such as tools.
     */

    private static ArmorChassisConfig create(
            ArmorChassisConfig.TierRange dormant,
            ArmorChassisConfig.TierRange awakened,
            ArmorChassisConfig.TierRange resonant,
            ArmorChassisConfig.TierRange ascendant,
            ArmorChassisConfig.TierRange transcendent
    ) {

        Map<ResourceLocation, ArmorChassisConfig.TierRange> ranges =
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


        return new ArmorChassisConfig(
                /*
                 * Armor:
                 * 100% Defense development.
                 */
                1.0,
                0.0,

                /*
                 * Toughness:
                 * 75% Defense + 25% Vitality.
                 */
                0.75,
                0.25,

                ranges
        );
    }
}
