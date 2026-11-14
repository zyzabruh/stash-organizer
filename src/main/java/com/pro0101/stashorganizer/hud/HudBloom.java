package com.pro0101.stashorganizer.hud;

import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;

/**
 * Builds a small bloom out of the HUD shapes themselves.
 *
 * <p>The HUD pass has no framebuffer to blur, so a real bloom pass would mean rendering the panel
 * to a target and filtering it back. At HUD sizes the same read comes from a few expanding
 * translucent quads behind the panel and a soft halo around the text: the light spills a couple of
 * pixels past the edge, which is all a bloom does at this scale.
 */
public final class HudBloom {
    private static final int[] GROW = {4, 3, 2, 1};
    private static final int[] ALPHA = {6, 10, 14, 18};

    private HudBloom() {}

    /**
     * Draws the halo behind a panel.
     *
     * @param renderer HUD renderer
     * @param x panel left
     * @param y panel top
     * @param width panel width
     * @param height panel height
     * @param color accent colour of the panel
     */
    public static void panel(HudRenderer renderer, double x, double y, double width, double height, Color color) {
        for (int i = 0; i < GROW.length; i++) {
            double grow = GROW[i];

            renderer.quad(
                x - grow, y - grow, width + grow * 2.0, height + grow * 2.0,
                new Color(color.r, color.g, color.b, ALPHA[i])
            );
        }
    }

    /**
     * Draws text with a soft halo of its own colour.
     *
     * @param renderer HUD renderer
     * @param text text to draw
     * @param x text left
     * @param y text top
     * @param color text colour
     * @param shadow whether the game shadow is drawn too
     * @return width of the text, as {@link HudRenderer#text} returns it
     */
    public static double text(HudRenderer renderer, String text, double x, double y, Color color, boolean shadow) {
        Color glow = new Color(color.r, color.g, color.b, 40);

        renderer.text(text, x - 1.0, y, glow, false);
        renderer.text(text, x + 1.0, y, glow, false);
        renderer.text(text, x, y - 1.0, glow, false);
        renderer.text(text, x, y + 1.0, glow, false);

        return renderer.text(text, x, y, color, shadow);
    }
}
