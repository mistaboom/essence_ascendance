package com.mistaboom.essence_ascendance.gameplay;

import net.minecraft.resources.ResourceLocation;
import java.util.Objects;
import java.util.UUID;

/** Immutable committed progression facts. No award identity or mutable native object crosses this boundary. */
public sealed interface ProgressionOutcome {
    String actionId();
    UUID actor();
    long committedTick();

    private static void validate(String actionId, UUID actor, long tick) {
        Objects.requireNonNull(actionId); Objects.requireNonNull(actor);
        if (actionId.isBlank() || actionId.length() > 256 || tick < 0)
            throw new IllegalArgumentException("Invalid committed progression identity");
    }

    record SkillPurchased(String actionId, UUID actor, long committedTick, ResourceLocation skillId,
                          ResourceLocation categoryId, int oldRank, int newRank) implements ProgressionOutcome {
        public SkillPurchased {
            validate(actionId, actor, committedTick); Objects.requireNonNull(skillId); Objects.requireNonNull(categoryId);
            if (oldRank < 0 || newRank <= oldRank) throw new IllegalArgumentException("Purchase must increase rank");
        }
    }

    record BonusPurchased(String actionId, UUID actor, long committedTick, ResourceLocation statId,
                          long oldInvestment, long newInvestment, double oldMaximumHealth,
                          double newMaximumHealth, double purchasedBonusIncrease) implements ProgressionOutcome {
        public BonusPurchased {
            validate(actionId, actor, committedTick); Objects.requireNonNull(statId);
            if (oldInvestment < 0 || newInvestment <= oldInvestment || !Double.isFinite(oldMaximumHealth)
                    || !Double.isFinite(newMaximumHealth) || oldMaximumHealth <= 0 || newMaximumHealth <= 0
                    || !Double.isFinite(purchasedBonusIncrease) || purchasedBonusIncrease < 0)
                throw new IllegalArgumentException("Invalid committed Bonus purchase");
        }
    }

    record Ascended(String actionId, UUID actor, long committedTick, ResourceLocation fromTierId,
                    ResourceLocation toTierId, int fromTierOrder, int toTierOrder, String chapterId,
                    int completedSeals, boolean manual, boolean night, boolean fullFunctionalArmor)
            implements ProgressionOutcome {
        public Ascended {
            validate(actionId, actor, committedTick); Objects.requireNonNull(fromTierId); Objects.requireNonNull(toTierId);
            Objects.requireNonNull(chapterId);
            if (fromTierOrder < 0 || toTierOrder <= fromTierOrder || chapterId.length() > 128
                    || completedSeals < 0 || completedSeals > 6) throw new IllegalArgumentException("Invalid Ascension transition");
        }
    }

    record ActivityContributed(String actionId, UUID actor, long committedTick, String chapterId,
                               String categoryId, String activityId, long positiveContribution)
            implements ProgressionOutcome {
        public ActivityContributed {
            validate(actionId, actor, committedTick); Objects.requireNonNull(chapterId);
            Objects.requireNonNull(categoryId); Objects.requireNonNull(activityId);
            if (chapterId.isBlank() || chapterId.length() > 128 || categoryId.isBlank() || categoryId.length() > 128
                    || activityId.isBlank() || activityId.length() > 128 || positiveContribution <= 0
                    || positiveContribution > 1_000_000_000L) throw new IllegalArgumentException("Contribution must be positive");
        }
    }

    record SealsCompleted(String actionId, UUID actor, long committedTick, String chapterId,
                          int playerTierOrder, int completedSeals) implements ProgressionOutcome {
        public SealsCompleted {
            validate(actionId, actor, committedTick); Objects.requireNonNull(chapterId);
            if (chapterId.isBlank() || chapterId.length() > 128 || playerTierOrder < 0 || completedSeals != 6)
                throw new IllegalArgumentException("Seal completion requires all six categories");
        }
    }

    record Crafted(String actionId, UUID actor, long committedTick, ResourceLocation itemId,
                   int count) implements ProgressionOutcome {
        public Crafted {
            validate(actionId, actor, committedTick); Objects.requireNonNull(itemId);
            if (count <= 0) throw new IllegalArgumentException("Invalid crafted output count");
        }
    }

    record NexusUsed(String actionId, UUID actor, long committedTick, ResourceLocation dimension,
                     long position, UUID builder) implements ProgressionOutcome {
        public NexusUsed { validate(actionId, actor, committedTick); Objects.requireNonNull(dimension); }
    }
}
