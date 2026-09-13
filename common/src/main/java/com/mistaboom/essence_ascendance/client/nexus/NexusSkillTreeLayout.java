package com.mistaboom.essence_ascendance.client.nexus;

import com.mistaboom.essence_ascendance.skill.SkillDefinition;
import com.mistaboom.essence_ascendance.skill.SkillLayoutHint;
import com.mistaboom.essence_ascendance.tier.AscendanceTierDefinition;
import com.mistaboom.essence_ascendance.tier.AscendanceTierRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic, relationship-driven layout for one skill page.
 *
 * <p>The required Ascendance tier is a hard horizontal rank. Vertical positions
 * are computed as a layered graph: choice-group members in the same tier form
 * indivisible blocks, barycentric sweeps reduce crossings between ranks, and a
 * final constrained relaxation aligns related nodes without allowing nodes in
 * a column to collide. No skill ID has a hand-authored coordinate and no random
 * or force-directed state is involved, so the same catalog always produces the
 * same layout.</p>
 */
public final class NexusSkillTreeLayout {
    public static final int NODE_WIDTH = 108;
    public static final int NODE_HEIGHT = 32;
    public static final int COLUMN_GAP = 31;
    public static final int ROW_GAP = 30;
    public static final int CONTENT_PADDING = 10;
    public static final int TIER_HEADER_HEIGHT = 16;
    /** Shared visual rhythm for choice frames and connection lanes. */
    public static final int ROUTE_SPACING = 6;
    public static final int CHOICE_FRAME_PADDING = ROUTE_SPACING;

    /**
     * One unit is half a row pitch. With the 31 px tier gutter this also
     * makes a one-unit change across one tier an exact 45-degree diagonal.
     */
    private static final int HALF_ROW_STEP = (NODE_HEIGHT + ROW_GAP) / 2;
    private static final int MIN_NODE_SEPARATION_UNITS = 2;
    private static final int ORDERING_SWEEPS = 6;
    private static final int POSITIONING_SWEEPS = 8;

    private static final int PREREQUISITE_WEIGHT = 8;
    private static final int REPLACEMENT_WEIGHT = 5;
    private static final int CHOICE_AFFINITY_WEIGHT = 4;
    private static final double FAMILY_AFFINITY_WEIGHT = 1.25D;
    private static final double POSITION_ANCHOR_WEIGHT = 0.35D;
    private static final double CROSSING_PENALTY = 10_000.0D;
    private static final double NODE_PENETRATION_PENALTY = 20_000.0D;

    private NexusSkillTreeLayout() {
    }

