package com.pro0101.stashorganizer.hud;

import com.pro0101.stashorganizer.StashOrganizerAddon;
import com.pro0101.stashorganizer.modules.bephax.BetterStashFinder;
import com.pro0101.stashorganizer.modules.bephax.searcharea.SearchArea;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.Color;

import java.util.ArrayList;
import java.util.List;

/**
 * What the search modules found: the stash count and which search mode is running.
 *
 * <p>Both only spoke through chat, which is exactly the output you cannot read while flying a search pattern.
 */
public class DiscoveryHud extends HudElement {
    public static final HudElementInfo<DiscoveryHud> INFO = new HudElementInfo<>(
        StashOrganizerAddon.HUD_GROUP,
        "discovery-status",
        "Stashes found and the running search mode.",
        DiscoveryHud::new
    );

    private static final String TITLE = "Discovery";
    private static final Color BACKGROUND = new Color(0, 0, 0, 120);
    private static final Color LINE_COLOR = new Color(170, 170, 170);
    private static final double PADDING = 2;

    private final List<String> lines = new ArrayList<>();

    public DiscoveryHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        lines.clear();

        BetterStashFinder stashFinder = Modules.get().get(BetterStashFinder.class);
        if (stashFinder != null && stashFinder.isActive()) {
            String last = stashFinder.lastFound();
            lines.add("stashes " + stashFinder.stashCount() + (last == null ? "" : ", last " + last));
        }

        SearchArea searchArea = Modules.get().get(SearchArea.class);
        if (searchArea != null && searchArea.isActive()) {
            lines.add("search " + searchArea.chunkLoadMode.get().name().toLowerCase(java.util.Locale.ROOT));
        }

        if (lines.isEmpty()) lines.add("idle");

        double lineHeight = renderer.textHeight(true) + 1;
        double width = renderer.textWidth(TITLE, true);
        for (String line : lines) width = Math.max(width, renderer.textWidth(line, true));

        setSize(width + PADDING * 2, lineHeight * (lines.size() + 1) + PADDING * 2);

        HudBloom.panel(renderer, x, y, getWidth(), getHeight(), Color.WHITE);
        renderer.quad(x, y, getWidth(), getHeight(), BACKGROUND);

        double textX = x + PADDING;
        double textY = y + PADDING;

        HudBloom.text(renderer, TITLE, textX, textY, Color.WHITE, true);
        textY += lineHeight;

        for (String line : lines) {
            HudBloom.text(renderer, line, textX, textY, LINE_COLOR, true);
            textY += lineHeight;
        }
    }
}
