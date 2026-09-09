package com.mistaboom.essence_ascendance.mapping;

import java.nio.file.Path;

/*
 * Public initialization/access facade for item -> Essence mappings.
 */
public final class ItemEssenceMappings {

    private ItemEssenceMappings() {
    }

    public static void init() {
        ItemEssenceMappingManager.init();
    }

    public static ItemEssenceMappingRegistry.ReloadReport reload() {
        return ItemEssenceMappingManager.reload();
    }

    public static Path configDirectory() {
        return ItemEssenceMappingManager.configDirectory();
    }
}
