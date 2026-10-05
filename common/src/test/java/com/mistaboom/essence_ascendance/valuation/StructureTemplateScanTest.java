package com.mistaboom.essence_ascendance.valuation;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Exact historical scan coverage with a deterministic bound on adversarial scanning work. */
public final class StructureTemplateScanTest {
    private static final Pattern ORIGINAL = Pattern.compile(
            "([a-z0-9_.-]+):((?:chests|containers|archaeology)/[a-z0-9_./-]+)");
    private static int assertions;

    public static void main(String[] args) throws Exception {
        String template = representativeTemplate();
        sameMatches(template, "binary template with palettes, repeated chests and nested containers");
        check(matches(ProceduralStructureIndex.lootTableScanner(template)).stream()
                        .map(match -> match.namespace() + ":" + match.path()).toList().equals(List.of(
                                "minecraft:chests/simple_dungeon", "fixture.mod-1:containers/nested/room",
                                "minecraft:chests/simple_dungeon", "fixture:archaeology/desert_well")),
                "all namespaces, repeated occurrences and container families remain in source order");

        for (String text : List.of("", "minecraft:chests/a", "fixture:containers/a/b.c-d_e",
                "0.-_:archaeology/0.-_/", "x:chests/a:y:containers/b",
                "x:chests/a:y:containers/b:z:archaeology/c", "x:chests/a/y:chests/z",
                "x:chests/a//./b", "x:chests/", "x:chest/a", ":chests/a", "X:chests/a",
                "Uppercase_x:chests/a", "x:chests/UPPER", "x:chests/aUPPERy:containers/b",
                "x:chests/a\\y:containers/b", "x:chests/a\u0000y:containers/b",
                "\u00ffx:chests/a\ud800y:containers/b\udfffz:archaeology/c",
                ":chests/a", ":containers/a", ":archaeology/a", ":chests/:containers/:archaeology/",
                ":chests/mod:containers/a", "a:chests/b:containers/c", "a:chests/b:archaeology/c",
                "a:chests/b/c:containers/d:e:archaeology/f", "a:chests/a::containers/b:archaeology/c",
                "a:containers/b\u0000:chests/c:archaeology/d", "a:archaeology/b\u0000c:containers/d",
                "a:chests/:b:containers/c", "a:chests/a:chests/b:chests/c")) {
            sameMatches(text, "namespace and nonoverlapping-match boundary");
        }

        for (int length : new int[]{1, 8, 64, 1024, 4096}) {
            String run = "a0_.-".repeat((length + 4) / 5).substring(0, length);
            sameMatches(run, "namespace run without a colon, length " + length);
            sameMatches(run + ":wrong/path", "namespace run before unrelated path, length " + length);
            sameMatches(run + ":chests/room", "complete long namespace, length " + length);
            sameMatches(run + "\u0000real:containers/room", "valid match after failed run, length " + length);
            sameMatches("start:chests/" + run + ":archaeology/end", "adjacent matches, length " + length);
        }

        // Seeded Latin-1 bytes model the string view of arbitrary decompressed NBT. Injected
        // identifiers deliberately abut binary data and other possible namespace characters.
        Random random = new Random(0x51A7C7L);
        String[] references = {"minecraft:chests/a", "fixture:containers/b", "fixture:archaeology/c"};
        for (int sample = 0; sample < 128; sample++) {
            byte[] bytes = new byte[random.nextInt(2049)];
            random.nextBytes(bytes);
            StringBuilder content = new StringBuilder(new String(bytes, StandardCharsets.ISO_8859_1));
            for (String reference : references) content.insert(random.nextInt(content.length() + 1), reference);
            sameMatches(content, "seeded binary template " + sample);
        }

        // Count matcher reads instead of imposing a machine-dependent wall-clock deadline.
        for (int length : new int[]{1024, 4096, 16384, 65536}) {
            CountingSequence content = new CountingSequence("a".repeat(length) + ":unrelated/"
                    + "b".repeat(length) + "\u0000fixture:chests/final");
            List<Match> found = matches(ProceduralStructureIndex.lootTableScanner(content));
            check(found.size() == 1 && found.getFirst().namespace().equals("fixture")
                            && found.getFirst().path().equals("chests/final"),
                    "long nonmatching runs retain the trailing reference");
            check(content.reads <= 16L * content.length(),
                    "scan work stays linear for long namespace runs: " + content.reads + " reads");
        }
        for (int length : new int[]{1024, 4096, 16384}) {
            CountingSequence content = new CountingSequence(":chests/".repeat(length)
                    + "\u0000" + "a".repeat(length) + ":containers/valid");
            List<Match> found = matches(ProceduralStructureIndex.lootTableScanner(content));
            check(found.size() == 1 && found.getFirst().namespace().equals("a".repeat(length))
                            && found.getFirst().path().equals("containers/valid"),
                    "namespaceless valid families cannot consume a later real match");
            check(content.reads <= 16L * content.length(), "dense candidate colons retain bounded scan work");
        }
        System.out.println("StructureTemplateScanTest: " + assertions
                + " exact match coverage and bounded scan-work checks PASS");
    }