    public static Layout build(List<SkillDefinition> definitions) {
        List<AscendanceTierDefinition> tiers =
                new ArrayList<>(AscendanceTierRegistry.powerTiers());
        tiers.sort(Comparator.comparingInt(AscendanceTierDefinition::order));

        Map<ResourceLocation, Integer> tierColumns = new LinkedHashMap<>();
        for (int index = 0; index < tiers.size(); index++) {
            tierColumns.put(tiers.get(index).id(), index);
        }

        List<SkillDefinition> stableDefinitions = new ArrayList<>(definitions);
        stableDefinitions.sort(
                Comparator.comparingInt(SkillDefinition::displayOrder)
                        .thenComparing(skill -> skill.id().toString())
        );

        List<MutableNode> graphNodes = new ArrayList<>(stableDefinitions.size());
        Map<ResourceLocation, MutableNode> byId = new LinkedHashMap<>();
        for (int index = 0; index < stableDefinitions.size(); index++) {
            SkillDefinition definition = stableDefinitions.get(index);
            int column = tierColumns.getOrDefault(definition.requiredTierId(), 0);
            MutableNode node = new MutableNode(definition, column, index);
            graphNodes.add(node);
            byId.putIfAbsent(definition.id(), node);
        }

        UnionFind families = new UnionFind(graphNodes.size());
        Map<MutableNode, Map<MutableNode, Integer>> adjacency = new HashMap<>();
        for (MutableNode node : graphNodes) {
            adjacency.put(node, new LinkedHashMap<>());
        }

        // Prerequisites and replacements are directional when drawn, but both
        // imply undirected branch/family affinity for vertical layout.
        for (MutableNode node : graphNodes) {
            for (ResourceLocation prerequisiteId : node.definition.prerequisites()) {
                addRelation(
                        byId.get(prerequisiteId),
                        node,
                        PREREQUISITE_WEIGHT,
                        adjacency,
                        families
                );
            }
            node.definition.replacementTargetId().ifPresent(targetId -> addRelation(
                    byId.get(targetId),
                    node,
                    REPLACEMENT_WEIGHT,
                    adjacency,
                    families
            ));
        }

        // Connect every pair in a choice group. Besides making one family, this
        // preserves affinity for groups whose members span multiple tiers.
        Map<ResourceLocation, List<MutableNode>> choiceMembers = new LinkedHashMap<>();
        for (MutableNode node : graphNodes) {
            node.definition.choiceGroupId().ifPresent(groupId ->
                    choiceMembers.computeIfAbsent(groupId, ignored -> new ArrayList<>())
                            .add(node)
            );
        }
        for (List<MutableNode> members : choiceMembers.values()) {
            for (int left = 0; left < members.size(); left++) {
                for (int right = left + 1; right < members.size(); right++) {
                    addRelation(
                            members.get(left),
                            members.get(right),
                            CHOICE_AFFINITY_WEIGHT,
                            adjacency,
                            families
                    );
                }
            }
        }

        Map<Integer, Integer> familyStableOrders = new HashMap<>();
        Map<Integer, List<MutableNode>> familyMembers = new HashMap<>();
        for (MutableNode node : graphNodes) {
            node.family = families.find(node.stableIndex);
            familyStableOrders.merge(node.family, node.stableIndex, Math::min);
            familyMembers.computeIfAbsent(node.family, ignored -> new ArrayList<>())
                    .add(node);
        }

        int columnCount = Math.max(1, tiers.size());
        Map<Integer, List<Block>> blocksByColumn = buildBlocks(
                graphNodes,
                columnCount,
                familyStableOrders
        );
        assignTightSlots(blocksByColumn);

        // Alternating left/right barycentric sweeps are the standard layered-
        // graph crossing heuristic. Blocks, rather than individual nodes, move;
        // therefore a same-tier choice group can never be interleaved by an
        // unrelated skill.
        for (int pass = 0; pass < ORDERING_SWEEPS; pass++) {
            orderSweep(
                    blocksByColumn,
                    adjacency,
                    familyMembers,
                    true,
                    columnCount
            );
            orderSweep(
                    blocksByColumn,
                    adjacency,
                    familyMembers,
                    false,
                    columnCount
            );
        }

        List<Relation> relations = relations(graphNodes, adjacency);
        transposeForCrossings(blocksByColumn, relations, graphNodes, columnCount);

        // Ordering is now fixed. Relax block centers on a half-row grid so a
        // branch can sit between two rows or a multi-parent child can center on
        // its parents. Isotonic collision resolution retains block order and
        // guarantees the normal node gap within every tier.
        Map<Block, Integer> anchoredCenters = new HashMap<>();
        for (List<Block> blocks : blocksByColumn.values()) {
            for (Block block : blocks) {
                anchoredCenters.put(block, block.centerSlot);
            }
        }
        for (int pass = 0; pass < POSITIONING_SWEEPS; pass++) {
            boolean leftToRight = pass % 2 == 0;
            int start = leftToRight ? 0 : columnCount - 1;
            int end = leftToRight ? columnCount : -1;
            int step = leftToRight ? 1 : -1;
            for (int column = start; column != end; column += step) {
                relaxColumn(
                        blocksByColumn.get(column),
                        adjacency,
                        familyMembers,
                        anchoredCenters
                );
            }
        }

        int minimumSlot = Integer.MAX_VALUE;
        int maximumSlot = Integer.MIN_VALUE;
        for (MutableNode node : graphNodes) {
            minimumSlot = Math.min(minimumSlot, node.slot);
            maximumSlot = Math.max(maximumSlot, node.slot);
        }
        if (graphNodes.isEmpty()) {
            minimumSlot = 0;
            maximumSlot = 0;
        }

        List<Node> nodes = new ArrayList<>(graphNodes.size());
        for (MutableNode node : graphNodes) {
            int x = CONTENT_PADDING
                    + node.column * (NODE_WIDTH + COLUMN_GAP);
            int y = CONTENT_PADDING
                    + TIER_HEADER_HEIGHT
                    + (node.slot - minimumSlot) * HALF_ROW_STEP;
            nodes.add(new Node(node.definition, x, y));
        }
        nodes.sort(Comparator.comparingInt(node -> node.definition().displayOrder()));

        int contentWidth = CONTENT_PADDING * 2
                + columnCount * NODE_WIDTH
                + Math.max(0, columnCount - 1) * COLUMN_GAP;
        int contentHeight = CONTENT_PADDING * 2
                + TIER_HEADER_HEIGHT
                + (maximumSlot - minimumSlot) * HALF_ROW_STEP
                + NODE_HEIGHT;

        return new Layout(
                List.copyOf(nodes),
                List.copyOf(tiers),
                contentWidth,
                contentHeight
        );
    }

