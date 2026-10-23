package com.mistaboom.essence_ascendance.visual;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

/** Pixel fixtures and the pre-extraction Essentium output fingerprint. */
public final class TexturePixelsTest {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        check(TexturePixels.abgrToArgb(0x7F123456) == 0x7F563412, "NativeImage ABGR to ARGB");
        check(TexturePixels.argbToAbgr(0x7F563412) == 0x7F123456, "ARGB to NativeImage ABGR");
        for (int color = 0; color <= 255; color++) {
            int rgb = color * 0x010101;
            check(TexturePixels.transitionTint(new int[]{0xFF000000 | rgb}) == rgb,
                    "Direct host tint with no white blending for " + color);
        }
        check(TexturePixels.averageRgb(new int[]{0xFFFF0000, 0x800000FF, 0x0000FF00}) == 0xAA0055,
                "Average must weight partial alpha and ignore invisible RGB");
        check(TexturePixels.transitionTint(new int[]{0xFF000000, 0xFF010101}) == 0x010101,
                "Average must round fractional channels once without adding white");
        check(TexturePixels.transitionTint(new int[]{0xFFFF0000, 0x800000FF, 0x0000FF00}) == 0xAA0055,
                "Transition tint must use exact partial-alpha weights and exclude invisible RGB");
        check(TexturePixels.composeLatentOre(new int[]{0xFF404040}, 1, 1,
                new int[]{0xFFFFFFFF}, 1, 1, new int[]{0}, 1, 1)[0] == 0xFF505050,
                "Every host must receive the same small transition luminance lift");
        check(TexturePixels.composeLatentOre(new int[]{0xFF404040}, 1, 1,
                new int[]{0xFFE0E0E0}, 1, 1, new int[]{0}, 1, 1)[0] == 0xFF505050,
                "A uniform one-pixel mask has no relative shading to add");
        check(TexturePixels.composeLatentOre(new int[]{0xFF404040}, 1, 1,
                new int[]{0x80FFFFFF}, 1, 1, new int[]{0}, 1, 1)[0] == 0xFF484848,
                "Host-relative colorization must preserve mask alpha before composition");
        int[] twoPixelMask = {0xFFFFFFFF, 0xFF7F7F7F};
        int[] noOre = {0, 0};
        for (int hostRgb : new int[]{0xFF7E7E7E, 0xFF505052, 0xFF622626, 0xFFDBDF9E}) {
            int[] hostPair = {hostRgb, hostRgb};
            int[] transitionPair = TexturePixels.composeLatentOre(
                    hostPair, 2, 1, twoPixelMask, 2, 1, noOre, 2, 1);
            int hostLuminance = testLuminance(hostRgb);
            int transitionAverage = (testLuminance(transitionPair[0]) + testLuminance(transitionPair[1]) + 1) / 2;
            check(Math.abs(transitionAverage - Math.min(255, hostLuminance + 16)) <= 1,
                    "Host-relative transition must use one consistent luminance lift");
            check(testLuminance(transitionPair[0]) > testLuminance(transitionPair[1]),
                    "Transition mask shading must remain visible for every host color");
        }
        int[] netherrack = TexturePixels.composeLatentOre(
                new int[]{0xFF622626, 0xFF622626}, 2, 1, twoPixelMask, 2, 1, noOre, 2, 1);
        check(((netherrack[0] >>> 16) & 0xFF) - ((netherrack[0] >>> 8) & 0xFF) > 40,
                "Netherrack-colored transitions must remain red instead of washing toward pink");
        int[] endStone = TexturePixels.composeLatentOre(
                new int[]{0xFFDBDF9E, 0xFFDBDF9E}, 2, 1, twoPixelMask, 2, 1, noOre, 2, 1);
        check((endStone[1] & 0xFF) < ((endStone[1] >>> 8) & 0xFF),
                "End-stone transitions must retain their warm host color");
        check(TexturePixels.tintArgb(0x80904020, 0x804020) == 0x80481004,
                "Tint preserves alpha and source shading");
        check(TexturePixels.overArgb(0x80FF0000, 0xFF0000FF) == 0xFF80007F,
                "Partial alpha source OVER an opaque host");
        check(TexturePixels.overArgb(0x00010203, 0xFF345678) == 0xFF345678,
                "Invisible overlay leaves host unchanged");
        check(TexturePixels.overArgb(0x80FF0000, 0x800000FF) == 0xC0AA0055,
                "Source OVER preserves partial background alpha");
        int[] host = {0xFF203040, 0xFF456789, 0xFFABCDEF, 0x00123456};
        int[] composed = TexturePixels.composeLatentOre(host, 2, 2,
                new int[]{0, 0, 0xFFFFFFFF, 0}, 2, 2,
                new int[]{0xFFA13E07, 0, 0xFF29F178, 0}, 2, 2);
        check(composed[0] == 0xFFA13E07, "Ore RGB must remain untinted");
        check(composed[1] == host[1] && composed[3] == host[3], "Outside overlays preserves exact host pixel");
        check(composed[2] == 0xFF29F178, "Ore must cover tinted transition");
        int[] solid = new int[16];
        Arrays.fill(solid, 0xFF304050);
        int[] scaled = TexturePixels.composeLatentOre(solid, 4, 4, new int[]{0}, 1, 1,
                new int[]{0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFFFFFFFF}, 2, 2);
        check(scaled[0] == 0xFFFF0000 && scaled[1] == 0xFFFF0000
                        && scaled[2] == 0xFF00FF00 && scaled[3] == 0xFF00FF00
                        && scaled[8] == 0xFF0000FF && scaled[15] == 0xFFFFFFFF,
                "Whole masks must scale by nearest neighbor without repeating or cropping");
        int[] reduced = TexturePixels.composeLatentOre(new int[]{0xFF304050}, 1, 1,
                new int[]{0}, 1, 1, new int[]{0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFFFFFFFF}, 2, 2);
        check(reduced[0] == 0xFFFF0000, "Downsampling must use the same deliberate nearest-neighbor policy");
        for (int alpha = 0; alpha <= 255; alpha++) {
            int pixel = TexturePixels.composeLatentOre(new int[]{0xFF304050}, 1, 1,
                    new int[]{alpha << 24 | 0x234567}, 1, 1,
                    new int[]{(255 - alpha) << 24 | 0xCB9876}, 1, 1)[0];
            check(pixel >>> 24 == 255, "Opaque hosts must never acquire pinholes");
        }
        boolean rejected = false;
        try { TexturePixels.transitionTint(new int[]{0x00123456}); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "Fully invisible hosts must be rejected");
        originalMasks();
        originalTransitionAppearance();
        faceMaskOrientation();
        essentiumRegression();
        System.out.println("TexturePixelsTest: " + assertions + " checks PASS");
    }

    private static void originalMasks() throws Exception {
        check(resourceHash("block/latent_ore/latent_ore_base.png")
                        .equals("3ead70d1f5509cad229caf80c86c5b63bc9a85e1b34a8e0e5dc012f8c24f9712"),
                "The original ore artwork must be packaged unchanged");
        check(resourceHash("block/latent_ore/latent_ore_transition.png")
                        .equals("1c8e3541a7cfd2325e76c27d2753e89229d66032c9a36b31ad7de0de2c5e8d76"),
                "The original transition artwork must be packaged unchanged");
        BufferedImage ore = image("block/latent_ore/latent_ore_base.png");
        BufferedImage transition = image("block/latent_ore/latent_ore_transition.png");
        int[] host = new int[32 * 32];
        Arrays.fill(host, 0xFF234567);
        int[] composed = TexturePixels.composeLatentOre(host, 32, 32,
                transition.getRGB(0, 0, transition.getWidth(), transition.getHeight(), null, 0, transition.getWidth()),
                transition.getWidth(), transition.getHeight(),
                ore.getRGB(0, 0, ore.getWidth(), ore.getHeight(), null, 0, ore.getWidth()), ore.getWidth(), ore.getHeight());
        for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) {
            int nativeOre = ore.getRGB(x * ore.getWidth() / 32, y * ore.getHeight() / 32);
            int border = transition.getRGB(x * transition.getWidth() / 32, y * transition.getHeight() / 32);
            int finalPixel = composed[y * 32 + x];
            check(finalPixel >>> 24 == 255, "Actual source masks over 32px host must stay opaque");
            if (nativeOre >>> 24 == 255) check(finalPixel == nativeOre, "Original visible artwork remains untinted");
            if (nativeOre >>> 24 == 0 && border >>> 24 == 0)
                check(finalPixel == host[0], "Actual artwork leaves exposed host intact");
        }
    }

    /** The authored transition retains its original host-relative tint for every baseline host. */
    private static void originalTransitionAppearance() throws IOException {
        BufferedImage ore = image("block/latent_ore/latent_ore_base.png");
        BufferedImage transition = image("block/latent_ore/latent_ore_transition.png");
        int width = ore.getWidth(), height = ore.getHeight();
        int[] orePixels = ore.getRGB(0, 0, width, height, null, 0, width);
        int[] transitionPixels = transition.getRGB(0, 0, width, height, null, 0, width);
        for (int hostRgb : new int[]{0xFF7E7E7E, 0xFF505052, 0xFF622626, 0xFFDBDF9E}) {
            int[] hostPixels = new int[width * height];
            Arrays.fill(hostPixels, hostRgb);
            int[] composed = TexturePixels.composeLatentOre(hostPixels, width, height,
                    transitionPixels, width, height, orePixels, width, height);
            int transitionLuminance = 0, transitionCount = 0;
            for (int i = 0; i < composed.length; i++) {
                if (orePixels[i] >>> 24 == 255) {
                    check(composed[i] == orePixels[i],
                            "The original transition must preserve the authored ore core for every host");
                } else if (transitionPixels[i] >>> 24 == 0) {
                    check(composed[i] == hostRgb,
                            "The original transition must preserve uncovered native host pixels");
                } else {
                    transitionLuminance += testLuminance(composed[i]);
                    transitionCount++;
                }
            }
            check(transitionCount == 95,
                    "The original authored transition ring must remain complete for host "
                            + Integer.toHexString(hostRgb));
            check(Math.abs((transitionLuminance + transitionCount / 2) / transitionCount
                            - Math.min(255, testLuminance(hostRgb) + 16)) <= 1,
                    "The original authored transition must retain its +16 host-relative luminance for host "
                            + Integer.toHexString(hostRgb));
        }
    }

    private static void faceMaskOrientation() {
        int[] host = new int[9];
        Arrays.fill(host, 0xFF123456);
        int[] transition = new int[9];
        int[] ore = new int[9];
        ore[0] = 0xFFEEDDCC;
        int[] nativeFace = TexturePixels.composeLatentOre(host, 3, 3, transition, 3, 3, ore, 3, 3);
        int[] uDown = TexturePixels.composeLatentOre(host, 3, 3, transition, 3, 3, ore, 3, 3, 1);
        int[] uUp = TexturePixels.composeLatentOre(host, 3, 3, transition, 3, 3, ore, 3, 3, -1);
        int[] vUp = TexturePixels.composeLatentOre(host, 3, 3, transition, 3, 3, ore, 3, 3, 2);
        check(nativeFace[0] == ore[0] && uDown[6] == ore[0] && uUp[2] == ore[0] && vUp[8] == ore[0],
                "World/item orientation variants rotate only the ore mask to the expected face corner");
        for (int i = 0; i < host.length; i++) {
            if (i != 6) check(uDown[i] == host[i], "Clockwise mask leaves uncovered host pixels native");
            if (i != 2) check(uUp[i] == host[i], "Counterclockwise mask leaves uncovered host pixels native");
            if (i != 8) check(vUp[i] == host[i], "Half-turn mask leaves uncovered host pixels native");
        }
        boolean rejected = false;
        try { TexturePixels.composeLatentOre(host, 3, 3, transition, 3, 3, ore, 3, 3, 3); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "Unsupported mask orientation is rejected");
    }

    private static String resourceHash(String path) throws Exception {
        try (var input = TexturePixelsTest.class.getResourceAsStream("/assets/essence_ascendance/textures/" + path)) {
            if (input == null) throw new IOException("Missing source resource " + path);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()));
        }
    }

    private static void essentiumRegression() throws Exception {
        BufferedImage base = mask("base");
        BufferedImage accent = mask("accent");
        Map<String, Integer> essences = Map.of("offense", CanonicalPaletteValues.OFFENSE,
                "defense", CanonicalPaletteValues.DEFENSE, "vitality", CanonicalPaletteValues.VITALITY,
                "mobility", CanonicalPaletteValues.MOBILITY, "gathering", CanonicalPaletteValues.GATHERING,
                "utility", CanonicalPaletteValues.UTILITY);
        Map<String, Integer> tiers = Map.of("dormant", CanonicalPaletteValues.DORMANT_PRIMARY,
                "awakened", CanonicalPaletteValues.AWAKENED_PRIMARY, "resonant", CanonicalPaletteValues.RESONANT_PRIMARY,
                "ascendant", CanonicalPaletteValues.ASCENDANT_PRIMARY, "transcendent", CanonicalPaletteValues.TRANSCENDENT_PRIMARY);
        Map<String, int[]> combinations = new TreeMap<>();
        combinations.put("latent", new int[]{0xFFFFFF, 0xFFFFFF});
        essences.forEach((essence, color) -> tiers.forEach((tier, tierColor) ->
                combinations.put(essence + "_" + tier, new int[]{tierColor, color})));
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (int[] colors : combinations.values()) {
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                int pixel = TexturePixels.overArgb(TexturePixels.tintArgb(accent.getRGB(x, y), colors[1]),
                        TexturePixels.tintArgb(base.getRGB(x, y), colors[0]));
                check(pixel >>> 24 == 255, "Essentium must remain opaque");
                digest.update((byte) (pixel >>> 24));
                digest.update((byte) (pixel >>> 16));
                digest.update((byte) (pixel >>> 8));
                digest.update((byte) pixel);
            }
        }
        // Captured from all 31 PNG outputs of the original generator before extraction.
        check(HexFormat.of().formatHex(digest.digest())
                        .equals("74740ca8ae196725cb9b1841d624395e77d5437a84ac538d46d8d23ecdad5a78"),
                "Extraction must leave every existing Essentium pixel unchanged");
    }

    private static BufferedImage mask(String name) throws IOException {
        return image("item/essence_block/" + name + ".png");
    }

    private static BufferedImage image(String path) throws IOException {
        try (var input = TexturePixelsTest.class.getResourceAsStream(
                "/assets/essence_ascendance/textures/" + path)) {
            if (input == null) throw new IOException("Missing source image " + path);
            return ImageIO.read(input);
        }
    }

    private static int testLuminance(int argb) {
        return (54 * ((argb >>> 16) & 0xFF) + 183 * ((argb >>> 8) & 0xFF)
                + 19 * (argb & 0xFF) + 128) >>> 8;
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
