package com.mistaboom.essence_ascendance.skill.effect;

/** Skill-agnostic geometry for the existing compact cards. All units here are display pixels. */
public final class SkillEffectHudLayout {
    public static final int WIDTH = 158;
    private static final int CONTENT_WIDTH = WIDTH - 14;
    private static final int HEADER_GAP = 5;
    private static final int BADGE_WIDTH = 68;
    private static final float MIN_HEADER_SCALE = .8f;
    private SkillEffectHudLayout() { }

    public static int height(int detailLines) { return 30 + 9 * Math.max(0, detailLines); }

    /** Fit normal numeric badges alongside the title without adding a row or widening a card.
     * Very long translations still use the renderer's existing ellipsis at the minimum scale. */
    public static Header header(int titlePixels, int badgePixels) {
        titlePixels = Math.max(0, titlePixels);
        badgePixels = Math.max(0, badgePixels);
        double total = (double) titlePixels + badgePixels;
        // Preserve the existing full-size header whenever it fits. When shrinking, reserve two
        // pixels for opposite floor/ceil rounding so an exact-width pair does not lose a character.
        float scale = total + HEADER_GAP <= CONTENT_WIDTH ? 1 : (float) Math.max(MIN_HEADER_SCALE,
                (CONTENT_WIDTH - HEADER_GAP - 2) / Math.max(1, total));
        int content = (int) Math.floor(CONTENT_WIDTH / scale);
        int gap = (int) Math.ceil(HEADER_GAP / scale);
        int badge = Math.min(content, (int) Math.floor(BADGE_WIDTH / scale));
        int title = Math.max(0, content - Math.min(badgePixels, badge) - gap);
        return new Header(scale, content, title, badge);
    }

    /** Width limits and right edge in the scaled header's local coordinates. */
    public record Header(float scale, int rightEdge, int titleLimit, int badgeLimit) { }
}
