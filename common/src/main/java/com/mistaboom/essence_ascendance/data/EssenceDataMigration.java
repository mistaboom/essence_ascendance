package com.mistaboom.essence_ascendance.data;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public final class EssenceDataMigration {

    public static final int CURRENT_VERSION =
            4;

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


    public record MigrationResult(
            CompoundTag root,
            int version,
            boolean migrated
    ) {
    }
}
