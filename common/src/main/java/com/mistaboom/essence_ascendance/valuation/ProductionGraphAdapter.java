package com.mistaboom.essence_ascendance.valuation;

import com.mistaboom.essence_ascendance.balance.economy.ProductionGraph;
import net.minecraft.server.MinecraftServer;

/** The valuation and final economy use the same once-normalized loaded recipes. */
public final class ProductionGraphAdapter {
    private ProductionGraphAdapter() { }
    public static ProductionGraph collect(MinecraftServer server) {
        return ProceduralValuationEngine.generationData(server).production();
    }
}
