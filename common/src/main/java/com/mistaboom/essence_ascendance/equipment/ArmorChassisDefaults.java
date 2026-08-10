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


    public static ArmorChassisConfig create(
            BalanceProfileDefinition profile
    ) {

        Objects.requireNonNull(
                profile,
                "Balance profile cannot be null"
        );


        if (profile.id()
                .equals(
                        BalanceProfiles.MODDED.id()
                )) {

            return createModded();
        }


        if (profile.id()
                .equals(
                        BalanceProfiles.VANILLA_PLUS.id()
                )) {

            return createVanillaPlus();
        }


        return createVanilla();
    }


    private static ArmorChassisConfig createVanilla() {

        return create(
                7.0,
                12.0,
                0.0,
                2.0,

                12.0,
                15.0,
                2.0,
                4.0,

                15.0,
                20.0,
                4.0,
                8.0,

                20.0,
                24.0,
                8.0,
                12.0,

                24.0,
                30.0,
                12.0,
                16.0
        );
    }


    private static ArmorChassisConfig createVanillaPlus() {

        return create(
                8.0,
                13.0,
                0.0,
                3.0,

                13.0,
                17.0,
                3.0,
                6.0,

                17.0,
                21.0,
                6.0,
                10.0,

                21.0,
                25.0,
                10.0,
                14.0,

                25.0,
                30.0,
                14.0,
                18.0
        );
    }


    private static ArmorChassisConfig createModded() {

        return create(
                10.0,
                14.0,
                0.0,
                4.0,

                14.0,
                18.0,
                4.0,
                8.0,

                18.0,
                22.0,
                8.0,
                12.0,

                22.0,
                26.0,
                12.0,
                16.0,

                26.0,
                30.0,
                16.0,
                20.0
        );
    }


    private static ArmorChassisConfig create(
            double dormantMinArmor,
            double dormantMaxArmor,
            double dormantMinToughness,
            double dormantMaxToughness,

            double awakenedMinArmor,
            double awakenedMaxArmor,
            double awakenedMinToughness,
            double awakenedMaxToughness,

            double resonantMinArmor,
            double resonantMaxArmor,
            double resonantMinToughness,
            double resonantMaxToughness,

            double ascendantMinArmor,
            double ascendantMaxArmor,
            double ascendantMinToughness,
            double ascendantMaxToughness,

            double transcendentMinArmor,
            double transcendentMaxArmor,
            double transcendentMinToughness,
            double transcendentMaxToughness
    ) {

        Map<
                ResourceLocation,
                ArmorChassisConfig.TierRange
                > ranges =
                new LinkedHashMap<>();


        ranges.put(
                AscendanceTiers.DORMANT.id(),
                new ArmorChassisConfig.TierRange(
                        dormantMinArmor,
                        dormantMaxArmor,
                        dormantMinToughness,
                        dormantMaxToughness
                )
        );


        ranges.put(
                AscendanceTiers.AWAKENED.id(),
                new ArmorChassisConfig.TierRange(
                        awakenedMinArmor,
                        awakenedMaxArmor,
                        awakenedMinToughness,
                        awakenedMaxToughness
                )
        );


        ranges.put(
                AscendanceTiers.RESONANT.id(),
                new ArmorChassisConfig.TierRange(
                        resonantMinArmor,
                        resonantMaxArmor,
                        resonantMinToughness,
                        resonantMaxToughness
                )
        );


        ranges.put(
                AscendanceTiers.ASCENDANT.id(),
                new ArmorChassisConfig.TierRange(
                        ascendantMinArmor,
                        ascendantMaxArmor,
                        ascendantMinToughness,
                        ascendantMaxToughness
                )
        );


        ranges.put(
                AscendanceTiers.TRANSCENDENT.id(),
                new ArmorChassisConfig.TierRange(
                        transcendentMinArmor,
                        transcendentMaxArmor,
                        transcendentMinToughness,
                        transcendentMaxToughness
                )
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