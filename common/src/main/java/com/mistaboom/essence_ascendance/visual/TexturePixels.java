package com.mistaboom.essence_ascendance.visual;

/**
 * Shared straight-alpha ARGB pixel operations. Image decoding, resource access and
 * GPU ownership belong to the callers; this core is also used by the build tool.
 */
public final class TexturePixels {
    private static final int TRANSITION_LUMINANCE_LIFT = 16;

    private TexturePixels() {
    }

    /** Preserves source alpha and shading, using the original Essentium rounding. */
    public static int tintArgb(int argb, int color) {
        int alpha = (argb >>> 24) & 0xFF;
        int red = ((argb >>> 16) & 0xFF) * ((color >>> 16) & 0xFF) / 255;
        int green = ((argb >>> 8) & 0xFF) * ((color >>> 8) & 0xFF) / 255;
        int blue = (argb & 0xFF) * (color & 0xFF) / 255;
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    /** Source OVER, extracted unchanged from the opaque Essentium compositor. */
    public static int overArgb(int foreground, int background) {
        int foregroundAlpha = (foreground >>> 24) & 0xFF;
        int backgroundAlpha = (background >>> 24) & 0xFF;
        int outputAlpha = foregroundAlpha
                + (backgroundAlpha * (255 - foregroundAlpha) + 127) / 255;
        if (outputAlpha == 0) {
            return 0;
        }

        int foregroundRed = (foreground >>> 16) & 0xFF;
        int foregroundGreen = (foreground >>> 8) & 0xFF;
        int foregroundBlue = foreground & 0xFF;
        int backgroundRed = (background >>> 16) & 0xFF;
        int backgroundGreen = (background >>> 8) & 0xFF;
        int backgroundBlue = background & 0xFF;
        int remainingBackground = (backgroundAlpha * (255 - foregroundAlpha) + 127) / 255;

        int red = (foregroundRed * foregroundAlpha + backgroundRed * remainingBackground)
                / outputAlpha;
        int green = (foregroundGreen * foregroundAlpha + backgroundGreen * remainingBackground)
                / outputAlpha;
        int blue = (foregroundBlue * foregroundAlpha + backgroundBlue * remainingBackground)
                / outputAlpha;
        return outputAlpha << 24 | red << 16 | green << 8 | blue;
    }

    /** Alpha-weighted native image RGB; invisible pixels contribute no color. */
    public static int averageRgb(int[] argb) {
        ChannelSums sums = channelSums(argb);
        return (int) ((sums.red + sums.alpha / 2) / sums.alpha) << 16
                | (int) ((sums.green + sums.alpha / 2) / sums.alpha) << 8
                | (int) ((sums.blue + sums.alpha / 2) / sums.alpha);
    }

    private static ChannelSums channelSums(int[] argb) {
        long alpha = 0;
        long red = 0;
        long green = 0;
        long blue = 0;
        for (int pixel : argb) {
            int weight = pixel >>> 24;
            alpha += weight;
            red += (long) ((pixel >>> 16) & 0xFF) * weight;
            green += (long) ((pixel >>> 8) & 0xFF) * weight;
            blue += (long) (pixel & 0xFF) * weight;
        }
        if (alpha == 0) {
            throw new IllegalArgumentException("A host texture must contain visible pixels");
        }
        return new ChannelSums(alpha, red, green, blue);
    }

    /** Alpha-weighted average host color used by the shared transition colorizer. */
    public static int transitionTint(int[] nativeHostArgb) {
        return averageRgb(nativeHostArgb);
    }

    /**
     * Uses the host color for every substrate. The mask supplies relative shading, while a
     * small normalized luminance lift keeps the transition visible without washing it white.
     */
    private static int transitionArgb(int argb, int hostRgb, int transitionLuminance, double scale) {
        if (transitionLuminance <= 0) return argb;
        int maskLuminance = luminance(argb);
        int result = argb & 0xFF000000;
        for (int shift : new int[]{16, 8, 0}) {
            int hostChannel = Math.max(1, (hostRgb >>> shift) & 0xFF);
            int channel = (int) Math.clamp(Math.round(
                    hostChannel * maskLuminance * scale / transitionLuminance), 0, 255);
            result |= channel << shift;
        }
        return result;
    }

    private static double transitionScale(int hostRgb, int[] transitionArgb, int transitionLuminance) {
        if (transitionLuminance <= 0) return 1;
        int target = Math.min(255, luminance(hostRgb) + TRANSITION_LUMINANCE_LIFT);
        double low = 0;
        double high = 1;
        while (averageColoredLuminance(hostRgb, transitionArgb, transitionLuminance, high) < target
                && high < 256) high *= 2;
        for (int iteration = 0; iteration < 24; iteration++) {
            double middle = (low + high) / 2;
            if (averageColoredLuminance(hostRgb, transitionArgb, transitionLuminance, middle) < target)
                low = middle;
            else high = middle;
        }
        return high;
    }

    private static int averageColoredLuminance(int hostRgb, int[] transitionArgb,
                                               int transitionLuminance, double scale) {
        long alpha = 0;
        long luminance = 0;
        for (int pixel : transitionArgb) {
            int weight = pixel >>> 24;
            alpha += weight;
            luminance += (long) luminance(transitionArgb(pixel, hostRgb, transitionLuminance, scale)) * weight;
        }
        return alpha == 0 ? 0 : (int) ((luminance + alpha / 2) / alpha);
    }

    private static int luminance(int argb) {
        return (54 * ((argb >>> 16) & 0xFF) + 183 * ((argb >>> 8) & 0xFF)
                + 19 * (argb & 0xFF) + 128) >>> 8;
    }

    private static int averageLuminanceOrZero(int[] argb) {
        long alpha = 0;
        long luminance = 0;
        for (int pixel : argb) {
            int weight = pixel >>> 24;
            alpha += weight;
            luminance += (long) luminance(pixel) * weight;
        }
        return alpha == 0 ? 0 : (int) ((luminance + alpha / 2) / alpha);
    }

    /**
     * Output uses the native host resolution. Each whole overlay is scaled with
     * nearest-neighbor sampling, never tiled or cropped. Ore RGB is never tinted.
     */
    public static int[] composeLatentOre(
            int[] hostArgb, int width, int height,
            int[] transitionArgb, int transitionWidth, int transitionHeight,
            int[] oreArgb, int oreWidth, int oreHeight
    ) {
        validateImage(hostArgb, width, height);
        validateImage(transitionArgb, transitionWidth, transitionHeight);
        validateImage(oreArgb, oreWidth, oreHeight);
        int tint = transitionTint(hostArgb);
        int transitionLuminance = averageLuminanceOrZero(transitionArgb);
        double transitionScale = transitionScale(tint, transitionArgb, transitionLuminance);
        int[] result = new int[hostArgb.length];
        for (int y = 0; y < height; y++) {
            int transitionRow = (int) ((long) y * transitionHeight / height) * transitionWidth;
            int oreRow = (int) ((long) y * oreHeight / height) * oreWidth;
            for (int x = 0; x < width; x++) {
                int transition = transitionArgb[transitionRow + (int) ((long) x * transitionWidth / width)];
                int ore = oreArgb[oreRow + (int) ((long) x * oreWidth / width)];
                int index = y * width + x;
                // Preserve even invisible host RGB outside the overlays exactly.
                result[index] = (transition >>> 24) == 0 && (ore >>> 24) == 0
                        ? hostArgb[index]
                        : overArgb(ore, overArgb(transitionArgb(
                                transition, tint, transitionLuminance, transitionScale), hostArgb[index]));
            }
        }
        return result;
    }

    /** Minecraft 1.21.1 NativeImage uses ABGR; BufferedImage and this core use ARGB. */
    public static int abgrToArgb(int abgr) {
        return (abgr & 0xFF00FF00) | ((abgr & 0xFF) << 16) | ((abgr >>> 16) & 0xFF);
    }

    public static int argbToAbgr(int argb) {
        return abgrToArgb(argb);
    }

    private static void validateImage(int[] pixels, int width, int height) {
        if (width <= 0 || height <= 0 || (long) width * height != pixels.length) {
            throw new IllegalArgumentException("Image dimensions must match its pixel count");
        }
    }

    private record ChannelSums(long alpha, long red, long green, long blue) {
    }
}
