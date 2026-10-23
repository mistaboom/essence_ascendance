package com.mistaboom.essence_ascendance.team.compat;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.server.MinecraftServer;

import java.util.function.Consumer;

/** Registers the audited 26.1.2 FTB event set on the active loader's bus. */
public final class FtbTeamsEventBridge {
    private FtbTeamsEventBridge() { }

    @ExpectPlatform
    public static void install(Consumer<MinecraftServer> invalidate) {
        throw new AssertionError("Architectury must bind the active loader's FTB event bridge");
    }
}
