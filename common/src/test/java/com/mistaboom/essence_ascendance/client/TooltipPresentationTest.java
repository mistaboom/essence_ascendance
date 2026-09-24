package com.mistaboom.essence_ascendance.client;

import com.mistaboom.essence_ascendance.network.ItemEssenceTooltipPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;

import java.util.ArrayList;
import java.util.List;

/** Reusable presentation contracts and real Minecraft payload codec, without a running client. */
public final class TooltipPresentationTest {
    private static int assertions;

    public static void main(String[] args) {
        Thread.currentThread().setUncaughtExceptionHandler((thread, failure) -> failure.printStackTrace(
                new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.err))));
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        wrapping();
        scrolling();
        styles();
        codec();
        completedYieldSnapshots();
        AscendancePaletteTest.main(args);
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out)).println("TooltipPresentationTest: " + assertions
                + " token wrapping, bounded scroll, semantic styling and exact payload assertions passed");
    }

    private static void wrapping() {
        List<String> tokens = List.of("120 Off", "200 Def", "35 Vit", "40 Mob", "9 Gath", "10 Util");
        List<List<String>> wrapped = TooltipLayout.wrapTokens(tokens, text -> text.length() * 6, 48, 12, 18, 144);
        check(wrapped.size() > 1, "Six outputs should not force one wide line");
        check(wrapped.stream().flatMap(List::stream).toList().equals(tokens), "Wrapping changed/lost a value or its name");
        for (int i = 0; i < wrapped.size(); i++) {
            List<String> line = wrapped.get(i);
            int lineWidth = (i == 0 ? 48 : 12) + line.stream().mapToInt(text -> text.length() * 6).sum()
                    + (line.size() - 1) * 18;
            check(lineWidth <= 144, "Wrapped line exceeded the available pixel width");
        }
        check(TooltipLayout.wrapTokens(List.of("1 Off", "1 Def"), String::length, 4, 2, 3, 17).size() == 1,
                "Exactly fitting tokens wrapped early");
        check(TooltipLayout.wrapTokens(List.of("1 Off", "1 Def"), String::length, 4, 2, 3, 16).size() == 2,
                "Overflowing token did not wrap");
        check(TooltipLayout.wrapTokens(List.of(), String::length, 4, 2, 3, 144).isEmpty(), "Empty outputs added a line");
        check(TooltipLayout.compactWidth(80, 800) == 144, "Short base tooltip lost compact minimum");
        check(TooltipLayout.compactWidth(500, 800) == 220, "Long base tooltip defeated compact maximum");
        check(TooltipLayout.compactWidth(500, 200) == 168, "Layout ignored screen width");
        // A single token cannot be split: do not lose it even if an external translation is very long.
        check(TooltipLayout.wrapTokens(List.of("indivisible"), text -> 300, 30, 12, 10, 144)
                .equals(List.of(List.of("indivisible"))), "Oversized individual token was discarded");
    }

    private static void scrolling() {
        List<String> lines = List.of("Title", "Description", "Cost", "State", "Prerequisite", "Requirement", "Action");
        var first = TooltipLayout.viewport(lines, 5, 0);
        check(first.lines().equals(lines.subList(0, 4)), "Tooltip title/content ordering changed");
        check(first.maximumOffset() == 3, "Incorrect scroll bounds");
        var last = TooltipLayout.viewport(lines, 5, 999);
        check(last.offset() == 3 && last.lines().getLast().equals("Action"), "Last detail is not reachable");
        check(last.lines().getFirst().equals("Title"), "Scrolling lost pinned title");
        List<String> reached = new ArrayList<>();
        for (int scroll = 0; scroll <= first.maximumOffset(); scroll++)
            reached.addAll(TooltipLayout.viewport(lines, 5, scroll).lines());
        check(reached.containsAll(lines), "Tooltip viewport hid information permanently");
        check(TooltipLayout.viewport(lines, 10, 99).lines().equals(lines), "Fitting content got clipped");
        check(TooltipLayout.viewport(lines, 5, -1).offset() == 0, "Negative offset was not clamped");
    }

    private static void styles() {
        SemanticTooltip tooltip = new SemanticTooltip()
                .title(Component.literal("Any future skill"), 0xFFAA33)
                .description(Component.literal("A wrapped description"))
                .field(Component.literal("Rank: ").append(SemanticTooltip.value(1)))
                .section(Component.literal("Prerequisites"))
                .requirement(Component.literal("Owned prerequisite"), SemanticTooltip.State.MET)
                .requirement(Component.literal("Staged prerequisite"), SemanticTooltip.State.STAGED)
                .requirement(Component.literal("Missing requirement"), SemanticTooltip.State.MISSING)
                .requirement(Component.literal("Unavailable provider"), SemanticTooltip.State.UNAVAILABLE)
                .hint(Component.literal("Click to stage"));
        List<SemanticTooltip.Line> lines = tooltip.lines();
        check(lines.getFirst().content().getStyle().isBold(), "Title is not bold");
        check(lines.getFirst().content().getStyle().getColor().getValue() == 0xFFAA33, "Title lost Essence accent");
        check(lines.get(1).content().getStyle().getColor().getValue() == ChatFormatting.GRAY.getColor(),
                "Description style not shared");
        check(lines.get(2).content().getSiblings().getFirst().getStyle().getColor().getValue()
                == ChatFormatting.WHITE.getColor(), "Field styling overwrote value contrast");
        check(lines.get(3).content().getStyle().isBold(), "Section heading not distinct");
        for (int i = 0; i < SemanticTooltip.State.values().length; i++) {
            var line = lines.get(4 + i);
            check(line.indentation() == 1, "Requirement lacks continuation indentation");
            check(line.content().getStyle().getColor().getValue() == SemanticTooltip.State.values()[i].color().getColor(),
                    "Requirement status color mismatch");
        }
        check(lines.getLast().content().getStyle().getColor().getValue() == ChatFormatting.GRAY.getColor(),
                "Action hint must remain neutral");
        var paragraphs = new SemanticTooltip().gap().title(Component.literal("Skill"), 0xD65368)
                .gap().gap().detail(Component.literal("Effect paragraph")).gap().gap();
        check(paragraphs.lines().size() == 4 && paragraphs.lines().get(1).content().getString().isEmpty(),
                "Paragraph spacing has no leading or duplicate blank rows");
        check(paragraphs.lines().get(2).indentation() == 1, "Effect paragraphs retain continuation indentation");
    }

    private static void codec() {
        for (long units : new long[]{1L, 5_000_000L, 9_007_199_254_740_991L,
                9_007_199_254_740_993L, Long.MAX_VALUE}) {
            ItemEssenceTooltipPayload payload = new ItemEssenceTooltipPayload(42, 0, 1, List.of(
                    new ItemEssenceTooltipPayload.Entry("test:item", List.of(
                            new ItemEssenceTooltipPayload.Output("essence_ascendance:offense", units)))));
            RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
            try {
                ItemEssenceTooltipPayload.CODEC.encode(buffer, payload);
                ItemEssenceTooltipPayload result = ItemEssenceTooltipPayload.CODEC.decode(buffer);
                check(result.equals(payload), "Tooltip wire codec lost integer precision: " + units);
                check(buffer.readableBytes() == 0, "Tooltip codec did not consume the packet");
            } finally {
                buffer.release();
            }
        }
        for (long units : new long[]{0L, -1L, Long.MIN_VALUE}) {
            try {
                new ItemEssenceTooltipPayload.Output("essence_ascendance:offense", units);
                throw new AssertionError("Nonpositive output accepted");
            } catch (IllegalArgumentException expected) { assertions++; }
        }
        check(ItemEssenceTooltipPayload.TYPE.id().getPath().endsWith("_v2"),
                "Changed wire format reused old negotiation channel");
    }

    private static void completedYieldSnapshots() {
        ItemEssenceTooltipClientState.clearForTesting();
        check(!ItemEssenceTooltipClientState.yieldSnapshot().ready(),
                "A cleared connection exposed believable yield data");
        ItemEssenceTooltipClientState.acceptForTesting(payload(7, 1, 2, "minecraft:coal", 250_000L));
        check(!ItemEssenceTooltipClientState.yieldSnapshot().ready(),
                "A non-starting or incomplete chunk published a snapshot");
        ItemEssenceTooltipClientState.acceptForTesting(payload(7, 0, 2, "minecraft:diamond", 2_000_000L));
        check(!ItemEssenceTooltipClientState.yieldSnapshot().ready(),
                "Chunk zero published before the completed snapshot");
        ItemEssenceTooltipClientState.acceptForTesting(payload(7, 1, 2, "minecraft:coal", 250_000L));
        var first = ItemEssenceTooltipClientState.yieldSnapshot();
        check(first.ready() && first.generation() == 7 && first.rows().size() == 2,
                "Completed chunks were not atomically installed");
        check(first.rows().stream().filter(row -> row.itemId().toString().equals("minecraft:coal"))
                        .findFirst().orElseThrow().outputs().getFirst().microUnits() == 250_000L,
                "Fractional positive yield lost exact micro-units");
        ItemEssenceTooltipClientState.acceptForTesting(payload(6, 0, 1, "minecraft:stick", 1_000_000L));
        check(ItemEssenceTooltipClientState.yieldSnapshot() == first,
                "An older generation replaced the installed snapshot");
        ItemEssenceTooltipClientState.acceptForTesting(payload(8, 0, 2, "minecraft:stick", 1_000_000L));
        check(ItemEssenceTooltipClientState.yieldSnapshot() == first,
                "An incomplete replacement erased completed data");
        ItemEssenceTooltipClientState.acceptForTesting(payload(8, 1, 2, "minecraft:apple", 125_000L));
        var replacement = ItemEssenceTooltipClientState.yieldSnapshot();
        check(replacement.ready() && replacement.generation() == 8 && replacement.rows().size() == 2
                        && replacement != first,
                "A completed newer generation did not replace the snapshot once");
        ItemEssenceTooltipClientState.clearForTesting();
        check(!ItemEssenceTooltipClientState.yieldSnapshot().ready()
                        && ItemEssenceTooltipClientState.yieldSnapshot().rows().isEmpty(),
                "Disconnect retained rows from the previous connection");
    }

    private static ItemEssenceTooltipPayload payload(long generation, int index, int count,
                                                      String item, long microUnits) {
        return new ItemEssenceTooltipPayload(generation, index, count, List.of(
                new ItemEssenceTooltipPayload.Entry(item, List.of(
                        new ItemEssenceTooltipPayload.Output("essence_ascendance:offense", microUnits)))));
    }

    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }
}