    private static Map<Integer, List<Block>> buildBlocks(
            List<MutableNode> nodes,
            int columnCount,
            Map<Integer, Integer> familyStableOrders
    ) {
        Map<Integer, List<MutableNode>> nodesByColumn = new LinkedHashMap<>();
        for (int column = 0; column < columnCount; column++) {
            nodesByColumn.put(column, new ArrayList<>());
        }
        for (MutableNode node : nodes) {
            nodesByColumn.computeIfAbsent(node.column, ignored -> new ArrayList<>())
                    .add(node);
        }

        Map<Integer, List<Block>> result = new LinkedHashMap<>();
        for (int column = 0; column < columnCount; column++) {
            List<MutableNode> columnNodes = nodesByColumn.getOrDefault(column, List.of());
            Map<ResourceLocation, List<MutableNode>> grouped = new LinkedHashMap<>();
            for (MutableNode node : columnNodes) {
                node.definition.choiceGroupId().ifPresent(groupId ->
                        grouped.computeIfAbsent(groupId, ignored -> new ArrayList<>())
                                .add(node)
                );
            }

            List<Block> blocks = new ArrayList<>();
            Set<ResourceLocation> emittedGroups = new LinkedHashSet<>();
            for (MutableNode node : columnNodes) {
                ResourceLocation groupId = node.definition.choiceGroup();
                if (groupId == null) {
                    blocks.add(new Block(List.of(node), familyStableOrders));
                } else if (emittedGroups.add(groupId)) {
                    blocks.add(new Block(grouped.get(groupId), familyStableOrders));
                }
            }

            blocks.sort(
                    Comparator.comparingDouble((Block block) -> block.hintRank)
                            .thenComparingInt(block -> block.familyStableOrder)
                            .thenComparingInt(block -> block.stableOrder)
            );
            result.put(column, blocks);
        }
        return result;
    }

    private static void addRelation(
            MutableNode left,
            MutableNode right,
            int weight,
            Map<MutableNode, Map<MutableNode, Integer>> adjacency,
            UnionFind families
    ) {
        if (left == null || right == null || left == right) {
            return;
        }
        adjacency.get(left).merge(right, weight, Integer::sum);
        adjacency.get(right).merge(left, weight, Integer::sum);
        families.union(left.stableIndex, right.stableIndex);
    }

