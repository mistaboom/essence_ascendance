package com.mistaboom.essence_ascendance.client.nexus;

import com.mistaboom.essence_ascendance.network.BonusTrackSnapshot;
import net.minecraft.resources.ResourceLocation;

/** Pure vertical geometry: effects use their own checkpoints against the common tier guides. */
public record NexusBonusTrackLayout(BonusTrackSnapshot track, int top, int bottom) {
    public NexusBonusTrackLayout {
        // Extremely short windows can collapse the enclosing content band to one pixel.
        bottom = Math.max(top + 1, bottom);
    }

    public double startPosition() {
        for (int i = 0; i < track.checkpoints().size(); i++)
            if (track.checkpoints().get(i).tierId().equals(track.startTier()))
                return i == 0 ? 0 : track.tierPositions().get(i - 1);
        return 0;
    }

    public double completionPosition() {
        for (int i = 0; i < track.checkpoints().size(); i++)
            if (track.checkpoints().get(i).tierId().equals(track.completionTier())) return track.tierPositions().get(i);
        return 1;
    }

    /** A tier names the band it unlocks, rather than the upper boundary of that band. */
    public double bandCenterPosition(int checkpointIndex) {
        double entry = checkpointIndex == 0 ? 0 : track.tierPositions().get(checkpointIndex - 1);
        return (entry + track.tierPositions().get(checkpointIndex)) / 2;
    }

    public int bandCenterY(int checkpointIndex) { return yForPosition(bandCenterPosition(checkpointIndex)); }

    /** The tier that permits passing this boundary; a permanent end retains its completion tier. */
    public ResourceLocation boundaryUnlockTier(int checkpointIndex) {
        for (int i = checkpointIndex + 1; i < track.checkpoints().size(); i++)
            if (track.checkpoints().get(i).purchasable()) return track.checkpoints().get(i).tierId();
        return track.completionTier();
    }

    public ResourceLocation capUnlockTier(ResourceLocation playerTier) {
        for (int i = 0; i < track.checkpoints().size(); i++)
            if (track.checkpoints().get(i).tierId().equals(playerTier)) return boundaryUnlockTier(i);
        return track.startTier();
    }

    public double positionForEffect(double effect) {
        double requested = clamp(effect);
        double previousEffect = 0;
        double previousPosition = startPosition();
        for (int i = 0; i < track.checkpoints().size(); i++) {
            double nextEffect = track.checkpoints().get(i).effectFraction();
            if (nextEffect <= previousEffect) continue;
            double nextPosition = track.tierPositions().get(i);
            if (requested <= nextEffect) return previousPosition
                    + (nextPosition - previousPosition) * (requested - previousEffect) / (nextEffect - previousEffect);
            previousPosition = nextPosition;
            previousEffect = nextEffect;
        }
        return track.available() ? completionPosition() : startPosition();
    }

    public double effectForPosition(double position) {
        if (!track.available() || position <= startPosition()) return 0;
        double previousEffect = 0;
        double previousPosition = startPosition();
        for (int i = 0; i < track.checkpoints().size(); i++) {
            double nextEffect = track.checkpoints().get(i).effectFraction();
            if (nextEffect <= previousEffect) continue;
            double nextPosition = track.tierPositions().get(i);
            if (position <= nextPosition) return previousEffect
                    + (nextEffect - previousEffect) * (position - previousPosition) / (nextPosition - previousPosition);
            previousPosition = nextPosition;
            previousEffect = nextEffect;
        }
        return 1;
    }

    public int yForPosition(double position) { return bottom - (int) Math.round((bottom - top) * clamp(position)); }
    public int yForEffect(double effect) { return yForPosition(positionForEffect(effect)); }
    public double effectForY(double y) { return effectForPosition((bottom - y) / (bottom - top)); }
    public int startY() { return yForPosition(startPosition()); }
    public int completionY() { return yForPosition(completionPosition()); }

    public static int visibleCount(int viewportWidth, int trackCount, int preferredWidth, int gap) {
        return Math.max(1, Math.min(Math.max(1, trackCount), (Math.max(1, viewportWidth) + gap) / (preferredWidth + gap)));
    }

    public static int trackWidth(int viewportWidth, int count, int preferredWidth, int gap) {
        int columns = Math.max(1, count);
        return Math.max(1, Math.min(preferredWidth, Math.max(1, viewportWidth - gap * (columns - 1)) / columns));
    }

    private static double clamp(double value) { return Math.max(0, Math.min(1, value)); }
}
