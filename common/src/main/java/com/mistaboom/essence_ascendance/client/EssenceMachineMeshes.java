package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import net.minecraft.resources.ResourceLocation;

/** Shared static mesh definitions for the modeled machine blocks. */
public final class EssenceMachineMeshes {

    public static final BlockbenchStaticMesh CRUCIBLE = new BlockbenchStaticMesh(
            id("meshes/essence_crucible.eamesh"),
            id("textures/block/essence_crucible.png")
    );

    public static final BlockbenchStaticMesh PYLON = new BlockbenchStaticMesh(
            id("meshes/essence_pylon.eamesh"),
            id("textures/block/essence_pylon.png")
    );

    public static final BlockbenchStaticMesh NEXUS = new BlockbenchStaticMesh(
            id("meshes/ascendance_nexus.eamesh"),
            id("textures/block/ascendance_nexus.png")
    );

    public static final BlockbenchStaticMesh INFUSER = new BlockbenchStaticMesh(
            id("meshes/essence_infuser.eamesh"),
            id("textures/block/essence_infuser.png")
    );

    private EssenceMachineMeshes() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                EssenceAscendance.MOD_ID,
                path
        );
    }
}
