package com.mistaboom.essence_ascendance.valuation;

/** Economic confidence cannot be manufactured by semantic/progression hints. */
final class ProceduralValuationConfidence {
    private ProceduralValuationConfidence() { }

    static double bound(double proposed, boolean knownAcquisition) {
        if (!Double.isFinite(proposed)) return 0.10;
        return Math.max(0.10, Math.min(knownAcquisition ? 0.97 : 0.49, proposed));
    }
}