    private static void orderSweep(
            Map<Integer, List<Block>> blocksByColumn,
            Map<MutableNode, Map<MutableNode, Integer>> adjacency,
            Map<Integer, List<MutableNode>> familyMembers,
            boolean leftToRight,
            int columnCount
    ) {
        int start = leftToRight ? 0 : columnCount - 1;
        int end = leftToRight ? columnCount : -1;
        int step = leftToRight ? 1 : -1;

        for (int column = start; column != end; column += step) {
            List<Block> blocks = blocksByColumn.get(column);
            if (blocks == null || blocks.size() < 2) {
                continue;
            }

            Map<Block, Double> scores = new HashMap<>();
            for (Block block : blocks) {
                double weightedPosition = 0.0D;
                double totalWeight = 0.0D;

                for (MutableNode member : block.members) {
                    for (Map.Entry<MutableNode, Integer> relation
                            : adjacency.get(member).entrySet()) {
                        MutableNode neighbor = relation.getKey();
                        boolean usable = leftToRight
                                ? neighbor.column < column
                                : neighbor.column > column;
                        if (!usable) {
                            continue;
                        }
                        double targetCenter = neighbor.slot - member.offsetFromBlock;
                        weightedPosition += targetCenter * relation.getValue();
                        totalWeight += relation.getValue();
                    }
                }

                // Direct edges dominate; the connected-component mean is a
                // gentle secondary pull keeping disconnected-looking portions
                // of the same branch in a common lane.
                double familyPosition = 0.0D;
                int familyCount = 0;
                Set<Integer> seenFamilies = new LinkedHashSet<>();
                for (MutableNode member : block.members) {
                    if (!seenFamilies.add(member.family)) {
                        continue;
                    }
                    for (MutableNode relative : familyMembers.get(member.family)) {
                        boolean usable = leftToRight
                                ? relative.column < column
                                : relative.column > column;
                        if (usable) {
                            familyPosition += relative.slot;
                            familyCount++;
                        }
                    }
                }
                if (familyCount > 0) {
                    weightedPosition += familyPosition / familyCount
                            * FAMILY_AFFINITY_WEIGHT;
                    totalWeight += FAMILY_AFFINITY_WEIGHT;
                }

                scores.put(
                        block,
                        totalWeight > 0.0D
                                ? weightedPosition / totalWeight
                                : (double) block.centerSlot
                );
            }

            blocks.sort(
                    Comparator.comparingDouble((Block block) -> scores.get(block))
                            .thenComparingDouble(block -> block.hintRank)
                            .thenComparingInt(block -> block.familyStableOrder)
                            .thenComparingInt(block -> block.stableOrder)
            );
            assignTightSlots(blocks);
        }
    }

    /**
     * Barycentric ordering is followed by deterministic adjacent transposition.
     * A swap is retained only when it lowers weighted edge length or removes a
     * crossing between the same two tier ranks. This catches local minima while
     * keeping display order as the stable tie-breaker.
     */
    private static void transposeForCrossings(
            Map<Integer, List<Block>> blocksByColumn,
            List<Relation> relations,
            List<MutableNode> nodes,
            int columnCount
    ) {
        int maximumPasses = Math.max(1, relations.size());
        for (int pass = 0; pass < maximumPasses; pass++) {
            boolean changed = false;
            for (int column = 0; column < columnCount; column++) {
                List<Block> blocks = blocksByColumn.get(column);
                if (blocks == null || blocks.size() < 2) {
                    continue;
                }
                for (int index = 0; index + 1 < blocks.size(); index++) {
                    double before = orderingObjective(relations, nodes);
                    java.util.Collections.swap(blocks, index, index + 1);
                    assignTightSlots(blocks);
                    double after = orderingObjective(relations, nodes);
                    if (after + 0.0001D < before) {
                        changed = true;
                    } else {
                        java.util.Collections.swap(blocks, index, index + 1);
                        assignTightSlots(blocks);
                    }
                }
            }
            if (!changed) {
                return;
            }
        }
    }

    private static double orderingObjective(
            List<Relation> relations,
            List<MutableNode> nodes
    ) {
        double objective = 0.0D;
        for (Relation relation : relations) {
            if (relation.left.column != relation.right.column) {
                objective += relation.weight
                        * Math.abs(relation.left.slot - relation.right.slot);
            }
        }

        for (int first = 0; first < relations.size(); first++) {
            Relation one = relations.get(first);
            if (!one.visible) {
                continue;
            }
            RankedEdge oneEdge = RankedEdge.of(one);
            if (oneEdge == null) {
                continue;
            }
            for (int second = first + 1; second < relations.size(); second++) {
                Relation two = relations.get(second);
                if (!two.visible) {
                    continue;
                }
                RankedEdge twoEdge = RankedEdge.of(two);
                if (twoEdge == null || one.sharesNode(two)) {
                    continue;
                }
                if (oneEdge.crosses(twoEdge)) {
                    objective += CROSSING_PENALTY * (one.weight + two.weight);
                }
            }
        }

        // Straight long edges should use an open lane through intermediate
        // tiers. Penalizing actual segment/rectangle penetration during the
        // transpose phase prevents a perfectly aligned edge from disappearing
        // behind an unrelated node merely because that arrangement is short.
        for (Relation relation : relations) {
            if (!relation.visible) {
                continue;
            }
            RankedEdge edge = RankedEdge.of(relation);
            if (edge == null || edge.rightColumn - edge.leftColumn < 2) {
                continue;
            }
            for (MutableNode node : nodes) {
                if (node == relation.left || node == relation.right) {
                    continue;
                }
                if (edge.penetrates(node)) {
                    objective += NODE_PENETRATION_PENALTY * relation.weight;
                }
            }
        }
        return objective;
    }

