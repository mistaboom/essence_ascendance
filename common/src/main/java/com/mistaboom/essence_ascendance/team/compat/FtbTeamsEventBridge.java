package com.mistaboom.essence_ascendance.team.compat;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.server.MinecraftServer;

import java.util.function.Consumer;

/** Attempts the compiled FTB event API on the active loader; core membership can use polling. */
public final class FtbTeamsEventBridge {
    private FtbTeamsEventBridge() { }

    @ExpectPlatform
    public static void install(Consumer<MinecraftServer> invalidate) {
        throw new AssertionError("Architectury must bind the active loader's FTB event bridge");
    }
}
