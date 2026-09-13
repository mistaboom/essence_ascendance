package com.mistaboom.essence_ascendance.equipment;

/** Loader-neutral view of the server-synchronized anvil level cost. */
public interface AnvilMenuCostView {

    int essenceAscendance$displayCost();
    default int essenceAscendance$originalCost() { return essenceAscendance$displayCost(); }
}
