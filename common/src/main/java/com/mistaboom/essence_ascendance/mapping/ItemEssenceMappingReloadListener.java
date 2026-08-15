package com.mistaboom.essence_ascendance.mapping;

/*
 * Legacy source shim from the first item-mapping prototype.
 *
 * Item mappings no longer use Minecraft's datapack reload system. The active
 * implementation is ItemEssenceMappingManager and reads global config files.
 *
 * This class intentionally remains temporarily so this replacement patch can be
 * copied over a working tree that already contains the earlier prototype
 * without requiring the user to manually delete a Java source file.
 */
@Deprecated(forRemoval = true)
final class ItemEssenceMappingReloadListener {

    private ItemEssenceMappingReloadListener() {
    }
}
