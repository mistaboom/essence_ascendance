package com.mistaboom.essence_ascendance.data;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

public final class EssenceDataMigration {

    public static final int CURRENT_VERSION =
            5;

    public static final String DATA_VERSION_TAG =
            "data_version";


    private EssenceDataMigration() {
    }


    public static MigrationResult migrate(
            CompoundTag root
    ) {
        int originalVersion =
                getVersion(root);


        if (originalVersion > CURRENT_VERSION) {

            EssenceAscendance.LOGGER.warn(
                    "Essence Ascendance save data version {} is newer than supported version {}. Attempting best-effort load.",
                    originalVersion,
                    CURRENT_VERSION
            );

            return new MigrationResult(
                    root,
                    originalVersion,
                    false
            );
        }


        int version =
                originalVersion;

        boolean migrated =
                false;


        while (version < CURRENT_VERSION) {

            switch (version) {

                case 0 -> {
                    migrateVersion0To1(
                            root
                    );

                    version =
                            1;

                    migrated =
                            true;
                }


                case 1 -> {
                    migrateVersion1To2(
                            root
                    );

                    version =
                            2;

                    migrated =
                            true;
                }


                case 2 -> {
                    migrateVersion2To3(
                            root
                    );

                    version =
                            3;

                    migrated =
                            true;
                }


                case 3 -> {
                    migrateVersion3To4(
                            root
                    );

                    version =
                            4;

                    migrated =
                            true;
                }


                case 4 -> {
                    migrateVersion4To5(
                            root
                    );

                    version =
                            5;

                    migrated =
                            true;
                }


                default ->
                        throw new IllegalStateException(
                                "No Essence Ascendance migration path from data version "
                                        + version
                        );
            }
        }


        root.putInt(
                DATA_VERSION_TAG,
                CURRENT_VERSION
        );


        if (migrated) {

            EssenceAscendance.LOGGER.info(
                    "Migrated Essence Ascendance save data from version {} to version {}",
                    originalVersion,
                    CURRENT_VERSION
            );
        }


        return new MigrationResult(
                root,
                version,
                migrated
        );
    }


    public static void writeCurrentVersion(
            CompoundTag root
    ) {
        root.putInt(
                DATA_VERSION_TAG,
                CURRENT_VERSION
        );
    }


    private static int getVersion(
            CompoundTag root
    ) {
        if (!root.contains(
                DATA_VERSION_TAG,
                Tag.TAG_INT
        )) {
            return 0;
        }

        return root.getInt(
                DATA_VERSION_TAG
        );
    }


    private static void migrateVersion0To1(
            CompoundTag root
    ) {
        /*
         * Version 1 established explicit schema versioning.
         *
         * No structural transformation was required.
         */
    }


    private static void migrateVersion1To2(
            CompoundTag root
    ) {
        /*
         * Version 2 adds optional per-player internal milestone
         * completion data.
         *
         * Existing players simply begin with no internal milestone
         * entries, so no structural transformation is required.
         */
    }


    private static void migrateVersion2To3(
            CompoundTag root
    ) {
        /*
         * Version 3 consolidates the runtime onto the six registered Essences.
         * PlayerEssenceData performs registry-aware reads, ignoring retired or
         * otherwise unknown IDs without converting or refunding them. Marking
         * the save migrated makes the next write persist that normalized view.
         */
    }


    private static void migrateVersion3To4(
            CompoundTag root
    ) {
        /*
         * Version 4 adds optional per-player skill purchases, loadout
         * selections, permanent Player Attunements, and a persisted Nexus
         * revision. Existing players naturally begin with empty collections
         * and revision zero, so no structural transformation is required.
         */
    }


    private static void migrateVersion4To5(
            CompoundTag root
    ) {
        /*
         * Reach moved from Gathering to Utility without changing its stable
         * stat ID. Leaving old investments in place would silently turn
         * Gathering Essence into Utility Essence when a player de-allocates
         * the stat. Refund and clear only that investment during the schema
         * transition, preserving the exact original Essence category.
         *
         * Version 5 also permits optional fractional resource-cost carry in
         * player data. Older players naturally begin without those entries.
         */
        StatCategoryMigrationService.RefundResult refund =
                StatCategoryMigrationService.refundInvestmentToOriginalEssence(
                        root,
                        ResourceLocation.fromNamespaceAndPath(
                                EssenceAscendance.MOD_ID,
                                "reach"
                        ),
                        ResourceLocation.fromNamespaceAndPath(
                                EssenceAscendance.MOD_ID,
                                "gathering"
                        )
                );

        if (refund.affectedPlayers() > 0) {
            EssenceAscendance.LOGGER.info(
                    "Refunded {} Gathering Essence from legacy Reach investments for {} player(s)",
                    refund.totalRefunded(),
                    refund.affectedPlayers()
            );
        }

        if (refund.totalUnrefunded() > 0L) {
            EssenceAscendance.LOGGER.warn(
                    "Could not refund {} legacy Reach investment because affected Gathering Essence balances reached Long.MAX_VALUE",
                    refund.totalUnrefunded()
            );
        }
    }


    public record MigrationResult(
            CompoundTag root,
            int version,
            boolean migrated
    ) {
    }
}
