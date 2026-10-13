package com.mistaboom.essence_ascendance.crucible;

/** Personal receiving preference in the existing stable six-essence order; never a spending permission. */
public final class CrucibleChannelSelection {
    public static final int ALL = (1 << EssenceCrucibleEssences.ORDERED.size()) - 1;
    public static final int SELECT_ALL = EssenceCrucibleEssences.ORDERED.size();
    public static final int SELECT_NONE = SELECT_ALL + 1;
    private CrucibleChannelSelection() { }
    public static boolean valid(int mask) { return mask >= 0 && (mask & ~ALL) == 0; }
    public static boolean selected(int mask, int index) {
        return index >= 0 && index < EssenceCrucibleEssences.ORDERED.size() && (mask & (1 << index)) != 0;
    }
    public static boolean validAction(int action) { return action >= 0 && action <= SELECT_NONE; }
    public static int apply(int mask, int action) {
        if (!valid(mask) || !validAction(action)) throw new IllegalArgumentException("Invalid channel selection");
        return action == SELECT_ALL ? ALL : action == SELECT_NONE ? 0 : mask ^ (1 << action);
    }
}
