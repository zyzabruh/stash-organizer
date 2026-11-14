package com.pro0101.stashorganizer.hud;

import com.pro0101.stashorganizer.StashOrganizerAddon;
import com.pro0101.stashorganizer.modules.stashorganizer.StashOrganizer;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.Color;

import java.util.ArrayList;
import java.util.List;

/** Phase, counters and progress bar of the stash organizer. */
public class StashHud extends HudElement {
    public static final HudElementInfo<StashHud> INFO = new HudElementInfo<>(
        StashOrganizerAddon.HUD_GROUP,
        "stash-organizer",
        "Shows the phase, the counters and the progress of the stash organizer.",
        StashHud::new
    );

    private static final String TITLE = "Stash organizer";
    private static final Color BACKGROUND = new Color(0, 0, 0, 120);
    private static final Color LINE_COLOR = new Color(170, 170, 170);
    private static final Color BAR_BACKGROUND = new Color(255, 255, 255, 40);
    private static final Color BAR_COLOR = new Color(90, 200, 120, 220);
    private static final double PADDING = 2;
    private static final double BAR_HEIGHT = 3;
    private static final double BAR_SPACING = 3;
    private static final double MIN_BAR_WIDTH = 60;

    private final List<String> lines = new ArrayList<>();

    public StashHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        StashOrganizer module = Modules.get().get(StashOrganizer.class);

        lines.clear();
        if (module != null) lines.addAll(module.hudLines());
        if (lines.isEmpty()) lines.add("idle");

        double progress = module == null ? -1 : module.hudProgress();

        double lineHeight = renderer.textHeight(true) + 1;
        double width = renderer.textWidth(TITLE, true);
        for (String line : lines) width = Math.max(width, renderer.textWidth(line, true));
        if (progress >= 0) width = Math.max(width, MIN_BAR_WIDTH);

        double height = lineHeight * (lines.size() + 1) + PADDING * 2;
        if (progress >= 0) height += BAR_HEIGHT + BAR_SPACING;

        setSize(width + PADDING * 2, height);

        HudBloom.panel(renderer, x, y, getWidth(), getHeight(), BAR_COLOR);
        renderer.quad(x, y, getWidth(), getHeight(), BACKGROUND);

        double textX = x + PADDING;
        double textY = y + PADDING;

        HudBloom.text(renderer, TITLE, textX, textY, Color.WHITE, true);
        textY += lineHeight;

        for (String line : lines) {
            HudBloom.text(renderer, line, textX, textY, LINE_COLOR, true);
            textY += lineHeight;
        }

        if (progress < 0) return;

        double barWidth = getWidth() - PADDING * 2;
        renderer.quad(textX, textY + BAR_SPACING, barWidth, BAR_HEIGHT, BAR_BACKGROUND);
        renderer.quad(textX, textY + BAR_SPACING, barWidth * clamp(progress), BAR_HEIGHT, BAR_COLOR);
    }

    private static double clamp(double value) {
        return Math.min(1, Math.max(0, value));
    }
}
