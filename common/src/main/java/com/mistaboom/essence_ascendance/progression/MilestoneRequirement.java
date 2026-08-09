package com.mistaboom.essence_ascendance.progression;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;

public sealed interface MilestoneRequirement
        permits MilestoneRequirement.Milestone,
        MilestoneRequirement.AllOf,
        MilestoneRequirement.AnyOf {


    static Milestone milestone(
            ResourceLocation milestoneId
    ) {
        return new Milestone(
                milestoneId
        );
    }


    static Milestone milestone(
            MilestoneDefinition milestone
    ) {
        return new Milestone(
                milestone.id()
        );
    }


    static AllOf allOf(
            MilestoneRequirement... children
    ) {
        return new AllOf(
                List.of(children)
        );
    }


    static AnyOf anyOf(
            MilestoneRequirement... children
    ) {
        return new AnyOf(
                List.of(children)
        );
    }


    /*
     * ============================================================
     * SINGLE MILESTONE
     * ============================================================
     */

    record Milestone(
            ResourceLocation milestoneId
    ) implements MilestoneRequirement {

        public Milestone {
            Objects.requireNonNull(
                    milestoneId,
                    "Milestone ID cannot be null"
            );
        }
    }


    /*
     * ============================================================
     * ALL OF
     * ============================================================
     */

    record AllOf(
            List<MilestoneRequirement> children
    ) implements MilestoneRequirement {

        public AllOf {

            if (children == null
                    || children.isEmpty()) {

                throw new IllegalArgumentException(
                        "ALL_OF must contain at least one requirement"
                );
            }

            children =
                    List.copyOf(
                            children
                    );
        }
    }


    /*
     * ============================================================
     * ANY OF
     * ============================================================
     */

    record AnyOf(
            List<MilestoneRequirement> children
    ) implements MilestoneRequirement {

        public AnyOf {

            if (children == null
                    || children.isEmpty()) {

                throw new IllegalArgumentException(
                        "ANY_OF must contain at least one requirement"
                );
            }

            children =
                    List.copyOf(
                            children
                    );
        }
    }
}