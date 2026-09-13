package com.mistaboom.essence_ascendance.balance.engine;

import com.mistaboom.essence_ascendance.balance.config.BalanceSettings;
import com.mistaboom.essence_ascendance.balance.config.BalanceOverrides;
import com.mistaboom.essence_ascendance.valuation.ProceduralValuationResult;
import com.mistaboom.essence_ascendance.valuation.ValuationEvidenceSnapshot;
import net.minecraft.server.MinecraftServer;
import java.util.List;

public record PackEvidenceContext(MinecraftServer server, BalanceSettings settings, BalanceOverrides overrides,
                                  List<ProceduralValuationResult> valuations, ValuationEvidenceSnapshot acquisition) { }
