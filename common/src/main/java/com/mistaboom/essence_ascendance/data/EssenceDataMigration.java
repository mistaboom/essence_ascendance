package com.mistaboom.essence_ascendance.data;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public final class EssenceDataMigration {

    public static final int CURRENT_VERSION = 1;

    public static final String DATA_VERSION_TAG =
            "data_version";

    private EssenceDataMigration() {
    }


    public static MigrationResult migrate(
            CompoundTag root
    ) {
        int originalVersion =
                getVersion(root);

        /*
         * A save created by a newer version of Essence Ascendance
         * should not be blindly "migrated backwards".
         *
         * We still attempt to load the fields we understand, but
         * warn loudly so this situation is visible during development.
         */
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

                /*
                 * Version 0:
                 *
                 * Legacy data created before explicit schema
                 * versioning existed.
                 *
                 * No structural conversion is currently necessary.
                 *
                 * Existing available Essence and invested Essence
                 * already use compatible ResourceLocation keys.
                 *
                 * Missing Ascendance tier data already defaults
                 * safely to Dormant.
                 */
                case 0 -> {
                    migrateVersion0To1(root);
                    version = 1;
                    migrated = true;
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
         * Version 1 establishes explicit save-data versioning.
         *
         * The underlying player data layout does not need to
         * change, so this migration intentionally performs no
         * structural transformation.
         */
    }


    public record MigrationResult(
            CompoundTag root,
            int version,
            boolean migrated
    ) {
    }
}