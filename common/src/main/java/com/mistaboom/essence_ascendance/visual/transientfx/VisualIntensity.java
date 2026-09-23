package com.mistaboom.essence_ascendance.visual.transientfx;

/** Presentation importance only; it never changes gameplay outcomes. */
public enum VisualIntensity {
    MICRO(1, 32.0, 10.0),
    STANDARD(2, 56.0, 24.0),
    MAJOR(4, 80.0, 40.0),
    SIGNATURE(8, 112.0, 64.0);

    private final int budgetCost;
    private final double worldRange;
    private final double detailRange;

    VisualIntensity(int budgetCost, double worldRange, double detailRange) {
        this.budgetCost = budgetCost;
        this.worldRange = worldRange;
        this.detailRange = detailRange;
    }

    public int budgetCost() { return budgetCost; }
    public double worldRange() { return worldRange; }
    public double detailRange() { return detailRange; }
}
