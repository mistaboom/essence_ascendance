package com.mistaboom.essence_ascendance.balance.economy;

import com.mistaboom.essence_ascendance.balance.engine.PackEvidence;
import net.minecraft.server.MinecraftServer;

/** Optional adapters supply complete processes, overriding a lower-priority generic process by ID. */
public interface ProductionProvider extends com.mistaboom.essence_ascendance.balance.engine.GenerationProvider {
    // Runs before acquisition. Providers extract facts, never prices or Essence skill rules.
    ProductionGraph collect(com.mistaboom.essence_ascendance.valuation.GenerationDataSnapshot inputs,
                            ProductionGraph sharedProduction);
}
