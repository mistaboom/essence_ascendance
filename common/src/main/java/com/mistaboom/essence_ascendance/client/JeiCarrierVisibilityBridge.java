package com.mistaboom.essence_ascendance.client;

import java.util.function.Consumer;

/**
 * Optional-JEI-safe bridge for server-authoritative Essentium carrier visibility.
 *
 * Client gameplay/network code can update the visible Skill Essence state without
 * loading any JEI classes. The optional JEI plugin installs a runtime callback
 * while JEI is available and removes it when JEI shuts down.
 */
public final class JeiCarrierVisibilityBridge {

    private static Boolean skillEssencesVisible;
    private static Consumer<Boolean> runtimeListener;

    private JeiCarrierVisibilityBridge() {
    }

    public static synchronized void setSkillEssencesVisible(boolean visible) {
        boolean changed = skillEssencesVisible == null
                || skillEssencesVisible != visible;

        skillEssencesVisible = visible;

        if (changed && runtimeListener != null) {
            runtimeListener.accept(visible);
        }
    }

    public static synchronized void clearSkillEssenceVisibility() {
        skillEssencesVisible = null;
    }

    public static synchronized Boolean skillEssencesVisible() {
        return skillEssencesVisible;
    }

    public static synchronized void installRuntimeListener(
            Consumer<Boolean> listener
    ) {
        runtimeListener = listener;

        if (runtimeListener != null && skillEssencesVisible != null) {
            runtimeListener.accept(skillEssencesVisible);
        }
    }

    public static synchronized void clearRuntimeListener() {
        runtimeListener = null;
    }
}
