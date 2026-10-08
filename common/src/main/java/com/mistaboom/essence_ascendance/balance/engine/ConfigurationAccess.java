package com.mistaboom.essence_ascendance.balance.engine;

import java.util.*;

/** Conservative, bounded conjunction of already solved item access; no registry membership proof.
 * Does not guess joint finite quest inventories or search a Cartesian set of configurations. */
public final class ConfigurationAccess {
    private ConfigurationAccess() { }
    public record Proof(CompetitiveCapabilities.Placement placement, List<String> selected, List<String> unknown) { }
    public static Proof require(Map<String, ResourceEvidence> resources, List<List<String>> requirements) {
        return new Resolver(resources).require(requirements);
    }
    /** Finite quest lineage cannot erase a later, independently proved renewable
     * operation. This refines only quest-derived restrictions; explicit exact-recipe
     * requirements are applied separately by their caller. Advisory source rows and
     * unproved/uncertain renewability never remove a finite claim. */
    public static Set<String> remainingFiniteClaims(Map<String, ResourceEvidence> resources, Set<String> claims) {
        var remaining = new HashSet<>(claims);
        remaining.removeIf(id -> {
            var resource = resources.get(id);
            return resource != null && resource.reachable() && resource.external()
                    && (resource.availability() == Availability.RENEWABLE_MANUAL || resource.availability() == Availability.EFFECTIVELY_INFINITE)
                    && resource.sources().stream().anyMatch(source -> source.renewable() && source.confidence() >= .5
                    && source.expectedOutput() > 0 && (source.kind() == AcquisitionSource.Kind.PLAYER_ACTION || source.kind() == AcquisitionSource.Kind.FARMING)
                    && source.availability() != null && source.availability().accessProven() && source.availability().uncertainty().isEmpty()
                    && source.availability().category() == SourceAvailability.Category.CONDITIONAL_RENEWABLE);
        });
        return Set.copyOf(remaining);
    }
    /** One per provider operation; repeated seed/soil/module inputs reuse normalized placements. */
    public static final class Resolver {
        private final Map<String, ResourceEvidence> resources;
        private final Set<String> constrained;
        private final Map<String, CompetitiveCapabilities.Placement> placements = new HashMap<>();
        public Resolver(Map<String, ResourceEvidence> resources) { this(resources, Set.of()); }
        public Resolver(Map<String, ResourceEvidence> resources, Set<String> constrained) { this.resources = resources; this.constrained = constrained; }
        private CompetitiveCapabilities.Placement placement(String id) {
            return placements.computeIfAbsent(id, key -> CompetitiveCapabilities.placement(resources, key));
        }
        public Proof require(List<List<String>> requirements) {
        List<String> selected = new ArrayList<>(), unknown = new ArrayList<>();
        List<AcquisitionSource> acquisition = new ArrayList<>();
        ProgressionBand stage = ProgressionBand.ENTRY; double confidence = 1;
        for (var group : requirements) {
            String best = group.stream().distinct().filter(id -> placement(id).reachable())
                    .filter(id -> !constrained.contains(id))
                    .filter(id -> independent(resources.get(id)))
                    .min(Comparator.<String, ProgressionBand>comparing(id -> placement(id).stage())
                            .thenComparing(Comparator.<String>comparingDouble(id -> resources.get(id).confidence()).reversed())
                            .thenComparing(Comparator.naturalOrder())).orElse(null);
            if (best == null) { unknown.add("Required configuration input has no independent supported acquisition: " + group); continue; }
            var placement = placement(best);
            selected.add(best); acquisition.addAll(placement.acquisition());
            stage = ProgressionBand.at(Math.max(stage.ordinal(), placement.stage().ordinal()));
            confidence = Math.min(confidence, placement.confidence());
        }
        if (requirements.isEmpty()) unknown.add("Configuration access requirements absent");
        boolean reached = unknown.isEmpty();
        return new Proof(new CompetitiveCapabilities.Placement(stage, reached, reached ? confidence : 0,
                acquisition.stream().distinct().toList()), List.copyOf(selected), List.copyOf(unknown));
        }
    }
    private static boolean independent(ResourceEvidence resource) {
        if (resource.confidence() < .5) return false;
        // The shared compact resource does not retain a complete joint quest inventory proof.
        // Reward-only setup is diagnosed until a provider supplies that proof; do not combine exclusive rewards.
        return resource.sources().stream().anyMatch(s -> s.kind() != AcquisitionSource.Kind.QUEST_REWARD
                && s.kind() != AcquisitionSource.Kind.ADMINISTRATIVE && s.expectedOutput() > 0 && s.confidence() >= .5
                && (s.availability() == null || s.availability().accessProven()));
    }
}