    private static List<Relation> relations(
            List<MutableNode> nodes,
            Map<MutableNode, Map<MutableNode, Integer>> adjacency
    ) {
        List<Relation> result = new ArrayList<>();
        for (MutableNode node : nodes) {
            for (Map.Entry<MutableNode, Integer> entry : adjacency.get(node).entrySet()) {
                if (node.stableIndex < entry.getKey().stableIndex) {
                    MutableNode other = entry.getKey();
                    result.add(new Relation(
                            node,
                            other,
                            entry.getValue(),
                            visiblyConnected(node, other)
                    ));
                }
            }
        }
        return result;
    }

    private static boolean visiblyConnected(MutableNode left, MutableNode right) {
        return left.definition.prerequisites().contains(right.definition.id())
                || right.definition.prerequisites().contains(left.definition.id())
                || left.definition.replacementTargetId()
                .filter(right.definition.id()::equals)
                .isPresent()
                || right.definition.replacementTargetId()
                .filter(left.definition.id()::equals)
                .isPresent();
    }

    private static void relaxColumn(
            List<Block> blocks,
            Map<MutableNode, Map<MutableNode, Integer>> adjacency,
            Map<Integer, List<MutableNode>> familyMembers,
            Map<Block, Integer> anchoredCenters
    ) {
        if (blocks == null || blocks.isEmpty()) {
            return;
        }

        double[] desiredCenters = new double[blocks.size()];
        for (int index = 0; index < blocks.size(); index++) {
            Block block = blocks.get(index);
            double weightedPosition = anchoredCenters.get(block) * POSITION_ANCHOR_WEIGHT;
            double totalWeight = POSITION_ANCHOR_WEIGHT;

            for (MutableNode member : block.members) {
                for (Map.Entry<MutableNode, Integer> relation
                        : adjacency.get(member).entrySet()) {
                    MutableNode neighbor = relation.getKey();
                    if (neighbor.column == member.column) {
                        continue;
                    }
                    double targetCenter = neighbor.slot - member.offsetFromBlock;
                    weightedPosition += targetCenter * relation.getValue();
                    totalWeight += relation.getValue();
                }
            }

            Set<Integer> seenFamilies = new LinkedHashSet<>();
            for (MutableNode member : block.members) {
                if (!seenFamilies.add(member.family)) {
                    continue;
                }
                double familyPosition = 0.0D;
                int familyCount = 0;
                for (MutableNode relative : familyMembers.get(member.family)) {
                    if (relative.column != member.column) {
                        familyPosition += relative.slot;
                        familyCount++;
                    }
                }
                if (familyCount > 0) {
                    weightedPosition += familyPosition / familyCount
                            * FAMILY_AFFINITY_WEIGHT;
                    totalWeight += FAMILY_AFFINITY_WEIGHT;
                }
            }
            desiredCenters[index] = weightedPosition / totalWeight;
        }

        int[] centers = collisionFreeCenters(blocks, desiredCenters);
        for (int index = 0; index < blocks.size(); index++) {
            Block block = blocks.get(index);
            block.centerSlot = centers[index];
            block.updateMemberSlots();
        }
    }

    /**
     * Pool-adjacent-violators solves the ordered center placement after each
     * block's required separation is subtracted. Rounding the resulting pools
     * to integer half-row units preserves order and cannot create a collision.
     */
    private static int[] collisionFreeCenters(
            List<Block> blocks,
            double[] desiredCenters
    ) {
        int count = blocks.size();
        int[] separationPrefix = new int[count];
        for (int index = 1; index < count; index++) {
            separationPrefix[index] = separationPrefix[index - 1]
                    + blocks.get(index - 1).extentUnits()
                    + blocks.get(index).extentUnits()
                    + MIN_NODE_SEPARATION_UNITS;
        }

        List<Pool> pools = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            pools.add(new Pool(
                    index,
                    index,
                    desiredCenters[index] - separationPrefix[index],
                    1
            ));
            while (pools.size() >= 2) {
                Pool right = pools.get(pools.size() - 1);
                Pool left = pools.get(pools.size() - 2);
                if (left.mean() <= right.mean()) {
                    break;
                }
                pools.remove(pools.size() - 1);
                pools.remove(pools.size() - 1);
                pools.add(left.merge(right));
            }
        }

