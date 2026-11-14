package com.pro0101.stashorganizer.render;

/**
 * Colour helpers shared by the render modules.
 */
public final class Colours {
    private Colours() {
    }

    /**
     * Converts a hue to a fully saturated, fully bright colour.
     *
     * @param hue hue in the range {@code [0, 1)}
     * @return packed RGB, without alpha
     */
    public static int hsv(float hue) {
        float sector = hue * 6.0F;
        int index = (int) sector;
        float fraction = sector - index;

        int down = Math.round((1.0F - fraction) * 255.0F);
        int up = Math.round(fraction * 255.0F);

        return switch (index % 6) {
            case 0 -> (255 << 16) | (up << 8);
            case 1 -> (down << 16) | (255 << 8);
            case 2 -> (255 << 8) | up;
            case 3 -> (down << 8) | 255;
            case 4 -> (up << 16) | 255;
            default -> (255 << 16) | down;
        };
    }
}
