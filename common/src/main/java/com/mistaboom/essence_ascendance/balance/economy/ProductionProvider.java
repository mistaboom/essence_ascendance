package com.mistaboom.essence_ascendance.balance.economy;

import com.mistaboom.essence_ascendance.balance.engine.PackEvidence;
import net.minecraft.server.MinecraftServer;

/** Optional adapters supply complete processes, overriding a lower-priority generic process by ID. */
public interface ProductionProvider {
    String id();
    int priority();
    ProductionGraph collect(MinecraftServer server, PackEvidence evidence);
}