        int[] centers = new int[count];
        int previousAdjusted = Integer.MIN_VALUE;
        for (Pool pool : pools) {
            int adjusted = (int) Math.round(pool.mean());
            adjusted = Math.max(adjusted, previousAdjusted);
            for (int index = pool.start; index <= pool.end; index++) {
                centers[index] = adjusted + separationPrefix[index];
            }
            previousAdjusted = adjusted;
        }
        return centers;
    }

    private static void assignTightSlots(Map<Integer, List<Block>> blocksByColumn) {
        for (List<Block> blocks : blocksByColumn.values()) {
            assignTightSlots(blocks);
        }
    }

    private static void assignTightSlots(List<Block> blocks) {
        int nodeCount = blocks.stream().mapToInt(block -> block.members.size()).sum();
        int cursor = -(nodeCount - 1);
        for (Block block : blocks) {
            block.centerSlot = cursor + block.extentUnits();
            block.updateMemberSlots();
            cursor += block.members.size() * MIN_NODE_SEPARATION_UNITS;
        }
    }

    private static int hintRank(SkillLayoutHint hint) {
        return switch (hint) {
            case UPPER -> 0;
            case AUTO, CENTER -> 1;
            case LOWER -> 2;
        };
    }

    private static final class MutableNode {
        private final SkillDefinition definition;
        private final int column;
        private final int stableIndex;
        private int family;
        private int offsetFromBlock;
        private int slot;

        private MutableNode(
                SkillDefinition definition,
                int column,
                int stableIndex
        ) {
            this.definition = definition;
            this.column = column;
            this.stableIndex = stableIndex;
        }
    }

    private static final class Block {
        private final List<MutableNode> members;
        private final int stableOrder;
        private final int familyStableOrder;
        private final double hintRank;
        private int centerSlot;

        private Block(
                List<MutableNode> sourceMembers,
                Map<Integer, Integer> familyStableOrders
        ) {
            members = new ArrayList<>(sourceMembers);
            members.sort(
                    Comparator.comparingInt(
                                    (MutableNode node) -> hintRank(node.definition.layoutHint())
                            )
                            .thenComparingInt(node -> node.stableIndex)
            );
            stableOrder = members.stream()
                    .mapToInt(node -> node.stableIndex)
                    .min()
                    .orElse(Integer.MAX_VALUE);
            familyStableOrder = members.stream()
                    .mapToInt(node -> familyStableOrders.get(node.family))
                    .min()
                    .orElse(Integer.MAX_VALUE);
            hintRank = members.stream()
                    .mapToInt(node -> hintRank(node.definition.layoutHint()))
                    .average()
                    .orElse(1.0D);

            for (int index = 0; index < members.size(); index++) {
                members.get(index).offsetFromBlock =
                        index * MIN_NODE_SEPARATION_UNITS - extentUnits();
            }
        }

        private int extentUnits() {
            return members.size() - 1;
        }

        private void updateMemberSlots() {
            for (MutableNode member : members) {
                member.slot = centerSlot + member.offsetFromBlock;
            }
        }
    }

    private record Relation(
            MutableNode left,
            MutableNode right,
            int weight,
            boolean visible
    ) {
        private boolean sharesNode(Relation other) {
            return left == other.left
                    || left == other.right
                    || right == other.left
                    || right == other.right;
        }
    }

    private record RankedEdge(
            int leftColumn,
            int rightColumn,
            MutableNode leftNode,
            MutableNode rightNode
    ) {
        private static RankedEdge of(Relation relation) {
            if (relation.left.column == relation.right.column) {
                return null;
            }
            return relation.left.column < relation.right.column
                    ? new RankedEdge(
                            relation.left.column,
                            relation.right.column,
                            relation.left,
                            relation.right
                    )
                    : new RankedEdge(
                            relation.right.column,
                            relation.left.column,
                            relation.right,
                            relation.left
                    );
        }

        /**
         * Tests the shared horizontal interval of two straight edges. Unlike a
         * same-rank-pair inversion count, this also catches a long prerequisite
         * edge crossing an edge that terminates in an intermediate tier.
         */
        private boolean crosses(RankedEdge other) {
            int overlapLeft = Math.max(leftColumn, other.leftColumn);
            int overlapRight = Math.min(rightColumn, other.rightColumn);
            if (overlapLeft >= overlapRight) {
                return false;
            }

            double leftDifference = yAt(overlapLeft) - other.yAt(overlapLeft);
            double rightDifference = yAt(overlapRight) - other.yAt(overlapRight);
            return leftDifference * rightDifference < -0.0001D;
        }

        private double yAt(int column) {
            double progress = (double) (column - leftColumn)
                    / (rightColumn - leftColumn);
            return leftNode.slot
                    + (rightNode.slot - leftNode.slot) * progress;
        }

        private boolean penetrates(MutableNode node) {
            if (node.column <= leftColumn || node.column >= rightColumn) {
                return false;
            }

            int columnPitch = NODE_WIDTH + COLUMN_GAP;
            double edgeLeft = leftColumn * columnPitch + NODE_WIDTH;
            double edgeRight = rightColumn * columnPitch;
            double rectangleLeft = node.column * columnPitch;
            double rectangleRight = rectangleLeft + NODE_WIDTH;
            double overlapLeft = Math.max(edgeLeft, rectangleLeft);
            double overlapRight = Math.min(edgeRight, rectangleRight);
            if (overlapLeft >= overlapRight) {
                return false;
            }

            double startY = pixelYAt(overlapLeft, edgeLeft, edgeRight);
            double endY = pixelYAt(overlapRight, edgeLeft, edgeRight);
            double segmentTop = Math.min(startY, endY);
            double segmentBottom = Math.max(startY, endY);
            double nodeTop = node.slot * HALF_ROW_STEP;
            double nodeBottom = nodeTop + NODE_HEIGHT;
            return segmentBottom > nodeTop + 0.0001D
                    && segmentTop < nodeBottom - 0.0001D;
        }

        private double pixelYAt(
                double x,
                double edgeLeft,
                double edgeRight
        ) {
            double progress = (x - edgeLeft) / (edgeRight - edgeLeft);
            double leftY = leftNode.slot * HALF_ROW_STEP + NODE_HEIGHT / 2.0D;
            double rightY = rightNode.slot * HALF_ROW_STEP + NODE_HEIGHT / 2.0D;
            return leftY + (rightY - leftY) * progress;
        }
    }

    private record Pool(int start, int end, double valueSum, int weight) {
        private double mean() {
            return valueSum / weight;
        }

        private Pool merge(Pool other) {
            return new Pool(
                    start,
                    other.end,
                    valueSum + other.valueSum,
                    weight + other.weight
            );
        }
    }

    private static final class UnionFind {
        private final int[] parents;

        private UnionFind(int size) {
            parents = new int[size];
            for (int index = 0; index < size; index++) {
                parents[index] = index;
            }
        }

        private int find(int index) {
            int parent = parents[index];
            if (parent != index) {
                parents[index] = find(parent);
            }
            return parents[index];
        }

        private void union(int left, int right) {
            int leftRoot = find(left);
            int rightRoot = find(right);
            if (leftRoot == rightRoot) {
                return;
            }
            // Stable root selection prevents input-map iteration from affecting
            // family identity or any subsequent ordering tie-break.
            if (leftRoot < rightRoot) {
                parents[rightRoot] = leftRoot;
            } else {
                parents[leftRoot] = rightRoot;
            }
        }
    }

    public record Layout(
            List<Node> nodes,
            List<AscendanceTierDefinition> tiers,
            int contentWidth,
            int contentHeight
    ) {
        public Node node(ResourceLocation id) {
            for (Node node : nodes) {
                if (node.definition().id().equals(id)) {
                    return node;
                }
            }
            return null;
        }
    }

    public record Node(
            SkillDefinition definition,
            int x,
            int y
    ) {
        public int centerX() {
            return x + NODE_WIDTH / 2;
        }

        public int centerY() {
            return y + NODE_HEIGHT / 2;
        }
    }
}