    private static String representativeTemplate() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream nbt = new DataOutputStream(bytes)) {
            nbt.writeByte(10); nbt.writeUTF("");
            nbt.writeByte(9); nbt.writeUTF("palette"); nbt.writeByte(10); nbt.writeInt(2);
            for (String block : List.of("minecraft:chest", "fixture:decorative_stone")) {
                stringTag(nbt, "Name", block); nbt.writeByte(0);
            }
            nbt.writeByte(9); nbt.writeUTF("blocks"); nbt.writeByte(10); nbt.writeInt(4);
            for (String reference : List.of("minecraft:chests/simple_dungeon",
                    "fixture.mod-1:containers/nested/room", "minecraft:chests/simple_dungeon",
                    "fixture:archaeology/desert_well")) {
                nbt.writeByte(10); nbt.writeUTF("nbt");
                stringTag(nbt, "LootTable", reference); nbt.writeByte(0); nbt.writeByte(0);
            }
            nbt.writeByte(0);
        }
        return bytes.toString(StandardCharsets.ISO_8859_1);
    }

    private static void stringTag(DataOutputStream nbt, String name, String value) throws Exception {
        nbt.writeByte(8); nbt.writeUTF(name); nbt.writeUTF(value);
    }

    private static void sameMatches(CharSequence content, String label) {
        List<Match> expected = matches(ORIGINAL.matcher(content));
        check(expected.equals(matches(ProceduralStructureIndex.lootTableScanner(content.toString()))),
                "historical full matches, offsets, groups and order on the String path: " + label);
        check(expected.equals(matches(ProceduralStructureIndex.lootTableScanner(new CountingSequence(content.toString())))),
                "historical full matches, offsets, groups and order on the CharSequence path: " + label);
    }

    private static List<Match> matches(Matcher matcher) {
        List<Match> matches = new ArrayList<>();
        while (matcher.find()) matches.add(new Match(matcher.start(), matcher.end(), matcher.group(),
                matcher.group(1), matcher.group(2)));
        return matches;
    }

    private static List<Match> matches(ProceduralStructureIndex.LootTableScanner scanner) {
        List<Match> matches = new ArrayList<>();
        while (scanner.find()) matches.add(new Match(scanner.start(), scanner.end(), scanner.group(),
                scanner.group(1), scanner.group(2)));
        return matches;
    }

    private record Match(int start, int end, String full, String namespace, String path) {}

    private static final class CountingSequence implements CharSequence {
        private final String content;
        private long reads;
        private CountingSequence(String content) { this.content = content; }
        @Override public int length() { return content.length(); }
        @Override public char charAt(int index) { reads++; return content.charAt(index); }
        @Override public CharSequence subSequence(int start, int end) { return content.subSequence(start, end); }
        @Override public String toString() { return content; }
    }

    private static void check(boolean condition, String description) {
        assertions++;
        if (!condition) throw new AssertionError(description);
    }
}
