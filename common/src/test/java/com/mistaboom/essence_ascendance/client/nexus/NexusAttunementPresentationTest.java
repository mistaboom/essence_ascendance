package com.mistaboom.essence_ascendance.client.nexus;

/** Responsive geometry and asynchronous handoff invariants, independent of a live OpenGL context. */
public final class NexusAttunementPresentationTest {
    private static int checks;
    public static void main(String[] args) {
        for (int width : new int[] {280, 360, 480, 720}) {
            for (int height : new int[] {120, 180, 280, 400}) {
                for (int count : new int[] {1, 2, 3, 6, 8, 12}) {
                    var layout = NexusConstellationLayout.create(12, 30, width, height, count);
                    check(layout.equals(NexusConstellationLayout.create(12, 30, width, height, count)), "Deterministic layout");
                    check(layout.nodes().size() == count, "Every registered category receives one node");
                    for (var node : layout.nodes()) {
                        check(node.x() - node.radius() >= 12 && node.x() + node.radius() < 12 + width,
                                "Crystal inside horizontal viewport");
                        check(node.y() - node.radius() >= 30 && node.y() + node.radius() < 30 + height,
                                "Crystal inside vertical viewport");
                        check(node.contains(node.x(), node.y()), "Crystal hit region includes center");
                        check(!node.contains(node.x() + node.radius() + 6, node.y()), "Hit region excludes unrelated space");
                    }
                    if (count == 6) {
                        check(layout.medallionRadius() >= 17, "Narrow supported layout retains readable central medallion");
                        for (var node : layout.nodes()) {
                            int labelY = node.y() + (Math.sin(node.angle()) < -0.1 ? -node.radius() - 27 : node.radius() + 13);
                            check(labelY >= 30 && labelY + 19 <= 30 + height, "Two-line category caption remains visible");
                            check(Math.hypot(node.x() - layout.centerX(), node.y() - layout.centerY())
                                            > node.radius() + layout.medallionRadius(), "Category and medallion do not overlap");
                        }
                    }
                }
            }
        }
        var handoff = new NexusAscensionHandoff();
        handoff.begin("dormant");
        check(!handoff.ready(true, 99, "awakened"), "Sending request never closes the Nexus");
        handoff.acknowledge(false, false, 5);
        check(!handoff.ready(true, 99, "awakened"), "Rejected or stale request leaves Nexus open");
        handoff.acknowledge(true, false, 5);
        check(!handoff.ready(true, 99, "awakened"), "Ordinary Bonus/skill commit does not animate Ascension");
        handoff.acknowledge(true, true, 8);
        check(!handoff.ready(false, 8, "awakened"), "Unready snapshot cannot close");
        check(!handoff.ready(true, 7, "awakened"), "Wait for authoritative revision");
        check(!handoff.ready(true, 8, "dormant"), "Wait for authoritative tier transition");
        check(handoff.ready(true, 8, "awakened"), "Confirmed and synchronized Ascension closes and animates");
        handoff.begin("awakened");
        check(!handoff.ready(true, 9, "resonant"), "Previous acknowledgement cannot authorize next chapter");
        System.out.println("NexusAttunementPresentationTest: " + checks + " checks PASS");
    }
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
