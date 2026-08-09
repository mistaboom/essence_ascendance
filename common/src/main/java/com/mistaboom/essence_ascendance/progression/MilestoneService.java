package com.mistaboom.essence_ascendance.progression;

import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class MilestoneService {

    private MilestoneService() {
    }


    public static MilestoneProgress evaluate(
            ServerPlayer player,
            MilestoneRequirement requirement
    ) {

        /*
         * ========================================================
         * ALWAYS COMPLETE
         * ========================================================
         */

        if (requirement
                instanceof MilestoneRequirement.Always) {

            return new MilestoneProgress(
                    requirement,
                    true,
                    true,
                    List.of()
            );
        }


        /*
         * ========================================================
         * SINGLE MILESTONE
         * ========================================================
         */

        if (requirement
                instanceof MilestoneRequirement.Milestone leaf) {

            Optional<MilestoneDefinition> definition =
                    EssenceConfigManager
                            .get()
                            .getMilestone(
                                    leaf.milestoneId()
                            );

            if (definition.isEmpty()) {

                return new MilestoneProgress(
                        requirement,
                        false,
                        false,
                        List.of()
                );
            }


            Optional<MilestoneProvider> provider =
                    MilestoneProviderRegistry.get(
                            definition.get().providerId()
                    );

            if (provider.isEmpty()) {

                return new MilestoneProgress(
                        requirement,
                        false,
                        false,
                        List.of()
                );
            }


            boolean complete =
                    provider.get()
                            .isComplete(
                                    player,
                                    definition.get()
                            );


            return new MilestoneProgress(
                    requirement,
                    true,
                    complete,
                    List.of()
            );
        }


        /*
         * ========================================================
         * ALL OF
         * ========================================================
         */

        if (requirement
                instanceof MilestoneRequirement.AllOf allOf) {

            List<MilestoneProgress> children =
                    evaluateChildren(
                            player,
                            allOf.children()
                    );


            boolean resolvable =
                    children.stream()
                            .allMatch(
                                    MilestoneProgress::resolvable
                            );


            boolean complete =
                    resolvable
                            && children.stream()
                            .allMatch(
                                    MilestoneProgress::complete
                            );


            return new MilestoneProgress(
                    requirement,
                    resolvable,
                    complete,
                    children
            );
        }


        /*
         * ========================================================
         * ANY OF
         * ========================================================
         */

        if (requirement
                instanceof MilestoneRequirement.AnyOf anyOf) {

            List<MilestoneProgress> children =
                    evaluateChildren(
                            player,
                            anyOf.children()
                    );


            /*
             * All branches must resolve successfully.
             *
             * This treats a missing provider or bad milestone ID as
             * a configuration problem rather than silently allowing
             * another branch to bypass it.
             */
            boolean resolvable =
                    children.stream()
                            .allMatch(
                                    MilestoneProgress::resolvable
                            );


            boolean complete =
                    resolvable
                            && children.stream()
                            .anyMatch(
                                    MilestoneProgress::complete
                            );


            return new MilestoneProgress(
                    requirement,
                    resolvable,
                    complete,
                    children
            );
        }


        throw new IllegalStateException(
                "Unsupported milestone requirement type: "
                        + requirement.getClass().getName()
        );
    }


    private static List<MilestoneProgress> evaluateChildren(
            ServerPlayer player,
            List<MilestoneRequirement> requirements
    ) {

        List<MilestoneProgress> results =
                new ArrayList<>();


        for (MilestoneRequirement requirement :
                requirements) {

            results.add(
                    evaluate(
                            player,
                            requirement
                    )
            );
        }


        return List.copyOf(
                results
        );
    }
}