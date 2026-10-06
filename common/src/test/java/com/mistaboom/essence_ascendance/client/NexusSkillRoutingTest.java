package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.client.nexus.NexusSkillTreeLayout;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import com.mistaboom.essence_ascendance.skill.*;
import com.mistaboom.essence_ascendance.skill.balance.*;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Exercise the screen's actual geometry without creating a client/OpenGL window. */
public final class NexusSkillRoutingTest {
    public static void run() {
        try {
            var parent = ResourceLocation.parse("test:parent");
            var child = ResourceLocation.parse("test:child");
            var connection = record("SkillConnection", parent, child);
            var childBox = record("Rect", 278, 62, 386, 94);
            var obstacle = record("SkillRouteObstacle", childBox, Set.of(child), false);
            check(!(boolean) call("skillRouteSegmentIsClear", point(398, 78), point(278, 78), List.of(obstacle), connection, 6),
                    "A route approached the opposite side and crossed its destination box");
            check((boolean) call("skillRouteSegmentIsClear", point(266, 78), point(278, 78), List.of(obstacle), connection, 6),
                    "An outward-facing endpoint stub was blocked");
            // Existing saved tiers remain supported, including same-column prerequisite edges.
            Map<ResourceLocation, ResourceLocation> sameTier = new TreeMap<>();
            SkillRegistry.values().forEach(s -> sameTier.put(s.id(), AscendanceTiers.DORMANT.id()));
            for (var tiers : List.of(sameTier, SkillProgressionGraph.close(SkillRegistry.values(), sameTier))) {
                SkillBalanceRuntime.withRequiredTiers(tiers, () -> {
                    try { for (var essence : EssenceTypes.ORDERED) routes(NexusSkillTreeLayout.build(SkillRegistry.values(essence.id()))); }
                    catch (ReflectiveOperationException e) { throw new AssertionError(e); }
                    return null;
                });
            }
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private static void routes(NexusSkillTreeLayout.Layout layout) throws ReflectiveOperationException {
        Map<ResourceLocation, Object> bounds = new LinkedHashMap<>();
        List<Object> obstacles = new ArrayList<>();
        List<Object> occupied = new ArrayList<>();
        for (var node : layout.nodes()) {
            var box = record("Rect", node.x(), node.y(), node.x() + NexusSkillTreeLayout.NODE_WIDTH, node.y() + NexusSkillTreeLayout.NODE_HEIGHT);
            bounds.put(node.definition().id(), box);
            obstacles.add(record("SkillRouteObstacle", box, Set.of(node.definition().id()), false));
        }
        var viewport = new com.mistaboom.essence_ascendance.client.ui.UiBounds(0, 0, layout.contentWidth(), layout.contentHeight());
        var scroll = new com.mistaboom.essence_ascendance.client.ui.fullscreen.FullscreenViewport();
        scroll.configure(viewport, layout.contentWidth(), layout.contentHeight());
        var definitions = layout.nodes().stream().map(NexusSkillTreeLayout.Node::definition).toList();
        for (var frame : (List<?>) call("skillChoiceFrames", definitions, layout, viewport, scroll))
            obstacles.add(record("SkillRouteObstacle", accessor(frame, "bounds"), accessor(frame, "memberIds"), true));
        for (var node : layout.nodes()) for (var parent : node.definition().prerequisiteRanks(node.definition().maximumRank()).keySet()) {
            var child = node.definition().id();
            var connection = record("SkillConnection", parent, child);
            var endpoints = call("centeredConnectionEndpoints", bounds.get(parent), bounds.get(child));
            var start = accessor(endpoints, "start"); var end = accessor(endpoints, "end");
            var route = (List<?>) call("routeSkillOrthogonally", start, end, connection, bounds, obstacles, occupied, Set.of());
            check(route.size() >= 2 && route.getFirst().equals(start) && route.getLast().equals(end), "Missing prerequisite line: " + parent + " -> " + child);
            check((boolean) call("skillRouteIsClear", route, obstacles, connection, 6), "Prerequisite line crosses a box: " + child);
            for (int i = 1; i < route.size(); i++) occupied.add(record("SkillRouteSegment", route.get(i - 1), route.get(i)));
        }
    }
    private static Object point(int x, int y) throws ReflectiveOperationException { return record("ConnectionPoint", x, y); }
    private static Object record(String name, Object... args) throws ReflectiveOperationException {
        var type = Class.forName(AscendanceNexusScreen.class.getName() + "$" + name);
        var constructor = type.getDeclaredConstructors()[0]; constructor.setAccessible(true);
        return constructor.newInstance(args);
    }
    private static Object accessor(Object record, String name) throws ReflectiveOperationException {
        var method = record.getClass().getDeclaredMethod(name); method.setAccessible(true); return method.invoke(record);
    }
    private static Object call(String name, Object... args) throws ReflectiveOperationException {
        var method = Arrays.stream(AscendanceNexusScreen.class.getDeclaredMethods()).filter(m -> m.getName().equals(name)).findFirst().orElseThrow();
        method.setAccessible(true); return method.invoke(null, args);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
