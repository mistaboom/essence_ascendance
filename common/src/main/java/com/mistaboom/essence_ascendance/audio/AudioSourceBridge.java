package com.mistaboom.essence_ascendance.audio;

import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.function.Consumer;

/** Client-installed snapshot adapter, following ArchiveClientBridge's dedicated-server boundary. */
public final class AudioSourceBridge {
    private static Consumer<BlockEntity> observer = ignored -> { };
    private AudioSourceBridge() { }
    public static void install(Consumer<BlockEntity> adapter) { observer = java.util.Objects.requireNonNull(adapter); }
    public static void changed(BlockEntity source) { observer.accept(source); }
}
