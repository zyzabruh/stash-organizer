package com.pro0101.stashorganizer.hud;

import com.pro0101.stashorganizer.StashOrganizerAddon;
import com.pro0101.stashorganizer.modules.huntbuddy.ElytraRecast;
import com.pro0101.stashorganizer.modules.huntbuddy.Pitch40;
import com.pro0101.stashorganizer.modules.huntbuddy.RocketBoost;
import com.pro0101.stashorganizer.modules.huntbuddy.RocketFly;
import com.pro0101.stashorganizer.modules.huntbuddy.TrailFollower;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The flight chain imported from HunterBuddy says nothing on screen: five modules, most of them toggling each other, and
 * no way to tell what is running or at which altitude. This lists the active ones plus the bounds and trail mode.
 */
public class FlightHud extends HudElement {
    public static final HudElementInfo<FlightHud> INFO = new HudElementInfo<>(
        StashOrganizerAddon.HUD_GROUP,
        "flight-status",
        "Lists the running flight modules, with the pitch-40 bounds and the trail follower mode.",
        FlightHud::new
    );

    private static final String TITLE = "Flight";
    private static final Color BACKGROUND = new Color(0, 0, 0, 120);
    private static final Color LINE_COLOR = new Color(170, 170, 170);
    private static final double PADDING = 2;

    private final List<String> lines = new ArrayList<>();

    public FlightHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        lines.clear();

        Pitch40 pitch40 = Modules.get().get(Pitch40.class);
        if (pitch40 != null && pitch40.isActive()) {
            lines.add(
                String.format(
                    Locale.ROOT, "pitch-40 %.0f..%.0f deg", pitch40.pitch40LowerBounds.get(), pitch40.pitch40UpperBounds.get()
                )
            );
        }

        TrailFollower trailFollower = Modules.get().get(TrailFollower.class);
        if (trailFollower != null && trailFollower.isActive()) {
            lines.add("trail-follower " + trailFollower.overworldFlightMode.get().name().replace('_', '-').toLowerCase(Locale.ROOT));
        }

        if (isActive(RocketFly.class)) lines.add("rocket-fly");
        if (isActive(RocketBoost.class)) lines.add("rocket-boost");
        if (isActive(ElytraRecast.class)) lines.add("elytra-recast");

        if (lines.isEmpty()) lines.add("idle");

        double lineHeight = renderer.textHeight(true) + 1;
        double width = renderer.textWidth(TITLE, true);
        for (String line : lines) width = Math.max(width, renderer.textWidth(line, true));

        setSize(width + PADDING * 2, lineHeight * (lines.size() + 1) + PADDING * 2);

        renderer.quad(x, y, getWidth(), getHeight(), BACKGROUND);

        double textX = x + PADDING;
        double textY = y + PADDING;

        renderer.text(TITLE, textX, textY, Color.WHITE, true);
        textY += lineHeight;

        for (String line : lines) {
            renderer.text(line, textX, textY, LINE_COLOR, true);
            textY += lineHeight;
        }
    }

    private static boolean isActive(Class<? extends Module> type) {
        Module module = Modules.get().get(type);
        return module != null && module.isActive();
    }
}
